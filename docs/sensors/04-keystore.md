# Keystore

Rust, later and not in this repository, generates the Ed25519 seed and signs. This library only stores the seed. It does not generate the seed and it does not sign.

The Android Keystore holds a non-exportable AES-256-GCM key. The library uses that key to wrap and unwrap the seed.

`protectSeed` takes a 32-byte seed, wraps it with the key, and stores the IV followed by the ciphertext in private SharedPreferences.

`revealSeed` reads that stored value and unwraps the seed for the caller. If `revealSeed` runs before `protectSeed` has stored a seed, it throws.
