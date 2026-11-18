# Documentation

This repository is the Android sensor library. The library is the Gradle module `:sensors`, and its namespace is `com.attendance.sensors`. These documents are the contract for that library.

The Go server and the Tauri app are out of scope. They are not in this repository. A later Rust component, also outside this repository, generates the Ed25519 seed and produces signatures. This library stores that seed and carries beacon and chirp bytes.

## Contents

- [CHANGELOG.md](CHANGELOG.md) records revisions of the documents in this folder.
- [sensors/](sensors/OVERVIEW.md) is the contract for module `:sensors`: setup, the BLE beacon, the ultrasonic chirp, and seed storage.
