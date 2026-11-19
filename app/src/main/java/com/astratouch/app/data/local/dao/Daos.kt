package com.astratouch.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY sort_order ASC, name ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE is_active = 1 LIMIT 1")
    suspend fun getActive(): ProfileEntity?

    @Query("SELECT * FROM profiles")
    suspend fun getAll(): List<ProfileEntity>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Upsert
    suspend fun upsertAll(profiles: List<ProfileEntity>)

    @Query("UPDATE profiles SET is_active = 0")
    suspend fun clearActive()

    @Transaction
    suspend fun setActive(id: String) {
        clearActive()
        activate(id)
    }

    @Query("UPDATE profiles SET is_active = 1 WHERE id = :id")
    suspend fun activate(id: String)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int
}

@Dao
interface BubbleConfigDao {
    @Query("SELECT * FROM bubble_configs WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<BubbleConfigEntity?>

    @Query("SELECT * FROM bubble_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BubbleConfigEntity?

    @Query("SELECT * FROM bubble_configs")
    suspend fun getAll(): List<BubbleConfigEntity>

    @Upsert
    suspend fun upsert(config: BubbleConfigEntity)

    @Query("UPDATE bubble_configs SET position_x = :x, position_y = :y, dock_side = :dockSide WHERE id = :id")
    suspend fun updatePosition(id: String, x: Int, y: Int, dockSide: String)

    @Query("DELETE FROM bubble_configs WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface RadialConfigDao {
    @Query("SELECT * FROM radial_configs WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<RadialConfigEntity?>

    @Query("SELECT * FROM radial_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RadialConfigEntity?

    @Query("SELECT * FROM radial_configs")
    suspend fun getAll(): List<RadialConfigEntity>

    @Upsert
    suspend fun upsert(config: RadialConfigEntity)

    @Query("SELECT * FROM radial_items WHERE radial_config_id = :configId ORDER BY page ASC, slot ASC")
    fun observeItems(configId: String): Flow<List<RadialItemEntity>>

    @Query("SELECT * FROM radial_items WHERE radial_config_id = :configId ORDER BY page ASC, slot ASC")
    suspend fun getItems(configId: String): List<RadialItemEntity>

    @Query("SELECT * FROM radial_items")
    suspend fun getAllItems(): List<RadialItemEntity>

    @Upsert
    suspend fun upsertItem(item: RadialItemEntity)

    @Upsert
    suspend fun upsertItems(items: List<RadialItemEntity>)

    @Query("DELETE FROM radial_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM radial_items WHERE radial_config_id = :configId")
    suspend fun deleteItemsFor(configId: String)

    @Transaction
    suspend fun replaceItems(configId: String, items: List<RadialItemEntity>) {
        deleteItemsFor(configId)
        upsertItems(items)
    }
}

@Dao
interface GestureDao {
    @Query("SELECT * FROM gesture_mappings WHERE profile_id = :profileId")
    fun observeForProfile(profileId: String): Flow<List<GestureMappingEntity>>

    @Query("SELECT * FROM gesture_mappings WHERE profile_id = :profileId")
    suspend fun getForProfile(profileId: String): List<GestureMappingEntity>

    @Query("SELECT * FROM gesture_mappings")
    suspend fun getAll(): List<GestureMappingEntity>

    @Upsert
    suspend fun upsert(mapping: GestureMappingEntity)

    @Upsert
    suspend fun upsertAll(mappings: List<GestureMappingEntity>)

    @Query("DELETE FROM gesture_mappings WHERE profile_id = :profileId")
    suspend fun deleteForProfile(profileId: String)
}

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros ORDER BY sort_order ASC, name ASC")
    fun observeAll(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE profile_id = :profileId ORDER BY sort_order ASC, name ASC")
    fun observeForProfile(profileId: String): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MacroEntity?

    @Query("SELECT * FROM macros WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<MacroEntity?>

    @Query("SELECT * FROM macros")
    suspend fun getAll(): List<MacroEntity>

    @Upsert
    suspend fun upsert(macro: MacroEntity)

    @Upsert
    suspend fun upsertAll(macros: List<MacroEntity>)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE macros SET last_run_at = :at, last_run_status = :status WHERE id = :id")
    suspend fun updateRunState(id: String, at: Long, status: String)

    @Query("SELECT * FROM macro_steps WHERE macro_id = :macroId ORDER BY step_order ASC")
    suspend fun getSteps(macroId: String): List<MacroStepEntity>

    @Query("SELECT * FROM macro_steps ORDER BY macro_id ASC, step_order ASC")
    suspend fun getAllSteps(): List<MacroStepEntity>

    @Query("SELECT * FROM macro_steps WHERE macro_id = :macroId ORDER BY step_order ASC")
    fun observeSteps(macroId: String): Flow<List<MacroStepEntity>>

    @Upsert
    suspend fun upsertSteps(steps: List<MacroStepEntity>)

    @Query("DELETE FROM macro_steps WHERE macro_id = :macroId")
    suspend fun deleteSteps(macroId: String)

    @Transaction
    suspend fun replaceSteps(macroId: String, steps: List<MacroStepEntity>) {
        deleteSteps(macroId)
        upsertSteps(steps)
    }

    @Insert
    suspend fun insertExecution(record: MacroExecutionEntity): Long

    @Query("SELECT * FROM macro_executions ORDER BY started_at DESC LIMIT :limit")
    fun observeExecutions(limit: Int = 100): Flow<List<MacroExecutionEntity>>

    @Query("SELECT * FROM macro_executions ORDER BY started_at DESC LIMIT :limit")
    suspend fun getExecutions(limit: Int = 200): List<MacroExecutionEntity>

    @Query("DELETE FROM macro_executions")
    suspend fun clearExecutions()

    @Query("SELECT COUNT(*) FROM macros")
    suspend fun count(): Int
}

@Dao
interface ThemeDao {
    @Query("SELECT * FROM theme_configs")
    fun observeAll(): Flow<List<ThemeConfigEntity>>

    @Query("SELECT * FROM theme_configs WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ThemeConfigEntity?>

    @Query("SELECT * FROM theme_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ThemeConfigEntity?

    @Query("SELECT * FROM theme_configs")
    suspend fun getAll(): List<ThemeConfigEntity>

    @Upsert
    suspend fun upsert(theme: ThemeConfigEntity)
}

@Dao
interface AppDao {
    @Query("SELECT * FROM favorite_apps ORDER BY is_favorite DESC, last_used_at DESC")
    fun observeAll(): Flow<List<FavoriteAppEntity>>

    @Query("SELECT * FROM favorite_apps WHERE is_favorite = 1")
    fun observeFavorites(): Flow<List<FavoriteAppEntity>>

    @Query("SELECT * FROM favorite_apps")
    suspend fun getAll(): List<FavoriteAppEntity>

    @Query("SELECT * FROM favorite_apps WHERE package_name = :packageName LIMIT 1")
    suspend fun get(packageName: String): FavoriteAppEntity?

    @Upsert
    suspend fun upsert(app: FavoriteAppEntity)

    @Upsert
    suspend fun upsertAll(apps: List<FavoriteAppEntity>)

    @Query("DELETE FROM favorite_apps WHERE package_name = :packageName")
    suspend fun delete(packageName: String)

    @Query("UPDATE favorite_apps SET is_favorite = :favorite WHERE package_name = :packageName")
    suspend fun setFavorite(packageName: String, favorite: Boolean)

    @Query(
        "UPDATE favorite_apps SET last_used_at = :at, launch_count = launch_count + 1 " +
            "WHERE package_name = :packageName",
    )
    suspend fun recordLaunch(packageName: String, at: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY is_pinned DESC, updated_at DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY is_pinned DESC, updated_at DESC")
    suspend fun getAll(): List<NoteEntity>

    @Upsert
    suspend fun upsert(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int
}

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_records ORDER BY is_pinned DESC, created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ClipboardRecordEntity>>

    @Query("SELECT * FROM clipboard_records ORDER BY is_pinned DESC, created_at DESC")
    fun observeAll(): Flow<List<ClipboardRecordEntity>>

    @Query("SELECT * FROM clipboard_records WHERE created_at >= :since ORDER BY created_at DESC")
    suspend fun getSince(since: Long): List<ClipboardRecordEntity>

    @Insert
    suspend fun insert(record: ClipboardRecordEntity): Long

    @Query("DELETE FROM clipboard_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE clipboard_records SET is_pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM clipboard_records WHERE is_pinned = 0")
    suspend fun clearUnpinned()

    @Query("DELETE FROM clipboard_records")
    suspend fun clearAll()

    @Query("DELETE FROM clipboard_records WHERE is_pinned = 0 AND created_at < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}

@Dao
interface RecentActionDao {
    @Query("SELECT * FROM recent_actions ORDER BY used_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 12): Flow<List<RecentActionEntity>>

    @Query("SELECT * FROM recent_actions")
    suspend fun getAll(): List<RecentActionEntity>

    @Query("SELECT * FROM recent_actions WHERE action_id = :actionId LIMIT 1")
    suspend fun get(actionId: String): RecentActionEntity?

    @Upsert
    suspend fun upsert(entity: RecentActionEntity)

    @Query("DELETE FROM recent_actions")
    suspend fun clear()
}

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers ORDER BY id DESC")
    fun observeAll(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers")
    suspend fun getAll(): List<TimerEntity>

    @Upsert
    suspend fun upsert(timer: TimerEntity): Long

    @Insert
    suspend fun insert(timer: TimerEntity): Long

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
