package za.co.communiservesa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.communiservesa.util.Validators

/** Input validation: bad input must be rejected before it reaches the network layer. */
class ValidatorsTest {

    @Test fun acceptsRealEmailsAndRejectsJunk() {
        assertTrue(Validators.isValidEmail("thabo@example.co.za"))
        assertTrue(Validators.isValidEmail("  nomvula@mail.com "))
        assertFalse(Validators.isValidEmail(""))
        assertFalse(Validators.isValidEmail(null))
        assertFalse(Validators.isValidEmail("no-at-sign.com"))
        assertFalse(Validators.isValidEmail("a@b"))
        assertFalse(Validators.isValidEmail("two words@mail.com"))
    }

    @Test fun passwordNeedsLengthLettersAndDigits() {
        assertTrue(Validators.isStrongPassword("Secure123"))
        assertFalse(Validators.isStrongPassword("short1"))
        assertFalse(Validators.isStrongPassword("onlyletters"))
        assertFalse(Validators.isStrongPassword("12345678"))
        assertFalse(Validators.isStrongPassword(null))
    }

    @Test fun nameMustHaveAtLeastTwoCharacters() {
        assertTrue(Validators.isValidName("Li"))
        assertFalse(Validators.isValidName(" "))
        assertFalse(Validators.isValidName("A"))
        assertFalse(Validators.isValidName(null))
    }

    @Test fun phoneIsOptionalButMustLookLikeAPhoneNumber() {
        assertTrue(Validators.isValidPhoneOrBlank(""))
        assertTrue(Validators.isValidPhoneOrBlank("011 000 0001"))
        assertTrue(Validators.isValidPhoneOrBlank("+27 82 123 4567"))
        assertFalse(Validators.isValidPhoneOrBlank("call me"))
        assertFalse(Validators.isValidPhoneOrBlank("123"))
    }

    @Test fun serverUrlMustBeHttpOrHttps() {
        assertTrue(Validators.isValidServerUrl("http://10.0.2.2:3000/"))
        assertTrue(Validators.isValidServerUrl("https://communiservesa-api.onrender.com/"))
        assertFalse(Validators.isValidServerUrl("ftp://host/"))
        assertFalse(Validators.isValidServerUrl("localhost:3000"))
        assertFalse(Validators.isValidServerUrl(""))
    }

    @Test fun radiusIsClampedToOneToFifty() {
        assertEquals(1, Validators.clampRadius(-5))
        assertEquals(10, Validators.clampRadius(10))
        assertEquals(50, Validators.clampRadius(999))
    }
}
