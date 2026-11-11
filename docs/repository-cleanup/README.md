# Repository cleanup — coordinated execution

**Tracking issue:** [#426](https://github.com/wsg138/EnthusiaStaff/issues/426)  
**Baseline:** 2026-11-11, `main` at `2d0e425ed0b59c8ecd1a2485514b873263782390`  
**Scope:** repository structure, documentation, wiki, open PRs, issues, branches and obsolete coordination files. **This is an audit and staged migration, not a deletion authorization.**

## Goals

1. Make the repository legible to a developer who did not participate in its AI-assisted history.
2. Establish one **current** source of truth per subject (features, configuration, operational status, build, architecture).
3. Preserve original proposals, critical decisions, migration evidence and verifiable history in a navigable chronology and archive.
4. Review every open PR/issue and every non-default branch. Close or delete only with evidence of disposition and dependency checks.
5. Keep the GitHub wiki source-controlled under `docs/wiki/pages/`, validate it and publish separately.
6. Avoid disrupting the live EnthusiaStaff 0.1.1 deployment, runtime modules, integration copies and CI paths.

## Snapshot and workstreams

At kickoff: **142 open PRs, 35 open issues, 208 branches** (includes `main`), 5,153 tracked tree entries, approximately 171 `ai-agents/` files, 149 `docs/` files, and 2,268 `components/` files. Counts are a dated snapshot; query GitHub for live status.

The exact 142-PR snapshot is in [PR-SNAPSHOT-2026-11-11.md](PR-SNAPSHOT-2026-11-11.md). PR audit responsibility is split evenly: open PR numbers **697 and higher** (71 at baseline) versus **696 and lower** (71).

| Workstream | Owner assignment | Main deliverable |
| --- | --- | --- |
| W1 | PRs #697 and higher | Evidence-backed triage of the 71 newer open PRs |
| W2 | PRs #696 and lower | Evidence-backed triage of the 71 older open PRs |
| W3 | Open issues and branches | Issue deduplication, branch provenance/dependency inventory |
| W4 | Historical documentation and specifications | Current-vs-historical map, source-linked development timeline, archive proposal |
| W5 | Module, external component and fork architecture | Code/Gradle/dependency map, linked external repository inventory, staged relocation plan |
| W6 | GitHub wiki and developer navigation | Verified wiki accuracy/link audit, focused source-controlled wiki PR |

Each worker reports a checkpoint or final result to **issue #426** with the heading `CLEANUP-W1` through `CLEANUP-W6`, links to source paths/PRs/commits and an explicit list of unresolved items. The issue is the coordination index; it must not become a new dumping ground for full copied logs.

## Proposed eventual directory model

This is an architectural target, **not the current physical layout**. Do not move code based solely on this diagram.

```text
EnthusiaStaff/
  README.md
  docs/
    specifications/
    architecture/
    developer/
    operations/
    integrations/
    history/
      TIMELINE.md
      decisions/
      milestones/
      archive/
    wiki/
      pages/
    repository-cleanup/      # temporary coordination; archive when complete
  src/                        # future staged Gradle-aware migration
    minecraft/
      paper/
      velocity/
      authority-bridge/
    services/
      staff-bot/
      moderation-web/
    libraries/
    apis/
  external/                   # verified cross-repository references and provenance
  tooling/
  .github/
  gradle/
  build.gradle.kts
  settings.gradle.kts
```

Keep `paper/`, `velocity/`, `domain/`, `persistence/`, `protocol/`, `staff-bot/`, `components/`, and the existing Gradle/workflow paths where they are until W5 proves a migration can preserve builds, CI and deployments.

## Authority and classification rules

- **Current implementation truth:** merged `main` source, configuration, schemas, tests and environment-specific evidence.
- **Desired product behavior:** source-controlled specification, noting unimplemented portions explicitly.
- **Historical truth:** dated specs, original handoffs, ADRs, proof records, accepted/rejected approaches. Do not rewrite them as though they always described today's state.
- **Production truth:** separately verified live status. A merged PR or build is not itself live-deployment evidence.
- **Generated/ephemeral state:** should not be committed; first confirm consumers and provenance before removing tracked material.

For every existing document: classify as **canonical current / current specialized / historical unique / duplicate superseded / referenced tooling contract / generated unnecessary / unresolved**. Record old path, new home or canonical pointer, last evidence, and all incoming references before moving/removing.

For PRs: **merge candidate / active but blocked / superseded by exact PR/commit / obsolete and unneeded / unknown**. For branches: **protected/default / open-PR head / active external dependency / fully merged / unique unmerged / unknown**. Nothing is marked “safe to delete” based solely on its age or title.

## Execution gates

**Phase A — inventory:** reports and document-only PRs. No deletion, production changes, or destructive Git actions.

**Phase B — consolidate:** approve one current documentation map; create source-linked history timeline; reconcile current specs and wiki. Keep legacy snapshots available under history where appropriate.

**Phase C — PR/issue/branch triage:** resolve or merge real unfinished work with independent review/tests; close superseded items with replacement links; remove branches only when merged/unused and cross-reference checks pass.

**Phase D — code layout migration:** only after module graph and path consumers are proven. Separate coherent PRs, exact-head full Gradle/MariaDB/CI tests, rollback plan, versioned release compatibility and deployment documentation.

**Phase E — wiki publication:** use `docs/wiki/pages/`, validate with `python scripts/wiki/validate_wiki.py`, merge reviewed source, then follow the existing manual Publish Wiki workflow, preserving its backups.

## Critical protections

- **Do not** mass-close PRs/issues or mass-delete branches in the inventory phase.
- **Do not** change Cloudflare DNS, production servers, MariaDB, LuckPerms, or deploy any JAR as part of repository cleanup.
- **Do not** remove `components/` mirrored source unless W5 verifies synchronization and build/runtime consumers. Some components are actual tracked source, not mere links.
- **Do not** remove `ai-agents/` or legacy manifests until any CI/scripts or active work-package references are found and handled.
- **Do not** treat a newly written condensed document as proof that the old detail can safely be lost; retain source links and unique evidence.
- No credentials, internal network addresses, player/case data or secrets in public GitHub documentation.
- Each worker uses its own short-lived branch for any authored change. Avoid writing to the same documents as other workers.
- Historical references remain available in Git history, but hard-to-find history alone does not replace a usable source-controlled archive when it is operationally important.

## Current files worth reconciling

- `README.md` already links many flat documentation pages.
- `ENTHUSIASTAFF-GOALS.md` is long and mixes desired scope with older assumptions.
- `WORKSPACE-MANIFEST.md` and `UPGRADE-MANIFEST.md` contain stale checkpoints and historic pre-deployment material.
- `docs/current-roadmap.md` has a dated 2026-10-07 reconciliation.
- `docs/repository-cleanup-audit.md` is the prior, non-destructive initial audit; this plan extends rather than replaces it.
- `ai-agents/` contains handoff and package registries that may still be referenced by active work.
- `docs/wiki/pages/` is the publishable wiki source; `docs/wiki/legacy/` contains an old snapshot.
- `components/` holds tracked external copies, especially the site, Commend and Currency.
- `docs/architecture.md` contains historic Java/API statements amended by later notes; prefer an explicit current section over a contradictory document.

**Exit criterion:** a new developer can navigate source, current docs, status, history, build and wiki in minutes; no outstanding PR/issue/branch lacks a documented disposition; all proposed removals are individually justified and tested.
