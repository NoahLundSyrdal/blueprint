# Blueprint Current Status

Last updated: 2026-04-18

## One-Line Status

Blueprint is a working PyCharm/JetBrains plugin demo for architecture-first, dependency-aware Python node execution with scoped plan, execute, review, diff preview, and apply.

## What Works Today

- List-based node creation and editing.
- Typed nodes: schema, backend/service, frontend/CLI, test, migration, telemetry, docs, and other.
- Structured node fields:
  - title
  - summary
  - description
  - dependencies
  - file scope
  - acceptance criteria
- Local persistence for nodes and artifacts.
- Offline mock demo mode with deterministic plan, execution, and review output.
- Live provider mode through `CodexClient`.
- OpenAI is the default live provider.
- Anthropic is still available if explicitly selected with `BLUEPRINT_LLM_PROVIDER=anthropic`.
- Plan generation using `plan_generation_prompt`.
- Node execution using `per_node_execution_prompt`.
- Review using the review prompt/service.
- Diff preview using the JetBrains diff viewer.
- Approved apply flow with file-scope enforcement.
- Dependency IDs on nodes.
- DAG validation and readiness checks.
- Blocked reasons for dependency failures.
- Wave preview.
- Sequential "run ready" flow.
- Dependency picker.
- Read-only mini graph visualization.
- Click-to-select mini graph node cards.
- Mini graph/list/detail selection synchronization.
- Hover tooltips on mini graph node cards.
- Demo sample flows, including Project Invite Flow.
- UML Invite Flow seed that starts from a prefilled UML-like schema contract and Python file scopes.
- UML import from pasted PlantUML, Mermaid classDiagram, or simple entity-bullet text.
- Python project analyzer that detects config files, package roots, test roots, framework hints, and suggested test commands.
- Python-to-UML generation from the open project, producing editable Mermaid `classDiagram` text.
- Edited generated UML can be imported into normal Blueprint nodes, then executed through the existing plan/review/apply pipeline.

## Current Demo Flow

The recommended demo is:

1. Open the Blueprint tool window.
2. Enable Offline mock demo mode.
3. For an existing Python project, click `Generate UML`.
4. Review or edit the generated Mermaid class diagram, then click OK.
5. Blueprint imports the edited UML into schema, service, CLI, test, and docs nodes.
6. Show the node list and mini graph.
7. Click graph cards to show graph-driven navigation.
8. Select the first ready schema node.
9. Click `Generate Plan`.
10. Click `Execute Node`.
11. Click `Review`.
12. Click `Preview Diff`.
13. Click `Apply All`.
14. Show that downstream nodes become ready after dependencies are applied.

If the open project has too few Python classes for a compelling diagram, use `Import UML` or `Seed: UML Invite Flow` as the fallback demo path.

## UML / Schema Support Today

Blueprint supports UML-like architecture input as a schema node and can import common text UML formats into Blueprint nodes.

This means a user can either create a node manually like:

- Type: `SCHEMA`
- Title: `Project invite UML schema`
- Description: pasted UML, PlantUML, Mermaid, or structured architecture text
- File scope: exact files the schema node may create or change
- Acceptance criteria: explicit requirements the generated schema/model must satisfy

Downstream Python service, CLI, test, and docs nodes can depend on that schema node. Blueprint will treat the schema node as an upstream architecture contract through the node definition, dependency graph, and generated artifacts.

The `Import UML` button currently accepts pasted text and parses:

- PlantUML/Mermaid-style `class` or `entity` blocks.
- Simple bullet-field entity blocks.
- Arrow relationships such as `Project "1" --> "many" Invite`.
- Simple relationship prose such as `Invite belongs to Project`.

The importer creates normal Blueprint nodes, so the imported graph uses the same planning, execution, review, diff preview, and apply pipeline as hand-authored nodes.

Blueprint also has a `Generate UML` button. It scans Python files in detected source roots, extracts class names, fields, public methods, inheritance, and simple field-type relationships, then produces editable Mermaid `classDiagram` text. The user can change that diagram before importing it into Blueprint nodes.

This is the current honest product loop:

1. Open an existing Python project in PyCharm.
2. Generate UML from the current code.
3. Edit the generated UML text.
4. Import the edited UML into Blueprint nodes.
5. Run the existing plan, execute, review, diff, and apply flow to change code.

## Python Project Context

Blueprint analyzes the open PyCharm project before planning or executing nodes. It detects:

- Python config files such as `pyproject.toml`, `setup.py`, `setup.cfg`, `requirements.txt`, `poetry.lock`, `uv.lock`, `tox.ini`, `noxfile.py`, and `pytest.ini`.
- Source roots from `src/<package>` and top-level packages containing `__init__.py`.
- Test roots such as `tests` and folders containing `test_*.py`.
- Framework hints such as pytest, FastAPI, Django, Flask, Pydantic, SQLAlchemy, Typer, and Click.
- Suggested test commands such as `uv run pytest`, `poetry run pytest`, `pipenv run pytest`, or `python -m pytest`.

This context is injected into planning and execution prompts so generated work is grounded in the actual Python project shape.

The ToolWindow also has a `Python Context` button that displays the analyzer output in the graph/status panel.

The `Generate UML` button uses the same analyzer context to prefer real Python source roots over generated, virtualenv, build, and cache folders.

## What Not To Claim Yet

Do not claim that Blueprint currently:

- Imports arbitrary UML diagram images.
- Infers a perfect full architecture graph from arbitrary code or diagrams.
- Provides drag-and-drop graph editing.
- Runs true parallel execution.
- Performs deep PSI-based architecture inference.
- Applies code directly from a drawn graph without the reviewable node pipeline.
- Guarantees production-ready code from arbitrary UML input without review.

The honest claim is:

> Blueprint can generate an editable UML view from a Python project, import edited UML into reviewable architecture nodes, then drive scoped implementation changes with dependency checks, review, diff preview, and safe apply.

## Demo-Safe Claim

For the hackathon demo, say:

> "I start by defining the architecture as a schema node. Blueprint treats that as the upstream contract. The Python service, CLI, test, and docs nodes depend on it, so the tool can show what is ready, what is blocked, and what changed before anything is applied."

For the project-to-UML flow, say:

> "Blueprint reads the Python project into an editable UML diagram. I can change the architecture at the diagram level, import that edited design into nodes, and then Blueprint safely plans, patches, reviews, previews, and applies the code changes."

## Suggested UML Demo Node

Use this as the intended schema-node content:

```text
Type:
SCHEMA

Title:
Project invite UML schema

Description:
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

File scope:
blueprint_demo/project_invite/models.py

Acceptance criteria:
AC1: Invite model includes projectId, email, token, status, createdAt, and expiresAt.
AC2: Invite status is limited to pending, accepted, or expired.
AC3: Generated changes stay inside the schema node file scope.
```

## Current Technical Notes

- Gradle wrapper is pinned to Gradle 8.9.
- The default sandbox target is PyCharm Community (`platformType=PC`).
- The project should run Gradle with JDK 17.
- Current JDK 17 path on this machine:
  - `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- Build command that has passed locally:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew build -x test --no-daemon
```

## Known Rough Edges

- The UI is functional but dense.
- Offline mock mode is best for demos.
- Live provider mode requires environment variables and should not be the primary demo path.
- UML image import is not implemented yet. Text UML import exists for common class/entity formats.
- Project UML generation is regex/file based, not deep PSI. It is good for classes, fields, methods, inheritance, and simple type relationships.
- The mini graph is navigation-only, not an editor.
- Some Kotlin incremental compilation cache warnings have appeared, but the build succeeds after fallback compilation.
- The project is currently optimized for a hackathon demo, not production plugin distribution.

## Latest Product Improvement

Blueprint now includes `Generate UML`, which scans the open Python project and produces editable Mermaid `classDiagram` text. When the user clicks OK, the edited diagram is imported into the same Blueprint node pipeline as pasted UML.

Blueprint also includes `Import UML`, which turns pasted UML-like architecture text into:

1. An imported schema contract node.
2. A Python service node depending on the schema.
3. A Python CLI node depending on the service.
4. A pytest-style test node depending on service and CLI.
5. A docs node depending on service and CLI.

The nodes are normal persisted Blueprint nodes, not a separate demo-only path.

## Latest Demo Improvement

The plugin now includes a dedicated `Seed: UML Invite Flow` button that creates:

1. A schema node prefilled with UML-like architecture text.
2. A Python service node depending on the schema node.
3. A Python CLI node depending on the service node.
4. A pytest-style test node depending on service and CLI.
5. A docs node depending on service and CLI.

This makes the demo cleaner because the architecture-first story starts visibly from a UML/schema contract instead of a generic sample node.

## Best Next Improvement

Make the generated UML editor persistent and two-pane: left side diagram text, right side node preview. After that, use PyCharm PSI for deeper Python symbol analysis.
