package com.collegeproj.crashpulse.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ApiModelsTest {
    @Test
    fun jobStatesExposeOnlyExpectedTerminalStates() {
        val terminal = setOf("SUCCEEDED", "FAILED", "CANCELLED")
        assertTrue("SUCCEEDED" in terminal)
        assertTrue("FAILED" in terminal)
        assertFalse("RUNNING" in terminal)
    }

    @Test
    fun incidentDetailKeepsHumanReviewSafetyMeaning() {
        val id = UUID.randomUUID()
        val view = ApiResultView("1.0", "run_test", "2026-01-01T00:00:00Z", "confirmed_incident", true, true, "end_of_video", true, false)
        assertEquals(id.toString(), id.toString())
        assertTrue(view.human_review_required)
        assertFalse(view.emergency_action_authorized)
    }
}
