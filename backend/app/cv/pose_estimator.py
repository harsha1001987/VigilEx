from __future__ import annotations

from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
import torch
from ultralytics import YOLO


# VigilEx CV configuration
CV_DIR = Path(__file__).resolve().parent
DEFAULT_MODEL_PATH = CV_DIR / "models" / "yolo11n-pose.pt"

DEFAULT_MAX_PEOPLE = 5
MIN_LANDMARK_CONFIDENCE = 0.5
TRACKER_CONFIG = "bytetrack.yaml"

# COCO-17 indices
NOSE = 0
LEFT_EYE = 1
RIGHT_EYE = 2
LEFT_EAR = 3
RIGHT_EAR = 4
LEFT_SHOULDER = 5
RIGHT_SHOULDER = 6
LEFT_ELBOW = 7
RIGHT_ELBOW = 8
LEFT_WRIST = 9
RIGHT_WRIST = 10
LEFT_HIP = 11
RIGHT_HIP = 12
LEFT_KNEE = 13
RIGHT_KNEE = 14
LEFT_ANKLE = 15
RIGHT_ANKLE = 16

Landmark = Tuple[float, float, float]
Landmarks = List[Landmark]


class PoseEstimator:
    """YOLO11n-Pose + ByteTrack wrapper used by VigilEx."""

    def __init__(
        self,
        model_path: str | Path = DEFAULT_MODEL_PATH,
        max_people: int = DEFAULT_MAX_PEOPLE,
    ) -> None:
        self.model_path = Path(model_path)
        self.max_people = max_people

        if not self.model_path.exists():
            raise FileNotFoundError(
                f"YOLO pose model not found: {self.model_path}"
            )

        self.device = "cuda:0" if torch.cuda.is_available() else "cpu"
        self._model = self._load_model()
        self._warmup()

    def _load_model(self) -> YOLO:
        # PyTorch 2.6 changed torch.load defaults. The trusted local
        # YOLO checkpoint requires weights_only=False while loading.
        original_torch_load = torch.load

        def patched_torch_load(*args, **kwargs):
            kwargs["weights_only"] = False
            return original_torch_load(*args, **kwargs)

        torch.load = patched_torch_load
        try:
            return YOLO(str(self.model_path))
        finally:
            torch.load = original_torch_load

    def _warmup(self) -> None:
        dummy = np.zeros((320, 320, 3), dtype=np.uint8)
        self._model.predict(
            dummy,
            device=self.device,
            max_det=self.max_people,
            verbose=False,
        )

    def estimate(self, frame_bgr: np.ndarray) -> List[Landmarks]:
        """Detect people and return 17 COCO landmarks for each person."""
        results = self._model.predict(
            frame_bgr,
            device=self.device,
            max_det=self.max_people,
            verbose=False,
        )

        if not results:
            return []

        return _extract_landmarks(results[0])

    def estimate_tracked(
        self,
        frame_bgr: np.ndarray,
    ) -> List[Tuple[int, Landmarks]]:
        """Detect and track people with ByteTrack."""
        results = self._model.track(
            frame_bgr,
            device=self.device,
            max_det=self.max_people,
            persist=True,
            tracker=TRACKER_CONFIG,
            verbose=False,
        )

        if not results:
            return []

        result = results[0]

        if result.keypoints is None or result.boxes is None:
            return []

        track_ids = result.boxes.id
        if track_ids is None:
            return []

        people = _extract_landmarks(result)
        output: List[Tuple[int, Landmarks]] = []

        for index, person in enumerate(people):
            if index >= len(track_ids):
                break

            output.append((int(track_ids[index].item()), person))

        return output

    def close(self) -> None:
        """Release model/GPU resources when the processor is finished."""
        if hasattr(self, "_model"):
            del self._model

        if torch.cuda.is_available():
            torch.cuda.empty_cache()


def _extract_landmarks(result) -> List[Landmarks]:
    """Convert an Ultralytics result into plain Python tuples."""
    if result.keypoints is None or result.keypoints.xy is None:
        return []

    xy = result.keypoints.xy.detach().cpu().numpy()

    confidence = result.keypoints.conf
    if confidence is not None:
        conf = confidence.detach().cpu().numpy()
    else:
        conf = np.ones(xy.shape[:2], dtype=np.float32)

    people: List[Landmarks] = []

    for person_index in range(len(xy)):
        person: Landmarks = []

        for point_index in range(17):
            person.append(
                (
                    float(xy[person_index, point_index, 0]),
                    float(xy[person_index, point_index, 1]),
                    float(conf[person_index, point_index]),
                )
            )

        people.append(person)

    return people


def _valid(
    point: Optional[Landmark],
    threshold: float = MIN_LANDMARK_CONFIDENCE,
) -> bool:
    return point is not None and point[2] >= threshold


def _best_side(
    landmarks: Landmarks,
    left_index: int,
    right_index: int,
    threshold: float = MIN_LANDMARK_CONFIDENCE,
) -> Optional[Landmark]:
    left = landmarks[left_index]
    right = landmarks[right_index]

    left_ok = _valid(left, threshold)
    right_ok = _valid(right, threshold)

    if not left_ok and not right_ok:
        return None
    if left_ok and not right_ok:
        return left
    if right_ok and not left_ok:
        return right

    return left if left[2] >= right[2] else right


def landmarks_to_posture_dict(
    landmarks: Landmarks,
    confidence_threshold: float = MIN_LANDMARK_CONFIDENCE,
) -> Dict[str, Optional[Landmark]]:
    """
    Convert COCO-17 landmarks into the representation expected by posture.py.

    For side-dependent joints, the side with higher confidence is selected.
    """
    def valid(point: Landmark) -> Optional[Landmark]:
        return point if point[2] >= confidence_threshold else None

    shoulder = _best_side(
        landmarks, LEFT_SHOULDER, RIGHT_SHOULDER, confidence_threshold
    )
    elbow = _best_side(
        landmarks, LEFT_ELBOW, RIGHT_ELBOW, confidence_threshold
    )
    wrist = _best_side(
        landmarks, LEFT_WRIST, RIGHT_WRIST, confidence_threshold
    )
    hip = _best_side(
        landmarks, LEFT_HIP, RIGHT_HIP, confidence_threshold
    )
    knee = _best_side(
        landmarks, LEFT_KNEE, RIGHT_KNEE, confidence_threshold
    )
    ankle = _best_side(
        landmarks, LEFT_ANKLE, RIGHT_ANKLE, confidence_threshold
    )
    ear = _best_side(
        landmarks, LEFT_EAR, RIGHT_EAR, confidence_threshold
    )

    nose = valid(landmarks[NOSE])
    head = ear if ear is not None else nose

    # Keep the same simple point-based contract used by the posture engine.
    return {
        "head": head,
        "neck": head,
        "shoulder": shoulder,
        "elbow": elbow,
        "wrist": wrist,
        "trunk": shoulder,
        "hip": hip,
        "knee": knee,
        "ankle": ankle,
    }


def landmarks_to_render_points(
    landmarks: Landmarks,
    confidence_threshold: float = MIN_LANDMARK_CONFIDENCE,
) -> Dict[str, Optional[Tuple[float, float]]]:
    """Return all COCO-17 points as optional 2D coordinates."""
    names = [
        "nose",
        "left_eye",
        "right_eye",
        "left_ear",
        "right_ear",
        "left_shoulder",
        "right_shoulder",
        "left_elbow",
        "right_elbow",
        "left_wrist",
        "right_wrist",
        "left_hip",
        "right_hip",
        "left_knee",
        "right_knee",
        "left_ankle",
        "right_ankle",
    ]

    points: Dict[str, Optional[Tuple[float, float]]] = {}

    for index, name in enumerate(names):
        x, y, confidence = landmarks[index]
        points[name] = (x, y) if confidence >= confidence_threshold else None

    return points


def _distance(a: Landmark, b: Landmark) -> float:
    return float(np.hypot(a[0] - b[0], a[1] - b[1]))


def estimate_camera_yaw_deg(
    landmarks: Landmarks,
    confidence_threshold: float = MIN_LANDMARK_CONFIDENCE,
) -> float:
    """
    Coarse 2D camera-view gate.

    This is NOT a true 3D camera-pose estimator. If both shoulders and both
    hips cannot be observed, 90 degrees is returned so posture scoring can
    reject the frame.
    """
    ls = landmarks[LEFT_SHOULDER]
    rs = landmarks[RIGHT_SHOULDER]
    lh = landmarks[LEFT_HIP]
    rh = landmarks[RIGHT_HIP]

    if not all(
        _valid(point, confidence_threshold)
        for point in (ls, rs, lh, rh)
    ):
        return 90.0

    shoulder_width = _distance(ls, rs)

    shoulder_mid = (
        (ls[0] + rs[0]) / 2.0,
        (ls[1] + rs[1]) / 2.0,
        1.0,
    )
    hip_mid = (
        (lh[0] + rh[0]) / 2.0,
        (lh[1] + rh[1]) / 2.0,
        1.0,
    )

    torso_length = _distance(shoulder_mid, hip_mid)

    if torso_length <= 1e-6:
        return 90.0

    ratio = shoulder_width / torso_length

    # Same empirical idea as the old Ergoscan gate: use 2D shoulder width
    # relative to torso length only as a coarse view-quality signal.
    frontal_ratio = 0.5

    normalized = min(ratio / frontal_ratio, 1.0)
    yaw = float(np.degrees(np.arccos(normalized)))

    return max(0.0, min(90.0, yaw))
