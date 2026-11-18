package com.attendance.sensors

import android.content.Context
import com.attendance.sensors.audio.ChirpListener
import com.attendance.sensors.audio.ChirpPlayer
import com.attendance.sensors.ble.BeaconAdvertiser
import com.attendance.sensors.ble.BeaconScanner
import com.attendance.sensors.crypto.SeedStore

class Sensors(context: Context) {
    private val appContext = context.applicationContext
    private val seeds = SeedStore(appContext)
    private val advertiser = BeaconAdvertiser(appContext)
    private val scanner = BeaconScanner(appContext)
    private val player = ChirpPlayer()
    private val listener = ChirpListener(appContext)

    fun protectSeed(seed: ByteArray) {
        seeds.protect(seed)
    }

    fun revealSeed(): ByteArray = seeds.reveal()

    fun startAdvertising(nonce: ByteArray, issuedAt: Long, signature: ByteArray) {
        advertiser.start(nonce, issuedAt, signature)
    }

    fun stopAdvertising() {
        advertiser.stop()
    }

    fun startScan(onBeacon: (Beacon) -> Unit) {
        scanner.start(onBeacon)
    }

    fun stopScan() {
        scanner.stop()
    }

    fun latestBeacon(): Beacon? = scanner.latest()

    fun startChirp(nonce: ByteArray) {
        player.start(nonce)
    }

    fun stopChirp() {
        player.stop()
    }

    fun startListening(onNonce: (ByteArray) -> Unit) {
        listener.start(onNonce)
    }

    fun stopListening() {
        listener.stop()
    }

    fun latestChirp(): ByteArray? = listener.latest()
}
