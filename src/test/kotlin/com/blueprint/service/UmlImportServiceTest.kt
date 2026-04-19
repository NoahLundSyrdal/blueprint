package com.blueprint.service

import com.blueprint.ir.ArchitectureIR
import com.blueprint.ir.Component
import com.blueprint.ir.ComponentKind
import com.blueprint.ir.Field
import com.blueprint.ir.Ownership
import com.blueprint.ir.SourceRef
import com.blueprint.ir.TypeRef
import com.blueprint.ir.UmlToIR
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

    @Test
    fun `code backed UML maps new related model to the existing source file`() {
        val previous = ArchitectureIR(
            components = listOf(
                model("CarCompany", "app/company.py", "id" to "str", "name" to "str"),
                model("VehicleModel", "app/vehicle.py", "id" to "str", "name" to "str", "company" to "CarCompany"),
                model("Dealership", "app/dealership.py", "id" to "str", "company" to "CarCompany"),
            ),
        )
        val parsed = UmlImportService.ParsedUml(
            entities = listOf(
                UmlImportService.ParsedEntity("CarCompany", listOf("id: str", "name: str")),
                UmlImportService.ParsedEntity("VehicleModel", listOf("id: str", "name: str", "company: CarCompany", "warranty_policy: WarrantyPolicy")),
                UmlImportService.ParsedEntity("WarrantyPolicy", listOf("months: int", "provider: str")),
                UmlImportService.ParsedEntity("Dealership", listOf("id: str", "company: CarCompany")),
            ),
            relationships = listOf(
                UmlImportService.ParsedRelationship("VehicleModel", "references", "WarrantyPolicy"),
            ),
            warnings = emptyList(),
        )

        val remapped = CodeBackedUmlSourceMapper.remap(UmlToIR.toIR(parsed, "cars"), parsed, previous)
        val subset = CodeBackedUmlSourceMapper.changedModelSubset(remapped, previous)

        val byName = remapped.components.associateBy { it.name }
        assertEquals(listOf("app/vehicle.py"), byName.getValue("VehicleModel").ownership.files)
        assertEquals(listOf("app/vehicle.py"), byName.getValue("WarrantyPolicy").ownership.files)

        assertEquals(setOf("VehicleModel", "WarrantyPolicy"), subset.components.map { it.name }.toSet())
        assertTrue(subset.components.none { component ->
            component.ownership.files.any { it.startsWith("blueprint_demo/imported_") }
        })
    }
}

private fun model(name: String, path: String, vararg fields: Pair<String, String>): Component =
    Component(
        id = "app.${name.lowercase()}",
        name = name,
        kind = ComponentKind.MODEL,
        ownership = Ownership(files = listOf(path)),
        sourceRef = SourceRef(path, 1),
        fields = fields.map { (fieldName, type) -> Field(fieldName, TypeRef(type)) },
    )

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
