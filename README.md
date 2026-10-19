# Air Remote

An experimental Android app with one button that triggers a short vibration on a paired **Google Fitbit Air**.

Pair the Air through Google Health first. Then open Air Remote, allow Nearby devices access, and tap **Vibrate**. The command runs on the phone over Bluetooth; a computer and an API are not needed for normal use. The current selector expects exactly one paired device named `Google Fitbit Air`.

**Google Health connection limitation:** Air Remote opens its own temporary Gattlink/DTLS session. It does not disable, force-stop, or unpair Google Health, but it can interrupt or delay Health's Bluetooth session and syncing. Uninterrupted coexistence has **not** been verified. If that is required, this version does not meet that requirement yet.

## What it does

- Finds the already-paired device named `Google Fitbit Air`.
- Sends a bounded vibration-preview command through Golden Gate, DTLS, and CoAP.
- Restores the original vibration intensity and checks it on successful completion.
- Shows progress and the result; runs the command in a foreground service with a temporary wake lock.
- Keeps device selection and command results in private app storage.

This uses the band's intensity-change previews; it is not arbitrary motor waveform control. Connection retries may delay delivery or repeat a preview. A disconnect during a command can prevent restoration from being confirmed.

## Build

Requirements: Git, Python 3.9+, JDK 11, and the Android SDK components listed in [Building](docs/BUILDING.md). The native build currently targets **ARM64 only**. The app requires **Android 12+** and targets Android 16 runtime rules.

Set `JAVA_HOME` to JDK 11 and `ANDROID_HOME` to the Android SDK, then run:

```sh
python scripts/build.py
```

This fetches the pinned transport sources, compiles the native library, runs app unit tests, and builds:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install that debug APK on your own Android phone, pair the Air through the normal device setup, and open Air Remote. See [Building](docs/BUILDING.md) for Windows commands and Android Studio setup.

## Project layout

```text
app/                  Android app, resources, and protocol tests
scripts/              Portable dependency and native-build helpers
docs/                 Build, architecture, and testing notes
third_party/notices/  Preserved upstream license notices
dependencies.lock.json  Exact transport and native dependency revisions
.deps/                Downloaded upstream sources (generated, ignored by Git)
```

## Status and scope

The original prototype was tested on one Pixel 10 Pro with one Fitbit Air. Vibration was physically confirmed. Commands also succeeded with Google Health running and with the screen off. Those checks did **not** establish uninterrupted Google Health connectivity, long-term reliability, or compatibility with other firmware and phones.

This repository extracts and refactors that prototype into a standalone project. See [Testing](docs/TESTING.md) for the distinction between prototype hardware observations and the checks on this source tree. There is no network/API listener, boot receiver, or notification-listener service.

## Contributing and license

Start with [Architecture](docs/ARCHITECTURE.md) and [Contributing](CONTRIBUTING.md). This project derives from [Fitbit Air Notification Bridge](https://github.com/paceaitian/fitbit-air-notification-bridge) and Fitbit's [Golden Gate](https://github.com/Fitbit/golden-gate). It is unofficial and is not affiliated with Google or Fitbit.

Licensed under [Apache-2.0](LICENSE). Preserve [NOTICE](NOTICE) and the [third-party notices](THIRD_PARTY.md) when redistributing.
