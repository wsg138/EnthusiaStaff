# ES-D04 — Account linking and DiscordSRV migration

Status: `BLOCKED` / `PARKED_BLOCKED`. Priority: 133. Depends on `ES-D01`–`ES-D03` (all `COMPLETE`). Internal package; external provider contracts were inspected before implementation.

## Objective
Make EnthusiaStaff the durable Discord↔Minecraft link authority while importing legacy DiscordSRV relationships without forcing relink.

## Scope completed in frozen implementation
Two-direction one-use five-minute link codes; replacement invalidation; verified online-account completion; confirmed unlink; audited staff recovery/reassignment; historical links; PlayTimePlugin public-service active-minute main-account selection with 25% hysteresis and staff override; idempotent DiscordSRV link import; temporary main-link mirroring required for legacy role sync.

## Required safety
One Minecraft UUID cannot have two current Discord owners. Races/replays/restarts fail closed. Never read PlayTimePlugin SQLite directly; if provider active-playtime data is unavailable preserve the existing automatic main instead of guessing zero. Never commit legacy production link data.

The D04 persistence refactor now has one authoritative transactional owner for link/unlink/reassignment. Unlink/reassignment replacement-main planning is validated and committed in the same JDBC transaction as the identity mutation. Staff recovery and main-account override mutations are committed atomically with their audits. Discord link mutations use bounded deadlock retry around the complete transaction.

Validation covers unlinking a Minecraft account and later linking the same UUID to a different Discord account while preserving historical ownership, plus unlinking/reassigning the current main with deterministic valid replacement behavior.

## Exclusions
No bot moderation panel, punishment enforcement, role-sync replacement, AutoMod, production DiscordSRV import execution, production Discord configuration/data, deployment, LiteBans authority change, issue #43 acceptance, or cutover.

## Implementation state
- Claimed: 2026-08-23 through the owner-authorized Discord sequential worker lane.
- Starting `main`: `783925e2b49ab4567bd3c3869e43fc03ff6d285f`.
- Intervening authoritative `main` reconciled through `f129226ac017c97fc4126629dd0f47bff729abd6`.
- Branch: `package/es-d04-account-linking`.
- Implementation PR: #151, open and non-draft.
- Frozen implementation/product head: `b231022b065b5843d2dd73811dfbf51acba6314b`.
- Migration ceiling at claim: V19; frozen D04 adds forward-only `V20__discord_account_linking.sql`.
- DiscordSRV integration uses the public `AccountLinkManager` API only.
- PlayTime integration uses public `PlaytimeService#getLifetime(UUID).activeMinutes` only.
- Issue #43 remains open; LiteBans remains authoritative.

## Review result
Harsh full-diff review and automated review identified and repaired substantive defects including non-atomic main replacement/auditing, async Bukkit access, repeated runtime-graph construction, dynamic SQL review concerns, elapsed-code expiry persistence, and MariaDB deadlock retry. CodeRabbit status is success on the frozen head. The remaining two live inline threads on PR #151 concern stale orchestration records only; the canonical blocked state is published separately on `main` while the implementation head remains frozen for exact-SHA staging recovery.

## Exact-head evidence already passed
Frozen head `b231022b065b5843d2dd73811dfbf51acba6314b`:

- Coverage/full validation run `32738304907`, job `97466391922`: PASS.
- Exact SHA checkout; Temurin Java `21.0.12+8`.
- `clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`: PASS, including MariaDB/Testcontainers integration tests.
- Runtime provider-contract inspection checked 24 source types with 0 leaks.
- Paper runtime SHA-256 `f3f76799987541b6d7db52fb31f48938e79ddd81daa5733a8dc4404a27439d1c`.
- Velocity runtime SHA-256 `8f1d750862506d3cc0e281fbd3f03abff2a8d188770fec1225e771024e21e7a3`.
- Aggregate JaCoCo: 51.09% line / 41.59% branch / 53.44% instruction.
- Validation artifact `9524397425`, digest `sha256:230de565c87f1939dd0f06f2bcb028a394d96e73e43237fb43b2f02adccbd6c8`; Codacy coverage upload/final notification passed.
- Sentinel artifact workflow `32738306003`, job `97466394689`: PASS; artifact `9524138779`, digest `sha256:4f472f5a20c9d825ad7129bbf0bc4727740a4166f9d1c697c843df5b84020b67`.
- Sentinel controller request from PR comment `5395469895`, durable job `225`: terminal `PAPER_RESTART_OK` on the exact frozen head.

## Blocker
Canonical Pi staging is an independent required gate for this Paper/runtime plus MariaDB/Flyway package. No exact D04 Pi PASS is claimed.

The connected GitHub worker can retrieve a workflow only after its run ID is known, but its available commit-workflow listing excludes `pull_request_target` executions. The D04 automatic public `Pi Staging` run and correlated private `wsg138/EnthusiaStaff-Staging` run therefore cannot be discovered or verified from the available connector surface, and no exact D04 public/private Pi run IDs are recorded elsewhere in the repository or PR. Sentinel `PAPER_RESTART_OK` is not a substitute under `VALIDATION-POLICY.md`.

Exact unblock condition: obtain or execute the canonical public `Pi Staging` path for exact source `b231022b065b5843d2dd73811dfbf51acba6314b`; verify the correlated private `Lincoln-PI-4` run and every provenance, MariaDB/Flyway boot/restart, process-reap, guarded database cleanup, sanitized-evidence, and public transfer-cleanup assertion; then reconcile current `main`, resolve the remaining tracking-only review threads, rerun any invalidated applicable state/static checks, merge PR #151 normally, verify containment/cleanup, publish D04 `COMPLETE`, and stop. Missing/stale/different-head evidence is not a pass.

## Routing while parked
`ES-D05 — Staff bot runtime foundation` remains independently `READY`. A future Discord worker may select D05 while D04 is `PARKED_BLOCKED`; this D04 worker did not start it. `ES-D06` and `ES-D13` remain dependency-blocked on both D04 and D05.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d04-account-linking-blocked.md`.
