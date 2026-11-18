# BLE beacon

The beacon is manufacturer data with company id `0xFFFF`. The body is 76 bytes, laid out in this order:

1. Nonce, 8 bytes.
2. `issuedAt`, an unsigned 32-bit integer, 4 bytes, big-endian.
3. Signature, 64 bytes.

The caller supplies the nonce, the issued-at value, and the signature. This library does not sign the beacon.

## Advertising

Every phone sends the same legacy advertisement, from Android 9 upward. A legacy packet cannot hold the 76-byte body, so the body is split into chunks. Each chunk is the tag byte `0x02`, then the chunk index, then the chunk count, then at most 22 bytes of the body. A 76-byte body uses four chunks: three of 22 bytes and one of 10 bytes. The advertiser advances to the next chunk about every 200 ms. The advertisement is non-connectable, high power, and low latency.

## Scan

Every phone scans in low-latency mode with the same settings. Chunks are reassembled per device until the body is complete. The completed beacon includes the RSSI observed for that beacon.

## API

- `startAdvertising(nonce, issuedAt, signature)` advertises the 76-byte body as legacy chunks on every supported Android version.
- `stopAdvertising` stops advertising.
- `startScan` starts the scanner.
- `stopScan` stops the scanner.
- `latestBeacon` is the latest completed beacon, including its RSSI.

A missing permission throws `SensorException`. From Android 12 the host grants `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN`, and `BLUETOOTH_CONNECT`. On Android 9 through 11 the host grants `ACCESS_FINE_LOCATION` and leaves location services on. Advertising on those versions uses the install-time `BLUETOOTH` and `BLUETOOTH_ADMIN` permissions.
