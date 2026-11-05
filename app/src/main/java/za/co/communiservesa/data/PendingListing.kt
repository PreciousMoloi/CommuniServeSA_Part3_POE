package za.co.communiservesa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A service/event listing created while offline (or before signing in).
 * Queued in Room and posted to the REST API by the sync routine as soon as
 * the device is online and signed in.
 */
@Entity(tableName = "pending_listings")
data class PendingListing(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val type: String,
    val title: String,
    val category: String,
    val description: String,
    val phone: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)
