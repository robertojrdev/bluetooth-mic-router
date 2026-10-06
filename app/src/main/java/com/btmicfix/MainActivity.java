package com.btmicfix;

// Modified from BTMicFix: public-API routing UI; no external routing extras are accepted.
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
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
import android.graphics.drawable.Icon;
import android.service.quicksettings.TileService;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private AudioManager audio;
    private RoutingService service;
    private boolean bound;
    private LinearLayout devices;
    private TextView status;
    private Button permissions;
    private Button stop;
    private Button extend;
    private Button automatic;
    private String error;
    private boolean automaticStartupAttempted;
    private final Runnable serviceListener = this::refresh;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateStatus();
            uiHandler.postDelayed(this, 1_000);
        }
    };
    private final AudioDeviceCallback deviceListener = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { refresh(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { refresh(); }
    };
    private final AudioManager.OnCommunicationDeviceChangedListener routeListener = device -> refresh();
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((RoutingService.LocalBinder) binder).service();
            service.addListener(serviceListener);
            startDefaultAutomatic();
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            refresh();
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Only the system's fixed tile-preferences action opens settings; no routing input is accepted.
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
        title.setTextSize(30);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);
        TextView tagline = new TextView(this);
        tagline.setText(R.string.tagline);
        tagline.setTextSize(18);
        tagline.setTextColor(0xFF65E0C7);
        tagline.setPadding(0, 8, 0, 0);
        content.addView(tagline);
        TextView instructions = new TextView(this);
        instructions.setText(R.string.instructions);
        instructions.setPadding(0, padding, 0, padding);
        content.addView(instructions);
        Button settings = new Button(this);
        settings.setText(R.string.settings);
        settings.setOnClickListener(view -> showSettings());
        content.addView(settings);
        Button tile = new Button(this);
        tile.setText(R.string.add_tile);
        tile.setOnClickListener(view -> addTile());
        content.addView(tile);
        Button notificationSettings = new Button(this);
        notificationSettings.setText(R.string.notification_settings);
        notificationSettings.setOnClickListener(view -> showNotificationSettings());
        content.addView(notificationSettings);
        permissions = new Button(this);
        permissions.setText(R.string.grant_permissions);
        permissions.setOnClickListener(view -> grantPermissions());
        content.addView(permissions);
        status = new TextView(this);
        status.setTextIsSelectable(true);
        status.setPadding(padding, padding, padding, padding);
        android.graphics.drawable.GradientDrawable card = new android.graphics.drawable.GradientDrawable();
        card.setColor(0xFF192B36);
        card.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        status.setBackground(card);
        LinearLayout.LayoutParams cardLayout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLayout.setMargins(0, padding, 0, padding);
        status.setLayoutParams(cardLayout);
        content.addView(status);
        devices = new LinearLayout(this);
        devices.setOrientation(LinearLayout.VERTICAL);
        content.addView(devices);
        automatic = new Button(this);
        automatic.setText(R.string.enable_automatic);
        automatic.setOnClickListener(view -> enableAutomatic());
        content.addView(automatic);
        extend = new Button(this);
        extend.setText(R.string.extend_session);
        extend.setOnClickListener(view -> startService(new Intent(this, RoutingService.class)
                .setAction(RoutingService.EXTEND)));
        content.addView(extend);
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
        TextView privacy = new TextView(this);
        privacy.setText(R.string.privacy_hint);
        privacy.setPadding(0, padding, 0, padding);
        content.addView(privacy);
        Button about = new Button(this);
        about.setText(R.string.about);
        about.setOnClickListener(view -> showAbout());
        content.addView(about);
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i) instanceof Button) styleButton((Button) content.getChildAt(i));
        }
        setContentView(scroll);
        if (TileService.ACTION_QS_TILE_PREFERENCES.equals(getIntent().getAction())) showSettings();
    }

    @Override protected void onStart() {
        super.onStart();
        automaticStartupAttempted = false;
        // Binding itself is passive; the visible activity enables the saved automatic default once.
        bound = bindService(new Intent(this, RoutingService.class), connection, BIND_AUTO_CREATE);
        audio.registerAudioDeviceCallback(deviceListener, new Handler(Looper.getMainLooper()));
        audio.addOnCommunicationDeviceChangedListener(getMainExecutor(), routeListener);
        refresh();
        uiHandler.post(tick);
    }

    @Override protected void onStop() {
        uiHandler.removeCallbacks(tick);
        if (service != null) service.removeListener(serviceListener);
        if (bound) unbindService(connection);
        bound = false;
        service = null;
        audio.unregisterAudioDeviceCallback(deviceListener);
        audio.removeOnCommunicationDeviceChangedListener(routeListener);
        super.onStop();
    }

    private void grantPermissions() {
        // Notification permission is optional: denying it must never stop automatic routing.
        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] names, int[] grants) {
        super.onRequestPermissionsResult(requestCode, names, grants);
        startDefaultAutomatic();
        refresh();
    }

    private void refresh() {
        if (status == null) return;
        boolean allowed = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
        permissions.setVisibility(allowed ? View.GONE : View.VISIBLE);
        updateStatus();
        stop.setEnabled(service != null && service.enabled());
        extend.setVisibility(service != null && service.timed() ? View.VISIBLE : View.GONE);
        automatic.setVisibility(new RoutingSettings(this).automatic() ? View.VISIBLE : View.GONE);
        automatic.setEnabled(allowed && (service == null || !service.automatic()));
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
            styleButton(button);
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

    private void updateStatus() {
        if (status == null) return;
        String state = service != null ? service.status()
                : "Routing is off.\nSystem communication device: "
                + RoutingService.describe(audio.getCommunicationDevice());
        status.setText(error != null ? error + "\n" + state : state);
    }

    private void enableAutomatic() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            grantPermissions();
            return;
        }
        try {
            startForegroundService(new Intent(this, RoutingService.class).setAction(RoutingService.ARM));
        } catch (RuntimeException failure) {
            error = "Android could not enable automatic mode. Try manual routing.";
            refresh();
        }
    }

    private void startDefaultAutomatic() {
        if (automaticStartupAttempted || service == null
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        automaticStartupAttempted = true;
        if (new RoutingSettings(this).automatic() && !service.enabled()) enableAutomatic();
    }

    private void showNotificationSettings() {
        new AlertDialog.Builder(this).setTitle("Background notification")
                .setMessage("Turn off this app's notifications on the next screen to hide the persistent notification. "
                        + "Automatic routing can keep running. Android still lists the app under active apps. "
                        + "Use the Headset mic tile or this app to stop routing.\n\n"
                        + "New installations do not ask for notification permission. You can enable notifications here if you want the Stop and timer controls.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open notification settings", (dialog, which) ->
                        startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())))
                .show();
    }

    private void showAbout() {
        String license;
        try (java.io.InputStream file = getAssets().open("LICENSE")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = file.read(buffer)) != -1) bytes.write(buffer, 0, count);
            license = bytes.toString(java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (java.io.IOException failure) {
            license = "Apache License 2.0. Full license is included with the source distribution.";
        }
        TextView text = new TextView(this);
        text.setText(getString(R.string.about_text, license));
        text.setTextIsSelectable(true);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        new AlertDialog.Builder(this).setTitle("About Bluetooth Mic Router")
                .setView(scroll).setPositiveButton("Close", null).show();
    }

    private void styleButton(Button button) {
        button.setAllCaps(false);
        button.setMinHeight((int) (52 * getResources().getDisplayMetrics().density));
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, "Edit your Quick Settings panel and drag Headset mic into it.", Toast.LENGTH_LONG).show();
            return;
        }
        getSystemService(StatusBarManager.class).requestAddTileService(
                new ComponentName(this, RoutingTileService.class), getString(R.string.tile_name),
                Icon.createWithResource(this, R.drawable.ic_headset_tile), getMainExecutor(), result -> {
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                            || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        Toast.makeText(this, "Tile added. Swipe down to use it; long press for Settings.", Toast.LENGTH_LONG).show();
                    } else Toast.makeText(this, "You can also add Headset mic by editing Quick Settings.", Toast.LENGTH_LONG).show();
                });
    }

    private void showSettings() {
        RoutingSettings settings = new RoutingSettings(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, padding);
        TextView durationLabel = new TextView(this);
        durationLabel.setText(R.string.duration_setting);
        form.addView(durationLabel);
        int[] values = {1, 5, 10, 15, 30, 60, 0};
        String[] labels = new String[values.length];
        int selected = 1;
        for (int i = 0; i < values.length; i++) {
            labels[i] = RoutingSettings.durationLabel(values[i]);
            if (values[i] == settings.minutes()) selected = i;
        }
        Spinner duration = new Spinner(this);
        ArrayAdapter<String> durations = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        durations.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        duration.setAdapter(durations);
        duration.setSelection(selected);
        form.addView(duration);

        TextView headsetLabel = new TextView(this);
        headsetLabel.setText(R.string.headset_setting);
        headsetLabel.setPadding(0, padding, 0, 0);
        form.addView(headsetLabel);
        List<AudioDeviceInfo> choices = new ArrayList<>();
        List<String> names = new ArrayList<>();
        choices.add(null);
        names.add("Ask if several are connected");
        int selectedHeadset = 0;
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
                if (!RoutingService.isHeadset(device)) continue;
                choices.add(device);
                names.add(RoutingService.describe(device));
                if (settings.headsetKey().equals(RoutingSettings.key(device))) selectedHeadset = choices.size() - 1;
            }
        }
        boolean missingPreferred = !settings.headsetKey().isEmpty() && selectedHeadset == 0;
        if (missingPreferred) {
            selectedHeadset = choices.size();
            choices.add(null);
            names.add(settings.headsetName() + " (not connected; keep preference)");
        }
        Spinner headset = new Spinner(this);
        ArrayAdapter<String> headsets = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        headsets.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        headset.setAdapter(headsets);
        headset.setSelection(selectedHeadset);
        form.addView(headset);
        Switch auto = new Switch(this);
        auto.setText(R.string.automatic_setting);
        auto.setChecked(settings.automatic());
        form.addView(auto);
        TextView help = new TextView(this);
        help.setPadding(0, padding, 0, 0);
        help.setText(R.string.settings_help);
        form.addView(help);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        new AlertDialog.Builder(this).setTitle("Settings").setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    settings.minutes(values[duration.getSelectedItemPosition()]);
                    int chosen = headset.getSelectedItemPosition();
                    if (!(missingPreferred && chosen == choices.size() - 1)) settings.headset(choices.get(chosen));
                    settings.automatic(auto.isChecked());
                    if (auto.isChecked()) enableAutomatic();
                    else if (service != null && service.automatic()) service.stopRouting("Automatic mode disabled.");
                    RoutingTileService.refreshTile(this);
                    refresh();
                }).show();
    }
}
