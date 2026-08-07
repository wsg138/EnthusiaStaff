# Latest AI handoff

Current persistent package handoff:

[`2026-08-07-es-p04-staff-mode-tools.md`](../package-handoffs/2026-08-07-es-p04-staff-mode-tools.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P04 — Staff-mode operational tools` is the selected active package. It was claimed by the generic sequential package worker from exact legitimate aggregate `main` `5c820c29c2fe5a498ea7f80454579953ac05b436` on required branch `package/es-p04-staff-mode-tools`.

Selection followed live continuation classification. `ES-P02 — Runtime database recovery and Velocity reload` remains `BLOCKED` / `PARKED_BLOCKED`: newest inspected private `plugin-live-test.yml` run `31141797380` again failed before the required ordinary `ubuntu-latest` runner allocated. Build job `92753075216` has runner ID `0`, empty runner name, steps `[]`, and GitHub's same Billing & plans payment/spending-limit annotation; Pi job `92753100652` skipped. The exact unblock condition therefore has not changed, and no redundant rerun was made.

`ES-P01`, `ES-P03`, and `ES-X05` remain `COMPLETE`. `ES-P09` remains dependency-derived `READY` at priority 55 but is unassigned and must not be started by this worker. ES-P04 priority 40 was the lowest eligible READY package.

ES-P04 scope is limited to the non-excluded operational staff-mode tools and their shared safety/recovery behavior: random teleport, player inspector, freeze, reports, spectate/follow, vanish, staff chat, and tools menu; stale/transferred/spoofed tool rejection; cooldowns; snapshot/restoration and reconnect/reload/shutdown/rank-change safety; missing dependency behavior; and Bedrock command/text fallback. Cheat testers/fake entities belong to `ES-P10`; fake bases belong to `ES-P11`.

Baseline inspection confirms the existing `StaffModeManager` already owns durable entry/exit, exact snapshot restoration and verification, reconnect and rank recovery, inventory/world/damage isolation, and creation of `staff_tool` PDC items. `StaffToolTransferListener` protects transfers, but no interaction dispatcher consumes the tool tag. The current tag-presence check is also not sufficient authority to satisfy ES-P04's forged/stale-tool requirement, so that boundary is part of the selected package.

Current migration boundary is immutable V17. Issue #43 remains open/deferred; LiteBans remains authoritative. No production, private-data, cutover, deployment, or ES-V02 action is authorized.

Exact next action: finish mapping existing command/manager/permission/provider contracts and tests, implement and checkpoint the first coherent dispatcher/security slice, then open the required draft PR and continue only ES-P04 through review, exact-head validation, normal merge, containment, final state publication, and cleanup.
