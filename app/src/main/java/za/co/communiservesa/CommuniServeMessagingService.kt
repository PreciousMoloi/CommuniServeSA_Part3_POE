package za.co.communiservesa

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import za.co.communiservesa.i18n.S
import za.co.communiservesa.i18n.Strings
import za.co.communiservesa.push.PushManager
import za.co.communiservesa.util.Prefs

/**
 * Receives Firebase Cloud Messaging pushes
 * (https://firebase.google.com/docs/cloud-messaging/android/receive).
 *
 * - While the app is in the foreground, onMessageReceived runs and we draw the
 *   notification ourselves (so it is translated and tappable).
 * - While the app is in the background, FCM shows "notification" payloads itself.
 * - onNewToken keeps the backend in sync whenever Firebase rotates the token.
 */
class CommuniServeMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val prefs = Prefs(this)
        if (!prefs.notifications) return
        val title = message.notification?.title ?: message.data["title"] ?: "CommuniServe SA"
        val body = message.notification?.body ?: message.data["body"] ?: Strings.get(prefs.language, S.NOTIF_DEFAULT_BODY)
        Log.i("CommuniServeFCM", "Push received: $title")
        PushManager.show(this, title, body)
    }

    override fun onNewToken(token: String) {
        val prefs = Prefs(this)
        prefs.pushToken = token
        CoroutineScope(Dispatchers.IO).launch { PushManager.registerWithServer(prefs) }
    }
}
