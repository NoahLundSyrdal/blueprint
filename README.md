# Blueprint

Blueprint is a PyCharm/JetBrains plugin for architecture-first Python development.

The demo flow is:

1. Open an existing Python project.
2. Generate editable UML from the project.
3. Edit or import UML as an architecture contract.
4. Convert the UML into Blueprint nodes.
5. Generate a plan.
6. Execute the selected node.
7. Review the generated diff.
8. Apply approved, in-scope changes.

## Current Scope

Blueprint is intentionally focused on a hackathon-ready vertical slice:

- Python project context detection.
- Editable Mermaid UML generation from Python classes.
- UML import into dependency-aware nodes.
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
examples/invite_project
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
3. Opens the bundled demo Python project at `examples/invite_project`.

Do not test Blueprint from PyCharm's normal right-click `Diagrams` menu. That is PyCharm's built-in diagram feature, not this plugin.

Blueprint is a ToolWindow inside the launched sandbox IDE. If the side tab is not visible, open it with:

```text
View -> Tool Windows -> Blueprint
```

The expected sandbox flow is:

1. Open the `Blueprint` ToolWindow.
2. Enable `Offline mock demo`.
3. Click `Generate UML`.
4. Review the generated Mermaid class diagram.
5. Click OK to import it into Blueprint nodes.
6. Select the first schema node.
7. Run `Generate Plan -> Execute Node -> Review -> Preview Diff -> Apply All`.

Useful diagnostics:

- If the sandbox starts but Blueprint is missing, check `build/idea-sandbox/system/log/idea.log` for `Loaded custom plugins: Blueprint`.
- If Gradle fails with Java/class-version errors, make sure JDK 17 is selected:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

- If `Generate UML` finds no classes, confirm the opened project is `examples/invite_project` or another Python project with class definitions.
- Shutdown warnings from forcibly closing `runIde` are usually PyCharm sandbox noise, not Blueprint plugin failures.

## Demo Tip

In the Blueprint ToolWindow, enable `Offline mock demo`, then click `Generate UML` or `Seed: UML Invite Flow`.

Recommended smoke test:

1. Run `./scripts/run-blueprint-pycharm.sh`.
2. In the sandbox PyCharm window, open the `Blueprint` ToolWindow.
3. Enable `Offline mock demo`.
4. Click `Generate UML`.
5. Click OK to import the generated diagram.
6. Select `01 Imported UML schema contract`.
7. Click `Generate Plan`.
8. Click `Execute Node`.
9. Click `Review`.
10. Click `Preview Diff`.
11. Click `Apply All`.
