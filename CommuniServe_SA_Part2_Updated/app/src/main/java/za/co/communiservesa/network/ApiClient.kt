package za.co.communiservesa.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Holds the JWT returned after a successful login/registration. */
object SessionStore {
    var token: String? = null
}

/**
 * Builds and rebuilds the Retrofit client used to talk to the CommuniServe
 * SA REST API.
 *
 * IMPORTANT - emulator vs physical device:
 * 10.0.2.2 is a special alias that ONLY exists inside the Android Emulator
 * and points back to the host computer's localhost. A physical phone has
 * no idea what 10.0.2.2 means, so it cannot reach the API with that
 * address. For a real device, [configure] must be called with the
 * developer machine's LAN IP address instead, e.g. "http://192.168.1.10:3000/".
 * MainActivity exposes a "Server address" field that does exactly this at
 * runtime, so the same APK works on both without a rebuild.
 */
object ApiClient {

    private const val TAG = "ApiClient"

    @Volatile private var cachedApi: ApiService? = null
    @Volatile private var currentBaseUrl: String = ""

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
        SessionStore.token?.let { request.addHeader("Authorization", "Bearer $it") }
        chain.proceed(request.build())
    }

    private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        // 45s, not the previous 10s: Render's free tier puts an idle
        // service to sleep, and waking it back up can take 20-50 seconds.
        // A short timeout was turning a real "please wait" cold start into
        // a false "submission failed" for the person using the app.
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * (Re)configures Retrofit for [baseUrl]. Safe to call repeatedly - this
     * is how the app switches from the emulator-only address to a physical
     * device's real server address without needing a new build.
     *
     * Deliberately catches Throwable (not just Exception): a malformed
     * address throws IllegalArgumentException from Retrofit's builder, and
     * we never want a typo in a text field to crash the whole app.
     */
    @Synchronized
    fun configure(baseUrl: String) {
        val trimmed = baseUrl.trim()
        val normalised = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        if (normalised == currentBaseUrl && cachedApi != null) return
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl(normalised)
                .client(buildClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            cachedApi = retrofit.create(ApiService::class.java)
            currentBaseUrl = normalised
            Log.i(TAG, "API client configured for $normalised")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to configure API client for '$normalised'", t)
            // Leave any previously-working client in place rather than crash.
        }
    }

    /** Null until [configure] has succeeded at least once. */
    val api: ApiService? get() = cachedApi

    val baseUrl: String get() = currentBaseUrl
}
