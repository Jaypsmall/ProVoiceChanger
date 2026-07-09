package com.provoicechanger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

public class DemonControlView extends View {
    interface Listener {
        void onActivationRequested(boolean activate);
        void onSettingsChanged(int chunk, float pitch, float drive);
        void onInputRequested();
        void onOutputRequested();
        void onOverlayPermissionRequested();
        void onRootRequested();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF activationBounds = new RectF();
    private final RectF inputBounds = new RectF();
    private final RectF outputBounds = new RectF();
    private final RectF overlayBounds = new RectF();
    private final RectF rootBounds = new RectF();
    private final RectF heroBounds = new RectF();
    
    // Colores: Rojo para inactivo, Amarillo (como el ojo) para activo
    private static final int COLOR_RED = 0xFFCF1824;
    private static final int COLOR_YELLOW = 0xFFFFD700; // Amarillo intenso

    private final Bitmap demonRed;
    private final Bitmap demonYellow;

    private Listener listener;
    private boolean active;
    private int chunk = VoiceSettings.DEFAULT_CHUNK;
    private float pitch = VoiceSettings.DEFAULT_PITCH;
    private float drive = VoiceSettings.DEFAULT_DRIVE;
    private int activeSlider = -1;

    public DemonControlView(Context context) {
        super(context);
        setFocusable(true);
        demonRed = BitmapFactory.decodeResource(getResources(), R.drawable.demon_red_v2);
        demonYellow = BitmapFactory.decodeResource(getResources(), R.drawable.demon_yellow_v2);
    }

    void setListener(Listener listener) { this.listener = listener; }

    void setValues(int chunk, float pitch, float drive, boolean active) {
        this.chunk = chunk; this.pitch = pitch; this.drive = drive; this.active = active;
        invalidate();
    }

    void setActive(boolean active) { this.active = active; invalidate(); }
    void setOverlayReady(boolean ready) {
        invalidate(); }
    void setRootActive(boolean active) {
        invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(); int h = getHeight();
        int activeColor = active ? COLOR_YELLOW : COLOR_RED;

        // 1. Fondo completo
        heroBounds.set(0, 0, w, h);
        Bitmap b = active ? demonYellow : demonRed;
        if (b != null) canvas.drawBitmap(b, null, heroBounds, paint);
        else canvas.drawColor(Color.BLACK);

        // 2. Titulo
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(activeColor);
        textPaint.setTextSize(sp(26));
        canvas.drawText("PRO VOICE CHANGER", w / 2.0f, h * 0.08f, textPaint);

        // 3. Botones en los bordes (Estilo IN/OUT, estrechos y alargados)
        float sw = dp(35); float sh = dp(80);
        float margin = dp(5);
        
        // Lado Izquierdo: Entrada, Activar, Root
        inputBounds.set(margin, h * 0.15f, margin + sw, h * 0.15f + sh);
        activationBounds.set(margin, h * 0.30f, margin + sw, h * 0.30f + sh);
        rootBounds.set(margin, h * 0.45f, margin + sw, h * 0.45f + sh);

        // Lado Derecho: Salida, Flotante
        outputBounds.set(w - margin - sw, h * 0.15f, w - margin, h * 0.15f + sh);
        overlayBounds.set(w - margin - sw, h * 0.30f, w - margin, h * 0.30f + sh);

        drawEdgeButton(canvas, inputBounds, "IN", activeColor);
        drawEdgeButton(canvas, activationBounds, active ? "ON" : "OFF", activeColor);
        drawEdgeButton(canvas, rootBounds, "RT", activeColor);
        drawEdgeButton(canvas, outputBounds, "OUT", activeColor);
        drawEdgeButton(canvas, overlayBounds, "FLT", activeColor);

        // 4. Sliders (Sincronizados con el color)
        drawSliders(canvas, w, h, activeColor);
    }

    private void drawEdgeButton(Canvas canvas, RectF b, String label, int color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(40, 0, 0, 0));
        canvas.drawRoundRect(b, dp(4), dp(4), paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(0x66FFFFFF);
        canvas.drawRoundRect(b, dp(4), dp(4), paint);
        
        textPaint.setTextSize(sp(12)); textPaint.setColor(Color.WHITE);
        canvas.drawText(label, b.centerX(), b.centerY() + dp(4), textPaint);
    }

    private void drawSliders(Canvas canvas, int w, int h, int color) {
        float startY = h * 0.65f;
        float spacing = (h - startY - dp(40)) / 3f;
        drawSlider(canvas, 0, "CHUNK", String.valueOf(chunk), "256", "2048", startY, color);
        drawSlider(canvas, 1, "PITCH", String.format(Locale.US, "%.2f", pitch), "0.50", "1.50", startY + spacing, color);
        drawSlider(canvas, 2, "DISTORSION", String.format(Locale.US, "%.2f", drive), "1.00", "10.0", startY + spacing * 2, color);
    }

    private void drawSlider(Canvas canvas, int id, String label, String val, String min, String max, float y, int color) {
        float left = dp(40); float right = getWidth() - dp(40);
        float ty = y + dp(28);
        float norm = 0;
        if (id == 0) norm = (chunk - 256f) / (2048f - 256f);
        else if (id == 1) norm = (pitch - 0.5f) / (1.5f - 0.5f);
        else if (id == 2) norm = (drive - 1f) / (10f - 1f);
        float tx = left + (right - left) * norm;

        textPaint.setTextAlign(Paint.Align.LEFT); textPaint.setTextSize(sp(14));
        textPaint.setColor(color);
        canvas.drawText(label, left, y, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT); textPaint.setColor(Color.WHITE);
        canvas.drawText(val, right, y, textPaint);

        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(4)); paint.setColor(0x33FFFFFF);
        canvas.drawLine(left, ty, right, ty, paint);
        paint.setColor(color);
        canvas.drawLine(left, ty, tx, ty, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(tx, ty, dp(10), paint);

        textPaint.setTextAlign(Paint.Align.LEFT); textPaint.setTextSize(sp(10)); textPaint.setColor(0x88FFFFFF);
        canvas.drawText(min, left, ty + dp(22), textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(max, right, ty + dp(22), textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(); float y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (activationBounds.contains(x, y)) { if (listener != null) listener.onActivationRequested(!active); return true; }
            if (inputBounds.contains(x, y)) { if (listener != null) listener.onInputRequested(); return true; }
            if (outputBounds.contains(x, y)) { if (listener != null) listener.onOutputRequested(); return true; }
            if (overlayBounds.contains(x, y)) { if (listener != null) listener.onOverlayPermissionRequested(); return true; }
            if (rootBounds.contains(x, y)) { if (listener != null) listener.onRootRequested(); return true; }
            activeSlider = hitSlider(x, y);
        }
        if (activeSlider != -1 && (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_DOWN)) {
            updateSlider(activeSlider, x); return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) activeSlider = -1;
        return true;
    }

    private int hitSlider(float x, float y) {
        float startY = getHeight() * 0.65f;
        float spacing = (getHeight() - startY - dp(40)) / 3f;
        for (int i = 0; i < 3; i++) {
            float ty = startY + spacing * i + dp(28);
            if (Math.abs(y - ty) < dp(35)) return i;
        }
        return -1;
    }

    private void updateSlider(int s, float x) {
        float l = dp(40); float r = getWidth() - dp(40);
        float n = Math.max(0, Math.min(1, (x - l) / (r - l)));
        if (s == 0) chunk = Math.round(256 + n * (2048 - 256));
        else if (s == 1) pitch = 0.5f + n * (1.5f - 0.5f);
        else if (s == 2) drive = 1f + n * (10f - 1f);
        if (listener != null) listener.onSettingsChanged(chunk, pitch, drive);
        invalidate();
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
}
