package com.astratouch.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astratouch.app.data.local.AstraDatabase
import com.astratouch.app.data.local.entity.MacroEntity
import com.astratouch.app.data.local.entity.MacroStepEntity
import com.astratouch.app.data.local.entity.NoteEntity
import com.astratouch.app.data.local.entity.ProfileEntity
import com.astratouch.app.data.repository.MacroRepository
import com.astratouch.app.data.repository.NotesRepository
import com.astratouch.app.data.repository.ProfileRepository
import com.astratouch.app.domain.model.MacroRunStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room integration tests: real SQLite, real DAOs, real migrations.
 * Runs under Robolectric so it executes in the regular JVM unit-test task.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33, 36])
class DatabaseTest {

    private lateinit var db: AstraDatabase
    private lateinit var profileRepository: ProfileRepository
    private lateinit var macroRepository: MacroRepository
    private lateinit var notesRepository: NotesRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AstraDatabase::class.java,
        ).allowMainThreadQueries().build()
        profileRepository = ProfileRepository(db)
        macroRepository = MacroRepository(db)
        notesRepository = NotesRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `seeding creates every built-in profile with dependent rows`() = runTest {
        profileRepository.ensureSeeded()
        val profiles = profileRepository.observeProfiles().first()
        assertEquals(com.astratouch.app.domain.model.ProfileType.entries.size, profiles.size)
        profiles.forEach { profile ->
            assertNotNull(profileRepository.getBubbleConfig(profile.bubbleConfigId))
            assertNotNull(profileRepository.getRadialConfig(profile.radialConfigId))
            assertTrue(profileRepository.getGestures(profile.id).isNotEmpty())
            assertTrue(
                "profile ${profile.id} should have radial items",
                profileRepository.observeRadialItems(profile.radialConfigId).first().isNotEmpty(),
            )
        }
        assertEquals(1, profiles.count { it.isActive })
    }

    @Test
    fun `seeding twice does not duplicate rows`() = runTest {
        profileRepository.ensureSeeded()
        val first = profileRepository.observeProfiles().first().size
        profileRepository.ensureSeeded()
        assertEquals(first, profileRepository.observeProfiles().first().size)
    }

    @Test
    fun `macros seed with ordered steps and history records runs`() = runTest {
        macroRepository.ensureSeeded()
        val macros = macroRepository.observeMacros().first()
        assertEquals(6, macros.size)
        // The six required defaults are present by name.
        val names = macros.map { it.name }.toSet()
        assertTrue(
            names.containsAll(
                setOf("Work Mode", "Reading Mode", "Gaming Mode", "Media Mode", "Quick Screenshot", "Productivity Mode"),
            ),
        )
        val first = macros.first()
        val steps = macroRepository.getSteps(first.id)
        assertTrue(steps.isNotEmpty())
        assertEquals(steps.indices.toList(), steps.map { it.order })

        macroRepository.recordRun(first, MacroRunStatus.SUCCESS, "ok")
        assertEquals(1, macroRepository.getExecutions().size)
        assertEquals(MacroRunStatus.SUCCESS, macroRepository.getExecutions().first().status)
    }

    @Test
    fun `duplicating a macro copies its steps`() = runTest {
        macroRepository.ensureSeeded()
        val source = macroRepository.observeMacros().first().first()
        val copy = macroRepository.duplicateMacro(source.id, "macro-copy", "Copy")
        assertNotNull(copy)
        assertEquals(copy!!.id, "macro-copy")
        assertTrue(macroRepository.getSteps("macro-copy").isNotEmpty())
    }

    @Test
    fun `deleting a macro cascades to its steps`() = runTest {
        macroRepository.ensureSeeded()
        val source = macroRepository.observeMacros().first().first()
        macroRepository.deleteMacro(source.id)
        assertTrue(macroRepository.getSteps(source.id).isEmpty())
    }

    @Test
    fun `notes round trip and update`() = runTest {
        val id = notesRepository.upsert(
            com.astratouch.app.domain.model.Note(title = "First", body = "Hello"),
        )
        assertTrue(id > 0)
        val saved = notesRepository.getNote(id)
        assertEquals("First", saved?.title)
        notesRepository.upsert(saved!!.copy(body = "Updated"))
        assertEquals("Updated", notesRepository.getNote(id)?.body)
        notesRepository.delete(id)
        assertEquals(0, notesRepository.count())
    }

    @Test
    fun `bubble position persists without touching other fields`() = runTest {
        profileRepository.ensureSeeded()
        val profile = profileRepository.observeActiveProfile().first()!!
        val original = profileRepository.getBubbleConfig(profile.bubbleConfigId)!!
        profileRepository.saveBubblePosition(original.id, 321, 654, "LEFT")
        val updated = profileRepository.getBubbleConfig(original.id)!!
        assertEquals(321, updated.positionX)
        assertEquals(654, updated.positionY)
        assertEquals(original.sizeDp, updated.sizeDp)
    }

    @Test
    fun `macro step ordering is normalised on save`() = runTest {
        val macro = com.astratouch.app.domain.model.Macro(id = "m1", name = "Test")
        val steps = listOf(
            com.astratouch.app.domain.model.MacroStep(id = "a", macroId = "m1", order = 5, actionId = "nav.home"),
            com.astratouch.app.domain.model.MacroStep(id = "b", macroId = "m1", order = 1, actionId = "nav.back"),
        )
        macroRepository.saveMacro(macro, steps)
        val stored = macroRepository.getSteps("m1")
        assertEquals(listOf("a", "b"), stored.map { it.id })
        assertEquals(listOf(0, 1), stored.map { it.order })
    }

    @Test
    fun `database keeps foreign keys between radial configs and items`() = runTest {
        profileRepository.ensureSeeded()
        val profile = profileRepository.observeProfiles().first().first()
        val config = profileRepository.getRadialConfig(profile.radialConfigId)!!
        val items = profileRepository.observeRadialItems(config.id).first()
        assertTrue(items.all { it.radialConfigId == config.id })
        assertEquals(items.size, items.map { it.slot }.distinct().size)
    }
}
