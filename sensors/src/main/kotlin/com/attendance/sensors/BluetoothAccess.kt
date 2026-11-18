package com.attendance.sensors

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
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

internal fun Context.requireBluetoothAdapter(): BluetoothAdapter {
    requireBluetoothRuntimePermissions(Manifest.permission.BLUETOOTH_CONNECT)
    val manager = getSystemService(BluetoothManager::class.java)
        ?: throw SensorException("Bluetooth is off")
    val adapter = manager.adapter ?: throw SensorException("Bluetooth is off")
    val enabled = try {
        adapter.isEnabled
    } catch (_: SecurityException) {
        throw SensorException("Missing permission: BLUETOOTH_CONNECT")
    }
    if (!enabled) throw SensorException("Bluetooth is off")
    return adapter
}
