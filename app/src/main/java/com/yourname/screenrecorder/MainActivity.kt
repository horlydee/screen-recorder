package com.yourname.screenrecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var qualityGroup: RadioGroup
    private lateinit var projectionManager: MediaProjectionManager
    private var isRecording = false

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                startRecordingService(result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        recordButton = findViewById(R.id.recordButton)
        qualityGroup = findViewById(R.id.qualityGroup)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        recordButton.setOnClickListener {
            if (!isRecording) {
                requestPermissionsIfNeeded()
            } else {
                stopRecordingService()
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        } else {
            launchScreenCapturePrompt()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            launchScreenCapturePrompt()
        }
    }

    private fun launchScreenCapturePrompt() {
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startRecordingService(resultCode: Int, data: Intent) {
        val quality = if (qualityGroup.checkedRadioButtonId == R.id.qualityBalanced) {
            ScreenRecordService.QUALITY_BALANCED
        } else {
            ScreenRecordService.QUALITY_LOW
        }
        val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_START
            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
            putExtra(ScreenRecordService.EXTRA_QUALITY, quality)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        isRecording = true
        statusText.text = "Recording... (minimize the app and go play)"
        recordButton.text = "Stop Recording"
        qualityGroup.isEnabled = false
        for (i in 0 until qualityGroup.childCount) {
            qualityGroup.getChildAt(i).isEnabled = false
        }
    }

    private fun stopRecordingService() {
        val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_STOP
        }
        startService(serviceIntent)
        isRecording = false
        statusText.text = "Saved to Movies/ScreenRecordings"
        recordButton.text = "Start Recording"
        qualityGroup.isEnabled = true
        for (i in 0 until qualityGroup.childCount) {
            qualityGroup.getChildAt(i).isEnabled = true
        }
    }
}
