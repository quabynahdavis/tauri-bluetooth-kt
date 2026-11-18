# Ultrasonic chirp

The chirp carries the nonce only. It does not carry the signature.

Audio is 48000 Hz, 16-bit, mono, continuous-phase FSK. Bit 0 is a 17200 Hz tone and bit 1 is a 17800 Hz tone. Each bit lasts 20 ms, which is 960 samples. Phase continues across bit boundaries.

The decoder measures the two tones with Goertzel bins 344 and 356. At 960 samples and 48000 Hz, bin `k` sits at `k × 50` Hz, so bin 344 is 17200 Hz and bin 356 is 17800 Hz.

## Frame

The frame is 112 bits, sent in this order:

1. A 16-bit preamble, `01010101 01010101`.
2. A 16-bit sync word, `0xD3A5`.
3. The 64-bit nonce, most significant bit first.
4. CRC-16/CCITT-FALSE over the nonce, 16 bits, most significant bit first. The polynomial is `0x1021` and the initial value is `0xFFFF`.

The CRC covers the nonce. At 20 ms per bit the frame lasts 2.24 seconds.

## Decode

The decoder searches 16 symbol phases. A bit counts only when the louder tone has at least twice the power of the other tone. The CRC on the recovered nonce must match. A candidate that fails the tone test or the CRC is not a completed chirp.

## Playback and capture

Playback uses media usage and loops the frame. It does not use the voice-call stream.

Capture prefers `UNPROCESSED`. When that source is not available, capture uses `MIC`.

## API

- `startChirp(nonce)` starts looped playback of the frame for that nonce.
- `stopChirp` stops playback.
- `startListening` starts capture and decode.
- `stopListening` stops capture.
- `latestChirp` is the latest nonce whose frame decoded with a matching CRC.

A missing permission throws `SensorException`. Listening requires the host application to have granted `RECORD_AUDIO`.
