package com.provoicechanger.whatsapphook;

import android.media.AudioRecord;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class WhatsAppAudioHook implements IXposedHookLoadPackage {
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final float DEFAULT_PITCH = 0.70f;
    private static final float DEFAULT_DRIVE = 4.0f;
    private static final float DEFAULT_GAIN = 0.84f;
    private static final double MODULATION_HZ = 31.0;
    private static final int FALLBACK_SAMPLE_RATE = 48000;

    private double modulationPhase;
    private boolean loggedOnce;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!WHATSAPP_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        hookShortReads();
        hookByteReads();
        hookByteBufferReads();
        XposedBridge.log("ProVoiceChanger: hooks instalados en WhatsApp");
    }

    private void hookShortReads() {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object result = param.getResult();
                if (!(result instanceof Integer) || ((Integer) result) <= 0) {
                    return;
                }

                short[] buffer = (short[]) param.args[0];
                int offset = (Integer) param.args[1];
                int read = (Integer) result;
                processShorts(buffer, offset, read, sampleRate(param.thisObject));
                logFirstBuffer(read, "short[]");
            }
        };

        XposedHelpers.findAndHookMethod(
                AudioRecord.class,
                "read",
                short[].class,
                int.class,
                int.class,
                hook
        );
        XposedHelpers.findAndHookMethod(
                AudioRecord.class,
                "read",
                short[].class,
                int.class,
                int.class,
                int.class,
                hook
        );
    }

    private void hookByteReads() {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object result = param.getResult();
                if (!(result instanceof Integer) || ((Integer) result) <= 1) {
                    return;
                }

                byte[] buffer = (byte[]) param.args[0];
                int offset = (Integer) param.args[1];
                int readBytes = (Integer) result;
                processPcm16Bytes(buffer, offset, readBytes, sampleRate(param.thisObject));
                logFirstBuffer(readBytes, "byte[]");
            }
        };

        XposedHelpers.findAndHookMethod(
                AudioRecord.class,
                "read",
                byte[].class,
                int.class,
                int.class,
                hook
        );
        XposedHelpers.findAndHookMethod(
                AudioRecord.class,
                "read",
                byte[].class,
                int.class,
                int.class,
                int.class,
                hook
        );
    }

    private void hookByteBufferReads() {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object result = param.getResult();
                if (!(result instanceof Integer) || ((Integer) result) <= 1) {
                    return;
                }

                ByteBuffer buffer = (ByteBuffer) param.args[0];
                int readBytes = (Integer) result;
                processByteBuffer(buffer, readBytes, sampleRate(param.thisObject));
                logFirstBuffer(readBytes, "ByteBuffer");
            }
        };

        XposedHelpers.findAndHookMethod(
                AudioRecord.class,
                "read",
                ByteBuffer.class,
                int.class,
                hook
        );
        XposedHelpers.findAndHookMethod(
                AudioRecord.class,
                "read",
                ByteBuffer.class,
                int.class,
                int.class,
                hook
        );
    }

    private void processShorts(short[] buffer, int offset, int samples, int sampleRate) {
        if (buffer == null || samples <= 0 || offset < 0 || offset + samples > buffer.length) {
            return;
        }

        short[] copy = new short[samples];
        System.arraycopy(buffer, offset, copy, 0, samples);

        for (int i = 0; i < samples; i++) {
            int sourceIndex = Math.min(samples - 1, Math.max(0, (int) (i * DEFAULT_PITCH)));
            buffer[offset + i] = processSample(copy[sourceIndex], sampleRate);
        }
    }

    private void processPcm16Bytes(byte[] buffer, int offset, int readBytes, int sampleRate) {
        if (buffer == null || readBytes <= 1 || offset < 0 || offset + readBytes > buffer.length) {
            return;
        }

        int sampleCount = readBytes / 2;
        short[] samples = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int index = offset + i * 2;
            samples[i] = (short) ((buffer[index] & 0xFF) | (buffer[index + 1] << 8));
        }

        for (int i = 0; i < sampleCount; i++) {
            int sourceIndex = Math.min(sampleCount - 1, Math.max(0, (int) (i * DEFAULT_PITCH)));
            short processed = processSample(samples[sourceIndex], sampleRate);
            int index = offset + i * 2;
            buffer[index] = (byte) (processed & 0xFF);
            buffer[index + 1] = (byte) ((processed >> 8) & 0xFF);
        }
    }

    private void processByteBuffer(ByteBuffer buffer, int readBytes, int sampleRate) {
        if (buffer == null || readBytes <= 1) {
            return;
        }

        int sampleCount = readBytes / 2;
        ByteBuffer duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        duplicate.position(0);

        short[] samples = new short[sampleCount];
        for (int i = 0; i < sampleCount && duplicate.remaining() >= 2; i++) {
            samples[i] = duplicate.getShort();
        }

        duplicate.position(0);
        for (int i = 0; i < sampleCount && duplicate.remaining() >= 2; i++) {
            int sourceIndex = Math.min(sampleCount - 1, Math.max(0, (int) (i * DEFAULT_PITCH)));
            duplicate.putShort(processSample(samples[sourceIndex], sampleRate));
        }
    }

    private short processSample(short input, int sampleRate) {
        double phaseStep = 2.0 * Math.PI * MODULATION_HZ / Math.max(1, sampleRate);
        float sample = input / 32768.0f;
        float shaped = (float) Math.tanh(sample * DEFAULT_DRIVE);
        float modulation = (float) (0.72 + (0.28 * Math.sin(modulationPhase)));

        modulationPhase += phaseStep;
        if (modulationPhase >= Math.PI * 2.0) {
            modulationPhase -= Math.PI * 2.0;
        }

        return toPcm16(shaped * modulation * DEFAULT_GAIN);
    }

    private int sampleRate(Object audioRecord) {
        if (audioRecord instanceof AudioRecord) {
            int rate = ((AudioRecord) audioRecord).getSampleRate();
            if (rate > 0) {
                return rate;
            }
        }
        return FALLBACK_SAMPLE_RATE;
    }

    private short toPcm16(float value) {
        float clamped = Math.max(-1.0f, Math.min(1.0f, value));
        return (short) Math.round(clamped * 32767.0f);
    }

    private void logFirstBuffer(int read, String type) {
        if (loggedOnce) {
            return;
        }
        loggedOnce = true;
        XposedBridge.log("ProVoiceChanger: primer buffer WhatsApp procesado: " + type + " read=" + read);
    }
}
