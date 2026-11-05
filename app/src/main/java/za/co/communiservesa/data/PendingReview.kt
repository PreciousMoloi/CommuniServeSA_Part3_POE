package za.co.communiservesa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A review that was written while offline. It is queued here and
 * synchronised with the REST API once the device reconnects.
 */
@Entity(tableName = "pending_reviews")
data class PendingReview(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serviceId: String,
    val rating: Int,
    val text: String
)
