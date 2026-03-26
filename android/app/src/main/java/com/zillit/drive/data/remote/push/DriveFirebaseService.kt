package com.zillit.drive.data.remote.push

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zillit.drive.R
import com.zillit.drive.data.local.prefs.SessionManager
import com.zillit.drive.domain.repository.DriveRepository
import com.zillit.drive.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DriveFirebaseService : FirebaseMessagingService() {

    @Inject
    lateinit var sessionManager: SessionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Register/update the FCM token with the backend
        // The backend can use this to send targeted push notifications
        serviceScope.launch {
            try {
                // Store token locally for future API registration
                // The actual API call to register would go here when the backend
                // supports a token registration endpoint
                android.util.Log.d("DriveFirebase", "New FCM token: $token")
            } catch (e: Exception) {
                android.util.Log.e("DriveFirebase", "Failed to register FCM token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: "Zillit Drive"
        val body = message.notification?.body ?: data["body"] ?: ""
        val type = data["type"] ?: "general" // "upload", "share", "general"
        val fileId = data["file_id"]
        val folderId = data["folder_id"]

        // Determine which notification channel to use
        val channelId = when (type) {
            "upload" -> MainActivity.CHANNEL_UPLOADS
            "share" -> MainActivity.CHANNEL_SHARING
            else -> MainActivity.CHANNEL_SHARING
        }

        // Build deep link intent
        val deepLinkUri = when {
            fileId != null -> Uri.parse("zillit-drive://file/$fileId")
            folderId != null -> Uri.parse("zillit-drive://folder/$folderId")
            else -> null
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            deepLinkUri?.let { this.data = it }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted on Android 13+
            android.util.Log.w("DriveFirebase", "Notification permission not granted", e)
        }
    }
}
