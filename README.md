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

Build:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew build -x test --no-daemon
```

## Demo Tip

In the Blueprint ToolWindow, enable `Offline mock demo`, then click `Generate UML` or `Seed: UML Invite Flow`.
