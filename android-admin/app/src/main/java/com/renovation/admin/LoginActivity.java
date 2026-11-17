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

public class LoginActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        root.addView(Ui.text(this, "管理员数据工作台 · 本地演示版", 12, false, Ui.MUTED));

        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams cardLp = Ui.matchWrap(this);
        cardLp.topMargin = Ui.dp(this, 36);
        card.setLayoutParams(cardLp);
        card.addView(Ui.text(this, "登录管理台", 18, true, Ui.INK));
        card.addView(Ui.spacer(this, 4));
        card.addView(Ui.text(this, "账号仅用于本地演示；正式版由服务端签发令牌并校验角色。", 11, false, Ui.MUTED));
        card.addView(Ui.spacer(this, 18));
        card.addView(Ui.label(this, "管理员手机号"));
        EditText phone = Ui.input(this, "请输入手机号");
        phone.setText("13800000001");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        LinearLayout.LayoutParams inputLp = Ui.matchWrap(this);
        inputLp.topMargin = Ui.dp(this, 7);
        phone.setLayoutParams(inputLp);
        card.addView(phone);
        card.addView(Ui.spacer(this, 14));
        card.addView(Ui.label(this, "登录密码"));
        EditText password = Ui.input(this, "演示密码 admin123");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setLayoutParams(inputLp);
        card.addView(password);
        card.addView(Ui.spacer(this, 20));
        TextView submit = Ui.button(this, "进入管理台", true);
        submit.setTextSize(15);
        submit.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        submit.setLayoutParams(Ui.matchWrap(this));
        submit.setOnClickListener(v -> {
            String p = phone.getText().toString().trim();
            String code = password.getText().toString().trim();
            if (p.isEmpty()) { Toast.makeText(this, "请输入手机号", Toast.LENGTH_SHORT).show(); return; }
            if (!"admin123".equals(code)) { Toast.makeText(this, "演示密码为 admin123", Toast.LENGTH_SHORT).show(); return; }
            sp.edit().putBoolean("logged_in", true).putString("admin_name", "张川").apply();
            Db.get(this).addAudit("A-01", "admin.login", "管理员登录管理台");
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        card.addView(submit);
        card.addView(Ui.spacer(this, 14));
        TextView hint = Ui.text(this, "演示账号  13800000001  /  admin123", 11, false, Ui.MUTED);
        hint.setGravity(Gravity.CENTER);
        card.addView(hint);
        root.addView(card);

        root.addView(Ui.spacer(this, 24));
        LinearLayout note = Ui.card(this);
        note.setBackground(Ui.rounded(Ui.BLUE_SOFT, Ui.dp(this, 16), Color.TRANSPARENT, 0));
        note.addView(Ui.text(this, "安全边界", 12, true, Ui.BLUE));
        note.addView(Ui.spacer(this, 5));
        note.addView(Ui.text(this, "本 APK 为管理员端本地演示，数据存于设备 SQLite。正式部署时角色、用户状态、导出解锁与审计均由 Flask 服务端判定，客户端不保存数据库账号或管理员密钥。", 11, false, Ui.MUTED));
        root.addView(note);

        TextView footer = Ui.text(this, "RENOVATION · CONTROL DESK  /  v1.0.0", 9, true, Color.parseColor("#9AA6BD"));
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = Ui.matchWrap(this);
        footerLp.topMargin = Ui.dp(this, 22);
        footer.setLayoutParams(footerLp);
        root.addView(footer);

        setContentView(root);
    }
}
