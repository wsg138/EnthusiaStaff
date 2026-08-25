# Workspace state

Last updated: 2026-08-25

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package record, canonical handoff, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Universal package active state | None after blocker publication. `ES-X04 — EnthusiaCommend reputation provider` is `BLOCKED` / `PARKED_BLOCKED` with both implementation PRs preserved. |
| X04 standalone | `wsg138/EnthusiaCommend` PR #12, branch `package/es-x04-commend-provider`, frozen head `30ac1afbb6b45e958c6972330c42a870d619d530`; open, non-draft, mergeable at last reconciliation. |
| X04 aggregate | Staff PR #152, branch `package/es-x04-commend-provider`, frozen head `9d44bbcac4d3cb9a489e9c9f755e80ae7ace28b1`; open, non-draft, mergeable at last reconciliation. |
| X04 product state | Transactional/versioned reputation moderation provider and Staff sanction projection are implemented. This continuation compacted committed operation snapshot persistence with legacy-read compatibility and fail-closed mismatch tests. Shared standalone↔aggregate product Git objects are synchronized pre-merge; aggregate-only `COMPONENT-METADATA.md` is the expected extra file. |
| X04 review state | Staff PR #152 has zero live inline threads. Commend PR #12's six correctness/data-integrity threads are all resolved. CodeRabbit status is successful on the frozen Staff head. Codacy remains non-passing with 100 reported new static issues (`8` high, `92` medium). |
| X04 standalone validation | PR workflow `32763949487` / job `97549027434` succeeded with Java 21, 110 tests, PMD, and artifact `9533731303`, but raw checkout proves it tested synthetic merge ref `cf6f64d...`, not exact standalone head `30ac1af...`; therefore it is not an exact-head PASS. Package-branch push CI is configured read-only, but an exact push run is not directly inspectable through the connected PR-run listing. |
| X04 Staff hosted validation | Exact-head Coverage/full validation `32763957896` / job `97549217101` PASS on `9d44bbc...`: Java 21 full build/tests including MariaDB/Testcontainers; 27 provider API source types / 0 leaks; Paper SHA-256 `7dd515e21409abb8c8496701e22ced3bdf3e266af8bc5c5bb0e7c52302c1198a`; Velocity SHA-256 `e4c7e48b51a8681eaac5742de96a841462aaeabd74507dcf1c8e1b02faef7586`; JaCoCo 50.50% line / 41.12% branch / 52.93% instruction; artifact `9534065111`, digest `sha256:132df7318d872c0f6e9863bd71fa3f8c69ee72478de742ff1d4f792ebf4fbd2f`; Codacy coverage upload/final notification PASS. |
| X04 Sentinel | Exact-head artifact run `32763957749` / job `97549055756` PASS; artifact `9533760777`, digest `sha256:285503604af4a7d2bd0bde450acf594909490767fdfc433a66e74ae9fe2d6d16`. Exact restart request comment `5400262894` was bound to `9d44bbc...` as durable job `231` and reached terminal `PAPER_RESTART_OK`; Paper reached readiness and stopped cleanly twice against one disposable state. Sentinel does not substitute for canonical Pi. |
| X04 canonical Pi blocker | Required canonical Pi is not verified. Existing automatic `pull_request_target` runs cannot be correlated through the connected commit-workflow listing, and PR #152 does not yet provide stable exact-head public/private run evidence. Independent control-plane fix PR #156 remains open/unmerged at `a1903feaf81cff9d8a151d197fc7efe2b1b855ae`; this worker did not modify or merge it. |
| X04 exact unblock | Obtain directly inspectable exact-head standalone CI; resolve/static-clean Codacy; make the trusted canonical Pi path discoverable/executable and verify exact public/private Pi evidence; reconcile live heads; then merge both implementation PRs normally and verify post-merge parity/metadata/cleanup. |
| Discord program active package | None. `ES-D05 — Staff bot runtime foundation` is `BLOCKED` / `PARKED_BLOCKED` with implementation preserved on PR #160 / `package/es-d05-staff-bot-runtime`. |
| Discord latest completion | `ES-D03 — Authorization and cross-platform policy` is `COMPLETE` through PR #149 on normal merge. Frozen validated merge-ready head: `5cd98a719e30eff64d159f1e219ea70553c66c0`. |
| D04 independent state | D04 remains separate from D05. Staff PR #151 remains open, and independent private staging-control PR `wsg138/EnthusiaStaff-Staging#109` remains open at D05 terminal reconciliation. D05 did not touch or synchronize that work. |
| D05 runtime contract | PR #153 merged normally before D05 claim, recording production app `1541279426233376818`, staging app `1541279616881397772`, guild `1410303324745371709`, and staging test channel `1541286004298752091`; tokens remain secret. |
| D05 product state | Implementation is complete and frozen at product SHA `5f24ba1818c81e0a30a516fa70c8597586184b00`: isolated Java 21 process, JDA 6.5.0 with no privileged Gateway intents, exact application/guild/channel fencing, bounded application work, bounded read-only replay guard, loopback health/readiness, callback generation fencing, privacy-safe logging, graceful/forced shutdown, executable shaded artifact, integrity verification, and non-destructive `--smoke-test`. Existing webhook delivery remains separate. |
| D05 review/static state | Full current-diff review found no remaining functional/security/lifecycle defect after fixing the valid CodeRabbit findings (privileged intent, JSON control escaping, configuration-cache task design) and synchronizing package records. Codacy reports zero new issues, 63.04% diff coverage, and +0.17% coverage variation. CodeRabbit commit status is successful; its generic docstring-coverage warning is not a package correctness gate. |
| D05 hosted validation | Frozen product SHA `5f24ba1...`: Coverage/full validation `32874248685` / job `97888464396` PASS; Staff Bot Configuration Cache `32874248800` / job `97888275507` PASS twice with configuration-cache problems treated as failures; Sentinel Restart Artifact run `32874248693` PASS; validation artifact `9573547679`, digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`; JaCoCo 50.76% line / 41.41% branch / 53.21% instruction. |
| D05 canonical Pi | Public Pi run `32879118794` and correlated private run `32880103099` / job `97907230239` PASS for the exact frozen source on trusted `Lincoln-PI-4`. This is canonical Paper/Pi evidence only and is not treated as the missing Discord connection smoke. |
| D05 blocker | Final acceptance requires a real non-destructive staging Discord connect/disconnect smoke using the staging bot token and proving exact app/guild/test-channel readiness. No authorized secret-bearing execution path accessible to this worker contains `ENTHUSIA_STAFF_BOT_TOKEN`; the current public/private Pi path is Paper-only. The token must not be requested in chat, committed, logged, or placed on a command line. PR #160 remains open and unmerged. |
| D05 exact unblock | Securely provision the staging token to an authorized trusted runtime/secret manager, run the existing `--smoke-test` against exact frozen product source/artifact `5f24ba1818c81e0a30a516fa70c8597586184b00`, record only sanitized exact-provenance app/guild/channel/readiness/disconnect/shutdown evidence, then resume #160, reconcile live state, merge normally if all gates remain satisfied, verify containment/cleanup, and publish `COMPLETE`. |
| Migration state | Canonical `main` is at V19. D05 adds no migration and does not consume D04's unmerged migration work. |
| Independently parked packages | `ES-X01`, `ES-X03`, and X04 remain independently `BLOCKED` / `PARKED_BLOCKED`. D05 does not modify them. |
| Production boundary | No production Discord configuration, bot token, private production data, deployment, migration/import execution, LiteBans authority change, or cutover is authorized or performed. Issue #43 remains open and LiteBans remains authoritative. |
| Universal current handoff | `ai-agents/reports/package-handoffs/2026-08-24-es-x04-commend-provider-blocked.md` |
| Discord current handoff | `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md` |

## X04 parked record

X04 was selected because its existing paired PRs made it the highest-priority universal `ACTIONABLE_CONTINUATION`. The implementation is frozen at Commend `30ac1afbb6b45e958c6972330c42a870d619d530` and Staff `9d44bbcac4d3cb9a489e9c9f755e80ae7ace28b1` while required evidence remains incomplete. The worker repaired one bounded persistence concern, mirrored the change exactly, added focused regression tests, preserved read-only CI permissions, completed exact-head Staff full validation, and obtained exact-head Sentinel restart success.

The package is not merged because validation policy requires all applicable exact reviewed-head evidence. The observable Commend build remains merge-ref-only; Staff Codacy reports unresolved new static issues; and canonical Pi cannot yet be correlated through the current trusted public control plane. Missing or merge-ref-only evidence is explicitly not called a pass.

Resume the existing X04 PRs only when those exact conditions can be resolved and proven. Do not replace the implementation branches or bypass the independent staging-control-plane review.

## D05 parked record

D05 was selected through the dedicated Discord-program lane after D04 was found separately active/blocked on its existing branch and staging-control dependency. The owner-authored D05 identity contract in PR #153 was validated and merged normally first so implementation started from one canonical contract. D05 owns only the staff-bot runtime/build/tests/docs and directly necessary package-state files; it does not absorb D04, website, competition, or global provider work.

The executable implementation is complete and frozen at `5f24ba1818c81e0a30a516fa70c8597586184b00`. All connected exact-head hosted/static/Paper-Pi gates pass. The only remaining D05 acceptance requirement is the real staging Discord `--smoke-test`, and the connected worker has no authorized secret-bearing runtime path containing the staging token. The package is therefore genuinely `BLOCKED` / `PARKED_BLOCKED`, PR #160 is preserved open/unmerged, and the gate is not weakened.

## D04 independent record

D04 remains independent work in Staff PR #151 with private staging-control PR #109 still open at D05 terminal reconciliation. D05 did not edit, rebase, synchronize, merge, or replace either line of work.

## Independent ES-X03 blocker

ES-X03 remains parked on its own recorded blocker. D05 does not alter that package, its provider mirror, or its frozen validation evidence.
