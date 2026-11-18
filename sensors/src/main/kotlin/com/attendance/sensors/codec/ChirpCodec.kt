package com.attendance.sensors.codec

import kotlin.math.cos
import kotlin.math.sin

object ChirpCodec {
    const val SAMPLE_RATE = 48_000
    const val SAMPLES_PER_BIT = 960
    const val FRAME_BITS = 112
    const val PHASES = 16
    const val NONCE_BYTES = 8
    const val FREQ_ZERO = 17_200
    const val FREQ_ONE = 17_800
    const val BIN_ZERO = 344
    const val BIN_ONE = 356

    val frameSamples: Int = FRAME_BITS * SAMPLES_PER_BIT

    private const val PREAMBLE = 0x5555
    private const val SYNC = 0xD3A5
    private const val AMPLITUDE = 12_000.0

    // A real tone lands many orders of magnitude above this. Exact silence does not.
    private const val MIN_TOTAL_POWER = 1.0e6

    fun synthesize(nonce: ByteArray): ShortArray {
        require(nonce.size == NONCE_BYTES) { "nonce must be 8 bytes" }
        val bits = frameBits(nonce)
        val samples = ShortArray(bits.size * SAMPLES_PER_BIT)
        var phase = 0.0
        var index = 0
        for (bit in bits) {
            val frequency = if (bit) FREQ_ONE else FREQ_ZERO
            val delta = 2.0 * Math.PI * frequency / SAMPLE_RATE
            repeat(SAMPLES_PER_BIT) {
                samples[index++] = (sin(phase) * AMPLITUDE).toInt().toShort()
                phase += delta
                if (phase >= 2.0 * Math.PI) phase -= 2.0 * Math.PI
            }
        }
        return samples
    }

    fun decode(samples: ShortArray): ByteArray? {
        if (samples.size < frameSamples) return null
        val step = SAMPLES_PER_BIT / PHASES
        for (phase in 0 until PHASES) {
            val offset = phase * step
            val available = samples.size - offset
            if (available < frameSamples) continue
            val symbolCount = available / SAMPLES_PER_BIT
            val bits = arrayOfNulls<Boolean>(symbolCount)
            for (symbol in 0 until symbolCount) {
                bits[symbol] = readBit(samples, offset + symbol * SAMPLES_PER_BIT)
            }
            for (start in 0..symbolCount - FRAME_BITS) {
                decodeSymbols(bits, start)?.let { return it }
            }
        }
        return null
    }

    private fun frameBits(nonce: ByteArray): BooleanArray {
        val bits = BooleanArray(FRAME_BITS)
        var position = 0
        fun write(value: Int, width: Int) {
            for (shift in width - 1 downTo 0) {
                bits[position++] = ((value ushr shift) and 1) == 1
            }
        }
        write(PREAMBLE, 16)
        write(SYNC, 16)
        for (byte in nonce) {
            write(byte.toInt() and 0xFF, 8)
        }
        write(Crc16.ccittFalse(nonce), 16)
        check(position == FRAME_BITS)
        return bits
    }

    private fun decodeSymbols(bits: Array<Boolean?>, start: Int): ByteArray? {
        for (index in 0 until FRAME_BITS) {
            if (bits[start + index] == null) return null
        }
        if (readBits(bits, start, 16) != PREAMBLE) return null
        if (readBits(bits, start + 16, 16) != SYNC) return null
        val nonce = ByteArray(NONCE_BYTES)
        for (byteIndex in nonce.indices) {
            nonce[byteIndex] = readBits(bits, start + 32 + byteIndex * 8, 8).toByte()
        }
        val crc = readBits(bits, start + 32 + NONCE_BYTES * 8, 16)
        if (crc != Crc16.ccittFalse(nonce)) return null
        return nonce
    }

    private fun readBit(samples: ShortArray, offset: Int): Boolean? {
        val zero = goertzelPower(samples, offset, BIN_ZERO)
        val one = goertzelPower(samples, offset, BIN_ONE)
        val total = zero + one
        val louder = maxOf(zero, one)
        val quieter = minOf(zero, one)
        if (total < MIN_TOTAL_POWER || louder < quieter * 2.0) return null
        return one > zero
    }

    private fun goertzelPower(samples: ShortArray, offset: Int, bin: Int): Double {
        val coefficient = 2.0 * cos(2.0 * Math.PI * bin / SAMPLES_PER_BIT)
        var previous = 0.0
        var beforePrevious = 0.0
        for (i in 0 until SAMPLES_PER_BIT) {
            val sample = samples[offset + i].toDouble()
            val next = sample + coefficient * previous - beforePrevious
            beforePrevious = previous
            previous = next
        }
        return previous * previous + beforePrevious * beforePrevious - coefficient * previous * beforePrevious
    }

    private fun readBits(bits: Array<Boolean?>, offset: Int, width: Int): Int {
        var value = 0
        for (i in 0 until width) {
            value = (value shl 1) or if (bits[offset + i] == true) 1 else 0
        }
        return value
    }
}
