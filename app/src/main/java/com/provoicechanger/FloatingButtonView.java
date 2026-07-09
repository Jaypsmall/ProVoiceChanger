package com.provoicechanger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

final class FloatingButtonView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Bitmap eyesRed;
    private final Bitmap eyesGreen;
    private boolean active;

    FloatingButtonView(Context context) {
        super(context);
        // OFF: demon_icon (Rojo), ON: green_eyes (Amarillo)
        eyesRed = BitmapFactory.decodeResource(getResources(), R.drawable.demon_icon);
        eyesGreen = BitmapFactory.decodeResource(getResources(), R.drawable.green_eyes);
    }

    void setActive(boolean active) {
        this.active = active;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = dp(65); // Ajustado para que los ojos luzcan bien
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        bounds.set(0, 0, getWidth(), getHeight());
        Bitmap b = active ? eyesGreen : eyesRed;
        
        if (b != null) {
            canvas.drawBitmap(b, null, bounds, paint);
        } else {
            paint.setColor(active ? 0xFF43E97B : 0xFFDF3F48);
            canvas.drawCircle(getWidth()/2f, getHeight()/2f, getWidth()/2f - dp(5), paint);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
