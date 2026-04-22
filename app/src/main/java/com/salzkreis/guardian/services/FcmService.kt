package com.salzkreis.guardian.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.salzkreis.guardian.managers.FirebaseManager

class FcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        // Befehle kommen über Firestore-Listener in GuardianService
    }

    override fun onNewToken(token: String) {
        FirebaseManager(this).updateFcmToken(token)
    }
}
