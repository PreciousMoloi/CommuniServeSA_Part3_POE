package za.co.communiservesa

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test
import za.co.communiservesa.network.SsoRequest

/** The API verifies the Firebase ID token, so the SSO request must carry it in the JSON body. */
class SsoRequestTest {
    @Test fun requestCarriesFirebaseIdToken() {
        val json = Gson().toJsonTree(SsoRequest("a@b.co.za", "Ann", idToken = "abc.def.ghi")).asJsonObject
        assertEquals("abc.def.ghi", json.get("idToken").asString)
        assertEquals("google", json.get("provider").asString)
    }
}
