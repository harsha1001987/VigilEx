"""
Assessment reports: content built from the stored assessment, rendered to PDF.

Report data flows one way: stored assessment -> ReportContent -> PDF. Nothing
here produces a score or risk band; findings describe the backend's own
component severities in the same observation language the app uses
(app InterpretationRules.kt), never diagnostic language.
"""

from __future__ import annotations

import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from xml.sax.saxutils import escape

from reportlab.graphics.shapes import Circle, Drawing, Line, Rect
from reportlab.lib import colors
from reportlab.lib.enums import TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase.pdfmetrics import stringWidth
from reportlab.pdfgen.canvas import Canvas
from reportlab.platypus import (
    CondPageBreak,
    Flowable,
    KeepTogether,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
)

from app.core.config import get_settings
from app.models.assessment import Assessment
from app.schemas.analysis import (
    ComponentRow,
    ComponentTable,
    Intervention,
    MeasurementReliability,
    MethodResult,
    ProductRecommendation,
    RegionMeasurement,
    ReportContent,
    ReportFinding,
)
from app.services.analysis_assessments import (
    NOT_MEASURED_NOTE,
    VIEW_EXPLANATION,
    intervention_plan,
    measurement_reliability,
    method_result,
    overall_risk,
    products_for,
    region_measurements,
    video_name,
)
from app.services.interventions import frequency_word, region_status

# ------------------------------------------------------------ wording rules

MAX_FINDINGS = 3
MAX_OBSERVATION_REGIONS = 2
MAX_EVIDENCE_FRAMES = 3

_STATUS_RANK = {"not_measured": 0, "low": 1, "attention": 2, "elevated": 3, "high": 4}
_STATUS_LABEL = {"not_measured": "Not measured", "low": "Low", "attention": "Attention", "elevated": "Elevated", "high": "High"}

_TITLES = {
    "upper_arm": ("Elevated upper-arm loading", "Moderate upper-arm elevation", "Limited upper-arm elevation"),
    "knee_flexion": ("Deep knee flexion", "Moderate knee flexion", "Limited knee flexion"),
    "trunk": ("Pronounced trunk flexion", "Moderate trunk flexion", "Low trunk deviation"),
    "neck": ("Pronounced neck flexion", "Moderate neck flexion", "Limited neck flexion"),
}

_GUIDANCE = {
    "low": "Scores are within the range the methods treat as acceptable.",
    "moderate": "Further ergonomic review may be warranted.",
    "high": "Ergonomic review is recommended soon.",
    "very_high": "Ergonomic review is recommended promptly.",
}

# Main report, in reading order; the product section appears only when a product qualifies.
PRODUCT_SECTION = "Recommended product to evaluate"
MAIN_SECTIONS = (
    "Overall assessment",
    "Priority action",
    PRODUCT_SECTION,
    "Other actions",
    "Measurements",
    "Posture evidence",
    "Camera view and reliability",
)
TECHNICAL_SECTION = "Technical details and assessment limitations"

COMPONENT_HEADING = "Component measurements at the peak-score frame"
COMPONENT_NOTE = (
    "The overall peak shown in Measurements is the highest observed value across analyzed frames. Component "
    "angles below are taken from the frame where the RULA or REBA score reached its peak, so they may differ."
)
MEASUREMENTS_NOTE = "Average and peak joint angles across all analyzed frames in which each region was observable."

METHODOLOGY = [
    "VigilEx detects the worker's pose in sampled video frames (YOLO11n-Pose) and follows the same worker "
    "through the video (ByteTrack). Joint angles are measured from the detected 2D landmarks.",
    "RULA and REBA are scored per sampled frame from those angles; this report gives the peak score of each "
    "and how the scored frames were distributed across risk bands.",
    "Measurements give the average and highest value of each angle across all analyzed frames. Component "
    "tables give the angles at the single frame where each method reached its peak score.",
]

LIMITATIONS = [
    "Wrist posture, wrist twist, force or load, coupling and activity cannot be observed from the video "
    "and are scored at their lowest-risk values. This is not evidence that they were low risk.",
    VIEW_EXPLANATION,
    "This report describes observed posture. It does not assess injury, pain, fatigue or health, "
    "and findings should be reviewed by a qualified person.",
    "Recommended interventions are general workplace and posture suggestions based on the observed posture, "
    "not medical advice.",
]
PRODUCT_LIMITATION = (
    "Product suggestions are for evaluation only. Product descriptions come from Extrive product material; "
    "the posture scores in this report are not evidence of a product's effectiveness."
)


def report_sections(has_product: bool) -> list[str]:
    return [s for s in MAIN_SECTIONS if has_product or s != PRODUCT_SECTION] + [TECHNICAL_SECTION]


def methodology(assessment: Assessment) -> list[str]:
    video = (assessment.capture_metadata or {}).get("video") or {}
    hz, sampled = video.get("sample_hz"), video.get("frames_sampled")
    out = list(METHODOLOGY)
    if hz and sampled:
        out.insert(1, f"Frames were sampled at {hz:g} per second ({sampled} frames sampled).")
    return out


def limitations(reliability: MeasurementReliability | None, has_product: bool) -> list[str]:
    out = list(LIMITATIONS)
    if reliability is not None and reliability.status == "limited":
        out.insert(2, f"{reliability.title}: {' '.join(reliability.reasons)}")
    if has_product:
        out.append(PRODUCT_LIMITATION)
    return out

_status = region_status


def _title(region: RegionMeasurement, status: str) -> str:
    strong, moderate, quiet = _TITLES[region.metric]
    return {"elevated": strong, "high": strong, "attention": moderate}.get(status, quiet)


def _detail(region: RegionMeasurement, status: str) -> str:
    if status == "not_measured":
        return "Not measured in any scored frame; this is not evidence of low risk."
    if status == "low":
        return f"Stayed within the lowest scoring range in all {region.frames_measured} measured frames."
    word = frequency_word(region.frames_at_peak_severity, region.frames_measured)
    return (
        f"Observed {word} during the analyzed movement: "
        f"{region.frames_at_peak_severity} of {region.frames_measured} measured frames."
    )


def _findings(regions: list[RegionMeasurement], overall: str | None) -> tuple[list[ReportFinding], str | None]:
    scored = [(r, _status(r, overall)) for r in regions]
    order = {r.metric: i for i, r in enumerate(regions)}
    scored.sort(
        key=lambda rs: (
            -_STATUS_RANK[rs[1]],
            -(rs[0].frames_at_peak_severity / rs[0].frames_measured if rs[0].frames_measured else 0),
            order[rs[0].metric],
        )
    )
    flagged = [rs for rs in scored if _STATUS_RANK[rs[1]] >= _STATUS_RANK["attention"]]
    quiet = [rs for rs in scored if rs[1] == "low"]
    quiet_count = 2 if not flagged else 1 if len(flagged) < MAX_FINDINGS else 0
    chosen = (flagged + quiet[:quiet_count])[:MAX_FINDINGS]
    findings = [
        ReportFinding(title=_title(r, s), detail=_detail(r, s), status=_STATUS_LABEL[s]) for r, s in chosen
    ]

    if not any(s != "not_measured" for _, s in scored):
        return findings, None
    named = [_title(r, s) for r, s in flagged[:MAX_OBSERVATION_REGIONS]]
    if not named:
        observation = "All measured body regions stayed within their lowest scoring range during the analyzed movement."
    else:
        phrases = [named[0]] + [n[0].lower() + n[1:] for n in named[1:]]
        verb = "was" if len(phrases) == 1 else "were"
        observation = f"{' and '.join(phrases)} {verb} observed during the analyzed movement."
    return findings, observation


def build_report_content(assessment: Assessment) -> ReportContent:
    rula = method_result(assessment, "RULA")
    reba = method_result(assessment, "REBA")
    overall = overall_risk(rula, reba)
    regions = region_measurements(assessment)
    findings, observation = _findings(regions, overall)
    back, interventions, summary_text = intervention_plan(assessment, rula, reba)
    products = products_for(interventions, back)
    reliability = measurement_reliability(assessment, back, regions)
    video = (assessment.capture_metadata or {}).get("video") or {}
    return ReportContent(
        assessment_id=assessment.id,
        title="Ergonomic assessment",
        video_name=video_name(assessment),
        analyzed_at=assessment.created_at,
        duration_sec=video.get("duration_sec"),
        overall_risk=overall,
        rula=rula,
        reba=reba,
        observation=(f"{observation} {_GUIDANCE[overall]}" if observation and overall else observation),
        action_summary=summary_text,
        interventions=interventions,
        product_recommendations=products,
        measurement_reliability=reliability,
        back_posture=back,
        findings=findings,
        measurements=regions,
        measurement_note=f"{MEASUREMENTS_NOTE} {NOT_MEASURED_NOTE}",
        evidence_frames=len(evidence_frames(assessment, regions, rula, reba, overall, interventions)),
        sections=report_sections(bool(products)),
        technical_section=TECHNICAL_SECTION,
        component_note=COMPONENT_NOTE,
        component_tables=[
            t for t in (component_table(assessment, rula, "RULA"), component_table(assessment, reba, "REBA")) if t
        ],
        methodology=methodology(assessment),
        limitations=limitations(reliability, bool(products)),
        methodology_version=assessment.methodology_version,
    )


# ------------------------------------------------------------ evidence

_REGION_ANGLE = {"trunk": "trunk_deg", "upper_arm": "upper_arm_deg", "knee_flexion": "knee_flexion_deg", "neck": "neck_deg"}
_METRIC_FOR_AREA = {"back": "trunk", "knees": "knee_flexion", "neck": "neck", "upper_arm": "upper_arm"}


def _frame_score(frame: dict[str, Any], method: str) -> int | None:
    return (frame.get(method.lower()) or {}).get("score")


def evidence_frames(
    assessment: Assessment,
    regions: list[RegionMeasurement],
    rula: MethodResult | None,
    reba: MethodResult | None,
    overall: str | None,
    interventions: list[Intervention] | None = None,
) -> list[tuple[int, dict[str, Any], str]]:
    """The priority action's peak-angle frame, the leading method's peak-score frame, the other peak."""
    keyframes = (assessment.keypoint_series or {}).get("keyframes", [])
    level = {"negligible": 0, "low": 0, "moderate": 1, "medium": 1, "high": 2, "very_high": 3}
    lead, other = ("RULA", "REBA") if (rula and reba and level.get(rula.risk, 0) > level.get(reba.risk, 0)) else ("REBA", "RULA")
    results = {"RULA": rula, "REBA": reba}

    def peak(method: str) -> tuple[int, str] | None:
        r = results[method]
        if r is None or r.score is None:
            return None
        for i, f in enumerate(keyframes):
            if _frame_score(f, method) == r.score:
                return i, f"Peak {method} {r.score} / {r.scale_max}"
        return None

    key = None
    if interventions:
        # The evidence for the priority action comes first.
        metric = _METRIC_FOR_AREA[interventions[0].area]
        key = next((r for r in regions if r.metric == metric), None)
    if key is None:
        flagged = sorted(
            (r for r in regions if r.peak_severity in ("moderate", "high")),
            key=lambda r: (-(r.peak_severity == "high"), -(r.frames_at_peak_severity / max(r.frames_measured, 1))),
        )
        key = flagged[0] if flagged else None

    candidates: list[tuple[int, str] | None] = []
    if key is not None:
        angle_key = _REGION_ANGLE[key.metric]
        best = None
        for i, f in enumerate(keyframes):
            angle = ((f.get("rula") or {}).get("angles") or {}).get(angle_key)
            if angle is not None and (best is None or angle > best[1]):
                best = (i, angle)
        if best:
            candidates.append((best[0], f"Peak {key.label.lower()} {best[1]:.1f}°"))
    candidates += [peak(lead), peak(other)]

    seen, out = set(), []
    for c in candidates:
        if c and c[0] not in seen:
            seen.add(c[0])
            out.append((c[0], keyframes[c[0]], c[1]))
    return out[:MAX_EVIDENCE_FRAMES]


# ------------------------------------------------------------ storage

def report_dir() -> Path:
    settings = get_settings()
    path = Path(settings.report_dir)
    if not path.is_absolute():
        path = Path(__file__).resolve().parent.parent.parent / settings.report_dir
    path.mkdir(parents=True, exist_ok=True)
    return path


def report_path(assessment_id: uuid.UUID) -> Path:
    return report_dir() / f"{assessment_id}.pdf"


def report_file_name(assessment: Assessment) -> str:
    return f"VigilEx-ergonomic-assessment-{assessment.created_at:%Y-%m-%d}-{str(assessment.id)[:8]}.pdf"


def delete_report(assessment_id: uuid.UUID) -> None:
    path = report_path(assessment_id)
    if path.exists():
        path.unlink()


def stored_report(assessment: Assessment) -> Path | None:
    """The generated PDF if it exists and is not older than the assessment."""
    path = report_path(assessment.id)
    if not path.exists():
        return None
    generated = datetime.fromtimestamp(path.stat().st_mtime, tz=timezone.utc)
    if assessment.updated_at and generated < assessment.updated_at:
        return None
    return path


# ------------------------------------------------------------ PDF
#
# Layout rules: every variable text is a wrapping Paragraph (never a bare
# table string), tables repeat their header row on a new page, a heading is
# always kept with the block it introduces, and nothing is drawn outside the
# frame. Colour is never the only signal: every status and risk is in words.

INK = colors.HexColor("#171717")
SECONDARY = colors.HexColor("#45453F")
MUTED = colors.HexColor("#6E6E68")
HAIRLINE = colors.HexColor("#E4E3DD")
SUNKEN = colors.HexColor("#F3F2EE")
YELLOW = colors.HexColor("#F4C928")
GOLD = colors.HexColor("#B68A2C")
GOLD_INK = colors.HexColor("#7A5C14")
RED = colors.HexColor("#C93636")
RED_INK = colors.HexColor("#A82A2A")
NOTICE = colors.HexColor("#FBF6E6")

_RISK_COLOR = {"low": INK, "moderate": GOLD_INK, "high": RED_INK, "very_high": RED_INK}
_RISK_ACCENT = {"low": MUTED, "moderate": GOLD, "high": RED, "very_high": RED}
_STATUS_COLOR = {"Low": MUTED, "Not measured": MUTED, "Attention": INK, "Elevated": GOLD_INK, "High": RED_INK}
_AREA_LABEL = {"back": "Back / trunk", "knees": "Knees", "neck": "Neck", "upper_arm": "Upper arm"}

PAGE_W, PAGE_H = A4
MARGIN_X = 18 * mm
MARGIN_TOP = 24 * mm
MARGIN_BOTTOM = 22 * mm
REPORT_TITLE = "Ergonomic assessment report"

# allowWidows=0: a paragraph never leaves its last line alone at the top of the next page.
_body = ParagraphStyle("body", fontName="Helvetica", fontSize=9.5, leading=13.5, textColor=INK, allowWidows=0)
_body_muted = ParagraphStyle("body_muted", parent=_body, textColor=SECONDARY)
_small = ParagraphStyle("small", fontName="Helvetica", fontSize=8, leading=11, textColor=MUTED)
_lead = ParagraphStyle("lead", fontName="Helvetica", fontSize=11, leading=15.5, textColor=INK, allowWidows=0)
_title_style = ParagraphStyle("title", fontName="Helvetica-Bold", fontSize=22, leading=26, textColor=INK)
_name_style = ParagraphStyle("name", fontName="Helvetica", fontSize=13, leading=17, textColor=INK)
_h1 = ParagraphStyle("h1", fontName="Helvetica-Bold", fontSize=14, leading=18, textColor=INK)
_h2 = ParagraphStyle("h2", fontName="Helvetica-Bold", fontSize=10.5, leading=14, textColor=INK)
_h2_gold = ParagraphStyle("h2_gold", parent=_h2, textColor=GOLD_INK)
_action = ParagraphStyle("action", fontName="Helvetica-Bold", fontSize=15, leading=19, textColor=INK)
_meta_label = ParagraphStyle("meta_label", fontName="Helvetica", fontSize=8.5, leading=12, textColor=MUTED)
_meta_value = ParagraphStyle("meta_value", fontName="Helvetica", fontSize=9.5, leading=12, textColor=INK)
_th = ParagraphStyle("th", fontName="Helvetica-Bold", fontSize=8, leading=10, textColor=MUTED)
_th_right = ParagraphStyle("th_right", parent=_th, alignment=TA_RIGHT)
_td = ParagraphStyle("td", fontName="Helvetica", fontSize=9, leading=12, textColor=INK, allowWidows=0)
_td_right = ParagraphStyle("td_right", parent=_td, alignment=TA_RIGHT)
_td_bold = ParagraphStyle("td_bold", parent=_td, fontName="Helvetica-Bold")


def _p(text: str | None, style: ParagraphStyle) -> Paragraph:
    """A wrapping paragraph of plain text (escaped; never interpreted as markup)."""
    return Paragraph(escape(text or ""), style)


def _risk_words(risk: str | None) -> str:
    return "Not measured" if risk is None else f"{risk.replace('_', ' ').capitalize()} risk"


def _status_cell(status: str, align_right: bool = True) -> Paragraph:
    style = ParagraphStyle(f"status_{status}", parent=_td_right if align_right else _td, textColor=_STATUS_COLOR.get(status, INK))
    if status in ("Elevated", "High"):
        style.fontName = "Helvetica-Bold"
    return Paragraph(escape(status), style)


def _fit(text: str, font: str, size: float, max_width: float) -> str:
    """Shortens [text] with an ellipsis so it fits on one line of [max_width]."""
    if stringWidth(text, font, size) <= max_width:
        return text
    while text and stringWidth(text + "…", font, size) > max_width:
        text = text[:-1]
    return text.rstrip() + "…"


class _Rule(Flowable):
    def __init__(self, width: float, weight: float = 0.5, color=HAIRLINE):
        super().__init__()
        self.width, self.weight, self.color = width, weight, color

    def wrap(self, *_):
        return self.width, self.weight

    def draw(self):
        self.canv.setFillColor(self.color)
        self.canv.rect(0, 0, self.width, self.weight, stroke=0, fill=1)


class _RiskMark(Flowable):
    """A filled dot before the overall risk words; the words carry the meaning."""

    def __init__(self, color, size: float = 9):
        super().__init__()
        self.color, self.size = color, size

    def wrap(self, *_):
        return self.size, self.size

    def draw(self):
        self.canv.setFillColor(self.color)
        self.canv.circle(self.size / 2, self.size / 2, self.size / 2, stroke=0, fill=1)


def _numbered_canvas(footer_left: str):
    """A canvas that draws the header and footer once the page count is known ("Page 2 of 5")."""

    class NumberedCanvas(Canvas):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, **kwargs)
            self._pages: list[dict] = []

        def showPage(self):
            self._pages.append(dict(self.__dict__))
            self._startPage()

        def save(self):
            total = len(self._pages)
            for state in self._pages:
                self.__dict__.update(state)
                self._chrome(total)
                super().showPage()
            super().save()

        def _chrome(self, total: int):
            self.saveState()
            top = PAGE_H - 13 * mm
            self.setFillColor(YELLOW)
            self.rect(MARGIN_X, top - 0.6 * mm, 3.2 * mm, 3.2 * mm, stroke=0, fill=1)
            self.setFillColor(INK)
            self.setFont("Helvetica-Bold", 9.5)
            self.drawString(MARGIN_X + 5 * mm, top, "VigilEx")
            self.setFillColor(MUTED)
            self.setFont("Helvetica", 8)
            self.drawRightString(PAGE_W - MARGIN_X, top, REPORT_TITLE)
            self.setStrokeColor(HAIRLINE)
            self.setLineWidth(0.5)
            self.line(MARGIN_X, top - 3.5 * mm, PAGE_W - MARGIN_X, top - 3.5 * mm)

            self.line(MARGIN_X, 14 * mm, PAGE_W - MARGIN_X, 14 * mm)
            page = f"Page {self._pageNumber} of {total}"
            self.setFont("Helvetica", 8)
            self.drawRightString(PAGE_W - MARGIN_X, 9.5 * mm, page)
            room = PAGE_W - 2 * MARGIN_X - stringWidth(page, "Helvetica", 8) - 10 * mm
            self.drawString(MARGIN_X, 9.5 * mm, _fit(footer_left, "Helvetica", 8, room))
            self.restoreState()

    return NumberedCanvas


_COCO_EDGES = [
    (0, 1), (0, 2), (1, 3), (2, 4), (5, 6), (5, 7), (7, 9), (6, 8), (8, 10),
    (5, 11), (6, 12), (11, 12), (11, 13), (13, 15), (12, 14), (14, 16),
]


def _pose(frame: dict[str, Any], max_width: float, max_height: float, fw: int, fh: int) -> Drawing:
    """The detected pose, drawn from the stored landmarks (the video itself is not kept), fitted to the box."""
    pts = frame.get("landmarks") or []
    if len(pts) >= 17 and not (fw and fh):
        # No stored frame size: frame the visible landmarks instead of drawing nothing.
        seen = [p for p in pts if (p.get("confidence") or 0) >= 0.5]
        if seen:
            fw = int(max(p["x"] for p in seen) * 1.15) or 1
            fh = int(max(p["y"] for p in seen) * 1.1) or 1
    aspect = fw / fh if fw and fh else 16 / 9
    width, height = max_width, max_width / aspect
    if height > max_height:
        width, height = max_height * aspect, max_height
    d = Drawing(width, height)
    d.add(Rect(0, 0, width, height, fillColor=SUNKEN, strokeColor=None))
    if len(pts) >= 17 and fw and fh:
        def at(i):
            p = pts[i]
            if (p.get("confidence") or 0) < 0.5:
                return None
            return p["x"] / fw * width, height - p["y"] / fh * height

        for a, b in _COCO_EDGES:
            p1, p2 = at(a), at(b)
            if p1 and p2:
                d.add(Line(p1[0], p1[1], p2[0], p2[1], strokeColor=INK, strokeWidth=1.4, strokeLineCap=1))
        for i in range(len(pts)):
            p = at(i)
            if p:
                d.add(Circle(p[0], p[1], 1.6, fillColor=INK, strokeColor=None))
    return d


_COMPONENT_ORDER = ("upper_arm", "lower_arm", "wrist", "wrist_twist", "neck", "trunk", "legs")


def component_table(assessment: Assessment, result: MethodResult | None, method: str) -> ComponentTable | None:
    """The method's components at the first frame that reached its peak score."""
    if result is None or result.score is None:
        return None
    for index, frame in enumerate((assessment.keypoint_series or {}).get("keyframes", [])):
        scored = frame.get(method.lower()) or {}
        if scored.get("score") != result.score:
            continue
        components = scored.get("components") or {}
        rows = []
        # JSONB does not keep key order; list components head-to-toe as the methods do.
        for key in sorted(components, key=lambda k: _COMPONENT_ORDER.index(k) if k in _COMPONENT_ORDER else len(_COMPONENT_ORDER)):
            c = components[key]
            measured = bool(c.get("measured"))
            rows.append(ComponentRow(
                name=c.get("name") or key.replace("_", " ").capitalize(),
                measured=measured,
                angle_deg=c.get("angle_deg") if measured else None,
                band=c.get("band") if measured else None,
                max_band=c.get("max_band") if measured else None,
            ))
        return ComponentTable(
            method=method,
            title=f"{method} — {COMPONENT_HEADING.lower()}",
            score=result.score,
            scale_max=result.scale_max,
            frame_number=index + 1,
            frame_time_sec=frame.get("t"),
            rows=rows,
        )
    return None


def _product_block(product: ProductRecommendation, panel, bullets) -> Table:
    """The recommended product to evaluate: tied to the finding, then what it is, then how to evaluate it."""
    head = " · ".join(p for p in (product.maker, product.category) if p)
    parts: list[Any] = [
        _p(head, _small),
        Spacer(1, 3),
        _p(product.headline, _action),
        Spacer(1, 6),
        _p(product.rationale, _body),
        Spacer(1, 10),
    ]
    if product.content_status == "verified":
        parts += [
            _p("Why it may help", _h2),
            Spacer(1, 3),
            _p(f"{product.intended_use} {product.operating_principle}", _body),
            Spacer(1, 4),
            _p("Relevant tasks: " + "; ".join(t[0].lower() + t[1:] for t in product.task_types) + ".", _body_muted),
            Spacer(1, 10),
        ]
    else:
        parts += [
            _p("Product information pending", _h2_gold),
            Spacer(1, 3),
            _p(f"Missing approved content: {', '.join(product.content_gaps)}.", _body_muted),
            Spacer(1, 10),
        ]
    parts += [_p("Best results come from combining approaches", _h2), Spacer(1, 3)]
    parts += bullets(product.complementary_actions)
    parts += [Spacer(1, 10), _p("Before introducing it", _h2), Spacer(1, 3)]
    parts += bullets(product.suitability)
    parts += [Spacer(1, 10)]
    if product.evidence_note:
        parts += [_p(product.evidence_note, _body_muted), Spacer(1, 4)]
    parts += [_p(product.qualification, _body_muted)]
    if product.sources:
        parts += [Spacer(1, 6), _p("Product information: " + "; ".join(product.sources) + ".", _small)]
    return panel(parts, INK)


def render_pdf(assessment: Assessment, content: ReportContent, target: Path) -> None:
    width = PAGE_W - 2 * MARGIN_X
    name = content.video_name or f"Assessment {str(content.assessment_id)[:8]}"
    analyzed = f"{content.analyzed_at:%d %b %Y · %H:%M} UTC"
    doc = SimpleDocTemplate(
        str(target),
        pagesize=A4,
        leftMargin=MARGIN_X,
        rightMargin=MARGIN_X,
        topMargin=MARGIN_TOP,
        bottomMargin=MARGIN_BOTTOM,
        title=f"VigilEx {REPORT_TITLE.lower()}",
        author="VigilEx",
        invariant=1,  # Same assessment, same bytes.
    )

    story: list[Any] = []
    overall = content.overall_risk
    primary = content.interventions[0] if content.interventions else None
    others = content.interventions[1:]
    product = content.product_recommendations[0] if content.product_recommendations else None
    reliability = content.measurement_reliability
    limited = reliability is not None and reliability.status == "limited"

    def heading(name_: str, *first: Any, top: float = 20, keep: float | None = None) -> None:
        """A section heading, kept on the same page as the block it introduces.

        A block that may be long (keep=height) is not moved whole: the heading
        starts a new page only when less than [keep] is left, then the block flows on.
        """
        story.append(Spacer(1, top))
        head = [_Rule(width, 0.8, INK), Spacer(1, 7), _p(name_, _h1), Spacer(1, 8)]
        if keep is None:
            story.append(KeepTogether([*head, *first]))
        else:
            story.extend([CondPageBreak(keep), *head, *first])

    def subheading(text: str, *first: Any, style=_h2) -> None:
        story.append(KeepTogether([_p(text, style), Spacer(1, 4), *first]))

    def table(header: list[str] | None, rows: list[list[Any]], widths: list[float], right_from: int = 1) -> Table:
        """A data table: wrapping cells, hairline rows, a header that repeats on each page."""
        data: list[list[Any]] = []
        if header:
            data.append([_p(h, _th_right if i >= right_from else _th) for i, h in enumerate(header)])
        for row in rows:
            data.append([
                cell if not isinstance(cell, str) else _p(cell, _td_right if i >= right_from else _td)
                for i, cell in enumerate(row)
            ])
        style = [
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("LEFTPADDING", (0, 0), (-1, -1), 0),
            ("RIGHTPADDING", (0, 0), (-1, -1), 6),
            ("RIGHTPADDING", (-1, 0), (-1, -1), 0),
            ("TOPPADDING", (0, 0), (-1, -1), 5),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ("LINEBELOW", (0, 0), (-1, -1), 0.5, HAIRLINE),
        ]
        if header:
            style.append(("LINEBELOW", (0, 0), (-1, 0), 0.8, INK))
        return Table(data, colWidths=[w * width for w in widths], repeatRows=1 if header else 0,
                     hAlign="LEFT", style=style)

    def panel(flowables: list, accent, background=SUNKEN) -> Table:
        """A tinted block with an accent bar on the left.

        One table row per block, so a long panel splits cleanly between blocks
        (never mid-line, never moved whole leaving its heading behind).
        """
        return Table(
            # Spacer(0, 0), not "": an empty string cell would give every row a full text line of height.
            [[Spacer(0, 0), f] for f in flowables],
            colWidths=[3, width - 3],
            hAlign="LEFT",
            style=[
                ("BACKGROUND", (0, 0), (0, -1), accent),
                ("BACKGROUND", (1, 0), (1, -1), background),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (0, -1), 0),
                ("RIGHTPADDING", (0, 0), (0, -1), 0),
                ("LEFTPADDING", (1, 0), (1, -1), 14),
                ("RIGHTPADDING", (1, 0), (1, -1), 14),
                ("TOPPADDING", (0, 0), (-1, -1), 0),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
                ("TOPPADDING", (1, 0), (1, 0), 11),
                ("BOTTOMPADDING", (1, -1), (1, -1), 13),
            ],
        )

    def bullets(items: list[str]) -> list:
        style = ParagraphStyle("bullet", parent=_body, leftIndent=10, bulletIndent=0)
        return [Paragraph(escape(t), style, bulletText="•") for t in items]

    def deg(v):
        return "—" if v is None else f"{v:.1f}°"

    def score(r: MethodResult | None) -> str:
        return "—" if r is None or r.score is None else f"{r.score} / {r.scale_max}"

    # ---------------------------------------------------------- title block
    story.append(_p(REPORT_TITLE, _title_style))
    story.append(Spacer(1, 6))
    story.append(_p(name, _name_style))
    story.append(Spacer(1, 10))
    # Only facts the assessment holds; a missing value is left out, never shown as zero.
    meta = [("Analyzed", analyzed)]
    if content.duration_sec is not None:
        meta.append(("Video duration", f"{content.duration_sec:.1f} s"))
    meta.append(("Assessment ID", str(content.assessment_id)))
    story.append(Table(
        [[_p(k, _meta_label), _p(v, _meta_value)] for k, v in meta],
        colWidths=[32 * mm, width - 32 * mm],
        hAlign="LEFT",
        style=[("LEFTPADDING", (0, 0), (-1, -1), 0), ("TOPPADDING", (0, 0), (-1, -1), 1.5),
               ("BOTTOMPADDING", (0, 0), (-1, -1), 1.5), ("VALIGN", (0, 0), (-1, -1), "TOP")],
    ))

    # ---------------------------------------------------------- 1 Overall assessment
    risk_style = ParagraphStyle("risk", fontName="Helvetica-Bold", fontSize=22, leading=26,
                                textColor=_RISK_COLOR.get(overall, MUTED))
    risk_line = Table(
        [[_RiskMark(_RISK_ACCENT.get(overall, HAIRLINE)), _p(_risk_words(overall), risk_style)]],
        colWidths=[14, width - 14],
        hAlign="LEFT",
        style=[("VALIGN", (0, 0), (-1, -1), "MIDDLE"), ("LEFTPADDING", (0, 0), (-1, -1), 0),
               ("RIGHTPADDING", (0, 0), (-1, -1), 0), ("TOPPADDING", (0, 0), (-1, -1), 0),
               ("BOTTOMPADDING", (0, 0), (-1, -1), 0)],
    )
    verdict: list[Any] = [_p("Overall posture risk", _small), Spacer(1, 3), risk_line]
    if overall in _GUIDANCE:
        verdict += [Spacer(1, 4), _p(_GUIDANCE[overall], _body_muted)]
    scores = [
        [method, score(r), _p("Not measured" if r is None or r.score is None else _risk_words(r.risk), _td_right)]
        for method, r in (("RULA peak score", content.rula), ("REBA peak score", content.reba))
    ]
    left = 0.46
    summary = Table(
        [[verdict, table(["Method", "Score", "Risk level"], scores, [0.24, 0.12, 0.18])]],
        colWidths=[left * width, (1 - left) * width],
        hAlign="LEFT",
        style=[("VALIGN", (0, 0), (-1, -1), "TOP"), ("LEFTPADDING", (0, 0), (-1, -1), 0),
               ("RIGHTPADDING", (0, 0), (0, 0), 12), ("RIGHTPADDING", (1, 0), (1, 0), 0),
               ("TOPPADDING", (0, 0), (-1, -1), 0), ("BOTTOMPADDING", (0, 0), (-1, -1), 0)],
    )
    heading("Overall assessment", summary, top=16)
    if content.action_summary:
        story.append(Spacer(1, 12))
        story.append(_p(content.action_summary, _lead))
    if limited:
        # Next to the result, so no result is read without its limitation.
        story.append(Spacer(1, 12))
        story.append(KeepTogether(panel([
            _p(reliability.title, _h2_gold),
            Spacer(1, 4),
            *[_p(r, _body) for r in reliability.reasons],
            Spacer(1, 4),
            _p(reliability.recommendation, _body_muted),
        ], GOLD, NOTICE)))
    if content.findings:
        story.append(Spacer(1, 12))
        rows = [
            [Paragraph(f"<b>{escape(f.title)}</b><br/><font color='#45453F'>{escape(f.detail)}</font>", _td),
             _status_cell(f.status)]
            for f in content.findings
        ]
        subheading("Key findings", table(["Finding", "Status"], rows, [0.78, 0.22]))
    if content.interventions:
        # The summary page names every recommended action, in the backend's priority order;
        # what each involves and why follows in the sections below.
        rows = [
            [
                Paragraph(
                    f"<b>{escape(item.title)}</b> &nbsp;<font color='#6E6E68'>"
                    f"{escape(_AREA_LABEL.get(item.area, item.area))}{' · priority' if i == 0 else ''}</font>",
                    _td,
                ),
                _status_cell(_STATUS_LABEL[item.status]),
            ]
            for i, item in enumerate(content.interventions)
        ]
        story.append(Spacer(1, 12))
        subheading("Recommended actions", table(["Action", "Status"], rows, [0.78, 0.22]))

    # ---------------------------------------------------------- 2 Priority action
    if primary is None:
        heading("Priority action", _p("No body region was flagged often enough to need a specific action.", _body_muted))
    else:
        status = _STATUS_LABEL[primary.status]
        heading("Priority action", panel([
            _p(_AREA_LABEL.get(primary.area, primary.area), _small),
            Spacer(1, 3),
            _p(primary.title, _action),
            Spacer(1, 6),
            _p(primary.recommendation, ParagraphStyle("rec", parent=_body, fontSize=10.5, leading=14.5)),
            Spacer(1, 6),
            _p(primary.observation, _body_muted),
            Spacer(1, 6),
            Paragraph(f"Status: {escape(status)}", ParagraphStyle(
                "hero_status", parent=_td_bold, textColor=_STATUS_COLOR.get(status, INK))),
        ], RED if primary.status == "high" else GOLD))

    # ---------------------------------------------------------- Recommended product (when one qualifies)
    if product is not None:
        heading(PRODUCT_SECTION, _product_block(product, panel, bullets), keep=70 * mm)

    # ---------------------------------------------------------- Other actions
    if not others:
        heading("Other actions", _p("No other body region needed action.", _body_muted))
    else:
        rows = [
            [
                Paragraph(
                    f"<b>{escape(item.title)}</b> &nbsp;<font color='#6E6E68'>{escape(_AREA_LABEL.get(item.area, item.area))}</font>"
                    f"<br/>{escape(item.recommendation)}<br/><font color='#45453F'>{escape(item.observation)}</font>",
                    _td,
                ),
                _status_cell(_STATUS_LABEL[item.status]),
            ]
            for item in others
        ]
        heading("Other actions", table(["Action", "Status"], rows, [0.78, 0.22]))

    # ---------------------------------------------------------- Measurements
    rows = []
    for m in content.measurements:
        label = "Back / trunk" if m.metric == "trunk" else m.label
        rows.append([label, deg(m.average_deg), deg(m.maximum_deg), _status_cell(_STATUS_LABEL[_status(m, overall)])])
    heading("Measurements", table(["Body region", "Average", "Peak", "Status"], rows, [0.4, 0.18, 0.18, 0.24]))
    story.append(Spacer(1, 4))
    story.append(_p(content.measurement_note, _small))

    # ---------------------------------------------------------- Posture evidence
    frames = evidence_frames(assessment, content.measurements, content.rula, content.reba, overall, content.interventions)
    if frames:
        video = (assessment.capture_metadata or {}).get("video") or {}
        fw, fh = int(video.get("width") or 0), int(video.get("height") or 0)
        portrait = bool(fw and fh and fh > fw)
        columns = 3 if portrait else 2
        gap = 6 * mm
        cell = (width - gap * (columns - 1)) / columns
        cells = [
            [
                _pose(frame, cell, 62 * mm, fw, fh),
                Spacer(1, 4),
                _p(f"Frame {index + 1} · {frame.get('t', 0):.1f} s", _small),
                _p(reason, _td_bold),
            ]
            for index, frame, reason in frames
        ]
        grid = [cells[i:i + columns] + [""] * (columns - len(cells[i:i + columns])) for i in range(0, len(cells), columns)]
        evidence = Table(grid, colWidths=[cell + gap] * (columns - 1) + [cell], hAlign="LEFT", style=[
            ("VALIGN", (0, 0), (-1, -1), "TOP"), ("LEFTPADDING", (0, 0), (-1, -1), 0),
            ("RIGHTPADDING", (0, 0), (-1, -1), 0), ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
        ])
        first_row = min(62 * mm, cell / (fw / fh) if fw and fh else cell * 9 / 16) + 30
        heading("Posture evidence", evidence, keep=first_row + 40)
        story.append(_p("Detected pose drawn from the stored landmarks. The original video is not retained by the server.", _small))
    else:
        heading("Posture evidence", _p("No scored frame is available to show.", _body_muted))

    # ---------------------------------------------------------- Camera view and reliability
    if reliability is not None:
        def percent(v):
            return "—" if v is None else f"{v * 100:.0f}%"

        heading("Camera view and reliability", _p(reliability.explanation, _body))
        story.append(Spacer(1, 8))
        story.append(table(None, [
            ["Measured frames filmed close to side-on", percent(reliability.side_on_fraction)],
            ["Sampled frames with the worker detected", percent(reliability.detection_fraction)],
        ], [0.75, 0.25]))
        story.append(Spacer(1, 10))
        if limited:
            story.append(Paragraph(
                f"<font color='#7A5C14'><b>{escape(reliability.title)}.</b></font> "
                "The reasons and what to do about them are given under Overall assessment.",
                _body,
            ))
        else:
            story.append(_p(
                "No camera-view limitation was detected by these checks. This is not a confidence rating; "
                "the limitations in the appendix still apply.",
                _body_muted,
            ))
    else:
        heading("Camera view and reliability", _p("No camera-view information is stored for this assessment.", _body_muted))

    # ---------------------------------------------------------- Appendix: technical details and limitations
    story.append(CondPageBreak(60 * mm))
    rows = []
    for method, r in (("RULA", content.rula), ("REBA", content.reba)):
        if r is None:
            rows.append([method, "—", "Not measured", "—", "—"])
            continue
        dist = ", ".join(f"{k.replace('_', ' ')} {v}" for k, v in r.risk_distribution.items() if v) or "—"
        rows.append([method, score(r), _risk_words(r.risk) if r.score is not None else "Not measured", str(r.frames_scored), dist])
    heading(
        content.technical_section,
        _p("Scores", _h2),
        Spacer(1, 4),
        table(["Method", "Peak score", "Risk level", "Frames scored", "Frames by risk band"], rows,
              [0.13, 0.15, 0.2, 0.16, 0.36], right_from=5),
        top=22,
    )
    story.append(Spacer(1, 4))
    story.append(_p(
        "Scores are the peak per-frame score reported by the VigilEx pipeline; a component's band is its score within its range.",
        _small,
    ))
    if content.component_tables:
        story.append(Spacer(1, 14))
        subheading(COMPONENT_HEADING[0].upper() + COMPONENT_HEADING[1:], _p(content.component_note, _body_muted))
        for t in content.component_tables:
            at = f"frame {t.frame_number}" + ("" if t.frame_time_sec is None else f" · {t.frame_time_sec:.1f} s")
            rows = [
                [
                    c.name,
                    deg(c.angle_deg) if c.measured else "Not observable",
                    f"{c.band} / {c.max_band}" if c.measured and c.band is not None else "—",
                ]
                for c in t.rows
            ]
            story.append(Spacer(1, 10))
            story.append(KeepTogether([
                Paragraph(f"<b>{escape(t.method)}</b> &nbsp;score {t.score} / {t.scale_max} · {escape(at)}", _td),
                Spacer(1, 3),
                table(["Component", "Angle", "Band"], rows, [0.5, 0.25, 0.25]),
            ]))
    story.append(Spacer(1, 14))
    version = _p(f"Methodology version: {content.methodology_version}", _small)
    # The camera-view limitation is printed in full on the first page, so the list does not repeat it.
    printed_limitations = [
        item for item in content.limitations
        if not (limited and reliability.title and item.startswith(f"{reliability.title}:"))
    ]
    for title, items in (("Methodology", content.methodology), ("Limitations", printed_limitations)):
        points = bullets(items)
        subheading(title, *points[:1])  # The heading stays with its first point; the rest flow on.
        if title == "Limitations" and len(points) > 1:
            # The version line never sits alone on a final page.
            story.extend(points[1:-1])
            story.append(KeepTogether([points[-1], Spacer(1, 10), version]))
        else:
            story.extend(points[1:])
        if title == "Methodology":
            story.append(Spacer(1, 12))  # Never after the last block: a trailing spacer can open a blank page.
    if len(printed_limitations) <= 1:
        story.append(version)

    footer = f"{name} · {content.analyzed_at:%d %b %Y}"
    doc.build(story, canvasmaker=_numbered_canvas(footer))


def generate_report(assessment: Assessment) -> tuple[ReportContent, Path]:
    content = build_report_content(assessment)
    target = report_path(assessment.id)
    tmp = target.with_suffix(".tmp")
    render_pdf(assessment, content, tmp)
    tmp.replace(target)  # Atomic: a reader never sees a half-written PDF.
    return content, target
