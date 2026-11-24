package com.example.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class CarAudioRecorder {
    private var recorder: MediaRecorder? = null
    private var activeOutputFile: File? = null

    fun startRecording(context: Context): Result<File> {
        return try {
            releaseRecorder()
            val outDir = File(context.cacheDir, "car_sounds").apply { mkdirs() }
            val outFile = File(outDir, "car_sound_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context.applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            activeOutputFile = outFile
            Result.success(outFile)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start microphone recording", e)
            releaseRecorder()
            Result.failure(
                IllegalStateException("Unable to open microphone for vehicle audio: ${e.localizedMessage ?: "Mic error"}", e)
            )
        }
    }

    fun stopRecording(): Result<File> {
        val current = recorder
        val file = activeOutputFile
        return try {
            current?.apply {
                stop()
                reset()
                release()
            }
            recorder = null
            if (file != null && file.exists() && file.length() > 0L) {
                Result.success(file)
            } else {
                Result.failure(IllegalStateException("No audio recorded or audio file is empty."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed stopping audio recording cleanly", e)
            runCatching { current?.release() }
            recorder = null
            file?.delete()
            activeOutputFile = null
            Result.failure(IllegalStateException("Recording failed. Please record for at least 1 second.", e))
        }
    }

    fun cancelRecording() {
        val current = recorder
        recorder = null
        runCatching {
            current?.stop()
        }
        runCatching {
            current?.reset()
            current?.release()
        }
        activeOutputFile?.delete()
        activeOutputFile = null
    }

    private fun releaseRecorder() {
        runCatching {
            recorder?.reset()
            recorder?.release()
        }
        recorder = null
    }

    private companion object {
        const val TAG = "CarAudioRecorder"
    }
}
