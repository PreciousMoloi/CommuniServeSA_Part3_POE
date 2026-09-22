package za.co.communiservesa

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles incoming Firebase Cloud Messaging push notifications, per
 * https://firebase.google.com/docs/cloud-messaging/android/receive.
 * Firebase automatically instantiates this service and calls
 * onMessageReceived whenever a push arrives while the app is running.
 */
class CommuniServeMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: "CommuniServe SA"
        val body = message.notification?.body ?: "You have a new community update."
        val manager = getSystemService(NotificationManager::class.java)
        // Notification channels are required from Android 8 (API 26) onward;
        // creating the same channel again on later calls is a safe no-op.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel("communiservesa", "CommuniServe Alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
        try {
            if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            manager.notify(System.currentTimeMillis().toInt(), NotificationCompat.Builder(this, "communiservesa")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build())
        } catch (t: Throwable) {
            android.util.Log.e("CommuniServeFCM", "Unable to display push notification", t)
        }
    }
}
