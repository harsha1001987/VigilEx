import cv2

from backend.app.cv.pose_estimator import PoseEstimator


def main():
    print("Starting VigilEx ByteTrack camera test...")

    estimator = PoseEstimator()

    camera = cv2.VideoCapture(0)

    if not camera.isOpened():
        print("ERROR: Could not open camera.")
        return

    print("Camera opened.")
    print("Move around or have another person enter the frame.")
    print("Press Q to quit.")

    try:
        while True:
            success, frame = camera.read()

            if not success:
                print("ERROR: Could not read camera frame.")
                break

            tracked_people = estimator.estimate_tracked(frame)

            for track_id, landmarks in tracked_people:
                print(
                    f"Track ID: {track_id} | "
                    f"Keypoints: {len(landmarks)}"
                )

                # Draw the visible keypoints
                for x, y, confidence in landmarks:
                    if confidence >= 0.5:
                        cv2.circle(
                            frame,
                            (int(x), int(y)),
                            4,
                            (0, 255, 0),
                            -1,
                        )

                # Put track ID near the person's first visible point
                visible_points = [
                    (x, y)
                    for x, y, confidence in landmarks
                    if confidence >= 0.5
                ]

                if visible_points:
                    x, y = visible_points[0]

                    cv2.putText(
                        frame,
                        f"ID: {track_id}",
                        (int(x), int(y) - 10),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.7,
                        (0, 255, 0),
                        2,
                    )

            cv2.imshow("VigilEx - ByteTrack Test", frame)

            key = cv2.waitKey(1) & 0xFF

            if key == ord("q"):
                break

    finally:
        camera.release()
        cv2.destroyAllWindows()

    print("ByteTrack test stopped.")


if __name__ == "__main__":
    main()