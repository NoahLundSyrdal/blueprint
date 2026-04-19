package com.blueprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowIntentRoutingTest {
    @Test
    fun `recognizes workflow questions and legacy synonyms without exposing them in copy`() {
        assertTrue(WorkflowIntentRouting.isWorkflowQuestion("what changed in the diff?"))
        assertTrue(WorkflowIntentRouting.isWorkflowQuestion("can you generate patch for this uml?"))
        assertTrue(WorkflowIntentRouting.isWorkflowQuestion("how do I apply patch after review?"))
        assertTrue(WorkflowIntentRouting.isWorkflowQuestion("should I refresh from code now?"))
        assertTrue(WorkflowIntentRouting.isWorkflowQuestion("do I need code nodes first?"))
        assertTrue(WorkflowIntentRouting.isWorkflowQuestion("should I create code nodes first?"))
    }

    @Test
    fun `keeps uml edit requests out of workflow routing`() {
        assertFalse(WorkflowIntentRouting.isWorkflowQuestion("add a Supplier entity with one address field"))
        assertFalse(WorkflowIntentRouting.isWorkflowQuestion("rename InviteGuest to GuestInvite"))
        assertFalse(WorkflowIntentRouting.isWorkflowQuestion("make CarCompany own many Dealerships"))
    }
}
