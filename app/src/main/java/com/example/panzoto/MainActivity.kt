package com.example.panzoto

import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask
import android.net.Uri
import android.os.Handler
import android.os.Looper

private var mediaRecorder: MediaRecorder? = null
private var outputFilePath: String = ""
private var hasVoiceDetected = false

private var startTimeMillis: Long = 0
private var silenceStartMillis: Long? = null
private var monitorTimer: Timer? = null

private val silenceThreshold = 500 // Adjust based on microphone sensitivity
private val silenceDurationMillis = 3000L // 3 seconds
private val maxChunkDurationMillis = 60000L // 60 seconds


class MainActivity : ComponentActivity() {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFilePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {
            AudioRecorderScreen(
                onStartRecording = { startRecording() },
                onStopRecording = { stopRecording() }
            )
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO
        )
        ActivityCompat.requestPermissions(this, permissions, 0)
    }

    private fun hasPermissions(): Boolean {
        val recordAudioPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return recordAudioPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun forceSplit() {
        stopRecording(autoRestart = true)
    }


    private var monitoringHandler: Handler? = null
    private var hasVoiceDetected = false

    private val monitoringRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - startTimeMillis
            val amplitude = mediaRecorder?.maxAmplitude ?: 0

            if (amplitude < silenceThreshold) {
                if (silenceStartMillis == null) {
                    silenceStartMillis = currentTime
                } else if (currentTime - silenceStartMillis!! >= silenceDurationMillis) {
                    Log.d("Split", "Silence detected, splitting recording.")
                    forceSplit()
                    return
                }
            } else {
                hasVoiceDetected = true // ✅ Real sound detected
                silenceStartMillis = null
            }

            if (elapsedTime >= maxChunkDurationMillis) {
                Log.d("Split", "Max duration reached, splitting recording.")
                forceSplit()
                return
            }

            monitoringHandler?.postDelayed(this, 200)
        }
    }

    private fun startMonitoring() {
        hasVoiceDetected = false
        silenceStartMillis = null
        monitoringHandler = Handler(Looper.getMainLooper())
        monitoringHandler?.post(monitoringRunnable)
    }


    private fun startRecording() {
        val fileName = "audio_record_${System.currentTimeMillis()}.3gp"
        outputFilePath = "${externalCacheDir?.absolutePath}/$fileName"

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(outputFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            prepare()
            start()
        }

        startTimeMillis = System.currentTimeMillis()
        silenceStartMillis = null

        startMonitoring()
        Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording(autoRestart: Boolean = false) {
        monitoringHandler?.removeCallbacks(monitoringRunnable)
        monitoringHandler = null

        val duration = System.currentTimeMillis() - startTimeMillis
        if (duration < 1000L) {
            Log.w("Split", "Recording too short (<1s). Skipping stop.")
            if (autoRestart) startRecording()
            return
        }

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("Recorder", "Stop failed: ${e.message}")
            if (autoRestart) startRecording()
            return
        }

        mediaRecorder = null

        val inputFile = File(outputFilePath)
        if (!inputFile.exists() || inputFile.length() < 2000) {
            Log.d("UploadSkipped", "Skipped small file: ${inputFile.name}")
            inputFile.delete()
            if (autoRestart) startRecording()
            return
        }

        if (!hasVoiceDetected) {
            Log.d(
                "UploadSkipped",
                "Skipped silent recording (no voice detected): ${inputFile.name}"
            )
            inputFile.delete()
            if (autoRestart) startRecording()
            return
        }

        val encryptedFile = File("${outputFilePath}_encrypted")
        FileEncryptor.encryptFile(inputFile, encryptedFile)

        if (!encryptedFile.exists() || encryptedFile.length() < 1000) {
            Log.d("UploadSkipped", "Encrypted file too small or missing: ${encryptedFile.name}")
            encryptedFile.delete()
            if (autoRestart) startRecording()
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val presignedKey = "audio_upload/${Uri.encode(encryptedFile.name)}"
            requestPresignedUrlAndUpload(encryptedFile, presignedKey)
        }, 200)

        if (autoRestart) {
            startRecording()
        }
    }


    private fun requestPresignedUrlAndUpload(encryptedFile: File, key: String) {
        if (!encryptedFile.exists() || encryptedFile.length() < 1000) {
            Log.e("PresignedURL", "Encrypted file invalid: ${encryptedFile.absolutePath}")
            return
        }

        val apiUrl = "https://o3xjl9jmwf.execute-api.us-east-1.amazonaws.com/generate-url?key=$key"
        Log.d("PresignedURL", "Requesting presigned URL from: $apiUrl")

        val client = OkHttpClient()
        val request = Request.Builder().url(apiUrl).get().build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    val jsonResponse = JSONObject(bodyString ?: "{}")
                    val presignedUrl = jsonResponse.getString("url")
                    Log.d("PresignedURL", "Got presigned URL: $presignedUrl")

                    uploadEncryptedFile(encryptedFile, presignedUrl)
                } else {
                    Log.e("PresignedURL", "Failed to get URL: ${response.code} ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("PresignedURL", "Exception during request: ${e.message}", e)
            }
        }.start()
    }


    private fun uploadEncryptedFile(encryptedFile: File, presignedUrl: String) {
        val client = OkHttpClient()

        val fileBytes = encryptedFile.readBytes()
        val requestBody = RequestBody.create(null, fileBytes) // <= Notice: NULL media type

        val request = Request.Builder()
            .url(presignedUrl)
            .put(requestBody) // DO NOT manually add Content-Type
            .build()

        val call = client.newCall(request)

        Thread {
            try {
                val response = call.execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful) {
                    Log.d("UploadStatus", "Upload successful!")
                } else {
                    Log.e("UploadStatus", "Upload failed: ${response.code}, error: $responseBody")
                }
            } catch (e: Exception) {
                Log.e("UploadStatus", "Exception during upload", e)
            }
        }.start()
    }


}


@Composable
fun AudioRecorderScreen(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = {
                if (!isRecording) {
                    onStartRecording()
                    isRecording = true
                }
            },
            enabled = !isRecording,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Recording")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isRecording) {
                    onStopRecording()
                    isRecording = false
                }
            },
            enabled = isRecording,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop Recording")
        }
    }
}
