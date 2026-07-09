package com.provoicechanger;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class AudioService extends Service {
    public static final String ACTION_START_PROCESSING = "com.provoicechanger.action.START_PROCESSING";
    public static final String ACTION_STOP_PROCESSING = "com.provoicechanger.action.STOP_PROCESSING";
    public static final String ACTION_STOP_SERVICE = "com.provoicechanger.action.STOP_SERVICE";
    public static final String ACTION_UPDATE_DEVICES = "com.provoicechanger.action.UPDATE_DEVICES";

    private static final String CHANNEL_ID = "pro_voice_changer_audio";
    private static final int NOTIFICATION_ID = 11;
    private static final int SAMPLE_RATE = 44100; 
    private static final double MODULATION_HZ = 31.0;
    private static final long DOUBLE_TAP_MS = 320L;

    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean processing;
    private volatile int chunkSize = VoiceSettings.DEFAULT_CHUNK;
    private volatile float pitchFactor = VoiceSettings.DEFAULT_PITCH;
    private volatile float drive = VoiceSettings.DEFAULT_DRIVE;

    private AudioRecord recorder;
    private AudioTrack player;
    private Thread audioThread;
    private double modulationPhase;
    private boolean shuttingDown;
    private WindowManager windowManager;
    private WindowManager.LayoutParams floatingParams;
    private FloatingButtonView floatingButton;
    private WindowManager.LayoutParams panelParams;
    private LinearLayout floatingPanel;
    private TextView panelStatus;
    private TextView panelChunkValue;
    private TextView panelPitchValue;
    private TextView panelDriveValue;
    private Button panelToggleButton;
    private Runnable pendingSingleTap;
    private long lastTapAt;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = VoiceSettings.preferences(this);
        loadSettings();
        registerPreferenceListener();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("ProVoice Activo"));
        createFloatingButtonIfAllowed();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP_SERVICE.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_STOP_PROCESSING.equals(action)) { stopAudioProcessing(); updateFloatingPanelState(); return START_STICKY; }
        if (ACTION_UPDATE_DEVICES.equals(action)) { loadSettings(); createFloatingButtonIfAllowed(); updateFloatingPanelState(); return START_STICKY; }
        if (ACTION_START_PROCESSING.equals(action)) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return START_NOT_STICKY;
            startAudioProcessing(); return START_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        shuttingDown = true;
        stopAudioProcessing();
        removeFloatingPanel();
        removeFloatingButton();
        if (preferences != null && preferenceListener != null) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        if (preferences != null) preferences.edit().putBoolean(VoiceSettings.KEY_ACTIVE, false).apply();
        super.onDestroy();
    }

    private synchronized void startAudioProcessing() {
        if (processing) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("Error permisos");
            return;
        }
        loadSettings();

        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBufferSize, 4096);

        // Usar VOICE_COMMUNICATION para activar la cancelación de eco por hardware si está disponible
        recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        player = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED || player.getState() != AudioTrack.STATE_INITIALIZED) {
            releaseAudioObjects();
            updateNotification("Error Hardware");
            return;
        }

        try {
            recorder.startRecording();
            player.play();
        } catch (Exception e) {
            releaseAudioObjects();
            return;
        }

        processing = true;
        writePtr = 0;
        readPtr = 0;
        preferences.edit().putBoolean(VoiceSettings.KEY_ACTIVE, true).apply();
        audioThread = new Thread(this::processAudioLoop, "ProVoice-Audio");
        audioThread.start();
        updateNotification("Efecto ON");
        updateFloatingButtonState();
        updateFloatingPanelState();
    }

    private final float[] ringBuffer = new float[16384];
    private int writePtr = 0;
    private float readPtr = 0;

    private void processAudioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        short[] input = new short[512]; // Buffer más pequeño para reducir latencia
        short[] output = new short[512];
        
        while (processing) {
            int read = recorder.read(input, 0, input.length);
            if (read > 0) {
                applyDemonEffect(input, output, read);
                player.write(output, 0, read);
            }
        }
    }

    private void applyDemonEffect(short[] in, short[] out, int len) {
        float p = pitchFactor;
        float d = drive;
        double phaseStep = 2.0 * Math.PI * MODULATION_HZ / SAMPLE_RATE;
        
        // Escribir al buffer circular
        for (int i = 0; i < len; i++) {
            ringBuffer[writePtr] = in[i] / 32768.0f;
            writePtr = (writePtr + 1) % ringBuffer.length;
        }

        // Sincronización agresiva para latencia mínima
        // Mantener el puntero de lectura justo detrás del de escritura (aprox 2x el tamaño del bloque)
        float targetDistance = len * 1.5f; 
        readPtr = (writePtr - targetDistance + ringBuffer.length) % ringBuffer.length;

        // Leer con pitch shifting
        for (int k = 0; k < len; k++) {
            int i0 = (int) readPtr;
            int i1 = (i0 + 1) % ringBuffer.length;
            float frac = readPtr - (float)i0;
            
            float s = ringBuffer[i0] + frac * (ringBuffer[i1] - ringBuffer[i0]);
            
            readPtr += p;
            if (readPtr >= ringBuffer.length) readPtr -= ringBuffer.length;

            // Efecto de distorsión y modulación
            float shaped = (float) Math.tanh(s * d);
            float m = (float) (0.85 + (0.15 * Math.sin(modulationPhase)));
            
            // Salida con limitador y volumen controlado para evitar retroalimentación
            float outVal = shaped * m * 0.6f;
            out[k] = (short) (outVal * 32767.0f);
            
            modulationPhase += phaseStep;
            if (modulationPhase >= Math.PI * 2.0) modulationPhase -= Math.PI * 2.0;
        }
    }

    private synchronized void stopAudioProcessing() {
        if (!processing) return;
        processing = false;
        if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) {} }
        if (player != null) { try { player.pause(); player.flush(); } catch (Exception ignored) {} }
        if (audioThread != null) { try { audioThread.join(300); } catch (Exception ignored) {} }
        audioThread = null;
        releaseAudioObjects();
        updateNotification("Efecto OFF");
        updateFloatingButtonState();
        updateFloatingPanelState();
    }

    private void loadSettings() {
        chunkSize = VoiceSettings.getChunk(preferences);
        pitchFactor = VoiceSettings.getPitch(preferences);
        drive = VoiceSettings.getDrive(preferences);
    }

    private void registerPreferenceListener() {
        preferenceListener = (p, k) -> { if (VoiceSettings.KEY_CHUNK.equals(k) || VoiceSettings.KEY_PITCH.equals(k) || VoiceSettings.KEY_DRIVE.equals(k)) { loadSettings(); } };
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    private void releaseAudioObjects() {
        if (recorder != null) { try { recorder.release(); } catch (Exception ignored) {} recorder = null; }
        if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; }
    }

    private void createFloatingButtonIfAllowed() {
        if (!canDrawOverlays() || floatingButton != null || shuttingDown) return;
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        floatingButton = new FloatingButtonView(getApplicationContext());
        floatingButton.setActive(processing);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        floatingParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        floatingParams.gravity = Gravity.TOP | Gravity.START;
        floatingParams.x = dp(18); floatingParams.y = dp(120);
        installFloatingTouchHandler();
        try { windowManager.addView(floatingButton, floatingParams); } catch (Exception e) { floatingButton = null; }
    }

    private void installFloatingTouchHandler() {
        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            private int ix, iy; private float itx, ity; private long ts; private boolean dr;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: ix = floatingParams.x; iy = floatingParams.y; itx = e.getRawX(); ity = e.getRawY(); ts = System.currentTimeMillis(); dr = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(e.getRawX() - itx); int dy = Math.round(e.getRawY() - ity);
                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) dr = true;
                        floatingParams.x = ix + dx; floatingParams.y = iy + dy;
                        if (windowManager != null && floatingButton != null) windowManager.updateViewLayout(floatingButton, floatingParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dr) { if (System.currentTimeMillis() - ts >= 650L) openMainActivity(); else handleFloatingButtonTap(); }
                        return true;
                } return false;
            }
        });
    }

    private void handleFloatingButtonTap() {
        long now = System.currentTimeMillis();
        if (now - lastTapAt <= DOUBLE_TAP_MS) { lastTapAt = 0L; if (pendingSingleTap != null) { mainHandler.removeCallbacks(pendingSingleTap); pendingSingleTap = null; } toggleFloatingPanel(); return; }
        lastTapAt = now; pendingSingleTap = () -> { pendingSingleTap = null; if (processing) stopAudioProcessing(); else startAudioProcessing(); };
        mainHandler.postDelayed(pendingSingleTap, DOUBLE_TAP_MS);
    }

    private void toggleFloatingPanel() { if (floatingPanel == null) createFloatingPanelIfAllowed(); else removeFloatingPanel(); }

    private void createFloatingPanelIfAllowed() {
        if (!canDrawOverlays() || floatingPanel != null || shuttingDown) return;
        if (windowManager == null) windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        floatingPanel = new LinearLayout(getApplicationContext());
        floatingPanel.setOrientation(LinearLayout.VERTICAL);
        floatingPanel.setPadding(dp(12), dp(10), dp(12), dp(12));
        floatingPanel.setBackgroundResource(R.drawable.floating_panel_background);

        TextView handle = makeText("ProVoice", 15, true, 0xFFFFFFFF);
        handle.setGravity(Gravity.CENTER);
        floatingPanel.addView(handle, new LinearLayout.LayoutParams(-1, dp(34)));

        panelStatus = makeText("", 12, true, 0xFFDFFF00);
        panelStatus.setGravity(Gravity.CENTER);
        floatingPanel.addView(panelStatus, new LinearLayout.LayoutParams(-1, -2));

        panelToggleButton = new Button(getApplicationContext());
        panelToggleButton.setAllCaps(false); panelToggleButton.setTextColor(0xFFFFFFFF); panelToggleButton.setTextSize(13);
        panelToggleButton.setBackgroundResource(R.drawable.button_primary_background);
        panelToggleButton.setOnClickListener(v -> { if (processing) stopAudioProcessing(); else startAudioProcessing(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40)); lp.topMargin = dp(10);
        floatingPanel.addView(panelToggleButton, lp);

        panelChunkValue = addSlider("CHUNK", VoiceSettings.MAX_CHUNK - VoiceSettings.MIN_CHUNK, chunkSize - VoiceSettings.MIN_CHUNK, p -> { chunkSize = VoiceSettings.MIN_CHUNK + p; VoiceSettings.saveAudioValues(preferences, chunkSize, pitchFactor, drive); });
        panelPitchValue = addSlider("PITCH", 1000, Math.round((pitchFactor - VoiceSettings.MIN_PITCH) / (VoiceSettings.MAX_PITCH - VoiceSettings.MIN_PITCH) * 1000f), p -> { pitchFactor = VoiceSettings.MIN_PITCH + (p / 1000f) * (VoiceSettings.MAX_PITCH - VoiceSettings.MIN_PITCH); VoiceSettings.saveAudioValues(preferences, chunkSize, pitchFactor, drive); });
        panelDriveValue = addSlider("DISTORSION", 1000, Math.round((drive - VoiceSettings.MIN_DRIVE) / (VoiceSettings.MAX_DRIVE - VoiceSettings.MIN_DRIVE) * 1000f), p -> { drive = VoiceSettings.MIN_DRIVE + (p / 1000f) * (VoiceSettings.MAX_DRIVE - VoiceSettings.MIN_DRIVE); VoiceSettings.saveAudioValues(preferences, chunkSize, pitchFactor, drive); });

        LinearLayout row = new LinearLayout(getApplicationContext());
        Button open = makePanelButton("Abrir", R.drawable.button_neutral_background);
        Button close = makePanelButton("Cerrar", R.drawable.button_danger_background);
        open.setOnClickListener(v -> openMainActivity()); close.setOnClickListener(v -> removeFloatingPanel());
        row.addView(open, new LinearLayout.LayoutParams(0, dp(38), 1f));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(38), 1f); clp.leftMargin = dp(8);
        row.addView(close, clp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2); rlp.topMargin = dp(12);
        floatingPanel.addView(row, rlp);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        panelParams = new WindowManager.LayoutParams(dp(260), WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = floatingParams != null ? floatingParams.x + dp(64) : dp(80);
        panelParams.y = floatingParams != null ? floatingParams.y : dp(100);
        installPanelDragHandler(handle);
        updateFloatingPanelState();
        try { windowManager.addView(floatingPanel, panelParams); } catch (Exception e) { floatingPanel = null; }
    }

    private TextView addSlider(String label, int max, int progress, SliderCallback cb) {
        LinearLayout row = new LinearLayout(getApplicationContext());
        TextView lv = makeText(label, 11, true, 0xFFFFFFFF);
        TextView vv = makeText("", 11, true, 0xFFCF1824); vv.setGravity(Gravity.RIGHT);
        row.addView(lv, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(vv, new LinearLayout.LayoutParams(dp(70), -2));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2); rlp.topMargin = dp(10);
        floatingPanel.addView(row, rlp);
        SeekBar sb = new SeekBar(getApplicationContext()); sb.setMax(max); sb.setProgress(progress);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) { if (u) { cb.onChanged(p); updateFloatingPanelState(); } }
            @Override public void onStartTrackingTouch(SeekBar sb) {} @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        floatingPanel.addView(sb, new LinearLayout.LayoutParams(-1, dp(32)));
        return vv;
    }

    private Button makePanelButton(String l, int bg) { Button b = new Button(getApplicationContext()); b.setText(l); b.setAllCaps(false); b.setTextSize(12); b.setTextColor(0xFFFFFFFF); b.setBackgroundResource(bg); return b; }
    private TextView makeText(String t, int s, boolean b, int c) { TextView tv = new TextView(getApplicationContext()); tv.setText(t); tv.setTextSize(s); tv.setTextColor(c); if (b) tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return tv; }
    private void installPanelDragHandler(View h) {
        h.setOnTouchListener(new View.OnTouchListener() {
            private int ix, iy; private float itx, ity;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: ix = panelParams.x; iy = panelParams.y; itx = e.getRawX(); ity = e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE: panelParams.x = ix + Math.round(e.getRawX() - itx); panelParams.y = iy + Math.round(e.getRawY() - ity); if (windowManager != null && floatingPanel != null) windowManager.updateViewLayout(floatingPanel, panelParams); return true;
                } return false;
            }
        });
    }

    private void updateFloatingButtonState() {
        final FloatingButtonView fb = floatingButton;
        if (fb != null) {
            fb.post(() -> fb.setActive(processing));
        }
    }

    private void updateFloatingPanelState() {
        if (floatingPanel == null) return;
        final boolean isProc = processing;
        final int curChunk = chunkSize;
        final float curPitch = pitchFactor;
        final float curDrive = drive;

        mainHandler.post(() -> {
            if (floatingPanel == null) return;
            if (panelStatus != null) {
                panelStatus.setText(isProc ? "Activo" : "Pausado");
                panelStatus.setTextColor(isProc ? 0xFFDFFF00 : 0xFFCF1824);
            }
            if (panelToggleButton != null) {
                panelToggleButton.setText(isProc ? "Pausar" : "Activar");
                panelToggleButton.setBackgroundResource(isProc ? R.drawable.button_danger_background : R.drawable.button_primary_background);
            }
            if (panelChunkValue != null) panelChunkValue.setText(String.valueOf(curChunk));
            if (panelPitchValue != null) panelPitchValue.setText(String.format(java.util.Locale.US, "%.2f", curPitch));
            if (panelDriveValue != null) panelDriveValue.setText(String.format(java.util.Locale.US, "%.2f", curDrive));
        });
    }

    private void removeFloatingButton() { if (windowManager != null && floatingButton != null) try { windowManager.removeView(floatingButton); } catch (Exception ignored) {} floatingButton = null; }
    private void removeFloatingPanel() { if (windowManager != null && floatingPanel != null) try { windowManager.removeView(floatingPanel); } catch (Exception ignored) {} floatingPanel = null; }
    private void openMainActivity() { Intent i = new Intent(this, MainActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP); startActivity(i); }
    private void createNotificationChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { NotificationChannel c = new NotificationChannel(CHANNEL_ID, "ProVoice", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); } }
    private Notification buildNotification(String t) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle("ProVoice").setContentText(t).setSmallIcon(R.drawable.ic_stat_voice).setContentIntent(pi).setOngoing(true).build();
    }
    private void updateNotification(String t) { if (!shuttingDown) ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, buildNotification(t)); }
    private boolean hasMicrophonePermission() { return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }
    private boolean canDrawOverlays() { return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private interface SliderCallback { void onChanged(int p); }
}
