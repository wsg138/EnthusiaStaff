# ES-D05 staff bot runtime foundation — Discord acceptance checkpoint

Date: 2026-08-26
Status: `MERGE_PENDING`
Classification: owner-authorized actionable continuation after external blocker cleared
Package: `ES-D05 — Staff bot runtime foundation`
Implementation PR: #160
Implementation branch: `package/es-d05-staff-bot-runtime`

## Reconciled state

- Original claim base: `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`.
- Frozen reviewed D05 product source: `5f24ba1818c81e0a30a516fa70c8597586184b00`.
- First executable current-main reconciliation: canonical `main` `37a2073b535cf32f89b2fc075699dca4e3420408`, ordinary two-parent merge `9c99e78f520cd59e7e59506c37573ac9ad028d63`.
- Latest canonical `main` before final gate freeze: `bea28469385945dae818824064ca9bc2c15f56fd`, whose advance from `37a207...` is Wiki documentation only.
- Latest ordinary two-parent documentation-only reconciliation merge: `a0d37e82957ca1318907f538084eb7e85bc7de95`.
- The exact delta from executable synchronization head `9c99e78...` through `a0d37e8...` contains only D05 orchestration/handoff state plus `docs/wiki/pages/**`; no executable, build, workflow, dependency, migration, test, or runtime input changed.
- Current-main migration boundary: V19; D05 adds no migration and does not consume D04's V20.
- D04 remains independent on PR #151. X03, provider, production Discord, LiteBans, and issue #43 authority are unchanged.

## Successful live Discord acceptance

The previously genuine external D05 acceptance blocker is cleared by trusted staging evidence.

- Staging repository: `wsg138/EnthusiaStaff-Staging`.
- Actions run: `32926306691`.
- Latest run attempt: 3.
- Job: `98071453002` — `Authenticate staging bot and verify Discord readiness fence`.
- Trusted runner: `Lincoln-PI-4`, self-hosted Linux/ARM64, runner ID 2.
- Trusted staging-control head: `03b3fce61bffe552d7905a4e4aa18e3015ea4e00`.
- Workflow pins source repository `wsg138/EnthusiaStaff`, D05 branch `package/es-d05-staff-bot-runtime`, and exact source SHA `5f24ba1818c81e0a30a516fa70c8597586184b00`.
- Exact-source fetch, Java 21 setup/requirement, staff-bot build/integrity verification, non-destructive live smoke, and sanitized PASS publication all completed successfully.

Sanitized live acceptance facts:

- staging application ID `1541279616881397772`: `PASS`;
- Enthusia guild ID `1410303324745371709`: `PASS`;
- required staging test channel `1541286004298752091`: `PASS` for view/send permission fence;
- readiness fence: `PASS`;
- non-destructive smoke process exit: `0`;
- graceful close/shutdown path: `PASS`.

The smoke sends no moderation action or test message and changes no Discord configuration. It does not access production moderation data or use the production bot identity. No Discord bot-token value was requested, inspected, exposed, logged, committed, copied into this handoff, or placed on a command line.

## Existing frozen-product gates

For exact frozen product `5f24ba1818c81e0a30a516fa70c8597586184b00`:

- Coverage/full Java 21 validation `32874248685` / job `97888464396`: `SUCCESS`.
- Staff Bot Configuration Cache `32874248800` / job `97888275507`: `SUCCESS` twice with configuration-cache problems treated as failures.
- Sentinel Restart Artifact `32874248693`: `SUCCESS`.
- Validation artifact `9573547679`, digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`.
- Aggregate JaCoCo: 50.76% lines, 41.41% branches, 53.21% instructions.
- Codacy: zero new issues, 63.04% diff coverage, +0.17% variation.
- CodeRabbit: successful after valid findings were repaired; all live inline review threads resolved.
- Canonical Pi public `32879118794` and correlated private `32880103099` / job `97907230239`: `SUCCESS` for the repository Paper/Pi gate.

The real Discord smoke is separate acceptance evidence and is attributed only to the exact product source it executed.

## Current-main reconciliation and validation rule

Current `main` advanced after the D05 freeze with legitimate unrelated executable work. PR #160 was first reconciled using normal merge history rather than rebase/force/squash. The exact diff of executable reconciliation head `9c99e78f520cd59e7e59506c37573ac9ad028d63` against then-current `main` is D05-only: staff-bot source/tests/build integration, staff-bot configuration-cache workflow, and runtime documentation. No D04 migration/shared-file work or X03/provider work was absorbed.

A later concurrent Wiki-only merge advanced canonical `main` to `bea28469385945dae818824064ca9bc2c15f56fd`. That change was reconciled normally through `a0d37e82957ca1318907f538084eb7e85bc7de95`. Comparison from `9c99e78...` through `a0d37e8...` proves the later delta is state/documentation only, so executable evidence on an exact descendant with the same executable tree may be reused under the explicit state-only follow-up exception in `VALIDATION-POLICY.md`. Stale runs bound to superseded PR heads remain non-passing evidence.

## Exact next action

1. Obtain fresh terminal hosted validation for the synchronized D05 executable tree, including the full Java 21 repository gate, staff-bot configuration-cache gate, Sentinel/runtime artifact gate, configured static/review checks, and canonical Pi where current policy/configuration requires it.
2. Reconcile live `main` again immediately before merge and ensure no new collision exists.
3. Merge PR #160 normally only with every required gate green and no valid unresolved review thread.
4. Verify merge containment and absence of unrelated D04/X03/production changes.
5. Publish ES-D05 `COMPLETE` in canonical registries/workspace/package/handoff and stop without starting D06.
