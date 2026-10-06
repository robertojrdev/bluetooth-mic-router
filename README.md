# BTMicFix Keepalive (local v4 test build)

Locally modified from Endda/BTMicFix commit d38e6ccfb05d83ede27d99c68a4d2506073b9bd7.
Upstream copyright/license remain in LICENSE. This branch uses Android/Java platform
APIs only. Shizuku, shell execution, runtime dependencies, companion association and
boot startup are removed.

## Use

Connect a call-capable headset. Open the app once to grant Nearby devices and
notification permissions, then tap Add Quick Settings tile. Accept Android's prompt,
or edit the Quick Settings panel and add Headset mic. The tile toggles routing;
long press opens Settings. Default manual duration: five minutes. Settings offers
1, 5, 10, 15, 30 or 60 minutes, or Until I stop. The notification offers Stop,
Add 5 minutes and Reapply headset. Reapply preserves the deadline; extension adds
five minutes to that deadline. Settings changes apply to the next session.

Choose a preferred headset for the tile. With no preference and multiple devices,
manual tile activation asks; automatic mode waits for one unambiguous headset.
A missing preferred headset never falls back to another device. Preferences store
only duration, automatic default and selected headset address/name/profile privately.
Recording events/audio are not saved. Backup and device transfer remain disabled.

## Optional automatic mode (experimental)

Choose a headset and enable Automatic when recording starts in Settings. Save arms
the foreground service; Stop/the tile disables it. The preference remains the default
tile mode, but opening the launcher, boot or process death never starts it. Tap the
tile to arm it again after Stop/reboot.

AudioManager.AudioRecordingCallback reports anonymized recording metadata through a
public API. The app checks unsilenced microphone-source activity only. It receives
no audio, does not read the green indicator, identify the recording app or save
these events. Playback capture and phone-call capture sources are excluded. It may
react to other apps such as dictation and camera recording; it cannot restrict this
to ChatGPT from anonymized metadata alone.

While armed, a notification and metadata/device callbacks remain active. There is
no silent playback or mode request while idle. Recording start selects the preferred
or unambiguous headset and begins the unchanged silent keepalive from working v3.
Five seconds after recording ends it releases routing and returns to listening.
The chosen duration is also a maximum recording session; after timeout/failure it
waits for an inactive recording state before another attempt. Telephone/ringing
modes are not overridden. An existing MODE_IN_COMMUNICATION session may be rerouted
in automatic mode, as requested. Cleanup uses MODE_NORMAL to release this UID's
request without adopting another app's request.

Automatic detection and rerouting after capture starts need S23 testing. The
recording app may keep its chosen input; Samsung may restrict events/background
services. Manual mode remains available. Compare phone/headset pickup before relying
on this experimental option.

## Battery, privacy and timer

Routing plays a looping, one-second, all-zero AudioTrack voice buffer, as in v3.
It never records audio. Stop, timeout, disconnect, failure, service destruction and
automatic recording-end release the track. SCO/HFP can reduce media quality and
use additional phone/headset battery.

Timers use elapsed real time, an in-process AlarmManager listener and Handler
fallback; both are canceled on cleanup. Listener alarms require no exact-alarm
permission. They do not survive process death, which also ends playback/routing.
Android sleep/battery restrictions can delay callbacks: this is a convenience timer,
not a guaranteed exact deadline in all Samsung power modes. No wake lock or battery
exemption is requested. UI countdown refreshes run only while the app is visible;
Android's notification chronometer displays remaining time without service polling.

Route acceptance does not establish another app's actual microphone. The app shows
system device/mode, and Android/other audio owners can override it. No automatic
reapply or mode-setting retry loop is used.

Package com.btmicfix.local, versionCode 4, updates v3 with the same local debug key.
Android 12+. Exactly five permissions: BLUETOOTH_CONNECT, MODIFY_AUDIO_SETTINGS,
FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE, POST_NOTIFICATIONS.
No INTERNET, RECORD_AUDIO, root, ADB or Shizuku. The new exported tile service is
protected by system-only BIND_QUICK_SETTINGS_TILE. Launcher input cannot route;
the fixed tile-preferences action only opens Settings.

## Build and checks

JDK17 and SDK35; untracked local.properties contains the SDK path. Gradle8.9 official
wrapper/distribution checksums and dependency checksums are verified. Official build
plugin com.android.application 8.7.3, no runtime dependencies.

    ./gradlew --offline --dependency-verification=strict clean assembleDebug lint

Host tests compile SessionDeadline.java, AutomaticPolicy.java and tests/SessionPolicyTest.java
with javac, then run com.btmicfix.SessionPolicyTest. They test deadlines, extension,
invalid durations, indefinite sessions and timeout suppression/rearming.
APK: app/build/outputs/apk/debug/app-debug.apk. Local Android debug key only.
Final manifest, DEX, native contents and signing are independently checked. Delivery
uses a ZIP of the unchanged APK over private Tailscale HTTPS with attachment and
resumable-download headers.
