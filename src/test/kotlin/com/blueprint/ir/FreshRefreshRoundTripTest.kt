package com.blueprint.ir

import com.blueprint.model.Patch
import com.blueprint.service.DiskSnapshot
import com.blueprint.service.PatchFreshness
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

class FreshRefreshRoundTripTest {
    @Test
    fun `after disk changes ast refresh sees newly applied UML fields and classes`() {
        val dir = Files.createTempDirectory("blueprint-fresh-refresh")
        val models = dir.resolve("models.py")
        Files.writeString(
            models,
            """
            from dataclasses import dataclass

            @dataclass
            class Invite:
                id: str
                email: str
            """.trimIndent(),
        )

        val refreshedBefore = PythonAstParser.parse(dir, listOf(models))
        assumeTrue("python3 is required for this refresh regression", refreshedBefore.usedAst)
        assertTrue(refreshedBefore.symbols.single { it.name == "Invite" }.fields.none { it.name == "expires_at" })

        val updatedContent = """
            from dataclasses import dataclass
            from datetime import datetime

            @dataclass
            class Invite:
                id: str
                email: str
                expires_at: datetime

            @dataclass
            class InviteAuditLog:
                actor_email: str
                action: str
                created_at: datetime
                invite: Invite
        """.trimIndent()
        val patch = Patch(path = "models.py", action = "update", content = updatedContent)
        val before = DiskSnapshot(exists = true, content = Files.readString(models))
        Files.writeString(models, updatedContent)
        val after = DiskSnapshot(exists = true, content = Files.readString(models))
        val verification = PatchFreshness.verify(patch, before, after)
        assertTrue(verification.matchesPatch)
        assertTrue(verification.changedDisk)

        val refreshedAfter = PythonAstParser.parse(dir, listOf(models))
        assumeTrue("python3 is required for this refresh regression", refreshedAfter.usedAst)
        val invite = refreshedAfter.symbols.single { it.name == "Invite" }
        val auditLog = refreshedAfter.symbols.single { it.name == "InviteAuditLog" }
        assertTrue(invite.fields.any { it.name == "expires_at" && it.type == "datetime" })
        assertTrue(auditLog.fields.any { it.name == "invite" && it.type == "Invite" })
    }
}
