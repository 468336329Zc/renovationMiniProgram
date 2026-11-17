package com.renovation.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private Db db;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView[] tabs;
    private int activeTab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = Db.get(this);
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
        content.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 28));
        scroll.addView(content);
        root.addView(scroll);
        setContentView(root);
        selectTab(0);
    }

    private View buildHeader() {
        LinearLayout header = Ui.row(this);
        header.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 8));
        header.setBackgroundColor(Ui.BG);
        LinearLayout left = Ui.column(this);
        TextView title = Ui.text(this, "管理台", 24, true, Ui.INK);
        left.addView(title);
        left.addView(Ui.text(this, "装修记 / 数据工作台", 10, true, Ui.BLUE));
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        left.setLayoutParams(leftLp);
        header.addView(left);

        LinearLayout right = Ui.column(this);
        right.setGravity(Gravity.END);
        TextView avatar = Ui.text(this, "张", 13, true, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.rounded(Ui.BLUE_DARK, Ui.dp(this, 12), Color.TRANSPARENT, 0));
        avatar.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        right.addView(avatar);
        right.addView(Ui.text(this, "张川 · 超管", 9, false, Ui.MUTED));
        header.addView(right);
        return header;
    }

    private View buildTabs() {
        LinearLayout nav = Ui.row(this);
        nav.setPadding(Ui.dp(this, 12), Ui.dp(this, 2), Ui.dp(this, 12), Ui.dp(this, 10));
        String[] names = {"概览", "用户", "数据", "运营", "管理"};
        tabs = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            TextView tab = Ui.text(this, names[i], 12, true, Ui.MUTED);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            tab.setBackground(Ui.rounded(Color.WHITE, Ui.dp(this, 10), Color.TRANSPARENT, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1);
            lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            tab.setLayoutParams(lp);
            final int index = i;
            tab.setOnClickListener(v -> selectTab(index));
            tabs[i] = tab;
            nav.addView(tab);
        }
        return nav;
    }

    private void selectTab(int index) {
        activeTab = index;
        for (int i = 0; i < tabs.length; i++) {
            boolean active = i == index;
            tabs[i].setTextColor(active ? Color.WHITE : Ui.MUTED);
            tabs[i].setBackground(Ui.rounded(active ? Ui.BLUE : Color.WHITE, Ui.dp(this, 10), Color.TRANSPARENT, 0));
        }
        content.removeAllViews();
        switch (index) {
            case 0: buildOverview(); break;
            case 1: buildUsers(); break;
            case 2: buildData(); break;
            case 3: buildOperations(); break;
            default: buildManage(); break;
        }
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void buildOverview() {
        List<Db.User> users = db.users(null, null);
        double budget = 0, spent = 0;
        int active = 0;
        for (Db.User u : users) {
            budget += u.budget;
            spent += u.spent;
            if ("正常".equals(u.status)) active++;
        }
        double pending = 0;
        for (Db.Contract c : db.contracts(null)) pending += c.getPending();
        int openIssues = db.issues(true).size();
        int projects = users.size();
        int audits7 = db.count("audits", null);
        double pct = budget == 0 ? 0 : spent / budget * 100;

        LinearLayout hero = Ui.card(this);
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.BLUE_DARK, Color.parseColor("#143D91")});
        heroBg.setCornerRadius(Ui.dp(this, 22));
        hero.setBackground(heroBg);
        LinearLayout heroTop = Ui.row(this);
        heroTop.addView(Ui.text(this, "PLATFORM SPEND", 10, true, Color.parseColor("#9FB8FF")));
        TextView live = Ui.pill(this, "LIVE · 本地", Color.parseColor("#B8C8FF"), Color.parseColor("#213E82"));
        LinearLayout.LayoutParams liveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        liveLp.leftMargin = Ui.dp(this, 10);
        live.setLayoutParams(liveLp);
        heroTop.addView(live);
        hero.addView(heroTop);
        hero.addView(Ui.spacer(this, 10));
        hero.addView(Ui.text(this, money(spent), 34, true, Color.WHITE));
        hero.addView(Ui.spacer(this, 4));
        hero.addView(Ui.text(this, "已支出 · 总预算 " + money(budget) + " · 执行 " + pct(pct), 11, false, Color.parseColor("#C8D6FF")));
        hero.addView(Ui.spacer(this, 18));
        LinearLayout heroRow = Ui.row(this);
        heroRow.addView(heroStat("在建项目", String.valueOf(projects), "个"));
        heroRow.addView(heroStat("合同待付", ChartView.shortMoney(pending), "元"));
        heroRow.addView(heroStat("待解决", String.valueOf(openIssues), "条"));
        hero.addView(heroRow);
        addWithMargins(hero, 0, 0, 0, 4);

        LinearLayout grid1 = Ui.row(this);
        grid1.addView(kpi("用户总数", String.valueOf(users.size()), "正常 " + active + " · 停用 " + (users.size() - active), 1f));
        grid1.addView(kpi("本月新增", String.valueOf(db.newUsers(30)), "近 30 天注册", 1f));
        addWithMargins(grid1, 0, 4, 0, 0);

        LinearLayout grid2 = Ui.row(this);
        grid2.addView(kpi("合同总额", ChartView.shortMoney(db.sum("contracts", "total_amount", null)), "待付 " + ChartView.shortMoney(pending), 1f));
        grid2.addView(kpi("审计动作", String.valueOf(audits7), "本地已记录", 1f));
        addWithMargins(grid2, 0, 8, 0, 0);

        content.addView(section("支出趋势", "按月汇总 · 单位元"));
        LinearLayout chartCard = Ui.card(this);
        ChartView chart = new ChartView(this);
        chart.setData(db.monthly(), true, true);
        chart.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 158)));
        chartCard.addView(chart);
        addWithMargins(chartCard, 0, 0, 0, 0);

        content.addView(section("费用构成", "按分类 · 识别超支"));
        LinearLayout categoryCard = Ui.card(this);
        for (Db.Stat stat : db.categories()) {
            categoryCard.addView(categoryRow(stat.label, stat.value, spent));
        }
        addWithMargins(categoryCard, 0, 0, 0, 0);

        content.addView(section("需要关注", "按风险级别排序"));
        List<Db.Issue> issues = db.issues(true);
        int shown = 0;
        for (Db.Issue issue : issues) {
            if (shown++ >= 4) break;
            content.addView(issueCard(issue));
        }
        if (issues.isEmpty()) content.addView(empty("当前没有待解决问题"));

        content.addView(section("最近审计", "谁在什么时候改了什么"));
        LinearLayout auditCard = Ui.card(this);
        for (Db.Audit audit : db.audits(4)) auditCard.addView(auditRow(audit));
        addWithMargins(auditCard, 0, 0, 0, 0);
    }

    private View heroStat(String label, String value, String unit) {
        LinearLayout box = Ui.column(this);
        box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(Ui.text(this, label, 9, false, Color.parseColor("#9FB8FF")));
        LinearLayout line = Ui.row(this);
        line.addView(Ui.text(this, value, 20, true, Color.WHITE));
        TextView unitView = Ui.text(this, " " + unit, 9, false, Color.parseColor("#B8C8FF"));
        unitView.setPadding(0, Ui.dp(this, 7), 0, 0);
        line.addView(unitView);
        box.addView(line);
        return box;
    }

    private View kpi(String label, String value, String sub, float weight) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
        card.setLayoutParams(lp);
        card.addView(Ui.text(this, label, 11, true, Ui.MUTED));
        card.addView(Ui.spacer(this, 6));
        card.addView(Ui.text(this, value, 22, true, Ui.INK));
        card.addView(Ui.spacer(this, 3));
        card.addView(Ui.text(this, sub, 9, false, Ui.MUTED));
        return card;
    }

    private View categoryRow(String label, double amount, double total) {
        LinearLayout row = Ui.column(this);
        row.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
        LinearLayout top = Ui.row(this);
        top.addView(Ui.text(this, label, 12, true, Ui.INK));
        TextView value = Ui.text(this, ChartView.money(amount), 12, true, Ui.INK);
        value.setGravity(Gravity.END);
        value.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(value);
        row.addView(top);
        ProgressBar bar = progress(total == 0 ? 0 : amount / total * 100);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
        lp.topMargin = Ui.dp(this, 7);
        bar.setLayoutParams(lp);
        row.addView(bar);
        return row;
    }

    private void buildUsers() {
        content.addView(section("用户资产", "搜索、筛选、进入详情管控"));
        final EditText search = Ui.input(this, "搜索姓名 / 手机号 / 项目");
        search.setSingleLine(true);
        content.addView(search);
        final LinearLayout list = Ui.column(this);
        LinearLayout chips = Ui.row(this);
        final String[] status = {"全部", "正常", "停用"};
        final String[] current = {"全部"};
        final TextView[] chipViews = new TextView[status.length];
        for (int i = 0; i < status.length; i++) {
            TextView chip = Ui.pill(this, status[i], Ui.MUTED, Color.parseColor("#EEF2F9"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, Ui.dp(this, 8), Ui.dp(this, 8), 0);
            chip.setLayoutParams(lp);
            final String value = status[i];
            chip.setOnClickListener(v -> {
                current[0] = value;
                for (int j = 0; j < chipViews.length; j++) {
                    boolean on = status[j].equals(value);
                    chipViews[j].setTextColor(on ? Color.WHITE : Ui.MUTED);
                    chipViews[j].setBackground(Ui.rounded(on ? Ui.BLUE : Color.parseColor("#EEF2F9"), Ui.dp(this, 999), Color.TRANSPARENT, 0));
                }
                renderUsers(list, search.getText().toString(), value);
            });
            chipViews[i] = chip;
            chips.addView(chip);
        }
        content.addView(chips);
        content.addView(list);
        renderUsers(list, "", "全部");
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderUsers(list, s.toString(), current[0]);
            }
            public void afterTextChanged(Editable s) {}
        });
    }

    private void renderUsers(LinearLayout list, String query, String status) {
        list.removeAllViews();
        List<Db.User> users = db.users(query, status);
        if (users.isEmpty()) { list.addView(empty("没有匹配的用户")); return; }
        for (Db.User user : users) {
            LinearLayout card = Ui.card(this);
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, UserDetailActivity.class);
                intent.putExtra("user_id", user.id);
                startActivity(intent);
            });
            LinearLayout top = Ui.row(this);
            LinearLayout nameBox = Ui.column(this);
            nameBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            LinearLayout nameLine = Ui.row(this);
            nameLine.addView(Ui.text(this, user.name, 16, true, Ui.INK));
            TextView idView = Ui.text(this, "  " + user.id, 10, false, Ui.MUTED);
            idView.setPadding(0, Ui.dp(this, 2), 0, 0);
            nameLine.addView(idView);
            nameBox.addView(nameLine);
            nameBox.addView(Ui.text(this, user.city + " · " + user.method + " · " + user.phone, 10, false, Ui.MUTED));
            top.addView(nameBox);
            top.addView(Ui.pill(this, user.status, Ui.statusFg(user.status), Ui.statusBg(user.status)));
            card.addView(top);
            card.addView(Ui.spacer(this, 12));
            card.addView(Ui.text(this, user.projectName, 12, true, Ui.INK));
            LinearLayout budgetLine = Ui.row(this);
            budgetLine.setPadding(0, Ui.dp(this, 6), 0, 0);
            budgetLine.addView(Ui.text(this, "预算 " + ChartView.shortMoney(user.budget), 10, false, Ui.MUTED));
            TextView spent = Ui.text(this, "已支出 " + ChartView.shortMoney(user.spent), 10, true, Ui.BLUE);
            spent.setGravity(Gravity.END);
            spent.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            budgetLine.addView(spent);
            card.addView(budgetLine);
            ProgressBar bar = progress(user.budget == 0 ? 0 : user.spent / user.budget * 100);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
            lp.topMargin = Ui.dp(this, 7);
            bar.setLayoutParams(lp);
            card.addView(bar);
            LinearLayout footer = Ui.row(this);
            footer.setPadding(0, Ui.dp(this, 8), 0, 0);
            footer.addView(Ui.text(this, "预算执行 " + pct(user.budget == 0 ? 0 : user.spent / user.budget * 100), 9, false, Ui.MUTED));
            TextView issues = Ui.text(this, "待解决问题 " + user.openIssues, 9, true, user.openIssues > 0 ? Ui.ORANGE : Ui.MUTED);
            issues.setGravity(Gravity.END);
            issues.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            footer.addView(issues);
            card.addView(footer);
            addWithMargins(card, 0, 10, 0, 0);
            list.addView(card);
        }
    }

    private void buildData() {
        content.addView(section("支出审查", "点击卡片调整风险标记"));
        final LinearLayout list = Ui.column(this);
        LinearLayout chips = Ui.row(this);
        String[] risks = {"全部", "正常", "超支", "异常", "待核"};
        final TextView[] chipViews = new TextView[risks.length];
        for (int i = 0; i < risks.length; i++) {
            TextView chip = Ui.pill(this, risks[i], Ui.MUTED, Color.parseColor("#EEF2F9"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, Ui.dp(this, 7), 0);
            chip.setLayoutParams(lp);
            final String risk = risks[i];
            chip.setOnClickListener(v -> {
                for (int j = 0; j < chipViews.length; j++) {
                    boolean on = risks[j].equals(risk);
                    chipViews[j].setTextColor(on ? Color.WHITE : Ui.MUTED);
                    chipViews[j].setBackground(Ui.rounded(on ? Ui.BLUE : Color.parseColor("#EEF2F9"), Ui.dp(this, 999), Color.TRANSPARENT, 0));
                }
                renderExpenses(list, risk);
            });
            chipViews[i] = chip;
            chips.addView(chip);
        }
        content.addView(chips);
        content.addView(list);
        renderExpenses(list, "全部");

        content.addView(section("合同台账", "已付 / 应付 / 风险"));
        for (Db.Contract contract : db.contracts(null)) content.addView(contractCard(contract));
    }

    private void renderExpenses(LinearLayout list, String risk) {
        list.removeAllViews();
        List<Db.Expense> expenses = db.expenses(risk, null);
        if (expenses.isEmpty()) { list.addView(empty("没有匹配的支出")); return; }
        for (Db.Expense expense : expenses) {
            LinearLayout card = Ui.card(this);
            card.setOnClickListener(v -> showRiskDialog(expense));
            LinearLayout top = Ui.row(this);
            LinearLayout left = Ui.column(this);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            left.addView(Ui.text(this, expense.category, 13, true, Ui.INK));
            left.addView(Ui.text(this, expense.userName + " · " + expense.spentAt, 9, false, Ui.MUTED));
            top.addView(left);
            top.addView(Ui.text(this, ChartView.money(expense.amount), 16, true, Ui.INK));
            card.addView(top);
            LinearLayout footer = Ui.row(this);
            footer.setPadding(0, Ui.dp(this, 8), 0, 0);
            footer.addView(Ui.pill(this, expense.risk, Ui.statusFg(expense.risk), Ui.statusBg(expense.risk)));
            TextView note = Ui.text(this, expense.note, 9, false, Ui.MUTED);
            note.setPadding(Ui.dp(this, 8), 0, 0, 0);
            note.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            footer.addView(note);
            card.addView(footer);
            addWithMargins(card, 0, 8, 0, 0);
            list.addView(card);
        }
    }

    private void showRiskDialog(Db.Expense expense) {
        String[] values = {"正常", "超支", "异常", "待核"};
        new AlertDialog.Builder(this)
                .setTitle(expense.category + " · " + ChartView.money(expense.amount))
                .setItems(values, (dialog, which) -> {
                    db.updateRisk(expense.id, values[which]);
                    db.addAudit("A-01", "expense.risk", "标记 " + expense.category + " 为" + values[which]);
                    Toast.makeText(this, "已更新风险标记", Toast.LENGTH_SHORT).show();
                    selectTab(2);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private View contractCard(Db.Contract contract) {
        LinearLayout card = Ui.card(this);
        LinearLayout top = Ui.row(this);
        LinearLayout left = Ui.column(this);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        left.addView(Ui.text(this, contract.name, 13, true, Ui.INK));
        left.addView(Ui.text(this, contract.userName + " · " + contract.status, 9, false, Ui.MUTED));
        top.addView(left);
        top.addView(Ui.pill(this, contract.nodesDone + "/" + contract.nodesTotal, Ui.BLUE, Ui.BLUE_SOFT));
        card.addView(top);
        card.addView(Ui.spacer(this, 9));
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
        addWithMargins(card, 0, 8, 0, 0);
        return card;
    }

    private View cityRow(String label, int count) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
        row.addView(Ui.text(this, label, 12, true, Ui.INK));
        TextView value = Ui.text(this, count + " 人", 11, true, Ui.BLUE);
        value.setGravity(Gravity.END);
        value.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(value);
        return row;
    }

    private void buildOperations() {
        content.addView(section("运营统计", "选择时间窗口，观察增长与转化"));
        final LinearLayout body = Ui.column(this);
        int[] ranges = {7, 30, 90};
        LinearLayout chips = Ui.row(this);
        TextView[] chipViews = new TextView[3];
        for (int i = 0; i < 3; i++) {
            TextView chip = Ui.pill(this, ranges[i] + " 天", Ui.MUTED, Color.parseColor("#EEF2F9"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, Ui.dp(this, 8), 0);
            chip.setLayoutParams(lp);
            final int days = ranges[i];
            chip.setOnClickListener(v -> {
                for (int j = 0; j < chipViews.length; j++) {
                    boolean on = ranges[j] == days;
                    chipViews[j].setTextColor(on ? Color.WHITE : Ui.MUTED);
                    chipViews[j].setBackground(Ui.rounded(on ? Ui.BLUE : Color.parseColor("#EEF2F9"), Ui.dp(this, 999), Color.TRANSPARENT, 0));
                }
                renderOps(body, days);
            });
            chipViews[i] = chip;
            chips.addView(chip);
        }
        chipViews[1].setTextColor(Color.WHITE);
        chipViews[1].setBackground(Ui.rounded(Ui.BLUE, Ui.dp(this, 999), Color.TRANSPARENT, 0));
        content.addView(chips);
        content.addView(body);
        renderOps(body, 30);
    }

    private void renderOps(LinearLayout body, int days) {
        body.removeAllViews();
        int newUsers = db.newUsers(days);
        double spend = db.spendSince(days);
        int active = db.activeUsers(days);
        LinearLayout metrics = Ui.row(this);
        metrics.addView(kpi("新增用户", String.valueOf(newUsers), "近 " + days + " 天", 1f));
        metrics.addView(kpi("活跃用户", String.valueOf(active), "有登录记录", 1f));
        addWithMargins(metrics, 0, 10, 0, 0);
        LinearLayout spendCard = Ui.card(this);
        spendCard.addView(Ui.text(this, "窗口内支出", 11, true, Ui.MUTED));
        spendCard.addView(Ui.spacer(this, 5));
        spendCard.addView(Ui.text(this, money(spend), 24, true, Ui.INK));
        spendCard.addView(Ui.text(this, "仅用于运营观察，不替代财务对账", 9, false, Ui.MUTED));
        addWithMargins(spendCard, 0, 8, 0, 0);

        body.addView(section("转化漏斗", "注册 → 建项目 → 记账 → 进度 → 合同"));
        LinearLayout funnelCard = Ui.card(this);
        Map<String, Integer> funnel = db.funnel();
        int max = 1;
        for (Integer value : funnel.values()) max = Math.max(max, value);
        for (Map.Entry<String, Integer> entry : funnel.entrySet()) {
            LinearLayout line = Ui.column(this);
            line.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
            LinearLayout top = Ui.row(this);
            top.addView(Ui.text(this, entry.getKey(), 11, true, Ui.INK));
            TextView value = Ui.text(this, String.valueOf(entry.getValue()), 11, true, Ui.BLUE);
            value.setGravity(Gravity.END);
            value.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            top.addView(value);
            line.addView(top);
            ProgressBar bar = progress(entry.getValue() * 100.0 / max);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
            lp.topMargin = Ui.dp(this, 6);
            bar.setLayoutParams(lp);
            line.addView(bar);
            funnelCard.addView(line);
        }
        addWithMargins(funnelCard, 0, 0, 0, 0);

        body.addView(section("城市分布", "按用户所在城市"));
        LinearLayout cityCard = Ui.card(this);
        for (Db.Stat stat : db.cities()) cityCard.addView(cityRow(stat.label, (int) stat.value));
        addWithMargins(cityCard, 0, 0, 0, 0);

        body.addView(section("月度支出", "按月对比"));
        LinearLayout chartCard = Ui.card(this);
        ChartView chart = new ChartView(this);
        chart.setData(db.monthly(), false, true);
        chart.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 150)));
        chartCard.addView(chart);
        addWithMargins(chartCard, 0, 0, 0, 0);

        TextView notify = Ui.button(this, "发送运营通知", true);
        LinearLayout.LayoutParams notifyLp = Ui.matchWrap(this);
        notifyLp.topMargin = Ui.dp(this, 18);
        notify.setLayoutParams(notifyLp);
        notify.setOnClickListener(v -> showNoticeDialog());
        body.addView(notify);
    }

    private void showNoticeDialog() {
        LinearLayout form = Ui.column(this);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        EditText title = Ui.input(this, "通知标题");
        EditText body = Ui.input(this, "通知内容");
        form.addView(title);
        form.addView(Ui.spacer(this, 10));
        form.addView(body);
        new AlertDialog.Builder(this)
                .setTitle("发送运营通知")
                .setView(form)
                .setPositiveButton("发送", (dialog, which) -> {
                    String t = title.getText().toString().trim();
                    if (t.isEmpty()) { Toast.makeText(this, "请填写标题", Toast.LENGTH_SHORT).show(); return; }
                    db.addAudit("A-02", "notification.send", t + " · " + body.getText().toString().trim());
                    Toast.makeText(this, "通知已记录到审计", Toast.LENGTH_SHORT).show();
                    selectTab(3);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void buildManage() {
        content.addView(section("产品开关", "只影响本地演示行为"));
        LinearLayout settingsCard = Ui.card(this);
        settingsCard.addView(settingRow("首页广告位", "在首页底部出现，不遮挡记账", "ads"));
        settingsCard.addView(settingRow("导出激励视频", "导出前需要完成解锁", "export"));
        settingsCard.addView(settingRow("维护模式", "暂停用户端写入入口", "maintenance"));
        addWithMargins(settingsCard, 0, 0, 0, 0);

        content.addView(section("管理员", "角色分级 · 默认拒绝"));
        LinearLayout adminsCard = Ui.card(this);
        for (Db.Admin admin : db.admins()) {
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            LinearLayout left = Ui.column(this);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            left.addView(Ui.text(this, admin.name + "  " + admin.id, 12, true, Ui.INK));
            left.addView(Ui.text(this, admin.phone + " · " + admin.role, 9, false, Ui.MUTED));
            row.addView(left);
            row.addView(Ui.pill(this, admin.status, Ui.statusFg(admin.status), Ui.statusBg(admin.status)));
            adminsCard.addView(row);
        }
        TextView add = Ui.button(this, "+ 新增管理员", false);
        LinearLayout.LayoutParams addLp = Ui.matchWrap(this);
        addLp.topMargin = Ui.dp(this, 12);
        add.setLayoutParams(addLp);
        add.setOnClickListener(v -> showAddAdminDialog());
        adminsCard.addView(add);
        addWithMargins(adminsCard, 0, 0, 0, 0);

        content.addView(section("审计日志", "本地最近 6 条"));
        LinearLayout auditCard = Ui.card(this);
        for (Db.Audit audit : db.audits(6)) auditCard.addView(auditRow(audit));
        addWithMargins(auditCard, 0, 0, 0, 0);

        LinearLayout actions = Ui.row(this);
        TextView export = Ui.button(this, "导出审计 CSV", false);
        export.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        export.setOnClickListener(v -> shareAudits());
        TextView logout = Ui.ghostButton(this, "退出登录");
        LinearLayout.LayoutParams logoutLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        logoutLp.leftMargin = Ui.dp(this, 8);
        logout.setLayoutParams(logoutLp);
        logout.setOnClickListener(v -> {
            getSharedPreferences("session", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        actions.addView(export);
        actions.addView(logout);
        LinearLayout.LayoutParams actionsLp = Ui.matchWrap(this);
        actionsLp.topMargin = Ui.dp(this, 18);
        actions.setLayoutParams(actionsLp);
        content.addView(actions);
    }

    private View settingRow(String title, String desc, String key) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
        LinearLayout text = Ui.column(this);
        text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        text.addView(Ui.text(this, title, 12, true, Ui.INK));
        text.addView(Ui.text(this, desc, 9, false, Ui.MUTED));
        row.addView(text);
        Switch toggle = new Switch(this);
        toggle.setChecked("true".equals(db.setting(key)));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            db.setSetting(key, isChecked ? "true" : "false");
            db.addAudit("A-01", "setting." + key, title + (isChecked ? " · 开启" : " · 关闭"));
        });
        row.addView(toggle);
        return row;
    }

    private void showAddAdminDialog() {
        LinearLayout form = Ui.column(this);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        EditText name = Ui.input(this, "姓名");
        EditText phone = Ui.input(this, "手机号");
        form.addView(name);
        form.addView(Ui.spacer(this, 10));
        form.addView(phone);
        new AlertDialog.Builder(this)
                .setTitle("新增管理员")
                .setView(form)
                .setPositiveButton("创建", (dialog, which) -> {
                    String n = name.getText().toString().trim();
                    String p = phone.getText().toString().trim();
                    if (n.isEmpty() || p.isEmpty()) { Toast.makeText(this, "姓名和手机号必填", Toast.LENGTH_SHORT).show(); return; }
                    String id = "A-" + String.format(Locale.CHINA, "%02d", db.count("admins", null) + 1);
                    db.addAdmin(id, n, p, "运营管理员");
                    db.addAudit("A-01", "admin.create", "新增 " + n + " · 运营管理员");
                    Toast.makeText(this, "已创建 " + id, Toast.LENGTH_SHORT).show();
                    selectTab(4);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void shareAudits() {
        String csv = db.exportAudits();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_SUBJECT, "装修记 · 审计日志");
        intent.putExtra(Intent.EXTRA_TEXT, csv);
        startActivity(Intent.createChooser(intent, "导出审计 CSV"));
        db.addAudit("A-01", "export.audit", "导出审计日志 CSV");
    }

    private View issueCard(Db.Issue issue) {
        LinearLayout card = Ui.card(this);
        LinearLayout top = Ui.row(this);
        top.addView(Ui.pill(this, issue.level, levelFg(issue.level), levelBg(issue.level)));
        TextView title = Ui.text(this, issue.title, 12, true, Ui.INK);
        title.setPadding(Ui.dp(this, 8), 0, 0, 0);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(title);
        top.addView(Ui.text(this, issue.status, 9, true, issue.status.equals("待解决") ? Ui.ORANGE : Ui.GREEN));
        card.addView(top);
        card.addView(Ui.text(this, issue.userName + " · " + issue.recordedAt, 9, false, Ui.MUTED));
        addWithMargins(card, 0, 8, 0, 0);
        return card;
    }

    private View auditRow(Db.Audit audit) {
        LinearLayout row = Ui.column(this);
        row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        LinearLayout top = Ui.row(this);
        top.addView(Ui.text(this, audit.action, 10, true, Ui.BLUE));
        TextView time = Ui.text(this, audit.createdAt, 9, false, Ui.MUTED);
        time.setGravity(Gravity.END);
        time.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(time);
        row.addView(top);
        row.addView(Ui.text(this, audit.detail, 10, false, Ui.INK));
        return row;
    }

    private View section(String title, String subtitle) {
        LinearLayout box = Ui.row(this);
        box.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 9));
        LinearLayout left = Ui.column(this);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        left.addView(Ui.text(this, title, 15, true, Ui.INK));
        left.addView(Ui.text(this, subtitle, 9, false, Ui.MUTED));
        box.addView(left);
        return box;
    }

    private View empty(String text) {
        LinearLayout box = Ui.card(this);
        box.addView(Ui.text(this, text, 12, false, Ui.MUTED));
        return box;
    }

    private ProgressBar progress(double pct) {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(Math.max(0, Math.min(100, (int) Math.round(pct))));
        bar.setProgressTintList(ColorStateList.valueOf(pct > 100 ? Ui.RED : Ui.BLUE));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EAF0FA")));
        return bar;
    }

    private void addWithMargins(View view, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = Ui.matchWrap(this);
        lp.setMargins(Ui.dp(this, left), Ui.dp(this, top), Ui.dp(this, right), Ui.dp(this, bottom));
        view.setLayoutParams(lp);
        content.addView(view);
    }

    private String money(double value) { return ChartView.money(value); }

    private String pct(double value) { return String.format(Locale.CHINA, "%.1f%%", value); }

    private int levelFg(String level) {
        return "高".equals(level) ? Ui.RED : ("中".equals(level) ? Ui.ORANGE : Ui.MUTED);
    }

    private int levelBg(String level) {
        return "高".equals(level) ? Ui.RED_SOFT : ("中".equals(level) ? Ui.ORANGE_SOFT : Color.parseColor("#EEF2F9"));
    }
}
