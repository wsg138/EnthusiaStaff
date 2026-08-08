# EnthusiaStaff workspace state

Last updated: 2026-08-08

Live GitHub state overrides stale records, but persistent package state must be reconciled here.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P03`, `ES-P04`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Active/selected package | `ES-R01 — Billing-independent staging bridge recovery`, `IN_PROGRESS` / `VERIFYING`. This worker may work no second package. |
| Current legitimate EnthusiaStaff main | `094838fa221476e0832cf821f7b4908b9402d0d9` — normal merge of ES-R01 public bridge PR #93 |
| Current legitimate staging main | `4036d6e915c2d751bef18849107722dfd1e586a6` — normal merge of ES-R01 PR-target provenance fix PR #60 |
| ES-R01 public implementation | PR #93, frozen head `cccadbd1885f78db517ff643f941d04bd0fba2a3`, merged normally as `094838fa221476e0832cf821f7b4908b9402d0d9` |
| ES-R01 private implementation | PR #58 → `570f83e41cb80b498a82c8b5a509c42345558a46`; PR #59 → `313ed2815058eadeb8c823453f4152089cae01d4`; PR #60 → `4036d6e915c2d751bef18849107722dfd1e586a6` |
| ES-R01 current checkpoint | PR #94, `package/es-r01-proof-retry-checkpoint`; documentation/package-state only; after normal merge it must trigger the fresh current-`main` bridge proof that determines terminal package status |
| Active ES-R01 handoff | `ai-agents/reports/package-handoffs/2026-08-08-es-r01-pr-target-provenance-correction.md` |
| Database-gate evidence | `ai-agents/reports/package-handoffs/2026-08-08-es-r01-blocked-staging-database.md` |
| Observed ES-R01 gate | The existing authorized disposable Pi-staging MariaDB endpoint has not accepted connections from `Lincoln-PI-4`; the strongest prior proof exhausted bounded SQLState class `08xxx` retries before Paper boot. This is blocked-gate evidence, not yet terminal package classification. |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70; do not synchronize or rerun while ES-R01 is unfinished |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81; do not synchronize or rerun while ES-R01 is unfinished |
| Migration boundary | V18 remains immutable/current; ES-R01 changed no migration |
| Production boundary | issue #43 remains open/deferred; LiteBans remains authoritative |
| Next mandatory action | Complete PR #94 exact-head validation/review, merge it normally, verify containment, then inspect the automatic fresh current-`main` Pi Staging proof. Publish terminal `COMPLETE` or `BLOCKED` only from that post-merge evidence, using a small documentation-only finalization PR when necessary. |

## ES-R01 reconciliation and implementation record

- The worker selected ES-R01 as the only legitimate continuation after reconciling live GitHub and confirming ES-P02 and ES-P05 remained parked.
- The original private-hosted `ubuntu-latest` billing deadlock was removed by repository-side bridge work rather than waived.
- Public PR #93 now performs exact-source authorization, Java 21 hosted build/validation, immutable runtime/provenance packaging, bounded transient transfer, private dispatch/correlation, verdict propagation, and cleanup.
- Private PR #58 removed the private GitHub-hosted build prerequisite, verifies public source/run/release/asset/manifest/runtime identity, and keeps boot/restart on trusted self-hosted `Lincoln-PI-4`.
- Private PR #59 added bounded readiness retries only for connection-level SQLState class `08xxx`; the guarded disposable reset still must succeed before Paper may boot.
- Private PR #60 corrected `pull_request_target` provenance binding and fail-path cleanup without weakening same-repository/main-base trust.

## Live end-to-end evidence before the required post-#94-merge proof

### Merged-main proof

Public run `31249125885` at `094838fa221476e0832cf821f7b4908b9402d0d9`:

- public hosted build job `93082543002` succeeded;
- private run `31249402654`, job `93083246690`, allocated `Lincoln-PI-4` runner ID `2`;
- exact public artifact/run/source provenance verification passed;
- disposable database pre-reset returned SQLState `08000` before Paper boot;
- transient release/tag cleanup succeeded.

### Corrected PR-target proof

Public run `31250170297` for PR #94 head `4acb4853c5ce00805ff206e3d0bb28a2458e82c8`:

- public hosted build job `93085175893` succeeded;
- private run `31250450219`, job `93085892938`, allocated `Lincoln-PI-4` runner ID `2`;
- corrected PR-target provenance verification passed against trusted base/workflow SHA `094838fa221476e0832cf821f7b4908b9402d0d9`;
- seven total guarded MariaDB connection attempts all returned SQLState `08000`, with the bounded retry result exhausted;
- Paper never booted, as required by the fail-closed boundary;
- sanitized evidence artifact `9019842260` was uploaded;
- public transient release/tag cleanup succeeded.

PR #94 head `4eac5351abe0e5eb6e7053811f6bb3deaa85d884` also completed hosted build/coverage and Codacy checks successfully and again reached `Lincoln-PI-4`; its automatic Pi bridge failed at the guarded disposable Paper boot/restart step and uploaded sanitized evidence. CodeRabbit then identified a valid sequencing defect in the package-state documents: terminal classification must wait for the post-merge current-main proof. This checkpoint update fixes that process defect before the final PR #94 freeze.

## Classification snapshot

- `ES-R01`: `IN_PROGRESS` / `VERIFYING` `ACTIONABLE_CONTINUATION`. Repository implementation is merged. The database failure is a blocked gate, but terminal `BLOCKED` / `PARKED_BLOCKED` is deferred until the required post-PR-#94-merge current-main proof is inspected.
- `ES-P02`: `PARKED_BLOCKED`. Its open branch/PR and drift behind `main` do not make it actionable. Resume only after ES-R01 reaches terminal package state and routing permits it.
- `ES-P05`: `PARKED_BLOCKED`. Its implementation/hosted validation remains preserved; resume only after ES-R01 reaches terminal package state and normal continuation priority permits it.
- `ES-P07`, `ES-P06`, `ES-P08`, `ES-X01`, `ES-X02`, `ES-X03`, `ES-X04`, and `ES-QA01`: dependency-blocked planned work.
- `ES-V01`, `ES-V02`, `ES-V03`, and `ES-A01`: deferred private/acceptance work under their existing contracts. Issue #43 is still open and does not authorize production cutover.

## Post-merge decision boundary

After PR #94 merges normally, do not select another package. Inspect the automatic current-`main` Pi Staging run produced by that merge. If it passes the full bridge and two-cycle boot/restart/reset acceptance, ES-R01 can be marked `COMPLETE`. If it again fails only because the existing authorized disposable staging MariaDB endpoint is unreachable from `Lincoln-PI-4`, classify ES-R01 `BLOCKED` / `PARKED_BLOCKED` and publish that current-main evidence through the smallest documentation-only finalization PR needed to make `main` canonical. Do not manually rerun an identical failure absent a later material condition change.

## Safety boundaries

No production data/configuration, credentials, punishment/player records, raw addresses, private databases, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration/cutover, or ES-V02 execution is authorized by this checkpoint publication.
