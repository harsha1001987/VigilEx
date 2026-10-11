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

## 11. Authentication

Every API route except `GET /`, `/api/v1/health*`, `POST /api/v1/auth/register` and
`POST /api/v1/auth/login` requires `Authorization: Bearer <access token>`.

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/auth/register` | `{name, email, password}` → 201 with a token. Creates the account and its own organization. 409 if the email exists. |
| `POST /api/v1/auth/login` | `{email, password}` → token, or 401 `Incorrect email or password.` for any failure. |
| `GET /api/v1/auth/me` | The signed-in account. |
| `POST /api/v1/auth/logout` | Revokes every token of the account (all devices). |

- **Passwords**: Argon2id (`argon2-cffi`), 8–128 characters. Never stored or logged in plain text.
- **Tokens**: HS256 JWTs (`PyJWT`) with `sub`, `typ=access`, `iat`, `exp`, `iss`, `aud` and a session
  version. Lifetime `ACCESS_TOKEN_EXPIRE_MINUTES` (default 720, one shift). There are no refresh
  tokens: the app signs in again when a token expires.
- **Sign-out / revocation**: tokens are otherwise stateless. Signing out (or an operator password reset)
  increments `users.session_version`, which invalidates all of the account's tokens. A single device
  cannot be signed out on its own.
- **Signing key**: set `AUTH_SECRET_KEY` (≥ 32 random characters) per environment, outside version
  control. The API refuses to start without it. Rotating it signs everyone out.
- **Rate limits**: sign-in attempts per client address and per email, and registrations per client
  address (`LOGIN_RATE_*`, `REGISTER_RATE_*`), answered with 429 and `Retry-After`. Counters are kept in
  the API process's memory: with several processes or hosts, add a shared limiter (reverse proxy,
  gateway or Redis).
- **Ownership**: the owner of every assessment is the account from the token, never a client-supplied
  id. Another account's assessment, report or PDF is reported exactly like a missing one (404). Sites,
  areas and tasks are scoped to the account's organization.

### Assessments created before authentication

They have no owner and are **visible to no account**. Nothing assigns them automatically, because the
database does not record who made them. An operator who knows whose they are assigns them one by one:

```powershell
python -m app.scripts.assign_assessments --list
python -m app.scripts.assign_assessments --email person@company.com --id <assessment id> --dry-run
python -m app.scripts.assign_assessments --email person@company.com --id <assessment id>
```

Only unowned assessments are assigned; an owned one is never moved.

### Account recovery

There is no reset email yet. An operator sets a new password (read without echo; all sessions end):

```powershell
python -m app.scripts.set_password --email person@company.com
```

### Production

Serve the API only over HTTPS (TLS at a reverse proxy or load balancer). Plain HTTP is for local
development only; the Android app allows it in debug builds only. Restrict `CORS_ORIGINS`, keep
`AUTH_SECRET_KEY` in a secret manager, and run behind a shared rate limiter.
