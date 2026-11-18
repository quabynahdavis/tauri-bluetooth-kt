package com.attendance.sensors.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.attendance.sensors.SensorException
import com.attendance.sensors.codec.ChirpCodec

internal class ChirpPlayer {
    private val lock = Any()
    private var track: AudioTrack? = null
    private var stream: Thread? = null
    @Volatile private var streaming = false

    fun start(nonce: ByteArray) {
        val samples = ChirpCodec.synthesize(nonce)
        val pcm = ByteArray(samples.size * 2)
        for (index in samples.indices) {
            val sample = samples[index].toInt()
            pcm[index * 2] = (sample and 0xFF).toByte()
            pcm[index * 2 + 1] = ((sample ushr 8) and 0xFF).toByte()
        }
        stop()
        val playing = staticTrack(pcm) ?: streamTrack(pcm)
            ?: throw SensorException("Audio playback is unavailable")
        synchronized(lock) { track = playing }
        if (!streaming) playing.play()
    }

    fun stop() {
        val current: AudioTrack?
        val worker: Thread?
        synchronized(lock) {
            streaming = false
            current = track
            worker = stream
            track = null
            stream = null
        }
        try {
            current?.pause()
            current?.flush()
        } catch (_: Exception) {
        }
        worker?.join(1_000)
        try {
            current?.release()
        } catch (_: Exception) {
        }
    }

    private fun staticTrack(pcm: ByteArray): AudioTrack? {
        val track = newTrack(pcm.size, AudioTrack.MODE_STATIC) ?: return null
        var written = 0
        while (written < pcm.size) {
            val count = track.write(pcm, written, pcm.size - written)
            if (count <= 0) {
                track.release()
                return null
            }
            written += count
        }
        val frames = pcm.size / 2
        if (track.setLoopPoints(0, frames, -1) != AudioTrack.SUCCESS) {
            track.release()
            return null
        }
        return track
    }

    private fun streamTrack(pcm: ByteArray): AudioTrack? {
        val min = AudioTrack.getMinBufferSize(
            ChirpCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) return null
        val track = newTrack(maxOf(min, pcm.size), AudioTrack.MODE_STREAM) ?: return null
        streaming = true
        val worker = Thread({
            try {
                track.play()
                while (streaming) {
                    var offset = 0
                    while (streaming && offset < pcm.size) {
                        val count = track.write(pcm, offset, pcm.size - offset)
                        if (count <= 0) return@Thread
                        offset += count
                    }
                }
            } catch (_: Exception) {
            }
        }, "sensors-chirp-play")
        synchronized(lock) { stream = worker }
        worker.start()
        return track
    }

    private fun newTrack(bufferBytes: Int, mode: Int): AudioTrack? {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(ChirpCodec.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(mode)
            .setBufferSizeInBytes(bufferBytes)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return null
        }
        return track
    }
}
