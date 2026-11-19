package com.astratouch.app

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.astratouch.app.data.local.AstraDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that `MIGRATION_1_2` really upgrades a v1 database and leaves every
 * pre-existing row intact.
 *
 * This test is deliberately **self-contained**: it builds the historical v1
 * schema with raw SQL instead of relying on `MigrationTestHelper`, which would
 * need exported schema JSON assets for version 1. Hand-writing v1 keeps the test
 * valid forever — the historical shape never changes — and keeps it running in
 * CI without a generated-schema step.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-test.db"

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        if (::helper.isInitialized) helper.close()
        context.deleteDatabase(dbName)
    }

    private fun openV1(): SupportSQLiteDatabase {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `profiles` (" +
                                "`id` TEXT NOT NULL PRIMARY KEY, `type` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                                "`description` TEXT NOT NULL, `theme_id` TEXT NOT NULL, " +
                                "`bubble_config_id` TEXT NOT NULL, `radial_config_id` TEXT NOT NULL, " +
                                "`is_active` INTEGER NOT NULL, `auto_switch_package` TEXT, " +
                                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
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
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        return helper.writableDatabase
    }

    @Test
    fun migrate1To2_preservesRowsAndAddsNewStructures() {
        val v1 = openV1()
        v1.execSQL(
            "INSERT INTO profiles (id, type, name, description, theme_id, bubble_config_id, " +
                "radial_config_id, is_active, auto_switch_package, created_at, updated_at, sort_order) " +
                "VALUES ('p1', 'DEFAULT', 'Default', 'd', 't1', 'b1', 'r1', 1, NULL, 10, 10, 0)",
        )
        v1.execSQL("INSERT INTO recent_actions (action_id, used_at) VALUES ('nav.home', 1000)")
        v1.execSQL(
            "INSERT INTO favorite_apps (package_name, label, is_favorite, category, last_used_at, launch_count) " +
                "VALUES ('com.example', 'Example', 1, 'tools', 42, 3)",
        )
        helper.close()

        // Now open the same file through an upgraded helper and apply the real migration.
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(AstraDatabase.VERSION) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        if (oldVersion < 2) AstraDatabase.MIGRATION_1_2.migrate(db)
                    }
                })
                .build(),
        )
        val db = helper.writableDatabase

        // Existing rows survive.
        db.query("SELECT id, name, is_active FROM profiles").use { c ->
            assertTrue("profiles row must survive the migration", c.moveToFirst())
            assertEquals("p1", c.getString(0))
            assertEquals("Default", c.getString(1))
            assertEquals(1, c.getInt(2))
            assertEquals(1, c.count)
        }

        // New column on an existing table gets the documented default.
        db.query("SELECT action_id, used_at, use_count FROM recent_actions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("nav.home", c.getString(0))
            assertEquals(1000L, c.getLong(1))
            assertEquals(1, c.getInt(2))
        }

        db.query("SELECT package_name, group_name FROM favorite_apps").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("com.example", c.getString(0))
            assertEquals("", c.getString(1))
        }

        // New table exists and is writable with the exact current shape.
        db.execSQL(
            "INSERT INTO timers (label, duration_ms, remaining_ms, is_running, ends_at_epoch_ms, " +
                "ringtone_requested) VALUES ('tea', 180000, 180000, 0, 0, 1)",
        )
        db.query("SELECT label, duration_ms FROM timers").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("tea", c.getString(0))
            assertEquals(180000L, c.getLong(1))
        }

        assertEquals(AstraDatabase.VERSION, db.version)
    }

    @Test
    fun migration1To2_isIdempotent() {
        val db = openV1()
        AstraDatabase.MIGRATION_1_2.migrate(db)
        // Re-running must not throw ("duplicate column name") because every
        // statement is guarded: CREATE TABLE IF NOT EXISTS + a PRAGMA column check
        // before each ALTER.
        AstraDatabase.MIGRATION_1_2.migrate(db)
        assertTrue(
            "timers must exist after the first migration",
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='timers'").use { it.moveToFirst() },
        )
    }
}
