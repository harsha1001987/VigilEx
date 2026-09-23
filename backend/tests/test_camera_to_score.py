import cv2

from backend.app.cv.pose_estimator import (
    PoseEstimator,
    landmarks_to_posture_dict,
)
from backend.app.cv.posture import assess_posture


def main():
    print("Starting VigilEx camera → posture → RULA/REBA test...")

    estimator = PoseEstimator()

    camera = cv2.VideoCapture(0)

    if not camera.isOpened():
        print("ERROR: Could not open camera.")
        return

    print("Camera opened.")
    print("Press Q to quit.")

    while True:
        ok, frame = camera.read()

        if not ok:
            print("ERROR: Could not read camera frame.")
            break

        people = estimator.estimate(frame)

        display = frame.copy()

        # We only want ONE person for this test.
        if people:
            landmarks = people[0]

            # Convert YOLO COCO-17 output into the format
            # expected by posture.assess_posture().
            posture_landmarks = landmarks_to_posture_dict(
               landmarks
           )

            result = assess_posture(posture_landmarks)

            print("\n" + "=" * 50)
            print("ONE-PERSON POSTURE RESULT")
            print("=" * 50)

            print(f"RULA score : {result['rula']['score']}")
            print(f"RULA risk  : {result['rula']['risk']}")

            print(f"REBA score : {result['reba']['score']}")
            print(f"REBA risk  : {result['reba']['risk']}")

            print("\nAngles:")

            for name, value in result["rula"]["angles"].items():
                print(f"  {name}: {value}")

            # Draw detected keypoints.
            for x, y, confidence in landmarks:
                if confidence >= 0.5:
                    cv2.circle(
                        display,
                        (int(x), int(y)),
                        4,
                        (0, 255, 0),
                        -1,
                    )

        else:
            cv2.putText(
                display,
                "No person detected",
                (30, 50),
                cv2.FONT_HERSHEY_SIMPLEX,
                1,
                (0, 0, 255),
                2,
            )

        cv2.imshow("VigilEx - Camera Posture Test", display)

        key = cv2.waitKey(1) & 0xFF

        if key == ord("q"):
            break

    camera.release()
    cv2.destroyAllWindows()

    print("\nCamera test finished.")


if __name__ == "__main__":
    main()