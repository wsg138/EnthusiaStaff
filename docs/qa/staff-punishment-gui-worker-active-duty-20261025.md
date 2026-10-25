# Punishment GUI queued worker active-duty recheck — 2026-10-25

**Status: DRAFT / offline QA / NO-GO.** Based on PR #728, exact source head
`9174ef78687bc6eebd5ac08a492e4dc972c916c8`. No production mutation.

## Reproduced gap

GUI inventory clicks use `authorizedActor(viewer)` to check the current Paper
staff identity and active-duty policy. The GUI then queues draft preparation,
guided assessment, note/visibility re-preparation, or confirmation on a worker
with that captured `Actor`. Before this change, these worker entrypoints
rechecked target hierarchy, but not the `ActiveDutyAuthorizationPolicy`
injected by `PaperCommandRegistrar`. If Staff Mode was disabled while queued,
the actor could still be passed into draft preparation/confirmation. This is
distinct from the stale **rank** issue in PR #728.

## Change

- Share a single fail-closed `currentWorkerAuthority` predicate between
  initial GUI authorization and worker rechecks. It verifies viewer UUID,
  actor presence, and either ISSUE or REQUEST permission through the injected
  active-duty policy; request-only developers remain permitted while on duty.
- Recheck at the start of queued expert preparation, guided assessment,
  re-preparation and confirmation. Recheck again immediately before the
  corresponding workflow persistence/confirmation call.
- Preserve existing target hierarchy and guided-feature gates; no policy
  recommendation, staff rank mapping, command route, or server config changed.

## Red-to-green and full offline verification

The two new `PunishmentGuiWorkerAuthorityTest` tests **failed before the
implementation**: a missing worker authority predicate and absent worker-site
checks. After implementation, the tests pass. The runtime policy test toggles
`ActiveDutyAuthorizationPolicy` off between an authorized click-equivalent
and a queued check; verifies the worker predicate denies after deactivation,
denies mismatched viewer IDs and null actors, and preserves active MOD and
request-only DEVELOPER authority. The companion source-contract test checks
all four queued mutation entrypoints for the recheck.

Fresh Windows/offline `gradlew.bat --offline --no-daemon --rerun-tasks
:domain:test :paper:test :staff-bot:test :velocity:test
:integration-tests:compileTestJava :paper:shadowJar`:
**BUILD SUCCESSFUL, 30 tasks executed, 1m13s**. Results: domain 347 pass;
Paper 1,398 pass; StaffBot 509 pass/14 skipped; Velocity 233 pass/1 skipped.
**2,487 pass, 15 environment-specific skips, zero failures.**
Java integration test sources **compiled**, MariaDB-backed tests **not run**.

Local shaded Paper JAR: **11,555,521 bytes**, SHA256
`cb5a0a643d6466134676c3598b1bbbdb22903c26158f7576f5436a9d5e6708f1`.
Packaged `guided-punishment.enabled: false`.

## Remaining release gates

These checks narrow the time-of-check/time-of-use window but **do not**
make Staff Mode or LuckPerms permission changes atomic with a database
commit. Source-contract tests do not replace live Paper/Folia scheduling,
Java/Bedrock/Geyser GUI, live rank/Staff Mode transitions, production custom
reason policies, MariaDB, rollback rehearsal or owner severity calibration
(#616). **Do not merge, deploy, enable guided mode, alter production config,
restart servers, or post Discord** without explicit owner approval.
