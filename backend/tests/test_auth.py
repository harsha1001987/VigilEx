"""
Authentication and ownership: accounts, tokens, sign-out, rate limits, and
that every protected route refuses anonymous callers and other accounts.
"""

import copy
import uuid
from datetime import datetime, timedelta, timezone

import jwt
import pytest
from fastapi.routing import APIRoute
from fastapi.testclient import TestClient

from app.core import security
from app.core.config import Settings, get_settings
from app.main import app
from app.models.assessment import Assessment
from app.models.organization import Organization
from app.models.site import Site
from app.models.area import Area
from app.models.task import Task
from app.models.user import User
from app.scripts import assign_assessments
from app.services import analysis_assessments as analyses
from app.services import reports
from conftest import DEFAULT_PASSWORD, bearer, client_for, make_user
from test_interventions import real_result  # noqa: F401  (session fixture: one real process_video run)

REGISTER = "/api/v1/auth/register"
LOGIN = "/api/v1/auth/login"
ME = "/api/v1/auth/me"
LOGOUT = "/api/v1/auth/logout"


def _register(client, email="new@example.com", password="a-good-password", name="New Person"):
    return client.post(REGISTER, json={"name": name, "email": email, "password": password})


def _token(**overrides) -> str:
    """A token with the real key and claims, except [overrides] (None removes a claim)."""
    s = get_settings()
    now = datetime.now(timezone.utc)
    claims = {"sub": str(uuid.uuid4()), "typ": "access", "ver": 0, "iat": now, "exp": now + timedelta(minutes=5),
              "iss": s.auth_token_issuer, "aud": s.auth_token_audience}
    key = overrides.pop("_key", s.signing_key)
    for k, v in overrides.items():
        if v is None:
            claims.pop(k)
        else:
            claims[k] = v
    return jwt.encode(claims, key, algorithm="HS256")


# ================================================================ passwords

def test_passwords_are_hashed_with_argon2_and_verified():
    hashed = security.hash_password("correct horse battery")
    assert hashed.startswith("$argon2id$") and "correct horse" not in hashed
    assert security.verify_password("correct horse battery", hashed)
    assert not security.verify_password("wrong", hashed)
    assert not security.verify_password("anything", None)  # Unknown account: always fails.
    assert not security.verify_password("anything", "not-a-hash")
    assert security.hash_password("same") != security.hash_password("same")  # Salted.


# ================================================================ registration

def test_register_returns_a_session_and_stores_only_a_hash(anon_client, db):
    response = _register(anon_client, email="  New.Person@Example.COM ")
    assert response.status_code == 201
    body = response.json()
    assert body["token_type"] == "bearer" and body["access_token"]
    assert body["expires_in"] == get_settings().access_token_expire_minutes * 60
    assert body["user"]["email"] == "new.person@example.com"  # Normalized.
    assert "password" not in str(body).lower()

    user = db.query(User).filter(User.email == "new.person@example.com").one()
    assert user.password_hash.startswith("$argon2id$") and "a-good-password" not in user.password_hash
    # Every account gets its own organization.
    assert db.get(Organization, user.organization_id) is not None
    me = anon_client.get(ME, headers={"Authorization": f"Bearer {body['access_token']}"})
    assert me.status_code == 200 and me.json()["id"] == str(user.id)


def test_duplicate_registration_is_rejected_in_any_letter_case(anon_client, db):
    assert _register(anon_client, email="dup@example.com").status_code == 201
    response = _register(anon_client, email="DUP@example.com")
    assert response.status_code == 409
    assert response.json() == {"detail": "An account with this email already exists."}
    assert db.query(User).count() == 1


@pytest.mark.parametrize("body", [
    {"name": "A", "email": "not-an-email", "password": "a-good-password"},
    {"name": "A", "email": "a@example.com", "password": "short"},
    {"name": "   ", "email": "a@example.com", "password": "a-good-password"},
    {"name": "A", "email": "a@example.com", "password": "        "},
    {"name": "A", "email": "a@example.com", "password": "x" * 129},
    {"email": "a@example.com", "password": "a-good-password"},
])
def test_registration_input_is_validated(anon_client, db, body):
    assert anon_client.post(REGISTER, json=body).status_code == 422
    assert db.query(User).count() == 0


# ================================================================ sign-in

def test_login_with_the_right_password(anon_client, user):
    response = anon_client.post(LOGIN, json={"email": "OWNER@example.com", "password": DEFAULT_PASSWORD})
    assert response.status_code == 200
    token = response.json()["access_token"]
    assert anon_client.get(ME, headers={"Authorization": f"Bearer {token}"}).json()["email"] == user.email


@pytest.mark.parametrize("email,password", [
    ("owner@example.com", "wrong-password"),
    ("nobody@example.com", DEFAULT_PASSWORD),
    ("not an email", DEFAULT_PASSWORD),
])
def test_failed_login_never_says_which_part_was_wrong(anon_client, user, email, password):
    response = anon_client.post(LOGIN, json={"email": email, "password": password})
    assert response.status_code == 401
    assert response.json() == {"detail": "Incorrect email or password."}
    assert response.headers["www-authenticate"] == "Bearer"


def test_account_without_a_password_cannot_sign_in(anon_client, db):
    org = Organization(name="Legacy")
    db.add(org)
    db.flush()
    db.add(User(organization_id=org.id, name="Legacy", email="legacy@example.com"))
    db.commit()
    response = anon_client.post(LOGIN, json={"email": "legacy@example.com", "password": "anything-at-all"})
    assert response.status_code == 401


# ================================================================ tokens

def test_me_requires_a_token(anon_client):
    response = anon_client.get(ME)
    assert response.status_code == 401
    assert response.json() == {"detail": "Not authenticated"}
    assert response.headers["www-authenticate"] == "Bearer"


@pytest.mark.parametrize("make", [
    lambda u: "garbage",
    lambda u: _token(sub=str(u.id), _key="another-key-that-is-long-enough-to-be-valid!!"),
    lambda u: _token(sub=str(u.id), exp=datetime.now(timezone.utc) - timedelta(seconds=1)),
    lambda u: _token(sub=str(u.id), aud="someone-else"),
    lambda u: _token(sub=str(u.id), iss="someone-else"),
    lambda u: _token(sub=str(u.id), typ="refresh"),
    lambda u: _token(sub=str(u.id), ver=None),
    lambda u: _token(sub="not-a-uuid"),
    lambda u: _token(),  # Well-formed, but no such account.
    lambda u: jwt.encode({"sub": str(u.id), "typ": "access", "ver": 0}, key=None, algorithm="none"),
], ids=["garbage", "wrong-key", "expired", "audience", "issuer", "type", "no-version", "subject", "unknown-user", "alg-none"])
def test_invalid_tokens_are_rejected(anon_client, user, make):
    response = anon_client.get(ME, headers={"Authorization": f"Bearer {make(user)}"})
    assert response.status_code == 401
    assert response.json() == {"detail": "Your session is invalid or has expired. Sign in again."}


def test_a_valid_token_for_the_user_is_accepted(anon_client, user):
    assert anon_client.get(ME, headers={"Authorization": f"Bearer {_token(sub=str(user.id))}"}).status_code == 200


def test_token_lifetime_follows_configuration(user, monkeypatch):
    monkeypatch.setattr(get_settings(), "access_token_expire_minutes", 7)
    now = datetime(2026, 10, 9, tzinfo=timezone.utc)
    issued = security.create_access_token(user.id, 0, now=now)
    assert issued.expires_at == now + timedelta(minutes=7)


def test_signing_key_must_be_configured():
    with pytest.raises(RuntimeError, match="AUTH_SECRET_KEY"):
        Settings(database_url="postgresql://x", auth_secret_key=None).signing_key
    with pytest.raises(RuntimeError, match="AUTH_SECRET_KEY"):
        Settings(database_url="postgresql://x", auth_secret_key="too-short").signing_key


# ================================================================ sign-out

def test_logout_revokes_existing_tokens(anon_client, db, user):
    headers = bearer(user)
    assert anon_client.get(ME, headers=headers).status_code == 200
    assert anon_client.post(LOGOUT, headers=headers).status_code == 204
    assert anon_client.get(ME, headers=headers).status_code == 401
    assert anon_client.post(LOGOUT, headers=headers).status_code == 401
    # Signing in again issues a working token.
    fresh = anon_client.post(LOGIN, json={"email": user.email, "password": DEFAULT_PASSWORD}).json()["access_token"]
    assert anon_client.get(ME, headers={"Authorization": f"Bearer {fresh}"}).status_code == 200


# ================================================================ rate limits

def test_repeated_failed_logins_are_throttled(anon_client, user, monkeypatch):
    monkeypatch.setattr(get_settings(), "login_rate_limit", 3)
    for _ in range(3):
        assert anon_client.post(LOGIN, json={"email": user.email, "password": "wrong"}).status_code == 401
    blocked = anon_client.post(LOGIN, json={"email": user.email, "password": DEFAULT_PASSWORD})
    assert blocked.status_code == 429
    assert blocked.json() == {"detail": "Too many attempts. Try again later."}
    assert int(blocked.headers["retry-after"]) > 0


def test_registration_is_throttled(anon_client, monkeypatch):
    monkeypatch.setattr(get_settings(), "register_rate_limit", 2)
    assert _register(anon_client, email="r1@example.com").status_code == 201
    assert _register(anon_client, email="r2@example.com").status_code == 201
    assert _register(anon_client, email="r3@example.com").status_code == 429


# ================================================================ every protected route

PUBLIC = {("GET", "/"), ("GET", "/api/v1/health"), ("GET", "/api/v1/health/db"), ("POST", REGISTER), ("POST", LOGIN)}


def _api_routes():
    for route in app.routes:
        if isinstance(route, APIRoute):
            for method in route.methods - {"HEAD", "OPTIONS"}:
                yield method, route.path


def test_the_public_routes_are_exactly_the_expected_ones():
    assert PUBLIC <= set(_api_routes())


@pytest.mark.parametrize("method,path", sorted(set(_api_routes()) - PUBLIC))
def test_every_other_route_requires_an_account(anon_client, method, path):
    url = path
    for name in ("assessment_id", "organization_id", "site_id", "area_id", "task_id"):
        url = url.replace("{" + name + "}", str(uuid.uuid4()))
    response = anon_client.request(method, url)
    assert response.status_code == 401, (method, path, response.status_code)


# ================================================================ other accounts' data

@pytest.fixture
def two_accounts(db, real_result):
    """Alice and Bob, each with one completed assessment and a generated report."""
    alice = make_user(db, "alice@example.com", "Alice")
    bob = make_user(db, "bob@example.com", "Bob")
    records = {}
    for person in (alice, bob):
        record = analyses.start_analysis(db, file_name=f"{person.name}.mp4", content_type="video/mp4", size_bytes=1, owner=person)
        assert analyses.complete_analysis(db, record, copy.deepcopy(real_result)) is None
        db.refresh(record)
        reports.generate_report(record)
        records[person.name] = record
    return alice, bob, records


def test_an_account_cannot_reach_another_accounts_assessments(two_accounts):
    alice, bob, records = two_accounts
    theirs = records["Bob"].id
    client = client_for(alice)

    for method, url in [
        ("GET", f"/api/v1/assessments/{theirs}"),
        ("GET", f"/api/v1/assessments/{theirs}/report"),
        ("GET", f"/api/v1/assessments/{theirs}/report/pdf"),
        ("POST", f"/api/v1/assessments/{theirs}/report"),
        ("POST", f"/api/v1/assessments/{theirs}/results"),
        ("POST", f"/api/v1/assessments/{theirs}/process-video"),
        ("POST", f"/api/v1/assessments/{theirs}/upload-video"),
        ("PUT", f"/api/v1/assessments/{theirs}"),
        ("DELETE", f"/api/v1/assessments/{theirs}"),
    ]:
        kwargs = {}
        if url.endswith("/results"):
            kwargs["json"] = {"cv_result": {}}
        elif url.endswith("/process-video"):
            kwargs["json"] = {"video_path": "x.mp4"}
        elif url.endswith("/upload-video"):
            kwargs["files"] = {"file": ("x.mp4", b"x", "video/mp4")}
        response = client.request(method, url, **kwargs)
        # PUT validates its body before ownership; anything but a leak is acceptable there.
        assert response.status_code in ((404, 422) if method == "PUT" else (404,)), (method, url, response.status_code)
        assert "Bob" not in response.text

    listed = client.get("/api/v1/assessments").json()
    assert [i["id"] for i in listed["items"]] == [str(records["Alice"].id)]
    overview = client.get("/api/v1/overview").json()
    assert overview["total_assessments"] == 1
    assert [i["id"] for i in overview["latest"]] == [str(records["Alice"].id)]

    # Bob's assessment and report are untouched, and still his.
    bob_client = client_for(bob)
    assert bob_client.get(f"/api/v1/assessments/{theirs}").status_code == 200
    assert bob_client.get(f"/api/v1/assessments/{theirs}/report/pdf").content.startswith(b"%PDF")


def test_new_analyses_belong_to_the_signed_in_account(db, user, real_result, monkeypatch):
    from app.api.routes import cv_analysis

    monkeypatch.setattr(cv_analysis.batch_processor, "process_video", lambda **_: copy.deepcopy(real_result))
    client = client_for(user)
    body = client.post("/api/v1/analyze-video", files={"file": ("clip.mp4", b"video bytes", "video/mp4")}).json()
    record = db.get(Assessment, uuid.UUID(body["assessment_id"]))
    assert record.assessor_id == user.id
    assert record.organization_id == user.organization_id
    # Nobody else sees it.
    other = client_for(make_user(db, "other@example.com", "Other"))
    assert other.get(f"/api/v1/assessments/{record.id}").status_code == 404


def test_a_client_supplied_owner_is_ignored(db, user):
    """POST /assessments takes the owner from the token, and only the caller's organization."""
    site = Site(organization_id=user.organization_id, name="S")
    db.add(site)
    db.flush()
    area = Area(site_id=site.id, name="A")
    db.add(area)
    db.flush()
    task = Task(area_id=area.id, name="T")
    db.add(task)
    db.commit()
    other = make_user(db, "other@example.com", "Other")
    payload = {"organization_id": str(user.organization_id), "site_id": str(site.id), "area_id": str(area.id),
               "task_id": str(task.id), "assessor_id": str(other.id), "status": "draft", "consent_given": False}

    created = client_for(user).post("/api/v1/assessments", json=payload)
    assert created.status_code == 201
    assert created.json()["assessor_id"] == str(user.id)
    # Another account cannot create assessments in this organization or use its hierarchy.
    assert client_for(other).post("/api/v1/assessments", json=payload).status_code == 404
    mine_org = {**payload, "organization_id": str(other.organization_id)}
    assert client_for(other).post("/api/v1/assessments", json=mine_org).status_code == 404


def test_hierarchy_is_scoped_to_the_accounts_organization(db, user):
    other = make_user(db, "other@example.com", "Other")
    site = client_for(user).post("/api/v1/sites", json={"organization_id": str(user.organization_id), "name": "Mine"})
    assert site.status_code == 201
    site_id = site.json()["id"]
    them = client_for(other)
    assert them.get(f"/api/v1/sites/{site_id}").status_code == 404
    assert them.get("/api/v1/sites").json() == []
    assert them.post("/api/v1/sites", json={"organization_id": str(user.organization_id), "name": "X"}).status_code == 404
    assert them.post("/api/v1/areas", json={"site_id": site_id, "name": "X"}).status_code == 404
    assert them.get(f"/api/v1/organizations/{user.organization_id}").status_code == 404
    assert [o["id"] for o in them.get("/api/v1/organizations").json()] == [str(other.organization_id)]
    assert them.post("/api/v1/organizations", json={"name": "New"}).status_code == 403


# ================================================================ unowned historical assessments

def test_unowned_assessments_are_visible_to_no_account(db, user, real_result):
    legacy = analyses.start_analysis(db, file_name="old.mp4", content_type="video/mp4", size_bytes=1, owner=None)
    assert analyses.complete_analysis(db, legacy, copy.deepcopy(real_result)) is None
    for person in (user, make_user(db, "other@example.com", "Other")):
        client = client_for(person)
        assert client.get(f"/api/v1/assessments/{legacy.id}").status_code == 404
        assert client.post(f"/api/v1/assessments/{legacy.id}/report").status_code == 404
        assert client.get("/api/v1/assessments").json()["total"] == 0
        assert client.get("/api/v1/overview").json()["total_assessments"] == 0
    # The record itself is kept.
    db.expire_all()
    assert db.get(Assessment, legacy.id).assessor_id is None


def test_operator_assigns_an_unowned_assessment_explicitly(db, user, real_result, capsys):
    legacy = analyses.start_analysis(db, file_name="old.mp4", content_type="video/mp4", size_bytes=1, owner=None)
    assert analyses.complete_analysis(db, legacy, copy.deepcopy(real_result)) is None

    assert assign_assessments.main(["--list"]) == 0
    assert str(legacy.id) in capsys.readouterr().out
    assert assign_assessments.main(["--email", user.email, "--id", str(legacy.id), "--dry-run"]) == 0
    db.expire_all()
    assert db.get(Assessment, legacy.id).assessor_id is None

    assert assign_assessments.main(["--email", user.email, "--id", str(legacy.id)]) == 0
    db.expire_all()
    assert db.get(Assessment, legacy.id).assessor_id == user.id
    assert client_for(user).get(f"/api/v1/assessments/{legacy.id}").status_code == 200

    # An owned assessment is never moved to someone else.
    other = make_user(db, "other@example.com", "Other")
    assert assign_assessments.main(["--email", other.email, "--id", str(legacy.id)]) == 1
    db.expire_all()
    assert db.get(Assessment, legacy.id).assessor_id == user.id


def test_unknown_account_or_assessment_changes_nothing(db, user):
    assert assign_assessments.main(["--email", "nobody@example.com", "--id", str(uuid.uuid4())]) == 1
    assert assign_assessments.main(["--email", user.email, "--id", str(uuid.uuid4())]) == 1


def test_sensitive_values_are_not_logged(anon_client, user, caplog):
    caplog.set_level("DEBUG")
    anon_client.post(LOGIN, json={"email": user.email, "password": DEFAULT_PASSWORD})
    anon_client.post(LOGIN, json={"email": user.email, "password": "wrong-password-xyz"})
    token = bearer(user)["Authorization"].split()[1]
    anon_client.get(ME, headers={"Authorization": f"Bearer {token}"})
    assert DEFAULT_PASSWORD not in caplog.text and "wrong-password-xyz" not in caplog.text
    assert token not in caplog.text


def test_unauthenticated_client_has_no_default_headers():
    assert "authorization" not in {k.lower() for k in TestClient(app).headers}


# ================================================================ account recovery

def test_operator_password_reset_replaces_the_password_and_ends_sessions(anon_client, db, user, monkeypatch, capsys):
    from app.scripts import set_password

    old = bearer(user)
    monkeypatch.setenv("VIGILEX_NEW_PASSWORD", "a-brand-new-password")
    assert set_password.main(["--email", user.email]) == 0
    assert "a-brand-new-password" not in capsys.readouterr().out
    assert anon_client.get(ME, headers=old).status_code == 401
    assert anon_client.post(LOGIN, json={"email": user.email, "password": DEFAULT_PASSWORD}).status_code == 401
    assert anon_client.post(LOGIN, json={"email": user.email, "password": "a-brand-new-password"}).status_code == 200
    monkeypatch.setenv("VIGILEX_NEW_PASSWORD", "short")
    assert set_password.main(["--email", user.email]) == 1
    assert set_password.main(["--email", "nobody@example.com"]) == 1
