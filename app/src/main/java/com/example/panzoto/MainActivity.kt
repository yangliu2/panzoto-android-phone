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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject

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
        val recordAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return recordAudioPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun startRecording() {
        if (!hasPermissions()) {
            Toast.makeText(this, "Permissions not granted!", Toast.LENGTH_SHORT).show()
            return
        }

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
        Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null

        Toast.makeText(this, "Recording saved: $outputFilePath", Toast.LENGTH_SHORT).show()

        Log.d("AudioPath", "Saved audio file: $outputFilePath")

        // Encrypt the file immediately after recording
        val inputFile = File(outputFilePath)
        val encryptedFile = File("${outputFilePath}_encrypted")

        FileEncryptor.encryptFile(inputFile, encryptedFile)

        Toast.makeText(this, "Encrypted file saved: ${encryptedFile.absolutePath}", Toast.LENGTH_LONG).show()

        Log.d("EncryptedPath", "Saved encrypted file: ${encryptedFile.absolutePath}")

        requestPresignedUrlAndUpload(encryptedFile)
    }

    private fun requestPresignedUrlAndUpload(encryptedFile: File) {
        val apiUrl = "https://o3xjl9jmwf.execute-api.us-east-1.amazonaws.com/generate-url?key=audio_upload/${encryptedFile.name}"

        val client = OkHttpClient()

        // Step 1: Request Pre-Signed URL
        val request = Request.Builder()
            .url(apiUrl)
            .get()
            .build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    val jsonResponse = JSONObject(bodyString ?: "{}")
                    val presignedUrl = jsonResponse.getString("url")

                    Log.d("PresignedURL", "Got presigned URL: $presignedUrl")

                    // Step 2: Upload Encrypted File
                    uploadEncryptedFile(encryptedFile, presignedUrl)
                } else {
                    Log.e("PresignedURL", "Failed to get URL: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("PresignedURL", "Exception during request", e)
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
