# VigilEx Backend

FastAPI + PostgreSQL backend foundation for the VigilEx ergonomic assessment app.

## 1. Requirements

- Python 3.10+ (this project was set up with Python 3.13)
- PostgreSQL 14+ running locally (or reachable)

## 2. PostgreSQL setup

Install PostgreSQL for Windows: https://www.postgresql.org/download/windows/

After installing, create the database and a user (PowerShell, using `psql` from the PostgreSQL bin directory):

```powershell
psql -U postgres
```

Then in the `psql` prompt:

```sql
CREATE USER vigilex_user WITH PASSWORD 'vigilex_password';
CREATE DATABASE vigilex OWNER vigilex_user;
\q
```

Update `backend\.env` if you use different credentials.

## 3. Create the virtual environment

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

## 4. Install dependencies

```powershell
pip install -r requirements.txt
```

## 5. Configure .env

Copy `.env.example` to `.env` and set `DATABASE_URL` to match your PostgreSQL credentials:

```powershell
Copy-Item .env.example .env
```

```
DATABASE_URL=postgresql+psycopg://vigilex_user:vigilex_password@localhost:5432/vigilex
```

## 6. Run Alembic migrations

```powershell
alembic upgrade head
```

This creates the `organizations`, `users`, `sites`, `areas`, `tasks`, and `assessments` tables.

## 7. Start FastAPI

```powershell
uvicorn app.main:app --reload
```

## 8. Open Swagger docs

http://127.0.0.1:8000/docs

## 9. Test /health

http://127.0.0.1:8000/api/v1/health

Expected response:

```json
{"status": "ok"}
```

## 10. Test /health/db

http://127.0.0.1:8000/api/v1/health/db

Expected response (only if PostgreSQL is reachable):

```json
{"status": "ok", "database": "connected"}
```

## Notes

- CORS allowed origins are configured via `CORS_ORIGINS` in `.env`.
- API routes are versioned under `/api/v1`.
