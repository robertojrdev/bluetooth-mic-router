package com.btmicfix;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import java.util.List;

/** SystemUI-only tile entry point. No commands or caller-provided route data are accepted. */
public final class RoutingTileService extends TileService {
    private RoutingService service;
    private boolean bound;
    private boolean listening;
    private boolean pendingClick;
    private final Runnable listener = this::updateTile;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((RoutingService.LocalBinder) binder).service();
            if (listening) service.addListener(listener);
            updateTile();
            if (pendingClick) {
                pendingClick = false;
                toggle();
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            updateTile();
        }
    };

    static void refreshTile(Context context) {
        try {
            requestListeningState(context, new ComponentName(context, RoutingTileService.class));
        } catch (RuntimeException ignored) {
            // Tile may not yet be added. Routing must not depend on SystemUI availability.
        }
    }

    @Override public void onStartListening() {
        super.onStartListening();
        listening = true;
        bindIfNeeded();
        if (service != null) service.addListener(listener);
        updateTile();
    }

    private void bindIfNeeded() {
        if (!bound) bound = bindService(new Intent(this, RoutingService.class), connection, BIND_AUTO_CREATE);
    }

    @Override public void onStopListening() {
        listening = false;
        pendingClick = false;
        detach();
        super.onStopListening();
    }

    private void detach() {
        if (service != null) service.removeListener(listener);
        if (bound) unbindService(connection);
        bound = false;
        service = null;
    }

    @Override public void onClick() {
        super.onClick();
        // Stop is safe while locked; starting a new route requires an unlock.
        if (isLocked() && (service == null || !service.enabled())) unlockAndRun(this::toggle);
        else toggle();
    }

    private void toggle() {
        if (service == null) {
            pendingClick = true;
            bindIfNeeded();
            return;
        }
        if (service.enabled()) {
            service.stopRouting("Routing is off.");
            finishClick();
            return;
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            openApp();
            finishClick();
            return;
        }
        AudioManager audio = getSystemService(AudioManager.class);
        RoutingSettings settings = new RoutingSettings(this);
        if (settings.automatic()) {
            try {
                startForegroundService(new Intent(this, RoutingService.class).setAction(RoutingService.ARM));
            } catch (RuntimeException error) {
                Toast.makeText(this, "Android blocked automatic mode. Open the app to enable it.", Toast.LENGTH_LONG).show();
            }
            finishClick();
            return;
        }
        List<AudioDeviceInfo> devices = settings.candidates(audio.getAvailableCommunicationDevices());
        if (devices.isEmpty()) {
            Toast.makeText(this, settings.headsetKey().isEmpty() ? "Connect a call-capable headset first."
                    : "Preferred headset is unavailable. Connect it or change Settings.", Toast.LENGTH_LONG).show();
            finishClick();
        } else if (devices.size() == 1) {
            start(devices.get(0));
        } else {
            String[] names = new String[devices.size()];
            for (int i = 0; i < names.length; i++) names[i] = RoutingService.describe(devices.get(i));
            showDialog(new AlertDialog.Builder(this).setTitle("Choose headset")
                    .setItems(names, (dialog, which) -> start(devices.get(which)))
                    .setNegativeButton("Cancel", (dialog, which) -> finishClick())
                    .setOnCancelListener(dialog -> finishClick()).create());
        }
    }

    private void start(AudioDeviceInfo device) {
        try {
            startForegroundService(new Intent(this, RoutingService.class)
                    .setAction(RoutingService.START).putExtra(RoutingService.DEVICE_ID, device.getId()));
        } catch (RuntimeException error) {
            Toast.makeText(this, "Android blocked routing. Open the app and try its route button.",
                    Toast.LENGTH_LONG).show();
        }
        finishClick();
    }

    private void finishClick() {
        updateTile();
        if (!listening) detach();
    }

    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 4, intent, PendingIntent.FLAG_IMMUTABLE));
        } else startActivity(intent);
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = service != null && service.enabled();
        tile.setLabel(getString(R.string.tile_name));
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        RoutingSettings settings = new RoutingSettings(this);
        String subtitle = enabled ? (!service.hasRequest() ? service.timeStatus()
                : service.routeMatches() ? service.timed() ? "Routing; auto-stop set" : "Routing until you stop"
                : "Route changed; tap to stop")
                : settings.automatic() ? "Enable automatic mode"
                : "Start for " + RoutingSettings.durationLabel(settings.minutes());
        tile.setSubtitle(subtitle);
        tile.setStateDescription(subtitle);
        tile.updateTile();
    }

    @Override public void onDestroy() {
        detach();
        super.onDestroy();
    }
}
