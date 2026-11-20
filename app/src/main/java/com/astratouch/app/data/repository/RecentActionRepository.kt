package com.astratouch.app.data.repository

import com.astratouch.app.data.local.AstraDatabase
import com.astratouch.app.data.local.entity.RecentActionEntity
import com.astratouch.app.domain.model.RecentAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentActionRepository @Inject constructor(private val db: AstraDatabase) {

    fun observeRecent(): Flow<List<RecentAction>> =
        db.recentActionDao().observeRecent().map { l ->
            l.map { RecentAction(actionId = it.actionId, usedAt = it.usedAt) }
        }

    suspend fun record(actionId: String) {
        val existing = db.recentActionDao().get(actionId)
        db.recentActionDao().upsert(
            RecentActionEntity(
                actionId = actionId,
                usedAt = System.currentTimeMillis(),
                useCount = (existing?.useCount ?: 0) + 1,
            ),
        )
    }

    suspend fun clear() = db.recentActionDao().clear()
}
