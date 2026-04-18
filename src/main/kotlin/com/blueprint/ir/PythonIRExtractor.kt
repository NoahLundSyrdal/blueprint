package com.blueprint.ir

import com.blueprint.service.PythonProjectAnalyzer
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Recovers an ArchitectureIR from a Python project by walking .py files and
 * classifying classes / dataclasses / protocols into components, contracts,
 * functions, and data types. Python's ast module is the primary parser; the
 * old regex parser is retained only as a fallback when python is unavailable.
 */
@Service(Service.Level.PROJECT)
class PythonIRExtractor(private val project: Project) {

    data class ExtractionResult(
        val ir: ArchitectureIR,
        val filesScanned: Int,
        val warnings: List<String>,
    )

    private val log = Logger.getInstance(PythonIRExtractor::class.java)

    fun extract(maxDepth: Int = 8): ExtractionResult {
        val basePath = project.basePath
        if (basePath.isNullOrBlank()) return empty("Project has no base path.")
        val base = Paths.get(basePath).normalize()
        if (!Files.isDirectory(base)) return empty("Project base path is not a directory.")

        return try {
            val context = project.service<PythonProjectAnalyzer>().analyze()
            val files = collectPythonFiles(base, context, maxDepth)
            val astParsed = PythonAstParser.parse(base, files)
            val rawParsed = if (astParsed.usedAst) {
                astParsed.symbols.map { it.toParsedClass() }
            } else {
                files.flatMap { parseFile(base, it) }
            }
            val parsed = preferGeneratedBlueprintClasses(rawParsed
                .distinctBy { "${it.relPath}:${it.name}" }
            )
                .sortedWith(compareBy<ParsedClass> { it.relPath }.thenBy { it.name })

            val classNames = parsed.map { it.name }.toSet()
            val components = mutableListOf<Component>()
            val contracts = mutableListOf<Contract>()
            val dataTypes = mutableListOf<DataType>()
            val edges = mutableListOf<Edge>()
            val warnings = mutableListOf<String>()

            if (!context.isPythonLikely()) warnings += "Python project context is sparse; IR inferred from .py files only."
            if (files.isEmpty()) warnings += "No Python files were found in the current project."
            warnings += astParsed.warnings
            if (!astParsed.usedAst) warnings += "Using legacy regex parser because AST extraction was not available."
            if (parsed.isEmpty()) warnings += "No Python classes or module functions were found. Add code or paste UML manually."

            parsed.forEach { cls ->
                val classified = classify(cls)
                components += classified.component
                classified.contract?.let { contracts += it }
                classified.dataType?.let { dataTypes += it }
            }

            edges += recoverEdges(parsed, classNames)

            val ir = ArchitectureIR(
                project = ProjectMeta(
                    name = project.name,
                    language = Language.PYTHON,
                    sourceRoots = context.sourceRoots,
                ),
                components = components,
                contracts = contracts,
                dataTypes = dataTypes,
                edges = edges.distinct(),
                coverage = RecoveryCoverage(
                    componentsRecovered = components.size,
                    componentsTotal = parsed.size,
                    contractsRecovered = contracts.size,
                ),
            )

            ExtractionResult(ir, files.size, warnings)
        } catch (t: Throwable) {
            log.warn("Failed extracting Python IR", t)
            empty("Python IR extraction failed: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    // ---------- classification ----------

    private data class Classified(
        val component: Component,
        val contract: Contract?,
        val dataType: DataType?,
    )

    private fun classify(cls: ParsedClass): Classified {
        val decoratorNames = cls.decorators.map { it.lowercase() }
        val baseSet = cls.bases.toSet()
        val allMethodsAsync = cls.methods.isNotEmpty() && cls.methods.all { it.async }
        val anyAsync = cls.methods.any { it.async }
        val concurrency = if (anyAsync) Concurrency.ASYNC else Concurrency.SYNC
        val ownership = Ownership(files = listOf(cls.relPath))
        val sourceRef = SourceRef(cls.relPath, cls.line)
        val componentId = componentIdFor(cls)

        if (cls.symbolKind == ParsedSymbolKind.FUNCTION) {
            val operation = cls.function?.toOperation()
            val kind = when {
                cls.relPath.contains("/tests/") || cls.relPath.startsWith("tests/") || cls.name.startsWith("test_") -> ComponentKind.TEST
                decoratorNames.any { it.contains("click.command") || it.contains("click.group") || it.startsWith("cli.") } -> ComponentKind.CLI
                decoratorNames.any { it.isRouteDecorator() } || cls.routePaths.isNotEmpty() -> ComponentKind.SERVICE
                cls.name.endsWith("_job") || cls.name.endsWith("_task") || cls.name.endsWith("_worker") -> ComponentKind.JOB
                else -> ComponentKind.SERVICE
            }
            val component = Component(
                id = componentId,
                name = cls.name,
                kind = kind,
                concurrency = concurrency,
                ownership = ownership,
                operations = listOfNotNull(operation),
                requires = inferRequires(cls),
                sourceRef = sourceRef,
                description = buildString {
                    append("Module function recovered from ${cls.relPath}")
                    if (cls.routePaths.isNotEmpty()) append(" routes ${cls.routePaths.joinToString(", ")}")
                },
                opaqueAnnotations = collectOpaque(cls),
                tags = setOf("function") + if (cls.routePaths.isNotEmpty()) setOf("route") else emptySet(),
            )
            return Classified(component, null, null)
        }

        // Protocol / ABC → PORT + Contract(INTERFACE).
        val isProtocol = "Protocol" in baseSet || cls.bases.any { it.endsWith(".Protocol") }
        val isAbc = "ABC" in baseSet || cls.bases.any { it.endsWith(".ABC") } ||
            cls.methods.any { "abstractmethod" in it.decorators.map { d -> d.lowercase() } }
        if (isProtocol || isAbc) {
            val contractId = "$componentId.contract"
            val contract = Contract(
                id = contractId,
                name = cls.name,
                kind = ContractKind.INTERFACE,
                concurrency = if (allMethodsAsync) Concurrency.ASYNC else concurrency,
                operations = cls.methods.filter { !it.name.startsWith("_") || it.name == "__init__" }.map { it.toOperation() },
                description = "Recovered from ${cls.relPath}",
            )
            val component = Component(
                id = componentId,
                name = cls.name,
                kind = ComponentKind.PORT,
                concurrency = concurrency,
                provides = listOf(contractId),
                requires = inferRequires(cls),
                ownership = ownership,
                operations = contract.operations,
                sourceRef = sourceRef,
                description = "Protocol/ABC recovered from ${cls.relPath}",
                opaqueAnnotations = collectOpaque(cls),
            )
            return Classified(component, contract, null)
        }

        // Dataclass / Enum / TypedDict / Pydantic → MODEL + DataType.
        val isDataclass = "dataclass" in decoratorNames
        val isPydantic = cls.bases.any { it == "BaseModel" || it.endsWith(".BaseModel") }
        val isEnum = cls.bases.any { it == "Enum" || it == "IntEnum" || it == "StrEnum" || it.endsWith(".Enum") }
        val isTypedDict = cls.bases.any { it == "TypedDict" || it.endsWith(".TypedDict") }
        if (isDataclass || isPydantic || isEnum || isTypedDict) {
            val kind = when {
                isEnum -> DataKind.ENUM
                isTypedDict -> DataKind.TYPED_DICT
                isPydantic -> DataKind.PYDANTIC
                else -> DataKind.DATACLASS
            }
            val dataTypeId = "$componentId.data"
            val dataType = DataType(
                id = dataTypeId,
                name = cls.name,
                kind = kind,
                fields = cls.fields.map { (n, t) -> Field(n, parseType(t)) },
                sourceRef = sourceRef,
            )
            val component = Component(
                id = componentId,
                name = cls.name,
                kind = ComponentKind.MODEL,
                concurrency = Concurrency.SYNC,
                ownership = ownership,
                fields = dataType.fields,
                sourceRef = sourceRef,
                description = "$kind recovered from ${cls.relPath}",
                opaqueAnnotations = collectOpaque(cls),
                tags = setOf(kind.name.lowercase()),
            )
            return Classified(component, null, dataType)
        }

        // Heuristic kind by name / decorators / location.
        val kind = when {
            cls.relPath.contains("/tests/") || cls.relPath.startsWith("tests/") || cls.name.startsWith("Test") -> ComponentKind.TEST
            decoratorNames.any { it.contains("click.command") || it.contains("click.group") || it.startsWith("cli.") } -> ComponentKind.CLI
            decoratorNames.any { it.isRouteDecorator() } || cls.routePaths.isNotEmpty() -> ComponentKind.SERVICE
            cls.name.endsWith("Registry") || cls.methods.any { it.name == "register" || it.name == "deregister" } -> ComponentKind.REGISTRY
            cls.name.endsWith("Adapter") || cls.name.endsWith("Client") ||
                cls.name.endsWith("Repository") || cls.name.endsWith("Gateway") ||
                cls.name.endsWith("DAO") -> ComponentKind.ADAPTER
            cls.name.endsWith("Service") || cls.name.endsWith("Manager") ||
                cls.name.endsWith("Controller") || cls.name.endsWith("Handler") ||
                cls.name.endsWith("UseCase") -> ComponentKind.SERVICE
            cls.name.endsWith("Job") || cls.name.endsWith("Task") || cls.name.endsWith("Worker") -> ComponentKind.JOB
            else -> ComponentKind.SERVICE
        }

        val operations = cls.methods
            .filter { !it.name.startsWith("_") || it.name == "__init__" }
            .map { it.toOperation() }

        val component = Component(
            id = componentId,
            name = cls.name,
            kind = kind,
            concurrency = concurrency,
            ownership = ownership,
            operations = operations,
            fields = cls.fields.map { (n, t) -> Field(n, parseType(t)) },
            requires = inferRequires(cls),
            sourceRef = sourceRef,
            description = "Recovered from ${cls.relPath}",
            opaqueAnnotations = collectOpaque(cls),
        )
        return Classified(component, null, null)
    }

    private fun inferRequires(cls: ParsedClass): List<String> {
        val init = cls.methods.firstOrNull { it.name == "__init__" } ?: cls.function ?: return emptyList()
        return init.params
            .filter { it.name != "self" }
            .mapNotNull { p ->
                val root = p.type.substringBefore("[").substringAfterLast(".").trim()
                root.takeIf { it.matches(Regex("""[A-Z][A-Za-z0-9_]*""")) }
            }
            .distinct()
    }

    private fun collectOpaque(cls: ParsedClass): List<OpaqueAnnotation> {
        val out = mutableListOf<OpaqueAnnotation>()
        cls.decorators
            .filter { it !in SEMANTIC_DECORATORS }
            .forEach { dec ->
                out += OpaqueAnnotation(
                    symbol = "@$dec",
                    sourceRef = SourceRef(cls.relPath, cls.line),
                    reason = "Unrecognized class decorator",
                )
            }
        cls.methods.forEach { m ->
            m.decorators.filter { it !in SEMANTIC_DECORATORS && it !in POLICY_DECORATORS && it !in KNOWN_METHOD_DECORATORS }
                .forEach { dec ->
                    out += OpaqueAnnotation(
                        symbol = "@$dec on ${cls.name}.${m.name}",
                        sourceRef = SourceRef(cls.relPath, m.line),
                        reason = "Unrecognized method decorator",
                    )
                }
        }
        return out
    }

    // ---------- parsing ----------

    private data class ParsedClass(
        val name: String,
        val symbolKind: ParsedSymbolKind = ParsedSymbolKind.CLASS,
        val bases: List<String>,
        val relPath: String,
        val line: Int,
        val decorators: List<String>,
        val fields: LinkedHashMap<String, String> = linkedMapOf(),
        val methods: MutableList<ParsedMethod> = mutableListOf(),
        val function: ParsedMethod? = null,
        val imports: Set<String> = emptySet(),
        val calls: Set<String> = emptySet(),
        val references: Set<String> = emptySet(),
        val routePaths: Set<String> = emptySet(),
    )

    private enum class ParsedSymbolKind {
        CLASS,
        FUNCTION,
    }

    private fun preferGeneratedBlueprintClasses(classes: List<ParsedClass>): List<ParsedClass> {
        val generatedClassNames = classes
            .filter { it.isBlueprintGenerated() }
            .map { it.name }
            .toSet()
        if (generatedClassNames.isEmpty()) return classes
        return classes.filter { it.isBlueprintGenerated() || it.name !in generatedClassNames }
    }

    private fun ParsedClass.isBlueprintGenerated(): Boolean =
        relPath.replace('\\', '/').startsWith("blueprint_demo/")

    private data class ParsedMethod(
        val name: String,
        val async: Boolean,
        val params: List<ParsedParam>,
        val returns: String,
        val decorators: List<String>,
        val line: Int,
    ) {
        fun toOperation(): Operation = Operation(
            name = name,
            params = params.filter { it.name != "self" && it.name != "cls" }.map { Param(it.name, parseType(it.type), it.default) },
            returns = parseType(returns),
            concurrency = if (async) Concurrency.ASYNC else Concurrency.SYNC,
            patterns = decorators.filter { it in POLICY_DECORATORS || it in SEMANTIC_DECORATORS }.toSet(),
        )
    }

    private data class ParsedParam(val name: String, val type: String, val default: String?)

    private fun parseFile(base: Path, file: Path): List<ParsedClass> {
        val rel = base.relativize(file).toString().replace('\\', '/')
        val text = readSmall(file)
        if (text.isBlank()) return emptyList()

        val lines = text.lines()
        val classes = mutableListOf<ParsedClass>()
        var current: ParsedClass? = null
        var classIndent = 0
        var pendingClassDecorators = mutableListOf<String>()
        var pendingMethodDecorators = mutableListOf<String>()

        fun closeCurrent() {
            current?.let { classes += it }
            current = null
        }

        for ((idx, rawLine) in lines.withIndex()) {
            val trimmed = rawLine.trim()

            if (trimmed.startsWith("@")) {
                val decoratorName = trimmed.removePrefix("@").substringBefore("(").trim()
                val indent = indentOf(rawLine.takeWhile { it == ' ' || it == '\t' })
                val cls = current
                if (cls == null || indent <= classIndent) {
                    pendingClassDecorators += decoratorName
                } else {
                    pendingMethodDecorators += decoratorName
                }
                continue
            }

            val header = CLASS_HEADER.matchEntire(rawLine)
            if (header != null) {
                closeCurrent()
                classIndent = indentOf(header.groupValues[1])
                current = ParsedClass(
                    name = header.groupValues[2],
                    symbolKind = ParsedSymbolKind.CLASS,
                    bases = parseBases(header.groupValues.getOrElse(3) { "" }),
                    relPath = rel,
                    line = idx + 1,
                    decorators = pendingClassDecorators.toList(),
                )
                pendingClassDecorators.clear()
                pendingMethodDecorators.clear()
                continue
            }

            val cls = current
            if (cls == null) {
                pendingClassDecorators.clear()
                continue
            }
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue

            val indent = indentOf(rawLine.takeWhile { it == ' ' || it == '\t' })
            if (indent <= classIndent) {
                closeCurrent()
                pendingMethodDecorators.clear()
                continue
            }

            val methodMatch = METHOD_REGEX.find(rawLine)
            if (methodMatch != null) {
                val async = methodMatch.groupValues[1].isNotBlank()
                val name = methodMatch.groupValues[2]
                val params = parseParams(methodMatch.groupValues.getOrElse(3) { "" })
                val returns = methodMatch.groupValues.getOrElse(4) { "" }.trim()
                cls.methods += ParsedMethod(
                    name = name,
                    async = async,
                    params = params,
                    returns = returns.ifBlank { "None" },
                    decorators = pendingMethodDecorators.toList(),
                    line = idx + 1,
                )
                pendingMethodDecorators.clear()
                continue
            }

            SELF_ANNOTATED.find(rawLine)?.let { match ->
                cls.addField(match.groupValues[1], cleanType(match.groupValues[2]))
            }
            SELF_ASSIGNED.find(rawLine)?.let { match ->
                cls.addField(match.groupValues[1], inferTypeFromValue(match.groupValues[2]))
            }
            if (indent == classIndent + 4) {
                val annotated = CLASS_ANNOTATED.matchEntire(rawLine)
                if (annotated != null) {
                    cls.addField(annotated.groupValues[1], cleanType(annotated.groupValues[2]))
                    pendingMethodDecorators.clear()
                } else {
                    CLASS_ASSIGNED.matchEntire(rawLine)?.let { match ->
                        cls.addField(match.groupValues[1], inferTypeFromValue(match.groupValues[2]))
                    }
                }
            }
        }
        closeCurrent()
        return classes
    }

    private fun ParsedClass.addField(name: String, type: String) {
        val field = name.trim()
        if (field.isBlank() || field.startsWith("_") || field in IGNORED_FIELDS) return
        fields.putIfAbsent(field, type.ifBlank { "Any" })
    }

    private fun parseBases(raw: String): List<String> =
        raw.split(',')
            .map { it.substringBefore("[").trim() }
            .filter { it.isNotBlank() }
            .map { it.substringAfterLast(".") }
            .filter { it.matches(Regex("""[A-Z][A-Za-z0-9_]*""")) }
            .distinct()

    private fun parseParams(raw: String): List<ParsedParam> {
        if (raw.isBlank()) return emptyList()
        return splitTopLevel(raw).mapNotNull { rawParam ->
            val piece = rawParam.trim().trimStart('*')
            if (piece.isBlank()) return@mapNotNull null
            val (head, default) = if ("=" in piece) piece.substringBefore("=").trim() to piece.substringAfter("=").trim() else piece to null
            val (name, type) = if (":" in head) head.substringBefore(":").trim() to head.substringAfter(":").trim() else head to "Any"
            ParsedParam(name, type, default)
        }
    }

    private fun splitTopLevel(raw: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        val buf = StringBuilder()
        for (c in raw) {
            when (c) {
                '[', '(', '{' -> { depth++; buf.append(c) }
                ']', ')', '}' -> { depth--; buf.append(c) }
                ',' -> if (depth == 0) { out += buf.toString(); buf.setLength(0) } else buf.append(c)
                else -> buf.append(c)
            }
        }
        if (buf.isNotEmpty()) out += buf.toString()
        return out
    }

    private fun cleanType(raw: String): String =
        raw.substringBefore("=").substringBefore("#").trim().ifBlank { "Any" }

    private fun inferTypeFromValue(raw: String): String {
        val value = raw.substringBefore("#").trim()
        val constructor = Regex("""^([A-Z][A-Za-z0-9_]*)\s*\(""").find(value)?.groupValues?.getOrNull(1)
        if (constructor != null) return constructor
        return when {
            value.startsWith("\"") || value.startsWith("'") -> "str"
            value == "True" || value == "False" -> "bool"
            value.startsWith("[") -> "list"
            value.startsWith("{") -> "dict"
            value.matches(Regex("""-?\d+""")) -> "int"
            value.matches(Regex("""-?\d+\.\d+""")) -> "float"
            else -> "Any"
        }
    }

    private fun String.referencesClass(className: String): Boolean =
        Regex("""\b${Regex.escape(className)}\b""").containsMatchIn(this)

    private fun PythonAstParser.Symbol.toParsedClass(): ParsedClass {
        val parsedMethods = methods.map { it.toParsedMethod() }.toMutableList()
        val functionMethod = parsedMethods.firstOrNull()
        return ParsedClass(
            name = name,
            symbolKind = if (symbolKind == "FUNCTION") ParsedSymbolKind.FUNCTION else ParsedSymbolKind.CLASS,
            bases = bases.map { it.substringBefore("[").substringAfterLast(".") }.filter { it.isNotBlank() }.distinct(),
            relPath = relPath,
            line = line,
            decorators = decorators.filter { it.isNotBlank() },
            fields = fields.fold(linkedMapOf()) { acc, field ->
                if (field.name.isNotBlank() && !field.name.startsWith("_") && field.name !in IGNORED_FIELDS) {
                    acc.putIfAbsent(field.name, field.type.ifBlank { "Any" })
                }
                acc
            },
            methods = if (symbolKind == "FUNCTION") mutableListOf() else parsedMethods,
            function = if (symbolKind == "FUNCTION") functionMethod else null,
            imports = imports.filter { it.isNotBlank() }.toSet(),
            calls = calls.filter { it.isNotBlank() }.toSet(),
            references = references.filter { it.isNotBlank() }.toSet(),
            routePaths = routePaths.filter { it.isNotBlank() }.toSet(),
        )
    }

    private fun PythonAstParser.MethodDecl.toParsedMethod(): ParsedMethod =
        ParsedMethod(
            name = name,
            async = async,
            params = params.map { ParsedParam(it.name, it.type, it.default) },
            returns = returns.ifBlank { "None" },
            decorators = decorators,
            line = line,
        )

    private fun recoverEdges(parsed: List<ParsedClass>, symbolNames: Set<String>): List<Edge> {
        val edges = mutableListOf<Edge>()

        fun targetId(name: String): String? {
            val simple = name.substringBefore("[").substringAfterLast(".").trim()
            if (simple.isBlank() || simple !in symbolNames) return null
            val target = parsed.firstOrNull { it.name == simple } ?: return null
            return componentIdFor(target)
        }

        fun addEdge(
            source: ParsedClass,
            targetName: String,
            kind: EdgeKind,
            label: String,
            evidence: String,
            confidence: Double,
        ) {
            val target = targetId(targetName) ?: return
            val from = componentIdFor(source)
            if (target == from) return
            edges += Edge(
                from = from,
                to = target,
                toKind = EdgeTargetKind.COMPONENT,
                kind = kind,
                label = label,
                sourceRef = SourceRef(source.relPath, source.line),
                evidence = evidence,
                confidence = confidence,
            )
        }

        parsed.forEach { cls ->
            cls.bases.forEach { base ->
                addEdge(cls, base, EdgeKind.EXTENDS, "extends", "base class: $base", 1.0)
            }

            cls.fields.forEach { (fieldName, type) ->
                symbolNames
                    .filter { other -> other != cls.name && type.referencesClass(other) }
                    .forEach { other ->
                        val kind = if (fieldName.endsWith("s", ignoreCase = true)) EdgeKind.CONTAINS else EdgeKind.REFERENCES
                        addEdge(cls, other, kind, "$fieldName field", "field annotation/value: $fieldName -> $type", 0.95)
                    }
            }

            val callable = cls.methods.firstOrNull { it.name == "__init__" } ?: cls.function
            callable?.params.orEmpty()
                .filter { it.name != "self" && it.name != "cls" }
                .forEach { param ->
                    addEdge(
                        cls,
                        param.type,
                        EdgeKind.REFERENCES,
                        "${param.name} constructor param",
                        "typed parameter: ${param.name}: ${param.type}",
                        0.9,
                    )
                }

            cls.imports.forEach { imported ->
                addEdge(cls, imported, EdgeKind.REFERENCES, "import", "imported symbol: $imported", 0.65)
            }

            cls.calls.forEach { call ->
                val label = if (cls.isTestSymbol()) "tests" else "calls"
                addEdge(cls, call, EdgeKind.CALLS, label, "call expression: $call", 0.8)
            }

            cls.references.forEach { ref ->
                if (ref.firstOrNull()?.isUpperCase() == true) {
                    addEdge(cls, ref, EdgeKind.REFERENCES, "references", "name reference: $ref", 0.6)
                }
            }
        }

        return edges
            .groupBy { listOf(it.from, it.to, it.kind.name, it.label).joinToString("|") }
            .values
            .map { it.first() }
    }

    private fun ParsedClass.isTestSymbol(): Boolean =
        relPath.startsWith("tests/") || relPath.contains("/tests/") || name.startsWith("Test") || name.startsWith("test_")

    private fun String.isRouteDecorator(): Boolean =
        contains(".route") ||
            endsWith(".get") ||
            endsWith(".post") ||
            endsWith(".put") ||
            endsWith(".delete") ||
            endsWith(".patch")

    // ---------- filesystem ----------

    private fun collectPythonFiles(
        base: Path,
        context: PythonProjectAnalyzer.PythonProjectContext,
        maxDepth: Int,
    ): List<Path> {
        val roots = context.sourceRoots.ifEmpty { listOf("") }
        val files = roots.flatMap { rel ->
            val root = if (rel.isBlank()) base else base.resolve(rel).normalize()
            if (!Files.isDirectory(root) || !root.startsWith(base)) emptyList()
            else Files.walk(root, maxDepth).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { it.fileName.toString().endsWith(".py") }
                    .filter { path -> !base.relativize(path).any { part -> shouldSkipDir(part.toString()) } }
                    .filter { path -> !isLikelyGenerated(path.fileName.toString()) }
                    .toList()
            }
        } + collectBlueprintGeneratedPythonFiles(base, maxDepth)
        val unique = files.distinct().sorted()
        return if (unique.isNotEmpty()) unique else fallback(base, maxDepth)
    }

    private fun collectBlueprintGeneratedPythonFiles(base: Path, maxDepth: Int): List<Path> {
        val generatedRoot = base.resolve("blueprint_demo").normalize()
        if (!Files.isDirectory(generatedRoot) || !generatedRoot.startsWith(base)) return emptyList()
        Files.walk(generatedRoot, maxDepth).use { stream ->
            return stream.filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".py") }
                .filter { path -> !base.relativize(path).any { part -> shouldSkipDir(part.toString()) } }
                .filter { path -> !isLikelyGenerated(path.fileName.toString()) }
                .toList()
        }
    }

    private fun fallback(base: Path, maxDepth: Int): List<Path> =
        Files.walk(base, maxDepth).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".py") }
                .filter { path -> !base.relativize(path).any { part -> shouldSkipDir(part.toString()) } }
                .filter { path -> !isLikelyGenerated(path.fileName.toString()) }
                .toList().distinct().sorted()
        }

    private fun readSmall(path: Path, maxChars: Int = 500_000): String =
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > 1_000_000L) ""
            else Files.readString(path, StandardCharsets.UTF_8).take(maxChars)
        } catch (_: Throwable) {
            ""
        }

    private fun indentOf(prefix: String): Int =
        prefix.fold(0) { acc, char -> acc + if (char == '\t') 4 else 1 }

    private fun shouldSkipDir(name: String): Boolean =
        name in SKIPPED_DIRECTORIES || name.startsWith(".")

    private fun isLikelyGenerated(fileName: String): Boolean =
        fileName.endsWith("_pb2.py") || fileName.endsWith("_pb2_grpc.py")

    private fun empty(warning: String): ExtractionResult =
        ExtractionResult(
            ir = ArchitectureIR(project = ProjectMeta(name = project.name)),
            filesScanned = 0,
            warnings = listOf(warning),
        )

    // ---------- helpers ----------

    private fun componentIdFor(cls: ParsedClass): String = componentIdFor(cls.name, cls.relPath)
    private fun componentIdFor(name: String, all: List<ParsedClass>): String {
        val match = all.firstOrNull { it.name == name }
        return if (match != null) componentIdFor(match) else name.lowercase()
    }
    private fun componentIdFor(name: String, relPath: String): String {
        val module = relPath.removeSuffix(".py").replace('/', '.').trim('.')
        return if (module.isBlank()) name.lowercase() else "$module.${name.lowercase()}"
    }

    companion object {
        private val CLASS_HEADER = Regex("""^(\s*)class\s+([A-Z][A-Za-z0-9_]*)\s*(?:\(([^)]*)\))?\s*:\s*$""")
        private val METHOD_REGEX = Regex("""^\s*(async\s+)?def\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*(?:->\s*([^:]+))?\s*:""")
        private val SELF_ANNOTATED = Regex("""self\.([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^=#]+)""")
        private val SELF_ASSIGNED = Regex("""self\.([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([^#]+)""")
        private val CLASS_ANNOTATED = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^=#]+).*$""")
        private val CLASS_ASSIGNED = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([^#]+).*$""")

        private val IGNORED_FIELDS = setOf("Config", "Meta", "model_config", "objects")
        private val SKIPPED_DIRECTORIES = setOf(
            ".git", ".gradle", ".idea", ".mypy_cache", ".pytest_cache", ".ruff_cache", ".tox", ".venv",
            "__pycache__", "build", "dist", "node_modules", "site-packages", "venv",
        )

        private val SEMANTIC_DECORATORS = setOf(
            "dataclass", "dataclasses.dataclass",
            "property", "staticmethod", "classmethod",
            "click.command", "click.group", "click.option", "click.argument",
            "app.route", "router.get", "router.post", "router.put", "router.delete", "router.patch",
            "task", "celery.task", "shared_task",
            "cached_property",
        )
        private val POLICY_DECORATORS = setOf(
            "retry", "tenacity.retry", "traced", "tracer", "authenticated", "login_required",
            "cache", "lru_cache", "functools.lru_cache", "functools.cache",
        )
        private val KNOWN_METHOD_DECORATORS = setOf(
            "abstractmethod", "abc.abstractmethod",
            "override", "typing.override",
        )
    }
}

private fun parseType(raw: String): TypeRef {
    val trimmed = raw.trim().trimEnd(',').trim().ifBlank { return TypeRef("Any") }
    val opening = trimmed.indexOf('[')
    val closing = trimmed.lastIndexOf(']')
    if (opening > 0 && closing > opening) {
        val head = trimmed.substring(0, opening).substringAfterLast(".").trim()
        val inner = trimmed.substring(opening + 1, closing)
        val parts = splitTopLevelTypes(inner).map { parseType(it) }
        val optional = head == "Optional"
        return if (optional && parts.size == 1) parts[0].copy(optional = true)
        else TypeRef(head, parts)
    }
    val base = trimmed.substringAfterLast(".").trim()
    return TypeRef(base.ifBlank { "Any" })
}

private fun splitTopLevelTypes(raw: String): List<String> {
    val out = mutableListOf<String>()
    var depth = 0
    val buf = StringBuilder()
    for (c in raw) {
        when (c) {
            '[' -> { depth++; buf.append(c) }
            ']' -> { depth--; buf.append(c) }
            ',' -> if (depth == 0) { out += buf.toString(); buf.setLength(0) } else buf.append(c)
            else -> buf.append(c)
        }
    }
    if (buf.isNotEmpty()) out += buf.toString()
    return out
}
