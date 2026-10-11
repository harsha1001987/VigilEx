"""
Extrive intervention products: qualifying finding -> product to evaluate.

    interventions (from the intervention engine, most important first)
      -> each product whose trigger matches a qualifying intervention
      -> ProductRecommendation built from approved product content
      -> API (assessment summary, report content), PDF and app

A product is only ever suggested for evaluation, and only when an
intervention the engine already produced matches the product's trigger: no
product is added for an assessment without that finding. Product text comes
only from app/content/extrive_products.json, which cites its Extrive
sources; a missing field is reported as a content gap and the wording falls
back to restrained text, so reports never fail or invent product details.
"""

from __future__ import annotations

import json
import logging
from functools import lru_cache
from pathlib import Path
from typing import Any

from app.schemas.analysis import BackPosture, Intervention, ProductRecommendation
from app.services.interventions import frequency_word

log = logging.getLogger("vigilex.products")

CATALOGUE_PATH = Path(__file__).resolve().parent.parent / "content" / "extrive_products.json"

# Fields a product needs before its description is shown as verified.
REQUIRED_CONTENT = ("intended_use", "operating_principle", "task_types", "suitability")
_FIELD_LABEL = {
    "intended_use": "intended use",
    "operating_principle": "operating principle",
    "task_types": "relevant task types",
    "suitability": "fit and suitability guidance",
}

# Task-level changes come first; a product complements them, never replaces them.
COMPLEMENTARY_ACTIONS = {
    "back": [
        "Adjust the workstation and work height where practical.",
        "Bring tools and materials closer to reduce unnecessary reaching and bending.",
        "Avoid prolonged or repeated awkward postures where possible.",
    ],
}

QUALIFICATION = (
    "This video assessment observed posture only. It does not establish that this worker needs {name} or that "
    "it will suit them, and {name} does not replace correcting the work design. Have a qualified person assess "
    "fit and task suitability before introducing it."
)
EFFICIENCY_NOTE = "Evaluate whether {name} improves task comfort and work efficiency in your operating environment."
PENDING_NOTE = "Approved {name} product information is pending from Extrive, so no product description is shown."


@lru_cache(maxsize=4)
def _load(path: str) -> dict[str, dict[str, Any]]:
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        products = data.get("products") or {}
        if not isinstance(products, dict):
            raise ValueError("'products' is not an object")
        return products
    except (OSError, ValueError) as e:
        # Product content is optional: the rest of the report must still work.
        log.warning("Extrive product content unavailable (%s): %s", path, e)
        return {}


def catalogue(path: Path | None = None) -> dict[str, dict[str, Any]]:
    return _load(str(path or CATALOGUE_PATH))


def _text(value: Any) -> str | None:
    return value.strip() if isinstance(value, str) and value.strip() else None


def _texts(value: Any) -> list[str]:
    return [v.strip() for v in value if isinstance(v, str) and v.strip()] if isinstance(value, list) else []


def _qualifies(product: dict[str, Any], intervention: Intervention, back: BackPosture | None) -> bool:
    trigger = product.get("trigger") or {}
    if trigger.get("area") != intervention.area:
        return False
    direction = back.direction if back else None
    return direction not in (trigger.get("excluded_directions") or [])


def _rationale(name: str, intervention: Intervention, back: BackPosture | None) -> str:
    evidence = intervention.evidence
    # consistently / repeatedly / briefly -> consistent / repeated / brief: it qualifies the posture here.
    word = frequency_word(evidence.frames_flagged, evidence.frames_measured).removesuffix("ly")
    if intervention.area == "back":
        observed = "forward trunk bending" if back and back.direction == "forward" else "trunk inclination away from upright"
        return (
            f"The assessment observed {word} {observed}, beyond the upright range in {evidence.frames_flagged} of "
            f"{evidence.frames_measured} measured frames. Alongside improving task height, reach and work positioning, "
            f"consider evaluating Extrive's {name} support solution for tasks involving repeated or sustained bending. "
            "Review whether its intended support, fit and operating requirements match this task before introducing it."
        )
    return (
        f"The assessment flagged this region in {evidence.frames_flagged} of {evidence.frames_measured} measured frames. "
        f"Alongside the task changes above, consider evaluating Extrive's {name} for this task."
    )


def build_recommendation(
    product_id: str,
    product: dict[str, Any],
    intervention: Intervention,
    back: BackPosture | None,
    primary: bool,
) -> ProductRecommendation:
    name = _text(product.get("name")) or product_id
    content = {
        "intended_use": _text(product.get("intended_use")),
        "operating_principle": _text(product.get("operating_principle")),
        "task_types": _texts(product.get("task_types")),
        "suitability": _texts(product.get("suitability")),
    }
    gaps = [_FIELD_LABEL[f] for f in REQUIRED_CONTENT if not content[f]]
    suitability = content["suitability"] or [
        f"Have a qualified person assess {name} fit, task suitability, comfort and worker acceptance before use."
    ]
    evidence_note = _text(product.get("evidence_note")) or EFFICIENCY_NOTE.format(name=name)
    if gaps:
        evidence_note = f"{PENDING_NOTE.format(name=name)} {evidence_note}"
    return ProductRecommendation(
        product_id=product_id,
        name=name,
        maker=_text(product.get("maker")) or "Extrive Innovations",
        category=_text(product.get("category")),
        linked_area=intervention.area,
        role="primary" if primary else "supporting",
        headline=f"Evaluate {name} for this task",
        rationale=_rationale(name, intervention, back),
        intended_use=content["intended_use"],
        operating_principle=content["operating_principle"],
        task_types=content["task_types"],
        suitability=suitability,
        complementary_actions=COMPLEMENTARY_ACTIONS.get(intervention.area, [])
        + [f"Evaluate {name} fit, task suitability, comfort and worker acceptance before use."],
        evidence_note=evidence_note,
        qualification=QUALIFICATION.format(name=name),
        sources=_texts(product.get("sources")),
        content_status="incomplete" if gaps else "verified",
        content_gaps=gaps,
    )


def product_recommendations(
    interventions: list[Intervention],
    back: BackPosture | None,
    products: dict[str, dict[str, Any]] | None = None,
) -> list[ProductRecommendation]:
    """Products to evaluate, each linked to a qualifying intervention; the priority action's first."""
    products = catalogue() if products is None else products
    out = []
    for index, intervention in enumerate(interventions):
        for product_id, product in products.items():
            if isinstance(product, dict) and _qualifies(product, intervention, back):
                out.append(build_recommendation(product_id, product, intervention, back, primary=index == 0))
    return out
