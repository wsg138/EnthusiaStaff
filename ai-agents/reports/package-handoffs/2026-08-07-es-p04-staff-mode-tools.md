# ES-P04 handoff — Staff-mode operational tools

Date: 2026-08-07
Status: `ACTIVE`
Classification: `ACTIONABLE_CONTINUATION`
Priority: `40`
Worker: generic sequential package worker
PR: #79 (draft)

## Selection and starting state

- Starting aggregate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Required branch: `package/es-p04-staff-mode-tools`.
- Dependency `ES-P03` is `COMPLETE` through merge `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.
- `ES-P02` was rechecked first and remains `BLOCKED` / `PARKED_BLOCKED`. Inspected private `plugin-live-test.yml` run `31141797380`, build job `92753075216`, had `ubuntu-latest`, runner ID `0`, empty runner name, steps `[]`, and GitHub's Billing & plans payment/spending-limit annotation; Pi `92753100652` skipped. No identical rerun was requested.
- `ES-P09` remains dependency-ready at priority 55 and was not started.
- Issue #43 remains open/deferred; LiteBans remains authoritative.
- Flyway V17 remains the immutable migration boundary.

## Implemented scope

The non-excluded operational staff-mode hotbar is implemented:

- slot 1 random-player teleport;
- slot 2 player inspector;
- slot 3 freeze;
- slot 4 reports;
- slot 6 follow/spectate;
- slot 7 vanish;
- slot 8 staff chat;
- slot 9 staff-tools text menu.

Slot 5 Cheat Tester remains explicitly unavailable because fake/test entities belong to `ES-P10`. Fake bases remain `ES-P11`.

Staff tool tags are no longer treated as authority. Server-issued items carry the owner UUID and a random token for the current staff-profile application. Use is rejected for unknown IDs, inactive/stale sessions, rank loss, owner mismatch, old-session token, wrong slot, or wrong material. The dispatcher then rechecks the action permission and active staff rank before execution.

Inspector, freeze, reports, vanish, and staff chat reuse their existing command/service paths. This preserves established operational-mode, hierarchy, audit, provider-availability, and failure behavior rather than duplicating moderation logic in the listener.

Random teleport samples each candidate on the target's Folia entity scheduler and excludes self, active staff, vanished, frozen, exempt, dead, sleeping, vehicle, spectator, disabled-world, and disabled-backend cases. Final actor authority is rechecked before `teleportAsync`.

Follow/spectate samples the target on its entity scheduler, rejects vanished/exempt targets, moves asynchronously, and preserves the actor's rank-required game mode. Spectator-profile ranks attach to the live target when still safe; creative-profile ranks follow by teleport without weakening the staff-mode game-mode fence.

`/stafftools`, `/stafftools random`, and `/stafftools spectate <player>` provide text/command fallbacks. The Wiki explicitly tells Bedrock/Geyser users to type the displayed commands if Java click events are unavailable.

## Safety and recovery preserved

The existing durable `StaffModeManager` architecture was retained rather than replaced:

- durable snapshot before temporary staff state;
- exact restore and checksum verification on exit;
- reconnect/startup recovery;
- rank reconciliation and durable exit on rank loss;
- inventory, damage, world-interaction, transfer and game-mode isolation;
- no migration change.

Rank-profile reapplication issues a fresh staff-tool session token, making copied items from the older profile stale automatically. Quit, failed activation and successful exit clear the runtime token.

## Implementation commits

- `b70c6c48976ca176a3ed5c9c327a31d9422ed934` — first coherent dispatcher/security implementation.
- `c1db641169a40d0be64ad48b4672d3dce3da72c4` — command/permission/recovery/Bedrock Wiki documentation.
- `b83d060b97f9dc46f59fc7d137e0d01672f407fb` — resolves all 14 Codacy findings reported on the superseded documentation head by decomposing dispatcher and policy complexity.
- `e7321ff2858f06da6d4daeca073fcb5a96bf4246` — hardens Paper 1.21.11 interaction handling: predicted-cancelled right-click-air remains usable for staff tools and redundant precise entity interaction packets do not create a second dispatcher path.

## Tests added

Focused unit tests cover:

- canonical IDs and fixed slots;
- rank availability and Cheat Tester exclusion;
- stale/inactive, transferred, owner-mismatched, old-session, wrong-slot and wrong-material tool rejection;
- per-player/per-tool cooldown expiry and clear;
- random-target identity/state/environment exclusions;
- case-insensitive disabled server/world settings and cooldown-class mapping.

Existing repository suites remain the source for durable session/store/reconnect/rank/inventory/provider regression coverage and must pass on the final exact head.

## Documentation

Updated:

- `docs/wiki/pages/Staff-Mode-Vanish-and-Freeze.md`
- `docs/wiki/pages/Commands-and-Permissions.md`
- `paper/src/main/resources/plugin.yml`
- `paper/src/main/resources/config.yml`

The docs cover fixed slots, commands, permission nodes, target exemptions, stale-tool behavior, cooldowns, restart-scoped settings, exact restoration, provider failures, troubleshooting, Bedrock fallbacks, and explicit ES-P10/ES-V02/production boundaries.

## Validation evidence so far

- Draft PR #79 exists and is the only ES-P04 implementation PR.
- Wiki validation on superseded head `c1db641169a40d0be64ad48b4672d3dce3da72c4`: run `31171631905`, job `92844514821`, success.
- Codacy on that superseded head: check `92844755477` found 14 issues. Every annotation was addressed in `b83d060b97f9dc46f59fc7d137e0d01672f407fb`; final exact-head rerun remains required.
- PR review threads at this checkpoint: zero. Submitted PR reviews: zero.
- Configured Pi staging has repeatedly failed before product execution because the private `ubuntu-latest` prerequisite receives runner ID `0`, no steps, and the Billing & plans payment/spending-limit rejection. The downstream Pi job is skipped. This is external infrastructure evidence, **not** a staging pass.
- No migration files changed; V1-V17 remain unchanged.
- No production, private player data, cutover, issue #43, or ES-V02 action was performed.

## Current blocker risk

There is no known product/dependency blocker. The configured Pi staging path remains externally unavailable. If all ordinary exact-head hosted/review gates pass but the Pi path still cannot start product work, `VALIDATION-POLICY.md` requires either a real successful staging run or an explicit owner-approved infrastructure exception for ES-P04 with the missing staging assigned to a named later validation package. `ES-V02` already owns representative Java/Bedrock staging, but that assignment alone is not permission to invent the exception.

## Exact next action

Freeze the checkpoint head produced with this handoff if no new static-analysis/review issue appears. Run/inspect exact-head Wiki, Java 21/full-suite/MariaDB migration/coverage/runtime-JAR/provider-leak, Codacy and review-bot evidence. Reconfirm the Pi result for that exact head without retrying an unchanged external condition. Then either:

1. satisfy every completion gate and merge PR #79 normally, verify containment, publish `COMPLETE`, derive later READY states, clean the branch, and stop; or
2. if the only remaining gate is the unapproved/unavailable Pi staging path, publish ES-P04 durably as `BLOCKED` / `PARKED_BLOCKED` with the exact unblock condition and stop.

Do not start ES-P09 or any second package.
