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
        Toast.makeText(this, "Recording saved: $outputFilePath", Toast.LENGTH_LONG).show()

        Log.d("AudioPath", "Saved audio file: $outputFilePath")
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
