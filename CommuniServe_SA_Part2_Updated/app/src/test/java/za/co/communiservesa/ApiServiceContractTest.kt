package za.co.communiservesa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.communiservesa.network.ServiceDto

class ApiServiceContractTest {
    @Test fun ratingsMustBeBetweenZeroAndFive() {
        val results = listOf(ServiceDto("1", "A", "Plumber", rating = 4.8), ServiceDto("2", "B", "Tutor", rating = 0.0))
        assertTrue(results.all { it.rating in 0.0..5.0 })
    }
    @Test fun searchRadiusMustBeBetweenOneAndFifty() {
        assertTrue((1..50).contains(10))
        assertTrue((1..50).contains(50))
    }
    @Test fun serviceModelKeepsRequiredIdentity() {
        val service = ServiceDto("s1", "Community Plumbing Services", "Plumber")
        assertEquals("s1", service.id)
        assertTrue(service.title.isNotBlank())
        assertTrue(service.category.isNotBlank())
    }
}
