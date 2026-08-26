# Workspace state

Last updated: 2026-08-25

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package record, canonical handoff, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Universal package active state | None. `ES-X04 — EnthusiaCommend reputation provider` is `COMPLETE`; `ES-X01` and `ES-X03` remain independently `BLOCKED` / `PARKED_BLOCKED`. |
| X04 standalone | `wsg138/EnthusiaCommend` PR #12 merged normally as `b4a1b57ba918f10ab28d140f9fc0e588a95389c1` after exact reviewed head `325c304512187f274463c31f1649efe0ae56ab7d`. Its fully contained package branch remains safe to delete when an authorized branch-deletion path is available. |
| X04 aggregate | Staff PR #152 merged normally as `e91fc1150a82cc0df081a82bb3dd69714f8bfc14` after exact reviewed head `7ef4b70ed01ad46b925669a0b1378053d8e26789`; the temporary Staff package branch is deleted. |
| X04 product state | Transactional/versioned reputation moderation and the Staff sanction projection are implemented and merged. Post-merge standalone↔aggregate shared Git objects are identical; aggregate-only `COMPONENT-METADATA.md` is the one allowed extra file and records `IN_SYNC`. |
| X04 review state | Commend PR #12's six correctness/data-integrity threads are resolved; Staff PR #152 has zero live inline threads. Exact-head Codacy checks passed with no issues. CodeRabbit status was successful but explicitly skipped automated review, so no automated full-review approval is claimed. |
| X04 standalone validation | Exact-head workflow `32797266212` / job `97651014296` PASS on `325c304...`: Temurin Java 21, Maven `clean verify`, 110 tests with zero failures/errors/skips, PMD, artifact `9545261529` / `sha256:14704bdc74a6ae261226b098e4488dd75ff12152e06b2e962122ce04a153d9bb`, and Codacy with zero annotations. |
| X04 Staff hosted validation | Exact-head Coverage/full validation `32882926827` / job `97916497081` PASS on `7ef4b70...`: Java 21 full build/tests including MariaDB/Testcontainers; 27 provider API source types / 0 leaks; JaCoCo 50.50% line / 41.12% branch / 52.93% instruction; artifact `9576803249`, digest `sha256:8f9e79118425b19ffdd20b685466039edfbd12f41fb56f68d1a4b17528193d00`; Codacy found no issues. |
| X04 Sentinel | Exact-head Sentinel workflow `32882926734` PASS; durable job `250` reached `PAPER_RESTART_OK`. |
| X04 canonical Pi | Public run `32882924737` and correlated private run `32883859152` / job `97919562717` PASS on trusted `Lincoln-PI-4`, including exact artifact verification, guarded disposable Paper boot/restart, durable sanitized evidence, and cleanup. |
| X04 remaining work | No implementation, validation, synchronization, merge, or canonical-publication work remains. Only deletion of the fully contained standalone package branch is pending because the prior connected mutation surface did not expose branch deletion. |
| Discord program live work | `ES-D04` has an independent live continuation on Staff PR #151 owned by another worker. `ES-D05` is `BLOCKED` / `PARKED_BLOCKED` with implementation preserved on PR #160 / `package/es-d05-staff-bot-runtime`. |
| Discord latest completion | `ES-D03 — Authorization and cross-platform policy` is `COMPLETE` through PR #149 on normal merge. Frozen validated merge-ready head: `5cd98a719e30eff64d1595f1e219ea70553c66c0`. |
| D04 independent live state | D04 staging-control prerequisites `wsg138/EnthusiaStaff-Staging#108` and `#109` are merged. Staff PR #151 has resumed independently and is actively being reconciled/reviewed; live GitHub controls its current head and package status. D05 did not edit, synchronize, merge, or replace D04. |
| D05 product state | Implementation is complete and frozen at executable/product SHA `5f24ba1818c81e0a30a516fa70c8597586184b00`: isolated Java 21 process, JDA 6.5.0 with no privileged Gateway intents, exact application/guild/channel fencing, bounded work/replay primitives, loopback health/readiness, callback generation fencing, privacy-safe lifecycle logging, deterministic shutdown, shaded runtime verification, tests/docs, and non-destructive `--smoke-test`. Existing webhook delivery remains separate. |
| D05 review/static state | Harsh current-diff review found no remaining functional/security/lifecycle defect after the valid CodeRabbit findings were fixed; all inline review threads are resolved. Codacy reports zero new issues, 63.04% diff coverage, and +0.17% coverage variation. The generic CodeRabbit docstring warning is not a package correctness gate. |
| D05 hosted validation | Frozen product SHA `5f24ba1...`: Coverage/full validation `32874248685` / job `97888464396` PASS; Staff Bot Configuration Cache `32874248800` / job `97888275507` PASS twice with configuration-cache problems treated as failures; Sentinel Restart Artifact run `32874248693` PASS; validation artifact `9573547679`, digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`; JaCoCo 50.76% line / 41.41% branch / 53.21% instruction. |
| D05 canonical Pi | Public Pi run `32879118794` and correlated private run `32880103099` / job `97907230239` PASS for the exact frozen source on trusted `Lincoln-PI-4`. This is canonical Paper/Pi evidence only and is not treated as the missing Discord connection smoke. |
| D05 blocker | Final acceptance requires a real non-destructive staging Discord connect/disconnect smoke using the staging bot token and proving exact app/guild/test-channel readiness. Fresh search of the current public and private staging-control repositories finds no `ENTHUSIA_STAFF_BOT_TOKEN` or staff-bot smoke execution path. The current public/private Pi path is Paper-only. The token must not be requested in chat, committed, logged, or placed on a command line. PR #160 remains open and unmerged. |
| D05 exact unblock | Securely provision the staging token to an authorized trusted runtime/secret manager, run the existing `--smoke-test` against exact frozen product source/artifact `5f24ba1818c81e0a30a516fa70c8597586184b00`, record only sanitized exact-provenance app/guild/channel/readiness/disconnect/shutdown evidence, then resume #160, reconcile live state, merge normally if all gates remain satisfied, verify containment/cleanup, and publish `COMPLETE`. |
| Migration state | Canonical `main` remains at V19 until D04 merges. D05 adds no migration. |
| Independently parked packages | `ES-X01` and `ES-X03` remain independently `BLOCKED` / `PARKED_BLOCKED` on their own recorded conditions and were not modified by D05. |
| Production boundary | No production Discord configuration, reputation/player rows, bot token, private production data, deployment, migration/import execution, LiteBans authority change, or cutover is authorized or performed. Issue #43 remains open and LiteBans remains authoritative. |
| Universal current handoff | `ai-agents/reports/package-handoffs/2026-08-25-es-x04-commend-provider-complete.md` |
| Discord current handoff | `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md` |

## X04 completed record

X04 completed through normally merged Commend PR #12 and Staff PR #152 after exact-head Java 21, test, static, review, Sentinel, and canonical Pi gates. Post-merge containment and standalone↔aggregate parity are exact, and component metadata records `IN_SYNC`.

The only residual cleanup is the fully contained standalone package branch. It has no unique work and is safe to delete when an authorized branch-deletion path is available; it is not an implementation or validation blocker.

## D05 parked record

D05 was selected through the dedicated Discord-program lane and completed its implementation, review repair, and every connected exact-head hosted/static/Paper-Pi validation gate. The executable product is frozen at `5f24ba1818c81e0a30a516fa70c8597586184b00`; implementation PR #160 remains open with later state-only publication commits.

The only remaining package acceptance requirement is the real staging Discord `--smoke-test`. Fresh reconciliation after staging-control PRs #108/#109 merged still finds no authorized secret-bearing execution path containing the staging bot token. D05 is therefore genuinely `BLOCKED` / `PARKED_BLOCKED`; the missing credential is not requested or exposed and the package gate is not weakened.

## D04 independent continuation

D04 remains independent live work in Staff PR #151. Its staging-control prerequisites #108 and #109 are merged, and another worker is actively advancing that package. D05 did not edit, rebase, synchronize, merge, or replace D04.

## Independent ES-X03 blocker

ES-X03 remains parked on its own recorded blocker. D05 does not alter that package, its provider mirror, or its frozen validation evidence.
