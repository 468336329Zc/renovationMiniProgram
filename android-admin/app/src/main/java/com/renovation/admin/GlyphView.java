package com.renovation.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** 轻量线性图标，避免为单一图标引入整套资源。 */
final class GlyphView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String type;
    private int color;

    GlyphView(Context context, String type, int color) {
        super(context);
        this.type = type;
        this.color = color;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setColor(int color) {
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        float left = (w - s) / 2f, top = (h - s) / 2f;
        paint.setColor(color);
        paint.setStrokeWidth(Ui.dp(getContext(), 1.45f));
        Path p = new Path();

        if ("home".equals(type)) {
            p.moveTo(left + s * .13f, top + s * .46f);
            p.lineTo(left + s * .5f, top + s * .14f);
            p.lineTo(left + s * .87f, top + s * .46f);
            canvas.drawPath(p, paint);
            p.reset();
            p.moveTo(left + s * .22f, top + s * .43f);
            p.lineTo(left + s * .22f, top + s * .85f);
            p.lineTo(left + s * .78f, top + s * .85f);
            p.lineTo(left + s * .78f, top + s * .43f);
            canvas.drawPath(p, paint);
            canvas.drawLine(left + s * .42f, top + s * .85f, left + s * .42f, top + s * .62f, paint);
            canvas.drawLine(left + s * .42f, top + s * .62f, left + s * .60f, top + s * .62f, paint);
            canvas.drawLine(left + s * .60f, top + s * .62f, left + s * .60f, top + s * .85f, paint);
        } else if ("users".equals(type)) {
            canvas.drawCircle(left + s * .5f, top + s * .34f, s * .16f, paint);
            canvas.drawArc(new RectF(left + s * .17f, top + s * .52f, left + s * .83f, top + s * 1.02f),
                    200, 140, false, paint);
        } else if ("contract".equals(type)) {
            p.moveTo(left + s * .24f, top + s * .12f);
            p.lineTo(left + s * .62f, top + s * .12f);
            p.lineTo(left + s * .80f, top + s * .30f);
            p.lineTo(left + s * .80f, top + s * .88f);
            p.lineTo(left + s * .24f, top + s * .88f);
            p.close();
            canvas.drawPath(p, paint);
            canvas.drawLine(left + s * .62f, top + s * .12f, left + s * .62f, top + s * .31f, paint);
            canvas.drawLine(left + s * .62f, top + s * .31f, left + s * .80f, top + s * .31f, paint);
            for (int i = 0; i < 3; i++) {
                canvas.drawLine(left + s * .35f, top + s * (.45f + i * .13f),
                        left + s * .69f, top + s * (.45f + i * .13f), paint);
            }
        } else if ("stats".equals(type)) {
            canvas.drawLine(left + s * .14f, top + s * .86f, left + s * .88f, top + s * .86f, paint);
            canvas.drawRect(left + s * .24f, top + s * .54f, left + s * .39f, top + s * .86f, paint);
            canvas.drawRect(left + s * .44f, top + s * .30f, left + s * .59f, top + s * .86f, paint);
            canvas.drawRect(left + s * .64f, top + s * .42f, left + s * .79f, top + s * .86f, paint);
        } else if ("shield".equals(type)) {
            p.moveTo(left + s * .5f, top + s * .12f);
            p.lineTo(left + s * .84f, top + s * .25f);
            p.lineTo(left + s * .84f, top + s * .52f);
            p.cubicTo(left + s * .84f, top + s * .74f, left + s * .67f, top + s * .86f, left + s * .5f, top + s * .92f);
            p.cubicTo(left + s * .33f, top + s * .86f, left + s * .16f, top + s * .74f, left + s * .16f, top + s * .52f);
            p.lineTo(left + s * .16f, top + s * .25f);
            p.close();
            canvas.drawPath(p, paint);
        } else if ("sync".equals(type)) {
            canvas.drawArc(new RectF(left + s * .18f, top + s * .18f, left + s * .82f, top + s * .82f), 210, 115, false, paint);
            canvas.drawArc(new RectF(left + s * .18f, top + s * .18f, left + s * .82f, top + s * .82f), 30, 115, false, paint);
            canvas.drawLine(left + s * .29f, top + s * .17f, left + s * .29f, top + s * .33f, paint);
            canvas.drawLine(left + s * .29f, top + s * .17f, left + s * .45f, top + s * .17f, paint);
            canvas.drawLine(left + s * .71f, top + s * .83f, left + s * .71f, top + s * .67f, paint);
            canvas.drawLine(left + s * .71f, top + s * .83f, left + s * .55f, top + s * .83f, paint);
        } else if ("download".equals(type)) {
            canvas.drawLine(left + s * .5f, top + s * .16f, left + s * .5f, top + s * .64f, paint);
            p.moveTo(left + s * .32f, top + s * .48f);
            p.lineTo(left + s * .5f, top + s * .66f);
            p.lineTo(left + s * .68f, top + s * .48f);
            canvas.drawPath(p, paint);
            p.reset();
            p.moveTo(left + s * .18f, top + s * .70f);
            p.lineTo(left + s * .18f, top + s * .86f);
            p.lineTo(left + s * .82f, top + s * .86f);
            p.lineTo(left + s * .82f, top + s * .70f);
            canvas.drawPath(p, paint);
        } else if ("search".equals(type)) {
            canvas.drawCircle(left + s * .44f, top + s * .43f, s * .24f, paint);
            canvas.drawLine(left + s * .62f, top + s * .62f, left + s * .82f, top + s * .82f, paint);
        } else if ("db".equals(type)) {
            canvas.drawOval(new RectF(left + s * .2f, top + s * .12f, left + s * .8f, top + s * .34f), paint);
            canvas.drawLine(left + s * .2f, top + s * .23f, left + s * .2f, top + s * .76f, paint);
            canvas.drawLine(left + s * .8f, top + s * .23f, left + s * .8f, top + s * .76f, paint);
            canvas.drawArc(new RectF(left + s * .2f, top + s * .64f, left + s * .8f, top + s * .88f), 0, 180, false, paint);
            canvas.drawArc(new RectF(left + s * .2f, top + s * .34f, left + s * .8f, top + s * .50f), 0, 180, false, paint);
        } else if ("lock".equals(type)) {
            p.addRoundRect(new RectF(left + s * .2f, top + s * .44f, left + s * .8f, top + s * .86f),
                    s * .06f, s * .06f, Path.Direction.CW);
            canvas.drawPath(p, paint);
            canvas.drawArc(new RectF(left + s * .33f, top + s * .18f, left + s * .67f, top + s * .54f), 180, 180, false, paint);
        } else if ("right".equals(type)) {
            canvas.drawLine(left + s * .2f, top + s * .5f, left + s * .78f, top + s * .5f, paint);
            p.moveTo(left + s * .58f, top + s * .30f);
            p.lineTo(left + s * .78f, top + s * .5f);
            p.lineTo(left + s * .58f, top + s * .70f);
            canvas.drawPath(p, paint);
        } else if ("close".equals(type)) {
            canvas.drawLine(left + s * .24f, top + s * .24f, left + s * .76f, top + s * .76f, paint);
            canvas.drawLine(left + s * .76f, top + s * .24f, left + s * .24f, top + s * .76f, paint);
        } else {
            canvas.drawCircle(left + s * .5f, top + s * .5f, s * .1f, paint);
        }
    }
}
