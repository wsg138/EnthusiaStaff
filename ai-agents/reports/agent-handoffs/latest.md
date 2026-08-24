# Latest package-worker handoff

Current package: `ES-D04 — Account linking and DiscordSRV migration`.

Status: `BLOCKED` / `PARKED_BLOCKED`.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d04-account-linking-blocked.md`.

Implementation is preserved and unmerged:
- Branch: `package/es-d04-account-linking`.
- PR: #151, open and non-draft.
- Frozen product head: `b231022b065b5843d2dd73811dfbf51acba6314b`.
- Reconciled canonical `main` before status publication: `f129226ac017c97fc4126629dd0f47bff729abd6`.

D04 product scope is implemented at the frozen head. It includes one-use two-direction five-minute codes, durable history and replacement invalidation, online Minecraft proof, confirmed unlink, atomic audited staff recovery/main-account overrides, 25% active-playtime main selection through the public PlayTimePlugin service, explicit idempotent DiscordSRV public-API import, best-effort main mirroring, Paper `/link`/`/unlink`, and forward V20 persistence.

Exact-head hosted validation is green: Coverage/full run `32738304907`, job `97466391922`, passed Java 21 full build/tests, MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, aggregate JaCoCo and Codacy; artifact `9524397425`, digest `sha256:230de565c87f1939dd0f06f2bcb028a394d96e73e43237fb43b2f02adccbd6c8`. Sentinel artifact run `32738306003`, job `97466394689`, passed with artifact `9524138779`, digest `sha256:4f472f5a20c9d825ad7129bbf0bc4727740a4166f9d1c697c843df5b84020b67`. Sentinel durable job `225` ended `PAPER_RESTART_OK` on the same exact SHA.

The blocker is the separate required canonical Pi staging gate. The connected GitHub worker can fetch known workflow runs but cannot list the `pull_request_target` executions needed to discover PR #151's automatic Pi run, and no exact D04 public/private Pi run identifiers are recorded elsewhere. No Pi pass is claimed; Sentinel does not substitute for canonical MariaDB/Flyway staging.

Resume only when the exact blocker changes: obtain or execute canonical public `Pi Staging` for source `b231022b065b5843d2dd73811dfbf51acba6314b`, verify the correlated private `wsg138/EnthusiaStaff-Staging` run on `Lincoln-PI-4` and every provenance/runtime/restart/cleanup assertion, then reconcile current `main`, resolve the two remaining tracking-only PR threads, run any newly applicable state/static checks, merge #151 normally, verify containment/cleanup, and publish D04 `COMPLETE`.

`ES-D05 — Staff bot runtime foundation` remains independently `READY` while D04 is parked. This worker did not start it. Concurrent website, competition, ES-X03, ES-X04 and D05 work was not absorbed or overwritten. Issue #43 remains open and LiteBans remains authoritative.
