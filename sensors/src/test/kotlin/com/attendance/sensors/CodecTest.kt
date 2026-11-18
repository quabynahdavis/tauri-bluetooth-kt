package com.attendance.sensors

import com.attendance.sensors.codec.BeaconCodec
import com.attendance.sensors.codec.ChirpCodec
import com.attendance.sensors.codec.Crc16
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Random

class CodecTest {
    @Test
    fun crc16CcittFalseMatchesTheAsciiCheckValue() {
        val crc = Crc16.ccittFalse("123456789".toByteArray(Charsets.US_ASCII))
        assertEquals(0x29B1, crc)
    }

    @Test
    fun packAndUnpackBeaconBody() {
        val nonce = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val issuedAt = 1_700_000_000L
        val signature = ByteArray(64) { it.toByte() }
        val body = BeaconCodec.pack(nonce, issuedAt, signature)

        assertEquals(76, body.size)
        assertEquals(0x65, body[8].toInt() and 0xFF)
        assertEquals(0x53, body[9].toInt() and 0xFF)
        assertEquals(0xF1, body[10].toInt() and 0xFF)
        assertEquals(0x00, body[11].toInt() and 0xFF)

        val unpacked = BeaconCodec.unpack(body)
        assertArrayEquals(nonce, unpacked.nonce)
        assertEquals(issuedAt, unpacked.issuedAt)
        assertArrayEquals(signature, unpacked.signature)
    }

    @Test
    fun chunkThenReassembleOutOfOrder() {
        val nonce = byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2)
        val issuedAt = 0xFFFF_FFFFL
        val signature = ByteArray(64) { (255 - it).toByte() }
        val body = BeaconCodec.pack(nonce, issuedAt, signature)
        val chunks = BeaconCodec.chunkPayloads(body)

        assertEquals(4, chunks.size)
        assertEquals(listOf(22, 22, 22, 10), chunks.map { it.size - 3 })
        chunks.forEachIndexed { index, chunk ->
            assertEquals(BeaconCodec.CHUNK_TAG, chunk[0].toInt() and 0xFF)
            assertEquals(index, chunk[1].toInt() and 0xFF)
            assertEquals(4, chunk[2].toInt() and 0xFF)
        }

        val shuffled = listOf(chunks[2], chunks[0], chunks[3], chunks[1])
        assertArrayEquals(body, BeaconCodec.reassemble(shuffled))
    }

    @Test
    fun chirpRoundTripIncludingLeadingSilence() {
        val nonce = byteArrayOf(
            0x01, 0x23, 0x45, 0x67,
            0x89.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(),
        )
        val tone = ChirpCodec.synthesize(nonce)
        assertArrayEquals(nonce, ChirpCodec.decode(tone))

        val silence = 350
        val padded = ShortArray(silence + tone.size)
        tone.copyInto(padded, destinationOffset = silence)
        assertArrayEquals(nonce, ChirpCodec.decode(padded))
    }

    @Test
    fun silenceAndNoiseDoNotDecode() {
        assertNull(ChirpCodec.decode(ShortArray(ChirpCodec.frameSamples)))

        val noise = ShortArray(ChirpCodec.frameSamples + 240)
        val random = Random(42)
        for (i in noise.indices) {
            noise[i] = random.nextInt(65536).toShort()
        }
        assertNull(ChirpCodec.decode(noise))
    }
}
