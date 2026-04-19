package com.blueprint.ui

import com.blueprint.service.UmlImportService
import com.intellij.openapi.project.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UmlPreviewSanitizationTest {
    @Test
    fun `uml parser and proposal preview drop dataclass stereotype fields`() {
        val parsed = UmlImportService(fakeProject("uml-preview-test")).parse(
            """
            classDiagram
            class Invite {
              &lt;&lt;dataclass&gt;&gt;
              id: str
              accepted_at: datetime
            }
            """.trimIndent(),
        )

        val visibleFields = parsed.entities.single().fields
            .map { sanitizeFieldForTest(it) }
            .filter { it.isNotBlank() }

        assertEquals(listOf("id: str", "accepted_at: datetime"), parsed.entities.single().fields)
        assertEquals(listOf("id: str", "accepted_at: datetime"), visibleFields)
        assertFalse(visibleFields.any { it.contains("dataclass", ignoreCase = true) })
        assertTrue(visibleFields.none { it.contains("&lt;&lt;") || it.contains("&gt;&gt;") })
    }

    private fun sanitizeFieldForTest(field: String): String =
        field
            .replace("&lt;&lt;", "<<")
            .replace("&gt;&gt;", ">>")
            .trim()
            .takeUnless { it.matches(Regex("""<<[^>]+>>""")) }
            .orEmpty()
}

private fun fakeProject(name: String): Project =
    java.lang.reflect.Proxy.newProxyInstance(
        Project::class.java.classLoader,
        arrayOf(Project::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getName" -> name
            "isDisposed" -> false
            "getBasePath" -> null
            "toString" -> "FixtureProject($name)"
            "hashCode" -> name.hashCode()
            "equals" -> false
            else -> null
        }
    } as Project
