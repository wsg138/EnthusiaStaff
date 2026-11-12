# Documentation guide

EnthusiaStaff is a **live network plugin under active development**. Use this index instead of treating dated AI-worker reports, old rollout manifests, or the GitHub Wiki as interchangeable specifications.

## Start here

| I need to… | Read first | Then consult |
| --- | --- | --- |
| Understand the current work and blockers | [Current roadmap](current-roadmap.md) | [Feature reference](feature-reference.md), [hands-on acceptance issue #236](https://github.com/wsg138/EnthusiaStaff/issues/236) |
| Build or test the project | [Development workflow](development.md) | [Wiki build and testing](wiki/pages/Build-and-Testing.md) |
| Understand module responsibilities | [Architecture](architecture.md) | [Module and migration map](repository-cleanup/MODULE-RELOCATION-PLAN.md) |
| Install or operate server runtimes | [Wiki installation](wiki/pages/Installation.md) | [Configuration](configuration.md), [recovery](rollback.md), [database](database.md) |
| Work on moderation/staff behavior | [Feature reference](feature-reference.md) | [Punishment workflow](punishment-workflow.md), [inventory safety](inventory-safety.md), [security](security.md) |
| Integrate providers, APIs, web or bot | [External component catalog](repository-cleanup/EXTERNAL-COMPONENT-CATALOG.md) | [Discord platform](discord-moderation-platform.md), [website/API wiki](wiki/pages/Website-and-Web-API.md) |
| Understand intended versus implemented behavior | [Specifications index](specifications/README.md) | [Product goals](../ENTHUSIASTAFF-GOALS.md), merged code and tests |
| Understand why something changed | [History timeline](history/TIMELINE.md) | [Historical archive](history/archive/README.md), [source catalog](history/SOURCE-CATALOG.md), [ADRs](decisions/) |
| Contribute to the repository cleanup | [Cleanup status and review queue](repository-cleanup/STATUS.md) | [Execution plan](repository-cleanup/README.md), [tracking issue #426](https://github.com/wsg138/EnthusiaStaff/issues/426) |

## Current code locations

The **current physical layout is still at the repository root**. The proposed `src/` and `external/` directories are not yet implemented; source paths must not be guessed from the future plan.

| Area | Current directory |
| --- | --- |
| Minecraft Paper plugin | [`paper/`](../paper/) |
| Minecraft Velocity plugin | [`velocity/`](../velocity/) |
| Shared libraries, state and network protocol | [`common/`](../common/), [`domain/`](../domain/), [`persistence/`](../persistence/), [`protocol/`](../protocol/) |
| StaffBot and Cloudflare moderation web application | [`staff-bot/`](../staff-bot/), [`moderation-web/`](../moderation-web/) |
| Published/compile-time APIs and integration contracts | [`discord-platform-api/`](../discord-platform-api/), [`moderation-platform-api/`](../moderation-platform-api/), [`integration-contracts/`](../integration-contracts/) |
| Authority bridge transition runtime | [`paper-authority-bridge/`](../paper-authority-bridge/) |
| Tests and validation | [`integration-tests/`](../integration-tests/), [`validation/`](../validation/) |
| External mirrored or metadata-only repositories | [`components/`](../components/) and [catalog](repository-cleanup/EXTERNAL-COMPONENT-CATALOG.md) |
| Build/deployment/wiki and mirror tooling | [`scripts/`](../scripts/), [`tools/`](../tools/), [`.github/workflows/`](../.github/workflows/) |

## Which information is authoritative?

1. **Implemented behavior:** exact merged source, configuration, migrations, and tests on the relevant `main` commit.
2. **Desired behavior:** [product goals](../ENTHUSIASTAFF-GOALS.md) and current focused requirements; these do not certify implementation.
3. **Verification / deployment:** scoped CI, database/runtime evidence, and independently confirmed production acceptance. On November 11, 2026, the owner reported installing the **Paper 0.1.1** JAR; this does not verify other deployed modules or their hashes.
4. **Human-readable procedures:** [source-controlled Wiki](wiki/README.md) and focused docs. The [published wiki](https://github.com/wsg138/EnthusiaStaff/wiki) may lag the reviewed Markdown source until the protected publish workflow is run.
5. **Historical records:** [timeline](history/TIMELINE.md), [source catalog](history/SOURCE-CATALOG.md), dated migration decisions and agent handoffs preserve the design/evidence *as of that date*. They are not a current status override.

In particular, [`WORKSPACE-MANIFEST.md`](../WORKSPACE-MANIFEST.md), [`UPGRADE-MANIFEST.md`](../UPGRADE-MANIFEST.md), older audits under `reports/`, and historical LiteBans migration instructions contain dated assumptions. Some remain referenced by validators, so they are preserved until a dependency-aware move is validated.

## Wiki and review

Wiki pages are authored in [`docs/wiki/pages/`](wiki/pages/); changes should pass `python scripts/wiki/validate_wiki.py`. The [wiki publishing instructions](wiki/README.md) describe the separate, protected live publication with backup/restore.

The [cleanup audits](repository-cleanup/STATUS.md) track provisional PR and branch dispositions. A PR marked superseded is closed only after exact replacement evidence is checked; an unmatched test or stacked dependency keeps it open. **Do not bulk-delete branches, source mirrors or worker evidence on the basis of filename age.**
