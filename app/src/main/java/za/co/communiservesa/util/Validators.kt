package za.co.communiservesa.util

/**
 * Pure input-validation helpers (no Android classes) so the same rules can be
 * unit-tested in GitHub Actions. They keep bad input from ever reaching the
 * network layer, which is how the app "handles invalid inputs without crashing".
 */
object Validators {
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun isValidEmail(value: String?): Boolean = !value.isNullOrBlank() && EMAIL.matches(value.trim())

    /** 8+ characters containing at least one letter and one digit. */
    fun isStrongPassword(value: String?): Boolean =
        value != null && value.length >= 8 && value.any { it.isLetter() } && value.any { it.isDigit() }

    fun isValidName(value: String?): Boolean = !value.isNullOrBlank() && value.trim().length >= 2

    /** Accepts typical South African and international phone formats; blank is allowed (optional field). */
    fun isValidPhoneOrBlank(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        val digits = value.count { it.isDigit() }
        return digits in 7..15 && value.all { it.isDigit() || it in "+-() " }
    }

    /** A server address must be http(s)://host[:port][/] - guards the "Save & Test" field. */
    fun isValidServerUrl(value: String?): Boolean =
        !value.isNullOrBlank() && Regex("^https?://[A-Za-z0-9.\\-]+(:\\d{1,5})?(/.*)?$").matches(value.trim())

    fun clampRadius(km: Int): Int = km.coerceIn(1, 50)
}
