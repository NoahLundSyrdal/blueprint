package com.blueprint.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

class PythonAstParserTest {
    @Test
    fun `ast parser recovers classes module functions imports calls and multiline signatures`() {
        val dir = Files.createTempDirectory("blueprint-python-ast")
        val app = dir.resolve("app")
        Files.createDirectories(app)
        val models = app.resolve("models.py")
        val service = app.resolve("service.py")

        Files.writeString(
            models,
            """
            from dataclasses import dataclass
            from typing import Protocol

            @dataclass
            class User:
                id: str
                email: str

            class InviteRepository(Protocol):
                def save(self, invite: "Invite") -> None:
                    ...

            @dataclass
            class Invite:
                id: str
                user: User
                created_at: str
            """.trimIndent(),
        )
        Files.writeString(
            service,
            """
            from .models import Invite, InviteRepository, User

            def create_invite(
                repo: InviteRepository,
                user: User,
            ) -> Invite:
                invite = Invite(id="i1", user=user, created_at="now")
                repo.save(invite)
                return invite
            """.trimIndent(),
        )

        val result = PythonAstParser.parse(dir, listOf(models, service))
        assumeTrue("python3 is required for this AST parser test", result.usedAst)

        val names = result.symbols.map { it.name }.toSet()
        assertTrue(names.containsAll(setOf("User", "Invite", "InviteRepository", "create_invite")))

        val invite = result.symbols.single { it.name == "Invite" }
        assertEquals("CLASS", invite.symbolKind)
        assertEquals("app/models.py", invite.relPath)
        assertTrue(invite.decorators.contains("dataclass"))
        assertTrue(invite.fields.any { it.name == "user" && it.type == "User" })

        val createInvite = result.symbols.single { it.name == "create_invite" }
        assertEquals("FUNCTION", createInvite.symbolKind)
        assertTrue(createInvite.imports.contains("InviteRepository"))
        assertTrue(createInvite.calls.contains("Invite"))
        assertTrue(createInvite.methods.single().params.any { it.name == "repo" && it.type == "InviteRepository" })
        assertEquals("Invite", createInvite.methods.single().returns)
    }
}
