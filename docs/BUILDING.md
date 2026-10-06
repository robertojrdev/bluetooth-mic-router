# Build and verify

Use JDK 17, Android SDK platform 35 / build-tools 35.0.0, and the checked-in Gradle 8.9 wrapper. Put your SDK path in an untracked `local.properties` (`sdk.dir=...`) or set `ANDROID_HOME`.

```sh
./gradlew --dependency-verification=strict clean assembleDebug lint
./gradlew --dependency-verification=strict :app:dependencies
```

Debug output: `app/build/outputs/apk/debug/app-debug.apk`. The public application ID is `io.github.robertojrdev.bluetoothmicrouter`. The minimum SDK is 31 (Android 12); target/compile SDK is pinned to 35. Lint treats warnings as errors; only `OldTargetApi` is disabled for this pinned sideload build.

Application code uses the Android/Java platform only, with **zero third-party runtime dependencies**. Build-time Android Gradle Plugin 8.7.3 and its transitive tooling resolve through the declared Google, Maven Central and Gradle plugin repositories. Dependency checksums are pinned in `gradle/verification-metadata.xml`; do not silently regenerate them when verification fails.

The wrapper downloads from the official Gradle distribution host and pins its distribution checksum. Audited wrapper JAR SHA-256: `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`.

Run the host policy checks with no Android device or extra libraries:

```sh
mkdir -p /tmp/bmr-policy-tests
javac -d /tmp/bmr-policy-tests app/src/main/java/com/btmicfix/SessionDeadline.java app/src/main/java/com/btmicfix/AutomaticPolicy.java tests/SessionPolicyTest.java
java -cp /tmp/bmr-policy-tests com.btmicfix.SessionPolicyTest
```

For a release, `assembleRelease` produces an unsigned APK unless local signing is configured. Generate your own key **outside the repository**. Set `BMR_SIGNING_STORE` to its absolute path and `BMR_SIGNING_PASSWORD_FILE` to a private file containing its password. The key alias must be `bluetooth-mic-router`. The official release key is kept privately by the maintainer and is not included in source or CI.

```sh
./gradlew --dependency-verification=strict clean assembleRelease lint
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
apkanalyzer manifest permissions app/build/outputs/apk/release/app-release.apk
apkanalyzer manifest print app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
jadx -d /tmp/bmr-jadx app/build/outputs/apk/release/app-release.apk
unzip -l app/build/outputs/apk/release/app-release.apk
```

Debug and release certificates differ. Do not expect a self-built debug APK to update an official release install. Official releases use a separate package from earlier private BTMicFix test builds. For those private installs only, a debug build with `-PlocalApplicationId=com.btmicfix.local` retains their package identity when signed with the same local debug key. Stop routing in the old app before enabling a separately installed official release.

Check the release’s checksum and signing certificate against its published verification report. Builds may differ byte-for-byte across environments due to packaging/signing metadata; compare manifests, DEX and resources when investigating differences.
