package com.astratouch.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.astratouch.app.data.local.dao.AppDao
import com.astratouch.app.data.local.dao.BubbleConfigDao
import com.astratouch.app.data.local.dao.ClipboardDao
import com.astratouch.app.data.local.dao.GestureDao
import com.astratouch.app.data.local.dao.MacroDao
import com.astratouch.app.data.local.dao.NoteDao
import com.astratouch.app.data.local.dao.ProfileDao
import com.astratouch.app.data.local.dao.RadialConfigDao
import com.astratouch.app.data.local.dao.RecentActionDao
import com.astratouch.app.data.local.dao.ThemeDao
import com.astratouch.app.data.local.dao.TimerDao
import com.astratouch.app.data.local.entity.BubbleConfigEntity
import com.astratouch.app.data.local.entity.ClipboardRecordEntity
import com.astratouch.app.data.local.entity.FavoriteAppEntity
import com.astratouch.app.data.local.entity.GestureMappingEntity
import com.astratouch.app.data.local.entity.MacroEntity
import com.astratouch.app.data.local.entity.MacroExecutionEntity
import com.astratouch.app.data.local.entity.MacroStepEntity
import com.astratouch.app.data.local.entity.NoteEntity
import com.astratouch.app.data.local.entity.ProfileEntity
import com.astratouch.app.data.local.entity.RadialConfigEntity
import com.astratouch.app.data.local.entity.RadialItemEntity
import com.astratouch.app.data.local.entity.RecentActionEntity
import com.astratouch.app.data.local.entity.ThemeConfigEntity
import com.astratouch.app.data.local.entity.TimerEntity

/**
 * AstraTouch local database.
 *
 * The schema is exported to `app/schemas` on every build so migrations are
 * reviewable in pull requests (see docs/DATABASE.md).
 */
@Database(
    entities = [
        ProfileEntity::class,
        BubbleConfigEntity::class,
        RadialConfigEntity::class,
        RadialItemEntity::class,
        GestureMappingEntity::class,
        MacroEntity::class,
        MacroStepEntity::class,
        MacroExecutionEntity::class,
        ThemeConfigEntity::class,
        FavoriteAppEntity::class,
        NoteEntity::class,
        ClipboardRecordEntity::class,
        RecentActionEntity::class,
        TimerEntity::class,
    ],
    version = AstraDatabase.VERSION,
    exportSchema = true,
)
abstract class AstraDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun bubbleConfigDao(): BubbleConfigDao
    abstract fun radialConfigDao(): RadialConfigDao
    abstract fun gestureDao(): GestureDao
    abstract fun macroDao(): MacroDao
    abstract fun themeDao(): ThemeDao
    abstract fun appDao(): AppDao
    abstract fun noteDao(): NoteDao
    abstract fun clipboardDao(): ClipboardDao
    abstract fun recentActionDao(): RecentActionDao
    abstract fun timerDao(): TimerDao

    companion object {
        const val VERSION = 2
        const val NAME = "astratouch.db"

        /**
         * v1 -> v2: added the local timer store, recent-action frequency column
         * and the "group" concept for launcher apps. Written defensively so an
         * interrupted upgrade cannot leave the database unusable.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `timers` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`duration_ms` INTEGER NOT NULL, " +
                        "`remaining_ms` INTEGER NOT NULL, " +
                        "`is_running` INTEGER NOT NULL, " +
                        "`ends_at_epoch_ms` INTEGER NOT NULL, " +
                        "`ringtone_requested` INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE `recent_actions` ADD COLUMN `use_count` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `favorite_apps` ADD COLUMN `group_name` TEXT NOT NULL DEFAULT ''")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context, name: String = NAME): AstraDatabase =
            Room.databaseBuilder(context.applicationContext, AstraDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

        /**
         * Builds an in-memory database for tests. Instrumented/Robolectric tests
         * use this to verify DAO behaviour deterministically.
         */
        fun buildInMemory(context: Context): AstraDatabase =
            Room.inMemoryDatabaseBuilder(context, AstraDatabase::class.java)
                .allowMainThreadQueries()
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
