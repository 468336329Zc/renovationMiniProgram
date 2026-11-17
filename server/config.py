"""装修记服务端配置。

所有密钥只从环境变量读取；代码与客户端包内不出现生产密钥。
"""
from __future__ import annotations

import os
from datetime import timedelta
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent
load_dotenv(BASE_DIR / ".env")


def _int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except (TypeError, ValueError):
        return default


def _bool(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _seconds(name: str, default: str) -> int:
    """支持 15m / 7d / 3600 这类写法。"""
    raw = os.getenv(name, default).strip().lower()
    try:
        if raw.endswith("m"):
            return int(raw[:-1]) * 60
        if raw.endswith("h"):
            return int(raw[:-1]) * 3600
        if raw.endswith("d"):
            return int(raw[:-1]) * 86400
        return int(raw)
    except ValueError:
        return int(default.rstrip("smhd")) * (60 if default.endswith("m") else 1)


class Config:
    APP_ENV = os.getenv("APP_ENV", "development")
    DEBUG = _bool("DEBUG", APP_ENV != "production")
    HOST = os.getenv("HOST", "0.0.0.0")
    PORT = _int("PORT", 5000)
    SECRET_KEY = os.getenv("SECRET_KEY", "dev-only-change-me")
    TRUST_PROXY = _bool("TRUST_PROXY", False)

    # MySQL：只允许 API 服务访问
    DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
    DB_PORT = _int("DB_PORT", 3306)
    DB_NAME = os.getenv("DB_NAME", "renovation")
    DB_USER = os.getenv("DB_USER", "renovation_app")
    DB_PASSWORD = os.getenv("DB_PASSWORD", "change-me")
    DB_CHARSET = "utf8mb4"
    DB_CONNECT_TIMEOUT = _int("DB_CONNECT_TIMEOUT", 5)
    DB_READ_TIMEOUT = _int("DB_READ_TIMEOUT", 10)
    DB_WRITE_TIMEOUT = _int("DB_WRITE_TIMEOUT", 10)
    DB_TIMEZONE = os.getenv("DB_TIMEZONE", "+08:00")

    # 管理员与用户令牌完全隔离
    JWT_ADMIN_SECRET = os.getenv("JWT_ADMIN_SECRET", "")
    JWT_USER_SECRET = os.getenv("JWT_USER_SECRET", "")
    JWT_ISSUER = os.getenv("JWT_ISSUER", "renovation-api")
    ADMIN_JWT_AUDIENCE = "admin"
    USER_JWT_AUDIENCE = "user"
    ADMIN_ACCESS_TTL = _seconds("ADMIN_ACCESS_TTL", "15m")
    USER_ACCESS_TTL = _seconds("USER_ACCESS_TTL", "30m")
    REFRESH_TTL = _seconds("REFRESH_TTL", "7d")

    # 安全策略
    BCRYPT_ROUNDS = _int("BCRYPT_ROUNDS", 12)
    ADMIN_MAX_FAILED_LOGIN = _int("ADMIN_MAX_FAILED_LOGIN", 5)
    ADMIN_LOCK_MINUTES = _int("ADMIN_LOCK_MINUTES", 15)
    ADMIN_MFA_REQUIRED = _bool("ADMIN_MFA_REQUIRED", True)
    MFA_SECRET_KEY = os.getenv("MFA_SECRET_KEY", "")

    # 限流
    LOGIN_RATE_WINDOW_SECONDS = _int("LOGIN_RATE_WINDOW_SECONDS", 60)
    LOGIN_RATE_MAX = _int("LOGIN_RATE_MAX", 5)
    API_RATE_WINDOW_SECONDS = _int("API_RATE_WINDOW_SECONDS", 60)
    API_RATE_MAX = _int("API_RATE_MAX", 120)

    # 微信小程序；Secret 只在服务端
    WECHAT_APP_ID = os.getenv("WECHAT_APP_ID", "")
    WECHAT_APP_SECRET = os.getenv("WECHAT_APP_SECRET", "")
    # 仅开发/演示环境允许 code=demo 直接建用户；生产必须为 false
    ALLOW_DEMO_LOGIN = _bool("ALLOW_DEMO_LOGIN", APP_ENV != "production")

    # 导出激励视频：服务端签发一次性导出令牌
    EXPORT_UNLOCK_MODE = os.getenv("EXPORT_UNLOCK_MODE", "demo")
    EXPORT_UNLOCK_SECRET = os.getenv("EXPORT_UNLOCK_SECRET", "")
    EXPORT_TOKEN_TTL = _int("EXPORT_TOKEN_TTL", 600)

    # 广告位开关与文案
    AD_ENABLED = _bool("AD_ENABLED", False)
    AD_SLOT_HOME = os.getenv("AD_SLOT_HOME", "")
    AD_SLOT_STATS = os.getenv("AD_SLOT_STATS", "")
    AD_SLOT_EXPORT = os.getenv("AD_SLOT_EXPORT", "")

    # 上传目录：生产应替换为对象存储
    UPLOAD_DIR = Path(os.getenv("UPLOAD_DIR", str(BASE_DIR / "uploads")))
    MAX_UPLOAD_BYTES = _int("MAX_UPLOAD_BYTES", 5 * 1024 * 1024)

    # CORS：逗号分隔；小程序本身不受浏览器 CORS 保护
    CORS_ORIGINS = [x.strip() for x in os.getenv("CORS_ORIGINS", "").split(",") if x.strip()]


def validate_secrets() -> list[str]:
    """返回配置缺口；生产环境缺失时启动失败。"""
    problems: list[str] = []
    if len(Config.JWT_ADMIN_SECRET) < 32:
        problems.append("JWT_ADMIN_SECRET 至少 32 字符")
    if len(Config.JWT_USER_SECRET) < 32:
        problems.append("JWT_USER_SECRET 至少 32 字符")
    if Config.JWT_ADMIN_SECRET and Config.JWT_ADMIN_SECRET == Config.JWT_USER_SECRET:
        problems.append("JWT_ADMIN_SECRET 与 JWT_USER_SECRET 不能相同")
    if Config.ADMIN_MFA_REQUIRED and len(Config.MFA_SECRET_KEY) != 64:
        problems.append("MFA_SECRET_KEY 必须是 64 位 hex（openssl rand -hex 32）")
    return problems
