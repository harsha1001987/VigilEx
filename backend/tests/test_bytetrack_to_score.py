from pathlib import Path

import cv2

from backend.app.cv.pose_estimator import (
    PoseEstimator,
    landmarks_to_posture_dict,
)
from backend.app.cv.posture import assess_posture
from backend.app.cv.track_state import TrackStateManager
from backend.app.cv.worker_selector import PrimaryWorkerSelector


VIDEO_NAME = "19832490-hd_1920_1080_25fps (1).mp4"

# Set to a specific track ID to force that track as the primary worker.
# Leave as None to let PrimaryWorkerSelector choose automatically.
PRIMARY_TRACK_ID_OVERRIDE = None


def main():
    print("=" * 60)
    print("VIGILEX VIDEO → BYTETRACK → RULA/REBA TEST")
    print("=" * 60)

    # ---------------------------------------------------------
    # Locate video.
    # ---------------------------------------------------------
    test_directory = Path(__file__).resolve().parent
    video_path = test_directory / VIDEO_NAME

    print()
    print(f"Video path: {video_path}")

    if not video_path.exists():
        print()
        print("ERROR: Video file not found.")
        print(f"Expected file:")
        print(video_path)
        return

    print("Video file found.")

    # ---------------------------------------------------------
    # Load existing VigilEx pose estimator.
    # ---------------------------------------------------------
    estimator = PoseEstimator()
    track_manager = TrackStateManager()

    # ---------------------------------------------------------
    # Open video.
    # ---------------------------------------------------------
    video = cv2.VideoCapture(str(video_path))

    if not video.isOpened():
        print("ERROR: Could not open video.")
        return

    fps = video.get(cv2.CAP_PROP_FPS)
    frame_count = int(video.get(cv2.CAP_PROP_FRAME_COUNT))
    width = int(video.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(video.get(cv2.CAP_PROP_FRAME_HEIGHT))

    print()
    print(f"Resolution : {width} x {height}")
    print(f"FPS        : {fps:.2f}")
    print(f"Frame count: {frame_count}")

    print()
    print("Starting processing...")
    print("Press Q to stop.")
    print()

    frame_number = 0

    # ---------------------------------------------------------
    # Counters.
    # ---------------------------------------------------------
    total_tracked_detections = 0
    total_rula_scores = 0
    total_reba_scores = 0

    try:
        while True:
            success, frame = video.read()

            if not success:
                print()
                print("End of video reached.")
                break

            frame_number += 1

            # -----------------------------------------------------
            # YOLO11n-Pose + ByteTrack
            # -----------------------------------------------------
            tracked_people = estimator.estimate_tracked(frame)

            # -----------------------------------------------------
            # Process every tracked person.
            # -----------------------------------------------------
            for track_id, landmarks in tracked_people:

                total_tracked_detections += 1

                # -------------------------------------------------
                # Convert COCO-17 landmarks to posture format.
                # -------------------------------------------------
                posture_landmarks = landmarks_to_posture_dict(
                    landmarks
                )

                # -------------------------------------------------
                # Calculate RULA / REBA.
                # -------------------------------------------------
                result = assess_posture(
                    posture_landmarks
                )

                # -------------------------------------------------
                # IMPORTANT:
                #
                # assess_posture() returns:
                #
                # result["rula"]["score"]
                # result["rula"]["risk"]
                #
                # result["reba"]["score"]
                # result["reba"]["risk"]
                #
                # NOT:
                #
                # result["rula_score"]
                # result["reba_score"]
                # -------------------------------------------------

                rula = result.get("rula", {})
                reba = result.get("reba", {})

                rula_score = rula.get("score")
                rula_risk = rula.get("risk")

                reba_score = reba.get("score")
                reba_risk = reba.get("risk")

                if rula_score is not None:
                    total_rula_scores += 1

                if reba_score is not None:
                    total_reba_scores += 1

                # -------------------------------------------------
                # Update per-track temporal state / smoothing.
                # -------------------------------------------------
                track = track_manager.update(
                    track_id,
                    result,
                    frame_number,
                )

                smoothed_rula = track.smoothed_rula()
                smoothed_reba = track.smoothed_reba()

                # -------------------------------------------------
                # Terminal output (concise, one line per detection).
                # -------------------------------------------------
                print(
                    f"FRAME {frame_number} | TRACK ID {track_id} | "
                    f"Raw RULA: {rula_score} Smooth RULA: {smoothed_rula} | "
                    f"Raw REBA: {reba_score} Smooth REBA: {smoothed_reba} | "
                    f"Frames seen: {track.frames_seen} "
                    f"Valid RULA: {track.valid_rula_frames} "
                    f"Valid REBA: {track.valid_reba_frames}"
                )

                # -------------------------------------------------
                # Draw keypoints.
                # -------------------------------------------------
                for x, y, confidence in landmarks:

                    if confidence >= 0.5:

                        cv2.circle(
                            frame,
                            (int(x), int(y)),
                            4,
                            (0, 255, 0),
                            -1,
                        )

                # -------------------------------------------------
                # Find visible keypoint for text placement.
                # -------------------------------------------------
                visible_points = [
                    (x, y)
                    for x, y, confidence in landmarks
                    if confidence >= 0.5
                ]

                if visible_points:

                    x, y = visible_points[0]

                    # Track ID.
                    cv2.putText(
                        frame,
                        f"ID: {track_id}",
                        (int(x), int(y) - 30),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.7,
                        (0, 255, 0),
                        2,
                    )

                    # RULA / REBA.
                    cv2.putText(
                        frame,
                        f"RULA: {rula_score}  "
                        f"REBA: {reba_score}",
                        (int(x), int(y) - 5),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.55,
                        (0, 255, 0),
                        2,
                    )

            # -----------------------------------------------------
            # Drop tracks that have been missing too long.
            # -----------------------------------------------------
            track_manager.remove_stale_tracks(frame_number)

            # -----------------------------------------------------
            # Frame counter.
            # -----------------------------------------------------
            cv2.putText(
                frame,
                f"Frame: {frame_number}/{frame_count}",
                (20, 35),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.7,
                (255, 255, 255),
                2,
            )

            # -----------------------------------------------------
            # Display video.
            # -----------------------------------------------------
            cv2.imshow(
                "VigilEx - ByteTrack + RULA/REBA",
                frame,
            )

            key = cv2.waitKey(1) & 0xFF

            if key == ord("q"):
                print()
                print("Stopped by user.")
                break

    finally:
        video.release()
        cv2.destroyAllWindows()

    # ---------------------------------------------------------
    # Final summary.
    # ---------------------------------------------------------
    print()
    print("=" * 60)
    print("VIDEO PROCESSING FINISHED")
    print("=" * 60)

    print(
        f"Frames processed : {frame_number}"
    )

    print(
        f"Tracked detections: "
        f"{total_tracked_detections}"
    )

    print(
        f"RULA scores       : "
        f"{total_rula_scores}"
    )

    print(
        f"REBA scores       : "
        f"{total_reba_scores}"
    )

    print()

    if total_rula_scores > 0 and total_reba_scores > 0:
        print(
            "PIPELINE STATUS: "
            "VIDEO → YOLO11n-POSE → BYTETRACK → "
            "POSTURE → RULA/REBA WORKING"
        )
    else:
        print(
            "PIPELINE STATUS: "
            "RULA/REBA scores were not produced."
        )

    # ---------------------------------------------------------
    # Primary worker selection.
    #
    # Must run BEFORE finalize_all(), since selection reads the
    # still-active TrackState objects in track_manager.
    # ---------------------------------------------------------
    selector = PrimaryWorkerSelector()
    selection = selector.select_primary_track(
        track_manager,
        requested_track_id=PRIMARY_TRACK_ID_OVERRIDE,
    )

    print()
    print("=" * 60)
    print("PRIMARY WORKER SELECTION")
    print("=" * 60)

    print()
    print("Candidate tracks:")

    for candidate in selection["candidates"]:
        print()
        print(f"Track {candidate['track_id']}")
        print(f"  Frames observed : {candidate['frames_observed']}")
        print(f"  Valid RULA      : {candidate['valid_rula_frames']}")
        print(f"  Valid REBA      : {candidate['valid_reba_frames']}")

    print()
    print("Selected primary worker:")
    print(f"  Track ID        : {selection['selected_track_id']}")
    print(f"  Selection       : {selection['selection_method']}")
    print(f"  Status          : {selection['status']}")

    if selection["selected_track_id"] is not None:
        primary_track = track_manager.get(selection["selected_track_id"])
        primary_assessment = primary_track.to_assessment()

        print()
        print("=" * 60)
        print("PRIMARY WORKER FINAL ASSESSMENT")
        print("=" * 60)

        print()
        print(f"Track ID : {primary_assessment['track_id']}")

        print()
        print("RULA")
        print(f"  Score       : {primary_assessment['rula']['score']}")
        print(f"  Risk        : {primary_assessment['rula']['risk']}")
        print(
            f"  Coverage    : "
            f"{primary_assessment['rula']['coverage'] * 100:.1f}%"
        )

        print()
        print("REBA")
        print(f"  Score       : {primary_assessment['reba']['score']}")
        print(f"  Risk        : {primary_assessment['reba']['risk']}")
        print(
            f"  Coverage    : "
            f"{primary_assessment['reba']['coverage'] * 100:.1f}%"
        )

        print()
        print(f"Status : {primary_assessment['status']}")

    # ---------------------------------------------------------
    # Per-track final assessment.
    # ---------------------------------------------------------
    track_manager.finalize_all()
    finalized = track_manager.get_finalized()

    for track_id in sorted(finalized):
        assessment = finalized[track_id]

        rula = assessment["rula"]
        reba = assessment["reba"]
        angles = assessment["angles"]

        print()
        print("=" * 60)
        print(f"TRACK {track_id} FINAL ASSESSMENT")
        print("=" * 60)

        print(f"Frames observed : {assessment['frames_observed']}")

        print()
        print("RULA")
        print(f"  Score          : {rula['score']}")
        print(f"  Risk           : {rula['risk']}")
        print(f"  Valid frames   : {rula['valid_frames']}")
        print(f"  Coverage       : {rula['coverage'] * 100:.1f}%")

        print()
        print("REBA")
        print(f"  Score          : {reba['score']}")
        print(f"  Risk           : {reba['risk']}")
        print(f"  Valid frames   : {reba['valid_frames']}")
        print(f"  Coverage       : {reba['coverage'] * 100:.1f}%")

        print()
        print("Angles")
        print(f"  Upper arm      : {angles['upper_arm_deg']}")
        print(f"  Lower arm      : {angles['lower_arm_flexion_deg']}")
        print(f"  Neck           : {angles['neck_deg']}")
        print(f"  Trunk          : {angles['trunk_deg']}")
        print(f"  Knee           : {angles['knee_flexion_deg']}")

        print()
        print(f"Status           : {assessment['status']}")


if __name__ == "__main__":
    main()