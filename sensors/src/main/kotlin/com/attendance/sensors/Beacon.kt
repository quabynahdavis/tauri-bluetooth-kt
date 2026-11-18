package com.attendance.sensors

class Beacon(
    nonce: ByteArray,
    val issuedAt: Long,
    signature: ByteArray,
    val rssi: Int,
    val deviceAddress: String,
) {
    val nonce: ByteArray = nonce.copyOf()
    val signature: ByteArray = signature.copyOf()

    init {
        require(this.nonce.size == 8) { "nonce must be 8 bytes" }
        require(this.signature.size == 64) { "signature must be 64 bytes" }
        require(issuedAt in 0L..0xFFFF_FFFFL) {
            "issuedAt must fit in an unsigned 32-bit second timestamp"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Beacon) return false
        return issuedAt == other.issuedAt &&
            rssi == other.rssi &&
            deviceAddress == other.deviceAddress &&
            nonce.contentEquals(other.nonce) &&
            signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var result = nonce.contentHashCode()
        result = 31 * result + issuedAt.hashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + rssi
        result = 31 * result + deviceAddress.hashCode()
        return result
    }
}
