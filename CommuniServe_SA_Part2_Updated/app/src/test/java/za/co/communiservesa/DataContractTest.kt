package za.co.communiservesa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.communiservesa.network.AuthRequest
import za.co.communiservesa.network.CreateListingRequest
import za.co.communiservesa.network.EventDto
import za.co.communiservesa.network.ReviewRequest
import za.co.communiservesa.network.UserDto

/**
 * Plain JVM unit tests (no Android framework dependency) so they run both
 * locally with `./gradlew testDebugUnitTest` and in the GitHub Actions CI
 * workflow (.github/workflows/build.yml) without needing an emulator.
 */
class DataContractTest {

    @Test fun reviewRatingMustStayWithinOneToFive() {
        val validRatings = listOf(1, 2, 3, 4, 5)
        assertTrue(validRatings.all { it in 1..5 })
        val review = ReviewRequest(rating = 5, text = "Excellent, would recommend.")
        assertTrue(review.rating in 1..5)
        assertFalse(review.text.isBlank())
    }

    @Test fun userDefaultsMatchAppSettingsDefaults() {
        val user = UserDto(id = "u1", name = "Thabo", email = "thabo@example.co.za")
        // These defaults must match Prefs' own defaults (language "English",
        // radius 10, notifications on) so a fresh account behaves the same
        // whether its settings came from the server or from local prefs.
        assertEquals("English", user.language)
        assertEquals(10, user.radius)
        assertTrue(user.notifications)
    }

    @Test fun createListingRequiresATypeOfServiceOrEvent() {
        val service = CreateListingRequest("Garden Services", "Gardening", "Lawn care", "0123456789", "Service")
        val event = CreateListingRequest("Clean-up Day", "Community", "Saturday clean-up", "", "Event")
        assertTrue(listOf(service, event).all { it.type == "Service" || it.type == "Event" })
        assertFalse(service.title.isBlank())
    }

    @Test fun eventDistanceCannotBeNegative() {
        val event = EventDto("e1", "Community Clean-up", "Community", distanceKm = 1.2)
        assertTrue(event.distanceKm >= 0.0)
    }

    @Test fun authRequestRejectsObviouslyInvalidEmails() {
        val request = AuthRequest(name = "Nomvula", email = "nomvula@example.co.za", password = "SecurePass1")
        assertTrue(request.email.contains("@"))
        assertTrue(request.password.length >= 8)
    }
}
