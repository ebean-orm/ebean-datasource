# Guides

Step-by-step guides written as instructions for AI agents and developers.

## Creating DataSource Pools

Step-by-step guide covering common scenarios for creating and configuring connection pools.

| Guide | Description |
|-------|-------------|
| [Create a DataSource Pool](create-datasource-pool.md) | Basic pool creation, read-only pools, Kubernetes configuration, AWS Lambda setup, and connection validation settings |

---

## Helping AI agents find these guides

AI coding agents can only follow these guides if they know they exist. Below are
copy-paste snippets for the most common AI tooling configurations. Add whichever
applies to your project.

### Project `README.md` (universal — works with all AI tools)

Add a section near the top of your project README:

```markdown
## Documentation & Guides

This project includes step-by-step guides for common tasks with the ebean-datasource connection pool:

**https://github.com/ebean-orm/ebean-datasource/tree/main/docs/guides/**

AI agents should fetch and follow the relevant guide before performing datasource-related tasks.
```

### Why README is important

Many AI agents (Copilot, Claude, Cursor, Aider) read the project `README.md`
automatically as part of understanding the codebase. A visible link in README is the
most universally effective way to ensure any agent working on your project will
discover the guides without requiring tool-specific configuration files.

------

### GitHub Copilot — `.github/copilot-instructions.md`

```markdown
## ebean-datasource

This project uses ebean-datasource for connection pooling. Step-by-step guides for common
tasks are at: https://github.com/ebean-orm/ebean-datasource/tree/main/docs/guides/

Key guides (fetch and follow when performing the relevant task):
- Create a DataSource Pool: https://raw.githubusercontent.com/ebean-orm/ebean-datasource/main/docs/guides/create-datasource-pool.md
```

### Claude Code — `CLAUDE.md`

Same content as above — Claude Code reads `CLAUDE.md` at the project root.

### Cursor — `.cursor/rules/ebean-datasource.mdc`

```markdown
---
description: ebean-datasource connection pool guidance
globs: ["**/*.java", "**/pom.xml"]
alwaysApply: false
---

## ebean-datasource

This project uses ebean-datasource for connection pooling. Before performing any datasource configuration task, fetch and
follow the relevant step-by-step guide from:
https://github.com/ebean-orm/ebean-datasource/tree/main/docs/guides/
```
