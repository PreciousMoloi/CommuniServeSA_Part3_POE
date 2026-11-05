package za.co.communiservesa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.communiservesa.util.MapMath

class MapMathTest {
    private val home = MapMath.Point(-26.2041, 28.0473)

    @Test fun realCoordinatesAreUsedAsIs() {
        val p = MapMath.positionFor("s9", -25.75, 28.19, 3.0, home)
        assertEquals(-25.75, p.lat, 0.0)
        assertEquals(28.19, p.lng, 0.0)
    }

    @Test fun distanceBasedPinsLandAtTheStatedDistance() {
        for (id in listOf("s1", "s2", "s3", "e1", "e2")) {
            val p = MapMath.positionFor(id, null, null, 4.0, home)
            assertEquals("pin for $id", 4.0, MapMath.distanceKm(home, p), 0.15)
        }
    }

    @Test fun pinsAreStableBetweenCalls() {
        assertEquals(MapMath.positionFor("s1", null, null, 2.1, home), MapMath.positionFor("s1", null, null, 2.1, home))
    }

    @Test fun zeroOrMissingCoordinatesAreNotTreatedAsReal() {
        assertFalse(MapMath.hasRealCoords(null, null))
        assertFalse(MapMath.hasRealCoords(0.0, 0.0))
        assertFalse(MapMath.hasRealCoords(120.0, 10.0))
        assertTrue(MapMath.hasRealCoords(-26.0, 28.0))
    }

    @Test fun biggerRadiusMeansMoreZoomedOut() {
        assertTrue(MapMath.zoomForRadius(1) > MapMath.zoomForRadius(10))
        assertTrue(MapMath.zoomForRadius(10) > MapMath.zoomForRadius(50))
    }
}
