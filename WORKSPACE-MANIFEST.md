# EnthusiaStaff workspace manifest

Last updated: 2026-07-30 (America/Indianapolis)

This manifest records review, recovery, and validation state. Nothing listed here
has been deployed, released, applied to production data, or used to replace
LiteBans.

## Repository checkpoint

| Repository | Remote URL | Default branch | Working branch | Current checkpoint | Build and test status | Codacy status | Current blockers |
| --- | --- | --- | --- | --- | --- | --- | --- |
| EnthusiaStaff | `https://github.com/wsg138/EnthusiaStaff.git` | `main` at `7675ac0625d993dec55b5f31ac6b7cdbfec1d6d1` | `section/punishment-request-interfaces` for draft PR #21 | PR #20 merged as `133ace65b9ccdfd459d38089928e4bea612ed2a6`; PR #21 implementation and focused tests validated at `4e0ebf749932587fe6ebfe418058ba2450424ec3`; current branch additionally aligns the Wiki workflow with current `main` and updates checkpoint documentation | PASS at `4e0ebf749932587fe6ebfe418058ba2450424ec3`: Java 21 multi-module build, unit tests, MariaDB Testcontainers, aggregate JaCoCo, Paper/Velocity runtime-jar integrity, 24 provider API source-type checks per jar, and zero provider API leakage; validation run `30583224286`, artifact `8775490979` | Zero introduced issues was reported for the validated implementation checkpoint; final branch analysis must remain at zero before merge | PR #21 still requires current-main history integration, final exact-head validation after documentation/history updates, one final review when allowance is available, no unresolved review threads, and an accurate PR description. Production-like Paper/Velocity/provider staging remains unavailable. |
| enthusia-site | `https://github.com/wsg138/enthusia-site.git` | `main` | Expected feature branch not reconstructed | No PR #21 website changes | NOT_RUN | NOT_ANALYZED | Provider/site work remains deferred until the root punishment checkpoint is complete. |
| EnthusiaCurrency | `https://github.com/wsg138/EnthusiaCurrency.git` | `main` | Expected provider branch not reconstructed | No PR #21 provider changes | NOT_RUN | NOT_ANALYZED | Provider API reconstruction and staging remain outstanding. |
| EnthusiaCommend | `https://github.com/wsg138/EnthusiaCommend.git` | `main` | Expected provider branch not reconstructed | No PR #21 provider changes | NOT_RUN | NOT_ANALYZED | Provider API reconstruction and staging remain outstanding. |
| EnthusiaAutoClicker | `https://github.com/wsg138/EnthusiaAutoClicker.git` | `main` | Expected provider branch not reconstructed | No PR #21 provider changes | NOT_RUN | NOT_ANALYZED | Provider API reconstruction and staging remain outstanding. |
| Enthusia-RoseChat | Intended `wsg138/Enthusia-RoseChat` remains missing or inaccessible | UNKNOWN | None | BLOCKED | BLOCKED | NOT_ANALYZED | Do not invent a remote or push to an upstream owner. |
| EnthusiaMarket | `https://github.com/wsg138/EnthusiaMarket.git` | `main` | Expected provider branch not reconstructed | No PR #21 provider changes | NOT_RUN | NOT_ANALYZED | Existing detached/upstream references are read-only; provider reconstruction remains outstanding. |

## Merged PR #20 checkpoint

- Pull request: #20, **Add durable punishment request workflow**.
- Final reviewed head: `a334f46adc9beea679b4f5d6d13ee7d4c3960ef4`.
- Squash merge on `main`: `133ace65b9ccdfd459d38089928e4bea612ed2a6`.
- Final validation run: `30572347767`.
- Source branch retained: `section/durable-punishment-requests`.
- Complete Java 21 build, unit tests, MariaDB 11.8.3 Testcontainers,
  aggregate JaCoCo, and Paper/Velocity runtime-jar inspection passed.
- Twenty-four provider API source types were checked and no provider API class
  leaked into either runtime jar.
- Codacy reported zero introduced issues and no unresolved GitHub review thread
  remained. CodeRabbit could not review the final head because its allowance was
  exhausted.

## Active PR #21 checkpoint

- Pull request: #21, **Expose durable punishment request interfaces**.
- Branch: `section/punishment-request-interfaces`; the branch was not reset,
  renamed, recreated, or replaced.
- Merge base with PR #20: `133ace65b9ccdfd459d38089928e4bea612ed2a6`.
- Validated implementation/test head: `4e0ebf749932587fe6ebfe418058ba2450424ec3`.
- Exact-head validation run: `30583224286`; validation artifact: `8775490979`.
- Build result: `BUILD SUCCESSFUL`; 49 actionable Gradle tasks, 40 executed and
  9 up-to-date.
- Runtime jars:
  - Paper SHA-256 `107af810a1c1020105bd0a5017ccc4619b4ad87dcd2f9e76d0403dc720a2da7f`.
  - Velocity SHA-256 `13329af707e6744eac980abf39ddf1facd1773963671986f25c4643d8815322c`.
  - Both checked 24 provider API source types with zero leaks.
- Aggregate JaCoCo at that checkpoint: line 32.30%, branch 26.47%, instruction
  34.61%.
- Implemented interface behavior includes routed draft confirmation, filtered and
  paginated review queues, fenced claim/approve/deny actions, stale/resolved-state
  handling, offline target presentation, revision display, self/Developer/rank
  restrictions, and idempotent retry messaging.
- Existing domain and MariaDB tests cover direct temporary Helper application,
  permanent Helper requests, Developer request-only behavior, stale fences, lost
  leases, expiry, external fulfillment, approval-time sanction start, and
  idempotent decisions. Focused PR #21 tests cover bootstrap/constructor wiring,
  permission consistency, presentation states, pagination, empty queues, offline
  targets, and reviewer visibility.
- The Wiki has not been changed for PR #21. Staff-facing Wiki updates remain
  deferred until the interfaces are final and must distinguish automated testing
  from staging verification.

## Workspace and release rules

- Keep remaining root-plugin work on `section/plugin` after PR #21 merges and is
  incorporated into the latest `main`.
- Update every SHA, PR, validation, coverage, Codacy, review, and blocker field at
  coherent checkpoints.
- A skipped or superseded run is never recorded as passed.
- A merged PR is a checkpoint, not deployment authorization.
- Keep each related repository independent and never stage `related-repos/` in
  the root repository.
