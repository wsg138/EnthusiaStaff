# Temporary Cheat Tester totem-safety hold

Report (October 11, 2026): the live Cheat Tester sometimes takes a player's totem and does not restore it. Source review shows `CheatTesterProbeEngine.beginTotem()` removes the offhand item intentionally to challenge auto-refill logic. The normal completion path invokes `CheatTesterSnapshotCodec.restore()`, which serializes and restores the exact offhand contents, but we do **not** have an end-to-end passing runtime proof that it completes in all failure/disconnect cases.

This mitigation **blocks new TOTEM_REFILL probes**, including direct command requests, selection and cycling. Other tester types remain available; the default is changed to NO_FALL. The menu displays a clear disabled notice. The gate also applies to explicit `run` calls, before any inventory mutation or external asset lock.

Existing in-flight tester journal records are **not** deleted or bypassed: recovery code remains intact.

**Before reenabling:** capture sanitized logs of a failing run; reproduce on an isolated Paper 26.2 server with real offhand totems and stacks; test cancel/timeout/quit/crash/restart; verify byte-for-byte before/after inventory, durable journal terminal state, locked cursor paths and inventory restoration. Do not use live player inventories to test this.

This is a containment change, not a claim the underlying loss has been repaired.
