"""认证、授权、密码、MFA、审计与限流。

安全原则：
- 客户端不可信；角色与账号状态每次从数据库读取；
- 管理员与用户令牌使用不同密钥和 audience；
- refresh token 只存 SHA-256；
- 权限默认拒绝，未登记角色没有任何写权限。
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import logging
import secrets
import threading
import time
from collections import defaultdict, deque
from datetime import datetime, timedelta, timezone
from functools import wraps
from typing import Any, Callable

import bcrypt
import jwt
import pyotp
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from flask import g, request

from config import Config
from db import execute, query_one

log = logging.getLogger("renovation.security")


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str, details: Any = None):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message
        self.details = details


class ConfigError(RuntimeError):
    pass


# ------------------------------------------------------------------
# 令牌
# ------------------------------------------------------------------
def _now() -> datetime:
    return datetime.now(timezone.utc)


def _encode(payload: dict, secret: str, audience: str, ttl: int) -> str:
    if len(secret) < 32:
        raise ConfigError("JWT secret 至少 32 字符")
    now = _now()
    body = {
        **payload,
        "iss": Config.JWT_ISSUER,
        "aud": audience,
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(seconds=ttl)).timestamp()),
        "jti": secrets.token_hex(8),
    }
    return jwt.encode(body, secret, algorithm="HS256")


def sign_admin_access(admin_id: str, role: str, is_super: bool) -> str:
    return _encode(
        {"sub": admin_id, "role": role, "isSuper": bool(is_super), "kind": "admin"},
        Config.JWT_ADMIN_SECRET,
        Config.ADMIN_JWT_AUDIENCE,
        Config.ADMIN_ACCESS_TTL,
    )


def sign_user_access(user_id: str) -> str:
    return _encode(
        {"sub": user_id, "kind": "user"},
        Config.JWT_USER_SECRET,
        Config.USER_JWT_AUDIENCE,
        Config.USER_ACCESS_TTL,
    )


def _decode(token: str, secret: str, audience: str) -> dict:
    try:
        payload = jwt.decode(
            token,
            secret,
            algorithms=["HS256"],
            audience=audience,
            issuer=Config.JWT_ISSUER,
            options={"require": ["exp", "iat", "aud", "iss", "sub"]},
        )
    except jwt.PyJWTError as exc:
        raise ApiError(401, "invalid_token", "登录状态无效，请重新登录") from exc
    return payload


def decode_admin(token: str) -> dict:
    payload = _decode(token, Config.JWT_ADMIN_SECRET, Config.ADMIN_JWT_AUDIENCE)
    if payload.get("kind") != "admin":
        raise ApiError(401, "invalid_token", "令牌类型不匹配")
    return payload


def decode_user(token: str) -> dict:
    payload = _decode(token, Config.JWT_USER_SECRET, Config.USER_JWT_AUDIENCE)
    if payload.get("kind") != "user":
        raise ApiError(401, "invalid_token", "令牌类型不匹配")
    return payload


def new_refresh_token() -> str:
    return secrets.token_urlsafe(48)


def hash_refresh(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def refresh_expiry() -> datetime:
    return datetime.now() + timedelta(seconds=Config.REFRESH_TTL)


def bearer_token() -> str:
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        raise ApiError(401, "missing_token", "请先登录")
    token = header[7:].strip()
    if not token:
        raise ApiError(401, "missing_token", "请先登录")
    return token


# ------------------------------------------------------------------
# 密码
# ------------------------------------------------------------------
COMMON_PASSWORDS = {"12345678", "password", "admin123", "123456789", "qwerty123"}
_DUMMY_HASH = bcrypt.hashpw(b"unknown-account-dummy", bcrypt.gensalt(rounds=Config.BCRYPT_ROUNDS)).decode("utf-8")


def assert_strong_password(password: str) -> None:
    if not password or len(password) < 10:
        raise ApiError(400, "weak_password", "密码至少 10 位")
    if password.lower() in COMMON_PASSWORDS:
        raise ApiError(400, "weak_password", "密码过于常见")
    if not any(c.isupper() for c in password):
        raise ApiError(400, "weak_password", "密码需包含大写字母")
    if not any(c.islower() for c in password):
        raise ApiError(400, "weak_password", "密码需包含小写字母")
    if not any(c.isdigit() for c in password):
        raise ApiError(400, "weak_password", "密码需包含数字")
    if not any(not c.isalnum() for c in password):
        raise ApiError(400, "weak_password", "密码需包含符号")


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=Config.BCRYPT_ROUNDS)).decode("utf-8")


def verify_password(password: str, password_hash: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode("utf-8"), password_hash.encode("utf-8"))
    except (ValueError, TypeError):
        return False


def dummy_verify() -> None:
    """未知账号也做一次等价耗时比较，降低账号枚举信号。"""
    bcrypt.checkpw(b"unknown-account-dummy", _DUMMY_HASH.encode("utf-8"))


# ------------------------------------------------------------------
# MFA
# ------------------------------------------------------------------
def _mfa_key() -> bytes:
    raw = Config.MFA_SECRET_KEY.strip()
    if len(raw) == 64:
        try:
            return bytes.fromhex(raw)
        except ValueError as exc:
            raise ConfigError("MFA_SECRET_KEY 不是合法 hex") from exc
    raise ConfigError("MFA_SECRET_KEY 必须是 64 位 hex")


def new_totp_secret() -> str:
    return pyotp.random_base32()


def encrypt_secret(secret: str) -> bytes:
    aes = AESGCM(_mfa_key())
    nonce = secrets.token_bytes(12)
    return nonce + aes.encrypt(nonce, secret.encode("utf-8"), b"renovation-mfa")


def decrypt_secret(ciphertext: bytes) -> str:
    aes = AESGCM(_mfa_key())
    nonce, data = ciphertext[:12], ciphertext[12:]
    return aes.decrypt(nonce, data, b"renovation-mfa").decode("utf-8")


def verify_totp(secret: str, code: str) -> bool:
    if not code or not code.isdigit() or len(code) != 6:
        return False
    return pyotp.TOTP(secret).verify(code, valid_window=1)


def totp_uri(secret: str, label: str) -> str:
    return pyotp.TOTP(secret).provisioning_uri(name=label, issuer_name="装修记 · 管理端")


# ------------------------------------------------------------------
# RBAC
# ------------------------------------------------------------------
ROLE_MATRIX: dict[str, set[str]] = {
    "超级管理员": {
        "overview:read", "users:read", "users:write", "data:read", "data:write",
        "analytics:read", "admins:read", "admins:write", "settings:read",
        "settings:write", "audit:read", "export:read", "notifications:write",
    },
    "运营管理员": {
        "overview:read", "users:read", "users:write", "data:read", "data:write",
        "analytics:read", "audit:read", "export:read", "notifications:write",
        "settings:read",
    },
    "数据审核": {
        "overview:read", "users:read", "data:read", "data:write",
        "analytics:read", "audit:read", "export:read",
    },
    "只读观察员": {
        "overview:read", "users:read", "data:read", "analytics:read",
    },
}


def has_permission(role: str, is_super: bool, permission: str) -> bool:
    if is_super:
        return True
    return permission in ROLE_MATRIX.get(role, set())


def _load_admin() -> dict:
    token = bearer_token()
    payload = decode_admin(token)
    admin = query_one(
        "SELECT id, name, phone, role, city, status, is_super, mfa_enabled FROM admins WHERE id = %s",
        (payload["sub"],),
    )
    if not admin:
        raise ApiError(401, "invalid_token", "账号不存在")
    if admin["status"] != "在岗":
        raise ApiError(403, "account_disabled", "账号已被停用")
    if admin["role"] != payload.get("role") or bool(admin["is_super"]) != bool(payload.get("isSuper")):
        # 角色变更后旧令牌仍可能有效，以数据库为准；这里不强制拒绝，只覆盖上下文。
        pass
    admin["is_super"] = bool(admin["is_super"])
    admin["mfa_enabled"] = bool(admin["mfa_enabled"])
    return admin


def admin_required(view: Callable):
    @wraps(view)
    def wrapper(*args, **kwargs):
        g.admin = _load_admin()
        return view(*args, **kwargs)

    return wrapper


def permission_required(permission: str):
    def decorator(view: Callable):
        @wraps(view)
        @admin_required
        def wrapper(*args, **kwargs):
            if not has_permission(g.admin["role"], g.admin["is_super"], permission):
                raise ApiError(403, "forbidden", "当前角色没有该操作权限")
            return view(*args, **kwargs)

        return wrapper

    return decorator


def user_required(view: Callable):
    @wraps(view)
    def wrapper(*args, **kwargs):
        payload = decode_user(bearer_token())
        user = query_one(
            "SELECT id, name, phone, city, status, openid, created_at FROM users WHERE id = %s",
            (payload["sub"],),
        )
        if not user:
            raise ApiError(401, "invalid_token", "用户不存在")
        if user["status"] != "正常":
            raise ApiError(403, "account_disabled", "账号已被停用")
        g.user = user
        return view(*args, **kwargs)

    return wrapper


# ------------------------------------------------------------------
# 审计
# ------------------------------------------------------------------
def write_audit(
    admin_id: str | None,
    action: str,
    object_type: str = "",
    object_id: str = "",
    detail: dict | None = None,
) -> None:
    try:
        execute(
            """INSERT INTO audit_logs (admin_id, action, object_type, object_id, detail, ip, user_agent)
               VALUES (%s, %s, %s, %s, %s, %s, %s)""",
            (
                admin_id,
                action,
                object_type,
                object_id,
                json.dumps(detail, ensure_ascii=False) if detail else None,
                client_ip(),
                (request.user_agent.string or "")[:255],
            ),
        )
    except Exception:  # 审计失败不阻断主流程，但必须留下服务端日志
        log.exception("audit write failed: %s", action)


def client_ip() -> str:
    if Config.TRUST_PROXY:
        forwarded = request.headers.get("X-Forwarded-For", "").split(",")[0].strip()
        if forwarded:
            return forwarded[:64]
    return (request.remote_addr or "")[:64]


# ------------------------------------------------------------------
# 内存限流
# ------------------------------------------------------------------
class SlidingWindow:
    def __init__(self, limit: int, window_seconds: int):
        self.limit = limit
        self.window = window_seconds
        self._hits: dict[str, deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def allow(self, key: str) -> bool:
        now = time.monotonic()
        with self._lock:
            bucket = self._hits[key]
            while bucket and bucket[0] <= now - self.window:
                bucket.popleft()
            if len(bucket) >= self.limit:
                return False
            bucket.append(now)
            return True

    def hit(self, key: str) -> None:
        now = time.monotonic()
        with self._lock:
            self._hits[key].append(now)


api_limiter = SlidingWindow(Config.API_RATE_MAX, Config.API_RATE_WINDOW_SECONDS)
login_limiter = SlidingWindow(Config.LOGIN_RATE_MAX, Config.LOGIN_RATE_WINDOW_SECONDS)


def api_rate_limit_key() -> str:
    admin = getattr(g, "admin", None)
    if admin:
        return f"admin:{admin['id']}"
    user = getattr(g, "user", None)
    if user:
        return f"user:{user['id']}"
    return f"ip:{client_ip()}"
