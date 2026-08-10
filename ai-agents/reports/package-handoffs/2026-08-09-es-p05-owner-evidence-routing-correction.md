# ES-P05 post-merge routing correction handoff

Date: 2026-08-09

## Scope

This is a documentation-only correction after ES-P05 implementation PR #81 and completion-record PR #107 merged. It changes no product code, product tests, migration, workflow, runtime configuration, provider implementation, production system, or private data.

Canonical ES-P05 terminal evidence remains in [`2026-08-09-es-p05-report-workflow-complete.md`](2026-08-09-es-p05-report-workflow-complete.md).

## Why a correction was required

A concurrent ES-P05 finalizer merged PR #107 while this worker was reconciling live GitHub. PR #107 correctly marked ES-P05 `COMPLETE`, but its derived routing did not include owner-provided private LiteBans evidence supplied to this worker and it marked ES-X01 `READY` without reapplying the package's repository-availability prerequisite.

Live facts requiring correction:

- ES-P05 frozen head `9e6d5f8afc120b76f5f396a2e3e279bc5f851c85` is fully contained in normal merge `52c0dc47efdc2296827b4b6b743d01a86f72c856` and its implementation branch is deleted.
- Completion record PR #107 merged normally as `8d5b7dce21bba9c892e8219d0929fa5286aebbcc`.
- Issue #43 remains open and LiteBans remains authoritative.
- `COMP-ROSECHAT` remains unresolved and a live repository search did not identify a verified supported RoseChat standalone repository. ES-X01 therefore meets its own documented `BLOCKED` condition after dependencies complete.
- Owner-provided private local LiteBans execution demonstrates that ES-V01's former private-environment unblock condition changed and that unfinished repository-side work exists locally.

## LiteBans evidence ownership

The existing canonical owner is `ES-V01 — Private LiteBans representative-data verification`.

Reason: ES-V01 explicitly owns representative private schema inspection, mapping/rejection verification, rerun/idempotency, expiration/history behavior and sanitized conclusions for `AUD-MIG-003` and `AUD-MIG-004`. The UUID-only sanction defect was discovered while exercising exactly that migration-reader surface against representative private data.

The owner-provided sanitized evidence states:

- private MariaDB version: 10.11.6;
- local repository fix: `22934e33 Support UUID-only LiteBans sanctions`;
- the commit exists only locally on `section/plugin` and is not on GitHub;
- 102 bans, 53 mutes and 1,747 history rows were examined;
- after the fix, 153 supported sanctions imported: 100 bans and 53 mutes;
- a second import replayed all 153 without duplicate cases/events;
- mapped timestamp/expiry mismatch count was zero;
- 35 sanctions were active, 56 expired and 62 ended early; 37 were permanent;
- 49 warnings and 44 kicks remain intentionally unsupported/audit-only under the current contract;
- two IP-ban rows and five history-address rows were rejected as malformed under current validation;
- invalid historical usernames were ignored while usable UUID/network observations remained;
- local `:persistence:test`, `:integration-tests:compileTestJava`, and disposable two-pass MariaDB import validation passed;
- the private original dump was not modified or uploaded.

The seven rejected malformed source/history rows remain a separate pre-rehearsal data-policy decision. They are not authorization to silently skip, rewrite, repair or expose private source history.

## Corrected incomplete-package classifications

| Package | Classification / reason |
| --- | --- |
| `ES-V01` | `ACTIONABLE_CONTINUATION` / `PARTIAL` — former private-environment condition changed; local repository fix `22934e33` and representative validation work are unfinished on GitHub. |
| `ES-P07` | `READY` — dependency complete, but actionable continuation precedence places it behind ES-V01. |
| `ES-P06` | `READY` — ES-P05 complete, but actionable continuation precedence places it behind ES-V01. |
| `ES-P08` | `PARKED_BLOCKED` — ES-P07 incomplete. |
| `ES-X01` | `PARKED_BLOCKED` / `BLOCKED` — dependencies complete, but the required supported RoseChat repository/default branch/source/AGENTS remain unresolved. |
| `ES-X02` | `PARKED_BLOCKED` — ES-P08 incomplete. |
| `ES-X03` | `PARKED_BLOCKED` — ES-P08/ES-X02 incomplete. |
| `ES-X04` | `PARKED_BLOCKED` — ES-P08/ES-X02 incomplete. |
| `ES-V02` | `PARKED_BLOCKED` / `DEFERRED` — incomplete dependencies plus private distributed Java/Bedrock acceptance. |
| `ES-V03` | `PARKED_BLOCKED` / `DEFERRED` — incomplete destructive-provider dependencies plus private acceptance. |
| `ES-A01` | `PARKED_BLOCKED` / `DEFERRED` — ES-V01/V02/V03 incomplete and separate owner authorization/issue #43 boundary required. |
| `ES-QA01` | `PARKED_BLOCKED` — ES-A01 incomplete. |

## Exact next action

The next normal sequential worker must resume `ES-V01` before starting a new ready package. It must preserve/reproduce the exact behavior of local commit `22934e33` on the correct canonical ES-V01 package branch, inspect the actual diff rather than trusting the note alone, add or retain synthetic UUID-only LiteBans regression coverage, run normal exact-head validation, publish sanitized package state, and keep the private database local.

Do not begin production cutover, issue #43 acceptance, a real shadow migration, production authority changes, or source-data rewriting. Do not activate ES-P07, ES-P06 or ES-X01 from this correction worker.

## ES-P05 stop condition

After the routing-correction PR merges normally and `main` is verified to contain the corrected registry/workspace/latest-handoff state, this ES-P05 worker stops. No second implementation package is started.