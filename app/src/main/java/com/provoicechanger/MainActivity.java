package com.provoicechanger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements DemonControlView.Listener {
    private static final int REQUEST_RECORD_AUDIO = 1001;

    private SharedPreferences preferences;
    private DemonControlView controlView;
    private DrawerLayout drawerLayout;
    private View drawerContent;

    private Switch switchActivation;
    private Switch switchRoot;
    private TextView textActivation;
    private TextView textRoot;
    private TextView textRecord;
    private TextView iconRecord;
    private TextView textAppVersion;

    private boolean pendingActivation;
    private boolean recording;

    private final android.content.BroadcastReceiver recordingReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.provoicechanger.RECORDING_STATUS".equals(intent.getAction())) {
                recording = intent.getBooleanExtra("recording", false);
                controlView.setRecording(recording);
                updateDrawerState();
                String filePath = intent.getStringExtra("file_path");
                if (filePath != null) {
                    shareAudioFile(filePath);
                } else {
                    Toast.makeText(context, recording ? "Grabando..." : "Grabación finalizada", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(0xFF000000);
            getWindow().setNavigationBarColor(0xFF000000);
        }

        preferences = VoiceSettings.preferences(this);

        drawerLayout = new DrawerLayout(this);

        controlView = new DemonControlView(this);
        controlView.setListener(this);
        controlView.setValues(
                VoiceSettings.getChunk(preferences),
                VoiceSettings.getPitch(preferences),
                VoiceSettings.getDrive(preferences),
                preferences.getBoolean(VoiceSettings.KEY_ACTIVE, false)
        );
        controlView.setOverlayReady(canDrawOverlays());
        controlView.setRootActive(preferences.getBoolean(VoiceSettings.KEY_ROOT_MODE, false));

        drawerContent = getLayoutInflater().inflate(R.layout.nav_drawer_content, drawerLayout, false);

        DrawerLayout.LayoutParams drawerParams = new DrawerLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().density * 280),
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        drawerParams.gravity = GravityCompat.START;
        drawerContent.setLayoutParams(drawerParams);

        setupDrawerViews();

        drawerLayout.addView(controlView);
        drawerLayout.addView(drawerContent);
        setContentView(drawerLayout);

        ContextCompat.registerReceiver(
                this,
                recordingReceiver,
                new android.content.IntentFilter("com.provoicechanger.RECORDING_STATUS"),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    private void setupDrawerViews() {
        switchActivation = drawerContent.findViewById(R.id.switch_menu_activation);
        switchRoot = drawerContent.findViewById(R.id.switch_menu_root);
        textActivation = drawerContent.findViewById(R.id.text_menu_activation);
        textRoot = drawerContent.findViewById(R.id.text_menu_root);
        textRecord = drawerContent.findViewById(R.id.text_menu_record);
        iconRecord = drawerContent.findViewById(R.id.icon_menu_record);
        textAppVersion = drawerContent.findViewById(R.id.text_app_version);

        String versionName = "1.0.2";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        if (textAppVersion != null) {
            textAppVersion.setText("ProVoiceChanger v" + versionName);
        }

        View itemActivation = drawerContent.findViewById(R.id.menu_item_activation);
        if (itemActivation != null) {
            itemActivation.setOnClickListener(v -> {
                boolean active = preferences.getBoolean(VoiceSettings.KEY_ACTIVE, false);
                onActivationRequested(!active);
            });
        }

        View itemInput = drawerContent.findViewById(R.id.menu_item_input);
        if (itemInput != null) {
            itemInput.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                onInputRequested();
            });
        }

        View itemOutput = drawerContent.findViewById(R.id.menu_item_output);
        if (itemOutput != null) {
            itemOutput.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                onOutputRequested();
            });
        }

        View itemOverlay = drawerContent.findViewById(R.id.menu_item_overlay);
        if (itemOverlay != null) {
            itemOverlay.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                onOverlayPermissionRequested();
            });
        }

        View itemRoot = drawerContent.findViewById(R.id.menu_item_root);
        if (itemRoot != null) {
            itemRoot.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                onRootRequested();
            });
        }

        View itemRecord = drawerContent.findViewById(R.id.menu_item_record);
        if (itemRecord != null) {
            itemRecord.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                onRecordRequested();
            });
        }

        updateDrawerState();
    }

    private void updateDrawerState() {
        boolean active = preferences.getBoolean(VoiceSettings.KEY_ACTIVE, false);
        boolean root = preferences.getBoolean(VoiceSettings.KEY_ROOT_MODE, false);

        if (switchActivation != null) switchActivation.setChecked(active);
        if (textActivation != null) textActivation.setText(active ? "Efecto: Activado" : "Efecto: Inactivo");

        if (switchRoot != null) switchRoot.setChecked(root);
        if (textRoot != null) textRoot.setText(root ? "Modo Root: Activado" : "Modo Root (RT)");

        if (textRecord != null) textRecord.setText(recording ? "Detener Grabación" : "Grabar Audio (REC)");
        if (iconRecord != null) iconRecord.setText(recording ? "⏹️" : "🔴");
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(recordingReceiver);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        controlView.setValues(
                VoiceSettings.getChunk(preferences),
                VoiceSettings.getPitch(preferences),
                VoiceSettings.getDrive(preferences),
                preferences.getBoolean(VoiceSettings.KEY_ACTIVE, false)
        );
        controlView.setOverlayReady(canDrawOverlays());
        controlView.setRootActive(preferences.getBoolean(VoiceSettings.KEY_ROOT_MODE, false));
        updateDrawerState();

        if (pendingActivation && hasMicrophonePermission() && canDrawOverlays()) {
            pendingActivation = false;
            activateVoiceChanger();
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onMenuRequested() {
        if (drawerLayout != null) {
            updateDrawerState();
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    @Override
    public void onActivationRequested(boolean activate) {
        if (activate) {
            activateVoiceChanger();
        } else {
            deactivateVoiceChanger();
        }
        updateDrawerState();
    }

    @Override
    public void onSettingsChanged(int chunk, float pitch, float drive) {
        VoiceSettings.saveAudioValues(preferences, chunk, pitch, drive);
    }

    @Override
    public void onInputRequested() {
        showDeviceDialog(true);
    }

    @Override
    public void onOutputRequested() {
        showDeviceDialog(false);
    }

    @Override
    public void onOverlayPermissionRequested() {
        requestOverlayPermission();
    }

    @Override
    public void onRootRequested() {
        boolean currentRoot = preferences.getBoolean(VoiceSettings.KEY_ROOT_MODE, false);
        if (currentRoot) {
            preferences.edit().putBoolean(VoiceSettings.KEY_ROOT_MODE, false).apply();
            controlView.setRootActive(false);
            updateDrawerState();
            Toast.makeText(this, "Modo Root desactivado", Toast.LENGTH_SHORT).show();
        } else {
            new Thread(() -> {
                boolean success = RootHelper.requestRoot();
                runOnUiThread(() -> {
                    if (success) {
                        preferences.edit().putBoolean(VoiceSettings.KEY_ROOT_MODE, true).apply();
                        controlView.setRootActive(true);
                        updateDrawerState();
                        RootHelper.runSystemAudioFix();
                        Toast.makeText(this, "Modo Root activado (Beta)", Toast.LENGTH_LONG).show();
                        showRootWarning();
                    } else {
                        Toast.makeText(this, "No se pudo obtener acceso Root", Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        }
    }

    @Override
    public void onRecordRequested() {
        if (!preferences.getBoolean(VoiceSettings.KEY_ACTIVE, false)) {
            Toast.makeText(this, "Activa primero el efecto", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, AudioService.class);
        if (recording) {
            intent.setAction(AudioService.ACTION_STOP_RECORDING);
        } else {
            intent.setAction(AudioService.ACTION_START_RECORDING);
        }
        startService(intent);
    }

    private void shareAudioFile(String filePath) {
        java.io.File file = new java.io.File(filePath);
        Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                file
        );

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("audio/wav");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        shareIntent.setPackage("com.whatsapp");
        
        try {
            startActivity(Intent.createChooser(shareIntent, "Enviar grabación"));
        } catch (android.content.ActivityNotFoundException e) {
            shareIntent.setPackage(null);
            startActivity(Intent.createChooser(shareIntent, "Enviar grabación"));
        }
    }

    private void showRootWarning() {
        new AlertDialog.Builder(this)
                .setTitle("Modo Root Experimental")
                .setMessage("El modo Root intenta mejorar la compatibilidad. Para aplicar los cambios " +
                        "de sistema, puede ser necesario reiniciar el servidor de audio. " +
                        "¿Deseas intentarlo ahora? (El audio del sistema se detendrá un segundo)")
                .setPositiveButton("Reiniciar Audio", (dialog, which) -> {
                    new Thread(RootHelper::restartAudioService).start();
                    Toast.makeText(this, "Reiniciando servidor de audio...", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Solo activar", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && pendingActivation) {
            pendingActivation = false;
            activateVoiceChanger();
        }
    }

    private void activateVoiceChanger() {
        if (!hasMicrophonePermission()) {
            pendingActivation = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_RECORD_AUDIO
                );
            }
            return;
        }

        if (!canDrawOverlays()) {
            pendingActivation = true;
            requestOverlayPermission();
            return;
        }

        preferences.edit().putBoolean(VoiceSettings.KEY_ACTIVE, true).apply();
        controlView.setActive(true);
        controlView.setOverlayReady(true);
        updateDrawerState();

        Intent intent = new Intent(this, AudioService.class);
        intent.setAction(AudioService.ACTION_START_PROCESSING);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void deactivateVoiceChanger() {
        pendingActivation = false;
        preferences.edit().putBoolean(VoiceSettings.KEY_ACTIVE, false).apply();
        controlView.setActive(false);
        updateDrawerState();
        stopService(new Intent(this, AudioService.class));
    }

    private void showDeviceDialog(boolean input) {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int flag = input ? AudioManager.GET_DEVICES_INPUTS : AudioManager.GET_DEVICES_OUTPUTS;
        AudioDeviceInfo[] devices = audioManager.getDevices(flag);
        List<AudioDeviceInfo> selectable = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        labels.add(input ? "Automatico de entrada" : "Automatico de salida");
        selectable.add(null);

        boolean submixFound = false;
        for (AudioDeviceInfo device : devices) {
            labels.add(describeDevice(device));
            selectable.add(device);
            if (device.getType() == AudioDeviceInfo.TYPE_REMOTE_SUBMIX) {
                submixFound = true;
            }
        }

        if (!submixFound && preferences.getBoolean(VoiceSettings.KEY_ROOT_MODE, false)) {
            labels.add("[VIRTUAL] Cable de Audio (Root)");
            selectable.add(null);
        }

        String title = input ? "Seleccionar Entrada" : "Seleccionar Salida";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    AudioDeviceInfo selected = selectable.get(which);
                    String label = labels.get(which);
                    int id = selected == null ? VoiceSettings.NO_DEVICE : selected.getId();
                    
                    if (selected == null && label.contains("[VIRTUAL]")) {
                        id = 32768;
                    }

                    preferences.edit()
                            .putInt(input
                                    ? VoiceSettings.KEY_INPUT_DEVICE_ID
                                    : VoiceSettings.KEY_OUTPUT_DEVICE_ID, id)
                            .apply();

                    Intent intent = new Intent(this, AudioService.class);
                    intent.setAction(AudioService.ACTION_UPDATE_DEVICES);
                    if (preferences.getBoolean(VoiceSettings.KEY_ACTIVE, false)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent);
                        } else {
                            startService(intent);
                        }
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void requestOverlayPermission() {
        if (canDrawOverlays()) {
            controlView.setOverlayReady(true);
            Toast.makeText(this, "Boton flotante listo", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(
                    this,
                    "Activa 'Mostrar sobre otras apps' para ProVoiceChanger",
                    Toast.LENGTH_LONG
            ).show();
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        }
    }

    private String describeDevice(AudioDeviceInfo device) {
        String productName = device.getProductName() == null
                ? "Audio"
                : device.getProductName().toString();
        return "[" + device.getId() + "] " + typeName(device.getType()) + " (Port:" + device.getType() + ") - " + productName;
    }

    private String typeName(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                return "Microfono";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "Headset";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return "USB";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "Bluetooth";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "Altavoz";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return "Auriculares";
            case AudioDeviceInfo.TYPE_REMOTE_SUBMIX:
                return "Cable Virtual (PRO)";
            default:
                return "Dispositivo";
        }
    }

    private boolean hasMicrophonePermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }
}
