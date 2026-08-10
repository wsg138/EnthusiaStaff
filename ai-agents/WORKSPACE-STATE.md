# Workspace state

Last updated: 2026-08-09

Live GitHub overrides stale records. This file records the current sequential-worker routing snapshot; detailed evidence remains in package records and canonical handoffs.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02` |
| Active/selected package | `ES-V01 — Private LiteBans representative-data verification`; `PARTIAL` / `ACTIONABLE_CONTINUATION` in PR `#110` on `package/es-v01-litebans-private-verification`. |
| Highest-priority next continuation | ES-V01 remains active until PR `#110` completes post-review-fix exact-head validation, substantive review, normal merge, containment, cleanup, and terminal state publication. |
| Ready packages | `ES-P07 — Inventory and Ender editing runtime completion` (`READY`, priority 45) and `ES-P06 — Discord notification delivery completion` (`READY`, priority 60); both remain behind the ES-V01 actionable continuation. |
| Parked provider package | `ES-X01 — RoseChat provider and communication integration`; `BLOCKED` / `PARKED_BLOCKED` because its dependencies are complete but the supported RoseChat standalone repository/default branch/source/AGENTS remain unresolved. |
| ES-P05 implementation | frozen head `9e6d5f8afc120b76f5f396a2e3e279bc5f851c85`; PR #81 merged normally as `52c0dc47efdc2296827b4b6b743d01a86f72c856`; completion record PR #107 merged as `8d5b7dce21bba9c892e8219d0929fa5286aebbcc`; routing correction PR #108 merged as `b59b3aeec7bb6a890b35c944fbdd070c690c9486`. |
| ES-P05 hosted proof | Wiki `31348316809` success; Coverage `31348316817` / job `93334330436` success; validation artifact `9048186426`, digest `sha256:62c9ad1cad86fa2e5189445a9a9ff5f5b6761f1b19d4c25ebacbea5860d4fff9`; Codacy static `93334591193` success with zero annotations; diff coverage 42.96%; coverage variation +0.03%. |
| ES-P05 review state | CodeRabbit exact-head status success; all three historical substantive threads resolved; valid unresolved thread count zero. |
| ES-P05 canonical Pi proof | public run `31348316060`: build `93334328455` success, bridge `93335216891` success; private run `31348651990` / job `93335243975` success on trusted `Lincoln-PI-4`, runner ID `2`. |
| ES-P05 private evidence | exact provenance/freshness PASS; guarded disposable DB pre-reset PASS; two Paper starts; V1–V18 then schema-v18 restart; 2/2 storage-ready cycles in `SHADOW_MIGRATION`; clean shutdown markers; zero failure scans; post-reset PASS; artifact `9048272564`, digest `sha256:55f61484144ef1911e81678ba26d6ca910d915375f0a0425f9d707a4d8904234`. |
| ES-P05 containment/cleanup | implementation head and routing-correction head are fully contained in their normal merge commits with no file delta; GitHub auto-deleted both temporary branches. External parity is not applicable. |
| ES-V01 PR | PR `#110`; branch `package/es-v01-litebans-private-verification`; pre-review exact head `2485c8b7a4a80ae306216eb9f66f1e9415d9eac0`. Valid review fixes intentionally advance the live head; after those fixes, live GitHub defines the frozen exact head. Intended post-merge state is `COMPLETE` only after exact-head gates, zero valid unresolved threads, normal merge, containment, safe branch cleanup, and canonical terminal publication. |
| LiteBans private-evidence routing | Local commit `22934e33` was reproduced as `ea07f55a` on the ES-V01 package branch. Disposable private verification passed dry-run, import, replay, and abandoned-run recovery; the private database stays local. Seven malformed/rejected rows remain a separate data-policy decision. |
| Migration boundary | V18 remains current and immutable; ES-P05 added no migration. The LiteBans UUID-only compatibility repair changes no Flyway migration. |
| Production boundary | issue #43 remains open/deferred; LiteBans remains authoritative; no production data/infrastructure/authority/deployment/shadow/cutover change. |
| Exact next sequential-worker action | Continue only ES-V01 PR `#110`: freeze the post-review-fix head, rerun every invalidated exact-head gate, resolve valid review findings, merge normally if clean, prove containment/cleanup, and publish terminal state. Do not activate ES-P07, ES-P06 or ES-X01 first. |

## ES-P05 terminal result

ES-P05 is complete. The final implementation head passed exact hosted/static/review/canonical public→private Pi proof and PR #81 merged normally. The implementation branch was safely auto-deleted after containment. Documentation-only completion PR #107 and owner-evidence routing correction PR #108 then merged normally.

The routing correction was necessary because owner-provided LiteBans evidence was not available to the concurrent PR #107 finalizer and because live provider reconciliation still shows the RoseChat repository unresolved. No ES-P05 product code, tests, migration, runtime configuration or workflow was changed by the routing correction.

## Owner-provided LiteBans evidence boundary

The supplied private local run used the repository migration service against a private MariaDB 10.11.6 LiteBans copy. It found a UUID-only ban/mute compatibility defect and produced local fix `22934e33`. After the fix, 153 supported sanctions imported and replayed idempotently without duplicate cases/events or mapped timestamp/expiry mismatches. No private database or row data is stored here.

The seven rejected malformed source/history rows remain unresolved policy input before rehearsal; they do not authorize silent skipping or rewriting. Production cutover, issue #43 acceptance, real shadow migration and punishment-authority changes remain prohibited without separate owner authorization.

## Stop boundary

ES-V01 is the sole active package. Continue only PR `#110` through review fixes, final exact-head validation, substantive review, normal merge, containment, safe branch cleanup, and canonical terminal publication. Do not activate, prepare, stage, or partially implement ES-P07, ES-P06, ES-X01, or any other package in this worker.
