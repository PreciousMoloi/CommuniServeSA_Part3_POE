package za.co.communiservesa.util

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure map maths (no Android classes) so it can be unit-tested.
 *
 * Listings that were created with "Use My Location" carry real coordinates. Older / sample
 * listings only know their distance from the user, so they are pinned at that distance in a
 * stable direction (derived from the id) - the pin never jumps around between visits.
 */
object MapMath {
    data class Point(val lat: Double, val lng: Double)

    val DEFAULT_CENTER = Point(-26.2041, 28.0473) // Johannesburg, used only when GPS is unavailable

    fun hasRealCoords(lat: Double?, lng: Double?): Boolean =
        lat != null && lng != null && abs(lat) <= 90 && abs(lng) <= 180 && (lat != 0.0 || lng != 0.0)

    fun positionFor(id: String, lat: Double?, lng: Double?, distanceKm: Double, origin: Point): Point {
        if (hasRealCoords(lat, lng)) return Point(lat!!, lng!!)
        val bearing = Math.toRadians((abs(id.hashCode().toLong()) % 360).toDouble())
        val d = max(distanceKm, 0.3)
        val dLat = d * cos(bearing) / 111.0
        val dLng = d * sin(bearing) / (111.0 * max(cos(Math.toRadians(origin.lat)), 0.01))
        return Point(origin.lat + dLat, origin.lng + dLng)
    }

    fun zoomForRadius(km: Int): Double = when {
        km <= 2 -> 15.0
        km <= 5 -> 14.0
        km <= 10 -> 13.0
        km <= 25 -> 11.5
        else -> 10.0
    }

    /** Great-circle distance in km (haversine). */
    fun distanceKm(a: Point, b: Point): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}
