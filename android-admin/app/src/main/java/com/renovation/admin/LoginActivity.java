package com.renovation.admin;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class LoginActivity extends Activity {
    private EditText phoneInput;
    private EditText passwordInput;
    private EditText mfaInput;
    private EditText baseInput;
    private TextView submit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Session.load(this);
        SharedPreferences sp = getSharedPreferences("session", MODE_PRIVATE);
        if (sp.getBoolean("logged_in", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 54), Ui.dp(this, 24), Ui.dp(this, 24));

        LinearLayout brand = Ui.row(this);
        TextView mark = Ui.text(this, "装", 22, true, Color.WHITE);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.rounded(Ui.BLUE, Ui.dp(this, 15), Color.TRANSPARENT, 0));
        mark.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));
        brand.addView(mark);
        LinearLayout brandText = Ui.column(this);
        brandText.setPadding(Ui.dp(this, 12), 0, 0, 0);
        brandText.addView(Ui.text(this, "装修记", 24, true, Ui.INK));
        brandText.addView(Ui.text(this, "ADMIN WORKSPACE", 10, true, Ui.BLUE));
        brand.addView(brandText);
        root.addView(brand);

        root.addView(Ui.spacer(this, 8));
        root.addView(Ui.text(this, "把每一笔支出、每一个节点、每一份合同，\n放在同一张桌面上。", 20, true, Ui.INK));
        root.addView(Ui.spacer(this, 8));
        root.addView(Ui.text(this, "管理员数据工作台 · 本地演示 + 服务端账号", 12, false, Ui.MUTED));

        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams cardLp = Ui.matchWrap(this);
        cardLp.topMargin = Ui.dp(this, 28);
        card.setLayoutParams(cardLp);
        card.addView(Ui.text(this, "登录管理台", 18, true, Ui.INK));
        card.addView(Ui.spacer(this, 4));
        card.addView(Ui.text(this, "13800000001 / admin123 为本地演示账号；其他手机号直接验证服务端账号并同步 MySQL 数据。", 11, false, Ui.MUTED));
        card.addView(Ui.spacer(this, 18));
        card.addView(Ui.label(this, "管理员手机号"));
        phoneInput = Ui.input(this, "请输入手机号");
        phoneInput.setText("13800000001");
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        LinearLayout.LayoutParams inputLp = Ui.matchWrap(this);
        inputLp.topMargin = Ui.dp(this, 7);
        phoneInput.setLayoutParams(inputLp);
        card.addView(phoneInput);
        card.addView(Ui.spacer(this, 14));
        card.addView(Ui.label(this, "登录密码"));
        passwordInput = Ui.input(this, "演示密码 admin123");
        passwordInput.setText("admin123");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setLayoutParams(inputLp);
        card.addView(passwordInput);
        card.addView(Ui.spacer(this, 14));
        card.addView(Ui.label(this, "MFA 动态码（服务端账号已开启时填写）"));
        mfaInput = Ui.input(this, "6 位动态码，可留空");
        mfaInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        mfaInput.setLayoutParams(inputLp);
        card.addView(mfaInput);
        card.addView(Ui.spacer(this, 14));
        card.addView(Ui.label(this, "服务端 API 地址（仅远程账号使用）"));
        baseInput = Ui.input(this, "http://服务器IP:5000/api");
        baseInput.setText(Session.baseUrl);
        baseInput.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        baseInput.setLayoutParams(inputLp);
        card.addView(baseInput);
        card.addView(Ui.spacer(this, 20));
        submit = Ui.button(this, "进入管理台", true);
        submit.setTextSize(15);
        submit.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        submit.setLayoutParams(Ui.matchWrap(this));
        submit.setOnClickListener(v -> doLogin());
        card.addView(submit);
        card.addView(Ui.spacer(this, 14));
        TextView hint = Ui.text(this, "本地演示账号 13800000001 / admin123", 11, false, Ui.MUTED);
        hint.setGravity(Gravity.CENTER);
        card.addView(hint);
        root.addView(card);

        root.addView(Ui.spacer(this, 24));
        LinearLayout note = Ui.card(this);
        note.setBackground(Ui.rounded(Ui.BLUE_SOFT, Ui.dp(this, 16), Color.TRANSPARENT, 0));
        note.addView(Ui.text(this, "数据边界", 12, true, Ui.BLUE));
        note.addView(Ui.spacer(this, 5));
        note.addView(Ui.text(this, "本地演示账号只读本地 SQLite 演示数据；其他账号登录后从 Flask API 同步服务端 MySQL 快照，管理端不保存数据库账号或 MySQL 密码。", 11, false, Ui.MUTED));
        root.addView(note);

        TextView footer = Ui.text(this, "RENOVATION · CONTROL DESK  /  v1.0.19", 9, true, Color.parseColor("#9AA6BD"));
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = Ui.matchWrap(this);
        footerLp.topMargin = Ui.dp(this, 22);
        footer.setLayoutParams(footerLp);
        root.addView(footer);

        setContentView(root);
    }

    private void doLogin() {
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String mfa = mfaInput.getText().toString().trim();
        String base = ApiClient.normalizeBase(baseInput.getText().toString());
        if (phone.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入手机号和密码", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("13800000001".equals(phone) && "admin123".equals(password)) {
            Session.demo = true;
            Session.token = "";
            Session.refreshToken = "";
            Session.name = "张川";
            Session.phone = phone;
            Session.role = "超级管理员";
            Session.baseUrl = base;
            Session.save(this);
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject data = RemoteSync.login(base, phone, password, mfa);
                JSONObject admin = data.optJSONObject("admin");
                Session.demo = false;
                Session.token = data.optString("accessToken", "");
                Session.refreshToken = data.optString("refreshToken", "");
                Session.phone = phone;
                Session.baseUrl = base;
                Session.name = admin == null ? "管理员" : admin.optString("name", "管理员");
                Session.role = admin == null ? "管理员" : admin.optString("role", "管理员");
                // 仅同步管理端展示所需数据，写入 remote 镜像库；不覆盖本地演示库。
                RemoteSync.sync(Db.getRemote(this), base, Session.token);
                Session.save(this);
                runOnUiThread(() -> {
                    setLoading(false);
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
            } catch (ApiClient.ApiException error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    String message = error.getMessage();
                    if ("mfa_required".equals(error.code)) message = "该账号已开启 MFA，请填写 6 位动态码";
                    if ("mfa_setup_required".equals(error.code)) message = "该账号尚未绑定 MFA，请先在服务端完成 MFA 绑定";
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(this, "同步服务端数据失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void setLoading(boolean loading) {
        submit.setEnabled(!loading);
        submit.setText(loading ? "正在验证并同步服务端数据…" : "进入管理台");
    }
}
