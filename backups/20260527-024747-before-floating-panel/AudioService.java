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
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;

public class AudioService extends Service {
    public static final String ACTION_START_PROCESSING =
            "com.provoicechanger.action.START_PROCESSING";
    public static final String ACTION_STOP_PROCESSING =
            "com.provoicechanger.action.STOP_PROCESSING";
    public static final String ACTION_STOP_SERVICE =
            "com.provoicechanger.action.STOP_SERVICE";
    public static final String ACTION_UPDATE_DEVICES =
            "com.provoicechanger.action.UPDATE_DEVICES";

    private static final String CHANNEL_ID = "pro_voice_changer_audio";
    private static final int NOTIFICATION_ID = 11;
    private static final int SAMPLE_RATE = 44100;
    private static final double MODULATION_HZ = 31.0;

    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    private volatile boolean processing;
    private volatile int chunkSize = VoiceSettings.DEFAULT_CHUNK;
    private volatile float pitchFactor = VoiceSettings.DEFAULT_PITCH;
    private volatile float drive = VoiceSettings.DEFAULT_DRIVE;
    private volatile int inputDeviceId = VoiceSettings.NO_DEVICE;
    private volatile int outputDeviceId = VoiceSettings.NO_DEVICE;

    private AudioRecord recorder;
    private AudioTrack player;
    private Thread audioThread;
    private int bufferSizeBytes;
    private double modulationPhase;
    private boolean shuttingDown;
    private WindowManager windowManager;
    private WindowManager.LayoutParams floatingParams;
    private FloatingButtonView floatingButton;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = VoiceSettings.preferences(this);
        loadSettings();
        registerPreferenceListener();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_ready)));
        createFloatingButtonIfAllowed();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP_SERVICE.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_STOP_PROCESSING.equals(action)) {
            stopAudioProcessing();
            return START_STICKY;
        }

        if (ACTION_UPDATE_DEVICES.equals(action)) {
            loadSettings();
            applyPreferredDevices();
            createFloatingButtonIfAllowed();
            return START_STICKY;
        }

        if (ACTION_START_PROCESSING.equals(action)) {
            createFloatingButtonIfAllowed();
            startAudioProcessing();
            return START_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        shuttingDown = true;
        stopAudioProcessing();
        removeFloatingButton();
        if (preferences != null && preferenceListener != null) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        }
        if (preferences != null) {
            preferences.edit().putBoolean(VoiceSettings.KEY_ACTIVE, false).apply();
        }
        super.onDestroy();
    }

    private synchronized void startAudioProcessing() {
        if (processing) {
            return;
        }

        if (!hasMicrophonePermission()) {
            updateNotification(getString(R.string.notification_missing_microphone));
            preferences.edit().putBoolean(VoiceSettings.KEY_ACTIVE, false).apply();
            stopSelf();
            return;
        }

        loadSettings();

        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBuffer <= 0) {
            minBuffer = VoiceSettings.MAX_CHUNK * 2;
        }
        bufferSizeBytes = Math.max(minBuffer, VoiceSettings.MAX_CHUNK * 2);

        recorder = createRecorder(bufferSizeBytes);
        player = createPlayer(bufferSizeBytes);
        applyPreferredDevices();

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED
                || player.getState() != AudioTrack.STATE_INITIALIZED) {
            releaseAudioObjects();
            updateNotification(getString(R.string.notification_audio_error));
            preferences.edit().putBoolean(VoiceSettings.KEY_ACTIVE, false).apply();
            return;
        }

        try {
            recorder.startRecording();
            player.play();
        } catch (IllegalStateException exception) {
            releaseAudioObjects();
            updateNotification(getString(R.string.notification_audio_error));
            preferences.edit().putBoolean(VoiceSettings.KEY_ACTIVE, false).apply();
            return;
        }

        processing = true;
        preferences.edit().putBoolean(VoiceSettings.KEY_ACTIVE, true).apply();
        audioThread = new Thread(this::processAudioLoop, "ProVoiceChanger-Audio");
        audioThread.start();
        updateNotification(getString(R.string.notification_processing));
        updateFloatingButtonState();
    }

    private synchronized void stopAudioProcessing() {
        if (!processing && audioThread == null && recorder == null && player == null) {
            return;
        }

        processing = false;

        if (recorder != null) {
            try {
                recorder.stop();
            } catch (IllegalStateException ignored) {
                // Recorder was not actively recording.
            }
        }

        if (player != null) {
            try {
                player.pause();
                player.flush();
            } catch (IllegalStateException ignored) {
                // Player was already stopped or not initialized.
            }
        }

        if (audioThread != null && Thread.currentThread() != audioThread) {
            try {
                audioThread.join(400);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        audioThread = null;
        releaseAudioObjects();
        updateNotification(getString(R.string.notification_paused));
        updateFloatingButtonState();
    }

    private void processAudioLoop() {
        int shortBufferSize = Math.max(VoiceSettings.MAX_CHUNK, bufferSizeBytes / 2);
        short[] input = new short[shortBufferSize];
        short[] output = new short[shortBufferSize];

        while (processing && !Thread.currentThread().isInterrupted()) {
            int requested = VoiceSettings.clamp(chunkSize, VoiceSettings.MIN_CHUNK, input.length);
            int read = recorder.read(input, 0, requested);
            if (read > 0) {
                applyDemonEffect(input, output, read);
                player.write(output, 0, read);
            }
        }
    }

    private void applyDemonEffect(short[] input, short[] output, int length) {
        float currentPitch = pitchFactor;
        float currentDrive = drive;
        double phaseStep = 2.0 * Math.PI * MODULATION_HZ / SAMPLE_RATE;

        for (int i = 0; i < length; i++) {
            int sourceIndex = Math.min(length - 1, Math.max(0, (int) (i * currentPitch)));
            float sample = input[sourceIndex] / 32768.0f;
            float shaped = (float) Math.tanh(sample * currentDrive);
            float modulation = (float) (0.72 + (0.28 * Math.sin(modulationPhase)));
            output[i] = toPcm16(shaped * modulation * 0.84f);

            modulationPhase += phaseStep;
            if (modulationPhase >= Math.PI * 2.0) {
                modulationPhase -= Math.PI * 2.0;
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private AudioRecord createRecorder(int sizeBytes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();

            return new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(sizeBytes)
                    .build();
        }

        return new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                sizeBytes
        );
    }

    private AudioTrack createPlayer(int sizeBytes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();

            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            return new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(sizeBytes)
                    .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                    .build();
        }

        return new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                sizeBytes,
                AudioTrack.MODE_STREAM
        );
    }

    private synchronized void applyPreferredDevices() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (recorder != null && inputDeviceId != VoiceSettings.NO_DEVICE) {
            AudioDeviceInfo inputDevice = findDevice(
                    audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS),
                    inputDeviceId
            );
            if (inputDevice != null) {
                recorder.setPreferredDevice(inputDevice);
            }
        }

        if (player != null && outputDeviceId != VoiceSettings.NO_DEVICE) {
            AudioDeviceInfo outputDevice = findDevice(
                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS),
                    outputDeviceId
            );
            if (outputDevice != null) {
                player.setPreferredDevice(outputDevice);
            }
        }
    }

    private AudioDeviceInfo findDevice(AudioDeviceInfo[] devices, int id) {
        for (AudioDeviceInfo device : devices) {
            if (device.getId() == id) {
                return device;
            }
        }
        return null;
    }

    private void loadSettings() {
        if (preferences == null) {
            return;
        }

        chunkSize = VoiceSettings.getChunk(preferences);
        pitchFactor = VoiceSettings.getPitch(preferences);
        drive = VoiceSettings.getDrive(preferences);
        inputDeviceId = preferences.getInt(
                VoiceSettings.KEY_INPUT_DEVICE_ID,
                VoiceSettings.NO_DEVICE
        );
        outputDeviceId = preferences.getInt(
                VoiceSettings.KEY_OUTPUT_DEVICE_ID,
                VoiceSettings.NO_DEVICE
        );
    }

    private void registerPreferenceListener() {
        preferenceListener = (sharedPreferences, key) -> {
            if (VoiceSettings.KEY_CHUNK.equals(key)
                    || VoiceSettings.KEY_PITCH.equals(key)
                    || VoiceSettings.KEY_DRIVE.equals(key)
                    || VoiceSettings.KEY_INPUT_DEVICE_ID.equals(key)
                    || VoiceSettings.KEY_OUTPUT_DEVICE_ID.equals(key)) {
                loadSettings();
                applyPreferredDevices();
            }
        };
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    private void releaseAudioObjects() {
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }

        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void createFloatingButtonIfAllowed() {
        if (!canDrawOverlays() || floatingButton != null || shuttingDown) {
            return;
        }

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        floatingButton = new FloatingButtonView(getApplicationContext());
        floatingButton.setActive(processing);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        floatingParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        floatingParams.gravity = Gravity.TOP | Gravity.START;
        floatingParams.x = dp(18);
        floatingParams.y = dp(120);

        installFloatingTouchHandler();

        try {
            windowManager.addView(floatingButton, floatingParams);
        } catch (RuntimeException exception) {
            floatingButton = null;
            floatingParams = null;
        }
    }

    private void installFloatingTouchHandler() {
        floatingButton.setOnTouchListener(new android.view.View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long touchStartedAt;
            private boolean dragged;

            @Override
            public boolean onTouch(android.view.View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = floatingParams.x;
                        initialY = floatingParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartedAt = System.currentTimeMillis();
                        dragged = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int deltaX = Math.round(event.getRawX() - initialTouchX);
                        int deltaY = Math.round(event.getRawY() - initialTouchY);
                        if (Math.abs(deltaX) > dp(4) || Math.abs(deltaY) > dp(4)) {
                            dragged = true;
                        }
                        floatingParams.x = initialX + deltaX;
                        floatingParams.y = initialY + deltaY;
                        if (windowManager != null && floatingButton != null) {
                            windowManager.updateViewLayout(floatingButton, floatingParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!dragged) {
                            long duration = System.currentTimeMillis() - touchStartedAt;
                            if (duration >= 650L) {
                                openMainActivity();
                            } else if (processing) {
                                stopAudioProcessing();
                            } else {
                                startAudioProcessing();
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void updateFloatingButtonState() {
        if (floatingButton == null) {
            return;
        }
        floatingButton.post(() -> floatingButton.setActive(processing));
    }

    private void removeFloatingButton() {
        if (windowManager != null && floatingButton != null) {
            try {
                windowManager.removeView(floatingButton);
            } catch (IllegalArgumentException ignored) {
                // The overlay was already detached.
            }
        }
        floatingButton = null;
        floatingParams = null;
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String contentText) {
        Intent launchIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle(getString(R.string.app_name))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_stat_voice)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String contentText) {
        if (shuttingDown) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(contentText));
    }

    private boolean hasMicrophonePermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private short toPcm16(float value) {
        float clamped = Math.max(-1.0f, Math.min(1.0f, value));
        return (short) Math.round(clamped * 32767.0f);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
