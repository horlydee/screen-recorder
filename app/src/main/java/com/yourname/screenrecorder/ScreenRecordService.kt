package com.yourname.screenrecorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_QUALITY = "EXTRA_QUALITY"
        const val QUALITY_LOW = 0
        const val QUALITY_BALANCED = 1
        private const val CHANNEL_ID = "screen_record_channel"
        private const val NOTIFICATION_ID = 1
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val quality = intent.getIntExtra(EXTRA_QUALITY, QUALITY_LOW)
                if (data != null) {
                    startForegroundNotification()
                    beginRecording(resultCode, data, quality)
                }
            }
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Screen Recording", NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording screen")
            .setContentText("Tap to return to the app")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // Rounds down to the nearest even number — H264 encoders require even dimensions,
    // and many low-end hardware encoders are happiest with multiples of 16.
    private fun makeEven(value: Int): Int = if (value % 2 == 0) value else value - 1

    private fun beginRecording(resultCode: Int, data: Intent, quality: Int) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        val display = (getSystemService(DisplayManager::class.java)).getDisplay(0)
        display?.getRealMetrics(metrics)
        val nativeWidth = metrics.widthPixels
        val nativeHeight = metrics.heightPixels
        val density = metrics.densityDpi

        // Target the SHORT edge (phones are portrait, so width < height).
        // Scaling the capture resolution down is the single biggest lever for
        // reducing encoder + GPU-composition load on weak chipsets like the
        // A03s's Helio P35 — far more effective than just capping bitrate.
        val shortEdge = minOf(nativeWidth, nativeHeight)
        val (targetShortEdge, frameRate, bitRate) = when (quality) {
            QUALITY_BALANCED -> Triple(720, 24, 4_000_000)
            else -> Triple(480, 15, 1_800_000) // QUALITY_LOW
        }
        val scale = minOf(1f, targetShortEdge.toFloat() / shortEdge.toFloat())
        val outWidth = makeEven((nativeWidth * scale).toInt())
        val outHeight = makeEven((nativeHeight * scale).toInt())

        val fileName = "ScreenRecording_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64_000)
            setAudioSamplingRate(44_100)
            setVideoSize(outWidth, outHeight)
            setVideoEncodingBitRate(bitRate)
            setVideoFrameRate(frameRate)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenRecordings")
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                pfd = contentResolver.openFileDescriptor(uri!!, "w")
                setOutputFile(pfd!!.fileDescriptor)
            } else {
                val dir = getExternalFilesDir(null)
                setOutputFile("$dir/$fileName")
            }

            prepare()
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecording",
            outWidth, outHeight, density,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface, null, null
        )

        mediaRecorder?.start()
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            // recorder may not have started long enough to produce a valid file
        }
        virtualDisplay?.release()
        mediaProjection?.stop()
        pfd?.close()

        mediaRecorder = null
        virtualDisplay = null
        mediaProjection = null
        pfd = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }
}
