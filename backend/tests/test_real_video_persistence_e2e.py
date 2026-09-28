"""
End-to-end real video pipeline -> backend persistence -> PostgreSQL verification test.
"""

import uuid
from pathlib import Path
from decimal import Decimal
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.db.base import Base
from app.models.organization import Organization
from app.models.site import Site
from app.models.area import Area
from app.models.task import Task
from app.models.assessment import Assessment
from app.models.assessment_score import AssessmentScore
from app.cv.batch_processor import process_video
from app.cv.track_state import TrackStateManager
from app.cv.worker_selector import PrimaryWorkerSelector
from app.cv.pose_estimator import landmarks_to_posture_dict
from app.cv.posture import assess_posture
from app.services.assessment_persistence import persist_cv_assessment_results

POSTGRES_URL = "postgresql+psycopg://vigilex_user:vigilex_password@localhost:5432/vigilex"


def test_real_video_pipeline_to_postgresql():
    """
    Runs real video -> YOLO11n-Pose -> ByteTrack -> posture -> RULA/REBA -> primary worker -> PostgreSQL persistence.
    """
    test_dir = Path(__file__).resolve().parent
    video_path = test_dir / "19832490-hd_1920_1080_25fps (1).mp4"

    if not video_path.exists():
        pytest.skip(f"Video file not found at {video_path}")

    # Connect to real PostgreSQL database
    engine = create_engine(POSTGRES_URL, echo=False)
    SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    db = SessionLocal()

    try:
        # Seed parent hierarchy if missing
        org = db.query(Organization).first()
        if not org:
            org = Organization(id=uuid.uuid4(), name="VigilEx Real Video Org")
            db.add(org)
            db.commit()

        site = db.query(Site).filter(Site.organization_id == org.id).first()
        if not site:
            site = Site(id=uuid.uuid4(), organization_id=org.id, name="Main Facility")
            db.add(site)
            db.commit()

        area = db.query(Area).filter(Area.site_id == site.id).first()
        if not area:
            area = Area(id=uuid.uuid4(), site_id=site.id, name="Assembly Line 1")
            db.add(area)
            db.commit()

        task = db.query(Task).filter(Task.area_id == area.id).first()
        if not task:
            task = Task(id=uuid.uuid4(), area_id=area.id, name="Material Lifting Task")
            db.add(task)
            db.commit()

        # Create draft assessment in PostgreSQL
        assessment_id = uuid.uuid4()
        assessment = Assessment(
            id=assessment_id,
            organization_id=org.id,
            site_id=site.id,
            area_id=area.id,
            task_id=task.id,
            status="draft",
            consent_given=False,
        )
        db.add(assessment)
        db.commit()

        # -------------------------------------------------------------
        # Real Video Processing Pipeline
        # -------------------------------------------------------------
        print("\nProcessing video through CV pipeline...")
        cv_result = process_video(str(video_path), stride_hz=25.0)

        # Primary worker selection
        track_manager = TrackStateManager()
        # Build track states from keyframes
        for frame in cv_result.get("keyframes", []):
            track_id = frame["track_id"]
            frame_idx = int(frame["t"] * cv_result["video"]["fps"])
            frame_assessment = {
                "rula": frame["rula"],
                "reba": frame["reba"],
            }
            track_manager.update(track_id, frame_assessment, frame_idx)

        selector = PrimaryWorkerSelector()
        selection = selector.select_primary_track(track_manager)

        primary_assessment = None
        if selection["selected_track_id"] is not None:
            primary_track = track_manager.get(selection["selected_track_id"])
            if primary_track:
                primary_assessment = primary_track.to_assessment()

        # -------------------------------------------------------------
        # Backend Persistence to PostgreSQL
        # -------------------------------------------------------------
        persisted = persist_cv_assessment_results(
            db,
            assessment,
            cv_result,
            primary_assessment,
        )

        # -------------------------------------------------------------
        # Verification directly against PostgreSQL
        # -------------------------------------------------------------
        queried = db.query(Assessment).filter(Assessment.id == assessment_id).first()
        assert queried is not None
        assert queried.status == "completed"
        assert queried.methodology_version == "vigilex-rula-reba-v1"

        scores = db.query(AssessmentScore).filter(AssessmentScore.assessment_id == assessment_id).all()
        assert len(scores) >= 1

        rula_score = next((s for s in scores if s.method == "RULA"), None)
        reba_score = next((s for s in scores if s.method == "REBA"), None)

        print("\n" + "=" * 60)
        print("POSTGRESQL PERSISTENCE VERIFICATION SUCCESSFUL")
        print("=" * 60)
        print(f"Assessment ID      : {queried.id}")
        print(f"Status             : {queried.status}")
        print(f"Methodology        : {queried.methodology_version}")

        if primary_assessment:
            print(f"Primary Track ID   : {primary_assessment['track_id']}")

        if rula_score:
            print(f"RULA Score         : {rula_score.score}")
            print(f"RULA Risk          : {rula_score.risk_band}")
            print(f"RULA Valid Frames  : {rula_score.inputs['valid_frames']}")
            print(f"RULA Coverage      : {rula_score.inputs['coverage_pct']}")

        if reba_score:
            print(f"REBA Score         : {reba_score.score}")
            print(f"REBA Risk          : {reba_score.risk_band}")
            print(f"REBA Valid Frames  : {reba_score.inputs['valid_frames']}")
            print(f"REBA Coverage      : {reba_score.inputs['coverage_pct']}")

        print("=" * 60)

    finally:
        db.close()


if __name__ == "__main__":
    test_real_video_pipeline_to_postgresql()
