# Workspace state

Last updated: 2026-08-24

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
| X04 Sentinel | Exact-head artifact run `32763957749` / job `97549055756` PASS; artifact `9533760777`, digest `sha256:285503604af4a7d2bd0bde450acf594909490767fdfc433a66e74ae9fe2d6d16`. Exact restart request comment `5400262894` is bound to `9d44bbc...` as durable job `231`; latest verified state is `QUEUED` / `AUTHORIZED`, so no runtime PASS is claimed. |
| X04 canonical Pi blocker | Required canonical Pi is not verified. Existing automatic `pull_request_target` runs cannot be correlated through the connected commit-workflow listing, and PR #152 does not yet provide stable exact-head public/private run evidence. Independent control-plane fix PR #156 remains open/unmerged at `e822df6ded598f74776858afab36a2768c7d7c95`; this worker did not modify or merge it. |
| X04 exact unblock | Obtain directly inspectable exact-head standalone CI; resolve/static-clean Codacy; require terminal exact-head Sentinel `PAPER_RESTART_OK` if still applicable; make the trusted canonical Pi path discoverable/executable and verify exact public/private Pi evidence; reconcile live heads; then merge both implementation PRs normally and verify post-merge parity/metadata/cleanup. |
| Discord program active package | None in canonical `main` state. `ES-D04 — Account linking and DiscordSRV migration` remains `BLOCKED` / `PARKED_BLOCKED` with implementation preserved on `package/es-d04-account-linking`, PR #151, frozen head `b231022b065b5843d2dd73811dfbf51acba6314b`. |
| Discord latest completion | `ES-D03 — Authorization and cross-platform policy` is `COMPLETE` through PR #149 on normal merge. Frozen validated merge-ready head: `5cd98a719e30eff64d159f1e219ea70553c66c0`. |
| D04 product state | Implementation/review scope is complete at the frozen head: durable two-direction five-minute account linking, V20 persistence/auditing, atomic recovery/main replacement, public PlayTime active-minute main selection, explicit DiscordSRV import and best-effort legacy mirroring, Paper `/link` and `/unlink`, concurrency/replay/restart coverage. |
| D04 hosted validation | Coverage/full validation `32738304907` / job `97466391922` PASS on exact frozen SHA with Java 21, MariaDB/Testcontainers, runtime JARs, provider-contract inspection, JaCoCo and Codacy coverage; Sentinel durable job `225` ended `PAPER_RESTART_OK`. |
| D04 blocker | Required canonical Pi staging is not verified; D04 remains independently parked on its exact frozen source. |
| Ready Discord work | `ES-D05 — Staff bot runtime foundation` remains independently available through its dedicated Discord-program registry/lane; concurrent Discord work was not modified by X04. |
| Migration state | Canonical `main` remains at V19 until D04 merges. X04 adds no production migration execution. |
| Collision reconciliation | Blocker-publication branch starts exactly from canonical `main` `cb19463f16e124564ccbc17034b4c18f5cd0281f`. X04 did not absorb D04, D05, website, or staging-control-plane PR #156 work. |
| Independently parked packages | `ES-X01` and `ES-X03` remain independently `BLOCKED` / `PARKED_BLOCKED` on their own recorded conditions and were not modified by X04. |
| Production boundary | No production Discord configuration, reputation/player rows, bot token, private production data, deployment, migration/import execution, LiteBans authority change, or cutover is authorized or performed. Issue #43 remains open and LiteBans remains authoritative. |
| Current handoff | `ai-agents/reports/package-handoffs/2026-08-24-es-x04-commend-provider-blocked.md` |

## X04 parked record

X04 was selected because its existing paired PRs made it the highest-priority universal `ACTIONABLE_CONTINUATION`. The implementation is frozen at Commend `30ac1afbb6b45e958c6972330c42a870d619d530` and Staff `9d44bbcac4d3cb9a489e9c9f755e80ae7ace28b1` while required evidence remains incomplete. The worker repaired one bounded persistence concern, mirrored the change exactly, added focused regression coverage, preserved read-only CI permissions, and completed exact-head Staff full validation.

The package is not merged because validation policy requires all applicable exact reviewed-head evidence. The observable Commend build remains merge-ref-only; Staff Codacy reports unresolved new static issues; exact frozen-head Sentinel restart is queued rather than passed; and canonical Pi cannot yet be correlated through the current trusted public control plane. Missing, queued, or merge-ref-only evidence is explicitly not called a pass.

Resume the existing X04 PRs only when those exact conditions can be resolved and proven. Do not replace the implementation branches or bypass the independent staging-control-plane review.

## D04 parked record

D04 remains independently parked at frozen head `b231022b065b5843d2dd73811dfbf51acba6314b` on its own exact-head canonical Pi evidence condition. X04 did not change D04 product or routing state.

## Independent ES-X03 blocker

ES-X03 remains parked on its own recorded blocker. X04 does not alter that package, its provider mirror, or its frozen validation evidence.
