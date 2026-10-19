# Testing

## Automated checks

```sh
python scripts/build.py
```

This compiles the native ARM64 library and Android app, and runs app unit tests. The protocol tests cover request/response encoding, parsing, unknown fields, truncated payloads, and unsupported intensity values. They run on the JVM and do not require a band.

Build output: `app/build/outputs/apk/debug/app-debug.apk`.
Test reports: `app/build/reports/tests/testDebugUnitTest/`.

## Hardware checks for changes to Bluetooth behavior

Use your own paired Fitbit Air and record the phone model, Android version, band firmware, and results without publishing device addresses or account data.

1. Open Air Remote, allow Nearby devices access, and tap Vibrate once.
2. Confirm a wrist vibration, a successful UI result, and that the intensity is restored.
3. Try a command with the band out of range; check that failure is displayed and another command remains possible.
4. Start a command, turn the screen off, and check completion afterward.
5. Check permissions denied and Bluetooth off; verify useful messages.
6. Test repeated commands and activity recreation without duplicate sends or stuck progress.

For coexistence work, additionally observe Google Health's actual connection and completed syncs before/during/after commands, both idle and actively syncing. An enabled package or running process is insufficient evidence of uninterrupted connectivity.

## Observed prototype results

The earlier prototype was tested on one Pixel 10 Pro and one Fitbit Air on 2026-10-19. A vibration was physically confirmed; commands also succeeded with Google Health running and with the screen off. No claim of uninterrupted Health connectivity follows from those observations. Band firmware was not recorded, so the result is not a compatibility matrix.

The standalone package refactors and renames that app and removes unused code. Do not imply a new hardware run merely because a refactor compiles.

## Standalone source validation

On 2026-10-19, the standalone project was checked on Windows with the pinned JDK 11 / Android toolchain:

- All six JVM protocol tests passed, with no failures, errors, or skipped tests.
- The native ARM64 libraries and debug APK built successfully (`:app:assembleDebug`).
- Kotlin formatting, Python syntax, XML parsing, local documentation links, and the Gradle wrapper checksum were checked.
- The source export was checked to exclude downloaded dependencies, build output, private device identifiers, logs, local SDK settings, and signing keys.

The renamed `dev.airremote` package has not had a fresh on-device hardware test. Linux/macOS and the GitHub Actions workflow have not been executed in this session.

## Privacy of test artifacts

Keep device logs, screenshots of Health, extracted APKs, Bluetooth addresses, account information, and local signing keys out of commits and issue attachments. The project excludes generated build and dependency directories. A useful issue report includes a sanitized error and the relevant software versions.
