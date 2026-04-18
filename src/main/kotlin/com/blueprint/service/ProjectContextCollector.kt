package com.blueprint.service

import com.blueprint.model.BlueprintNode
import com.blueprint.model.FileScope
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Small V1 context collector: gathers bounded file excerpts from the node's
 * file scope so the prompts can operate on evidence instead of project guesses.
 */
@Service(Service.Level.PROJECT)
class ProjectContextCollector(private val project: Project) {

    private val log = Logger.getInstance(ProjectContextCollector::class.java)

    fun collectRelevantFiles(
        node: BlueprintNode,
        maxFiles: Int = 16,
        maxTotalChars: Int = 80_000,
    ): String {
        val basePath = project.basePath ?: return ""
        val base = Paths.get(basePath).normalize()
        val candidates = collectCandidates(base, node).distinct().take(maxFiles)
        if (candidates.isEmpty()) return ""

        val out = StringBuilder()
        var remaining = maxTotalChars
        for (file in candidates) {
            if (remaining <= 0) break
            try {
                if (!Files.isRegularFile(file)) continue
                val rel = base.relativize(file).toString().replace('\\', '/')
                if (!node.fileScope.isEmpty() && !node.fileScope.allows(rel)) continue
                if (Files.size(file) > 60_000L || isProbablyBinary(file)) continue
                val content = Files.readString(file, StandardCharsets.UTF_8)
                val clipped = content.take(remaining)
                out.append("\n--- FILE: ").append(rel).append(" ---\n")
                out.append(clipped)
                if (clipped.length < content.length) out.append("\n... [truncated]\n")
                remaining -= clipped.length
            } catch (t: Throwable) {
                log.warn("Failed collecting context for $file", t)
            }
        }
        return out.toString().trim()
    }

    private fun collectCandidates(base: Path, node: BlueprintNode): List<Path> {
        if (node.fileScope.isEmpty()) return emptyList()
        val exact = node.fileScope.paths
            .mapNotNull { FileScope.normalizeRelativePath(it) }
            .map { base.resolve(it).normalize() }
            .filter { it.startsWith(base) && Files.exists(it) }

        val fromDirectories = node.fileScope.directories
            .mapNotNull { FileScope.normalizeRelativePath(it) }
            .flatMap { rel -> walkFiles(base, base.resolve(rel).normalize(), node, 8) }

        val fromGlobs = if (node.fileScope.globs.isEmpty()) {
            emptyList()
        } else {
            walkFiles(base, base, node, 16)
        }

        return exact + fromDirectories + fromGlobs
    }

    private fun walkFiles(base: Path, root: Path, node: BlueprintNode, limit: Int): List<Path> {
        if (!root.startsWith(base) || !Files.exists(root)) return emptyList()
        val files = mutableListOf<Path>()
        val stream = Files.walk(root)
        try {
            val iterator = stream.iterator()
            while (iterator.hasNext() && files.size < limit) {
                val p = iterator.next().normalize()
                if (!Files.isRegularFile(p)) continue
                val rel = base.relativize(p).toString().replace('\\', '/')
                if (shouldSkip(rel)) continue
                if (node.fileScope.allows(rel)) files.add(p)
            }
        } finally {
            stream.close()
        }
        return files
    }

    private fun shouldSkip(relPath: String): Boolean {
        val parts = relPath.split('/')
        if (parts.any { it in skippedDirectories }) return true
        return relPath.substringAfterLast('.', "").lowercase() in skippedExtensions
    }

    private fun isProbablyBinary(path: Path): Boolean {
        val ext = path.fileName.toString().substringAfterLast('.', "").lowercase()
        return ext in skippedExtensions
    }

    private companion object {
        val skippedDirectories = setOf(".git", ".gradle", "build", "out", "node_modules", ".idea")
        val skippedExtensions = setOf(
            "class", "jar", "zip", "gz", "png", "jpg", "jpeg", "gif", "webp", "ico",
            "pdf", "mp4", "mov", "mp3", "wav", "ttf", "otf", "woff", "woff2"
        )
    }
}
