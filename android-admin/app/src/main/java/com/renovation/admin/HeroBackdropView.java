package com.renovation.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** 首页深色预览块的柔和玻璃底纹：深墨底 + 右上浅绿光晕。 */
final class HeroBackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    HeroBackdropView(Context context) {
        super(context);
        paint.setColor(Ui.NAVY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        canvas.drawRect(0, 0, w, h, paint);

        RadialGradient glow = new RadialGradient(
                w - Ui.dp(getContext(), 36), -Ui.dp(getContext(), 42),
                Ui.dp(getContext(), 170),
                new int[]{0x59B9EE5F, 0x00B9EE5F},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(glow);
        canvas.drawCircle(w - Ui.dp(getContext(), 36), -Ui.dp(getContext(), 42), Ui.dp(getContext(), 170), paint);
        paint.setShader(null);

        RadialGradient bottom = new RadialGradient(
                w / 2f, h + Ui.dp(getContext(), 30),
                Ui.dp(getContext(), 220),
                new int[]{0x3317211D, 0x0017211D},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(bottom);
        canvas.drawRect(0, h * .55f, w, h, paint);
        paint.setShader(null);
    }
}
