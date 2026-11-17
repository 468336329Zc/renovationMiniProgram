"""管理员 API：概览、用户、数据审查、运营统计、管理员、设置与审计。"""
from __future__ import annotations

import csv
import io
from datetime import date, datetime, timedelta
from decimal import Decimal

from flask import Blueprint, g, request

from api.common import (
    body,
    csv_response,
    get_setting,
    integer,
    new_id,
    ok,
    page_args,
    save_setting,
    text,
)
from config import Config
from db import commit, execute, query_all, query_one
from security import (
    ApiError,
    assert_strong_password,
    encrypt_secret,
    hash_password,
    new_totp_secret,
    permission_required,
    totp_uri,
    write_audit,
)

bp = Blueprint("admin", __name__)

RISK_STATUS = {"正常", "超支", "异常", "待核"}
ISSUE_LEVELS = {"高", "中", "低"}
ISSUE_STATUS = {"待解决", "已解决", "已转交"}
ROLES = {"超级管理员", "运营管理员", "数据审核", "只读观察员"}


def _range_days() -> int:
    raw = request.args.get("range", "7").strip()
    if raw not in {"7", "30", "90"}:
        raise ApiError(400, "invalid_request", "range 只支持 7/30/90")
    return int(raw)


def _project_of_user(user_id: str) -> dict | None:
    return query_one("SELECT * FROM projects WHERE user_id = %s ORDER BY created_at ASC LIMIT 1", (user_id,))


@bp.get("/admin/overview")
@permission_required("overview:read")
def overview():
    users = query_one(
        """SELECT COUNT(*) AS total,
                  SUM(status = '正常') AS active,
                  SUM(created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)) AS new7
           FROM users"""
    )
    finance = query_one(
        """SELECT COUNT(*) AS projects, COALESCE(SUM(budget),0) AS budget,
                  COALESCE(SUM(spent),0) AS spent, COALESCE(SUM(open_issues),0) AS open_issues
           FROM v_project_finance"""
    )
    contracts = query_one(
        """SELECT COUNT(*) AS total, COALESCE(SUM(total_amount - paid_amount),0) AS pending
           FROM contracts WHERE status IN ('执行中','待付','风险')"""
    )
    spend_7d = query_one(
        "SELECT COALESCE(SUM(amount),0) AS amount FROM expenses WHERE spent_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)"
    )
    growth = query_all(
        """SELECT DATE_FORMAT(created_at, '%%m-%%d') AS day, COUNT(*) AS count
           FROM users WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 13 DAY)
           GROUP BY day ORDER BY day ASC"""
    )
    categories = query_all(
        """SELECT category, SUM(amount) AS amount FROM expenses GROUP BY category ORDER BY amount DESC LIMIT 6"""
    )
    recent_issues = query_all(
        """SELECT i.id, i.title, i.level, i.status, i.recorded_at, p.project_name, u.name AS user_name
           FROM issues i JOIN projects p ON p.id = i.project_id JOIN users u ON u.id = p.user_id
           WHERE i.status = '待解决' ORDER BY i.recorded_at DESC LIMIT 6"""
    )
    return ok(
        {
            "users": users,
            "finance": finance,
            "contracts": contracts,
            "spend7d": spend_7d["amount"],
            "growth": growth,
            "categories": categories,
            "recentIssues": recent_issues,
            "audit7d": query_one(
                "SELECT COUNT(*) AS count FROM audit_logs WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)"
            )["count"],
        }
    )


@bp.get("/admin/users")
@permission_required("users:read")
def list_users():
    page, size = page_args()
    q = request.args.get("q", "").strip()
    status = request.args.get("status", "").strip()
    city = request.args.get("city", "").strip()
    where = ["1=1"]
    params: list = []
    if q:
        where.append("(u.name LIKE %s OR u.phone LIKE %s OR u.id LIKE %s OR p.project_name LIKE %s)")
        like = f"%{q}%"
        params.extend([like, like, like, like])
    if status:
        where.append("u.status = %s")
        params.append(status)
    if city:
        where.append("u.city = %s")
        params.append(city)
    base = f"""SELECT u.id, u.name, u.phone, u.city, u.status, u.created_at,
                      p.id AS project_id, p.project_name, p.method,
                      f.budget, f.spent, f.remaining, f.budget_used_pct, f.open_issues
               FROM users u
               LEFT JOIN projects p ON p.user_id = u.id
               LEFT JOIN v_project_finance f ON f.project_id = p.id
               WHERE {' AND '.join(where)}"""
    total = query_one(f"SELECT COUNT(DISTINCT u.id) AS count FROM users u LEFT JOIN projects p ON p.user_id = u.id WHERE {' AND '.join(where)}", params)
    rows = query_all(f"{base} ORDER BY u.created_at DESC LIMIT %s OFFSET %s", (*params, size, (page - 1) * size))
    return ok({"items": rows, "page": page, "pageSize": size, "total": total["count"]})


@bp.get("/admin/users/<user_id>")
@permission_required("users:read")
def user_detail(user_id: str):
    user = query_one("SELECT * FROM users WHERE id = %s", (user_id,))
    if not user:
        raise ApiError(404, "user_not_found", "用户不存在")
    project = _project_of_user(user_id)
    finance = query_one("SELECT * FROM v_project_finance WHERE project_id = %s", (project["id"],)) if project else None
    expenses = query_all(
        "SELECT * FROM expenses WHERE project_id = %s ORDER BY spent_at DESC LIMIT 50", (project["id"],)
    ) if project else []
    contracts = query_all(
        "SELECT * FROM contracts WHERE project_id = %s ORDER BY created_at DESC", (project["id"],)
    ) if project else []
    issues = query_all(
        "SELECT * FROM issues WHERE project_id = %s ORDER BY recorded_at DESC", (project["id"],)
    ) if project else []
    logs = query_all(
        """SELECT * FROM audit_logs WHERE (object_type = 'user' AND object_id = %s)
           OR (object_type = 'project' AND object_id = %s)
           ORDER BY created_at DESC LIMIT 50""",
        (user_id, project["id"] if project else "-"),
    )
    return ok(
        {
            "user": user,
            "project": {**project, **{k: v for k, v in (finance or {}).items() if k != "project_id"}} if project else None,
            "expenses": expenses,
            "contracts": contracts,
            "issues": issues,
            "logs": logs,
        }
    )


@bp.patch("/admin/users/<user_id>")
@permission_required("users:write")
def update_user(user_id: str):
    user = query_one("SELECT * FROM users WHERE id = %s", (user_id,))
    if not user:
        raise ApiError(404, "user_not_found", "用户不存在")
    data = body()
    status = text(data.get("status"), "status", 12, required=False)
    if status and status not in {"正常", "停用"}:
        raise ApiError(400, "invalid_request", "status 不合法")
    if status:
        execute("UPDATE users SET status = %s WHERE id = %s", (status, user_id))
        if status == "停用":
            execute("UPDATE user_sessions SET revoked_at = NOW() WHERE user_id = %s AND revoked_at IS NULL", (user_id,))
        write_audit(g.admin["id"], "admin.user.status", "user", user_id, {"status": status})
    name = data.get("name")
    if name is not None:
        execute("UPDATE users SET name = %s WHERE id = %s", (text(name, "name", 40), user_id))
        write_audit(g.admin["id"], "admin.user.rename", "user", user_id)
    city = data.get("city")
    if city is not None:
        execute("UPDATE users SET city = %s WHERE id = %s", (text(city, "city", 40, required=False), user_id))
    commit()
    return ok(query_one("SELECT * FROM users WHERE id = %s", (user_id,)))


@bp.patch("/admin/users/<user_id>/budget")
@permission_required("users:write")
def update_budget(user_id: str):
    user = query_one("SELECT id FROM users WHERE id = %s", (user_id,))
    if not user:
        raise ApiError(404, "user_not_found", "用户不存在")
    project = _project_of_user(user_id)
    if not project:
        raise ApiError(404, "project_not_found", "用户还没有项目")
    data = body()
    try:
        budget = Decimal(str(data.get("budget"))).quantize(Decimal("0.01"))
    except Exception as exc:
        raise ApiError(400, "invalid_request", "budget 必须是金额") from exc
    if budget < 0:
        raise ApiError(400, "invalid_request", "budget 不能为负")
    execute("UPDATE projects SET budget = %s WHERE id = %s", (budget, project["id"]))
    write_audit(g.admin["id"], "admin.user.budget", "project", project["id"], {"before": project["budget"], "after": float(budget)})
    commit()
    return ok(query_one("SELECT * FROM v_project_finance WHERE project_id = %s", (project["id"],)))


@bp.delete("/admin/users/<user_id>")
@permission_required("users:write")
def delete_user(user_id: str):
    user = query_one("SELECT id, name FROM users WHERE id = %s", (user_id,))
    if not user:
        raise ApiError(404, "user_not_found", "用户不存在")
    execute("DELETE FROM users WHERE id = %s", (user_id,))
    write_audit(g.admin["id"], "admin.user.deleted", "user", user_id, {"name": user["name"]})
    commit()
    return ok({"deleted": True})


@bp.post("/admin/users/<user_id>/revoke-sessions")
@permission_required("users:write")
def revoke_sessions(user_id: str):
    _, affected = execute(
        "UPDATE user_sessions SET revoked_at = NOW() WHERE user_id = %s AND revoked_at IS NULL", (user_id,)
    )
    write_audit(g.admin["id"], "admin.user.revoke_sessions", "user", user_id, {"count": affected})
    commit()
    return ok({"revoked": affected})


@bp.get("/admin/expenses")
@permission_required("data:read")
def list_expenses():
    page, size = page_args(30)
    where = ["1=1"]
    params: list = []
    q = request.args.get("q", "").strip()
    risk = request.args.get("risk", "").strip()
    category = request.args.get("category", "").strip()
    if q:
        where.append("(u.name LIKE %s OR p.project_name LIKE %s OR e.note LIKE %s)")
        params.extend([f"%{q}%"] * 3)
    if risk:
        where.append("e.risk_status = %s")
        params.append(risk)
    if category:
        where.append("e.category = %s")
        params.append(category)
    total = query_one(
        f"""SELECT COUNT(*) AS count, COALESCE(SUM(e.amount),0) AS amount
            FROM expenses e JOIN projects p ON p.id = e.project_id JOIN users u ON u.id = p.user_id
            WHERE {' AND '.join(where)}""",
        params,
    )
    rows = query_all(
        f"""SELECT e.*, p.project_name, u.name AS user_name
            FROM expenses e JOIN projects p ON p.id = e.project_id JOIN users u ON u.id = p.user_id
            WHERE {' AND '.join(where)}
            ORDER BY e.spent_at DESC, e.created_at DESC LIMIT %s OFFSET %s""",
        (*params, size, (page - 1) * size),
    )
    return ok({"items": rows, "page": page, "pageSize": size, "total": total["count"], "amount": total["amount"]})


@bp.patch("/admin/expenses/<expense_id>/risk")
@permission_required("data:write")
def mark_expense(expense_id: str):
    expense = query_one("SELECT * FROM expenses WHERE id = %s", (expense_id,))
    if not expense:
        raise ApiError(404, "expense_not_found", "支出不存在")
    status = text(body().get("riskStatus"), "riskStatus", 12)
    if status not in RISK_STATUS:
        raise ApiError(400, "invalid_request", "riskStatus 不合法")
    execute("UPDATE expenses SET risk_status = %s WHERE id = %s", (status, expense_id))
    write_audit(g.admin["id"], "admin.expense.risk", "expense", expense_id, {"status": status})
    commit()
    return ok(query_one("SELECT * FROM expenses WHERE id = %s", (expense_id,)))


@bp.get("/admin/contracts")
@permission_required("data:read")
def list_contracts():
    page, size = page_args(30)
    where = ["1=1"]
    params: list = []
    q = request.args.get("q", "").strip()
    status = request.args.get("status", "").strip()
    if q:
        where.append("(c.name LIKE %s OR u.name LIKE %s OR p.project_name LIKE %s)")
        params.extend([f"%{q}%"] * 3)
    if status:
        where.append("c.status = %s")
        params.append(status)
    total = query_one(
        f"""SELECT COUNT(*) AS count, COALESCE(SUM(c.total_amount),0) AS total,
                   COALESCE(SUM(c.paid_amount),0) AS paid
            FROM contracts c JOIN projects p ON p.id = c.project_id JOIN users u ON u.id = p.user_id
            WHERE {' AND '.join(where)}""",
        params,
    )
    rows = query_all(
        f"""SELECT c.*, p.project_name, u.name AS user_name
            FROM contracts c JOIN projects p ON p.id = c.project_id JOIN users u ON u.id = p.user_id
            WHERE {' AND '.join(where)}
            ORDER BY c.created_at DESC LIMIT %s OFFSET %s""",
        (*params, size, (page - 1) * size),
    )
    return ok({"items": rows, "total": total["count"], "totalAmount": total["total"], "paidAmount": total["paid"]})


@bp.get("/admin/issues")
@permission_required("data:read")
def list_issues():
    page, size = page_args(30)
    where = ["1=1"]
    params: list = []
    q = request.args.get("q", "").strip()
    status = request.args.get("status", "").strip()
    level = request.args.get("level", "").strip()
    if q:
        where.append("(i.title LIKE %s OR p.project_name LIKE %s OR u.name LIKE %s)")
        params.extend([f"%{q}%"] * 3)
    if status:
        where.append("i.status = %s")
        params.append(status)
    if level:
        where.append("i.level = %s")
        params.append(level)
    total = query_one(
        f"""SELECT COUNT(*) AS count FROM issues i JOIN projects p ON p.id = i.project_id
            JOIN users u ON u.id = p.user_id WHERE {' AND '.join(where)}""",
        params,
    )
    rows = query_all(
        f"""SELECT i.*, p.project_name, u.name AS user_name
            FROM issues i JOIN projects p ON p.id = i.project_id JOIN users u ON u.id = p.user_id
            WHERE {' AND '.join(where)} ORDER BY i.recorded_at DESC LIMIT %s OFFSET %s""",
        (*params, size, (page - 1) * size),
    )
    return ok({"items": rows, "total": total["count"]})


@bp.patch("/admin/issues/<issue_id>")
@permission_required("data:write")
def update_issue(issue_id: str):
    issue = query_one("SELECT * FROM issues WHERE id = %s", (issue_id,))
    if not issue:
        raise ApiError(404, "issue_not_found", "问题不存在")
    data = body()
    status = text(data.get("status"), "status", 16, required=False)
    level = text(data.get("level"), "level", 8, required=False)
    if status:
        if status not in ISSUE_STATUS:
            raise ApiError(400, "invalid_request", "status 不合法")
        execute(
            "UPDATE issues SET status = %s, resolved_at = %s, resolved_by = %s WHERE id = %s",
            (status, datetime.now() if status == "已解决" else None, g.admin["id"] if status == "已解决" else None, issue_id),
        )
    if level:
        if level not in ISSUE_LEVELS:
            raise ApiError(400, "invalid_request", "level 不合法")
        execute("UPDATE issues SET level = %s WHERE id = %s", (level, issue_id))
    write_audit(g.admin["id"], "admin.issue.updated", "issue", issue_id, {"status": status, "level": level})
    commit()
    return ok(query_one("SELECT * FROM issues WHERE id = %s", (issue_id,)))


@bp.get("/admin/analytics")
@permission_required("analytics:read")
def analytics():
    days = _range_days()
    since = datetime.now() - timedelta(days=days)
    new_users = query_one("SELECT COUNT(*) AS count FROM users WHERE created_at >= %s", (since,))["count"]
    spend = query_one("SELECT COALESCE(SUM(amount),0) AS amount FROM expenses WHERE spent_at >= %s", (since.date(),))["amount"]
    active = query_one(
        "SELECT COUNT(DISTINCT object_id) AS count FROM audit_logs WHERE action = 'user.login' AND created_at >= %s",
        (since,),
    )["count"]
    resolved = query_one(
        "SELECT COUNT(*) AS count FROM issues WHERE resolved_at >= %s", (since,)
    )["count"]
    growth = query_all(
        """SELECT DATE_FORMAT(created_at, '%%m-%%d') AS day, COUNT(*) AS count
           FROM users WHERE created_at >= %s GROUP BY day ORDER BY day ASC""",
        (since,),
    )
    categories = query_all(
        """SELECT category, SUM(amount) AS amount FROM expenses WHERE spent_at >= %s
           GROUP BY category ORDER BY amount DESC""",
        (since.date(),),
    )
    cities = query_all(
        """SELECT city, COUNT(*) AS count FROM users WHERE city <> '' GROUP BY city ORDER BY count DESC LIMIT 8"""
    )
    signs = query_one(
        """SELECT COUNT(*) AS total_users,
                  SUM(EXISTS(SELECT 1 FROM projects p WHERE p.user_id = u.id)) AS has_project,
                  SUM(EXISTS(SELECT 1 FROM expenses e JOIN projects p ON p.id = e.project_id WHERE p.user_id = u.id)) AS has_expense,
                  SUM(EXISTS(SELECT 1 FROM nodes n JOIN projects p ON p.id = n.project_id WHERE p.user_id = u.id AND n.status = '已完成')) AS has_node,
                  SUM(EXISTS(SELECT 1 FROM contracts c JOIN projects p ON p.id = c.project_id WHERE p.user_id = u.id)) AS has_contract
           FROM users u"""
    )
    funnel = [
        {"key": "register", "label": "注册", "value": signs["total_users"] or 0},
        {"key": "project", "label": "建项目", "value": signs["has_project"] or 0},
        {"key": "expense", "label": "记支出", "value": signs["has_expense"] or 0},
        {"key": "node", "label": "记进度", "value": signs["has_node"] or 0},
        {"key": "contract", "label": "管合同", "value": signs["has_contract"] or 0},
    ]
    monthly = query_all(
        """SELECT DATE_FORMAT(spent_at, '%%Y-%%m') AS month, SUM(amount) AS amount
           FROM expenses WHERE spent_at >= DATE_SUB(CURDATE(), INTERVAL 5 MONTH)
           GROUP BY month ORDER BY month ASC"""
    )
    return ok(
        {
            "range": days,
            "newUsers": new_users,
            "spend": spend,
            "activeUsers": active,
            "resolvedIssues": resolved,
            "growth": growth,
            "categories": categories,
            "cities": cities,
            "funnel": funnel,
            "monthly": monthly,
        }
    )


@bp.get("/admin/admins")
@permission_required("admins:read")
def list_admins():
    rows = query_all(
        "SELECT id, name, phone, role, city, status, is_super, mfa_enabled, last_login_at, created_at FROM admins ORDER BY created_at ASC"
    )
    return ok(rows)


@bp.post("/admin/admins")
@permission_required("admins:write")
def create_admin():
    data = body()
    name = text(data.get("name"), "name", 40)
    phone = text(data.get("phone"), "phone", 20)
    role = text(data.get("role"), "role", 30, required=False) or "运营管理员"
    if role not in ROLES:
        raise ApiError(400, "invalid_request", "角色不合法")
    password = text(data.get("password"), "password", 128)
    assert_strong_password(password)
    if query_one("SELECT id FROM admins WHERE phone = %s", (phone,)):
        raise ApiError(409, "admin_exists", "手机号已存在")
    admin_id = new_id("A", 3)
    want_mfa = bool(data.get("mfa", True)) or role == "超级管理员" or Config.ADMIN_MFA_REQUIRED
    secret = new_totp_secret() if want_mfa else ""
    execute(
        """INSERT INTO admins (id, name, phone, password_hash, role, city, is_super, mfa_enabled, mfa_secret_ciphertext)
           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)""",
        (
            admin_id, name, phone, hash_password(password), role,
            text(data.get("city"), "city", 40, required=False),
            1 if role == "超级管理员" else 0,
            1 if want_mfa else 0,
            encrypt_secret(secret) if want_mfa else None,
        ),
    )
    write_audit(g.admin["id"], "admin.admin.created", "admin", admin_id, {"role": role})
    commit()
    result = query_one("SELECT id, name, phone, role, is_super, mfa_enabled FROM admins WHERE id = %s", (admin_id,))
    if want_mfa:
        result["mfaSecret"] = secret
        result["mfaUri"] = totp_uri(secret, f"{name} {phone}")
    return ok(result, 201)


@bp.patch("/admin/admins/<admin_id>")
@permission_required("admins:write")
def update_admin(admin_id: str):
    target = query_one("SELECT * FROM admins WHERE id = %s", (admin_id,))
    if not target:
        raise ApiError(404, "admin_not_found", "管理员不存在")
    if target["is_super"] and not g.admin["is_super"]:
        raise ApiError(403, "forbidden", "只有超级管理员可以修改超级管理员")
    data = body()
    status = text(data.get("status"), "status", 12, required=False)
    role = text(data.get("role"), "role", 30, required=False)
    if status and status not in {"在岗", "停用"}:
        raise ApiError(400, "invalid_request", "status 不合法")
    if role and role not in ROLES:
        raise ApiError(400, "invalid_request", "role 不合法")
    if role == "超级管理员" and not g.admin["is_super"]:
        raise ApiError(403, "forbidden", "只有超级管理员可以授予超级管理员")
    if status:
        execute("UPDATE admins SET status = %s WHERE id = %s", (status, admin_id))
    if role:
        execute("UPDATE admins SET role = %s, is_super = %s WHERE id = %s", (role, 1 if role == "超级管理员" else 0, admin_id))
    write_audit(g.admin["id"], "admin.admin.updated", "admin", admin_id, {"status": status, "role": role})
    commit()
    return ok(query_one("SELECT id, name, phone, role, status, is_super, mfa_enabled FROM admins WHERE id = %s", (admin_id,)))


@bp.post("/admin/admins/<admin_id>/reset-mfa")
@permission_required("admins:write")
def reset_admin_mfa(admin_id: str):
    target = query_one("SELECT * FROM admins WHERE id = %s", (admin_id,))
    if not target:
        raise ApiError(404, "admin_not_found", "管理员不存在")
    if target["is_super"] and not g.admin["is_super"]:
        raise ApiError(403, "forbidden", "只有超级管理员可以重置超级管理员 MFA")
    secret = new_totp_secret()
    execute(
        "UPDATE admins SET mfa_enabled = 1, mfa_secret_ciphertext = %s WHERE id = %s",
        (encrypt_secret(secret), admin_id),
    )
    write_audit(g.admin["id"], "admin.admin.reset_mfa", "admin", admin_id)
    commit()
    return ok({"mfaSecret": secret, "mfaUri": totp_uri(secret, f"{target['name']} {target['phone']}")})


@bp.get("/admin/settings")
@permission_required("settings:read")
def get_settings():
    return ok({key: get_setting(key) for key in ("ads", "export", "app")})


@bp.put("/admin/settings")
@permission_required("settings:write")
def update_settings():
    data = body()
    allowed = {"ads", "export", "app"}
    unknown = set(data) - allowed
    if unknown:
        raise ApiError(400, "invalid_request", f"不支持设置项: {', '.join(sorted(unknown))}")
    for key, value in data.items():
        if not isinstance(value, dict):
            raise ApiError(400, "invalid_request", f"{key} 必须是对象")
        save_setting(key, value, g.admin["id"])
    write_audit(g.admin["id"], "admin.settings.updated", "setting", "", {"keys": list(data.keys())})
    commit()
    return ok({key: get_setting(key) for key in allowed})


@bp.get("/admin/audit-logs")
@permission_required("audit:read")
def audit_logs():
    page, size = page_args(30)
    action = request.args.get("action", "").strip()
    where = ["1=1"]
    params: list = []
    if action:
        where.append("action LIKE %s")
        params.append(f"%{action}%")
    total = query_one(f"SELECT COUNT(*) AS count FROM audit_logs WHERE {' AND '.join(where)}", params)
    rows = query_all(
        f"""SELECT a.*, ad.name AS admin_name FROM audit_logs a
            LEFT JOIN admins ad ON ad.id = a.admin_id
            WHERE {' AND '.join(where)} ORDER BY a.created_at DESC LIMIT %s OFFSET %s""",
        (*params, size, (page - 1) * size),
    )
    return ok({"items": rows, "total": total["count"]})


@bp.post("/admin/notifications")
@permission_required("notifications:write")
def send_notification():
    data = body()
    title = text(data.get("title"), "title", 120)
    body_text = text(data.get("body"), "body", 1000, required=False)
    audience = text(data.get("audience"), "audience", 20, required=False) or "all"
    if audience not in {"all", "active", "over_budget", "city"}:
        raise ApiError(400, "invalid_request", "audience 不合法")
    execute(
        "INSERT INTO notifications (title, body, audience, sent_by) VALUES (%s, %s, %s, %s)",
        (title, body_text, audience, g.admin["id"]),
    )
    write_audit(g.admin["id"], "admin.notification.sent", "notification", "", {"audience": audience})
    commit()
    return ok({"sent": True}, 201)


@bp.get("/admin/export")
@permission_required("export:read")
def export_admin():
    kind = request.args.get("type", "users").strip()
    if kind == "expenses":
        rows = query_all(
            """SELECT e.id, e.spent_at, u.name AS user_name, p.project_name, e.category, e.amount,
                      e.pay_type, e.risk_status, e.note
               FROM expenses e JOIN projects p ON p.id = e.project_id JOIN users u ON u.id = p.user_id
               ORDER BY e.spent_at DESC"""
        )
        return csv_response(
            "expenses.csv",
            ["ID", "日期", "用户", "项目", "分类", "金额", "付款方式", "风险", "备注"],
            [[r["id"], r["spent_at"], r["user_name"], r["project_name"], r["category"], r["amount"],
              r["pay_type"], r["risk_status"], r["note"]] for r in rows],
        )
    rows = query_all(
        """SELECT u.id, u.name, u.phone, u.city, u.status, u.created_at, p.project_name,
                  f.budget, f.spent, f.remaining, f.open_issues
           FROM users u LEFT JOIN projects p ON p.user_id = u.id
           LEFT JOIN v_project_finance f ON f.project_id = p.id ORDER BY u.created_at DESC"""
    )
    return csv_response(
        "users.csv",
        ["ID", "姓名", "手机", "城市", "状态", "注册时间", "项目", "预算", "已支出", "余额", "待解决问题"],
        [[r["id"], r["name"], r["phone"], r["city"], r["status"], r["created_at"], r["project_name"],
          r["budget"], r["spent"], r["remaining"], r["open_issues"]] for r in rows],
    )
