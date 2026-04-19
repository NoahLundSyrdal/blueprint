package com.blueprint.service

import com.blueprint.model.NodeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonModelModuleRendererTest {
    @Test
    fun `fresh module imports date and datetime field types`() {
        val rendered = PythonModelModuleRenderer.render(
            listOf(
                NodeContract(
                    name = "Invite",
                    kind = "schema",
                    schema = """
                        id: str
                        created_at: datetime
                        expires_on: date
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(rendered.contains("from dataclasses import dataclass\n"))
        assertTrue(rendered.contains("from datetime import date, datetime\n"))
        assertTrue(rendered.contains("created_at: datetime"))
        assertTrue(rendered.contains("expires_on: date"))
    }

    @Test
    fun `fresh module orders referenced models before dependents`() {
        val rendered = PythonModelModuleRenderer.render(
            listOf(
                NodeContract(
                    name = "InventoryVehicle",
                    kind = "schema",
                    schema = """
                        vin: str
                        model: VehicleModel
                        warranty_policy: WarrantyPolicy
                        listed_on: date
                    """.trimIndent(),
                ),
                NodeContract(
                    name = "WarrantyPolicy",
                    kind = "schema",
                    schema = """
                        id: str
                        months: int
                    """.trimIndent(),
                ),
                NodeContract(
                    name = "VehicleModel",
                    kind = "schema",
                    schema = """
                        id: str
                        name: str
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(rendered.contains("from datetime import date\n"))
        assertTrue(
            "dependencies should be rendered before InventoryVehicle",
            rendered.indexOf("class WarrantyPolicy:") < rendered.indexOf("class InventoryVehicle:") &&
                rendered.indexOf("class VehicleModel:") < rendered.indexOf("class InventoryVehicle:"),
        )
    }

    @Test
    fun `merge preserves existing methods while replacing generated fields`() {
        val existing = """
            from dataclasses import dataclass

            @dataclass
            class InventoryVehicle:
                vin: str

                def mark_sold(self) -> None:
                    self.status = "sold"
        """.trimIndent()

        val result = PythonModelModuleRenderer.renderWithReport(
            listOf(
                NodeContract(
                    name = "InventoryVehicle",
                    kind = "schema",
                    schema = """
                        vin: str
                        status: str
                        listed_on: date
                    """.trimIndent(),
                ),
            ),
            existingContent = existing,
        )
        val rendered = result.content

        assertEquals(1, Regex("@dataclass").findAll(rendered).count())
        assertTrue(rendered.contains("from datetime import date\n"))
        assertTrue(rendered.contains("    vin: str\n    status: str\n    listed_on: date\n\n    def mark_sold"))
        assertTrue(rendered.contains("self.status = \"sold\""))
        assertEquals(
            listOf("add field InventoryVehicle.status", "add field InventoryVehicle.listed_on", "add import from datetime import date"),
            result.changes,
        )
    }

    @Test
    fun `merge preserves unrelated top level code`() {
        val existing = """
            from dataclasses import dataclass

            class Helper:
                pass

            def build_slug(value: str) -> str:
                return value.lower()
        """.trimIndent()

        val rendered = PythonModelModuleRenderer.render(
            listOf(
                NodeContract(
                    name = "WarrantyPolicy",
                    kind = "schema",
                    schema = """
                        id: str
                        expires_on: date
                    """.trimIndent(),
                ),
            ),
            existingContent = existing,
        )

        assertTrue(rendered.contains("class Helper:\n    pass"))
        assertTrue(rendered.contains("def build_slug(value: str) -> str:\n    return value.lower()"))
        assertTrue(rendered.contains("class WarrantyPolicy:"))
        assertTrue(rendered.contains("expires_on: date"))
    }

    @Test
    fun `merge can repair missing imports without reporting field changes`() {
        val existing = """
            from dataclasses import dataclass

            @dataclass
            class Invite:
                id: str
                expires_on: date
        """.trimIndent()

        val result = PythonModelModuleRenderer.renderWithReport(
            listOf(
                NodeContract(
                    name = "Invite",
                    kind = "schema",
                    schema = """
                        id: str
                        expires_on: date
                    """.trimIndent(),
                ),
            ),
            existingContent = existing,
        )

        assertTrue(result.content.contains("from datetime import date\n"))
        assertEquals(listOf("add import from datetime import date"), result.changes)
    }
}
