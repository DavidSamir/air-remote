# Architecture

## Command path

```text
MainActivity: one Vibrate button
  -> VibrationService: foreground, bounded queue and retries
    -> BluetoothRuntime: initialize Golden Gate once per process
    -> register the already-bonded Air address
    -> Golden Gate Gattlink + BOOTSTRAP DTLS + CoAP
      -> VibrationExecutor
        -> SettingsProtocol
        -> Fitbit Air /settings
    -> close this app's peer and release wake lock
  -> DeviceStore result -> MainActivity progress/result
```

## Files to start with

| File | Responsibility |
| --- | --- |
| `MainActivity.kt` | Native Android UI, permission flow, paired-band selection, result display |
| `VibrationService.kt` | Temporary connection lifecycle, foreground notification, queue, retry policy |
| `BluetoothRuntime.kt` | Process-wide transport initialization |
| `DeviceStore.kt` | Private device address and result storage |
| `VibrationCommand.kt` | Command and pattern data |
| `VibrationExecutor.kt` | Read, change, restore, and verify vibration intensity |
| `SettingsProtocol.kt` | Minimal protobuf encoding/decoding for `/settings` |
| `AirRemoteApplication.kt` | Debug logging and Rx cancellation error handling |

## What produces the vibration

The app does not have a public raw-motor API. It uses the Air's short vibration preview when its intensity changes:

1. `PUT /settings` reads the current haptics intensity.
2. `POST /settings` selects the other supported intensity.
3. After 800 ms, another `POST /settings` restores the original intensity.
4. A read-back verifies restoration before reporting success.

A single group can therefore produce two previews. The UI sends one group. The command model retains double/triple groups as extension points, but only the one-button path is exposed in the app.

The executor rejects an initial intensity of OFF rather than silently enabling vibration. A failure after the first write can leave the original setting unconfirmed. The service retries the command with bounded delays; this can repeat a preview. These semantics should be considered before changing retry behavior.

## Google Health coexistence

Keeping Google Health's package enabled is different from preserving its active protocol session. This app opens its own session and closes its own peer. It contains no code to force-stop, disable, or unpair Google Health. Nevertheless, the band's transport can be contested, and Health syncing may pause, fail, or reconnect. The app displays that limitation.

Do not claim uninterrupted coexistence based only on successful vibration, an enabled Health package, or a running Health process. Establishing that behavior requires observing Health's connection and successful syncs before, during, and after repeated commands, including active-sync and idle scenarios.

## Background behavior

An in-progress command uses a foreground service and a temporary partial wake lock, so the command can continue when the screen turns off. The service stops when its queue is empty. This is not an always-listening background app. No network receiver, API listener, boot startup, or notification-reading access is implemented.

The exported launcher also accepts a development intent with `pattern` and optional `request_id` extras. This is a local Android command entry point, not an authenticated remote API. The service is not exported. Keep this distinction if adding external command reception.

## Dependency boundary

App code lives under `app/`. The pinned transport is fetched to `.deps/golden-gate`, including its native dependencies. Gradle maps four upstream library modules into the build; the upstream demo app and remote API module are not included. The only bootstrap source adjustment is 16 KiB alignment of the JNI shared library; the native core gets the corresponding linker flag from `build_native.py`.
