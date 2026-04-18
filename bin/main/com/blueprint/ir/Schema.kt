package com.blueprint.ir

import com.google.gson.annotations.SerializedName

/**
 * Typed architecture intermediate representation.
 *
 * The IR is the source of truth for a project's architecture. Mermaid diagrams,
 * BlueprintNode graphs, and generated code skeletons are all projections over
 * this structure.
 *
 * v1 scope: components, contracts, data types, events, extension points,
 * edges, ownership, concurrency. Layering/side-effect/import policy deferred
 * until they have a consumer.
 */
data class ArchitectureIR(
    val version: String = "1",
    val project: ProjectMeta = ProjectMeta(),
    val components: List<Component> = emptyList(),
    val contracts: List<Contract> = emptyList(),
    val dataTypes: List<DataType> = emptyList(),
    val events: List<EventDecl> = emptyList(),
    val extensionPoints: List<ExtensionPoint> = emptyList(),
    val edges: List<Edge> = emptyList(),
    val coverage: RecoveryCoverage? = null,
)

data class ProjectMeta(
    val name: String = "",
    val language: Language = Language.PYTHON,
    val pythonMinVersion: String = "3.10",
    val concurrencyDefault: Concurrency = Concurrency.SYNC,
    val sourceRoots: List<String> = emptyList(),
)

enum class Language {
    @SerializedName("python") PYTHON,
    @SerializedName("other") OTHER,
}

enum class Concurrency {
    @SerializedName("sync") SYNC,
    @SerializedName("async") ASYNC,
}

// ----- Components -----

data class Component(
    val id: String,                          // stable id, e.g. "payments.service"
    val name: String,                        // display name, e.g. "PaymentService"
    val kind: ComponentKind = ComponentKind.SERVICE,
    val concurrency: Concurrency = Concurrency.SYNC,
    val provides: List<String> = emptyList(),    // contract ids
    val requires: List<String> = emptyList(),    // contract ids
    val ownership: Ownership = Ownership(),
    val allowedPatterns: Set<String> = emptySet(),
    val forbiddenPatterns: Set<String> = emptySet(),
    val fields: List<Field> = emptyList(),       // component-owned state (primarily MODEL)
    val operations: List<Operation> = emptyList(),// bare operations not tied to a contract
    val tags: Set<String> = emptySet(),
    val opaqueAnnotations: List<OpaqueAnnotation> = emptyList(),
    val sourceRef: SourceRef? = null,
    val description: String = "",
)

enum class ComponentKind {
    @SerializedName("service") SERVICE,
    @SerializedName("adapter") ADAPTER,
    @SerializedName("port") PORT,
    @SerializedName("model") MODEL,
    @SerializedName("registry") REGISTRY,
    @SerializedName("cli") CLI,
    @SerializedName("job") JOB,
    @SerializedName("test") TEST,
    @SerializedName("docs") DOCS,
    @SerializedName("other") OTHER,
}

data class Ownership(
    val files: List<String> = emptyList(),
    val globs: List<String> = emptyList(),
    val excludeGlobs: List<String> = emptyList(),
) {
    fun isEmpty(): Boolean = files.isEmpty() && globs.isEmpty()
}

// ----- Contracts -----

data class Contract(
    val id: String,
    val name: String,
    val kind: ContractKind = ContractKind.INTERFACE,
    val concurrency: Concurrency = Concurrency.SYNC,
    val operations: List<Operation> = emptyList(),
    val schema: SchemaRef? = null,
    val description: String = "",
)

enum class ContractKind {
    @SerializedName("interface") INTERFACE,
    @SerializedName("event") EVENT,
    @SerializedName("schema") SCHEMA,
    @SerializedName("cli") CLI,
}

data class Operation(
    val name: String,
    val params: List<Param> = emptyList(),
    val returns: TypeRef = TypeRef("None"),
    val raises: List<TypeRef> = emptyList(),
    val concurrency: Concurrency = Concurrency.SYNC,
    val patterns: Set<String> = emptySet(),
)

data class Param(
    val name: String,
    val type: TypeRef = TypeRef("Any"),
    val default: String? = null,
)

// ----- Data -----

data class DataType(
    val id: String,
    val name: String,
    val kind: DataKind = DataKind.DATACLASS,
    val fields: List<Field> = emptyList(),
    val invariants: List<String> = emptyList(),
    val sourceRef: SourceRef? = null,
)

enum class DataKind {
    @SerializedName("dataclass") DATACLASS,
    @SerializedName("typed_dict") TYPED_DICT,
    @SerializedName("enum") ENUM,
    @SerializedName("alias") ALIAS,
    @SerializedName("pydantic") PYDANTIC,
}

data class Field(
    val name: String,
    val type: TypeRef = TypeRef("Any"),
    val optional: Boolean = false,
    val default: String? = null,
)

// ----- Events & registries -----

data class EventDecl(
    val id: String,
    val name: String,
    val payload: TypeRef = TypeRef("Any"),
)

data class ExtensionPoint(
    val id: String,
    val name: String,
    val payload: TypeRef = TypeRef("Any"),
    val ordering: Ordering = Ordering.UNORDERED,
    val registrations: List<Registration> = emptyList(),
)

enum class Ordering {
    @SerializedName("unordered") UNORDERED,
    @SerializedName("priority") PRIORITY,
    @SerializedName("insertion") INSERTION,
}

data class Registration(
    val componentId: String,
    val priority: Int? = null,
)

// ----- Edges -----

data class Edge(
    val from: String,                        // component id
    val to: String,                          // component id, contract id, or event id
    val toKind: EdgeTargetKind = EdgeTargetKind.COMPONENT,
    val kind: EdgeKind = EdgeKind.CALLS,
    val label: String = "",
)

enum class EdgeKind {
    @SerializedName("calls") CALLS,
    @SerializedName("emits") EMITS,
    @SerializedName("listens") LISTENS,
    @SerializedName("registers_with") REGISTERS_WITH,
    @SerializedName("reads") READS,
    @SerializedName("writes") WRITES,
    @SerializedName("extends") EXTENDS,
    @SerializedName("contains") CONTAINS,
    @SerializedName("references") REFERENCES,
}

enum class EdgeTargetKind {
    @SerializedName("component") COMPONENT,
    @SerializedName("contract") CONTRACT,
    @SerializedName("event") EVENT,
    @SerializedName("extension_point") EXTENSION_POINT,
}

// ----- Refs / misc -----

data class TypeRef(
    val id: String = "Any",
    val generic: List<TypeRef> = emptyList(),
    val optional: Boolean = false,
) {
    override fun toString(): String {
        val base = if (generic.isEmpty()) id else "$id[${generic.joinToString(", ")}]"
        return if (optional) "Optional[$base]" else base
    }
}

data class SchemaRef(val id: String)

data class SourceRef(val path: String, val line: Int? = null)

data class OpaqueAnnotation(
    val symbol: String,
    val sourceRef: SourceRef,
    val reason: String,
)

// ----- Recovery -----

data class RecoveryCoverage(
    val componentsRecovered: Int = 0,
    val componentsTotal: Int = 0,
    val contractsRecovered: Int = 0,
    val opaqueZones: List<OpaqueAnnotation> = emptyList(),
    val driftWarnings: List<DriftWarning> = emptyList(),
)

data class DriftWarning(
    val componentId: String,
    val kind: DriftKind,
    val detail: String,
    val sourceRef: SourceRef? = null,
)

enum class DriftKind {
    @SerializedName("orphan_file") ORPHAN_FILE,
    @SerializedName("missing_file") MISSING_FILE,
    @SerializedName("undeclared_import") UNDECLARED_IMPORT,
    @SerializedName("missing_contract_op") MISSING_CONTRACT_OP,
    @SerializedName("extra_contract_op") EXTRA_CONTRACT_OP,
}
