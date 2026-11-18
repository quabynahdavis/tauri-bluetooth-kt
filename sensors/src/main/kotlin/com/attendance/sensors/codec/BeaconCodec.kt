package com.attendance.sensors.codec

object BeaconCodec {
    const val BODY_BYTES = 76
    const val NONCE_BYTES = 8
    const val SIGNATURE_BYTES = 64
    const val COMPANY_ID = 0xFFFF
    const val EXTENDED_TAG = 0x01
    const val CHUNK_TAG = 0x02
    const val CHUNK_BODY_BYTES = 22

    class Body(
        val nonce: ByteArray,
        val issuedAt: Long,
        val signature: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Body) return false
            return issuedAt == other.issuedAt &&
                nonce.contentEquals(other.nonce) &&
                signature.contentEquals(other.signature)
        }

        override fun hashCode(): Int {
            var result = nonce.contentHashCode()
            result = 31 * result + issuedAt.hashCode()
            result = 31 * result + signature.contentHashCode()
            return result
        }
    }

    fun pack(nonce: ByteArray, issuedAt: Long, signature: ByteArray): ByteArray {
        require(nonce.size == NONCE_BYTES) { "nonce must be 8 bytes" }
        require(signature.size == SIGNATURE_BYTES) { "signature must be 64 bytes" }
        require(issuedAt in 0L..0xFFFF_FFFFL) {
            "issuedAt must fit in an unsigned 32-bit second timestamp"
        }
        val body = ByteArray(BODY_BYTES)
        nonce.copyInto(body, destinationOffset = 0)
        body[8] = (issuedAt ushr 24).toByte()
        body[9] = (issuedAt ushr 16).toByte()
        body[10] = (issuedAt ushr 8).toByte()
        body[11] = issuedAt.toByte()
        signature.copyInto(body, destinationOffset = 12)
        return body
    }

    fun unpack(body: ByteArray): Body {
        require(body.size == BODY_BYTES) { "beacon body must be 76 bytes" }
        val nonce = body.copyOfRange(0, NONCE_BYTES)
        val issuedAt =
            ((body[8].toLong() and 0xFF) shl 24) or
                ((body[9].toLong() and 0xFF) shl 16) or
                ((body[10].toLong() and 0xFF) shl 8) or
                (body[11].toLong() and 0xFF)
        val signature = body.copyOfRange(12, BODY_BYTES)
        return Body(nonce, issuedAt, signature)
    }

    fun extendedPayload(body: ByteArray): ByteArray {
        require(body.size == BODY_BYTES) { "beacon body must be 76 bytes" }
        val payload = ByteArray(1 + body.size)
        payload[0] = EXTENDED_TAG.toByte()
        body.copyInto(payload, destinationOffset = 1)
        return payload
    }

    fun chunkPayloads(body: ByteArray): List<ByteArray> {
        require(body.size == BODY_BYTES) { "beacon body must be 76 bytes" }
        val count = (body.size + CHUNK_BODY_BYTES - 1) / CHUNK_BODY_BYTES
        return List(count) { index ->
            val start = index * CHUNK_BODY_BYTES
            val end = minOf(start + CHUNK_BODY_BYTES, body.size)
            val slice = body.copyOfRange(start, end)
            ByteArray(3 + slice.size).also { payload ->
                payload[0] = CHUNK_TAG.toByte()
                payload[1] = index.toByte()
                payload[2] = count.toByte()
                slice.copyInto(payload, destinationOffset = 3)
            }
        }
    }

    fun reassemble(payloads: Iterable<ByteArray>): ByteArray {
        val assembler = Assembler()
        for (payload in payloads) {
            val body = assembler.add(payload)
            if (body != null) return body
        }
        throw IllegalArgumentException("beacon chunks are incomplete")
    }

    class Assembler {
        private val parts = HashMap<Int, ByteArray>()
        private var count: Int? = null

        fun add(payload: ByteArray): ByteArray? {
            if (payload.size < 4 || (payload[0].toInt() and 0xFF) != CHUNK_TAG) return null
            val index = payload[1].toInt() and 0xFF
            val total = payload[2].toInt() and 0xFF
            if (total <= 0 || index >= total) return null
            val data = payload.copyOfRange(3, payload.size)
            if (data.isEmpty() || data.size > CHUNK_BODY_BYTES) return null
            if (count != null && count != total) {
                parts.clear()
            }
            count = total
            parts[index] = data
            if ((0 until total).any { parts[it] == null }) return null
            var size = 0
            for (i in 0 until total) {
                size += parts.getValue(i).size
            }
            if (size != BODY_BYTES) return null
            val body = ByteArray(BODY_BYTES)
            var offset = 0
            for (i in 0 until total) {
                val chunk = parts.getValue(i)
                chunk.copyInto(body, destinationOffset = offset)
                offset += chunk.size
            }
            parts.clear()
            count = null
            return body
        }
    }
}
