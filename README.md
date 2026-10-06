# Bluetooth Mic Router for Android

**Use your Bluetooth headset microphone with ChatGPT Voice and other microphone apps.** Bluetooth Mic Router requests Android’s Bluetooth communication route, keeps it active during a session, and releases it afterwards. Includes automatic routing, a Quick Settings tile, and an auto-stop timer.

[Download the latest release](https://github.com/robertojrdev/bluetooth-mic-router/releases/latest) · [How it works](docs/HOW-IT-WORKS.md) · [Privacy and security](docs/SECURITY-REVIEW.md) · [Build from source](docs/BUILDING.md)

- **Automatic by default:** open the app and grant Nearby devices permission. When recording starts, it requests your connected headset’s call microphone. It watches recording status, **never microphone audio**.
- **Quick Settings:** add **Headset mic** beside Bluetooth and the flashlight. Tap to enable/disable; long press for settings.
- **Timed sessions:** 1, 5, 10, 15, 30 or 60 minutes, or until you stop. Default: 5 minutes. Extend a running session by 5 minutes.
- **No internet or recording permission.** No analytics, ads, accounts, root, Shizuku or shell commands. No third-party runtime libraries.
- **Quiet background operation:** notification permission is optional. Hide the persistent notification in Android settings while automatic mode keeps running.

## Install and use

Requires **Android 12 or newer** and a Bluetooth headset that supports calls (HFP/SCO or BLE headset communication audio). Media-only Bluetooth devices are not sufficient.

1. Download `Bluetooth-Mic-Router-1.0.0.zip` from [Releases](https://github.com/robertojrdev/bluetooth-mic-router/releases/latest). Extract it and tap the APK. Check the release checksums if verifying the download.
2. Connect your headphones in Android Bluetooth settings. Open Bluetooth Mic Router and grant **Nearby devices** permission.
3. Automatic mode is enabled by default. Choose your preferred headset and session duration in **Routing settings**, especially if several headsets are connected.
4. Open ChatGPT Voice or another microphone app. Speak into the headset with your phone several metres away to check which microphone is actually being used.
5. Routing stops five seconds after recording ends, or when the session timer expires. Automatic mode stays ready for the next recording. After a timeout, recording must end before another automatic session can start.

For manual use, disable automatic mode in Routing settings, tap **Route to…**, then start the recording app. **Stop** or the tile disables the running service; opening the app again enables it if the automatic setting is still on. Turn that setting off to keep manual mode as your default.

It does not restart at boot or after Android stops the app. Open the app or use its tile to enable it again.

## Hide the persistent notification

Tap **Hide or show background notification** and turn off the app’s notifications on the Android settings screen. On Android 13+, the service can run without notification permission. Android still lists it under **active apps**. Use the tile or app to stop it when its notification is hidden.

New installations do not ask for notification permission. If you want notification controls (Stop, Add 5 minutes, Reapply headset), enable notifications in Android settings. Older Android versions offer notification-channel settings; the exact switches depend on your device.

[Android’s notification permission documentation](https://developer.android.com/develop/ui/views/notifications/notification-permission) explains the distinction between a required foreground-service notification and its visibility in the notification drawer.

## Compatibility and limitations

**User-reported working setup:** Samsung Galaxy S23, Android 16, Sony WF-1000XM6, ChatGPT Voice. Manual persistence and automatic routing were confirmed with the preceding routing implementation; 1.0.0 preserves that audio-routing code. The new branding/default/notification controls have not yet been tested on that phone.

Android and individual apps can override communication routing or choose their own recording input. A successful route request does not prove which microphone another app records. This app cannot force every recorder on every Android device. ChatGPT Voice and dictation may use different recording paths: test the feature you use.

Automatic mode responds to microphone activity from **any app**, including camera recording or dictation. It cannot reliably identify ChatGPT through the unprivileged recording callback. It avoids detected cellular-call audio modes and skips ambiguous headset selections. A short delay before routing can still occur.

The silent keepalive runs only while routing. It holds the headset’s call profile active, which can use more battery and reduce music quality. Waiting for a recording uses callbacks rather than a polling loop and does not play the keepalive. Actual battery impact has not been measured.

## Permissions

| Permission | Purpose |
| --- | --- |
| `BLUETOOTH_CONNECT` | Enumerate/select connected headset communication devices; Nearby devices permission. |
| `MODIFY_AUDIO_SETTINGS` | Request communication mode and headset routing. |
| `FOREGROUND_SERVICE` | Keep the user-enabled routing/monitoring service running in the background. |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Declare the connected-device foreground-service type. |
| `POST_NOTIFICATIONS` | Optional visibility of background controls; never required or automatically requested. |

There is **no** `INTERNET`, `RECORD_AUDIO`, camera, location, contacts, SMS, storage, accessibility, installation or root permission. Android’s `BIND_QUICK_SETTINGS_TILE` protects the exported tile service; it is a component access restriction, not an app-requested permission.

## Source, license and attribution

Maintained by **Roberto Jr Dev**. Released under the [Apache License 2.0](LICENSE).

Derived from [BTMicFix by Doug Lynch / Endda](https://github.com/Endda/BTMicFix), audited at commit `d38e6ccfb05d83ede27d99c68a4d2506073b9bd7`. The current implementation replaces the original UI and routing lifecycle, removes Shizuku and shell execution, and adds the keepalive, timer, tile and recording-status automation. See [NOTICE](NOTICE) for attribution. Original commits are retained for provenance; the published release contains no privileged implementation.

Bluetooth Mic Router is an independent project, with no affiliation with OpenAI, Samsung, Sony or the upstream author. It is intended for sideloading; it is not a Play Store listing.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Please include Android version, phone/headset model, manual vs automatic mode, session duration, and whether the system route changes when reporting an issue. Avoid posting recordings, Bluetooth addresses, full device logs or personal information.
