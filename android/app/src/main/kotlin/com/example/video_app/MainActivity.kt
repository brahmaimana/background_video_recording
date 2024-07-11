package com.example.video_app

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.content.res.Configuration
import android.media.MediaScannerConnection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.hbisoft.hbrecorder.HBRecorder
import com.hbisoft.hbrecorder.HBRecorderCodecInfo
import com.hbisoft.hbrecorder.HBRecorderListener
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity: FlutterFragmentActivity(), HBRecorderListener {
    private lateinit var methodChannelResult: MethodChannel.Result
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var hbRecorder: HBRecorder
    private lateinit var resolver: ContentResolver
    private lateinit var contentValues: ContentValues
    private var mUri: Uri? = null

    private val REQUIRED_PERMISSIONS = mutableListOf(
        android.Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }.apply {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private var resultLauncher = 
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                setOutputPath()
                data?.let { hbRecorder.startScreenRecording(it, result.resultCode) }
            }
        }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { isGranted ->
        if (isGranted.containsValue(false)) {
            Toast.makeText(this, "Permission not granted", Toast.LENGTH_LONG).show()
        } else {
            if (hbRecorder.isBusyRecording) {
                hbRecorder.stopScreenRecording()
            } else {
                hbRecorder.setAudioBitrate(128000)
                hbRecorder.setAudioSamplingRate(44100)
                hbRecorder.recordHDVideo(true)
                hbRecorder.isAudioEnabled(true)
                hbRecorder.setNotificationSmallIcon(R.drawable.icon)
                hbRecorder.setNotificationTitle(getString(R.string.stop_recording_notification_title))
                hbRecorder.setNotificationDescription(getString(R.string.stop_recording_notification_message))
                val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
                resultLauncher.launch(permissionIntent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "location",
                "Location",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = 
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "recordPlatform"
        ).setMethodCallHandler { call, result ->
            methodChannelResult = result
            when (call.method) {
                "start" -> {
                    hbRecorder = HBRecorder(this, this)
                    // Examples of how to use the HBRecorderCodecInfo class to get codec info
                    val hbRecorderCodecInfo = HBRecorderCodecInfo()
                    val mWidth = hbRecorder.defaultWidth
                    val mHeight = hbRecorder.defaultHeight
                    val mMimeType = "video/avc"
                    val mFPS = 30
                    mediaProjectionManager = 
                        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
                }
                "stop" -> {
                    if (hbRecorder.isBusyRecording) {
                        hbRecorder.stopScreenRecording()
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun setOutputPath() {
        val filename: String = generateFileName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver = contentResolver
            contentValues = ContentValues()
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/"+"HBRecorder")
            contentValues.put(MediaStore.Video.Media.TITLE, filename)
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            mUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            // FILE NAME SHOULD BE THE SAME
            hbRecorder.fileName = filename
            hbRecorder.setOutputUri(mUri)
        } else {
            createFolder()
            hbRecorder.setOutputPath(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).toString()+"/HBRecorder"
            )
        }
    }

    private fun generateFileName(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
        val curDate = Date(System.currentTimeMillis())
        return formatter.format(curDate).replace(" ", "")
    }

    private fun createFolder() {
        val f1 = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "HBRecorder"
        )
        if (!f1.exists()) {
            if (f1.mkdirs()) {
                Log.i("Folder ", "created")
            }
        }
    }

    override fun HBRecorderOnStart() {}

    override fun HBRecorderOnComplete() {
        if (hbRecorder.wasUriSet()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                updateGalleryUri()
            } else {
                refreshGalleryFile()
            }
        } else {
            refreshGalleryFile()
        }
    }

    private fun refreshGalleryFile() {
        MediaScannerConnection.scanFile(
            this, arrayOf(hbRecorder.filePath), null
        ) { path, uri ->
            Log.i("ExternalStorage", "Scanned $path:")
            Log.i("ExternalStorage", "-> uri: $uri")
            methodChannelResult.success(mUri.toString())
        }
    }

    private fun updateGalleryUri() {
        contentValues.clear()
        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
        mUri?.let { resolver.update(it, contentValues, null, null) }
        methodChannelResult.success(mUri.toString())
    }

    override fun HBRecorderOnError(errorCode: Int, reason: String?) {}

    override fun HBRecorderOnPause() {}

    override fun HBRecorderOnResume() {}
}
