# ES-D07 — Discord punishment enforcement

Status: `IN_PROGRESS` / `ACTIVE`. Priority: 136. Depends on `ES-D03`, `ES-D05`, `ES-D06`. Internal package.

## Assignment

- Selected by the Discord-program worker on 2026-09-14 after live reconciliation.
- Start/base `main`: `074c0ae0bee7222bcd5c9096f8db0071f5b84cdf`.
- Implementation branch: `package/es-d07-discord-punishment-enforcement`.
- No pre-existing D07 implementation branch or PR existed at claim time.
- ES-D13 remains independently `BLOCKED` / `PARKED_BLOCKED` on PR #178 and is not modified, synchronized, merged, closed, or absorbed by this package.
- Open PR #199 owns Paper staff-menu paths. Open PR #139 owns Market/component work plus its branch-local V21 migration. D07 will not absorb either worker's work.

## Objective

Implement authoritative Discord-only punishment actions with durable external enforcement and recovery.

## Scope

Warning; temporary/permanent managed-role mute preserving ticket/support access; kick; temporary/permanent native guild ban; temporary/permanent channel/category read-only/no-access restrictions; unmute/unban/unrestrict/end/revoke/overturn flows; duration parser/presets; confirmation and immediate reauthorization; reason/explanation/message-delete options; temp expiry; DMs and delivery outcomes; native-ban reconciliation; quick commands calling the same services; durable outbox/worker semantics and partial-failure truthfulness.

Permanent Discord ban/mute/restriction requires Admin+ under approved policy. Discord native Timeout is not the normal mute mechanism.

Approval flows must authorize the concrete requested sanction at decision time, not only the abstract `APPROVE_SANCTION_REQUEST` operation. Loading/claiming/approving a request must re-check the requester's/approver's current authority, required approval rank, target protection, actual Discord consequence type, permanent/custom flags, duration ceiling and current policy before commit. In particular, Mod/Developer approval capability must never allow approval of an Admin-only permanent/custom consequence merely because the surface operation itself is permitted. Mirror the existing fail-closed punishment-request rank/revalidation semantics rather than building a weaker Discord-only approval path.

## Persistence and collision boundary

D01/D02 already merged the V19 Discord moderation schema, including `moderation_enforcement_targets`, `discord_reconciliation_state`, and `discord_maintenance_work`. D07 will first use those generic durable primitives and the existing Discord identity model. No new migration is claimed by this package at activation, avoiding collision with ES-X03's branch-local Market migration. A forward-only migration may be added only if implementation proves the merged schema cannot truthfully represent the package contract; any such change requires a fresh live migration collision preflight.

## Exclusions

No automatic Minecraft punishment, AutoMod enforcement, cross-platform `Both` orchestration, production deployment/cutover, production Discord configuration/data mutation, LiteBans authority change, or issue #43 acceptance.

## Required validation contract frozen at claim

- Deterministic unit/adapter coverage for success, rejection/authorization, Discord hierarchy denial, rate-limit/retry, restart/recovery, temporary expiry, idempotent replay, revision/conflict handling, partial failure, and shutdown/quiescence where applicable.
- Explicit approval-escalation coverage proving Mod/Developer cannot approve Admin-only permanent/custom Discord sanctions and that concrete sanction authority is rechecked at decision time.
- MariaDB recovery/integration coverage for every new durable write/read path without rewriting migration history.
- Quick-command and panel paths must converge on the same application service and final confirmation must perform immediate reauthorization.
- Public/user-facing responses must be explicit projections/allowlists rather than internal persistence records.
- Full clean build/tests, repository analyzers, hosted CI, Codacy zero new valid findings, harsh self-review, and all valid CodeRabbit findings repaired/resolved.
- Exact-head Staff Bot artifact/configuration validation and all other actually applicable configured exact-head checks.
- Isolated staging guild/bot destructive-enforcement validation where an authorized non-production harness is available. Production Discord is never used as a substitute.
- Sentinel/Pi are required only to the extent the current package contract and validation policy make them applicable to the final executable head; missing/queued/wrong-revision/failed evidence is never called passing.

## Completion checklist

- [x] Reconcile live `main`, registries, open PRs/branches, migrations, issue #43, and concurrent ownership.
- [x] Confirm dependency-complete D07 is the highest-priority eligible Discord package; keep parked D13 untouched.
- [x] Claim one temporary package branch from exact live `main`.
- [ ] Implement the complete D07 domain/application/persistence/JDA runtime surface with separated validation, persistence, orchestration, and presentation.
- [ ] Add deterministic unit and MariaDB integration/recovery tests for all required paths.
- [ ] Perform harsh self-review and repair all confirmed defects.
- [ ] Open/update the single D07 implementation PR after a coherent checkpoint.
- [ ] Freeze one exact executable candidate and complete all required hosted/static/review gates.
- [ ] Complete applicable authorized Sentinel/Pi/staging evidence without touching production Discord.
- [ ] Reconcile any moving `main` through normal merge history and rerun invalidated exact-head gates.
- [ ] Merge normally only when every required gate is terminal/green; never squash/rebase/force/auto-merge.
- [ ] Prove exact product containment, clean temporary package state, and publish terminal `COMPLETE` or genuine `BLOCKED` state to `main`.
