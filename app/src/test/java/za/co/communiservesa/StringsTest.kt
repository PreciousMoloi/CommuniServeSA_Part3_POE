package za.co.communiservesa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.communiservesa.i18n.S
import za.co.communiservesa.i18n.Strings

/** Guards the multi-language feature: every key must exist, be non-blank and keep its format placeholders. */
class StringsTest {

    @Test fun everyKeyIsTranslatedIntoAllThreeLanguages() {
        for (key in S.values()) {
            for ((name, table) in listOf("en" to Strings.en, "af" to Strings.af, "zu" to Strings.zu)) {
                val text = table[key]
                assertTrue("$name is missing $key", text != null)
                assertFalse("$name has a blank $key", text!!.isBlank())
            }
        }
    }

    @Test fun placeholdersMatchAcrossLanguages() {
        val placeholder = Regex("%[sd]")
        for (key in S.values()) {
            val expected = placeholder.findAll(Strings.en.getValue(key)).map { it.value }.toList()
            assertEquals("Afrikaans placeholders differ for $key", expected, placeholder.findAll(Strings.af.getValue(key)).map { it.value }.toList())
            assertEquals("isiZulu placeholders differ for $key", expected, placeholder.findAll(Strings.zu.getValue(key)).map { it.value }.toList())
        }
    }

    @Test fun languageSwitchChangesTheText() {
        val en = Strings.get(Strings.ENGLISH, S.DISCOVER)
        val af = Strings.get(Strings.AFRIKAANS, S.DISCOVER)
        val zu = Strings.get(Strings.ISIZULU, S.DISCOVER)
        assertEquals("Discover", en)
        assertNotEquals(en, af)
        assertNotEquals(en, zu)
        assertNotEquals(af, zu)
    }

    @Test fun unknownLanguageFallsBackToEnglish() {
        assertEquals(Strings.get(Strings.ENGLISH, S.SIGN_IN), Strings.get("Klingon", S.SIGN_IN))
    }

    @Test fun formatArgumentsAreApplied() {
        assertEquals("Welcome back, Thabo", Strings.get(Strings.ENGLISH, S.WELCOME_BACK, "Thabo"))
        assertTrue(Strings.get(Strings.AFRIKAANS, S.SEARCH_RADIUS_KM, 25).contains("25"))
        assertTrue(Strings.get(Strings.ISIZULU, S.QUEUED_COUNT, 3).contains("3"))
    }
}
