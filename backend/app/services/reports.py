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

from reportlab.graphics.shapes import Circle, Drawing, Line, Rect
from reportlab.lib import colors
from reportlab.lib.enums import TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import (
    CondPageBreak,
    Flowable,
    KeepTogether,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

from app.core.config import get_settings
from app.models.assessment import Assessment
from app.schemas.analysis import MethodResult, RegionMeasurement, ReportContent, ReportFinding
from app.services.analysis_assessments import method_result, overall_risk, region_measurements, video_name

# ------------------------------------------------------------ wording rules

CONSISTENT_SHARE = 0.66
REPEATED_SHARE = 0.2
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

METHODOLOGY = [
    "VigilEx detects the worker's pose in sampled video frames (YOLO11n-Pose), follows the same worker "
    "through the video (ByteTrack) and measures joint angles in the side view.",
    "RULA and REBA are scored per sampled frame from those angles; this report gives the peak score of each "
    "and how the scored frames were distributed across risk bands.",
]

LIMITATIONS = [
    "Wrist posture, wrist twist, force or load, coupling and activity cannot be observed from the video "
    "and are scored at their lowest-risk values.",
    "Angles are measured from 2D landmarks in the side view; camera position affects them.",
    "This report describes observed posture. It does not assess injury, pain, fatigue or health, "
    "and findings should be reviewed by a qualified person.",
]


def _status(region: RegionMeasurement, overall: str | None) -> str:
    if region.peak_severity is None:
        return "not_measured"
    if region.peak_severity == "ok":
        return "low"
    if region.peak_severity == "moderate":
        return "attention"
    return "high" if overall in ("high", "very_high") else "elevated"


def _title(region: RegionMeasurement, status: str) -> str:
    strong, moderate, quiet = _TITLES[region.metric]
    return {"elevated": strong, "high": strong, "attention": moderate}.get(status, quiet)


def _detail(region: RegionMeasurement, status: str) -> str:
    if status == "not_measured":
        return "Not measured in any scored frame."
    if status == "low":
        return f"Stayed within the lowest scoring range in all {region.frames_measured} measured frames."
    share = region.frames_at_peak_severity / region.frames_measured if region.frames_measured else 0.0
    word = "consistently" if share >= CONSISTENT_SHARE else "repeatedly" if share >= REPEATED_SHARE else "briefly"
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
        findings=findings,
        measurements=regions,
        evidence_frames=len(evidence_frames(assessment, regions, rula, reba, overall)),
        methodology=METHODOLOGY,
        limitations=LIMITATIONS,
        methodology_version=assessment.methodology_version,
    )


# ------------------------------------------------------------ evidence

_REGION_ANGLE = {"trunk": "trunk_deg", "upper_arm": "upper_arm_deg", "knee_flexion": "knee_flexion_deg", "neck": "neck_deg"}


def _frame_score(frame: dict[str, Any], method: str) -> int | None:
    return (frame.get(method.lower()) or {}).get("score")


def evidence_frames(
    assessment: Assessment,
    regions: list[RegionMeasurement],
    rula: MethodResult | None,
    reba: MethodResult | None,
    overall: str | None,
) -> list[tuple[int, dict[str, Any], str]]:
    """Peak-score frame of the leading method, the key region's peak-angle frame, the other peak."""
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
                return i, f"Peak {method} {r.score:02d} / {r.scale_max:02d}"
        return None

    candidates = [peak(lead)]
    flagged = sorted(
        (r for r in regions if r.peak_severity in ("moderate", "high")),
        key=lambda r: (-(r.peak_severity == "high"), -(r.frames_at_peak_severity / max(r.frames_measured, 1))),
    )
    if flagged:
        key = flagged[0]
        angle_key = _REGION_ANGLE[key.metric]
        best = None
        for i, f in enumerate(keyframes):
            angle = ((f.get("rula") or {}).get("angles") or {}).get(angle_key)
            if angle is not None and (best is None or angle > best[1]):
                best = (i, angle)
        if best:
            candidates.append((best[0], f"Peak {key.label.lower()} {best[1]:.1f}°"))
    candidates.append(peak(other))

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

INK = colors.HexColor("#0A0A0A")
SECONDARY = colors.HexColor("#55554F")
MUTED = colors.HexColor("#8C8C85")
HAIRLINE = colors.HexColor("#E2E2DC")
SUNKEN = colors.HexColor("#EDEDE8")
GOLD = colors.HexColor("#C9A227")
GOLD_INK = colors.HexColor("#7A5F00")
RED = colors.HexColor("#C62828")

_ACCENT = {"low": colors.HexColor("#171717"), "moderate": GOLD, "high": RED, "very_high": colors.HexColor("#8E1B1B")}
_STATUS_COLOR = {"Low": MUTED, "Not measured": MUTED, "Attention": INK, "Elevated": GOLD_INK, "High": RED}

_label = ParagraphStyle("label", fontName="Helvetica-Bold", fontSize=7.5, leading=10, textColor=MUTED)
_label_ink = ParagraphStyle("label_ink", parent=_label, textColor=INK)
_body = ParagraphStyle("body", fontName="Helvetica", fontSize=9.5, leading=13.5, textColor=SECONDARY)
_body_ink = ParagraphStyle("body_ink", parent=_body, textColor=INK)
_lead = ParagraphStyle("lead", fontName="Helvetica", fontSize=12.5, leading=17, textColor=INK)
_title = ParagraphStyle("title", fontName="Helvetica-Bold", fontSize=24, leading=28, textColor=INK)
_section = ParagraphStyle("section", fontName="Helvetica", fontSize=26, leading=28, textColor=INK)
_small = ParagraphStyle("small", fontName="Helvetica", fontSize=8, leading=11, textColor=MUTED)
_right = ParagraphStyle("right", parent=_body_ink, alignment=TA_RIGHT)


class _Rule(Flowable):
    def __init__(self, width: float, weight: float = 0.5, color=HAIRLINE, accent=None):
        super().__init__()
        self.width, self.weight, self.color, self.accent = width, weight, color, accent

    def wrap(self, *_):
        return self.width, self.weight + (3 if self.accent else 0)

    def draw(self):
        self.canv.setFillColor(self.color)
        self.canv.rect(0, 0, self.width, self.weight, stroke=0, fill=1)
        if self.accent is not None:
            self.canv.setFillColor(self.accent)
            self.canv.rect(0, self.weight, 40, 3, stroke=0, fill=1)


_COCO_EDGES = [
    (0, 1), (0, 2), (1, 3), (2, 4), (5, 6), (5, 7), (7, 9), (6, 8), (8, 10),
    (5, 11), (6, 12), (11, 12), (11, 13), (13, 15), (12, 14), (14, 16),
]


def _pose(frame: dict[str, Any], width: float, fw: int, fh: int) -> Drawing:
    """The detected pose, drawn from the stored landmarks (the video itself is not kept)."""
    aspect = fw / fh if fw and fh else 16 / 9
    height = width / aspect
    d = Drawing(width, height)
    d.add(Rect(0, 0, width, height, fillColor=SUNKEN, strokeColor=None))
    pts = frame.get("landmarks") or []
    if len(pts) >= 17 and fw and fh:
        def at(i):
            p = pts[i]
            if (p.get("confidence") or 0) < 0.5:
                return None
            return p["x"] / fw * width, height - p["y"] / fh * height

        for a, b in _COCO_EDGES:
            p1, p2 = at(a), at(b)
            if p1 and p2:
                d.add(Line(p1[0], p1[1], p2[0], p2[1], strokeColor=INK, strokeWidth=1.6, strokeLineCap=1))
        for i in range(len(pts)):
            p = at(i)
            if p:
                d.add(Circle(p[0], p[1], 1.8, fillColor=INK, strokeColor=None))
    return d


def render_pdf(assessment: Assessment, content: ReportContent, target: Path) -> None:
    width = A4[0] - 36 * mm
    doc = SimpleDocTemplate(
        str(target),
        pagesize=A4,
        leftMargin=18 * mm,
        rightMargin=18 * mm,
        topMargin=22 * mm,
        bottomMargin=20 * mm,
        title="VigilEx ergonomic assessment",
        author="VigilEx",
        invariant=1,  # Same assessment, same bytes.
    )

    def frame_page(canvas, doc_):
        canvas.saveState()
        canvas.setFont("Helvetica-Bold", 8)
        canvas.setFillColor(INK)
        canvas.drawString(18 * mm, A4[1] - 13 * mm, "V I G I L E X")
        canvas.setFont("Helvetica-Bold", 7)
        canvas.setFillColor(MUTED)
        canvas.drawRightString(A4[0] - 18 * mm, A4[1] - 13 * mm, "ERGONOMIC ASSESSMENT")
        canvas.setStrokeColor(HAIRLINE)
        canvas.line(18 * mm, 14 * mm, A4[0] - 18 * mm, 14 * mm)
        canvas.setFont("Helvetica", 7.5)
        canvas.drawString(18 * mm, 10 * mm, f"VigilEx · Ergonomic assessment · {content.video_name or content.assessment_id}")
        canvas.drawRightString(A4[0] - 18 * mm, 10 * mm, f"{doc_.page:02d}")
        canvas.restoreState()

    story: list[Any] = []

    def section(number: int, name: str):
        story.append(CondPageBreak(60 * mm))
        story.append(_Rule(width, 1, INK))
        story.append(Spacer(1, 6))
        story.append(Table([[Paragraph(f"{number:02d}", _section), Paragraph(name.upper(), _label_ink)]],
                           colWidths=[22 * mm, width - 22 * mm],
                           style=[("VALIGN", (0, 0), (-1, -1), "MIDDLE"), ("LEFTPADDING", (0, 0), (-1, -1), 0)]))
        story.append(Spacer(1, 10))

    def table(rows, widths, header=True, status_col=None):
        t = Table(rows, colWidths=[w * width for w in widths])
        style = [
            ("FONT", (0, 0), (-1, -1), "Helvetica", 9),
            ("TEXTCOLOR", (0, 0), (-1, -1), INK),
            ("ALIGN", (1, 0), (-1, -1), "RIGHT"),
            ("LEFTPADDING", (0, 0), (-1, -1), 0),
            ("RIGHTPADDING", (0, 0), (-1, -1), 0),
            ("TOPPADDING", (0, 0), (-1, -1), 5),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ("LINEBELOW", (0, 1), (-1, -1), 0.5, HAIRLINE),
        ]
        if header:
            style += [
                ("FONT", (0, 0), (-1, 0), "Helvetica-Bold", 7),
                ("TEXTCOLOR", (0, 0), (-1, 0), MUTED),
                ("LINEBELOW", (0, 0), (-1, 0), 0.8, INK),
            ]
        if status_col is not None:
            for i, row in enumerate(rows[1:], start=1):
                style.append(("TEXTCOLOR", (status_col, i), (status_col, i), _STATUS_COLOR.get(row[status_col], INK)))
        t.setStyle(TableStyle(style))
        return t

    def deg(v):
        return "—" if v is None else f"{v:.1f}°"

    # Title block
    story.append(Paragraph("Ergonomic assessment", _title))
    story.append(Spacer(1, 8))
    meta = [
        ["VIDEO", content.video_name or "—"],
        ["ANALYZED", f"{content.analyzed_at:%d %b %Y · %H:%M} UTC"],
        ["DURATION", "—" if content.duration_sec is None else f"{content.duration_sec:.1f} s"],
        ["ASSESSMENT", str(content.assessment_id)],
    ]
    story.append(Table(meta, colWidths=[28 * mm, width - 28 * mm], style=[
        ("FONT", (0, 0), (0, -1), "Helvetica-Bold", 7), ("TEXTCOLOR", (0, 0), (0, -1), MUTED),
        ("FONT", (1, 0), (1, -1), "Helvetica", 9), ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 1.5), ("BOTTOMPADDING", (0, 0), (-1, -1), 1.5),
    ]))
    story.append(Spacer(1, 18))

    # 01 Executive assessment
    section(1, "Executive assessment")
    story.append(Paragraph("ERGONOMIC RISK", _label))
    risk_word = (content.overall_risk or "not measured").replace("_", " ").upper()
    risk_color = RED if content.overall_risk in ("high", "very_high") else INK
    story.append(Paragraph(risk_word, ParagraphStyle("risk", fontName="Helvetica-Bold", fontSize=38, leading=44, textColor=risk_color)))
    story.append(Spacer(1, 4))
    story.append(_Rule(width, 0.8, INK, accent=_ACCENT.get(content.overall_risk, HAIRLINE)))
    rows = []
    for name, r in (("RULA", content.rula), ("REBA", content.reba)):
        if r is None:
            rows.append([name, "—", "Not measured"])
        else:
            rows.append([name, f"{r.score:02d} / {r.scale_max:02d}", (r.risk or "—").replace("_", " ").capitalize()])
    story.append(table(rows, [0.2, 0.3, 0.5], header=False))
    if content.observation:
        story.append(Spacer(1, 10))
        story.append(Paragraph("PRIMARY OBSERVATION", _label))
        story.append(Spacer(1, 3))
        story.append(Paragraph(content.observation, _lead))
    story.append(Spacer(1, 18))

    # 02 Key findings
    section(2, "Key ergonomic findings")
    if content.findings:
        rows = [["", "FINDING", "STATUS"]]
        for i, f in enumerate(content.findings, start=1):
            rows.append([f"{i:02d}", Paragraph(f"<b>{f.title}</b><br/>{f.detail}", _body_ink), f.status])
        story.append(table(rows, [0.08, 0.72, 0.2], status_col=2))
    else:
        story.append(Paragraph("No body region could be measured.", _body))
    story.append(Spacer(1, 18))

    # 03 RULA / REBA results
    section(3, "RULA / REBA results")
    for r in (content.rula, content.reba):
        if r is None:
            continue
        name = "RULA" if r.scale_max == 7 else "REBA"
        dist = ", ".join(f"{k.replace('_', ' ')} {v}" for k, v in r.risk_distribution.items() if v) or "—"
        story.append(KeepTogether([
            Paragraph(f"<b>{name}</b> &nbsp; {r.score:02d} / {r.scale_max:02d} · {(r.risk or '—').replace('_', ' ')} risk", _body_ink),
            Spacer(1, 3),
            Paragraph(f"{r.frames_scored} frames scored. Frames by risk band: {dist}.", _body),
            Spacer(1, 10),
        ]))
    story.append(Paragraph("Scores are the peak per-frame score reported by the VigilEx pipeline.", _small))
    story.append(Spacer(1, 18))

    # 04 Measurements
    section(4, "Biomechanical measurements")
    overall = content.overall_risk
    rows = [["REGION", "AVG", "MAX", "STATUS"]]
    for m in content.measurements:
        rows.append([m.label, deg(m.average_deg), deg(m.maximum_deg), _STATUS_LABEL[_status(m, overall)]])
    story.append(table(rows, [0.4, 0.18, 0.18, 0.24], status_col=3))
    story.append(Spacer(1, 4))
    story.append(Paragraph("Joint angles in degrees across the frames where each region was measured.", _small))
    story.append(Spacer(1, 18))

    # 05 Posture evidence
    frames = evidence_frames(assessment, content.measurements, content.rula, content.reba, overall)
    if frames:
        section(5, "Posture evidence")
        video = (assessment.capture_metadata or {}).get("video") or {}
        fw, fh = int(video.get("width") or 0), int(video.get("height") or 0)
        cell = (width - 8 * mm) / 2
        cells = []
        for index, frame, reason in frames:
            cells.append([
                _pose(frame, cell, fw, fh),
                Spacer(1, 3),
                Paragraph(f"FRAME {index + 1:02d} · {frame.get('t', 0):.1f} s", _label),
                Paragraph(reason.upper(), _label_ink),
            ])
        grid = [cells[i:i + 2] + [""] * (2 - len(cells[i:i + 2])) for i in range(0, len(cells), 2)]
        story.append(Table(grid, colWidths=[cell + 4 * mm, cell + 4 * mm], style=[
            ("VALIGN", (0, 0), (-1, -1), "TOP"), ("LEFTPADDING", (0, 0), (-1, -1), 0),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
        ]))
        story.append(Paragraph("Detected pose from the stored landmarks. The original video is not retained by the server.", _small))
        story.append(Spacer(1, 18))

    # 06 Methodology and limitations
    section(6, "Methodology and limitations")
    for text in content.methodology:
        story.append(Paragraph(text, _body))
        story.append(Spacer(1, 4))
    story.append(Spacer(1, 6))
    story.append(Paragraph("LIMITATIONS", _label))
    story.append(Spacer(1, 3))
    for text in content.limitations:
        story.append(Paragraph(text, _body))
        story.append(Spacer(1, 4))
    story.append(Spacer(1, 6))
    story.append(Paragraph(f"Methodology version: {content.methodology_version}", _small))

    doc.build(story, onFirstPage=frame_page, onLaterPages=frame_page)


def generate_report(assessment: Assessment) -> tuple[ReportContent, Path]:
    content = build_report_content(assessment)
    target = report_path(assessment.id)
    tmp = target.with_suffix(".tmp")
    render_pdf(assessment, content, tmp)
    tmp.replace(target)  # Atomic: a reader never sees a half-written PDF.
    return content, target
