package za.co.communiservesa.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
/**
 * Room DAO for review drafts submitted while offline. Reviews are queued
 * here and synced to the REST API once connectivity returns (see
 * syncPendingReviews() in MainActivity.kt).
 */
@Dao
interface PendingReviewDao {
    @Insert
    suspend fun insert(review: PendingReview)

    @Query("SELECT * FROM pending_reviews ORDER BY localId ASC")
    suspend fun all(): List<PendingReview>

    @Query("DELETE FROM pending_reviews WHERE localId = :localId")
    suspend fun delete(localId: Long)
}
