package com.attendance.sensors

import com.attendance.sensors.codec.BeaconCodec
import com.attendance.sensors.codec.ChirpCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The air format is a pure function of the nonce. Android 9 and Android 14 run
 * the same code, so these checks are the cross-version contract we can prove
 * without a phone.
 */
class SameOnEveryAndroidTest {
    @Test
    fun chunksFitInsideALegacyAdvertisement() {
        val chunks = BeaconCodec.chunkPayloads(body())
        assertEquals(4, chunks.size)
        chunks.forEach { chunk ->
            assertTrue(
                "chunk is ${chunk.size} bytes, legacy manufacturer data holds 27",
                chunk.size <= LEGACY_MANUFACTURER_PAYLOAD,
            )
            assertEquals(BeaconCodec.CHUNK_TAG, chunk[0].toInt() and 0xFF)
        }
    }

    @Test
    fun extendedPacketDoesNotFitInALegacyAdvertisement() {
        val extended = BeaconCodec.extendedPayload(body())
        assertEquals(77, extended.size)
        assertTrue(extended.size > LEGACY_MANUFACTURER_PAYLOAD)
    }

    @Test
    fun theSameNonceAlwaysRebuildsTheSameBeacon() {
        val first = BeaconCodec.chunkPayloads(body())
        val second = BeaconCodec.chunkPayloads(body())
        assertEquals(first.size, second.size)
        first.zip(second).forEach { (left, right) -> assertArrayEquals(left, right) }

        val assembler = BeaconCodec.Assembler()
        assertNull(assembler.add(first[0]))
        assertNull(assembler.add(first[1]))
        assertNull(assembler.add(first[2]))
        assertArrayEquals(body(), assembler.add(first[3]))
    }

    @Test
    fun theSameNonceAlwaysRebuildsTheSameChirp() {
        val first = ChirpCodec.synthesize(NONCE)
        val second = ChirpCodec.synthesize(NONCE)
        assertEquals(ChirpCodec.SAMPLE_RATE, 48_000)
        assertEquals(ChirpCodec.FREQ_ZERO, 17_200)
        assertEquals(ChirpCodec.FREQ_ONE, 17_800)
        assertEquals(first.size, ChirpCodec.frameSamples)
        assertArrayEquals(first, second)
        assertArrayEquals(NONCE, ChirpCodec.decode(first))
    }

    private fun body(): ByteArray {
        return BeaconCodec.pack(NONCE, ISSUED_AT, SIGNATURE)
    }

    private companion object {
        // 31-byte legacy PDU minus length, type, and company id.
        const val LEGACY_MANUFACTURER_PAYLOAD = 27
        const val ISSUED_AT = 1_700_000_000L
        val NONCE = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val SIGNATURE = ByteArray(64) { it.toByte() }
    }
}
