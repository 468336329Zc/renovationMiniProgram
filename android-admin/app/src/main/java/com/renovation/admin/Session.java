package com.renovation.admin;

import android.content.Context;
import android.content.SharedPreferences;

/** 管理端登录态：演示账号只读本地库；服务端账号读取远程镜像缓存。 */
final class Session {
    static boolean demo = true;
    static String token = "";
    static String refreshToken = "";
    static String name = "张川";
    static String phone = "13800000001";
    static String role = "超级管理员";
    static String baseUrl = ApiClient.DEFAULT_BASE_URL;

    private Session() {}

    static void load(Context context) {
        SharedPreferences sp = context.getSharedPreferences("session", Context.MODE_PRIVATE);
        demo = sp.getBoolean("demo", true);
        token = sp.getString("token", "");
        refreshToken = sp.getString("refresh_token", "");
        name = sp.getString("admin_name", "张川");
        phone = sp.getString("admin_phone", "13800000001");
        role = sp.getString("admin_role", demo ? "超级管理员" : "管理员");
        baseUrl = sp.getString("api_base", ApiClient.DEFAULT_BASE_URL);
    }

    static void save(Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences("session", Context.MODE_PRIVATE).edit();
        editor.putBoolean("logged_in", true)
                .putBoolean("demo", demo)
                .putString("token", token)
                .putString("refresh_token", refreshToken)
                .putString("admin_name", name)
                .putString("admin_phone", phone)
                .putString("admin_role", role)
                .putString("api_base", baseUrl)
                .apply();
    }

    static void clear(Context context) {
        context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().clear().apply();
        token = "";
        refreshToken = "";
        demo = true;
    }
}
