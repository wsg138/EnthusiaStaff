# ES-D05 staff bot runtime foundation — hosted validation blocked

Date: 2026-08-26
Status: `BLOCKED` / `PARKED_BLOCKED`
Package: `ES-D05 — Staff bot runtime foundation`
Implementation PR: #160
Implementation branch: `package/es-d05-staff-bot-runtime`
Current implementation/package-record head: `6451ede1d6caeeeee19ac16eac86fbbe5570bff5`
Frozen reviewed D05 product source: `5f24ba1818c81e0a30a516fa70c8597586184b00`

## What changed

The prior external Discord-acceptance blocker is cleared. Trusted `wsg138/EnthusiaStaff-Staging` run `32926306691`, latest attempt 3 / job `98071453002`, executed the exact frozen D05 product SHA `5f24ba1818c81e0a30a516fa70c8597586184b00` on trusted self-hosted `Lincoln-PI-4` and passed the non-destructive `--smoke-test`.

Sanitized acceptance evidence: staging application `1541279616881397772` PASS; Enthusia guild `1410303324745371709` PASS; required staging test channel `1541286004298752091` PASS for view/send; readiness PASS; smoke exit 0; graceful close/shutdown PASS. The run sent no moderation action or test message, changed no Discord configuration, accessed no production moderation data, and exposed no bot-token value.

PR #160 was then reconciled with current-main executable state using normal merge history. The first executable reconciliation was two-parent merge `9c99e78f520cd59e7e59506c37573ac9ad028d63`. Later `main` advances were documentation/orchestration/component-metadata only and were reconciled normally through `5dee27e700a5fdd0a78121a5fd16c863dac5e0dc`. Current package-record head `6451ede1d6caeeeee19ac16eac86fbbe5570bff5` adds only D05 handoff/orchestration tracking after that executable reconciliation. Empty trigger commit `6b12c9ba781cd85075df649d89a3a01e7245d6b7` had exactly zero changed files from its parent.

D04 remains independent on Staff PR #151. D05 adds no Flyway migration and does not consume D04's branch-local V20. X03/provider/website/production Discord/LiteBans/issue #43 authority remain untouched.

## Required live Discord acceptance — PASS

- Staging repository: `wsg138/EnthusiaStaff-Staging`.
- Run: `32926306691`, latest attempt 3.
- Job: `98071453002`.
- Trusted staging-control head: `03b3fce61bffe552d7905a4e4aa18e3015ea4e00`.
- Exact D05 source: `5f24ba1818c81e0a30a516fa70c8597586184b00`.
- Java 21 exact-source build/integrity before secret scope: PASS.
- Staging application/guild/channel/readiness/exit/graceful shutdown: PASS.

No Discord bot token was requested, inspected, logged, committed, copied into tracking text, modified, or placed on a command line during this continuation.

## Existing frozen-product evidence

For exact frozen product `5f24ba1818c81e0a30a516fa70c8597586184b00`:

- Coverage/full Java 21 validation `32874248685` / job `97888464396`: SUCCESS.
- Staff Bot Configuration Cache `32874248800` / job `97888275507`: SUCCESS twice with configuration-cache problems treated as failures.
- Sentinel Restart Artifact `32874248693`: SUCCESS.
- Validation artifact `9573547679`, digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`.
- Aggregate JaCoCo: 50.76% lines / 41.41% branches / 53.21% instructions.
- Codacy: zero new issues, 63.04% diff coverage, +0.17% coverage variation.
- CodeRabbit: success after valid findings were fixed; all live inline threads resolved.
- Canonical Pi public `32879118794` and correlated private `32880103099` / job `97907230239`: SUCCESS for the frozen Paper/Pi gate.

These executable results remain attributed only to the exact revisions that executed them. They are not used to waive the fresh post-reconciliation executable gate required after `main` introduced unrelated executable changes.

## Current hard blocker — GitHub Actions scheduling

Fresh executable validation after `9c99e78...` is mandatory under `VALIDATION-POLICY.md`. The ordinary hosted gate cannot be waived or called passing when queued/missing.

The current repository Actions scheduler is not executing queued work:

- repository-wide `in_progress` workflow count observed during this continuation: `0`;
- repository-wide queued workflow count: `14`;
- even `main` workflow-dispatch run `32984827059` has remained queued since 2026-08-26 15:21 UTC;
- D05 Coverage runs `32984359237` and `32984371731` remain queued with no jobs;
- Staff Bot Configuration Cache run `32984361382` was retried but remains queued with no jobs;
- Sentinel run `32984723125` has a queued build job and has not produced executable evidence;
- Pi runs including `32984459623` and `32984806337` remain queued/non-passing.

A bounded workaround used validation-only PR #167 on the identical implementation candidate SHA with a fresh PR concurrency key. GitHub integrations received its events (Codacy created an exact-head analysis), but GitHub Actions created no workflow run for the exact validation head. An empty commit with an identical tree and a later handoff-only commit were also used to rule out commit de-duplication; Actions still created no exact-head runs. PR #167 was closed without merge and must remain non-implementation history.

This is infrastructure-unavailable evidence, not a D05 product failure. It still cannot authorize merge because `VALIDATION-POLICY.md` explicitly says queued or missing checks are not passing evidence and the owner-approved infrastructure exception may not excuse a missing ordinary GitHub-hosted build that the repository normally executes.

## Exact unblock

Resume ES-D05 only after GitHub Actions scheduling materially changes — for example, the repository queue begins allocating ordinary GitHub-hosted jobs or an authorized workflow-dispatch path becomes executable. Do not repeatedly rerun the same queued jobs merely to change timestamps.

Then:

1. reconcile live `main` and PR #160 again;
2. preserve D04/X03 and resolve only legitimate new state conflicts;
3. freeze the exact final executable tree;
4. run fresh applicable full Java 21/Coverage, staff-bot configuration-cache, Sentinel, configured static/review, and canonical Pi gates on that executable tree;
5. merge PR #160 normally only if every required gate is terminal and green and zero valid review threads remain;
6. verify post-merge containment/cleanup;
7. publish ES-D05 `COMPLETE` and stop without beginning D06.

Until that condition changes, ES-D05 is correctly `BLOCKED` / `PARKED_BLOCKED`. The implementation PR and branch remain preserved and unmerged.
