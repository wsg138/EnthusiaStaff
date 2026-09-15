# ES-D07 — Discord punishment enforcement

Status: `REVIEW` / `MERGE_PENDING`. Priority: 136. Depends on `ES-D03`, `ES-D05`, `ES-D06`. Internal package.

## Assignment

- Selected by the Discord-program worker on 2026-09-14 after live reconciliation.
- Start/base `main`: `074c0ae0bee7222bcd5c9096f8db0071f5b84cdf`.
- Implementation branch: `package/es-d07-discord-punishment-enforcement`.
- Implementation PR: #201.
- No pre-existing D07 implementation branch or PR existed at claim time.
- ES-D13 remains independently `BLOCKED` / `PARKED_BLOCKED` on PR #178 and is not modified, synchronized, merged, closed, or absorbed by this package.
- Open PR #199 owns Paper staff-menu paths. Open PR #139 owns Market/component work plus its branch-local migration. D07 does not absorb either worker's work.

## Objective

Implement authoritative Discord-only punishment actions with durable external enforcement and recovery.

## Scope

Warning; temporary/permanent managed-role mute preserving ticket/support access; kick; temporary/permanent native guild ban; temporary/permanent channel/category read-only/no-access restrictions; unmute/unban/unrestrict/end/revoke/overturn flows; duration parser/presets; confirmation and immediate reauthorization; reason/explanation/message-delete options; temp expiry; DMs and delivery outcomes; native-ban reconciliation; quick commands calling the same services; durable outbox/worker semantics and partial-failure truthfulness.

Permanent Discord ban/mute/restriction requires Admin+ under approved policy. Discord native Timeout is not the normal mute mechanism.

Approval flows authorize the concrete requested sanction at decision time, not only the abstract `APPROVE_SANCTION_REQUEST` operation. Loading/claiming/approving a request re-checks current authority, required approval rank, target protection, actual Discord consequence type, permanent/custom flags, duration ceiling and current policy before commit. Mod/Developer approval capability cannot authorize an Admin-only permanent/custom consequence merely because the surface operation itself is permitted.

## Persistence and collision boundary

D01/D02 already merged the V19 Discord moderation schema, including `moderation_enforcement_targets`, `discord_reconciliation_state`, and `discord_maintenance_work`. D07 uses those durable primitives and the existing Discord identity model. D07 adds no Flyway migration, so it does not collide with independent ES-X03 migration work.

## Exclusions

No automatic Minecraft punishment, AutoMod enforcement, cross-platform `Both` orchestration, production deployment/cutover, production Discord configuration/data mutation, LiteBans authority change, or issue #43 acceptance.

## Required validation contract frozen at claim

- Deterministic unit/adapter coverage for success, rejection/authorization, Discord hierarchy denial, rate-limit/retry, restart/recovery, temporary expiry, idempotent replay, revision/conflict handling, partial failure, and shutdown/quiescence where applicable.
- Explicit approval-escalation coverage proving Mod/Developer cannot approve Admin-only permanent/custom Discord sanctions and that concrete sanction authority is rechecked at decision time.
- MariaDB recovery/integration coverage for every new durable write/read path without rewriting migration history.
- Quick-command and panel paths converge on the same application service and final confirmation performs immediate reauthorization.
- Public/user-facing responses use explicit projections/allowlists rather than internal persistence records.
- Full clean build/tests, repository analyzers, hosted CI, Codacy zero new valid findings, harsh self-review, and all valid CodeRabbit findings repaired/resolved.
- Exact-head Staff Bot artifact/configuration validation and all other actually applicable configured exact-head checks.
- Isolated staging guild/bot destructive-enforcement validation where an authorized non-production harness is available. Production Discord is never used as a substitute.
- Sentinel/Pi are required only to the extent the current package contract and validation policy make them applicable to the final executable head; missing/queued/wrong-revision/failed evidence is never called passing.

## Frozen reviewed product checkpoint

- Frozen executable product head: `aea6696cb97df4463b90abfcbbd8bfa4bb80b913`.
- Review repair workflow `34925882460`, job `104243730808`, started from exact prior PR head `97f6f235fbaefb4b3956d8aef7fe9e132823b682`, applied the four verified CodeRabbit repairs, and pushed the product commit only after validation succeeded.
- Temurin Java `21.0.12+1` clean repository build/tests and `:staff-bot:verifyStaffBotRuntime` passed: `BUILD SUCCESSFUL`, 69 actionable tasks, including integration, domain, Paper, persistence, protocol, staff-bot, and Velocity tests/builds.
- Repository PMD 6.55.0 passed with zero findings. Changed-Java Lizard passed `CCN <= 8`, practical method length `<= 50`, and argument count `<= 8`. `DiscordPunishmentWorkerTest` remained bounded at 431 nonblank/non-line-comment lines. `git diff --check` passed.
- The final four CodeRabbit findings were repaired with regression coverage: invalid removal-state transitions are rejected; no-effect pending/retry expiry has a dedicated transition; custom durations honor the parser maximum; configured Discord IDs fail closed on JDA-compatible signed-long overflow; and managed mute reconcile/removal proves current ownership from the latest relevant audit-log role change rather than adopting external assignments.
- All four corresponding review threads were answered and resolved after verification.
- The immediately preceding Codacy repair also removed all three valid complexity findings structurally without broad exclusions.
- Disposable inspection/repair workflows and scripts remain only on an isolated temporary branch and are not part of PR #201.

## Staging availability

Isolated non-production Discord enforcement is not claimed as passed. Live reconciliation found no authorized D07 destructive target/fixture/harness in the trusted staging repository. The frozen package contract requires that destructive staging only where an authorized non-production harness is available; production Discord is never used as a substitute. No production Discord action/configuration/data mutation was attempted.

## Completion checklist

- [x] Reconcile live `main`, registries, open PRs/branches, migrations, issue #43, and concurrent ownership.
- [x] Confirm dependency-complete D07 is the highest-priority eligible Discord package; keep parked D13 untouched.
- [x] Claim one temporary package branch from exact live `main`.
- [x] Implement the complete D07 domain/application/persistence/JDA runtime surface with separated validation, persistence, orchestration, and presentation.
- [x] Add deterministic unit and MariaDB integration/recovery tests for required paths.
- [x] Perform harsh self-review and repair confirmed defects.
- [x] Open/update the single D07 implementation PR.
- [x] Freeze one reviewed executable candidate and pass the local clean-build/static repair gate.
- [ ] Complete current exact-head hosted CI, Codacy, and final CodeRabbit review with zero valid unresolved findings.
- [ ] Reconcile current `main` immediately before merge and rerun invalidated gates if it moved.
- [ ] Merge normally only when every required gate is terminal/green; never squash/rebase/force/auto-merge.
- [ ] Prove exact product containment, clean temporary package state, and publish terminal `COMPLETE` state to `main`.

## Exact next action

This state-only checkpoint follows frozen product `aea6696cb97df4463b90abfcbbd8bfa4bb80b913`. Run and inspect every normal PR-hosted gate on the resulting current head, require fresh Codacy zero-valid-finding evidence and zero valid unresolved review threads, reconcile live `main`, and merge PR #201 normally if all gates remain clean. Then verify containment/cleanup and publish canonical `COMPLETE` state without starting D08, D09, or any other package.
