package com.blueprint.ui

import com.blueprint.model.AcceptanceCriterion
import com.blueprint.model.AcceptanceCriterionType
import com.blueprint.model.BlueprintNode
import com.blueprint.model.ExecutionStatus
import com.blueprint.model.FileScope
import com.blueprint.model.NodeType
import com.blueprint.service.ApplyChangesService
import com.blueprint.service.CodexClient
import com.blueprint.service.DependencyGraphService
import com.blueprint.service.JsonExtractor
import com.blueprint.service.NodeExecutionService
import com.blueprint.service.NodePlanningService
import com.blueprint.service.NodeRegistry
import com.blueprint.service.PythonProjectAnalyzer
import com.blueprint.service.PythonUmlGenerator
import com.blueprint.service.ReviewService
import com.blueprint.service.UmlImportService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
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
        fixedCellHeight = 30
        setCellRenderer { list, value, _, isSelected, _ ->
            JLabel("${statusChip(badgeFor(value))}  ${value.type.name.lowercase()}  ${value.title.ifBlank { "(untitled)" }}").apply {
                isOpaque = true
                border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
                background = if (isSelected) list.selectionBackground else statusTint(badgeFor(value))
                foreground = if (isSelected) list.selectionForeground else list.foreground
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
                background = if (isSelected) list.selectionBackground else list.background
                foreground = if (isSelected) list.selectionForeground else list.foreground
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
    private val filterCombo = JComboBox(NodeFilter.values())
    private val activityLog = JBTextArea(6, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val chatHistory = JBTextArea(12, 28).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        text = """
            Blueprint chat

            I help refine the UML before code generation.

            Try:
            - explain this UML
            - add an InvitePolicy entity
            - what should I generate next?

            Live mode uses OpenAI through OPENAI_API_KEY. Mock mode is deterministic for demos.
        """.trimIndent() + "\n"
    }
    private val chatInput = JBTextArea(3, 28).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val umlStatusLabel = JLabel("UML: not generated yet")
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
    private val mockMode = JBCheckBox("Offline mock demo").apply {
        isSelected = codex.providerMode() == "mock"
        addActionListener {
            codex.setProviderOverride(if (isSelected) "mock" else null)
            providerLabel.text = providerText()
            logActivity("Mode changed to ${providerText()}")
        }
    }
    private var selectedCanvasId: String? = null

    init {
        umlEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = refreshCanvasFromUml()
            override fun removeUpdate(e: DocumentEvent) = refreshCanvasFromUml()
            override fun changedUpdate(e: DocumentEvent) = refreshCanvasFromUml()
        })
        buildUi()
        registry.addListener(object : NodeRegistry.Listener {
            override fun changed() = SwingUtilities.invokeLater { refreshList() }
        })
        refreshList()
        logActivity("Blueprint ready. Seed UML Invite Flow, keep mock mode on, then run the first ready node.")
    }

    private fun buildUi() {
        miniGraph.onNodeSelected = { nodeId ->
            selectNodeFromGraph(nodeId)
        }
        filterCombo.addActionListener {
            refreshList()
            logActivity("Node filter: ${filterCombo.selectedItem}")
        }
        val overview = JPanel(GridLayout(0, 1, 4, 4)).apply {
            border = BorderFactory.createTitledBorder("Overview")
            add(JLabel("Project: ${project.name}").apply { foreground = Color(0x333333) })
            add(summaryLabel.apply { foreground = Color(0x333333) })
            add(providerLabel.apply { foreground = providerColor() })
            add(JLabel("Next: Abstract Code to UML").apply {
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
            add(JButton("Sample: Invite UML").apply { addActionListener { seedUmlInviteFlow() } })
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

        val secondaryTabs = JTabbedPane().apply {
            addTab("UML Source", JBScrollPane(umlEditor))
            addTab("Node Details", JBScrollPane(form))
            addTab("Review / Safety", summary)
            addTab("Plan JSON", JBScrollPane(planArea))
            addTab("Execution JSON", JBScrollPane(execArea))
            addTab("Review JSON", JBScrollPane(reviewArea))
            addTab("Activity", JBScrollPane(activityLog))
        }

        val lowerWorkspace = JPanel(BorderLayout()).apply {
            minimumSize = Dimension(0, 260)
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
                    add(JLabel("Codebase -> UML -> chat refinement -> code nodes -> apply -> UML again").apply {
                        foreground = Color(0x333333)
                    })
                    add(umlStatusLabel.apply { foreground = Color(0x555555) })
                }, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                    add(JButton("Abstract Code to UML").apply { addActionListener { generateProjectUml() } })
                    add(JButton("Create Code Nodes").apply { addActionListener { generateCodeFromUml() } })
                }, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(JBScrollPane(miniGraph), BorderLayout.CENTER)
        }

        val mainCanvas = JSplitPane(JSplitPane.VERTICAL_SPLIT, diagramPanel, lowerWorkspace).apply {
            dividerLocation = 520
            resizeWeight = 0.76
            isContinuousLayout = true
        }

        val workspace = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainCanvas, chatPanel()).apply {
            dividerLocation = 840
            resizeWeight = 1.0
            isContinuousLayout = true
        }

        val right = JPanel(BorderLayout()).apply {
            add(workspace, BorderLayout.CENTER)
        }

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right).apply {
            dividerLocation = 300
            resizeWeight = 0.0
            isContinuousLayout = true
        }
        add(headerPanel(), BorderLayout.NORTH)
        add(split, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        nodeList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                if (refreshingList) return@addListSelectionListener
                registry.setSelectedNode(nodeList.selectedValue?.id)
                loadSelectedIntoForm()
            }
        }
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

    private fun headerPanel(): JPanel =
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
            add(JPanel(GridLayout(0, 1)).apply {
                add(JLabel("Blueprint").apply {
                    font = font.deriveFont(java.awt.Font.BOLD, 18f)
                })
                add(JLabel("Abstract code to UML, refine with chat, generate code when ready. Repeat anytime.").apply {
                    foreground = Color(0x666666)
                })
            }, BorderLayout.CENTER)
            add(JLabel("Main canvas: editable UML. Sidecar: OpenAI-assisted architecture chat.").apply {
                foreground = Color(0x555555)
            }, BorderLayout.EAST)
        }

    private fun chatPanel(): JPanel =
        JPanel(BorderLayout(6, 6)).apply {
            preferredSize = Dimension(320, 0)
            minimumSize = Dimension(260, 0)
            border = BorderFactory.createTitledBorder("Blueprint Chat")
            add(JBScrollPane(chatHistory), BorderLayout.CENTER)
            add(JPanel(BorderLayout(4, 4)).apply {
                add(JPanel(GridLayout(0, 1, 4, 4)).apply {
                    border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
                    add(JButton("Explain UML").apply {
                        addActionListener { sendSuggestedChat("explain this UML") }
                    })
                    add(JButton("Add InvitePolicy").apply {
                        addActionListener { sendSuggestedChat("add an InvitePolicy entity") }
                    })
                    add(JButton("What next?").apply {
                        addActionListener { sendSuggestedChat("what should I generate next?") }
                    })
                }, BorderLayout.NORTH)
                add(JBScrollPane(chatInput), BorderLayout.CENTER)
                add(JButton("Send").apply { addActionListener { sendChat() } }, BorderLayout.EAST)
            }, BorderLayout.SOUTH)
        }

    private fun actionPanel(): JPanel =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                add(mockMode)
                add(JLabel(providerText()).apply { foreground = providerColor() })
                add(JLabel("  Loop: Code -> UML -> Chat -> Code Nodes -> Apply -> UML again").apply {
                    foreground = Color(0x555555)
                })
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                add(JButton("Save").apply { addActionListener { saveCurrent() } })
                add(JButton("Generate Plan").apply { addActionListener { generatePlan() } })
                add(JButton("Execute Node").apply { addActionListener { executeNode() } })
                add(JButton("Run Ready").apply { addActionListener { runAllReadyNodes() } })
                add(JButton("Review").apply { addActionListener { review() } })
                add(JButton("Preview Waves").apply { addActionListener { previewWaves() } })
                add(JButton("Python Context").apply { addActionListener { showPythonContext() } })
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                add(JButton("Selected + Dependents").apply { addActionListener { previewSelectedAndDependents() } })
                add(JButton("Why Blocked?").apply { addActionListener { showWhyBlocked() } })
                add(JButton("Preview Diff").apply { addActionListener { previewDiff() } })
                add(JButton("Apply All").apply { addActionListener { applyChanges(null) } })
                add(JButton("Apply File").apply { addActionListener { applySelectedFile() } })
            })
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

    private fun sendChat() {
        val message = chatInput.text.trim()
        if (message.isBlank()) return
        chatInput.text = ""
        appendChat("You", message)
        if (shouldRefineUml(message)) {
            refineUmlWithChat(message)
        } else {
            appendChat("Blueprint", chatResponse(message))
        }
    }

    private fun sendSuggestedChat(message: String) {
        chatInput.text = message
        sendChat()
    }

    private fun appendChat(author: String, message: String) {
        chatHistory.append("\n$author: $message\n")
        chatHistory.caretPosition = chatHistory.document.length
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
                "The main canvas is editable Mermaid UML. Ask for architecture changes like 'add an InvitePolicy entity' or 'make Project own many Invites'. I will rewrite the UML, then you can Create Code Nodes."
            }
            selected != null -> {
                val readiness = graph.readinessFor(selected)
                "${selected.title.ifBlank { selected.id.take(8) }} is selected. Status: ${badgeFor(selected)}. " +
                    if (readiness.ready) "It is ready to run." else "It is blocked; ask 'why blocked' for details."
            }
            else -> "Start with Abstract Code to UML. Refine the editable diagram here with chat, then click Create Code Nodes when the architecture is ready."
        }
    }

    private fun shouldRefineUml(message: String): Boolean {
        val lower = message.lowercase()
        return listOf("add", "remove", "change", "rename", "refactor", "relationship", "entity", "class", "field", "uml", "diagram")
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
                umlEditor.text = nextUml
                umlEditor.caretPosition = 0
                umlStatusLabel.text = "UML: refined by chat. Create Code Nodes when ready, or keep editing."
                appendChat("Blueprint", "Updated the UML. Review it in the main canvas, then keep refining or click Create Code Nodes.")
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
            "policy" in lower -> """

                class InvitePolicy {
                  maxAgeDays: int
                  requiresDomainMatch: bool
                }

                Invite --> InvitePolicy : uses
            """.trimIndent()
            "audit" in lower || "event" in lower -> """

                class AuditEvent {
                  id: str
                  actorEmail: str
                  action: str
                  createdAt: datetime
                }

                Project --> AuditEvent : records
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
        status("Generating UML from Python project...")
        val generated = project.service<PythonUmlGenerator>().generate()
        umlEditor.text = generated.text
        umlEditor.caretPosition = 0
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
        umlEditor.text = text
        umlEditor.caretPosition = 0
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

        result.nodes.forEach(registry::add)
        selectNode(result.nodes.first().id)
        registry.setSelectedNode(result.nodes.first().id)
        graphArea.text = result.summary
        appendChat(
            "Blueprint",
            "Imported $sourceLabel into ${result.nodes.size} nodes. The diagram is now the main control surface; select the first schema card and run Generate Plan."
        )
        logActivity(
            "Imported $sourceLabel: ${result.parsed.entities.size} entit${if (result.parsed.entities.size == 1) "y" else "ies"}, " +
                "${result.parsed.relationships.size} relationship(s), ${result.nodes.size} node(s)."
        )
        status("Imported $sourceLabel: ${result.nodes.size} nodes")
    }

    private fun umlImportExample(): String =
        """
        classDiagram
        class Project {
          id: string
          name: string
        }

        class User {
          id: string
          email: string
        }

        class Invite {
          id: string
          projectId: string
          email: string
          token: string
          status: pending | accepted | expired
          createdAt: datetime
          expiresAt: datetime
        }

        Project "1" --> "many" Invite
        User may accept Invite
        Invite belongs to Project
        """.trimIndent()

    private fun seedSampleNode() {
        val n = inviteBackendNode()
        registry.add(n)
        selectNode(n.id)
        logActivity("Seeded one safe demo node scoped to blueprint_demo/project_invite/service.py")
    }

    private fun seedUmlInviteFlow() {
        val schema = BlueprintNode(
            type = NodeType.SCHEMA,
            title = "01 UML invite schema contract",
            summary = "Turn the UML-like invite architecture into the upstream data contract.",
            description = """
                Define the invite domain model from this UML-like architecture:

                Project
                - id
                - name

                User
                - id
                - email

                Invite
                - id
                - projectId
                - email
                - token
                - status: pending | accepted | expired
                - createdAt
                - expiresAt

                Relationships:
                Project 1 -> many Invite
                User may accept Invite
                Invite belongs to Project

                The schema node is the architecture contract. Downstream Python
                service, CLI, test, and docs nodes must respect this contract.
            """.trimIndent(),
            fileScope = FileScope(paths = listOf("blueprint_demo/project_invite/models.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "Invite model includes projectId, email, token, status, createdAt, and expiresAt."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "Invite status is limited to pending, accepted, or expired."),
                criterion("AC3", AcceptanceCriterionType.CODEGEN, "Generated changes stay inside the schema node file scope.")
            )
        )
        val backend = BlueprintNode(
            type = NodeType.BACKEND,
            title = "02 Invite service from schema",
            summary = "Create, list, and redeem invites using the UML schema contract.",
            description = "Implement the Python service for project invites. The service must use the Invite fields and status values defined by the upstream UML schema node.",
            dependencies = listOf(schema.id),
            fileScope = FileScope(paths = listOf("blueprint_demo/project_invite/service.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "Python service can create a pending invite using the schema contract."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "Python service can list pending invites for a project."),
                criterion("AC3", AcceptanceCriterionType.INTERFACE_CONTRACT, "Python service can redeem valid invite tokens and update status."),
                criterion("AC4", AcceptanceCriterionType.CODEGEN, "Implementation stays inside the declared service file scope.")
            )
        )
        val frontend = BlueprintNode(
            type = NodeType.FRONTEND,
            title = "03 Invite management CLI",
            summary = "Create a Python CLI for managing project invites.",
            description = "Add a compact Python CLI that uses the invite service and reflects pending, accepted, and expired invite statuses.",
            dependencies = listOf(backend.id),
            fileScope = FileScope(paths = listOf("blueprint_demo/project_invite/cli.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.UX, "User can enter an email and create an invite from the CLI."),
                criterion("AC2", AcceptanceCriterionType.UX, "CLI shows invite status values from the schema contract."),
                criterion("AC3", AcceptanceCriterionType.INTERFACE_CONTRACT, "CLI calls the invite service produced by the service node.")
            )
        )
        val test = BlueprintNode(
            type = NodeType.TEST,
            title = "04 Invite contract tests",
            summary = "Verify the UML schema, service, and CLI contract path.",
            description = "Add tests for invite creation, listing, redemption, and schema-defined status handling.",
            dependencies = listOf(backend.id, frontend.id),
            fileScope = FileScope(paths = listOf("tests/test_project_invites.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.TEST, "Tests cover create, list, redeem, and invalid token cases."),
                criterion("AC2", AcceptanceCriterionType.TEST, "Tests assert pending, accepted, and expired status behavior.")
            )
        )
        val docs = BlueprintNode(
            type = NodeType.DOCS,
            title = "05 Invite architecture notes",
            summary = "Document the UML-driven invite flow.",
            description = "Document how the UML schema contract maps to Python model, service, CLI, and tests.",
            dependencies = listOf(backend.id, frontend.id),
            fileScope = FileScope(paths = listOf("docs/project_invites.md")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.OTHER, "Docs explain the schema fields, relationships, Python service behavior, CLI behavior, and validation path.")
            )
        )
        listOf(schema, backend, frontend, test, docs).forEach(registry::add)
        selectNode(schema.id)
        logActivity("Seeded UML Invite Flow: schema contract first, downstream nodes dependency-blocked until the contract is applied.")
    }

    private fun seedInviteFlow() {
        val schema = BlueprintNode(
            type = NodeType.SCHEMA,
            title = "01 Invite data model",
            summary = "Define the invite record used by the Python service and tests.",
            description = "Create a minimal invite data shape for project invite creation, listing, and redemption.",
            fileScope = FileScope(paths = listOf("blueprint_demo/project_invite/models.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "Invite model includes projectId, email, token, status, createdAt, and expiresAt.")
            )
        )
        val backend = inviteBackendNode(dependencies = listOf(schema.id))
        val frontend = BlueprintNode(
            type = NodeType.FRONTEND,
            title = "03 Invite management CLI",
            summary = "Add a clear Python CLI for managing project invites.",
            description = "Create a small CLI for listing pending invites and creating a new project invite.",
            dependencies = listOf(backend.id),
            fileScope = FileScope(paths = listOf("blueprint_demo/project_invite/cli.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.UX, "User can enter an email and create an invite from the CLI."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "CLI uses the invite service contract produced by the service node.")
            )
        )
        val test = BlueprintNode(
            type = NodeType.TEST,
            title = "04 Invite flow tests",
            summary = "Add coverage for project invite creation and listing.",
            description = "Test happy path and duplicate/invalid invite cases.",
            dependencies = listOf(backend.id, frontend.id),
            fileScope = FileScope(paths = listOf("tests/test_project_invites.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.TEST, "Tests cover create, list, and redeem invite behavior.")
            )
        )
        val docs = BlueprintNode(
            type = NodeType.DOCS,
            title = "05 Invite flow docs",
            summary = "Document the project invite flow.",
            description = "Add concise developer-facing notes for invite data, Python service, CLI, and validation.",
            dependencies = listOf(backend.id, frontend.id),
            fileScope = FileScope(paths = listOf("docs/project_invites.md")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.OTHER, "Docs explain model fields, service behavior, CLI entry point, and validation behavior.")
            )
        )
        listOf(schema, backend, frontend, test, docs).forEach(registry::add)
        selectNode(schema.id)
        logActivity("Seeded Project Invite Flow: 5 ordered Python nodes, safe blueprint_demo file scopes, dependencies wired.")
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

    private fun inviteBackendNode(dependencies: List<String> = emptyList()): BlueprintNode =
        BlueprintNode(
            type = NodeType.BACKEND,
            title = "02 Project invite service",
            summary = "Create, list, and redeem project invites.",
            description = "Implement the Python service surface for creating project invites, listing pending invites, and redeeming invite tokens.",
            dependencies = dependencies,
            fileScope = FileScope(paths = listOf("blueprint_demo/project_invite/service.py")),
            acceptanceCriteria = listOf(
                criterion("AC1", AcceptanceCriterionType.INTERFACE_CONTRACT, "Service creates a pending invite for a project."),
                criterion("AC2", AcceptanceCriterionType.INTERFACE_CONTRACT, "Service lists pending invites for the current project."),
                criterion("AC3", AcceptanceCriterionType.INTERFACE_CONTRACT, "Service marks a valid token as redeemed."),
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
        planArea.text = "Generating plan...\n\nBlueprint is rendering plan_generation_prompt with this node, file scope, dependencies, and acceptance criteria."
        n.executionStatus = ExecutionStatus.EXECUTING
        registry.update(n)
        project.service<NodePlanningService>().generatePlanAsync(n) { plan ->
            registry.setPlan(n.id, plan)
            planArea.text = plan.rawJson.ifBlank { JsonExtractor.toJson(plan) }
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
        execArea.text = "Executing node...\n\nBlueprint is rendering per_node_execution_prompt and will return structured patches for review."
        n.executionStatus = ExecutionStatus.EXECUTING
        registry.update(n)
        project.service<NodeExecutionService>().executeNodeAsync(n, plan) { exec ->
            registry.setExecution(n.id, exec)
            execArea.text = exec.rawJson.ifBlank { JsonExtractor.toJson(exec) }
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
        reviewArea.text = "Reviewing generated patches...\n\nBlueprint is checking scope, acceptance criteria, and safety before apply."
        project.service<ReviewService>().reviewAsync(n, exec) { r ->
            registry.setReview(n.id, r)
            reviewArea.text = r.rawJson.ifBlank { JsonExtractor.toJson(r) }
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
            status("Selected UML entity: $id")
            appendChat("Blueprint", "$id selected on the UML canvas. Ask me to refine it, or edit the UML source directly.")
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
        appendChat("Blueprint", "${node.title.ifBlank { node.id.take(8) }} selected from the diagram. Ask 'what next?' or 'why blocked?'.")
        logActivity("Graph selected ${node.title.ifBlank { node.id.take(8) }} (${node.id.take(8)}).")
    }

    private fun refreshCanvasFromUml() {
        SwingUtilities.invokeLater {
            updateMiniGraph(project.service<DependencyGraphService>().analyze())
        }
    }

    private fun loadSelectedIntoForm() {
        val n = nodeList.selectedValue ?: run {
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
            else -> "No safety issues reported yet."
        }
        dependencyBlockArea.text = if (readiness.reasons.isEmpty()) {
            "Dependency status: ready\nThis node can run now."
        } else {
            "Dependency blockers:\n" +
            readiness.reasons.joinToString("\n") { "- $it" }
        }
        safetyArea.foreground = if (safetyArea.text.startsWith("-") || safetyArea.text.contains("BLOCKED") || safetyArea.text.contains("PARTIAL")) {
            Color(160, 70, 20)
        } else {
            Color(60, 110, 70)
        }
        dependencyBlockArea.foreground = if (readiness.reasons.isEmpty()) Color(60, 110, 70) else Color(160, 70, 20)
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
    }

    private fun updateMiniGraph(report: DependencyGraphService.GraphReport) {
        val selectedId = nodeList.selectedValue?.id
        val umlViews = umlCanvasViews(selectedId)
        if (umlViews.isNotEmpty()) {
            miniGraph.setGraph(umlViews)
            return
        }
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
            )
        }
        miniGraph.setGraph(views)
    }

    private fun umlCanvasViews(selectedNodeId: String?): List<MiniGraphPanel.NodeView> {
        val parsed = runCatching { project.service<UmlImportService>().parse(umlEditor.text) }.getOrNull()
            ?: return emptyList()
        if (parsed.entities.isEmpty()) return emptyList()
        val entityNames = parsed.entities.map { it.name }.toSet()
        val dependenciesByEntity = parsed.relationships
            .filter { it.from in entityNames && it.to in entityNames }
            .groupBy({ it.to }, { it.from })
        return parsed.entities.mapIndexed { index, entity ->
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
                    append("Editable UML entity")
                    if (entity.fields.isNotEmpty()) {
                        append("\n")
                        append(entity.fields.take(8).joinToString("\n") { "- $it" })
                    }
                },
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
        providerLabel.text = providerText()
        providerLabel.foreground = providerColor()
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
            "APPLIED" -> Color(0xE7F6ED)
            "REVIEWED" -> Color(0xEAF1FF)
            "EXECUTED" -> Color(0xF1F5FF)
            "PLANNED" -> Color(0xFFF8E6)
            "BLOCKED" -> Color(0xFFE8E0)
            "PARTIAL" -> Color(0xFFF0D6)
            else -> Color(0xF7F7F7)
        }

    private fun statusChip(status: String): String =
        "[$status]"

    private fun providerText(): String =
        if (codex.providerMode() == "mock") {
            "Mode: MOCK DEMO (offline, deterministic)"
        } else {
            "Mode: LIVE (${codex.providerMode()})"
        }

    private fun providerColor(): Color =
        if (codex.providerMode() == "mock") Color(0x2E7D32) else Color(0x5F6368)

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
