# BLE beacon

The beacon is manufacturer data with company id `0xFFFF`. The body is 76 bytes, laid out in this order:

1. Nonce, 8 bytes.
2. `issuedAt`, an unsigned 32-bit integer, 4 bytes, big-endian.
3. Signature, 64 bytes.

The caller supplies the nonce, the issued-at value, and the signature. This library does not sign the beacon.

## Extended advertising

When the controller supports extended advertising, the manufacturer payload is the tag byte `0x01` followed by the 76-byte body. The advertisement is non-connectable. Transmit power is high, the interval is low latency, and legacy mode is off.

## Chunk fallback

When extended advertising is not available, the same body is sent in chunks. Each chunk is the tag byte `0x02`, then the chunk index, then the chunk count, then at most 22 bytes of the body. Chunks are filled to 22 body bytes until the remainder, which occupies the last chunk. A 76-byte body therefore uses four chunks: three of 22 bytes and one of 10 bytes. The advertiser advances to the next chunk about every 200 ms.

## Scan

The scanner uses low-latency mode. On API 31 and newer it disables legacy scan results so an extended payload stays intact. Chunks are reassembled per device until the body is complete. The completed beacon includes the RSSI observed for that beacon.

## API

- `startAdvertising(nonce, issuedAt, signature)` advertises the 76-byte body. It uses the extended form when the controller supports it, and the chunk form otherwise.
- `stopAdvertising` stops advertising.
- `startScan` starts the scanner.
- `stopScan` stops the scanner.
- `latestBeacon` is the latest completed beacon, including its RSSI.

A missing Bluetooth permission throws `SensorException`. The host application is responsible for granting `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN`, and `BLUETOOTH_CONNECT`.
