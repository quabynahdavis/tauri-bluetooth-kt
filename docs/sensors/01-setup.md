# Setup

`:sensors` is an Android library module. The Android Gradle Plugin is 9.5 and includes Kotlin, so the module is built with that built-in Kotlin support. Gradle is 9.6. `compileSdk` is 37 and `minSdk` is 26. The language target is JDK 17. The machine build uses OpenJDK 25 to run Gradle. OpenJDK 25 is the Gradle runtime, and JDK 17 remains the language target.

The namespace is `com.attendance.sensors`.

## Android SDK

The SDK lives in the project-local directory `plugins/.sdk`. That directory is gitignored. `local.properties` sets `sdk.dir` to this directory, and `local.properties` is gitignored.

`/opt/android-sdk/platforms` is empty, so the build does not take its platforms from that tree. `~/Android` is not the SDK location for this project. Command-line tools are installed at `/opt/android-sdk/cmdline-tools` and are what populate `plugins/.sdk`, including the Android 37 platform that `compileSdk` 37 compiles against.

## Tests

From `plugins/`, the test command is:

```
./gradlew :sensors:test
```

## Permissions

The library manifest declares these permissions:

- `BLUETOOTH_ADVERTISE`
- `BLUETOOTH_SCAN`, with the `neverForLocation` flag
- `BLUETOOTH_CONNECT`
- `RECORD_AUDIO`

The library lists them. It does not request them. The host application requests them at runtime. If a call needs a permission the host has not granted, the library throws `SensorException`.
