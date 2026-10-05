package com.btmicfix;

// Locally hardened replacement: launcher input is ignored; routing requires a button tap.
import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private AudioManager audio;
    private RoutingService service;
    private boolean bound;
    private LinearLayout devices;
    private TextView status;
    private Button permissions;
    private Button stop;
    private String error;
    private final AudioDeviceCallback deviceListener = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { refresh(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { refresh(); }
    };
    private final AudioManager.OnCommunicationDeviceChangedListener routeListener = device -> refresh();
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((RoutingService.LocalBinder) binder).service();
            service.setUiListener(MainActivity.this::refresh);
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            refresh();
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No Intent extras, URLs, commands, or external route requests are consumed.
        audio = getSystemService(AudioManager.class);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        // Target SDK 35 enforces edge-to-edge: keep all controls inside system insets.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            content.setPadding(padding + bars.left, padding + bars.top,
                    padding + bars.right, padding + bars.bottom);
            return insets;
        });
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(26);
        content.addView(title);
        TextView instructions = new TextView(this);
        instructions.setText(R.string.instructions);
        instructions.setPadding(0, padding, 0, padding);
        content.addView(instructions);
        permissions = new Button(this);
        permissions.setText(R.string.grant_permissions);
        permissions.setOnClickListener(view -> grantPermissions());
        content.addView(permissions);
        status = new TextView(this);
        status.setTextIsSelectable(true);
        status.setPadding(0, padding, 0, padding);
        content.addView(status);
        devices = new LinearLayout(this);
        devices.setOrientation(LinearLayout.VERTICAL);
        content.addView(devices);
        Button refresh = new Button(this);
        refresh.setText(R.string.refresh);
        refresh.setOnClickListener(view -> { error = null; refresh(); });
        content.addView(refresh);
        stop = new Button(this);
        stop.setText(R.string.stop);
        stop.setOnClickListener(view -> {
            error = null;
            if (service != null) service.stopRouting("Routing is off.");
            refresh();
        });
        content.addView(stop);
        setContentView(scroll);
    }

    @Override protected void onStart() {
        super.onStart();
        // Binding alone never routes audio or starts a foreground notification.
        bound = bindService(new Intent(this, RoutingService.class), connection, BIND_AUTO_CREATE);
        audio.registerAudioDeviceCallback(deviceListener, new Handler(Looper.getMainLooper()));
        audio.addOnCommunicationDeviceChangedListener(getMainExecutor(), routeListener);
        refresh();
    }

    @Override protected void onStop() {
        if (service != null) service.setUiListener(null);
        if (bound) unbindService(connection);
        bound = false;
        service = null;
        audio.unregisterAudioDeviceCallback(deviceListener);
        audio.removeOnCommunicationDeviceChangedListener(routeListener);
        super.onStop();
    }

    private void grantPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] names, int[] grants) {
        super.onRequestPermissionsResult(requestCode, names, grants);
        refresh();
    }

    private void refresh() {
        if (status == null) return;
        boolean allowed = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
        boolean notified = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        permissions.setVisibility(allowed && notified ? View.GONE : View.VISIBLE);
        String state = service != null ? service.status()
                : "Routing is off.\nSystem communication device: "
                + RoutingService.describe(audio.getCommunicationDevice());
        status.setText(error != null ? error + "\n" + state : state);
        stop.setEnabled(service != null && service.hasRequest());
        devices.removeAllViews();
        if (!allowed) {
            TextView text = new TextView(this);
            text.setText(R.string.nearby_permission);
            devices.addView(text);
            return;
        }
        for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
            if (!RoutingService.isHeadset(device)) continue;
            Button button = new Button(this);
            button.setText(getString(R.string.route_to, RoutingService.describe(device)));
            button.setOnClickListener(view -> {
                error = null;
                try {
                    startForegroundService(new Intent(this, RoutingService.class)
                            .setAction(RoutingService.START).putExtra(RoutingService.DEVICE_ID, device.getId()));
                } catch (RuntimeException failure) {
                    error = "Android could not start the routing service. Check permissions and retry.";
                    refresh();
                }
            });
            devices.addView(button);
        }
        if (devices.getChildCount() == 0) {
            TextView text = new TextView(this);
            text.setText(R.string.no_headset);
            devices.addView(text);
        }
    }
}
