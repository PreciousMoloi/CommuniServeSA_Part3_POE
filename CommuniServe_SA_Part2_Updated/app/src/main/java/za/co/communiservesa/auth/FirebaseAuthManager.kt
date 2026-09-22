package za.co.communiservesa.auth

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Thin, crash-safe wrapper around Firebase Authentication (email/password),
 * following the setup described in "Firebase Authentication" (create a
 * Firebase project, register this app, enable the Email/Password sign-in
 * method, download google-services.json into app/).
 *
 * This is intentionally SEPARATE from the app's own REST API login
 * (ApiService.register/login backed by server/server.js). The two are not
 * merged into one login, because the Node server issues and verifies its
 * OWN JWTs - it has no way to verify a Firebase ID token without the
 * Firebase Admin SDK and a service-account key, which is outside the scope
 * of this project. In practice this means:
 *   - "Sign In" / "Create Account" (REST) -> required to add/edit services,
 *     events and reviews, because those endpoints are protected by the
 *     API's own JWT `auth` middleware.
 *   - "Sign In with Firebase" / "Register with Firebase" (this class) ->
 *     demonstrates the Firebase Authentication SDK integration and gives
 *     the user a Firebase-managed identity (visible in the Firebase
 *     Console under Authentication > Users).
 *
 * Every function below fails safely instead of crashing: FirebaseAuth
 * .getInstance() throws IllegalStateException when no FirebaseApp has been
 * initialised (i.e. google-services.json has not been added / the
 * google-services plugin has not been enabled yet), and [isConfigured]
 * / the try-catch in [credentialCall] handle that case with a clear
 * message rather than letting it propagate.
 */
object FirebaseAuthManager {

    private const val TAG = "FirebaseAuthManager"

    /** True once a real google-services.json has been processed by the build. */
    val isConfigured: Boolean
        get() = try {
            FirebaseApp.getInstance()
            true
        } catch (t: Throwable) {
            false
        }

    private val auth: FirebaseAuth?
        get() = if (isConfigured) {
            try {
                FirebaseAuth.getInstance()
            } catch (t: Throwable) {
                Log.e(TAG, "FirebaseAuth.getInstance() failed", t)
                null
            }
        } else null

    val currentUserEmail: String? get() = auth?.currentUser?.email

    suspend fun register(email: String, password: String): Result<String> =
        credentialCall(email, password) { a, e, p -> a.createUserWithEmailAndPassword(e, p) }

    suspend fun signIn(email: String, password: String): Result<String> =
        credentialCall(email, password) { a, e, p -> a.signInWithEmailAndPassword(e, p) }


    /** Builds the Google account chooser. Requires a real Firebase project
     * and the server OAuth client ID in strings.xml (google-services.json
     * supplies the Firebase configuration). */
    fun googleClient(context: Context): com.google.android.gms.auth.api.signin.GoogleSignInClient? {
        if (!isConfigured) return null
        return try {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(za.co.communiservesa.R.string.google_server_client_id))
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, options)
        } catch (t: Throwable) {
            Log.e(TAG, "Google Sign-In client creation failed", t)
            null
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<String> {
        val firebaseAuth = auth ?: return Result.failure(IllegalStateException("Firebase Authentication isn't configured."))
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            Result.success(result.user?.email ?: "Google account")
        } catch (t: Throwable) {
            Log.e(TAG, "Google Sign-In failed", t)
            Result.failure(t)
        }
    }

    suspend fun messagingToken(): Result<String> = try {
        if (!isConfigured) Result.failure(IllegalStateException("Firebase is not configured."))
        else Result.success(FirebaseMessaging.getInstance().token.await())
    } catch (t: Throwable) {
        Log.e(TAG, "FCM token retrieval failed", t)
        Result.failure(t)
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (t: Throwable) {
            Log.e(TAG, "Firebase sign-out failed", t)
        }
    }

    private suspend fun credentialCall(
        email: String,
        password: String,
        call: (FirebaseAuth, String, String) -> com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult>
    ): Result<String> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException(
                "Firebase Authentication isn't set up yet. Add app/google-services.json and " +
                    "enable the google-services plugin - see README \"Firebase Authentication setup\"."
            )
        )
        return try {
            val result = call(firebaseAuth, email, password).await()
            Result.success(result.user?.email ?: email)
        } catch (t: Throwable) {
            Log.e(TAG, "Firebase credential call failed", t)
            Result.failure(t)
        }
    }
}
