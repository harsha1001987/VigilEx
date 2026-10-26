# Complete Guide: How to Set Up and Run VigilEx

This guide provides step-by-step instructions for a new team member to set up and run the **VigilEx** application on a new machine from scratch.

VigilEx consists of two main components:
1. **Backend:** FastAPI + PostgreSQL + Computer Vision (YOLO11n-Pose & ByteTrack)
2. **Frontend:** Android Application (Jetpack Compose + Kotlin)

---

## 1. System Requirements & Prerequisites

Before starting, install the following software on your computer:

| Dependency | Required Version | Download Link / Notes |
| :--- | :--- | :--- |
| **Git** | Latest | [git-scm.com](https://git-scm.com/) |
| **Python** | 3.10 to 3.13 | [python.org](https://www.python.org/) (Ensure *"Add python.exe to PATH"* is checked during setup) |
| **PostgreSQL** | 14 or higher | [postgresql.org](https://www.postgresql.org/download/) |
| **Android Studio** | Ladybug / 2024.2+ | [developer.android.com/studio](https://developer.android.com/studio) |
| **Java JDK** | JDK 17 or 21 | Included automatically with Android Studio |

---

## 2. Clone the Repository

Open a terminal or PowerShell window in your preferred development directory and clone the project:

```bash
git clone <REPOSITORY_URL>
cd VigilEx
```

---

## 3. Backend Setup (FastAPI + PostgreSQL)

### Step 3.1: Create the PostgreSQL Database

1. Ensure the PostgreSQL service is running on your machine on port `5432`.
2. Open a terminal and log in to PostgreSQL as superuser:

   * **Windows (PowerShell):**
     ```powershell
     psql -U postgres
     ```
     *(If `psql` is not in your PATH, run `& "C:\Program Files\PostgreSQL\<VERSION>\bin\psql.exe" -U postgres`)*

   * **macOS / Linux:**
     ```bash
     sudo -u postgres psql
     ```

3. Run the following SQL commands in the `psql` prompt to create the database user, database, and permissions:

   ```sql
   CREATE USER vigilex_user WITH PASSWORD 'vigilex_password';
   CREATE DATABASE vigilex OWNER vigilex_user;
   ALTER USER vigilex_user CREATEDB;
   \q
   ```

---

### Step 3.2: Set Up Python Virtual Environment & Install Dependencies

1. Navigate to the `backend` folder:

   ```bash
   cd backend
   ```

2. Create a virtual environment:

   * **Windows (PowerShell):**
     ```powershell
     # If script execution is restricted on Windows PowerShell:
     Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope Process

     python -m venv .venv
     .\.venv\Scripts\Activate.ps1
     ```

   * **macOS / Linux:**
     ```bash
     python3 -m venv .venv
     source .venv/bin/activate
     ```

3. Upgrade `pip` and install all required packages:

   ```bash
   pip install --upgrade pip
   pip install -r requirements.txt
   ```

---

### Step 3.3: Create Environment Configuration (`.env`)

Create a file named `.env` inside the `backend/` directory:

* **File location:** `backend/.env`
* **File contents:**

  ```env
  DATABASE_URL=postgresql+psycopg://vigilex_user:vigilex_password@localhost:5432/vigilex
  CORS_ORIGINS=http://localhost:8000,http://127.0.0.1:8000
  AUTH_SECRET_KEY=vigilex_development_secret_key_32bytes_long_12345
  ```

> **Note:** `AUTH_SECRET_KEY` must be set to a random secret string of at least 32 characters for JWT token authentication to function properly.

---

### Step 3.4: Apply Database Migrations & Initial Seed Data

1. Run Alembic schema migrations to create all database tables:

   ```bash
   alembic upgrade head
   ```

2. Run the idempotent seed script to populate default workspace hierarchy data (*Organization, Site, Area, Task*):

   * **Windows (PowerShell):**
     ```powershell
     $env:PYTHONPATH=".;.."
     python -m app.db.seed
     ```

   * **macOS / Linux:**
     ```bash
     PYTHONPATH=".:.." python3 -m app/db/seed.py
     ```

---

### Step 3.5: Launch the FastAPI Backend Server

Start the Uvicorn server:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

#### Verify Backend Execution:
Open your browser and test these URLs:
* **API Health Status:** [http://127.0.0.1:8000/api/v1/health](http://127.0.0.1:8000/api/v1/health) $\rightarrow$ Expected output: `{"status": "ok"}`
* **Database Connection Status:** [http://127.0.0.1:8000/api/v1/health/db](http://127.0.0.1:8000/api/v1/health/db) $\rightarrow$ Expected output: `{"status": "ok", "database": "connected"}`
* **Interactive API Documentation (Swagger):** [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)

---

## 4. Android App Setup

### Step 4.1: Open the Project in Android Studio

1. Open **Android Studio**.
2. Click **Open** and select the root `VigilEx` repository directory.
3. Wait for Android Studio and Gradle to download dependencies and finish indexing.

---

### Step 4.2: Running the App

#### Option A: Android Studio IDE (Recommended)
1. In Android Studio, select a target device from the top toolbar:
   * **Android Emulator** (Create a virtual device with API level 26+ if you don't have one).
   * **Physical Android Device** connected via USB.
2. Click the green **Run** button (`Shift + F10`).

#### Option B: Command Line (Gradle & ADB)
From the root `VigilEx` directory:

* **Windows (PowerShell):**
  ```powershell
  .\gradlew assembleDebug
  .\gradlew installDebug
  ```

* **macOS / Linux:**
  ```bash
  ./gradlew assembleDebug
  ./gradlew installDebug
  ```

---

## 5. Connecting the Android App to the Backend Server

Depending on how you run the Android app, configure network connectivity as follows:

### Scenario A: Android Emulator
* **Server Base URL:** `http://10.0.2.2:8000/`
* The Android Emulator automatically forwards `10.0.2.2` to `127.0.0.1` on your host computer. No extra configuration is needed.

---

### Scenario B: Physical Phone via USB Cable
1. Enable **Developer Options** and **USB Debugging** on your phone.
2. Plug the phone into your computer via USB.
3. Run the ADB port forwarding command:

   * **Windows:** `.\gradlew adbReverseDevServer` (or `adb reverse tcp:8000 tcp:8000`)
   * **macOS/Linux:** `./gradlew adbReverseDevServer` (or `adb reverse tcp:8000 tcp:8000`)

   *(This forwards port `8000` on your physical phone directly to port `8000` on your computer over USB).*

---

### Scenario C: Physical Phone via Wi-Fi Network
1. Connect your computer and phone to the same Wi-Fi network.
2. Find your computer's local IP address:
   * **Windows:** Run `ipconfig` (Look for IPv4 address under Wi-Fi, e.g., `192.168.1.50`)
   * **macOS/Linux:** Run `ifconfig` or `ip a`
3. In the VigilEx Android app:
   * Open **Settings** $\rightarrow$ **Developer options**.
   * Enter `http://<YOUR_COMPUTER_IP>:8000/` (e.g. `http://192.168.1.50:8000/`).
   * Tap **Test connection** $\rightarrow$ Verify it shows *"Server reachable."*
   * Tap **Save**.

---

## 6. First-Time Application Workflow Test

Follow these steps to perform a complete ergonomic assessment test:

1. Launch the **VigilEx** app on your phone or emulator.
2. Tap **Create account** $\rightarrow$ Enter your name, email, and password $\rightarrow$ Tap **Create account**.
3. Tap **Sign in** with your new credentials.
4. Navigate to the **Assessment Studio** tab.
5. Tap **Record video** to film a 10-15 second workplace task using the camera, OR tap **Choose from device** to select an existing MP4, MOV, or 3GP video file.
6. Review the video preview and specs (resolution, duration, file size).
7. Tap **Analyze video**:
   * The app uploads the video file to the FastAPI server.
   * The backend runs posture estimation using **YOLO11n-Pose**, worker tracking via **ByteTrack**, and calculates **RULA & REBA** ergonomic risk scores.
   * Results are saved to the PostgreSQL database.
8. Review the posture risk category, RULA/REBA scores, joint angle measurements, and recommended safety interventions, or tap **Generate report** to download a PDF report!

---

## 7. Running Automated Test Suites

### Android Unit Tests
From the root directory, run:

```bash
# Windows
.\gradlew app:testDebugUnitTest

# macOS / Linux
./gradlew app:testDebugUnitTest
```

### Backend Integration & CV Tests
From the `backend/` directory with the virtual environment activated:

```bash
pytest backend/tests/test_hierarchy_routes.py backend/tests/test_upload_video.py backend/tests/test_assessment_persistence.py backend/tests/test_track_state.py backend/tests/test_worker_selector.py backend/tests/test_real_video_persistence_e2e.py -s
```

---

## 8. Troubleshooting FAQ

* **Issue:** `Could not connect to server` on physical phone.
  * **Fix:** Ensure port 8000 is allowed in Windows Firewall / OS Firewall, and run `adb reverse tcp:8000 tcp:8000` if connected via USB.

* **Issue:** `psycopg.errors.UndefinedColumn` or missing tables.
  * **Fix:** Make sure you ran `alembic upgrade head` inside the `backend/` directory while connected to your PostgreSQL database.

* **Issue:** `AUTH_SECRET_KEY must be set...` error on backend start.
  * **Fix:** Ensure `AUTH_SECRET_KEY` is present in `backend/.env` and is at least 32 characters long.
