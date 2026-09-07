# Press Distribution System Diagrams

This directory contains architectural and behavioral diagrams of the Press Distribution Management System, prepared for the thesis project.

## Source Files

| File | Purpose |
|------|---------|
| `database-schema.puml` | Database schema diagram (ERD) — tables, columns, types, primary/foreign keys, UNIQUE/CHECK constraints and indexes (based on Flyway migration V1) |
| `class-domain-model.puml` | Domain model class diagram — JPA entities, relationships, multiplicities, constraints and business formulas |
| `class-application-architecture.puml` | Layered architecture diagram — controllers, services, repositories, security, configuration, persistence |
| `sequence-login-and-audit.puml` | User login sequence diagram with failed attempt logging in the audit log |
| `sequence-password-recovery.puml` | Password recovery sequence diagram using a recovery code (unauthenticated) |
| `sequence-bulk-entry.puml` | Bulk entry sequence diagram for parish issue records (Administrator role) |
| `sequence-reports-csv-export.puml` | Report generation and CSV export sequence diagram with role-based access control |

## Technology

Diagrams use the [PlantUML](https://plantuml.com/) format — a text-based UML diagram description language. The `.puml` files are readable in any text editor and version-control friendly.

## Opening and Rendering

### IntelliJ IDEA

1. Install the **PlantUML Integration** plugin (Settings → Plugins → Marketplace).
2. Open any `.puml` file — a preview will appear in the side panel.

### Visual Studio Code

1. Install the **PlantUML** extension (jebbs.plantuml).
2. Open a `.puml` file and use the command `PlantUML: Preview Current Diagram` (Alt+D).

### draw.io / diagrams.net

1. Open [app.diagrams.net](https://app.diagrams.net).
2. Menu: Extras → Edit PlantUML — paste the `.puml` file content.
3. Note: PlantUML support in draw.io is limited and may not render all elements.

### Online Renderer

1. Open [PlantUML Web Server](https://www.plantuml.com/plantuml/uml).
2. Paste the `.puml` file content and click "Submit".

### Local CLI Rendering

If PlantUML CLI is installed (requires Java and Graphviz):

```bash
plantuml -tsvg docs/diagrams/*.puml
```

This command will generate `.svg` files alongside the source files.

## Notes

- **Do not commit** generated SVG/PNG/PDF files unless the thesis submission process requires it.
- Diagrams reflect the **current state of the implementation**. They should be updated after significant architectural or business flow changes.
- Labels and descriptions in diagrams are in English. Technical names (classes, methods, endpoints, tables) retain the original English spelling from the source code.
