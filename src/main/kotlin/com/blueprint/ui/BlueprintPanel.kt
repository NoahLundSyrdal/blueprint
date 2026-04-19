package com.blueprint.ui

import com.blueprint.ir.IRStore
import com.blueprint.model.AcceptanceCriterion
import com.blueprint.model.AcceptanceCriterionType
import com.blueprint.model.BlueprintNode
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
import com.blueprint.service.PythonProjectAnalyzer
import com.blueprint.service.PythonUmlGenerator
import com.blueprint.service.ReviewService
import com.blueprint.service.UmlImportService
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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

internal data class GuidedInviteScenarioState(
    val codeMapReady: Boolean,
    val umlDraftReady: Boolean,
    val reviewedDiffReady: Boolean,
    val appliedReady: Boolean,
    val refreshedCodeMapReady: Boolean,
)

internal object GuidedInviteScenario {
    const val PROMPT = "add an InvitePolicy entity"
    const val PATCH_PATH = "blueprint_demo/imported_invite/models.py"

    fun matchesProject(projectName: String, basePath: String?): Boolean {
        val normalizedPath = basePath.orEmpty().replace('\\', '/')
        return projectName == "invite_project" || normalizedPath.endsWith("/examples/invite_project")
    }

    fun checklistText(state: GuidedInviteScenarioState): String {
        val currentStep = when {
            !state.codeMapReady -> 1
            !state.umlDraftReady -> 2
            !state.reviewedDiffReady -> 3
            !state.appliedReady -> 4
            !state.refreshedCodeMapReady -> 5
            else -> 0
        }
        return listOf(
            "${stepMarker(1, currentStep, state.codeMapReady)} Abstract Code to UML -> expect Project, User, and Invite in the current code map.",
            "${stepMarker(2, currentStep, state.umlDraftReady)} Use demo prompt: \"$PROMPT\" -> expect InvitePolicy linked from Invite in the UML draft.",
            "${stepMarker(3, currentStep, state.reviewedDiffReady)} Generate Code Diff -> expect a reviewed diff for $PATCH_PATH.",
            "${stepMarker(4, currentStep, state.appliedReady)} Apply Approved Changes -> expect the imported invite patch to be written to disk.",
            "${stepMarker(5, currentStep, state.refreshedCodeMapReady)} Refresh UML From Code -> expect InvitePolicy to appear in the refreshed current code map.",
        ).joinToString("\n")
    }

    private fun stepMarker(step: Int, currentStep: Int, done: Boolean): String =
        when {
            done -> "[done]"
            step == currentStep -> "[next]"
            else -> "[wait]"
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
    private var refreshingList = false

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
    private val reviewSummaryArea = JBTextArea(3, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
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
    private val guideLabel = JLabel("Generate UML, change it with chat, then generate a code diff.")
    private val firstRunScenarioArea = JBTextArea(5, 40).apply {
        isEditable = false
        isFocusable = false
        lineWrap = true
        wrapStyleWord = true
        rows = 5
    }
    private val firstRunPromptButton = JButton("Use Demo Prompt").apply {
        addActionListener { sendSuggestedChat(GuidedInviteScenario.PROMPT) }
    }
    private val primaryActionButton = JButton("Generate Code Diff").apply {
        putClientProperty("blueprint.primary", true)
        addActionListener { runPrimaryProductAction() }
    }
    private val applyApprovedButton = JButton("Apply Approved Changes").apply { addActionListener { applyChanges(null) } }
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
    private val umlEditor = JBTextArea(18, 72).apply {
        lineWrap = false
        text = """
            classDiagram
            %% Start here:
            %% 1. Click "Abstract Code to UML" to read this Python project.
            %% 2. Edit the UML directly or ask chat to refine it.
            %% 3. Click "Create Code Nodes" when the design is ready.
            %%
            %% This loop can run anytime:
            %% codebase -> UML -> chat refinement -> code nodes -> apply -> UML again
        """.trimIndent()
    }

    private val planArea = JBTextArea().apply { isEditable = false }
    private val execArea = JBTextArea().apply { isEditable = false }
    private val reviewArea = JBTextArea().apply { isEditable = false }
    private val secondaryTabs = JTabbedPane()
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
        SwingUtilities.invokeLater { relayoutChatTranscript() }
        logActivity(
            if (shouldShowInviteFirstRunScenario()) {
                "Blueprint ready. Use the first-run invite demo checklist, keep mock mode on, and follow the guided flow."
            } else {
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
            text.contains("Create Code Nodes") ||
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
        }
        val refreshCodeMapLayout: () -> Unit = {
            updateMiniGraph(project.service<DependencyGraphService>().analyze())
            logActivity("Code map layout: ${codeMapGroupCombo.selectedItem}")
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
            add(JLabel("Workflow: UML -> code diff -> apply").apply {
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
            add(JButton("Abstract Code to UML").apply { addActionListener { generateProjectUml() } })
            add(JButton("Paste UML").apply { addActionListener { importUml() } })
            add(JButton("Create Code Nodes").apply { addActionListener { generateCodeFromUml() } })
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
            add(JBScrollPane(reviewSummaryArea), BorderLayout.CENTER)
            val lower = JPanel(GridLayout(1, 3, 6, 0)).apply {
                add(JBScrollPane(safetyArea))
                add(JBScrollPane(dependencyBlockArea))
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
                }, BorderLayout.CENTER)
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
                        add(firstRunScenarioArea, BorderLayout.CENTER)
                        add(
                            JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                                add(firstRunPromptButton)
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
                add(guideLabel.apply {
                    foreground = Color(0x444444)
                }, BorderLayout.CENTER)
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                add(mockMode)
                add(actionProviderLabel.apply { foreground = providerColor() })
                add(advancedMode)
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                add(JButton("Refresh UML From Code").apply { addActionListener { generateProjectUml() } })
            })
            val advancedRows = listOf(
                JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                add(previewDiffButton)
                add(applyApprovedButton)
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
        val demoPrompt = if (shouldShowInviteFirstRunScenario()) GuidedInviteScenario.PROMPT else "add a Supplier entity"
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
            "generate code" in lower || "code nodes" in lower -> {
                "When the UML looks right, click Create Code Nodes. Blueprint will turn the current UML into scoped nodes, then you can plan, execute, review, preview the diff, and apply."
            }
            "abstract" in lower || "sync" in lower -> {
                "Click Abstract Code to UML at any time. Blueprint will rescan the Python project and replace the editable UML with the current code architecture."
            }
            "blocked" in lower || "why" in lower -> {
                val node = selected ?: return "Select a node in the UML diagram first, then ask why it is blocked."
                val readiness = graph.readinessFor(node)
                if (readiness.ready) {
                    "${node.title.ifBlank { node.id.take(8) }} is ready. Run Generate Plan, then Execute Node."
                } else {
                    "Blocked reasons for ${node.title.ifBlank { node.id.take(8) }}:\n" +
                        readiness.reasons.joinToString("\n") { "- $it" }
                }
            }
            "next" in lower || "run" in lower -> {
                if (ready.isEmpty()) {
                    "No nodes are ready right now. Check the UML diagram for blocked nodes, or select a node and ask why it is blocked."
                } else {
                    "Next ready node: ${ready.first().title.ifBlank { ready.first().id.take(8) }}.\nRun Generate Plan -> Execute Node -> Review -> Preview Diff -> Apply All."
                }
            }
            "changed" in lower || "diff" in lower -> {
                val node = selected ?: return "Select a node first; I will summarize its generated changes."
                changedFileSummary(registry.getExecution(node.id))
            }
            "uml" in lower || "diagram" in lower -> {
                if (shouldShowInviteFirstRunScenario()) {
                    "The main canvas is editable Mermaid UML. For the guided invite demo, try '${GuidedInviteScenario.PROMPT}', then generate a reviewed code diff."
                } else {
                    "The main canvas is editable Mermaid UML. Ask for architecture changes like 'add a Supplier entity' or 'make CarCompany own many Dealerships'. I will rewrite the UML, then you can Create Code Nodes."
                }
            }
            selected != null -> {
                val readiness = graph.readinessFor(selected)
                "${selected.title.ifBlank { selected.id.take(8) }} is selected. Status: ${badgeFor(selected)}. " +
                    if (readiness.ready) "It is ready to run." else "It is blocked; ask 'why blocked' for details."
            }
            else -> "Start with Abstract Code to UML. Refine the editable diagram here with chat, then click Create Code Nodes when the architecture is ready."
        }
    }

    private fun shouldAnswerWithModel(message: String): Boolean {
        if (codex.providerMode() == "mock") return false
        val lower = message.lowercase()
        if (!codex.hasOpenAIKey() && codex.providerMode() == "openai") return false
        if (listOf("run", "next", "why", "blocked", "diff", "changed", "generate code", "code nodes", "apply").any { it in lower }) {
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
            !listOf("what next", "what should", "why", "blocked", "diff", "changed", "generate code", "code nodes", "explain").any { it in lower }
    }

    private fun refineUmlWithChat(message: String) {
        status("Refining UML with ${providerText()}...")
        appendChat("Blueprint", "Refining the editable UML. In live mode this uses the configured OpenAI provider/API key.")
        val currentUml = umlEditor.text
        Thread {
            val result = if (codex.providerMode() == "mock") {
                CodexClient.Result(mockUmlEdit(currentUml, message), ok = true)
            } else {
                codex.sendPromptResult(umlChatPrompt(currentUml, message))
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
                umlStatusLabel.text = "UML: refined by chat. Create Code Nodes when ready, or keep editing."
                appendChat("Blueprint", "Updated the UML. Review it in the main canvas, then keep refining or click Create Code Nodes.")
                updateGuide()
                status("UML refined")
            }
        }.start()
    }

    private fun umlChatPrompt(currentUml: String, message: String): String =
        """
        You are Blueprint, an architecture assistant inside PyCharm.

        The user edits a Mermaid UML classDiagram that will later be converted into scoped code-generation nodes.
        Update the UML according to the user's request.

        Rules:
        - Return only Mermaid classDiagram text.
        - Preserve useful existing classes, fields, methods, and relationships unless the user asked to remove them.
        - Keep names clear and Python-friendly.
        - Prefer class blocks and simple relationship lines.
        - Do not include explanations, markdown fences, or prose.

        Current UML:
        $currentUml

        User request:
        $message
        """.trimIndent()

    private fun architectureChatPrompt(message: String): String {
        val selected = nodeList.selectedValue
        val graph = project.service<DependencyGraphService>()
        val ready = graph.readyNodes()
        return """
        You are Blueprint, an architecture assistant inside PyCharm.

        Product loop:
        codebase -> editable UML -> chat refinement -> code nodes -> plan/execute/review/apply -> UML again.

        Answer the user's question clearly and briefly. Do not claim you changed code unless the user used the execution buttons.
        When useful, refer to the current UML and generated nodes.

        Current UML:
        ${umlEditor.text}

        Selected generated node:
        ${selected?.let { "${it.title} (${it.type.name.lowercase()}, ${badgeFor(it)})" } ?: "none"}

        Ready generated nodes:
        ${ready.joinToString("\n") { node -> "- ${node.title.ifBlank { node.id.take(8) }}" }.ifBlank { "none" }}

        User question:
        $message
        """.trimIndent()
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
        setUmlEditorText(generated.text, pendingEdits = false)
        umlStatusLabel.text = "UML: ${generated.classCount} class(es), ${generated.relationshipCount} relationship(s), ${generated.filesScanned} file(s) scanned."
        graphArea.text = buildString {
            appendLine("Abstracted Python codebase to editable UML.")
            appendLine("Classes: ${generated.classCount}")
            appendLine("Relationships: ${generated.relationshipCount}")
            appendLine("Files scanned: ${generated.filesScanned}")
            if (generated.warnings.isNotEmpty()) {
                appendLine()
                appendLine("Warnings:")
                generated.warnings.forEach { appendLine("- $it") }
            }
        }.trim()
        appendChat("Blueprint", "I abstracted the current Python code into UML. Edit it directly or ask chat to refine the architecture. Create Code Nodes when ready.")
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
                JLabel("Paste PlantUML, Mermaid classDiagram, or simple entity bullets. Blueprint will create reviewable nodes."),
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
        appendChat("Blueprint", "Loaded pasted UML into the main editor. Keep refining it, then click Create Code Nodes.")
        status("Loaded UML into editor")
    }

    private fun generateCodeFromUml() {
        val text = umlEditor.text.trim()
        if (text.isBlank() || !text.contains("classDiagram")) {
            Messages.showWarningDialog(
                project,
                "The UML editor needs Mermaid classDiagram text before Blueprint can generate code nodes.",
                "Blueprint - Create Code Nodes"
            )
            status("No usable UML to generate code")
            return
        }
        importUmlText(text, "editable UML")
    }

    private fun runPrimaryProductAction() {
        when {
            selectedNodeCanApply() -> applyChanges(null)
            currentUmlEntityCount() == 0 -> generateProjectUml()
            else -> generateCodeDiffFromCurrentUml()
        }
    }

    private fun selectedNodeCanApply(): Boolean {
        val node = nodeList.selectedValue ?: return false
        if (node.executionStatus == ExecutionStatus.APPLIED) return false
        val exec = registry.getExecution(node.id) ?: return false
        return exec.patches.isNotEmpty() && reviewAllowsApply(registry.getReview(node.id))
    }

    private fun generateCodeDiffFromCurrentUml() {
        val text = umlEditor.text.trim()
        if (text.isBlank() || !text.contains("classDiagram")) {
            val existingNodes = project.service<DependencyGraphService>().readyNodes().ifEmpty { registry.all() }
            if (existingNodes.isNotEmpty()) {
                status("Using existing code nodes for diff")
                logActivity("Generate Code Diff used existing nodes because the UML text was not parseable.")
                generateFirstRealCodeDiff(existingNodes)
                return
            }
            generateProjectUml()
            return
        }
        val parsed = project.service<UmlImportService>().parse(text)
        if (parsed.entities.isEmpty()) {
            val existingNodes = project.service<DependencyGraphService>().readyNodes().ifEmpty { registry.all() }
            if (existingNodes.isNotEmpty()) {
                status("Using existing code nodes for diff")
                logActivity("Generate Code Diff used existing nodes because the UML editor had no parseable entities.")
                generateFirstRealCodeDiff(existingNodes)
                return
            }
            status("No parseable UML entities")
            showArtifactTab("Review")
            reviewSummaryArea.text = "No parseable UML entities. Click Refresh UML From Code, then ask chat for the architecture change again."
            safetyArea.text = "No diff generated."
            return
        }
        importUmlText(text, "current UML")
        val nodes = project.service<DependencyGraphService>().readyNodes().ifEmpty { registry.all() }
        if (nodes.isEmpty()) return status("No code nodes created")
        generateFirstRealCodeDiff(nodes)
    }

    private fun generateFirstRealCodeDiff(
        nodes: List<BlueprintNode>,
        index: Int = 0,
        noChangeTitles: List<String> = emptyList(),
    ) {
        if (index >= nodes.size) {
            val checked = noChangeTitles.size
            reviewSummaryArea.text = if (checked == 0) {
                "No implementation nodes were ready to run."
            } else {
                "No code changes were generated. The current UML appears to match the code for $checked checked node(s)."
            }
            safetyArea.text = "No diff to apply."
            showArtifactTab("Review")
            status("No code changes")
            logActivity("Generate Code Diff found no changed patches across $checked node(s).")
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
        reviewSummaryArea.text = "Generating a code diff from the current UML. Blueprint will plan, write a scoped patch, review it, and open the diff."
        safetyArea.text = "Review gate is on. Apply stays blocked unless review approves the patch."
        n.executionStatus = ExecutionStatus.EXECUTING
        registry.update(n)

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
                return@generatePlanAsync
            }

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
                    logActivity("Skipped no-op code diff for $title")
                    onNoChange?.invoke(title)
                    return@executeNodeAsync
                }

                project.service<ReviewService>().reviewAsync(n, changedExec) { review ->
                    registry.setReview(n.id, review)
                    reviewArea.text = review.rawJson.ifBlank { JsonExtractor.toJson(review) }
                    refreshArtifactSummary()
                    showArtifactTab("Review")
                    DiffPreview.show(project, n, changedExec)
                    logActivity(
                        "Code diff ready for ${n.title.ifBlank { n.id.take(8) }}: " +
                            "${changedExec.patches.size} file(s), review ${review.reviewStatus}."
                    )
                    status("Code diff ready: review ${review.reviewStatus}")
                }
            }
        }
    }

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
            "Imported $sourceLabel into ${result.nodes.size} implementation nodes. Click Generate Code Diff to preview code changes."
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
            if (parseIssues.isNotEmpty()) {
                logActivity("Review ${r.reviewStatus} with warnings for ${n.title}: ${parseIssues.joinToString("; ")}")
            } else {
                logActivity("Review ${r.reviewStatus} for ${n.title}: ${r.issues.size} issue(s), next action ${r.recommendedNextAction}.")
            }
            status("Review ${r.reviewStatus}: ${n.title.ifBlank { n.id.take(8) }}")
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
        n.executionStatus = if (result.applied.size == patchCount && result.skipped.isEmpty()) {
            ExecutionStatus.APPLIED
        } else {
            ExecutionStatus.REVIEW
        }
        if (n.executionStatus == ExecutionStatus.APPLIED) {
            umlHasPendingEdits = false
            val generated = project.service<PythonUmlGenerator>().generate()
            loadGeneratedUml(generated)
            showArtifactTab("UML")
            logActivity(
                "Freshness verified after apply: refreshed UML from disk with " +
                    "${generated.classCount} class(es), ${generated.relationshipCount} relationship(s)."
            )
        }
        registry.update(n)
        refreshArtifactSummary()
        logActivity("Apply finished for ${n.title.ifBlank { n.id.take(8) }}: ${result.applied.size} applied, ${result.skipped.size} skipped.")
        status("Apply finished: ${result.applied.size} applied, ${result.skipped.size} skipped")
        if (result.skipped.isNotEmpty()) {
            Messages.showWarningDialog(
                project,
                "Skipped:\n" + result.skipped.joinToString("\n") { "${it.first} - ${it.second}" },
                "Blueprint - Some changes skipped",
            )
        } else {
            Messages.showInfoMessage(
                project,
                "Applied ${result.applied.size} file change(s).\n\n${result.applied.joinToString("\n")}",
                "Blueprint - Apply Complete"
            )
        }
    }

    private fun reviewAllowsApply(review: ReviewArtifact?): Boolean =
        review?.reviewStatus == "APPROVE" && review.recommendedNextAction == "apply"

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
        if (!sourceFile.isFile) {
            status("Could not find source: $sourcePath")
            Messages.showWarningDialog(
                project,
                "Could not find source file:\n$sourcePath",
                "Blueprint - Source Not Found",
            )
            return
        }
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceFile)
        if (virtualFile == null) {
            status("Could not open source: ${sourceFile.path}")
            Messages.showWarningDialog(
                project,
                "Could not open source file:\n${sourceFile.path}",
                "Blueprint - Source Not Found",
            )
            return
        }
        val zeroBasedLine = (node.sourceLine ?: 1).coerceAtLeast(1) - 1
        OpenFileDescriptor(project, virtualFile, zeroBasedLine, 0).navigate(true)
        selectedCanvasId = node.id
        status("Opened source: ${node.sourceDescriptionForStatus()}")
        logActivity("Opened source for ${node.title}: ${node.sourceDescriptionForStatus()}")
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
        updateMiniGraph(project.service<DependencyGraphService>().analyze())
        updateGuide()
    }

    private fun umlDocumentChanged() {
        if (!suppressUmlDocumentEvents) {
            umlHasPendingEdits = true
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
            updateOverviewSummary()
            selectedLabel.text = "Selected: none"
            artifactLabel.text = "Artifacts: not planned"
            reviewSummaryArea.text = "No node selected."
            safetyArea.text = "Select or seed a node to begin."
            dependencyBlockArea.text = "No dependency status yet."
            graphArea.text = "No graph yet. Seed a sample or create nodes."
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
        val graph = project.service<DependencyGraphService>()
        val report = graph.analyze()
        val readiness = graph.readinessFor(n)
        updateOverviewSummary(report)
        val canApply = !exec?.patches.isNullOrEmpty() && reviewAllowsApply(review)
        applyApprovedButton.isEnabled = canApply
        applyApprovedButton.text = if (canApply) "Apply Approved Changes" else "Apply Blocked By Review"
        artifactLabel.text = "Artifacts: plan=${plan?.status ?: "not planned"} | exec=${exec?.status ?: "not executed"} | review=${review?.reviewStatus ?: "not reviewed"} | node=${badgeFor(n)} | ready=${readiness.ready}"
        reviewSummaryArea.text = buildString {
            append(review?.summary ?: "No review yet. Run Review before applying for the safest demo flow.")
            append("\n\n")
            append(changedFileSummary(exec))
        }

        val issues = buildList {
            if (plan != null) addAll(JsonExtractor.planIssues(plan))
            if (exec != null) addAll(JsonExtractor.executionIssues(exec))
            if (review != null) addAll(JsonExtractor.reviewIssues(review))
        }
        val scopeDrops = exec?.validation?.risks.orEmpty().filter { it.contains("out-of-scope", ignoreCase = true) }
        safetyArea.text = when {
            issues.isNotEmpty() || scopeDrops.isNotEmpty() ->
                (issues + scopeDrops).distinct().joinToString("\n") { "- $it" }
            exec?.status == "PARTIAL" -> "Execution is PARTIAL. Inspect the diff and validation notes before applying."
            exec?.status == "BLOCKED" -> "Execution is BLOCKED. Do not apply until the node is revised."
            review?.reviewStatus == "APPROVE" -> "Review approved. Scope compliance: ${review.scopeCompliance.result}."
            review != null -> reviewBlockMessage(review)
            else -> "No safety issues reported yet."
        }
        dependencyBlockArea.text = if (readiness.reasons.isEmpty()) {
            "Dependency status: ready\nThis node can run now."
        } else {
            "Dependency blockers:\n" +
            readiness.reasons.joinToString("\n") { "- $it" }
        }
        safetyArea.foreground = if (safetyArea.text.startsWith("-") || safetyArea.text.contains("BLOCKED") || safetyArea.text.contains("PARTIAL")) {
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
            modeBannerLabel.text = "Viewing: UML draft \u2014 pending edits"
            return
        }
        val codeViews = codeMapViews(selectedId, report)
        if (codeViews.isNotEmpty()) {
            miniGraph.setGraph(codeViews)
            modeBannerLabel.text = "Viewing: current code map"
            return
        }
        modeBannerLabel.text = "Viewing: workflow nodes"
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
            val fieldLines = entity.fields.filterNot { it.contains("(") && it.contains(")") }
            val methodLines = entity.fields.filter { it.contains("(") && it.contains(")") }
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
                    if (entity.fields.isNotEmpty()) {
                        append("\n")
                        append(entity.fields.take(8).joinToString("\n") { "- $it" })
                    }
                },
                origin = MiniGraphPanel.NodeOrigin.PROPOSED_UML,
                kind = "UML entity",
                preview = entity.fields.take(3).joinToString(", ").ifBlank { "no fields yet" },
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
        refreshFirstRunScenario()
        when {
            selectedNodeCanApply() -> {
                primaryActionButton.text = "Apply Approved Changes"
                guideLabel.text = "Review approved the generated diff. Apply it to disk, then refresh UML from code."
            }
            currentUmlEntityCount() == 0 -> {
            primaryActionButton.text = "Refresh UML From Code"
            guideLabel.text = "Start by reading the current project into an editable UML diagram."
            }
            else -> {
            primaryActionButton.text = "Generate Code Diff"
            guideLabel.text = "Change the UML with chat or direct edits, then generate a reviewed code diff."
            }
        }
    }

    private fun shouldShowInviteFirstRunScenario(): Boolean =
        GuidedInviteScenario.matchesProject(project.name, project.basePath)

    private fun refreshFirstRunScenario() {
        if (!shouldShowInviteFirstRunScenario()) return
        val state = currentInviteFirstRunScenarioState()
        firstRunScenarioArea.text = GuidedInviteScenario.checklistText(state)
        firstRunPromptButton.isEnabled = state.codeMapReady && !state.umlDraftReady
    }

    private fun currentInviteFirstRunScenarioState(): GuidedInviteScenarioState {
        val ir = project.service<IRStore>().load()
        val componentNames = ir?.components?.map { it.name }?.toSet().orEmpty()
        val parsedUml = runCatching { project.service<UmlImportService>().parse(umlEditor.text) }.getOrNull()
        val entityNames = parsedUml?.entities?.map { it.name }?.toSet().orEmpty()
        val hasInvitePolicyLink = parsedUml?.relationships.orEmpty().any { relationship ->
            setOf(relationship.from, relationship.to) == setOf("Invite", "InvitePolicy")
        }
        val reviewedInviteDiff = registry.all().any { node ->
            registry.getReview(node.id) != null &&
                registry.getExecution(node.id)?.patches.orEmpty().any { it.path == GuidedInviteScenario.PATCH_PATH }
        }
        val appliedInviteDiff = registry.all().any { node ->
            node.executionStatus == ExecutionStatus.APPLIED &&
                registry.getExecution(node.id)?.patches.orEmpty().any { it.path == GuidedInviteScenario.PATCH_PATH }
        }
        return GuidedInviteScenarioState(
            codeMapReady = setOf("Project", "User", "Invite").all { it in componentNames },
            umlDraftReady = umlHasPendingEdits && "InvitePolicy" in entityNames && hasInvitePolicyLink,
            reviewedDiffReady = reviewedInviteDiff,
            appliedReady = appliedInviteDiff,
            refreshedCodeMapReady = appliedInviteDiff && !umlHasPendingEdits && "InvitePolicy" in componentNames,
        )
    }

    private fun guidedNextState(): Pair<String, String> {
        val entityCount = currentUmlEntityCount()
        if (entityCount == 0) {
            return "Next: Abstract Code to UML" to "Read the current Python project and draw the first UML diagram."
        }

        val allNodes = registry.all()
        if (allNodes.isEmpty()) {
            return "Next: Create Code Nodes" to "Turn the edited UML into reviewable implementation nodes."
        }

        val graph = project.service<DependencyGraphService>()
        val selected = nodeList.selectedValue ?: graph.readyNodes().firstOrNull() ?: allNodes.first()
        val shortTitle = selected.title.ifBlank { selected.id.take(8) }
        return when {
            registry.getPlan(selected.id) == null ->
                "Next: Generate Plan" to "Plan the selected node: $shortTitle."
            registry.getExecution(selected.id) == null ->
                "Next: Execute Node" to "Generate scoped patches for: $shortTitle."
            registry.getReview(selected.id) == null ->
                "Next: Review Changes" to "Check generated patches before applying."
            selected.executionStatus != ExecutionStatus.APPLIED ->
                "Next: Apply Approved Changes" to "Apply reviewed changes for: $shortTitle."
            graph.readyNodes().any { it.id != selected.id } ->
                "Next: Select Ready Node" to "Move to the next dependency-ready node."
            else ->
                "Next: Refresh UML From Code" to "All current work is applied. Re-abstract the updated codebase."
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

    private fun changedFileSummary(exec: com.blueprint.model.ExecutionArtifact?): String {
        if (exec == null) return "Changed files: none yet."
        if (exec.patches.isEmpty()) return "Changed files: none."
        return "Changed files (${exec.patches.size}):\n" +
            exec.patches.joinToString("\n") { "- ${it.action} ${it.path}" }
    }

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

    private fun logActivity(msg: String) {
        val at = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        activityLog.append("[$at] $msg\n")
        activityLog.caretPosition = activityLog.document.length
    }
}
