package za.co.communiservesa

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import za.co.communiservesa.push.PushManager
import za.co.communiservesa.util.Prefs

/**
 * Application-level setup and safety net.
 *
 * - Applies the saved light/dark preference before any screen is drawn.
 * - Creates the notification channel once, so FCM pushes that arrive while the
 *   app is closed have a channel to land in.
 * - Logs any uncaught exception with the tag "CommuniServeSA-FATAL" before the
 *   system's normal crash handling takes over, so the cause is always visible
 *   in Logcat. (Every network and database call is also individually guarded.)
 */
class CommuniServeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CommuniServeSA-FATAL", "Uncaught exception on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        try {
            AppCompatDelegate.setDefaultNightMode(
                if (Prefs(this).darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            PushManager.ensureChannel(this)
        } catch (t: Throwable) {
            Log.e("CommuniServeSA", "Start-up preferences failed", t)
        }
    }
}
