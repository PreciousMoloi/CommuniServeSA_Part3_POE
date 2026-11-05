package za.co.communiservesa.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Room DAO for listings queued offline - see PendingListing. */
@Dao
interface PendingListingDao {
    @Insert
    suspend fun insert(listing: PendingListing)

    @Query("SELECT * FROM pending_listings ORDER BY localId ASC")
    suspend fun all(): List<PendingListing>

    @Query("DELETE FROM pending_listings WHERE localId = :localId")
    suspend fun delete(localId: Long)

    @Query("SELECT COUNT(*) FROM pending_listings")
    suspend fun count(): Int
}
