package com.astratouch.app.data.repository

import com.astratouch.app.data.local.AstraDatabase
import com.astratouch.app.data.mapper.toDomain
import com.astratouch.app.data.mapper.toEntity
import com.astratouch.app.domain.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(private val db: AstraDatabase) {

    fun observeNotes(): Flow<List<Note>> = db.noteDao().observeAll().map { l -> l.map { it.toDomain() } }

    suspend fun getNote(id: Long): Note? = db.noteDao().getById(id)?.toDomain()

    suspend fun upsert(note: Note): Long {
        val now = System.currentTimeMillis()
        val normalized = note.copy(
            updatedAt = now,
            createdAt = if (note.createdAt == 0L) now else note.createdAt,
        )
        return db.noteDao().upsert(normalized.toEntity())
    }

    suspend fun delete(id: Long) = db.noteDao().deleteById(id)

    suspend fun count(): Int = db.noteDao().count()

    suspend fun getAll(): List<Note> = db.noteDao().getAll().map { it.toDomain() }
}
