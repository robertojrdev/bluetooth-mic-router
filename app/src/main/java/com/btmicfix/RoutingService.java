package com.btmicfix;

// Locally hardened replacement: public audio APIs only; manual, nonexported service.
import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public final class RoutingService extends Service {
    static final String START = "com.btmicfix.local.START";
    static final String STOP = "com.btmicfix.local.STOP";
    static final String DEVICE_ID = "device_id";
    private static final String CHANNEL = "manual_routing";
    private static final int NOTIFICATION = 1;
    private AudioManager audio;
    private NotificationManager notifications;
    private Integer requestedId;
    private String requestedName;
    private boolean ownsMode;
    private boolean foreground;
    private boolean monitoring;
    private int previousMode;
    private String result = "Routing is off.";
    private Runnable uiListener;
    private final LocalBinder binder = new LocalBinder();

    final class LocalBinder extends Binder {
        RoutingService service() { return RoutingService.this; }
    }

    private final AudioManager.OnCommunicationDeviceChangedListener routeListener =
            device -> publish();
    private final AudioDeviceCallback deviceListener = new AudioDeviceCallback() {
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            if (requestedId == null) return;
            for (AudioDeviceInfo device : removed) {
                if (device.getId() == requestedId) {
                    stopRouting("Headset disconnected. Routing stopped.");
                    return;
                }
            }
            publish();
        }
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { publish(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        audio = getSystemService(AudioManager.class);
        notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Manual Bluetooth routing", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public boolean onUnbind(Intent intent) {
        uiListener = null;
        return false;
    }

    void setUiListener(Runnable listener) { uiListener = listener; }
    boolean hasRequest() { return requestedId != null; }

    String status() {
        AudioDeviceInfo selected = audio.getCommunicationDevice();
        return result + "\nSystem communication device: " + describe(selected)
                + "\nAudio mode: " + audio.getMode()
                + "\nThis does not verify which microphone ChatGPT records.";
    }

    static boolean isHeadset(AudioDeviceInfo device) {
        return device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET;
    }

    static String describe(AudioDeviceInfo device) {
        if (device == null) return "none";
        String profile = device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ? "SCO / HFP"
                : device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET ? "BLE headset"
                : "type " + device.getType();
        return device.getProductName() + " (" + profile + ", id " + device.getId() + ")";
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (STOP.equals(intent.getAction())) {
            stopRouting("Routing is off.");
            return START_NOT_STICKY;
        }
        if (!START.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            stopRouting("Bluetooth permission is needed.");
            return START_NOT_STICKY;
        }

        try {
            // Start promptly; do not perform asynchronous work before this call.
            startForeground(NOTIFICATION, notification("Requesting headset route…"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            foreground = true;
        } catch (RuntimeException error) {
            stopRouting("Android could not start foreground routing. Check permissions.");
            return START_NOT_STICKY;
        }

        int id = intent.getIntExtra(DEVICE_ID, -1);
        AudioDeviceInfo target = null;
        for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
            if (device.getId() == id && isHeadset(device)) { target = device; break; }
        }
        if (target == null) {
            stopRouting("Headset is no longer available. Refresh and retry.");
            return START_NOT_STICKY;
        }

        try {
            if (!ownsMode) {
                previousMode = audio.getMode();
                // Avoid overriding an existing call or another app's communication session.
                if (previousMode != AudioManager.MODE_NORMAL) {
                    stopRouting("Audio is already in use. Stop the call or voice session and retry.");
                    return START_NOT_STICKY;
                }
                ownsMode = true;
            }
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
            if (!audio.setCommunicationDevice(target)) {
                stopRouting("Android rejected the communication route request.");
                return START_NOT_STICKY;
            }
            requestedId = target.getId();
            requestedName = describe(target);
            result = "Route request accepted: " + requestedName;
            if (!monitoring) {
                audio.registerAudioDeviceCallback(deviceListener, new Handler(Looper.getMainLooper()));
                audio.addOnCommunicationDeviceChangedListener(getMainExecutor(), routeListener);
                monitoring = true;
            }
            // Log numeric device identity/profile only; no MAC address or device name.
            Log.i("BTMicFix", "Route accepted: id=" + id + " type=" + target.getType());
            publish();
        } catch (RuntimeException error) {
            Log.w("BTMicFix", "Route request failed: " + error.getClass().getSimpleName());
            stopRouting("Android could not set the headset route.");
        }
        return START_NOT_STICKY;
    }

    void stopRouting(String message) {
        releaseRoute();
        result = message;
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
        }
        publish();
        stopSelf();
    }

    private void releaseRoute() {
        if (monitoring) {
            audio.unregisterAudioDeviceCallback(deviceListener);
            audio.removeOnCommunicationDeviceChangedListener(routeListener);
            monitoring = false;
        }
        if (ownsMode) {
            try {
                audio.clearCommunicationDevice();
                // MODE_NORMAL relinquishes this app's mode request; it cannot end another UID's call.
                audio.setMode(previousMode);
            } catch (RuntimeException error) {
                Log.w("BTMicFix", "Route cleanup failed: " + error.getClass().getSimpleName());
            }
        }
        ownsMode = false;
        requestedId = null;
        requestedName = null;
    }

    private void publish() {
        if (foreground && requestedId != null) {
            AudioDeviceInfo selected = audio.getCommunicationDevice();
            boolean matches = selected != null && selected.getId() == requestedId;
            notifications.notify(NOTIFICATION, notification(matches
                    ? "System route: " + requestedName
                    : "Route changed by Android or another app. Open to check."));
        }
        if (uiListener != null) uiListener.run();
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, RoutingService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("BTMicFix Local")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Stop routing", stop).build())
                .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    @Override public void onDestroy() {
        uiListener = null;
        releaseRoute();
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
