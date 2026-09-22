"""VigilEx video -> YOLO pose -> ByteTrack -> RULA/REBA processor.

This replaces the old Ergoscan batch processor. It deliberately contains no
PPE detection, dashboard rendering, WebSocket code, frontend annotation schema,
or FFmpeg output.
"""

from __future__ import annotations

import os
import time
from pathlib import Path
from typing import Callable, Dict, List, Optional, Tuple

import cv2

from .pose_estimator import (
    PoseEstimator,
    estimate_camera_yaw_deg,
    landmarks_to_posture_dict,
)
from .posture import assess_posture


CV_DIR = Path(__file__).resolve().parent
DEFAULT_MODEL_PATH = CV_DIR / "models" / "yolo11n-pose.pt"

DEFAULT_MAX_PEOPLE = 5
DEFAULT_SAMPLE_HZ = 5.0
MAX_FRAME_DIM = 1280
MAX_CAMERA_YAW_DEG = 45.0
MIN_LANDMARK_CONFIDENCE = 0.5
MIN_VISIBLE_KEYPOINTS = 5

METHODOLOGY_VERSION = "vigilex-rula-reba-v1"

ProgressFn = Callable[[float, str, Dict], None]


def _visible_count(landmarks) -> int:
    return sum(
        1 for _, _, confidence in landmarks
        if confidence >= MIN_LANDMARK_CONFIDENCE
    )


def _empty_assessment() -> Dict:
    return {
        "rula": {
            "score": None,
            "risk": "unknown",
            "angles": {},
            "components": {},
        },
        "reba": {
            "score": None,
            "risk": "unknown",
            "angles": {},
            "components": {},
        },
        "label": "unknown",
    }


def _select_worker(
    tracked: List[Tuple[int, list]],
    current_track_id: Optional[int],
) -> Optional[Tuple[int, list]]:
    """Keep the existing ByteTrack worker; otherwise choose the best detection."""
    if not tracked:
        return None

    if current_track_id is not None:
        for track_id, landmarks in tracked:
            if track_id == current_track_id:
                return track_id, landmarks

    candidates = [
        item for item in tracked
        if _visible_count(item[1]) >= MIN_VISIBLE_KEYPOINTS
    ]

    if not candidates:
        return None

    return max(candidates, key=lambda item: _visible_count(item[1]))


def _average(values: List[float]) -> Optional[float]:
    return sum(values) / len(values) if values else None


def _risk_distribution(keyframes: List[Dict], method: str) -> Dict[str, int]:
    counts: Dict[str, int] = {}

    for frame in keyframes:
        block = frame.get(method) or {}
        risk = block.get("risk")

        if risk and risk != "unknown":
            counts[risk] = counts.get(risk, 0) + 1

    return counts


def process_video(
    video_path: str,
    on_progress: Optional[ProgressFn] = None,
    stride_hz: float = DEFAULT_SAMPLE_HZ,
    model_path: Optional[str] = None,
    max_people: int = DEFAULT_MAX_PEOPLE,
) -> Dict:
    """Process one VigilEx assessment video and return analysis data."""

    if not os.path.exists(video_path):
        raise FileNotFoundError(video_path)

    if stride_hz <= 0:
        raise ValueError("stride_hz must be greater than 0")

    cap = cv2.VideoCapture(video_path)

    if not cap.isOpened():
        raise RuntimeError(f"Cannot open video: {video_path}")

    fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    source_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    source_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)

    duration_sec = (
        total_frames / fps
        if fps > 0 and total_frames > 0
        else 0.0
    )

    width = source_width
    height = source_height

    if (
        source_width
        and source_height
        and max(source_width, source_height) > MAX_FRAME_DIM
    ):
        scale = MAX_FRAME_DIM / max(source_width, source_height)
        width = max(1, round(source_width * scale))
        height = max(1, round(source_height * scale))

    model = str(model_path or DEFAULT_MODEL_PATH)

    if on_progress:
        on_progress(
            0.0,
            "loading_models",
            {
                "fps": fps,
                "duration_sec": duration_sec,
                "model": model,
            },
        )

    estimator = PoseEstimator(
        model_path=model,
        max_people=max_people,
    )

    stride = max(1, round(fps / stride_hz))

    keyframes: List[Dict] = []

    primary_track_id: Optional[int] = None

    sampled_frames = 0
    detected_frames = 0
    scoreable_frames = 0
    frame_index = 0

    first_seen_sec: Optional[float] = None
    last_seen_sec: Optional[float] = None

    rula_scores: List[int] = []
    reba_scores: List[int] = []

    trunk_angles: List[float] = []
    upper_arm_angles: List[float] = []
    knee_angles: List[float] = []
    neck_angles: List[float] = []

    started = time.time()

    try:
        while True:
            ok, frame = cap.read()

            if not ok:
                break

            if (width, height) != (source_width, source_height):
                frame = cv2.resize(
                    frame,
                    (width, height),
                    interpolation=cv2.INTER_AREA,
                )

            if frame_index % stride != 0:
                frame_index += 1
                continue

            sampled_frames += 1
            t_sec = frame_index / fps if fps > 0 else 0.0

            tracked = estimator.estimate_tracked(frame)

            selected = _select_worker(
                tracked,
                primary_track_id,
            )

            if selected is None:
                frame_index += 1
                continue

            track_id, landmarks = selected

            if primary_track_id is None:
                primary_track_id = track_id
                first_seen_sec = t_sec

            # If ByteTrack loses the original person and returns another ID,
            # record the new ID explicitly rather than hiding the switch.
            if track_id != primary_track_id:
                primary_track_id = track_id

            detected_frames += 1
            last_seen_sec = t_sec

            posture_dict = landmarks_to_posture_dict(landmarks)

            camera_yaw_deg = estimate_camera_yaw_deg(landmarks)

            side_view_ok = camera_yaw_deg <= MAX_CAMERA_YAW_DEG

            if side_view_ok:
                assessment = assess_posture(posture_dict)
                scoreable_frames += 1
            else:
                assessment = _empty_assessment()

            rula = assessment["rula"]
            reba = assessment["reba"]
            angles = reba.get("angles") or {}

            if rula.get("score") is not None:
                rula_scores.append(int(rula["score"]))

            if reba.get("score") is not None:
                reba_scores.append(int(reba["score"]))

            if angles.get("trunk_deg") is not None:
                trunk_angles.append(float(angles["trunk_deg"]))

            if angles.get("upper_arm_deg") is not None:
                upper_arm_angles.append(float(angles["upper_arm_deg"]))

            if angles.get("knee_deg") is not None:
                knee_angles.append(float(angles["knee_deg"]))

            if angles.get("neck_deg") is not None:
                neck_angles.append(float(angles["neck_deg"]))

            keyframes.append(
                {
                    "t": round(t_sec, 3),
                    "track_id": int(track_id),
                    "camera_yaw_deg": round(float(camera_yaw_deg), 2),
                    "side_view_ok": bool(side_view_ok),
                    "landmarks": [
                        {
                            "x": float(x),
                            "y": float(y),
                            "confidence": float(confidence),
                        }
                        for x, y, confidence in landmarks
                    ],
                    "rula": rula,
                    "reba": reba,
                }
            )

            if (
                on_progress
                and sampled_frames % 5 == 0
            ):
                fraction = (
                    frame_index / max(1, total_frames)
                    if total_frames
                    else 0.0
                )

                elapsed = time.time() - started
                eta = (
                    (elapsed / fraction) * (1.0 - fraction)
                    if fraction > 0
                    else 0.0
                )

                on_progress(
                    min(1.0, fraction),
                    "processing",
                    {
                        "frames_total": total_frames,
                        "frames_done": frame_index,
                        "samples_processed": sampled_frames,
                        "detected_frames": detected_frames,
                        "scoreable_frames": scoreable_frames,
                        "eta_sec": eta,
                    },
                )

            frame_index += 1

    finally:
        cap.release()
        estimator.close()

    detection_fraction = (
        detected_frames / sampled_frames
        if sampled_frames
        else 0.0
    )

    scoreable_fraction = (
        scoreable_frames / detected_frames
        if detected_frames
        else 0.0
    )

    result = {
        "methodology_version": METHODOLOGY_VERSION,

        "video": {
            "duration_sec": round(duration_sec, 3),
            "fps": fps,
            "width": width,
            "height": height,
            "frames_total": total_frames,
            "frames_sampled": sampled_frames,
            "sample_hz": stride_hz,
        },

        "worker": {
            "track_id": primary_track_id,
            "first_seen_sec": first_seen_sec,
            "last_seen_sec": last_seen_sec,
            "detected_frames": detected_frames,
        },

        "quality": {
            "detection_fraction": round(detection_fraction, 4),
            "scoreable_fraction": round(scoreable_fraction, 4),
            "camera_view_ok": scoreable_frames > 0,
            "max_camera_yaw_deg": MAX_CAMERA_YAW_DEG,
        },

        "derived_angles": {
            "trunk_deg_avg": _average(trunk_angles),
            "trunk_deg_max": max(trunk_angles) if trunk_angles else None,
            "upper_arm_deg_avg": _average(upper_arm_angles),
            "upper_arm_deg_max": (
                max(upper_arm_angles)
                if upper_arm_angles
                else None
            ),
            "knee_deg_avg": _average(knee_angles),
            "knee_deg_max": max(knee_angles) if knee_angles else None,
            "neck_deg_avg": _average(neck_angles),
            "neck_deg_max": max(neck_angles) if neck_angles else None,
        },

        "rula": {
            "max_score": max(rula_scores) if rula_scores else None,
            "frames_scored": len(rula_scores),
            "risk_distribution": _risk_distribution(
                keyframes,
                "rula",
            ),
        },

        "reba": {
            "max_score": max(reba_scores) if reba_scores else None,
            "frames_scored": len(reba_scores),
            "risk_distribution": _risk_distribution(
                keyframes,
                "reba",
            ),
        },

        "keyframes": keyframes,

        "meta": {
            "pose_model": Path(model).name,
            "processor": "VigilExBatchProcessor",
        },
    }

    if on_progress:
        on_progress(
            1.0,
            "complete",
            {
                "frames_total": total_frames,
                "samples_processed": sampled_frames,
                "detected_frames": detected_frames,
                "scoreable_frames": scoreable_frames,
            },
        )

    return result


def process_image(
    image_path: str,
    model_path: Optional[str] = None,
    max_people: int = DEFAULT_MAX_PEOPLE,
) -> Dict:
    """Process one image for quick/manual CV inspection."""

    if not os.path.exists(image_path):
        raise FileNotFoundError(image_path)

    frame = cv2.imread(image_path)

    if frame is None:
        raise RuntimeError(f"Cannot read image: {image_path}")

    height, width = frame.shape[:2]

    if max(width, height) > MAX_FRAME_DIM:
        scale = MAX_FRAME_DIM / max(width, height)
        width = max(1, round(width * scale))
        height = max(1, round(height * scale))
        frame = cv2.resize(
            frame,
            (width, height),
            interpolation=cv2.INTER_AREA,
        )

    model = str(model_path or DEFAULT_MODEL_PATH)

    estimator = PoseEstimator(
        model_path=model,
        max_people=max_people,
    )

    try:
        people = estimator.estimate(frame)

        if not people:
            return {
                "methodology_version": METHODOLOGY_VERSION,
                "worker": None,
                "quality": {"worker_detected": False},
                "rula": None,
                "reba": None,
                "derived_angles": {},
                "keyframes": [],
            }

        landmarks = max(
            people,
            key=_visible_count,
        )

        camera_yaw_deg = estimate_camera_yaw_deg(landmarks)
        side_view_ok = camera_yaw_deg <= MAX_CAMERA_YAW_DEG

        if side_view_ok:
            assessment = assess_posture(
                landmarks_to_posture_dict(landmarks)
            )
        else:
            assessment = _empty_assessment()

        return {
            "methodology_version": METHODOLOGY_VERSION,

            "video": {
                "duration_sec": 0.0,
                "width": width,
                "height": height,
                "frames_total": 1,
                "frames_sampled": 1,
            },

            "worker": {
                "track_id": None,
                "detected_frames": 1,
            },

            "quality": {
                "worker_detected": True,
                "camera_view_ok": side_view_ok,
                "camera_yaw_deg": round(
                    float(camera_yaw_deg),
                    2,
                ),
            },

            "derived_angles": assessment["reba"].get(
                "angles",
                {},
            ),

            "rula": assessment["rula"],
            "reba": assessment["reba"],

            "keyframes": [
                {
                    "t": 0.0,
                    "track_id": None,
                    "camera_yaw_deg": round(
                        float(camera_yaw_deg),
                        2,
                    ),
                    "side_view_ok": side_view_ok,
                    "landmarks": [
                        {
                            "x": float(x),
                            "y": float(y),
                            "confidence": float(confidence),
                        }
                        for x, y, confidence in landmarks
                    ],
                    "rula": assessment["rula"],
                    "reba": assessment["reba"],
                }
            ],

            "meta": {
                "pose_model": Path(model).name,
                "processor": "VigilExBatchProcessor",
                "input_type": "image",
            },
        }

    finally:
        estimator.close()
