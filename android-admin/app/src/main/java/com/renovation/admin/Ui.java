package com.renovation.admin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 白色玻璃 · 大字编辑风：浅暖白底、白色浮卡、深墨预览块、绿色信号色。 */
final class Ui {
    static final int PAGE = Color.parseColor("#F1F4F0");
    static final int BG = Color.WHITE;
    static final int CARD = Color.argb(232, 255, 255, 255);
    static final int PANEL = Color.parseColor("#F5F8F5");
    static final int INK = Color.parseColor("#14201B");
    static final int INK2 = Color.parseColor("#485750");
    static final int MUTED = Color.parseColor("#7A8880");
    static final int LINE = Color.parseColor("#E2E9E2");
    static final int LINE_SOFT = Color.parseColor("#EEF2EC");
    static final int NAVY = Color.parseColor("#17211D");
    static final int NAVY2 = Color.parseColor("#24362F");
    static final int BLUE = Color.parseColor("#1F9E66");
    static final int BLUE_DARK = Color.parseColor("#0F6B45");
    static final int BLUE_SOFT = Color.parseColor("#E8F6EF");
    static final int BRIGHT = Color.parseColor("#B9EE5F");
    static final int ORANGE = Color.parseColor("#C96A1A");
    static final int ORANGE_SOFT = Color.parseColor("#FBEEE3");
    static final int GREEN = Color.parseColor("#0F7A5A");
    static final int GREEN_SOFT = Color.parseColor("#E6F4EF");
    static final int RED = Color.parseColor("#BE3B45");
    static final int RED_SOFT = Color.parseColor("#FBE9EB");
    static final int HERO_TEXT = Color.parseColor("#F5F8F2");
    static final int HERO_MUTED = Color.parseColor("#A8BBAF");
    static final int HERO_DIM = Color.parseColor("#9FB3A8");
    static final int HERO_LINE = Color.parseColor("#3A473F");
    static final String SANS = "sans-serif";
    private static Typeface monoBase;

    private Ui() {}

    static int dp(Context c, float value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView text(Context c, String value, float size, boolean bold, int color) {
        TextView view = new TextView(c);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(SANS, bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setLineSpacing(dp(c, 1.2f), 1f);
        return view;
    }

    static TextView mono(Context c, String value, float size, boolean bold, int color) {
        TextView view = text(c, value, size, bold, color);
        if (monoBase == null) {
            try {
                monoBase = Typeface.createFromAsset(c.getAssets(), "SpaceGrotesk.ttf");
            } catch (Exception ignored) {
                monoBase = Typeface.MONOSPACE;
            }
        }
        view.setTypeface(bold ? Typeface.create(monoBase, Typeface.BOLD) : monoBase);
        return view;
    }

    static GradientDrawable rounded(int color, float radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke);
        return d;
    }

    static GradientDrawable topRounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        return d;
    }

    static LinearLayout card(Context c) {
        LinearLayout layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(c, 16), dp(c, 17), dp(c, 16), dp(c, 17));
        layout.setBackground(rounded(CARD, dp(c, 22), LINE, dp(c, 1)));
        layout.setElevation(dp(c, 2));
        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    static LinearLayout panel(Context c) {
        LinearLayout layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(c, 13), dp(c, 13), dp(c, 13), dp(c, 13));
        layout.setBackground(rounded(PANEL, dp(c, 17), LINE, dp(c, 1)));
        return layout;
    }

    static LinearLayout row(Context c) {
        LinearLayout layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    static LinearLayout column(Context c) {
        LinearLayout layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams weightLp(float weight, int h) {
        return new LinearLayout.LayoutParams(0, h, weight);
    }

    static void margins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) raw).setMargins(left, top, right, bottom);
        }
    }

    static TextView pill(Context c, String text, int textColor, int bg) {
        TextView view = text(c, text, 10, true, textColor);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 4));
        view.setBackground(rounded(bg, dp(c, 10), Color.TRANSPARENT, 0));
        return view;
    }

    static TextView bluePill(Context c, String text) {
        return pill(c, text, BLUE_DARK, BLUE_SOFT);
    }

    static TextView button(Context c, String text, boolean primary) {
        TextView view = text(c, text, 12, true, primary ? INK : INK2);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9));
        view.setBackground(rounded(primary ? BRIGHT : PANEL, dp(c, 15), primary ? BRIGHT : LINE, dp(c, 1)));
        view.setClickable(true);
        return view;
    }

    static TextView ghostButton(Context c, String text) {
        TextView view = text(c, text, 11, true, INK2);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(c, 12), dp(c, 9), dp(c, 12), dp(c, 9));
        view.setBackground(rounded(CARD, dp(c, 9), LINE, dp(c, 1)));
        view.setClickable(true);
        return view;
    }

    static EditText input(Context c, String hint) {
        EditText input = new EditText(c);
        input.setHint(hint);
        input.setTextSize(12.5f);
        input.setTextColor(INK);
        input.setHintTextColor(Color.parseColor("#9AA7BA"));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        input.setBackground(rounded(CARD, dp(c, 14), LINE, dp(c, 1)));
        return input;
    }

    static View divider(Context c) {
        View view = new View(c);
        view.setBackgroundColor(LINE_SOFT);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return view;
    }

    static View spacer(Context c, int height) {
        View view = new View(c);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, height)));
        return view;
    }

    static TextView section(Context c, String text) {
        TextView view = text(c, text, 17, true, INK);
        view.setPadding(0, dp(c, 18), 0, dp(c, 9));
        return view;
    }

    static TextView label(Context c, String text) {
        return text(c, text, 11, false, MUTED);
    }

    static TextView value(Context c, String text, float size) {
        return text(c, text, size, true, INK);
    }

    static LinearLayout.LayoutParams matchWrap(Context c) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static int statusBg(String status) {
        if ("正常".equals(status) || "在岗".equals(status) || "已完成".equals(status) || "已付".equals(status)) return GREEN_SOFT;
        if ("停用".equals(status) || "异常".equals(status) || "风险".equals(status) || "逾期".equals(status)) return RED_SOFT;
        if ("待核".equals(status) || "待付".equals(status) || "进行中".equals(status)) return ORANGE_SOFT;
        return BLUE_SOFT;
    }

    static int statusFg(String status) {
        if ("正常".equals(status) || "在岗".equals(status) || "已完成".equals(status) || "已付".equals(status)) return GREEN;
        if ("停用".equals(status) || "异常".equals(status) || "风险".equals(status) || "逾期".equals(status)) return RED;
        if ("待核".equals(status) || "待付".equals(status) || "进行中".equals(status)) return ORANGE;
        return BLUE_DARK;
    }
}
