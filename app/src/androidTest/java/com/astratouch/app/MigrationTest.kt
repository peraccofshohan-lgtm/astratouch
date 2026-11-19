package com.astratouch.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.astratouch.app.data.local.AstraDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * Verifies that the 1 → 2 migration really runs against a v1 schema and leaves a
 * database that matches the current entity definitions.
 *
 * The v1 schema is written by hand here (it is the historical shape) so this test
 * stays valid even as the exported schema files evolve.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AstraDatabase::class.java,
    )

    private fun createV1(name: String) {
        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `profiles` (" +
                    "`id` TEXT NOT NULL PRIMARY KEY, `type` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, `theme_id` TEXT NOT NULL, `bubble_config_id` TEXT NOT NULL, " +
                    "`radial_config_id` TEXT NOT NULL, `is_active` INTEGER NOT NULL, " +
                    "`auto_switch_package` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                    "`sort_order` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recent_actions` (" +
                    "`action_id` TEXT NOT NULL PRIMARY KEY, `used_at` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `favorite_apps` (" +
                    "`package_name` TEXT NOT NULL PRIMARY KEY, `label` TEXT NOT NULL, " +
                    "`is_favorite` INTEGER NOT NULL, `category` TEXT NOT NULL, " +
                    "`last_used_at` INTEGER NOT NULL, `launch_count` INTEGER NOT NULL)",
            )
            db.execSQL(
                "INSERT INTO recent_actions (action_id, used_at) VALUES ('nav.home', 1000)",
            )
            db.execSQL(
                "INSERT INTO favorite_apps (package_name, label, is_favorite, category, last_used_at, launch_count) " +
                    "VALUES ('com.example', 'Example', 1, 'tools', 42, 3)",
            )
        }
    }

    @Test
    fun `migrate 1 to 2 preserves data and adds new columns`() {
        val name = "migration-test.db"
        createV1(name)

        val db = helper.runMigrationsAndValidate(name, 2, true, AstraDatabase.MIGRATION_1_2)

        db.query("SELECT action_id, used_at, use_count FROM recent_actions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("nav.home", cursor.getString(0))
            assertEquals(1000L, cursor.getLong(1))
            assertEquals(1, cursor.getInt(2))
        }
        db.query("SELECT group_name FROM favorite_apps").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='timers'").use { cursor ->
            assertTrue("timers table should exist after migration", cursor.moveToFirst())
        }
    }
}
