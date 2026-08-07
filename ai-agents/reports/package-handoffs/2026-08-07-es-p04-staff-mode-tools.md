# ES-P04 handoff — Staff-mode operational tools

Date: 2026-08-07
Status: `ACTIVE`
Classification: `ACTIONABLE_CONTINUATION`
Priority: `40`
Worker: generic sequential package worker
PR: #79 (ready for review)

## Selection and starting state

- Starting aggregate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Required branch: `package/es-p04-staff-mode-tools`.
- `ES-P03` is `COMPLETE` through merge `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.
- `ES-P02` remains `BLOCKED` / `PARKED_BLOCKED`; its private ordinary Ubuntu prerequisite still receives runner ID `0`, zero steps, and the Billing & plans payment/spending-limit rejection, with Pi skipped.
- `ES-P09` remains dependency-ready at priority 55 and was not started.
- Issue #43 remains open/deferred; LiteBans remains authoritative.
- Flyway V17 remains immutable.

## Implemented scope

ES-P04 implements the non-excluded operational staff-mode tools: random teleport, player inspector, freeze, reports, follow/spectate, vanish, staff chat, and a text/command staff-tools menu. Cheat Tester remains explicitly deferred to `ES-P10`; fake bases remain `ES-P11`.

Server-issued tool items are bound to owner UUID, current profile token, canonical slot, material, current active session/rank, and action permission. Unknown, stale, transferred, old-session, rank-invalid, wrong-slot, and wrong-material tools fail closed. Durable moderation actions reuse the existing command/service boundaries.

Random teleport is isolated in `StaffToolRandomTeleportService`; candidate state is sampled on target Folia entity schedulers, unsafe/exempt/hidden/frozen/staff/world/backend targets are filtered, and actor authority is rechecked before async teleport. Follow/spectate uses the same scheduler-safety model while preserving rank-required game mode. `/stafftools` supplies Bedrock-safe typed-command fallbacks.

The existing durable staff-session recovery model remains intact: durable snapshot before profile, exact restore/checksum verification, reconnect/startup recovery, rank reconciliation, inventory/damage/world/transfer/game-mode isolation, and no migration change.

## Product commits

- `b70c6c48976ca176a3ed5c9c327a31d9422ed934` — dispatcher/security implementation.
- `c1db641169a40d0be64ad48b4672d3dce3da72c4` — Wiki/reference documentation.
- `b83d060b97f9dc46f59fc7d137e0d01672f407fb` — resolves 14 Codacy findings.
- `e7321ff2858f06da6d4daeca073fcb5a96bf4246` — Paper interaction-event hardening.
- `8c9a3f2c8220c36e84a275dac0c9f3ee4c59d0d8` — extracts random teleport service and resolves the final file-length Codacy finding.
- `dd1f10a12680eab6768473aee5b542e840b09b65` — restores the two-argument entity-scheduler helper overload omitted by the extraction; no gameplay behavior change.

## Validation evidence so far

- PR #79 is the only implementation PR and is ready for review.
- Superseded exact head `93858cf68c539a0c1fe62b20d1993607981517ab` passed Wiki run `31173353489`, job `92849855813`.
- Its hosted Java run `31173353511`, job `92849833109`, reached a real GitHub-hosted runner and failed compilation only because the extracted random service called a missing two-argument `onEntity(...)` overload. Commit `dd1f10a12680eab6768473aee5b542e840b09b65` restores that helper.
- Codacy's PR summary after the structural split showed 0 new issues before the compile-only helper fix. Final exact-head Codacy remains required.
- `@coderabbitai review` was requested after PR #79 was moved out of draft. Final exact-head review disposition remains required.
- Superseded Pi evidence remains infrastructure-only: private `ubuntu-latest` job `92847691164` had runner ID `0`, empty runner name, zero steps, Billing & plans rejection; Pi `92847702006` skipped. No product code ran there and it is not a pass.
- V1-V17 unchanged. No production/private-data/cutover/ES-V02 action occurred.

## Exact next action

Freeze the records checkpoint produced with this handoff, then obtain exact-head Wiki, Java 21/full-suite/MariaDB migration/coverage/runtime-JAR/provider-leak, Codacy, CodeRabbit/review, and Pi evidence. If all ordinary gates pass but the Pi path remains unavailable and no owner-approved ES-P04 exception exists, publish `ES-P04` as `BLOCKED` / `PARKED_BLOCKED` on `main` through the required docs-only status-publication PR, preserve PR #79/branch, and stop. Do not start ES-P09.
