package za.co.communiservesa

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import retrofit2.HttpException
import za.co.communiservesa.auth.FirebaseAuthManager
import za.co.communiservesa.data.AppDatabase
import za.co.communiservesa.data.PendingListing
import za.co.communiservesa.data.PendingReview
import za.co.communiservesa.data.SavedItem
import za.co.communiservesa.i18n.S
import za.co.communiservesa.i18n.Strings
import za.co.communiservesa.network.*
import za.co.communiservesa.push.PushManager
import za.co.communiservesa.util.MapMath
import za.co.communiservesa.util.Prefs
import za.co.communiservesa.util.Validators
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

/**
 * CommuniServe SA - single-activity app. Every screen is built in code from a
 * small design system (see the helpers below) and translated through [tr].
 *
 * Part 3 feature map:
 *  - Register / log in ........ authenticate(), showRegister()      (bcrypt-hashed on the API)
 *  - Single sign-on (SSO) ..... googleSignIn() -> Firebase + POST /api/auth/sso
 *  - Settings ................. showProfile() (language, radius, push, dark mode)
 *  - REST API ................. ApiClient / ApiService -> server/server.js
 *  - Offline mode + sync ...... Room (saved items, queued reviews/listings), cached lists,
 *                               automatic sync on reconnect (registerConnectivityWatcher)
 *  - Real-time notifications .. PushManager + CommuniServeMessagingService (FCM)
 *  - Multi-language ........... i18n/Strings.kt (English, Afrikaans, isiZulu)
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var prefs: Prefs
    private val gson = Gson()

    // Nullable on purpose: a Room problem on one device must degrade to "offline
    // features unavailable", never crash the whole app.
    private var db: AppDatabase? = null

    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var services: List<ServiceDto> = emptyList()
    private var events: List<EventDto> = emptyList()
    private var eventsOffline = false

    // Per-screen references. They are reset in setPage() so async results can never
    // be drawn onto a screen the person has already left.
    private var resultsPanel: LinearLayout? = null
    private var queueNote: TextView? = null
    private var capturedLocationText: TextView? = null
    private var capturedLat: Double? = null
    private var capturedLng: Double? = null

    // Back button / predictive-back target for the current screen (null = leave the app).
    private var onBack: (() -> Unit)? = null

    // Map screen state (null whenever another screen is showing).
    private var mapView: MapView? = null
    private var refreshMapLocation: (() -> Unit)? = null
    private var userPoint: GeoPoint? = null
    private val splashAnimators = mutableListOf<ObjectAnimator>()

    private var authInFlight = false
    private var syncInFlight = false
    @Volatile private var wasOffline = false
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) toast(tr(S.NOTIF_DENIED))
        }

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) {
                val refresh = refreshMapLocation
                if (refresh != null) refresh() else requestLocation()
            } else toast(tr(S.LOCATION_NEEDED))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15/16 enforce edge-to-edge; opting in explicitly keeps older versions consistent.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        SessionStore.token = prefs.token
        ApiClient.configure(prefs.apiBaseUrl)
        warmUpServer()

        db = try {
            AppDatabase.get(this)
        } catch (t: Throwable) {
            Log.e(TAG, "Room database failed to initialise", t)
            null
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val target = onBack
                if (target != null) target() else finish()
            }
        })

        PushManager.ensureChannel(this)
        registerConnectivityWatcher()
        val proceed = {
            if (prefs.email != null && SessionStore.token != null) {
                startBackgroundServices()
                showDiscover()
            } else showLogin()
        }
        // Animated splash only on a fresh launch (not when the activity is recreated, e.g. dark mode).
        if (savedInstanceState == null) showSplash(proceed) else proceed()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        splashAnimators.forEach { it.cancel() }
        disposeMap()
        try {
            connectivityCallback?.let { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not unregister network callback", t)
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Design system - colours, spacing and small view-builder helpers.
    // ---------------------------------------------------------------------

    private enum class BtnStyle { PRIMARY, SECONDARY, TEXT, DANGER, GOOGLE }

    private fun tr(key: S, vararg args: Any): String = Strings.get(prefs.language, key, *args)
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

    private fun base(title: String, subtitle: String? = null, bare: Boolean = false): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.cream_bg))
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        if (!bare) root.addView(banner(title, subtitle))
        scroll = ScrollView(this).apply {
            setBackgroundColor(color(R.color.cream_bg))
            clipToPadding = false
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
            contentDescription = "CommuniServe SA logo"
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
            setTextColor(Color.WHITE)
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

    private fun iconView(res: Int, tint: Int, sizeDp: Int = 24): ImageView = ImageView(this).apply {
        setImageResource(res)
        setColorFilter(tint)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun tintedDrawable(res: Int, tint: Int): android.graphics.drawable.Drawable? =
        ContextCompat.getDrawable(this, res)?.mutate()?.also { it.setTint(tint) }

    /** Opens the maps app on a "near me" search (needs no API key; uses the phone's own location). */
    private fun searchNearby(query: String) {
        val uri = userPoint?.let { Uri.parse("geo:${it.latitude},${it.longitude}?q=${Uri.encode(query)}") }
            ?: Uri.parse("geo:0,0?q=${Uri.encode("$query near me")}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(packageManager) != null) safeStart(intent)
        else safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/${Uri.encode("$query near me")}")))
    }

    /** One place type opens straight away; several offer a short picker. */
    private fun showNearby(options: List<Pair<String, String>>) {
        if (options.size == 1) { searchNearby(options[0].second); return }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(tr(S.NEAR_TITLE))
            .setItems(options.map { it.first }.toTypedArray()) { _, i -> searchNearby(options[i].second) }
            .show()
    }

    private enum class Tab { DISCOVER, EVENTS, MAP, SAVED, PROFILE }

    private fun setPage(title: String, subtitle: String? = null, back: (() -> Unit)? = null, tab: Tab? = null, bare: Boolean = false) {
        disposeMap()
        base(title, subtitle, bare)
        resultsPanel = null
        queueNote = null
        capturedLocationText = null
        onBack = back
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.cream_bg))
        }
        container.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (tab != null) container.addView(bottomNav(tab), LinearLayout.LayoutParams(-1, -2))
        // Edge-to-edge: pad the screen by the status bar, navigation bar, cutout and keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(container)
        applyFallbackInsets(container)
    }

    /**
     * Safety net: if the system never reports bar insets (some devices/themes), pad by the
     * status and navigation bar sizes from resources so content never sits under the system bars.
     */
    private fun applyFallbackInsets(container: View) {
        container.post {
            if (container.paddingTop == 0 && container.paddingBottom == 0) {
                fun size(name: String): Int {
                    val id = resources.getIdentifier(name, "dimen", "android")
                    return if (id > 0) resources.getDimensionPixelSize(id) else 0
                }
                container.setPadding(0, size("status_bar_height"), 0, size("navigation_bar_height"))
            }
        }
    }

    /** Persistent bottom navigation for the five main destinations. */
    private fun bottomNav(active: Tab): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(color(R.color.surface))
            elevation = dpf(8f)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        val items = listOf(
            Triple(Tab.DISCOVER, R.drawable.ic_nav_home, tr(S.DISCOVER)),
            Triple(Tab.EVENTS, R.drawable.ic_nav_events, tr(S.NAV_EVENTS)),
            Triple(Tab.MAP, R.drawable.ic_nav_map, tr(S.NAV_MAP)),
            Triple(Tab.SAVED, R.drawable.ic_nav_saved, tr(S.NAV_SAVED)),
            Triple(Tab.PROFILE, R.drawable.ic_nav_profile, tr(S.NAV_PROFILE)),
        )
        items.forEach { (tab, glyph, label) ->
            val on = tab == active
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(56)
                isClickable = true
                isFocusable = true
                contentDescription = label
                setPadding(dp(2), dp(4), dp(2), dp(4))
                setOnClickListener {
                    if (on) return@setOnClickListener
                    when (tab) {
                        Tab.DISCOVER -> showDiscover()
                        Tab.EVENTS -> loadEvents("")
                        Tab.MAP -> showMap()
                        Tab.SAVED -> showSaved()
                        Tab.PROFILE -> showProfile()
                    }
                }
            }
            val pill = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = if (on) rounded(color(R.color.teal_700), radiusDp = 16) else null
                setPadding(dp(18), dp(4), dp(18), dp(4))
            }
            pill.addView(iconView(glyph, if (on) Color.WHITE else color(R.color.ink_600)))
            item.addView(pill)
            item.addView(TextView(this).apply {
                text = label
                textSize = 11.5f
                maxLines = 1
                gravity = Gravity.CENTER
                setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (on) color(R.color.teal_700) else color(R.color.ink_600))
                setPadding(0, dp(2), 0, 0)
            })
            bar.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return bar
    }

    /** Small caps-style label that groups related controls. */
    private fun sectionLabel(text: String, parent: LinearLayout = root): TextView {
        val label = TextView(this).apply {
            this.text = text.uppercase()
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.06f
            setTextColor(color(R.color.teal_500))
            setPadding(0, dp(18), 0, dp(6))
        }
        parent.addView(label)
        return label
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

    private fun field(hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        val e = EditText(this).apply {
            this.hint = hint
            textSize = 15.5f
            setTextColor(color(R.color.ink_900))
            setHintTextColor(color(R.color.ink_400))
            background = rounded(color(R.color.surface), radiusDp = 16, strokeColor = color(R.color.divider), strokeDp = 1)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            // Single- vs multi-line comes from the input type itself (calling isSingleLine here
            // would silently drop the password masking).
            inputType = type
            minHeight = dp(48)
        }
        root.addView(e, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return e
    }

    private fun passwordField(hint: String): EditText =
        field(hint, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

    private fun emailField(hint: String): EditText =
        field(hint, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)

    private fun button(text: String, style: BtnStyle = BtnStyle.PRIMARY, action: () -> Unit): Button {
        val (bg, stroke, fg) = when (style) {
            BtnStyle.PRIMARY -> Triple(color(R.color.teal_700), null, Color.WHITE)
            BtnStyle.SECONDARY -> Triple(color(R.color.surface), color(R.color.teal_500), color(R.color.teal_500))
            BtnStyle.TEXT -> Triple(Color.TRANSPARENT, null, color(R.color.ink_600))
            BtnStyle.DANGER -> Triple(color(R.color.surface), color(R.color.error), color(R.color.error))
            BtnStyle.GOOGLE -> Triple(color(R.color.surface), color(R.color.divider), color(R.color.ink_900))
        }
        val b = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(fg)
            minHeight = dp(52)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = if (style == BtnStyle.TEXT) null
            else rounded(bg, radiusDp = 28, strokeColor = stroke, strokeDp = if (stroke != null) 1 else 0)
            elevation = if (style == BtnStyle.PRIMARY) dpf(2f) else 0f
            stateListAnimator = null
            if (style == BtnStyle.GOOGLE) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_google_g, 0, 0, 0)
                compoundDrawablePadding = dp(12)
            }
            setOnClickListener { action() }
        }
        root.addView(b, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        return b
    }

    /** A small rounded pill, e.g. "Verified" or a category tag. */
    private fun chip(text: String, bg: Int, fg: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 11.5f
        setTypeface(null, Typeface.BOLD)
        setTextColor(fg)
        background = rounded(bg, radiusDp = 20)
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    /** The English / Afrikaans / isiZulu picker shown on the login screen. */
    private fun languageRow(onChange: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        Strings.languages.forEach { lang ->
            val selected = lang == prefs.language
            row.addView(TextView(this).apply {
                text = lang
                textSize = 13.5f
                gravity = Gravity.CENTER
                minHeight = dp(44)
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (selected) Color.WHITE else color(R.color.teal_500))
                background = rounded(
                    if (selected) color(R.color.teal_700) else color(R.color.surface),
                    radiusDp = 22, strokeColor = color(R.color.teal_500), strokeDp = 1
                )
                setPadding(dp(14), dp(8), dp(14), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener { prefs.language = lang; onChange() }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4); marginEnd = dp(4) })
        }
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
    }

    /** A polished, tappable list row used for services, events and saved items. */
    private fun cardRow(title: String, meta: String, badgeText: String? = null, badgeIsAccent: Boolean = false, click: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.surface), radiusDp = 16, strokeColor = color(R.color.divider), strokeDp = 1)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dpf(2f)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }
        val accent = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            gravity = Gravity.CENTER
            text = title.trim().take(1).uppercase()
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(color(if (badgeIsAccent) R.color.orange_700 else R.color.teal_500), radiusDp = 14)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
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
            val badge = chip(badgeText, color(R.color.teal_100), color(R.color.teal_500))
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

    private fun emptyState(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(color(R.color.ink_600))
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }

    private fun stars(rating: Double): String {
        val full = rating.roundToInt().coerceIn(0, 5)
        return "★".repeat(full) + "☆".repeat(5 - full)
    }

    private fun km(v: Double): String = "%.1f".format(Locale.US, v)

    // ---------------------------------------------------------------------
    // Safe API calling and shared helpers
    // ---------------------------------------------------------------------

    /**
     * Runs [action] against the current ApiService and NEVER throws. Catching
     * Throwable (not just Exception) matters: a bad server address fails while
     * the Retrofit/OkHttp client is built and surfaces as an Error. Every failure
     * is logged so it is visible in Logcat.
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

    /** True once the person holds a real CommuniServe JWT (Demo Mode's "demo" token does not count). */
    private fun isRestSignedIn(): Boolean {
        val token = SessionStore.token
        return !token.isNullOrBlank() && token != "demo"
    }

    private enum class Outcome { OK, RETRY_LATER, DROP }

    /** Decides what to do with a queued action after trying to send it. */
    private fun outcomeOf(result: Result<*>): Outcome {
        if (result.isSuccess) return Outcome.OK
        val e = result.exceptionOrNull()
        return if (e is HttpException && e.code() in 400..499 && e.code() != 401 && e.code() != 429) Outcome.DROP else Outcome.RETRY_LATER
    }

    private fun requireDb(): AppDatabase? {
        if (db == null) toast(tr(S.NO_STORAGE))
        return db
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(tr(S.NO_APP_FOR_ACTION))
        } catch (t: Throwable) {
            Log.e(TAG, "Could not start activity", t)
            toast(tr(S.NO_APP_FOR_ACTION))
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, if (message.length > 60) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    private fun typeLabel(type: String): String = when (type) {
        "service" -> tr(S.SERVICE)
        "event" -> tr(S.EVENT)
        "rsvp" -> tr(S.RSVP)
        else -> type
    }

    // ---------------------------------------------------------------------
    // Login / Register / SSO
    // ---------------------------------------------------------------------

    private val guestEmail = "demo@communiservesa.co.za"

    /** Branded header used above the sign-in and registration cards. */
    private fun authHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(4), 0, dp(18))
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.app_logo)
            contentDescription = "CommuniServe SA logo"
            background = rounded(color(R.color.surface), radiusDp = 24, strokeColor = color(R.color.divider), strokeDp = 1)
            elevation = dpf(2f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }, LinearLayout.LayoutParams(dp(84), dp(84)))
        addView(TextView(this@MainActivity).apply {
            text = "CommuniServe SA"
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(color(R.color.teal_700))
            setPadding(0, dp(10), 0, dp(2))
        })
        addView(TextView(this@MainActivity).apply {
            text = tr(S.TAGLINE).uppercase()
            textSize = 11.5f
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(color(R.color.ink_600))
        })
    }

    /** A rounded input with a leading icon and, for passwords, a show/hide eye. */
    private fun iconField(icon: Int, hint: String, type: Int, password: Boolean = false): EditText {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.cream_bg), radiusDp = 16, strokeColor = color(R.color.divider), strokeDp = 1)
            setPadding(dp(16), 0, dp(8), 0)
            minimumHeight = dp(56)
        }
        row.addView(iconView(icon, color(R.color.teal_700), 22))
        val edit = EditText(this).apply {
            this.hint = hint
            textSize = 15.5f
            setTextColor(color(R.color.ink_900))
            setHintTextColor(color(R.color.ink_400))
            background = null
            inputType = type
            typeface = Typeface.DEFAULT
            setPadding(dp(12), dp(14), dp(8), dp(14))
        }
        row.addView(edit, LinearLayout.LayoutParams(0, -2, 1f))
        if (password) {
            var shown = false
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_eye)
                setColorFilter(color(R.color.teal_700))
                contentDescription = hint
                isClickable = true
                isFocusable = true
                setPadding(dp(9), dp(9), dp(9), dp(9))
                setOnClickListener {
                    shown = !shown
                    setImageResource(if (shown) R.drawable.ic_eye_off else R.drawable.ic_eye)
                    edit.inputType = InputType.TYPE_CLASS_TEXT or
                        (if (shown) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
                    edit.typeface = Typeface.DEFAULT
                    edit.setSelection(edit.text.length)
                }
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
        }
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return edit
    }

    private fun pillify(b: Button, filled: Boolean) {
        b.background = if (filled) rounded(color(R.color.teal_700), radiusDp = 28)
        else rounded(color(R.color.surface), radiusDp = 28, strokeColor = color(R.color.teal_500), strokeDp = 2)
        b.minHeight = dp(56)
    }

    private fun orDivider() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun line() = View(this).apply { setBackgroundColor(color(R.color.divider)) }
        row.addView(line(), LinearLayout.LayoutParams(0, dp(1), 1f))
        row.addView(TextView(this).apply { text = tr(S.OR_DIVIDER); textSize = 13f; setTextColor(color(R.color.ink_600)); setPadding(dp(12), 0, dp(12), 0) })
        row.addView(line(), LinearLayout.LayoutParams(0, dp(1), 1f))
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(14) })
    }

    private fun linkRow(prefix: String, link: String, action: () -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(TextView(this).apply { text = "$prefix "; textSize = 14f; setTextColor(color(R.color.ink_600)) })
        row.addView(TextView(this).apply {
            text = link
            textSize = 14f
            minHeight = dp(44)
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(null, Typeface.BOLD)
            setTextColor(color(R.color.teal_700))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        })
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
    }

    private fun guestButtonAndNote() {
        val b = button(tr(S.GUEST_CONTINUE), BtnStyle.SECONDARY) {
            prefs.email = guestEmail; prefs.name = "Guest"; prefs.token = "demo"; SessionStore.token = "demo"
            showDiscover()
        }
        b.background = rounded(color(R.color.teal_100), radiusDp = 28)
        b.minHeight = dp(56)
        b.setTextColor(color(R.color.teal_700))
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(tintedDrawable(R.drawable.ic_nav_profile, color(R.color.teal_700)), null, null, null)
        b.compoundDrawablePadding = dp(10)
        bodyText(tr(S.GUEST_FOOT), size = 12.5f, muted = true)
    }

    /** Shared sign-in / registration layout: header, then one rounded card holding the form. */
    private fun authScreen(title: String, subtitle: String, register: Boolean = false, form: () -> Unit) {
        setPage("", null, bare = true)
        languageRow { if (register) showRegister() else showLogin() }
        val outer = root
        outer.addView(authHeader())
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.surface), radiusDp = 28, strokeColor = color(R.color.divider), strokeDp = 1)
            elevation = dpf(3f)
            setPadding(dp(20), dp(22), dp(20), dp(14))
        }
        outer.addView(card, LinearLayout.LayoutParams(-1, -2))
        root = card
        card.addView(TextView(this).apply { text = title; textSize = 25f; setTypeface(null, Typeface.BOLD); setTextColor(color(R.color.ink_900)) })
        card.addView(TextView(this).apply {
            text = subtitle; textSize = 14.5f; setTextColor(color(R.color.ink_600)); setPadding(0, dp(4), 0, dp(18))
        })
        form()
        root = outer
        if (BuildConfig.DEBUG && !register) addServerAddressControls()
    }

    private fun showLogin() {
        val authUi = getSharedPreferences("auth_ui", MODE_PRIVATE)
        authScreen(tr(S.AUTH_LOGIN_TITLE), tr(S.AUTH_LOGIN_SUB)) {
            val email = iconField(R.drawable.ic_mail, tr(S.HINT_EMAIL), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            val password = iconField(R.drawable.ic_lock, tr(S.HINT_PASSWORD), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, password = true)
            val remember = android.widget.CheckBox(this).apply {
                text = tr(S.REMEMBER_ME)
                textSize = 14f
                setTextColor(color(R.color.ink_600))
                isChecked = authUi.getBoolean("remember", false)
                minHeight = dp(48)
            }
            if (remember.isChecked) email.setText(authUi.getString("email", ""))
            val optionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            optionsRow.addView(remember, LinearLayout.LayoutParams(0, -2, 1f))
            optionsRow.addView(TextView(this).apply {
                text = tr(S.FORGOT_PASSWORD)
                textSize = 14f
                minHeight = dp(48)
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(null, Typeface.BOLD)
                setTextColor(color(R.color.teal_700))
                isClickable = true
                isFocusable = true
                setOnClickListener { toast(tr(S.FORGOT_UNAVAILABLE)) }
            })
            root.addView(optionsRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            pillify(button(tr(S.SIGN_IN)) {
                val e = email.text.toString().trim()
                val p = password.text.toString()
                when {
                    e.isBlank() || p.isBlank() -> toast(tr(S.ERR_ENTER_CREDENTIALS))
                    !Validators.isValidEmail(e) -> toast(tr(S.ERR_INVALID_EMAIL))
                    else -> {
                        // "Remember me" keeps only the email address, never the password.
                        authUi.edit().putBoolean("remember", remember.isChecked).putString("email", if (remember.isChecked) e else "").apply()
                        authenticate(e, p, register = false)
                    }
                }
            }, true)
            orDivider()
            pillify(button(tr(S.CONTINUE_GOOGLE), BtnStyle.GOOGLE) { googleSignIn() }, false)
            linkRow(tr(S.NO_ACCOUNT), tr(S.SIGN_UP)) { showRegister() }
            guestButtonAndNote()
        }
    }

    private fun showRegister() {
        authScreen(tr(S.AUTH_REG_TITLE), tr(S.AUTH_REG_SUB), register = true) {
            val name = iconField(R.drawable.ic_nav_profile, tr(S.HINT_NAME), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
            val email = iconField(R.drawable.ic_mail, tr(S.HINT_EMAIL), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            val password = iconField(R.drawable.ic_lock, tr(S.HINT_PASSWORD_NEW), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, password = true)
            val confirm = iconField(R.drawable.ic_lock, tr(S.HINT_CONFIRM), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, password = true)
            pillify(button(tr(S.REGISTER)) {
                val n = name.text.toString().trim()
                val e = email.text.toString().trim()
                val p = password.text.toString()
                when {
                    !Validators.isValidName(n) -> toast(tr(S.ERR_NAME_REQUIRED))
                    !Validators.isValidEmail(e) -> toast(tr(S.ERR_INVALID_EMAIL))
                    !Validators.isStrongPassword(p) -> toast(tr(S.ERR_WEAK_PASSWORD))
                    p != confirm.text.toString() -> toast(tr(S.ERR_PASSWORDS_MATCH))
                    else -> authenticate(e, p, register = true, name = n)
                }
            }, true)
            orDivider()
            pillify(button(tr(S.CONTINUE_GOOGLE), BtnStyle.GOOGLE) { googleSignIn() }, false)
            linkRow(tr(S.HAVE_ACCOUNT), tr(S.LOG_IN_LINK)) { showLogin() }
            guestButtonAndNote()
        }
    }

    /** Stores the session returned by the API and opens the app. */
    private fun completeSignIn(response: AuthResponse) {
        SessionStore.token = response.token
        prefs.token = response.token
        prefs.email = response.user.email
        prefs.name = response.user.name
        // Local choices win, except a fresh install picks up the language saved on the server.
        if (prefs.language == Strings.ENGLISH && response.user.language in Strings.languages) prefs.language = response.user.language
        startBackgroundServices()
        showDiscover()
    }

    /** Email + password against our own REST API (the API stores only a bcrypt hash). */
    private fun authenticate(email: String, password: String, register: Boolean, name: String = "") {
        if (authInFlight) return
        authInFlight = true
        lifecycleScope.launch {
            try {
                val result = callApi { api ->
                    if (register) api.register(AuthRequest(name, email, password))
                    else api.login(AuthRequest(email = email, password = password))
                }
                result.onSuccess { response ->
                    toast(if (register) tr(S.ACCOUNT_CREATED) else tr(S.WELCOME_BACK, response.user.name))
                    completeSignIn(response)
                }.onFailure { e -> toast(describeAuthError(e, register)) }
            } finally {
                authInFlight = false
            }
        }
    }

    private fun describeAuthError(e: Throwable, register: Boolean): String = when {
        e is HttpException && e.code() == 401 -> tr(S.ERR_BAD_LOGIN)
        e is HttpException && e.code() == 409 -> tr(S.ERR_EMAIL_EXISTS)
        e is HttpException && e.code() == 400 && register -> tr(S.ERR_WEAK_PASSWORD)
        e is HttpException -> tr(S.ERR_SERVER, e.code())
        e is IOException -> tr(S.ERR_NETWORK)
        else -> tr(S.ERR_GENERIC)
    }

    /**
     * Single sign-on: the person signs in with Google (Firebase Authentication), and the
     * verified identity is exchanged with our API for a CommuniServe JWT so that Google users
     * can also write reviews and add listings - one identity, one session.
     */
    private fun googleSignIn() {
        if (!FirebaseAuthManager.isConfigured) { toast(tr(S.SSO_NOT_CONFIGURED)); return }
        if (authInFlight) return
        authInFlight = true
        lifecycleScope.launch {
            try {
                FirebaseAuthManager.signInWithGoogle(this@MainActivity)
                    .onSuccess { identity ->
                        val ssoRequest = SsoRequest(identity.email, identity.name, idToken = identity.idToken)
                        var ssoResult = callApi { api -> api.sso(ssoRequest) }
                        if (isTransient(ssoResult.exceptionOrNull())) {
                            // Wake a sleeping server, then try the exchange once more before falling back.
                            Log.w(TAG, "SSO exchange failed (${ssoResult.exceptionOrNull()?.message}); retrying once")
                            callApi { api -> api.health() }
                            ssoResult = callApi { api -> api.sso(ssoRequest) }
                        }
                        ssoResult
                            .onSuccess { response ->
                                toast(tr(S.SSO_SUCCESS, identity.email))
                                completeSignIn(response)
                            }
                            .onFailure { e ->
                                if (e is HttpException && e.code() in 400..499 && e.code() != 404 || e !is HttpException && e !is IOException) {
                                    toast(describeAuthError(e, register = false))
                                } else {
                                    // Google itself succeeded but our API is down / not updated yet:
                                    // let the person in with their Google identity; publishing is queued.
                                    prefs.email = identity.email
                                    prefs.name = identity.name
                                    prefs.token = "demo"
                                    SessionStore.token = "demo"
                                    toast(tr(S.SSO_LIMITED))
                                    startBackgroundServices()
                                    showDiscover()
                                }
                            }
                    }
                    .onFailure { e ->
                        if (e !is FirebaseAuthManager.SsoCancelledException) toast(tr(S.SSO_FAILED, e.message ?: "unknown error"))
                    }
            } finally {
                authInFlight = false
            }
        }
    }

    /**
     * Lets a developer point the (debug) app at a real server address. 10.0.2.2 only works
     * inside the Android Emulator; a physical phone needs the computer's LAN IP.
     */
    private fun addServerAddressControls() {
        // Kept out of the way: normal users never need to see a server address.
        val firstAdvanced = root.childCount + 1
        button(tr(S.CONN_SETTINGS), BtnStyle.TEXT) {
            val show = root.getChildAt(firstAdvanced).visibility != View.VISIBLE
            for (i in firstAdvanced until root.childCount) root.getChildAt(i).visibility = if (show) View.VISIBLE else View.GONE
        }
        sectionLabel(tr(S.SERVER_ADDRESS))
        bodyText(tr(S.SERVER_HELP), size = 12.5f, muted = true)
        val server = field(tr(S.HINT_API), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI).apply { setText(prefs.apiBaseUrl) }
        button(tr(S.SAVE_TEST), BtnStyle.SECONDARY) {
            val url = server.text.toString().trim()
            when {
                url.isBlank() -> toast(tr(S.ENTER_SERVER))
                !Validators.isValidServerUrl(url) -> toast(tr(S.ENTER_SERVER))
                else -> {
                    prefs.apiBaseUrl = url
                    ApiClient.configure(url)
                    lifecycleScope.launch {
                        callApi { api -> api.health() }
                            .onSuccess { toast(tr(S.CONNECTED, ApiClient.baseUrl)) }
                            .onFailure { e -> toast(tr(S.UNREACHABLE, e.message ?: "unknown error")) }
                    }
                }
            }
        }
        bodyText(tr(S.CURRENT_API, prefs.apiBaseUrl), size = 12f, muted = true)
        for (i in firstAdvanced until root.childCount) root.getChildAt(i).visibility = View.GONE
    }

    // ---------------------------------------------------------------------
    // Discover / Services / Events
    // ---------------------------------------------------------------------

    /**
     * Free hosting puts an idle API to sleep; waking it can take 30+ seconds. Pinging /health
     * as soon as the app opens means the server is usually awake by the time the person taps
     * Sign in, Submit or Sync. Failures are only logged - this never blocks or crashes the UI.
     */
    private fun warmUpServer() {
        lifecycleScope.launch {
            callApi { api -> api.health() }
                .onSuccess { Log.i(TAG, "API warm-up OK at ${ApiClient.baseUrl}") }
                .onFailure { Log.w(TAG, "API warm-up failed at ${ApiClient.baseUrl}: ${it.message}") }
        }
    }

    /** True for failures worth one more attempt: no connection, a sleeping/restarting server (5xx) or 404 from an old deploy. */
    private fun isTransient(e: Throwable?): Boolean =
        e is IOException || (e is HttpException && (e.code() >= 500 || e.code() == 404))

    /**
     * "Retry connection": a reachable server is not the same as a signed-in session. If the person
     * signed in with Google while the API was down, repeat the Google-to-API exchange now, then
     * send anything that was queued while in limited mode.
     */
    private fun retryServerConnection() {
        lifecycleScope.launch {
            val health = callApi { api -> api.health() }
            if (health.isFailure) { toast(tr(S.UNREACHABLE)); return@launch }
            if (SessionStore.token != "demo") { toast(tr(S.CONNECTED, ApiClient.baseUrl)); return@launch }
            val identity = FirebaseAuthManager.currentIdentity()
            if (identity == null) { toast(tr(S.SIGN_IN_AGAIN)); return@launch }
            callApi { api -> api.sso(SsoRequest(identity.email, identity.name, idToken = identity.idToken)) }
                .onSuccess { response ->
                    toast(tr(S.SSO_SUCCESS, identity.email))
                    completeSignIn(response)
                    syncAll(silent = true)
                }
                .onFailure { e ->
                    Log.w(TAG, "Retry SSO exchange failed", e)
                    toast(when {
                        e is HttpException && e.code() == 503 -> tr(S.SSO_SERVER_NOT_READY)
                        e is HttpException && e.code() == 404 -> tr(S.SSO_SERVER_OUTDATED)
                        else -> describeAuthError(e, register = false)
                    })
                }
        }
    }

    private fun showDiscover() {
        setPage(tr(S.DISCOVER), tr(S.DISCOVER_SUB), tab = Tab.DISCOVER)
        val search = field(tr(S.SEARCH_HINT))
        // Connection status: honest about whether the API session is real.
        val limited = SessionStore.token == "demo"
        if (!limited) root.addView(
            chip("\u25CF " + tr(S.ONLINE_STATUS), color(R.color.teal_100), color(R.color.teal_700)),
            LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12) }
        )

        val radius = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, (1..50).map { "$it km" })
            setSelection((prefs.radius - 1).coerceIn(0, 49))
        }
        fun runSearch(q: String) {
            prefs.radius = Validators.clampRadius(radius.selectedItemPosition + 1)
            loadServices(q)
        }
        // Category chips: "All" searches the directory; the others open "near me" results in Maps.
        val cats = listOf(
            Triple(R.drawable.ic_cat_all, tr(S.CAT_ALL), emptyList<Pair<String, String>>()),
            Triple(R.drawable.ic_cat_health, tr(S.CAT_HEALTH), listOf(
                tr(S.NEAR_PHARMACY) to "pharmacies", tr(S.NEAR_HOSPITAL) to "hospitals", tr(S.NEAR_CLINIC) to "clinics")),
            Triple(R.drawable.ic_cat_food, tr(S.CAT_FOOD), listOf(tr(S.NEAR_RESTAURANT) to "restaurants")),
            Triple(R.drawable.ic_cat_transport, tr(S.CAT_TRANSPORT), listOf(
                tr(S.NEAR_TAXI) to "taxi ranks", tr(S.NEAR_BUS) to "bus stops", tr(S.NEAR_TRAIN) to "train stations")),
            Triple(R.drawable.ic_cat_repairs, tr(S.CAT_REPAIRS), listOf(tr(S.NEAR_REPAIR) to "repair shops")),
            Triple(R.drawable.ic_cat_cleaning, tr(S.CAT_CLEANING), listOf(tr(S.NEAR_CLEANING) to "cleaning services")),
        )
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cats.forEachIndexed { i, (icon, label, nearby) ->
            val fg = if (i == 0) Color.WHITE else color(R.color.teal_700)
            val c = TextView(this).apply {
                text = label
                textSize = 13.5f
                minHeight = dp(44)
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(fg)
                setCompoundDrawablesRelativeWithIntrinsicBounds(tintedDrawable(icon, fg), null, null, null)
                compoundDrawablePadding = dp(8)
                background = rounded(
                    if (i == 0) color(R.color.teal_700) else color(R.color.surface),
                    radiusDp = 22, strokeColor = color(R.color.teal_500), strokeDp = 1
                )
                setPadding(dp(14), dp(8), dp(16), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (nearby.isEmpty()) { search.setText(""); runSearch("") } else showNearby(nearby)
                }
            }
            chipRow.addView(c, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        button(tr(S.FIND_SERVICES)) { runSearch(search.text.toString().trim()) }

        // Quick actions as two-up tiles instead of a wall of buttons.
        val tiles = listOf(
            R.drawable.ic_nav_events to tr(S.COMMUNITY_EVENTS) to { prefs.radius = Validators.clampRadius(radius.selectedItemPosition + 1); loadEvents(search.text.toString().trim()) },
            R.drawable.ic_nav_map to tr(S.MAP_OPEN) to { showMap() },
            R.drawable.ic_nav_saved to tr(S.SAVED_OFFLINE) to { showSaved() },
            R.drawable.ic_add to tr(S.QUICK_ADD) to { showAddListing() },
        )
        tiles.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { idx, t ->
                val (iconLabel, action) = t
                val (glyph, label) = iconLabel
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(color(R.color.surface), radiusDp = 16, strokeColor = color(R.color.divider), strokeDp = 1)
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    minimumHeight = dp(88)
                    isClickable = true
                    isFocusable = true
                    contentDescription = label
                    setOnClickListener { action() }
                }
                tile.addView(LinearLayout(this).apply {
                    gravity = Gravity.CENTER
                    background = rounded(color(R.color.teal_100), radiusDp = 12)
                    addView(iconView(glyph, color(R.color.teal_700), 22))
                }, LinearLayout.LayoutParams(dp(40), dp(40)))
                tile.addView(TextView(this).apply {
                    text = label
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(color(R.color.ink_900))
                    setPadding(0, dp(8), 0, 0)
                })
                row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = if (idx == 0) 0 else dp(5); marginEnd = if (idx == 0) dp(5) else 0
                })
            }
            root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        run {
            val outer = root
            val pair = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            outer.addView(pair, LinearLayout.LayoutParams(-1, -2))
            root = pair
            button(tr(S.PENDING_APPROVALS), BtnStyle.SECONDARY) { showPendingApprovals() }
            button(tr(S.SYNC_NOW), BtnStyle.SECONDARY) { syncAll(silent = false) }
            root = outer
            for (i in 0 until pair.childCount) {
                (pair.getChildAt(i) as? Button)?.textSize = 13.5f
                pair.getChildAt(i).layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = if (i == 0) 0 else dp(5); marginEnd = if (i == 0) dp(5) else 0; bottomMargin = dp(10)
                }
            }
        }
        if (limited) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(color(R.color.surface), radiusDp = 16, strokeColor = color(R.color.orange_500), strokeDp = 1)
                setPadding(dp(16), dp(14), dp(16), dp(8))
            }
            card.addView(TextView(this).apply {
                text = if (prefs.email == guestEmail) tr(S.GUEST_TITLE) else tr(S.LIMITED_TITLE)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(color(R.color.orange_700))
            })
            card.addView(TextView(this).apply {
                text = if (prefs.email == guestEmail) tr(S.GUEST_NOTE) else tr(S.SSO_LIMITED)
                textSize = 13.5f
                setTextColor(color(R.color.ink_900))
                setPadding(0, dp(6), 0, dp(4))
            })
            card.addView(Button(this).apply {
                text = if (prefs.email == guestEmail) tr(S.SIGN_IN) else tr(S.RETRY_CONNECTION); isAllCaps = false; minHeight = dp(48)
                setTextColor(color(R.color.teal_700)); background = null
                setOnClickListener { if (prefs.email == guestEmail) showLogin() else retryServerConnection() }
            })
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
        }
        queueNote = bodyText("", size = 13f, bold = true).apply { visibility = View.GONE; setTextColor(color(R.color.orange_700)) }
        bodyText(tr(S.TIP_OFFLINE), size = 13f, muted = true)
        // One reusable panel for results, so a repeated search replaces the list instead of stacking.
        resultsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(resultsPanel)
        updateQueueNote()
        loadServices("")
    }

    private fun <T> readCache(json: String?, type: Class<Array<T>>): List<T> = try {
        if (json.isNullOrBlank()) emptyList() else gson.fromJson(json, type)?.toList() ?: emptyList()
    } catch (t: Throwable) {
        Log.w(TAG, "Ignoring unreadable cache", t)
        emptyList()
    }

    private fun matches(query: String, vararg parts: String) =
        query.isBlank() || parts.joinToString(" ").contains(query, ignoreCase = true)

    private fun loadServices(query: String) {
        lifecycleScope.launch {
            callApi { api -> api.services(query, prefs.radius) }
                .onSuccess { result ->
                    services = result
                    if (query.isBlank()) prefs.cachedServicesJson = gson.toJson(result)
                    renderServices(offline = false)
                }
                .onFailure {
                    // Offline: show the last successful results (or the built-in demo data on first launch).
                    val cached = readCache(prefs.cachedServicesJson, Array<ServiceDto>::class.java).ifEmpty { demoServices() }
                    services = cached.filter { matches(query, it.title, it.category, it.description) && it.distanceKm <= prefs.radius }
                    renderServices(offline = true)
                }
        }
    }

    private fun renderServices(offline: Boolean) {
        val panel = resultsPanel ?: return
        panel.removeAllViews()
        if (offline) {
            panel.addView(chip(tr(S.OFFLINE_BADGE), color(R.color.surface_alt), color(R.color.ink_600)),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })
        }
        sectionLabel("${if (offline) tr(S.OFFLINE_RESULTS) else tr(S.SERVICES)} (${services.size})", panel)
        if (services.isEmpty()) panel.addView(emptyState(tr(S.NO_SERVICES)))
        services.forEach { s ->
            panel.addView(cardRow(
                title = s.title,
                meta = "${s.category} • ${stars(s.rating)} ${s.rating} • ${km(s.distanceKm)} km",
                badgeText = if (s.verified) tr(S.VERIFIED) else null
            ) { showService(s) })
        }
    }

    private fun loadEvents(query: String) {
        lifecycleScope.launch {
            callApi { api -> api.events(query, prefs.radius) }
                .onSuccess {
                    events = it
                    eventsOffline = false
                    if (query.isBlank()) prefs.cachedEventsJson = gson.toJson(it)
                }
                .onFailure {
                    val cached = readCache(prefs.cachedEventsJson, Array<EventDto>::class.java).ifEmpty { demoEvents() }
                    events = cached.filter { e -> matches(query, e.title, e.category, e.description) }
                    eventsOffline = true
                }
            showEvents()
        }
    }

    private fun showEvents() {
        setPage(tr(S.COMMUNITY_EVENTS), tab = Tab.EVENTS)
        if (eventsOffline) {
            root.addView(chip(tr(S.OFFLINE_BADGE), color(R.color.surface_alt), color(R.color.ink_600)),
                LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12) })
        }
        if (events.isEmpty()) root.addView(emptyState(tr(S.NO_EVENTS)))
        events.forEach { e ->
            root.addView(cardRow(title = e.title, meta = "${e.category} • ${e.dateTime}\n${e.venue}", badgeIsAccent = true) { showEvent(e) })
        }
        button(tr(S.BACK), BtnStyle.TEXT) { showDiscover() }
    }

    private fun showService(service: ServiceDto) {
        setPage(service.title, service.category, back = { showDiscover() })
        val statusChip = chip(
            if (service.verified) tr(S.VERIFIED_LISTING) else tr(S.PENDING_VERIFICATION),
            color(if (service.verified) R.color.teal_100 else R.color.surface_alt),
            color(if (service.verified) R.color.teal_500 else R.color.ink_600)
        )
        root.addView(statusChip, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12) })
        bodyText("${stars(service.rating)}  ${service.rating} • ${tr(S.KM_AWAY, km(service.distanceKm))}", bold = true)
        bodyText(service.description, muted = true)
        button(tr(S.CALL_PROVIDER)) {
            if (service.phone.isNotBlank()) safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(service.phone)}")))
            else toast(tr(S.NO_PHONE))
        }
        button(tr(S.DIRECTIONS), BtnStyle.SECONDARY) { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(service.title)}"))) }
        button(tr(S.SAVE_OFFLINE), BtnStyle.SECONDARY) { saveService(service) }
        button(tr(S.WRITE_REVIEW), BtnStyle.SECONDARY) { showReview(service) }
        button(tr(S.VIEW_REVIEWS), BtnStyle.TEXT) { showReviews(service) }
        button(tr(S.BACK), BtnStyle.TEXT) { showDiscover() }
    }

    private fun saveService(s: ServiceDto) {
        val database = requireDb() ?: return
        lifecycleScope.launch {
            try {
                database.savedDao().insert(SavedItem(s.id, s.title, "service", s.category, "${s.description}\n${s.rating}★ • ${km(s.distanceKm)} km"))
                toast(tr(S.SAVED_OK))
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to save service offline", t)
                toast(tr(S.SAVE_FAIL))
            }
        }
    }

    private fun showSaved() {
        setPage(tr(S.SAVED_OFFLINE), tab = Tab.SAVED)
        val listPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listPanel)
        button(tr(S.BACK), BtnStyle.TEXT) { showDiscover() }
        val database = requireDb()
        if (database == null) {
            listPanel.addView(emptyState(tr(S.NO_STORAGE)))
            return
        }
        lifecycleScope.launch {
            try {
                // Flow: the list updates itself the moment an item is saved or removed.
                database.savedDao().observeAll().collect { items ->
                    listPanel.removeAllViews()
                    if (items.isEmpty()) listPanel.addView(emptyState(tr(S.NO_SAVED)))
                    items.forEach { item ->
                        listPanel.addView(cardRow(
                            title = item.title,
                            meta = "${typeLabel(item.type)} • ${item.category}\n${item.details}"
                        ) { showSavedDetail(item) })
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load saved items", t)
                listPanel.addView(emptyState(tr(S.SAVE_FAIL)))
            }
        }
    }

    /** A saved item's details, read entirely from Room with no network call. */
    private fun showSavedDetail(item: SavedItem) {
        setPage(item.title, item.category, back = { showSaved() })
        root.addView(chip(tr(S.OFFLINE_COPY), color(R.color.surface_alt), color(R.color.ink_600)),
            LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12) })
        bodyText(typeLabel(item.type), bold = true)
        bodyText(item.details, muted = true)
        bodyText(tr(S.OFFLINE_NOTE), size = 12.5f, muted = true)
        button(tr(S.REMOVE_SAVED), BtnStyle.DANGER) {
            val database = requireDb() ?: return@button
            lifecycleScope.launch {
                try {
                    database.savedDao().delete(item.remoteId)
                    toast(tr(S.REMOVED))
                    showSaved()
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to remove saved item", t)
                    toast(tr(S.SAVE_FAIL))
                }
            }
        }
        button(tr(S.BACK), BtnStyle.TEXT) { showSaved() }
    }

    private fun showReview(service: ServiceDto) {
        setPage(tr(S.REVIEW_TITLE, service.title), back = { showService(service) })
        if (SessionStore.token == "demo") {
            bodyText(tr(S.SSO_LIMITED), size = 13.5f).setTextColor(color(R.color.orange_700))
            button(tr(S.RETRY_CONNECTION), BtnStyle.SECONDARY) { retryServerConnection() }
        } else if (!isRestSignedIn()) {
            bodyText(tr(S.NEEDS_SIGNIN_WARNING), size = 13.5f).setTextColor(color(R.color.error))
        }
        val rating = RatingBar(this).apply { numStars = 5; stepSize = 1f; this.rating = 5f }
        root.addView(rating, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12) })
        val text = field(tr(S.HINT_REVIEW), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        button(tr(S.SUBMIT_REVIEW)) {
            val body = text.text.toString().trim()
            if (body.isBlank()) { toast(tr(S.REVIEW_EMPTY)); return@button }
            val req = ReviewRequest(rating.rating.roundToInt().coerceIn(1, 5), body.take(500))
            lifecycleScope.launch {
                val result: Result<ReviewDto> =
                    if (isRestSignedIn()) callApi { api -> api.addReview(service.id, req) }
                    else Result.failure(IllegalStateException("Not signed in"))
                if (result.isSuccess) {
                    toast(tr(S.REVIEW_SUBMITTED))
                } else if (outcomeOf(result) == Outcome.DROP) {
                    toast(tr(S.ERR_SERVER, (result.exceptionOrNull() as? HttpException)?.code() ?: 400))
                } else {
                    queueReview(service.id, req, result.exceptionOrNull())
                }
                showService(service)
            }
        }
        button(tr(S.BACK), BtnStyle.TEXT) { showService(service) }
    }

    private suspend fun queueReview(serviceId: String, req: ReviewRequest, cause: Throwable?) {
        val database = db
        if (database == null) { toast(tr(S.REVIEW_NOT_SAVED)); return }
        try {
            database.pendingReviewDao().insert(PendingReview(serviceId = serviceId, rating = req.rating, text = req.text))
            toast(if (cause is IOException) tr(S.REVIEW_QUEUED) else tr(S.SYNC_NEED_SIGNIN))
            updateQueueNote()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to queue offline review", t)
            toast(tr(S.REVIEW_NOT_SAVED))
        }
    }

    private fun showReviews(service: ServiceDto) {
        setPage(tr(S.REVIEWS), service.title, back = { showService(service) })
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(panel)
        button(tr(S.BACK), BtnStyle.TEXT) { showService(service) }
        lifecycleScope.launch {
            val reviews = callApi { api -> api.reviews(service.id) }.getOrDefault(emptyList())
            if (reviews.isEmpty()) panel.addView(emptyState(tr(S.NO_REVIEWS)))
            reviews.forEach { r -> panel.addView(cardRow(title = "${stars(r.rating.toDouble())}  ${r.author}", meta = r.text) { }) }
        }
    }

    private fun showEvent(event: EventDto) {
        setPage(event.title, event.category, back = { showEvents() })
        bodyText("${event.dateTime}\n${event.venue}", bold = true)
        bodyText(event.description, muted = true)
        button(tr(S.RSVP)) { rsvpToEvent(event) }
        button(tr(S.OPEN_MAP), BtnStyle.SECONDARY) { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(event.venue)}"))) }
        button(tr(S.SAVE_EVENT), BtnStyle.SECONDARY) {
            val database = requireDb() ?: return@button
            lifecycleScope.launch {
                try {
                    database.savedDao().insert(SavedItem(event.id, event.title, "event", event.category, "${event.dateTime}\n${event.venue}"))
                    toast(tr(S.EVENT_SAVED))
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to save event offline", t)
                    toast(tr(S.SAVE_FAIL))
                }
            }
        }
        button(tr(S.BACK), BtnStyle.TEXT) { showEvents() }
    }

    /** RSVPs persist in Room (type "rsvp") so they survive restarts and appear under Saved / Offline. */
    private fun rsvpToEvent(event: EventDto) {
        val database = requireDb() ?: return
        lifecycleScope.launch {
            try {
                database.savedDao().insert(SavedItem("rsvp-${event.id}", event.title, "rsvp", event.category, "${tr(S.RSVP_ATTENDING)} • ${event.dateTime}\n${event.venue}"))
                toast(tr(S.RSVP_SAVED, event.title))
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to save RSVP", t)
                toast(tr(S.SAVE_FAIL))
            }
        }
    }

    // ---------------------------------------------------------------------
    // Add listing (works offline: queued in Room, sent automatically later)
    // ---------------------------------------------------------------------

    private fun showAddListing() {
        capturedLat = null
        capturedLng = null
        setPage(tr(S.ADD_LISTING), tr(S.ADD_LISTING_SUB), back = { showDiscover() })
        if (SessionStore.token == "demo") {
            bodyText(tr(S.SSO_LIMITED), size = 13.5f).setTextColor(color(R.color.orange_700))
            button(tr(S.RETRY_CONNECTION), BtnStyle.SECONDARY) { retryServerConnection() }
        } else if (!isRestSignedIn()) {
            bodyText(tr(S.NEEDS_SIGNIN_WARNING), size = 13.5f).setTextColor(color(R.color.error))
        }
        sectionLabel(tr(S.LISTING_TYPE))
        val typeOptions = listOf(tr(S.SERVICE), tr(S.EVENT))
        val type = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, typeOptions) }
        root.addView(type, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(14) })
        val title = field(tr(S.HINT_TITLE), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val category = field(tr(S.HINT_CATEGORY), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val description = field(tr(S.HINT_DESC), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val phone = field(tr(S.HINT_PHONE))
        button(tr(S.USE_LOCATION), BtnStyle.SECONDARY) { requestLocation() }
        capturedLocationText = bodyText(tr(S.LOCATION_NONE), size = 12.5f, muted = true)
        button(tr(S.SUBMIT_APPROVAL)) {
            val isService = type.selectedItemPosition == 0
            val t = title.text.toString().trim()
            val c = category.text.toString().trim()
            val phoneText = phone.text.toString().trim()
            when {
                t.isBlank() || c.isBlank() -> toast(tr(S.TITLE_CAT_REQUIRED))
                isService && !Validators.isValidPhoneOrBlank(phoneText) -> toast(tr(S.HINT_PHONE))
                else -> submitListing(CreateListingRequest(
                    t, c, description.text.toString().trim(), phoneText,
                    if (isService) "Service" else "Event", capturedLat, capturedLng
                ))
            }
        }
        button(tr(S.BACK), BtnStyle.TEXT) { showDiscover() }
    }

    private fun submitListing(request: CreateListingRequest) {
        lifecycleScope.launch {
            val result: Result<*> =
                if (!isRestSignedIn()) Result.failure<Any>(IllegalStateException("Not signed in"))
                else if (request.type == "Service") callApi { api -> api.addService(request) }
                else callApi { api -> api.addEvent(request) }
            when {
                result.isSuccess -> { toast(tr(S.SUBMITTED)); showDiscover() }
                outcomeOf(result) == Outcome.DROP ->
                    toast(tr(S.ERR_SERVER, (result.exceptionOrNull() as? HttpException)?.code() ?: 400))
                else -> {
                    val database = requireDb() ?: return@launch
                    try {
                        database.pendingListingDao().insert(PendingListing(
                            type = request.type, title = request.title, category = request.category,
                            description = request.description, phone = request.phone,
                            latitude = request.latitude, longitude = request.longitude
                        ))
                        toast(when {
                            SessionStore.token == "demo" -> tr(S.SSO_LIMITED)
                            result.exceptionOrNull() is IOException -> tr(S.LISTING_QUEUED)
                            else -> tr(S.SYNC_NEED_SIGNIN)
                        })
                        showDiscover()
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to queue offline listing", t)
                        toast(tr(S.REVIEW_NOT_SAVED))
                    }
                }
            }
        }
    }

    private fun showPendingApprovals() {
        setPage(tr(S.PENDING_APPROVALS), tr(S.PENDING_SUB), back = { showDiscover() })
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(panel)
        button(tr(S.BACK), BtnStyle.TEXT) { showDiscover() }
        lifecycleScope.launch {
            callApi { api -> api.pending() }
                .onSuccess { items ->
                    panel.removeAllViews()
                    if (items.isEmpty()) panel.addView(emptyState(tr(S.NO_PENDING)))
                    items.forEach { item ->
                        val title = (item.title as String?).orEmpty().ifBlank { item.id }
                        panel.addView(cardRow(
                            title = "${typeLabel(item.type)} • $title",
                            meta = tr(S.STATUS, item.status),
                            badgeText = tr(S.APPROVE)
                        ) {
                            lifecycleScope.launch {
                                callApi { api -> api.approve(item.id) }
                                    .onSuccess { toast(tr(S.APPROVED)); showPendingApprovals() }
                                    .onFailure { e -> toast(tr(S.APPROVE_FAIL, e.message ?: "unknown error")) }
                            }
                        })
                    }
                }
                .onFailure { e ->
                    panel.addView(emptyState(
                        if (e is HttpException && e.code() == 401) tr(S.SYNC_NEED_SIGNIN) else tr(S.PENDING_LOAD_FAIL, e.message ?: "unknown error")
                    ))
                }
        }
    }

    // ---------------------------------------------------------------------
    // Profile & settings
    // ---------------------------------------------------------------------

    /** Groups related controls in one rounded card; call endCard(outer) when done. */
    private fun beginCard(): LinearLayout {
        val outer = root
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.surface), radiusDp = 20, strokeColor = color(R.color.divider), strokeDp = 1)
            setPadding(dp(16), dp(6), dp(16), dp(12))
        }
        outer.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root = card
        return outer
    }

    private fun endCard(outer: LinearLayout) { root = outer }

    private fun showProfile() {
        setPage(tr(S.PROFILE_SETTINGS), tab = Tab.PROFILE)
        val headCard = beginCard()
        val headRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(4)) }
        headRow.addView(TextView(this).apply {
            text = prefs.name.trim().take(1).uppercase()
            textSize = 26f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(color(R.color.teal_700), radiusDp = 32)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(64), dp(64)))
        val headText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headText.addView(TextView(this).apply { text = prefs.name; textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(color(R.color.ink_900)) })
        headText.addView(TextView(this).apply { text = prefs.email ?: ""; textSize = 14f; setTextColor(color(R.color.ink_600)); setPadding(0, 0, 0, dp(6)) })
        headText.addView(chip(if (prefs.name != "Community Member") tr(S.LOCAL_HERO) else tr(S.NEW_MEMBER), color(R.color.orange_500), Color.WHITE))
        headRow.addView(headText, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
        root.addView(headRow)
        endCard(headCard)

        val prefsCard = beginCard()
        sectionLabel(tr(S.LANGUAGE))
        val lang = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Strings.languages)
            setSelection(Strings.languages.indexOf(prefs.language).coerceAtLeast(0))
        }
        root.addView(lang, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(14) })

        val radiusLabel = sectionLabel(tr(S.SEARCH_RADIUS_KM, prefs.radius))
        val radius = SeekBar(this).apply {
            max = 49
            progress = (prefs.radius - 1).coerceIn(0, 49)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    radiusLabel.text = tr(S.SEARCH_RADIUS_KM, value + 1).uppercase()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        root.addView(radius, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(14) })

        val notifications = MaterialSwitch(this).apply {
            text = tr(S.NOTIFICATIONS); isChecked = prefs.notifications; textSize = 15f
            setTextColor(color(R.color.ink_900))
        }
        root.addView(notifications, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val dark = MaterialSwitch(this).apply {
            text = tr(S.DARK_MODE); isChecked = prefs.darkMode; textSize = 15f
            setTextColor(color(R.color.ink_900))
        }
        root.addView(dark, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        endCard(prefsCard)
        root.addView(View(this), LinearLayout.LayoutParams(-1, dp(14)))

        button(tr(S.SAVE_SETTINGS)) {
            val newLanguage = lang.selectedItem.toString()
            val newRadius = Validators.clampRadius(radius.progress + 1)
            val darkChanged = prefs.darkMode != dark.isChecked
            prefs.language = newLanguage
            prefs.radius = newRadius
            prefs.notifications = notifications.isChecked
            prefs.darkMode = dark.isChecked
            toast(tr(S.SETTINGS_SAVED))

            lifecycleScope.launch {
                if (prefs.notifications) PushManager.enable(applicationContext, prefs) else PushManager.disable()
                if (isRestSignedIn()) {
                    callApi { api -> api.updateProfile(ProfileUpdate(language = newLanguage, notifications = prefs.notifications, radius = newRadius)) }
                }
            }
            if (prefs.notifications) requestNotificationPermissionIfNeeded()
            if (darkChanged) {
                // Re-themes the whole app; the activity is recreated and returns to Discover.
                AppCompatDelegate.setDefaultNightMode(if (prefs.darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
            } else showProfile() // redraw immediately in the newly chosen language
        }
        button(tr(S.TEST_PUSH), BtnStyle.SECONDARY) {
            if (!PushManager.show(this, tr(S.NOTIF_TEST_TITLE), tr(S.NOTIF_TEST_BODY))) {
                toast(tr(S.NOTIF_DENIED))
                requestNotificationPermissionIfNeeded()
            }
        }
        button(tr(S.SYNC_NOW), BtnStyle.SECONDARY) { syncAll(silent = false) }
        button(tr(S.SIGN_OUT), BtnStyle.DANGER) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(tr(S.SIGN_OUT_TITLE))
                .setMessage(tr(S.SIGN_OUT_BODY))
                .setPositiveButton(tr(S.SIGN_OUT)) { _, _ ->
                    SessionStore.token = null
                    prefs.clearSession()
                    FirebaseAuthManager.signOut()
                    showLogin()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        if (BuildConfig.DEBUG) addServerAddressControls()
        button(tr(S.BACK), BtnStyle.TEXT) { showDiscover() }
    }

    // ---------------------------------------------------------------------
    // Offline sync, connectivity, push
    // ---------------------------------------------------------------------

    /** Counts what is waiting in the offline queue and shows it on the Discover screen. */
    private fun updateQueueNote() {
        val database = db ?: return
        lifecycleScope.launch {
            try {
                val count = database.pendingReviewDao().count() + database.pendingListingDao().count()
                queueNote?.let {
                    it.text = tr(S.QUEUED_COUNT, count)
                    it.visibility = if (count > 0) View.VISIBLE else View.GONE
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not count queued actions", t)
            }
        }
    }

    /**
     * Sends everything queued while offline (reviews and listings) to the REST API.
     * Called manually, when the network returns, and after sign-in. A failed item stays
     * queued (network / expired session) unless the server rejects it as invalid.
     */
    private fun syncAll(silent: Boolean) {
        val database = db
        if (database == null) { if (!silent) toast(tr(S.NO_STORAGE)); return }
        if (!isRestSignedIn()) { if (!silent) toast(tr(S.SYNC_NEED_SIGNIN)); return }
        if (syncInFlight) return
        syncInFlight = true
        lifecycleScope.launch {
            try {
                var synced = 0
                var total = 0
                var stop = false
                for (review in database.pendingReviewDao().all()) {
                    if (stop) break
                    total++
                    val result = callApi { api -> api.addReview(review.serviceId, ReviewRequest(review.rating, review.text)) }
                    when (outcomeOf(result)) {
                        Outcome.OK -> { database.pendingReviewDao().delete(review.localId); synced++ }
                        Outcome.DROP -> database.pendingReviewDao().delete(review.localId)
                        Outcome.RETRY_LATER -> stop = true
                    }
                }
                for (listing in database.pendingListingDao().all()) {
                    if (stop) break
                    total++
                    val request = CreateListingRequest(listing.title, listing.category, listing.description, listing.phone, listing.type, listing.latitude, listing.longitude)
                    val result: Result<*> =
                        if (listing.type == "Service") callApi { api -> api.addService(request) } else callApi { api -> api.addEvent(request) }
                    when (outcomeOf(result)) {
                        Outcome.OK -> { database.pendingListingDao().delete(listing.localId); synced++ }
                        Outcome.DROP -> database.pendingListingDao().delete(listing.localId)
                        Outcome.RETRY_LATER -> stop = true
                    }
                }
                if (synced > 0) toast(tr(S.SYNC_DONE, synced))
                else if (!silent) toast(if (total == 0) tr(S.SYNC_NOTHING) else tr(S.ERR_NETWORK))
                updateQueueNote()
                PushManager.registerWithServer(prefs)
            } catch (t: Throwable) {
                Log.e(TAG, "Offline sync failed", t)
            } finally {
                syncInFlight = false
            }
        }
    }

    /** Watches connectivity so queued actions sync automatically the moment the phone is back online. */
    private fun registerConnectivityWatcher() {
        try {
            val manager = getSystemService(ConnectivityManager::class.java) ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread {
                        if (wasOffline) { wasOffline = false; toast(tr(S.BACK_ONLINE)) }
                        if (!isFinishing) syncAll(silent = true)
                    }
                }
                override fun onLost(network: Network) { wasOffline = true }
            }
            manager.registerDefaultNetworkCallback(callback)
            connectivityCallback = callback
        } catch (t: Throwable) {
            Log.e(TAG, "Could not watch connectivity", t)
        }
    }

    /** After sign-in: ask for notification permission, enable FCM push and flush the offline queue. */
    private fun startBackgroundServices() {
        if (prefs.notifications) requestNotificationPermissionIfNeeded()
        lifecycleScope.launch {
            if (prefs.notifications) PushManager.enable(applicationContext, prefs)
        }
        syncAll(silent = true)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !PushManager.canPost(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---------------------------------------------------------------------
    // Animated splash
    // ---------------------------------------------------------------------

    /**
     * Logo pops in with an overshoot, then pulses while a ripple ring radiates outwards and the
     * name and tagline slide up. Tap to skip; otherwise it continues after ~2.2 s.
     */
    private fun showSplash(next: () -> Unit) {
        var done = false
        onBack = null
        val ring = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dp(3), Color.WHITE) }
            alpha = 0f
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            contentDescription = "CommuniServe SA logo"
            scaleX = 0.2f; scaleY = 0.2f; alpha = 0f
        }
        val stack = FrameLayout(this).apply {
            addView(ring, FrameLayout.LayoutParams(dp(130), dp(130), Gravity.CENTER))
            addView(logo, FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER))
        }
        val name = TextView(this).apply {
            text = "CommuniServe SA"; textSize = 28f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; alpha = 0f; translationY = dpf(18f)
        }
        val tag = TextView(this).apply {
            text = tr(S.TAGLINE); textSize = 14f; gravity = Gravity.CENTER
            setTextColor(Color.argb(230, 255, 255, 255)); alpha = 0f; translationY = dpf(18f)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(color(R.color.teal_700))
            addView(stack, LinearLayout.LayoutParams(dp(220), dp(220)))
            addView(name, LinearLayout.LayoutParams(-2, -2))
            addView(tag, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        }
        setContentView(container)

        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900)
            .setInterpolator(OvershootInterpolator(1.8f))
            .withEndAction {
                if (done) return@withEndAction
                val pulse = ObjectAnimator.ofPropertyValuesHolder(
                    logo, PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.07f), PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.07f)
                ).apply { duration = 650; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE }
                val ripple = ObjectAnimator.ofPropertyValuesHolder(
                    ring,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.9f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.9f),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 0f)
                ).apply { duration = 1300; repeatCount = ValueAnimator.INFINITE }
                splashAnimators.add(pulse); splashAnimators.add(ripple)
                pulse.start(); ripple.start()
            }.start()
        name.animate().alpha(1f).translationY(0f).setStartDelay(450).setDuration(600).start()
        tag.animate().alpha(1f).translationY(0f).setStartDelay(750).setDuration(600).start()

        val proceed: () -> Unit = {
            if (!done) {
                done = true
                splashAnimators.forEach { it.cancel() }
                splashAnimators.clear()
                next()
            }
        }
        container.setOnClickListener { proceed() }
        lifecycleScope.launch { delay(2200); proceed() }
    }

    // ---------------------------------------------------------------------
    // Nearby map (OpenStreetMap via osmdroid - no API key needed)
    // ---------------------------------------------------------------------

    private fun disposeMap() {
        refreshMapLocation = null
        mapView?.let { try { it.onDetach() } catch (t: Throwable) { Log.w(TAG, "Map detach failed", t) } }
        mapView = null
    }

    /** Fetches the lists for the map (live from the API, else cache, else sample data). */
    private suspend fun refreshListingsForMap() {
        callApi { api -> api.services("", 50) }
            .onSuccess { services = it; prefs.cachedServicesJson = gson.toJson(it) }
            .onFailure { services = readCache(prefs.cachedServicesJson, Array<ServiceDto>::class.java).ifEmpty { demoServices() } }
        callApi { api -> api.events("", 50) }
            .onSuccess { events = it; prefs.cachedEventsJson = gson.toJson(it); eventsOffline = false }
            .onFailure { events = readCache(prefs.cachedEventsJson, Array<EventDto>::class.java).ifEmpty { demoEvents() }; eventsOffline = true }
    }

    private fun pin(tint: Int): android.graphics.drawable.Drawable? =
        ContextCompat.getDrawable(this, R.drawable.ic_map_pin)?.mutate()?.also { it.setTint(tint) }

    private fun showMap() {
        disposeMap()
        resultsPanel = null; queueNote = null; capturedLocationText = null
        onBack = { showDiscover() }

        // The tile cache lives in the app's private cache folder (no storage permission needed).
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }

        var showServicePins = true
        var showEventPins = true
        val map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 4.0
            controller.setZoom(MapMath.zoomForRadius(prefs.radius))
            val start = userPoint ?: GeoPoint(MapMath.DEFAULT_CENTER.lat, MapMath.DEFAULT_CENTER.lng)
            controller.setCenter(start)
        }
        mapView = map

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.cream_bg))
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left + dp(16), bars.top + dp(12), bars.right + dp(16), bars.bottom + dp(8))
            WindowInsetsCompat.CONSUMED
        }
        container.addView(banner(tr(S.MAP_OPEN), tr(S.MAP_SUB)))

        val toggles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val servicesChip = TextView(this)
        val eventsChip = TextView(this)
        fun styleToggle(view: TextView, label: String, on: Boolean, onColor: Int) {
            view.text = label
            view.textSize = 13.5f; view.gravity = Gravity.CENTER; view.minHeight = dp(44)
            view.setTypeface(null, Typeface.BOLD)
            view.setTextColor(if (on) Color.WHITE else color(R.color.ink_600))
            view.background = rounded(if (on) onColor else color(R.color.surface), radiusDp = 22, strokeColor = onColor, strokeDp = 1)
            view.setPadding(dp(16), dp(8), dp(16), dp(8))
            view.isClickable = true
        }
        toggles.addView(servicesChip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        toggles.addView(eventsChip)
        container.addView(toggles, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        container.addView(map, LinearLayout.LayoutParams(-1, 0, 1f))

        val detail = TextView(this).apply {
            textSize = 14f; setTextColor(color(R.color.ink_900)); visibility = View.GONE
            setPadding(0, dp(8), 0, dp(4))
        }
        val detailBtn = Button(this).apply {
            text = tr(S.MAP_DETAILS); isAllCaps = false; visibility = View.GONE
            setTextColor(Color.WHITE); background = rounded(color(R.color.teal_700), radiusDp = 12)
            minHeight = dp(44)
        }
        val note = TextView(this).apply {
            text = tr(S.MAP_NOTE); textSize = 11.5f; setTextColor(color(R.color.ink_400)); setPadding(0, dp(4), 0, dp(4))
        }
        container.addView(detail)
        container.addView(detailBtn, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        container.addView(note)

        fun redraw() {
            if (mapView !== map) return
            val origin = userPoint ?: GeoPoint(MapMath.DEFAULT_CENTER.lat, MapMath.DEFAULT_CENTER.lng)
            val originPoint = MapMath.Point(origin.latitude, origin.longitude)
            map.overlays.clear()

            // Search-radius circle from Settings.
            map.overlays.add(Polygon(map).apply {
                points = Polygon.pointsAsCircle(origin, prefs.radius * 1000.0)
                fillPaint.color = Color.argb(30, 16, 89, 78)
                outlinePaint.color = Color.argb(160, 16, 89, 78)
                outlinePaint.strokeWidth = 3f
            })

            fun select(title: String, meta: String, open: () -> Unit) {
                detail.text = "$title\n$meta"
                detail.visibility = View.VISIBLE
                detailBtn.visibility = View.VISIBLE
                detailBtn.setOnClickListener { open() }
            }

            if (showServicePins) services.filter { it.distanceKm <= prefs.radius }.forEach { s ->
                val at = MapMath.positionFor(s.id, s.latitude, s.longitude, s.distanceKm, originPoint)
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(at.lat, at.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = pin(color(R.color.teal_500))
                    title = s.title
                    snippet = "${s.category} • ${stars(s.rating)} ${s.rating}"
                    setOnMarkerClickListener(Marker.OnMarkerClickListener { marker, _ ->
                        marker.showInfoWindow()
                        select(s.title, "${s.category} • ${km(s.distanceKm)} km", open = { showService(s) })
                        true
                    })
                })
            }
            if (showEventPins) events.filter { it.distanceKm <= prefs.radius }.forEach { e ->
                val at = MapMath.positionFor(e.id, e.latitude, e.longitude, e.distanceKm, originPoint)
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(at.lat, at.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = pin(color(R.color.orange_500))
                    title = e.title
                    snippet = "${e.category} • ${e.dateTime}"
                    setOnMarkerClickListener(Marker.OnMarkerClickListener { marker, _ ->
                        marker.showInfoWindow()
                        select(e.title, "${e.dateTime} • ${e.venue}", open = { showEvent(e) })
                        true
                    })
                })
            }

            // The person's own position, drawn last so it sits on top.
            if (userPoint != null) {
                map.overlays.add(Marker(map).apply {
                    position = origin
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_user_location)
                    title = tr(S.MAP_YOU)
                })
            }
            map.invalidate()
        }

        fun refreshToggles() {
            styleToggle(servicesChip, tr(S.SERVICES), showServicePins, color(R.color.teal_500))
            styleToggle(eventsChip, tr(S.MAP_EVENTS), showEventPins, color(R.color.orange_500))
        }
        servicesChip.setOnClickListener { showServicePins = !showServicePins; refreshToggles(); redraw() }
        eventsChip.setOnClickListener { showEventPins = !showEventPins; refreshToggles(); redraw() }
        refreshToggles()

        // Locate the person, centre the map on them and redraw.
        fun locate() {
            if (!hasLocationPermission()) {
                locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return
            }
            toast(tr(S.MAP_LOCATING))
            try {
                locationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                    .addOnSuccessListener { loc ->
                        if (mapView !== map) return@addOnSuccessListener
                        if (loc != null) {
                            val here = GeoPoint(loc.latitude, loc.longitude)
                            userPoint = here
                            map.controller.animateTo(here)
                            map.controller.setZoom(MapMath.zoomForRadius(prefs.radius))
                            redraw()
                        } else toast(tr(S.MAP_NO_LOCATION))
                    }
                    .addOnFailureListener { t ->
                        Log.e(TAG, "Map location failed", t)
                        if (mapView === map) toast(tr(S.MAP_NO_LOCATION))
                    }
            } catch (t: SecurityException) {
                Log.e(TAG, "Location permission missing at call time", t)
                toast(tr(S.LOCATION_NEEDED))
            }
        }
        refreshMapLocation = { locate() }

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val recenter = Button(this).apply {
            text = tr(S.MAP_RECENTER); isAllCaps = false; minHeight = dp(48)
            setTextColor(color(R.color.teal_500))
            background = rounded(color(R.color.surface), radiusDp = 12, strokeColor = color(R.color.teal_500), strokeDp = 1)
            setOnClickListener { locate() }
        }
        val back = Button(this).apply {
            text = tr(S.BACK); isAllCaps = false; minHeight = dp(48)
            setTextColor(color(R.color.ink_600)); background = null
            setOnClickListener { showDiscover() }
        }
        buttons.addView(recenter, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        buttons.addView(back, LinearLayout.LayoutParams(0, -2, 1f))
        container.addView(buttons)

        container.addView(bottomNav(Tab.MAP), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        setContentView(container)
        redraw()
        lifecycleScope.launch {
            refreshListingsForMap()
            redraw()
        }
        locate()
    }

    // ---------------------------------------------------------------------
    // Location
    // ---------------------------------------------------------------------

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (!hasLocationPermission()) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        try {
            locationClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        capturedLat = loc.latitude
                        capturedLng = loc.longitude
                        capturedLocationText?.text = tr(S.LOCATION_OK, "%.5f".format(Locale.US, loc.latitude), "%.5f".format(Locale.US, loc.longitude))
                    } else {
                        capturedLocationText?.text = tr(S.LOCATION_FAIL)
                        toast(tr(S.LOCATION_FAIL))
                    }
                }
                .addOnFailureListener { t ->
                    Log.e(TAG, "Location request failed", t)
                    capturedLocationText?.text = tr(S.LOCATION_FAIL)
                }
        } catch (t: SecurityException) {
            Log.e(TAG, "Location permission missing at call time", t)
            toast(tr(S.LOCATION_NEEDED))
        }
    }

    // ---------------------------------------------------------------------
    // Built-in sample data, used only on the very first launch while offline
    // ---------------------------------------------------------------------

    private fun demoServices() = listOf(
        ServiceDto("s1", "Community Plumbing Services", "Plumber", "Emergency and maintenance plumbing for homes and small businesses.", "0110000001", 4.8, 2.1, true),
        ServiceDto("s2", "Reliable Electrical Repairs", "Electrician", "Residential electrical repairs and installations.", "0110000002", 4.6, 3.4, true),
        ServiceDto("s3", "Maths Tutor", "Tutor", "One-to-one tutoring for school learners.", "0110000003", 4.9, 1.8, true)
    )

    private fun demoEvents() = listOf(
        EventDto("e1", "Community Clean-up", "Community", "Join neighbours for a Saturday clean-up.", "Local Community Hall", "Saturday 09:00", 1.2),
        EventDto("e2", "Youth Coding Meetup", "Education", "A beginner-friendly coding and technology session.", "Innovation Hub", "Sunday 13:00", 4.0)
    )
}
