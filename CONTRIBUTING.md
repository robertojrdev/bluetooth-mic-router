# Contributing

Start with [BUILDING](docs/BUILDING.md), [HOW-IT-WORKS](docs/HOW-IT-WORKS.md) and the [security review](docs/SECURITY-REVIEW.md).

Keep the app small and permission-minimal. Explain and review any new dependency, permission, exported component, recording-data use or background behavior. Do not add analytics, network access, privileged shell execution, root, Shizuku or audio capture to solve routing issues.

Run lint, assemble the APK, and run `SessionPolicyTest` before submitting changes. Inspect the final merged/APK manifest when changing dependencies or components. Add meaningful policy tests when changing timeout/restart behavior. Keep private signing keys, SDK paths, built APKs and device logs out of Git.

For bug reports, include phone/Android/headset model, app version, manual or automatic mode, timer duration, and observed route before/after the failure. Distinguish system route status from a physical microphone test. Do not publish Bluetooth addresses, recordings or unredacted device logs.

Contributions are provided under the repository’s Apache 2.0 license. Preserve upstream copyright and attribution notices.
