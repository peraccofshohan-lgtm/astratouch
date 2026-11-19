package com.astratouch.app.service.action

import com.astratouch.app.domain.model.AccessId
import com.astratouch.app.domain.model.ActionCategory
import com.astratouch.app.domain.model.ActionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM checks of the action metadata contract. Registry wiring itself is
 * covered by ActionRegistryIntegrationTest (Robolectric).
 */
class ActionModelTest {

    private val action = AstraAction(
        id = ActionIds.SCREENSHOT,
        name = "Screenshot",
        description = "Capture the screen",
        iconKey = "ic_camera",
        category = ActionCategory.CAPTURE,
        requiredAccess = setOf(AccessId.ACCESSIBILITY),
        minApi = 30,
    )

    @Test
    fun `api availability respects min api`() {
        assertFalse(action.isApiSupported(29))
        assertTrue(action.isApiSupported(30))
        assertTrue(action.isApiSupported(36))
    }

    @Test
    fun `api availability respects an upper bound`() {
        val legacy = action.copy(maxApiInclusive = 31)
        assertTrue(legacy.isApiSupported(30))
        assertFalse(legacy.isApiSupported(32))
    }

    @Test
    fun `action ids are unique strings`() {
        val ids = ActionIds::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.isAccessible = true; it.get(null) as String }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all { it.contains('.') })
    }

    @Test
    fun `failure reasons all carry a user facing message`() {
        FailureReason.entries.forEach { reason ->
            assertTrue("missing message for $reason", reason.userMessage.isNotBlank())
        }
    }

    @Test
    fun `capability states expose readable labels`() {
        CapabilityState.entries.forEach { assertTrue(it.label.isNotBlank()) }
        assertTrue(CapabilityState.AVAILABLE.isAvailable)
        assertFalse(CapabilityState.NEEDS_PERMISSION.isAvailable)
    }

    @Test
    fun `action results keep explanations`() {
        val failure = ActionResult.Failure(FailureReason.TIMEOUT, "Stopped")
        assertEquals(FailureReason.TIMEOUT, failure.reason)
        assertTrue(failure.recoverable)
        assertTrue(ActionResult.NeedsPermission(AccessId.OVERLAY, "why").explanation.isNotBlank())
        assertTrue(ActionResult.Unsupported("nope").explanation.isNotBlank())
    }
}
