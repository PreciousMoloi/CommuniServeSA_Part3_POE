package za.co.communiservesa.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's local Room database, used for offline storage: saved
 * services/events (SavedItem) and review drafts queued while offline
 * (PendingReview). See https://developer.android.com/training/data-storage/room
 * for the Room persistence library this is built on.
 */

@Database(
    entities = [SavedItem::class, PendingReview::class, PendingListing::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun savedDao(): SavedItemDao
    abstract fun pendingReviewDao(): PendingReviewDao
    abstract fun pendingListingDao(): PendingListingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        /**
         * Returns a single shared database instance for the whole app
         * (singleton pattern), so multiple screens don't each open their
         * own connection to the same SQLite file.
         */

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "communiservesa_database"
                )
                    // v1 -> v2 added the pending_listings table. The data in v1 was only a
                    // local cache of bookmarks, so a clean rebuild is safe and avoids a
                    // schema-mismatch crash on devices that still have the old database.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
