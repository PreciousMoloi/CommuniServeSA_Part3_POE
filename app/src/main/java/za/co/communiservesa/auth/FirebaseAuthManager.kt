package za.co.communiservesa.auth

import android.util.Log
import com.google.firebase.FirebaseApp
import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
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

    data class SsoIdentity(val email: String, val name: String, val idToken: String)

    /** Thrown when the person closes the Google account chooser - not an error to show. */
    class SsoCancelledException : Exception("Sign-in cancelled")

    /**
     * Native "Continue with Google": Android's own account chooser bottom sheet (Credential
     * Manager) lists the Google accounts on the phone - no browser page. The Google ID token it
     * returns is turned into a Firebase credential, so the user also appears in Firebase
     * Authentication > Users. The verified email + name are then exchanged with our API
     * (POST /api/auth/sso) for a CommuniServe JWT.
     *
     * Needs: Google provider enabled in Firebase, the app's SHA-1 registered, and a
     * google-services.json downloaded AFTER that (it then contains the web client ID that the
     * build exposes as R.string.default_web_client_id).
     */
    suspend fun signInWithGoogle(activity: Activity): Result<SsoIdentity> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase is not configured (missing google-services.json).")
        )
        // Looked up by name so the project still compiles when google-services.json is absent (CI).
        val clientIdRes = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (clientIdRes == 0) return Result.failure(
            IllegalStateException("No web client ID. Enable Google sign-in in Firebase, add your SHA-1, then re-download google-services.json.")
        )
        return try {
            val option = GetSignInWithGoogleOption.Builder(activity.getString(clientIdRes)).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return Result.failure(IllegalStateException("Unexpected credential type"))
            }
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val user = firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await().user
                ?: return Result.failure(IllegalStateException("No user returned by Google"))
            val email = user.email ?: google.id
            // Firebase ID token: the API verifies its signature, issuer and audience server-side,
            // so a client can no longer claim someone else's email address.
            val firebaseIdToken = user.getIdToken(false).await().token
                ?: return Result.failure(IllegalStateException("Firebase did not return an ID token"))
            Result.success(SsoIdentity(email, user.displayName ?: google.displayName ?: email.substringBefore('@'), firebaseIdToken))
        } catch (e: GetCredentialCancellationException) {
            Result.failure(SsoCancelledException())
        } catch (t: Throwable) {
            Log.e(TAG, "Google SSO failed", t)
            Result.failure(t)
        }
    }

    /** The already-signed-in Google user with a freshly refreshed ID token, for retrying the API exchange. */
    suspend fun currentIdentity(): SsoIdentity? = try {
        val user = auth?.currentUser
        val token = user?.getIdToken(true)?.await()?.token
        val email = user?.email
        if (user == null || token.isNullOrBlank() || email.isNullOrBlank()) null
        else SsoIdentity(email, user.displayName ?: email.substringBefore('@'), token)
    } catch (t: Throwable) {
        Log.e(TAG, "Could not refresh Google ID token", t)
        null
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
