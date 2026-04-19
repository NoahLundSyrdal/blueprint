package com.blueprint.service

import com.intellij.openapi.project.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UmlImportServiceTest {
    @Test
    fun `parse omits mermaid stereotypes from field previews`() {
        val parsed = UmlImportService(fakeProject("uml-import-test")).parse(
            """
            classDiagram
            class Invite <<dataclass>> {
              id: str
              accepted_at: datetime
            }
            """.trimIndent(),
        )

        assertEquals(listOf("Invite"), parsed.entities.map { it.name })
        assertEquals(listOf("id: str", "accepted_at: datetime"), parsed.entities.single().fields)
    }

    @Test
    fun `parse drops escaped html stereotype lines from fields`() {
        val parsed = UmlImportService(fakeProject("uml-import-test")).parse(
            """
            classDiagram
            class Invite {
              &lt;&lt;dataclass&gt;&gt;
              id: str
            }
            """.trimIndent(),
        )

        assertEquals(listOf("id: str"), parsed.entities.single().fields)
        assertTrue(parsed.warnings.isEmpty() || parsed.warnings.none { it.contains("dataclass", ignoreCase = true) })
    }
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
