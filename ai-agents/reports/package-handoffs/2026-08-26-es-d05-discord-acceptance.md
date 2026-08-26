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
- Current canonical `main` at final validation freeze: `592778acc3c77f834359732e16ff12b7b1e881d4`.
- Latest ordinary two-parent reconciliation merge: `5dee27e700a5fdd0a78121a5fd16c863dac5e0dc`.
- The exact delta from executable synchronization head `9c99e78...` through `5dee27e...` contains only D05 orchestration/handoff state, Wiki documentation, X01 orchestration/package metadata, and component metadata. No executable, build, workflow, dependency, migration, test, or runtime input changed.
- Empty validation-trigger commit `6b12c9ba781cd85075df649d89a3a01e7245d6b7` has exactly the same tree as its parent `666b37acc3aa88a20e807927ac6a4148b1f2d24a`; exact compare reports zero changed files.
- Validation-only PR #167 exists solely to obtain repository-configured gates on the identical implementation candidate under a fresh PR concurrency key. It must never merge; implementation remains PR #160. This handoff-only update is the only content delta after the empty trigger commit and is non-executable under the state-only follow-up rule.
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

Later concurrent `main` advances through `592778acc3c77f834359732e16ff12b7b1e881d4` were Wiki documentation and orchestration/component metadata only. They were reconciled through ordinary two-parent history, ending at `5dee27e700a5fdd0a78121a5fd16c863dac5e0dc`. Comparison from `9c99e78...` through `5dee27e...` proves the later delta is state/documentation/metadata only, so executable evidence on an exact descendant with the same executable tree may be reused under the explicit state-only follow-up exception in `VALIDATION-POLICY.md`.

Several intermediate workflow records are intentionally non-passing and remain historical only: configuration-cache run `32984361382` was cancelled and its retry remained queued; Pi run `32984459623` had its executable/public-start jobs cancelled by later synchronization and its retry remained queued; Sentinel intermediate runs did not execute their queued build jobs before their superseding synchronization. Earlier fail-closed Pi runs also rejected superseded exact PR heads. None of those records is promoted to passing evidence and none identified a D05 executable defect. Coverage runs `32984359237` and `32984371731` likewise remained queued without jobs. Validation-only PR #167 is the bounded attempt to obtain a fresh concurrency key without changing executable content.

## Exact next action

1. Obtain fresh terminal hosted validation for the final D05 executable tree, including the full Java 21 repository gate, staff-bot configuration-cache gate, Sentinel/runtime artifact gate, configured static/review checks, and canonical Pi where current policy/configuration requires it.
2. Reconcile live `main` again immediately before merge and ensure no new collision exists.
3. Merge PR #160 normally only with every required gate green and no valid unresolved review thread.
4. Verify merge containment and absence of unrelated D04/X03/production changes.
5. Publish ES-D05 `COMPLETE` in canonical registries/workspace/package/handoff and stop without starting D06.
