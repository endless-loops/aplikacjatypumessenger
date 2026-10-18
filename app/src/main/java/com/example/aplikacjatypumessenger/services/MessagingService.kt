package com.example.aplikacjatypumessenger.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.example.aplikacjatypumessenger.ChatActivity
import com.example.aplikacjatypumessenger.GroupChatActivity
import com.example.aplikacjatypumessenger.MainActivity
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.utils.Extras
import com.example.aplikacjatypumessenger.utils.appContainer
import com.example.aplikacjatypumessenger.utils.runSuspendCatching
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * Odbiera powiadomienia wysyłane przez Cloud Function `sendMessageNotification`
 * (payload typu "data": chatId, messageId, senderId, title, body, isGroup).
 */
class MessagingService : FirebaseMessagingService() {

    private val container get() = applicationContext.appContainer

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val userId = container.authRepository.currentUserId ?: return
        container.applicationScope.launch {
            runSuspendCatching { container.userRepository.updateFcmToken(userId, token) }
                .onFailure { Log.w(TAG, "Nie udało się zapisać tokenu FCM", it) }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val chatId = data[KEY_CHAT_ID].orEmpty()
        if (chatId.isEmpty() || container.authRepository.currentUserId == null) return

        data[KEY_MESSAGE_ID]?.takeIf { it.isNotEmpty() }?.let { messageId ->
            container.applicationScope.launch {
                runSuspendCatching { container.messageRepository.markAsDelivered(messageId) }
                    .onFailure { Log.w(TAG, "Nie udało się oznaczyć wiadomości jako dostarczonej", it) }
            }
        }

        // Użytkownik ma ten czat otwarty - wiadomość widzi od razu
        if (container.activeChatTracker.activeChatId == chatId) return

        showNotification(
            chatId = chatId,
            senderId = data[KEY_SENDER_ID].orEmpty(),
            isGroup = data[KEY_IS_GROUP].toBoolean(),
            title = data[KEY_TITLE] ?: remoteMessage.notification?.title ?: getString(R.string.notification_default_title),
            body = data[KEY_BODY] ?: remoteMessage.notification?.body.orEmpty()
        )
    }

    private fun showNotification(chatId: String, senderId: String, isGroup: Boolean, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createNotificationChannel()

        val chatIntent = if (isGroup) {
            Intent(this, GroupChatActivity::class.java)
        } else {
            Intent(this, ChatActivity::class.java).putExtra(Extras.OTHER_USER_ID, senderId)
        }.putExtra(Extras.CHAT_ID, chatId)

        // Osobny requestCode dla każdego czatu, inaczej powiadomienia nadpisywałyby sobie extras
        val pendingIntent = TaskStackBuilder.create(this)
            .addNextIntent(Intent(this, MainActivity::class.java))
            .addNextIntent(chatIntent)
            .getPendingIntent(notificationIdFor(chatId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        NotificationManagerCompat.from(this).notify(notificationIdFor(chatId), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_description)
            enableVibration(true)
            setShowBadge(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "MessagingService"
        private const val CHANNEL_ID = "chat_notifications"
        private const val KEY_CHAT_ID = "chatId"
        private const val KEY_MESSAGE_ID = "messageId"
        private const val KEY_SENDER_ID = "senderId"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_IS_GROUP = "isGroup"

        /** Jedno powiadomienie na czat; ekran czatu usuwa je po otwarciu. */
        fun notificationIdFor(chatId: String): Int = chatId.hashCode()
    }
}
