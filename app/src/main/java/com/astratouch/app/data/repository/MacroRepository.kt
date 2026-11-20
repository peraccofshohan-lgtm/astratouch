package com.astratouch.app.data.repository

import com.astratouch.app.data.local.AstraDatabase
import com.astratouch.app.data.local.entity.MacroExecutionEntity
import com.astratouch.app.data.mapper.toDomain
import com.astratouch.app.data.mapper.toEntity
import com.astratouch.app.domain.model.DefaultMacros
import com.astratouch.app.domain.model.Macro
import com.astratouch.app.domain.model.MacroExecutionRecord
import com.astratouch.app.domain.model.MacroRunStatus
import com.astratouch.app.domain.model.MacroStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroRepository @Inject constructor(
    private val db: AstraDatabase,
) {
    fun observeMacros(): Flow<List<Macro>> =
        db.macroDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeMacrosFor(profileId: String): Flow<List<Macro>> =
        db.macroDao().observeForProfile(profileId).map { list -> list.map { it.toDomain() } }

    fun observeMacro(id: String): Flow<Macro?> =
        db.macroDao().observeById(id).map { it?.toDomain() }

    fun observeSteps(macroId: String): Flow<List<MacroStep>> =
        db.macroDao().observeSteps(macroId).map { list -> list.map { it.toDomain() } }

    suspend fun getMacro(id: String): Macro? = db.macroDao().getById(id)?.toDomain()

    suspend fun getSteps(macroId: String): List<MacroStep> =
        db.macroDao().getSteps(macroId).map { it.toDomain() }

    suspend fun getAllMacros(): List<Macro> = db.macroDao().getAll().map { it.toDomain() }

    suspend fun getAllSteps(): List<MacroStep> = db.macroDao().getAllSteps().map { it.toDomain() }

    fun observeExecutions(limit: Int = 100): Flow<List<MacroExecutionRecord>> =
        db.macroDao().observeExecutions(limit).map { list -> list.map { it.toDomain() } }

    suspend fun getExecutions(limit: Int = 200): List<MacroExecutionRecord> =
        db.macroDao().getExecutions(limit).map { it.toDomain() }

    suspend fun saveMacro(macro: Macro, steps: List<MacroStep>) = db.macroDao().let {
        it.upsert(macro.toEntity())
        it.replaceSteps(
            macro.id,
            steps.mapIndexed { index, step -> step.copy(macroId = macro.id, order = index).toEntity() },
        )
    }

    suspend fun deleteMacro(id: String) = db.macroDao().deleteById(id)

    suspend fun duplicateMacro(id: String, newId: String, newName: String): Macro? {
        val original = db.macroDao().getById(id) ?: return null
        val steps = db.macroDao().getSteps(id)
        val now = System.currentTimeMillis()
        val copy = original.copy(
            id = newId,
            name = newName,
            createdAt = now,
            updatedAt = now,
            lastRunAt = 0L,
            lastRunStatus = MacroRunStatus.NEVER.name,
            sortOrder = original.sortOrder + 1,
        )
        db.macroDao().upsert(copy)
        db.macroDao().replaceSteps(
            newId,
            steps.map { it.copy(id = "$newId-${it.order}", macroId = newId) },
        )
        return copy.toDomain()
    }

    suspend fun recordRun(macro: Macro, status: MacroRunStatus, detail: String) {
        val now = System.currentTimeMillis()
        db.macroDao().updateRunState(macro.id, now, status.name)
        db.macroDao().insertExecution(
            MacroExecutionEntity(
                macroId = macro.id,
                macroName = macro.name,
                startedAt = now,
                finishedAt = now,
                status = status.name,
                detail = detail,
            ),
        )
    }

    suspend fun clearHistory() = db.macroDao().clearExecutions()

    suspend fun ensureSeeded() {
        if (db.macroDao().count() > 0) return
        val now = System.currentTimeMillis()
        DefaultMacros.all().forEachIndexed { index, (macro, steps) ->
            val seeded = macro.copy(createdAt = now, updatedAt = now, sortOrder = index)
            db.macroDao().upsert(seeded.toEntity())
            db.macroDao().upsertSteps(steps.map { it.toEntity() })
        }
    }
}
