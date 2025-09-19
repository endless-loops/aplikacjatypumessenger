package com.example.aplikacjatypumessenger.services

import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.ChatActivity
import com.example.aplikacjatypumessenger.MainActivity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
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

    // Handle incoming FCM messages
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Message data: ${remoteMessage.data}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            handleDataMessage(remoteMessage.data)
        }

        // Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            showNotification(
                title = it.title ?: "New Message",
                body = it.body ?: "",
                data = remoteMessage.data
            )
        }
    }

    // Handle token refresh
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")

        // Send token to your server or save to Firestore
        saveTokenToFirestore(token)
    }

    private fun handleDataMessage(data: Map<String, String>) {
        // Extract data from the message
        val title = data["title"] ?: "New Message"
        val body = data["body"] ?: ""
        val senderId = data["senderId"] ?: ""
        val senderName = data["senderName"] ?: "Unknown"
        val chatId = data["chatId"] ?: ""
        val messageType = data["type"] ?: "message"

        // Only show notification if user is not currently in the specific chat
        if (!isUserInChat(chatId)) {
            showNotification(
                title = senderName,
                body = body,
                data = data
            )
        }
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create intent for when notification is clicked
        val intent = createNotificationIntent(data)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Make sure you have this icon
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true) // Dismiss when clicked
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))

        // Generate unique notification ID (use chat ID or timestamp)
        val notificationId = data["chatId"]?.hashCode() ?: System.currentTimeMillis().toInt()

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun createNotificationIntent(data: Map<String, String>): Intent {
        val chatId = data["chatId"]
        val otherUserId = data["senderId"]

        val intent = if (!chatId.isNullOrEmpty() && !otherUserId.isNullOrEmpty()) {
            // Open specific chat
            Intent(this, ChatActivity::class.java).apply {
                putExtra("chatId", chatId)
                putExtra("otherUserId", otherUserId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } else {
            // Open main activity
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        return intent
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
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.uid)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d(TAG, "Token updated successfully")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error updating token", e)
                }
        }
    }

    // Check if user is currently viewing a specific chat
    private fun isUserInChat(chatId: String): Boolean {
        // You can implement this using SharedPreferences or a singleton
        // to track which chat is currently open
        val sharedPref = getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
        val currentChatId = sharedPref.getString("current_chat_id", "")
        return currentChatId == chatId
    }
}