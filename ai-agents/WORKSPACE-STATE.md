# Workspace state

Last updated: 2026-08-24

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package record, canonical handoff, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Discord program active package | None. `ES-D04 — Account linking and DiscordSRV migration` is `BLOCKED` / `PARKED_BLOCKED` with implementation preserved on `package/es-d04-account-linking`, PR #151, frozen head `b231022b065b5843d2dd73811dfbf51acba6314b`. |
| Discord latest completion | `ES-D03 — Authorization and cross-platform policy` is `COMPLETE` through PR #149 on normal merge. Frozen validated merge-ready head: `5cd98a719e30eff64d1595f1e219ea70553c66c0`. |
| D04 product state | Implementation/review scope is complete at the frozen head: durable two-direction five-minute account linking, V20 persistence/auditing, atomic recovery/main replacement, public PlayTime active-minute main selection, explicit DiscordSRV import and best-effort legacy mirroring, Paper `/link` and `/unlink`, concurrency/replay/restart coverage. |
| D04 hosted validation | Coverage/full validation `32738304907` / job `97466391922` PASS on exact frozen SHA with Temurin Java 21.0.12+8, full build, MariaDB/Testcontainers, runtime JARs, 24 provider-contract source types / 0 leaks, JaCoCo 51.09% line / 41.59% branch / 53.44% instruction, artifact `9524397425` digest `sha256:230de565c87f1939dd0f06f2bcb028a394d96e73e43237fb43b2f02adccbd6c8`, and successful Codacy coverage/final notification. |
| D04 Sentinel | Artifact run `32738306003` / job `97466394689` PASS on exact frozen SHA; artifact `9524138779`, digest `sha256:4f472f5a20c9d825ad7129bbf0bc4727740a4166f9d1c697c843df5b84020b67`. Sentinel request comment `5395469895`, durable job `225`, terminal `PAPER_RESTART_OK`. |
| D04 blocker | Required canonical Pi staging is not verified. The connected GitHub workflow surface cannot list/discover the PR's automatic `pull_request_target` Pi run, and no exact D04 public/private Pi run IDs are recorded elsewhere. Sentinel is independent and is not substituted. Unblock by obtaining/executing canonical Pi for exact source `b231022b065b5843d2dd73811dfbf51acba6314b` and verifying the correlated private `Lincoln-PI-4` run plus every runtime/provenance/restart/cleanup assertion. |
| D04 review state | All substantive code findings are repaired and CodeRabbit status is success on the frozen head. Two live inline threads remain tracking-only stale-record findings on the frozen implementation PR; canonical state is corrected by the status-publication record while the implementation head is intentionally not moved before exact-SHA Pi recovery. |
| Ready Discord work | `ES-D05 — Staff bot runtime foundation` remains `READY`. A future Discord worker may select it while D04 stays `PARKED_BLOCKED`; this D04 worker did not start it. |
| Migration state | Canonical `main` remains at V19 until D04 merges. Frozen D04 adds only forward `V20__discord_account_linking.sql`; no production migration was executed. |
| Collision reconciliation | Canonical `main` used for status publication is `f129226ac017c97fc4126629dd0f47bff729abd6`. D04 PR #151 contains no website/competition implementation. Concurrent ES-X04 and D05 work remain independent and are not absorbed or overwritten. |
| Independently parked package | `ES-X03 — EnthusiaMarket destructive provider` remains separately `BLOCKED` / `PARKED_BLOCKED` on its own runtime-validation condition and is not modified by D04. |
| Production boundary | No production Discord configuration, bot token, player/private production data, deployment, production migration/import execution, LiteBans authority change, or cutover is authorized or performed. Issue #43 remains open and LiteBans remains authoritative. |
| Current handoff | `ai-agents/reports/package-handoffs/2026-08-24-es-d04-account-linking-blocked.md` |

## D04 parked record

D04 was selected through the dedicated owner-authorized Discord lane and resumed as the existing actionable continuation in PR #151. The branch reconciled intervening `main` hardening through `f129226ac017c97fc4126629dd0f47bff729abd6` without absorbing unrelated work. The implementation is frozen at `b231022b065b5843d2dd73811dfbf51acba6314b` because the remaining blocker is exact-head canonical Pi evidence; changing the implementation head solely to update routing records would make that evidence target less stable.

The repository policy requires canonical Pi for this changed Paper runtime plus MariaDB/Flyway scope. Missing evidence is explicitly not called a pass. Resume D04 only when the exact blocker condition changes: the worker can identify/execute and inspect the canonical public-to-private Pi path for the frozen exact source.

## Independent ES-X03 blocker

ES-X03 remains parked on its own recorded blocker. D04 does not alter that package, its provider mirror, or its frozen validation evidence.
