package com.example.panzoto

import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.panzoto.audio.AudioRecorder

class MainActivity : AppCompatActivity() {

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AudioRecorder
        audioRecorder = AudioRecorder(this)

        // Get references to the buttons
        startButton = findViewById(R.id.startRecordingButton)
        stopButton = findViewById(R.id.stopRecordingButton)

        // Set up the start button click listener
        startButton.setOnClickListener {
            startRecording()
        }

        // Set up the stop button click listener
        stopButton.setOnClickListener {
            stopRecording()
        }

        // Request permissions for recording if necessary
        audioRecorder.requestRecordingPermission(this)
    }

    private fun startRecording() {
        // Disable the start button and enable the stop button
        startButton.isEnabled = false
        stopButton.isEnabled = true

        // Start the audio recording
        audioRecorder.startRecording()
    }

    private fun stopRecording() {
        // Disable the stop button and enable the start button
        startButton.isEnabled = true
        stopButton.isEnabled = false

        // Stop the audio recording
        audioRecorder.stopRecording()
    }
}
