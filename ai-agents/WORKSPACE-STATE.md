# Workspace state

Last updated: 2026-08-26

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package records, canonical handoffs, PRs, and workflow ledgers. This file is a current routing summary, not a replacement for those durable records.

## Current routing

| Field | Value |
| --- | --- |
| Universal package active state | None after the current universal workers stop. `ES-X04 — EnthusiaCommend reputation provider` is `COMPLETE`; `ES-X01` and `ES-X03` remain independently `BLOCKED` / `PARKED_BLOCKED`. |
| X01 current classification | `BLOCKED` / `PARKED_BLOCKED`. Supported `wsg138/Enthusia-RoseChat:master` is verified at `8fcca5420b0f54207d6efa332327b9fd18edb8d8`, but the checked-in provider license excludes the publication/(re)distribution rights required by the current public aggregate-copy/parity policy. No X01 implementation branch/PR or source import exists. |
| X01 exact unblock | Durable verifiable redistribution authorization/license change, or explicitly authorized canonical mirror-policy redesign that removes republication while retaining deterministic supported-source verification. |
| X03 current classification | `BLOCKED` / `PARKED_BLOCKED`. Standalone Market PR #3 merged; Staff PR #139 remains preserved at package-record head `702b13438fd95da235b4a87218901be04999aaea`. Canonical public/private Pi evidence passes for that exact head. Current blocker is safe serialization behind independent D04 because X03 and D04 both carry branch-local V20 migrations and overlap shared Staff files. |
| X04 state | `COMPLETE`; Commend PR #12 and Staff PR #152 merged normally after exact-head build/static/review/Sentinel/Pi gates, containment and standalone↔aggregate parity are exact, and component metadata is `IN_SYNC`. |
| Discord program live work | `ES-D04` remains independently `ACTIVE` on Staff PR #151. `ES-D05` is `BLOCKED` / `PARKED_BLOCKED` on Staff PR #160 after successful live Discord acceptance because mandatory fresh post-reconciliation ordinary hosted validation cannot currently execute. |
| Discord latest completion | `ES-D03 — Authorization and cross-platform policy` remains the latest completed Discord package until D05 actually merges and terminal publication completes. |
| D04 isolation | D04 owns its account-linking/DiscordSRV work, including its unmerged V20 migration. D05 has not edited, synchronized, merged, renumbered, or replaced D04. |
| D05 implementation | Branch `package/es-d05-staff-bot-runtime`, PR #160, current package-record head `6451ede1d6caeeeee19ac16eac86fbbe5570bff5`, preserved and unmerged. Frozen reviewed D05 product source `5f24ba1818c81e0a30a516fa70c8597586184b00`. |
| D05 product state | Isolated Java 21 staff-bot process, JDA 6.5.0 with no privileged Gateway intents, exact application/guild/channel fencing, bounded work/replay, loopback health/readiness, generation fencing, privacy-safe lifecycle behavior, deterministic shutdown, shaded runtime verification, tests/docs, and non-destructive `--smoke-test` are implemented. Existing webhook delivery remains separate. |
| D05 frozen validation | Frozen product `5f24ba1...`: Coverage/full validation `32874248685` / job `97888464396` PASS; Staff Bot Configuration Cache `32874248800` / job `97888275507` PASS twice; Sentinel Restart Artifact `32874248693` PASS; validation artifact `9573547679` digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`; JaCoCo 50.76% line / 41.41% branch / 53.21% instruction; Codacy zero new issues; valid CodeRabbit findings resolved; all live inline threads resolved. |
| D05 prior canonical Pi | Public `32879118794` and correlated private `32880103099` / job `97907230239` PASS for exact frozen source on trusted `Lincoln-PI-4`. This remains attributed only to the frozen Paper/Pi revision. |
| D05 live Discord acceptance | `PASS`: trusted `wsg138/EnthusiaStaff-Staging` run `32926306691`, latest attempt 3 / job `98071453002`, on trusted `Lincoln-PI-4`. Trusted staging-control head `03b3fce61bffe552d7905a4e4aa18e3015ea4e00` pins exact D05 product `5f24ba1818c81e0a30a516fa70c8597586184b00`. Staging application `1541279616881397772`, guild `1410303324745371709`, required channel `1541286004298752091` view/send, readiness, exit 0, and graceful shutdown all passed. No moderation action/test message, Discord configuration change, production-data access, or token exposure occurred. |
| D05 reconciliation | First executable current-main merge: `9c99e78f520cd59e7e59506c37573ac9ad028d63`. Later current-main state/docs reconciliation: `5dee27e700a5fdd0a78121a5fd16c863dac5e0dc`. Current package-record head after state-only validation tracking: `6451ede1d6caeeeee19ac16eac86fbbe5570bff5`. Empty trigger commit `6b12c9ba781cd85075df649d89a3a01e7245d6b7` changed zero files. |
| D05 current blocker | Fresh executable validation after `9c99e78...` is mandatory, but GitHub Actions scheduling is unavailable: observed repository-wide 0 in-progress and 14 queued runs; `main` workflow-dispatch `32984827059` queued since 15:21 UTC; D05 Coverage `32984359237` / `32984371731`, configuration-cache `32984361382`, Sentinel `32984723125`, and Pi runs including `32984459623` / `32984806337` remain queued/non-passing. Validation-only PR #167 on the identical candidate received external integration events but no GitHub Actions run and was closed without merge. |
| D05 exact unblock | Resume only after GitHub Actions scheduling materially changes and ordinary hosted jobs allocate again, or another already-authorized exact-head hosted path becomes executable. Reconcile live `main`, freeze the exact executable tree, run fresh required hosted/static/review/Sentinel/Pi gates, merge #160 normally only if all are green, verify containment, publish `COMPLETE`, and stop. |
| Migration state | Canonical `main` remains at V19. D05 adds no migration. D04 and X03 retain responsibility for their independent branch-local migration work. |
| Independently parked packages | `ES-X01`, `ES-X03`, and `ES-D05` are independently `PARKED_BLOCKED` on their documented external conditions. D04 remains active independently. |
| Production boundary | No production Discord configuration, bot-token value, private production data, deployment, migration/import execution, LiteBans authority change, or cutover is authorized or performed. Issue #43 remains open and LiteBans remains authoritative. |
| Universal current handoff | `ai-agents/reports/package-handoffs/2026-08-26-es-x01-license-redistribution-blocked.md` |
| Discord current handoff | `ai-agents/reports/package-handoffs/2026-08-26-es-d05-hosted-validation-blocked.md` |

## D05 parked record

The prior D05 external acceptance blocker has materially changed: the real non-destructive staging Discord smoke is now successful with exact frozen-product provenance. That acceptance is not the current blocker.

After current-main executable reconciliation, repository policy requires a fresh executable hosted gate set. GitHub Actions currently cannot provide it. The observed repository queue contains no in-progress workflows and fourteen queued workflows, including a `main` workflow-dispatch run and D05 Coverage/configuration-cache/Sentinel/Pi work. Re-running bounded D05 jobs and creating validation-only PR #167 on the identical candidate did not produce usable Actions execution; external integrations did receive the new PR/head events, so this is recorded as an Actions scheduling/infrastructure condition rather than a D05 product defect.

`VALIDATION-POLICY.md` explicitly forbids treating queued/missing checks as passing and forbids using the owner-approved infrastructure exception for a missing ordinary GitHub-hosted build that the repository normally executes. PR #160 therefore remains open and unmerged. Resume only when the Actions scheduling condition materially changes; do not repeatedly rerun identical queued work merely to change timestamps.

D06 is not activated by this publication.
