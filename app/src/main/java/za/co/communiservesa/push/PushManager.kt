package za.co.communiservesa.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import za.co.communiservesa.MainActivity
import za.co.communiservesa.R
import za.co.communiservesa.network.ApiClient
import za.co.communiservesa.network.DeviceRequest
import za.co.communiservesa.util.Prefs

/**
 * Real-time notifications through Firebase Cloud Messaging (FCM).
 *
 * - The phone subscribes to the "community_alerts" topic, so anything sent to that
 *   topic from the Firebase Console (Messaging > New campaign > Notification)
 *   arrives on the phone in real time, even when the app is closed.
 * - The FCM token is also registered with our REST API (POST /api/devices) so the
 *   backend knows which devices exist.
 * - Every call is wrapped in try/catch: with no google-services.json (e.g. in CI)
 *   push simply stays off instead of crashing the app.
 */
object PushManager {
    private const val TAG = "PushManager"
    const val CHANNEL_ID = "communiservesa"
    const val TOPIC = "community_alerts"

    private fun firebaseReady(): Boolean = try { FirebaseApp.getInstance(); true } catch (t: Throwable) { false }

    fun ensureChannel(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "CommuniServe Alerts", NotificationManager.IMPORTANCE_DEFAULT)
                channel.description = "Nearby events, approvals and community updates"
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create notification channel", t)
        }
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Shows a notification that opens the app when tapped. Returns false if it could not be shown. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, title: String, body: String): Boolean {
        return try {
            if (!canPost(context)) return false
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to show notification", t)
            false
        }
    }

    /** Fetches the FCM token, subscribes to the alerts topic and registers the token with the API. */
    suspend fun enable(context: Context, prefs: Prefs) {
        if (!firebaseReady()) { Log.i(TAG, "Firebase not configured - push disabled"); return }
        try {
            val messaging = FirebaseMessaging.getInstance()
            val token = messaging.token.await()
            prefs.pushToken = token

            Log.d("FCM_TOKEN", "My Token is: $token") // <--- ADD THIS LINE
            messaging.subscribeToTopic(TOPIC).await()
            Log.i(TAG, "Subscribed to $TOPIC")
            registerWithServer(prefs)
        } catch (t: Throwable) {
            Log.e(TAG, "Could not enable push notifications", t)
        }
    }

    suspend fun disable() {
        if (!firebaseReady()) return
        try { FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC).await() }
        catch (t: Throwable) { Log.e(TAG, "Could not unsubscribe from push", t) }
    }

    /** Sends the stored token to the API (needs a real sign-in; silently skipped otherwise). */
    suspend fun registerWithServer(prefs: Prefs) {
        val token = prefs.pushToken ?: return
        val session = prefs.token
        if (session.isNullOrBlank() || session == "demo") return
        try { ApiClient.api?.registerDevice(DeviceRequest(token)) }
        catch (t: Throwable) { Log.w(TAG, "Device registration will retry later: ${t.message}") }
    }
}
