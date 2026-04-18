# Blueprint

Blueprint is a PyCharm/JetBrains plugin for architecture-first Python development.

The core loop is:

1. Open an existing Python project.
2. Abstract the codebase up into editable Mermaid UML.
3. Edit the UML directly or use the side chat to refine the architecture.
4. When the UML is ready, generate scoped code nodes from it.
5. Plan, execute, review, preview diff, and apply approved changes.
6. Repeat the loop whenever needed: codebase -> UML -> chat refinement -> code.

## Current Scope

Blueprint is intentionally focused on a hackathon-ready vertical slice:

- Python project context detection.
- Editable Mermaid UML generation from Python classes.
- OpenAI-assisted UML refinement in the side chat.
- UML conversion into dependency-aware code-generation nodes.
- Offline mock mode for deterministic demos.
- Plan, execute, review, diff preview, and scoped apply.
- List-based node workflow with a read-only mini graph.

It does not yet include drag-and-drop graph editing or true parallel execution.

## Run Locally

Use JDK 17:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew runIde
```

For the easiest demo test, use the helper script:

```bash
./scripts/run-blueprint-pycharm.sh
```

That launches a PyCharm sandbox with Blueprint installed and opens:

```text
examples/car_company_project
```

Once PyCharm opens, use `View -> Tool Windows -> Blueprint` if the Blueprint tab is not already visible.

Build:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew build -x test --no-daemon
```

## Teammate / Agent Notes

The fastest reliable way to test the plugin is:

```bash
cd /Users/noahsyrdal/blueprint
./scripts/run-blueprint-pycharm.sh
```

This does three important things:

1. Uses JDK 17.
2. Runs the plugin through Gradle `runIde`.
3. Opens the bundled demo Python project at `examples/car_company_project`.

Do not test Blueprint from PyCharm's normal right-click `Diagrams` menu. That is PyCharm's built-in diagram feature, not this plugin.

Blueprint is a ToolWindow inside the launched sandbox IDE. If the side tab is not visible, open it with:

```text
View -> Tool Windows -> Blueprint
```

The expected sandbox flow is:

1. Open the `Blueprint` ToolWindow.
2. Enable `Offline mock demo`.
3. Click `Abstract Code to UML`.
4. Review or edit the Mermaid UML in the main editor.
5. Ask chat to refine it, for example: `add a Supplier entity`.
6. Click `Create Code Nodes`.
7. Select the first generated schema node.
8. Run `Generate Plan -> Execute Node -> Review -> Preview Diff -> Apply All`.

Useful diagnostics:

- If the sandbox starts but Blueprint is missing, check `build/idea-sandbox/system/log/idea.log` for `Loaded custom plugins: Blueprint`.
- If Gradle fails with Java/class-version errors, make sure JDK 17 is selected:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

- If `Abstract Code to UML` finds no classes, confirm the opened project is `examples/car_company_project` or another Python project with class definitions.
- Live chat uses the configured provider through `CodexClient`; default live provider is OpenAI and requires `OPENAI_API_KEY`.
- Shutdown warnings from forcibly closing `runIde` are usually PyCharm sandbox noise, not Blueprint plugin failures.

## Demo Tip

In the Blueprint ToolWindow, enable `Offline mock demo`, then click `Abstract Code to UML`.

Recommended smoke test:

1. Run `./scripts/run-blueprint-pycharm.sh`.
2. In the sandbox PyCharm window, open the `Blueprint` ToolWindow.
3. Enable `Offline mock demo`.
4. Click `Abstract Code to UML`.
5. Ask chat: `add a Supplier entity`.
6. Click `Create Code Nodes`.
7. Select `01 Imported UML schema contract`.
8. Click `Generate Plan`.
9. Click `Execute Node`.
10. Click `Review`.
11. Click `Preview Diff`.
12. Click `Apply All`.
