package com.provoicechanger;

import android.content.Context;
import android.content.SharedPreferences;

final class VoiceSettings {
    static final String PREFS_NAME = "pro_voice_changer_settings";
    static final String KEY_CHUNK = "chunk";
    static final String KEY_PITCH = "pitch";
    static final String KEY_DRIVE = "drive";
    static final String KEY_ACTIVE = "active";
    static final String KEY_INPUT_DEVICE_ID = "input_device_id";
    static final String KEY_OUTPUT_DEVICE_ID = "output_device_id";
    static final String KEY_ROOT_MODE = "root_mode";

    static final int NO_DEVICE = -1;
    static final int MIN_CHUNK = 256;
    static final int MAX_CHUNK = 2048;
    static final int DEFAULT_CHUNK = 900;
    static final float MIN_PITCH = 0.5f;
    static final float MAX_PITCH = 1.5f;
    static final float DEFAULT_PITCH = 0.70f;
    static final float MIN_DRIVE = 1.0f;
    static final float MAX_DRIVE = 10.0f;
    static final float DEFAULT_DRIVE = 4.0f;

    private VoiceSettings() {
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static int getChunk(SharedPreferences preferences) {
        return clamp(
                preferences.getInt(KEY_CHUNK, DEFAULT_CHUNK),
                MIN_CHUNK,
                MAX_CHUNK
        );
    }

    static float getPitch(SharedPreferences preferences) {
        return clamp(
                preferences.getFloat(KEY_PITCH, DEFAULT_PITCH),
                MIN_PITCH,
                MAX_PITCH
        );
    }

    static float getDrive(SharedPreferences preferences) {
        return clamp(
                preferences.getFloat(KEY_DRIVE, DEFAULT_DRIVE),
                MIN_DRIVE,
                MAX_DRIVE
        );
    }

    static void saveAudioValues(
            SharedPreferences preferences,
            int chunk,
            float pitch,
            float drive
    ) {
        preferences.edit()
                .putInt(KEY_CHUNK, clamp(chunk, MIN_CHUNK, MAX_CHUNK))
                .putFloat(KEY_PITCH, clamp(pitch, MIN_PITCH, MAX_PITCH))
                .putFloat(KEY_DRIVE, clamp(drive, MIN_DRIVE, MAX_DRIVE))
                .apply();
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
