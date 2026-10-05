# BTMicFix Local (hardened test build)

Locally modified from Endda/BTMicFix commit `d38e6ccfb05d83ede27d99c68a4d2506073b9bd7`.
Upstream copyright/license remain in LICENSE. This branch replaces the Kotlin/Compose
application with a minimal Android-platform Java implementation of the same public
communication-routing API. Shizuku, shell execution, companion association, automatic
routing, stored preferences, and all third-party runtime dependencies are removed.

## Use

1. Connect a call-capable Bluetooth headset in Android settings.
2. Open BTMicFix Local and grant Nearby devices and notification permissions.
3. Tap the headset route button; check the actual system communication device shown.
4. Open ChatGPT and test the microphone with the phone several metres away.
5. Stop routing in the app or its notification, then compare.

A successful setCommunicationDevice request does not prove that another application
records from that microphone. Android or another audio-mode owner can override the
route. The application never records or plays audio to keep a route alive. Samsung /
ChatGPT compatibility must be tested on the actual phone. SCO/HFP can reduce media
quality and use additional headset battery while enabled.

Routing is manual. The private foreground service remains running while requested,
uses callbacks rather than polling, stops on disconnect/Stop, and does not restart
at boot or after process death. Samsung may still stop it; no battery exemption or
privileged access is requested. Opening the exported launcher consumes no intent
extras or data and cannot enable a route without a button tap.

Package: `com.btmicfix.local` (separate from upstream). Android 12+.
Permissions: BLUETOOTH_CONNECT, MODIFY_AUDIO_SETTINGS, FOREGROUND_SERVICE,
FOREGROUND_SERVICE_CONNECTED_DEVICE, POST_NOTIFICATIONS.
No INTERNET or RECORD_AUDIO permission, no root/ADB/Shizuku requirement.

## Build

Use JDK 17 and Android SDK 35. Set the SDK path in untracked local.properties.
Gradle 8.9 is pinned with its official distribution SHA-256; the wrapper JAR matches
Gradle's official checksum. Build plugin: com.android.application 8.7.3.

```sh
./gradlew clean assembleDebug lint
```

Debug output: app/build/outputs/apk/debug/app-debug.apk. Signing uses the normal local
Android debug key, never a repository key. The debug artifact is intended for personal
testing; USB debugging permits debugging it and is not a runtime requirement.
