# Security and privacy review

## Scope and verdict

The locally reviewed release is **safe enough to test** within its stated scope: public audio routing, a user-visible/background connected-device service, and anonymized recording-activity callbacks. This is a source and artifact review by the maintainer’s development workflow, not an independent certification or a guarantee against unknown defects.

The review covered every upstream tracked file and both upstream commits, build configuration, wrapper provenance, dependency verification, current Java code, source and merged manifests, lint, host timer/restart tests, final APK inventory, signing verification and local JADX/DEX inspection. No source or APK was uploaded to third-party scanning sites. Per-artifact results are published alongside releases.

The preceding routing implementation received user-reported functional confirmation on a Samsung Galaxy S23 running Android 16 with Sony WF-1000XM6 and ChatGPT Voice. The maintainer had no attached device for independent ADB/runtime/network/battery testing. New release UI/default changes require further user testing; they do not alter the working audio-route/keepalive/recording-session methods.

## Permissions and components

The final APK requests only `BLUETOOTH_CONNECT`, `MODIFY_AUDIO_SETTINGS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` and optional `POST_NOTIFICATIONS`.

- Launcher activity: exported, normal launcher and fixed Quick Settings preferences filters; incoming route/device/command extras are not used.
- Worker routing service: not exported, connected-device foreground service; only in-app requests and immutable notification intents reach it.
- Quick Settings tile service: exported as required by Android, protected by `android.permission.BIND_QUICK_SETTINGS_TILE`.
- No providers, receivers, boot handling, accessibility, device administrator or VPN components.

No INTERNET or RECORD_AUDIO permission exists. The app never captures audio, reads credentials/contacts/SMS, installs packages, loads native libraries, downloads code or runs commands. The only persistent data is the app-private routing preference file, excluded from backups.

## Original privileged code

Upstream Shizuku code offered a Binder method accepting a command string, prefix checks, then `sh -c`. Prefix checks do not prevent shell metacharacter injection if an attacker can submit a string. The audit found no reachable UI call site or demonstrated external Binder acquisition path, so an external exploit was not established. The hardened implementation removes the AIDL, Shizuku dependencies, permission flow, provider/service declarations and shell implementation entirely.

Upstream and local hardening history are retained for provenance. Read current release code, rather than assuming every historical revision has the current security properties.

## Dependencies, APK contents and URLs

There are no third-party runtime dependencies or native `.so` libraries. The only bundled assets are unchanged Apache 2.0 license text and the attribution NOTICE. DEX files contain the app’s platform-Java classes and generated resources.

Runtime network clients, sockets, telemetry, analytics, dynamic code loaders and shell execution were not found in the release. The source URL displayed as selectable text in About is informational; it is not loaded by a WebView, client or background task. Other APK URLs are Android XML namespaces and legal/license references in the bundled license/NOTICE. Documentation/build metadata contains upstream, Android, GitHub and official dependency-repository URLs; these are not app network activity.

## Limitations and follow-up testing

- Recording callbacks can reveal that another app is recording. The app observes transient metadata, not audio or identified app names; it does not store that metadata.
- Automatic mode can affect recordings outside ChatGPT and consume battery during routing. Five-minute default sessions limit continuous routing; the callback watcher can remain enabled afterwards.
- Android or a recording app can override routing, and Samsung power management can stop the app. Notification hiding does not grant immunity from those lifecycle rules.
- Device/profile changes, incoming cellular calls, Bluetooth disconnects, session expiry, screen-off behavior, and hidden-notification operation should be tested on each device.
- Route status cannot verify another app’s actual microphone source. Physically separate phone and headset and compare recordings with routing on and off.
- The timer does not require special alarm permission and is subject to Android scheduling; no exact wall-clock stop guarantee is claimed.
- Signing, manifest and source checks establish artifact integrity, not successful installation or function on every phone.

Please report security concerns through GitHub’s private vulnerability reporting where available. Do not post secrets, personal logs or recordings in public issues.
