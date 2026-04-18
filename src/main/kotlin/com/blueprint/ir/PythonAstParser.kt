package com.blueprint.ir

import com.google.gson.Gson
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Uses Python's built-in ast module as Blueprint's structural Python parser.
 *
 * This avoids depending on a particular JetBrains Python plugin while still
 * parsing real Python syntax: multiline signatures, decorators, imports,
 * annotations, module functions, and method bodies.
 */
object PythonAstParser {
    private val gson = Gson()

    data class ParseResult(
        val symbols: List<Symbol>,
        val warnings: List<String>,
        val usedAst: Boolean,
    )

    data class Symbol(
        val name: String,
        val symbolKind: String,
        val bases: List<String>,
        val relPath: String,
        val line: Int,
        val decorators: List<String>,
        val fields: List<FieldDecl>,
        val methods: List<MethodDecl>,
        val imports: List<String>,
        val calls: List<String>,
        val references: List<String>,
        val routePaths: List<String>,
    )

    data class FieldDecl(val name: String, val type: String)

    data class MethodDecl(
        val name: String,
        val async: Boolean,
        val params: List<ParamDecl>,
        val returns: String,
        val decorators: List<String>,
        val line: Int,
    )

    data class ParamDecl(val name: String, val type: String, val default: String?)

    fun parse(base: Path, files: List<Path>, timeoutSeconds: Long = 15): ParseResult {
        if (files.isEmpty()) return ParseResult(emptyList(), emptyList(), usedAst = true)
        val python = findPythonExecutable()
            ?: return ParseResult(emptyList(), listOf("python3 was not found; falling back to legacy Python parser."), usedAst = false)

        val request = AstRequest(
            base = base.toAbsolutePath().normalize().toString(),
            files = files.map { it.toAbsolutePath().normalize().toString() },
        )
        return try {
            val process = ProcessBuilder(python, "-c", AST_SCRIPT)
                .redirectErrorStream(false)
                .start()
            OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8).use { writer ->
                gson.toJson(request, writer)
            }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ParseResult(emptyList(), listOf("Python AST parser timed out; falling back to legacy parser."), usedAst = false)
            }
            val stdout = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            val stderr = process.errorStream.readBytes().toString(StandardCharsets.UTF_8).trim()
            if (process.exitValue() != 0) {
                val detail = stderr.ifBlank { "exit ${process.exitValue()}" }
                return ParseResult(emptyList(), listOf("Python AST parser failed: $detail"), usedAst = false)
            }
            val output = gson.fromJson(stdout, AstOutput::class.java)
            ParseResult(
                symbols = output.symbols.map { it.toSymbol() },
                warnings = output.warnings + stderr.takeIf { it.isNotBlank() }.orEmpty().let { if (it.isBlank()) emptyList() else listOf(it) },
                usedAst = true,
            )
        } catch (t: Throwable) {
            ParseResult(emptyList(), listOf("Python AST parser failed: ${t.message ?: t.javaClass.simpleName}"), usedAst = false)
        }
    }

    private fun findPythonExecutable(): String? {
        val candidates = listOf("python3", "python")
        return candidates.firstOrNull { exe ->
            try {
                val process = ProcessBuilder(exe, "--version").redirectErrorStream(true).start()
                process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0
            } catch (_: Throwable) {
                false
            }
        }
    }

    private data class AstRequest(val base: String, val files: List<String>)

    private data class AstOutput(
        val symbols: List<AstSymbol> = emptyList(),
        val warnings: List<String> = emptyList(),
    )

    private data class AstSymbol(
        val name: String = "",
        val symbolKind: String = "CLASS",
        val bases: List<String> = emptyList(),
        val relPath: String = "",
        val line: Int = 1,
        val decorators: List<String> = emptyList(),
        val fields: List<AstField> = emptyList(),
        val methods: List<AstMethod> = emptyList(),
        val imports: List<String> = emptyList(),
        val calls: List<String> = emptyList(),
        val references: List<String> = emptyList(),
        val routePaths: List<String> = emptyList(),
    ) {
        fun toSymbol(): Symbol = Symbol(
            name = name,
            symbolKind = symbolKind,
            bases = bases,
            relPath = relPath,
            line = line,
            decorators = decorators,
            fields = fields.map { FieldDecl(it.name, it.type) },
            methods = methods.map { it.toMethod() },
            imports = imports,
            calls = calls,
            references = references,
            routePaths = routePaths,
        )
    }

    private data class AstField(val name: String = "", val type: String = "Any")

    private data class AstMethod(
        val name: String = "",
        val async: Boolean = false,
        val params: List<AstParam> = emptyList(),
        val returns: String = "None",
        val decorators: List<String> = emptyList(),
        val line: Int = 1,
    ) {
        fun toMethod(): MethodDecl = MethodDecl(
            name = name,
            async = async,
            params = params.map { ParamDecl(it.name, it.type, it.default) },
            returns = returns,
            decorators = decorators,
            line = line,
        )
    }

    private data class AstParam(
        val name: String = "",
        val type: String = "Any",
        val default: String? = null,
    )

    private const val AST_SCRIPT = """
import ast
import json
import os
import sys

req = json.load(sys.stdin)
base = os.path.abspath(req.get("base", "."))
files = req.get("files", [])
warnings = []
symbols = []

def relpath(path):
    return os.path.relpath(path, base).replace(os.sep, "/")

def unparse(node, default="Any"):
    if node is None:
        return default
    try:
        return ast.unparse(node).strip()
    except Exception:
        return default

def dotted(node):
    if node is None:
        return ""
    if isinstance(node, ast.Name):
        return node.id
    if isinstance(node, ast.Attribute):
        left = dotted(node.value)
        return (left + "." if left else "") + node.attr
    if isinstance(node, ast.Call):
        return dotted(node.func)
    if isinstance(node, ast.Subscript):
        return dotted(node.value)
    return unparse(node, "")

def decorator_name(node):
    return dotted(node) or unparse(node, "")

def infer_type(value):
    if isinstance(value, ast.Call):
        name = dotted(value.func)
        return name.split(".")[-1] if name else "Any"
    if isinstance(value, ast.Constant):
        if isinstance(value.value, str):
            return "str"
        if isinstance(value.value, bool):
            return "bool"
        if isinstance(value.value, int):
            return "int"
        if isinstance(value.value, float):
            return "float"
        if value.value is None:
            return "None"
    if isinstance(value, ast.List):
        return "list"
    if isinstance(value, ast.Dict):
        return "dict"
    if isinstance(value, ast.Set):
        return "set"
    if isinstance(value, ast.Tuple):
        return "tuple"
    return "Any"

def default_values(args):
    pos = list(args.posonlyargs) + list(args.args)
    defaults = [None] * (len(pos) - len(args.defaults)) + list(args.defaults)
    pairs = list(zip(pos, defaults))
    pairs += [(arg, None) for arg in args.kwonlyargs]
    return pairs

def parse_params(fn):
    out = []
    for arg, default in default_values(fn.args):
        out.append({
            "name": arg.arg,
            "type": unparse(arg.annotation, "Any"),
            "default": unparse(default, None) if default is not None else None,
        })
    if fn.args.vararg is not None:
        out.append({"name": "*" + fn.args.vararg.arg, "type": unparse(fn.args.vararg.annotation, "Any"), "default": None})
    if fn.args.kwarg is not None:
        out.append({"name": "**" + fn.args.kwarg.arg, "type": unparse(fn.args.kwarg.annotation, "Any"), "default": None})
    return out

def parse_method(fn):
    return {
        "name": fn.name,
        "async": isinstance(fn, ast.AsyncFunctionDef),
        "params": parse_params(fn),
        "returns": unparse(fn.returns, "None"),
        "decorators": [decorator_name(d) for d in fn.decorator_list],
        "line": getattr(fn, "lineno", 1),
    }

def imported_symbols(tree):
    names = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                names.add(alias.asname or alias.name.split(".")[0])
                names.add(alias.name)
        elif isinstance(node, ast.ImportFrom):
            module = node.module or ""
            for alias in node.names:
                local = alias.asname or alias.name
                names.add(local)
                names.add((module + "." + alias.name).strip("."))
    return sorted(n for n in names if n)

def collect_calls_and_refs(node):
    calls = set()
    refs = set()
    for child in ast.walk(node):
        if isinstance(child, ast.Call):
            name = dotted(child.func)
            if name:
                calls.add(name)
                calls.add(name.split(".")[-1])
        elif isinstance(child, ast.Name):
            refs.add(child.id)
        elif isinstance(child, ast.Attribute):
            refs.add(child.attr)
            full = dotted(child)
            if full:
                refs.add(full)
    return sorted(calls), sorted(refs)

def field_put(fields, name, type_name):
    if not name or name.startswith("_") or name in fields:
        return
    fields[name] = type_name or "Any"

def parse_class(cls, path, file_imports):
    fields = {}
    methods = []
    route_paths = set()
    class_calls, class_refs = collect_calls_and_refs(cls)
    for item in cls.body:
        if isinstance(item, ast.AnnAssign) and isinstance(item.target, ast.Name):
            field_put(fields, item.target.id, unparse(item.annotation, "Any"))
        elif isinstance(item, ast.Assign):
            for target in item.targets:
                if isinstance(target, ast.Name):
                    field_put(fields, target.id, infer_type(item.value))
        elif isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef)):
            methods.append(parse_method(item))
            for dec in item.decorator_list:
                name = decorator_name(dec)
                if ".route" in name or name.endswith((".get", ".post", ".put", ".delete", ".patch")):
                    route_paths.add(unparse(dec.args[0], "") if isinstance(dec, ast.Call) and dec.args else name)
            for child in ast.walk(item):
                if isinstance(child, ast.AnnAssign) and isinstance(child.target, ast.Attribute):
                    if isinstance(child.target.value, ast.Name) and child.target.value.id == "self":
                        field_put(fields, child.target.attr, unparse(child.annotation, "Any"))
                elif isinstance(child, ast.Assign):
                    for target in child.targets:
                        if isinstance(target, ast.Attribute) and isinstance(target.value, ast.Name) and target.value.id == "self":
                            field_put(fields, target.attr, infer_type(child.value))
    return {
        "name": cls.name,
        "symbolKind": "CLASS",
        "bases": [unparse(base, "") for base in cls.bases],
        "relPath": relpath(path),
        "line": getattr(cls, "lineno", 1),
        "decorators": [decorator_name(d) for d in cls.decorator_list],
        "fields": [{"name": k, "type": v} for k, v in fields.items()],
        "methods": methods,
        "imports": file_imports,
        "calls": class_calls,
        "references": class_refs,
        "routePaths": sorted(route_paths),
    }

def parse_function(fn, path, file_imports):
    calls, refs = collect_calls_and_refs(fn)
    route_paths = []
    decorators = [decorator_name(d) for d in fn.decorator_list]
    for dec in fn.decorator_list:
        name = decorator_name(dec)
        if ".route" in name or name.endswith((".get", ".post", ".put", ".delete", ".patch")):
            route_paths.append(unparse(dec.args[0], "") if isinstance(dec, ast.Call) and dec.args else name)
    return {
        "name": fn.name,
        "symbolKind": "FUNCTION",
        "bases": [],
        "relPath": relpath(path),
        "line": getattr(fn, "lineno", 1),
        "decorators": decorators,
        "fields": [],
        "methods": [parse_method(fn)],
        "imports": file_imports,
        "calls": calls,
        "references": refs,
        "routePaths": route_paths,
    }

for path in files:
    try:
        with open(path, "r", encoding="utf-8") as f:
            text = f.read()
        tree = ast.parse(text, filename=path)
    except SyntaxError as exc:
        warnings.append(f"{relpath(path)}:{exc.lineno}: syntax error: {exc.msg}")
        continue
    except Exception as exc:
        warnings.append(f"{relpath(path)}: could not parse: {exc}")
        continue
    file_imports = imported_symbols(tree)
    for item in tree.body:
        if isinstance(item, ast.ClassDef):
            symbols.append(parse_class(item, path, file_imports))
        elif isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef)):
            symbols.append(parse_function(item, path, file_imports))

json.dump({"symbols": symbols, "warnings": warnings}, sys.stdout)
"""
}
