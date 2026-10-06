package com.btmicfix;

// Locally hardened replacement: public audio APIs only; nonexported worker service.
import android.Manifest;
import android.app.Notification;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

public final class RoutingService extends Service {
    static final String START = "com.btmicfix.local.START";
    static final String STOP = "com.btmicfix.local.STOP";
    static final String REAPPLY = "com.btmicfix.local.REAPPLY";
    static final String EXTEND = "com.btmicfix.local.EXTEND";
    static final String ARM = "com.btmicfix.local.ARM";
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
    private AudioTrack keepalive;
    private int previousMode;
    private String result = "Routing is off.";
    private final Set<Runnable> listeners = new HashSet<>();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final SessionDeadline session = new SessionDeadline();
    private AlarmManager alarms;
    private final AlarmManager.OnAlarmListener timeoutAlarm = this::checkTimeout;
    private final Runnable timeoutCallback = this::checkTimeout;
    private boolean automaticArmed;
    private boolean automaticSession;
    private final AutomaticPolicy automaticPolicy = new AutomaticPolicy();
    private final Runnable idleCallback = () -> {
        if (!automaticArmed) return;
        readRecordingState();
        if (!automaticPolicy.recording() && automaticSession) {
            finishSession("Recording ended. Automatic mode is ready.", false);
        }
    };
    private final AudioManager.AudioRecordingCallback recordingListener =
            new AudioManager.AudioRecordingCallback() {
                @Override public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
                    if (automaticArmed) recordingChanged(configs);
                }
            };
    private final LocalBinder binder = new LocalBinder();

    final class LocalBinder extends Binder {
        RoutingService service() { return RoutingService.this; }
    }

    private final AudioManager.OnCommunicationDeviceChangedListener routeListener =
            device -> publish();
    private final AudioManager.OnModeChangedListener modeListener = mode -> {
        if (automaticSession && (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE)) {
            finishSession("Phone call owns audio. Automatic routing paused.", true);
        } else publish();
    };
    private final AudioDeviceCallback deviceListener = new AudioDeviceCallback() {
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            if (requestedId == null) return;
            for (AudioDeviceInfo device : removed) {
                if (device.getId() == requestedId) {
                    finishSession("Headset disconnected. Routing stopped.", false);
                    return;
                }
            }
            publish();
        }
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            if (automaticArmed) readRecordingState();
            publish();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        audio = getSystemService(AudioManager.class);
        alarms = getSystemService(AlarmManager.class);
        notifications = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Bluetooth microphone routing", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Background routing and automatic mode. You can hide this notification in Android settings.");
        channel.setShowBadge(false);
        notifications.createNotificationChannel(channel);
    }

    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public boolean onUnbind(Intent intent) {
        return false;
    }

    void addListener(Runnable listener) { listeners.add(listener); }
    void removeListener(Runnable listener) { listeners.remove(listener); }
    boolean hasRequest() { return requestedId != null; }
    boolean enabled() { return automaticArmed || hasRequest(); }
    boolean automatic() { return automaticArmed; }
    boolean timed() { return hasRequest() && session.timed(); }
    boolean routeMatches() {
        AudioDeviceInfo selected = audio.getCommunicationDevice();
        return hasRequest() && selected != null && selected.getId() == requestedId
                && audio.getMode() == AudioManager.MODE_IN_COMMUNICATION;
    }

    String timeStatus() {
        if (!hasRequest()) return automaticArmed
                ? automaticPolicy.recording() && !automaticPolicy.mayStart()
                    ? "Waiting for recording to end" : "Automatic ready"
                : "Off";
        if (!session.timed()) return "Until you stop";
        long seconds = (session.remaining(SystemClock.elapsedRealtime()) + 999) / 1000;
        return "Auto-stop in " + (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    String status() {
        AudioDeviceInfo selected = audio.getCommunicationDevice();
        boolean matches = requestedId != null && selected != null && selected.getId() == requestedId;
        String route = requestedId == null ? result
                : matches ? "System route matches requested headset: " + requestedName
                : "Headset route is no longer selected. Android or another app changed it.";
        boolean playing = keepalive != null && keepalive.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
        return route + "\nSystem communication device: " + describe(selected)
                + "\nAudio mode: " + audio.getMode()
                + "\nSilent keepalive: " + (playing ? "playing (no recording)" : "off")
                + "\n" + (hasRequest() ? timeStatus() : "Default: "
                        + RoutingSettings.durationLabel(new RoutingSettings(this).minutes()))
                + (automaticArmed ? "\nAutomatic mode: watching recording activity (no audio access)." : "")
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
        if (ARM.equals(intent.getAction())) {
            armAutomatic();
            return START_NOT_STICKY;
        }
        if (EXTEND.equals(intent.getAction())) {
            if (hasRequest() && session.expired(SystemClock.elapsedRealtime())) checkTimeout();
            else if (hasRequest() && session.extend(SystemClock.elapsedRealtime())) {
                scheduleTimeout();
                publish();
            }
            if (!enabled()) stopSelf();
            return START_NOT_STICKY;
        }
        boolean reapply = REAPPLY.equals(intent.getAction());
        if (!START.equals(intent.getAction()) && !reapply) {
            if (!enabled()) stopSelf();
            return START_NOT_STICKY;
        }
        if (hasRequest() && session.expired(SystemClock.elapsedRealtime())) {
            checkTimeout();
            if (reapply) return START_NOT_STICKY;
        }
        if (reapply && !hasRequest()) {
            if (!enabled()) stopSelf();
            return START_NOT_STICKY;
        }

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            stopRouting("Bluetooth permission is needed.");
            return START_NOT_STICKY;
        }

        if (!prepareForeground("Requesting headset route…")) return START_NOT_STICKY;
        int id = reapply ? requestedId : intent.getIntExtra(DEVICE_ID, -1);
        route(id, reapply && automaticSession);
        return START_NOT_STICKY;
    }

    private boolean prepareForeground(String text) {
        try {
            startForeground(NOTIFICATION, notification(text),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            foreground = true;
            return true;
        } catch (RuntimeException error) {
            stopRouting("Android could not start foreground routing. Check permissions.");
            return false;
        }
    }

    private void route(int id, boolean automaticRequest) {
        boolean newSession = !hasRequest();
        AudioDeviceInfo target = null;
        for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
            if (device.getId() == id && isHeadset(device)) { target = device; break; }
        }
        if (target == null) {
            finishSession("Headset is no longer available. Refresh and retry.", true);
            return;
        }

        try {
            if (!ownsMode) {
                int existingMode = audio.getMode();
                // Avoid overriding an existing call or another app's communication session.
                if (existingMode != AudioManager.MODE_NORMAL
                        && !(automaticRequest && existingMode == AudioManager.MODE_IN_COMMUNICATION)) {
                    finishSession("Audio is already in use. Stop the call or voice session and retry.", true);
                    return;
                }
                // MODE_NORMAL releases only this UID's mode request; never adopt another UID's mode.
                previousMode = AudioManager.MODE_NORMAL;
                ownsMode = true;
            }
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
            // Android 16 requires active voice playback/recording for nonprivileged mode owners.
            // A one-second all-zero PCM buffer loops in AudioTrack; no recorder, files or worker loop.
            startKeepalive();
            if (!audio.setCommunicationDevice(target)) {
                finishSession("Android rejected the communication route request.", true);
                return;
            }
            requestedId = target.getId();
            requestedName = describe(target);
            automaticSession = automaticRequest;
            if (newSession) {
                session.start(SystemClock.elapsedRealtime(), new RoutingSettings(this).minutes());
                scheduleTimeout();
            }
            result = "Route request accepted: " + requestedName;
            monitorDevices();
            // Log numeric device identity/profile only; no MAC address or device name.
            Log.i("BTMicFix", "Route accepted: id=" + id + " type=" + target.getType());
            publish();
        } catch (RuntimeException error) {
            Log.w("BTMicFix", "Route request failed: " + error.getClass().getSimpleName());
            finishSession("Android could not set the headset route.", true);
        }
    }

    private void monitorDevices() {
        if (monitoring) return;
        audio.registerAudioDeviceCallback(deviceListener, timerHandler);
        audio.addOnCommunicationDeviceChangedListener(getMainExecutor(), routeListener);
        audio.addOnModeChangedListener(getMainExecutor(), modeListener);
        monitoring = true;
    }

    private void armAutomatic() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            stopRouting("Bluetooth permission is needed.");
            return;
        }
        if (!prepareForeground("Automatic mode ready. Waiting for recording.")) return;
        try {
            if (!automaticArmed) {
                audio.registerAudioRecordingCallback(recordingListener, timerHandler);
                automaticArmed = true;
            }
            monitorDevices();
            result = "Automatic mode ready. Waiting for recording.";
            readRecordingState();
            publish();
        } catch (RuntimeException error) {
            stopRouting("Android could not monitor recording activity. Use manual routing.");
        }
    }

    private void readRecordingState() {
        if (automaticArmed) recordingChanged(audio.getActiveRecordingConfigurations());
    }

    private void recordingChanged(List<AudioRecordingConfiguration> configs) {
        boolean recording = false;
        for (AudioRecordingConfiguration config : configs) {
            int source = config.getClientAudioSource();
            if (!config.isClientSilenced() && source != MediaRecorder.AudioSource.REMOTE_SUBMIX
                    && source != MediaRecorder.AudioSource.VOICE_CALL
                    && source != MediaRecorder.AudioSource.VOICE_UPLINK
                    && source != MediaRecorder.AudioSource.VOICE_DOWNLINK) recording = true;
        }
        automaticPolicy.update(recording);
        timerHandler.removeCallbacks(idleCallback);
        if (!recording) {
            // Allow brief recording restarts and route reconfigurations without flapping the headset.
            if (automaticSession) timerHandler.postDelayed(idleCallback, 5_000);
            return;
        }
        if (hasRequest() || !automaticPolicy.mayStart()) return;
        List<AudioDeviceInfo> devices = new RoutingSettings(this).candidates(audio.getAvailableCommunicationDevices());
        if (devices.size() != 1) {
            result = devices.isEmpty() ? "Automatic ready; preferred headset is unavailable."
                    : "Automatic ready; choose a preferred headset in Settings.";
            publish();
            return;
        }
        route(devices.get(0).getId(), true);
    }

    private void scheduleTimeout() {
        timerHandler.removeCallbacks(timeoutCallback);
        alarms.cancel(timeoutAlarm);
        if (!session.timed()) return;
        // Listener alarms need no exact-alarm permission. They live only with this service.
        alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, session.at(),
                "BTMicFix auto-stop", timeoutAlarm, timerHandler);
        timerHandler.postDelayed(timeoutCallback, session.remaining(SystemClock.elapsedRealtime()));
    }

    private void checkTimeout() {
        if (hasRequest() && session.expired(SystemClock.elapsedRealtime())) {
            finishSession("Timer finished. Routing is off.", true);
        }
    }

    private void startKeepalive() {
        if (keepalive != null && keepalive.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) return;
        stopKeepalive();
        final int sampleRate = 16000;
        final int frames = sampleRate;
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(frames * Short.BYTES)
                .build();
        keepalive = track; // Ensure failure cleanup releases the native platform track.
        short[] silence = new short[frames];
        if (track.write(silence, 0, frames) != frames
                || track.setLoopPoints(0, frames, -1) != AudioTrack.SUCCESS) {
            throw new IllegalStateException("Could not initialize silent voice playback");
        }
        track.play();
        if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            throw new IllegalStateException("Silent voice playback did not start");
        }
    }

    private void stopKeepalive() {
        if (keepalive == null) return;
        AudioTrack track = keepalive;
        keepalive = null;
        try {
            track.stop();
        } catch (IllegalStateException error) {
            Log.w("BTMicFix", "Silent playback already stopped");
        } finally {
            track.release();
        }
    }

    void stopRouting(String message) {
        if (automaticArmed) {
            automaticArmed = false;
            audio.unregisterAudioRecordingCallback(recordingListener);
        }
        automaticPolicy.clear();
        timerHandler.removeCallbacks(idleCallback);
        finishSession(message, false);
    }

    private void finishSession(String message, boolean blockUntilIdle) {
        if (automaticArmed && blockUntilIdle) automaticPolicy.suppressUntilIdle();
        timerHandler.removeCallbacks(idleCallback);
        releaseRoute();
        result = message;
        if (foreground && !automaticArmed) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
        }
        publish();
        if (!automaticArmed) stopSelf();
    }

    private void releaseRoute() {
        timerHandler.removeCallbacks(timeoutCallback);
        alarms.cancel(timeoutAlarm);
        session.clear();
        stopKeepalive();
        if (monitoring && !automaticArmed) {
            audio.unregisterAudioDeviceCallback(deviceListener);
            audio.removeOnCommunicationDeviceChangedListener(routeListener);
            audio.removeOnModeChangedListener(modeListener);
            monitoring = false;
        }
        if (ownsMode) {
            try {
                audio.clearCommunicationDevice();
            } catch (RuntimeException error) {
                Log.w("BTMicFix", "Route cleanup failed: " + error.getClass().getSimpleName());
            } finally {
                try {
                    // Relinquish this UID's request even if clearing the device failed.
                    audio.setMode(previousMode);
                } catch (RuntimeException error) {
                    Log.w("BTMicFix", "Mode cleanup failed: " + error.getClass().getSimpleName());
                }
            }
        }
        ownsMode = false;
        requestedId = null;
        requestedName = null;
        automaticSession = false;
    }

    private void publish() {
        if (foreground) {
            notifications.notify(NOTIFICATION, notification(routeMatches()
                    ? "System route: " + requestedName + "\n" + timeStatus()
                    : hasRequest() ? "Route changed by Android or another app. Open to check.\n" + timeStatus()
                    : result + "\nAutomatic mode enabled; tap Stop to disable."));
        }
        RoutingTileService.refreshTile(this);
        for (Runnable listener : new ArrayList<>(listeners)) listener.run();
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, RoutingService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_headset_tile)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Stop routing", stop).build())
                .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE);
        if (timed()) {
            builder.setWhen(System.currentTimeMillis() + session.remaining(SystemClock.elapsedRealtime()))
                    .setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true);
            PendingIntent extend = PendingIntent.getService(this, 3,
                    new Intent(this, RoutingService.class).setAction(EXTEND), PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null, "Add 5 minutes", extend).build());
        }
        if (requestedId != null) {
            PendingIntent reapply = PendingIntent.getService(this, 2,
                    new Intent(this, RoutingService.class).setAction(REAPPLY),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(new Notification.Action.Builder(null, "Reapply headset", reapply).build());
        }
        return builder.build();
    }

    @Override public void onDestroy() {
        listeners.clear();
        if (automaticArmed) audio.unregisterAudioRecordingCallback(recordingListener);
        automaticArmed = false;
        timerHandler.removeCallbacks(idleCallback);
        releaseRoute();
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
        RoutingTileService.refreshTile(this);
        super.onDestroy();
    }
}
