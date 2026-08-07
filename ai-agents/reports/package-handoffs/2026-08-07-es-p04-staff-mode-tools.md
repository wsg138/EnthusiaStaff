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
- `ES-P02` was rechecked first and remains `BLOCKED` / `PARKED_BLOCKED`. Private `plugin-live-test.yml` run `31141797380`, build job `92753075216`, had `ubuntu-latest`, runner ID `0`, empty runner name, steps `[]`, and GitHub's Billing & plans payment/spending-limit annotation; Pi `92753100652` skipped. No identical rerun was requested.
- `ES-P09` remains dependency-ready at priority 55 and was not started.
- Issue #43 remains open/deferred; LiteBans remains authoritative.
- Flyway V17 remains the immutable migration boundary.

## Implemented scope

The non-excluded operational staff-mode hotbar is implemented: random-player teleport, player inspector, freeze, reports, follow/spectate, vanish, staff chat, and the staff-tools text menu. Slot 5 Cheat Tester remains explicitly unavailable because fake/test entities belong to `ES-P10`; fake bases remain `ES-P11`.

Staff tool tags are not authority. Server-issued items carry the owner UUID and a random token for the current staff-profile application. Use is rejected for unknown IDs, inactive/stale sessions, rank loss, owner mismatch, old-session token, wrong slot, or wrong material. The dispatcher then rechecks the action permission and active staff rank before execution.

Inspector, freeze, reports, vanish, and staff chat reuse their existing command/service paths. Random teleport samples candidates on their Folia entity schedulers, applies staff/vanish/freeze/exemption/unsafe-state/world/backend filters, and rechecks actor authority before `teleportAsync`. Follow/spectate samples the target on its entity scheduler, rejects vanished/exempt targets, moves asynchronously, and preserves the actor's rank-required game mode.

`/stafftools`, `/stafftools random`, and `/stafftools spectate <player>` provide text/command fallbacks. The Wiki explicitly tells Bedrock/Geyser users to type the displayed commands if Java click events are unavailable.

## Safety and recovery preserved

The existing durable `StaffModeManager` architecture remains responsible for durable snapshot-before-profile, exact restore/checksum verification, reconnect/startup recovery, rank reconciliation and durable exit on rank loss, inventory/damage/world/transfer/game-mode isolation, and migration-free recovery. Rank-profile reapplication issues a fresh staff-tool session token, making older copied items stale automatically. Quit, failed activation, and successful exit clear the runtime token.

## Implementation commits

- `b70c6c48976ca176a3ed5c9c327a31d9422ed934` — first coherent dispatcher/security implementation.
- `c1db641169a40d0be64ad48b4672d3dce3da72c4` — command/permission/recovery/Bedrock Wiki documentation.
- `b83d060b97f9dc46f59fc7d137e0d01672f407fb` — resolves all 14 Codacy findings from the superseded documentation head by decomposing dispatcher and policy complexity.
- `e7321ff2858f06da6d4daeca073fcb5a96bf4246` — hardens Paper 1.21.11 interaction handling so predicted-cancelled air clicks remain usable and redundant precise entity packets do not double-dispatch.
- `8c9a3f2c8220c36e84a275dac0c9f3ee4c59d0d8` — resolves the final Codacy file-length finding by extracting scheduler-safe random teleport into `StaffToolRandomTeleportService` without weakening authorization or filtering.

## Tests and documentation

Focused tests cover canonical IDs/fixed slots, rank availability and Cheat Tester exclusion, stale/transferred/old-session/wrong-slot/wrong-material rejection, cooldown expiry/clear, random-target identity/state/environment exclusions, and disabled-server/world/cooldown settings.

Updated reference files include `Staff-Mode-Vanish-and-Freeze.md`, `Commands-and-Permissions.md`, `plugin.yml`, and `config.yml`. They document fixed slots, commands, permissions, target exemptions, stale-tool behavior, cooldowns, restart-scoped settings, exact restoration, provider failures, troubleshooting, Bedrock fallbacks, and explicit ES-P10/ES-V02/production boundaries.

## Validation evidence so far

- Draft PR #79 is the only ES-P04 implementation PR.
- Superseded head `501cb2a90fab0c28a080a64ce51e81d10f2f0da5` passed Wiki validation in run `31172662371`, job `92847679913`.
- Codacy check `92848009322` on that superseded head reported one remaining dispatcher file-length issue after earlier static-analysis remediation; it was fixed in `8c9a3f2c8220c36e84a275dac0c9f3ee4c59d0d8`. Final exact-head static analysis remains required.
- On the same superseded head, aggregate Pi wrapper `31172658131` pointed to private staging run `31172664638`. Its ordinary `ubuntu-latest` job `92847691164` had runner ID `0`, empty runner name, steps `[]`, and the Billing & plans payment/spending-limit rejection; Pi job `92847702006` skipped. No product code ran. This is external infrastructure evidence, **not** a staging pass.
- PR review threads at latest inspection: zero. Submitted PR reviews: zero.
- No migration files changed; V1-V17 remain unchanged.
- No production, private player data, cutover, issue #43, or ES-V02 action was performed.

## Current blocker risk

There is no known product/dependency blocker. The configured Pi staging path remains externally unavailable. If every ordinary exact-head hosted/review gate passes but Pi still cannot start product work, `VALIDATION-POLICY.md` requires either a real successful staging run or an explicit owner-approved infrastructure exception for ES-P04 with missing staging assigned to a named later validation package. `ES-V02` already owns representative Java/Bedrock staging, but that assignment alone is not permission to invent the exception.

## Exact next action

Freeze the records checkpoint produced with this handoff, run/inspect exact-head Wiki, Java 21/full-suite/MariaDB migration/coverage/runtime-JAR/provider-leak, Codacy and review-bot evidence, and record the Pi result for that same head. Then either merge normally if all completion gates are satisfied or publish ES-P04 durably as `BLOCKED` / `PARKED_BLOCKED` if the unavailable Pi gate remains unapproved. Do not start ES-P09 or any second package.
