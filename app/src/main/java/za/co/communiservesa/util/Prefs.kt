package za.co.communiservesa.util

import android.content.Context
import za.co.communiservesa.BuildConfig

/**
 * Thin wrapper around SharedPreferences for everything the app needs to
 * remember between launches: the signed-in user, their app settings, and
 * the REST API address the client should talk to.
 */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("communiservesa", Context.MODE_PRIVATE)

    var email: String?
        get() = p.getString("email", null)
        set(value) { p.edit().putString("email", value).apply() }

    var token: String?
        get() = p.getString("token", null)
        set(value) { p.edit().putString("token", value).apply() }

    var name: String
        get() = p.getString("name", "Community Member") ?: "Community Member"
        set(value) { p.edit().putString("name", value).apply() }

    var language: String
        get() = p.getString("language", "English") ?: "English"
        set(value) { p.edit().putString("language", value).apply() }

    var radius: Int
        get() = p.getInt("radius", 10)
        set(value) { p.edit().putInt("radius", value).apply() }

    var notifications: Boolean
        get() = p.getBoolean("notifications", true)
        set(value) { p.edit().putBoolean("notifications", value).apply() }

    var darkMode: Boolean
        get() = p.getBoolean("dark", false)
        set(value) { p.edit().putBoolean("dark", value).apply() }

    // Last successful API results, kept as JSON so browsing still works offline.
    var cachedServicesJson: String?
        get() = p.getString("cachedServices", null)
        set(value) { p.edit().putString("cachedServices", value).apply() }

    var cachedEventsJson: String?
        get() = p.getString("cachedEvents", null)
        set(value) { p.edit().putString("cachedEvents", value).apply() }

    /** Latest FCM registration token (null until Firebase has issued one). */
    var pushToken: String?
        get() = p.getString("pushToken", null)
        set(value) { p.edit().putString("pushToken", value).apply() }

    /** Clears everything tied to the signed-in person (settings and caches stay). */
    fun clearSession() {
        p.edit().remove("email").remove("token").remove("name").apply()
    }

    /**
     * The REST API base URL. On the emulator the default is http://10.0.2.2:3000/; on a
     * physical phone the default is the hosted HTTPS API (see [ServerConfig]). A debug
     * build can still override it with a LAN address from the Login screen, which persists
     * here so it survives app restarts.
     */
    var apiBaseUrl: String
        get() = ServerConfig.resolve(
            stored = p.getString("apiBaseUrl", null),
            emulatorUrl = BuildConfig.API_BASE_URL,
            hostedUrl = BuildConfig.HOSTED_API_URL,
            isEmulator = isRunningOnEmulator()
        )
        set(value) { p.edit().putString("apiBaseUrl", value).apply() }

    /** Standard emulator fingerprints; a physical phone matches none of them. */
    private fun isRunningOnEmulator(): Boolean =
        android.os.Build.FINGERPRINT.startsWith("generic") ||
            android.os.Build.FINGERPRINT.contains("emulator") ||
            android.os.Build.HARDWARE.contains("goldfish") ||
            android.os.Build.HARDWARE.contains("ranchu") ||
            android.os.Build.MODEL.contains("Emulator") ||
            android.os.Build.MODEL.contains("Android SDK built for")
}
