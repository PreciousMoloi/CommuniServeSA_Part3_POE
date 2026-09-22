package za.co.communiservesa.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
/** Room DAO for offline-saved services/events (the "My Saved / Offline" screen). */

@Dao
interface SavedItemDao {
    // Returns a Flow so the Saved/Offline screen updates automatically
    // whenever an item is added, without a manual refresh call.
    @Query("SELECT * FROM saved_items ORDER BY title ASC")
    fun observeAll(): Flow<List<SavedItem>>
    // REPLACE avoids a duplicate-key crash if the same service is saved twice.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SavedItem)
}
