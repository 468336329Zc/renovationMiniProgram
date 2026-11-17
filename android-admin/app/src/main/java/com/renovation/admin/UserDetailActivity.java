package com.renovation.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class UserDetailActivity extends Activity {
    private Db db;
    private String userId;
    private Db.User user;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView[] tabs;
    private int active;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = Db.get(this);
        userId = getIntent().getStringExtra("user_id");
        user = db.user(userId);
        if (user == null) { finish(); return; }
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(buildHeader());
        root.addView(buildTabs());
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 26));
        scroll.addView(content);
        root.addView(scroll);
        setContentView(root);
        select(0);
    }

    private View buildHeader() {
        LinearLayout header = Ui.column(this);
        header.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 8));
        LinearLayout top = Ui.row(this);
        TextView back = Ui.text(this, "‹", 26, true, Ui.BLUE);
        back.setGravity(Gravity.CENTER);
        back.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 40)));
        back.setOnClickListener(v -> finish());
        top.addView(back);
        LinearLayout name = Ui.column(this);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        name.addView(Ui.text(this, user.name, 20, true, Ui.INK));
        name.addView(Ui.text(this, user.id + " · " + user.city + " · " + user.phone, 9, false, Ui.MUTED));
        top.addView(name);
        top.addView(Ui.pill(this, user.status, Ui.statusFg(user.status), Ui.statusBg(user.status)));
        header.addView(top);
        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 10), 0, 0);
        TextView toggle = Ui.button(this, "停用账号", false);
        toggle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        toggle.setOnClickListener(v -> {
            String next = "正常".equals(user.status) ? "停用" : "正常";
            db.updateUserStatus(user.id, next);
            db.addAudit("A-01", "user.status", (next.equals("停用") ? "停用 " : "启用 ") + user.id + " · " + user.name);
            Toast.makeText(this, "账号状态已更新", Toast.LENGTH_SHORT).show();
            recreate();
        });
        TextView budget = Ui.button(this, "调整预算", false);
        LinearLayout.LayoutParams budgetLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        budgetLp.leftMargin = Ui.dp(this, 8);
        budget.setLayoutParams(budgetLp);
        budget.setOnClickListener(v -> editBudget());
        actions.addView(toggle);
        actions.addView(budget);
        header.addView(actions);
        LinearLayout actions2 = Ui.row(this);
        actions2.setPadding(0, Ui.dp(this, 8), 0, 0);
        TextView revoke = Ui.ghostButton(this, "强制下线");
        revoke.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        revoke.setOnClickListener(v -> {
            db.addAudit("A-01", "user.revoke", "强制下线 " + user.id + " 的全部会话");
            Toast.makeText(this, "已吊销该用户的本地演示会话", Toast.LENGTH_SHORT).show();
        });
        TextView delete = Ui.ghostButton(this, "删除用户");
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        deleteLp.leftMargin = Ui.dp(this, 8);
        delete.setLayoutParams(deleteLp);
        delete.setOnClickListener(v -> confirmDelete());
        actions2.addView(revoke);
        actions2.addView(delete);
        header.addView(actions2);
        return header;
    }

    private View buildTabs() {
        LinearLayout nav = Ui.row(this);
        nav.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 8));
        String[] names = {"概览", "支出", "进度", "合同"};
        tabs = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            TextView tab = Ui.text(this, names[i], 12, true, Ui.MUTED);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            tab.setBackground(Ui.rounded(Color.WHITE, Ui.dp(this, 10), Color.TRANSPARENT, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1);
            lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            tab.setLayoutParams(lp);
            final int index = i;
            tab.setOnClickListener(v -> select(index));
            tabs[i] = tab;
            nav.addView(tab);
        }
        return nav;
    }

    private void select(int index) {
        active = index;
        for (int i = 0; i < tabs.length; i++) {
            boolean on = i == index;
            tabs[i].setTextColor(on ? Color.WHITE : Ui.MUTED);
            tabs[i].setBackground(Ui.rounded(on ? Ui.BLUE : Color.WHITE, Ui.dp(this, 10), Color.TRANSPARENT, 0));
        }
        content.removeAllViews();
        user = db.user(userId);
        if (index == 0) renderOverview();
        else if (index == 1) renderExpenses();
        else if (index == 2) renderNodes();
        else renderContracts();
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void renderOverview() {
        LinearLayout hero = Ui.card(this);
        hero.setBackground(Ui.rounded(Ui.BLUE_DARK, Ui.dp(this, 20), Color.TRANSPARENT, 0));
        hero.addView(Ui.text(this, "当前项目", 10, true, Color.parseColor("#9FB8FF")));
        hero.addView(Ui.spacer(this, 5));
        hero.addView(Ui.text(this, user.projectName, 18, true, Color.WHITE));
        hero.addView(Ui.text(this, user.method + " · " + user.id + " · 预算 " + ChartView.shortMoney(user.budget), 10, false, Color.parseColor("#C8D6FF")));
        hero.addView(Ui.spacer(this, 16));
        LinearLayout row = Ui.row(this);
        row.addView(heroStat("已支出", ChartView.shortMoney(user.spent), "元"));
        row.addView(heroStat("剩余", ChartView.shortMoney(user.getRemaining()), "元"));
        row.addView(heroStat("待解决", String.valueOf(user.openIssues), "条"));
        hero.addView(row);
        add(hero, 0, 0);

        LinearLayout kpi = Ui.row(this);
        kpi.addView(kpiCard("预算执行", pct(user.budget == 0 ? 0 : user.spent / user.budget * 100), "预算 " + ChartView.shortMoney(user.budget)));
        kpi.addView(kpiCard("合同待付", ChartView.shortMoney(pendingOfUser()), "已签 " + db.contracts(user.id).size() + " 份"));
        add(kpi, 8, 0);

        content.addView(section("风险提示", "进入详情逐项处理"));
        List<Db.Issue> issues = db.issues(true);
        int count = 0;
        for (Db.Issue issue : issues) {
            if (!issue.userId.equals(userId)) continue;
            count++;
            content.addView(issueCard(issue));
        }
        if (count == 0) content.addView(empty("该用户当前没有待解决问题"));

        content.addView(section("审计记录", "与该用户相关的近 5 条"));
        LinearLayout auditCard = Ui.card(this);
        int shown = 0;
        for (Db.Audit audit : db.audits(30)) {
            if (!audit.detail.contains(user.id) && !audit.detail.contains(user.name)) continue;
            if (shown++ >= 5) break;
            auditCard.addView(auditRow(audit));
        }
        if (shown == 0) auditCard.addView(Ui.text(this, "暂无", 11, false, Ui.MUTED));
        add(auditCard, 0, 8);
    }

    private View heroStat(String label, String value, String unit) {
        LinearLayout box = Ui.column(this);
        box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(Ui.text(this, label, 9, false, Color.parseColor("#9FB8FF")));
        box.addView(Ui.text(this, value + " " + unit, 16, true, Color.WHITE));
        return box;
    }

    private View kpiCard(String label, String value, String sub) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
        card.setLayoutParams(lp);
        card.addView(Ui.text(this, label, 10, true, Ui.MUTED));
        card.addView(Ui.spacer(this, 5));
        card.addView(Ui.text(this, value, 19, true, Ui.INK));
        card.addView(Ui.text(this, sub, 9, false, Ui.MUTED));
        return card;
    }

    private void renderExpenses() {
        content.addView(section("支出明细", "所有记录均来自本地演示数据"));
        List<Db.Expense> expenses = db.expenses(null, userId);
        if (expenses.isEmpty()) content.addView(empty("暂无支出"));
        for (Db.Expense expense : expenses) {
            LinearLayout card = Ui.card(this);
            LinearLayout top = Ui.row(this);
            LinearLayout left = Ui.column(this);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            left.addView(Ui.text(this, expense.category, 13, true, Ui.INK));
            left.addView(Ui.text(this, expense.payType + " · " + expense.spentAt, 9, false, Ui.MUTED));
            top.addView(left);
            top.addView(Ui.text(this, ChartView.money(expense.amount), 16, true, Ui.INK));
            card.addView(top);
            LinearLayout foot = Ui.row(this);
            foot.setPadding(0, Ui.dp(this, 8), 0, 0);
            foot.addView(Ui.pill(this, expense.risk, Ui.statusFg(expense.risk), Ui.statusBg(expense.risk)));
            TextView note = Ui.text(this, expense.note, 9, false, Ui.MUTED);
            note.setPadding(Ui.dp(this, 8), 0, 0, 0);
            note.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            foot.addView(note);
            card.addView(foot);
            add(card, 8, 0);
        }
    }

    private void renderNodes() {
        content.addView(section("装修进度", "节点状态影响用户端首页"));
        List<Db.Node> nodes = db.nodes(userId);
        if (nodes.isEmpty()) content.addView(empty("暂无节点"));
        for (Db.Node node : nodes) {
            LinearLayout card = Ui.card(this);
            LinearLayout top = Ui.row(this);
            TextView seq = Ui.text(this, String.format(Locale.CHINA, "%02d", node.seq), 10, true, Ui.BLUE);
            seq.setGravity(Gravity.CENTER);
            seq.setBackground(Ui.rounded(Ui.BLUE_SOFT, Ui.dp(this, 8), Color.TRANSPARENT, 0));
            seq.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
            top.addView(seq);
            TextView name = Ui.text(this, node.name, 13, true, Ui.INK);
            name.setPadding(Ui.dp(this, 10), 0, 0, 0);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            top.addView(name);
            top.addView(Ui.pill(this, node.status, Ui.statusFg(node.status), Ui.statusBg(node.status)));
            card.addView(top);
            if (node.recordedAt != null && !node.recordedAt.isEmpty()) {
                card.addView(Ui.text(this, "记录时间 " + node.recordedAt, 9, false, Ui.MUTED));
            }
            add(card, 7, 0);
        }
    }

    private void renderContracts() {
        content.addView(section("合同台账", "已付与待付节点"));
        List<Db.Contract> contracts = db.contracts(userId);
        if (contracts.isEmpty()) content.addView(empty("暂无合同"));
        for (Db.Contract contract : contracts) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, contract.name, 13, true, Ui.INK));
            card.addView(Ui.text(this, contract.status + " · 节点 " + contract.nodesDone + "/" + contract.nodesTotal, 9, false, Ui.MUTED));
            card.addView(Ui.spacer(this, 8));
            LinearLayout line = Ui.row(this);
            line.addView(Ui.text(this, "已付 " + ChartView.shortMoney(contract.paid), 11, true, Ui.BLUE));
            TextView pending = Ui.text(this, "待付 " + ChartView.shortMoney(contract.getPending()), 11, true, Ui.ORANGE);
            pending.setGravity(Gravity.END);
            pending.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            line.addView(pending);
            card.addView(line);
            ProgressBar bar = progress(contract.total == 0 ? 0 : contract.paid / contract.total * 100);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
            lp.topMargin = Ui.dp(this, 7);
            bar.setLayoutParams(lp);
            card.addView(bar);
            add(card, 8, 0);
        }
    }

    private void editBudget() {
        EditText input = Ui.input(this, "预算金额");
        input.setText(String.valueOf((long) user.budget));
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("调整预算")
                .setView(box)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        double value = Double.parseDouble(input.getText().toString().trim());
                        if (value < 0) throw new NumberFormatException();
                        db.updateBudget(user.id, value);
                        db.addAudit("A-01", "user.budget", "调整 " + user.id + " 预算为 " + ChartView.money(value));
                        Toast.makeText(this, "预算已更新", Toast.LENGTH_SHORT).show();
                        recreate();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "请输入有效金额", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("删除用户")
                .setMessage("将删除 " + user.name + " 的本地项目、支出、节点与合同数据，且不可恢复。")
                .setPositiveButton("确认删除", (dialog, which) -> {
                    db.deleteUser(user.id);
                    db.addAudit("A-01", "user.delete", "删除用户 " + user.id + " · " + user.name);
                    Toast.makeText(this, "用户已删除", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private double pendingOfUser() {
        double pending = 0;
        for (Db.Contract contract : db.contracts(userId)) pending += contract.getPending();
        return pending;
    }

    private View issueCard(Db.Issue issue) {
        LinearLayout card = Ui.card(this);
        LinearLayout top = Ui.row(this);
        top.addView(Ui.pill(this, issue.level, levelFg(issue.level), levelBg(issue.level)));
        TextView title = Ui.text(this, issue.title, 12, true, Ui.INK);
        title.setPadding(Ui.dp(this, 8), 0, 0, 0);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(title);
        String resolved = issue.status.equals("已解决") ? "已完成" : "待核";
        top.addView(Ui.pill(this, issue.status, Ui.statusFg(resolved), Ui.statusBg(resolved)));
        card.addView(top);
        card.addView(Ui.text(this, issue.recordedAt, 9, false, Ui.MUTED));
        add(card, 7, 0);
        return card;
    }

    private View auditRow(Db.Audit audit) {
        LinearLayout row = Ui.column(this);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        row.addView(Ui.text(this, audit.action + " · " + audit.createdAt, 9, true, Ui.BLUE));
        row.addView(Ui.text(this, audit.detail, 10, false, Ui.INK));
        return row;
    }

    private View section(String title, String subtitle) {
        LinearLayout box = Ui.column(this);
        box.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 8));
        box.addView(Ui.text(this, title, 15, true, Ui.INK));
        box.addView(Ui.text(this, subtitle, 9, false, Ui.MUTED));
        return box;
    }

    private View empty(String text) {
        LinearLayout box = Ui.card(this);
        box.addView(Ui.text(this, text, 11, false, Ui.MUTED));
        return box;
    }

    private ProgressBar progress(double pct) {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(Math.max(0, Math.min(100, (int) Math.round(pct))));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(pct > 100 ? Ui.RED : Ui.BLUE));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#EAF0FA")));
        return bar;
    }

    private void add(View view, int top, int bottom) {
        LinearLayout.LayoutParams lp = Ui.matchWrap(this);
        lp.setMargins(0, Ui.dp(this, top), 0, Ui.dp(this, bottom));
        view.setLayoutParams(lp);
        content.addView(view);
    }

    private String pct(double value) { return String.format(Locale.CHINA, "%.1f%%", value); }

    private int levelFg(String level) {
        return "高".equals(level) ? Ui.RED : ("中".equals(level) ? Ui.ORANGE : Ui.MUTED);
    }

    private int levelBg(String level) {
        return "高".equals(level) ? Ui.RED_SOFT : ("中".equals(level) ? Ui.ORANGE_SOFT : Color.parseColor("#EEF2F9"));
    }
}
