package com.renovation.admin;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

/** 服务端账号登录后，把管理端需要展示的数据同步到 remote 镜像库。 */
final class RemoteSync {
    private RemoteSync() {}

    static JSONObject login(String base, String phone, String password, String mfaCode) throws ApiClient.ApiException {
        JSONObject body = new JSONObject();
        try {
            body.put("phone", phone);
            body.put("password", password);
            if (mfaCode != null && !mfaCode.trim().isEmpty()) body.put("mfaCode", mfaCode.trim());
        } catch (Exception ignored) {}
        return ApiClient.request(base, "/admin/login", "POST", body, "");
    }

    static void sync(Db db, String base, String token) throws ApiClient.ApiException {
        db.clearRemoteData();
        SQLiteDatabase raw = db.writable();
        JSONObject users = ApiClient.request(base, "/admin/users?pageSize=100", "GET", null, token);
        JSONArray userItems = users.optJSONArray("items");
        if (userItems != null) {
            for (int i = 0; i < userItems.length(); i++) {
                JSONObject item = userItems.optJSONObject(i);
                if (item == null) continue;
                ContentValues v = new ContentValues();
                v.put("id", str(item, "id"));
                v.put("name", str(item, "name"));
                v.put("phone", str(item, "phone"));
                v.put("city", str(item, "city"));
                v.put("status", str(item, "status"));
                v.put("created_at", str(item, "created_at"));
                v.put("project_id", str(item, "project_id"));
                v.put("project_name", str(item, "project_name"));
                v.put("method", str(item, "method"));
                v.put("budget", dbl(item, "budget"));
                v.put("spent", dbl(item, "spent"));
                v.put("open_issues", item.optInt("open_issues", 0));
                raw.insert("users", null, v);
            }
        }

        JSONObject contracts = ApiClient.request(base, "/admin/contracts?pageSize=100", "GET", null, token);
        JSONArray contractItems = contracts.optJSONArray("items");
        if (contractItems != null) {
            for (int i = 0; i < contractItems.length(); i++) {
                JSONObject item = contractItems.optJSONObject(i);
                if (item == null) continue;
                ContentValues v = new ContentValues();
                v.put("user_id", userIdForProject(raw, str(item, "project_id")));
                v.put("user_name", str(item, "user_name"));
                v.put("name", str(item, "name"));
                v.put("total_amount", dbl(item, "total_amount"));
                v.put("paid_amount", dbl(item, "paid_amount"));
                v.put("status", str(item, "status"));
                v.put("payment_nodes_total", item.optInt("payment_nodes_total", 0));
                v.put("payment_nodes_done", item.optInt("payment_nodes_done", 0));
                raw.insert("contracts", null, v);
            }
        }

        JSONObject expenses = ApiClient.request(base, "/admin/expenses?pageSize=100", "GET", null, token);
        JSONArray expenseItems = expenses.optJSONArray("items");
        if (expenseItems != null) {
            for (int i = 0; i < expenseItems.length(); i++) {
                JSONObject item = expenseItems.optJSONObject(i);
                if (item == null) continue;
                ContentValues v = new ContentValues();
                v.put("user_id", userIdForProject(raw, str(item, "project_id")));
                v.put("user_name", str(item, "user_name"));
                v.put("category", str(item, "category"));
                v.put("amount", dbl(item, "amount"));
                v.put("pay_type", str(item, "pay_type"));
                v.put("spent_at", str(item, "spent_at"));
                v.put("risk_status", str(item, "risk_status"));
                v.put("note", str(item, "note"));
                raw.insert("expenses", null, v);
            }
        }

        JSONObject issues = ApiClient.request(base, "/admin/issues?pageSize=100", "GET", null, token);
        JSONArray issueItems = issues.optJSONArray("items");
        if (issueItems != null) {
            for (int i = 0; i < issueItems.length(); i++) {
                JSONObject item = issueItems.optJSONObject(i);
                if (item == null) continue;
                ContentValues v = new ContentValues();
                v.put("user_id", userIdForProject(raw, str(item, "project_id")));
                v.put("user_name", str(item, "user_name"));
                v.put("title", str(item, "title"));
                v.put("level", str(item, "level"));
                v.put("status", str(item, "status"));
                v.put("recorded_at", str(item, "recorded_at"));
                raw.insert("issues", null, v);
            }
        }

        String ads = ApiClient.request(base, "/admin/ads?range=30", "GET", null, token).toString();
        saveSetting(raw, "ads_snapshot", ads);
        String[] adSlots = {"home_banner", "expenses_feed", "progress_feed", "stats_feed", "export_rewarded", "export_feed"};
        for (String slot : adSlots) {
            String slotSnapshot = ApiClient.request(base, "/admin/ads?range=30&slot=" + slot, "GET", null, token).toString();
            saveSetting(raw, "ads_slot_" + slot, slotSnapshot);
        }
        String analytics = ApiClient.request(base, "/admin/analytics?range=30", "GET", null, token).toString();
        saveSetting(raw, "analytics_snapshot", analytics);
    }

    static JSONObject patchUser(String base, String token, String userId, JSONObject body) throws ApiClient.ApiException {
        return ApiClient.request(base, "/admin/users/" + userId, "PATCH", body, token);
    }

    static JSONObject revokeSessions(String base, String token, String userId) throws ApiClient.ApiException {
        return ApiClient.request(base, "/admin/users/" + userId + "/revoke-sessions", "POST", new JSONObject(), token);
    }

    static JSONObject deleteUser(String base, String token, String userId) throws ApiClient.ApiException {
        return ApiClient.request(base, "/admin/users/" + userId, "DELETE", null, token);
    }

    static JSONObject updateSettings(String base, String token, JSONObject settings) throws ApiClient.ApiException {
        return ApiClient.request(base, "/admin/settings", "PUT", settings, token);
    }

    private static String userIdForProject(SQLiteDatabase raw, String projectId) {
        if (projectId == null || projectId.isEmpty()) return "";
        try (Cursor c = raw.query("users", new String[]{"id"}, "project_id=?", new String[]{projectId}, null, null, null, "1")) {
            return c.moveToFirst() ? c.getString(0) : projectId;
        }
    }

    private static void saveSetting(SQLiteDatabase raw, String key, String value) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("value", value);
        raw.insertWithOnConflict("settings", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static String str(JSONObject item, String key) {
        Object value = item.opt(key);
        return value == null || JSONObject.NULL.equals(value) ? "" : String.valueOf(value);
    }

    private static double dbl(JSONObject item, String key) {
        String value = str(item, key).replace(",", "").replace("¥", "");
        if (value.isEmpty() || "null".equals(value)) return 0;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
