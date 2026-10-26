# How to Run VigilEx

This document provides step-by-step instructions for running the **VigilEx** application, including the **FastAPI + PostgreSQL backend** and the **Jetpack Compose Android app**.

---

## 1. Prerequisites

Ensure you have the following installed on your host machine:

* **Python:** 3.10 or higher (Python 3.13 recommended)
* **PostgreSQL:** 14 or higher (e.g., PostgreSQL 18)
* **Android Development:** Android Studio 2026.1+ with Android SDK (API 26+)
* **Command Line Tools:** Git, PowerShell, ADB (Android Debug Bridge)

---

## 2. Backend Setup (FastAPI + PostgreSQL)

### Step 1: PostgreSQL Database Configuration

1. Ensure your local PostgreSQL service is running on port `5432`.
2. Open a PowerShell prompt and connect to PostgreSQL as superuser:

   ```powershell
   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres
   ```

3. Create the database user, database, and permissions:

   ```sql
   CREATE USER vigilex_user WITH PASSWORD 'vigilex_password';
   CREATE DATABASE vigilex OWNER vigilex_user;
   ALTER USER vigilex_user CREATEDB;
   \q
   ```

---

### Step 2: Python Virtual Environment & Dependencies

1. Navigate to the `backend` directory:

   ```powershell
   cd F:\Projects\VigilEx\backend
   ```

2. Create and activate a Python virtual environment:

   ```powershell
   python -m venv .venv
   .\.venv\Scripts\Activate.ps1
   ```

3. Install required Python packages:

   ```powershell
   pip install -r requirements.txt
   ```

---

### Step 3: Environment Configuration (`.env`)

1. Ensure `backend\.env` exists with the following configuration:

   ```env
   DATABASE_URL=postgresql+psycopg://vigilex_user:vigilex_password@localhost:5432/vigilex
   CORS_ORIGINS=http://localhost:8000,http://127.0.0.1:8000
   AUTH_SECRET_KEY=vigilex_development_secret_key_32bytes_long_12345
   ```

---

### Step 4: Database Migrations & Initial Seed Data

1. Apply Alembic database schema migrations:

   ```powershell
   alembic upgrade head
   ```

2. Run the idempotent seed script to populate V1 default hierarchy data (*Organization, Site, Area, Task*):

   ```powershell
   $env:PYTHONPATH="F:\Projects\VigilEx;F:\Projects\VigilEx\backend"
   python -m app.db.seed
   ```

---

### Step 5: Start the FastAPI Server

Launch the FastAPI backend development server:

```powershell
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

#### Health Check Verification

* **API Health:** Open [http://127.0.0.1:8000/api/v1/health](http://127.0.0.1:8000/api/v1/health) (Expect `{"status": "ok"}`)
* **DB Connection:** Open [http://127.0.0.1:8000/api/v1/health/db](http://127.0.0.1:8000/api/v1/health/db) (Expect `{"status": "ok", "database": "connected"}`)
* **OpenAPI Documentation:** Open [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)

---

## 3. Android App Setup

### Option A: Using Android Studio IDE

1. Launch **Android Studio**.
2. Open the project at `F:\Projects\VigilEx`.
3. Allow Gradle to sync dependencies.
4. Select your target device (*Android Emulator* or *Connected USB Physical Device*).
5. Click **Run** (`Shift + F10`) or **Debug** (`Shift + F9`).

---

### Option B: Using Command Line (Gradle & ADB)

1. Open PowerShell at the root project directory:

   ```powershell
   cd F:\Projects\VigilEx
   ```

2. Build the Debug APK:

   ```powershell
   .\gradlew assembleDebug
   ```

3. Install the Debug APK onto a connected device or running emulator:

   ```powershell
   .\gradlew installDebug
   ```

---

## 4. Connecting Android App to the Backend Server

The VigilEx app connects to the FastAPI backend based on your target device setup:

### A. Android Emulator

* **Server Address:** `http://10.0.2.2:8000/`
* Android Emulator automatically routes `10.0.2.2` to the host PC's `127.0.0.1:8000`. No manual setup required.

---

### B. Physical Phone over USB

1. Enable **Developer Options** and **USB Debugging** on your physical phone.
2. Connect phone to host PC via USB cable.
3. Run the ADB port forwarding task (automatically triggered during debug builds):

   ```powershell
   .\gradlew adbReverseDevServer
   ```

   This forwards `http://127.0.0.1:8000/` on the physical phone directly to port `8000` on your host PC over USB.

---

### C. Physical Phone over Wi-Fi

1. Connect your PC and phone to the same Wi-Fi network.
2. Find your PC's IPv4 address via `ipconfig` (e.g., `192.168.1.50`).
3. Ensure port `8000` is allowed in Windows Firewall.
4. On the VigilEx phone app:
   * Open **Settings** -> **Developer options**.
   * Enter `http://192.168.1.50:8000/` in the **Server address** field.
   * Tap **Test connection** (should report `Server reachable.`).
   * Tap **Save**.

---

## 5. Testing & Verification

### Running Android Unit Tests

```powershell
cd F:\Projects\VigilEx
.\gradlew app:testDebugUnitTest
```

### Running Backend Integration & Pipeline Tests

```powershell
cd F:\Projects\VigilEx\backend
$env:DATABASE_URL="postgresql+psycopg://vigilex_user:vigilex_password@localhost:5432/vigilex"
$env:AUTH_SECRET_KEY="vigilex_development_secret_key_32bytes_long_12345"
$env:PYTHONPATH="F:\Projects\VigilEx;F:\Projects\VigilEx\backend"
python -m pytest backend/tests/test_hierarchy_routes.py backend/tests/test_upload_video.py backend/tests/test_assessment_persistence.py backend/tests/test_track_state.py backend/tests/test_worker_selector.py backend/tests/test_real_video_persistence_e2e.py -s
```

---

## 6. End-to-End User Flow Summary

1. Launch VigilEx Android app and tap **Create account** or **Sign in**.
2. Go to **Assessment Studio** tab.
3. Tap **Record video** to film a workplace task using the phone camera, OR tap **Choose from device** to pick a sample MP4/MOV/3GP video.
4. Inspect the video preview and metadata facts (resolution, duration, size).
5. Tap **Analyze video** to upload to the FastAPI backend.
6. The backend runs `YOLO11n-Pose` posture landmark estimation, `ByteTrack` worker tracking, and `RULA/REBA` ergonomic scoring.
7. The completed assessment is transactionally saved to PostgreSQL.
8. View the ergonomic risk summary, RULA/REBA scores, posture findings, and recommended interventions in the app or download the generated PDF report!
