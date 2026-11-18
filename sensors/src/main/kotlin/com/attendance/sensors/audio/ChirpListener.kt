package com.attendance.sensors.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.Context
import com.attendance.sensors.SensorException
import com.attendance.sensors.codec.ChirpCodec
import com.attendance.sensors.requirePermissions
import kotlin.math.min

internal class ChirpListener(private val context: Context) {
    private val lock = Any()
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private var latestNonce: ByteArray? = null
    @Volatile private var running = false

    fun start(onNonce: (ByteArray) -> Unit) {
        context.requirePermissions(Manifest.permission.RECORD_AUDIO)
        stop()
        val recorder = openRecorder()
        running = true
        val worker = Thread({ capture(recorder, onNonce) }, "sensors-chirp-listen")
        synchronized(lock) {
            record = recorder
            thread = worker
        }
        worker.start()
    }

    fun stop() {
        running = false
        val worker: Thread?
        val recorder: AudioRecord?
        synchronized(lock) {
            worker = thread
            recorder = record
            thread = null
        }
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        worker?.join(2_000)
        if (worker == null) {
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
            synchronized(lock) { record = null }
        }
    }

    fun latest(): ByteArray? = synchronized(lock) { latestNonce?.copyOf() }

    private fun capture(recorder: AudioRecord, onNonce: (ByteArray) -> Unit) {
        val capacity = ChirpCodec.SAMPLE_RATE * 4
        val ring = ShortArray(capacity)
        var write = 0
        var filled = 0
        val incoming = ShortArray(ChirpCodec.SAMPLE_RATE / 5)
        try {
            recorder.startRecording()
            while (running) {
                val count = recorder.read(incoming, 0, incoming.size)
                if (count <= 0) continue
                for (index in 0 until count) {
                    ring[write] = incoming[index]
                    write = (write + 1) % capacity
                }
                filled = min(capacity, filled + count)
                if (filled < ChirpCodec.frameSamples) continue
                val snapshot = ShortArray(filled)
                val start = if (filled == capacity) write else 0
                for (index in 0 until filled) {
                    snapshot[index] = ring[(start + index) % capacity]
                }
                val nonce = ChirpCodec.decode(snapshot) ?: continue
                synchronized(lock) { latestNonce = nonce.copyOf() }
                onNonce(nonce.copyOf())
            }
        } catch (_: Exception) {
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) {
            }
            recorder.release()
            synchronized(lock) {
                if (record === recorder) record = null
            }
        }
    }

    private fun openRecorder(): AudioRecord {
        val minimum = AudioRecord.getMinBufferSize(
            ChirpCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimum <= 0) throw SensorException("Audio capture is unavailable")
        val bufferBytes = maxOf(minimum, ChirpCodec.SAMPLE_RATE * 2 / 5)
        return createRecorder(MediaRecorder.AudioSource.UNPROCESSED, bufferBytes)
            ?: createRecorder(MediaRecorder.AudioSource.MIC, bufferBytes)
            ?: throw SensorException("Audio capture is unavailable")
    }

    private fun createRecorder(source: Int, bufferBytes: Int): AudioRecord? {
        return try {
            val recorder = AudioRecord(
                source,
                ChirpCodec.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                null
            } else {
                recorder
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
