package com.example.aplikacjatypumessenger.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.aplikacjatypumessenger.ChatActivity
import com.example.aplikacjatypumessenger.GroupChatActivity
import com.example.aplikacjatypumessenger.MainActivity
import com.example.aplikacjatypumessenger.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MessagingService"
        private const val CHANNEL_ID = "chat_notifications"
        private const val CHANNEL_NAME = "Chat Messages"
        private const val CHANNEL_DESCRIPTION = "Notifications for new chat messages"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Message data: ${remoteMessage.data}")

        if (remoteMessage.data.isNotEmpty()) {
            handleDataMessage(remoteMessage.data)
        }

        remoteMessage.notification?.let {
            Log.d(TAG, "Notification body: ${it.body}")
            showNotification(
                title = it.title ?: "Nowa wiadomość",
                body = it.body ?: "",
                data = remoteMessage.data
            )
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val title = data["senderName"] ?: "Nowa wiadomość"
        val body = data["body"] ?: ""
        val chatId = data["chatId"] ?: ""

        // Wyświetl powiadomienie tylko jeśli użytkownik nie jest aktualnie w czacie
        if (!isUserInChat(chatId)) {
            showNotification(title, body, data)
        }
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val intent = createNotificationIntent(data)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))

        val notificationId = data["chatId"]?.hashCode() ?: System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun createNotificationIntent(data: Map<String, String>): Intent {
        val chatId = data["chatId"]
        val senderId = data["senderId"]
        val type = data["type"] // "private" lub "group"

        return when {
            type == "group" && !chatId.isNullOrEmpty() -> {
                Intent(this, GroupChatActivity::class.java).apply {
                    putExtra("groupId", chatId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            !chatId.isNullOrEmpty() && !senderId.isNullOrEmpty() -> {
                Intent(this, ChatActivity::class.java).apply {
                    putExtra("chatId", chatId)
                    putExtra("otherUserId", senderId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            else -> {
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun saveTokenToFirestore(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d(TAG, "Token zapisany w Firestore")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Błąd zapisu tokenu", e)
            }
    }

    private fun isUserInChat(chatId: String): Boolean {
        val sharedPref = getSharedPreferences("chat_prefs", MODE_PRIVATE)
        val currentChatId = sharedPref.getString("current_chat_id", "")
        return currentChatId == chatId
    }
}
