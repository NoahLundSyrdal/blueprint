package com.blueprint.service

import com.blueprint.model.BlueprintNode
import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.FileScope
import com.blueprint.model.Patch
import com.blueprint.model.ReviewArtifact
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.charset.StandardCharsets

/**
 * Apply approved patches to disk, strictly inside the node's file scope.
 * Uses VFS so the IDE immediately sees the changes.
 */
@Service(Service.Level.PROJECT)
class ApplyChangesService(private val project: Project) {

    private val log = Logger.getInstance(ApplyChangesService::class.java)

    data class ApplyResult(
        val applied: List<String>,
        val skipped: List<Pair<String, String>>, // path -> reason
    )

    /** The most recent successful apply — available for one-level undo. Cleared on undo. */
    var lastUndo: UndoRecord? = null
        private set

    fun apply(
        node: BlueprintNode,
        execution: ExecutionArtifact,
        review: ReviewArtifact? = null,
    ): ApplyResult {
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<Pair<String, String>>()

        if (review != null &&
            (review.reviewStatus != "APPROVE" || review.recommendedNextAction != "apply")
        ) {
            return ApplyResult(
                emptyList(),
                execution.patches.map { it.path to "review did not approve changes" }
            )
        }

        val basePath = project.basePath
            ?: return ApplyResult(emptyList(), execution.patches.map { it.path to "No project base path" })
        val base = Paths.get(basePath).normalize()
        val undoEntries = mutableListOf<UndoEntry>()

        WriteCommandAction.runWriteCommandAction(project) {
            for (patch in execution.patches) {
                val rel = FileScope.normalizeRelativePath(patch.path)
                if (rel == null) {
                    skipped += patch.path to "invalid relative path"
                    continue
                }
                val target = base.resolve(rel).normalize()
                if (!target.startsWith(base)) {
                    skipped += rel to "path escapes project root"
                    continue
                }
                if (!node.fileScope.isEmpty() && !node.fileScope.allows(rel)) {
                    skipped += rel to "outside file scope"
                    continue
                }
                try {
                    val before = snapshot(base, rel)
                    val preflight = PatchFreshness.verify(patch, before, before)
                    if (preflight.matchesPatch && !preflight.changedDisk) {
                        skipped += rel to preflight.reason
                        continue
                    }
                    applyOne(basePath, rel, patch)
                    val after = snapshot(base, rel)
                    val verification = PatchFreshness.verify(patch, before, after)
                    if (verification.matchesPatch && verification.changedDisk) {
                        applied += rel
                        undoEntries += UndoEntry(
                            path = rel,
                            before = before,
                            patchAction = patch.action,
                            patchContent = patch.content,
                        )
                    } else {
                        skipped += rel to verification.reason.ifBlank { "patch did not change disk" }
                    }
                } catch (t: Throwable) {
                    log.warn("Failed to apply $rel", t)
                    skipped += rel to (t.message ?: t.javaClass.simpleName)
                }
            }
        }
        if (undoEntries.isNotEmpty()) {
            lastUndo = UndoRecord(nodeTitle = node.title.ifBlank { node.id }, entries = undoEntries)
        }
        return ApplyResult(applied, skipped)
    }

    fun applySingle(
        node: BlueprintNode,
        execution: ExecutionArtifact,
        path: String,
        review: ReviewArtifact? = null,
    ): ApplyResult {
        val rel = FileScope.normalizeRelativePath(path)
            ?: return ApplyResult(emptyList(), listOf(path to "invalid relative path"))
        val filtered = execution.copy(patches = execution.patches.filter {
            FileScope.normalizeRelativePath(it.path) == rel
        })
        if (filtered.patches.isEmpty()) return ApplyResult(emptyList(), listOf(rel to "no patch for file"))
        return apply(node, filtered, review)
    }

    private fun applyOne(basePath: String, rel: String, patch: Patch) {
        val absolute = "$basePath/$rel"
        val lfs = LocalFileSystem.getInstance()

        when (patch.action.lowercase()) {
            "delete" -> {
                val vf = lfs.refreshAndFindFileByPath(absolute) ?: return
                vf.delete(this)
            }
            "create", "update", "" -> {
                val bytes = patch.content.toByteArray(StandardCharsets.UTF_8)
                val existing = lfs.refreshAndFindFileByPath(absolute)
                if (existing != null) {
                    existing.setBinaryContent(bytes)
                } else {
                    val parentPath = rel.substringBeforeLast('/', "")
                    val parentVF: VirtualFile = if (parentPath.isBlank()) {
                        lfs.refreshAndFindFileByPath(basePath)
                            ?: error("Cannot resolve project root: $basePath")
                    } else {
                        val parentAbs = "$basePath/$parentPath"
                        lfs.refreshAndFindFileByPath(parentAbs)
                            ?: VfsUtil.createDirectoryIfMissing(parentAbs)
                            ?: error("Cannot create parent dir: $parentAbs")
                    }
                    val fileName = rel.substringAfterLast('/')
                    val created = parentVF.createChildData(this, fileName)
                    created.setBinaryContent(bytes)
                }
            }
            else -> error("Unknown patch action: ${patch.action}")
        }
    }

    /**
     * Restore the files touched by the last successful apply to their pre-apply state.
     * Clears [lastUndo] regardless of outcome so the record is never applied twice.
     * Files that were edited after apply are skipped to avoid silent data loss.
     */
    fun undoLast(): UndoResult {
        val record = lastUndo ?: return UndoResult(emptyList(), emptyList())
        lastUndo = null
        val basePath = project.basePath
            ?: return UndoResult(emptyList(), record.entries.map { it.path to "no project base path" })
        val base = Paths.get(basePath).normalize()
        val restored = mutableListOf<String>()
        val skipped = mutableListOf<Pair<String, String>>()

        WriteCommandAction.runWriteCommandAction(project) {
            for (entry in record.entries) {
                val current = snapshot(base, entry.path)
                when (val decision = UndoFreshness.decide(entry, current)) {
                    is UndoDecision.RestoreContent -> {
                        val undoAction = if (entry.patchAction.lowercase() == "delete") "create" else "update"
                        try {
                            applyOne(basePath, entry.path, Patch(
                                path = entry.path,
                                action = undoAction,
                                content = decision.content,
                            ))
                            restored += entry.path
                        } catch (t: Throwable) {
                            log.warn("Failed to restore ${entry.path}", t)
                            skipped += entry.path to (t.message ?: t.javaClass.simpleName)
                        }
                    }
                    is UndoDecision.DeleteFile -> {
                        try {
                            applyOne(basePath, entry.path, Patch(path = entry.path, action = "delete"))
                            restored += entry.path
                        } catch (t: Throwable) {
                            log.warn("Failed to delete ${entry.path} during undo", t)
                            skipped += entry.path to (t.message ?: t.javaClass.simpleName)
                        }
                    }
                    is UndoDecision.Skip -> skipped += entry.path to decision.reason
                }
            }
        }
        return UndoResult(restored, skipped)
    }

    /** Read current on-disk content for a relative path, or empty if missing. */
    fun readCurrentContent(relPath: String): String {
        val basePath = project.basePath ?: return ""
        val rel = FileScope.normalizeRelativePath(relPath) ?: return ""
        val base = Paths.get(basePath).normalize()
        val target = base.resolve(rel).normalize()
        if (!target.startsWith(base) || !Files.isRegularFile(target)) return ""
        return Files.readString(target, StandardCharsets.UTF_8)
    }

    fun readCurrentSnapshot(relPath: String): DiskSnapshot {
        val basePath = project.basePath ?: return DiskSnapshot(exists = false)
        val rel = FileScope.normalizeRelativePath(relPath) ?: return DiskSnapshot(exists = false)
        val base = Paths.get(basePath).normalize()
        return snapshot(base, rel)
    }

    private fun snapshot(base: Path, rel: String): DiskSnapshot {
        val target = base.resolve(rel).normalize()
        if (!target.startsWith(base) || !Files.isRegularFile(target)) return DiskSnapshot(exists = false)
        return DiskSnapshot(exists = true, content = Files.readString(target, StandardCharsets.UTF_8))
    }
}
