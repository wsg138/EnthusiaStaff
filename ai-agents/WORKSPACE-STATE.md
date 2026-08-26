# Workspace state

Last updated: 2026-08-26

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package records, canonical handoffs, PRs, and workflow ledgers. This file is a current routing summary, not a replacement for those durable records.

## Current routing

| Field | Value |
| --- | --- |
| Universal lane | No universal package is actively owned by this Discord worker. `ES-X04` is `COMPLETE`; `ES-X01` and `ES-X03` remain independently `BLOCKED` / `PARKED_BLOCKED`. |
| X03 | Staff PR #139 remains preserved. Its current blocker is safe serialization behind independent Discord package D04 because X03 and D04 both carry branch-local V20 migrations and overlap shared Staff integration/persistence files. D05 does not edit or advance X03. |
| X04 | `COMPLETE`; standalone and aggregate implementation PRs merged normally with exact post-merge parity already recorded. |
| Discord program live work | `ES-D04` remains independent on Staff PR #151. `ES-D05` is `MERGE_PENDING` on Staff PR #160 after successful live Discord acceptance and normal current-main reconciliation. |
| Discord latest completion | `ES-D03 — Authorization and cross-platform policy` remains the latest completed Discord package until D05 actually merges and terminal publication completes. |
| D04 isolation | D04 owns its account-linking/DiscordSRV work, including its unmerged V20 migration. D05 has not edited, rebased, merged, renumbered, or replaced D04. |
| D05 frozen product | `5f24ba1818c81e0a30a516fa70c8597586184b00`: isolated Java 21 staff-bot process, JDA 6.5.0 with no privileged Gateway intents, exact application/guild/channel fencing, bounded work/replay, loopback health/readiness, generation fencing, privacy-safe lifecycle behavior, deterministic shutdown, shaded runtime verification, tests/docs, and non-destructive `--smoke-test`. Existing webhook delivery remains separate. |
| D05 frozen hosted validation | Coverage/full validation `32874248685` / job `97888464396` PASS; Staff Bot Configuration Cache `32874248800` / job `97888275507` PASS twice with configuration-cache problems treated as failures; Sentinel Restart Artifact `32874248693` PASS; validation artifact `9573547679`, digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`; JaCoCo 50.76% line / 41.41% branch / 53.21% instruction. Codacy reported zero new issues; all valid CodeRabbit findings were repaired and live inline threads resolved. |
| D05 prior canonical Pi | Public run `32879118794` and correlated private run `32880103099` / job `97907230239` PASS for exact frozen source on trusted `Lincoln-PI-4`. This is Paper/Pi evidence, separate from the Discord smoke. |
| D05 live Discord acceptance | `PASS`: trusted `wsg138/EnthusiaStaff-Staging` run `32926306691`, latest attempt 3 / job `98071453002`, on trusted self-hosted `Lincoln-PI-4` (Linux/ARM64). Trusted staging-control head `03b3fce61bffe552d7905a4e4aa18e3015ea4e00` pins exact D05 product source `5f24ba1818c81e0a30a516fa70c8597586184b00`. Staging application `1541279616881397772`, guild `1410303324745371709`, required channel `1541286004298752091` view/send fence, readiness, smoke exit 0, and graceful close/shutdown all passed. No moderation action/test message, Discord configuration change, production-data access, or token exposure occurred. |
| D05 current-main reconciliation | Canonical `main` `37a2073b535cf32f89b2fc075699dca4e3420408` was merged into PR #160 through ordinary two-parent merge `9c99e78f520cd59e7e59506c37573ac9ad028d63`. Exact diff against that main head is D05-only: staff-bot source/tests/build integration, its config-cache workflow, runtime docs, plus D05 orchestration state. |
| D05 remaining gate | Because canonical `main` gained unrelated executable changes after the frozen D05 validation, fresh applicable exact-head hosted gates are required on the synchronized branch before normal merge. The successful live Discord acceptance remains attributed only to the exact frozen product source it actually executed. |
| Migration state | Canonical `main` remains at V19. D05 adds no migration. D04 and X03 remain responsible for their independent branch-local migration work. |
| Production boundary | No production Discord configuration, bot-token value, private production data, deployment, migration/import execution, LiteBans authority change, or cutover is authorized or performed. Issue #43 remains open and LiteBans remains authoritative. |
| Universal current handoff | `ai-agents/reports/package-handoffs/2026-08-26-es-x03-discord-serialization-blocked.md` |
| Discord current handoff | `ai-agents/reports/package-handoffs/2026-08-26-es-d05-discord-acceptance.md` |

## D05 exact next action

Finish fresh terminal exact-head validation for the reconciled PR #160 head, reconcile live `main` again immediately before merge, merge normally only if every required gate is green and all valid review threads remain resolved, verify exact containment/cleanup, publish ES-D05 `COMPLETE`, and stop without beginning D06.
