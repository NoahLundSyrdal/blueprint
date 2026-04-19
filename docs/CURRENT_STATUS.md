# Blueprint Current Status

Last updated: 2026-04-18

## One-Line Status

Blueprint is a working PyCharm/JetBrains plugin demo for an infinite architecture loop: abstract a Python codebase into editable UML, refine that UML with chat, create reviewable code nodes, apply scoped changes, then abstract back to UML again.

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
- Demo sample flows, including Car Company Inventory Flow.
- UML Car Company Flow seed that starts from a prefilled UML-like schema contract and Python file scopes.
- Editable UML workspace from pasted PlantUML, Mermaid classDiagram, or generated project UML.
- Chat-assisted UML refinement through `CodexClient`; live mode defaults to OpenAI and requires `OPENAI_API_KEY`.
- Python project analyzer that detects config files, package roots, test roots, framework hints, and suggested test commands.
- Python-to-UML generation from the open project, producing editable Mermaid `classDiagram` text.
- Current UML can be converted into normal Blueprint code nodes, then executed through the existing plan/review/apply pipeline.

## Current Demo Flow

The recommended demo is:

1. Open the Blueprint tool window.
2. Enable Offline mock demo mode.
3. For an existing Python project, click `Abstract Code to UML`.
4. Follow the compact `First-Run Demo` checklist shown in the action panel when `examples/invite_project` is open.
5. Use the deterministic prompt `add an InvitePolicy entity`.
6. Click `Generate Code Diff`.
7. Click `Preview Diff`.
8. Click `Apply Approved Changes`.
9. Click `Refresh UML From Code` again to show the loop can repeat from the updated codebase.

If the open project has too few Python classes for a compelling diagram, use `Paste UML` or `Sample: Invite UML` as the fallback demo path.

## UML / Schema Support Today

Blueprint supports UML-like architecture as the central editable artifact. The user can abstract it from Python code, paste it manually, or refine it with chat before creating code-generation nodes.

This means a user can either create a node manually like:

- Type: `SCHEMA`
- Title: `Car company inventory UML schema`
- Description: pasted UML, PlantUML, Mermaid, or structured architecture text
- File scope: exact files the schema node may create or change
- Acceptance criteria: explicit requirements the generated schema/model must satisfy

Downstream Python service, CLI, test, and docs nodes can depend on that schema node. Blueprint will treat the schema node as an upstream architecture contract through the node definition, dependency graph, and generated artifacts.

The `Paste UML` flow accepts pasted text into the main UML editor. `Create Code Nodes` then parses:

- PlantUML/Mermaid-style `class` or `entity` blocks.
- Simple bullet-field entity blocks.
- Arrow relationships such as `Project "1" --> "many" Invite`.
- Simple relationship prose such as `Invite belongs to Project`.

The importer creates normal Blueprint nodes, so the imported graph uses the same planning, execution, review, diff preview, and apply pipeline as hand-authored nodes.

Blueprint also has an `Abstract Code to UML` button. It scans Python files in detected source roots, extracts class names, fields, public methods, inheritance, and simple field-type relationships, then writes editable Mermaid `classDiagram` text into the main UML editor.

This is the current honest product loop:

1. Open an existing Python project in PyCharm.
2. Abstract UML from the current code.
3. Edit the generated UML text or refine it with chat.
4. Create code nodes from the edited UML.
5. Run the existing plan, execute, review, diff, and apply flow to change code.
6. Abstract the updated codebase back into UML and continue.

## Python Project Context

Blueprint analyzes the open PyCharm project before planning or executing nodes. It detects:

- Python config files such as `pyproject.toml`, `setup.py`, `setup.cfg`, `requirements.txt`, `poetry.lock`, `uv.lock`, `tox.ini`, `noxfile.py`, and `pytest.ini`.
- Source roots from `src/<package>` and top-level packages containing `__init__.py`.
- Test roots such as `tests` and folders containing `test_*.py`.
- Framework hints such as pytest, FastAPI, Django, Flask, Pydantic, SQLAlchemy, Typer, and Click.
- Suggested test commands such as `uv run pytest`, `poetry run pytest`, `pipenv run pytest`, or `python -m pytest`.

This context is injected into planning and execution prompts so generated work is grounded in the actual Python project shape.

The ToolWindow also has a `Python Context` button that displays the analyzer output in the graph/status panel.

The `Abstract Code to UML` button uses the same analyzer context to prefer real Python source roots over generated, virtualenv, build, and cache folders.

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

> "Blueprint abstracts the Python project into editable UML. I can refine the architecture with chat, create reviewable code nodes when the design is ready, safely apply the changes, and then abstract the updated codebase back into UML again."

## Suggested UML Demo Node

Use this as the intended schema-node content:

```text
Type:
SCHEMA

Title:
Car company inventory UML schema

Description:
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

File scope:
blueprint_demo/car_company/models.py

Acceptance criteria:
AC1: InventoryVehicle includes modelId, dealershipId, status, modelYear, and color.
AC2: InventoryVehicle status is limited to available, reserved, or sold.
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

Blueprint now centers on an editable UML workspace. `Abstract Code to UML` scans the open Python project and fills the UML editor. Chat can refine that UML. `Create Code Nodes` converts the current UML into the same Blueprint node pipeline as pasted UML.

Blueprint also includes `Import UML`, which turns pasted UML-like architecture text into:

1. An imported schema contract node.
2. A Python service node depending on the schema.
3. A Python CLI node depending on the service.
4. A pytest-style test node depending on service and CLI.
5. A docs node depending on service and CLI.

The nodes are normal persisted Blueprint nodes, not a separate demo-only path.

## Latest Demo Improvement

The plugin now includes a dedicated invite-first-run checklist plus a `Sample: Invite UML` fallback that creates:

1. A schema node prefilled with UML-like architecture text.
2. A Python service node depending on the schema node.
3. A Python CLI node depending on the service node.
4. A pytest-style test node depending on service and CLI.
5. A docs node depending on service and CLI.

This makes the demo cleaner because the architecture-first story starts visibly from a UML/schema contract instead of a generic sample node.

## Best Next Improvement

Make the generated UML editor persistent and two-pane: left side diagram text, right side node preview. After that, use PyCharm PSI for deeper Python symbol analysis.
