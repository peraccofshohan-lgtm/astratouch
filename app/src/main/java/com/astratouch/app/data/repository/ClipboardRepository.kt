package com.astratouch.app.data.repository

import com.astratouch.app.data.local.AstraDatabase
import com.astratouch.app.data.mapper.toDomain
import com.astratouch.app.data.mapper.toEntity
import com.astratouch.app.domain.model.ClipboardRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clipboard history repository.
 *
 * Sensitive filtering happens before it reaches this class
 * (see [com.astratouch.app.util.SensitiveContentFilter]); records flagged
 * redacted are never stored at all — the store only accepts already-vetted text.
 */
@Singleton
class ClipboardRepository @Inject constructor(private val db: AstraDatabase) {

    fun observeRecords(): Flow<List<ClipboardRecord>> =
        db.clipboardDao().observeAll().map { l -> l.map { it.toDomain() } }

    fun observeRecent(): Flow<List<ClipboardRecord>> =
        db.clipboardDao().observeRecent().map { l -> l.map { it.toDomain() } }

    suspend fun add(record: ClipboardRecord): Long = db.clipboardDao().insert(record.toEntity())

    suspend fun delete(id: Long) = db.clipboardDao().deleteById(id)

    suspend fun setPinned(id: Long, pinned: Boolean) = db.clipboardDao().setPinned(id, pinned)

    suspend fun clearUnpinned() = db.clipboardDao().clearUnpinned()

    suspend fun clearAll() = db.clipboardDao().clearAll()

    suspend fun purgeOlderThan(cutoff: Long) = db.clipboardDao().purgeOlderThan(cutoff)

    suspend fun getAll(): List<ClipboardRecord> = db.clipboardDao().getSince(0L).map { it.toDomain() }
}
