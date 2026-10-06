# How Bluetooth Mic Router works

The nonexported `RoutingService` uses Android’s public `AudioManager` APIs to set `MODE_IN_COMMUNICATION` and select an available `TYPE_BLUETOOTH_SCO` or `TYPE_BLE_HEADSET` with `setCommunicationDevice()`.

A static one-second, all-zero PCM buffer loops in an `AudioTrack` with `USAGE_VOICE_COMMUNICATION`. This silent playback keeps a nonprivileged communication-mode owner active on Android 16. There is no recorder, audio capture, file playback, busy loop, root or shell command.

Automatic mode uses `registerAudioRecordingCallback()` and `getActiveRecordingConfigurations()`. The callback exposes recording activity metadata; without privileged permissions, client identities are anonymized. The app uses only audio-source type and silenced state, discards the callback data, and never obtains audio samples. Remote submix and telephone capture sources are excluded. It can still react to recordings from apps other than ChatGPT.

When automatic mode is ready, no silent audio plays. A recording starts a routing session when exactly one eligible preferred headset is available. Recording end schedules cleanup after five seconds. The session timeout stops routing and suppresses automatic restart until the recording goes idle. New recordings can start a new session afterwards. Detected ringing/cellular-call modes pause an automatic session.

The timer uses elapsed realtime, a process-bound `AlarmManager.OnAlarmListener`, and a Handler fallback. It needs no exact-alarm special permission. Android scheduling, Doze and process death can affect delivery; treat the timer as a convenience, not an exact timing guarantee.

Cleanup releases the AudioTrack, clears this app’s communication-device request and relinquishes this UID’s audio-mode request. It runs on stop, timeout, disconnect, errors and service destruction. The service is `START_NOT_STICKY` and has no boot receiver.

The launcher starts saved automatic mode once per visible app entry after Bluetooth permission is granted. The settings default is automatic on, five minutes, and an unambiguous connected headset. Explicitly saved automatic-off preferences stay off. Stop pauses the service; turning automatic off in settings also changes the saved default.

Settings store only duration, automatic-mode choice and preferred headset identity/name in private SharedPreferences. Cloud/device-transfer backup is disabled. There are no external files, databases or event logs. Reading bundled `assets/LICENSE` for the About dialog is the only file input in app code.

The launcher accepts the fixed system tile-preferences action to open settings. It accepts no arbitrary route, command or privileged operation from incoming extras. The worker service is not exported. The exported Quick Settings tile requires Android’s `BIND_QUICK_SETTINGS_TILE` permission and cannot be driven by ordinary apps.

The foreground service must supply a notification, even if the user blocks notification display. It requests no notification permission at launch; Android 13+ can keep the service active while hiding its notification from the drawer. The system’s active-apps indicator remains. A button opens only this app’s system notification settings using a fixed intent and its own package name.
