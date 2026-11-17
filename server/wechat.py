"""服务端微信登录：小程序只提交 code，openid 由微信接口返回。"""
from __future__ import annotations

import json
import logging
import urllib.parse
import urllib.request

from config import Config
from security import ApiError

log = logging.getLogger("renovation.wechat")


def code_to_session(code: str) -> dict:
    if not Config.WECHAT_APP_ID or not Config.WECHAT_APP_SECRET:
        raise ApiError(503, "wechat_not_configured", "服务端未配置微信小程序 AppID/AppSecret")
    if not code or len(code) > 128:
        raise ApiError(400, "invalid_request", "缺少微信登录 code")

    query = urllib.parse.urlencode(
        {
            "appid": Config.WECHAT_APP_ID,
            "secret": Config.WECHAT_APP_SECRET,
            "js_code": code,
            "grant_type": "authorization_code",
        }
    )
    url = f"https://api.weixin.qq.com/sns/jscode2session?{query}"
    try:
        with urllib.request.urlopen(url, timeout=6) as response:
            body = json.loads(response.read().decode("utf-8"))
    except Exception as exc:
        log.warning("code2Session request failed: %s", exc)
        raise ApiError(503, "wechat_unavailable", "微信登录服务暂时不可用") from exc

    if body.get("errcode"):
        log.warning("code2Session rejected: errcode=%s", body.get("errcode"))
        raise ApiError(401, "wechat_login_failed", "微信登录凭证无效")
    openid = body.get("openid")
    if not openid:
        raise ApiError(401, "wechat_login_failed", "微信登录未返回 openid")
    return {"openid": openid, "session_key": body.get("session_key", ""), "unionid": body.get("unionid", "")}
