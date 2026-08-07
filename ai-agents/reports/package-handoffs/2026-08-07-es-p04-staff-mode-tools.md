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
- Flyway V17 remains immutable; ES-P04 adds no migration.

## Implemented scope

ES-P04 implements the non-excluded operational staff-mode tools: random teleport, player inspector, freeze, reports, follow/spectate, vanish, staff chat, and a text/command staff-tools menu. Cheat Tester remains explicitly deferred to `ES-P10`; fake bases remain `ES-P11`.

Server-issued tool items are bound to owner UUID, current profile token, canonical slot, material, current active session/rank, and action permission. Unknown, stale, transferred, old-session, rank-invalid, wrong-slot, and wrong-material tools fail closed. Durable moderation actions reuse the existing command/service boundaries.

Random teleport is isolated in `StaffToolRandomTeleportService`; candidate state is sampled on target Folia entity schedulers, unsafe/exempt/hidden/frozen/staff/world/backend targets are filtered, and actor authority is rechecked before async teleport. Follow/spectate uses the same scheduler-safety model while preserving rank-required game mode. `/stafftools` supplies Bedrock-safe typed-command fallbacks.

The existing durable staff-session recovery model remains intact: durable snapshot before profile, exact restore/checksum verification, reconnect/startup recovery, rank reconciliation, inventory/damage/world/transfer/game-mode isolation, and no migration change.

## Product and review-fix commits

- `b70c6c48976ca176a3ed5c9c327a31d9422ed934` — dispatcher/security implementation.
- `c1db641169a40d0be64ad48b4672d3dce3da72c4` — Wiki/reference documentation.
- `b83d060b97f9dc46f59fc7d137e0d01672f407fb` — resolves 14 Codacy findings.
- `e7321ff2858f06da6d4daeca073fcb5a96bf4246` — Paper interaction-event hardening.
- `8c9a3f2c8220c36e84a275dac0c9f3ee4c59d0d8` — extracts random teleport service and resolves the final file-length Codacy finding.
- `dd1f10a12680eab6768473aee5b542e840b09b65` — restores the two-argument entity-scheduler helper overload omitted by the extraction.
- `88041357bc83ff2b37ea75ae2764aaaeead6e383` — makes the deferred Cheat Tester unavailable for every current rank.
- `1dd2667e57c5219a5bedfb251169b0c377b32c0b` — tests that Cheat Tester remains unavailable for all ranks and null/system authority.
- `f4c564396a1d57c756a92cc625bac52c8cdee6eb` — adds inclusive 0/60000 ms and rejecting -1/60001 ms cooldown configuration boundary tests across all staff-tool cooldown paths.
- `ce6e516259b3739d08d4607a838c5e4940aafacd` — corrects non-spectator follow/spectate feedback so it does not falsely claim creative mode.

## Validation evidence so far

- PR #79 is the only implementation PR and is ready for review.
- Superseded records head `4cc0178a8f908c43aafc6a88cf3014e81f5756f2` passed Wiki run `31173610733`, job `92850645081`.
- The same superseded head passed Coverage run `31173610764`, job `92850703180` on GitHub-hosted runner `1000008310`, including Java 21 setup, runtime-JAR build and inspection, aggregate tests/coverage, validation-artifact upload, and Codacy coverage upload.
- Codacy on `4cc0178a8f908c43aafc6a88cf3014e81f5756f2`: static analysis `92850885117` success; coverage variation `92852019542` success; diff coverage `92852019707` success.
- Those green runs are superseded by the later valid review fixes and are supporting history only. Final exact-head reruns remain required.
- CodeRabbit review found valid issues after that green head: stale routing records, deferred Cheat Tester issuance, missing cooldown boundary coverage, and inaccurate follow/spectate feedback. Those defects are fixed on the branch. The remaining scheduler-helper extraction comment is a maintainability refactor suggestion rather than a remaining functional defect; the earlier compile drift it cites was already corrected and must be explicitly dispositioned before freeze.
- Superseded Pi evidence remains infrastructure-only: private `ubuntu-latest` job `92847691164` had runner ID `0`, empty runner name, zero steps, Billing & plans rejection; Pi `92847702006` skipped. No product code ran there and it is not a pass.
- No owner-approved ES-P04 infrastructure exception exists in inspected state.
- V1-V17 unchanged. No production/private-data/cutover/ES-V02 action occurred.

## Current tracked-state checkpoint

Workspace state, package registry, package file, and `agent-handoffs/latest.md` now record PR #79, completed implementation/review repairs, the immutable V17 boundary, the configured Pi blocker risk, and the correct terminal decision: `COMPLETE` only after every required gate plus normal merge, otherwise `BLOCKED` / `PARKED_BLOCKED` if Pi remains unavailable without an approved ES-P04 exception.

Latest product-code head before this records checkpoint: `ce6e516259b3739d08d4607a838c5e4940aafacd`. The final frozen implementation-branch head is the commit produced after this handoff/state reconciliation and must be read from live GitHub before validation.

## Exact next action

Disposition every remaining PR #79 review thread, freeze the resulting implementation-branch head, then obtain exact-head Wiki, Java 21/full-suite/MariaDB migration/coverage/runtime-JAR/provider-leak, Codacy, CodeRabbit/review, and configured Pi evidence. If all required gates pass, merge PR #79 normally, verify containment and cleanup, publish `COMPLETE`, and stop. If ordinary gates pass but the Pi path remains unavailable and no owner-approved ES-P04 exception exists, preserve PR #79 and its branch, publish `ES-P04` as `BLOCKED` / `PARKED_BLOCKED` on `main` through the required documentation-only status-publication PR, and stop. Do not start ES-P09.
