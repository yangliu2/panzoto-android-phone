package com.example.panzoto.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import androidx.appcompat.app.AppCompatActivity


class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val sampleRateInHz = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
    private var decibelThreshold = 60.0 // Threshold in dB

    // Check if the app has the required permission to record audio
    private fun hasRecordingPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            true // On older versions, permissions are granted at install time
        }
    }

    // Request permission to record audio (called from an Activity)
    fun requestRecordingPermission(activity: AppCompatActivity) {
        if (!hasRecordingPermission()) {
            ActivityCompat.requestPermissions(
                activity, arrayOf(Manifest.permission.RECORD_AUDIO), 1
            )
        }
    }

    // Initialize and start recording if permission is granted
    fun startRecording() {
        if (!hasRecordingPermission()) {
            Log.e("AudioRecorder", "Permission not granted to record audio.")
            return
        }

        val buffer = ByteArray(bufferSize)
        var fileIndex = 1
        var chunkFile = File(context.filesDir, "audio_chunk_$fileIndex.pcm")
        var fos = FileOutputStream(chunkFile)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        var startTime = System.currentTimeMillis()
        var currentTime = startTime

        Log.d("AudioRecorder", "Recording started")

        // Record audio in 30-second chunks
        while (isRecording) {
            val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (bytesRead > 0) {
                val decibels = getDecibelLevel(buffer)
                if (decibels > decibelThreshold) {
                    // Record audio only when the threshold is exceeded
                    fos.write(buffer, 0, bytesRead)
                }
            }

            // Chunk audio into 30-second intervals
            currentTime = System.currentTimeMillis()
            if (currentTime - startTime >= 30000) {
                fos.close()
                uploadToS3(chunkFile)

                // Increment file index and start a new chunk file
                fileIndex++
                chunkFile = File(context.filesDir, "audio_chunk_$fileIndex.pcm")
                fos = FileOutputStream(chunkFile)

                // Reset start time to the current time
                startTime = currentTime
            }
        }

        // Close file output stream after stopping
        fos.close()
    }

    // Stop recording and end the loop
    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        Log.d("AudioRecorder", "Recording stopped")
    }

    // Upload audio file to S3
    private fun uploadToS3(file: File) {
        // Implement your S3 upload logic here
    }

    // Calculate the decibel level of the audio buffer
    private fun getDecibelLevel(buffer: ByteArray): Double {
        var sum = 0.0
        for (i in buffer.indices) {
            sum += buffer[i].toDouble() * buffer[i].toDouble()
        }
        val rms = Math.sqrt(sum / buffer.size)
        return 20 * Math.log10(rms)
    }
}
