package com.astratouch.app.data.repository

import com.astratouch.app.data.local.AstraDatabase
import com.astratouch.app.data.mapper.toDomain
import com.astratouch.app.data.mapper.toEntity
import com.astratouch.app.domain.model.AstraTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerRepository @Inject constructor(private val db: AstraDatabase) {

    fun observeTimers(): Flow<List<AstraTimer>> =
        db.timerDao().observeAll().map { l -> l.map { it.toDomain() } }

    suspend fun upsert(timer: AstraTimer): Long = db.timerDao().upsert(timer.toEntity())

    suspend fun delete(id: Long) = db.timerDao().deleteById(id)

    suspend fun getRunning(): List<AstraTimer> = db.timerDao().getAll().map { it.toDomain() }.filter { it.isRunning }
}
