package com.provoicechanger;

import android.util.Log;
import java.io.DataOutputStream;
import java.io.IOException;

public class RootHelper {
    private static final String TAG = "RootHelper";

    public static boolean isRootAvailable() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su -c id");
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    public static boolean requestRoot() {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("/sbin/su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("id\n");
            os.writeBytes("exit\n");
            os.flush();
            if (process.waitFor() == 0) return true;
        } catch (Exception e) {
            try {
                process = Runtime.getRuntime().exec("su");
                os = new DataOutputStream(process.getOutputStream());
                os.writeBytes("id\n");
                os.writeBytes("exit\n");
                os.flush();
                return process.waitFor() == 0;
            } catch (Exception e2) {
                Log.e(TAG, "Root request failed", e2);
                return false;
            }
        } finally {
            try { if (os != null) os.close(); } catch (IOException ignored) {}
            if (process != null) process.destroy();
        }
        return false;
    }

    public static void runSystemAudioFix() {
        // Solo ajustes basicos de latencia, sin inyecciones agresivas
        executeRootCommand("setprop persist.audio.lowlatency 1");
        executeRootCommand("setprop audio.offload.disable 1");
        Log.i(TAG, "System Audio Fix applied (Basic)");
    }

    public static void restartAudioService() {
        executeRootCommand("killall audioserver");
        executeRootCommand("killall android.hardware.audio@2.0-service");
    }

    public static void connectVirtualCable(boolean connect) {
        // Deshabilitado por peticion de usuario (volver a version estable)
    }

    public static boolean executeRootCommand(String command) {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            return process.waitFor() == 0;
        } catch (Exception e) {
            Log.e(TAG, "Command failed: " + command, e);
            return false;
        } finally {
            try { if (os != null) os.close(); } catch (IOException ignored) {}
            if (process != null) process.destroy();
        }
    }
}
