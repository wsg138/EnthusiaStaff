# Enthusia punishment candidate: Folia admission audit — 2026-10-25

**Release status: NO-GO. Folia opt-in is intentionally NOT being enabled.**

The compiled Paper JAR currently has no top-level folia-supported: true in its packaged plugin.yml. Folia refuses to load plugins without explicit opt-in. That is a concrete startup blocker, not a reason to add the flag before validating runtime compatibility.

PaperMC reference: https://docs.papermc.io/paper/dev/folia-support/

PaperMC warns that adding the marker alone does not make a plugin safe: region ownership, player/entity scheduler use, world transitions and concurrency must be audited and runtime-tested first.

## Current source blockers (known, not exhaustive)

1. **Legacy Bukkit main-thread scheduler.** paper/src/main/java/net/enthusia/staff/paper/integration/RoseChatInboundBridgeIntegration.java, line 55, schedules inbound cross-platform RoseChat dispatch using plugin.getServer().getScheduler().runTask(plugin, task). Folia does not provide Bukkit's traditional universal main-thread scheduler. First confirm RoseChat's inbound API and thread contract; a blind switch to global-region scheduling could still be unsafe.
2. **Synchronous staff-state teleport.** paper/src/main/java/net/enthusia/staff/paper/staff/StaffStateCodec.java, line 94, calls player.teleport(decoded.location()) as part of state restoration. A cross-region/cross-world Folia restore needs a reviewed asynchronous teleport followed by the player-owner continuation. Do not reorder or remove restoration verification, persisted-state safeguards or rollback semantics just to satisfy a static check.
3. **Player disconnect requires a synchronous restore.** paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java, line 1076, calls restoreSavedState(player, session) directly during PlayerQuitEvent, *before the server saves the player's native state*. This is not fixed by changing StaffStateCodec to teleportAsync: asynchronous completion could happen after disconnect and after player data is saved. The full caller graph includes normal exit, backend handoff, restart recovery and quit. The migration must deliberately redesign those flows with durable, retry-safe recovery on the player-owner thread, with real lifecycle acceptance.

A read-only inspection of the connected local RoseChat source shows that its present RoseChatAPI compile/source branch does not expose the custom dispatchInboundChat method used by this integration; the EnthusiaStaff integration-contracts version is an explicit compile-time stub. That is insufficient evidence of RoseChat's actual runtime threading contract. Do not substitute a global-region scheduler without an upstream/provider contract or real integration test.

These are **source audit findings**, not exhaustive proof that other code is region-safe. The current Paper runtime and proxy tests cannot establish Folia scheduler and entity semantics.

## Read-only audit command

After a local Gradle :paper:shadowJar build from a candidate checkout:

    python scripts/qa/paper_folia_admission.py --root .

The script reads the exact shaded JAR's plugin.yml (not just a source manifest), checks the Folia opt-in field and scans for known legacy scheduling, synchronous staff teleport and quit-event immediate restore patterns (including multiline scheduler chaining). It never edits source, metadata, JAR or server files and never starts a server. Optional --output build/folia-admission.json writes a local JSON report. Add --require-folia-load-admission to obtain a nonzero exit while a known blocker remains.

The report ALWAYS includes folia_runtime_verified: false and release_status: NO_GO. folia_load_admission: true would mean only that the specific known source and metadata checks have cleared; it would NOT establish that Folia loads or runs the plugin safely. Zero static findings is not a complete threading audit. Keep guided-punishment.enabled: false throughout this work.

Regression tests:

    python -m unittest discover -s scripts/qa -p test_paper_folia_admission.py -v

Tests cover the current no-marker/known-blocker state, unsafe manual opt-in, duplicate/invalid metadata, missing JAR metadata, quit-event synchronous restoration remaining unsafe even after a hypothetical async teleport, multiline legacy scheduler syntax, and the case where static sources are clean but real runtime is still untested.

## Preconditions for later Folia opt-in

- Rework each known incompatible scheduler call safely, including retirement/shutdown and region migration; confirm dependent RoseChat supports the chosen thread contract.
- Review StaffStateCodec.restore teleport and multi-field player restore across real region ownership transitions, preserving rollback/recovery behavior. Account separately for StaffModeManager.onQuit which currently needs the original snapshot restored before player data is saved; no fire-and-forget asynchronous conversion is safe.
- Audit all Paper API player, inventory, world, permission and chat calls for region ownership, beyond the source heuristics; check dependency plugin Folia support.
- Obtain explicit authorization before starting an isolated Folia test server. Record actual startup, command, staff mode/restoration, sanction/punishment GUI and Java/Bedrock/Geyser behavior. Do not touch live/test servers or accept a server EULA without approval.
- Only after passing these checks, consider a separately reviewed folia-supported: true declaration. This marker is a promise of compatibility, not a workaround.

**Other unresolved release gates:** severity calibration issue #616; read-only comparison/approval of customized production reason-policies.yml; real Java/Bedrock client GUI tests; staging-only recovery/rollback; production rank and Staff Mode acceptance. Nothing here authorizes a merge, deployment, restart, live database write, Discord post or guided-mode enablement.
