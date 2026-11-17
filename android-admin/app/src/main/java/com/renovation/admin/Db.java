package com.renovation.admin;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 本地演示数据库：管理员端不直连 MySQL，正式版由 Flask API 提供数据。 */
final class Db extends SQLiteOpenHelper {
    private static final String NAME = "renovation_admin.db";
    private static final int VERSION = 1;
    private static Db instance;
    private final Context context;

    static synchronized Db get(Context context) {
        if (instance == null) instance = new Db(context.getApplicationContext());
        return instance;
    }

    private Db(Context context) {
        super(context, NAME, null, VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (id TEXT PRIMARY KEY, name TEXT, phone TEXT, city TEXT, status TEXT, created_at TEXT, project_id TEXT, project_name TEXT, method TEXT, budget REAL, spent REAL, open_issues INTEGER)");
        db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT, user_name TEXT, category TEXT, amount REAL, pay_type TEXT, spent_at TEXT, risk_status TEXT, note TEXT)");
        db.execSQL("CREATE TABLE nodes (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT, name TEXT, status TEXT, recorded_at TEXT, seq INTEGER)");
        db.execSQL("CREATE TABLE issues (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT, user_name TEXT, title TEXT, level TEXT, status TEXT, recorded_at TEXT)");
        db.execSQL("CREATE TABLE contracts (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT, user_name TEXT, name TEXT, total_amount REAL, paid_amount REAL, status TEXT, payment_nodes_total INTEGER, payment_nodes_done INTEGER)");
        db.execSQL("CREATE TABLE admins (id TEXT PRIMARY KEY, name TEXT, phone TEXT, role TEXT, status TEXT, created_at TEXT)");
        db.execSQL("CREATE TABLE audits (id INTEGER PRIMARY KEY AUTOINCREMENT, actor TEXT, action TEXT, detail TEXT, created_at TEXT)");
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT)");
        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS users");
        db.execSQL("DROP TABLE IF EXISTS expenses");
        db.execSQL("DROP TABLE IF EXISTS nodes");
        db.execSQL("DROP TABLE IF EXISTS issues");
        db.execSQL("DROP TABLE IF EXISTS contracts");
        db.execSQL("DROP TABLE IF EXISTS admins");
        db.execSQL("DROP TABLE IF EXISTS audits");
        db.execSQL("DROP TABLE IF EXISTS settings");
        onCreate(db);
    }

    private void seed(SQLiteDatabase db) {
        String[][] users = {
                {"U-1001", "张川", "13800000001", "重庆", "正常", "2026-09-02 10:20:00", "P-1001", "金茂悦 · 110.8㎡法式中古", "半包", "300000", "186420", "3"},
                {"U-1002", "李婷", "13800000002", "重庆", "正常", "2026-09-06 14:32:00", "P-1002", "江北嘴 · 89㎡奶油风", "全包", "220000", "104200", "1"},
                {"U-1003", "王磊", "13800000003", "成都", "正常", "2026-09-11 09:18:00", "P-1003", "天府新区 · 126㎡现代", "半包", "280000", "124100", "1"},
                {"U-1004", "陈默", "13800000004", "重庆", "停用", "2026-09-15 16:40:00", "P-1004", "渝北 · 72㎡旧改", "半包", "150000", "28600", "0"},
                {"U-1005", "刘洋", "13800000005", "杭州", "正常", "2026-09-21 11:05:00", "P-1005", "钱江新城 · 96㎡极简", "全包", "240000", "72000", "0"},
                {"U-1006", "周琪", "13800000006", "重庆", "正常", "2026-10-03 19:22:00", "P-1006", "南岸 · 108㎡原木", "半包", "260000", "18200", "0"},
                {"U-1007", "何欣", "13800000007", "南京", "正常", "2026-10-03 08:46:00", "P-1007", "河西 · 85㎡法式", "全包", "210000", "24600", "0"},
                {"U-1008", "孙浩", "13800000008", "重庆", "正常", "2026-10-08 13:10:00", "P-1008", "沙坪坝 · 118㎡现代", "自装", "190000", "0", "0"},
                {"U-1009", "文静", "13800000009", "西安", "正常", "2026-10-14 07:55:00", "P-1009", "曲江 · 92㎡侘寂", "半包", "200000", "0", "0"},
                {"U-1010", "肖博", "13800000010", "重庆", "正常", "2026-10-22 21:30:00", "P-1010", "九龙坡 · 105㎡奶油", "全包", "230000", "32800", "0"},
                {"U-1011", "唐一", "13800000011", "广州", "正常", "2026-11-10 10:12:00", "P-1011", "天河 · 78㎡黑白", "半包", "175000", "0", "0"},
                {"U-1012", "郭林", "13800000012", "重庆", "正常", "2026-11-14 15:48:00", "P-1012", "渝中 · 130㎡大平层", "全包", "320000", "0", "0"}
        };
        for (String[] u : users) {
            ContentValues v = new ContentValues();
            v.put("id", u[0]);
            v.put("name", u[1]);
            v.put("phone", u[2]);
            v.put("city", u[3]);
            v.put("status", u[4]);
            v.put("created_at", u[5]);
            v.put("project_id", u[6]);
            v.put("project_name", u[7]);
            v.put("method", u[8]);
            v.put("budget", Double.parseDouble(u[9]));
            v.put("spent", Double.parseDouble(u[10]));
            v.put("open_issues", Integer.parseInt(u[11]));
            db.insert("users", null, v);
        }

        Object[][] expenses = {
                {"U-1001", "张川", "主材", 68400d, "材料采购", "2026-09-18", "正常", "瓷砖、木地板、岩板"},
                {"U-1001", "张川", "半包工程", 42000d, "合同付款", "2026-09-25", "正常", "水电+泥瓦阶段款"},
                {"U-1001", "张川", "全屋定制", 33074d, "合同付款", "2026-10-06", "正常", "柜体首付款"},
                {"U-1001", "张川", "家电", 18600d, "电商采购", "2026-10-12", "正常", "冰箱、洗烘套装"},
                {"U-1001", "张川", "门窗", 12800d, "材料采购", "2026-10-20", "正常", "厨房推拉门、卧室门"},
                {"U-1001", "张川", "软装", 6546d, "电商采购", "2026-11-02", "正常", "窗帘、地毯、抱枕"},
                {"U-1001", "张川", "灯具", 5000d, "电商采购", "2026-11-08", "超支", "全光谱射灯补货"},
                {"U-1002", "李婷", "半包工程", 66000d, "合同付款", "2026-10-08", "正常", "施工队进度款"},
                {"U-1002", "李婷", "主材", 38200d, "材料采购", "2026-10-16", "正常", "瓷砖与石材"},
                {"U-1003", "王磊", "主材", 88600d, "材料采购", "2026-10-12", "正常", "木饰面、岩板"},
                {"U-1003", "王磊", "半包工程", 35500d, "合同付款", "2026-10-26", "正常", "木工阶段款"},
                {"U-1004", "陈默", "主材", 28600d, "材料采购", "2026-09-28", "待核", "旧改拆除后补砖"},
                {"U-1005", "刘洋", "全屋定制", 72000d, "合同付款", "2026-10-20", "正常", "柜体+木门"},
                {"U-1006", "周琪", "水电", 18200d, "合同付款", "2026-10-25", "正常", "水电改造"},
                {"U-1007", "何欣", "软装", 24600d, "电商采购", "2026-10-28", "正常", "沙发、床品"},
                {"U-1010", "肖博", "家电", 32800d, "电商采购", "2026-11-06", "正常", "中央空调、热水器"}
        };
        for (Object[] e : expenses) {
            ContentValues v = new ContentValues();
            v.put("user_id", (String) e[0]);
            v.put("user_name", (String) e[1]);
            v.put("category", (String) e[2]);
            v.put("amount", (Double) e[3]);
            v.put("pay_type", (String) e[4]);
            v.put("spent_at", (String) e[5]);
            v.put("risk_status", (String) e[6]);
            v.put("note", (String) e[7]);
            db.insert("expenses", null, v);
        }

        String[] nodeNames = {"开工准备", "拆改", "水电", "泥瓦", "木工", "油漆", "安装", "竣工", "软装"};
        String[] nodeDates = {"2026-09-10 09:00:00", "2026-09-16 10:00:00", "2026-09-28 15:00:00", "2026-10-12 10:00:00", "2026-10-24 09:30:00", "2026-11-03 16:00:00", "2026-11-12 11:00:00", "", ""};
        for (int i = 0; i < nodeNames.length; i++) {
            ContentValues v = new ContentValues();
            v.put("user_id", "U-1001");
            v.put("name", nodeNames[i]);
            v.put("status", i < 7 ? "已完成" : (i == 7 ? "进行中" : "待开始"));
            v.put("recorded_at", nodeDates[i]);
            v.put("seq", i + 1);
            db.insert("nodes", null, v);
        }

        Object[][] issues = {
                {"U-1001", "张川", "客厅射灯色温不一致", "高", "待解决", "2026-11-12 14:00:00"},
                {"U-1001", "张川", "全屋定制收口缝隙 3mm", "中", "待解决", "2026-11-15 10:00:00"},
                {"U-1001", "张川", "卫生间地漏排水偏慢", "低", "已解决", "2026-10-10 09:00:00"},
                {"U-1002", "李婷", "厨房热水管保温棉缺失", "中", "待解决", "2026-10-18 11:00:00"},
                {"U-1003", "王磊", "木饰面批次色差", "高", "待解决", "2026-11-01 15:00:00"}
        };
        for (Object[] i : issues) {
            ContentValues v = new ContentValues();
            v.put("user_id", (String) i[0]);
            v.put("user_name", (String) i[1]);
            v.put("title", (String) i[2]);
            v.put("level", (String) i[3]);
            v.put("status", (String) i[4]);
            v.put("recorded_at", (String) i[5]);
            db.insert("issues", null, v);
        }

        Object[][] contracts = {
                {"U-1001", "张川", "全屋定制 · 柜体与木门", 81000d, 73500d, "执行中", 4, 3},
                {"U-1001", "张川", "半包施工合同", 52000d, 33074d, "执行中", 3, 2},
                {"U-1002", "李婷", "全包整装合同", 165000d, 66000d, "执行中", 4, 1},
                {"U-1003", "王磊", "木作定制合同", 76000d, 35500d, "风险", 3, 1}
        };
        for (Object[] c : contracts) {
            ContentValues v = new ContentValues();
            v.put("user_id", (String) c[0]);
            v.put("user_name", (String) c[1]);
            v.put("name", (String) c[2]);
            v.put("total_amount", (Double) c[3]);
            v.put("paid_amount", (Double) c[4]);
            v.put("status", (String) c[5]);
            v.put("payment_nodes_total", (Integer) c[6]);
            v.put("payment_nodes_done", (Integer) c[7]);
            db.insert("contracts", null, v);
        }

        String[][] admins = {
                {"A-01", "张川", "13800000001", "超级管理员", "在岗"},
                {"A-02", "林岚", "13800000021", "运营管理员", "在岗"},
                {"A-03", "赵启", "13800000031", "只读观察员", "在岗"}
        };
        for (String[] a : admins) {
            ContentValues v = new ContentValues();
            v.put("id", a[0]);
            v.put("name", a[1]);
            v.put("phone", a[2]);
            v.put("role", a[3]);
            v.put("status", a[4]);
            v.put("created_at", "2026-09-01 09:00:00");
            db.insert("admins", null, v);
        }

        Object[][] audits = {
                {"A-01", "admin.login", "管理员登录管理台", "2026-11-17 08:30:00"},
                {"A-02", "user.status", "停用 U-1004 · 陈默", "2026-11-16 18:10:00"},
                {"A-01", "expense.risk", "标记 E-1007 为超支", "2026-11-16 15:20:00"},
                {"A-03", "export.audit", "导出审计日志 CSV（只读）", "2026-11-15 11:05:00"},
                {"U-1001", "user.login", "小程序用户登录", "2026-11-17 09:10:00"},
                {"U-1002", "user.login", "小程序用户登录", "2026-11-16 20:30:00"}
        };
        for (Object[] a : audits) {
            ContentValues v = new ContentValues();
            v.put("actor", (String) a[0]);
            v.put("action", (String) a[1]);
            v.put("detail", (String) a[2]);
            v.put("created_at", (String) a[3]);
            db.insert("audits", null, v);
        }

        setting(db, "ads", "true");
        setting(db, "export", "true");
        setting(db, "maintenance", "false");
    }

    private void setting(SQLiteDatabase db, String key, String value) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("value", value);
        db.insert("settings", null, v);
    }

    static class User {
        String id, name, phone, city, status, createdAt, projectId, projectName, method;
        double budget, spent;
        int openIssues;
        double getRemaining() { return budget - spent; }
    }

    static class Expense {
        long id;
        String userId, userName, category, payType, spentAt, risk, note;
        double amount;
    }

    static class Node {
        long id;
        String userId, name, status, recordedAt;
        int seq;
    }

    static class Issue {
        long id;
        String userId, userName, title, level, status, recordedAt;
    }

    static class Contract {
        long id;
        String userId, userName, name, status;
        double total, paid;
        int nodesTotal, nodesDone;
        double getPending() { return Math.max(total - paid, 0); }
    }

    static class Admin {
        String id, name, phone, role, status;
    }

    static class Audit {
        long id;
        String actor, action, detail, createdAt;
    }

    static class Stat {
        String label;
        double value;
        Stat(String label, double value) { this.label = label; this.value = value; }
    }

    List<User> users(String query, String status) {
        List<User> list = new ArrayList<>();
        String where = "1=1";
        if (query != null && !query.trim().isEmpty()) {
            where += " AND (name LIKE ? OR phone LIKE ? OR id LIKE ? OR project_name LIKE ?)";
        }
        if (status != null && !"全部".equals(status)) where += " AND status = ?";
        List<String> args = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            String like = "%" + query.trim() + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        if (status != null && !"全部".equals(status)) args.add(status);
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM users WHERE " + where + " ORDER BY created_at DESC",
                args.toArray(new String[0]))) {
            while (c.moveToNext()) list.add(user(c));
        }
        return list;
    }

    User user(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM users WHERE id = ?", new String[]{id})) {
            return c.moveToFirst() ? user(c) : null;
        }
    }

    private User user(Cursor c) {
        User u = new User();
        u.id = c.getString(c.getColumnIndexOrThrow("id"));
        u.name = c.getString(c.getColumnIndexOrThrow("name"));
        u.phone = c.getString(c.getColumnIndexOrThrow("phone"));
        u.city = c.getString(c.getColumnIndexOrThrow("city"));
        u.status = c.getString(c.getColumnIndexOrThrow("status"));
        u.createdAt = c.getString(c.getColumnIndexOrThrow("created_at"));
        u.projectId = c.getString(c.getColumnIndexOrThrow("project_id"));
        u.projectName = c.getString(c.getColumnIndexOrThrow("project_name"));
        u.method = c.getString(c.getColumnIndexOrThrow("method"));
        u.budget = c.getDouble(c.getColumnIndexOrThrow("budget"));
        u.spent = c.getDouble(c.getColumnIndexOrThrow("spent"));
        u.openIssues = c.getInt(c.getColumnIndexOrThrow("open_issues"));
        return u;
    }

    List<Expense> expenses(String risk, String userId) {
        List<Expense> list = new ArrayList<>();
        String where = "1=1";
        List<String> args = new ArrayList<>();
        if (risk != null && !"全部".equals(risk)) { where += " AND risk_status = ?"; args.add(risk); }
        if (userId != null) { where += " AND user_id = ?"; args.add(userId); }
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM expenses WHERE " + where + " ORDER BY spent_at DESC, id DESC",
                args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                Expense e = new Expense();
                e.id = c.getLong(c.getColumnIndexOrThrow("id"));
                e.userId = c.getString(c.getColumnIndexOrThrow("user_id"));
                e.userName = c.getString(c.getColumnIndexOrThrow("user_name"));
                e.category = c.getString(c.getColumnIndexOrThrow("category"));
                e.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
                e.payType = c.getString(c.getColumnIndexOrThrow("pay_type"));
                e.spentAt = c.getString(c.getColumnIndexOrThrow("spent_at"));
                e.risk = c.getString(c.getColumnIndexOrThrow("risk_status"));
                e.note = c.getString(c.getColumnIndexOrThrow("note"));
                list.add(e);
            }
        }
        return list;
    }

    List<Node> nodes(String userId) {
        List<Node> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM nodes WHERE user_id = ? ORDER BY seq", new String[]{userId})) {
            while (c.moveToNext()) {
                Node n = new Node();
                n.id = c.getLong(c.getColumnIndexOrThrow("id"));
                n.userId = c.getString(c.getColumnIndexOrThrow("user_id"));
                n.name = c.getString(c.getColumnIndexOrThrow("name"));
                n.status = c.getString(c.getColumnIndexOrThrow("status"));
                n.recordedAt = c.getString(c.getColumnIndexOrThrow("recorded_at"));
                n.seq = c.getInt(c.getColumnIndexOrThrow("seq"));
                list.add(n);
            }
        }
        return list;
    }

    List<Issue> issues(boolean onlyOpen) {
        List<Issue> list = new ArrayList<>();
        String where = onlyOpen ? "status = '待解决'" : "1=1";
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM issues WHERE " + where + " ORDER BY recorded_at DESC", null)) {
            while (c.moveToNext()) {
                Issue i = new Issue();
                i.id = c.getLong(c.getColumnIndexOrThrow("id"));
                i.userId = c.getString(c.getColumnIndexOrThrow("user_id"));
                i.userName = c.getString(c.getColumnIndexOrThrow("user_name"));
                i.title = c.getString(c.getColumnIndexOrThrow("title"));
                i.level = c.getString(c.getColumnIndexOrThrow("level"));
                i.status = c.getString(c.getColumnIndexOrThrow("status"));
                i.recordedAt = c.getString(c.getColumnIndexOrThrow("recorded_at"));
                list.add(i);
            }
        }
        return list;
    }

    List<Contract> contracts(String userId) {
        List<Contract> list = new ArrayList<>();
        String where = userId == null ? "1=1" : "user_id = ?";
        String[] args = userId == null ? null : new String[]{userId};
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM contracts WHERE " + where + " ORDER BY id", args)) {
            while (c.moveToNext()) {
                Contract ct = new Contract();
                ct.id = c.getLong(c.getColumnIndexOrThrow("id"));
                ct.userId = c.getString(c.getColumnIndexOrThrow("user_id"));
                ct.userName = c.getString(c.getColumnIndexOrThrow("user_name"));
                ct.name = c.getString(c.getColumnIndexOrThrow("name"));
                ct.total = c.getDouble(c.getColumnIndexOrThrow("total_amount"));
                ct.paid = c.getDouble(c.getColumnIndexOrThrow("paid_amount"));
                ct.status = c.getString(c.getColumnIndexOrThrow("status"));
                ct.nodesTotal = c.getInt(c.getColumnIndexOrThrow("payment_nodes_total"));
                ct.nodesDone = c.getInt(c.getColumnIndexOrThrow("payment_nodes_done"));
                list.add(ct);
            }
        }
        return list;
    }

    List<Admin> admins() {
        List<Admin> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM admins ORDER BY id", null)) {
            while (c.moveToNext()) {
                Admin a = new Admin();
                a.id = c.getString(c.getColumnIndexOrThrow("id"));
                a.name = c.getString(c.getColumnIndexOrThrow("name"));
                a.phone = c.getString(c.getColumnIndexOrThrow("phone"));
                a.role = c.getString(c.getColumnIndexOrThrow("role"));
                a.status = c.getString(c.getColumnIndexOrThrow("status"));
                list.add(a);
            }
        }
        return list;
    }

    List<Audit> audits(int limit) {
        List<Audit> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM audits ORDER BY created_at DESC LIMIT " + limit, null)) {
            while (c.moveToNext()) {
                Audit a = new Audit();
                a.id = c.getLong(c.getColumnIndexOrThrow("id"));
                a.actor = c.getString(c.getColumnIndexOrThrow("actor"));
                a.action = c.getString(c.getColumnIndexOrThrow("action"));
                a.detail = c.getString(c.getColumnIndexOrThrow("detail"));
                a.createdAt = c.getString(c.getColumnIndexOrThrow("created_at"));
                list.add(a);
            }
        }
        return list;
    }

    void addAudit(String actor, String action, String detail) {
        ContentValues v = new ContentValues();
        v.put("actor", actor);
        v.put("action", action);
        v.put("detail", detail);
        v.put("created_at", now());
        getWritableDatabase().insert("audits", null, v);
    }

    void updateUserStatus(String id, String status) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        getWritableDatabase().update("users", v, "id = ?", new String[]{id});
    }

    void updateBudget(String id, double budget) {
        ContentValues v = new ContentValues();
        v.put("budget", budget);
        getWritableDatabase().update("users", v, "id = ?", new String[]{id});
    }

    void deleteUser(String id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("expenses", "user_id = ?", new String[]{id});
        db.delete("nodes", "user_id = ?", new String[]{id});
        db.delete("issues", "user_id = ?", new String[]{id});
        db.delete("contracts", "user_id = ?", new String[]{id});
        db.delete("users", "id = ?", new String[]{id});
    }

    void updateRisk(long id, String risk) {
        ContentValues v = new ContentValues();
        v.put("risk_status", risk);
        getWritableDatabase().update("expenses", v, "id = ?", new String[]{String.valueOf(id)});
    }

    void updateIssue(long id, String status) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        getWritableDatabase().update("issues", v, "id = ?", new String[]{String.valueOf(id)});
    }

    void addAdmin(String id, String name, String phone, String role) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("name", name);
        v.put("phone", phone);
        v.put("role", role);
        v.put("status", "在岗");
        v.put("created_at", now());
        getWritableDatabase().insert("admins", null, v);
    }

    String setting(String key) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT value FROM settings WHERE key = ?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }

    void setSetting(String key, String value) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("value", value);
        getWritableDatabase().insertWithOnConflict("settings", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    int count(String table, String where) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + table + (where == null ? "" : " WHERE " + where), null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    double sum(String table, String column, String where) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(" + column + "),0) FROM " + table + (where == null ? "" : " WHERE " + where), null)) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    List<Stat> categories() {
        List<Stat> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT category, SUM(amount) AS amount FROM expenses GROUP BY category ORDER BY amount DESC", null)) {
            while (c.moveToNext()) list.add(new Stat(c.getString(0), c.getDouble(1)));
        }
        return list;
    }

    List<Stat> monthly() {
        List<Stat> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT substr(spent_at, 1, 7) AS month, SUM(amount) AS amount FROM expenses GROUP BY month ORDER BY month", null)) {
            while (c.moveToNext()) list.add(new Stat(c.getString(0), c.getDouble(1)));
        }
        return list;
    }

    List<Stat> cities() {
        List<Stat> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT city, COUNT(*) AS count FROM users WHERE city <> '' GROUP BY city ORDER BY count DESC", null)) {
            while (c.moveToNext()) list.add(new Stat(c.getString(0), c.getDouble(1)));
        }
        return list;
    }

    Map<String, Integer> funnel() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("注册用户", count("users", null));
        map.put("建立项目", count("users", "project_id <> ''"));
        map.put("记过支出", count("expenses", null) > 0 ? count("users", "id IN (SELECT user_id FROM expenses)") : 0);
        map.put("记过进度", count("users", "id IN (SELECT user_id FROM nodes)"));
        map.put("签过合同", count("users", "id IN (SELECT user_id FROM contracts)"));
        return map;
    }

    int newUsers(int days) {
        return count("users", "created_at >= date('now','-" + days + " day')");
    }

    double spendSince(int days) {
        return sum("expenses", "amount", "spent_at >= date('now','-" + days + " day')");
    }

    int activeUsers(int days) {
        List<String> ids = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT actor FROM audits WHERE action = 'user.login' AND created_at >= date('now','-" + days + " day') GROUP BY actor", null)) {
            while (c.moveToNext()) ids.add(c.getString(0));
        }
        return ids.size();
    }

    String exportAudits() {
        StringBuilder sb = new StringBuilder("时间,操作者,动作,详情\n");
        for (Audit a : audits(100)) {
            sb.append(a.createdAt).append(',').append(quote(a.actor)).append(',')
                    .append(quote(a.action)).append(',').append(quote(a.detail)).append('\n');
        }
        return sb.toString();
    }

    private String quote(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    }
}
