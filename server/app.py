"""装修记 Flask 服务入口。

运行：
    python app.py
生产：
    gunicorn -w 2 -b 0.0.0.0:5000 wsgi:app
"""
from __future__ import annotations

import logging

from flask import Flask, g, jsonify, request

from api.admin import bp as admin_bp
from api.auth import bp as auth_bp
from api.common import jsonable, ok
from api.user import bp as user_bp, register_uploads
from config import Config, validate_secrets
from db import close_db, query_one, rollback
from security import ApiError, api_limiter, api_rate_limit_key

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("renovation.app")


def create_app() -> Flask:
    app = Flask(__name__)
    app.config.update(
        SECRET_KEY=Config.SECRET_KEY,
        MAX_CONTENT_LENGTH=Config.MAX_UPLOAD_BYTES + 1024 * 1024,
        JSON_SORT_KEYS=False,
    )
    app.json.ensure_ascii = False

    problems = validate_secrets()
    if problems:
        if Config.APP_ENV == "production":
            raise RuntimeError("生产环境配置缺失：" + "；".join(problems))
        log.warning("开发环境配置不完整：%s", "；".join(problems))

    app.register_blueprint(auth_bp, url_prefix="/api")
    app.register_blueprint(user_bp, url_prefix="/api")
    app.register_blueprint(admin_bp, url_prefix="/api")
    register_uploads(app)

    @app.before_request
    def limit_api():
        if request.method == "OPTIONS":
            return None
        if not request.path.startswith("/api/"):
            return None
        if request.path == "/api/health":
            return None
        if not api_limiter.allow(api_rate_limit_key()):
            raise ApiError(429, "rate_limited", "请求过于频繁，请稍后再试")
        return None

    @app.after_request
    def security_headers(response):
        response.headers.setdefault("X-Content-Type-Options", "nosniff")
        response.headers.setdefault("X-Frame-Options", "DENY")
        response.headers.setdefault("Referrer-Policy", "no-referrer")
        origin = request.headers.get("Origin", "")
        if origin and origin in Config.CORS_ORIGINS:
            response.headers["Access-Control-Allow-Origin"] = origin
            response.headers["Vary"] = "Origin"
            response.headers["Access-Control-Allow-Credentials"] = "true"
            response.headers["Access-Control-Allow-Headers"] = "Authorization, Content-Type, X-Device-Label"
            response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, PATCH, DELETE, OPTIONS"
        return response

    @app.errorhandler(ApiError)
    def api_error(error: ApiError):
        try:
            rollback()
        except Exception:
            pass
        payload = {"ok": False, "error": {"code": error.code, "message": error.message}}
        if error.details is not None:
            payload["error"]["details"] = jsonable(error.details)
        return jsonify(payload), error.status

    @app.errorhandler(404)
    def not_found(_error):
        return jsonify({"ok": False, "error": {"code": "not_found", "message": "接口不存在"}}), 404

    @app.errorhandler(405)
    def method_not_allowed(_error):
        return jsonify({"ok": False, "error": {"code": "method_not_allowed", "message": "请求方法不支持"}}), 405

    @app.errorhandler(413)
    def too_large(_error):
        return jsonify({"ok": False, "error": {"code": "file_too_large", "message": "上传内容过大"}}), 413

    @app.errorhandler(Exception)
    def unhandled(error):
        try:
            rollback()
        except Exception:
            pass
        log.exception("unhandled error: %s", error)
        return jsonify({"ok": False, "error": {"code": "internal_error", "message": "服务暂时不可用"}}), 500

    @app.get("/api/health")
    def health():
        try:
            query_one("SELECT 1 AS ok")
        except Exception:
            return jsonify({"ok": False, "service": "renovation-server", "error": "database_unavailable"}), 503
        return ok({"service": "renovation-server", "time": __import__("datetime").datetime.now().strftime("%Y-%m-%d %H:%M:%S")})

    @app.teardown_appcontext
    def teardown(_error):
        close_db(_error)

    @app.after_request
    def log_request(response):
        if request.path.startswith("/api/"):
            log.info("%s %s -> %s", request.method, request.path, response.status_code)
        return response

    return app


app = create_app()

if __name__ == "__main__":
    app.run(host=Config.HOST, port=Config.PORT, debug=Config.DEBUG)
