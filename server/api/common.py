"""API 公共工具：响应、校验、ID、设置、导出令牌。"""
from __future__ import annotations

import base64
import csv
import hashlib
import hmac
import io
import json
import secrets
import time
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Any

from flask import jsonify, request

from config import Config
from db import execute, jsonable, query_one
from security import ApiError

AD_SLOT_DEFINITIONS: dict[str, dict[str, Any]] = {
    "home_banner": {
        "key": "home_banner", "name": "首页 Banner", "surface": "home",
        "type": "banner", "copy": "首页底部 Banner，不遮挡记账与进度。", "unitId": Config.AD_SLOT_HOME,
        "ecpm": 68.0,
    },
    "expenses_feed": {
        "key": "expenses_feed", "name": "记账页信息流", "surface": "expenses",
        "type": "feed", "copy": "支出列表底部信息流，不插入账目行。", "unitId": Config.AD_SLOT_STATS,
        "ecpm": 42.0,
    },
    "progress_feed": {
        "key": "progress_feed", "name": "进度页信息流", "surface": "progress",
        "type": "feed", "copy": "装修进度底部信息流，不遮挡节点操作。", "unitId": "",
        "ecpm": 38.0,
    },
    "stats_feed": {
        "key": "stats_feed", "name": "统计页信息流", "surface": "stats",
        "type": "feed", "copy": "费用统计底部信息流，不遮挡图表。", "unitId": "",
        "ecpm": 36.0,
    },
    "export_rewarded": {
        "key": "export_rewarded", "name": "导出页激励视频", "surface": "export",
        "type": "rewarded", "copy": "看一次激励视频解锁完整 CSV 导出。", "unitId": Config.AD_SLOT_EXPORT,
        "ecpm": 120.0,
    },
    "export_feed": {
        "key": "export_feed", "name": "导出页底部 Banner", "surface": "export",
        "type": "banner", "copy": "导出页底部 Banner，不遮挡导出按钮。", "unitId": "",
        "ecpm": 58.0,
    },
}

AD_SLOT_KEYS = tuple(AD_SLOT_DEFINITIONS.keys())

DEFAULT_SETTINGS: dict[str, Any] = {
    "ads": {
        "enabled": Config.AD_ENABLED,
        "slots": {
            key: {"enabled": True, **definition}
            for key, definition in AD_SLOT_DEFINITIONS.items()
        },
        "copy": "广告只出现在页面底部与导出解锁处，不遮挡记账、进度与合同操作。",
        "rewarded_ad_required": True,
    },
    "export": {
        "enabled": True,
        "rewarded_ad_required": True,
        "formats": ["csv"],
    },
    "app": {
        "name": "装修记",
        "support_phone": "",
    },
}


def ok(data: Any = None, status: int = 200):
    return jsonify({"ok": True, "data": jsonable(data)}), status


def body() -> dict:
    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        raise ApiError(400, "invalid_request", "请求体必须是 JSON 对象")
    return payload


def text(value: Any, name: str, max_length: int, required: bool = True) -> str:
    if value is None:
        value = ""
    if not isinstance(value, str):
        raise ApiError(400, "invalid_request", f"{name} 必须是字符串")
    value = value.strip()
    if required and not value:
        raise ApiError(400, "invalid_request", f"缺少 {name}")
    if len(value) > max_length:
        raise ApiError(400, "invalid_request", f"{name} 过长")
    return value


def integer(value: Any, name: str, default: int | None = None, minimum: int | None = None,
            maximum: int | None = None) -> int:
    if value is None or value == "":
        if default is None:
            raise ApiError(400, "invalid_request", f"缺少 {name}")
        return default
    try:
        result = int(value)
    except (TypeError, ValueError) as exc:
        raise ApiError(400, "invalid_request", f"{name} 必须是整数") from exc
    if minimum is not None and result < minimum:
        raise ApiError(400, "invalid_request", f"{name} 不能小于 {minimum}")
    if maximum is not None and result > maximum:
        raise ApiError(400, "invalid_request", f"{name} 不能大于 {maximum}")
    return result


def money(value: Any, name: str) -> Decimal:
    try:
        result = Decimal(str(value)).quantize(Decimal("0.01"))
    except (InvalidOperation, TypeError, ValueError) as exc:
        raise ApiError(400, "invalid_request", f"{name} 必须是金额") from exc
    if result <= 0:
        raise ApiError(400, "invalid_request", f"{name} 必须大于 0")
    if result > Decimal("9999999999.99"):
        raise ApiError(400, "invalid_request", f"{name} 超出范围")
    return result


def new_id(prefix: str, size: int = 4) -> str:
    return f"{prefix}-{secrets.token_hex(size).upper()}"


def page_args(default_size: int = 20, max_size: int = 100) -> tuple[int, int]:
    page = integer(request.args.get("page"), "page", 1, 1, 100000)
    size = integer(request.args.get("pageSize"), "pageSize", default_size, 1, max_size)
    return page, size


def get_setting(key: str) -> Any:
    default = DEFAULT_SETTINGS.get(key)
    row = query_one("SELECT value FROM settings WHERE setting_key = %s", (key,))
    if not row:
        return default
    value = row["value"]
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except json.JSONDecodeError:
            return default
    if isinstance(default, dict) and isinstance(value, dict):
        merged = {**default, **value}
        if isinstance(default.get("slots"), dict) and isinstance(value.get("slots"), dict):
            merged["slots"] = {**default["slots"], **value["slots"]}
        return merged
    return value


def save_setting(key: str, value: Any, admin_id: str | None = None) -> None:
    execute(
        """INSERT INTO settings (setting_key, value, updated_by)
           VALUES (%s, %s, %s)
           ON DUPLICATE KEY UPDATE value = VALUES(value), updated_by = VALUES(updated_by)""",
        (key, json.dumps(value, ensure_ascii=False), admin_id),
    )


def ensure_user_project(user_id: str) -> dict:
    """用户首次登录时创建默认项目与 9 个标准节点，保证小程序有可用账本。"""
    project = query_one(
        "SELECT * FROM projects WHERE user_id = %s ORDER BY created_at ASC LIMIT 1", (user_id,)
    )
    if project:
        return project
    project_id = new_id("P")
    execute(
        """INSERT INTO projects (id, user_id, project_name, area, method, budget, current_node, status)
           VALUES (%s, %s, '我的装修', 0, '半包', 0, '开工准备', '在建')""",
        (project_id, user_id),
    )
    nodes = [
        "开工准备", "拆改", "水电", "泥瓦", "木工", "油漆", "安装", "竣工", "软装"
    ]
    for index, name in enumerate(nodes, start=1):
        execute(
            """INSERT INTO nodes (id, project_id, seq, name, status, note)
               VALUES (%s, %s, %s, %s, %s, '')""",
            (f"N-{project_id[2:]}-{index:02d}", project_id, index, name, "待开始"),
        )
    return query_one("SELECT * FROM projects WHERE id = %s", (project_id,))


def project_finance(project_id: str) -> dict | None:
    return query_one("SELECT * FROM v_project_finance WHERE project_id = %s", (project_id,))


def project_for_user(user_id: str) -> dict | None:
    project = ensure_user_project(user_id)
    finance = project_finance(project["id"]) or {}
    return {**project, **{k: v for k, v in finance.items() if k != "project_id"}}


def require_project(user_id: str) -> dict:
    project = project_for_user(user_id)
    if not project:
        raise ApiError(404, "project_not_found", "还没有装修项目")
    return project


def csv_response(filename: str, headers: list[str], rows: list[list[Any]]):
    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow(headers)
    for row in rows:
        writer.writerow(["" if value is None else value for value in row])
    payload = buffer.getvalue()
    from flask import Response

    return Response(
        payload,
        content_type="text/csv; charset=utf-8",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


# ------------------------------------------------------------------
# 导出令牌：服务端签发，客户端不能伪造
# ------------------------------------------------------------------
def _export_key() -> bytes:
    secret = Config.EXPORT_UNLOCK_SECRET or Config.SECRET_KEY
    if len(secret) < 16:
        raise ApiError(503, "export_not_configured", "导出服务未配置密钥")
    return secret.encode("utf-8")


def issue_export_token(user_id: str) -> str:
    expires = int(time.time()) + Config.EXPORT_TOKEN_TTL
    message = f"{user_id}:{expires}".encode("utf-8")
    signature = hmac.new(_export_key(), message, hashlib.sha256).hexdigest()[:32]
    payload = base64.urlsafe_b64encode(message).decode("ascii").rstrip("=")
    return f"{payload}.{signature}"


def verify_export_token(token: str) -> str:
    try:
        payload, signature = token.split(".", 1)
        message = base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4))
        expected = hmac.new(_export_key(), message, hashlib.sha256).hexdigest()[:32]
        if not hmac.compare_digest(expected, signature):
            raise ValueError("bad signature")
        user_id, expires = message.decode("utf-8").split(":", 1)
        if int(expires) < int(time.time()):
            raise ValueError("expired")
        return user_id
    except Exception as exc:
        raise ApiError(401, "export_token_invalid", "导出凭证无效或已过期") from exc


def verify_ad_proof(user_id: str, proof: str) -> bool:
    """演示模式只允许非生产环境；生产必须由广告平台服务端回调签发 proof。"""
    if Config.EXPORT_UNLOCK_MODE == "demo" and Config.APP_ENV != "production":
        return proof in {"demo", "dev"}
    if not Config.EXPORT_UNLOCK_SECRET:
        raise ApiError(503, "export_not_configured", "广告解锁未配置服务端校验密钥")
    day = date.today().isoformat()
    expected = hmac.new(
        Config.EXPORT_UNLOCK_SECRET.encode("utf-8"),
        f"{user_id}:{day}".encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    return hmac.compare_digest(expected, proof)


def now_text() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")
