# ES-D05 — Staff bot runtime foundation

Status: `BLOCKED` / `PARKED_BLOCKED`. Priority: 134. Depends on `ES-D01`–`ES-D03` (all `COMPLETE`). Internal package.

## Claim and current reconciliation

Selected by the owner-authorized Discord-program worker on 2026-08-24 and carried through implementation, review, frozen-product validation, required live Discord acceptance, and current-main reconciliation on PR #160.

- Starting `main`: `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`.
- Implementation branch: `package/es-d05-staff-bot-runtime`.
- Implementation PR: #160, preserved and unmerged.
- Current package-record head: `6451ede1d6caeeeee19ac16eac86fbbe5570bff5`.
- Frozen reviewed D05 product source: `5f24ba1818c81e0a30a516fa70c8597586184b00`.
- First executable current-main reconciliation: ordinary two-parent merge `9c99e78f520cd59e7e59506c37573ac9ad028d63`.
- Latest ordinary current-main state reconciliation: `5dee27e700a5fdd0a78121a5fd16c863dac5e0dc`.
- Canonical `main` at blocker-publication start: `592778acc3c77f834359732e16ff12b7b1e881d4`.
- Canonical Flyway boundary is V19. D05 adds no migration and does not consume D04's unmerged V20.
- `ES-D04` remains independent on Staff PR #151 and was not modified, synchronized, merged, renumbered, or replaced.
- Production Discord configuration/deployment/data access, LiteBans cutover, and issue #43 acceptance remain unauthorized.

Canonical current handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-hosted-validation-blocked.md`.
Acceptance checkpoint on preserved implementation branch: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-discord-acceptance.md`.
Historical staging-blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`.

## Runtime identities and secret boundary

These identifiers are public/non-secret configuration:

- production application ID: `1541279426233376818`
- staging application ID: `1541279616881397772`
- Enthusia guild ID: `1410303324745371709`
- staff bot testing channel ID: `1541286004298752091`

D05 deliberately requests no privileged Gateway intents. Bot tokens remain secret and must never be committed, logged, copied into tests, requested in chat, exposed in artifacts, modified through this workflow, or placed on command lines.

## Objective and scope

Add the staff Discord bot as a third first-class Java 21 process isolated from Paper and Velocity, with no destructive moderation commands yet. Implemented scope includes JDA 6.5.0, staging/production identity fencing, Enthusia-only guild locking, staging test-channel view/send fencing, Gateway reconnect/backoff and REST rate-limit handling, bounded executors/queues, secret injection, health/readiness, privacy-safe lifecycle logging, graceful shutdown, bounded read-only interaction replay protection, shaded runtime packaging/integrity verification, tests/docs, and the non-destructive `--smoke-test`. Existing webhook delivery remains separate.

Exclusions remain punishment side effects, rich moderation UX, AutoMod enforcement, public installable bot behavior, production deployment/configuration, DiscordSRV role-sync replacement, token storage, and production cutover.

## Implementation checklist

- [x] Add isolated Java 21 `staff-bot` runtime module and executable shaded artifact.
- [x] Implement explicit staging/production configuration with secret redaction and exact application identity fencing.
- [x] Implement minimal Gateway intents, reconnect/backoff, Enthusia-only guild fence, and staging test-channel fence.
- [x] Add bounded application executor and bounded interaction replay/idempotency primitive.
- [x] Add local health/readiness endpoint and privacy-safe structured lifecycle logging.
- [x] Implement deterministic graceful shutdown and restart-safe lifecycle behavior.
- [x] Add unit/lifecycle/reconnect/rate-limit/shutdown/config/guild-fence/idempotency tests.
- [x] Update runtime packaging/integrity checks and operator documentation without changing the webhook subsystem.
- [x] Perform harsh full-diff review and resolve all valid review/static/CI findings.
- [x] Obtain exact-head hosted validation for the frozen D05 product source.
- [x] Obtain the required non-destructive staging Discord connection acceptance with exact application/guild/test-channel evidence.
- [x] Reconcile current `main` through normal merge history while preserving D04/X03 and proving later deltas are state/documentation-only.
- [ ] Obtain fresh mandatory post-reconciliation executable hosted validation once GitHub Actions scheduling is available.
- [ ] Merge normally, verify containment/cleanup, publish `COMPLETE`, and stop without starting D06.

## Frozen product evidence

Frozen D05 product source: `5f24ba1818c81e0a30a516fa70c8597586184b00`.

- Coverage/full Java 21 validation `32874248685` / job `97888464396`: `SUCCESS`; full build/tests, MariaDB/Testcontainers where applicable, aggregate JaCoCo, runtime JAR tasks, and `:staff-bot:verifyStaffBotRuntime` passed.
- Aggregate JaCoCo: 50.76% lines / 41.41% branches / 53.21% instructions.
- Validation artifact `9573547679`; digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`.
- Staff Bot Configuration Cache `32874248800` / job `97888275507`: `SUCCESS` twice with configuration-cache problems treated as failures.
- Sentinel Restart Artifact `32874248693`: `SUCCESS`.
- Codacy: zero new issues; 63.04% diff coverage; +0.17% coverage variation.
- CodeRabbit: `SUCCESS` after valid runtime/build findings were corrected; all live inline review threads resolved.
- Canonical Pi public `32879118794` and correlated private `32880103099` / job `97907230239`: `SUCCESS` for the frozen Paper/Pi gate.

These results remain attributed only to the exact revisions that executed them and do not substitute for the fresh post-reconciliation executable validation now required.

## Required live Discord acceptance — PASS

The prior staging-Discord acceptance blocker is cleared.

Trusted `wsg138/EnthusiaStaff-Staging` run `32926306691`, latest attempt 3 / job `98071453002`, completed successfully on trusted self-hosted runner `Lincoln-PI-4` (`Linux/ARM64`). Trusted staging-control head `03b3fce61bffe552d7905a4e4aa18e3015ea4e00` pins exact D05 product source `5f24ba1818c81e0a30a516fa70c8597586184b00`, sets up Java 21, builds/verifies the staff-bot runtime before secret scope, and executes the existing non-destructive `--smoke-test`.

Sanitized evidence:

- staging application `1541279616881397772`: `PASS`;
- Enthusia guild `1410303324745371709`: `PASS`;
- required test channel `1541286004298752091`: `PASS` for view/send;
- readiness: `PASS`;
- smoke exit: `0`;
- graceful close/shutdown: `PASS`.

The workflow sends no moderation action or test message and changes no Discord configuration. It does not access production moderation data. No bot-token value was inspected, requested, committed, logged, copied into tracking text, modified, or placed on a command line.

## Current-main synchronization

The first merge that changed aggregate executable state was normal two-parent merge `9c99e78f520cd59e7e59506c37573ac9ad028d63`. Later `main` advances were documentation/orchestration/component-metadata only and were reconciled normally through `5dee27e700a5fdd0a78121a5fd16c863dac5e0dc`.

Current package-record head `6451ede1d6caeeeee19ac16eac86fbbe5570bff5` contains only D05 state/handoff tracking after that executable reconciliation. Empty validation-trigger commit `6b12c9ba781cd85075df649d89a3a01e7245d6b7` had exactly zero file differences from its parent. This preserves executable-tree provenance but does not waive the need to execute the fresh hosted gates.

## Current external blocker — ordinary GitHub Actions validation unavailable

Fresh executable validation after `9c99e78...` is mandatory under `VALIDATION-POLICY.md`. Current GitHub Actions scheduling cannot provide it.

Observed during this continuation:

- repository-wide `in_progress` workflow count: `0`;
- repository-wide queued workflow count: `14`;
- `main` workflow-dispatch run `32984827059` remained queued since 15:21 UTC;
- D05 Coverage `32984359237` and `32984371731` remained queued with no jobs;
- Staff Bot Configuration Cache `32984361382` remained queued after retry with no jobs;
- Sentinel `32984723125` had no completed executable result;
- Pi runs including `32984459623` and `32984806337` remained queued/non-passing.

A bounded validation-only PR #167 pointed to the identical implementation candidate under a fresh PR concurrency key. External GitHub integrations received the event, including an exact-head Codacy analysis, but GitHub Actions created no exact-head workflow run. An empty identical-tree commit and a handoff-only content update also failed to produce a new Actions run. PR #167 was closed without merge.

This is infrastructure-unavailable evidence, not a D05 product failure. It nevertheless blocks #160 because queued/missing checks are not passing evidence, and `VALIDATION-POLICY.md` expressly forbids the owner-approved infrastructure exception for a missing ordinary GitHub-hosted build that the repository normally executes.

## Exact unblock

Resume D05 only after GitHub Actions scheduling materially changes and ordinary hosted jobs begin allocating again, or another already-authorized exact-head hosted execution path becomes executable. Do not repeatedly rerun the same queued jobs merely to change timestamps.

Then:

1. reconcile live `main` and PR #160 again;
2. preserve D04/X03 and resolve only legitimate new conflicts;
3. freeze the exact final executable tree;
4. run fresh applicable full Java 21/Coverage, staff-bot configuration-cache, Sentinel, configured static/review, and canonical Pi gates;
5. merge PR #160 normally only if every required gate is terminal and green and zero valid review threads remain;
6. verify post-merge containment/cleanup;
7. publish ES-D05 `COMPLETE` and stop without beginning D06.

Until that external condition changes, ES-D05 is correctly `BLOCKED` / `PARKED_BLOCKED`; PR #160 and its implementation branch remain preserved and unmerged.
