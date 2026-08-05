# Feature Completion Status

This page is the main directory for understanding **what EnthusiaStaff does, how
complete each part is, where its code lives, and what still needs work**.

> **Overall verdict: NOT READY for production authority.** LiteBans and the
> existing production staff stack remain authoritative.

## How the percentages work

Percentages are rounded planning estimates based on implemented behavior,
automated tests, available staging evidence and unfinished requirements. They are
not generated automatically and are not a release score.

| Mark | Meaning |
| --- | --- |
| ✅ **100%** | The intended repository feature is implemented and tested. |
| 🟢 **80–99%** | Core behavior exists; focused integration or staging remains. |
| 🟡 **50–79%** | Useful implementation exists, but important behavior is unfinished. |
| 🟠 **20–49%** | Early or partial implementation; substantial work remains. |
| 🔴 **0–19%** | Not started, contract-only or externally blocked. |

A high feature percentage does not by itself authorize production use. The final
release also requires combined provider, topology, migration, security, load,
recovery and operational evidence.

## Open a feature group

Each group has its own detailed page. Open one to see:

- specific feature categories and percentages;
- a plain-language explanation of what each category does;
- commands or runtime ownership;
- direct links to important source files and directories;
- related staff/operator/developer documentation; and
- the remaining work for that category.

| Feature group | Estimated completion | What belongs there | Largest unfinished areas |
| --- | ---: | --- | --- |
| [[Core Platform and Infrastructure]] | **About 72%** | Runtime jars, architecture, Paper/Velocity lifecycle, MariaDB, protocol, safe-write controls, modes, configuration, identity and CI. | Complete operational modes, modular configuration/reload, Bedrock aliases, production-volume and multi-backend failure testing. |
| [[Moderation, Punishments, and Reports]] | **About 63%** | Cases, sanctions, punishment GUI/drafts, rank approval, escalation, history, appeals, reports, evidence and automod. | Request notifications, complete escalation policy, authenticated appeal-review UI, RoseChat PM evidence and automod integration. |
| [[Staff Tools, Investigations, and Player-State Safety]] | **About 44%** | Staff mode, freeze, vanish, inventory/Ender access, confiscation, economy actions, alts, inspector, testers and fake systems. | Remaining recovery and integration hiding, concurrent/offline inventory safety, provider-backed economy, alt lifecycle and fake systems. |
| [[Integrations, Migration, and Release Readiness]] | **About 36%** | Discord, website bridge/site, provider APIs, LiteBans migration, shadow/cutover, client/runtime acceptance and release evidence. | Provider reconstruction, private site, real-data migration rehearsal, 168-hour shadow, full topology/Bedrock/Folia/load/process-kill acceptance. |

The percentages were not changed by PR #65 because this work reconciles routing and status wording rather than evaluating every feature group again.

## Find information by question

### I want to know what a feature does

Open the matching feature-group page above. Every group page explains the purpose
and user-facing behavior before listing implementation files.

### I want to find the source code

Start with the matching feature-group page for the most important files, then use
[[Developer Code Guide]] for the complete repository map and end-to-end feature
traces.

### I want exact proof or blockers

Use the
[requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md).
It records exact status, source files, configuration, tests, remaining work and
blockers. The Wiki percentages are the readable planning view of that evidence.

### I want staff instructions

- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Punishment System]]
- [[Reports and Evidence]]
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Inventory and Confiscation Safety]]
- [[Alt Investigations]]

### I want administrator or release instructions

- [[Commands and Permissions]]
- [[Roles and Permissions|Rank-Authority]]
- [[Configuration]]
- [[Integrations]]
- [[Installation]]
- [[Recovery and Troubleshooting]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]

### I want to know what should be developed next

Use [[Remaining Development Map|Development-Blueprint]]. It records the owner
priority order but deliberately requires fresh live gap inspection before one
bounded feature is selected.

## Current development boundaries

PR #64 is the latest merged product checkpoint. It closes one mounted-movement
freeze bypass and does not deploy or activate the plugin.

PR #65 is documentation-only reconciliation. Read live GitHub to determine
whether it remains open or is already merged. No implementation feature is
preselected in tracked documentation after this reconciliation.

The recent merged priority-one sequence includes staff-mode transfer, rank,
disable-recovery and world-interaction corrections; vanish live-rank
reconciliation; and freeze recovery, precise interaction and mounted-movement
corrections. These merges do not establish complete production-like staff-mode,
vanish or freeze acceptance.

V16 remains the highest Flyway migration. LiteBans remains authoritative.

## Current command gaps

`/history` and the exact sanction-change lifecycle are merged. The remaining
known top-level command gap is:

```text
/fakebase
```

`/alts` and `/alt` are registered on Velocity, but the complete alt lifecycle,
GUI, confidence, exception, inheritance and staging work remains unfinished.

Command existence alone does not establish complete behavior; verify live source,
registration, permissions, tests and Java/Bedrock usability before selecting work.

## External blockers

- The supported RoseChat provider repository/API required for complete staff,
  private-message evidence and pre-broadcast moderation behavior is unavailable.
- Polar does not expose the supported violation-event contract required for
  automatic enforcement.
- Complete provider branches, the private site, representative migration data,
  real Bedrock/Folia clients and the full multi-server failure environment are
  unavailable or unfinished.
- The mandatory uninterrupted 168-hour LiteBans shadow observation has not run
  and belongs to later issue #43 production-cutover acceptance.

## Updating completion information

When implementation changes:

1. Update the exact row in `reports/REQUIREMENTS-MATRIX.md` first.
2. Update the detailed feature-group page.
3. Adjust the group percentage here only when meaningful requirement groups move.
4. Update [[Remaining Development Map|Development-Blueprint]] when the unfinished
   development order changes.
5. Keep commands in [[Commands and Permissions]], source traces in
   [[Developer Code Guide]], and validation procedure in [[Build and Testing]].
6. Run `python scripts/wiki/validate_wiki.py` before publication.

## Punishment history lifecycle update

The punishment history and exact sanction-change lifecycle from PR #46 is merged:
database-bounded `/history`, complete `/case` detail, exact
reduce/end/revoke/overturn behavior, request/appeal linkage, append-only audit and
concurrency coverage exist in the repository. Representative non-production
usability, website reviewer UI and production authority remain separate.
