"""小程序用户 API：记账、进度、问题、合同、统计、导出与广告。"""
from __future__ import annotations

import uuid
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path

from flask import Blueprint, g, request, send_from_directory

from api.common import (
    body,
    csv_response,
    get_setting,
    integer,
    issue_export_token,
    money,
    new_id,
    ok,
    page_args,
    require_project,
    text,
    verify_ad_proof,
    verify_export_token,
)
from config import Config
from db import commit, execute, query_all, query_one, transaction
from security import ApiError, user_required, write_audit

bp = Blueprint("user", __name__)

EXPENSE_CATEGORIES = {"主材", "半包工程", "水电", "全屋定制", "家电", "门窗", "软装", "灯具", "设计", "其他"}
PAY_TYPES = {"合同付款", "材料采购", "电商采购", "其他"}
NODE_STATUS = {"待开始", "进行中", "已完成"}
ISSUE_LEVELS = {"高", "中", "低"}
ISSUE_STATUS = {"待解决", "已解决", "已转交"}


def _project() -> dict:
    return require_project(g.user["id"])


def _date(value, name: str, default: str | None = None) -> str:
    raw = text(value, name, 20, required=False) or default or date.today().isoformat()
    try:
        return datetime.strptime(raw, "%Y-%m-%d").date().isoformat()
    except ValueError as exc:
        raise ApiError(400, "invalid_request", f"{name} 必须是 YYYY-MM-DD") from exc


@bp.get("/user/home")
@user_required
def home():
    project = _project()
    expenses = query_all(
        "SELECT * FROM expenses WHERE project_id = %s ORDER BY spent_at DESC, created_at DESC LIMIT 5",
        (project["id"],),
    )
    nodes = query_all("SELECT * FROM nodes WHERE project_id = %s ORDER BY seq ASC", (project["id"],))
    issues = query_all(
        "SELECT * FROM issues WHERE project_id = %s AND status = '待解决' ORDER BY recorded_at DESC LIMIT 5",
        (project["id"],),
    )
    contracts = query_all(
        """SELECT id, name, total_amount, paid_amount, status, payment_nodes_total, payment_nodes_done
           FROM contracts WHERE project_id = %s ORDER BY created_at DESC""",
        (project["id"],),
    )
    done_nodes = sum(1 for node in nodes if node["status"] == "已完成")
    total_contract = sum(float(item["total_amount"] or 0) for item in contracts)
    paid_contract = sum(float(item["paid_amount"] or 0) for item in contracts)
    contract_pending = round(max(total_contract - paid_contract, 0), 2)
    available = round(float(project.get("budget", 0)) - float(project.get("spent", 0)) - contract_pending, 2)
    return ok(
        {
            "project": project,
            "summary": {
                "budget": project.get("budget", 0),
                "spent": project.get("spent", 0),
                "remaining": project.get("remaining", 0),
                "budgetUsedPct": project.get("budget_used_pct", 0),
                "openIssues": project.get("open_issues", 0),
                "nodeDone": done_nodes,
                "nodeTotal": len(nodes),
                "contractTotal": total_contract,
                "contractPaid": paid_contract,
                "contractPending": contract_pending,
                "available": available,
            },
            "recentExpenses": expenses,
            "nodes": nodes,
            "openIssues": issues,
            "contracts": contracts,
            "ads": get_setting("ads"),
        }
    )


@bp.get("/user/project")
@user_required
def get_project():
    return ok(_project())


@bp.patch("/user/project")
@user_required
def update_project():
    project = _project()
    data = body()
    fields, params = [], []
    if "projectName" in data:
        fields.append("project_name = %s")
        params.append(text(data.get("projectName"), "projectName", 80))
    if "area" in data:
        fields.append("area = %s")
        params.append(money(data.get("area"), "area") if data.get("area") not in (None, "", 0) else Decimal("0"))
    if "method" in data:
        method = text(data.get("method"), "method", 20)
        if method not in {"半包", "全包", "自装"}:
            raise ApiError(400, "invalid_request", "method 不合法")
        fields.append("method = %s")
        params.append(method)
    if "budget" in data:
        fields.append("budget = %s")
        params.append(money(data.get("budget"), "budget") if data.get("budget") not in (None, "", 0) else Decimal("0"))
    if not fields:
        raise ApiError(400, "invalid_request", "没有可更新字段")
    execute(f"UPDATE projects SET {', '.join(fields)} WHERE id = %s", (*params, project["id"]))
    commit()
    return ok(_project())


@bp.get("/user/expenses")
@user_required
def list_expenses():
    project = _project()
    page, size = page_args()
    conditions = ["project_id = %s"]
    params: list = [project["id"]]
    category = request.args.get("category", "").strip()
    if category:
        conditions.append("category = %s")
        params.append(category)
    start = request.args.get("from", "").strip()
    end = request.args.get("to", "").strip()
    if start:
        conditions.append("spent_at >= %s")
        params.append(_date(start, "from"))
    if end:
        conditions.append("spent_at <= %s")
        params.append(_date(end, "to"))
    where = " AND ".join(conditions)
    total = query_one(f"SELECT COUNT(*) AS count, COALESCE(SUM(amount),0) AS amount FROM expenses WHERE {where}", params)
    rows = query_all(
        f"SELECT * FROM expenses WHERE {where} ORDER BY spent_at DESC, created_at DESC LIMIT %s OFFSET %s",
        (*params, size, (page - 1) * size),
    )
    return ok({"items": rows, "page": page, "pageSize": size, "total": total["count"], "amount": total["amount"]})


@bp.post("/user/expenses")
@user_required
def create_expense():
    project = _project()
    data = body()
    category = text(data.get("category"), "category", 40)
    if category not in EXPENSE_CATEGORIES:
        raise ApiError(400, "invalid_request", "分类不合法")
    pay_type = text(data.get("payType"), "payType", 30, required=False) or "材料采购"
    if pay_type not in PAY_TYPES:
        raise ApiError(400, "invalid_request", "付款类型不合法")
    amount = money(data.get("amount"), "amount")
    spent_at = _date(data.get("spentAt"), "spentAt")
    voucher_count = integer(data.get("voucherCount"), "voucherCount", 0, 0, 3)
    note = text(data.get("note"), "note", 500, required=False)
    expense_id = new_id("E")
    execute(
        """INSERT INTO expenses
           (id, project_id, category, amount, pay_type, spent_at, risk_status, voucher_count, note, created_by)
           VALUES (%s, %s, %s, %s, %s, %s, '正常', %s, %s, %s)""",
        (expense_id, project["id"], category, amount, pay_type, spent_at, voucher_count, note, g.user["id"]),
    )
    write_audit(None, "user.expense.created", "expense", expense_id, {"amount": float(amount)})
    commit()
    return ok(query_one("SELECT * FROM expenses WHERE id = %s", (expense_id,)), 201)


@bp.put("/user/expenses/<expense_id>")
@user_required
def update_expense(expense_id: str):
    project = _project()
    row = query_one("SELECT * FROM expenses WHERE id = %s AND project_id = %s", (expense_id, project["id"]))
    if not row:
        raise ApiError(404, "expense_not_found", "支出记录不存在")
    data = body()
    fields, params = [], []
    if "category" in data:
        category = text(data.get("category"), "category", 40)
        if category not in EXPENSE_CATEGORIES:
            raise ApiError(400, "invalid_request", "分类不合法")
        fields.append("category = %s")
        params.append(category)
    if "amount" in data:
        fields.append("amount = %s")
        params.append(money(data.get("amount"), "amount"))
    if "payType" in data:
        pay_type = text(data.get("payType"), "payType", 30)
        if pay_type not in PAY_TYPES:
            raise ApiError(400, "invalid_request", "付款类型不合法")
        fields.append("pay_type = %s")
        params.append(pay_type)
    if "spentAt" in data:
        fields.append("spent_at = %s")
        params.append(_date(data.get("spentAt"), "spentAt"))
    if "note" in data:
        fields.append("note = %s")
        params.append(text(data.get("note"), "note", 500, required=False))
    if "voucherCount" in data:
        fields.append("voucher_count = %s")
        params.append(integer(data.get("voucherCount"), "voucherCount", 0, 0, 3))
    if not fields:
        raise ApiError(400, "invalid_request", "没有可更新字段")
    execute(f"UPDATE expenses SET {', '.join(fields)} WHERE id = %s", (*params, expense_id))
    write_audit(None, "user.expense.updated", "expense", expense_id)
    commit()
    return ok(query_one("SELECT * FROM expenses WHERE id = %s", (expense_id,)))


@bp.delete("/user/expenses/<expense_id>")
@user_required
def delete_expense(expense_id: str):
    project = _project()
    _, affected = execute("DELETE FROM expenses WHERE id = %s AND project_id = %s", (expense_id, project["id"]))
    if affected != 1:
        raise ApiError(404, "expense_not_found", "支出记录不存在")
    write_audit(None, "user.expense.deleted", "expense", expense_id)
    commit()
    return ok({"deleted": True})


@bp.get("/user/expenses/stats")
@user_required
def expense_stats():
    project = _project()
    by_category = query_all(
        """SELECT category, SUM(amount) AS amount, COUNT(*) AS count
           FROM expenses WHERE project_id = %s GROUP BY category ORDER BY amount DESC""",
        (project["id"],),
    )
    monthly = query_all(
        """SELECT DATE_FORMAT(spent_at, '%%Y-%%m') AS month, SUM(amount) AS amount
           FROM expenses WHERE project_id = %s AND spent_at >= DATE_SUB(CURDATE(), INTERVAL 5 MONTH)
           GROUP BY month ORDER BY month ASC""",
        (project["id"],),
    )
    total = query_one(
        "SELECT COALESCE(SUM(amount),0) AS amount, COUNT(*) AS count FROM expenses WHERE project_id = %s",
        (project["id"],),
    )
    return ok(
        {
            "total": total["amount"],
            "count": total["count"],
            "byCategory": by_category,
            "monthly": monthly,
            "budget": project.get("budget", 0),
            "spent": project.get("spent", 0),
            "remaining": project.get("remaining", 0),
            "budgetUsedPct": project.get("budget_used_pct", 0),
        }
    )


@bp.get("/user/nodes")
@user_required
def list_nodes():
    project = _project()
    nodes = query_all("SELECT * FROM nodes WHERE project_id = %s ORDER BY seq ASC", (project["id"],))
    photos = query_all(
        """SELECT p.* FROM node_photos p JOIN nodes n ON n.id = p.node_id
           WHERE n.project_id = %s ORDER BY p.sort_order ASC""",
        (project["id"],),
    )
    by_node: dict[str, list] = {}
    for photo in photos:
        by_node.setdefault(photo["node_id"], []).append(photo)
    for node in nodes:
        node["photos"] = by_node.get(node["id"], [])
    return ok(nodes)


@bp.patch("/user/nodes/<node_id>")
@user_required
def update_node(node_id: str):
    project = _project()
    node = query_one("SELECT * FROM nodes WHERE id = %s AND project_id = %s", (node_id, project["id"]))
    if not node:
        raise ApiError(404, "node_not_found", "进度节点不存在")
    data = body()
    fields, params = [], []
    if "status" in data:
        status = text(data.get("status"), "status", 16)
        if status not in NODE_STATUS:
            raise ApiError(400, "invalid_request", "节点状态不合法")
        fields.extend(["status = %s", "recorded_at = NOW()"])
        params.append(status)
    if "note" in data:
        fields.append("note = %s")
        params.append(text(data.get("note"), "note", 500, required=False))
    if "name" in data:
        fields.append("name = %s")
        params.append(text(data.get("name"), "name", 60))
    if not fields:
        raise ApiError(400, "invalid_request", "没有可更新字段")
    execute(f"UPDATE nodes SET {', '.join(fields)} WHERE id = %s", (*params, node_id))
    write_audit(None, "user.node.updated", "node", node_id, {"status": data.get("status")})
    commit()
    return ok(query_one("SELECT * FROM nodes WHERE id = %s", (node_id,)))


@bp.post("/user/nodes/<node_id>/photos")
@user_required
def add_node_photo(node_id: str):
    project = _project()
    node = query_one("SELECT id FROM nodes WHERE id = %s AND project_id = %s", (node_id, project["id"]))
    if not node:
        raise ApiError(404, "node_not_found", "进度节点不存在")
    data = body()
    url = text(data.get("url"), "url", 500)
    if not (url.startswith("https://") or url.startswith("/uploads/")):
        raise ApiError(400, "invalid_request", "照片地址不合法")
    execute("INSERT INTO node_photos (node_id, url, sort_order) VALUES (%s, %s, %s)", (node_id, url, 0))
    commit()
    return ok({"url": url}, 201)


@bp.get("/user/issues")
@user_required
def list_issues():
    project = _project()
    status = request.args.get("status", "").strip()
    params: list = [project["id"]]
    where = "project_id = %s"
    if status:
        where += " AND status = %s"
        params.append(status)
    return ok(query_all(f"SELECT * FROM issues WHERE {where} ORDER BY recorded_at DESC", params))


@bp.post("/user/issues")
@user_required
def create_issue():
    project = _project()
    data = body()
    title = text(data.get("title"), "title", 200)
    level = text(data.get("level"), "level", 8, required=False) or "中"
    if level not in ISSUE_LEVELS:
        raise ApiError(400, "invalid_request", "问题等级不合法")
    note = text(data.get("note"), "note", 500, required=False)
    node_id = text(data.get("nodeId"), "nodeId", 20, required=False) or None
    if node_id:
        node = query_one("SELECT id FROM nodes WHERE id = %s AND project_id = %s", (node_id, project["id"]))
        if not node:
            raise ApiError(400, "invalid_request", "进度节点不属于当前项目")
    issue_id = new_id("I")
    execute(
        """INSERT INTO issues (id, project_id, node_id, title, level, status, note)
           VALUES (%s, %s, %s, %s, %s, '待解决', %s)""",
        (issue_id, project["id"], node_id, title, level, note),
    )
    write_audit(None, "user.issue.created", "issue", issue_id, {"level": level})
    commit()
    return ok(query_one("SELECT * FROM issues WHERE id = %s", (issue_id,)), 201)


@bp.patch("/user/issues/<issue_id>")
@user_required
def update_issue(issue_id: str):
    project = _project()
    issue = query_one("SELECT * FROM issues WHERE id = %s AND project_id = %s", (issue_id, project["id"]))
    if not issue:
        raise ApiError(404, "issue_not_found", "问题不存在")
    data = body()
    status = text(data.get("status"), "status", 16, required=False)
    if status:
        if status not in ISSUE_STATUS:
            raise ApiError(400, "invalid_request", "问题状态不合法")
        if status == "已解决":
            execute("UPDATE issues SET status = %s, resolved_at = NOW() WHERE id = %s", (status, issue_id))
        else:
            execute("UPDATE issues SET status = %s, resolved_at = NULL WHERE id = %s", (status, issue_id))
    note = data.get("note")
    if note is not None:
        execute("UPDATE issues SET note = %s WHERE id = %s", (text(note, "note", 500, required=False), issue_id))
    write_audit(None, "user.issue.updated", "issue", issue_id, {"status": status})
    commit()
    return ok(query_one("SELECT * FROM issues WHERE id = %s", (issue_id,)))


@bp.get("/user/contracts")
@user_required
def list_contracts():
    project = _project()
    contracts = query_all("SELECT * FROM contracts WHERE project_id = %s ORDER BY created_at DESC", (project["id"],))
    payments = query_all(
        """SELECT p.* FROM contract_payments p JOIN contracts c ON c.id = p.contract_id
           WHERE c.project_id = %s ORDER BY p.contract_id, p.seq""",
        (project["id"],),
    )
    by_contract: dict[str, list] = {}
    for payment in payments:
        by_contract.setdefault(payment["contract_id"], []).append(payment)
    for contract in contracts:
        contract["payments"] = by_contract.get(contract["id"], [])
    return ok(contracts)


@bp.get("/user/contracts/<contract_id>")
@user_required
def get_contract(contract_id: str):
    project = _project()
    contract = query_one("SELECT * FROM contracts WHERE id = %s AND project_id = %s", (contract_id, project["id"]))
    if not contract:
        raise ApiError(404, "contract_not_found", "合同不存在")
    contract["payments"] = query_all(
        "SELECT * FROM contract_payments WHERE contract_id = %s ORDER BY seq ASC", (contract_id,)
    )
    return ok(contract)


@bp.post("/user/contracts/<contract_id>/payments/<int:seq>/pay")
@user_required
def pay_contract(contract_id: str, seq: int):
    project = _project()
    contract = query_one("SELECT * FROM contracts WHERE id = %s AND project_id = %s", (contract_id, project["id"]))
    if not contract:
        raise ApiError(404, "contract_not_found", "合同不存在")
    with transaction() as db:
        with db.cursor() as cursor:
            cursor.execute(
                "SELECT * FROM contract_payments WHERE contract_id = %s AND seq = %s FOR UPDATE",
                (contract_id, seq),
            )
            payment = cursor.fetchone()
            if not payment:
                raise ApiError(404, "payment_not_found", "付款节点不存在")
            if payment["status"] == "已付":
                raise ApiError(409, "already_paid", "该付款节点已支付")
            cursor.execute(
                "UPDATE contract_payments SET status = '已付', paid_at = NOW() WHERE contract_id = %s AND seq = %s",
                (contract_id, seq),
            )
            cursor.execute(
                """SELECT COALESCE(SUM(amount), 0) AS paid, COUNT(*) AS done
                   FROM contract_payments WHERE contract_id = %s AND status = '已付'""",
                (contract_id,),
            )
            totals = cursor.fetchone()
            cursor.execute("SELECT total_amount FROM contracts WHERE id = %s", (contract_id,))
            total = cursor.fetchone()["total_amount"]
            status = "已完工" if Decimal(totals["paid"]) >= Decimal(total) else "执行中"
            cursor.execute(
                "UPDATE contracts SET paid_amount = %s, payment_nodes_done = %s, status = %s WHERE id = %s",
                (totals["paid"], totals["done"], status, contract_id),
            )
        write_audit(None, "user.contract.payment", "contract", contract_id, {"seq": seq})
    contract = query_one("SELECT * FROM contracts WHERE id = %s", (contract_id,))
    contract["payments"] = query_all(
        "SELECT * FROM contract_payments WHERE contract_id = %s ORDER BY seq ASC", (contract_id,)
    )
    return ok(contract)


@bp.get("/user/notifications")
@user_required
def notifications():
    return ok(query_all("SELECT * FROM notifications ORDER BY sent_at DESC LIMIT 20"))


@bp.get("/user/ads")
@user_required
def ads():
    return ok(get_setting("ads"))


@bp.post("/user/export/unlock")
@user_required
def export_unlock():
    data = body()
    proof = text(data.get("adProof"), "adProof", 128, required=False)
    export_setting = get_setting("export")
    if export_setting.get("rewarded_ad_required", True) and not verify_ad_proof(g.user["id"], proof):
        write_audit(None, "user.export.denied", "user", g.user["id"])
        commit()
        raise ApiError(403, "ad_proof_invalid", "激励视频未完成，无法导出")
    token = issue_export_token(g.user["id"])
    write_audit(None, "user.export.unlock", "user", g.user["id"])
    commit()
    return ok({"token": token, "expiresIn": Config.EXPORT_TOKEN_TTL})


@bp.get("/user/export")
@user_required
def export_data():
    token = request.args.get("token", "").strip()
    token_user = verify_export_token(token)
    if token_user != g.user["id"]:
        raise ApiError(403, "export_token_mismatch", "导出凭证不属于当前账号")
    project = _project()
    expenses = query_all(
        "SELECT spent_at, category, amount, pay_type, risk_status, note FROM expenses WHERE project_id = %s ORDER BY spent_at",
        (project["id"],),
    )
    issues = query_all(
        "SELECT recorded_at, title, level, status, note FROM issues WHERE project_id = %s ORDER BY recorded_at",
        (project["id"],),
    )
    contracts = query_all(
        "SELECT name, total_amount, paid_amount, status FROM contracts WHERE project_id = %s",
        (project["id"],),
    )
    rows = [["支出", item["spent_at"], item["category"], item["amount"], item["pay_type"], item["risk_status"], item["note"]]
            for item in expenses]
    rows += [["问题", item["recorded_at"], item["title"], item["level"], item["status"], "", item["note"]]
             for item in issues]
    rows += [["合同", "", item["name"], item["total_amount"], item["status"], item["paid_amount"], ""]
             for item in contracts]
    write_audit(None, "user.export", "project", project["id"])
    commit()
    return csv_response(
        f"renovation-{g.user['id']}-{date.today().isoformat()}.csv",
        ["类型", "日期", "名称/分类", "金额/等级", "状态/方式", "已付", "备注"],
        rows,
    )


@bp.post("/user/uploads")
@user_required
def upload():
    if "file" not in request.files:
        raise ApiError(400, "invalid_request", "缺少 file")
    file = request.files["file"]
    if not file or not file.filename:
        raise ApiError(400, "invalid_request", "文件为空")
    suffix = Path(file.filename).suffix.lower()
    if suffix not in {".jpg", ".jpeg", ".png", ".webp"}:
        raise ApiError(400, "invalid_request", "只支持 jpg/png/webp")
    if request.content_length and request.content_length > Config.MAX_UPLOAD_BYTES:
        raise ApiError(413, "file_too_large", "图片超过大小限制")
    Config.UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    name = f"{uuid.uuid4().hex}{suffix}"
    file.save(Config.UPLOAD_DIR / name)
    return ok({"url": f"/uploads/{name}"}, 201)


def register_uploads(app):
    @app.get("/uploads/<path:name>")
    def uploaded_file(name: str):
        return send_from_directory(Config.UPLOAD_DIR, name)
