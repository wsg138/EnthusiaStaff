# PR #48 handoff — staff report queue and detail GUI

Recorded: 2026-08-02 America/Indiana/Indianapolis

## Repository and work item

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Work item | Staff report queue and detail inventory workflow |
| Starting `main` | `9d7f31945db37f9b2872ac57cb24de6b6b281198` |
| Branch | `feature/report-queue-gui` |
| Pull request | [#48 — Add staff report queue and detail GUI](https://github.com/wsg138/EnthusiaStaff/pull/48) |
| Intended post-merge state | `IDLE` |

Live GitHub and code state take priority over this report. Read PR #48 for the exact final feature SHA, workflow/job evidence, review status and merge result.

## Implemented behavior

- `/reports` opens a 54-slot staff report inventory for authorized Paper players.
- Explicit `/reports open|mine|claimed|review|closed`, `view` and mutation arguments remain available as the text/console/Bedrock fallback.
- Queue filters cover open, claimed by the viewer, all claimed, awaiting review and recently closed reports.
- Queue reads use the existing database-bounded report store with a maximum of 100 current results and local inventory pages.
- Report details show the reason, state, revision, reporter and target IDs, server/location context, description and retained evidence counts.
- Sensitive public-chat, private-message and client-evidence JSON is not copied into inventory item lore.
- Available actions are derived from the durable report state and assignment.
- Every GUI action collects a private audit note and opens a separate confirmation screen.
- The confirmation submits the exact revision displayed to staff and uses a stable operation UUID for idempotent retry behavior.
- Applied and rejected results reload current report state; a refresh failure cannot erase or misreport a known commit outcome.
- Rapid overlapping queue/detail loads are fenced per viewer so an older database response cannot replace a newer screen.
- Inventory movement and drag events are cancelled, state is bound to one viewer, and report permission is rechecked on interaction and presentation.
- Database work stays on the existing bounded worker executor; inventory and message operations return to the player entity scheduler.
- Pending input, load and confirmation state is removed when the player disconnects.

## Material architecture and files

### Added

- `paper/src/main/java/net/enthusia/staff/paper/report/ReportGuiAccess.java`
- `paper/src/main/java/net/enthusia/staff/paper/report/ReportGuiState.java`
- `paper/src/main/java/net/enthusia/staff/paper/report/ReportGuiHolder.java`
- `paper/src/main/java/net/enthusia/staff/paper/report/ReportGuiRenderer.java`
- `paper/src/main/java/net/enthusia/staff/paper/report/ReportGuiController.java`
- `paper/src/test/java/net/enthusia/staff/paper/report/ReportGuiAccessTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/report/ReportGuiStateTest.java`

### Updated

- `paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java`
- `paper/src/main/java/net/enthusia/staff/paper/command/ReportsCommand.java`
- `docs/wiki/pages/Reports-and-Evidence.md`
- `ai-agents/WORKSPACE-STATE.md`
- `ai-agents/reports/agent-handoffs/latest.md`

The GUI reuses `ReportStore`, `JdbcReportQueryStore` and `JdbcReportStateStore`; it does not introduce a parallel report engine or mutation path.

## Migrations and persistence

- No migration was added.
- No existing migration was edited.
- The live migration boundary at work-item start was `V14__punishment_history_and_exact_sanction_changes.sql`.
- `V1` through `V14` remain immutable.
- The GUI relies on the existing report row lock, optimistic revision, idempotency, audit, report-message and Discord-outbox transaction behavior.

## Commands, permissions and configuration

### Commands

- `/reports` — opens the staff GUI for an authorized player; console retains text output.
- Existing explicit queue, detail and state-change arguments remain unchanged as the plain-text fallback.

### Permission

- `enthusiastaff.reports.manage` — existing permission; no new authority node was added.

### Configuration

- No new configuration file or key was added.
- Modular report and GUI configuration remains a separate work item.

## Harsh review findings and fixes

The entire PR diff was reviewed separately from implementation.

Confirmed defects fixed before final validation:

1. **Out-of-order asynchronous navigation:** rapid queue/detail requests could complete in reverse order and reopen an older screen. Per-viewer load IDs now allow only the current request to present or report a load failure.
2. **Off-thread player-state reads:** asynchronous report work used live `Player` UUID access. Actor/viewer UUIDs are now captured on the owning thread or read from immutable GUI state before worker execution.
3. **Ambiguous post-commit refresh failure:** a committed report action followed by a failed detail reload could be presented as a failed mutation. Commit result and refresh result are now separated; unknown mutation failures explicitly say the outcome was not confirmed and require reopening before retry.

Reviewed and preserved boundaries:

- no main-thread database work;
- no unbounded database query or in-memory queue;
- exact report revisions remain authoritative;
- no raw sensitive evidence in ordinary item lore or logs;
- command fallback remains available;
- no migration, production, authority or LiteBans change;
- no duplicate report persistence system.

Safe optional cleanup and unrelated future work were not used to expand this PR.

## Validation and review evidence

Tracked content must be frozen before exact-head validation. Read PR #48 live for the final evidence comment, including:

- final feature SHA;
- Java 21 workflow run and job IDs;
- clean Gradle build and all configured test results;
- MariaDB/Testcontainers, migration and checksum results reached by the configured build;
- aggregate coverage summary;
- Paper and Velocity runtime JAR integrity, hashes and provider-leak inspection;
- wiki validation;
- combined status, review submissions and unresolved thread count;
- explicit Pi evidence or the statement that no exact-head Pi run exists.

A cancelled, superseded, merge-ref-only or different-head run is not final evidence.

## Merge readiness or blocker

This handoff does not itself assert merge readiness. PR #48 may be marked ready and merged with a normal merge commit only after the exact final feature head is green, the branch is synchronized with `main`, all valid review findings are resolved and the final evidence is posted on the PR.

If any required gate remains unavailable or red, leave the PR unmerged and treat the live PR evidence as the blocker record.

## Production boundary

- No plugin was deployed.
- No production system, database, credential, Discord route or player data was accessed.
- EnthusiaStaff authority was not activated.
- LiteBans remains authoritative.
- Issue #43 and the 168-hour production acceptance window remain untouched.
- A development merge does not authorize staging or production cutover.

## Remaining work

The report subsystem still needs separate work for:

- modular `reports.yml` and GUI layout/configuration with safe reload behavior;
- the supported RoseChat private-message callback bridge;
- dedicated sensitive-evidence presentation and privacy review;
- Discord report rendering and delivery validation;
- production-like multi-server/Folia staging.

## Next recommended work item

Implement modular report policy and GUI configuration, including validated defaults, atomic reload behavior and command/Bedrock parity. Reconcile that recommendation with live GitHub and current requirements before creating another branch.
