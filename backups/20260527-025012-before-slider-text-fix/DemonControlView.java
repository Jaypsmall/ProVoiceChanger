package com.provoicechanger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
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
    }

    private static final int SLIDER_NONE = -1;
    private static final int SLIDER_CHUNK = 0;
    private static final int SLIDER_PITCH = 1;
    private static final int SLIDER_DRIVE = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();
    private final RectF activationBounds = new RectF();
    private final RectF inputBounds = new RectF();
    private final RectF outputBounds = new RectF();
    private final RectF overlayBounds = new RectF();
    private final RectF heroBounds = new RectF();
    private final Rect demonSource = new Rect();
    private final RectF sliderTouchBounds = new RectF();
    private final Bitmap demonConcept;

    private Listener listener;
    private boolean active;
    private boolean overlayReady;
    private int chunk = VoiceSettings.DEFAULT_CHUNK;
    private float pitch = VoiceSettings.DEFAULT_PITCH;
    private float drive = VoiceSettings.DEFAULT_DRIVE;
    private int activeSlider = SLIDER_NONE;

    public DemonControlView(Context context) {
        super(context);
        setFocusable(true);
        demonConcept = BitmapFactory.decodeResource(getResources(), R.drawable.screm_demon);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setValues(int chunk, float pitch, float drive, boolean active) {
        this.chunk = VoiceSettings.clamp(chunk, VoiceSettings.MIN_CHUNK, VoiceSettings.MAX_CHUNK);
        this.pitch = VoiceSettings.clamp(pitch, VoiceSettings.MIN_PITCH, VoiceSettings.MAX_PITCH);
        this.drive = VoiceSettings.clamp(drive, VoiceSettings.MIN_DRIVE, VoiceSettings.MAX_DRIVE);
        this.active = active;
        invalidate();
    }

    void setActive(boolean active) {
        this.active = active;
        invalidate();
    }

    void setOverlayReady(boolean overlayReady) {
        this.overlayReady = overlayReady;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        drawBackground(canvas, width, height);
        drawTitle(canvas, width, height);
        drawHero(canvas, width, height);
        drawSideAreas(canvas);
        drawActivationButton(canvas, width, height);
        drawOverlayStatus(canvas, width, height);
        drawSliders(canvas, width, height);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (activationBounds.contains(x, y)) {
                    return true;
                }
                if (inputBounds.contains(x, y)) {
                    if (listener != null) {
                        listener.onInputRequested();
                    }
                    return true;
                }
                if (outputBounds.contains(x, y)) {
                    if (listener != null) {
                        listener.onOutputRequested();
                    }
                    return true;
                }
                if (overlayBounds.contains(x, y)) {
                    if (listener != null) {
                        listener.onOverlayPermissionRequested();
                    }
                    return true;
                }
                activeSlider = hitSlider(x, y);
                if (activeSlider != SLIDER_NONE) {
                    updateSlider(activeSlider, x);
                    return true;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (activeSlider != SLIDER_NONE) {
                    updateSlider(activeSlider, x);
                    return true;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (activationBounds.contains(x, y) && listener != null) {
                    listener.onActivationRequested(!active);
                }
                activeSlider = SLIDER_NONE;
                return true;

            case MotionEvent.ACTION_CANCEL:
                activeSlider = SLIDER_NONE;
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    private void drawBackground(Canvas canvas, int width, int height) {
        paint.setShader(new LinearGradient(
                0,
                0,
                0,
                height,
                Color.rgb(2, 2, 3),
                Color.rgb(12, 13, 15),
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        paint.setColor(Color.argb(90, 120, 0, 0));
        paint.setStrokeWidth(dp(1));
        for (int i = 0; i < 14; i++) {
            float y = height * (i / 14.0f);
            canvas.drawLine(0, y, width, y, paint);
        }
    }

    private void drawTitle(Canvas canvas, int width, int height) {
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.rgb(207, 24, 36));
        textPaint.setTextSize(Math.min(sp(30), width * 0.065f));
        textPaint.setShadowLayer(dp(7), 0, 0, Color.rgb(80, 0, 0));
        canvas.drawText("Pro VoiceChanger Demonio", width / 2.0f, height * 0.085f, textPaint);
        textPaint.clearShadowLayer();
    }

    private void drawHero(Canvas canvas, int width, int height) {
        float margin = dp(22);
        float top = height * 0.135f;
        float bottom = height * 0.335f;
        heroBounds.set(margin, top, width - margin, bottom);

        if (demonConcept != null) {
            demonSource.set(
                    (int) (demonConcept.getWidth() * 0.055f),
                    (int) (demonConcept.getHeight() * 0.125f),
                    (int) (demonConcept.getWidth() * 0.515f),
                    (int) (demonConcept.getHeight() * 0.355f)
            );
            canvas.drawBitmap(demonConcept, demonSource, heroBounds, paint);
        } else {
            paint.setColor(Color.rgb(7, 7, 8));
            canvas.drawRoundRect(heroBounds, dp(8), dp(8), paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(120, 190, 22, 34));
        canvas.drawRoundRect(heroBounds, dp(8), dp(8), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSideAreas(Canvas canvas) {
        float sideWidth = dp(54);
        float sideInset = dp(12);
        float top = heroBounds.top + dp(10);
        float bottom = heroBounds.bottom - dp(10);

        inputBounds.set(sideInset, top, sideInset + sideWidth, bottom);
        outputBounds.set(getWidth() - sideInset - sideWidth, top, getWidth() - sideInset, bottom);

        drawSideButton(canvas, inputBounds, "<-", "Entrada");
        drawSideButton(canvas, outputBounds, "->", "Salida");
    }

    private void drawSideButton(Canvas canvas, RectF bounds, String arrow, String label) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(35, 190, 20, 30));
        canvas.drawRoundRect(bounds, dp(7), dp(7), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(Color.argb(190, 190, 24, 36));
        canvas.drawRoundRect(bounds, dp(7), dp(7), paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(25));
        textPaint.setColor(Color.rgb(216, 28, 42));
        canvas.drawText(arrow, bounds.centerX(), bounds.centerY() - dp(8), textPaint);

        textPaint.setTextSize(sp(11));
        textPaint.setColor(Color.WHITE);
        canvas.drawText(label, bounds.centerX(), bounds.centerY() + dp(18), textPaint);
    }

    private void drawActivationButton(Canvas canvas, int width, int height) {
        float radius = Math.min(dp(63), width * 0.18f);
        float centerX = width / 2.0f;
        float centerY = height * 0.43f;
        activationBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        paint.setShader(new RadialGradient(
                centerX - radius * 0.25f,
                centerY - radius * 0.35f,
                radius * 1.35f,
                Color.rgb(48, 50, 52),
                Color.rgb(5, 5, 6),
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(4));
        paint.setColor(active ? Color.rgb(67, 233, 123) : Color.rgb(200, 22, 36));
        canvas.drawCircle(centerX, centerY, radius, paint);

        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(160, 255, 255, 255));
        canvas.drawCircle(centerX, centerY, radius - dp(6), paint);
        paint.setStyle(Paint.Style.FILL);

        String label = active ? "PAUSAR" : "ACTIVAR";
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(24));
        textPaint.setColor(Color.WHITE);
        textPaint.getTextBounds(label, 0, label.length(), textBounds);
        canvas.drawText(label, centerX, centerY - textBounds.exactCenterY(), textPaint);
    }

    private void drawOverlayStatus(Canvas canvas, int width, int height) {
        float buttonWidth = Math.min(dp(250), width - dp(52));
        float buttonHeight = dp(42);
        float centerX = width / 2.0f;
        float centerY = Math.min(height * 0.535f, activationBounds.bottom + dp(52));
        overlayBounds.set(
                centerX - buttonWidth / 2.0f,
                centerY - buttonHeight / 2.0f,
                centerX + buttonWidth / 2.0f,
                centerY + buttonHeight / 2.0f
        );

        int accent = overlayReady ? Color.rgb(67, 233, 123) : Color.rgb(220, 27, 42);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(55, Color.red(accent), Color.green(accent), Color.blue(accent)));
        canvas.drawRoundRect(overlayBounds, dp(8), dp(8), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.3f));
        paint.setColor(accent);
        canvas.drawRoundRect(overlayBounds, dp(8), dp(8), paint);
        paint.setStyle(Paint.Style.FILL);

        String label = overlayReady ? "FLOTANTE LISTO" : "PERMITIR FLOTANTE";
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(14));
        textPaint.setColor(Color.WHITE);
        textPaint.getTextBounds(label, 0, label.length(), textBounds);
        canvas.drawText(label, centerX, centerY - textBounds.exactCenterY(), textPaint);
    }

    private void drawSliders(Canvas canvas, int width, int height) {
        float left = dp(34);
        float right = width - dp(34);
        float startY = Math.max(height * 0.57f, activationBounds.bottom + dp(70));
        float available = height - startY - dp(58);
        float spacing = Math.max(dp(88), available / 3.0f);

        drawSlider(
                canvas,
                SLIDER_CHUNK,
                "CHUNK",
                String.valueOf(chunk),
                "256",
                "2048",
                left,
                right,
                startY,
                normalized(chunk, VoiceSettings.MIN_CHUNK, VoiceSettings.MAX_CHUNK)
        );
        drawSlider(
                canvas,
                SLIDER_PITCH,
                "PITCH",
                String.format(Locale.US, "%.2f", pitch),
                "0.5",
                "1.5",
                left,
                right,
                startY + spacing,
                normalized(pitch, VoiceSettings.MIN_PITCH, VoiceSettings.MAX_PITCH)
        );
        drawSlider(
                canvas,
                SLIDER_DRIVE,
                "DISTORSION",
                String.format(Locale.US, "%.2f", drive),
                "1.0",
                "10.0",
                left,
                right,
                startY + spacing * 2.0f,
                normalized(drive, VoiceSettings.MIN_DRIVE, VoiceSettings.MAX_DRIVE)
        );
    }

    private void drawSlider(
            Canvas canvas,
            int id,
            String label,
            String value,
            String min,
            String max,
            float left,
            float right,
            float y,
            float normalized
    ) {
        float labelY = y;
        float trackY = y + dp(34);
        float thumbX = left + (right - left) * normalized;

        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(sp(18));
        textPaint.setColor(Color.WHITE);
        canvas.drawText(label, left, labelY, textPaint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(17));
        textPaint.setColor(Color.rgb(220, 27, 42));
        canvas.drawText(value, thumbX, labelY, textPaint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(4));
        paint.setColor(Color.rgb(75, 75, 78));
        canvas.drawLine(left, trackY, right, trackY, paint);
        paint.setColor(Color.rgb(220, 27, 42));
        canvas.drawLine(left, trackY, thumbX, trackY, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(8, 8, 9));
        canvas.drawCircle(thumbX, trackY, dp(13), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(4));
        paint.setColor(Color.rgb(220, 27, 42));
        canvas.drawCircle(thumbX, trackY, dp(13), paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setTextSize(sp(16));
        textPaint.setColor(Color.rgb(190, 190, 195));
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(min, left, trackY + dp(36), textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(max, right, trackY + dp(36), textPaint);
    }

    private int hitSlider(float x, float y) {
        float width = getWidth();
        float height = getHeight();
        float left = dp(24);
        float right = width - dp(24);
        float startY = Math.max(height * 0.57f, activationBounds.bottom + dp(70));
        float available = height - startY - dp(58);
        float spacing = Math.max(dp(88), available / 3.0f);

        for (int i = 0; i < 3; i++) {
            float trackY = startY + spacing * i + dp(34);
            sliderTouchBounds.set(left, trackY - dp(28), right, trackY + dp(28));
            if (sliderTouchBounds.contains(x, y)) {
                return i;
            }
        }
        return SLIDER_NONE;
    }

    private void updateSlider(int slider, float x) {
        float left = dp(34);
        float right = getWidth() - dp(34);
        float normalized = VoiceSettings.clamp((x - left) / (right - left), 0.0f, 1.0f);

        if (slider == SLIDER_CHUNK) {
            chunk = Math.round(VoiceSettings.MIN_CHUNK
                    + normalized * (VoiceSettings.MAX_CHUNK - VoiceSettings.MIN_CHUNK));
        } else if (slider == SLIDER_PITCH) {
            pitch = VoiceSettings.MIN_PITCH
                    + normalized * (VoiceSettings.MAX_PITCH - VoiceSettings.MIN_PITCH);
        } else if (slider == SLIDER_DRIVE) {
            drive = VoiceSettings.MIN_DRIVE
                    + normalized * (VoiceSettings.MAX_DRIVE - VoiceSettings.MIN_DRIVE);
        }

        if (listener != null) {
            listener.onSettingsChanged(chunk, pitch, drive);
        }
        invalidate();
    }

    private float normalized(float value, float min, float max) {
        return VoiceSettings.clamp((value - min) / (max - min), 0.0f, 1.0f);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
