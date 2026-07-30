# EnthusiaStaff workspace manifest

Last updated: 2026-07-30 (America/Indianapolis)

This manifest records review, recovery, and validation state. Nothing listed here
has been deployed, released, applied to production data, or used to replace
LiteBans.

## Repository checkpoint

| Repository | Remote URL | Default branch | Working branch | Current checkpoint | Build and test status | Codacy status | Current blockers |
| --- | --- | --- | --- | --- | --- | --- | --- |
| EnthusiaStaff | `https://github.com/wsg138/EnthusiaStaff.git` | `main` at `7675ac0625d993dec55b5f31ac6b7cdbfec1d6d1` | `section/punishment-request-interfaces` for PR #21 | PR #20 merged as `133ace65b9ccdfd459d38089928e4bea612ed2a6`; PR #21 implementation, review fixes, and focused tests validated at `ec7b3077cd1c229ac3ef15eb0a3b616175612582`; checkpoint documentation is current | PASS at `ec7b3077cd1c229ac3ef15eb0a3b616175612582`: Java 21 multi-module build, unit tests, MariaDB Testcontainers, aggregate JaCoCo, Paper/Velocity runtime-jar integrity, 24 provider API source-type checks per jar, and zero provider API leakage; validation run `30588491828`, artifact `8777480221` | Codacy reports zero introduced issues on `ec7b3077cd1c229ac3ef15eb0a3b616175612582`; 12.95% diff coverage and -0.78% coverage variation remain within the configured gate | Explicit merge authorization is still required. The prior full CodeRabbit review's actionable findings are addressed and both review threads are resolved; its incremental rereview was rate-limited. The current PR head must retain green exact-head checks. Production-like Paper/Velocity/provider staging remains unavailable. |
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
- Validated implementation/test head: `ec7b3077cd1c229ac3ef15eb0a3b616175612582`.
- Exact implementation validation run: `30588491828`; validation artifact:
  `8777480221`.
- Build result: `BUILD SUCCESSFUL`; 49 actionable Gradle tasks, 40 executed and
  9 up-to-date.
- Runtime jars:
  - Paper SHA-256 `9c0c19589be72189f11a8ed6892ff9e3a87a79fb4e8604b8f8f3f991347f3293`.
  - Velocity SHA-256 `6e855b0d8a202fedb8951cfe9fb5593e06322413630343755be1e8cf77b0f575`.
  - Both checked 24 provider API source types with zero leaks.
- Aggregate JaCoCo at that checkpoint: line 32.21%, branch 26.41%, instruction
  34.52%.
- Codacy reports zero introduced issues, 12.95% diff coverage, and a -0.78%
  overall coverage variation against the PR merge base.
- The full CodeRabbit review identified two major findings and two maintenance
  findings. Shared approval rules, storage-readiness guards, explicit player
  directory wiring, and centralized denial presets address them; both inline
  review threads are resolved. A later incremental rereview was rate-limited.
- Implemented interface behavior includes routed draft confirmation, filtered and
  paginated review queues, fenced claim/approve/deny actions, stale/resolved-state
  handling, offline target presentation, revision display, self/Developer/rank
  restrictions, idempotent retry messaging, separated GUI rendering, and clear
  pre-bootstrap storage responses.
- Existing domain and MariaDB tests cover direct temporary Helper application,
  permanent Helper requests, Developer request-only behavior, stale fences, lost
  leases, expiry, external fulfillment, approval-time sanction start, and
  idempotent decisions. Focused PR #21 tests additionally cover the shared
  approval predicate, explicit bootstrap wiring, permission consistency,
  presentation states, pagination, empty queues, offline targets, and reviewer
  visibility.
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
- Only merge a current head whose required checks are green; any later source
  change invalidates prior exact-head evidence until validation runs again.
- Keep each related repository independent and never stage `related-repos/` in
  the root repository.
