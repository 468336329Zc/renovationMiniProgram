package com.renovation.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** 首页 Hero 的深海军蓝底纹：蓝图网格 + 右上光晕 + 校准角标。 */
final class HeroBackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);

    HeroBackdropView(Context context) {
        super(context);
        paint.setColor(Ui.NAVY);
        grid.setColor(Color.parseColor("#2A7C9BD6"));
        grid.setStrokeWidth(Ui.dp(context, 1));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        canvas.drawRect(0, 0, w, h, paint);

        RadialGradient glow = new RadialGradient(w - Ui.dp(getContext(), 36), -Ui.dp(getContext(), 42),
                Ui.dp(getContext(), 170), new int[]{0x7A1B4DFF, 0x001B4DFF}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(glow);
        canvas.drawCircle(w - Ui.dp(getContext(), 36), -Ui.dp(getContext(), 42), Ui.dp(getContext(), 170), paint);
        paint.setShader(null);

        int step = Ui.dp(getContext(), 26);
        for (int x = 0; x <= w; x += step) canvas.drawLine(x, 0, x, h, grid);
        for (int y = 0; y <= h; y += step) canvas.drawLine(0, y, w, y, grid);

        LinearGradient mask = new LinearGradient(0, h * .55f, 0, h,
                new int[]{0x000C1B33, 0xCC0C1B33}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(mask);
        canvas.drawRect(0, h * .55f, w, h, paint);
        paint.setShader(null);

        Paint crop = new Paint(Paint.ANTI_ALIAS_FLAG);
        crop.setColor(0x6BA8C2FF);
        crop.setStrokeWidth(Ui.dp(getContext(), 1));
        int m = Ui.dp(getContext(), 12), len = Ui.dp(getContext(), 14);
        canvas.drawLine(m, m, m + len, m, crop); canvas.drawLine(m, m, m, m + len, crop);
        canvas.drawLine(w - m, m, w - m - len, m, crop); canvas.drawLine(w - m, m, w - m, m + len, crop);
        canvas.drawLine(m, h - m, m + len, h - m, crop); canvas.drawLine(m, h - m, m, h - m - len, crop);
        canvas.drawLine(w - m, h - m, w - m - len, h - m, crop); canvas.drawLine(w - m, h - m, w - m, h - m - len, crop);
    }
}
