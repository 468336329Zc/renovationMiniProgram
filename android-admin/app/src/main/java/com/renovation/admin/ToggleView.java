package com.renovation.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RectF;
import android.view.View;

/** 原型中的 40×23 胶囊开关。 */
final class ToggleView extends View {
    interface Listener { void onChanged(boolean checked); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean checked;
    private Listener listener;

    ToggleView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
    }

    void setChecked(boolean checked) {
        this.checked = checked;
        invalidate();
    }

    boolean isChecked() { return checked; }

    void setListener(Listener listener) { this.listener = listener; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight(), w = getWidth();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked ? Ui.BLUE : Color.parseColor("#D7DFEA"));
        canvas.drawRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, paint);
        float knob = h - Ui.dp(getContext(), 6);
        float cx = checked ? w - h / 2f : h / 2f;
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(Ui.dp(getContext(), 2), 0, Ui.dp(getContext(), 1), Color.parseColor("#33000000"));
        canvas.drawCircle(cx, h / 2f, knob / 2f, paint);
        paint.clearShadowLayer();
    }

    @Override
    public boolean performClick() {
        checked = !checked;
        invalidate();
        if (listener != null) listener.onChanged(checked);
        return super.performClick();
    }
}
