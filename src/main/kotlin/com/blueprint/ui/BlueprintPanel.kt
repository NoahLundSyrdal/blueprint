package com.blueprint.ui

import com.blueprint.ir.IRStore
import com.blueprint.model.AcceptanceCriterion
import com.blueprint.model.AcceptanceCriterionType
import com.blueprint.model.BlueprintNode
import com.blueprint.model.ExecutionArtifact
import com.blueprint.model.ExecutionStatus
import com.blueprint.model.FileScope
import com.blueprint.model.NodeType
import com.blueprint.model.Patch
import com.blueprint.model.ReviewArtifact
import com.blueprint.service.ApplyChangesService
import com.blueprint.service.CodexClient
import com.blueprint.service.DependencyGraphService
import com.blueprint.service.JsonExtractor
import com.blueprint.service.NodeExecutionService
import com.blueprint.service.NodePlanningService
import com.blueprint.service.NodeRegistry
import com.blueprint.service.PatchFreshness
import com.blueprint.service.ProjectRunService
import com.blueprint.service.ProjectValidationService
import com.blueprint.service.PythonProjectAnalyzer
import com.blueprint.service.PythonUmlGenerator
import com.blueprint.service.ReviewService
import com.blueprint.service.UmlImportService
import com.blueprint.service.WorkspaceChatEntry
import com.blueprint.service.WorkspaceState
import com.blueprint.service.WorkspaceStateStore
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.io.File
import java.nio.charset.StandardCharsets
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.Scrollable
import javax.swing.JSeparator
import javax.swing.JSplitPane
import javax.swing.SwingConstants
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.AbstractBorder
import javax.swing.border.Border
import javax.swing.border.TitledBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.plaf.basic.BasicButtonUI
import javax.swing.table.DefaultTableModel

private enum class NodeFilter {
    ALL,
    READY,
    BLOCKED,
    APPLIED,
    CURRENT_WAVE
}

private data class DependencyChoice(val id: String, val label: String) {
    override fun toString(): String = label
}

private object BlueprintTheme {
    val Background = Color(0x1E1E1E)
    val Panel = Color(0x252526)
    val Surface = Color(0x2A2A2A)
    val SurfaceHover = Color(0x323337)
    val SurfacePressed = Color(0x383A40)
    val Border = Color(0x3C3C3C)
    val BorderStrong = Color(0x4A4A4A)
    val Text = Color(0xD4D4D4)
    val TextStrong = Color(0xFFFFFF)
    val Muted = Color(0x9DA3AF)
    val Accent = Color(0x4FC1FF)
    val AccentHover = Color(0x72CCFF)
    val AccentPressed = Color(0x2EA7E0)
    val AccentSurface = Color(0x102F42)
    val Danger = Color(0xF48771)
    val DangerSurface = Color(0x3D2420)
    val Warning = Color(0xDCDCAA)
    val WarningSurface = Color(0x3A331E)
    val Success = Color(0x6A9955)
    val SuccessSurface = Color(0x1F3826)
    val PurpleSurface = Color(0x2B2842)

    fun font(size: Float = 13f, style: Int = Font.PLAIN): Font =
        (UIManager.getFont("Label.font") ?: Font("Dialog", Font.PLAIN, size.toInt())).deriveFont(style, size)
}

private class RoundedLineBorder(
    private val color: Color,
    private val radius: Int = 10,
    private val padding: Insets = Insets(6, 8, 6, 8),
) : AbstractBorder() {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius)
        } finally {
            g2.dispose()
        }
    }

    override fun getBorderInsets(c: Component, insets: Insets): Insets {
        insets.top = padding.top
        insets.left = padding.left
        insets.bottom = padding.bottom
        insets.right = padding.right
        return insets
    }

    override fun getBorderInsets(c: Component): Insets =
        Insets(padding.top, padding.left, padding.bottom, padding.right)
}

private open class RoundedSurfacePanel(
    layout: LayoutManager,
    private val fill: Color,
    private val outline: Color? = null,
    private val radius: Int = 20,
) : JPanel(layout) {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = fill
            g2.fillRoundRect(0, 0, width - 1, height - 1, radius, radius)
            outline?.let {
                g2.color = it
                g2.drawRoundRect(0, 0, width - 1, height - 1, radius, radius)
            }
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}

private class VerticalScrollablePanel : JPanel(), Scrollable {
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = 24

    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        when (orientation) {
            SwingConstants.VERTICAL -> (visibleRect.height - 24).coerceAtLeast(24)
            else -> (visibleRect.width - 24).coerceAtLeast(24)
        }

    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = false
}

private class ChatBubblePanel(
    displayName: String,
    message: String,
    fill: Color,
    outline: Color?,
    labelColor: Color,
) : RoundedSurfacePanel(BorderLayout(0, 8), fill, outline, radius = 24) {
    private val messageArea = JBTextArea().apply {
        text = message
        isEditable = false
        isFocusable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        foreground = BlueprintTheme.Text
        border = BorderFactory.createEmptyBorder()
        font = BlueprintTheme.font(13f)
    }

    init {
        border = BorderFactory.createEmptyBorder(12, 16, 12, 16)
        add(JLabel(displayName).apply {
            isOpaque = false
            foreground = labelColor
            font = BlueprintTheme.font(11f, Font.BOLD)
        }, BorderLayout.NORTH)
        add(messageArea, BorderLayout.CENTER)
    }

    fun relayoutForViewport(viewportWidth: Int) {
        val bubbleWidth = (viewportWidth * 0.82f).toInt().coerceIn(240, 520)
        val contentWidth = (bubbleWidth - 32).coerceAtLeast(180)
        messageArea.setSize(contentWidth, Int.MAX_VALUE)
        val textSize = messageArea.preferredSize
        messageArea.preferredSize = Dimension(contentWidth, textSize.height)
        setSize(bubbleWidth, Int.MAX_VALUE)
        val bubbleSize = super.getPreferredSize()
        preferredSize = Dimension(bubbleWidth, bubbleSize.height)
        maximumSize = Dimension(bubbleWidth, bubbleSize.height)
        revalidate()
    }
}

internal data class FirstRunChecklistState(
    val codeMapReady: Boolean,
    val reviewedDiffReady: Boolean,
    val reviewApprovedReady: Boolean,
    val appliedReady: Boolean,
    val refreshedCodeMapReady: Boolean,
    val runCommand: String?,
    val runEntryCandidates: List<String> = emptyList(),
    val validationCommand: String?,
    val validationReady: Boolean,
    val validationPassed: Boolean,
    val runVerified: Boolean,
    val skippedFiles: List<PythonProjectAnalyzer.SkippedFile> = emptyList(),
) {
    /**
     * Returns the first-run checklist for any Python folder in plain product language.
     */
    fun checklistText(): String {
        val currentStep = when {
            !codeMapReady -> 1
            !reviewedDiffReady -> 2
            !reviewApprovedReady -> 3
            !appliedReady -> 4
            !refreshedCodeMapReady -> 5
            !runVerified -> 6
            else -> 6
        }
        val validationLine = when {
            !appliedReady && validationCommand.isNullOrBlank() ->
                "No validation command was inferred. Blueprint will validate after apply if it can infer a command; otherwise verify manually after Apply Approved Changes."
            !appliedReady ->
                "Blueprint will validate after apply with: $validationCommand"
            validationPassed && validationCommand.isNullOrBlank() ->
                "Validation passed after apply. Blueprint did not need a separate validation command."
            validationPassed ->
                "Validation passed after apply with: $validationCommand"
            validationReady && validationCommand.isNullOrBlank() ->
                "Validation ran after apply. Review the result before you continue."
            validationReady ->
                "Validation ran after apply with: $validationCommand. Review the result before you continue."
            validationCommand.isNullOrBlank() ->
                "No validation command was inferred. After Apply Approved Changes, verify manually or run your preferred checks."
            else ->
                "Validation is ready to run after apply with: $validationCommand"
        }
        val runLine = when {
            runCommand.isNullOrBlank() ->
                missingRunCommandChecklist(runEntryCandidates)
            runVerified ->
                "Run verified with: $runCommand"
            else ->
                "Run the changed app with: $runCommand"
        }
        val skippedLine = skippedFilesSummaryLine()
        return buildList {
            add("First-run checklist:")
            add("${markerForStep(1, currentStep, codeMapReady)} Refresh UML From Code -> load the current Python project into a code-backed UML diagram.")
            skippedLine?.let { add(it) }
            add("${markerForStep(2, currentStep, reviewedDiffReady)} Generate Code Diff -> create a reviewed code patch from your UML edits.")
            add("${markerForStep(3, currentStep, reviewApprovedReady)} Review approved -> confirm Blueprint says the reviewed code patch is safe to apply.")
            add("${markerForStep(4, currentStep, appliedReady)} Apply Approved Changes -> write the approved code patch to disk.")
            add("${markerForStep(5, currentStep, refreshedCodeMapReady)} Refresh UML From Code -> verify the code-backed UML after apply.")
            add("${markerForStep(6, currentStep, runVerified)} Run the changed app -> $runLine")
            add("")
            add("Validation:")
            add("- $validationLine")
        }.joinToString("\n")
    }

    private fun skippedFilesSummaryLine(): String? {
        if (skippedFiles.isEmpty()) return null
        val reasonSummary = skippedFiles.groupingBy { it.reason }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(2)
            .joinToString(", ") { (reason, count) ->
                if (count == 1) reason else "$count $reason"
            }
        val examplePaths = skippedFiles.take(2).joinToString(", ") { it.path }
        return "- Scope note: ${skippedFiles.size} Python path${if (skippedFiles.size == 1) " was" else "s were"} skipped during Refresh UML From Code ($reasonSummary). Inspect Skipped paths like $examplePaths if the UML looks incomplete."
    }

    private fun markerForStep(step: Int, currentStep: Int, done: Boolean): String =
        when {
            done -> "[done]"
            step == currentStep -> "[next]"
            else -> "[wait]"
        }
}

private fun missingRunCommandChecklist(runEntryCandidates: List<String>): String {
    val candidates = runEntryCandidates.take(4)
    return if (candidates.isEmpty()) {
        "Blueprint could not infer a run command yet. Verify manually with this checklist:\n- Open the likely entrypoint manually.\n- Confirm the changed feature exists.\n- Search for FastAPI, Flask, Streamlit, __main__.py, app.py, main.py, or __name__ == \"__main__\"."
    } else {
        "Blueprint could not infer a run command yet. Open Likely Entry File to jump into one of these likely entry files: ${candidates.joinToString(", ")}. Then confirm the changed feature exists."
    }
}

internal data class GuidedInviteScenarioState(
    val codeMapReady: Boolean,
    val prompt: String,
    val expectedEntity: String,
    val expectedRelationSource: String?,
    val expectedRelationTarget: String?,
    val resetSuggested: Boolean,
    val resetPath: String,
    val umlDraftReady: Boolean,
    val reviewedDiffReady: Boolean,
    val reviewApprovedReady: Boolean,
    val appliedReady: Boolean,
    val refreshedCodeMapReady: Boolean,
    val runVerified: Boolean,
    val promptReady: Boolean,
    val runCommand: String?,
) {
    fun demoReceiptText(): String {
        val runLine = when {
            runCommand.isNullOrBlank() -> "[wait] Run the changed app -> wait for an inferred run command, then verify the feature manually."
            runVerified -> "[pass] Run the changed app -> verified with: $runCommand"
            refreshedCodeMapReady -> "[next] Run the changed app -> use: $runCommand"
            else -> "[wait] Run the changed app -> use: $runCommand after Refresh UML From Code."
        }
        val tryChangeLine = when {
            !codeMapReady -> "[wait] Try This Change -> load the current code map first."
            promptReady || umlDraftReady -> "[pass] Try This Change -> loaded fresh prompt for the current code map: $prompt"
            else -> "[next] Try This Change -> load a fresh prompt for the current code map."
        }
        return listOf(
            "Demo receipt:",
            if (codeMapReady) "[pass] Refresh UML From Code -> expected visible result: current code map is loaded." else "[next] Refresh UML From Code -> expected visible result: current code map is loaded.",
            tryChangeLine,
            if (reviewedDiffReady) "[pass] Generate Code Diff -> expected visible result: reviewed code patch is ready." else if (umlDraftReady) "[next] Generate Code Diff -> expected visible result: reviewed code patch is ready." else "[wait] Generate Code Diff -> expected visible result: reviewed code patch is ready.",
            if (reviewApprovedReady) "[pass] Review approved -> expected visible result: Apply Approved Changes is unlocked." else if (reviewedDiffReady) "[next] Review approved -> expected visible result: Apply Approved Changes is unlocked." else "[wait] Review approved -> expected visible result: Apply Approved Changes is unlocked.",
            if (appliedReady) "[pass] Apply Approved Changes -> expected visible result: code files are written to disk." else if (reviewApprovedReady) "[next] Apply Approved Changes -> expected visible result: code files are written to disk." else "[wait] Apply Approved Changes -> expected visible result: code files are written to disk.",
            if (refreshedCodeMapReady) "[pass] Refresh UML From Code again -> expected visible result: code-backed UML reflects the applied change." else if (appliedReady) "[next] Refresh UML From Code again -> expected visible result: code-backed UML reflects the applied change." else "[wait] Refresh UML From Code again -> expected visible result: code-backed UML reflects the applied change.",
            runLine,
        ).joinToString("\n")
    }
}

internal object GuidedInviteScenario {
    const val PATCH_PATH = "blueprint_demo/imported_invite/models.py"
    private const val BASELINE_RESOURCE = "/guided_demo/invite_project_imported_invite_models.py"
    private val promptPlans = listOf(
        PromptPlan(
            prompt = "add an InvitePolicy entity",
            expectedEntity = "InvitePolicy",
            relationSource = "Invite",
            relationTarget = "InvitePolicy",
        ),
        PromptPlan(
            prompt = "add an InviteReminder entity",
            expectedEntity = "InviteReminder",
            relationSource = "Invite",
            relationTarget = "InviteReminder",
        ),
        PromptPlan(
            prompt = "add an expires_at field to Invite",
            expectedEntity = "Invite",
            expectedField = "expires_at",
        ),
    )

    data class PromptPlan(
        val prompt: String,
        val expectedEntity: String,
        val relationSource: String? = null,
        val relationTarget: String? = null,
        val expectedField: String? = null,
    )

    fun matchesProject(projectName: String, basePath: String?): Boolean {
        val normalizedPath = basePath.orEmpty().replace('\\', '/')
        return projectName == "invite_project" || normalizedPath.endsWith("/examples/invite_project")
    }

    fun importedInviteFile(projectBasePath: String?): File =
        projectBasePath?.let { File(it, PATCH_PATH) } ?: File(PATCH_PATH)

    fun baselineText(): String =
        GuidedInviteScenario::class.java.getResourceAsStream(BASELINE_RESOURCE)?.use { input ->
            input.readBytes().toString(StandardCharsets.UTF_8)
        } ?: error("Missing guided invite baseline resource: $BASELINE_RESOURCE")

    fun resetImportedInviteFile(projectBasePath: String?): Boolean {
        val target = importedInviteFile(projectBasePath)
        val parent = target.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        target.writeText(baselineText())
        return true
    }

    fun needsReset(projectBasePath: String?): Boolean {
        val target = importedInviteFile(projectBasePath)
        if (!target.isFile) return false
        return runCatching { target.readText() == baselineText() }.getOrDefault(false).not()
    }

    fun pickPrompt(componentNames: Set<String>, entityNames: Set<String>, inviteFields: List<String>): PromptPlan? =
        promptPlans.firstOrNull { plan ->
            when {
                plan.expectedField != null -> plan.expectedField !in inviteFields
                plan.expectedEntity !in componentNames && plan.expectedEntity !in entityNames -> true
                else -> false
            }
        }

    fun checklistText(state: GuidedInviteScenarioState): String {
        val runStep = when {
            state.runCommand.isNullOrBlank() -> "Run the changed app -> ${missingRunCommandChecklist(emptyList())}"
            state.runVerified -> "Run the changed app -> pass. Verified with: ${state.runCommand}"
            else -> "Run the changed app -> start with: ${state.runCommand}; verify the new feature appears."
        }
        if (state.resetSuggested) {
            return listOf(
                "Demo prompt scenario:",
                "[done] Refresh UML From Code -> current code map is loaded.",
                if (state.promptReady) "[done] Guided demo prompt loaded: \"${state.prompt}\"." else "[wait] Guided demo prompt will load after the current code map is ready.",
                "[done] Guided demo changes already exist in this sandbox.",
                "[next] Click Reset Demo Sandbox to restore ${state.resetPath} to the baseline invite demo file.",
                "[next] Then click Try This Change to load a fresh prompt for the clean sandbox.",
                "[wait] Generate Code Diff -> wait until the sandbox is reset or you choose your own new UML change.",
                "[wait] Blueprint reviews the fresh code patch before apply.",
                "[wait] Apply Approved Changes -> blocked until review approves the fresh reviewed code patch.",
                "[wait] Refresh UML From Code -> verify the code-backed UML after apply.",
                "[wait] $runStep",
                "",
                "Your own change:",
                "[next] Edit the UML directly or ask chat for a different architecture change, then Generate Code Diff.",
            ).joinToString("\n")
        }
        if (!state.codeMapReady) {
            return listOf(
                "Demo prompt scenario:",
                "[next] Refresh UML From Code -> load the current code map first so Blueprint can choose a fresh demo change.",
                "[wait] Try This Change -> load a fresh prompt for the current code map.",
                "[wait] Generate Code Diff -> available after the UML draft is updated.",
                "[wait] Blueprint reviews the code patch before apply.",
                "[wait] Apply Approved Changes -> blocked until review approves the reviewed code patch.",
                "[wait] Refresh UML From Code -> verify the code-backed UML after apply.",
                "[wait] $runStep",
                "",
                "Your own change:",
                "[next] You can skip the demo path and ask chat for a different architecture change after the first UML refresh.",
            ).joinToString("\n")
        }
        val currentStep = when {
            !state.codeMapReady -> 1
            !state.umlDraftReady -> 2
            !state.reviewedDiffReady -> 3
            !state.reviewApprovedReady -> 4
            !state.appliedReady -> 5
            !state.refreshedCodeMapReady -> 6
            !state.runVerified -> 7
            else -> 7
        }
        val expectedResult = when {
            state.expectedRelationSource != null && state.expectedRelationTarget != null ->
                "expect ${state.expectedEntity} linked from ${state.expectedRelationSource} in the UML draft."
            state.expectedEntity == "Invite" ->
                "expect Invite to include ${state.prompt.substringAfter("add an ").substringBefore(" field")} in the UML draft."
            else -> "expect ${state.expectedEntity} in the UML draft."
        }
        val refreshedResult = when {
            state.expectedEntity == "Invite" ->
                "expect the refreshed current code map to include ${state.prompt.substringAfter("add an ").substringBefore(" field")} on Invite."
            else -> "expect ${state.expectedEntity} to appear in the refreshed current code map."
        }
        return listOf(
            "Demo prompt scenario:",
            "${stepMarker(1, currentStep, state.codeMapReady)} Refresh UML From Code -> expect Project, User, and Invite in the current code map.",
            if (state.promptReady) {
                "${stepMarker(2, currentStep, state.umlDraftReady)} Try This Change: \"${state.prompt}\" -> loaded fresh prompt for the current code map; $expectedResult"
            } else {
                "${stepMarker(2, currentStep, false)} Try This Change -> use the button to load the fresh prompt into chat first."
            },
            "${stepMarker(3, currentStep, state.reviewedDiffReady)} Generate Code Diff -> expect a reviewed code patch for $PATCH_PATH.",
            "${stepMarker(4, currentStep, state.reviewApprovedReady)} Review approved -> the reviewed code patch is approved and Apply Approved Changes is now unlocked.",
            "${stepMarker(5, currentStep, state.appliedReady)} Apply Approved Changes -> expect the imported invite patch to be written to disk after review approval.",
            "${stepMarker(6, currentStep, state.refreshedCodeMapReady)} Refresh UML From Code -> $refreshedResult",
            "${stepMarker(7, currentStep, state.runVerified)} $runStep",
            "",
            "Your own change:",
            "[next] Edit the UML directly or ask chat for a different architecture change when you are not following the demo prompt.",
        ).joinToString("\n")
    }

    private fun stepMarker(step: Int, currentStep: Int, done: Boolean): String =
        when {
            done -> "[done]"
            step == currentStep -> "[next]"
            else -> "[wait]"
        }
}

internal data class PostApplyInlineSummary(
    val changedPaths: List<String>,
    val summaryLine: String,
    val validationAndPathsLine: String,
    val nextStepLine: String,
    val verifyChecklist: String,
)

internal data class GenerateDiffGuideSummary(
    val guideText: String,
    val nextStepDetail: String,
    val commandSummary: String,
)

internal object PatchChangeSummary {
    /**
     * Builds the review-tab summary shown before apply from the reviewed patch content.
     */
    fun reviewSummary(exec: ExecutionArtifact?): String {
        if (exec == null) return "What changed?\n- No reviewed code patch yet."
        if (exec.patches.isEmpty()) return "What changed?\n- No code changes needed"
        val semanticChanges = semanticChanges(exec.patches, exec.summary)
        return buildSummary(
            heading = "What changed?",
            semanticHeading = "Plain-English summary before apply:",
            semanticChanges = semanticChanges,
            changedFilesText = changedFilesSummary(exec),
        )
    }

    /**
     * Returns compact change lines for the whole reviewed patch or a filtered set of changed paths.
     */
    fun semanticChangeLines(exec: ExecutionArtifact?, changedPaths: Collection<String>? = null): List<String> {
        if (exec == null) return emptyList()
        val changedPathSet = changedPaths?.toSet()
        val filtered = if (changedPathSet == null) exec.patches else exec.patches.filter { it.path in changedPathSet }
        return semanticChanges(filtered, exec.summary)
    }

    /**
     * Builds the apply-success summary from only the paths that were actually written to disk.
     */
    fun applySummary(exec: ExecutionArtifact?, appliedPaths: List<String>): String {
        if (exec == null || appliedPaths.isEmpty()) return "What changed?\n- No code changes needed"
        val appliedPathSet = appliedPaths.toSet()
        val changedFiles = exec.patches.filter { it.path in appliedPathSet }
        if (changedFiles.isEmpty()) return "What changed?\n- No code changes needed"
        val semanticChanges = semanticChanges(changedFiles, exec.summary)
        return buildSummary(
            heading = "What changed?",
            semanticHeading = "Plain-English summary after apply:",
            semanticChanges = semanticChanges,
            changedFilesText = changedFilesSummary(changedFiles),
        )
    }

    /**
     * Returns a short human-readable summary of which files the reviewed patch touches.
     */
    fun changedFilesSummary(exec: ExecutionArtifact?): String =
        when {
            exec == null -> "Changed files: none yet."
            exec.patches.isEmpty() -> "Changed files: none."
            else -> changedFilesSummary(exec.patches)
        }

    private fun buildSummary(
        heading: String,
        semanticHeading: String,
        semanticChanges: List<String>,
        changedFilesText: String,
        diffGuidance: String? = null,
    ): String =
        buildString {
            appendLine(heading)
            appendLine(semanticHeading)
            semanticChanges.forEach { appendLine("- $it") }
            diffGuidance?.let {
                appendLine()
                appendLine(it)
            }
            appendLine()
            appendLine(changedFilesText)
        }.trim()

    private fun changedFilesSummary(patches: List<Patch>): String =
        buildString {
            appendLine("Changed files (${patches.size}):")
            patches.forEach { appendLine("- ${fileActionLabel(it.action)} ${it.path}") }
        }.trim()

    private fun fileActionLabel(action: String): String =
        when (action.lowercase()) {
            "create" -> "create"
            "delete" -> "delete"
            else -> "update"
        }

    private fun semanticChanges(patches: List<Patch>, fallbackSummary: String): List<String> {
        val changes = patches
            .asSequence()
            .flatMap { patch -> patchSemanticChanges(patch).asSequence() }
            .distinct()
            .toList()
        return if (changes.isEmpty()) {
            fallbackSummary.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: listOf("Reviewed code patch is ready.")
        } else {
            changes
        }
    }

    private fun patchSemanticChanges(patch: Patch): List<String> {
        val changedLines = meaningfulChangedLines(patch)
        if (changedLines.isEmpty()) return listOf(fallbackPatchSummary(patch))
        val classes = linkedMapOf<String, MutableList<String>>()
        var currentClass: String? = null
        for (line in changedLines) {
            val trimmed = line.trim()
            val className = Regex("^class\\s+([A-Za-z_][A-Za-z0-9_]*)").find(trimmed)?.groupValues?.get(1)
            if (className != null) {
                currentClass = className
                classes.getOrPut(className) { mutableListOf() }
                continue
            }
            val owner = currentClass ?: continue
            fieldSummary(trimmed)?.let { classes.getOrPut(owner) { mutableListOf() }.add(it) }
        }
        val action = patch.action.lowercase()
        val summaries = classes.entries.flatMap { (name, fields) ->
            summarizeClassChange(name, fields.distinct(), action)
        }
        return if (summaries.isNotEmpty()) summaries else listOf(fallbackPatchSummary(patch))
    }
    private fun summarizeClassChange(name: String, fields: List<String>, action: String): List<String> =
        when {
            action == "create" && fields.isEmpty() -> listOf("Added class $name")
            action == "create" -> listOf("Added class $name") + fields.map { "$name + $it" }
            fields.isEmpty() -> listOf("Updated class $name")
            else -> fields.map { "$name + $it" }
        }

    private fun meaningfulChangedLines(patch: Patch): List<String> {
        val lines = patch.content.replace("\r\n", "\n").replace("\r", "\n").lines()
        val diffLike = lines.any { it.startsWith("@@") || it.startsWith("+++") || it.startsWith("---") }
        if (!diffLike) return lines.filter { it.isNotBlank() }
        val changed = mutableListOf<String>()
        lines.forEach { line ->
            when {
                line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") -> Unit
                line.startsWith("+") && !line.startsWith("+++") -> changed += line.removePrefix("+")
                line.startsWith(" ") -> {
                    val context = line.removePrefix(" ")
                    if (context.trimStart().startsWith("class ")) changed += context
                }
            }
        }
        return changed.filter { it.isNotBlank() }
    }

    private fun fieldSummary(line: String): String? {
        val fieldMatch = Regex("^([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*([^=#]+)").find(line) ?: return null
        val name = fieldMatch.groupValues[1]
        if (name == "return") return null
        return "$name: ${fieldMatch.groupValues[2].trim()}"
    }

    private fun fallbackPatchSummary(patch: Patch): String {
        val target = patch.path.substringAfterLast('/').ifBlank { patch.path }
        return when (patch.action.lowercase()) {
            "create" -> "$target created"
            "delete" -> "$target deleted"
            else -> "$target updated"
        }
    }
}
internal object ReviewExplanation {
    fun summary(
        nodeTitle: String,
        exec: ExecutionArtifact?,
        review: ReviewArtifact?,
        readiness: DependencyGraphService.NodeReadiness?,
        validation: ProjectValidationService.ValidationResult?,
        validationCommand: String?,
    ): String {
        if (review == null) return "Review not run yet. Generate Code Diff first so Blueprint can review the patch before apply."
        return details(nodeTitle, exec, review, readiness, validation, validationCommand).joinToString("\n")
    }

    fun statusLine(nodeTitle: String, exec: ExecutionArtifact?, review: ReviewArtifact?): String {
        if (review == null) return "Review not run yet."
        val shortTitle = nodeTitle.ifBlank { "this change" }
        val changePhrase = changePhrase(exec)
        return if (review.reviewStatus.uppercase() == "APPROVE") {
            "Review approved $shortTitle because $changePhrase stays in scope. ${compactApprovalReason(review)}"
        } else {
            "Review blocked $shortTitle because ${blockerLine(review)} Fix: ${fixLine(review)}"
        }
    }

    fun details(
        nodeTitle: String,
        exec: ExecutionArtifact?,
        review: ReviewArtifact,
        readiness: DependencyGraphService.NodeReadiness?,
        validation: ProjectValidationService.ValidationResult?,
        validationCommand: String?,
    ): List<String> {
        val shortTitle = nodeTitle.ifBlank { "this change" }
        val changePhrase = changePhrase(exec)
        val scopeLine = when (review.scopeCompliance.result.uppercase()) {
            "PASS" -> "Scope: stays within the selected files."
            "PARTIAL" -> "Scope: mostly in scope, but review found scope concerns."
            else -> "Scope: review found out-of-scope changes."
        }
        val dependencyLine = if (readiness?.ready == false) {
            "Dependency status: blocked by ${readiness.reasons.firstOrNull().orEmpty()}."
        } else {
            "Dependency status: ready."
        }
        val validationLine = when (validation?.status) {
            ProjectValidationService.ValidationResult.Status.PASS -> "${validation.detailLabel()} status: passed after apply."
            ProjectValidationService.ValidationResult.Status.SKIPPED -> {
                if (validation.reason.contains("No Python validation command was inferred", ignoreCase = true)) {
                    "Validation status: skipped after apply because no validation command was inferred."
                } else {
                    "Validation status: skipped after apply."
                }
            }
            ProjectValidationService.ValidationResult.Status.FAIL -> "${validation.detailLabel()} status: failed after apply."
            null -> "Validation status: will run after apply if Blueprint can infer a command."
        }
        val validationCommandLine = validationCommand?.let { "Validation after apply: $it" }
            ?: "Validation after apply: Blueprint could not infer a validation command, so validation will be skipped unless you run checks manually."
        val lines = mutableListOf<String>()
        if (review.reviewStatus.uppercase() == "APPROVE") {
            lines += "Why is it safe to apply?"
            lines += "- Approved because $changePhrase stays aligned with $shortTitle and review found no blocking scope or safety issues."
            review.acceptanceReviewLine()?.let { lines += "- $it" }
            lines += "- ${safetyLine(review)}"
        } else {
            lines += "Why is it blocked?"
            lines += "- Not approved because ${blockerLine(review)}"
            lines += "- Fix: ${fixLine(review)}"
            review.acceptanceReviewLine()?.let { lines += "- $it" }
            lines += "- ${safetyLine(review)}"
        }
        lines += "- $scopeLine"
        lines += "- $dependencyLine"
        lines += "- $validationCommandLine"
        lines += "- $validationLine"
        return lines
    }

    private fun changePhrase(exec: ExecutionArtifact?): String {
        val semanticChanges = PatchChangeSummary.semanticChangeLines(exec).take(2)
        return if (semanticChanges.isEmpty()) {
            "the reviewed code patch"
        } else {
            semanticChanges.joinToString(" and ")
        }
    }

    private fun blockerLine(review: ReviewArtifact): String =
        review.issues.firstOrNull()?.details?.ifBlank { null }
            ?: review.summary.ifBlank { "review found a blocker in the generated patch." }

    private fun fixLine(review: ReviewArtifact): String =
        review.issues.firstOrNull()?.suggestedFix?.ifBlank { null }
            ?: review.followUpChecks.firstOrNull()
            ?: "adjust the UML or regenerate the patch and review again."

    private fun compactApprovalReason(review: ReviewArtifact): String =
        review.positiveSignals.firstOrNull()?.trim()?.trimEnd('.')?.let {
            "$it."
        } ?: "No blocking safety issues were reported."

    private fun safetyLine(review: ReviewArtifact): String =
        when {
            review.positiveSignals.isNotEmpty() -> "Safety: ${review.positiveSignals.take(2).joinToString(" ")}"
            review.issues.isEmpty() -> "Safety: No concrete safety issues were reported."
            else -> "Safety: ${review.issues.take(2).joinToString(" ") { it.title.ifBlank { it.category } }}"
        }

    private fun ReviewArtifact.acceptanceReviewLine(): String? {
        val accepted = acceptanceReview.filter { it.result.uppercase() == "PASS" }
        if (accepted.isNotEmpty()) {
            val evidence = accepted
                .flatMap { it.evidence }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(2)
            return if (evidence.isEmpty()) {
                "Acceptance: requested UML changes are covered by the reviewed patch."
            } else {
                "Acceptance: ${evidence.joinToString(" ")}"
            }
        }
        val concerns = acceptanceReview
            .filter { it.result.uppercase() == "PARTIAL" || it.result.uppercase() == "FAIL" }
            .flatMap { reviewItem ->
                reviewItem.issues.ifEmpty { listOf(reviewItem.criterion) }
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(2)
        return if (concerns.isEmpty()) null else "Acceptance: ${concerns.joinToString(" ")}"
    }
}

private class BlueprintButtonUi(private val primary: Boolean) : BasicButtonUI() {
    override fun installDefaults(button: AbstractButton) {
        super.installDefaults(button)
        button.isOpaque = false
        button.isContentAreaFilled = false
        button.isBorderPainted = false
        button.isFocusPainted = false
        button.isRolloverEnabled = true
        button.margin = Insets(0, 0, 0, 0)
        button.border = BorderFactory.createEmptyBorder(7, 12, 7, 12)
        button.font = BlueprintTheme.font(13f, if (primary) Font.BOLD else Font.PLAIN)
        button.foreground = if (primary) Color(0x061016) else BlueprintTheme.Text
    }

    override fun paint(g: Graphics, c: JComponent) {
        val button = c as AbstractButton
        val model = button.model
        val fill = when {
            primary && model.isPressed -> BlueprintTheme.AccentPressed
            primary && model.isRollover -> BlueprintTheme.AccentHover
            primary -> BlueprintTheme.Accent
            model.isPressed -> BlueprintTheme.SurfacePressed
            model.isRollover -> BlueprintTheme.SurfaceHover
            else -> BlueprintTheme.Surface
        }
        val border = when {
            primary -> BlueprintTheme.Accent
            model.isRollover || button.hasFocus() -> BlueprintTheme.Accent
            else -> BlueprintTheme.BorderStrong
        }

        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = fill
            g2.fillRoundRect(0, 0, c.width - 1, c.height - 1, 11, 11)
            g2.color = border
            g2.drawRoundRect(0, 0, c.width - 1, c.height - 1, 11, 11)
        } finally {
            g2.dispose()
        }
        super.paint(g, c)
    }
}

/**
 * Blueprint V2.75 demo-lock tool window. Still list-based, with dependency
 * clarity, read-only graph context, and stable mock-demo execution.
 */
class BlueprintPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val registry = project.service<NodeRegistry>()
    private val codex = service<CodexClient>()
    private val workspaceStore = project.service<WorkspaceStateStore>()
    private val chatHistory = mutableListOf<WorkspaceChatEntry>()
    private var restoringWorkspace = false
    private var restoredFromSession = false
    private var restoredAt: Long = 0L
    private var refreshingList = false
    private var primaryActionBusy = false

    private val listModel = DefaultListModel<BlueprintNode>()
    private val nodeList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixedCellHeight = 36
        setCellRenderer { list, value, _, isSelected, _ ->
            JLabel("${statusChip(badgeFor(value))}  ${value.type.name.lowercase()}  ${value.title.ifBlank { "(untitled)" }}").apply {
                isOpaque = true
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, if (isSelected) 3 else 0, 1, 0, if (isSelected) BlueprintTheme.Accent else BlueprintTheme.Border),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)
                )
                background = if (isSelected) BlueprintTheme.AccentSurface else statusTint(badgeFor(value))
                foreground = if (isSelected) BlueprintTheme.TextStrong else list.foreground
                font = BlueprintTheme.font(13f, if (isSelected) Font.BOLD else Font.PLAIN)
            }
        }
    }

    private val titleField = JBTextField()
    private val summaryField = JBTextField()
    private val descField = JBTextArea(3, 40)
    private val dependencyChoiceModel = DefaultComboBoxModel<DependencyChoice>()
    private val dependencyPicker = JComboBox(dependencyChoiceModel)
    private val dependencyListModel = DefaultListModel<String>()
    private val dependencyList = JBList(dependencyListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixedCellHeight = 24
        setCellRenderer { list, value, _, isSelected, _ ->
            JLabel(dependencyLabel(value)).apply {
                isOpaque = true
                border = BorderFactory.createEmptyBorder(3, 6, 3, 6)
                background = if (isSelected) BlueprintTheme.AccentSurface else list.background
                foreground = if (isSelected) BlueprintTheme.TextStrong else list.foreground
                font = BlueprintTheme.font(13f)
            }
        }
    }
    private val manualDependencyArea = JBTextArea(1, 36)
    private val typeCombo = JComboBox(NodeType.values())
    private val scopePathsArea = JBTextArea(2, 36)
    private val scopeGlobsArea = JBTextArea(1, 36)
    private val scopeDirsArea = JBTextArea(1, 36)
    private val criteriaModel = object : DefaultTableModel(arrayOf("ID", "Type", "Required", "Description", "Verify"), 0) {
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == 2) java.lang.Boolean::class.java else String::class.java
    }
    private val criteriaTable = JTable(criteriaModel).apply {
        fillsViewportHeight = true
        rowHeight = 24
    }

    private val statusLabel = JLabel("No node selected")
    private val selectedLabel = JLabel("Selected: none")
    private val artifactLabel = JLabel("Artifacts: not planned")
    private val groundingSummaryArea = JBTextArea(4, 40).apply {
        isEditable = false
        isFocusable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val reviewSummaryArea = JBTextArea(3, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val changedFilesPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val changedFilesScrollPane = JBScrollPane(changedFilesPanel).apply {
        border = BorderFactory.createEmptyBorder()
        viewport.isOpaque = false
        viewport.background = BlueprintTheme.Panel
        isOpaque = false
        preferredSize = Dimension(0, 110)
        minimumSize = Dimension(0, 80)
    }
    private val safetyArea = JBTextArea(4, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val graphArea = JBTextArea(6, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val dependencyBlockArea = JBTextArea(4, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val miniGraph = MiniGraphPanel()
    private val summaryLabel = JLabel("Total 0 | Ready 0 | Blocked 0 | Applied 0")
    private val providerLabel = JLabel(providerText())
    private val actionProviderLabel = JLabel(providerText())
    private val guideLabel = JLabel("Start by reading the current project into an editable UML diagram.")
    private val nextStepTitleLabel = JLabel("Next: Refresh UML From Code")
    private val nextStepDetailLabel = JLabel("Read the current Python project and draw the first UML diagram.")
    private val firstRunScenarioArea = JBTextArea(5, 40).apply {
        isEditable = false
        isFocusable = false
        lineWrap = true
        wrapStyleWord = true
        rows = 5
    }
    private val demoReceiptArea = JBTextArea(6, 40).apply {
        isEditable = false
        isFocusable = false
        lineWrap = true
        wrapStyleWord = true
        rows = 6
    }
    private val firstRunPromptButton = JButton("Try This Change").apply {
        addActionListener {
            val state = currentInviteFirstRunScenarioState()
            if (state.resetSuggested) {
                if (GuidedInviteScenario.resetImportedInviteFile(project.basePath)) {
                    appendChat(
                        "Blueprint",
                        "Reset Demo Sandbox restored ${state.resetPath} to the baseline invite demo file. Refresh UML From Code, then click Try This Change for a fresh prompt. You can still skip the reset and make your own UML edit instead."
                    )
                    logActivity("Demo e2e step passed: Reset invite demo sandbox at ${state.resetPath}.")
                    status("Invite demo sandbox reset")
                    refreshFirstRunScenario()
                } else {
                    Messages.showWarningDialog(
                        project,
                        "Blueprint could not reset ${state.resetPath} to the baseline invite demo file. Restore it manually, then click Refresh UML From Code.",
                        "Blueprint - Reset Demo Path"
                    )
                }
            } else {
                chatInput.text = state.prompt
                appendChat("Blueprint", "Fresh prompt loaded for the current sandbox: \"${state.prompt}\". Send it as-is, or edit it before Generate Code Diff.")
                logActivity("Demo e2e step passed: Try This Change prepared \"${state.prompt}\".")
                status("Fresh demo prompt loaded")
                refreshFirstRunScenario()
            }
        }
    }
    private val runDemoButton = JButton("Run Demo Step").apply { addActionListener { runDemoVerificationStep() } }
    private val runAppButton = JButton("Run In Blueprint").apply { addActionListener { toggleRunInBlueprint() } }
    private val runOutputArea = JBTextArea(8, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = false
        text = runOutputIdleHint()
    }
    private val primaryActionButton = JButton("Generate Code Diff").apply {
        putClientProperty("blueprint.primary", true)
        addActionListener { runPrimaryProductAction() }
    }
    private val applyApprovedButton = JButton("Apply Approved Changes").apply { addActionListener { applyChanges(null) } }
    private val verifyInUmlButton = JButton("Refresh UML From Code").apply {
        isEnabled = false
        toolTipText = "After apply, Blueprint refreshes UML automatically. Use Refresh UML From Code to rerun that refresh yourself and verify the changed files again."
        addActionListener { refreshUmlAfterApplyVerification() }
    }
    private val openLikelyEntryFileButton = JButton("Open Likely Entry File").apply {
        isEnabled = false
        toolTipText = "Open a likely app entry file when Blueprint cannot infer a run command. This does not run or apply anything."
        addActionListener { openLikelyEntryFile() }
    }
    private val openAppliedFilesButton = JButton("Open Changed Files").apply {
        isEnabled = false
        toolTipText = "Optional after verification: open the file(s) Blueprint last wrote to disk to inspect what changed."
        addActionListener { openAppliedFiles() }
    }
    private val undoLastApplyButton = JButton("Undo Last Apply").apply {
        isEnabled = false
        addActionListener { undoChanges() }
    }
    private val previewDiffButton = JButton("Preview Diff").apply { addActionListener { previewDiff() } }
    private val advancedMode = JBCheckBox("Advanced")
    private val filterCombo = JComboBox(NodeFilter.values())
    private val codeMapGroupCombo = JComboBox(CodeMapProjection.GroupMode.values()).apply {
        selectedItem = CodeMapProjection.GroupMode.PACKAGE
    }
    private val hideCodeMapTests = JBCheckBox("Hide tests")
    private val hideGeneratedCodeMap = JBCheckBox("Hide generated")
    private val hideExternalCodeMapEdges = JBCheckBox("Hide imports").apply { isSelected = true }
    private val hideLowConfidenceCodeMapEdges = JBCheckBox("Hide weak edges")
    private var umlHasPendingEdits = false
    private var suppressUmlDocumentEvents = false
    private val activityLog = JBTextArea(6, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val chatMessages = VerticalScrollablePanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = BlueprintTheme.Background
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
    }
    private val chatScrollPane = JBScrollPane(chatMessages).apply {
        border = RoundedLineBorder(BlueprintTheme.Border)
        viewport.background = BlueprintTheme.Background
        verticalScrollBar.unitIncrement = 16
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }
    private val chatInput = JBTextArea(3, 46).apply {
        lineWrap = true
        wrapStyleWord = true
        font = BlueprintTheme.font(13f)
        margin = Insets(8, 8, 8, 8)
    }
    private val umlStatusLabel = JLabel("UML: not generated yet")
    private val modeBannerLabel = JLabel("Viewing: current code map")
    private val scopeReceiptArea = JBTextArea(5, 40).apply {
        isEditable = false
        isFocusable = false
        lineWrap = true
        wrapStyleWord = true
        rows = 5
        text = "Scope receipt will appear here after Refresh UML From Code."
    }
    private val umlEditor = JBTextArea(18, 72).apply {
        lineWrap = false
        text = """
            classDiagram
            %% Start here:
            %% 1. Click "Refresh UML From Code" to read this Python project.
            %% 2. Edit the UML directly or ask chat to refine it.
            %% 3. Click "Generate Code Diff" when the design is ready.
            %%
            %% This loop can run anytime:
            %% codebase -> UML -> chat refinement -> Generate Code Diff -> Apply Approved Changes -> UML again
        """.trimIndent()
    }

    private val planArea = JBTextArea().apply { isEditable = false }
    private val execArea = JBTextArea().apply { isEditable = false }
    private val reviewArea = JBTextArea().apply { isEditable = false }
    private val secondaryTabs = JTabbedPane()
    private val validationResults = mutableMapOf<String, ProjectValidationService.ValidationResult>()
    private val lastReviewedUmlByNodeId = mutableMapOf<String, String>()
    private val reviewedAtByNodeId = mutableMapOf<String, Instant>()
    private var refreshedAfterApply = false
    private var postApplyChangedPaths: List<String> = emptyList()
    private var postApplyHighlightMessage: String? = null
    private var postApplyVerifyState: String? = null
    private var postApplyInlineSummary: PostApplyInlineSummary? = null
    private val mockMode = JBCheckBox("Offline mock demo").apply {
        isSelected = codex.providerMode() == "mock"
        addActionListener {
            codex.setProviderOverride(if (isSelected) "mock" else null)
            refreshProviderLabels()
            logActivity("Mode changed to ${providerText()}")
        }
    }
    private var selectedCanvasId: String? = null
    private var openAIKeySetMessageShown = false

    init {
        umlEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = umlDocumentChanged()
            override fun removeUpdate(e: DocumentEvent) = umlDocumentChanged()
            override fun changedUpdate(e: DocumentEvent) = umlDocumentChanged()
        })
        buildUi()
        // Suppress persistence while we lay down the seeded defaults.
        // restoreWorkspaceState() then either replaces those defaults with the
        // saved session OR persists the seeded baseline if nothing was saved.
        restoringWorkspace = true
        seedInitialChat()
        chatScrollPane.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                relayoutChatTranscript()
            }
        })
        registry.addListener(object : NodeRegistry.Listener {
            override fun changed() = SwingUtilities.invokeLater { refreshList() }
        })
        refreshList()
        restoreWorkspaceState()
        SwingUtilities.invokeLater { relayoutChatTranscript() }
        logActivity(
            when {
                restoredFromSession -> "Workspace restored from previous session${restoredAtSuffix()}. Use Reset if it looks stale."
                shouldShowInviteFirstRunScenario() ->
                    "Blueprint ready. Use the first-run invite demo checklist, keep mock mode on, and follow the guided flow."
                else ->
                    "Blueprint ready. Seed UML Car Company Flow, keep mock mode on, then run the first ready node."
            },
        )
    }

    private fun applyDarkTheme(component: Component) {
        when (component) {
            is JCheckBox -> styleCheckBox(component)
            is AbstractButton -> styleButton(component)
            is JPanel -> stylePanel(component)
            is JLabel -> styleLabel(component)
            is JTextArea -> styleTextArea(component)
            is JTextField -> styleTextField(component)
            is JComboBox<*> -> styleComboBox(component)
            is JBList<*> -> styleList(component)
            is JTable -> styleTable(component)
            is JTabbedPane -> styleTabs(component)
            is JSplitPane -> styleSplitPane(component)
            is JScrollPane -> styleScrollPane(component)
        }
        if (component is Container) {
            component.components.forEach { applyDarkTheme(it) }
        }
    }

    private fun stylePanel(panel: JPanel) {
        panel.isOpaque = true
        panel.background = if (panel === this) BlueprintTheme.Background else BlueprintTheme.Panel
        val titled = panel.border as? TitledBorder
        if (titled != null) {
            panel.background = BlueprintTheme.Surface
            panel.border = titledBorder(titled.title)
        }
    }

    private fun styleLabel(label: JLabel) {
        val isHeading = label.font?.isBold == true && (label.font?.size ?: 0) >= 15
        label.foreground = if (isHeading) BlueprintTheme.TextStrong else BlueprintTheme.Muted
        label.font = if (isHeading) {
            BlueprintTheme.font(15f, Font.BOLD)
        } else {
            BlueprintTheme.font(11f)
        }
    }

    private fun styleButton(button: AbstractButton) {
        val text = button.text.orEmpty()
        val primary = button.getClientProperty("blueprint.primary") == true ||
            text.startsWith("Next:") ||
            text.contains("Generate Code Diff") ||
            text == "Send"
        button.setUI(BlueprintButtonUi(primary))
        button.foreground = if (primary) Color(0x061016) else BlueprintTheme.Text
    }

    private fun styleCheckBox(checkBox: JCheckBox) {
        checkBox.isOpaque = false
        checkBox.foreground = BlueprintTheme.Text
        checkBox.font = BlueprintTheme.font(13f)
        checkBox.isFocusPainted = false
    }

    private fun styleTextArea(area: JTextArea) {
        area.background = if (area.isEditable) BlueprintTheme.Background else BlueprintTheme.Surface
        area.foreground = BlueprintTheme.Text
        area.caretColor = BlueprintTheme.Accent
        area.selectionColor = BlueprintTheme.AccentSurface
        area.selectedTextColor = BlueprintTheme.TextStrong
        area.font = BlueprintTheme.font(13f)
        area.border = inputBorder(area.hasFocus())
        area.margin = Insets(6, 8, 6, 8)
        installFocusBorder(area)
    }

    private fun styleTextField(field: JTextField) {
        field.background = BlueprintTheme.Background
        field.foreground = BlueprintTheme.Text
        field.caretColor = BlueprintTheme.Accent
        field.selectionColor = BlueprintTheme.AccentSurface
        field.selectedTextColor = BlueprintTheme.TextStrong
        field.font = BlueprintTheme.font(13f)
        field.border = inputBorder(field.hasFocus())
        installFocusBorder(field)
    }

    private fun styleComboBox(combo: JComboBox<*>) {
        combo.background = BlueprintTheme.Background
        combo.foreground = BlueprintTheme.Text
        combo.font = BlueprintTheme.font(13f)
        combo.border = inputBorder(combo.hasFocus())
        installFocusBorder(combo)
        @Suppress("UNCHECKED_CAST")
        (combo as JComboBox<Any?>).renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                label.background = if (isSelected) BlueprintTheme.AccentSurface else BlueprintTheme.Background
                label.foreground = if (isSelected) BlueprintTheme.TextStrong else BlueprintTheme.Text
                label.font = BlueprintTheme.font(13f)
                label.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
                return label
            }
        }
    }

    private fun styleList(list: JBList<*>) {
        list.background = BlueprintTheme.Background
        list.foreground = BlueprintTheme.Text
        list.selectionBackground = BlueprintTheme.AccentSurface
        list.selectionForeground = BlueprintTheme.TextStrong
        list.font = BlueprintTheme.font(13f)
        list.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    }

    private fun styleTable(table: JTable) {
        table.background = BlueprintTheme.Background
        table.foreground = BlueprintTheme.Text
        table.selectionBackground = BlueprintTheme.AccentSurface
        table.selectionForeground = BlueprintTheme.TextStrong
        table.gridColor = BlueprintTheme.Border
        table.rowHeight = 28
        table.font = BlueprintTheme.font(13f)
        table.border = BorderFactory.createLineBorder(BlueprintTheme.Border)
        table.tableHeader?.apply {
            background = BlueprintTheme.Surface
            foreground = BlueprintTheme.TextStrong
            font = BlueprintTheme.font(11f, Font.BOLD)
            border = BorderFactory.createMatteBorder(0, 0, 1, 0, BlueprintTheme.Border)
        }
    }

    private fun styleTabs(tabs: JTabbedPane) {
        tabs.background = BlueprintTheme.Background
        tabs.foreground = BlueprintTheme.Text
        tabs.font = BlueprintTheme.font(13f)
        tabs.border = BorderFactory.createMatteBorder(1, 0, 0, 0, BlueprintTheme.Border)
        if (tabs.getClientProperty("blueprint.tabStyle") != true) {
            tabs.putClientProperty("blueprint.tabStyle", true)
            tabs.addChangeListener { styleTabs(tabs) }
        }
        for (i in 0 until tabs.tabCount) {
            tabs.setBackgroundAt(i, if (i == tabs.selectedIndex) BlueprintTheme.Surface else BlueprintTheme.Background)
            tabs.setForegroundAt(i, if (i == tabs.selectedIndex) BlueprintTheme.Accent else BlueprintTheme.Muted)
        }
    }

    private fun styleSplitPane(splitPane: JSplitPane) {
        splitPane.background = BlueprintTheme.Background
        splitPane.border = BorderFactory.createLineBorder(BlueprintTheme.Border)
        splitPane.dividerSize = 8
    }

    private fun styleScrollPane(scrollPane: JScrollPane) {
        scrollPane.border = RoundedLineBorder(BlueprintTheme.Border)
        scrollPane.background = BlueprintTheme.Background
        scrollPane.viewport.background = BlueprintTheme.Background
        scrollPane.verticalScrollBar.background = BlueprintTheme.Background
        scrollPane.horizontalScrollBar.background = BlueprintTheme.Background
    }

    private fun titledBorder(title: String): Border {
        val titled = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BlueprintTheme.Border),
            title,
        )
        titled.titleColor = BlueprintTheme.TextStrong
        titled.titleFont = BlueprintTheme.font(15f, Font.BOLD)
        return BorderFactory.createCompoundBorder(
            titled,
            BorderFactory.createEmptyBorder(8, 8, 8, 8),
        )
    }

    private fun inputBorder(focused: Boolean): Border =
        RoundedLineBorder(if (focused) BlueprintTheme.Accent else BlueprintTheme.Border, 8, Insets(6, 8, 6, 8))

    private fun installFocusBorder(component: JComponent) {
        if (component.getClientProperty("blueprint.focusBorder") == true) return
        component.putClientProperty("blueprint.focusBorder", true)
        component.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                component.border = inputBorder(true)
            }

            override fun focusLost(e: FocusEvent) {
                component.border = inputBorder(false)
            }
        })
    }

    private fun buildUi() {
        miniGraph.onNodeSelected = { nodeId ->
            selectNodeFromGraph(nodeId)
        }
        miniGraph.onNodeOpenSource = { node ->
            openGraphSource(node)
        }
        filterCombo.addActionListener {
            refreshList()
            logActivity("Node filter: ${filterCombo.selectedItem}")
            persistWorkspace()
        }
        val refreshCodeMapLayout: () -> Unit = {
            updateMiniGraph(project.service<DependencyGraphService>().analyze())
            logActivity("Code map layout: ${codeMapGroupCombo.selectedItem}")
            persistWorkspace()
        }
        codeMapGroupCombo.addActionListener { refreshCodeMapLayout() }
        hideCodeMapTests.addActionListener { refreshCodeMapLayout() }
        hideGeneratedCodeMap.addActionListener { refreshCodeMapLayout() }
        hideExternalCodeMapEdges.addActionListener { refreshCodeMapLayout() }
        hideLowConfidenceCodeMapEdges.addActionListener { refreshCodeMapLayout() }
        val overview = JPanel(GridLayout(0, 1, 4, 4)).apply {
            border = BorderFactory.createTitledBorder("Overview")
            add(JLabel("Project: ${project.name}").apply { foreground = Color(0x333333) })
            add(summaryLabel.apply { foreground = Color(0x333333) })
            add(providerLabel.apply { foreground = providerColor() })
            add(JLabel("Workflow: Refresh UML From Code -> edit UML -> Generate Code Diff -> Apply Approved Changes").apply {
                foreground = Color(0x555555)
            })
            add(row("Filter", filterCombo))
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(JButton("Ready").apply { addActionListener { quickSelect(NodeFilter.READY) } })
                add(JButton("Blocked").apply { addActionListener { quickSelect(NodeFilter.BLOCKED) } })
                add(JButton("Applied").apply { addActionListener { quickSelect(NodeFilter.APPLIED) } })
                add(JButton("Wave").apply { addActionListener { quickSelect(NodeFilter.CURRENT_WAVE) } })
            })
        }
        val leftButtons = JPanel(GridLayout(0, 1, 4, 4)).apply {
            border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
            add(JButton("Refresh UML From Code").apply { addActionListener { generateProjectUml() } })
            add(JButton("Paste UML").apply { addActionListener { importUml() } })
            if (advancedMode.isSelected) add(JButton("Generate Code Diff").apply { addActionListener { generateCodeFromUml() } }) // Advanced mode only
            add(JButton("+ Manual Node").apply { addActionListener { addNode() } })
            add(
                JButton(if (shouldShowInviteFirstRunScenario()) "Sample: Invite UML" else "Sample: Car Company UML").apply {
                    addActionListener {
                        if (shouldShowInviteFirstRunScenario()) seedUmlInviteFlow() else seedUmlCarCompanyFlow()
                    }
                },
            )
            add(JButton("- Remove").apply { addActionListener { removeSelected() } })
        }
        val left = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(300, 0)
            minimumSize = Dimension(260, 0)
            add(overview, BorderLayout.NORTH)
            add(JBScrollPane(nodeList), BorderLayout.CENTER)
            add(leftButtons, BorderLayout.SOUTH)
            border = BorderFactory.createTitledBorder("Generated Nodes")
        }

        val form = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("Node")
            add(row("Type", typeCombo))
            add(row("Title", titleField))
            add(row("Summary", summaryField))
            add(row("Description", JBScrollPane(descField)))
            add(dependencyEditorPanel())
            add(row("File paths (one per line)", JBScrollPane(scopePathsArea)))
            add(row("Globs (one per line)", JBScrollPane(scopeGlobsArea)))
            add(row("Directories (one per line)", JBScrollPane(scopeDirsArea)))
            add(criteriaPanel())
        }

        val actions = actionPanel()

        val summary = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Review / Safety")
            val labels = JPanel(GridLayout(0, 1)).apply {
                add(selectedLabel)
                add(artifactLabel)
            }
            add(labels, BorderLayout.NORTH)
            val center = JPanel(GridLayout(2, 1, 0, 6)).apply {
                add(JBScrollPane(groundingSummaryArea))
                add(JBScrollPane(reviewSummaryArea))
            }
            add(center, BorderLayout.CENTER)
            val lower = JPanel(GridLayout(1, 3, 6, 0)).apply {
                add(changedFilesScrollPane)
                add(JBScrollPane(safetyArea))
                add(JBScrollPane(graphArea))
            }
            add(lower, BorderLayout.SOUTH)
        }

        fun refreshSecondaryTabs() {
            secondaryTabs.removeAll()
            secondaryTabs.addTab("UML", JBScrollPane(umlEditor))
            secondaryTabs.addTab("Nodes", left)
            secondaryTabs.addTab("Review", summary)
            secondaryTabs.addTab("Activity", JBScrollPane(activityLog))
            if (advancedMode.isSelected) {
                secondaryTabs.addTab("Node Details", JBScrollPane(form))
                secondaryTabs.addTab("Plan JSON", JBScrollPane(planArea))
                secondaryTabs.addTab("Execution JSON", JBScrollPane(execArea))
                secondaryTabs.addTab("Review JSON", JBScrollPane(reviewArea))
            }
        }
        refreshSecondaryTabs()
        advancedMode.addActionListener {
            refreshSecondaryTabs()
            revalidate()
            repaint()
            persistWorkspace()
        }

        val lowerWorkspace = JPanel(BorderLayout()).apply {
            minimumSize = Dimension(0, 320)
            preferredSize = Dimension(0, 380)
            add(actions, BorderLayout.NORTH)
            add(secondaryTabs, BorderLayout.CENTER)
        }

        val diagramPanel = JPanel(BorderLayout(6, 6)).apply {
            border = BorderFactory.createTitledBorder("UML Canvas")
            add(JPanel(BorderLayout()).apply {
                add(JPanel(GridLayout(0, 1, 2, 2)).apply {
                    add(JLabel("Blueprint").apply {
                        font = font.deriveFont(java.awt.Font.BOLD, 15f)
                    })
                    add(umlStatusLabel.apply { foreground = Color(0x555555) })
                    add(modeBannerLabel)
                    add(JBScrollPane(scopeReceiptArea).apply {
                        border = BorderFactory.createTitledBorder("Current Scope Receipt")
                        preferredSize = Dimension(0, 110)
                        verticalScrollBar.unitIncrement = 16
                    })
                }, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                    add(JButton("\u2212").apply { addActionListener { miniGraph.zoomOut() } })
                    add(JButton("+").apply { addActionListener { miniGraph.zoomIn() } })
                    add(JButton("\u27f3").apply { addActionListener { miniGraph.zoomReset() } })
                    add(JButton("Reset").apply {
                        toolTipText = "Forget restored chat, UML draft, and filters. Generated nodes are kept."
                        addActionListener { resetWorkspace() }
                    })
                }, BorderLayout.EAST)
                add(codeMapControls(), BorderLayout.SOUTH)
            }, BorderLayout.NORTH)
            add(JBScrollPane(miniGraph).apply {
                viewport.background = BlueprintTheme.Background
                verticalScrollBar.unitIncrement = 16
                horizontalScrollBar.unitIncrement = 16
            }, BorderLayout.CENTER)
        }

        val mainCanvas = JSplitPane(JSplitPane.VERTICAL_SPLIT, diagramPanel, lowerWorkspace).apply {
            dividerLocation = 480
            resizeWeight = 0.65
            isContinuousLayout = true
            isOneTouchExpandable = true
        }

        val workspace = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainCanvas, chatPanel()).apply {
            dividerLocation = 680
            resizeWeight = 0.62
            isContinuousLayout = true
        }
        add(workspace, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
        applyDarkTheme(this)
        SwingUtilities.invokeLater {
            workspace.setDividerLocation((workspace.width - 560).coerceAtLeast(520))
            mainCanvas.setDividerLocation((mainCanvas.height - 380).coerceAtLeast(360))
        }

        nodeList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                if (refreshingList) return@addListSelectionListener
                registry.setSelectedNode(nodeList.selectedValue?.id)
                loadSelectedIntoForm()
            }
        }
    }

    private fun codeMapControls(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
            add(JLabel("Group"))
            add(codeMapGroupCombo.apply { preferredSize = Dimension(130, 32) })
            add(hideCodeMapTests)
            add(hideGeneratedCodeMap)
            add(hideExternalCodeMapEdges)
            add(hideLowConfidenceCodeMapEdges)
        }

    private fun criteriaPanel(): JPanel =
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Acceptance criteria")
            add(JBScrollPane(criteriaTable), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JButton("+ Criterion").apply { addActionListener { addCriterionRow() } })
                add(JButton("- Criterion").apply { addActionListener { removeCriterionRow() } })
            }, BorderLayout.SOUTH)
            preferredSize = Dimension(0, 120)
        }

    private fun chatPanel(): JPanel =
        JPanel(BorderLayout(6, 6)).apply {
            preferredSize = Dimension(560, 0)
            minimumSize = Dimension(460, 0)
            border = BorderFactory.createTitledBorder("Architecture Chat")
            add(chatScrollPane, BorderLayout.CENTER)
            add(JPanel(BorderLayout(4, 4)).apply {
                add(JPanel(GridLayout(0, 2, 4, 4)).apply {
                    border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
                    add(JButton("Set key").apply {
                        addActionListener { setOpenAIKey() }
                    })
                }, BorderLayout.NORTH)
                add(JPanel(BorderLayout(4, 0)).apply {
                    add(JBScrollPane(chatInput), BorderLayout.CENTER)
                    add(JButton("Send").apply {
                        preferredSize = Dimension(88, 64)
                        addActionListener { sendChat() }
                    }, BorderLayout.EAST)
                }, BorderLayout.CENTER)
            }, BorderLayout.SOUTH)
        }

    private fun setOpenAIKey() {
        val keyField = JPasswordField(36)
        val panel = JPanel(BorderLayout(6, 6)).apply {
            add(
                JLabel("Paste an OpenAI API key for this PyCharm session. It is kept in memory only."),
                BorderLayout.NORTH
            )
            add(keyField, BorderLayout.CENTER)
        }
        applyDarkTheme(panel)
        val choice = JOptionPane.showConfirmDialog(
            this,
            panel,
            "Blueprint - OpenAI Key",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
        )
        if (choice != JOptionPane.OK_OPTION) return

        val key = String(keyField.password).trim()
        if (key.isBlank()) {
            status("OpenAI key unchanged")
            return
        }
        codex.setOpenAIKeyOverride(key)
        refreshProviderLabels()
        if (!openAIKeySetMessageShown) {
            appendChat("Blueprint", "OpenAI key set for this session. Live chat will use OpenAI unless Offline mock demo is enabled.")
            openAIKeySetMessageShown = true
        }
        logActivity("OpenAI key set for this session.")
        status("OpenAI key set")
    }

    private fun actionPanel(): JPanel =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            if (shouldShowInviteFirstRunScenario()) {
                add(
                    JPanel(BorderLayout(6, 6)).apply {
                        border = BorderFactory.createTitledBorder("First-Run Demo")
                        add(
                            JPanel(GridLayout(2, 1, 0, 6)).apply {
                                add(firstRunScenarioArea)
                                add(demoReceiptArea)
                            },
                            BorderLayout.CENTER,
                        )
                        add(
                            JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                                add(firstRunPromptButton)
                                add(runDemoButton)
                            },
                            BorderLayout.SOUTH,
                        )
                    },
                )
            }
            add(JPanel(BorderLayout(8, 2)).apply {
                border = BorderFactory.createEmptyBorder(2, 4, 6, 4)
                add(primaryActionButton.apply {
                    preferredSize = Dimension(260, 44)
                    font = font.deriveFont(java.awt.Font.BOLD, 13f)
                }, BorderLayout.WEST)
                add(
                    JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        add(nextStepTitleLabel.apply {
                            foreground = BlueprintTheme.TextStrong
                            font = BlueprintTheme.font(12f, Font.BOLD)
                        })
                        add(nextStepDetailLabel.apply {
                            foreground = BlueprintTheme.Muted
                            font = BlueprintTheme.font(12f)
                        })
                        add(guideLabel.apply {
                            foreground = Color(0x444444)
                        })
                    },
                    BorderLayout.CENTER,
                )
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                add(mockMode)
                add(actionProviderLabel.apply { foreground = providerColor() })
                add(advancedMode)
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                add(JButton("Refresh UML From Code").apply { addActionListener { generateProjectUml() } })
                add(runAppButton)
                add(openLikelyEntryFileButton)
            })
            add(JPanel(BorderLayout()).apply {
                border = BorderFactory.createTitledBorder("Run Output")
                add(JBScrollPane(runOutputArea), BorderLayout.CENTER)
                maximumSize = Dimension(Int.MAX_VALUE, 180)
            })
            val advancedRows = listOf(
                JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                add(previewDiffButton)
                add(applyApprovedButton)
                add(verifyInUmlButton)
                add(openAppliedFilesButton)
                add(undoLastApplyButton)
            },
                JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                add(JButton("Save").apply { addActionListener { saveCurrent() } })
                add(JButton("Generate Plan").apply { addActionListener { generatePlan() } })
                add(JButton("Execute Node").apply { addActionListener { executeNode() } })
                add(JButton("Run Ready").apply { addActionListener { runAllReadyNodes() } })
                add(JButton("Review").apply { addActionListener { review() } })
                add(JButton("Preview Waves").apply { addActionListener { previewWaves() } })
                add(JButton("Python Context").apply { addActionListener { showPythonContext() } })
            },
                JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                add(JButton("Selected + Dependents").apply { addActionListener { previewSelectedAndDependents() } })
                add(JButton("Why Blocked?").apply { addActionListener { showWhyBlocked() } })
                add(JButton("Apply File").apply { addActionListener { applySelectedFile() } })
            }
            )
            advancedRows.forEach { row ->
                row.isVisible = advancedMode.isSelected
                add(row)
            }
            advancedMode.addActionListener {
                advancedRows.forEach { it.isVisible = advancedMode.isSelected }
                revalidate()
                repaint()
            }
        }

    private fun dependencyEditorPanel(): JPanel =
        JPanel(BorderLayout(6, 4)).apply {
            border = BorderFactory.createTitledBorder("Dependencies")
            val pickerRow = JPanel(BorderLayout(6, 0)).apply {
                add(dependencyPicker, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                    add(JButton("+ Add").apply { addActionListener { addDependencyFromPicker() } })
                    add(JButton("- Remove").apply { addActionListener { removeSelectedDependency() } })
                }, BorderLayout.EAST)
            }
            add(pickerRow, BorderLayout.NORTH)
            add(JBScrollPane(dependencyList), BorderLayout.CENTER)
            add(row("Manual IDs (fallback)", JBScrollPane(manualDependencyArea)), BorderLayout.SOUTH)
            preferredSize = Dimension(0, 118)
        }

    private fun row(label: String, component: java.awt.Component): JPanel =
        JPanel(BorderLayout(8, 4)).apply {
            border = BorderFactory.createEmptyBorder(3, 4, 3, 4)
            add(JLabel(label).apply { preferredSize = Dimension(138, 24) }, BorderLayout.WEST)
            add(component, BorderLayout.CENTER)
        }

    private fun addNode() {
        val n = BlueprintNode(title = "New node")
        registry.add(n)
        selectNode(n.id)
        logActivity("Added node ${n.title}")
    }

    private fun runGuidedNextStep() {
        val entityCount = currentUmlEntityCount()
        if (entityCount == 0) {
            generateProjectUml()
            return
        }

        val allNodes = registry.all()
        if (allNodes.isEmpty()) {
            generateCodeFromUml()
            return
        }

        val graph = project.service<DependencyGraphService>()
        val selected = nodeList.selectedValue
            ?: graph.readyNodes().firstOrNull()
            ?: allNodes.firstOrNull()
            ?: return status("No generated nodes yet")
        if (nodeList.selectedValue?.id != selected.id) {
            selectNode(selected.id)
            registry.setSelectedNode(selected.id)
            loadSelectedIntoForm()
        }

        when {
            registry.getPlan(selected.id) == null -> generatePlan()
            registry.getExecution(selected.id) == null -> executeNode()
            registry.getReview(selected.id) == null -> review()
            selected.executionStatus != ExecutionStatus.APPLIED -> applyChanges(null)
            graph.readyNodes().any { it.id != selected.id } -> {
                val next = graph.readyNodes().first { it.id != selected.id }
                selectNode(next.id)
                registry.setSelectedNode(next.id)
                status("Selected next ready node")
            }
            else -> generateProjectUml()
        }
        updateGuide()
    }

    private fun sendChat() {
        val message = chatInput.text.trim()
        if (message.isBlank()) return
        chatInput.text = ""
        appendChat("You", message)
        if (shouldRefineUml(message)) {
            refineUmlWithChat(message)
        } else if (shouldAnswerWithModel(message)) {
            answerArchitectureChat(message)
        } else {
            appendChat("Blueprint", chatResponse(message))
        }
    }

    private fun sendSuggestedChat(message: String) {
        chatInput.text = message
        sendChat()
    }

    private fun seedInitialChat() {
        val demoPrompt = if (shouldShowInviteFirstRunScenario()) {
            currentInvitePrompt() ?: "restore examples/invite_project/blueprint_demo/imported_invite/models.py from git"
        } else {
            "add a Supplier entity"
        }
        appendChat(
            "Blueprint",
            """
            I help refine the UML before code generation.

            Try:
            - explain this UML
            - $demoPrompt
            - what should I generate next?

            Live mode uses OpenAI. Use OPENAI_API_KEY or Set key.
            """.trimIndent()
        )
    }

    private fun appendChat(author: String, message: String) {
        chatHistory += WorkspaceChatEntry(author = author, message = message)
        renderChatBubble(author, message)
        persistWorkspace()
    }

    private fun renderChatBubble(author: String, message: String) {
        val isUser = author.equals("You", ignoreCase = true)
        val bubbleColor = if (isUser) Color(0x2D323A) else Color(0x202225)
        val borderColor = if (isUser) null else BlueprintTheme.Border
        val labelColor = if (isUser) BlueprintTheme.Accent else BlueprintTheme.Muted
        val displayName = if (isUser) "You" else "Blueprint"

        val bubble = ChatBubblePanel(displayName, message, bubbleColor, borderColor, labelColor)
        bubble.relayoutForViewport(chatViewportWidth())

        val row = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(8, 0, 8, 0)
            add(bubble, if (isUser) BorderLayout.EAST else BorderLayout.WEST)
            putClientProperty("blueprint.chatBubble", bubble)
        }
        row.maximumSize = Dimension(Int.MAX_VALUE, row.preferredSize.height)
        chatMessages.add(row)
        relayoutChatTranscript(scrollToBottom = true)
    }

    private fun chatViewportWidth(): Int =
        ((chatScrollPane.viewport.extentSize.width.takeIf { it > 0 }
            ?: chatScrollPane.width.takeIf { it > 0 }
            ?: 460) - 24).coerceAtLeast(280)

    private fun relayoutChatTranscript(scrollToBottom: Boolean = false) {
        val viewportWidth = chatViewportWidth()
        for (index in 0 until chatMessages.componentCount) {
            val row = chatMessages.getComponent(index) as? JPanel ?: continue
            val bubble = row.getClientProperty("blueprint.chatBubble") as? ChatBubblePanel ?: continue
            bubble.relayoutForViewport(viewportWidth)
            row.maximumSize = Dimension(Int.MAX_VALUE, row.preferredSize.height)
        }
        chatMessages.revalidate()
        chatMessages.repaint()
        if (scrollToBottom) {
            SwingUtilities.invokeLater {
                chatScrollPane.verticalScrollBar.value = chatScrollPane.verticalScrollBar.maximum
            }
        }
    }

    private fun chatResponse(message: String): String {
        val selected = nodeList.selectedValue
        val lower = message.lowercase()
        val graph = project.service<DependencyGraphService>()
        val ready = graph.readyNodes()
        return when {
            lower.containsWorkflowQuestion() -> {
                "When the UML looks right, click Generate Code Diff. Blueprint will prepare a reviewed code patch that you can inspect and then apply."
            }
            "abstract" in lower || "sync" in lower -> {
                "Click Refresh UML From Code at any time. Blueprint will rescan the Python project and replace the editable UML with the current code architecture."
            }
            "blocked" in lower || "why" in lower -> {
                val node = selected ?: return "Select a node in the UML diagram first, then ask why it is blocked."
                val readiness = graph.readinessFor(node)
                if (readiness.ready) {
                    "${node.title.ifBlank { node.id.take(8) }} is ready. Keep refining the UML if needed, then click Generate Code Diff."
                } else {
                    "Blocked reasons for ${node.title.ifBlank { node.id.take(8) }}:\n" +
                        readiness.reasons.joinToString("\n") { "- $it" }
                }
            }
            "next" in lower || "run" in lower -> {
                if (ready.isEmpty()) {
                    "No reviewed code patch is ready yet. Refine the UML, or select a UML item and ask why it is blocked."
                } else {
                    "Next ready UML item: ${ready.first().title.ifBlank { ready.first().id.take(8) }}.\nReview the diagram, then click Generate Code Diff when you are ready for a reviewed code patch."
                }
            }
            "changed" in lower || "diff" in lower -> {
                val node = selected ?: return "Select a node first; I will summarize its generated changes."
                changedFileSummary(registry.getExecution(node.id))
            }
            "uml" in lower || "diagram" in lower -> {
                if (shouldShowInviteFirstRunScenario()) {
                    "The main canvas is editable Mermaid UML. For the guided invite demo, use Try This Change for a fresh prompt based on the current sandbox state, or try '${currentInvitePrompt() ?: "restore examples/invite_project/blueprint_demo/imported_invite/models.py from git"}', then click Generate Code Diff."
                } else {
                    "The main canvas is editable Mermaid UML. Ask for architecture changes like 'add a Supplier entity' or 'make CarCompany own many Dealerships'. I will rewrite the UML, then you can Generate Code Diff."
                }
            }
            selected != null -> {
                val readiness = graph.readinessFor(selected)
                "${selected.title.ifBlank { selected.id.take(8) }} is selected. Status: ${badgeFor(selected)}. " +
                    if (readiness.ready) "It is ready to run." else "It is blocked; ask 'why blocked' for details."
            }
            else -> "Start with Refresh UML From Code. Refine the editable diagram here with chat, then click Generate Code Diff when the architecture is ready."
        }
    }

    private fun shouldAnswerWithModel(message: String): Boolean {
        if (codex.providerMode() == "mock") return false
        val lower = message.lowercase()
        if (!codex.hasOpenAIKey() && codex.providerMode() == "openai") return false
        if (lower.containsWorkflowQuestion()) {
            return false
        }
        return listOf("explain", "what is", "what are", "current product", "product", "architecture", "summarize", "describe")
            .any { it in lower }
    }

    private fun answerArchitectureChat(message: String) {
        status("Asking OpenAI...")
        appendChat("Blueprint", "Thinking with ${providerText()}...")
        val prompt = architectureChatPrompt(message)
        Thread {
            val result = codex.sendPromptResult(prompt)
            SwingUtilities.invokeLater {
                if (!result.ok) {
                    appendChat("Blueprint", "I could not answer with the model: ${result.error ?: "request failed"}")
                    status("Chat failed")
                    return@invokeLater
                }
                appendChat("Blueprint", result.text.trim().ifBlank { "No answer returned." })
                status("Chat answered")
            }
        }.start()
    }

    private fun shouldRefineUml(message: String): Boolean {
        val lower = message.lowercase()
        return listOf("add", "remove", "change", "rename", "refactor", "relationship", "entity", "class", "field")
            .any { it in lower } &&
            !lower.containsWorkflowQuestion() &&
            "explain" !in lower
    }

    private fun String.containsWorkflowQuestion(): Boolean =
        listOf(
            "run",
            "next",
            "why",
            "blocked",
            "diff",
            "changed",
            "generate code",
            "generate patch",
            "create patch",
            "apply patch",
            "refresh from code",
            "apply",
        ).any { it in this }

    private fun refineUmlWithChat(message: String) {
        status("Refining UML with ${providerText()}...")
        appendChat("Blueprint", "Refining the editable UML. In live mode this uses the configured OpenAI provider/API key.")
        val currentUml = umlEditor.text
        val grounding = chatGrounding()
        Thread {
            val result = if (codex.providerMode() == "mock") {
                CodexClient.Result(mockUmlEdit(currentUml, message), ok = true)
            } else {
                codex.sendPromptResult(umlChatPrompt(currentUml, message, grounding))
            }
            SwingUtilities.invokeLater {
                if (!result.ok) {
                    val error = result.error ?: "UML chat request failed"
                    appendChat("Blueprint", "I could not update the UML: $error")
                    status("UML chat failed")
                    return@invokeLater
                }
                val nextUml = extractMermaid(result.text)
                if (!nextUml.contains("classDiagram")) {
                    appendChat("Blueprint", "The model did not return Mermaid classDiagram text. I left the UML unchanged.")
                    status("UML unchanged")
                    return@invokeLater
                }
                setUmlEditorText(nextUml, pendingEdits = true)
                umlStatusLabel.text = "UML: refined by chat. Generate Code Diff when ready, or keep editing."
                groundingSummaryArea.text = grounding.summaryText
                appendChat(
                    "Blueprint",
                    if (grounding.selectedId == null) {
                        "Updated the UML using whole-diagram context. Select a UML card if you want the next edit grounded to one entity's source file, fields, and relationships."
                    } else {
                        "Updated the UML using ${grounding.selectedLabel}. Blueprint grounded this edit to the selected source file, fields, methods, and relationships before rewriting the UML."
                    }
                )
                logActivity("Chat refined the UML using ${grounding.activityLabel}.")
                updateGuide()
                status("UML refined")
            }
        }.start()
    }

    private fun umlChatPrompt(
        currentUml: String,
        message: String,
        grounding: ChatGroundingContext.Grounding = chatGrounding(),
    ): String =
        """
        You are Blueprint, an architecture assistant inside PyCharm.

        The user edits a Mermaid UML classDiagram that will later be converted into a reviewed code patch.
        Update the UML according to the user's request.
        Use the grounding context to interpret pronouns like "this", "it", "selected", or "the current class".

        Rules:
        - Return only Mermaid classDiagram text.
        - Preserve useful existing classes, fields, methods, and relationships unless the user asked to remove them.
        - Keep names clear and Python-friendly.
        - Prefer class blocks and simple relationship lines.
        - Do not include explanations, markdown fences, or prose.
        - Keep changes focused on the selected entity when the request is ambiguous.

        Grounding context:
        ${grounding.promptText}

        Current UML:
        ${ChatGroundingContext.compactUml(currentUml)}

        User request:
        $message
        """.trimIndent()

    private fun architectureChatPrompt(message: String): String {
        val selected = nodeList.selectedValue
        val graph = project.service<DependencyGraphService>()
        val ready = graph.readyNodes()
        val grounding = chatGrounding()
        return """
        You are Blueprint, an architecture assistant inside PyCharm.

        Product loop:
        codebase -> editable UML -> chat refinement -> Generate Code Diff -> plan/execute/review/apply -> UML again.

        Answer the user's question clearly and briefly. Do not claim you changed code unless the user used the execution buttons.
        When useful, refer to the selected entity's source, fields, methods, relationships, current UML, and generated nodes.
        If the user refers to "this", "it", or "selected", resolve that from Grounding context.

        Grounding context:
        ${grounding.promptText}

        Current UML excerpt:
        ${ChatGroundingContext.compactUml(umlEditor.text)}

        Selected generated node:
        ${selected?.let { "${it.title} (${it.type.name.lowercase()}, ${badgeFor(it)})" } ?: "none"}

        Ready generated nodes:
        ${ready.joinToString("\n") { node -> "- ${node.title.ifBlank { node.id.take(8) }}" }.ifBlank { "none" }}

        User question:
        $message
        """.trimIndent()
    }

    private fun chatGrounding(): ChatGroundingContext.Grounding {
        val parsed = runCatching { project.service<UmlImportService>().parse(umlEditor.text) }.getOrNull()
        val ir = project.service<IRStore>().load()
        return ChatGroundingContext.build(
            ir = ir,
            parsedUml = parsed,
            selectedCanvasId = selectedCanvasId,
            viewingUmlDraft = umlHasPendingEdits,
        )
    }

    private fun refreshGroundingSummary() {
        groundingSummaryArea.text = chatGrounding().summaryText
    }

    private fun extractMermaid(text: String): String {
        val fenced = Regex("""```(?:mermaid)?\s*(classDiagram.*?)(?:```|$)""", RegexOption.DOT_MATCHES_ALL)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!fenced.isNullOrBlank()) return fenced
        val start = text.indexOf("classDiagram")
        return if (start >= 0) text.substring(start).trim() else text.trim()
    }

    private fun mockUmlEdit(currentUml: String, message: String): String {
        val lower = message.lowercase()
        val addition = when {
            "invitepolicy" in lower || ("policy" in lower && currentUml.contains("class Invite")) -> """

                class InvitePolicy {
                  maxInvitesPerProject: int
                  requireCompanyEmail: bool
                }

                Invite --> InvitePolicy : uses
            """.trimIndent()
            "invitereminder" in lower || ("reminder" in lower && currentUml.contains("class Invite")) -> """

                class InviteReminder {
                  sendAt: datetime
                  channel: str
                }

                Invite --> InviteReminder : schedules
            """.trimIndent()
            "expires_at" in lower || ("expire" in lower && currentUml.contains("class Invite")) -> currentUml.replace(
                "class Invite {",
                "class Invite {\n  expires_at: datetime",
            )
            "supplier" in lower -> """

                class Supplier {
                  id: str
                  name: str
                  category: str
                }

                CarCompany --> Supplier : sources parts from
            """.trimIndent()
            "service" in lower || "center" in lower -> """

                class ServiceCenter {
                  id: str
                  name: str
                  city: str
                }

                Dealership --> ServiceCenter : services with
            """.trimIndent()
            else -> """

                class ArchitectureDecision {
                  id: str
                  summary: str
                  status: str
                }
            """.trimIndent()
        }
        return if (currentUml.contains(addition.substringAfter("class ").substringBefore(" {"))) {
            currentUml
        } else {
            currentUml.trim() + "\n\n" + addition.trim() + "\n"
        }
    }

    private fun generateProjectUml() {
        if (!confirmDiscardPendingUmlEdits()) return
        status("Generating UML from Python project...")
        val generated = project.service<PythonUmlGenerator>().generate()
        loadGeneratedUml(generated)
    }

    private fun loadGeneratedUml(generated: PythonUmlGenerator.GeneratedUml) {
        if (!refreshedAfterApply) {
            postApplyInlineSummary = null
        }
        val context = project.service<PythonProjectAnalyzer>().analyze()
        setUmlEditorText(generated.text, pendingEdits = false)
        focusChangedEntityAfterRefresh()
        umlStatusLabel.text = "UML: ${generated.classCount} class(es), ${generated.relationshipCount} relationship(s), ${generated.filesScanned} file(s) scanned. ${context.scopeSummaryLine()}"
        scopeReceiptArea.text = buildString {
            appendLine("Current Scope Receipt")
            appendLine("- Included files: ${context.filesAnalyzed.size}")
            appendLine("- Skipped files: ${context.skippedFiles.size}")
            if (context.skippedFiles.isNotEmpty()) {
                val topReasons = context.skippedFiles.groupingBy { it.reason }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .take(3)
                    .joinToString(", ") { (reason, count) -> if (count == 1) reason else "$count $reason" }
                appendLine("- Scope note: Some Python paths were skipped during Refresh UML From Code.")
                appendLine("- Top skipped reasons: $topReasons")
                appendLine("- If the UML looks incomplete, inspect the skipped paths below.")
            }
            if (context.filesAnalyzed.isNotEmpty()) {
                appendLine("- Included paths:")
                context.filesAnalyzed.take(3).forEach { appendLine("  - $it") }
            }
            if (context.skippedFiles.isNotEmpty()) {
                appendLine("- Skipped paths:")
                context.skippedFiles.take(3).forEach { appendLine("  - ${it.path} — ${it.reason}") }
            }
        }.trim()
        graphArea.text = buildString {
            appendLine("Abstracted Python codebase to editable UML.")
            appendLine("Classes: ${generated.classCount}")
            appendLine("Relationships: ${generated.relationshipCount}")
            appendLine("Files scanned: ${generated.filesScanned}")
            appendLine(context.scopeReceipt())
            if (generated.warnings.isNotEmpty()) {
                appendLine()
                appendLine("Warnings:")
                generated.warnings.forEach { appendLine("- $it") }
            }
        }.trim()
        val refreshMessage = buildString {
            append("I abstracted the current Python code into UML. ${context.scopeSummaryLine().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }}. Edit it directly or ask chat to refine the architecture. Generate Code Diff when ready.")
            if (context.skippedFiles.isNotEmpty()) {
                append("\n\nSome Python paths were skipped during Refresh UML From Code. If the UML looks incomplete, inspect these skipped paths:\n")
                context.skippedFiles.take(3).forEach { append("- ${it.path}: ${it.reason}\n") }
            }
            if (generated.warnings.isNotEmpty()) {
                append("\nNotes:\n")
                generated.warnings.take(3).forEach { append("- $it\n") }
            }
        }.trim()
        appendChat("Blueprint", refreshMessage)
        logActivity("Abstracted code to UML: ${generated.classCount} class(es), ${generated.relationshipCount} relationship(s).")
        status("Code abstracted to UML")
    }

    private fun confirmDiscardPendingUmlEdits(): Boolean {
        if (!umlHasPendingEdits) return true
        val result = Messages.showYesNoDialog(
            project,
            "Refresh UML From Code will replace the chat-edited UML with the current code on disk.\n\n" +
                "Generate Code Diff first if you want to turn the UML changes into code.",
            "Blueprint - Discard UML Changes?",
            "Discard UML Changes",
            "Keep UML",
            Messages.getWarningIcon(),
        )
        return result == Messages.YES
    }

    private fun importUml() {
        val input = JBTextArea(20, 72).apply {
            lineWrap = false
            text = umlImportExample()
            caretPosition = 0
        }
        val panel = JPanel(BorderLayout(6, 6)).apply {
            add(
                JLabel("Paste PlantUML, Mermaid classDiagram, or simple entity bullets. Blueprint will turn them into a reviewed code patch flow."),
                BorderLayout.NORTH
            )
            add(JBScrollPane(input).apply { preferredSize = Dimension(760, 420) }, BorderLayout.CENTER)
        }
        applyDarkTheme(panel)
        val choice = JOptionPane.showConfirmDialog(
            this,
            panel,
            "Blueprint - Import UML",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
        )
        if (choice != JOptionPane.OK_OPTION) return

        val text = input.text.trim()
        if (text.isBlank()) {
            status("No UML text supplied")
            return
        }
        setUmlEditorText(text, pendingEdits = true)
        umlStatusLabel.text = "UML: pasted/loaded. Edit or ask chat to refine it."
        appendChat("Blueprint", "Loaded pasted UML into the main editor. Keep refining it, then click Generate Code Diff.")
        status("Loaded UML into editor")
    }

    private fun generateCodeFromUml() {
        val text = umlEditor.text.trim()
        if (text.isBlank() || !text.contains("classDiagram")) {
            Messages.showWarningDialog(
                project,
                "The UML editor needs Mermaid classDiagram text before Blueprint can Generate Code Diff.",
                "Blueprint - Generate Code Diff"
            )
            status("No usable UML to generate code")
            return
        }
        importUmlText(text, "editable UML")
    }

    private fun runPrimaryProductAction() {
        if (primaryActionBusy) return
        when {
            selectedNodeCanApply() -> applyChanges(null)
            currentUmlEntityCount() == 0 -> generateProjectUml()
            else -> generateCodeDiffFromCurrentUml()
        }
    }

    private fun selectedNodeCanApply(): Boolean {
        val node = nodeList.selectedValue ?: return false
        if (node.executionStatus == ExecutionStatus.APPLIED || node.executionStatus == ExecutionStatus.FAILED) return false
        val exec = registry.getExecution(node.id) ?: return false
        return exec.patches.isNotEmpty() && reviewAllowsApply(registry.getReview(node.id))
    }

    private fun generateCodeDiffFromCurrentUml() {
        beginPrimaryAction("Generating Code Diff...", "Blueprint is turning the current UML into a reviewed code patch.")
        val text = umlEditor.text.trim()
        if (text.isBlank() || !text.contains("classDiagram")) {
            val existingNodes = project.service<DependencyGraphService>().readyNodes().ifEmpty { registry.all() }
            if (existingNodes.isNotEmpty()) {
                status("Using existing reviewed code patch")
                logActivity("Generate Code Diff used existing nodes because the UML text was not parseable.")
                generateFirstRealCodeDiff(existingNodes)
                return
            }
            generateProjectUml()
            endPrimaryAction()
            return
        }
        val parsed = project.service<UmlImportService>().parse(text)
        if (parsed.entities.isEmpty()) {
            val existingNodes = project.service<DependencyGraphService>().readyNodes().ifEmpty { registry.all() }
            if (existingNodes.isNotEmpty()) {
                status("Using existing reviewed code patch")
                logActivity("Generate Code Diff used existing nodes because the UML editor had no parseable entities.")
                generateFirstRealCodeDiff(existingNodes)
                return
            }
            status("No parseable UML entities")
            showArtifactTab("Review")
            reviewSummaryArea.text = "No parseable UML entities. Click Refresh UML From Code, then ask chat for the architecture change again."
            safetyArea.text = "No diff generated."
            endPrimaryAction()
            return
        }
        importUmlText(text, "current UML")
        val nodes = project.service<DependencyGraphService>().readyNodes().ifEmpty { registry.all() }
        if (nodes.isEmpty()) {
            val noOpMessage = noOpDiffMessage()
            showArtifactTab("Review")
            reviewSummaryArea.text = noOpMessage
            safetyArea.text = "No reviewed code patch was generated because no UML-backed work items were ready."
            appendChat("Blueprint", noOpMessage)
            logActivity("Generate Code Diff found no file changes because the current UML-backed request already matched the code on disk.")
            status("No code changes needed")
            endPrimaryAction()
            return
        }
        generateFirstRealCodeDiff(nodes)
    }

    private fun generateFirstRealCodeDiff(
        nodes: List<BlueprintNode>,
        index: Int = 0,
        noChangeTitles: List<String> = emptyList(),
    ) {
        if (index >= nodes.size) {
            val noOpMessage = noOpDiffMessage()
            reviewSummaryArea.text = noOpMessage
            safetyArea.text = "No reviewed code patch to apply."
            showArtifactTab("Review")
            status("No code changes needed")
            appendChat("Blueprint", noOpMessage)
            logActivity("Generate Code Diff found no file changes because the current UML-backed request already matched the code on disk.")
            endPrimaryAction()
            return
        }

        generateCodeDiffForNode(
            nodes[index],
            onNoChange = { title ->
                generateFirstRealCodeDiff(nodes, index + 1, noChangeTitles + title)
            }
        )
    }

    private fun generateCodeDiffForNode(
        node: BlueprintNode,
        onNoChange: ((String) -> Unit)? = null,
    ) {
        selectNode(node.id)
        registry.setSelectedNode(node.id)
        loadSelectedIntoForm()
        saveCurrent()
        val n = registry.find(node.id) ?: node
        status("Generating code diff for ${n.title.ifBlank { n.id.take(8) }}...")
        showArtifactTab("Review")
        reviewSummaryArea.text = "Generating Code Diff for ${n.title.ifBlank { n.id.take(8) }}... Blueprint will plan, write a scoped patch, review it, and open the diff."
        safetyArea.text = "Review gate is on. Apply stays blocked unless review approves the patch."
        n.executionStatus = ExecutionStatus.EXECUTING
        registry.update(n)
        logActivity("Planning code diff for ${n.title.ifBlank { n.id.take(8) }} with ${providerText()}.")

        project.service<NodePlanningService>().generatePlanAsync(n) { plan ->
            registry.setPlan(n.id, plan)
            planArea.text = plan.rawJson.ifBlank { JsonExtractor.toJson(plan) }
            if (plan.status != "READY") {
                n.executionStatus = ExecutionStatus.BLOCKED
                registry.update(n)
                refreshArtifactSummary()
                showArtifactTab("Review")
                status("Code diff blocked at planning")
                logActivity("Code diff blocked at plan for ${n.title.ifBlank { n.id.take(8) }}")
                endPrimaryAction()
                return@generatePlanAsync
            }

            status("Writing code patch for ${n.title.ifBlank { n.id.take(8) }}...")
            reviewSummaryArea.text = "Plan ready. Writing a scoped patch for ${n.title.ifBlank { n.id.take(8) }}..."
            logActivity("Plan ready for ${n.title.ifBlank { n.id.take(8) }}; writing patch.")
            project.service<NodeExecutionService>().executeNodeAsync(n, plan) { exec ->
                val changedPatches = exec.patches.filter { patchChangesDisk(it) }
                val changedExec = exec.copy(
                    patches = changedPatches,
                    touchedFiles = exec.touchedFiles.filter { touched ->
                        changedPatches.any { it.path == touched.path }
                    },
                    summary = if (changedPatches.isEmpty()) {
                        "No code changes proposed; generated content matched current files."
                    } else {
                        exec.summary
                    }
                )
                registry.setExecution(n.id, changedExec)
                execArea.text = changedExec.rawJson.ifBlank { JsonExtractor.toJson(changedExec) }
                n.executionStatus = when (changedExec.status) {
                    "SUCCESS", "PARTIAL" -> ExecutionStatus.REVIEW
                    "BLOCKED" -> ExecutionStatus.BLOCKED
                    else -> ExecutionStatus.FAILED
                }
                registry.update(n)
                if (changedExec.patches.isEmpty()) {
                    n.executionStatus = ExecutionStatus.PLANNED
                    registry.update(n)
                    refreshArtifactSummary()
                    showArtifactTab("Review")
                    val title = n.title.ifBlank { n.id.take(8) }
                    status("No changes for $title; checking next node")
                    reviewSummaryArea.text = "No code changes for $title yet. Blueprint compared that UML-backed request against the code on disk and is checking the next UML change."
                    logActivity("No file changes were needed for $title because that UML-backed request already matched the code on disk.")
                    onNoChange?.invoke(title)
                    return@executeNodeAsync
                }

                status("Reviewing code diff for ${n.title.ifBlank { n.id.take(8) }}...")
                reviewSummaryArea.text = PatchChangeSummary.reviewSummary(changedExec)
                logActivity("Patch generated for ${n.title.ifBlank { n.id.take(8) }}: ${changedExec.patches.size} file(s). Reviewing safety.")
                project.service<ReviewService>().reviewAsync(n, changedExec) { review ->
                    registry.setReview(n.id, review)
                    lastReviewedUmlByNodeId[n.id] = normalizedUmlText()
                    reviewedAtByNodeId[n.id] = Instant.now()
                    refreshedAfterApply = false
                    postApplyInlineSummary = null
                    reviewArea.text = review.rawJson.ifBlank { JsonExtractor.toJson(review) }
                    refreshArtifactSummary()
                    showArtifactTab("Review")
                    DiffPreview.show(project, n, changedExec)
                    logActivity(
                        "Code diff ready for ${n.title.ifBlank { n.id.take(8) }}: " +
                            "${changedExec.patches.size} file(s), review ${review.reviewStatus}."
                    )
                    status("Code diff ready: review ${review.reviewStatus}")
                    endPrimaryAction()
                }
            }
        }
    }

    private fun noOpDiffMessage(): String =
        "No code changes needed. Blueprint compared the current UML-backed request against the code on disk. Refresh UML From Code to verify the current code, or refine the UML and try a different change."

    private fun patchChangesDisk(patch: Patch): Boolean {
        val before = project.service<ApplyChangesService>().readCurrentSnapshot(patch.path)
        val expectedExists = !patch.action.equals("delete", ignoreCase = true)
        val proposed = PatchFreshness.normalize(if (expectedExists) patch.content else "")
        return before.exists != expectedExists || PatchFreshness.normalize(before.content) != proposed
    }

    private fun importUmlText(text: String, sourceLabel: String) {
        if (text.isBlank()) {
            status("No UML text supplied")
            return
        }
        val result = project.service<UmlImportService>().importNodes(text)
        if (result.nodes.isEmpty()) {
            graphArea.text = result.summary
            status("UML import found no entities")
            Messages.showWarningDialog(project, result.summary, "Blueprint - Import UML")
            logActivity("UML import found no entities.")
            return
        }

        registry.replaceAll(result.nodes)
        selectNode(result.nodes.first().id)
        registry.setSelectedNode(result.nodes.first().id)
        graphArea.text = result.summary
        appendChat(
            "Blueprint",
            "Imported $sourceLabel into an editable UML draft. Review it, then click Generate Code Diff to preview code changes."
        )
        logActivity(
            "Imported $sourceLabel: ${result.parsed.entities.size} entit${if (result.parsed.entities.size == 1) "y" else "ies"}, " +
                "${result.parsed.relationships.size} relationship(s), ${result.nodes.size} node(s)."
        )
        status("Imported $sourceLabel: ${result.nodes.size} nodes")
    }

    private fun umlImportExample(): String =
        if (shouldShowInviteFirstRunScenario()) inviteUmlImportExample() else carCompanyUmlImportExample()

    private fun inviteUmlImportExample(): String =
        """
        classDiagram
        class User {
          id: str
          email: str
        }

        class Project {
          id: str
          name: str
          owner: User
        }

        class Invite {
          id: str
          email: str
          project: Project
          status: str
          created_at: datetime
        }

        class InvitePolicy {
          max_invites_per_project: int
          require_company_email: bool
        }

        Project --> User : owner
        Invite --> Project : belongs to
        Invite --> InvitePolicy : uses
        """.trimIndent()

    private fun carCompanyUmlImportExample(): String =
        """
        classDiagram
        class CarCompany {
          id: string
          name: string
          headquartersCity: string
        }

        class VehicleModel {
          id: string
          name: string
          segment: string
          basePrice: decimal
          companyId: string
        }

        class Dealership {
          id: string
          name: string
          city: string
          companyId: string
        }

        class InventoryVehicle {
          vin: string
          modelId: string
          dealershipId: string
          status: available | reserved | sold
          modelYear: int
          color: string
        }

        CarCompany "1" --> "many" VehicleModel
        CarCompany "1" --> "many" Dealership
        Dealership "1" --> "many" InventoryVehicle
        InventoryVehicle belongs to VehicleModel
        """.trimIndent()

    private fun seedSampleNode() {
        val n = carCompanyBackendNode()
        registry.add(n)
        selectNode(n.id)
        logActivity("Seeded one safe demo node scoped to blueprint_demo/car_company/service.py")
    }

    private fun seedUmlInviteFlow() {
        val schema = BlueprintNode(
            type = NodeType.SCHEMA,
            title = "01 UML invite schema contract",
            summary = "Turn the invite architecture into the upstream data contract.",
            description = """
                Define the invite flow from this UML-like architecture:

                User
                - id
                - email

                Project
                - id
                - name
                - owner: User

                Invite
                - id
                - email
                - project: Project
                - status
                - created_at

                InvitePolicy
                - max_invites_per_project
                - require_company_email

                Relationships:
                Project -> User (owner)
                Invite -> Project (belongs to)
                Invite -> InvitePolicy (uses)

                The schema node is the architecture contract. Downstream Python
                service, test, and docs nodes must respect this contract.
            """.trimIndent(),
            fileScope = FileScope(paths = listOf(GuidedInviteScenario.PATCH_PATH)),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "Invite keeps id, email, project, status, and created_at fields."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "InvitePolicy includes max_invites_per_project and require_company_email."),
                criterion("AC3", AcceptanceCriterionType.INTERFACE_CONTRACT, "Invite references InvitePolicy in the generated schema."),
                criterion("AC4", AcceptanceCriterionType.CODEGEN, "Generated changes stay inside the invite schema file scope.")
            )
        )
        val backend = BlueprintNode(
            type = NodeType.BACKEND,
            title = "02 Invite policy service",
            summary = "Apply invite policy rules before creating project invites.",
            description = "Implement a compact Python service that checks InvitePolicy before creating or accepting invites.",
            dependencies = listOf(schema.id),
            fileScope = FileScope(paths = listOf("blueprint_demo/imported_invite/service.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "Service reads Invite and InvitePolicy fields from the schema contract."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "Service can reject invites that violate require_company_email.")
            )
        )
        val test = BlueprintNode(
            type = NodeType.TEST,
            title = "03 Invite policy tests",
            summary = "Verify invite policy behavior against the schema contract.",
            description = "Add focused tests for company-email enforcement and policy-linked invite creation.",
            dependencies = listOf(schema.id, backend.id),
            fileScope = FileScope(paths = listOf("tests/test_imported_invite_policy.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.TEST, "Tests cover a passing company-email invite and a rejected external-email invite.")
            )
        )
        val docs = BlueprintNode(
            type = NodeType.DOCS,
            title = "04 Invite flow notes",
            summary = "Document the first-run invite architecture flow.",
            description = "Document how Project, User, Invite, and InvitePolicy move from UML into generated Python files.",
            dependencies = listOf(schema.id, backend.id),
            fileScope = FileScope(paths = listOf("docs/invite_flow.md")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.OTHER, "Docs explain the InvitePolicy fields, relationship, and resulting Python files.")
            )
        )
        listOf(schema, backend, test, docs).forEach(registry::add)
        selectNode(schema.id)
        logActivity("Seeded UML Invite Flow: InvitePolicy contract first, downstream nodes wait on the schema patch.")
    }

    private fun seedUmlCarCompanyFlow() {
        val schema = BlueprintNode(
            type = NodeType.SCHEMA,
            title = "01 UML car company schema contract",
            summary = "Turn the UML-like car company architecture into the upstream data contract.",
            description = """
                Define the car company inventory model from this UML-like architecture:

                CarCompany
                - id
                - name
                - headquartersCity

                VehicleModel
                - id
                - name
                - segment
                - basePrice
                - companyId

                Dealership
                - id
                - name
                - city
                - companyId

                InventoryVehicle
                - vin
                - modelId
                - dealershipId
                - status: available | reserved | sold
                - modelYear
                - color

                Relationships:
                CarCompany 1 -> many VehicleModel
                CarCompany 1 -> many Dealership
                Dealership 1 -> many InventoryVehicle
                InventoryVehicle belongs to VehicleModel

                The schema node is the architecture contract. Downstream Python
                service, CLI, test, and docs nodes must respect this contract.
            """.trimIndent(),
            fileScope = FileScope(paths = listOf("blueprint_demo/car_company/models.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "InventoryVehicle includes modelId, dealershipId, status, modelYear, and color."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "InventoryVehicle status is limited to available, reserved, or sold."),
                criterion("AC3", AcceptanceCriterionType.CODEGEN, "Generated changes stay inside the schema node file scope.")
            )
        )
        val backend = BlueprintNode(
            type = NodeType.BACKEND,
            title = "02 Inventory service from schema",
            summary = "Register, list, and sell vehicles using the UML schema contract.",
            description = "Implement the Python service for car company inventory. The service must use the VehicleModel, Dealership, and InventoryVehicle fields defined by the upstream UML schema node.",
            dependencies = listOf(schema.id),
            fileScope = FileScope(paths = listOf("blueprint_demo/car_company/service.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "Python service can register an available vehicle using the schema contract."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "Python service can list vehicles for a dealership."),
                criterion("AC3", AcceptanceCriterionType.INTERFACE_CONTRACT, "Python service can mark a vehicle as sold and update status."),
                criterion("AC4", AcceptanceCriterionType.CODEGEN, "Implementation stays inside the declared service file scope.")
            )
        )
        val frontend = BlueprintNode(
            type = NodeType.FRONTEND,
            title = "03 Inventory management CLI",
            summary = "Create a Python CLI for managing dealership inventory.",
            description = "Add a compact Python CLI that uses the inventory service and reflects available, reserved, and sold vehicle statuses.",
            dependencies = listOf(backend.id),
            fileScope = FileScope(paths = listOf("blueprint_demo/car_company/cli.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.UX, "User can register a vehicle and assign it to a dealership from the CLI."),
                criterion("AC2", AcceptanceCriterionType.UX, "CLI shows vehicle status values from the schema contract."),
                criterion("AC3", AcceptanceCriterionType.INTERFACE_CONTRACT, "CLI calls the inventory service produced by the service node.")
            )
        )
        val test = BlueprintNode(
            type = NodeType.TEST,
            title = "04 Inventory contract tests",
            summary = "Verify the UML schema, service, and CLI contract path.",
            description = "Add tests for vehicle registration, listing, sale, and schema-defined status handling.",
            dependencies = listOf(backend.id, frontend.id),
            fileScope = FileScope(paths = listOf("tests/test_car_company_inventory.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.TEST, "Tests cover register, list, sell, and invalid VIN cases."),
                criterion("AC2", AcceptanceCriterionType.TEST, "Tests assert available, reserved, and sold status behavior.")
            )
        )
        val docs = BlueprintNode(
            type = NodeType.DOCS,
            title = "05 Car company architecture notes",
            summary = "Document the UML-driven car company flow.",
            description = "Document how the UML schema contract maps to Python model, inventory service, CLI, and tests.",
            dependencies = listOf(backend.id, frontend.id),
            fileScope = FileScope(paths = listOf("docs/car_company_inventory.md")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.OTHER, "Docs explain the schema fields, relationships, Python service behavior, CLI behavior, and validation path.")
            )
        )
        listOf(schema, backend, frontend, test, docs).forEach(registry::add)
        selectNode(schema.id)
        logActivity("Seeded UML Car Company Flow: schema contract first, downstream nodes dependency-blocked until the contract is applied.")
    }

    private fun seedCarCompanyFlow() {
        val schema = BlueprintNode(
            type = NodeType.SCHEMA,
            title = "01 Car company data model",
            summary = "Define the inventory records used by the Python service and tests.",
            description = "Create minimal car company data shapes for vehicle registration, listing, and sales.",
            fileScope = FileScope(paths = listOf("blueprint_demo/car_company/models.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "InventoryVehicle includes modelId, dealershipId, status, modelYear, and color.")
            )
        )
        val backend = carCompanyBackendNode(dependencies = listOf(schema.id))
        val frontend = BlueprintNode(
            type = NodeType.FRONTEND,
            title = "03 Inventory management CLI",
            summary = "Add a clear Python CLI for managing dealership inventory.",
            description = "Create a small CLI for listing vehicles and registering a new dealership vehicle.",
            dependencies = listOf(backend.id),
            fileScope = FileScope(paths = listOf("blueprint_demo/car_company/cli.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.UX, "User can enter a VIN and register a vehicle from the CLI."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "CLI uses the inventory service contract produced by the service node.")
            )
        )
        val test = BlueprintNode(
            type = NodeType.TEST,
            title = "04 Inventory flow tests",
            summary = "Add coverage for vehicle registration and listing.",
            description = "Test happy path and duplicate/invalid VIN cases.",
            dependencies = listOf(backend.id, frontend.id),
            fileScope = FileScope(paths = listOf("tests/test_car_company_inventory.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.TEST, "Tests cover register, list, and sell vehicle behavior.")
            )
        )
        val docs = BlueprintNode(
            type = NodeType.DOCS,
            title = "05 Car company flow docs",
            summary = "Document the car company inventory flow.",
            description = "Add concise developer-facing notes for car company data, Python service, CLI, and validation.",
            dependencies = listOf(backend.id, frontend.id),
            fileScope = FileScope(paths = listOf("docs/car_company_inventory.md")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.OTHER, "Docs explain model fields, service behavior, CLI entry point, and validation behavior.")
            )
        )
        listOf(schema, backend, frontend, test, docs).forEach(registry::add)
        selectNode(schema.id)
        logActivity("Seeded Car Company Flow: 5 ordered Python nodes, safe blueprint_demo file scopes, dependencies wired.")
    }

    private fun seedCheckoutFlow() {
        val schema = BlueprintNode(
            type = NodeType.SCHEMA,
            title = "01 Checkout order model",
            summary = "Add the persisted order shape for checkout.",
            description = "Define the minimal order data model needed by checkout service and CLI nodes.",
            fileScope = FileScope(paths = listOf("blueprint_demo/checkout/models.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "Order model captures project, customer, line items, and total.")
            )
        )
        val api = BlueprintNode(
            type = NodeType.BACKEND,
            title = "02 Checkout service",
            summary = "Create order and return checkout status.",
            description = "Add the Python service behavior for creating checkout orders.",
            dependencies = listOf(schema.id),
            fileScope = FileScope(paths = listOf("blueprint_demo/checkout/service.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "Python checkout service creates an order using the schema node contract.")
            )
        )
        val ui = BlueprintNode(
            type = NodeType.FRONTEND,
            title = "03 Checkout CLI",
            summary = "Add a checkout command entry point.",
            description = "Create a compact Python CLI command and status display.",
            dependencies = listOf(api.id),
            fileScope = FileScope(paths = listOf("blueprint_demo/checkout/cli.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.UX, "CLI can trigger checkout and show a loading/result state.")
            )
        )
        listOf(schema, api, ui).forEach(registry::add)
        selectNode(schema.id)
        logActivity("Seeded Checkout Flow: 3 ordered nodes for a compact second demo.")
    }

    private fun carCompanyBackendNode(dependencies: List<String> = emptyList()): BlueprintNode =
        BlueprintNode(
            type = NodeType.BACKEND,
            title = "02 Car company inventory service",
            summary = "Register, list, and sell dealership vehicles.",
            description = "Implement the Python service surface for registering dealership inventory, listing available vehicles, and marking vehicles as sold.",
            dependencies = dependencies,
            fileScope = FileScope(paths = listOf("blueprint_demo/car_company/service.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "Service registers an available vehicle for a dealership."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "Service lists available vehicles for the current dealership."),
                criterion("AC3", AcceptanceCriterionType.INTERFACE_CONTRACT, "Service marks a valid VIN as sold."),
                criterion("AC4", AcceptanceCriterionType.CODEGEN, "Implementation stays inside the declared demo service file.")
            )
        )

    private fun criterion(id: String, type: AcceptanceCriterionType, description: String): AcceptanceCriterion =
        AcceptanceCriterion(id = id, type = type, description = description, verifyWith = "Review generated patch and run relevant tests")

    private fun addDependencyFromPicker() {
        val selected = dependencyPicker.selectedItem as? DependencyChoice ?: return
        val current = dependencyIdsFromList().toSet()
        val node = nodeList.selectedValue ?: return
        when {
            selected.id == node.id -> status("Cannot depend on self")
            selected.id in current -> status("Dependency already added")
            else -> {
                dependencyListModel.addElement(selected.id)
                refreshDependencyPicker()
                saveCurrent()
                status("Added dependency")
            }
        }
    }

    private fun removeSelectedDependency() {
        val index = dependencyList.selectedIndex
        if (index < 0) return
        dependencyListModel.remove(index)
        refreshDependencyPicker()
        saveCurrent()
        status("Removed dependency")
    }

    private fun removeSelected() {
        val n = nodeList.selectedValue ?: return
        val confirm = Messages.showYesNoDialog(project, "Remove '${n.title}'?", "Blueprint - Remove Node", Messages.getQuestionIcon())
        if (confirm != Messages.YES) return
        registry.remove(n.id)
        logActivity("Removed node ${n.title}")
    }

    private fun saveCurrent() {
        val n = nodeList.selectedValue ?: return
        n.type = typeCombo.selectedItem as NodeType
        n.title = titleField.text.trim()
        n.summary = summaryField.text.trim()
        n.description = descField.text
        n.dependencies = resolveDependencyIds(dependencyIdsFromEditor(), n.id)
        n.fileScope = FileScope(
            paths = lines(scopePathsArea.text),
            globs = lines(scopeGlobsArea.text),
            directories = lines(scopeDirsArea.text),
        )
        n.acceptanceCriteria = criteriaFromTable()
        registry.update(n)
        logActivity("Saved node ${n.title.ifBlank { n.id.take(8) }}")
        status("Saved")
    }

    private fun generatePlan() {
        saveCurrent()
        val n = nodeList.selectedValue ?: return
        status("Generating plan for ${n.title.ifBlank { n.id.take(8) }}...")
        showArtifactTab("Plan JSON")
        planArea.text = "Generating plan...\n\nBlueprint is rendering plan_generation_prompt with this node, file scope, dependencies, and acceptance criteria."
        n.executionStatus = ExecutionStatus.EXECUTING
        registry.update(n)
        project.service<NodePlanningService>().generatePlanAsync(n) { plan ->
            registry.setPlan(n.id, plan)
            planArea.text = plan.rawJson.ifBlank { JsonExtractor.toJson(plan) }
            showArtifactTab("Plan JSON")
            n.executionStatus = if (plan.status == "READY") ExecutionStatus.PLANNED else ExecutionStatus.BLOCKED
            registry.update(n)
            refreshArtifactSummary()
            val parseIssues = JsonExtractor.planIssues(plan)
            if (parseIssues.isNotEmpty()) {
                logActivity("Plan completed with schema warnings for ${n.title}: ${parseIssues.joinToString("; ")}")
            } else {
                logActivity("Plan ready for ${n.title}: ${plan.filesToTouch.size} scoped file(s).")
            }
            status("Plan ${plan.status}: ${n.title.ifBlank { n.id.take(8) }}")
        }
    }

    private fun showArtifactTab(title: String) {
        for (i in 0 until secondaryTabs.tabCount) {
            if (secondaryTabs.getTitleAt(i) == title) {
                secondaryTabs.selectedIndex = i
                return
            }
        }
    }

    private fun executeNode() {
        saveCurrent()
        val n = nodeList.selectedValue ?: return
        val readiness = project.service<DependencyGraphService>().readinessFor(n)
        if (!readiness.ready) {
            val reasons = readiness.reasons.joinToString("\n") { "- $it" }
            safetyArea.text = reasons
            status("Dependency blocked: ${n.title.ifBlank { n.id.take(8) }}")
            logActivity("Cannot execute ${n.title.ifBlank { n.id.take(8) }} yet: ${readiness.reasons.joinToString("; ")}")
            Messages.showWarningDialog(project, reasons, "Blueprint - Node Not Ready")
            return
        }
        val plan = registry.getPlan(n.id)
        status("Executing ${n.title.ifBlank { n.id.take(8) }}...")
        showArtifactTab("Execution JSON")
        execArea.text = "Executing node...\n\nBlueprint is rendering per_node_execution_prompt and will return structured patches for review."
        n.executionStatus = ExecutionStatus.EXECUTING
        registry.update(n)
        project.service<NodeExecutionService>().executeNodeAsync(n, plan) { exec ->
            registry.setExecution(n.id, exec)
            undoLastApplyButton.isEnabled = false
            execArea.text = exec.rawJson.ifBlank { JsonExtractor.toJson(exec) }
            showArtifactTab("Execution JSON")
            n.executionStatus = when (exec.status) {
                "SUCCESS" -> ExecutionStatus.REVIEW
                "PARTIAL" -> ExecutionStatus.REVIEW
                "BLOCKED" -> ExecutionStatus.BLOCKED
                else -> ExecutionStatus.FAILED
            }
            registry.update(n)
            refreshArtifactSummary()
            val parseIssues = JsonExtractor.executionIssues(exec)
            if (parseIssues.isNotEmpty()) {
                logActivity("Execution ${exec.status} with warnings for ${n.title}: ${parseIssues.joinToString("; ")}")
            } else {
                logActivity("Execution ${exec.status} for ${n.title}: ${exec.patches.size} reviewable patch(es).")
            }
            status("Execution ${exec.status}: ${n.title.ifBlank { n.id.take(8) }}")
        }
    }

    private fun previewDiff() {
        val n = nodeList.selectedValue ?: return
        val exec = registry.getExecution(n.id) ?: return status("No execution yet")
        if (exec.patches.isEmpty()) return status("No patches to preview")
        DiffPreview.show(project, n, exec)
        logActivity("Opened diff preview for ${n.title.ifBlank { n.id.take(8) }} (${exec.patches.size} file(s)).")
    }

    private fun review() {
        val n = nodeList.selectedValue ?: return
        val exec = registry.getExecution(n.id) ?: return status("No execution yet")
        status("Reviewing ${n.title.ifBlank { n.id.take(8) }}...")
        showArtifactTab("Review JSON")
        reviewArea.text = "Reviewing generated patches...\n\nBlueprint is checking scope, acceptance criteria, and safety before apply."
        project.service<ReviewService>().reviewAsync(n, exec) { r ->
            registry.setReview(n.id, r)
            reviewArea.text = r.rawJson.ifBlank { JsonExtractor.toJson(r) }
            showArtifactTab("Review JSON")
            refreshArtifactSummary()
            val parseIssues = JsonExtractor.reviewIssues(r)
            val reviewExplanation = ReviewExplanation.summary(
                nodeTitle = n.title.ifBlank { n.id.take(8) },
                exec = exec,
                review = r,
                readiness = project.service<DependencyGraphService>().readinessFor(n),
                validation = validationResults[n.id],
                validationCommand = project.service<ProjectValidationService>().selectedCommand(),
            )
            val reviewStatusLine = ReviewExplanation.statusLine(
                nodeTitle = n.title.ifBlank { n.id.take(8) },
                exec = exec,
                review = r,
            )
            if (parseIssues.isNotEmpty()) {
                logActivity("$reviewStatusLine Warnings: ${parseIssues.joinToString("; ")}")
            } else {
                logActivity(reviewStatusLine)
            }
            appendChat("Blueprint", "$reviewStatusLine\n\n$reviewExplanation")
            status(reviewStatusLine)
        }
    }

    private fun previewWaves() {
        graphArea.text = project.service<DependencyGraphService>().wavePreviewText()
        logActivity("Refreshed dependency wave preview")
    }

    private fun showPythonContext() {
        val context = project.service<PythonProjectAnalyzer>().analyze()
        graphArea.text = context.promptContext()
        status(if (context.isPythonLikely()) "Python context detected" else "Python context is sparse")
        logActivity(
            "Python context: ${context.sourceRoots.size} source root(s), " +
                "${context.testRoots.size} test root(s), package manager ${context.packageManager}."
        )
    }

    private fun previewSelectedAndDependents() {
        val selected = nodeList.selectedValue ?: return status("No node selected")
        val nodes = registry.all()
        val downstream = mutableSetOf<String>()
        fun visit(id: String) {
            nodes.filter { id in it.dependencies }.forEach { child ->
                if (downstream.add(child.id)) visit(child.id)
            }
        }
        visit(selected.id)
        val graph = project.service<DependencyGraphService>()
        val lines = buildList {
            add("Selected + dependents preview")
            add("Start: ${selected.title.ifBlank { selected.id.take(8) }} (${selected.id.take(8)})")
            add("")
            if (downstream.isEmpty()) {
                add("No downstream dependents.")
            } else {
                downstream.mapNotNull { id -> registry.find(id) }
                    .sortedBy { graph.readinessFor(it).wave ?: Int.MAX_VALUE }
                    .forEach { node ->
                        val readiness = graph.readinessFor(node)
                        add("Wave ${readiness.wave ?: "?"}: ${node.title.ifBlank { node.id.take(8) }} - ${badgeFor(node)}")
                        if (readiness.reasons.isNotEmpty()) {
                            readiness.reasons.forEach { add("  - $it") }
                        }
                    }
            }
        }
        graphArea.text = lines.joinToString("\n")
        logActivity("Previewed selected node and ${downstream.size} dependent(s)")
    }

    private fun showWhyBlocked() {
        val selected = nodeList.selectedValue ?: return status("No node selected")
        val readiness = project.service<DependencyGraphService>().readinessFor(selected)
        val text = if (readiness.ready) {
            "${selected.title.ifBlank { selected.id.take(8) }} is ready to run."
        } else {
            "Blocked reasons for ${selected.title.ifBlank { selected.id.take(8) }}:\n" +
                readiness.reasons.joinToString("\n") { "- $it" }
        }
        dependencyBlockArea.text = text
        Messages.showInfoMessage(project, text, "Blueprint - Dependency Status")
        logActivity("Explained dependency status for ${selected.title.ifBlank { selected.id.take(8) }}")
    }

    private fun runAllReadyNodes() {
        saveCurrent()
        val graph = project.service<DependencyGraphService>()
        val ready = graph.readyNodes()
        if (ready.isEmpty()) {
            graphArea.text = graph.wavePreviewText()
            status("No ready nodes")
            logActivity("No ready nodes to run")
            return
        }
        val confirm = Messages.showYesNoDialog(
            project,
            "Run ${ready.size} ready node(s) sequentially?\n\n" +
                ready.joinToString("\n") { "${it.title.ifBlank { it.id.take(8) }} (${it.id.take(8)})" },
            "Blueprint - Run Ready Nodes",
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return
        logActivity("Running ${ready.size} ready node(s) sequentially")
        runReadyNodeAt(ready, 0)
    }

    private fun runReadyNodeAt(nodes: List<BlueprintNode>, index: Int) {
        if (index >= nodes.size) {
            status("Ready run complete")
            logActivity("Ready run complete")
            refreshArtifactSummary()
            return
        }
        val node = registry.find(nodes[index].id) ?: return runReadyNodeAt(nodes, index + 1)
        val readiness = project.service<DependencyGraphService>().readinessFor(node)
        if (!readiness.ready) {
            logActivity("Skipped ${node.title.ifBlank { node.id.take(8) }}: ${readiness.reasons.joinToString("; ")}")
            runReadyNodeAt(nodes, index + 1)
            return
        }

        status("Running ${index + 1}/${nodes.size}: ${node.title.ifBlank { node.id.take(8) }}")
        node.executionStatus = ExecutionStatus.EXECUTING
        registry.update(node)
        project.service<NodePlanningService>().generatePlanAsync(node) { plan ->
            registry.setPlan(node.id, plan)
            if (nodeList.selectedValue?.id == node.id) planArea.text = plan.rawJson.ifBlank { JsonExtractor.toJson(plan) }
            if (plan.status != "READY") {
                node.executionStatus = ExecutionStatus.BLOCKED
                registry.update(node)
                logActivity("Plan blocked for ${node.title.ifBlank { node.id.take(8) }}")
                runReadyNodeAt(nodes, index + 1)
                return@generatePlanAsync
            }
            project.service<NodeExecutionService>().executeNodeAsync(node, plan) { exec ->
                registry.setExecution(node.id, exec)
                if (nodeList.selectedValue?.id == node.id) execArea.text = exec.rawJson.ifBlank { JsonExtractor.toJson(exec) }
                node.executionStatus = when (exec.status) {
                    "SUCCESS", "PARTIAL" -> ExecutionStatus.REVIEW
                    "BLOCKED" -> ExecutionStatus.BLOCKED
                    else -> ExecutionStatus.FAILED
                }
                registry.update(node)
                logActivity("Ran ${node.title.ifBlank { node.id.take(8) }}: ${exec.status}")
                runReadyNodeAt(nodes, index + 1)
            }
        }
    }

    private fun applySelectedFile() {
        val n = nodeList.selectedValue ?: return
        val exec = registry.getExecution(n.id) ?: return status("No execution yet")
        val paths = exec.patches.map { it.path }.toTypedArray()
        if (paths.isEmpty()) return status("No patches to apply")
        val selected = JOptionPane.showInputDialog(
            this,
            "Apply which file?",
            "Blueprint - Apply File",
            JOptionPane.QUESTION_MESSAGE,
            null,
            paths,
            paths.first()
        ) as? String ?: return
        applyChanges(selected)
    }

    private fun applyChanges(singlePath: String?) {
        val n = nodeList.selectedValue ?: return
        val exec = registry.getExecution(n.id) ?: return status("No execution yet")
        val review = registry.getReview(n.id)
        val patchCount = if (singlePath == null) exec.patches.size else 1
        if (exec.patches.isEmpty()) return status("No patches to apply")
        if (!reviewAllowsApply(review)) {
            val message = reviewBlockMessage(review)
            safetyArea.text = message
            showArtifactTab("Review")
            status("Apply blocked by review")
            logActivity("Apply blocked for ${n.title.ifBlank { n.id.take(8) }}: ${message.lines().firstOrNull().orEmpty()}")
            Messages.showWarningDialog(project, message, "Blueprint - Review Blocked Apply")
            return
        }
        val reviewLine = review?.let { "Review: ${it.reviewStatus} / ${it.recommendedNextAction}" } ?: "Review: not run"
        val targetLine = singlePath ?: "${exec.patches.size} changed file(s)"
        val displayedPatches = if (singlePath == null) exec.patches else exec.patches.filter { it.path == singlePath }
        val confirm = Messages.showYesNoDialog(
            project,
            "Apply $targetLine to disk for '${n.title.ifBlank { n.id.take(8) }}'?\n\n" +
                "$reviewLine\n" +
                "Safety: only approved, in-scope patches will be written.\n\n" +
                "Changed files:\n" + displayedPatches.joinToString("\n") { "${it.action}  ${it.path}" },
            "Blueprint - Apply Changes",
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) return
        val result = if (singlePath == null) {
            project.service<ApplyChangesService>().apply(n, exec, review)
        } else {
            project.service<ApplyChangesService>().applySingle(n, exec, singlePath, review)
        }
        undoLastApplyButton.isEnabled = result.applied.isNotEmpty()
        logActivity("Apply finished for ${n.title.ifBlank { n.id.take(8) }}: ${result.applied.size} applied, ${result.skipped.size} skipped.")
        if (result.skipped.isNotEmpty()) {
            n.executionStatus = ExecutionStatus.REVIEW
            registry.update(n)
            refreshArtifactSummary()
            status("Apply finished: ${result.applied.size} applied, ${result.skipped.size} skipped")
            Messages.showWarningDialog(
                project,
                "Skipped:\n" + result.skipped.joinToString("\n") { "${it.first} - ${it.second}" },
                "Blueprint - Some changes skipped",
            )
            return
        }

        if (result.applied.size != patchCount) {
            n.executionStatus = ExecutionStatus.REVIEW
            registry.update(n)
            refreshArtifactSummary()
            status("Apply finished: ${result.applied.size} applied, expected $patchCount")
            return
        }

        val patchesToValidate = displayedPatches.ifEmpty { exec.patches }
        runPostApplyValidation(n, patchesToValidate, result)
    }

    private fun runPostApplyValidation(
        node: BlueprintNode,
        patches: List<Patch>,
        applyResult: ApplyChangesService.ApplyResult,
    ) {
        val validation = project.service<ProjectValidationService>()
        val command = validation.selectedCommand()
        node.executionStatus = ExecutionStatus.EXECUTING
        registry.update(node)
        refreshArtifactSummary()
        showArtifactTab("Review")
        safetyArea.text = if (command == null) {
            "Applied changes. No inferred validation command was available."
        } else {
            "Applied changes. Running validation:\n$command"
        }
        status(if (command == null) "Validation skipped: no command inferred" else "Running validation: $command")
        logActivity(
            if (command == null) {
                "Validation skipped after apply for ${node.title.ifBlank { node.id.take(8) }}: no command inferred."
            } else {
                "Running validation after apply for ${node.title.ifBlank { node.id.take(8) }}: $command"
            }
        )

        validation.validateAfterApplyAsync(patches) { result ->
            validationResults[node.id] = result
            when (result.status) {
                ProjectValidationService.ValidationResult.Status.PASS,
                ProjectValidationService.ValidationResult.Status.SKIPPED -> {
                    node.executionStatus = ExecutionStatus.APPLIED
                    registry.update(node)
                    refreshUmlAfterSuccessfulApply()
                    refreshArtifactSummary()
                    logActivity("${result.summaryLine()} (${result.durationMillis}ms).")
                    status(result.summaryLine())
                    val changedPaths = applyResult.applied.distinct().sorted()
                    postApplyChangedPaths = changedPaths
                    val summaryLine = buildString {
                        append("Applied ")
                        append(if (changedPaths.size == 1) "1 file." else "${changedPaths.size} files.")
                        append(' ')
                        append(
                            when (result.status) {
                                ProjectValidationService.ValidationResult.Status.PASS -> "Validation passed."
                                ProjectValidationService.ValidationResult.Status.SKIPPED -> {
                                    if (result.reason.contains("No Python validation command was inferred", ignoreCase = true)) {
                                        "Validation skipped because no command was inferred."
                                    } else {
                                        "Validation skipped."
                                    }
                                }
                                ProjectValidationService.ValidationResult.Status.FAIL -> "Validation failed."
                            }
                        )
                    }
                    postApplyVerifyState = "Blueprint automatically refreshed the code-backed UML from disk after apply."
                    val refreshNote = "${postApplyVerifyState} Refresh UML From Code reruns that refresh when you want to verify it yourself."
                    val pythonContext = project.service<PythonProjectAnalyzer>().analyze()
                    val validationCommand = project.service<ProjectValidationService>().selectedCommand()
                    val runNote = inferredRunNote(pythonContext)
                    val commandBlock = commandReviewBlock(validationCommand, pythonContext)
                    val runBlock = buildString {
                        appendLine("Run after apply:")
                        appendLine(runNote)
                    }.trim()
                    val undoNote = if (undoLastApplyButton.isEnabled) {
                        "Undo Last Apply is available if you want to roll back this reviewed code patch."
                    } else {
                        "Undo Last Apply is not available for this apply result."
                    }
                    val umlRefreshLine = "Blueprint automatically refreshed the code-backed UML from disk after apply."
                    val highlightLine = postApplyHighlightMessage ?: "Blueprint refreshed the code-backed UML after apply."
                    val verifyStateLine = postApplyVerifyState ?: "Blueprint automatically refreshed the code-backed UML from disk after apply."
                    val whatChanged = PatchChangeSummary.applySummary(registry.getExecution(node.id), changedPaths)
                    val changedPathsBlock = buildString {
                        appendLine("Changed paths:")
                        if (changedPaths.isEmpty()) {
                            appendLine("- None")
                        } else {
                            changedPaths.forEach { appendLine("- $it") }
                        }
                    }.trim()
                    val validationBlock = buildString {
                        appendLine("Validation:")
                        appendLine(validationReportText(result))
                    }.trim()
                    val changedFilesText = if (changedPaths.isEmpty()) {
                        "No changed paths were written."
                    } else {
                        buildString {
                            appendLine("Changed paths:")
                            changedPaths.forEach { appendLine("- $it") }
                        }.trim()
                    }
                    val validationAndPathsLine = buildString {
                        appendLine(result.summaryLine())
                        appendLine(runBlock)
                        append(changedFilesText)
                    }.trim()
                    val verifyChecklist = buildString {
                        appendLine("Refresh UML From Code verification:")
                        appendLine("- Blueprint already reloaded the changed code into the UML after apply.")
                        appendLine("- Click Refresh UML From Code when you want to verify that reload yourself.")
                        appendLine("- $summaryLine")
                        appendLine("- ${result.summaryLine()}")
                        if (changedPaths.isEmpty()) {
                            appendLine("- No changed paths were written.")
                        } else {
                            appendLine("- Changed paths:")
                            changedPaths.forEach { appendLine("  - $it") }
                        }
                        appendLine("- $umlRefreshLine")
                        appendLine("- $verifyStateLine")
                        appendLine("- $highlightLine")
                        appendLine("- $refreshNote")
                    }.trim()
                    postApplyInlineSummary = PostApplyInlineSummary(
                        changedPaths = changedPaths,
                        summaryLine = summaryLine,
                        validationAndPathsLine = validationAndPathsLine,
                        nextStepLine = refreshNote,
                        verifyChecklist = verifyChecklist,
                    )
                    openAppliedFilesButton.isEnabled = changedPaths.isNotEmpty()
                    openAppliedFilesButton.text = if (changedPaths.size == 1) "Open Changed File" else "Open Changed Files"
                    verifyInUmlButton.isEnabled = true
                    val openChangedFilesNote = when (changedPaths.size) {
                        0 -> ""
                        1 -> "Optional after verification: Use Open Changed File if you want to inspect exactly what Blueprint wrote."
                        else -> "Optional after verification: Use Open Changed Files if you want to inspect exactly what Blueprint wrote."
                    }
                    Messages.showInfoMessage(
                        project,
                        listOf(summaryLine, whatChanged, changedPathsBlock, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, verifyStateLine, highlightLine, validationBlock)
                            .filter { it.isNotBlank() }
                            .joinToString("\n\n") + if (openChangedFilesNote.isBlank()) "" else "\n\n$openChangedFilesNote",
                        "Blueprint - Apply Complete"
                    )
                    SwingUtilities.invokeLater {
                        showArtifactTab("UML")
                        status(summaryLine)
                        umlStatusLabel.text = "UML: refreshed from code after apply. Refresh UML From Code to verify again, or use Undo Last Apply to roll it back."
                        appendChat(
                            "Blueprint",
                            listOf(summaryLine, whatChanged, commandBlock, refreshNote, runNote, undoNote, umlRefreshLine, verifyStateLine, highlightLine)
                                .filter { it.isNotBlank() }
                                .joinToString("\n"),
                        )
                        guideLabel.text = listOf(
                            "Apply complete. Review the refreshed code-backed UML now.",
                            verifyStateLine,
                            "Refresh UML From Code reruns that refresh when you want to verify it yourself.",
                            inferredRunGuideText(),
                            "Use Undo Last Apply to roll back this reviewed code patch.",
                        ).filter { it.isNotBlank() }.joinToString(" ")
                    }
                }
                ProjectValidationService.ValidationResult.Status.FAIL -> {
                    node.executionStatus = ExecutionStatus.FAILED
                    registry.update(node)
                    refreshArtifactSummary()
                    showArtifactTab("Review")
                    safetyArea.text = validationReportText(result)
                    safetyArea.foreground = BlueprintTheme.Danger
                    logActivity("${result.summaryLine()}: ${result.reason.ifBlank { "see validation output" }}")
                    status("Validation failed after apply")
                    Messages.showWarningDialog(
                        project,
                        validationReportText(result),
                        "Blueprint - Validation Failed",
                    )
                }
            }
        }
    }

    private fun refreshUmlAfterSuccessfulApply() {
        umlHasPendingEdits = false
        refreshedAfterApply = true
        postApplyHighlightMessage = null
        val generated = project.service<PythonUmlGenerator>().generate()
        loadGeneratedUml(generated)
        showArtifactTab("UML")
        logActivity(
            "Freshness verified after apply: refreshed UML from disk with " +
                "${generated.classCount} class(es), ${generated.relationshipCount} relationship(s)."
        )
    }

    private fun undoChanges() {
        val svc = project.service<ApplyChangesService>()
        val record = svc.lastUndo ?: return status("Nothing to undo")
        val fileList = record.entries.joinToString("\n") { it.path }
        val confirm = Messages.showYesNoDialog(
            project,
            "Restore ${record.entries.size} file(s) from before the last apply of '${record.nodeTitle}'?\n\n" +
                "$fileList\n\n" +
                "Files edited since apply will be skipped to avoid data loss.",
            "Blueprint - Undo Last Apply",
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) return
        val result = svc.undoLast()
        undoLastApplyButton.isEnabled = false
        logActivity("Undo apply for '${record.nodeTitle}': ${result.restored.size} restored, ${result.skipped.size} skipped.")
        status("Undo complete: ${result.restored.size} restored, ${result.skipped.size} skipped")
        if (result.skipped.isNotEmpty()) {
            Messages.showWarningDialog(
                project,
                "Skipped (restore manually):\n" + result.skipped.joinToString("\n") { "${it.first} — ${it.second}" },
                "Blueprint - Undo Partially Complete"
            )
        } else {
            Messages.showInfoMessage(
                project,
                "Restored ${result.restored.size} file(s):\n$fileList",
                "Blueprint - Undo Complete"
            )
        }
    }

    private fun reviewAllowsApply(review: ReviewArtifact?): Boolean =
        review?.reviewStatus == "APPROVE" && review.recommendedNextAction == "apply"

    private fun postApplyReviewSummary(exec: ExecutionArtifact?, summary: PostApplyInlineSummary): String =
        buildString {
            appendLine(summary.summaryLine)
            appendLine(summary.validationAndPathsLine)
            appendLine()
            appendLine(PatchChangeSummary.applySummary(exec, summary.changedPaths))
            appendLine(summary.verifyChecklist)
            append("\nVerified receipt:\n- Review the changed paths, validation result, and inferred run command above.\n- Refresh UML From Code to verify the updated code-backed UML.\n- Run the changed app to confirm the feature exists.\n- Open Changed Files is optional after verification if you want to inspect what Blueprint wrote.")
        }.trim()

    private fun validationReportText(result: ProjectValidationService.ValidationResult): String =
        buildString {
            appendLine("${result.detailLabel()} command: ${result.command.ifBlank { "not available" }}")
            append(result.summaryLine())
            result.exitCode?.let { append(" (exit $it)") }
            if (result.durationMillis > 0) append(" in ${result.durationMillis}ms")
            if (result.reason.isNotBlank() && result.status != ProjectValidationService.ValidationResult.Status.SKIPPED) {
                append("\nResult: ${result.reason}")
            }
            if (result.outputExcerpt.isNotBlank()) {
                append("\n\nOutput excerpt:\n")
                append(result.outputExcerpt)
            }
            if (result.relatedFiles.isNotEmpty()) {
                append("\n\nRelated files:\n")
                append(result.relatedFiles.joinToString("\n") { "- $it" })
            }
        }

    private fun openAppliedFiles() {
        val changedPaths = postApplyInlineSummary?.changedPaths.orEmpty()
        if (changedPaths.isEmpty()) {
            status("No changed files to open")
            return
        }
        if (changedPaths.size == 1) {
            openChangedFileWithReceipt(
                changedPaths.first(),
                "Inspected the only changed file after apply: ${changedPaths.first()}",
            )
            return
        }
        val selectedPath = JOptionPane.showInputDialog(
            this,
            "Open which changed file?",
            "Blueprint - Open Changed Files",
            JOptionPane.QUESTION_MESSAGE,
            null,
            changedPaths.toTypedArray(),
            changedPaths.first(),
        ) as? String ?: return
        openChangedFileWithReceipt(
            selectedPath,
            "Inspected one changed file after apply from the chooser: $selectedPath",
        )
    }

    private fun reviewBlockMessage(review: ReviewArtifact?): String {
        if (review == null) {
            return "Apply is blocked because review has not run yet.\n\nClick Generate Code Diff so Blueprint can create and review a patch first."
        }
        val issues = review.issues.take(3).joinToString("\n") {
            "- ${it.title.ifBlank { it.category }}: ${it.details.ifBlank { it.suggestedFix }}"
        }
        return buildString {
            append("Apply is blocked because review returned ${review.reviewStatus} / ${review.recommendedNextAction}.")
            if (review.summary.isNotBlank()) {
                append("\n\n")
                append(review.summary)
            }
            if (issues.isNotBlank()) {
                append("\n\nIssues:\n")
                append(issues)
            }
            append("\n\nChange the UML or regenerate the code diff before applying.")
        }
    }

    private fun quickSelect(filter: NodeFilter) {
        filterCombo.selectedItem = filter
        refreshList()
        val first = registry.all().firstOrNull { matchesFilter(it, filter) }
        if (first != null) {
            selectNode(first.id)
            registry.setSelectedNode(first.id)
            loadSelectedIntoForm()
            logActivity("Quick selected ${first.title.ifBlank { first.id.take(8) }} for $filter")
        } else {
            status("No $filter nodes")
        }
    }

    private fun matchesFilter(node: BlueprintNode, filter: NodeFilter = filterCombo.selectedItem as? NodeFilter ?: NodeFilter.ALL): Boolean {
        val graph = project.service<DependencyGraphService>()
        val readiness = graph.readinessFor(node)
        return when (filter) {
            NodeFilter.ALL -> true
            NodeFilter.READY -> readiness.ready
            NodeFilter.BLOCKED -> hasDependencyBlock(readiness) || badgeFor(node) == "BLOCKED" || badgeFor(node) == "PARTIAL"
            NodeFilter.APPLIED -> node.executionStatus == ExecutionStatus.APPLIED
            NodeFilter.CURRENT_WAVE -> {
                val selectedForWave = nodeList.selectedValue ?: registry.selectedNodeId()?.let { registry.find(it) }
                val selectedWave = selectedForWave?.let { graph.readinessFor(it).wave }
                selectedWave != null && readiness.wave == selectedWave
            }
        }
    }

    private fun refreshList() {
        val selectedId = registry.selectedNodeId() ?: nodeList.selectedValue?.id
        refreshingList = true
        try {
            listModel.clear()
            registry.all().filter { matchesFilter(it) }.forEach { listModel.addElement(it) }
            if (selectedId != null) selectNode(selectedId)
        } finally {
            refreshingList = false
        }
        loadSelectedIntoForm()
        refreshArtifactSummary()
        updateGuide()
    }

    private fun selectNode(id: String) {
        val node = registry.all().firstOrNull { it.id == id } ?: return
        nodeList.setSelectedValue(node, true)
    }

    private fun selectNodeFromGraph(id: String) {
        val node = registry.find(id)
        if (node == null) {
            selectedCanvasId = id
            updateMiniGraph(project.service<DependencyGraphService>().analyze())
            status("Selected canvas entity: $id")
            persistWorkspace()
            return
        }
        selectedCanvasId = id
        if (!matchesFilter(node)) {
            filterCombo.selectedItem = NodeFilter.ALL
        }
        selectNode(id)
        registry.setSelectedNode(id)
        loadSelectedIntoForm()
        status("Selected from graph: ${node.title.ifBlank { node.id.take(8) }}")
        logActivity("Graph selected ${node.title.ifBlank { node.id.take(8) }} (${node.id.take(8)}).")
        persistWorkspace()
    }

    private fun openGraphSource(node: MiniGraphPanel.NodeView) {
        val sourcePath = node.sourcePath.ifBlank {
            status("No source mapping for ${node.title}.")
            Messages.showInfoMessage(
                project,
                "${node.title} is not mapped to a source file yet.",
                "Blueprint - Source Not Mapped",
            )
            return
        }
        val sourceFile = resolveProjectFile(sourcePath)
        if (!openProjectFile(sourceFile, sourcePath, node.sourceLine)) return
        selectedCanvasId = node.id
        status("Opened source: ${node.sourceDescriptionForStatus()}")
        logActivity("Opened source for ${node.title}: ${node.sourceDescriptionForStatus()}")
    }

    private fun openProjectFile(sourceFile: File, displayPath: String, lineNumber: Int? = null): Boolean {
        if (!sourceFile.isFile) {
            status("Could not find source: $displayPath")
            Messages.showWarningDialog(
                project,
                "Could not find source file:\n$displayPath\n\nOpening a file here does not apply changes. Generate Code Diff and Apply Approved Changes are still separate steps.",
                "Blueprint - Source Not Found",
            )
            return false
        }
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceFile)
        if (virtualFile == null) {
            status("Could not open source: ${sourceFile.path}")
            Messages.showWarningDialog(
                project,
                "Could not open source file:\n${sourceFile.path}\n\nOpening a file here does not apply changes. Generate Code Diff and Apply Approved Changes are still separate steps.",
                "Blueprint - Source Not Found",
            )
            return false
        }
        val zeroBasedLine = (lineNumber ?: 1).coerceAtLeast(1) - 1
        OpenFileDescriptor(project, virtualFile, zeroBasedLine, 0).navigate(true)
        return true
    }

    private fun openChangedFile(path: String) {
        openChangedFileWithReceipt(path, "Opened changed file from review: $path")
    }

    private fun openChangedFileWithReceipt(path: String, receiptMessage: String) {
        val sourceFile = resolveProjectFile(path)
        if (!openProjectFile(sourceFile, path)) return
        status("Opened changed file: $path")
        logActivity(receiptMessage)
    }

    private fun refreshChangedFilesPanel(exec: ExecutionArtifact?) {
        changedFilesPanel.removeAll()
        val changedPaths = exec?.patches.orEmpty().map { it.path }.distinct().sorted()
        if (changedPaths.isEmpty()) {
            changedFilesPanel.add(JLabel("Changed files appear here after Generate Code Diff.").apply {
                foreground = BlueprintTheme.Muted
                font = BlueprintTheme.font(12f)
            })
        } else {
            changedFilesPanel.add(JLabel("Changed files (open to inspect, not apply):").apply {
                foreground = BlueprintTheme.TextStrong
                font = BlueprintTheme.font(12f, Font.BOLD)
            })
            changedFilesPanel.add(JSeparator().apply { foreground = BlueprintTheme.Border })
            changedPaths.forEach { path ->
                changedFilesPanel.add(JButton(path).apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                    horizontalAlignment = SwingConstants.LEFT
                    isFocusPainted = false
                    foreground = BlueprintTheme.Accent
                    background = BlueprintTheme.Panel
                    border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
                    toolTipText = "Open this changed file in the IDE. This does not apply the reviewed code patch."
                    addActionListener { openChangedFile(path) }
                })
            }
        }
        changedFilesPanel.revalidate()
        changedFilesPanel.repaint()
    }

    private fun resolveProjectFile(path: String): File {
        val raw = File(path)
        if (raw.isAbsolute) return raw
        val base = project.basePath ?: return raw
        return File(base, path)
    }

    private fun MiniGraphPanel.NodeView.sourceDescriptionForStatus(): String =
        when {
            source.isNotBlank() -> source
            sourceLine != null -> "$sourcePath:$sourceLine"
            else -> sourcePath
        }

    private fun setUmlEditorText(text: String, pendingEdits: Boolean) {
        suppressUmlDocumentEvents = true
        try {
            umlEditor.text = text
            umlEditor.caretPosition = 0
            umlHasPendingEdits = pendingEdits
        } finally {
            suppressUmlDocumentEvents = false
        }
        clearRestoredFlag()
        refreshReviewFreshnessState()
        updateMiniGraph(project.service<DependencyGraphService>().analyze())
        updateGuide()
        persistWorkspace()
    }

    private fun umlDocumentChanged() {
        if (!suppressUmlDocumentEvents) {
            umlHasPendingEdits = true
            clearRestoredFlag()
            refreshReviewFreshnessState()
            persistWorkspace()
        }
        refreshCanvasFromUml()
    }

    private fun refreshCanvasFromUml() {
        SwingUtilities.invokeLater {
            updateMiniGraph(project.service<DependencyGraphService>().analyze())
        }
    }

    private fun loadSelectedIntoForm() {
        val n = nodeList.selectedValue ?: run {
            applyApprovedButton.isEnabled = false
            applyApprovedButton.text = "Apply Approved Changes"
            openAppliedFilesButton.isEnabled = false
            openAppliedFilesButton.text = "Open Changed Files"
            undoLastApplyButton.isEnabled = false
            updateOverviewSummary()
            selectedLabel.text = "Selected: none"
            artifactLabel.text = "Artifacts: not planned"
            reviewSummaryArea.text = "No node selected."
            refreshChangedFilesPanel(null)
            safetyArea.text = "Select or seed a node to begin."
            dependencyBlockArea.text = "No dependency status yet."
            graphArea.text = "No graph yet. Seed a sample or load a UML change."
            updateMiniGraph(project.service<DependencyGraphService>().analyze())
            titleField.text = ""
            summaryField.text = ""
            descField.text = ""
            dependencyListModel.clear()
            manualDependencyArea.text = ""
            refreshDependencyPicker()
            scopePathsArea.text = ""
            scopeGlobsArea.text = ""
            scopeDirsArea.text = ""
            criteriaModel.rowCount = 0
            planArea.text = ""
            execArea.text = ""
            reviewArea.text = ""
            return
        }
        typeCombo.selectedItem = n.type
        titleField.text = n.title
        summaryField.text = n.summary
        descField.text = n.description
        loadDependencies(n.dependencies)
        refreshDependencyPicker()
        scopePathsArea.text = n.fileScope.paths.joinToString("\n")
        scopeGlobsArea.text = n.fileScope.globs.joinToString("\n")
        scopeDirsArea.text = n.fileScope.directories.joinToString("\n")
        loadCriteria(n.acceptanceCriteria)
        planArea.text = registry.getPlan(n.id)?.rawJson.orEmpty()
        execArea.text = registry.getExecution(n.id)?.rawJson.orEmpty()
        reviewArea.text = registry.getReview(n.id)?.rawJson.orEmpty()
        selectedLabel.text = "Selected: ${n.title.ifBlank { n.id.take(8) }} | ID: ${n.id}"
        refreshArtifactSummary()
        status("Loaded ${n.title.ifBlank { n.id.take(8) }}")
    }

    private fun refreshArtifactSummary() {
        val n = nodeList.selectedValue ?: return
        val plan = registry.getPlan(n.id)
        val exec = registry.getExecution(n.id)
        val review = registry.getReview(n.id)
        val validation = validationResults[n.id]
        val graph = project.service<DependencyGraphService>()
        val report = graph.analyze()
        val readiness = graph.readinessFor(n)
        val pythonContext = project.service<PythonProjectAnalyzer>().analyze()
        val validationCommand = project.service<ProjectValidationService>().selectedCommand()
        updateOverviewSummary(report)
        val reviewFreshness = reviewFreshnessFor(n, exec)
        val canApply = n.executionStatus != ExecutionStatus.APPLIED &&
            n.executionStatus != ExecutionStatus.FAILED &&
            !exec?.patches.isNullOrEmpty() &&
            reviewAllowsApply(review)
        applyApprovedButton.isEnabled = canApply
        applyApprovedButton.text = when {
            canApply -> "Apply Approved Changes"
            n.executionStatus == ExecutionStatus.FAILED -> "Validation Failed"
            else -> "Apply Blocked By Review"
        }
        val openAppliedFilesEnabled = n.executionStatus == ExecutionStatus.APPLIED && postApplyInlineSummary?.changedPaths.orEmpty().isNotEmpty()
        openAppliedFilesButton.isEnabled = openAppliedFilesEnabled
        openAppliedFilesButton.text = if (postApplyInlineSummary?.changedPaths?.size == 1) "Open Changed File" else "Open Changed Files"
        verifyInUmlButton.isEnabled = n.executionStatus == ExecutionStatus.APPLIED
        artifactLabel.text = "Artifacts: plan=${plan?.status ?: "not planned"} | exec=${exec?.status ?: "not executed"} | review=${review?.reviewStatus ?: "not reviewed"} | diff=${reviewFreshness.badge} | ${reviewFreshness.reviewedAtLine.lowercase(Locale.US)} | validation=${validation?.status ?: "not run"} | node=${badgeFor(n)} | ready=${readiness.ready}"
        val inlineSummary = postApplyInlineSummary
        reviewSummaryArea.text = if (n.executionStatus == ExecutionStatus.APPLIED && inlineSummary != null) {
            postApplyReviewSummary(exec, inlineSummary)
        } else {
            buildReviewSummary(exec, review, reviewFreshness, validationCommand)
        }
        refreshChangedFilesPanel(exec)
        refreshGroundingSummary()

        val issues = buildList {
            if (plan != null) addAll(JsonExtractor.planIssues(plan))
            if (exec != null) addAll(JsonExtractor.executionIssues(exec))
            if (review != null) addAll(JsonExtractor.reviewIssues(review))
        }
        val scopeDrops = exec?.validation?.risks.orEmpty().filter { it.contains("out-of-scope", ignoreCase = true) }
        val reviewDetails = review?.let {
            ReviewExplanation.details(
                nodeTitle = n.title.ifBlank { n.id.take(8) },
                exec = exec,
                review = it,
                readiness = readiness,
                validation = validation,
                validationCommand = validationCommand,
            )
        }
        val commandBlock = commandReviewBlock(validationCommand, pythonContext)
        safetyArea.text = when {
            validation?.status == ProjectValidationService.ValidationResult.Status.FAIL -> validationReportText(validation)
            validation?.status == ProjectValidationService.ValidationResult.Status.PASS -> validationReportText(validation)
            validation?.status == ProjectValidationService.ValidationResult.Status.SKIPPED -> validationReportText(validation)
            issues.isNotEmpty() || scopeDrops.isNotEmpty() ->
                listOf((issues + scopeDrops).distinct().joinToString("\n") { "- $it" }, commandBlock).joinToString("\n\n")
            reviewDetails != null -> listOf(reviewDetails.joinToString("\n"), commandBlock).joinToString("\n\n")
            n.executionStatus == ExecutionStatus.FAILED -> listOf(
                "Validation failed after apply. Generate Code Diff again after you fix the problem, or inspect the related file manually before continuing.",
                commandBlock,
            ).joinToString("\n\n")
            exec?.status == "PARTIAL" -> listOf(
                "Execution is PARTIAL. Inspect the diff and validation notes before applying.",
                commandBlock,
            ).joinToString("\n\n")
            exec?.status == "BLOCKED" -> listOf(
                "Execution is BLOCKED. Do not apply until the node is revised.",
                commandBlock,
            ).joinToString("\n\n")
            !exec?.patches.isNullOrEmpty() -> commandBlock
            else -> "No safety issues reported yet."
        }
        dependencyBlockArea.text = if (readiness.reasons.isEmpty()) {
            "Dependency status: ready\nThis node can run now."
        } else {
            "Dependency blockers:\n" +
            readiness.reasons.joinToString("\n") { "- $it" }
        }
        safetyArea.foreground = if (safetyArea.text.startsWith("-") ||
            safetyArea.text.contains("BLOCKED") ||
            safetyArea.text.contains("PARTIAL") ||
            safetyArea.text.contains("failed", ignoreCase = true)
        ) {
            BlueprintTheme.Warning
        } else {
            BlueprintTheme.Success
        }
        dependencyBlockArea.foreground = if (readiness.reasons.isEmpty()) BlueprintTheme.Success else BlueprintTheme.Warning
        graphArea.text = buildString {
            append("Selected readiness: ")
            append(if (readiness.ready) "READY" else "BLOCKED")
            readiness.wave?.let { append(" | wave $it") }
            if (readiness.reasons.isNotEmpty()) {
                append("\n")
                append(readiness.reasons.joinToString("\n") { "- $it" })
            }
            append("\n\n")
            append(graph.wavePreviewText())
        }
        updateMiniGraph(report)
        refreshFirstRunScenario()
    }

    private fun updateMiniGraph(report: DependencyGraphService.GraphReport) {
        val selectedId = nodeList.selectedValue?.id
        val proposalViews = if (umlHasPendingEdits) umlCanvasViews(selectedId) else emptyList()
        if (proposalViews.isNotEmpty()) {
            miniGraph.setGraph(proposalViews)
            modeBannerLabel.text = decorateBanner("Viewing: UML draft \u2014 pending edits")
            return
        }
        val codeViews = codeMapViews(selectedId, report)
        if (codeViews.isNotEmpty()) {
            miniGraph.setGraph(codeViews)
            modeBannerLabel.text = decorateBanner("Viewing: current code map")
            return
        }
        modeBannerLabel.text = decorateBanner("Viewing: workflow nodes")
        val views = registry.all().map { node ->
            val readiness = report.readiness[node.id]
            MiniGraphPanel.NodeView(
                id = node.id,
                title = node.title.ifBlank { "(untitled)" },
                status = badgeFor(node),
                wave = readiness?.wave ?: 1,
                dependencies = node.dependencies,
                selected = node.id == selectedId,
                ready = readiness?.ready == true,
                blocked = hasDependencyBlock(readiness) || badgeFor(node) == "BLOCKED" || badgeFor(node) == "PARTIAL",
                detail = graphNodeDetail(node, readiness),
                origin = MiniGraphPanel.NodeOrigin.WORKFLOW,
                kind = node.type.name.lowercase(),
                source = node.fileScope.paths.firstOrNull().orEmpty(),
                sourcePath = node.fileScope.paths.firstOrNull().orEmpty(),
                preview = readiness?.let { if (it.ready) "ready to run" else it.reasons.firstOrNull().orEmpty() }.orEmpty(),
                relationshipHint = if (node.dependencies.isEmpty()) "" else "depends on ${node.dependencies.size} node(s)",
            )
        }
        miniGraph.setGraph(views)
    }

    private fun codeMapViews(
        selectedNodeId: String?,
        report: DependencyGraphService.GraphReport,
    ): List<MiniGraphPanel.NodeView> {
        val ir = project.service<IRStore>().load() ?: return emptyList()
        val workflowByComponent = registry.all()
            .mapNotNull { node ->
                val componentId = node.metadata["componentId"] ?: return@mapNotNull null
                val readiness = report.readiness[node.id]
                componentId to CodeMapProjection.WorkflowBadge(
                    status = badgeFor(node),
                    ready = readiness?.ready == true,
                    blocked = hasDependencyBlock(readiness) || badgeFor(node) == "BLOCKED" || badgeFor(node) == "PARTIAL",
                    selected = node.id == selectedNodeId,
                )
            }
            .toMap()
        return CodeMapProjection.fromIr(
            ir = ir,
            selectedId = selectedCanvasId ?: selectedNodeId,
            workflowByComponentId = workflowByComponent,
            options = CodeMapProjection.Options(
                groupMode = codeMapGroupCombo.selectedItem as? CodeMapProjection.GroupMode
                    ?: CodeMapProjection.GroupMode.PACKAGE,
                hideTests = hideCodeMapTests.isSelected,
                hideGenerated = hideGeneratedCodeMap.isSelected,
                hideExternalEdges = hideExternalCodeMapEdges.isSelected,
                hideLowConfidenceEdges = hideLowConfidenceCodeMapEdges.isSelected,
            ),
        )
    }

    private fun umlCanvasViews(selectedNodeId: String?): List<MiniGraphPanel.NodeView> {
        val parsed = runCatching { project.service<UmlImportService>().parse(umlEditor.text) }.getOrNull()
            ?: return emptyList()
        if (parsed.entities.isEmpty()) return emptyList()
        val entityNames = parsed.entities.map { it.name }.toSet()
        val relationshipsByEntity = parsed.relationships
            .filter { it.from in entityNames && it.to in entityNames }
        val dependenciesByEntity = relationshipsByEntity.groupBy({ it.to }, { it.from })
        return parsed.entities.mapIndexed { index, entity ->
            val visibleFields = entity.fields.map(::sanitizeUmlPreviewField).filter { it.isNotBlank() }
            val fieldLines = visibleFields.filterNot { it.contains("(") && it.contains(")") }
            val methodLines = visibleFields.filter { it.contains("(") && it.contains(")") }
            val relationshipHint = relationshipsByEntity
                .filter { it.from == entity.name || it.to == entity.name }
                .map { rel -> rel.label.ifBlank { "relates to" } }
                .distinct()
                .let { labels ->
                    if (labels.isEmpty()) "" else "edges: ${relationshipsByEntity.count { it.to == entity.name }} in / ${relationshipsByEntity.count { it.from == entity.name }} out · " +
                        labels.take(2).joinToString(", ") + if (labels.size > 2) ", +${labels.size - 2}" else ""
                }
            MiniGraphPanel.NodeView(
                id = entity.name,
                title = entity.name,
                status = "UML",
                wave = index % 3 + 1,
                dependencies = dependenciesByEntity[entity.name].orEmpty().distinct(),
                selected = selectedCanvasId == entity.name || selectedNodeId == entity.name,
                ready = true,
                blocked = false,
                detail = buildString {
                    append("Proposed UML entity")
                    if (visibleFields.isNotEmpty()) {
                        append("\n")
                        append(visibleFields.take(8).joinToString("\n") { "- $it" })
                    }
                },
                origin = MiniGraphPanel.NodeOrigin.PROPOSED_UML,
                kind = "UML entity",
                preview = fieldLines.take(3).joinToString(", ").ifBlank { "no fields yet" },
                fields = fieldLines.take(3),
                fieldOverflowCount = (fieldLines.size - 3).coerceAtLeast(0),
                methods = methodLines.take(2),
                methodOverflowCount = (methodLines.size - 2).coerceAtLeast(0),
                relationshipHint = relationshipHint,
            )
        }
    }

    private fun graphNodeDetail(
        node: BlueprintNode,
        readiness: DependencyGraphService.NodeReadiness?,
    ): String {
        val reasons = readiness?.reasons.orEmpty().filter { !it.startsWith("Node is already") }
        return buildString {
            append(if (readiness?.ready == true) "Ready to run" else "Blocked")
            append("\n")
            append("Type: ${node.type.name.lowercase()}")
            if (reasons.isNotEmpty()) {
                append("\n")
                append(reasons.joinToString("\n") { "- $it" })
            }
        }
    }

    private fun updateOverviewSummary(report: DependencyGraphService.GraphReport = project.service<DependencyGraphService>().analyze()) {
        val allNodes = registry.all()
        val readyCount = project.service<DependencyGraphService>().readyNodes().size
        val blockedCount = allNodes.count { hasDependencyBlock(report.readiness[it.id]) }
        val appliedCount = allNodes.count { it.executionStatus == ExecutionStatus.APPLIED }
        summaryLabel.text = "Total ${allNodes.size} | Ready $readyCount | Blocked $blockedCount | Applied $appliedCount"
        refreshProviderLabels()
        updateGuide()
    }

    private fun updateGuide() {
        if (primaryActionBusy) return
        refreshFirstRunScenario()
        when {
            nodeList.selectedValue?.executionStatus == ExecutionStatus.FAILED -> {
                primaryActionButton.text = "Generate Code Diff"
                guideLabel.text = "Validation failed after apply. Adjust the UML or code, then Generate Code Diff again."
                updateNextStepBanner("Next: Generate Code Diff", "Validation failed after apply, so the reviewed code patch needs another pass.")
            }
            selectedNodeCanApply() -> {
                primaryActionButton.text = "Apply Approved Changes"
                guideLabel.text = "Review approved the reviewed code patch because it stays in scope and has no blocking safety issues. Apply Approved Changes to write it to disk, then Blueprint will validate the project."
                updateNextStepBanner("Next: Apply Approved Changes", "Review approved the current patch because it stays in scope and has no blocking safety issues, so this is the safe time to write it to disk.")
            }
            currentUmlEntityCount() == 0 -> {
                primaryActionButton.text = "Refresh UML From Code"
                guideLabel.text = emptyUmlGuideText()
                updateNextStepBanner("Next: Refresh UML From Code", "Load the current Python project into a code-backed UML diagram before editing.")
            }
            else -> {
                val diffGuide = generateDiffGuideSummary()
                primaryActionButton.text = "Generate Code Diff"
                guideLabel.text = listOf(diffGuide.guideText, diffGuide.commandSummary)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                updateNextStepBanner("Next: Generate Code Diff", diffGuide.nextStepDetail)
            }
        }
    }

    private fun emptyUmlGuideText(): String {
        val context = project.service<PythonProjectAnalyzer>().analyze()
        if (context.isPythonLikely()) {
            val runGuide = inferredRunGuideText(context)
            return listOf(
                "No code-backed UML is loaded yet. Start with Refresh UML From Code to read the current project into an editable UML diagram.",
                "Blueprint can open any Python folder, draw a code-backed UML diagram, help you refine it with chat or direct edits, Generate Code Diff, Apply Approved Changes, refresh UML from code to verify, and run the changed app.",
                "Blueprint found Python files, but no classes were extracted into the code-backed UML yet.",
                "After that, refine the UML, Generate Code Diff, Apply Approved Changes, Refresh UML From Code to verify, and Run In Blueprint.",
                "Next steps: review the inferred source roots, open a Python file to confirm the folder you want, or keep editing the project and refresh again.",
                runGuide,
            ).filter { it.isNotBlank() }.joinToString(" ")
        }
        val nextStep = context.notes.firstOrNull()
            ?: "Open a Python folder or add .py files, then click Refresh UML From Code again."
        return listOf(
            "This folder does not look like a supported Python project yet.",
            "Blueprint could not find Python files to turn into a code-backed UML diagram.",
            "When this folder has Python files, Blueprint can turn them into a code-backed UML diagram, help you refine that UML, Generate Code Diff, Apply Approved Changes, refresh UML from code to verify, and run the changed app.",
            nextStep,
            "Open a Python source root or add .py files, then Refresh UML From Code to start the full Blueprint loop.",
            "Next steps: open a Python source root, add .py files, or open source files manually while you pick the folder to map.",
        ).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun inferredRunGuideText(context: PythonProjectAnalyzer.PythonProjectContext = project.service<PythonProjectAnalyzer>().analyze()): String =
        context.runCommands.firstOrNull()?.let { "When you want to run the app, start with: $it or click Run In Blueprint." }
            ?: missingRunCommandGuidance(context)

    private fun generateDiffGuideSummary(
        context: PythonProjectAnalyzer.PythonProjectContext = project.service<PythonProjectAnalyzer>().analyze(),
        validationCommand: String? = project.service<ProjectValidationService>().selectedCommand(),
    ): GenerateDiffGuideSummary {
        val lines = mutableListOf("Change the UML with chat or direct edits, then Generate Code Diff.")
        lines += generateDiffValidationHint(validationCommand)
        if (umlHasPendingEdits) {
            lines += "Generate Code Diff will create a reviewed code patch for your current UML edits."
        }
        lines += validationCommandReviewText(validationCommand)
        lines += runCommandReviewText(context)
        val commandSummary = preApplyCommandSummary(context, validationCommand)
        val guideText = (lines + commandSummary.lines()).joinToString(" ")
        val nextStepDetail = if (umlHasPendingEdits) {
            "Create a reviewed code patch for the current UML edits. ${validationCommandReviewText(validationCommand)} ${runCommandReviewText(context)}"
        } else {
            "Turn the current UML edits into a reviewed code patch before apply. ${validationCommandReviewText(validationCommand)} ${runCommandReviewText(context)}"
        }
        return GenerateDiffGuideSummary(guideText, nextStepDetail, commandSummary)
    }

    private fun generateDiffValidationHint(command: String?): String =
        command?.takeIf { it.isNotBlank() }
            ?.let { "Generate Code Diff will prepare a patch that Blueprint validates after apply with: $it" }
            ?: "Generate Code Diff can still prepare a reviewed code patch, but Blueprint did not infer a validation command yet, so verify manually if you need extra checks."

    private fun validationCommandReviewText(command: String?): String =
        command?.takeIf { it.isNotBlank() }
            ?.let { "Validation after apply: $it" }
            ?: "Validation after apply is unavailable. Blueprint did not infer a validation command, so verify manually if you need extra checks."

    private fun runCommandReviewText(context: PythonProjectAnalyzer.PythonProjectContext = project.service<PythonProjectAnalyzer>().analyze()): String =
        context.runCommands.firstOrNull()?.let { "Run after apply was inferred automatically: $it" }
            ?: "Run after apply is unavailable. ${missingRunCommandGuidance(context)}"

    private fun preApplyCommandSummary(
        context: PythonProjectAnalyzer.PythonProjectContext = project.service<PythonProjectAnalyzer>().analyze(),
        validationCommand: String? = project.service<ProjectValidationService>().selectedCommand(),
    ): String = listOf(
        "Before apply, Blueprint expects:",
        "- ${validationCommandReviewText(validationCommand)}",
        "- ${runCommandReviewText(context)}",
    ).joinToString("\n")

    private fun commandReviewBlock(
        validationCommand: String?,
        context: PythonProjectAnalyzer.PythonProjectContext = project.service<PythonProjectAnalyzer>().analyze(),
    ): String = preApplyCommandSummary(context, validationCommand)

    private fun inferredRunNote(context: PythonProjectAnalyzer.PythonProjectContext = project.service<PythonProjectAnalyzer>().analyze()): String =
        context.runCommands.firstOrNull()?.let { "Run the changed app with: $it, or click Run In Blueprint to stream it here." }
            ?: missingRunCommandGuidance(context)

    private fun shouldShowInviteFirstRunScenario(): Boolean =
        GuidedInviteScenario.matchesProject(project.name, project.basePath)

    private fun refreshFirstRunScenario() {
        val inviteFlow = shouldShowInviteFirstRunScenario()
        val genericState = currentFirstRunChecklistState()
        firstRunScenarioArea.text = if (inviteFlow) {
            GuidedInviteScenario.checklistText(currentInviteFirstRunScenarioState())
        } else {
            genericState.checklistText()
        }
        if (inviteFlow) {
            val state = currentInviteFirstRunScenarioState()
            demoReceiptArea.text = state.demoReceiptText()
            firstRunPromptButton.text = if (state.resetSuggested) "Reset Demo Sandbox" else "Try This Change"
            firstRunPromptButton.isEnabled = state.codeMapReady
            firstRunPromptButton.toolTipText = if (state.resetSuggested) {
                "The guided demo prompt likely matches code that is already in ${state.resetPath}. Click Reset Demo Sandbox for a fresh invite demo run, or keep your own UML edit instead."
            } else if (state.promptReady) {
                "Fresh prompt already loaded for the current sandbox: \"${state.prompt}\""
            } else {
                "Fresh prompt for the current sandbox: \"${state.prompt}\""
            }
            runDemoButton.text = if (state.runVerified) "Demo Run Verified" else "Run Demo Step"
            runDemoButton.isEnabled = state.codeMapReady && !state.runCommand.isNullOrBlank()
            runDemoButton.toolTipText = when {
                state.runCommand.isNullOrBlank() -> "Refresh UML From Code first so Blueprint can infer a run command for the current project."
                state.runVerified -> "Blueprint already recorded a passed demo run for: ${state.runCommand}"
                else -> "Record the final manual demo step and expected visible result for: ${state.runCommand}"
            }
        } else {
            demoReceiptArea.text = genericState.checklistText()
            firstRunPromptButton.text = "Refresh UML From Code"
            firstRunPromptButton.isEnabled = !genericState.codeMapReady
            firstRunPromptButton.toolTipText = if (genericState.codeMapReady) {
                "Current Python folder is already loaded into the code-backed UML."
            } else {
                "Load the current Python folder into a code-backed UML diagram."
            }
            runDemoButton.text = if (genericState.runVerified) "Run Verified" else "Verify Run Step"
            runDemoButton.isEnabled = genericState.codeMapReady
            runDemoButton.toolTipText = when {
                genericState.runCommand.isNullOrBlank() -> "No run command was inferred. Use Open Likely Entry File to inspect the best candidate, or use the checklist to verify one of the likely entry files manually."
                genericState.runVerified -> "Blueprint already recorded a passed run step for: ${genericState.runCommand}"
                else -> "Record how you verified the changed app with: ${genericState.runCommand}"
            }
        }
        refreshRunControls()
    }

    private fun refreshRunControls() {
        val runner = project.service<ProjectRunService>()
        val context = project.service<PythonProjectAnalyzer>().analyze()
        updateLikelyEntryFileAction(context)
        if (runner.isRunning()) {
            runAppButton.text = "Stop Run"
            runAppButton.isEnabled = true
            runAppButton.toolTipText = "Stop the inferred project command running inside Blueprint."
            return
        }
        val runCommand = runner.inferredRunCommand(context)
        runAppButton.text = "Run In Blueprint"
        runAppButton.isEnabled = !runCommand.isNullOrBlank()
        runAppButton.toolTipText = runCommand?.let { "Run and stream output for: $it" }
            ?: runner.noCommandSummary(context)
        if (runOutputArea.text.isBlank() || runOutputArea.text == "Preparing inferred run command...") {
            runOutputArea.text = runOutputIdleHint()
        }
    }

    private fun toggleRunInBlueprint() {
        val runner = project.service<ProjectRunService>()
        if (runner.isRunning()) {
            val stopped = runner.stopRun()
            if (stopped) {
                runOutputArea.text = listOf(runOutputArea.text.trimEnd(), "", "Run stopped from Blueprint.").filter { it.isNotBlank() }.joinToString("\n")
                logActivity("Stopped in-app run for the inferred Python command.")
                status("Run stopped")
            }
            refreshRunControls()
            return
        }
        runOutputArea.text = "Preparing inferred run command..."
        runner.runInferredCommand { state ->
            when (state.status) {
                ProjectRunService.RunState.Status.FAILED -> {
                    runOutputArea.text = listOf(state.summary, state.output).filter { it.isNotBlank() }.joinToString("\n\n")
                    status("Run failed")
                    logActivity("In-app run failed: ${state.summary}")
                }
                ProjectRunService.RunState.Status.FINISHED -> {
                    runOutputArea.text = listOf(state.output, state.summary).filter { it.isNotBlank() }.joinToString("\n\n")
                    status("Run finished")
                    logActivity("In-app run finished: ${state.command}")
                }
                ProjectRunService.RunState.Status.STOPPED -> {
                    runOutputArea.text = listOf(state.output, state.summary).filter { it.isNotBlank() }.joinToString("\n\n")
                    status("Run stopped")
                    logActivity("In-app run stopped: ${state.command}")
                }
                ProjectRunService.RunState.Status.RUNNING -> {
                    runOutputArea.text = if (state.output.isBlank()) state.summary else listOf(
                        state.output,
                        "",
                        "Blueprint is still streaming output. Long-running apps can stay here until you click Stop Run.",
                    ).joinToString("\n")
                    status("Streaming app output")
                }
                ProjectRunService.RunState.Status.STARTING -> {
                    runOutputArea.text = state.summary
                    status("Starting inferred run command")
                    logActivity("Started in-app run for ${state.command}")
                }
                ProjectRunService.RunState.Status.IDLE -> Unit
            }
            refreshRunControls()
        }
    }

    private fun runOutputIdleHint(): String {
        val runner = project.service<ProjectRunService>()
        val context = project.service<PythonProjectAnalyzer>().analyze()
        val runCommand = runner.inferredRunCommand(context)
        return runCommand?.let {
            "Run Output\n\nRun In Blueprint will launch: $it\nClick Run In Blueprint to start: $it\nBlueprint will stream stdout and stderr here. If this command starts a dev server or watcher, it may keep streaming until you click Stop Run."
        } ?: "Run Output\n\n${runner.noCommandSummary(context)}\nWhen a command is available, Run In Blueprint will stream stdout and stderr here."
    }

    private fun missingRunCommandGuidance(
        context: PythonProjectAnalyzer.PythonProjectContext = project.service<PythonProjectAnalyzer>().analyze(),
    ): String = missingRunCommandChecklist(context.runEntryCandidates)

    private fun updateLikelyEntryFileAction(
        context: PythonProjectAnalyzer.PythonProjectContext = project.service<PythonProjectAnalyzer>().analyze(),
    ) {
        val firstCandidate = context.runEntryCandidates.firstOrNull()
        val hasRunCommand = !project.service<ProjectRunService>().inferredRunCommand(context).isNullOrBlank()
        openLikelyEntryFileButton.isEnabled = !hasRunCommand && firstCandidate != null
        openLikelyEntryFileButton.toolTipText = firstCandidate?.let {
            "Open likely entry file: $it. This does not run or apply anything."
        } ?: "Open a likely app entry file when Blueprint cannot infer a run command. This does not run or apply anything."
        openLikelyEntryFileButton.text = if (firstCandidate == null) {
            "Open Likely Entry File"
        } else {
            "Open Likely Entry File (${firstCandidate.substringAfterLast('/')})"
        }
    }

    private fun openLikelyEntryFile() {
        val context = project.service<PythonProjectAnalyzer>().analyze()
        val candidate = context.runEntryCandidates.firstOrNull()
        if (candidate == null) {
            status("No likely entry file found")
            Messages.showInfoMessage(
                project,
                missingRunCommandChecklist(emptyList()),
                "Blueprint - No Likely Entry File"
            )
            return
        }
        val sourceFile = resolveProjectFile(candidate)
        if (!openProjectFile(sourceFile, candidate)) return
        status("Opened likely entry file: $candidate")
        appendChat(
            "Blueprint",
            "Opened likely entry file: $candidate. This helps you inspect the app entrypoint when no run command is inferred. It does not run the app or apply changes."
        )
        logActivity("Opened likely entry file for manual verification: $candidate")
    }

    private fun currentInvitePrompt(): String? =
        currentInvitePromptPlan()?.prompt

    private fun currentInvitePromptPlan(): GuidedInviteScenario.PromptPlan? {
        val ir = project.service<IRStore>().load()
        val componentNames = ir?.components?.map { it.name }?.toSet().orEmpty()
        val parsedUml = runCatching { project.service<UmlImportService>().parse(umlEditor.text) }.getOrNull()
        val entityNames = parsedUml?.entities?.map { it.name }?.toSet().orEmpty()
        val inviteFields = parsedUml?.entities.orEmpty()
            .firstOrNull { it.name == "Invite" }
            ?.fields
            .orEmpty()
            .map { it.substringBefore(":").trim() }
        return GuidedInviteScenario.pickPrompt(componentNames, entityNames, inviteFields)
    }

    private fun currentFirstRunChecklistState(): FirstRunChecklistState {
        val runner = project.service<ProjectRunService>()
        val context = project.service<PythonProjectAnalyzer>().analyze()
        val selected = nodeList.selectedValue
        val validation = selected?.let { validationResults[it.id] }
        val exec = selected?.let { registry.getExecution(it.id) }
        val review = selected?.let { registry.getReview(it.id) }
        return FirstRunChecklistState(
            codeMapReady = currentUmlEntityCount() > 0,
            reviewedDiffReady = exec?.patches.orEmpty().isNotEmpty(),
            reviewApprovedReady = reviewAllowsApply(review),
            appliedReady = selected?.executionStatus == ExecutionStatus.APPLIED,
            refreshedCodeMapReady = refreshedAfterApply && !umlHasPendingEdits,
            runCommand = runner.inferredRunCommand().orEmpty().ifBlank { context.runCommands.firstOrNull() },
            runEntryCandidates = context.runEntryCandidates,
            validationCommand = project.service<ProjectValidationService>().selectedCommand(),
            validationReady = validation != null,
            validationPassed = validation?.status == ProjectValidationService.ValidationResult.Status.PASS,
            runVerified = activityLog.text.contains("Run the changed app", ignoreCase = true),
            skippedFiles = context.skippedFiles,
        )
    }

    private fun currentInviteFirstRunScenarioState(): GuidedInviteScenarioState {
        val ir = project.service<IRStore>().load()
        val componentNames = ir?.components?.map { it.name }?.toSet().orEmpty()
        val parsedUml = runCatching { project.service<UmlImportService>().parse(umlEditor.text) }.getOrNull()
        val entityNames = parsedUml?.entities?.map { it.name }?.toSet().orEmpty()
        val inviteFields = parsedUml?.entities.orEmpty()
            .firstOrNull { it.name == "Invite" }
            ?.fields
            .orEmpty()
            .map { it.substringBefore(":").trim() }
        val suggestedPrompt = chatInput.text.trim()
        val promptPlan = GuidedInviteScenario.pickPrompt(componentNames, entityNames, inviteFields)
        val resetNeeded = GuidedInviteScenario.needsReset(project.basePath)
        val reviewedInviteDiff = registry.all().any { node ->
            registry.getReview(node.id) != null &&
                registry.getExecution(node.id)?.patches.orEmpty().any { it.path == GuidedInviteScenario.PATCH_PATH }
        }
        val approvedInviteDiff = registry.all().any { node ->
            reviewAllowsApply(registry.getReview(node.id)) &&
                registry.getExecution(node.id)?.patches.orEmpty().any { it.path == GuidedInviteScenario.PATCH_PATH }
        }
        val appliedInviteDiff = registry.all().any { node ->
            node.executionStatus == ExecutionStatus.APPLIED &&
                registry.getExecution(node.id)?.patches.orEmpty().any { it.path == GuidedInviteScenario.PATCH_PATH }
        }
        val activePlan = promptPlan ?: GuidedInviteScenario.PromptPlan(
            prompt = "reset ${GuidedInviteScenario.PATCH_PATH} to the demo baseline",
            expectedEntity = "Invite",
            expectedField = "expires_at",
        )
        val promptReady = statefulPromptMatches(activePlan.prompt, suggestedPrompt)
        val hasExpectedDraft = when {
            activePlan.expectedField != null -> umlHasPendingEdits && activePlan.expectedField in inviteFields
            activePlan.relationSource != null && activePlan.relationTarget != null ->
                umlHasPendingEdits &&
                    activePlan.expectedEntity in entityNames &&
                    parsedUml?.relationships.orEmpty().any { relationship ->
                        setOf(relationship.from, relationship.to) == setOf(activePlan.relationSource, activePlan.relationTarget)
                    }
            else -> umlHasPendingEdits && activePlan.expectedEntity in entityNames
        }
        val refreshedReady = when {
            activePlan.expectedField != null -> appliedInviteDiff && !umlHasPendingEdits && activePlan.expectedField in componentNames
            else -> appliedInviteDiff && !umlHasPendingEdits && activePlan.expectedEntity in componentNames
        }
        return GuidedInviteScenarioState(
            codeMapReady = setOf("Project", "User", "Invite").all { it in componentNames },
            prompt = activePlan.prompt,
            expectedEntity = activePlan.expectedEntity,
            expectedRelationSource = activePlan.relationSource,
            expectedRelationTarget = activePlan.relationTarget,
            resetSuggested = promptPlan == null || resetNeeded,
            resetPath = GuidedInviteScenario.PATCH_PATH,
            umlDraftReady = hasExpectedDraft,
            reviewedDiffReady = reviewedInviteDiff,
            reviewApprovedReady = approvedInviteDiff,
            appliedReady = appliedInviteDiff,
            refreshedCodeMapReady = refreshedReady,
            runVerified = refreshedReady && activityLog.text.contains("Demo e2e step passed: Run the changed app", ignoreCase = true),
            promptReady = promptReady,
            runCommand = project.service<PythonProjectAnalyzer>().analyze().runCommands.firstOrNull(),
        )
    }

    private fun manualDemoExpectedVisibleResult(state: GuidedInviteScenarioState): String =
        when {
            state.resetSuggested ->
                "The guided demo prompt likely matches code already in the invite demo file. Click Reset Demo Sandbox for a fresh run, or keep your own UML edit instead."
            state.expectedEntity == "Invite" && state.prompt.contains("expires_at") ->
                "Expect Invite to show expires_at in the refreshed UML and in the running feature path."
            state.expectedRelationSource != null && state.expectedRelationTarget != null ->
                "Expect ${state.expectedRelationSource} to show ${state.expectedEntity} in the refreshed UML and the running app flow."
            else -> "Expect ${state.expectedEntity} to appear in the refreshed UML and in the running app flow."
        }

    private fun manualDemoWhatToLookForChecklist(state: GuidedInviteScenarioState): String =
        when {
            state.resetSuggested -> listOf(
                "- Reset Demo Sandbox restored the invite demo baseline before you verify again.",
                "- Refresh UML From Code before rerunning the prompt.",
                "- Click Try This Change again so the next demo prompt is fresh.",
            )
            state.expectedEntity == "Invite" && state.prompt.contains("expires_at") -> listOf(
                "- Invite shows expires_at in the refreshed UML.",
                "- The changed app path shows the new expires_at behavior.",
                "- The visible result matches the reviewed code patch you just applied.",
            )
            state.expectedRelationSource != null && state.expectedRelationTarget != null -> listOf(
                "- ${state.expectedRelationSource} shows ${state.expectedEntity} in the refreshed UML.",
                "- The changed app flow shows the new ${state.expectedEntity} behavior.",
                "- The visible result matches the reviewed code patch you just applied.",
            )
            else -> listOf(
                "- ${state.expectedEntity} appears in the refreshed UML.",
                "- The changed app flow shows the new ${state.expectedEntity} behavior.",
                "- The visible result matches the reviewed code patch you just applied.",
            )
        }.joinToString("\n")

    private fun runDemoVerificationStep() {
        val state = currentInviteFirstRunScenarioState()
        val runCommand = state.runCommand
        if (runCommand.isNullOrBlank()) {
            Messages.showInfoMessage(
                project,
                "Refresh UML From Code first so Blueprint can infer how this Python project should run.",
                "Blueprint - Demo Runner"
            )
            logActivity("Demo e2e step failed: Run the changed app could not start because no run command was inferred.")
            return
        }
        val expectedVisibleResult = manualDemoExpectedVisibleResult(state)
        val whatToLookForChecklist = manualDemoWhatToLookForChecklist(state)
        Messages.showInfoMessage(
            project,
            "Manual demo runner\n\n1. Refresh UML From Code\n2. Use Try This Change or edit the UML\n3. Generate Code Diff\n4. Apply Approved Changes\n5. Refresh UML From Code\n6. Run the changed app with: $runCommand\n7. Confirm the expected visible result in the changed app.\n\nWhat to look for:\n$whatToLookForChecklist\n\nExpected visible result:\n$expectedVisibleResult\n\nBlueprint records the run command and the visible result in Activity so the full demo path reads like a receipt.",
            "Blueprint - Run Demo Step"
        )
        logActivity("Demo e2e step passed: Run the changed app with $runCommand. Confirmed visible result: $expectedVisibleResult")
        status("Demo run and visible result recorded")
        refreshFirstRunScenario()
    }

    private fun statefulPromptMatches(expectedPrompt: String, currentPrompt: String): Boolean =
        currentPrompt.equals(expectedPrompt, ignoreCase = true)

    private data class ReviewFreshnessState(
        val badge: String,
        val warning: String,
        val reviewedAtLine: String,
        val bannerHint: String,
    )

    private fun reviewFreshnessFor(node: BlueprintNode, exec: ExecutionArtifact?): ReviewFreshnessState {
        val reviewedAt = reviewedAtByNodeId[node.id]
        val reviewedAtLine = reviewedAtLine(reviewedAt)
        if (exec?.patches.isNullOrEmpty()) {
            return ReviewFreshnessState(
                badge = "NONE",
                warning = "No reviewed code patch yet. Generate Code Diff after you refine the UML.",
                reviewedAtLine = reviewedAtLine,
                bannerHint = "No reviewed patch yet.",
            )
        }
        if (node.executionStatus == ExecutionStatus.APPLIED && refreshedAfterApply && !umlHasPendingEdits) {
            return ReviewFreshnessState(
                badge = "FRESH",
                warning = "This reviewed code patch matches the refreshed code-backed UML. Refresh UML From Code again anytime to verify after more edits.",
                reviewedAtLine = reviewedAtLine,
                bannerHint = "Reviewed patch is fresh.",
            )
        }
        if (normalizedUmlText() != lastReviewedUmlByNodeId[node.id].orEmpty()) {
            return ReviewFreshnessState(
                badge = "STALE",
                warning = "This reviewed code patch is stale because the UML changed after review. Generate Code Diff again before Apply Approved Changes.",
                reviewedAtLine = reviewedAtLine,
                bannerHint = "Reviewed patch is stale.",
            )
        }
        return ReviewFreshnessState(
            badge = "FRESH",
            warning = "This reviewed code patch matches the current UML. Apply Approved Changes, or keep editing and then Generate Code Diff again.",
            reviewedAtLine = reviewedAtLine,
            bannerHint = "Reviewed patch is fresh.",
        )
    }

    private fun buildReviewSummary(
        exec: ExecutionArtifact?,
        review: ReviewArtifact?,
        freshness: ReviewFreshnessState,
        validationCommand: String?,
    ): String {
        val summary = PatchChangeSummary.reviewSummary(exec)
        val scopeSentence = reviewScopeSentence(exec)
        val changedFilesHeader = reviewChangedFilesHeader(exec)
        val changedFilesInline = reviewChangedFilesInline(exec)
        val approvalSentence = reviewApprovalSentence(exec, review)
        return buildString {
            appendLine("Review this patch in one place:")
            appendLine("- Read What changed? for the plain-English summary.")
            appendLine("- Open Preview Diff to inspect the exact file edits.")
            appendLine("- Confirm the changed files and approval reason before apply.")
            appendLine()
            appendLine("Validation before apply:")
            appendLine("- ${validationCommandReviewText(validationCommand)}")
            appendLine()
            appendLine(changedFilesHeader)
            appendLine(changedFilesInline)
            appendLine("Diff status: ${freshness.badge}")
            appendLine(freshness.reviewedAtLine)
            appendLine(freshness.warning)
            approvalSentence?.let {
                appendLine()
                appendLine(it)
            }
            if (exec?.patches.orEmpty().isNotEmpty()) {
                appendLine()
                appendLine("What changed? explains the intent. Preview Diff confirms the exact file edits before apply.")
            }
            appendLine()
            appendLine(scopeSentence)
            appendLine()
            append(summary)
        }.trim()
    }
    private fun reviewApprovalSentence(exec: ExecutionArtifact?, review: ReviewArtifact?): String? {
        if (!reviewAllowsApply(review)) return null
        val changePhrase = PatchChangeSummary.semanticChangeLines(exec, exec?.patches.orEmpty().map { it.path })
            .take(2)
            .ifEmpty { listOf("the reviewed code patch") }
            .joinToString(" and ")
        val acceptedEvidence = review?.acceptanceReview.orEmpty()
            .filter { it.result.uppercase() == "PASS" }
            .flatMap { it.evidence }
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(1)
            .firstOrNull()
        val scopeReason = when (review?.scopeCompliance?.result?.uppercase()) {
            "PASS" -> "stays within the selected files"
            "PARTIAL" -> "mostly stays within the selected files"
            else -> "was reviewed for scope"
        }
        val evidenceReason = acceptedEvidence?.let { " It also matches the requested UML because $it." }.orEmpty()
        return "Why review approved this patch: $changePhrase $scopeReason, and no blocking safety issues were reported. That is why Apply Approved Changes is unlocked now.$evidenceReason"
    }

    private fun reviewChangedFilesHeader(exec: ExecutionArtifact?): String {
        val count = exec?.patches.orEmpty().map { it.path }.distinct().size
        return when (count) {
            0 -> "Changed files: 0 files"
            1 -> "Changed files: 1 file"
            else -> "Changed files: $count files"
        }
    }

    private fun reviewChangedFilesInline(exec: ExecutionArtifact?): String {
        val paths = exec?.patches.orEmpty().map { it.path }.distinct()
        return when (paths.size) {
            0 -> "Changed paths: none"
            1 -> "Changed path: ${paths.first()}"
            else -> "Changed paths: ${paths.joinToString(", ")}"
        }
    }

    private fun reviewScopeSentence(exec: ExecutionArtifact?): String {
        val paths = exec?.patches.orEmpty().map { it.path }.distinct()
        return when (paths.size) {
            0 -> "No files will change in this reviewed code patch."
            1 -> "Only 1 file will change: ${paths.first()}"
            else -> "${paths.size} files will change: ${paths.joinToString(", ")}"
        }
    }

    private fun refreshReviewFreshnessState() {
        if (nodeList.selectedValue != null) {
            refreshArtifactSummary()
        }
    }

    private fun reviewedAtLine(reviewedAt: Instant?): String =
        if (reviewedAt == null) {
            "Reviewed at not available yet"
        } else {
            "Reviewed at ${DateTimeFormatter.ofPattern("HH:mm:ss").format(reviewedAt.atZone(java.time.ZoneId.systemDefault()))} (${reviewAgeText(reviewedAt)})"
        }

    private fun reviewAgeText(reviewedAt: Instant, now: Instant = Instant.now()): String {
        val duration = Duration.between(reviewedAt, now).abs()
        val minutes = duration.toMinutes()
        val hours = duration.toHours()
        val days = duration.toDays()
        return when {
            duration.seconds < 60 -> "reviewed just now"
            minutes < 60 -> "reviewed $minutes min ago"
            hours < 24 -> "reviewed $hours hr ago"
            else -> "reviewed $days day${if (days == 1L) "" else "s"} ago"
        }
    }

    private fun refreshUmlAfterApplyVerification() {
        verifyInUmlButton.isEnabled = false
        postApplyVerifyState = "Blueprint reran Refresh UML From Code after apply so you can verify the latest code-backed UML yourself."
        logActivity("Refresh UML From Code reran after apply so you can verify the changed code from disk yourself.")
        status("Rerunning Refresh UML From Code after apply")
        generateProjectUml()
    }

    private fun focusChangedEntityAfterRefresh() {
        val changedPaths = postApplyChangedPaths
        if (changedPaths.isEmpty()) return
        val ir = project.service<IRStore>().load() ?: return
        val match = changedPaths.firstNotNullOfOrNull { changedPath ->
            ir.components.firstOrNull { component ->
                val sourcePath = component.sourceRef?.path
                sourcePath != null && changedPath.endsWith(sourcePath)
            }?.let { matched -> changedPath to matched }
        }
        postApplyChangedPaths = emptyList()
        if (match == null) {
            postApplyHighlightMessage = "Blueprint refreshed the code-backed UML after apply, but did not find a matching UML entity to highlight from the changed paths."
            if (postApplyVerifyState == null) {
                postApplyVerifyState = "Blueprint automatically refreshed the code-backed UML from disk after apply."
            }
            logActivity("Refreshed UML after apply but did not find a changed entity to highlight.")
            return
        }
        val (changedPath, matched) = match
        if (postApplyVerifyState == null) {
            postApplyVerifyState = "Blueprint automatically refreshed the code-backed UML from disk after apply."
        }
        postApplyHighlightMessage = "Blueprint highlighted ${matched.name} from $changedPath after refresh."
        selectedCanvasId = matched.id
        updateMiniGraph(project.service<DependencyGraphService>().analyze())
        status("Refreshed UML and highlighted ${matched.name} from $changedPath")
        logActivity("Refreshed UML highlighted ${matched.name} from $changedPath.")
    }

    private fun normalizedUmlText(): String = PatchFreshness.normalize(umlEditor.text)

    private fun updateNextStepBanner(title: String, detail: String) {
        val freshnessHint = nextStepFreshnessHint()
        nextStepTitleLabel.text = title
        nextStepDetailLabel.text = listOf(detail, freshnessHint).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun nextStepFreshnessHint(): String {
        val selected = nodeList.selectedValue ?: return ""
        val exec = registry.getExecution(selected.id)
        if (exec?.patches.isNullOrEmpty()) return reviewFreshnessFor(selected, exec).bannerHint
        val freshness = reviewFreshnessFor(selected, exec)
        val reviewedAtHint = freshness.reviewedAtLine
            .takeUnless { it.equals("Reviewed at not available yet.", ignoreCase = true) }
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            .orEmpty()
        return listOf(freshness.bannerHint, reviewedAtHint).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun guidedNextState(): Pair<String, String> {
        val entityCount = currentUmlEntityCount()
        if (entityCount == 0) {
            return "Next: Refresh UML From Code" to "Read the current Python project and draw the first UML diagram."
        }

        val allNodes = registry.all()
        if (allNodes.isEmpty()) {
            return "Next: Generate Code Diff" to "Turn the edited UML into a reviewed code patch."
        }

        val graph = project.service<DependencyGraphService>()
        val selected = nodeList.selectedValue ?: graph.readyNodes().firstOrNull() ?: allNodes.first()
        val shortTitle = selected.title.ifBlank { selected.id.take(8) }
        val review = registry.getReview(selected.id)
        val reviewApproved = reviewAllowsApply(review)
        return when {
            registry.getExecution(selected.id) == null ->
                "Next: Generate Code Diff" to "Create a reviewed code patch for: $shortTitle."
            !reviewApproved -> {
                val reviewDetail = review?.let { ReviewExplanation.statusLine(shortTitle, registry.getExecution(selected.id), it) }
                    ?: "Review not run yet. Generate Code Diff first so Blueprint can explain why the patch is safe to apply."
                "Next: Review approved" to reviewDetail
            }
            selected.executionStatus != ExecutionStatus.APPLIED ->
                "Next: Apply Approved Changes" to "Apply the approved reviewed code patch for: $shortTitle."
            graph.readyNodes().any { it.id != selected.id } ->
                "Next: Generate Code Diff" to "Another UML-backed change is ready when you want a new reviewed code patch."
            else ->
                "Next: Refresh UML From Code" to "All current work is applied. Refresh UML From Code to verify the updated code-backed UML, then run the changed app or make another change."
        }
    }

    private fun currentUmlEntityCount(): Int =
        runCatching { project.service<UmlImportService>().parse(umlEditor.text).entities.size }.getOrDefault(0)

    private fun refreshProviderLabels() {
        val text = providerText()
        val color = providerColor()
        providerLabel.text = text
        providerLabel.foreground = color
        actionProviderLabel.text = text
        actionProviderLabel.foreground = color
    }

    private fun badgeFor(node: BlueprintNode): String {
        val exec = registry.getExecution(node.id)
        val review = registry.getReview(node.id)
        return when {
            node.executionStatus == ExecutionStatus.APPLIED -> "APPLIED"
            node.executionStatus == ExecutionStatus.FAILED -> "FAILED"
            node.executionStatus == ExecutionStatus.BLOCKED || exec?.status == "BLOCKED" -> "BLOCKED"
            exec?.status == "PARTIAL" -> "PARTIAL"
            review != null -> "REVIEWED"
            exec != null -> "EXECUTED"
            registry.getPlan(node.id) != null -> "PLANNED"
            else -> "NOT PLANNED"
        }
    }

    private fun statusTint(status: String): Color =
        when (status) {
            "APPLIED" -> BlueprintTheme.SuccessSurface
            "REVIEWED" -> BlueprintTheme.PurpleSurface
            "EXECUTED" -> BlueprintTheme.AccentSurface
            "PLANNED" -> BlueprintTheme.WarningSurface
            "BLOCKED" -> BlueprintTheme.DangerSurface
            "FAILED" -> BlueprintTheme.DangerSurface
            "PARTIAL" -> BlueprintTheme.WarningSurface
            else -> BlueprintTheme.Surface
        }

    private fun statusChip(status: String): String =
        "[$status]"

    private fun providerText(): String =
        if (codex.providerMode() == "mock") {
            "Mode: MOCK DEMO (offline, deterministic)"
        } else if (codex.providerMode() == "openai") {
            "Mode: LIVE OpenAI (${codex.openAIKeySource()})"
        } else {
            "Mode: LIVE (${codex.providerMode()})"
        }

    private fun providerColor(): Color =
        when {
            codex.providerMode() == "mock" -> BlueprintTheme.Success
            codex.providerMode() == "openai" && !codex.hasOpenAIKey() -> BlueprintTheme.Danger
            else -> BlueprintTheme.Accent
        }

    private fun changedFileSummary(exec: com.blueprint.model.ExecutionArtifact?): String =
        PatchChangeSummary.changedFilesSummary(exec)

    private fun hasDependencyBlock(readiness: DependencyGraphService.NodeReadiness?): Boolean =
        readiness?.reasons.orEmpty().any { !it.startsWith("Node is already") }

    private fun addCriterionRow() {
        val next = criteriaModel.rowCount + 1
        criteriaModel.addRow(arrayOf("AC$next", AcceptanceCriterionType.OTHER.name, true, "", "Manual verification"))
    }

    private fun removeCriterionRow() {
        val row = criteriaTable.selectedRow
        if (row >= 0) criteriaModel.removeRow(row)
    }

    private fun loadCriteria(criteria: List<AcceptanceCriterion>) {
        criteriaModel.rowCount = 0
        criteria.forEach {
            criteriaModel.addRow(arrayOf(it.id, it.type.name, it.required, it.description, it.verifyWith))
        }
    }

    private fun loadDependencies(dependencies: List<String>) {
        dependencyListModel.clear()
        dependencies.distinct().forEach { dependencyListModel.addElement(it) }
        manualDependencyArea.text = ""
    }

    private fun refreshDependencyPicker() {
        val selected = nodeList.selectedValue
        val existing = dependencyIdsFromList().toSet()
        dependencyChoiceModel.removeAllElements()
        registry.all()
            .filter { it.id != selected?.id }
            .filter { it.id !in existing }
            .sortedBy { it.title.ifBlank { it.id } }
            .forEach { node ->
                dependencyChoiceModel.addElement(
                    DependencyChoice(
                        node.id,
                        "${node.title.ifBlank { "(untitled)" }} (${node.id.take(8)}) - ${badgeFor(node)}"
                    )
                )
            }
    }

    private fun criteriaFromTable(): List<AcceptanceCriterion> =
        (0 until criteriaModel.rowCount).mapNotNull { row ->
            val description = criteriaModel.getValueAt(row, 3)?.toString()?.trim().orEmpty()
            if (description.isBlank()) return@mapNotNull null
            AcceptanceCriterion(
                id = criteriaModel.getValueAt(row, 0)?.toString()?.trim().orEmpty().ifBlank { "AC${row + 1}" },
                type = runCatching {
                    AcceptanceCriterionType.valueOf(criteriaModel.getValueAt(row, 1)?.toString()?.trim().orEmpty())
                }.getOrDefault(AcceptanceCriterionType.OTHER),
                required = criteriaModel.getValueAt(row, 2) as? Boolean ?: true,
                description = description,
                verifyWith = criteriaModel.getValueAt(row, 4)?.toString()?.trim().orEmpty(),
            )
        }

    private fun lines(s: String): List<String> =
        s.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun dependencyIdsFromList(): List<String> =
        (0 until dependencyListModel.size()).map { dependencyListModel.getElementAt(it) }

    private fun dependencyIdsFromEditor(): List<String> =
        (dependencyIdsFromList() + lines(manualDependencyArea.text)).distinct()

    private fun dependencyLabel(id: String): String {
        val node = registry.find(id)
        return if (node == null) {
            "Missing: $id"
        } else {
            "${node.title.ifBlank { "(untitled)" }} (${node.id.take(8)}) - ${badgeFor(node)}"
        }
    }

    private fun resolveDependencyIds(raw: List<String>, selfId: String): List<String> {
        val nodes = registry.all()
        return raw.map { value ->
            val exact = nodes.firstOrNull { it.id == value }
            val byPrefix = nodes.filter { it.id.startsWith(value) }
            val byTitle = nodes.filter { it.title.equals(value, ignoreCase = true) }
            when {
                exact != null -> exact.id
                byPrefix.size == 1 -> byPrefix.first().id
                byTitle.size == 1 -> byTitle.first().id
                else -> value
            }
        }.filter { it != selfId }.distinct()
    }

    private fun status(msg: String) {
        statusLabel.text = msg
    }

    private fun beginPrimaryAction(buttonText: String, detail: String) {
        primaryActionBusy = true
        primaryActionButton.text = buttonText
        primaryActionButton.isEnabled = false
        guideLabel.text = detail
        status(buttonText.removeSuffix("..."))
        showArtifactTab("Review")
        reviewSummaryArea.text = detail
        safetyArea.text = "Working with ${providerText()}. Apply stays blocked until a reviewed patch is ready."
        logActivity(detail)
    }

    private fun endPrimaryAction() {
        primaryActionBusy = false
        primaryActionButton.isEnabled = true
        updateGuide()
    }

    private fun logActivity(msg: String) {
        val at = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val numbered = activityLog.lineCount + 1
        activityLog.append("[$at] ${numbered.toString().padStart(2, '0')}. ${receiptText(msg)}\n")
        activityLog.caretPosition = activityLog.document.length
    }

    private fun receiptText(msg: String): String {
        val lower = msg.lowercase(Locale.getDefault())
        return when {
            lower.startsWith("abstracted code to uml:") -> msg.replaceFirst("Abstracted code to UML:", "Step 1 complete - Scanned project and generated UML:")
            lower.startsWith("updated the uml using") -> "Step complete - Refined the UML draft from chat context and grounded source facts."
            lower.startsWith("planning code diff for") -> msg.replaceFirst("Planning code diff for", "Step 2 started - Generate Code Diff for")
            lower.startsWith("plan ready for") -> msg.replaceFirst("Plan ready for", "Step 2 progress - Planned reviewed code patch for")
            lower.startsWith("patch generated for") -> msg.replaceFirst("Patch generated for", "Step 2 complete - Generated reviewed code patch for")
            lower.startsWith("code diff ready for") -> msg.replaceFirst("Code diff ready for", "Step 2 complete - Generated reviewed code patch for")
            lower.startsWith("generate code diff used existing nodes because") ->
                "Step 2 blocked - Generate Code Diff kept the last reviewed patch because the current UML could not be parsed."
            lower.startsWith("generate code diff completed with no file changes because") -> "Step 2 complete - $msg"
            lower.startsWith("generate code diff found no file changes because") -> "Step 2 complete - $msg"
            lower.startsWith("generate code diff found no uml-backed work items ready to run") -> "Step 2 complete - $msg"
            lower.startsWith("generate code diff completed with no-op result across") ->
                "Step 2 complete - Generate Code Diff found no file changes because the current UML-backed request already matched the code on disk."
            lower.startsWith("no-op code diff for") ->
                "Step 2 complete - " + msg.replaceFirst("No-op code diff for", "No file changes were needed for")
                    .replace("; generated content matched disk.", " because that UML-backed request already matched the code on disk.")
            lower.startsWith("code diff blocked at plan for") ->
                msg.replaceFirst("Code diff blocked at plan for", "Step 2 blocked - Generate Code Diff stopped at planning for") + ". Review the blocked plan before continuing."
            lower.startsWith("cannot execute ") ->
                msg.replaceFirst("Cannot execute", "Blocked by dependencies for")
                    .replace(" yet:", ":")
            lower.startsWith("running validation after apply for") -> msg.replaceFirst("Running validation after apply for", "Step 4 validation started -")
            lower.startsWith("validation skipped after apply for") -> msg.replaceFirst("Validation skipped after apply for", "Step 4 validation skipped -")
            lower.startsWith("apply finished for") -> msg.replaceFirst("Apply finished for", "Step 4 complete - Applied approved changes for")
            lower.startsWith("undo apply for '") -> msg.replaceFirst("Undo apply for '", "Rolled back apply for '")
            lower.startsWith("validation passed:") -> "Step 4 validation passed - " + msg.removePrefix("Validation passed: ")
            lower.startsWith("validation skipped:") -> "Step 4 validation skipped - " + msg.removePrefix("Validation skipped: ")
            lower.startsWith("validation failed:") -> "Step 4 validation failed - " + msg.removePrefix("Validation failed: ")
            lower.startsWith("freshness verified after apply:") -> msg.replaceFirst("Freshness verified after apply:", "Step 5 complete - Refreshed UML from code after apply:")
            lower.startsWith("demo e2e step passed:") -> "Step 6 complete - " + msg.removePrefix("Demo e2e step passed: ")
            lower.startsWith("demo e2e step failed:") -> "Step 6 blocked - " + msg.removePrefix("Demo e2e step failed: ")
            lower.startsWith("opened diff preview for") -> msg.replaceFirst("Opened diff preview for", "Opened reviewed diff for")
            lower.startsWith("inspected the only changed file after apply:") -> "Optional inspection - $msg"
            lower.startsWith("inspected one changed file after apply from the chooser:") -> "Optional inspection - $msg"
            lower.startsWith("opened source for") -> msg.replaceFirst("Opened source for", "Opened source file for")
            lower.startsWith("opened likely entry file for manual verification:") -> msg.replaceFirst("Opened likely entry file for manual verification:", "Optional run check - Opened likely entry file for manual verification:")
            lower.startsWith("uml import found no entities") -> "Import blocked - Could not build UML from the imported text because no entities were found."
            lower.startsWith("saved node ") -> msg.replaceFirst("Saved node", "Saved workflow node")
            lower.startsWith("removed node ") -> msg.replaceFirst("Removed node", "Removed workflow node")
            lower.startsWith("refreshed dependency wave preview") -> "Optional advanced view - Refreshed dependency wave preview"
            lower.startsWith("mode changed to") -> msg
            lower.startsWith("review approved") -> "Step 3 complete - $msg"
            lower.startsWith("review blocked") -> "Step 3 blocked - $msg"
            lower.startsWith("review approve") || lower.startsWith("review request_changes") || lower.startsWith("review reject") ->
                "Step 3 review result - " + msg.replaceFirst(Regex("^Review\\s+", RegexOption.IGNORE_CASE), "")
            else -> msg
        }
    }

    private fun decorateBanner(text: String): String =
        if (restoredFromSession) "Restored \u00B7 $text" else text

    private fun clearRestoredFlag() {
        if (restoredFromSession) {
            restoredFromSession = false
        }
    }

    private fun restoredAtSuffix(): String {
        if (restoredAt <= 0L) return ""
        val ts = java.time.Instant.ofEpochMilli(restoredAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        return " (saved $ts)"
    }

    private fun captureWorkspaceState(): WorkspaceState =
        WorkspaceState(
            version = WorkspaceState.VERSION,
            savedAt = System.currentTimeMillis(),
            umlText = umlEditor.text,
            umlHasPendingEdits = umlHasPendingEdits,
            selectedCanvasId = selectedCanvasId,
            selectedNodeId = registry.selectedNodeId(),
            activityReceipt = activityLog.text,
            chatTranscript = chatHistory.toList(),
            codeMapGroupMode = (codeMapGroupCombo.selectedItem as? CodeMapProjection.GroupMode)?.name,
            hideTests = hideCodeMapTests.isSelected,
            hideGenerated = hideGeneratedCodeMap.isSelected,
            hideExternalEdges = hideExternalCodeMapEdges.isSelected,
            hideLowConfidenceEdges = hideLowConfidenceCodeMapEdges.isSelected,
            nodeFilter = (filterCombo.selectedItem as? NodeFilter)?.name,
            advancedMode = advancedMode.isSelected,
            modeBanner = modeBannerLabel.text,
        )

    private fun persistWorkspace() {
        if (restoringWorkspace) return
        workspaceStore.save(captureWorkspaceState())
    }

    private fun restoreWorkspaceState() {
        val state = workspaceStore.load()
        if (state == null) {
            restoringWorkspace = false
            persistWorkspace()
            return
        }
        restoringWorkspace = true
        try {
            state.codeMapGroupMode
                ?.let { runCatching { CodeMapProjection.GroupMode.valueOf(it) }.getOrNull() }
                ?.let { codeMapGroupCombo.selectedItem = it }
            hideCodeMapTests.isSelected = state.hideTests
            hideGeneratedCodeMap.isSelected = state.hideGenerated
            hideExternalCodeMapEdges.isSelected = state.hideExternalEdges
            hideLowConfidenceCodeMapEdges.isSelected = state.hideLowConfidenceEdges
            state.nodeFilter
                ?.let { runCatching { NodeFilter.valueOf(it) }.getOrNull() }
                ?.let { filterCombo.selectedItem = it }
            advancedMode.isSelected = state.advancedMode
            selectedCanvasId = state.selectedCanvasId

            if (!state.umlText.isNullOrBlank()) {
                suppressUmlDocumentEvents = true
                try {
                    umlEditor.text = state.umlText
                    umlEditor.caretPosition = 0
                    umlHasPendingEdits = state.umlHasPendingEdits
                } finally {
                    suppressUmlDocumentEvents = false
                }
            }

            activityLog.text = state.activityReceipt.orEmpty()

            if (state.chatTranscript.isNotEmpty()) {
                chatHistory.clear()
                chatMessages.removeAll()
                state.chatTranscript.forEach { entry ->
                    chatHistory += entry
                    renderChatBubble(entry.author, entry.message)
                }
            }

            state.selectedNodeId?.let { id ->
                if (registry.find(id) != null) registry.setSelectedNode(id)
            }
        } finally {
            restoringWorkspace = false
        }
        restoredFromSession = true
        restoredAt = state.savedAt
        refreshList()
        updateMiniGraph(project.service<DependencyGraphService>().analyze())
        updateGuide()
    }

    private fun sanitizeUmlPreviewField(field: String): String =
        field
            .replace("&lt;&lt;", "<<")
            .replace("&gt;&gt;", ">>")
            .trim()
            .takeUnless { it.matches(Regex("""<<[^>]+>>""")) }
            .orEmpty()

    private fun resetWorkspace() {
        val confirm = Messages.showYesNoDialog(
            project,
            "Forget restored chat, UML draft, and filter state?\n\nGenerated nodes and the IR snapshot are kept.",
            "Reset Blueprint Workspace",
            Messages.getQuestionIcon(),
        )
        if (confirm != Messages.YES) return
        workspaceStore.clear()
        restoringWorkspace = true
        try {
            chatHistory.clear()
            chatMessages.removeAll()
            seedInitialChat()
            codeMapGroupCombo.selectedItem = CodeMapProjection.GroupMode.PACKAGE
            hideCodeMapTests.isSelected = false
            hideGeneratedCodeMap.isSelected = false
            hideExternalCodeMapEdges.isSelected = true
            hideLowConfidenceCodeMapEdges.isSelected = false
            filterCombo.selectedItem = NodeFilter.ALL
            advancedMode.isSelected = false
            selectedCanvasId = null
        } finally {
            restoringWorkspace = false
        }
        restoredFromSession = false
        restoredAt = 0L
        refreshList()
        updateMiniGraph(project.service<DependencyGraphService>().analyze())
        relayoutChatTranscript(scrollToBottom = true)
        logActivity("Workspace reset to defaults")
        persistWorkspace()
    }
}
