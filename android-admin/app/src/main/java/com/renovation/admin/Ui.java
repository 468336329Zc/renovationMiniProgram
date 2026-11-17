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

/** 统一的冷白 + 电光蓝视觉语汇。 */
final class Ui {
    static final int BG = Color.parseColor("#F4F7FC");
    static final int CARD = Color.WHITE;
    static final int INK = Color.parseColor("#0B1220");
    static final int MUTED = Color.parseColor("#65708A");
    static final int LINE = Color.parseColor("#E4EAF4");
    static final int BLUE = Color.parseColor("#155EEF");
    static final int BLUE_DARK = Color.parseColor("#0B1F4B");
    static final int BLUE_SOFT = Color.parseColor("#E8F0FF");
    static final int ORANGE = Color.parseColor("#E86A17");
    static final int ORANGE_SOFT = Color.parseColor("#FFF0E4");
    static final int GREEN = Color.parseColor("#138A5B");
    static final int GREEN_SOFT = Color.parseColor("#E6F6EE");
    static final int RED = Color.parseColor("#C63C3C");
    static final int RED_SOFT = Color.parseColor("#FDECEC");

    private Ui() {}

    static int dp(Context c, float value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView text(Context c, String value, float size, boolean bold, int color) {
        TextView view = new TextView(c);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setLineSpacing(dp(c, 2), 1f);
        return view;
    }

    static GradientDrawable rounded(int color, float radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke);
        return d;
    }

    static LinearLayout card(Context c) {
        LinearLayout layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
        layout.setBackground(rounded(CARD, dp(c, 20), Color.TRANSPARENT, 0));
        layout.setElevation(dp(c, 2));
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
        TextView view = text(c, text, 11, true, textColor);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(c, 9), dp(c, 4), dp(c, 9), dp(c, 4));
        view.setBackground(rounded(bg, dp(c, 999), Color.TRANSPARENT, 0));
        return view;
    }

    static TextView button(Context c, String text, boolean primary) {
        TextView view = text(c, text, 13, true, primary ? Color.WHITE : BLUE);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(c, 16), dp(c, 11), dp(c, 16), dp(c, 11));
        view.setBackground(rounded(primary ? BLUE : BLUE_SOFT, dp(c, 12), Color.TRANSPARENT, 0));
        view.setClickable(true);
        return view;
    }

    static TextView ghostButton(Context c, String text) {
        TextView view = text(c, text, 13, true, MUTED);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        view.setBackground(rounded(Color.parseColor("#EEF2F9"), dp(c, 12), Color.TRANSPARENT, 0));
        view.setClickable(true);
        return view;
    }

    static EditText input(Context c, String hint) {
        EditText input = new EditText(c);
        input.setHint(hint);
        input.setTextSize(14);
        input.setTextColor(INK);
        input.setHintTextColor(Color.parseColor("#98A2B3"));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(dp(c, 13), dp(c, 10), dp(c, 13), dp(c, 10));
        input.setBackground(rounded(Color.parseColor("#EEF3FB"), dp(c, 12), LINE, dp(c, 1)));
        return input;
    }

    static View divider(Context c) {
        View view = new View(c);
        view.setBackgroundColor(LINE);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return view;
    }

    static View spacer(Context c, int height) {
        View view = new View(c);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, height)));
        return view;
    }

    static TextView section(Context c, String text) {
        TextView view = text(c, text, 15, true, INK);
        view.setPadding(0, dp(c, 22), 0, dp(c, 10));
        return view;
    }

    static TextView label(Context c, String text) {
        TextView view = text(c, text, 12, false, MUTED);
        return view;
    }

    static TextView value(Context c, String text, float size) {
        return text(c, text, size, true, INK);
    }

    static LinearLayout.LayoutParams matchWrap(Context c) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static int statusBg(String status) {
        if ("正常".equals(status) || "在岗".equals(status) || "已完成".equals(status) || "已付".equals(status)) return GREEN_SOFT;
        if ("停用".equals(status) || "异常".equals(status)) return RED_SOFT;
        if ("待核".equals(status) || "待付".equals(status) || "进行中".equals(status)) return ORANGE_SOFT;
        return BLUE_SOFT;
    }

    static int statusFg(String status) {
        if ("正常".equals(status) || "在岗".equals(status) || "已完成".equals(status) || "已付".equals(status)) return GREEN;
        if ("停用".equals(status) || "异常".equals(status)) return RED;
        if ("待核".equals(status) || "待付".equals(status) || "进行中".equals(status)) return ORANGE;
        return BLUE;
    }
}
