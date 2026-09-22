package za.co.communiservesa

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import za.co.communiservesa.auth.FirebaseAuthManager
import za.co.communiservesa.data.AppDatabase
import za.co.communiservesa.data.PendingReview
import za.co.communiservesa.data.SavedItem
import za.co.communiservesa.network.*
import za.co.communiservesa.util.Prefs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var prefs: Prefs

    // Nullable on purpose: Room initialisation is wrapped in a try/catch in
    // onCreate() so that a database problem on a particular device degrades
    // to "offline features unavailable" instead of crashing the whole app.
    private var db: AppDatabase? = null

    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var services: List<ServiceDto> = emptyList()
    private var events: List<EventDto> = emptyList()

    // Holds the panel currently showing search results, so a repeated
    // search replaces its contents instead of stacking a new list underneath.
    private var resultsPanel: LinearLayout? = null

    // Set by "Use My Location" in Add Listing, and shown inline so location
    // capture is visibly tied to the form rather than just a passing toast.
    private var capturedLocationText: TextView? = null
    private var capturedLatitude: Double? = null
    private var capturedLongitude: Double? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(if (prefs.darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        SessionStore.token = prefs.token

        // Point Retrofit at whatever server address was last configured
        // (defaults to the emulator-only 10.0.2.2 address - see Prefs.apiBaseUrl).
        ApiClient.configure(prefs.apiBaseUrl)

        // Room must never be allowed to crash app startup: a corrupted
        // local database file or a device-specific SQLite quirk should
        // disable offline features, not take down the whole app.
        db = try {
            AppDatabase.get(this)
        } catch (t: Throwable) {
            Log.e(TAG, "Room database failed to initialise", t)
            null
        }

        createNotificationChannel()
        registerConnectivityCallback()
        if (prefs.email != null && SessionStore.token != null) showDiscover() else showLogin()
    }

    override fun onDestroy() {
        networkCallback?.let { callback ->
            try { connectivityManager?.unregisterNetworkCallback(callback) } catch (_: Throwable) { }
        }
        networkCallback = null
        super.onDestroy()
    }

    private fun registerConnectivityCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread { if (!isFinishing && SessionStore.token != null) lifecycleScope.launch { syncPendingReviews(false) } }
                }
            }
            networkCallback = callback
            connectivityManager?.registerDefaultNetworkCallback(callback)
        } catch (t: Throwable) { Log.e(TAG, "Connectivity callback unavailable", t) }
    }

    // ---------------------------------------------------------------------
    // Design system - colors, spacing, and small view-builder helpers.
    // Every screen is built from these, so changes here apply everywhere.
    // ---------------------------------------------------------------------

    private enum class BtnStyle { PRIMARY, SECONDARY, TEXT, DANGER, FIREBASE }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
    private fun dpf(v: Float): Float = v * resources.displayMetrics.density

    private fun rounded(bg: Int, radiusDp: Int = 14, strokeColor: Int? = null, strokeDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(radiusDp.toFloat())
            setColor(bg)
            if (strokeColor != null) setStroke(dp(strokeDp), strokeColor)
        }

    private fun base(title: String, subtitle: String? = null): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.cream_bg))
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        root.addView(banner(title, subtitle))
        scroll = ScrollView(this).apply {
            setBackgroundColor(color(R.color.cream_bg))
            addView(root)
        }
        return root
    }

    /** The teal branded header shown at the top of every screen. */
    private fun banner(title: String, subtitle: String?): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.teal_700), radiusDp = 20)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        }
        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) }
        }
        textBlock.addView(TextView(this).apply {
            text = title
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setTextColor(color(R.color.white))
        })
        if (subtitle != null) {
            textBlock.addView(TextView(this).apply {
                text = subtitle
                textSize = 12.5f
                setTextColor(Color.argb(230, 255, 255, 255))
                setPadding(0, dp(2), 0, 0)
            })
        }
        wrap.addView(logo)
        wrap.addView(textBlock)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(wrap)
            setPadding(0, 0, 0, dp(18))
        }
    }

    private fun setPage(title: String, subtitle: String? = null) {
        base(title, subtitle)
        resultsPanel = null
        setContentView(scroll)
    }

    /** Small caps-style section label used to group related controls. */
    private fun sectionLabel(text: String) {
        root.addView(TextView(this).apply {
            this.text = text.uppercase()
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.06f
            setTextColor(color(R.color.teal_700))
            setPadding(0, dp(18), 0, dp(6))
        })
    }

    private fun bodyText(text: String, size: Float = 15.5f, bold: Boolean = false, muted: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            if (bold) setTypeface(null, Typeface.BOLD)
            setTextColor(color(if (muted) R.color.ink_600 else R.color.ink_900))
            setLineSpacing(dpf(2f), 1f)
            root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }

    private fun field(hint: String, password: Boolean = false): EditText {
        val e = EditText(this).apply {
            this.hint = hint
            textSize = 15.5f
            setTextColor(color(R.color.ink_900))
            setHintTextColor(color(R.color.ink_400))
            background = rounded(color(R.color.surface), radiusDp = 12, strokeColor = color(R.color.divider), strokeDp = 1)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(e, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return e
    }

    private fun button(text: String, style: BtnStyle = BtnStyle.PRIMARY, action: () -> Unit): Button {
        val (bg, stroke, fg) = when (style) {
            BtnStyle.PRIMARY -> Triple(color(R.color.teal_700), null, color(R.color.white))
            BtnStyle.SECONDARY -> Triple(color(R.color.surface), color(R.color.teal_700), color(R.color.teal_700))
            BtnStyle.TEXT -> Triple(Color.TRANSPARENT, null, color(R.color.ink_600))
            BtnStyle.DANGER -> Triple(color(R.color.surface), color(R.color.error), color(R.color.error))
            BtnStyle.FIREBASE -> Triple(color(R.color.surface), color(R.color.orange_500), color(R.color.orange_700))
        }
        val b = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(fg)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = if (style == BtnStyle.TEXT) null
            else rounded(bg, radiusDp = 12, strokeColor = stroke, strokeDp = if (stroke != null) 1 else 0)
            elevation = if (style == BtnStyle.PRIMARY) dpf(2f) else 0f
            stateListAnimator = null
            setOnClickListener { action() }
        }
        root.addView(b, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        return b
    }

    /** A small rounded pill, e.g. "✓ Verified" or a category tag. */
    private fun chip(text: String, bg: Int, fg: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 11.5f
        setTypeface(null, Typeface.BOLD)
        setTextColor(fg)
        background = rounded(bg, radiusDp = 20)
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    /** A polished, tappable list row used for services, events and saved items. */
    private fun cardRow(title: String, meta: String, badgeText: String? = null, badgeIsAccent: Boolean = false, click: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.surface), radiusDp = 16, strokeColor = color(R.color.divider), strokeDp = 1)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dpf(1f)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }
        val accent = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4), -1)
            background = rounded(color(if (badgeIsAccent) R.color.orange_500 else R.color.teal_500), radiusDp = 4)
        }
        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) }
        }
        textBlock.addView(TextView(this).apply {
            this.text = title
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(color(R.color.ink_900))
        })
        textBlock.addView(TextView(this).apply {
            this.text = meta
            textSize = 13.5f
            setTextColor(color(R.color.ink_600))
            setPadding(0, dp(3), 0, 0)
        })
        if (badgeText != null) {
            val badge = chip(badgeText, color(R.color.teal_100), color(R.color.teal_700))
            textBlock.addView(badge, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        }
        val chevron = TextView(this).apply {
            this.text = "›"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(color(R.color.orange_500))
            setPadding(dp(8), 0, 0, 0)
        }
        card.addView(accent)
        card.addView(textBlock)
        card.addView(chevron)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(card)
            setPadding(0, 0, 0, dp(10))
        }
    }

    private fun stars(rating: Double): String {
        val full = rating.roundToInt().coerceIn(0, 5)
        return "★".repeat(full) + "☆".repeat(5 - full)
    }

    // ---------------------------------------------------------------------
    // Safe API calling
    // ---------------------------------------------------------------------

    /**
     * Runs [action] against the current ApiService and NEVER throws.
     *
     * This is the single fix for the "Sign In / Register crashes the app"
     * bug: the previous code used `catch (e: Exception)`, but a failure
     * while the Retrofit/OkHttp client itself is being built (for example
     * from a bad server address) surfaces as an Error, not an Exception,
     * and was not being caught - it crashed the app instead of showing
     * the intended "server unavailable" message. Catching Throwable here
     * closes that gap for every network call in the app, and every
     * failure is logged so it is visible in Logcat for debugging and for
     * the demonstration video.
     */
    private suspend fun <T> callApi(action: suspend (ApiService) -> T): Result<T> {
        val service = ApiClient.api
            ?: return Result.failure(IllegalStateException("Server address is not configured yet"))
        return try {
            Result.success(action(service))
        } catch (t: Throwable) {
            Log.e(TAG, "API call failed", t)
            Result.failure(t)
        }
    }

    private fun requireDb(): AppDatabase? {
        if (db == null) toast("Offline storage isn't available on this device.")
        return db
    }

    // ---------------------------------------------------------------------
    // Login / Register
    // ---------------------------------------------------------------------

    private fun showLogin() {
        setPage("CommuniServe SA", "Discover Local. Connect Community.")
        bodyText("Sign in to find trusted local services and events.", muted = true)
        val email = field("Email address")
        val password = field("Password", true)
        button("Sign In") {
            if (email.text.isNullOrBlank() || password.text.isNullOrBlank()) toast("Enter your email and password")
            else authenticate(email.text.toString().trim(), password.text.toString(), false)
        }
        button("Create Account", BtnStyle.SECONDARY) { showRegister() }
        button("Forgot Password?", BtnStyle.TEXT) { requestPasswordReset(email.text.toString().trim()) }
        button("Demo Mode", BtnStyle.TEXT) {
            prefs.email = "demo@communiservesa.co.za"; prefs.name = "Demo Member"; prefs.token = "demo"; SessionStore.token = "demo"
            showDiscover()
        }
        button("Sign In with Firebase", BtnStyle.FIREBASE) {
            if (email.text.isNullOrBlank() || password.text.isNullOrBlank()) toast("Enter your email and password")
            else firebaseAuthenticate(email.text.toString().trim(), password.text.toString(), register = false)
        }
        button("Continue with Google (SSO)", BtnStyle.FIREBASE) { startGoogleSignIn() }
        bodyText(
            if (FirebaseAuthManager.isConfigured) "Firebase Authentication: configured" else "Firebase Authentication: not configured yet (see README)",
            size = 12f, muted = true
        )

        addServerAddressControls()
    }

    private fun requestPasswordReset(email: String) {
        if (!email.contains("@")) { toast("Enter your email address first"); return }
        lifecycleScope.launch {
            callApi { api -> api.forgotPassword(mapOf("email" to email)) }
                .onSuccess { toast("If the account exists, a password-reset link will be sent.") }
                .onFailure { e -> toast("Could not contact the server: ${e.message ?: "unknown error"}") }
        }
    }

    private fun showRegister() {
        setPage("Create Account", "Join CommuniServe SA")
        val name = field("Full name")
        val email = field("Email address")
        val password = field("Password (8+ characters)", true)
        button("Register") {
            if (name.text.isNullOrBlank() || !email.text.contains("@") || password.text.length < 8) {
                toast("Use a name, valid email and password of at least 8 characters")
            } else authenticate(email.text.toString().trim(), password.text.toString(), true, name.text.toString().trim())
        }
        button("Register with Firebase", BtnStyle.FIREBASE) {
            if (!email.text.contains("@") || password.text.length < 6) {
                toast("Use a valid email and password of at least 6 characters")
            } else firebaseAuthenticate(email.text.toString().trim(), password.text.toString(), register = true, name = name.text.toString().trim())
        }
        button("Back to Sign In", BtnStyle.TEXT) { showLogin() }
    }

    /**
     * Sign-in/registration through Firebase Authentication (see
     * FirebaseAuthManager for why this is kept separate from the REST
     * login above). On success this stores the Firebase email locally so
     * the Discover screen can open, but it deliberately does NOT set
     * prefs.token: that token is the REST API's own JWT, and actions that
     * call the REST API (adding a listing, writing a review) still need a
     * REST sign-in because the Node server cannot verify a Firebase ID
     * token. Demo Mode / offline browsing still work normally either way.
     */
    private fun startGoogleSignIn() {
        val client = FirebaseAuthManager.googleClient(this)
        if (client == null) {
            toast("Google SSO needs the real Firebase google-services.json and Web client ID. See README.")
            return
        }
        try {
            startActivityForResult(client.signInIntent, 9001)
        } catch (t: Throwable) {
            Log.e(TAG, "Could not launch Google Sign-In", t)
            toast("Could not open Google Sign-In")
        }
    }

    @Deprecated("Activity result API kept simple for the assessment prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 9001 || data == null) return
        lifecycleScope.launch {
            try {
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val token = account.idToken
                if (token.isNullOrBlank()) { toast("Google did not return an ID token"); return@launch }
                FirebaseAuthManager.signInWithGoogle(token)
                    .onSuccess { email ->
                        prefs.email = email
                        prefs.name = account.displayName ?: email.substringBefore('@')
                        toast("Google SSO sign-in successful")
                        showDiscover()
                    }
                    .onFailure { e -> toast("Google SSO failed: ${e.message ?: "unknown error"}") }
            } catch (t: Throwable) {
                Log.e(TAG, "Google Sign-In result failed", t)
                toast("Google Sign-In was cancelled or failed")
            }
        }
    }

    private fun firebaseAuthenticate(email: String, password: String, register: Boolean, name: String = "") {
        lifecycleScope.launch {
            val result = if (register) FirebaseAuthManager.register(email, password) else FirebaseAuthManager.signIn(email, password)
            result.onSuccess { firebaseEmail ->
                prefs.email = firebaseEmail
                if (name.isNotBlank()) prefs.name = name
                toast("Firebase ${if (register) "account created" else "sign-in successful"}: $firebaseEmail")
                toast("Note: adding listings or reviews still needs a CommuniServe SA (REST) sign-in.")
                showDiscover()
            }.onFailure { e ->
                toast("Firebase ${if (register) "registration" else "sign-in"} failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    /**
     * Lets the person point the app at a real server address. 10.0.2.2 is
     * an emulator-only alias for "the computer this emulator runs on" - a
     * physical phone has no such alias, so it must be given the computer's
     * actual LAN IP address (Settings > Network on the PC, or `ipconfig` /
     * `ifconfig`), for example http://192.168.1.10:3000/. The phone and
     * the computer running the API must be on the same Wi-Fi network.
     */
    private fun addServerAddressControls() {
        sectionLabel("Server address (only needed once per device)")
        bodyText(
            "Emulator: leave the default. Physical phone: enter your computer's LAN IP, e.g. http://192.168.1.10:3000/ (phone and computer must share the same Wi-Fi).",
            size = 12.5f, muted = true
        )
        val server = field("API base URL").apply { setText(prefs.apiBaseUrl) }
        button("Save & Test Connection", BtnStyle.SECONDARY) {
            val url = server.text.toString().trim()
            if (url.isBlank()) { toast("Enter a server address"); return@button }
            prefs.apiBaseUrl = url
            ApiClient.configure(url)
            lifecycleScope.launch {
                callApi { api -> api.health() }
                    .onSuccess { toast("Connected to ${ApiClient.baseUrl}") }
                    .onFailure { e -> toast("Could not reach server: ${e.message ?: "unknown error"}") }
            }
        }
        bodyText("Current API: ${prefs.apiBaseUrl}", size = 12f, muted = true)
    }

    private fun authenticate(email: String, password: String, register: Boolean, name: String = "") {
        lifecycleScope.launch {
            val result = callApi { api ->
                if (register) api.register(AuthRequest(name, email, password))
                else api.login(AuthRequest(email = email, password = password))
            }
            result.onSuccess { response ->
                SessionStore.token = response.token
                prefs.email = response.user.email
                prefs.token = response.token
                prefs.name = response.user.name
                prefs.language = response.user.language
                prefs.radius = response.user.radius
                prefs.notifications = response.user.notifications
                prefs.contributions = response.user.contributions
                toast(if (register) "Account created successfully" else "Welcome back, ${response.user.name}")
                showDiscover()
            }.onFailure { e ->
                val message = when {
                    e is retrofit2.HttpException && e.code() == 401 ->
                        if (register) "Something went wrong creating your account. Please check your details and try again."
                        else "Incorrect email or password. Don't have an account? Tap Create Account below."
                    e is retrofit2.HttpException && e.code() == 409 ->
                        "An account with this email already exists. Try signing in instead."
                    e is retrofit2.HttpException ->
                        "Something went wrong (error ${e.code()}). Please try again."
                    e is java.io.IOException ->
                        "Can't reach the server. Check your connection or the server address below."
                    else ->
                        "Something went wrong. Try Demo Mode, or check the server address below."
                }
                toast(message)
            }

        }
    }

    // ---------------------------------------------------------------------
    // Discover / Services / Events
    // ---------------------------------------------------------------------

    private fun showDiscover() {
        setPage(t("Discover", "Ontdek", "Thola"), t("Find trusted local services and events near you", "Vind plaaslike dienste en geleenthede", "Thola izinsizakalo nemicimbi eduze"))
        val search = field(t("Search services or events", "Soek dienste of geleenthede", "Sesha izinsizakalo noma imicimbi"))
        sectionLabel(t("Category", "Kategorie", "Isigaba"))
        val category = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("All", "Plumber", "Electrician", "Tutor") ) }
        root.addView(category, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        sectionLabel(t("Minimum rating", "Minimum gradering", "Isilinganiso esincane"))
        val minRating = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Any", "3+", "4+", "4.5+")) }
        root.addView(minRating, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        sectionLabel(t("Search radius", "Soekradius", "Ibanga lokusesha"))
        val radius = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, (1..50).map { "$it km" })
            setSelection((prefs.radius - 1).coerceIn(0, 49))
        }
        root.addView(radius, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        button(t("Find Services", "Vind dienste", "Thola izinsizakalo")) {
            prefs.radius = radius.selectedItemPosition + 1
            loadServices(search.text.toString(), category.selectedItem.toString(), minRating.selectedItem.toString())
        }
        button(t("Community Events", "Gemeenskapsgeleenthede", "Imicimbi yomphakathi"), BtnStyle.SECONDARY) { loadEvents(search.text.toString()) }
        button(t("My Saved / Offline", "My gestoorde / vanlyn", "Engikugcinile / okungaxhunyiwe"), BtnStyle.SECONDARY) { showSaved() }
        button(t("Add Service / Event", "Voeg diens / geleentheid by", "Engeza insizakalo / umcimbi"), BtnStyle.SECONDARY) { showAddListing() }
        button("Pending Approvals", BtnStyle.SECONDARY) { showPendingApprovals() }
        button(t("Profile & Settings", "Profiel & Instellings", "Iphrofayela & Izilungiselelo"), BtnStyle.SECONDARY) { showProfile() }
        button("Refresh / Sync Offline Actions", BtnStyle.TEXT) { syncPendingReviews() }
        bodyText("Tip: saved services and review drafts remain available when the network is unavailable.", size = 13f, muted = true)
        // A dedicated, reusable panel for results so a repeated search replaces
        // its contents instead of stacking a new list underneath the old one.
        resultsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(resultsPanel)
        loadServices("", "All", "Any")
    }

    private fun loadServices(query: String, category: String = "All", minRating: String = "Any") {
        lifecycleScope.launch {
            callApi { api -> api.services(query, prefs.radius) }
                .onSuccess { result ->
                    val threshold = when (minRating) { "3+" -> 3.0; "4+" -> 4.0; "4.5+" -> 4.5; else -> 0.0 }
                    services = result.filter { (category == "All" || it.category == category) && it.rating >= threshold }
                    renderServices(offline = false)
                }
                .onFailure {
                    val threshold = when (minRating) { "3+" -> 3.0; "4+" -> 4.0; "4.5+" -> 4.5; else -> 0.0 }
                    services = demoServices().filter { query.isBlank() || (it.title + it.category + it.description).contains(query, true) }.filter { it.distanceKm <= prefs.radius && (category == "All" || it.category == category) && it.rating >= threshold }
                    renderServices(offline = true)
                }
        }
    }

    private fun renderServices(offline: Boolean) {
        val panel = resultsPanel ?: return
        panel.removeAllViews()
        sectionLabel("${if (offline) "Offline/demo results" else "Services"} (${services.size})")
        if (services.isEmpty()) panel.addView(bodyEmptyState("No services matched your search."))
        services.forEach { s ->
            panel.addView(cardRow(
                title = s.title,
                meta = "${s.category} • ${stars(s.rating)} ${s.rating} • ${"%.1f".format(s.distanceKm)} km",
                badgeText = if (s.verified) "✓ Verified" else null
            ) { showService(s) })
        }
    }

    private fun bodyEmptyState(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(color(R.color.ink_600))
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }

    private fun loadEvents(query: String) {
        lifecycleScope.launch {
            callApi { api -> api.events(query, prefs.radius) }
                .onSuccess { events = it }
                .onFailure { events = demoEvents().filter { e -> query.isBlank() || (e.title + e.category).contains(query, true) } }
            showEvents()
        }
    }

    private fun showEvents() {
        setPage(t("Community Events", "Gemeenskapsgeleenthede", "Imicimbi yomphakathi"))
        if (events.isEmpty()) root.addView(bodyEmptyState("No events found."))
        events.forEach { e ->
            root.addView(cardRow(title = e.title, meta = "${e.category} • ${e.dateTime}\n${e.venue}", badgeText = if (e.verified) "✓ Verified" else "Pending", badgeIsAccent = true) { showEvent(e) })
        }
        button("Back", BtnStyle.TEXT) { showDiscover() }
    }

    private fun showService(service: ServiceDto) {
        setPage(service.title, service.category)
        val statusChip = chip(
            if (service.verified) "✓ Verified local listing" else "Pending verification",
            color(if (service.verified) R.color.teal_100 else R.color.surface_alt),
            color(if (service.verified) R.color.teal_700 else R.color.ink_600)
        )
        root.addView(statusChip, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12) })
        bodyText("${stars(service.rating)}  ${service.rating} • ${service.distanceKm} km away", bold = true)
        bodyText(service.description, muted = true)
        button("Call Provider") {
            if (service.phone.isNotBlank()) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${service.phone}"))) else toast("Provider phone number not available")
        }
        button("Directions", BtnStyle.SECONDARY) { openMap(service.latitude, service.longitude, service.title) }
        button("Save for Offline", BtnStyle.SECONDARY) { saveService(service) }
        button("Write Review", BtnStyle.SECONDARY) { showReview(service) }
        button("View Reviews", BtnStyle.TEXT) { showReviews(service) }
        button("Back", BtnStyle.TEXT) { showDiscover() }
    }

    private fun saveService(s: ServiceDto) {
        val database = requireDb() ?: return
        lifecycleScope.launch {
            try {
                database.savedDao().insert(SavedItem(s.id, s.title, "service", s.category, "${s.description}\n${s.rating}★ • ${s.distanceKm} km"))
                toast("Saved. Available offline.")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to save service offline", t)
                toast("Could not save this item offline.")
            }
        }
    }

    private fun showSaved() {
        setPage(t("Saved / Offline", "Gestoorde / Vanlyn", "Okugcinwe / Okungaxhunyiwe"))
        val listPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listPanel)
        button("Back", BtnStyle.TEXT) { showDiscover() }
        val database = requireDb()
        if (database == null) {
            listPanel.addView(bodyEmptyState("Offline storage isn't available on this device."))
            return
        }
        lifecycleScope.launch {
            try {
                database.savedDao().observeAll().collect { items ->
                    listPanel.removeAllViews()
                    if (items.isEmpty()) {
                        listPanel.addView(bodyEmptyState("No saved items yet."))
                    }
                    items.forEach { item ->
                        listPanel.addView(cardRow(
                            title = item.title,
                            meta = "${item.type.replaceFirstChar { c -> c.uppercase() }} • ${item.category}\n${item.details}"
                        ) { showSavedDetail(item)})
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load saved items", t)
                listPanel.addView(bodyEmptyState("Could not load saved items."))
            }
        }
    }
    /**
     * Shows a saved item's cached details entirely from RoomDB, with no
     * network call - this is what proves the "Saved / Offline" feature
     * actually works offline rather than just showing a list.
     */
    private fun showSavedDetail(item: SavedItem) {
        setPage(item.title, item.category)
        val offlineChip = chip("📴 Offline copy", color(R.color.surface_alt), color(R.color.ink_600))
        root.addView(offlineChip, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12) })
        bodyText(item.type.replaceFirstChar { c -> c.uppercase() }, bold = true)
        bodyText(item.details, muted = true)
        bodyText(
            "This information was saved to your device and does not require an internet connection to view.",
            size = 12.5f, muted = true
        )
        button("Back", BtnStyle.TEXT) { showSaved() }
    }

    private fun showReview(service: ServiceDto) {
        setPage("Review: ${service.title}")
        if (SessionStore.token.isNullOrBlank() || SessionStore.token == "demo") {
            val warning = bodyText(
                "⚠ Not signed in with a CommuniServe SA account - this review will queue as offline, but it can only sync once you Sign In (REST, not Firebase/Demo) from Profile & Settings.",
                size = 13.5f
            )
            warning.setTextColor(color(R.color.error))
        }
        val rating = RatingBar(this).apply { numStars = 5; stepSize = 1f; rating = 5f; layoutParams = LinearLayout.LayoutParams(-2, -2) }
        root.addView(rating, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12) })
        val text = field("Write your review")
        button("Submit Review") {
            val req = ReviewRequest(rating.rating.roundToInt(), text.text.toString().trim())
            lifecycleScope.launch {
                callApi { api -> api.addReview(service.id, req) }
                    .onSuccess { prefs.contributions = prefs.contributions + 1; toast("Review submitted") }
                    .onFailure {
                        val database = requireDb()
                        if (database != null) {
                            try {
                                database.pendingReviewDao().insert(PendingReview(serviceId = service.id, rating = req.rating, text = req.text))
                                toast("No network. Review queued for sync.")
                            } catch (t: Throwable) {
                                Log.e(TAG, "Failed to queue offline review", t)
                                toast("Could not submit or queue this review.")
                            }
                        } else {
                            toast("No network and offline storage is unavailable. Review not saved.")
                        }
                    }
                showService(service)
            }
        }
        button("Back", BtnStyle.TEXT) { showService(service) }
    }

    private fun showReviews(service: ServiceDto) {
        setPage("Reviews", service.title)
        lifecycleScope.launch {
            val reviews = callApi { api -> api.reviews(service.id) }.getOrDefault(emptyList())
            if (reviews.isEmpty()) root.addView(bodyEmptyState("No reviews available yet."))
            reviews.forEach { r ->
                root.addView(cardRow(title = "${stars(r.rating.toDouble())}  ${r.author}", meta = r.text) { })
            }
        }
        button("Back", BtnStyle.TEXT) { showService(service) }
    }

    private fun showEvent(event: EventDto) {
        setPage(event.title, event.category)
        bodyText("${event.dateTime}\n${event.venue}\nOrganiser: ${event.organiser}", bold = true)
        bodyText(event.description, muted = true)
        button("RSVP") { rsvpToEvent(event) }
        button("Open Map", BtnStyle.SECONDARY) { openMap(event.latitude, event.longitude, event.venue) }
        button("Save Event Offline", BtnStyle.SECONDARY) {
            val database = requireDb()
            if (database != null) {
                lifecycleScope.launch {
                    try {
                        database.savedDao().insert(SavedItem(event.id, event.title, "event", event.category, "${event.dateTime}\n${event.venue}"))
                        toast("Event saved offline")
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to save event offline", t)
                        toast("Could not save this event offline.")
                    }
                }
            }
        }
        button("Back", BtnStyle.TEXT) { showEvents() }
    }

    /**
     * RSVP now actually persists (previously this only showed a toast).
     * It is saved through the same offline store as "Save Event Offline",
     * tagged with type "rsvp" so it shows up distinctly under My Saved /
     * Offline and survives app restarts.
     */
    private fun rsvpToEvent(event: EventDto) {
        val database = requireDb() ?: return
        lifecycleScope.launch {
            var remoteSaved = false
            if (!SessionStore.token.isNullOrBlank() && SessionStore.token != "demo") {
                remoteSaved = callApi { api -> api.rsvp(event.id) }.isSuccess
            }
            try {
                database.savedDao().insert(SavedItem("rsvp-${event.id}", event.title, "rsvp", event.category, "You're attending • ${event.dateTime}\n${event.venue}"))
                toast(if (remoteSaved) "RSVP saved online and offline" else "RSVP saved offline")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to save RSVP", t)
                toast("Could not save your RSVP.")
            }
        }
    }
    private fun showAddListing() {
        setPage("Add Service / Event", "Submitted listings are reviewed before going live")
        // /api/services and /api/events on the server both require a valid
        // REST JWT (see server/server.js "auth" middleware). Signing in
        // with Firebase only does NOT provide that token by design (see
        // FirebaseAuthManager) - so warn up front rather than let the
        // person hit a confusing "submission failed" after filling in the
        // whole form.
        if (SessionStore.token.isNullOrBlank() || SessionStore.token == "demo") {
            val warning = bodyText(
                "⚠ You're not signed in with a CommuniServe SA account yet. Adding a listing needs a REST sign-in (not Firebase, not Demo Mode) - go to Profile & Settings and use Sign In / Create Account first.",
                size = 13.5f
            )
            warning.setTextColor(color(R.color.error))
        }
        sectionLabel("Listing type")
        val type = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Service", "Event")) }
        root.addView(type, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        val title = field("Title")
        val category = field("Category")
        val description = field("Description")
        val phone = field("Phone / Venue")
        button("Use My Location", BtnStyle.SECONDARY) { requestLocation() }
        val locationLabel = bodyText("Location not captured yet", size = 12.5f, muted = true)
        capturedLocationText = locationLabel
        button("Submit for Approval") {
            if (title.text.isNullOrBlank() || category.text.isNullOrBlank()) {
                toast("Title and category are required")
            } else lifecycleScope.launch {
                val request = CreateListingRequest(title.text.toString(), category.text.toString(), description.text.toString(), phone.text.toString(), type.selectedItem.toString(), capturedLatitude, capturedLongitude)
                val result = if (type.selectedItem.toString() == "Service") callApi { api -> api.addService(request) }
                             else callApi { api -> api.addEvent(request) }
                result.onSuccess { toast("Submitted for approval"); showDiscover() }
                    .onFailure { e -> toast("Submission failed: ${e.message ?: "server unreachable"}. If Render was asleep, wait ~30s and try again.") }
            }
        }
        button("Back", BtnStyle.TEXT) { showDiscover() }
    }
    private fun showPendingApprovals() {
        setPage("Pending Approvals", "Review listings submitted by the community")
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(panel)
        button("Back", BtnStyle.TEXT) { showDiscover() }

        lifecycleScope.launch {
            callApi { api -> api.pending() }
                .onSuccess { items ->
                    panel.removeAllViews()
                    if (items.isEmpty()) {
                        panel.addView(bodyEmptyState("No listings waiting for approval."))
                    }
                    items.forEach { item ->
                        panel.addView(cardRow(
                            title = "${item.type.replaceFirstChar { it.uppercase() }} • ${item.id}",
                            meta = "Status: ${item.status}",
                            badgeText = "Approve"
                        ) {
                            lifecycleScope.launch {
                                callApi { api -> api.approve(item.id) }
                                    .onSuccess { toast("Approved"); showPendingApprovals() }
                                    .onFailure { e -> toast("Could not approve: ${e.message ?: "unknown error"}") }
                            }
                        })
                    }
                }
                .onFailure { e -> toast("Could not load pending items: ${e.message ?: "unknown error"}") }
        }
    }
    private fun showProfile() {
        setPage(t("Profile & Settings", "Profiel & Instellings", "Iphrofayela & Izilungiselelo"))
        val heroChip = chip(
            if (prefs.name != "Community Member") "★ Local Hero • ${prefs.contributions} contribution(s)" else "New Member",
            color(R.color.orange_500), color(R.color.white)
        )
        root.addView(heroChip, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(10) })
        bodyText(prefs.name, bold = true, size = 18f)
        bodyText(prefs.email ?: "", muted = true)
        sectionLabel("Profile name")
        val profileName = field("Full name").apply { setText(prefs.name) }

        sectionLabel("Language")
        val lang = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("English", "Afrikaans", "isiZulu")); setSelection(listOf("English", "Afrikaans", "isiZulu").indexOf(prefs.language).coerceAtLeast(0)) }
        root.addView(lang, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        sectionLabel("Search radius: ${prefs.radius} km")
        val radius = SeekBar(this).apply { max = 49; progress = prefs.radius - 1 }
        root.addView(radius, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val notifications = Switch(this).apply { text = "Contextual notifications"; isChecked = prefs.notifications; textSize = 15f }
        root.addView(notifications, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val dark = Switch(this).apply { text = "Dark mode preference"; isChecked = prefs.darkMode; textSize = 15f }
        root.addView(dark, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        button("Save Settings") {
            prefs.name = profileName.text.toString().trim().ifBlank { prefs.name }; prefs.language = lang.selectedItem.toString(); prefs.radius = radius.progress + 1; prefs.notifications = notifications.isChecked; prefs.darkMode = dark.isChecked
            AppCompatDelegate.setDefaultNightMode(if (prefs.darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
            lifecycleScope.launch {
                val result = callApi { api -> api.updateProfile(UserDto("", prefs.name, prefs.email ?: "", prefs.language, prefs.notifications, prefs.radius, 0)) }
                result.onSuccess { toast("Settings saved to device and server") }
                    .onFailure { toast("Settings saved on this device; server sync unavailable") }
            }
            if (prefs.notifications) notifyUser("CommuniServe SA", "Settings saved. Nearby alerts are enabled.")
        }
        button("Sync Pending Reviews", BtnStyle.SECONDARY) { syncPendingReviews() }
        button("Test Notification", BtnStyle.SECONDARY) { requestNotificationPermissionAndTest() }
        button("Check Push Notification Setup", BtnStyle.TEXT) { checkPushSetup() }
        button("Sign Out", BtnStyle.DANGER) { SessionStore.token = null; prefs.token = null; prefs.email = null; FirebaseAuthManager.signOut(); showLogin() }
        addServerAddressControls()
        button("Back", BtnStyle.TEXT) { showDiscover() }
    }

    private fun requestNotificationPermissionAndTest() {
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 45)
            toast("Allow notifications, then tap Test Notification again")
            return
        }
        notifyUser("CommuniServe SA", "Notifications are working on this device.")
        toast("Test notification sent")
    }

    private fun checkPushSetup() {
        lifecycleScope.launch {
            FirebaseAuthManager.messagingToken()
                .onSuccess { token -> toast("FCM is configured. Token received.") }
                .onFailure { e -> toast("FCM not configured: ${e.message ?: "add Firebase configuration"}") }
        }
    }

    private fun openMap(latitude: Double?, longitude: Double?, query: String) {
        val uri = if (latitude != null && longitude != null) Uri.parse("geo:${latitude},${longitude}?q=${Uri.encode(query)}")
        else Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Throwable) { toast("No map application is installed") }
    }

    private fun syncPendingReviews(showToast: Boolean = true) {
        val database = requireDb() ?: return
        lifecycleScope.launch {
            val pending = try {
                database.pendingReviewDao().all()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to read pending reviews", t)
                toast("Could not read queued reviews.")
                return@launch
            }
            var synced = 0
            for (review in pending) {
                val result = callApi { api -> api.addReview(review.serviceId, ReviewRequest(review.rating, review.text)) }
                if (result.isSuccess) {
                    try {
                        database.pendingReviewDao().delete(review.localId)
                        synced++
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to remove synced review from queue", t)
                    }
                }
            }
            if (showToast) toast(if (synced == 0) "No queued reviews were synchronised" else "$synced queued review(s) synchronised")
        }
    }

    // ---------------------------------------------------------------------
    // Location / notifications / misc
    // ---------------------------------------------------------------------

    private fun requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 44)
            return
        }
        try {
            locationClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        capturedLatitude = loc.latitude
                        capturedLongitude = loc.longitude
                        capturedLocationText?.text = "Location captured: ${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)}"
                        toast("Location captured")
                    } else {
                        capturedLocationText?.text = "Location unavailable - try again outdoors or with GPS on"
                        toast("Location unavailable")
                    }
                }
                .addOnFailureListener { t ->
                    Log.e(TAG, "Location request failed", t)
                    capturedLocationText?.text = "Location unavailable"
                    toast("Location unavailable")
                }
        } catch (t: SecurityException) {
            Log.e(TAG, "Location permission missing at call time", t)
            toast("Location permission is required")
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = android.app.NotificationChannel("communiservesa", "CommuniServe Alerts", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create notification channel", t)
        }
    }

    private fun notifyUser(title: String, message: String) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val n = NotificationCompat.Builder(this, "communiservesa").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message).setAutoCancel(true).build()
            NotificationManagerCompat.from(this).notify(101, n)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to show notification", t)
        }
    }

    private fun t(en: String, af: String, zu: String) = when (prefs.language) { "Afrikaans" -> af; "isiZulu" -> zu; else -> en }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun demoServices() = listOf(
        ServiceDto("s1", "Community Plumbing Services", "Plumber", "Emergency and maintenance plumbing for homes and small businesses.", "", 4.8, 2.1, true),
        ServiceDto("s2", "Reliable Electrical Repairs", "Electrician", "Residential electrical repairs and installations.", "", 4.6, 3.4, true),
        ServiceDto("s3", "Maths Tutor", "Tutor", "One-to-one tutoring for school learners.", "", 4.9, 1.8, true)
    )
    private fun demoEvents() = listOf(
        EventDto("e1", "Community Clean-up", "Community", "Join neighbours for a Saturday clean-up.", "Local Community Hall", "Saturday 09:00", 1.2),
        EventDto("e2", "Youth Coding Meetup", "Education", "A beginner-friendly coding and technology session.", "Innovation Hub", "Sunday 13:00", 4.0)
    )
}
