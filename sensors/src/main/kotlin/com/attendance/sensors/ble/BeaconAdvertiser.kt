package com.attendance.sensors.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.attendance.sensors.SensorException
import com.attendance.sensors.codec.BeaconCodec
import com.attendance.sensors.requireBluetoothAdapter
import com.attendance.sensors.requireBluetoothRuntimePermissions

internal class BeaconAdvertiser(private val context: Context) {
    private val thread = HandlerThread("sensors-beacon-advertise").apply { start() }
    private val handler = Handler(thread.looper)
    private val lock = Any()
    private var token = 0
    private var advertiser: BluetoothLeAdvertiser? = null
    private var legacyCallback: AdvertiseCallback? = null
    private var pending: Runnable? = null
    private var chunks: List<ByteArray> = emptyList()
    private var chunkIndex = 0

    fun start(nonce: ByteArray, issuedAt: Long, signature: ByteArray) {
        val body = BeaconCodec.pack(nonce, issuedAt, signature)
        context.requireBluetoothRuntimePermissions(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        val adapter = context.requireBluetoothAdapter()
        val leAdvertiser = adapter.bluetoothLeAdvertiser
            ?: throw SensorException("Bluetooth LE advertiser is unavailable")
        stop()
        val session = synchronized(lock) {
            advertiser = leAdvertiser
            token
        }
        startLegacy(leAdvertiser, body, session)
    }

    fun stop() {
        val legacy: AdvertiseCallback?
        val runnable: Runnable?
        val current: BluetoothLeAdvertiser?
        synchronized(lock) {
            token += 1
            legacy = legacyCallback
            runnable = pending
            legacyCallback = null
            pending = null
            chunks = emptyList()
            chunkIndex = 0
            current = advertiser
        }
        runnable?.let(handler::removeCallbacks)
        if (current == null || legacy == null) return
        try {
            current.stopAdvertising(legacy)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLegacy(leAdvertiser: BluetoothLeAdvertiser, body: ByteArray, session: Int) {
        if (!isCurrent(session)) return
        val parts = BeaconCodec.chunkPayloads(body)
        synchronized(lock) {
            if (token != session) return
            chunks = parts
            chunkIndex = 0
        }
        advertiseChunk(leAdvertiser, session)
    }

    @SuppressLint("MissingPermission")
    private fun advertiseChunk(leAdvertiser: BluetoothLeAdvertiser, session: Int) {
        val previous: AdvertiseCallback?
        val payload: ByteArray
        synchronized(lock) {
            if (token != session || chunks.isEmpty()) return
            previous = legacyCallback
            payload = chunks[chunkIndex]
        }
        if (previous != null) {
            try {
                leAdvertiser.stopAdvertising(previous)
            } catch (_: Exception) {
            }
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()
        val started = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) = Unit
            override fun onStartFailure(errorCode: Int) = Unit
        }
        synchronized(lock) {
            if (token != session) return
            legacyCallback = started
        }
        try {
            leAdvertiser.startAdvertising(settings, manufacturerData(payload), started)
        } catch (_: SecurityException) {
            throw SensorException("Missing permission: ${advertisePermissionName()}")
        }
        val rotate = Runnable {
            val advanced = synchronized(lock) {
                if (token != session || chunks.isEmpty()) {
                    false
                } else {
                    chunkIndex = (chunkIndex + 1) % chunks.size
                    true
                }
            }
            if (advanced) advertiseChunk(leAdvertiser, session)
        }
        synchronized(lock) {
            if (token != session) return
            pending = rotate
        }
        handler.postDelayed(rotate, CHUNK_PERIOD_MS)
    }

    private fun manufacturerData(payload: ByteArray): AdvertiseData {
        return AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(BeaconCodec.COMPANY_ID, payload)
            .build()
    }

    private fun isCurrent(session: Int): Boolean = synchronized(lock) { token == session }

    private companion object {
        const val CHUNK_PERIOD_MS = 200L

        fun advertisePermissionName(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "BLUETOOTH_ADVERTISE"
            } else {
                "BLUETOOTH_ADMIN"
            }
        }
    }
}
