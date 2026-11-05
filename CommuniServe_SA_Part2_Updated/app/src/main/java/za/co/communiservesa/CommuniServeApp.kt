package za.co.communiservesa

import android.app.Application
import android.util.Log

/**
 * Application-level safety net.
 *
 * Every network and database call in this app already catches Throwable
 * and shows a toast instead of crashing (see MainActivity.callApi and
 * AppDatabase initialisation). This handler exists as a second line of
 * defence: if something outside those guarded call sites still throws,
 * it gets written to Logcat with the tag "CommuniServeSA-FATAL" before the
 * system's normal crash handling takes over, so the real cause is always
 * visible for debugging and for the demonstration video instead of a
 * silent "has stopped" dialog with no context.
 */
class CommuniServeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CommuniServeSA-FATAL", "Uncaught exception on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
