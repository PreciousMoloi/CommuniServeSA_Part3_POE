package za.co.communiservesa.util

/**
 * Chooses which REST API address the app should use. Kept free of Android classes so the
 * rule can be unit-tested in GitHub Actions.
 *
 * Why this exists: 10.0.2.2 is an alias that only exists inside the Android Emulator. On a
 * physical phone it can never connect, which is what produced the "server unavailable"
 * warning during Google sign-in and listing submission. A physical phone therefore always
 * uses the hosted (HTTPS) API unless the person typed a real address of their own.
 */
object ServerConfig {
    const val EMULATOR_ALIAS_HOST = "10.0.2.2"

    fun isEmulatorAddress(url: String?): Boolean =
        url != null && url.contains(EMULATOR_ALIAS_HOST)

    /**
     * @param stored      address the person saved earlier (null = never set)
     * @param emulatorUrl build-time address for the emulator (http://10.0.2.2:3000/)
     * @param hostedUrl   build-time address of the deployed API (https://...)
     * @param isEmulator  true when running inside the Android Emulator
     */
    fun resolve(stored: String?, emulatorUrl: String, hostedUrl: String, isEmulator: Boolean): String {
        val saved = stored?.trim().orEmpty()
        return when {
            // A saved emulator alias is useless on a real phone - ignore it.
            saved.isNotEmpty() && !(isEmulatorAddress(saved) && !isEmulator) -> saved
            isEmulator -> emulatorUrl
            else -> hostedUrl
        }
    }
}
