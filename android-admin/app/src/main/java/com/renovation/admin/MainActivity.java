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
import android.util.Log;
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

import org.json.JSONArray;
import org.json.JSONObject;

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
        try {
            setup();
        } catch (Throwable e) {
            showCrash(e);
        }
    }

    private void setup() {
        Session.load(this);
        db = Session.demo ? Db.get(this) : Db.getRemote(this);
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
        left.addView(Ui.text(this, Session.demo ? "装修记 / 数据工作台 · 本地演示" : "装修记 / 数据工作台 · 服务端数据", 10, true, Ui.BLUE));
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        left.setLayoutParams(leftLp);
        header.addView(left);

        LinearLayout right = Ui.column(this);
        right.setGravity(Gravity.END);
        String initial = Session.name == null || Session.name.isEmpty() ? "管" : Session.name.substring(0, 1);
        TextView avatar = Ui.text(this, initial, 13, true, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.rounded(Ui.BLUE_DARK, Ui.dp(this, 12), Color.TRANSPARENT, 0));
        avatar.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        right.addView(avatar);
        right.addView(Ui.text(this, Session.name + " · " + Session.role, 9, false, Ui.MUTED));
        header.addView(right);
        return header;
    }

    private View buildTabs() {
        LinearLayout nav = Ui.row(this);
        nav.setPadding(Ui.dp(this, 12), Ui.dp(this, 2), Ui.dp(this, 12), Ui.dp(this, 10));
        String[] names = {"概览", "用户", "合同", "统计", "系统"};
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
            case 2: buildContracts(); break;
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
        int adClicks = adInt("clicks");
        double pct = budget == 0 ? 0 : spent / budget * 100;

        LinearLayout hero = Ui.card(this);
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.BLUE_DARK, Color.parseColor("#143D91")});
        heroBg.setCornerRadius(Ui.dp(this, 22));
        hero.setBackground(heroBg);
        LinearLayout heroTop = Ui.row(this);
        heroTop.addView(Ui.text(this, "PLATFORM SPEND", 10, true, Color.parseColor("#9FB8FF")));
        TextView live = Ui.pill(this, Session.demo ? "LIVE · 本地" : "LIVE · 服务端", Color.parseColor("#B8C8FF"), Color.parseColor("#213E82"));
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
        grid2.addView(kpi("广告点击", String.valueOf(adClicks), "近 30 天 · 全部广告位", 1f));
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

        content.addView(section("最近广告点击", "谁在什么时候点了哪个广告位"));
        LinearLayout clickCard = Ui.card(this);
        JSONArray recentClicks = adSnapshot().optJSONArray("clicks");
        int clickShown = 0;
        if (recentClicks != null) {
            for (int i = 0; i < recentClicks.length() && clickShown < 4; i++) {
                JSONObject item = recentClicks.optJSONObject(i);
                if (item == null) continue;
                clickShown++;
                LinearLayout row = Ui.column(this);
                row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
                LinearLayout top = Ui.row(this);
                top.addView(Ui.text(this, item.optString("user_name", item.optString("user_id")), 11, true, Ui.INK));
                TextView when = Ui.text(this, item.optString("occurred_at"), 9, false, Ui.MUTED);
                when.setGravity(Gravity.END);
                when.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                top.addView(when);
                row.addView(top);
                row.addView(Ui.text(this, item.optString("slot_name", item.optString("slot_key")) + " · 第 " + item.optInt("user_click_count") + " 次点击", 9, true, Ui.BLUE));
                clickCard.addView(row);
            }
        }
        if (clickShown == 0) clickCard.addView(Ui.text(this, "还没有广告点击记录", 11, false, Ui.MUTED));
        addWithMargins(clickCard, 0, 0, 0, 0);
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
            LinearLayout.LayoutParams cardLp = Ui.matchWrap(this);
            cardLp.setMargins(0, Ui.dp(this, 10), 0, 0);
            card.setLayoutParams(cardLp);
            list.addView(card);
        }
    }

    private void buildContracts() {
        content.addView(section("合同台账", "总额、已付、待付与合同风险，只读查看，不修改用户合同"));
        double total = 0, paid = 0;
        List<Db.Contract> contracts = db.contracts(null);
        for (Db.Contract contract : contracts) {
            total += contract.total;
            paid += contract.paid;
        }
        LinearLayout summary = Ui.row(this);
        summary.addView(kpi("合同总额", ChartView.shortMoney(total), contracts.size() + " 份合同", 1f));
        summary.addView(kpi("已付金额", ChartView.shortMoney(paid), "待付 " + ChartView.shortMoney(Math.max(total - paid, 0)), 1f));
        addWithMargins(summary, 0, 10, 0, 0);

        LinearLayout chips = Ui.row(this);
        String[] statuses = {"全部", "执行中", "已完工", "待付", "风险"};
        final LinearLayout list = Ui.column(this);
        final TextView[] chipViews = new TextView[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            TextView chip = Ui.pill(this, statuses[i], Ui.MUTED, Color.parseColor("#EEF2F9"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, Ui.dp(this, 1), Ui.dp(this, 7), 0);
            chip.setLayoutParams(lp);
            final String status = statuses[i];
            chip.setOnClickListener(v -> {
                for (int j = 0; j < chipViews.length; j++) {
                    boolean on = statuses[j].equals(status);
                    chipViews[j].setTextColor(on ? Color.WHITE : Ui.MUTED);
                    chipViews[j].setBackground(Ui.rounded(on ? Ui.BLUE : Color.parseColor("#EEF2F9"), Ui.dp(this, 999), Color.TRANSPARENT, 0));
                }
                renderContracts(list, status);
            });
            chipViews[i] = chip;
            chips.addView(chip);
        }
        content.addView(chips);
        content.addView(list);
        renderContracts(list, "全部");
        if (contracts.isEmpty()) content.addView(empty("服务端还没有合同数据"));
    }

    private void renderContracts(LinearLayout list, String status) {
        list.removeAllViews();
        List<Db.Contract> contracts = db.contracts(null);
        boolean any = false;
        for (Db.Contract contract : contracts) {
            if (!"全部".equals(status) && !status.equals(contract.status)) continue;
            any = true;
            list.addView(contractCard(contract));
        }
        if (!any) list.addView(empty("没有匹配的合同"));
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
        LinearLayout.LayoutParams cardLp = Ui.matchWrap(this);
        cardLp.setMargins(0, Ui.dp(this, 8), 0, 0);
        card.setLayoutParams(cardLp);
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

        body.addView(section("预算排行", "按用户当前预算，不修改用户预算"));
        LinearLayout rankCard = Ui.card(this);
        List<Db.User> budgetUsers = db.users(null, null);
        budgetUsers.sort((a, b) -> Double.compare(b.budget, a.budget));
        for (int i = 0; i < Math.min(5, budgetUsers.size()); i++) {
            Db.User user = budgetUsers.get(i);
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            TextView index = Ui.text(this, String.valueOf(i + 1), 11, true, Ui.BLUE);
            index.setGravity(Gravity.CENTER);
            index.setBackground(Ui.rounded(Ui.BLUE_SOFT, Ui.dp(this, 8), Color.TRANSPARENT, 0));
            index.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
            row.addView(index);
            LinearLayout left = Ui.column(this);
            left.setPadding(Ui.dp(this, 10), 0, 0, 0);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            left.addView(Ui.text(this, user.name + " · " + user.city, 11, true, Ui.INK));
            left.addView(Ui.text(this, user.projectName, 9, false, Ui.MUTED));
            row.addView(left);
            row.addView(Ui.text(this, ChartView.shortMoney(user.budget), 13, true, Ui.BLUE));
            rankCard.addView(row);
        }
        if (budgetUsers.isEmpty()) rankCard.addView(Ui.text(this, "还没有用户预算数据", 11, false, Ui.MUTED));
        addWithMargins(rankCard, 0, 0, 0, 0);

        body.addView(section("广告效果", "六个广告位拆分曝光、点击与点击排行"));
        JSONObject adRoot = adSnapshot();
        JSONObject adSummary = adRoot.optJSONObject("summary");
        LinearLayout adCard = Ui.card(this);
        LinearLayout adMetrics = Ui.row(this);
        adMetrics.addView(kpi("曝光", String.valueOf(adSummary == null ? 0 : adSummary.optInt("impressions", 0)), "近 30 天", 1f));
        adMetrics.addView(kpi("点击", String.valueOf(adSummary == null ? 0 : adSummary.optInt("clicks", 0)), "点击用户 " + (adSummary == null ? 0 : adSummary.optInt("clickUsers", 0)), 1f));
        adCard.addView(adMetrics);
        adCard.addView(Ui.spacer(this, 10));
        JSONArray slots = adRoot.optJSONArray("slots");
        if (slots != null) {
            for (int i = 0; i < slots.length(); i++) {
                JSONObject slot = slots.optJSONObject(i);
                if (slot == null) continue;
                LinearLayout row = Ui.row(this);
                row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
                LinearLayout left = Ui.column(this);
                left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                left.addView(Ui.text(this, slot.optString("name", slot.optString("key")), 11, true, Ui.INK));
                left.addView(Ui.text(this, "曝光 " + slot.optInt("impressions") + " · 点击 " + slot.optInt("clicks") + " · 点击率 " + slot.optDouble("ctr") + "%", 9, false, Ui.MUTED));
                row.addView(left);
                row.addView(Ui.pill(this, slot.optBoolean("enabled", true) ? "启用" : "关闭", slot.optBoolean("enabled", true) ? Ui.BLUE : Ui.MUTED, slot.optBoolean("enabled", true) ? Ui.BLUE_SOFT : Color.parseColor("#EEF2F9")));
                adCard.addView(row);
            }
        }
        TextView detail = Ui.button(this, "查看点击明细与用户排行", false);
        LinearLayout.LayoutParams detailLp = Ui.matchWrap(this);
        detailLp.topMargin = Ui.dp(this, 10);
        detail.setLayoutParams(detailLp);
        detail.setOnClickListener(v -> showAdDetail(""));
        adCard.addView(detail);
        addWithMargins(adCard, 0, 0, 0, 0);
    }

    private JSONObject adSnapshot() {
        return adSnapshot("");
    }

    private JSONObject adSnapshot(String slot) {
        try {
            String raw;
            if (Session.demo) {
                raw = db.adSnapshotJson(30, slot == null ? "" : slot);
            } else if (slot != null && !slot.isEmpty()) {
                raw = db.setting("ads_slot_" + slot);
                if (raw == null || raw.isEmpty()) raw = db.setting("ads_snapshot");
            } else {
                raw = db.setting("ads_snapshot");
            }
            if (raw == null || raw.isEmpty()) return new JSONObject();
            JSONObject root = new JSONObject(raw);
            if (slot != null && !slot.isEmpty() && root.optJSONArray("slots") != null
                    && root.optJSONArray("slots").length() > 1) {
                root = filterAdSnapshot(root, slot);
            }
            return root;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private JSONObject filterAdSnapshot(JSONObject root, String slot) throws Exception {
        JSONObject copy = new JSONObject(root.toString());
        JSONArray slots = root.optJSONArray("slots");
        JSONArray filtered = new JSONArray();
        JSONObject summary = new JSONObject();
        int impressions = 0, clicks = 0, clickUsers = 0, rewards = 0;
        if (slots != null) {
            for (int i = 0; i < slots.length(); i++) {
                JSONObject item = slots.optJSONObject(i);
                if (item == null) continue;
                if (slot.equals(item.optString("key"))) {
                    filtered.put(item);
                    impressions += item.optInt("impressions");
                    clicks += item.optInt("clicks");
                    clickUsers += item.optInt("clickUsers");
                    rewards += item.optInt("rewards");
                }
            }
        }
        summary.put("impressions", impressions);
        summary.put("clicks", clicks);
        summary.put("clickUsers", clickUsers);
        summary.put("rewards", rewards);
        summary.put("ctr", impressions > 0 ? Math.round(clicks * 10000.0 / impressions) / 100.0 : 0);
        summary.put("avgClicks", clickUsers > 0 ? Math.round(clicks * 10.0 / clickUsers) / 10.0 : 0);
        summary.put("estimatedRevenue", 0);
        JSONArray clicksArray = root.optJSONArray("clicks");
        JSONArray filteredClicks = new JSONArray();
        if (clicksArray != null) {
            for (int i = 0; i < clicksArray.length(); i++) {
                JSONObject item = clicksArray.optJSONObject(i);
                if (item != null && slot.equals(item.optString("slot_key"))) filteredClicks.put(item);
            }
        }
        copy.put("summary", summary);
        copy.put("slots", filtered);
        copy.put("clicks", filteredClicks);
        copy.put("slot", slot);
        return copy;
    }

    private int adInt(String key) {
        JSONObject root = adSnapshot();
        JSONObject summary = root.optJSONObject("summary");
        return summary == null ? 0 : summary.optInt(key, 0);
    }

    private void showAdDetail(final String slot) {
        JSONObject root = adSnapshot(slot);
        JSONObject summary = root.optJSONObject("summary");
        ScrollView scrollView = new ScrollView(this);
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 8));

        TextView title = Ui.text(this, "广告效果详情", 18, true, Ui.INK);
        box.addView(title);
        box.addView(Ui.text(this, Session.demo ? "本地演示数据 · 近 30 天" : "服务端 MySQL 数据 · 近 30 天", 10, false, Ui.MUTED));
        box.addView(Ui.spacer(this, 12));

        LinearLayout metrics = Ui.row(this);
        metrics.addView(kpi("曝光", String.valueOf(summary == null ? 0 : summary.optInt("impressions")), "次", 1f));
        metrics.addView(kpi("点击", String.valueOf(summary == null ? 0 : summary.optInt("clicks")), "点击用户 " + (summary == null ? 0 : summary.optInt("clickUsers")), 1f));
        metrics.addView(kpi("激励完成", String.valueOf(summary == null ? 0 : summary.optInt("rewards")), "次", 1f));
        box.addView(metrics);
        box.addView(Ui.spacer(this, 8));
        if (summary != null) {
            box.addView(Ui.text(this, "点击率 " + summary.optDouble("ctr") + "% · 人均 " + summary.optDouble("avgClicks") + " 次 · 预估收入 ¥" + summary.optDouble("estimatedRevenue", 0), 10, true, Ui.BLUE));
        }

        box.addView(Ui.spacer(this, 16));
        box.addView(Ui.text(this, "按广告位", 13, true, Ui.INK));
        JSONArray slots = root.optJSONArray("slots");
        if (slots != null) {
            for (int i = 0; i < slots.length(); i++) {
                JSONObject item = slots.optJSONObject(i);
                if (item == null) continue;
                LinearLayout row = Ui.row(this);
                row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
                LinearLayout left = Ui.column(this);
                left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                left.addView(Ui.text(this, item.optString("name", item.optString("key")), 11, true, Ui.INK));
                left.addView(Ui.text(this, item.optString("key") + " · 曝光 " + item.optInt("impressions") + " · 点击 " + item.optInt("clicks") + " · 用户 " + item.optInt("clickUsers"), 9, false, Ui.MUTED));
                row.addView(left);
                TextView action = Ui.text(this, slot.isEmpty() ? "查看 ›" : "筛选中", 10, true, Ui.BLUE);
                action.setOnClickListener(v -> {
                    if (slot.isEmpty()) {
                        showAdDetail(item.optString("key"));
                    }
                });
                row.addView(action);
                box.addView(row);
            }
        }
        if (!slot.isEmpty()) {
            TextView clear = Ui.ghostButton(this, "查看全部广告位");
            LinearLayout.LayoutParams lp = Ui.matchWrap(this);
            lp.topMargin = Ui.dp(this, 6);
            clear.setLayoutParams(lp);
            clear.setOnClickListener(v -> showAdDetail(""));
            box.addView(clear);
        }

        box.addView(Ui.spacer(this, 16));
        box.addView(Ui.text(this, "用户点击排行", 13, true, Ui.INK));
        JSONArray ranking = root.optJSONArray("ranking");
        if (ranking != null && ranking.length() > 0) {
            for (int i = 0; i < Math.min(5, ranking.length()); i++) {
                JSONObject item = ranking.optJSONObject(i);
                if (item == null) continue;
                LinearLayout row = Ui.row(this);
                row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
                row.addView(Ui.text(this, String.valueOf(i + 1), 11, true, Ui.BLUE));
                LinearLayout left = Ui.column(this);
                left.setPadding(Ui.dp(this, 10), 0, 0, 0);
                left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                left.addView(Ui.text(this, item.optString("name", item.optString("user_id")), 11, true, Ui.INK));
                left.addView(Ui.text(this, item.optString("city", "") + " · 最近 " + item.optString("last_click", ""), 9, false, Ui.MUTED));
                row.addView(left);
                row.addView(Ui.text(this, item.optInt("clicks") + " 次", 12, true, Ui.ORANGE));
                box.addView(row);
            }
        } else {
            box.addView(Ui.text(this, "这个时间窗口还没有点击用户", 10, false, Ui.MUTED));
        }

        box.addView(Ui.spacer(this, 16));
        box.addView(Ui.text(this, "点击明细", 13, true, Ui.INK));
        JSONArray clicks = root.optJSONArray("clicks");
        if (clicks != null && clicks.length() > 0) {
            for (int i = 0; i < Math.min(8, clicks.length()); i++) {
                JSONObject item = clicks.optJSONObject(i);
                if (item == null) continue;
                LinearLayout row = Ui.column(this);
                row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
                LinearLayout top = Ui.row(this);
                top.addView(Ui.text(this, item.optString("user_name", item.optString("user_id")), 11, true, Ui.INK));
                TextView count = Ui.text(this, "第 " + item.optInt("user_click_count") + " 次", 9, true, Ui.BLUE);
                count.setGravity(Gravity.END);
                count.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                top.addView(count);
                row.addView(top);
                row.addView(Ui.text(this, item.optString("occurred_at") + " · " + item.optString("slot_name", item.optString("slot_key")), 9, false, Ui.MUTED));
                box.addView(row);
            }
        } else {
            box.addView(Ui.text(this, "这个广告位还没有点击记录", 10, false, Ui.MUTED));
        }
        scrollView.addView(box);
        new AlertDialog.Builder(this)
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void buildManage() {
        content.addView(section("数据源", "本地演示账号与服务端账号严格分流"));
        LinearLayout sourceCard = Ui.card(this);
        sourceCard.addView(infoRow("当前模式", Session.demo ? "本地演示数据" : "服务端 MySQL 镜像"));
        sourceCard.addView(infoRow("管理员", Session.name + " · " + Session.role));
        if (!Session.demo) sourceCard.addView(infoRow("API 地址", Session.baseUrl));
        sourceCard.addView(infoRow("用户 / 合同", db.users(null, null).size() + " 位 / " + db.contracts(null).size() + " 份"));
        addWithMargins(sourceCard, 0, 0, 0, 0);

        content.addView(section("广告位管理", "六个广告位与小程序页面一一对应"));
        LinearLayout adCard = Ui.card(this);
        JSONObject adRoot = adSnapshot();
        JSONArray adSlots = adRoot.optJSONArray("slots");
        for (int i = 0; i < Db.AD_SLOT_KEYS.length; i++) {
            String key = Db.AD_SLOT_KEYS[i];
            String name = Db.AD_SLOT_NAMES[i];
            boolean enabled = true;
            if (adSlots != null) {
                for (int j = 0; j < adSlots.length(); j++) {
                    JSONObject item = adSlots.optJSONObject(j);
                    if (item != null && key.equals(item.optString("key"))) {
                        enabled = item.optBoolean("enabled", true);
                        break;
                    }
                }
            }
            adCard.addView(adSlotRow(key, name, enabled));
        }
        adCard.addView(Ui.text(this, Session.demo ? "演示账号开关只影响本地演示；服务端账号保存后会调用 PUT /admin/settings 同步数据库。" : "开关会调用 PUT /admin/settings 写入服务端，下一次同步生效。", 9, false, Ui.MUTED));
        addWithMargins(adCard, 0, 0, 0, 0);

        content.addView(section("管理员账号", "角色与权限由服务端裁决"));
        LinearLayout adminsCard = Ui.card(this);
        List<Db.Admin> admins = db.admins();
        if (admins.isEmpty()) {
            adminsCard.addView(infoRow(Session.name, Session.role + " · 当前登录"));
        } else {
            for (Db.Admin admin : admins) adminsCard.addView(infoRow(admin.name + "  " + admin.id, admin.phone + " · " + admin.role));
        }
        addWithMargins(adminsCard, 0, 0, 0, 0);

        content.addView(section("服务与合规", "只读统计，不审核用户预算、不改用户账目"));
        LinearLayout compliance = Ui.card(this);
        compliance.addView(infoRow("数据库", "MySQL 8.0 · 仅 Flask API 访问"));
        compliance.addView(infoRow("用户数据", "小程序全部写入服务端；管理端不提供预算修改"));
        compliance.addView(infoRow("广告统计", "曝光 / 点击 / 点击用户 / 激励完成"));
        compliance.addView(infoRow("小程序广告位", "首页、记账、进度、统计、导出激励、导出底部"));
        addWithMargins(compliance, 0, 0, 0, 0);

        TextView logout = Ui.ghostButton(this, "退出登录");
        LinearLayout.LayoutParams logoutLp = Ui.matchWrap(this);
        logoutLp.topMargin = Ui.dp(this, 20);
        logout.setLayoutParams(logoutLp);
        logout.setOnClickListener(v -> {
            Session.clear(this);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        content.addView(logout);
    }

    private View infoRow(String label, String value) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        row.addView(Ui.text(this, label, 10, false, Ui.MUTED));
        TextView content = Ui.text(this, value, 11, true, Ui.INK);
        content.setGravity(Gravity.END);
        content.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(content);
        return row;
    }

    private View adSlotRow(String key, String name, boolean enabled) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        LinearLayout text = Ui.column(this);
        text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        text.addView(Ui.text(this, name, 11, true, Ui.INK));
        text.addView(Ui.text(this, key, 9, false, Ui.MUTED));
        row.addView(text);
        Switch toggle = new Switch(this);
        toggle.setChecked(enabled);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (Session.demo) {
                db.setSetting("ad_slot_" + key, isChecked ? "true" : "false");
                Toast.makeText(this, name + (isChecked ? " 已启用" : " 已关闭"), Toast.LENGTH_SHORT).show();
            } else {
                JSONObject payload = new JSONObject();
                try {
                    JSONObject ads = new JSONObject();
                    JSONObject slots = new JSONObject();
                    JSONObject slot = new JSONObject();
                    slot.put("enabled", isChecked);
                    slots.put(key, slot);
                    ads.put("slots", slots);
                    payload.put("ads", ads);
                } catch (Exception ignored) {}
                new Thread(() -> {
                    try {
                        RemoteSync.updateSettings(Session.baseUrl, Session.token, payload);
                        runOnUiThread(() -> Toast.makeText(this, "服务端广告位已更新", Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            toggle.setChecked(!isChecked);
                            Toast.makeText(this, "更新失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                }).start();
            }
        });
        row.addView(toggle);
        return row;
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
        LinearLayout.LayoutParams cardLp = Ui.matchWrap(this);
        cardLp.setMargins(0, Ui.dp(this, 8), 0, 0);
        card.setLayoutParams(cardLp);
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
        // 防御性处理：同一个 View 被复用时先脱离旧父容器，避免 "child already has a parent" 闪退。
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
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
    private void showCrash(Throwable e) {
        ScrollView crashScroll = new ScrollView(this);
        TextView crashText = new TextView(this);
        crashText.setText("管理台启动失败\n\n" + Log.getStackTraceString(e));
        crashText.setTextSize(12);
        crashText.setTextIsSelectable(true);
        crashText.setPadding(Ui.dp(this, 18), Ui.dp(this, 24), Ui.dp(this, 18), Ui.dp(this, 24));
        crashScroll.addView(crashText);
        setContentView(crashScroll);
    }

}
