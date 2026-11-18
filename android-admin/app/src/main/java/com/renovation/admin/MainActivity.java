package com.renovation.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 管理端主界面：严格对齐已确认原型的五栏数据工作台。 */
public class MainActivity extends Activity {
    private Db db;
    private ScrollView scroll;
    private LinearLayout content;
    private final List<TabItem> tabs = new ArrayList<>();
    private int activeTab;

    private static final String[] TAB_NAMES = {"首页", "用户", "合同", "统计", "系统"};
    private static final String[] TAB_ICONS = {"home", "users", "contract", "stats", "shield"};

    private static final class TabItem {
        GlyphView icon;
        TextView label;
        View indicator;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setup();
        } catch (Throwable e) {
            crashFallback(e);
        }
    }

    private void setup() {
        Session.load(this);
        db = Session.demo ? Db.get(this) : Db.getRemote(this);
        getWindow().setStatusBarColor(Ui.PAGE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.PAGE);
        root.addView(buildTopbar());

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        content = Ui.column(this);
        content.setPadding(dp(20), dp(14), dp(20), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);
        root.addView(buildTabbar());
        setContentView(root);
        selectTab(0);
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    private View buildTopbar() {
        LinearLayout wrap = Ui.column(this);
        LinearLayout bar = Ui.row(this);
        bar.setPadding(dp(20), 0, dp(14), 0);
        bar.setBackgroundColor(Ui.PAGE);

        FrameLayout mark = new FrameLayout(this);
        mark.setBackground(Ui.rounded(Ui.NAVY, dp(9), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        mark.setLayoutParams(markLp);
        View accent = new View(this);
        accent.setBackgroundColor(Ui.BLUE);
        FrameLayout.LayoutParams accentLp = new FrameLayout.LayoutParams(dp(1), dp(20), Gravity.CENTER);
        accentLp.leftMargin = dp(-13);
        accent.setLayoutParams(accentLp);
        mark.addView(accent);
        TextView letter = Ui.mono(this, "R", 13, true, Color.WHITE);
        letter.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams letterLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        letterLp.leftMargin = dp(5);
        letter.setLayoutParams(letterLp);
        mark.addView(letter);
        bar.addView(mark);

        LinearLayout brand = Ui.column(this);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        brandLp.leftMargin = dp(10);
        brand.setLayoutParams(brandLp);
        TextView kicker = Ui.mono(this, "RENOVATION / ADMIN", 8.5f, false, Ui.MUTED);
        kicker.setLetterSpacing(0.12f);
        brand.addView(kicker);
        brand.addView(Ui.text(this, "装修记 · 管理端", 16, true, Ui.INK));
        bar.addView(brand);

        FrameLayout sync = new FrameLayout(this);
        sync.setBackground(Ui.rounded(Color.WHITE, dp(10), Ui.LINE, dp(1)));
        sync.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        GlyphView syncIcon = new GlyphView(this, "sync", Ui.INK2);
        FrameLayout.LayoutParams syncLp = new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER);
        syncIcon.setLayoutParams(syncLp);
        sync.addView(syncIcon);
        View dot = new View(this);
        dot.setBackground(Ui.rounded(Ui.BLUE, dp(3), Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dp(5), dp(5), Gravity.TOP | Gravity.RIGHT);
        dotLp.topMargin = dp(7);
        dotLp.rightMargin = dp(7);
        dot.setLayoutParams(dotLp);
        sync.addView(dot);
        sync.setOnClickListener(v -> toast("已刷新 · " + timeNow()));
        bar.addView(sync);

        String initial = Session.name == null || Session.name.isEmpty() ? "管" : Session.name.substring(0, 1);
        TextView avatar = Ui.mono(this, initial, 13, true, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.rounded(Ui.BLUE, dp(18), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        avatarLp.leftMargin = dp(8);
        avatar.setLayoutParams(avatarLp);
        avatar.setOnClickListener(v -> toast("当前账号：" + Session.name + " · " + Session.role));
        bar.addView(avatar);

        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        bar.setLayoutParams(barLp);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(bar);
        View hair = new View(this);
        hair.setBackgroundColor(Ui.LINE_SOFT);
        wrap.addView(hair, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return wrap;
    }

    private View buildTabbar() {
        LinearLayout bar = Ui.row(this);
        bar.setBackgroundColor(Color.WHITE);
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
        for (int i = 0; i < TAB_NAMES.length; i++) {
            LinearLayout item = Ui.column(this);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            View indicator = new View(this);
            indicator.setBackgroundColor(Ui.BLUE);
            indicator.setLayoutParams(new LinearLayout.LayoutParams(dp(26), dp(2)));
            indicator.setVisibility(View.INVISIBLE);
            item.addView(indicator);
            GlyphView icon = new GlyphView(this, TAB_ICONS[i], Ui.MUTED);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
            iconLp.topMargin = dp(9);
            icon.setLayoutParams(iconLp);
            item.addView(icon);
            TextView label = Ui.text(this, TAB_NAMES[i], 10, false, Ui.MUTED);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(4);
            label.setLayoutParams(labelLp);
            item.addView(label);
            final int index = i;
            item.setOnClickListener(v -> selectTab(index));
            TabItem tab = new TabItem();
            tab.icon = icon;
            tab.label = label;
            tab.indicator = indicator;
            tabs.add(tab);
            bar.addView(item);
        }
        LinearLayout wrap = Ui.column(this);
        View hair = new View(this);
        hair.setBackgroundColor(Ui.LINE);
        wrap.addView(hair, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        wrap.addView(bar);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private void selectTab(int index) {
        activeTab = index;
        for (int i = 0; i < tabs.size(); i++) {
            boolean on = i == index;
            tabs.get(i).indicator.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            tabs.get(i).icon.setColor(on ? Ui.BLUE : Ui.MUTED);
            tabs.get(i).label.setTextColor(on ? Ui.BLUE_DARK : Ui.MUTED);
            tabs.get(i).label.setTypeface(android.graphics.Typeface.create(Ui.SANS,
                    on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        }
        content.removeAllViews();
        switch (index) {
            case 0: buildOverview(); break;
            case 1: buildUsers(); break;
            case 2: buildContracts(); break;
            case 3: buildStats(); break;
            default: buildSystem(); break;
        }
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    // ===== 概览 =====

    private void buildOverview() {
        List<Db.User> users = db.users(null, null);
        double budget = 0, spent = 0, contractTotal = 0, contractPending = 0;
        int stalled = 0;
        for (Db.User u : users) {
            budget += u.budget;
            spent += u.spent;
            if (db.stalled(u.id)) stalled++;
        }
        List<Db.Contract> contracts = db.contracts(null);
        for (Db.Contract c : contracts) {
            contractTotal += c.total;
            contractPending += c.getPending();
        }
        int new30 = db.newUsers(30);
        int active30 = db.activeUsers(30);
        int openIssues = db.issues(true).size();

        content.addView(buildHero(users.size(), new30, active30, budget, contractTotal, users.size(), stalled));

        LinearLayout quick = Ui.row(this);
        addQuick(quick, "users", "用户管理", () -> selectTab(1));
        addQuick(quick, "contract", "合同台账", () -> selectTab(2));
        addQuick(quick, "download", "数据导出", () -> showExportDialog(false));
        addQuick(quick, "shield", "系统状态", () -> selectTab(4));
        View lastQuick = quick.getChildAt(quick.getChildCount() - 1);
        ((LinearLayout.LayoutParams) lastQuick.getLayoutParams()).rightMargin = dp(0);
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        quickLp.bottomMargin = dp(18);
        quick.setLayoutParams(quickLp);
        content.addView(quick);

        addSectionHead("01", "最近进度", "全部记录", () -> toast("已打开全部进度记录"));
        content.addView(buildSiteStrip());

        addSectionHead("02", "用户增长", "近 30 天", null);
        content.addView(buildGrowthCard(30));

        addSectionHead("03", "城市排行", "TOP 5", null);
        content.addView(buildCityBlock(users.size(), 5));

        addSectionHead("04", "预算 Top 5", "完整排名", () -> showBudgetRankDialog(users));
        content.addView(buildBudgetRows(users, 5, true));

        addSectionHead("05", "最近注册", "TODAY", null);
        content.addView(buildRecentUsers(users, 3));

        if (openIssues > 0) {
            addSectionHead("06", "待解问题", String.valueOf(openIssues), null);
            content.addView(buildIssueRows(db.issues(true), 4));
        }
        content.addView(Ui.spacer(this, 8));
    }

    private View buildHero(int totalUsers, int newUsers, int activeUsers, double budget,
                           double contractTotal, int projects, int stalled) {
        FrameLayout hero = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(18));
        hero.setLayoutParams(lp);
        hero.setClipToOutline(true);
        hero.setBackground(Ui.rounded(Ui.NAVY, dp(18), Color.TRANSPARENT, 0));
        hero.addView(new HeroBackdropView(this), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout inner = Ui.column(this);
        inner.setPadding(dp(22), dp(22), dp(22), dp(20));
        hero.addView(inner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = Ui.row(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.rightMargin = dp(0);
        top.setLayoutParams(topLp);
        LinearLayout live = Ui.row(this);
        live.setGravity(Gravity.CENTER_VERTICAL);
        View green = new View(this);
        green.setBackground(Ui.rounded(Color.parseColor("#5EE0B0"), dp(3), Color.TRANSPARENT, 0));
        live.addView(green, new LinearLayout.LayoutParams(dp(5), dp(5)));
        TextView liveText = Ui.mono(this, "OVERVIEW / 管理快照", 10.5f, true, Color.parseColor("#A9C1F2"));
        liveText.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams liveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        liveLp.leftMargin = dp(7);
        liveText.setLayoutParams(liveLp);
        live.addView(liveText);
        top.addView(live, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout refresh = Ui.row(this);
        refresh.setGravity(Gravity.CENTER_VERTICAL);
        refresh.setPadding(dp(10), dp(7), dp(10), dp(7));
        refresh.setBackground(Ui.rounded(0x0DFFFFFF, dp(10), Color.parseColor("#3DBED3FF"), dp(1)));
        GlyphView refreshIcon = new GlyphView(this, "sync", Color.parseColor("#DCE7FF"));
        refresh.addView(refreshIcon, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView refreshText = Ui.text(this, "刷新", 11, true, Color.parseColor("#DCE7FF"));
        LinearLayout.LayoutParams refreshTextLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        refreshTextLp.leftMargin = dp(6);
        refreshText.setLayoutParams(refreshTextLp);
        refresh.addView(refreshText);
        refresh.setOnClickListener(v -> toast("已刷新：全量数据更新于 " + timeNow()));
        top.addView(refresh);
        inner.addView(top);

        LinearLayout main = Ui.row(this);
        main.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mainLp.topMargin = dp(22);
        mainLp.rightMargin = dp(0);
        main.setLayoutParams(mainLp);
        LinearLayout numBox = Ui.column(this);
        numBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView label = Ui.text(this, "累计用户", 11, false, Ui.HERO_MUTED);
        label.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.bottomMargin = dp(8);
        label.setLayoutParams(labelLp);
        numBox.addView(label);
        LinearLayout numLine = Ui.row(this);
        numLine.setGravity(Gravity.BOTTOM);
        TextView number = Ui.mono(this, formatInt(totalUsers), 54, true, Color.WHITE);
        number.setIncludeFontPadding(false);
        number.setLineSpacing(0, 1f);
        numLine.addView(number);
        TextView unit = Ui.text(this, "位用户", 11.5f, false, Ui.HERO_MUTED);
        unit.setPadding(dp(9), 0, 0, dp(10));
        numLine.addView(unit);
        numBox.addView(numLine);
        main.addView(numBox);

        LinearLayout note = Ui.column(this);
        note.setGravity(Gravity.RIGHT);
        note.setPadding(0, 0, 0, dp(2));
        note.setLayoutParams(new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT));
        note.addView(heroNote(String.valueOf(newUsers), "本月新增"));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(10);
        View second = heroNote(String.valueOf(activeUsers), "近 30 天活跃");
        second.setLayoutParams(noteLp);
        note.addView(second);
        main.addView(note);
        inner.addView(main);

        View rule = new View(this);
        rule.setBackgroundColor(0x2EABC5F5);
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        ruleLp.topMargin = dp(18);
        rule.setLayoutParams(ruleLp);
        inner.addView(rule);

        LinearLayout kpis = Ui.row(this);
        LinearLayout.LayoutParams kpisLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        kpisLp.topMargin = dp(16);
        kpis.setLayoutParams(kpisLp);
        kpis.addView(heroKpi("预算总额", compactMoney(budget), "人均 " + shortMoney(totalUsers == 0 ? 0 : budget / totalUsers), false));
        kpis.addView(heroDivide());
        kpis.addView(heroKpi("合同总额", compactMoney(contractTotal), db.contracts(null).size() + " 份合同", false));
        kpis.addView(heroDivide());
        kpis.addView(heroKpi("在管项目", String.valueOf(projects), stalled + " 个停滞", stalled > 0));
        View lastKpi = kpis.getChildAt(kpis.getChildCount() - 1);
        if (lastKpi instanceof LinearLayout) ((LinearLayout) lastKpi).setPadding(dp(0), dp(0), dp(0), dp(0));
        inner.addView(kpis);

        LinearLayout axis = Ui.row(this);
        LinearLayout.LayoutParams axisLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        axisLp.topMargin = dp(16);
        axis.setLayoutParams(axisLp);
        TextView date = Ui.mono(this, todayAxis(), 10.5f, true, Color.parseColor("#A7BFEA"));
        date.setSingleLine(true);
        date.setGravity(Gravity.START);
        date.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        axis.addView(date);
        TextView source = Ui.mono(this, Session.demo ? "本地演示" : "服务端数据", 10.5f, false, Ui.HERO_DIM);
        source.setSingleLine(true);
        source.setGravity(Gravity.CENTER);
        source.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        axis.addView(source);
        TextView version = Ui.mono(this, "v" + appVersionName(), 10.5f, false, Ui.HERO_DIM);
        version.setSingleLine(true);
        version.setGravity(Gravity.END);
        version.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        axis.addView(version);
        inner.addView(axis);
        return hero;
    }

    private View heroNote(String value, String label) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.BOTTOM);
        TextView number = Ui.mono(this, value, 14, true, Color.WHITE);
        number.setIncludeFontPadding(false);
        row.addView(number);
        TextView text = Ui.text(this, label, 11, false, Color.parseColor("#C7D6F2"));
        text.setSingleLine(true);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.leftMargin = dp(6);
        text.setLayoutParams(textLp);
        row.addView(text);
        return row;
    }

    private View heroStat(String value, String label) {
        LinearLayout box = Ui.column(this);
        box.setGravity(Gravity.RIGHT);
        TextView number = Ui.mono(this, value, 13.5f, true, Color.parseColor("#F5F8FF"));
        number.setIncludeFontPadding(false);
        number.setGravity(Gravity.RIGHT);
        box.addView(number);
        TextView text = Ui.text(this, label, 9.5f, false, Color.parseColor("#B9C9E6"));
        text.setSingleLine(true);
        text.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.topMargin = dp(2);
        text.setLayoutParams(textLp);
        box.addView(text);
        return box;
    }

    private View heroDivide() {
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor("#243B63"));
        line.setLayoutParams(new LinearLayout.LayoutParams(dp(1), dp(36)));
        return line;
    }

    private View heroKpi(String label, String value, String sub, boolean warn) {
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(0), dp(0), dp(13), dp(0));
        box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView labelView = Ui.text(this, label, 10, false, Color.parseColor("#8FA9D6"));
        labelView.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.bottomMargin = dp(6);
        labelView.setLayoutParams(labelLp);
        box.addView(labelView);
        TextView number = Ui.mono(this, value, 17, true, Color.parseColor("#F5F8FF"));
        number.setIncludeFontPadding(false);
        number.setSingleLine(true);
        box.addView(number);
        TextView subText = Ui.mono(this, sub, 10, false, warn ? Color.parseColor("#FFB37F") : Color.parseColor("#8FA9D6"));
        subText.setSingleLine(true);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(5);
        subText.setLayoutParams(subLp);
        box.addView(subText);
        return box;
    }

    private void addQuick(LinearLayout row, String iconType, String label, Runnable action) {
        LinearLayout item = Ui.column(this);
        item.setGravity(Gravity.START);
        item.setPadding(dp(11), dp(11), dp(9), dp(11));
        item.setBackground(Ui.rounded(Color.WHITE, dp(12), Ui.LINE, dp(1)));
        item.setElevation(dp(1));
        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, dp(66), 1);
        itemLp.rightMargin = dp(8);
        item.setLayoutParams(itemLp);
        GlyphView icon = new GlyphView(this, iconType, Ui.BLUE);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(18)));
        item.addView(icon);
        TextView text = Ui.text(this, label, 11.5f, true, Ui.INK);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.topMargin = dp(10);
        text.setLayoutParams(textLp);
        item.addView(text);
        item.setOnClickListener(v -> action.run());
        row.addView(item);
    }

    private View buildSiteStrip() {
        LinearLayout strip = Ui.row(this);
        addSitePhoto(strip, R.drawable.site_photo, "水电验收 · 11:20", "已打开江北云璟 · 水电验收记录");
        addSitePhoto(strip, R.drawable.living_photo, "主材进场 · 10:06", "已打开麓悦 · 主材进场记录");
        addSitePhoto(strip, R.drawable.material_photo, "定制复尺 · 09:42", "已打开南岸里 · 定制复尺记录");
        strip.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return strip;
    }

    private void addSitePhoto(LinearLayout strip, int drawableId, String label, String message) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Ui.rounded(Ui.NAVY, dp(10), Color.TRANSPARENT, 0));
        frame.setClipToOutline(true);
        frame.setLayoutParams(new LinearLayout.LayoutParams(0, dp(82), 1));
        ImageView image = new ImageView(this);
        image.setImageResource(drawableId);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.parseColor("#F0060F1E"), Color.parseColor("#00060F1E")}));
        frame.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.BOTTOM));
        TextView caption = Ui.text(this, label, 9.5f, false, Color.WHITE);
        caption.setMaxLines(1);
        caption.setPadding(dp(7), 0, dp(7), dp(6));
        caption.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        frame.addView(caption);
        frame.setOnClickListener(v -> toast(message));
        LinearLayout.LayoutParams frameLp = (LinearLayout.LayoutParams) frame.getLayoutParams();
        frameLp.rightMargin = dp(6);
        strip.addView(frame);
    }

    private View buildGrowthCard(int days) {
        LinearLayout card = Ui.card(this);
        int now = db.newUsers(days);
        int previous = db.newUsers(days * 2) - now;
        LinearLayout top = Ui.row(this);
        LinearLayout left = Ui.column(this);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        left.addView(Ui.mono(this, String.valueOf(now), 23, true, Ui.INK));
        left.addView(Ui.text(this, "本期新增用户 · 日均 " + String.format(Locale.CHINA, "%.1f", now / (float) days) + " 人", 9.5f, false, Ui.MUTED));
        top.addView(left);
        String deltaText;
        int deltaColor;
        if (previous > 0) {
            double delta = (now - previous) * 100.0 / previous;
            deltaText = (delta >= 0 ? "+" : "") + String.format(Locale.CHINA, "%.1f%%", delta);
            deltaColor = delta >= 0 ? Ui.GREEN : Ui.RED;
        } else {
            deltaText = "近 " + days + " 天";
            deltaColor = Ui.MUTED;
        }
        top.addView(Ui.pill(this, deltaText, deltaColor, deltaColor == Ui.GREEN ? Ui.GREEN_SOFT : (deltaColor == Ui.RED ? Ui.RED_SOFT : Ui.PANEL)));
        card.addView(top);
        TrendChartView chart = new TrendChartView(this);
        chart.setData(db.dailyNewUsers(days));
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(118));
        chartLp.topMargin = dp(6);
        chart.setLayoutParams(chartLp);
        card.addView(chart);
        LinearLayout legend = Ui.row(this);
        View line = new View(this);
        line.setBackgroundColor(Ui.BLUE);
        legend.addView(line, new LinearLayout.LayoutParams(dp(12), dp(2)));
        TextView label = Ui.text(this, "新增用户", 9, false, Ui.MUTED);
        label.setPadding(dp(4), 0, 0, 0);
        legend.addView(label);
        card.addView(legend);
        return card;
    }

    private View buildCityBlock(int total, int limit) {
        LinearLayout card = Ui.card(this);
        List<Db.Stat> cities = db.cities();
        int max = 1;
        for (Db.Stat stat : cities) max = Math.max(max, (int) stat.value);
        int shown = 0;
        for (Db.Stat stat : cities) {
            if (shown++ >= limit) break;
            double share = total == 0 ? 0 : stat.value / total * 100;
            card.addView(barRow(stat.label, stat.value / max * 88,
                    String.format(Locale.CHINA, "%.0f%%", share), shown == limit));
        }
        if (cities.isEmpty()) card.addView(Ui.text(this, "还没有城市数据", 11, false, Ui.MUTED));
        return card;
    }

    private View buildBudgetRows(List<Db.User> all, int limit, boolean includeSpent) {
        List<Db.User> users = new ArrayList<>(all);
        users.sort((a, b) -> Double.compare(b.budget, a.budget));
        LinearLayout block = whiteBlock();
        int shown = 0;
        for (Db.User user : users) {
            if (shown++ >= limit) break;
            LinearLayout row = Ui.row(this);
            row.setPadding(dp(14), dp(14), dp(16), dp(14));
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView rank = Ui.mono(this, String.format(Locale.CHINA, "%02d", shown), 10, true, Ui.BLUE);
            rank.setGravity(Gravity.CENTER);
            rank.setBackground(Ui.rounded(Ui.BLUE_SOFT, dp(8), Color.TRANSPARENT, 0));
            rank.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(30)));
            row.addView(rank);
            LinearLayout main = Ui.column(this);
            LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            mainLp.leftMargin = dp(10);
            mainLp.rightMargin = dp(10);
            main.setLayoutParams(mainLp);
            TextView title = Ui.text(this, user.name + " · " + user.projectName, 11.5f, true, Ui.INK);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            main.addView(title);
            TextView sub = Ui.text(this, user.city + " · " + user.method + (includeSpent ? " · 已支出 " + shortMoney(user.spent) : ""), 9.5f, false, Ui.MUTED);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(2);
            sub.setLayoutParams(subLp);
            main.addView(sub);
            row.addView(main);
            TextView budget = Ui.mono(this, shortMoney(user.budget), 13.5f, true, Ui.INK);
            budget.setGravity(Gravity.RIGHT);
            budget.setSingleLine(true);
            budget.setIncludeFontPadding(false);
            budget.setLayoutParams(new LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(budget);
            row.setOnClickListener(v -> openUser(user));
            block.addView(row);
            if (shown < Math.min(limit, users.size())) block.addView(Ui.divider(this));
        }
        if (users.isEmpty()) block.addView(emptyText("还没有用户预算数据"));
        return block;
    }

    private View buildRecentUsers(List<Db.User> users, int limit) {
        List<Db.User> sorted = new ArrayList<>(users);
        sorted.sort((a, b) -> (b.createdAt == null ? "" : b.createdAt).compareTo(a.createdAt == null ? "" : a.createdAt));
        LinearLayout block = whiteBlock();
        int shown = 0;
        for (Db.User user : sorted) {
            if (shown++ >= limit) break;
            LinearLayout row = Ui.row(this);
            row.setPadding(0, dp(9), 0, dp(9));
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(tinyIcon("users"));
            LinearLayout main = Ui.column(this);
            main.setPadding(dp(10), 0, 0, 0);
            main.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            main.addView(Ui.text(this, user.name + " · " + user.projectName, 11, true, Ui.INK));
            main.addView(Ui.text(this, user.city + " · " + user.method + " · 预算 " + shortMoney(user.budget), 9.5f, false, Ui.MUTED));
            row.addView(main);
            row.addView(Ui.mono(this, clockOf(user.createdAt), 9, false, Ui.MUTED));
            row.setOnClickListener(v -> openUser(user));
            block.addView(row);
            block.addView(Ui.divider(this));
        }
        if (sorted.isEmpty()) block.addView(emptyText("还没有注册用户"));
        return block;
    }

    private View buildIssueRows(List<Db.Issue> issues, int limit) {
        LinearLayout block = whiteBlock();
        int shown = 0;
        for (Db.Issue issue : issues) {
            if (shown++ >= limit) break;
            LinearLayout row = Ui.row(this);
            row.setPadding(0, dp(10), 0, dp(10));
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View rail = new View(this);
            rail.setBackgroundColor("高".equals(issue.level) ? Ui.RED : ("中".equals(issue.level) ? Ui.ORANGE : Ui.LINE));
            row.addView(rail, new LinearLayout.LayoutParams(dp(3), dp(28)));
            LinearLayout main = Ui.column(this);
            main.setPadding(dp(10), 0, 0, 0);
            main.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            LinearLayout title = Ui.row(this);
            title.addView(Ui.text(this, issue.title, 11.5f, true, Ui.INK));
            TextView lv = Ui.pill(this, issue.level, "高".equals(issue.level) ? Ui.RED : Ui.ORANGE,
                    "高".equals(issue.level) ? Ui.RED_SOFT : Ui.ORANGE_SOFT);
            LinearLayout.LayoutParams lvLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lvLp.leftMargin = dp(6);
            lv.setLayoutParams(lvLp);
            title.addView(lv);
            main.addView(title);
            main.addView(Ui.text(this, issue.userName + " · " + issue.recordedAt, 9.5f, false, Ui.MUTED));
            row.addView(main);
            block.addView(row);
            block.addView(Ui.divider(this));
        }
        return block;
    }

    // ===== 用户 =====

    private void buildUsers() {
        List<Db.User> all = db.users(null, null);
        int normal = 0, disabled = 0, stalled = 0;
        for (Db.User u : all) {
            if ("正常".equals(u.status)) normal++;
            if ("停用".equals(u.status)) disabled++;
            if (db.stalled(u.id)) stalled++;
        }

        content.addView(pageHead("USERS / ACCOUNTS", "用户与账号",
                formatInt(all.size()) + " 位用户 · " + db.contracts(null).size() + " 份合同 · 只读查看",
                "导出", () -> showExportDialog(true)));

        EditText search = Ui.input(this, "搜索昵称 / 项目 / 城市");
        LinearLayout searchWrap = Ui.row(this);
        searchWrap.setBackground(Ui.rounded(Color.WHITE, dp(9), Ui.LINE, dp(1)));
        searchWrap.setPadding(dp(11), 0, dp(8), 0);
        searchWrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        GlyphView searchIcon = new GlyphView(this, "search", Ui.MUTED);
        searchIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(16), dp(16)));
        searchWrap.addView(searchIcon);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setHintTextColor(Color.parseColor("#9AA7BA"));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        searchLp.leftMargin = dp(7);
        search.setLayoutParams(searchLp);
        searchWrap.addView(search);
        content.addView(searchWrap);

        String[] filters = {"全部", "正常", "已禁用", "停滞"};
        String[] counts = {formatInt(all.size()), String.valueOf(normal), String.valueOf(disabled), String.valueOf(stalled)};
        TextView[] chipViews = new TextView[filters.length];
        final String[] current = {"全部"};
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = Ui.row(this);
        chipRow.setPadding(0, dp(9), 0, dp(13));
        chipScroll.addView(chipRow);
        LinearLayout list = Ui.column(this);

        for (int i = 0; i < filters.length; i++) {
            TextView chip = chipView(filters[i], counts[i], i == 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            chip.setLayoutParams(lp);
            final String filter = filters[i];
            chip.setOnClickListener(v -> {
                current[0] = filter;
                for (int j = 0; j < chipViews.length; j++) paintChip(chipViews[j], filters[j].equals(filter), counts[j]);
                renderUsers(list, search.getText().toString(), filter);
            });
            chipViews[i] = chip;
            chipRow.addView(chip);
        }
        chipScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        list.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(chipScroll);
        content.addView(list);
        renderUsers(list, "", "全部");

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderUsers(list, s.toString(), current[0]);
            }
            public void afterTextChanged(Editable s) {}
        });

        TextView note = Ui.text(this, "这里只查看用户数据。禁用、强制下线与删除在用户详情里操作。", 9.5f, false, Ui.MUTED);
        note.setPadding(0, dp(10), 0, dp(6));
        content.addView(note);
    }

    private void renderUsers(LinearLayout list, String query, String filter) {
        list.removeAllViews();
        List<Db.User> users = db.users(query, null);
        List<Db.User> filtered = new ArrayList<>();
        for (Db.User user : users) {
            boolean keep = true;
            if ("正常".equals(filter)) keep = "正常".equals(user.status);
            else if ("已禁用".equals(filter)) keep = "停用".equals(user.status);
            else if ("停滞".equals(filter)) keep = db.stalled(user.id);
            if (keep) filtered.add(user);
        }
        if (filtered.isEmpty()) {
            list.addView(emptyCard("没有匹配的用户", "换个关键词，或切回「全部」"));
            return;
        }
        for (Db.User user : filtered) list.addView(userRow(user));
    }

    private View userRow(Db.User user) {
        boolean stalled = db.stalled(user.id);
        boolean disabled = "停用".equals(user.status);
        LinearLayout row = Ui.row(this);
        row.setPadding(dp(14), dp(15), dp(14), dp(15));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setAlpha(disabled ? 0.62f : 1f);

        TextView avatar = Ui.mono(this, user.name == null || user.name.isEmpty() ? "?" : user.name.substring(0, 1), 14, true, Ui.BLUE_DARK);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.rounded(Ui.BLUE_SOFT, dp(19), Color.parseColor("#291B4DFF"), dp(1)));
        avatar.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
        row.addView(avatar);

        LinearLayout main = Ui.column(this);
        LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        mainLp.leftMargin = dp(10);
        mainLp.rightMargin = dp(8);
        main.setLayoutParams(mainLp);

        LinearLayout nameLine = Ui.row(this);
        nameLine.addView(Ui.text(this, user.name, 13.5f, true, Ui.INK));
        TextView method = Ui.bluePill(this, user.method);
        LinearLayout.LayoutParams methodLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        methodLp.leftMargin = dp(6);
        method.setLayoutParams(methodLp);
        nameLine.addView(method);
        if (stalled && !disabled) {
            int idleDays = stalledDays(user.id);
            TextView stall = Ui.pill(this, idleDays > 0 ? "停滞 " + idleDays + " 天" : "停滞", Ui.ORANGE, Ui.ORANGE_SOFT);
            LinearLayout.LayoutParams stallLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stallLp.leftMargin = dp(6);
            stall.setLayoutParams(stallLp);
            nameLine.addView(stall);
        }
        main.addView(nameLine);
        TextView project = Ui.text(this, user.projectName + " · " + user.city, 10, false, Ui.MUTED);
        project.setSingleLine(true);
        project.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams projectLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        projectLp.topMargin = dp(2);
        project.setLayoutParams(projectLp);
        main.addView(project);

        LinearLayout track = Ui.row(this);
        track.setBackground(Ui.rounded(Color.parseColor("#E8EDF5"), dp(2), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        trackLp.topMargin = dp(8);
        track.setLayoutParams(trackLp);
        double progress = user.budget == 0 ? 0 : Math.min(100, user.spent / user.budget * 100);
        View fill = new View(this);
        fill.setBackground(Ui.rounded(user.spent > user.budget ? Ui.RED : (progress > 80 ? Ui.ORANGE : Ui.BLUE),
                dp(2), Color.TRANSPARENT, 0));
        fill.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (float) Math.max(2, progress)));
        track.addView(fill);
        View rest = new View(this);
        rest.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (float) Math.max(0, 100 - progress)));
        track.addView(rest);
        main.addView(track);

        LinearLayout foot = Ui.row(this);
        foot.setPadding(0, dp(4), 0, 0);
        foot.addView(Ui.mono(this, "已支出 " + shortMoney(user.spent), 9.5f, false, Ui.MUTED));
        String footEnd = user.openIssues > 0 ? user.openIssues + " 个问题"
                : (disabled ? "已禁用" : "进度 " + Math.min(9, db.nodes(user.id).size()) + " / 9");
        TextView end = Ui.mono(this, footEnd, 9.5f, false, user.openIssues > 0 ? Ui.ORANGE : Ui.MUTED);
        end.setGravity(Gravity.RIGHT);
        end.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        foot.addView(end);
        main.addView(foot);
        row.addView(main);

        LinearLayout right = Ui.column(this);
        right.setGravity(Gravity.RIGHT);
        right.setLayoutParams(new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView budget = Ui.mono(this, shortMoney(user.budget), 13.5f, true, Ui.INK);
        budget.setGravity(Gravity.RIGHT);
        budget.setSingleLine(true);
        budget.setIncludeFontPadding(false);
        right.addView(budget);
        TextView budgetLabel = Ui.text(this, "预算", 9.5f, false, Ui.MUTED);
        budgetLabel.setGravity(Gravity.RIGHT);
        right.addView(budgetLabel);
        TextView status = Ui.pill(this, disabled ? "停用" : "正常",
                disabled ? Ui.RED : Ui.GREEN, disabled ? Ui.RED_SOFT : Ui.GREEN_SOFT);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(6);
        status.setLayoutParams(statusLp);
        right.addView(status);
        row.addView(right);

        row.setBackgroundColor(Color.WHITE);
        row.setOnClickListener(v -> openUser(user));
        LinearLayout wrap = Ui.column(this);
        wrap.setBackgroundColor(Ui.PAGE);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(row);
        wrap.addView(Ui.divider(this));
        return wrap;
    }

    private TextView chipView(String label, String count, boolean on) {
        TextView chip = Ui.text(this, label + " " + count, 10.5f, true, on ? Color.WHITE : Ui.INK2);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackground(Ui.rounded(on ? Ui.NAVY : Color.WHITE, dp(15), on ? Ui.NAVY : Ui.LINE, dp(1)));
        return chip;
    }

    private void paintChip(TextView chip, boolean on, String count) {
        String label = chip.getText().toString();
        int space = label.lastIndexOf(' ');
        String name = space > 0 ? label.substring(0, space) : label;
        chip.setText(name + " " + count);
        chip.setTextColor(on ? Color.WHITE : Ui.INK2);
        chip.setBackground(Ui.rounded(on ? Ui.NAVY : Color.WHITE, dp(15), on ? Ui.NAVY : Ui.LINE, dp(1)));
    }

    // ===== 合同 =====

    private void buildContracts() {
        List<Db.Contract> contracts = db.contracts(null);
        double total = 0, paid = 0;
        for (Db.Contract c : contracts) {
            total += c.total;
            paid += c.paid;
        }
        double pending = Math.max(total - paid, 0);
        content.addView(pageHead("CONTRACTS / LEDGER", "合同台账",
                contracts.size() + " 份合同 · 总额 " + compactMoney(total) + " · 只读",
                "导出合同", () -> showExportDialog(false)));

        String[][] metrics = {
                {"合同总额", compactMoney(total), "覆盖率 " + pct(total == 0 ? 0 : paid / total * 100)},
                {"待付金额", compactMoney(pending), db.contractsPending() + " 份待付"},
                {"逾期节点", db.contractsOverdue() + " 份", "状态为风险"}
        };
        content.addView(metricStrip(metrics));

        String[] filters = {"全部", "待付", "逾期", "已付"};
        String[] counts = {String.valueOf(contracts.size()), String.valueOf(db.contractsPending()),
                String.valueOf(db.contractsOverdue()), String.valueOf(contracts.size() - db.contractsPending())};
        TextView[] chipViews = new TextView[filters.length];
        final String[] current = {"全部"};
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = Ui.row(this);
        chipScroll.addView(chipRow);
        LinearLayout list = Ui.column(this);
        for (int i = 0; i < filters.length; i++) {
            TextView chip = chipView(filters[i], counts[i], i == 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            chip.setLayoutParams(lp);
            final String filter = filters[i];
            chip.setOnClickListener(v -> {
                current[0] = filter;
                for (int j = 0; j < chipViews.length; j++) paintChip(chipViews[j], filters[j].equals(filter), counts[j]);
                renderContracts(list, filter);
            });
            chipViews[i] = chip;
            chipRow.addView(chip);
        }
        chipScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        list.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(chipScroll);
        content.addView(list);
        renderContracts(list, "全部");

        addSectionHead("01", "合同品类构成", contracts.size() + " 份", null);
        content.addView(buildContractCategories(contracts));

        TextView note = Ui.text(this, "合同内容、付款节点和凭证由用户自己记录；管理端不修改、不审核。", 9.5f, false, Ui.MUTED);
        note.setPadding(0, dp(10), 0, 0);
        content.addView(note);
    }

    private void renderContracts(LinearLayout list, String filter) {
        list.removeAllViews();
        List<Db.Contract> contracts = db.contracts(null);
        boolean any = false;
        for (Db.Contract c : contracts) {
            boolean pending = c.total > c.paid;
            boolean overdue = "风险".equals(c.status) || "逾期".equals(c.status);
            boolean keep;
            if ("待付".equals(filter)) keep = pending && !overdue;
            else if ("逾期".equals(filter)) keep = overdue;
            else if ("已付".equals(filter)) keep = !pending && !overdue;
            else keep = true;
            if (!keep) continue;
            any = true;
            list.addView(contractRow(c));
        }
        if (!any) list.addView(emptyCard("没有匹配的合同", "换个状态筛选再看"));
    }

    private View contractRow(Db.Contract contract) {
        boolean pending = contract.total > contract.paid;
        boolean overdue = "风险".equals(contract.status) || "逾期".equals(contract.status);
        LinearLayout row = Ui.row(this);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout main = Ui.column(this);
        main.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout title = Ui.row(this);
        title.addView(Ui.text(this, contract.name, 13, true, Ui.INK));
        String tag = overdue ? "逾期" : (pending ? "待付" : "已付");
        TextView pill = Ui.pill(this, tag, overdue ? Ui.RED : (pending ? Ui.BLUE_DARK : Ui.GREEN),
                overdue ? Ui.RED_SOFT : (pending ? Ui.BLUE_SOFT : Ui.GREEN_SOFT));
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillLp.leftMargin = dp(6);
        pill.setLayoutParams(pillLp);
        title.addView(pill);
        main.addView(title);
        Db.User contractUser = db.user(contract.userId);
        String where = contractUser == null || contractUser.city == null || contractUser.city.isEmpty() ? "" : " · " + contractUser.city;
        main.addView(Ui.text(this, contract.userName + where + " · 节点 " + contract.nodesDone + "/" + contract.nodesTotal
                + (pending ? " · 待付 " + pct(contract.getPending() * 100 / Math.max(1, contract.total)) : ""), 9.5f, false, Ui.MUTED));
        row.addView(main);
        main.setPadding(0, 0, dp(10), 0);
        LinearLayout side = Ui.column(this);
        side.setGravity(Gravity.RIGHT);
        side.setLayoutParams(new LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView amount = Ui.mono(this, money(contract.total), 13, true, Ui.INK);
        amount.setGravity(Gravity.RIGHT);
        amount.setSingleLine(true);
        amount.setIncludeFontPadding(false);
        side.addView(amount);
        TextView label = Ui.text(this, pending ? "待付" : "已付", 9.5f, false, Ui.MUTED);
        label.setGravity(Gravity.RIGHT);
        side.addView(label);
        row.addView(side);
        row.setOnClickListener(v -> showContractDialog(contract));
        LinearLayout wrap = Ui.column(this);
        wrap.setBackgroundColor(Ui.PAGE);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(row);
        wrap.addView(Ui.divider(this));
        return wrap;
    }

    private View buildContractCategories(List<Db.Contract> contracts) {
        Map<String, Integer> groups = new java.util.LinkedHashMap<>();
        groups.put("全屋定制", 0);
        groups.put("施工", 0);
        groups.put("主材", 0);
        groups.put("其他", 0);
        for (Db.Contract c : contracts) {
            String name = c.name == null ? "" : c.name;
            if (name.contains("定制") || name.contains("木作") || name.contains("柜")) groups.put("全屋定制", groups.get("全屋定制") + 1);
            else if (name.contains("施工") || name.contains("整装") || name.contains("半包")) groups.put("施工", groups.get("施工") + 1);
            else if (name.contains("主材") || name.contains("采购")) groups.put("主材", groups.get("主材") + 1);
            else groups.put("其他", groups.get("其他") + 1);
        }
        int total = Math.max(1, contracts.size());
        int max = 1;
        for (Integer n : groups.values()) max = Math.max(max, n);
        LinearLayout card = Ui.card(this);
        for (Map.Entry<String, Integer> entry : groups.entrySet()) {
            card.addView(barRow(entry.getKey(), entry.getValue() * 88.0 / max,
                    pct(entry.getValue() * 100.0 / total), "其他".equals(entry.getKey())));
        }
        return card;
    }

    // ===== 界面基础组件 =====

    private View pageHead(String kicker, String title, String sub, String action, Runnable onClick) {
        LinearLayout head = Ui.row(this);
        head.setGravity(Gravity.TOP);
        LinearLayout left = Ui.column(this);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView kick = Ui.mono(this, kicker, 9, true, Ui.MUTED);
        kick.setLetterSpacing(0.12f);
        left.addView(kick);
        TextView titleView = Ui.text(this, title, 23, true, Ui.INK);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(3);
        titleView.setLayoutParams(titleLp);
        left.addView(titleView);
        TextView subView = Ui.text(this, sub, 10.5f, false, Ui.MUTED);
    subView.setPadding(0, dp(4), 0, 0);
        left.addView(subView);
        head.addView(left);
        if (action != null) {
            TextView actionView = Ui.text(this, action, 10.5f, true, Ui.INK2);
            actionView.setPadding(dp(9), dp(7), dp(9), dp(7));
            actionView.setBackground(Ui.rounded(Color.WHITE, dp(8), Ui.LINE, dp(1)));
            actionView.setOnClickListener(v -> onClick.run());
            head.addView(actionView);
        }
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headLp.bottomMargin = dp(12);
        head.setLayoutParams(headLp);
        return head;
    }

    private View sectionHeadView(String index, String title, String meta) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = Ui.row(this);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView idx = Ui.mono(this, index, 9.5f, true, Ui.BLUE);
        idx.setPadding(0, 0, dp(8), 0);
        left.addView(idx);
        left.addView(Ui.text(this, title, 17.5f, true, Ui.INK));
        row.addView(left);
        row.addView(Ui.mono(this, meta, 9, false, Ui.MUTED));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(22);
        lp.bottomMargin = dp(12);
        row.setLayoutParams(lp);
        return row;
    }

    private View metricStrip(String[][] items) {
        LinearLayout strip = Ui.row(this);
        strip.setBackground(Ui.rounded(Color.WHITE, dp(10), Ui.LINE, dp(1)));
        strip.setPadding(0, dp(10), 0, dp(10));
        for (int i = 0; i < items.length; i++) {
            LinearLayout col = Ui.column(this);
            col.setPadding(dp(i == 0 ? 12 : 8), 0, dp(8), 0);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            col.addView(Ui.text(this, items[i][0], 9, false, Ui.MUTED));
            TextView value = Ui.mono(this, items[i][1], 14.5f, true, Ui.INK);
            value.setIncludeFontPadding(false);
            value.setSingleLine(true);
            LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            valueLp.topMargin = dp(4);
            value.setLayoutParams(valueLp);
            col.addView(value);
            TextView sub = Ui.mono(this, items[i][2], 9, false, Ui.MUTED);
            sub.setMaxLines(1);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(2);
            sub.setLayoutParams(subLp);
            col.addView(sub);
            strip.addView(col);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        strip.setLayoutParams(lp);
        return strip;
    }

    private View kpiGrid(String[][] items) {
        LinearLayout grid = Ui.column(this);
        for (int i = 0; i < items.length; i += 2) {
            LinearLayout row = Ui.row(this);
            row.addView(kpiCard(items[i]));
            if (i + 1 < items.length) row.addView(kpiCard(items[i + 1]));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rowLp.topMargin = dp(8);
            row.setLayoutParams(rowLp);
            grid.addView(row);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        grid.setLayoutParams(lp);
        return grid;
    }

    private View kpiCard(String[] item) {
        LinearLayout card = Ui.column(this);
        card.setPadding(dp(10), dp(9), dp(10), dp(10));
        card.setBackground(Ui.rounded(Color.WHITE, dp(9), Ui.LINE, dp(1)));
        card.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(Ui.text(this, item[0], 9.5f, false, Ui.MUTED));
        TextView value = Ui.mono(this, item[1], 20, true, Ui.INK);
        value.setPadding(0, dp(3), 0, 0);
        card.addView(value);
        TextView sub = Ui.mono(this, item[2], 9, false, Ui.MUTED);
        sub.setMaxLines(1);
        card.addView(sub);
        if (card.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            // 两列间距由外层 row 的边距补足
        }
        return card;
    }

    private View vizCard(String title, String meta, List<View> rows) {
        LinearLayout card = Ui.card(this);
        LinearLayout head = Ui.row(this);
        head.addView(Ui.text(this, title, 13, true, Ui.INK), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(Ui.mono(this, meta, 9, false, Ui.MUTED));
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headLp.bottomMargin = dp(4);
        head.setLayoutParams(headLp);
        card.addView(head);
        for (View row : rows) card.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        return card;
    }

    private View simpleRows(String[][] items) {
        LinearLayout block = whiteBlock();
        for (String[] item : items) {
            LinearLayout row = Ui.row(this);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout main = Ui.column(this);
            main.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            main.addView(Ui.text(this, item[0], 11, true, Ui.INK));
            main.addView(Ui.text(this, item[1], 9.5f, false, Ui.MUTED));
            row.addView(main);
            row.addView(Ui.mono(this, item[2], 11, true, Ui.INK));
            block.addView(row);
            block.addView(Ui.divider(this));
        }
        return block;
    }

    private View emptyCard(String title, String sub) {
        LinearLayout empty = Ui.column(this);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(10), dp(24), dp(10), dp(24));
        empty.setBackground(Ui.rounded(Color.WHITE, dp(12), Ui.LINE, dp(1)));
        empty.addView(Ui.text(this, title, 12, true, Ui.INK));
        empty.addView(Ui.text(this, sub, 10, false, Ui.MUTED));
        empty.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return empty;
    }

    // ===== 统计 =====

    private void buildStats() {
        content.addView(pageHead("ANALYTICS / READ ONLY", "数据统计",
                "只读汇总 · 不干预用户装修", null, null));

        TextView[] periodViews = new TextView[3];
        int[] ranges = {7, 30, 90};
        LinearLayout segmented = Ui.row(this);
        segmented.setPadding(dp(3), dp(3), dp(3), dp(3));
        segmented.setBackground(Ui.rounded(Color.WHITE, dp(9), Ui.LINE, dp(1)));
        LinearLayout body = Ui.column(this);
        for (int i = 0; i < 3; i++) {
            TextView button = Ui.text(this, "近 " + ranges[i] + " 天", 10.5f, true, i == 1 ? Color.WHITE : Ui.MUTED);
            button.setGravity(Gravity.CENTER);
            button.setPadding(0, dp(6), 0, dp(6));
            button.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            paintSegment(button, i == 1);
            final int days = ranges[i];
            button.setOnClickListener(v -> {
                for (int j = 0; j < periodViews.length; j++) paintSegment(periodViews[j], ranges[j] == days);
                renderStats(body, days);
            });
            periodViews[i] = button;
            segmented.addView(button);
        }
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        segLp.bottomMargin = dp(12);
        segmented.setLayoutParams(segLp);
        content.addView(segmented);
        content.addView(body);
        renderStats(body, 30);
    }

    private void paintSegment(TextView button, boolean on) {
        button.setTextColor(on ? Color.WHITE : Ui.MUTED);
        button.setBackground(Ui.rounded(on ? Ui.NAVY : Color.WHITE, dp(7), Color.TRANSPARENT, 0));
    }

    private void renderStats(LinearLayout body, int days) {
        body.removeAllViews();
        List<Db.User> users = db.users(null, null);
        int total = users.size();
        int newUsers = db.newUsers(days);
        int active = db.activeUsers(days);
        int active7 = db.activeUsers(7);
        double budgetTotal = 0, spentTotal = 0;
        int overBudget = 0;
        for (Db.User user : users) {
            budgetTotal += user.budget;
            spentTotal += user.spent;
            if (user.budget > 0 && user.spent > user.budget) overBudget++;
        }
        int openIssues = db.issues(true).size();

        String prev = "";
        int previous = db.newUsers(days * 2) - newUsers;
        if (previous > 0) {
            double delta = (newUsers - previous) * 100.0 / previous;
            prev = (delta >= 0 ? "+" : "") + String.format(Locale.CHINA, "%.1f%% 环比", delta);
        } else prev = "近 " + days + " 天";

        String[][] kpis = {
                {"累计用户", formatInt(total), "全部注册用户"},
                {"本期新增", String.valueOf(newUsers), prev},
                {"活跃用户", String.valueOf(active), "有登录记录"},
                {"7 日活跃率", pct(total == 0 ? 0 : active7 * 100.0 / total), "活跃 / 注册"}
        };
        body.addView(kpiGrid(kpis));

        body.addView(sectionHeadView("01", "用户增长与活跃", days + " DAYS"));
        body.addView(buildGrowthCard(days));
        body.addView(metricStrip(new String[][]{
                {"人均账本", String.format(Locale.CHINA, "%.1f", users.isEmpty() ? 0 : db.count("expenses", null) / (double) total), "支出笔数 / 用户"},
                {"人均预算", compactMoney(total == 0 ? 0 : budgetTotal / total), "按账本总额"},
                {"合同覆盖", pct(total == 0 ? 0 : db.count("users", "id IN (SELECT user_id FROM contracts)") * 100.0 / total), "有合同的用户"}
        }));
        body.addView(simpleRows(new String[][]{
                {"预算超支", "已支出超过自己设定的预算", overBudget + " 人"},
                {"未解问题", "用户记录的现场问题仍在处理", openIssues + " 个"}
        }));

        body.addView(sectionHeadView("02", "预算与支出", "仅统计"));
        body.addView(metricStrip(new String[][]{
                {"预算总额", compactMoney(budgetTotal), total + " 个项目"},
                {"实际支出", compactMoney(spentTotal), "已记账金额"},
                {"预算执行", pct(budgetTotal == 0 ? 0 : spentTotal / budgetTotal * 100), "超支 " + overBudget + " 人"}
        }));
        body.addView(buildBudgetDistribution(users));
        body.addView(buildFeeComposition());

        body.addView(sectionHeadView("03", "预算 Top 5", "完整排名"));
        body.addView(buildBudgetRows(users, 5, false));

        body.addView(sectionHeadView("04", "城市与装修方式", "TOP 5"));
        body.addView(buildCityBlock(total, 5));
        body.addView(buildMethodBlock(total));

        body.addView(sectionHeadView("05", "合同、问题与产品", "全量"));
        body.addView(kpiGrid(new String[][]{
                {"合同总额", compactMoney(db.sum("contracts", "total_amount", null)), db.contracts(null).size() + " 份合同"},
                {"待付金额", compactMoney(db.sum("contracts", "total_amount", null) - db.sum("contracts", "paid_amount", null)), db.contractsPending() + " 份待付"},
                {"逾期节点", db.contractsOverdue() + " 份", "状态为风险"},
                {"未解问题", openIssues + " 个", "共 " + db.count("issues", null) + " 个问题"}
        }));
        body.addView(buildIssueLevelBlock());
        body.addView(buildUsageBlock());
        body.addView(buildAdSummaryCard());
        body.addView(kpiGrid(new String[][]{
                {"数据源", Session.demo ? "本地" : "服务端", Session.demo ? "演示库" : "MySQL 镜像"},
                {"同步状态", Session.demo ? "实时" : "已同步", "最近 " + timeNow()},
                {"接口成功率", "—", "服务端未上报"},
                {"管理端版本", "v" + appVersionName(), "APK"}
        }));
        body.addView(Ui.spacer(this, 4));
    }

    private View buildBudgetDistribution(List<Db.User> users) {
        int[] bins = new int[5];
        for (Db.User user : users) {
            double b = user.budget;
            if (b < 100000) bins[0]++;
            else if (b < 200000) bins[1]++;
            else if (b < 300000) bins[2]++;
            else if (b < 500000) bins[3]++;
            else bins[4]++;
        }
        String[] labels = {"10 万以下", "10–20 万", "20–30 万", "30–50 万", "50 万以上"};
        List<View> rows = new ArrayList<>();
        for (int i = 0; i < bins.length; i++) {
            double share = users.isEmpty() ? 0 : bins[i] * 100.0 / users.size();
            rows.add(barRow(labels[i], share, pct(share), i == bins.length - 1));
        }
        return vizCard("预算区间分布", "USER / BUDGET", rows);
    }

    private View buildFeeComposition() {
        List<Db.Stat> categories = db.categories();
        double total = 0;
        for (Db.Stat stat : categories) total += stat.value;
        List<View> rows = new ArrayList<>();
        int i = 0;
        for (Db.Stat stat : categories) {
            double share = total == 0 ? 0 : stat.value * 100 / total;
            rows.add(barRow(stat.label, share, String.format(Locale.CHINA, "%.1f%%", share), i++ == categories.size() - 1));
        }
        return vizCard("费用构成", compactMoney(total), rows);
    }

    private View buildMethodBlock(int total) {
        List<Db.Stat> methods = db.methods();
        List<View> rows = new ArrayList<>();
        int i = 0;
        int max = 1;
        for (Db.Stat stat : methods) max = Math.max(max, (int) stat.value);
        for (Db.Stat stat : methods) {
            double share = total == 0 ? 0 : stat.value * 100 / total;
            rows.add(barRow(stat.label, stat.value * 88.0 / max, pct(share), i++ == methods.size() - 1));
        }
        if (rows.isEmpty()) rows.add(Ui.text(this, "还没有装修方式数据", 10, false, Ui.MUTED));
        return vizCard("装修方式", formatInt(total) + " 用户", rows);
    }

    private View buildIssueLevelBlock() {
        List<Db.Stat> levels = db.issueLevels();
        int total = db.count("issues", null);
        List<View> rows = new ArrayList<>();
        int i = 0;
        for (Db.Stat stat : levels) {
            double share = total == 0 ? 0 : stat.value * 100 / total;
            rows.add(barRow(stat.label + "级", share, pct(share), i++ == levels.size() - 1));
        }
        if (rows.isEmpty()) rows.add(Ui.text(this, "还没有现场问题", 10, false, Ui.MUTED));
        return vizCard("问题级别", total + " 个问题", rows);
    }

    private View buildUsageBlock() {
        int expenses = db.count("expenses", null);
        int contracts = db.count("contracts", null);
        int nodes = db.count("nodes", null);
        int issues = db.count("issues", null);
        int assets = db.count("users", null);
        List<View> rows = new ArrayList<>();
        int max = Math.max(1, Math.max(Math.max(expenses, contracts), Math.max(nodes, issues)));
        rows.add(barRow("支出记录", expenses * 86.0 / max, String.valueOf(expenses), false));
        rows.add(barRow("合同", contracts * 86.0 / max, String.valueOf(contracts), false));
        rows.add(barRow("进度节点", nodes * 86.0 / max, String.valueOf(nodes), false));
        rows.add(barRow("现场问题", issues * 86.0 / max, String.valueOf(issues), false));
        rows.add(barRow("用户", assets * 86.0 / max, String.valueOf(assets), true));
        return vizCard("内容产出量", "全量", rows);
    }

    private View buildAdSummaryCard() {
        JSONObject root = adSnapshot("");
        JSONObject summary = root.optJSONObject("summary");
        int impressions = summary == null ? 0 : summary.optInt("impressions");
        int clicks = summary == null ? 0 : summary.optInt("clicks");
        int clickUsers = summary == null ? 0 : summary.optInt("clickUsers");
        double revenue = summary == null ? 0 : summary.optDouble("estimatedRevenue", 0);
        JSONArray slots = root.optJSONArray( "slots");

        LinearLayout card = Ui.card(this);
        LinearLayout head = Ui.row(this);
        head.addView(Ui.text(this, "广告效果", 13, true, Ui.INK), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(Ui.mono(this, "¥" + String.format(Locale.CHINA, "%,.0f", revenue), 10, false, Ui.MUTED));
        card.addView(head);

        LinearLayout metrics = Ui.row(this);
        metrics.setPadding(0, dp(6), 0, 0);
        metrics.addView(inlineMetric("曝光", formatInt(impressions), "次", 1));
        metrics.addView(inlineMetric("点击", formatInt(clicks), summary == null ? "0%" : summary.optDouble("ctr") + "% CTR", 1));
        metrics.addView(inlineMetric("点击用户", formatInt(clickUsers), "人均 " + (summary == null ? "0" : summary.optDouble("avgClicks")) + " 次", 1));
        card.addView(metrics);
        card.addView(Ui.text(this, "广告只统计曝光、点击与关闭；点击记录用于效果分析，不关联用户账本。", 9.5f, false, Ui.MUTED));

        int slotsShown = slots == null ? 0 : slots.length();
        LinearLayout detail = Ui.row(this);
        detail.setPadding(dp(10), dp(9), dp(10), dp(9));
        detail.setBackground(Ui.rounded(Ui.PANEL, dp(9), Ui.LINE, dp(1)));
        LinearLayout detailMain = Ui.column(this);
        detailMain.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        detailMain.addView(Ui.text(this, "点击明细与用户排行", 11, true, Ui.INK));
        detailMain.addView(Ui.text(this, formatInt(clicks) + " 次点击 · " + formatInt(clickUsers) + " 位点击用户 · " + slotsShown + " 个广告位", 9.5f, false, Ui.MUTED));
        detail.addView(detailMain);
        TextView arrow = Ui.text(this, "查看明细 →", 9.5f, true, Ui.BLUE);
        detail.addView(arrow);
        detail.setOnClickListener(v -> showAdDetail(""));
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = dp(9);
        detail.setLayoutParams(detailLp);
        card.addView(detail);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        return card;
    }

    private View inlineMetric(String label, String value, String sub, float weight) {
        LinearLayout box = Ui.column(this);
        box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        box.addView(Ui.text(this, label, 9, false, Ui.MUTED));
        TextView number = Ui.mono(this, value, 17, true, Ui.INK);
        number.setPadding(0, dp(2), 0, 0);
        box.addView(number);
        box.addView(Ui.mono(this, sub, 9, false, Ui.MUTED));
        return box;
    }

    // ===== 系统 =====

    private void buildSystem() {
        content.addView(pageHead("SYSTEM / CONTROL", "系统与管控",
                "账号 · 广告 · 服务 · 数据合规", null, null));

        LinearLayout admin = Ui.card(this);
        LinearLayout adminTop = Ui.row(this);
        TextView avatar = Ui.mono(this, initialOf(Session.name), 14, true, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.rounded(Ui.NAVY, dp(21), Color.TRANSPARENT, 0));
        avatar.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        adminTop.addView(avatar);
        LinearLayout adminName = Ui.column(this);
        LinearLayout.LayoutParams adminNameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        adminNameLp.leftMargin = dp(11);
        adminName.setLayoutParams(adminNameLp);
        adminName.addView(Ui.text(this, Session.name, 14, true, Ui.INK));
        adminName.addView(Ui.mono(this, "ADMIN-001 · " + Session.phone, 9.5f, false, Ui.MUTED));
        adminTop.addView(adminName);
        adminTop.addView(Ui.pill(this, Session.role, Ui.BLUE_DARK, Ui.BLUE_SOFT));
        admin.addView(adminTop);

        LinearLayout facts = Ui.row(this);
        LinearLayout.LayoutParams factsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        factsLp.topMargin = dp(12);
        facts.setLayoutParams(factsLp);
        facts.addView(adminFact("管辖用户", String.valueOf(db.users(null, null).size())));
        facts.addView(adminFact("今日登录", Session.demo ? "本地演示" : "已登录"));
        facts.addView(adminFact("管理端版本", "v" + appVersionName()));
        admin.addView(facts);
        LinearLayout.LayoutParams adminLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        adminLp.bottomMargin = dp(14);
        admin.setLayoutParams(adminLp);
        content.addView(admin);

        addSectionHead("01", "账号与用户", "只管控账号", null);
        LinearLayout accountBlock = settingsBlock();
        accountBlock.addView(toggleRow("导出功能", "关闭后小程序端停止导出报表", loadBoolSetting("export", true),
                checked -> saveSetting("export", checked)));
        accountBlock.addView(dividerInside());
        accountBlock.addView(infoActionRow("强制下线", "让指定用户退出登录，需重新验证", "选择用户",
                () -> selectTab(1), false));
        accountBlock.addView(dividerInside());
        accountBlock.addView(infoActionRow("删除用户数据", "按用户申请删除账号与账本，操作不可恢复", "处理申请",
                () -> selectTab(1), false));
        content.addView(accountBlock);

        addSectionHead("02", "广告管理", "小程序端", null);
        LinearLayout adsBlock = settingsBlock();
        adsBlock.addView(toggleRow("广告总开关", "关闭后小程序端所有广告位停止展示", loadBoolSetting("ads", true),
                checked -> saveAdsTotal(checked)));
        for (int i = 0; i < Db.AD_SLOT_KEYS.length; i++) {
            String key = Db.AD_SLOT_KEYS[i];
            String name = Db.AD_SLOT_NAMES[i];
            adsBlock.addView(dividerInside());
            adsBlock.addView(toggleRow(name, key + " · 按小程序页面分别统计", adSlotEnabled(key),
                    checked -> saveAdSlot(key, checked)));
        }
        content.addView(adsBlock);

        addSectionHead("03", "服务与数据库", "HEALTH", null);
        LinearLayout health = Ui.card(this);
        health.addView(healthRow("API 服务", Session.demo ? "本地演示 · 未连接服务端" : Session.baseUrl, Session.demo ? Ui.MUTED : Ui.GREEN));
        health.addView(healthRow("MySQL 主库", Session.demo ? "本地 SQLite 演示库" : "MySQL 8.0 · 仅 Flask API 访问", Session.demo ? Ui.MUTED : Ui.GREEN));
        health.addView(healthRow("鉴权与权限", "JWT + RBAC · " + Session.role, Ui.BLUE));
        health.addView(healthRow("最近备份", "由服务端备份策略执行", Ui.MUTED));
        health.addView(healthRow("存储用量", "由服务端监控 · 管理端只读", Ui.MUTED));
        LinearLayout.LayoutParams healthLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        healthLp.bottomMargin = dp(14);
        health.setLayoutParams(healthLp);
        content.addView(health);

        addSectionHead("04", "数据与合规", "仅管理员", null);
        LinearLayout compliance = settingsBlock();
        compliance.addView(infoActionRow("全量导出", "用户 / 账本 / 支出 / 合同 · CSV", "导出",
                () -> showExportDialog(false), true));
        compliance.addView(dividerInside());
        compliance.addView(infoActionRow("脱敏导出", "隐藏手机号与详细地址", "导出",
                () -> exportCsv(true), false));
        compliance.addView(dividerInside());
        compliance.addView(infoActionRow("数据保留", "账号数据保留策略由服务端配置", "查看",
                () -> showInfoDialog("数据保留", "服务端数据保留策略以部署配置为准。\n管理端不额外保存用户账本副本，仅缓存同步所需的统计镜像。"), false));
        compliance.addView(dividerInside());
        compliance.addView(infoActionRow("备份", "每日自动备份 · 由服务端执行", "查看",
                () -> showInfoDialog("备份状态", "备份由服务端定时任务执行。\n管理端不接触数据库备份文件。"), false));
        content.addView(compliance);

        addSectionHead("05", "基础字典", "只改选项", null);
        LinearLayout dictionary = settingsBlock();
        dictionary.addView(infoActionRow("费用分类", "主材 / 施工 / 定制 / 软装 / 其他等 9 项", "管理",
                () -> showInfoDialog("费用分类", "主材\n半包工程\n全屋定制\n家电\n门窗\n软装\n灯具\n水电\n其他"), false));
        dictionary.addView(dividerInside());
        dictionary.addView(infoActionRow("进度节点", "开工 / 水电 / 泥瓦 / 木作 / 安装 / 竣工等 9 项", "管理",
                () -> showInfoDialog("进度节点", "开工准备\n拆改\n水电\n泥瓦\n木工\n油漆\n安装\n竣工\n软装"), false));
        dictionary.addView(dividerInside());
        dictionary.addView(infoActionRow("问题类型", "工期 / 材料 / 增项 / 付款 / 质量等", "管理",
                () -> showInfoDialog("问题类型", "工期延误\n材料色差\n增项\n付款争议\n质量问题\n其他"), false));
        content.addView(dictionary);

        TextView logout = Ui.text(this, "退出当前账号", 11.5f, true, Ui.RED);
        logout.setGravity(Gravity.CENTER);
        logout.setPadding(0, dp(12), 0, dp(12));
        logout.setBackground(Ui.rounded(Color.WHITE, dp(9), Color.parseColor("#33BE3B45"), dp(1)));
        LinearLayout.LayoutParams logoutLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logoutLp.topMargin = dp(18);
        logout.setLayoutParams(logoutLp);
        logout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("退出管理台")
                .setMessage("演示数据会保留，确认退出当前账号？")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出", (dialog, which) -> {
                    Session.clear(this);
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                }).show());
        content.addView(logout);
    }

    private LinearLayout settingsBlock() {
        LinearLayout block = Ui.column(this);
        block.setPadding(dp(12), 0, dp(12), 0);
        block.setBackground(Ui.rounded(Color.WHITE, dp(12), Ui.LINE, dp(1)));
        block.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return block;
    }

    private View dividerInside() {
        View view = new View(this);
        view.setBackgroundColor(Ui.LINE_SOFT);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return view;
    }

    private View toggleRow(String title, String sub, boolean checked, final ToggleListener listener) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout main = Ui.column(this);
        main.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        main.addView(Ui.text(this, title, 11.5f, true, Ui.INK));
        main.addView(Ui.text(this, sub, 9.5f, false, Ui.MUTED));
        row.addView(main);
        ToggleView toggle = new ToggleView(this);
        toggle.setChecked(checked);
        toggle.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(23)));
        toggle.setListener(listener::onChanged);
        row.addView(toggle);
        return row;
    }

    private interface ToggleListener { void onChanged(boolean checked); }

    private View infoActionRow(String title, String sub, String action, Runnable onClick, boolean primary) {
        LinearLayout row = Ui.row(this);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout main = Ui.column(this);
        main.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        main.addView(Ui.text(this, title, 11.5f, true, Ui.INK));
        main.addView(Ui.text(this, sub, 9.5f, false, Ui.MUTED));
        row.addView(main);
        TextView button = Ui.text(this, action, 10, true, primary ? Color.WHITE : Ui.INK2);
        button.setPadding(dp(9), dp(5), dp(9), dp(5));
        button.setBackground(Ui.rounded(primary ? Ui.BLUE : Ui.PANEL, dp(7), primary ? Ui.BLUE : Ui.LINE, dp(1)));
        button.setOnClickListener(v -> onClick.run());
        row.addView(button);
        return row;
    }

    private View adminFact(String label, String value) {
        LinearLayout box = Ui.column(this);
        box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(Ui.text(this, label, 9, false, Ui.MUTED));
        TextView valueView = Ui.mono(this, value, 10.5f, true, Ui.INK);
        valueView.setMaxLines(1);
        box.addView(valueView);
        return box;
    }

    private View healthRow(String label, String value, int dotColor) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, dp(7), 0, dp(7));
        View dot = new View(this);
        dot.setBackground(Ui.rounded(dotColor, dp(3), Color.TRANSPARENT, 0));
        row.addView(dot, new LinearLayout.LayoutParams(dp(6), dp(6)));
        TextView title = Ui.text(this, label, 10.5f, true, Ui.INK);
        title.setPadding(dp(7), 0, 0, 0);
        row.addView(title);
        TextView valueView = Ui.mono(this, value, 9.5f, false, Ui.MUTED);
        valueView.setGravity(Gravity.RIGHT);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(valueView);
        LinearLayout wrap = Ui.column(this);
        wrap.addView(row);
        wrap.addView(dividerInside());
        return wrap;
    }

    private String appVersionName() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return version == null ? "1.0.19" : version;
        } catch (Exception e) {
            return "1.0.19";
        }
    }

    private String initialOf(String name) {
        return name == null || name.isEmpty() ? "管" : name.substring(0, 1);
    }

    private boolean loadBoolSetting(String key, boolean fallback) {
        if (Session.demo) {
            String value = db.setting(key);
            return value == null ? fallback : "true".equals(value);
        }
        String raw = db.setting("settings_snapshot");
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            JSONObject root = new JSONObject(raw);
            JSONObject obj = root.optJSONObject(key);
            if (obj != null && obj.has("enabled")) return obj.optBoolean("enabled", fallback);
            if (root.has(key)) {
                Object value = root.opt(key);
                if (value instanceof Boolean) return (Boolean) value;
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private boolean adSlotEnabled(String key) {
        if (Session.demo) return db.slotEnabled(key);
        JSONObject root = adSnapshot("");
        JSONArray slots = root.optJSONArray("slots");
        if (slots != null) {
            for (int i = 0; i < slots.length(); i++) {
                JSONObject slot = slots.optJSONObject(i);
                if (slot != null && key.equals(slot.optString("key"))) return slot.optBoolean("enabled", true);
            }
        }
        return true;
    }

    private void saveAdSlot(String key, boolean checked) {
        if (Session.demo) {
            db.setSetting("ad_slot_" + key, checked ? "true" : "false");
            toast(Db.AD_SLOT_NAMES[java.util.Arrays.asList(Db.AD_SLOT_KEYS).indexOf(key)] + (checked ? " 已启用" : " 已关闭"));
            return;
        }
        try {
            JSONObject slot = new JSONObject();
            slot.put("enabled", checked);
            JSONObject slots = new JSONObject();
            slots.put(key, slot);
            JSONObject ads = new JSONObject();
            ads.put("slots", slots);
            JSONObject payload = new JSONObject();
            payload.put("ads", ads);
            pushSetting(payload, "广告位已更新");
        } catch (Exception e) {
            toast("设置失败：" + e.getMessage());
        }
    }

    private void saveAdsTotal(boolean checked) {
        if (Session.demo) {
            db.setSetting("ads", checked ? "true" : "false");
            toast(checked ? "广告总开关已开启" : "广告总开关已关闭");
            return;
        }
        try {
            JSONObject ads = new JSONObject();
            ads.put("enabled", checked);
            JSONObject payload = new JSONObject();
            payload.put("ads", ads);
            pushSetting(payload, "广告总开关已更新");
        } catch (Exception e) {
            toast("设置失败：" + e.getMessage());
        }
    }

    private void saveSetting(String key, boolean checked) {
        if (Session.demo) {
            db.setSetting(key, checked ? "true" : "false");
            toast("设置已保存");
            return;
        }
        try {
            JSONObject setting = new JSONObject();
            setting.put("enabled", checked);
            JSONObject payload = new JSONObject();
            payload.put(key, setting);
            pushSetting(payload, "设置已同步");
        } catch (Exception e) {
            toast("设置失败：" + e.getMessage());
        }
    }

    private void pushSetting(JSONObject payload, String success) {
        new Thread(() -> {
            try {
                RemoteSync.updateSettings(Session.baseUrl, Session.token, payload);
                runOnUiThread(() -> toast(success));
            } catch (Exception e) {
                runOnUiThread(() -> toast("更新失败：" + e.getMessage()));
            }
        }).start();
    }

    // ===== 广告详情与底部弹层 =====

    private void showAdDetail(String initialSlot) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = Ui.column(this);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.setBackground(Ui.rounded(Color.WHITE, dp(18), Ui.LINE, dp(1)));

        LinearLayout header = Ui.row(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headerText = Ui.column(this);
        headerText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView kicker = Ui.mono(this, "AD PERFORMANCE / 六广告位", 9, true, Ui.BLUE);
        kicker.setLetterSpacing(0.06f);
        headerText.addView(kicker);
        headerText.addView(Ui.text(this, "广告效果详情", 16, true, Ui.INK));
        header.addView(headerText);
        FrameLayout close = new FrameLayout(this);
        close.setBackground(Ui.rounded(Ui.PANEL, dp(15), Ui.LINE, dp(1)));
        close.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(30)));
        GlyphView closeIcon = new GlyphView(this, "close", Ui.MUTED);
        closeIcon.setLayoutParams(new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER));
        close.addView(closeIcon);
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = dp(10);
        header.setLayoutParams(headerLp);
        root.addView(header);

        LinearLayout body = Ui.column(this);
        ScrollView bodyScroll = new ScrollView(this);
        bodyScroll.setClipToPadding(false);
        bodyScroll.setPadding(0, 0, 0, dp(6));
        bodyScroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(bodyScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window shown = dialog.getWindow();
        if (shown != null) {
            shown.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            shown.setGravity(Gravity.BOTTOM);
            shown.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.88f));
        }
        renderAdBody(body, initialSlot == null ? "" : initialSlot, dialog);
    }

    private void renderAdBody(LinearLayout body, String slot, final Dialog dialog) {
        body.removeAllViews();
        JSONObject root = adSnapshot(slot);
        JSONObject allRoot = slot.isEmpty() ? root : adSnapshot("");
        JSONArray allSlots = allRoot.optJSONArray("slots");
        JSONObject summary = root.optJSONObject("summary");
        int impressions = summary == null ? 0 : summary.optInt("impressions");
        int clicks = summary == null ? 0 : summary.optInt("clicks");
        int clickUsers = summary == null ? 0 : summary.optInt("clickUsers");
        double revenue = summary == null ? 0 : summary.optDouble("estimatedRevenue", 0);
        double ctr = summary == null ? 0 : summary.optDouble("ctr", 0);
        double ecpm = impressions == 0 ? 0 : revenue * 1000.0 / impressions;

        LinearLayout row1 = Ui.row(this);
        row1.addView(adCell("曝光", compactCount(impressions), "次 · 近 30 天"));
        row1.addView(adCell("点击", formatInt(clicks), String.format(Locale.CHINA, "%.1f%% 点击率", ctr)));
        LinearLayout row2 = Ui.row(this);
        row2.addView(adCell("点击用户", formatInt(clickUsers), "人均 " + (summary == null ? 0 : summary.optDouble("avgClicks")) + " 次"));
        row2.addView(adCell("广告收入", "¥" + String.format(Locale.CHINA, "%,.0f", revenue), "eCPM ¥" + String.format(Locale.CHINA, "%.1f", ecpm)));
        LinearLayout.LayoutParams firstRowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        firstRowLp.bottomMargin = dp(7);
        row1.setLayoutParams(firstRowLp);
        body.addView(row1);
        body.addView(row2);

        body.addView(adSectionHead("01", "广告位表现", "点击广告位可筛选"));
        LinearLayout filterBlock = Ui.column(this);
        filterBlock.setPadding(0, dp(1), 0, dp(8));
        filterBlock.addView(adFilterChip("全部广告位", "", slot.isEmpty(), body, dialog, true));
        for (int pair = 0; pair < 3; pair++) {
            LinearLayout pairRow = Ui.row(this);
            LinearLayout.LayoutParams pairLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pairLp.topMargin = dp(6);
            pairRow.setLayoutParams(pairLp);
            for (int side = 0; side < 2; side++) {
                int index = pair * 2 + side;
                View chip = adFilterChip(Db.AD_SLOT_NAMES[index], Db.AD_SLOT_KEYS[index],
                        slot.equals(Db.AD_SLOT_KEYS[index]), body, dialog, false);
                LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, dp(30), 1);
                if (side == 1) chipLp.leftMargin = dp(6);
                chip.setLayoutParams(chipLp);
                pairRow.addView(chip);
            }
            filterBlock.addView(pairRow);
        }
        body.addView(filterBlock);

        LinearLayout table = Ui.column(this);
        table.setBackground(Ui.rounded(Color.WHITE, dp(10), Ui.LINE, dp(1)));
        LinearLayout headerRow = adTableHeader();
        table.addView(headerRow);
        table.addView(dividerInside());
        table.addView(adTableRow("全部广告位", summaryOf(allRoot), "", body, dialog, slot.isEmpty(), true));
        if (allSlots != null) {
            for (int i = 0; i < allSlots.length(); i++) {
                JSONObject item = allSlots.optJSONObject(i);
                if (item == null) continue;
                String key = item.optString("key");
                table.addView(dividerInside());
                table.addView(adTableRow(item.optString("name", key), item, key, body, dialog, slot.equals(key), false));
            }
        }
        LinearLayout.LayoutParams tableLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tableLp.bottomMargin = dp(4);
        table.setLayoutParams(tableLp);
        body.addView(table);

        body.addView(adSectionHead("02", "用户点击排行", "TOP 5"));
        JSONArray ranking = root.optJSONArray("ranking");
        int topClicks = 1;
        if (ranking != null) {
            for (int i = 0; i < ranking.length(); i++) {
                JSONObject item = ranking.optJSONObject(i);
                if (item != null) topClicks = Math.max(topClicks, item.optInt("clicks"));
            }
        }
        LinearLayout rankBlock = Ui.column(this);
        rankBlock.setBackground(Ui.rounded(Color.WHITE, dp(10), Ui.LINE, dp(1)));
        rankBlock.setPadding(dp(11), dp(5), dp(11), dp(5));
        if (ranking != null && ranking.length() > 0) {
            for (int i = 0; i < Math.min(5, ranking.length()); i++) {
                JSONObject item = ranking.optJSONObject(i);
                if (item == null) continue;
                LinearLayout row = Ui.row(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(8), 0, dp(8));
                TextView rank = Ui.mono(this, String.format(Locale.CHINA, "%02d", i + 1), 9.5f, true, Ui.BLUE);
                rank.setLayoutParams(new LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT));
                row.addView(rank);
                LinearLayout main = Ui.column(this);
                main.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                LinearLayout nameLine = Ui.row(this);
                nameLine.setGravity(Gravity.CENTER_VERTICAL);
                nameLine.addView(Ui.text(this, item.optString("name", item.optString("user_id")), 11.5f, true, Ui.INK));
                TextView city = Ui.text(this, " · " + item.optString("city", ""), 9.5f, false, Ui.MUTED);
                nameLine.addView(city);
                main.addView(nameLine);
                LinearLayout track = adTrack(item.optInt("clicks") * 100.0 / topClicks);
                LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
                trackLp.topMargin = dp(5);
                track.setLayoutParams(trackLp);
                main.addView(track);
                main.addView(Ui.mono(this, "最近 " + shortTime(item.optString("last_click")), 9, false, Ui.MUTED));
                row.addView(main);
                row.addView(Ui.mono(this, item.optInt("clicks") + " 次", 11, true, Ui.INK2));
                rankBlock.addView(row);
                if (i < Math.min(5, ranking.length()) - 1) rankBlock.addView(dividerInside());
            }
        } else {
            rankBlock.addView(emptyText("这个广告位还没有点击用户"));
        }
        body.addView(rankBlock);

        body.addView(adSectionHead("03", "点击明细", (slot.isEmpty() ? "全部广告位" : "当前广告位") + " · 最近优先"));
        LinearLayout clickBlock = Ui.column(this);
        clickBlock.setBackground(Ui.rounded(Color.WHITE, dp(10), Ui.LINE, dp(1)));
        JSONArray clickItems = root.optJSONArray("clicks");
        if (clickItems != null && clickItems.length() > 0) {
            int shown = Math.min(12, clickItems.length());
            for (int i = 0; i < shown; i++) {
                JSONObject item = clickItems.optJSONObject(i);
                if (item == null) continue;
                LinearLayout row = Ui.row(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(11), dp(9), dp(11), dp(9));
                TextView time = Ui.mono(this, shortTime(item.optString("occurred_at")), 9, false, Ui.MUTED);
                time.setLayoutParams(new LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT));
                row.addView(time);
                LinearLayout main = Ui.column(this);
                main.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                main.addView(Ui.text(this, item.optString("user_name", item.optString("user_id")), 11, true, Ui.INK));
                String creative = item.optString("creative", "");
                String detail = item.optString("slot_name", item.optString("slot_key"));
                if (!creative.isEmpty() && !creative.equals(detail)) detail += " · " + creative;
                if (!item.optString("project_name").isEmpty()) detail += " · " + item.optString("project_name");
                main.addView(Ui.text(this, detail, 9, false, Ui.MUTED));
                row.addView(main);
                row.addView(Ui.mono(this, "第 " + item.optInt("user_click_count") + " 次", 9, false, Ui.BLUE));
                clickBlock.addView(row);
                if (i < shown - 1) clickBlock.addView(dividerInside());
            }
        } else {
            clickBlock.addView(emptyText("这个广告位还没有点击记录"));
        }
        body.addView(clickBlock);

        TextView note = Ui.text(this, "同一用户在不同广告位点击分别计入；切换广告位后，上方汇总、排行与明细同步更新。", 9, false, Ui.MUTED);
        note.setPadding(0, dp(9), 0, dp(10));
        body.addView(note);

        LinearLayout actions = Ui.row(this);
        actions.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView export = Ui.button(this, "导出点击明细", false);
        export.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1));
        export.setOnClickListener(v -> exportAdClicks(slot));
        TextView close = Ui.button(this, "关闭", true);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(0, dp(42), 1);
        closeLp.leftMargin = dp(8);
        close.setLayoutParams(closeLp);
        close.setOnClickListener(v -> dialog.dismiss());
        actions.addView(export);
        actions.addView(close);
        body.addView(actions);
    }

    private JSONObject summaryOf(JSONObject root) {
        JSONObject summary = root.optJSONObject("summary");
        return summary == null ? new JSONObject() : summary;
    }

    private View adFilterChip(String name, String key, boolean selected, LinearLayout body, Dialog dialog, boolean full) {
        TextView chip = Ui.text(this, name, full ? 11 : 10, true, selected ? Color.WHITE : Ui.INK2);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(6), 0, dp(6), 0);
        chip.setBackground(Ui.rounded(selected ? Ui.NAVY : Ui.PANEL, dp(15),
                selected ? Ui.NAVY : Ui.LINE, dp(1)));
        chip.setOnClickListener(v -> renderAdBody(body, key, dialog));
        if (full) {
            chip.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        }
        return chip;
    }

    private LinearLayout adTableHeader() {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(11), dp(8), dp(11), dp(6));
        row.addView(adTableText("广告位", 1.6f, Gravity.START, 9, false, Ui.MUTED));
        row.addView(adTableText("曝光", .85f, Gravity.END, 9, false, Ui.MUTED));
        row.addView(adTableText("点击", .8f, Gravity.END, 9, false, Ui.MUTED));
        row.addView(adTableText("点击率", .65f, Gravity.END, 9, false, Ui.MUTED));
        return row;
    }

    private TextView adTableText(String text, float weight, int gravity, float size, boolean bold, int color) {
        TextView view = Ui.mono(this, text, size, bold, color);
        view.setGravity(gravity);
        view.setSingleLine(true);
        view.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        return view;
    }

    private View adTableRow(String name, JSONObject item, String key, LinearLayout body, Dialog dialog, boolean selected, boolean all) {
        int impressions = item.optInt("impressions");
        int clicks = item.optInt("clicks");
        double ctr = item.has("ctr") ? item.optDouble("ctr", 0) : (impressions == 0 ? 0 : clicks * 100.0 / impressions);
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(11), dp(9), dp(11), dp(9));
        row.setBackgroundColor(selected ? Ui.BLUE_SOFT : Color.TRANSPARENT);
        row.addView(adTableText(name, 1.6f, Gravity.START, 10, selected, selected ? Ui.BLUE_DARK : Ui.INK));
        row.addView(adTableText(compactCount(impressions), .85f, Gravity.END, 9.5f, false, Ui.INK2));
        row.addView(adTableText(formatInt(clicks), .8f, Gravity.END, 9.5f, false, Ui.INK2));
        row.addView(adTableText(String.format(Locale.CHINA, "%.1f%%", ctr), .65f, Gravity.END, 9.5f, false, Ui.INK2));
        row.setOnClickListener(v -> renderAdBody(body, key, dialog));
        return row;
    }

    private View adCell(String label, String value, String sub) {
        LinearLayout cell = Ui.column(this);
        cell.setPadding(dp(10), dp(9), dp(10), dp(9));
        cell.setBackground(Ui.rounded(Ui.PANEL, dp(8), Ui.LINE_SOFT, dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.rightMargin = dp(7);
        cell.setLayoutParams(lp);
        cell.addView(Ui.text(this, label, 9, false, Ui.MUTED));
        TextView number = Ui.mono(this, value, 18, true, Ui.INK);
        number.setPadding(0, dp(3), 0, 0);
        cell.addView(number);
        cell.addView(Ui.text(this, sub, 9, false, Ui.MUTED));
        return cell;
    }

    private View adSectionHead(String idx, String title, String meta) {
        LinearLayout row = Ui.row(this);
        LinearLayout left = Ui.row(this);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView idxView = Ui.mono(this, idx, 9.5f, true, Ui.BLUE);
        idxView.setPadding(0, 0, dp(6), 0);
        left.addView(idxView);
        left.addView(Ui.text(this, title, 12.5f, true, Ui.INK));
        row.addView(left);
        row.addView(Ui.text(this, meta, 9, false, Ui.MUTED));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private LinearLayout adTrack(double pct) {
        LinearLayout track = Ui.row(this);
        track.setBackground(Ui.rounded(Color.parseColor("#E8EDF5"), dp(2), Color.TRANSPARENT, 0));
        double clamped = Math.max(2, Math.min(100, pct));
        View fill = new View(this);
        fill.setBackground(Ui.rounded(Ui.BLUE, dp(2), Color.TRANSPARENT, 0));
        fill.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (float) clamped));
        track.addView(fill);
        View rest = new View(this);
        rest.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (float) (100 - clamped)));
        track.addView(rest);
        return track;
    }

    private String shortTime(String value) {
        if (value == null || value.length() < 16) return value == null ? "" : value;
        return value.substring(5, 16);
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
        int impressions = 0, clicks = 0, rewards = 0;
        double revenue = 0;
        java.util.Set<String> clickUsers = new java.util.LinkedHashSet<>();
        if (slots != null) {
            for (int i = 0; i < slots.length(); i++) {
                JSONObject item = slots.optJSONObject(i);
                if (item == null) continue;
                if (slot.equals(item.optString("key"))) {
                    filtered.put(item);
                    impressions += item.optInt("impressions");
                    clicks += item.optInt("clicks");
                    rewards += item.optInt("rewards");
                    revenue += item.optDouble("estimatedRevenue", 0);
                }
            }
        }
        summary.put("impressions", impressions);
        summary.put("clicks", clicks);
        summary.put("clickUsers", clickUsers.size());
        summary.put("rewards", rewards);
        summary.put("ctr", impressions > 0 ? Math.round(clicks * 10000.0 / impressions) / 100.0 : 0);
        summary.put("avgClicks", clickUsers.isEmpty() ? 0 : Math.round(clicks * 10.0 / clickUsers.size()) / 10.0);
        summary.put("estimatedRevenue", Math.round(revenue * 100.0) / 100.0);

        JSONArray clicksArray = root.optJSONArray("clicks");
        JSONArray filteredClicks = new JSONArray();
        java.util.Map<String, JSONObject> rankingMap = new java.util.LinkedHashMap<>();
        if (clicksArray != null) {
            for (int i = 0; i < clicksArray.length(); i++) {
                JSONObject item = clicksArray.optJSONObject(i);
                if (item == null || !slot.equals(item.optString("slot_key"))) continue;
                filteredClicks.put(item);
                String uid = item.optString("user_id");
                JSONObject rank = rankingMap.get(uid);
                if (rank == null) {
                    rank = new JSONObject();
                    rank.put("user_id", uid);
                    rank.put("name", item.optString("user_name", uid));
                    rank.put("city", item.optString("city", ""));
                    rank.put("clicks", 0);
                    rank.put("last_click", item.optString("occurred_at"));
                    rankingMap.put(uid, rank);
                }
                rank.put("clicks", rank.optInt("clicks") + 1);
            }
        }
        List<JSONObject> ranked = new ArrayList<>(rankingMap.values());
        ranked.sort((a, b) -> b.optInt("clicks") - a.optInt("clicks"));
        JSONArray ranking = new JSONArray();
        for (JSONObject item : ranked) ranking.put(item);
        for (JSONObject item : ranked) clickUsers.add(item.optString("user_id"));
        summary.put("clickUsers", clickUsers.size());
        summary.put("avgClicks", clickUsers.isEmpty() ? 0 : Math.round(clicks * 10.0 / clickUsers.size()) / 10.0);
        copy.put("summary", summary);
        copy.put("slots", filtered);
        copy.put("clicks", filteredClicks);
        copy.put("ranking", ranking);
        copy.put("slot", slot);
        return copy;
    }

    private void exportAdClicks(String slot) {
        JSONObject root = adSnapshot(slot);
        JSONArray clicks = root.optJSONArray("clicks");
        StringBuilder csv = new StringBuilder("时间,用户,城市,项目,广告位,素材,该用户累计点击\n");
        if (clicks != null) {
            for (int i = 0; i < clicks.length(); i++) {
                JSONObject item = clicks.optJSONObject(i);
                if (item == null) continue;
                csv.append(csvq(item.optString("occurred_at"))).append(',')
                        .append(csvq(item.optString("user_name", item.optString("user_id")))).append(',')
                        .append(csvq(item.optString("city"))).append(',')
                        .append(csvq(item.optString("project_name"))).append(',')
                        .append(csvq(item.optString("slot_name", item.optString("slot_key")))).append(',')
                        .append(csvq(item.optString("creative"))).append(',')
                        .append(item.optInt("user_click_count")).append('\n');
            }
        }
        shareText("广告点击明细", csv.toString());
    }

    // ===== 导出与详情弹窗 =====

    private void showExportDialog(boolean userContext) {
        new AlertDialog.Builder(this)
                .setTitle("数据导出")
                .setMessage("导出管理端当前镜像数据（CSV）。全量含手机号，脱敏版隐藏手机号。")
                .setNegativeButton("脱敏导出", (dialog, which) -> exportCsv(true))
                .setPositiveButton("全量导出", (dialog, which) -> exportCsv(false))
                .setNeutralButton("取消", null)
                .show();
    }

    private void exportCsv(boolean masked) {
        StringBuilder csv = new StringBuilder();
        csv.append("用户ID,姓名,手机号,城市,状态,项目,装修方式,预算,已支出,未解问题\n");
        for (Db.User user : db.users(null, null)) {
            csv.append(csvq(user.id)).append(',')
                    .append(csvq(user.name)).append(',')
                    .append(csvq(masked ? maskPhone(user.phone) : user.phone)).append(',')
                    .append(csvq(user.city)).append(',')
                    .append(csvq(user.status)).append(',')
                    .append(csvq(user.projectName)).append(',')
                    .append(csvq(user.method)).append(',')
                    .append(String.format(Locale.CHINA, "%.2f", user.budget)).append(',')
                    .append(String.format(Locale.CHINA, "%.2f", user.spent)).append(',')
                    .append(user.openIssues).append('\n');
        }
        csv.append("\n合同名称,用户,合同总额,已付,待付,状态,付款节点\n");
        for (Db.Contract contract : db.contracts(null)) {
            csv.append(csvq(contract.name)).append(',')
                    .append(csvq(contract.userName)).append(',')
                    .append(String.format(Locale.CHINA, "%.2f", contract.total)).append(',')
                    .append(String.format(Locale.CHINA, "%.2f", contract.paid)).append(',')
                    .append(String.format(Locale.CHINA, "%.2f", contract.getPending())).append(',')
                    .append(csvq(contract.status)).append(',')
                    .append(contract.nodesDone).append('/').append(contract.nodesTotal).append('\n');
        }
        csv.append("\n支出分类,用户,金额,支付方式,日期,风险状态\n");
        for (Db.Expense expense : db.expenses(null, null)) {
            csv.append(csvq(expense.category)).append(',')
                    .append(csvq(expense.userName)).append(',')
                    .append(String.format(Locale.CHINA, "%.2f", expense.amount)).append(',')
                    .append(csvq(expense.payType)).append(',')
                    .append(csvq(expense.spentAt)).append(',')
                    .append(csvq(expense.risk)).append('\n');
        }
        shareText(masked ? "装修记-脱敏数据" : "装修记-全量数据", csv.toString());
    }

    private void shareText(String title, String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, title);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, title));
    }

    private String csvq(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void showBudgetRankDialog(List<Db.User> all) {
        List<Db.User> users = new ArrayList<>(all);
        users.sort((a, b) -> Double.compare(b.budget, a.budget));
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(16), dp(4), dp(16), dp(8));
        int rank = 0;
        for (Db.User user : users) {
            rank++;
            LinearLayout row = Ui.row(this);
            row.setPadding(0, dp(9), 0, dp(9));
            row.addView(Ui.mono(this, String.format(Locale.CHINA, "%02d", rank), 9.5f, true, Ui.BLUE),
                    new LinearLayout.LayoutParams(dp(26), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout main = Ui.column(this);
            main.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            main.addView(Ui.text(this, user.name + " · " + user.projectName, 11.5f, true, Ui.INK));
            main.addView(Ui.text(this, user.city + " · " + user.method + " · 已支出 " + shortMoney(user.spent), 9.5f, false, Ui.MUTED));
            row.addView(main);
            row.addView(Ui.mono(this, shortMoney(user.budget), 11, true, Ui.INK));
            box.addView(row);
            box.addView(dividerInside());
        }
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("用户预算完整排名")
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showContractDialog(Db.Contract contract) {
        String message = "合同：" + contract.name + "\n"
                + "用户：" + contract.userName + "\n"
                + "总额：" + money(contract.total) + "\n"
                + "已付：" + money(contract.paid) + "\n"
                + "待付：" + money(contract.getPending()) + "\n"
                + "状态：" + contract.status + "\n"
                + "付款节点：" + contract.nodesDone + " / " + contract.nodesTotal + "\n\n"
                + "合同由用户自行记录，管理端只读查看。";
        new AlertDialog.Builder(this)
                .setTitle("合同详情")
                .setMessage(message)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showInfoDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("关闭", null)
                .show();
    }

    // __MORE__

    // ===== 通用组件 =====

    private void addSectionHead(String index, String title, String meta, Runnable action) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = Ui.row(this);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView idx = Ui.mono(this, index, 9.5f, true, Ui.BLUE);
        idx.setPadding(0, 0, dp(8), 0);
        left.addView(idx);
        left.addView(Ui.text(this, title, 17.5f, true, Ui.INK));
        row.addView(left);
        if (meta != null) {
            TextView metaView = Ui.mono(this, meta, 9, false, Ui.MUTED);
            if (action != null) metaView.setOnClickListener(v -> action.run());
            row.addView(metaView);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(25);
        lp.bottomMargin = dp(12);
        row.setLayoutParams(lp);
        row.setMinimumHeight(dp(27));
        content.addView(row);
    }

    private LinearLayout whiteBlock() {
        LinearLayout block = Ui.column(this);
        block.setPadding(0, 0, 0, 0);
        block.setBackground(Ui.rounded(Color.WHITE, dp(12), Ui.LINE, dp(1)));
        block.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return block;
    }

    private View barRow(String label, double widthPct, String value, boolean navy) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, dp(6), 0, dp(6));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView name = Ui.text(this, label, 10, false, Ui.INK2);
        name.setMaxLines(1);
        name.setLayoutParams(new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(name);
        LinearLayout track = Ui.row(this);
        track.setBackground(Ui.rounded(Color.parseColor("#E8EDF5"), dp(3), Color.TRANSPARENT, 0));
        track.setLayoutParams(new LinearLayout.LayoutParams(0, dp(6), 1));
        View fill = new View(this);
        fill.setBackground(Ui.rounded(navy ? Ui.NAVY : Ui.BLUE, dp(3), Color.TRANSPARENT, 0));
        double clamped = Math.max(2, Math.min(100, widthPct));
        fill.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (float) clamped));
        track.addView(fill);
        View rest = new View(this);
        rest.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (float) (100 - clamped)));
        track.addView(rest);
        row.addView(track);
        TextView count = Ui.mono(this, value, 9.5f, false, Ui.INK2);
        count.setGravity(Gravity.RIGHT);
        count.setLayoutParams(new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(count);
        return row;
    }

    private LinearLayout tinyIcon(String type) {
        LinearLayout wrap = Ui.column(this);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Ui.rounded(Ui.PANEL, dp(8), Color.TRANSPARENT, 0));
        frame.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        GlyphView glyph = new GlyphView(this, type, Ui.INK2);
        glyph.setLayoutParams(new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER));
        frame.addView(glyph);
        wrap.addView(frame);
        return wrap;
    }

    private TextView emptyText(String text) {
        TextView view = Ui.text(this, text, 11, false, Ui.MUTED);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(20), 0, dp(20));
        return view;
    }

    private void openUser(Db.User user) {
        Intent intent = new Intent(this, UserDetailActivity.class);
        intent.putExtra("user_id", user.id);
        startActivity(intent);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void crashFallback(Throwable error) {
        LinearLayout root = Ui.column(this);
        root.setPadding(dp(20), dp(80), dp(20), dp(20));
        root.setBackgroundColor(Ui.PAGE);
        root.addView(Ui.text(this, "管理台启动失败", 20, true, Ui.INK));
        root.addView(Ui.text(this, error.getClass().getSimpleName() + ": " + error.getMessage(), 11, false, Ui.RED));
        TextView retry = Ui.button(this, "重新进入", true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(18);
        retry.setLayoutParams(lp);
        retry.setOnClickListener(v -> recreate());
        root.addView(retry);
        setContentView(root);
    }

    // ===== 格式化 =====

    private String money(double value) {
        return "¥" + String.format(Locale.CHINA, "%,.0f", value);
    }

    private String compactMoney(double value) {
        if (Math.abs(value) >= 100000000) return String.format(Locale.CHINA, "¥%,.2f亿", value / 100000000);
        if (Math.abs(value) >= 10000) return String.format(Locale.CHINA, "¥%,.0f万", value / 10000);
        return String.format(Locale.CHINA, "¥%,.0f", value);
    }

    private String shortMoney(double value) {
        if (Math.abs(value) >= 10000) return String.format(Locale.CHINA, "¥%.1f万", value / 10000);
        return String.format(Locale.CHINA, "¥%,.0f", value);
    }

    private String pct(double value) {
        return String.format(Locale.CHINA, "%.0f%%", value);
    }

    private String formatInt(int value) {
        return String.format(Locale.CHINA, "%,d", value);
    }

    private String compactCount(int value) {
        if (value >= 10000) {
            String text = String.format(Locale.CHINA, "%.1f万", value / 10000.0);
            return text.replace(".0万", "万");
        }
        return formatInt(value);
    }

    private int stalledDays(String userId) {
        String latest = db.lastActivity(userId);
        if (latest == null || latest.isEmpty()) return 0;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            Date date = format.parse(latest.length() >= 10 ? latest.substring(0, 10) : latest);
            return date == null ? 0 : Math.max(0, (int) ((System.currentTimeMillis() - date.getTime()) / 86400000L));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone == null ? "" : phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String clockOf(String date) {
        return date != null && date.length() >= 16 ? date.substring(11, 16) : "";
    }

    private String timeNow() {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
    }

    private String todayAxis() {
        SimpleDateFormat monthDay = new SimpleDateFormat("MM.dd", Locale.CHINA);
        SimpleDateFormat weekday = new SimpleDateFormat("EEE", Locale.US);
        return (monthDay.format(new Date()) + " " + weekday.format(new Date())).toUpperCase(Locale.US);
    }
}
