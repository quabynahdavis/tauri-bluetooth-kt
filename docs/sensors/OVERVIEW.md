# Sensors

This folder is the contract for the Android library module `:sensors` (`com.attendance.sensors`). The module advertises and scans a signed BLE beacon, plays and listens for an ultrasonic chirp of a nonce, and stores a 32-byte Ed25519 seed wrapped by the Android Keystore.

The host application requests runtime permissions and calls the public functions. The library declares the permissions in its manifest. A missing permission throws `SensorException`.

## Contents

- [01-setup.md](01-setup.md) covers the Gradle module, the SDK location, permissions, and the test command.
- [02-ble-beacon.md](02-ble-beacon.md) covers the beacon body, extended advertising, the chunk fallback, and scanning.
- [03-ultrasonic-chirp.md](03-ultrasonic-chirp.md) covers the FSK frame, decode rules, playback, and capture.
- [04-keystore.md](04-keystore.md) covers wrapping and revealing the Ed25519 seed.
- [CHANGELOG.md](CHANGELOG.md) records revisions of the documents in this folder.
