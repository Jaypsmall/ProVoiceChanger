package com.provoicechanger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

final class FloatingButtonView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();

    private boolean active;

    FloatingButtonView(Context context) {
        super(context);
        setMinimumWidth(dp(72));
        setMinimumHeight(dp(72));
    }

    void setActive(boolean active) {
        this.active = active;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = dp(72);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = Math.min(getWidth(), getHeight()) / 2.0f - dp(4);
        float centerX = getWidth() / 2.0f;
        float centerY = getHeight() / 2.0f;

        paint.setShader(new RadialGradient(
                centerX - radius * 0.25f,
                centerY - radius * 0.35f,
                radius * 1.4f,
                Color.rgb(45, 46, 48),
                Color.rgb(5, 5, 6),
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setColor(active ? Color.rgb(67, 233, 123) : Color.rgb(220, 27, 42));
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setStyle(Paint.Style.FILL);

        String label = active ? "ON" : "PV";
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(dp(19));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.getTextBounds(label, 0, label.length(), textBounds);
        canvas.drawText(label, centerX, centerY - textBounds.exactCenterY(), textPaint);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
