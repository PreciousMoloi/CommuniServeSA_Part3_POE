package za.co.communiservesa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A service or event the user has bookmarked for offline access.
 * remoteId is the id returned by the REST API (or the demo dataset),
 * so re-saving the same item simply replaces the cached copy.
 */
@Entity(tableName = "saved_items")
data class SavedItem(
    @PrimaryKey val remoteId: String,
    val title: String,
    val type: String,
    val category: String,
    val details: String
)
