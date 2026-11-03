# Punishment queued target-picker navigation fence QA — 2026-10-25

**DRAFT / NOT DEPLOYED.** This work is isolated on top of draft PR #722 exact commit `d389f5fc6659d12784ab7819800a78468153d3fc`. No original branch was changed.

## Defect and consequence

The no-target `/punish` command called `openTargetPicker`, invalidated older work, then queued `openTargetPickerPage` through the entity scheduler **without carrying its own navigation ticket**. If a newer typed `/punish <target> <reason>` arrived before that queued callback executed, the old picker would run, invalidate the newer command's target-resolution ticket and display an outdated picker. The same stale callback could overwrite a different inventory opened by the staff member before the queued task executed.

## Fix

`openTargetPicker` now synchronously calls the existing `beginTargetResolution(viewer)` on the caller's entity thread, which cancels old note/search prompts and invalidates history/preparation tickets while minting a viewer- and source-inventory-bound target ticket. Its queued entity callback **consumes that original ticket against the current top inventory before** calling `openTargetPickerPage`. Any newer target lookup, typed command, picker command, inventory switch, or close invalidates the old callback. `openTargetPickerPage` retains its normal invalidation behavior for a legitimately admitted page.

No punishment reason, duration, rank, approval or Discord logic was changed.

## Red-to-green proof

A new `PunishmentGuiTargetResolutionNavigationTest.queuedTargetPickerCannotReplaceNewerTypedCommandOrDifferentInventory` exercises the fence's old-vs-new command and source-inventory identity, plus the controller's prequeue mint / postqueue consume order. **Before implementation**, the test failed with `Picker request must capture navigation identity before scheduling its page`. After implementation, it passed.

Two older source-contract tests were updated to accept immediate chat invalidation **through the existing shared `beginTargetResolution` helper**, instead of insisting on duplicated inline `searchCaptures.remove` / `preparationFence.invalidate` statements. Their original safety assertions remain in force on the shared helper. A full-suite run found and corrected the second source-contract expectation; the final full run is green.

## Fresh offline verification (Windows)

`gradlew.bat --offline --no-daemon --rerun-tasks :domain:test :paper:test :staff-bot:test :velocity:test :integration-tests:compileTestJava :paper:shadowJar`: **BUILD SUCCESSFUL** in 1m 10s; **30 executed** Gradle tasks.

| Module | Tests | Skipped | Failures |
|---|---:|---:|---:|
| Domain | 347 | 0 | 0 |
| Paper | 1,386 | 0 | 0 |
| StaffBot compatibility | 523 | 14 | 0 |
| Velocity | 234 | 1 | 0 |
| **Total** | **2,490** | **15** | **0** |

**2,475 passed**, 15 platform-specific skipped, zero failures. Focused target-resolution, private-chat preparation and search-capture navigation suites also passed.

Locally built shaded Paper JAR `paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`: **11,554,736 bytes**; SHA-256 `87bf6f0bffaac2a87794e6a440bdd0472e129e175c38ea467e900e45984f15a4`. Packaged `config.yml`: `guided-punishment.enabled: false`.

## Release gates still NOT met

The Java integration test module was **compiled**, not executed against MariaDB. Windows/offline JUnit does not establish Folia/Paper Linux runtime scheduling, Java/Bedrock/Geyser GUI behavior, live staff rank/permission behavior, customized production `reason-policies.yml` or rollback readiness. Owner severity-policy calibration issue #616 remains unapproved. No merge, deployment, server restart, database write, Discord posting or live config change. Guided punishment mode remains OFF.
