package com.attendance.sensors

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build

internal fun Context.requirePermissions(vararg permissions: String) {
    val missing = permissions.filter { permission ->
        checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
    }
    if (missing.isNotEmpty()) {
        val names = missing.joinToString(", ") { it.substringAfterLast('.') }
        throw SensorException("Missing permission: $names")
    }
}

/**
 * Bluetooth runtime permissions exist from API 31. On older releases they are not
 * granted or denied, so checking them would always report them missing.
 */
internal fun Context.requireBluetoothRuntimePermissions(vararg permissions: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    requirePermissions(*permissions)
}

/**
 * Android 9 through 11 treat a BLE scan as location. Android 12 and newer use
 * [Manifest.permission.BLUETOOTH_SCAN] with neverForLocation instead.
 */
internal fun Context.requireScanPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        requireBluetoothRuntimePermissions(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        return
    }
    requirePermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    val location = getSystemService(LocationManager::class.java)
        ?: throw SensorException("Location services are off")
    if (!location.isLocationEnabled) {
        throw SensorException("Location services are off")
    }
}

internal fun Context.requireBluetoothAdapter(): BluetoothAdapter {
    requireBluetoothRuntimePermissions(Manifest.permission.BLUETOOTH_CONNECT)
    val manager = getSystemService(BluetoothManager::class.java)
        ?: throw SensorException("Bluetooth is off")
    val adapter = manager.adapter ?: throw SensorException("Bluetooth is off")
    val enabled = try {
        adapter.isEnabled
    } catch (_: SecurityException) {
        throw SensorException(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "Missing permission: BLUETOOTH_CONNECT"
            } else {
                "Missing permission: BLUETOOTH"
            },
        )
    }
    if (!enabled) throw SensorException("Bluetooth is off")
    return adapter
}
