"""认证路由：管理员登录/刷新/登出，小程序用户登录/刷新/登出。"""
from __future__ import annotations

from datetime import datetime, timedelta

from flask import Blueprint, g, request

from api.common import body, new_id, ok, text, project_for_user
from config import Config
from db import commit, execute, query_one, transaction
from security import (
    ApiError,
    admin_required,
    client_ip,
    decrypt_secret,
    dummy_verify,
    hash_refresh,
    login_limiter,
    new_refresh_token,
    refresh_expiry,
    sign_admin_access,
    sign_user_access,
    user_required,
    verify_password,
    verify_totp,
    write_audit,
    ROLE_MATRIX,
)
from wechat import code_to_session

bp = Blueprint("auth", __name__)


def _device_label() -> str:
    return (request.headers.get("X-Device-Label") or request.user_agent.string or "")[:80]


def _admin_permissions(admin: dict) -> list[str]:
    if admin["is_super"]:
        return ["*"]
    return sorted(ROLE_MATRIX.get(admin["role"], set()))


def _admin_payload(admin: dict, access: str, refresh: str) -> dict:
    return {
        "accessToken": access,
        "accessExpiresIn": Config.ADMIN_ACCESS_TTL,
        "refreshToken": refresh,
        "refreshExpiresIn": Config.REFRESH_TTL,
        "admin": {
            "id": admin["id"],
            "name": admin["name"],
            "phone": admin["phone"],
            "role": admin["role"],
            "city": admin["city"],
            "isSuper": bool(admin["is_super"]),
            "mfaEnabled": bool(admin["mfa_enabled"]),
            "permissions": _admin_permissions(admin),
        },
    }


def _user_payload(user: dict, access: str, refresh: str) -> dict:
    return {
        "accessToken": access,
        "accessExpiresIn": Config.USER_ACCESS_TTL,
        "refreshToken": refresh,
        "refreshExpiresIn": Config.REFRESH_TTL,
        "user": {
            "id": user["id"],
            "name": user["name"],
            "phone": user["phone"],
            "city": user["city"],
        },
    }


def _record_admin_failure(admin: dict, reason: str) -> None:
    count = int(admin.get("failed_login_count") or 0) + 1
    locked_until = None
    if count >= Config.ADMIN_MAX_FAILED_LOGIN:
        locked_until = datetime.now() + timedelta(minutes=Config.ADMIN_LOCK_MINUTES)
        count = 0
    execute(
        "UPDATE admins SET failed_login_count = %s, locked_until = %s WHERE id = %s",
        (count, locked_until, admin["id"]),
    )
    write_audit(admin["id"], "admin.login.failed", "admin", admin["id"], {"reason": reason})
    commit()


@bp.post("/admin/login")
def admin_login():
    data = body()
    phone = text(data.get("phone"), "phone", 20)
    password = text(data.get("password"), "password", 128)
    mfa_code = text(data.get("mfaCode"), "mfaCode", 12, required=False)

    if not login_limiter.allow(f"{client_ip()}:{phone}"):
        raise ApiError(429, "rate_limited", "登录尝试过于频繁，请稍后再试")

    admin = query_one(
        """SELECT id, name, phone, role, city, status, is_super, mfa_enabled, password_hash,
                  mfa_secret_ciphertext, failed_login_count, locked_until
           FROM admins WHERE phone = %s""",
        (phone,),
    )
    if not admin:
        dummy_verify()
        write_audit(None, "admin.login.failed", "admin", phone, {"reason": "unknown_account"})
        commit()
        raise ApiError(401, "invalid_credentials", "账号或密码不正确")

    if admin["status"] != "在岗":
        raise ApiError(403, "account_disabled", "账号已被停用")

    locked_until = admin.get("locked_until")
    if locked_until and locked_until > datetime.now():
        raise ApiError(423, "account_locked", "登录失败次数过多，账号已临时锁定")

    if not verify_password(password, admin["password_hash"]):
        _record_admin_failure(admin, "bad_password")
        raise ApiError(401, "invalid_credentials", "账号或密码不正确")

    mfa_policy = bool(admin["is_super"]) or Config.ADMIN_MFA_REQUIRED
    if mfa_policy and not bool(admin["mfa_enabled"]):
        raise ApiError(403, "mfa_setup_required", "该账号必须先绑定 MFA 才能登录")
    if bool(admin["mfa_enabled"]):
        if not mfa_code:
            raise ApiError(401, "mfa_required", "请输入 MFA 动态码")
        try:
            secret = decrypt_secret(admin["mfa_secret_ciphertext"])
        except Exception as exc:
            raise ApiError(500, "mfa_config_error", "MFA 配置异常，请联系超级管理员") from exc
        if not verify_totp(secret, mfa_code):
            _record_admin_failure(admin, "bad_mfa")
            raise ApiError(401, "invalid_mfa", "MFA 动态码不正确")

    access = sign_admin_access(admin["id"], admin["role"], bool(admin["is_super"]))
    refresh = new_refresh_token()
    execute(
        """UPDATE admins SET failed_login_count = 0, locked_until = NULL,
                  last_login_at = NOW(), last_login_ip = %s WHERE id = %s""",
        (client_ip(), admin["id"]),
    )
    execute(
        """INSERT INTO admin_sessions (admin_id, token_hash, expires_at, device_label, ip)
           VALUES (%s, %s, %s, %s, %s)""",
        (admin["id"], hash_refresh(refresh), refresh_expiry(), _device_label(), client_ip()),
    )
    write_audit(admin["id"], "admin.login", "admin", admin["id"], {"device": _device_label()})
    commit()
    return ok(_admin_payload(admin, access, refresh))


@bp.post("/admin/refresh")
def admin_refresh():
    data = body()
    refresh_token = text(data.get("refreshToken"), "refreshToken", 512)
    token_hash = hash_refresh(refresh_token)
    session = query_one(
        """SELECT s.id AS session_id, s.expires_at, s.revoked_at, a.id, a.name, a.phone, a.role,
                  a.city, a.status, a.is_super, a.mfa_enabled
           FROM admin_sessions s JOIN admins a ON a.id = s.admin_id
           WHERE s.token_hash = %s AND s.revoked_at IS NULL LIMIT 1""",
        (token_hash,),
    )
    if not session or session["expires_at"] <= datetime.now():
        raise ApiError(401, "invalid_refresh", "登录状态已过期，请重新登录")
    if session["status"] != "在岗":
        raise ApiError(403, "account_disabled", "账号已被停用")

    new_token = new_refresh_token()
    try:
        with transaction() as db:
            with db.cursor() as cursor:
                cursor.execute(
                    "UPDATE admin_sessions SET revoked_at = NOW() WHERE id = %s AND revoked_at IS NULL",
                    (session["session_id"],),
                )
                if cursor.rowcount != 1:
                    raise ApiError(401, "refresh_reused", "刷新令牌已被使用")
                cursor.execute(
                    """INSERT INTO admin_sessions (admin_id, token_hash, expires_at, device_label, ip)
                       VALUES (%s, %s, %s, %s, %s)""",
                    (session["id"], hash_refresh(new_token), refresh_expiry(), _device_label(), client_ip()),
                )
            write_audit(session["id"], "admin.session.refreshed", "admin", session["id"])
    except ApiError:
        raise
    access = sign_admin_access(session["id"], session["role"], bool(session["is_super"]))
    return ok(_admin_payload(session, access, new_token))


@bp.post("/admin/logout")
def admin_logout():
    data = request.get_json(silent=True) or {}
    refresh_token = data.get("refreshToken") if isinstance(data, dict) else None
    if isinstance(refresh_token, str) and refresh_token:
        token_hash = hash_refresh(refresh_token)
        session = query_one(
            "SELECT id, admin_id FROM admin_sessions WHERE token_hash = %s AND revoked_at IS NULL LIMIT 1",
            (token_hash,),
        )
        execute("UPDATE admin_sessions SET revoked_at = NOW() WHERE token_hash = %s", (token_hash,))
        if session:
            write_audit(session["admin_id"], "admin.logout", "admin", session["admin_id"])
        commit()
    return ok({"loggedOut": True})


@bp.get("/admin/me")
@admin_required
def admin_me():
    admin = g.admin
    return ok(
        {
            "id": admin["id"],
            "name": admin["name"],
            "phone": admin["phone"],
            "role": admin["role"],
            "city": admin["city"],
            "isSuper": admin["is_super"],
            "mfaEnabled": admin["mfa_enabled"],
            "permissions": _admin_permissions(admin),
        }
    )


# ------------------------------------------------------------------
# 小程序用户
# ------------------------------------------------------------------
@bp.post("/user/login")
def user_login():
    data = body()
    code = text(data.get("code"), "code", 128)

    if code == "demo":
        if not (Config.ALLOW_DEMO_LOGIN and Config.APP_ENV != "production"):
            raise ApiError(403, "demo_login_disabled", "演示登录已关闭")
        openid = "demo-openid-local"
    else:
        session = code_to_session(code)
        openid = session["openid"]

    user = query_one("SELECT id, name, phone, city, status, openid FROM users WHERE openid = %s", (openid,))
    if not user:
        user_id = new_id("U")
        name = text(data.get("name"), "name", 40, required=False) or f"微信用户{openid[-4:]}"
        execute(
            """INSERT INTO users (id, openid, name, phone, city, status)
               VALUES (%s, %s, %s, %s, %s, '正常')""",
            (user_id, openid, name, text(data.get("phone"), "phone", 20, required=False), "重庆"),
        )
        user = query_one("SELECT id, name, phone, city, status, openid FROM users WHERE id = %s", (user_id,))
        write_audit(None, "user.register", "user", user_id, {"channel": "wechat"})
    elif user["status"] != "正常":
        raise ApiError(403, "account_disabled", "账号已被停用")

    project_for_user(user["id"])
    access = sign_user_access(user["id"])
    refresh = new_refresh_token()
    execute(
        """INSERT INTO user_sessions (user_id, token_hash, expires_at, device_label, ip)
           VALUES (%s, %s, %s, %s, %s)""",
        (user["id"], hash_refresh(refresh), refresh_expiry(), _device_label(), client_ip()),
    )
    write_audit(None, "user.login", "user", user["id"], {"device": _device_label()})
    commit()
    return ok(_user_payload(user, access, refresh))


@bp.post("/user/refresh")
def user_refresh():
    data = body()
    refresh_token = text(data.get("refreshToken"), "refreshToken", 512)
    session = query_one(
        """SELECT s.id AS session_id, s.expires_at, s.user_id, u.id, u.name, u.phone, u.city, u.status
           FROM user_sessions s JOIN users u ON u.id = s.user_id
           WHERE s.token_hash = %s AND s.revoked_at IS NULL LIMIT 1""",
        (hash_refresh(refresh_token),),
    )
    if not session or session["expires_at"] <= datetime.now():
        raise ApiError(401, "invalid_refresh", "登录状态已过期，请重新登录")
    if session["status"] != "正常":
        raise ApiError(403, "account_disabled", "账号已被停用")

    new_token = new_refresh_token()
    with transaction() as db:
        with db.cursor() as cursor:
            cursor.execute(
                "UPDATE user_sessions SET revoked_at = NOW() WHERE id = %s AND revoked_at IS NULL",
                (session["session_id"],),
            )
            if cursor.rowcount != 1:
                raise ApiError(401, "refresh_reused", "刷新令牌已被使用")
            cursor.execute(
                """INSERT INTO user_sessions (user_id, token_hash, expires_at, device_label, ip)
                   VALUES (%s, %s, %s, %s, %s)""",
                (session["user_id"], hash_refresh(new_token), refresh_expiry(), _device_label(), client_ip()),
            )
        write_audit(None, "user.session.refreshed", "user", session["user_id"])

    access = sign_user_access(session["user_id"])
    return ok(_user_payload(session, access, new_token))


@bp.post("/user/logout")
def user_logout():
    data = request.get_json(silent=True) or {}
    refresh_token = data.get("refreshToken") if isinstance(data, dict) else None
    if isinstance(refresh_token, str) and refresh_token:
        token_hash = hash_refresh(refresh_token)
        session = query_one(
            "SELECT id, user_id FROM user_sessions WHERE token_hash = %s AND revoked_at IS NULL LIMIT 1",
            (token_hash,),
        )
        execute("UPDATE user_sessions SET revoked_at = NOW() WHERE token_hash = %s", (token_hash,))
        if session:
            write_audit(None, "user.logout", "user", session["user_id"])
        commit()
    return ok({"loggedOut": True})


@bp.get("/me")
@user_required
def user_me():
    user = g.user
    return ok(
        {
            "id": user["id"],
            "name": user["name"],
            "phone": user["phone"],
            "city": user["city"],
            "project": project_for_user(user["id"]),
        }
    )
