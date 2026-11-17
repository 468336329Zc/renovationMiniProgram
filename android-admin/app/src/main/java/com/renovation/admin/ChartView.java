package com.renovation.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 轻量原生图表：柱状图 / 折线图，无第三方依赖。 */
final class ChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Db.Stat> data = new ArrayList<>();
    private boolean line;
    private boolean money = true;

    ChartView(Context context) { super(context); }

    ChartView(Context context, AttributeSet attrs) { super(context, attrs); }

    void setData(List<Db.Stat> data, boolean line, boolean money) {
        this.data = data;
        this.line = line;
        this.money = money;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null || data.isEmpty()) {
            paint.setColor(Ui.MUTED);
            paint.setTextSize(Ui.dp(getContext(), 12));
            canvas.drawText("暂无数据", Ui.dp(getContext(), 12), getHeight() / 2f, paint);
            return;
        }
        int padLeft = Ui.dp(getContext(), 8);
        int padRight = Ui.dp(getContext(), 8);
        int padTop = Ui.dp(getContext(), 18);
        int padBottom = Ui.dp(getContext(), 26);
        float chartW = getWidth() - padLeft - padRight;
        float chartH = getHeight() - padTop - padBottom;
        double max = 1;
        for (Db.Stat s : data) max = Math.max(max, s.value);

        paint.setStrokeWidth(Ui.dp(getContext(), 1));
        paint.setColor(Color.parseColor("#E9EEF7"));
        for (int i = 0; i <= 3; i++) {
            float y = padTop + chartH * i / 3f;
            canvas.drawLine(padLeft, y, getWidth() - padRight, y, paint);
        }

        float slot = chartW / data.size();
        float barW = Math.min(slot * 0.52f, Ui.dp(getContext(), 30));
        if (line) {
            Path path = new Path();
            List<float[]> points = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                float x = padLeft + slot * (i + 0.5f);
                float y = padTop + chartH - (float) (data.get(i).value / max) * chartH;
                points.add(new float[]{x, y});
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            paint.setColor(Ui.BLUE);
            paint.setStrokeWidth(Ui.dp(getContext(), 2.2f));
            canvas.drawPath(path, paint);
            paint.setColor(Ui.BLUE);
            for (float[] p : points) canvas.drawCircle(p[0], p[1], Ui.dp(getContext(), 3.2f), paint);
            paint.setColor(Ui.BLUE_SOFT);
            paint.setAlpha(150);
            Path fill = new Path();
            fill.moveTo(points.get(0)[0], padTop + chartH);
            for (float[] p : points) fill.lineTo(p[0], p[1]);
            fill.lineTo(points.get(points.size() - 1)[0], padTop + chartH);
            fill.close();
            canvas.drawPath(fill, paint);
            paint.setAlpha(255);
        } else {
            for (int i = 0; i < data.size(); i++) {
                float left = padLeft + slot * i + (slot - barW) / 2f;
                float h = (float) (data.get(i).value / max) * chartH;
                paint.setColor(i == data.size() - 1 ? Ui.BLUE : Color.parseColor("#BFD2FF"));
                canvas.drawRoundRect(left, padTop + chartH - h, left + barW, padTop + chartH,
                        Ui.dp(getContext(), 5), Ui.dp(getContext(), 5), paint);
            }
        }

        paint.setColor(Ui.MUTED);
        paint.setTextSize(Ui.dp(getContext(), 10));
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < data.size(); i++) {
            String label = data.get(i).label;
            if (label.length() > 7) label = label.substring(0, 7);
            canvas.drawText(label, padLeft + slot * (i + 0.5f), getHeight() - Ui.dp(getContext(), 7), paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Ui.INK);
        paint.setTextSize(Ui.dp(getContext(), 11));
        String top = money ? shortMoney(max) : String.format(Locale.CHINA, "%.0f", max);
        canvas.drawText(top, padLeft, padTop - Ui.dp(getContext(), 5), paint);
    }

    static String shortMoney(double value) {
        if (value >= 10000) return String.format(Locale.CHINA, "%.1f万", value / 10000);
        return String.format(Locale.CHINA, "%.0f", value);
    }

    static String money(double value) {
        if (value >= 10000) return String.format(Locale.CHINA, "¥%.2f万", value / 10000);
        return String.format(Locale.CHINA, "¥%.0f", value);
    }
}
