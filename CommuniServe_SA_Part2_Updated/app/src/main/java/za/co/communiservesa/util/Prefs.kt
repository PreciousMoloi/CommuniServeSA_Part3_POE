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

    var contributions: Int
        get() = p.getInt("contributions", 0)
        set(value) { p.edit().putInt("contributions", value).apply() }

    var notifications: Boolean
        get() = p.getBoolean("notifications", true)
        set(value) { p.edit().putBoolean("notifications", value).apply() }

    var darkMode: Boolean
        get() = p.getBoolean("dark", false)
        set(value) { p.edit().putBoolean("dark", value).apply() }

    /**
     * The REST API base URL. Defaults to the build-time value
     * (http://10.0.2.2:3000/, which ONLY works on the Android Emulator).
     * A physical device must set this to the developer machine's LAN IP,
     * e.g. http://192.168.1.10:3000/ - this is done from the Login or
     * Profile screen and persists here so it survives app restarts.
     */
    var apiBaseUrl: String
        get() = p.getString("apiBaseUrl", null) ?: BuildConfig.API_BASE_URL
        set(value) { p.edit().putString("apiBaseUrl", value).apply() }
}
