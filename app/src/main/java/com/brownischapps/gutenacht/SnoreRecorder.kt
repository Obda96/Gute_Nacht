package com.brownischapps.gutenacht

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class SnoreRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null

    fun startRecording(outputFile: File) {
        stopRecording()
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // Punkt 5: Optimiert für Schnarch- & Sprachfrequenzen (Mono, 16 kHz, 24 kbps)
            // Spart über 80% Speicherplatz gegenüber 128 kbps / 44.1 kHz bei klarer Erkennbarkeit!
            setAudioChannels(1)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(24000)
            setOutputFile(outputFile.absolutePath)

            try {
                prepare()
                start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            // Ignorieren, falls Aufnahme zu kurz war
        } finally {
            mediaRecorder = null
        }
    }
}