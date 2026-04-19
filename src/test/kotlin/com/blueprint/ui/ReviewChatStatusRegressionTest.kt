package com.blueprint.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReviewChatStatusRegressionTest {
    private val sourcePath = Paths.get("src/main/kotlin/com/blueprint/ui/BlueprintPanel.kt")

    /**
     * Verifies review results are summarized in chat and status using the human explanation helpers.
     */
    @Test
    fun `review flow sends human explanation to chat and status`() {
        val source = Files.readString(sourcePath)

        assertTrue(source.contains("val reviewExplanation = ReviewExplanation.summary("))
        assertTrue(source.contains("val reviewStatusLine = ReviewExplanation.statusLine("))
        assertTrue(source.contains("appendChat(\"Blueprint\", \"\$reviewStatusLine\\n\\n\$reviewExplanation\")"))
        assertTrue(source.contains("status(reviewStatusLine)"))
    }
}
