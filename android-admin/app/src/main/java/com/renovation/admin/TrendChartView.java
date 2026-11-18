package com.renovation.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** 原型中的增长折线：细网格、蓝色面积、无多余描点。 */
final class TrendChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Db.Stat> data = new ArrayList<>();

    TrendChartView(Context context) {
        super(context);
    }

    void setData(List<Db.Stat> data) {
        this.data = data;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null || data.isEmpty()) {
            paint.setColor(Ui.MUTED);
            paint.setTextSize(Ui.dp(getContext(), 11));
            canvas.drawText("暂无增长数据", Ui.dp(getContext(), 2), getHeight() / 2f, paint);
            return;
        }
        float padL = Ui.dp(getContext(), 2);
        float padR = Ui.dp(getContext(), 2);
        float padT = Ui.dp(getContext(), 8);
        float padB = Ui.dp(getContext(), 20);
        float w = getWidth() - padL - padR;
        float h = getHeight() - padT - padB;
        double max = 1;
        for (Db.Stat s : data) max = Math.max(max, s.value);

        paint.setStrokeWidth(Ui.dp(getContext(), 1));
        paint.setColor(Color.parseColor("#E8EDF5"));
        for (int i = 0; i <= 3; i++) {
            float y = padT + h * i / 3f;
            canvas.drawLine(padL, y, padL + w, y, paint);
        }

        int n = data.size();
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = padL + (n == 1 ? w / 2f : w * i / (n - 1f));
            ys[i] = padT + h - (float) (data.get(i).value / max) * h;
        }
        Path line = new Path();
        line.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) line.lineTo(xs[i], ys[i]);
        Path area = new Path(line);
        area.lineTo(xs[n - 1], padT + h);
        area.lineTo(xs[0], padT + h);
        area.close();
        paint.setShader(new LinearGradient(0, padT, 0, padT + h,
                Color.parseColor("#3D1B4DFF"), Color.parseColor("#001B4DFF"), Shader.TileMode.CLAMP));
        canvas.drawPath(area, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(getContext(), 2.1f));
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Ui.BLUE);
        canvas.drawPath(line, paint);
        paint.setStyle(Paint.Style.FILL);
        if (n > 1) {
            canvas.drawCircle(xs[n - 1], ys[n - 1], Ui.dp(getContext(), 3.4f), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(getContext(), 1.4f));
            paint.setColor(Color.parseColor("#441B4DFF"));
            canvas.drawCircle(xs[n - 1], ys[n - 1], Ui.dp(getContext(), 7), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        paint.setColor(Ui.MUTED);
        paint.setTextSize(Ui.dp(getContext(), 9));
        paint.setTextAlign(Paint.Align.LEFT);
        String a = data.get(0).label;
        String b = data.get(n / 2).label;
        String c = data.get(n - 1).label;
        canvas.drawText(a, padL, getHeight() - Ui.dp(getContext(), 3), paint);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(b, getWidth() / 2f, getHeight() - Ui.dp(getContext(), 3), paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(c, getWidth() - padR, getHeight() - Ui.dp(getContext(), 3), paint);
    }
}
