package za.co.communiservesa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.communiservesa.util.ServerConfig

/** The emulator-only alias must never be used on a physical phone (the cause of "server unavailable"). */
class ServerConfigTest {
    private val emu = "http://10.0.2.2:3000/"
    private val hosted = "https://communiservesa-api.onrender.com/"

    @Test fun physicalPhoneWithNothingSavedUsesHostedApi() =
        assertEquals(hosted, ServerConfig.resolve(null, emu, hosted, isEmulator = false))

    @Test fun physicalPhoneIgnoresSavedEmulatorAlias() =
        assertEquals(hosted, ServerConfig.resolve(emu, emu, hosted, isEmulator = false))

    @Test fun emulatorWithNothingSavedUsesEmulatorAlias() =
        assertEquals(emu, ServerConfig.resolve(null, emu, hosted, isEmulator = true))

    @Test fun savedLanAddressWinsOnPhone() =
        assertEquals("http://192.168.1.10:3000/", ServerConfig.resolve(" http://192.168.1.10:3000/ ", emu, hosted, isEmulator = false))

    @Test fun blankSavedValueFallsBackToDefault() =
        assertEquals(hosted, ServerConfig.resolve("   ", emu, hosted, isEmulator = false))

    @Test fun detectsEmulatorAlias() {
        assertTrue(ServerConfig.isEmulatorAddress(emu))
        assertFalse(ServerConfig.isEmulatorAddress(hosted))
        assertFalse(ServerConfig.isEmulatorAddress(null))
    }
}
