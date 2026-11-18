package com.attendance.sensors.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.attendance.sensors.Beacon
import com.attendance.sensors.SensorException
import com.attendance.sensors.codec.BeaconCodec
import com.attendance.sensors.requireBluetoothAdapter
import com.attendance.sensors.requireBluetoothRuntimePermissions

internal class BeaconScanner(private val context: Context) {
    private val lock = Any()
    private val assemblers = HashMap<String, BeaconCodec.Assembler>()
    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private var listener: ((Beacon) -> Unit)? = null
    private var latest: Beacon? = null

    @SuppressLint("MissingPermission")
    fun start(onBeacon: (Beacon) -> Unit) {
        context.requireBluetoothRuntimePermissions(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        val adapter = context.requireBluetoothAdapter()
        val leScanner = adapter.bluetoothLeScanner
            ?: throw SensorException("Bluetooth LE scanner is unavailable")
        stop()
        synchronized(lock) {
            listener = onBeacon
            assemblers.clear()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setLegacy(false)
                }
            }
            .build()
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handle(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::handle)
            }
        }
        scanner = leScanner
        callback = scanCallback
        try {
            leScanner.startScan(null, settings, scanCallback)
        } catch (security: SecurityException) {
            throw SensorException("Missing permission: BLUETOOTH_SCAN, BLUETOOTH_CONNECT")
        }
    }

    fun stop() {
        val current: BluetoothLeScanner?
        val active: ScanCallback?
        synchronized(lock) {
            current = scanner
            active = callback
            scanner = null
            callback = null
            listener = null
            assemblers.clear()
        }
        if (current != null && active != null) {
            try {
                current.stopScan(active)
            } catch (_: Exception) {
            }
        }
    }

    fun latest(): Beacon? = synchronized(lock) { latest }

    private fun handle(result: ScanResult) {
        val record = result.scanRecord ?: return
        val payload = record.getManufacturerSpecificData(BeaconCodec.COMPANY_ID) ?: return
        if (payload.isEmpty()) return
        val address = deviceAddress(result) ?: return
        when (payload[0].toInt() and 0xFF) {
            BeaconCodec.EXTENDED_TAG -> {
                if (payload.size < 1 + BeaconCodec.BODY_BYTES) return
                publish(payload.copyOfRange(1, 1 + BeaconCodec.BODY_BYTES), result.rssi, address)
            }
            BeaconCodec.CHUNK_TAG -> {
                val body = synchronized(lock) {
                    assemblers.getOrPut(address) { BeaconCodec.Assembler() }.add(payload)
                } ?: return
                publish(body, result.rssi, address)
            }
        }
    }

    private fun publish(body: ByteArray, rssi: Int, address: String) {
        val unpacked = try {
            BeaconCodec.unpack(body)
        } catch (_: IllegalArgumentException) {
            return
        }
        val beacon = Beacon(
            nonce = unpacked.nonce,
            issuedAt = unpacked.issuedAt,
            signature = unpacked.signature,
            rssi = rssi,
            deviceAddress = address,
        )
        val callback = synchronized(lock) {
            latest = beacon
            listener
        }
        callback?.invoke(beacon)
    }

    private fun deviceAddress(result: ScanResult): String? {
        return try {
            result.device?.address
        } catch (_: SecurityException) {
            null
        }
    }
}
