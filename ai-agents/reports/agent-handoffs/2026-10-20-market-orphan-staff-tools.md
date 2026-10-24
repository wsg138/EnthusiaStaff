# Market acceptance follow-up: orphan Staff tools

## Scope and routing

The owner requested a Roundtable continuation of the remaining Market work from Codex
session `01a11918-66d6-75b0-ac6c-5399eebc1b00`. The coordinator assigned the bounded
Staff prerequisite investigation as Roundtable `MARKET-02`, task
`01a15971-3951-7c28-b479-442dd8d04119`. This record covers that repair only; it does
not replace the aggregate package registry, start another historical package, or
claim completion of the Market acceptance campaign.

- Authoritative repository: `wsg138/EnthusiaStaff`.
- Starting main: `c945b92e088fe9d1fc326284d53df47799a6d128`.
- Isolated branch: `package/market-02-orphan-staff-tools`.
- Related existing PR: #476, inspected at
  `c898a2621a5b2ebaff7cba11d5049228518c1e21`. Its detached-session ownership and
  retry-ticket changes do not remove orphan tools in the no-session or verified
  local-exit paths. Its changes remain separate.
- Production access, deployment, inventory reset, permission changes, and client
  mode changes are outside this repair. No such operation was performed by this
  worker.

## Spec

1. When a successful durable lookup returns no Staff session, recovery shall
   remove only items carrying this plugin's `staff_tool` string tag from the
   player's inventory and cursor, on the player's entity scheduler, before
   releasing the recovery fence.
2. While storage is unavailable, the lookup fails, or recovery belongs to an
   older connection/attempt, recovery shall not interpret the uncertainty as
   permission to remove tools or release a newer fence.
3. When a saved snapshot has been restored, verified, and durably closed,
   cleanup shall retire ephemeral Staff tools that were already embedded in the
   saved inventory. It shall preserve the original saved bytes and checksum and
   every ordinary item, including vanilla items of the same material.
4. When a later active or pending local session replaces the closing session,
   the older cleanup callback shall preserve the newer session and its tools.
5. If cleanup cannot execute after closure, the local state shall remain
   retryable. A subsequent recovery shall read the durable absence of an active
   session and shall not replay an already closed snapshot.

## Prove

Source inspection established two connected paths in the starting main:

- `recover` cleared its gate and returned after `store.active` returned empty,
  leaving Staff-tagged inventory items intact. The tool dispatcher then denied
  the tagged item's interaction because no current session token existed.
- Fresh entry captured the existing inventory unchanged. Exit removed current
  tools before restoring the saved inventory; a snapshot already containing
  orphan tools therefore reintroduced them.

The regression invokes the actual manager against synthetic store, player,
inventory, and queued worker/entity schedulers. It is not a source-text test.
The post-restoration tests enter at the verified-checksum completion boundary;
they test closure and cleanup ordering, not Paper's item serialization or the
full player-state codec. No private player snapshot is used.

The first baseline run reached JUnit but failed in the test adapter because
Paper's `Plugin` interface also exposes `Namespaced.namespace()`. That run is
invalid as product-red evidence. The adapter was corrected before rerunning.

The corrected baseline run completed on Java 25.0.3 at
`2026-10-20T13:31:49.597Z`: 10 manager tests, 4 failures, 0 errors, 0 skips,
11.6 seconds. The four failures demonstrated the orphan inventory/cursor,
premature inactive-fence release, contaminated verified snapshot, and failed
cleanup retry defects. The other six safety cases passed. This is the valid
behavior-red checkpoint; later candidate tests add the independent review
regressions without replacing that evidence.

## Engine and architecture

- Keep the no-session transition fence until entity-owned cleanup completes.
- Give each recovery attempt an identity invalidated by retry or clear, so an
  old callback cannot claim a replacement attempt. Acquire and complete the
  expected attempt atomically with the bounded player cleanup; invoke external
  exit listeners only after the fence has been released.
- Wait for `completeExit` to succeed before cleaning a restored inventory, and
  retain the exact closing local session until the entity callback has verified
  it. A rejected or throwing scheduler leaves it retryable, and a later durable
  absence read cleans that known closed session without replaying its snapshot.
- Remove Staff-tagged cursor items using the same existing plugin tag predicate.
- Preserve the public `JavaPlugin` constructor signatures. Package-local
  `Plugin` constructor overloads permit behavioral testing with real scheduling
  boundaries and no server bootstrap.

No database schema, snapshot format, historical migration, command, permission,
tool authorization, or visual menu changed. Tagged items remain unusable outside
their active session; this repair does not bypass that authorization check.

## Refine and validation status

- `git diff --check`: passed locally.
- Corrected Java 25 baseline: 10 tests, 4 behavior failures, 0 errors or skips.
- Candidate focused execution: Java 25.0.3, 21 tests passed (15 manager and 6
  recovery-gate tests), 0 failures, 0 errors, 0 skips; report timestamp
  `2026-10-20T13:46:10Z`, 36.4 seconds. The coordinator applied the exact reviewed
  production patch to a fresh isolated checkout of the starting main.
- Independent production and focused-test review: no remaining actionable
  findings on patch SHA-256
  `94d40167924758927cd7fc06ba0d68abcf0aa78858991c2aa09808242f7865e0`.
  The independent reviewer identified and verified fixes for throwing entity
  schedulers, non-atomic check/clear races, global callbacks removing replacement
  state, and exit-listener reentrancy. The reviewed candidate has 15 manager
  scenarios and 6 recovery-gate tests.
- Existing restoration/audit source-marker tests now follow the new completion
  helpers while retaining durable-closure, exact-session, cleanup, and success
  notification ordering assertions. Their test-only patch SHA-256 is
  `c08384f0ddfc932a930dc9e185a3df287882143fb7e9ad728d36ddd993196dbb`.
  Candidate production and the 21 focused tests are byte-identical to the
  independently reviewed patch. Independent review also found no actionable
  findings in this test-only delta.
- Staff package lifecycle group: 273 tests passed, 0 failures, 0 errors, 0 skips,
  Java 25.0.3, 15.2 seconds; JUnit timestamps
  `2026-10-20T13:47:01Z`–`2026-10-20T13:47:02Z`. This total includes the 21
  focused manager/gate tests. The run used both reviewed patches. Project
  compilation enables `-Xlint:all` and `-Werror`.
- Commands executed by the coordinator from the isolated Windows candidate:

  ```text
  gradlew.bat --no-daemon :paper:test --tests net.enthusia.staff.paper.staff.StaffOrphanToolRecoveryTest --tests net.enthusia.staff.paper.staff.StaffModeRecoveryGateTest
  gradlew.bat --no-daemon :paper:test --tests net.enthusia.staff.paper.staff.*
  ```
- Current TEST artifact provenance and current durable mode state remain
  unverified. The observed client symptom and the source regression do not prove
  that this candidate has run on those clients.
- Full repository, hosted CI, staging, Java/Bedrock/Folia runtime acceptance,
  merge, and deployment: not performed by this worker.

The coordinator inspected the installed Minecraft development skill and found
no separate project SPEAR/EARS validator or state helper. This bounded
requirement/task/evidence record follows spec → prove → engine → architecture →
refine without claiming an unavailable helper passed.

## Exact next action

Continue review of PR #647 on the existing branch, preserving PR #476 and
unrelated package state. Complete applicable full repository and hosted checks
before any merge. Map the TEST Staff artifact to its source before attributing
the live symptom or planning separately authorized TEST activation. The branch
does not claim Market client acceptance, a merged repair, or runtime activation.

## Codacy refinement of PR #647

### Spec and evidence

The owner authorized the coordinator's next work, including resolution of PR
#647's Codacy findings. This follow-up starts from the clean implementation
commit `ed023bcf79527f3cccb581a4fbc8ce04bfe2d9ff` and remains within MARKET-02.
The coordinator retrieved all 17 exact GitHub annotations on that head. The
annotation JSON SHA-256 is
`e442710b4f2c14c72c1f8356f36ad98e3d17729900efb23bc0b645c1a77f9f9e`.

The refinement must preserve exact attempt identity, atomic ownership checks
through cleanup and fence release, entity ownership for inventory mutation,
post-completion listener timing, and all saved-state safety conditions already
specified above. Static-analysis remediation must not replace those contracts
with value equality, expose mutable production state for test convenience, or
relax repository-wide rules.

### Finding dispositions and implementation

Original line numbers below refer to `ed023bcf`.

| Location | Count | Finding | Disposition |
| --- | ---: | --- | --- |
| `StaffModeManager.java:575` | 2 | Cyclomatic complexity 15 and NPath 291 | Extract scheduler dispatch, entity completion, guarded cleanup, and retirement handlers. Cleanup remains inside `recoveryGate.complete`; listener notification and entry remain afterward. |
| `StaffModeRecoveryGate.java:22,32,36,40,44,52,60,68,75` | 9 | Method-level synchronization | Use one private final `attemptLock` for block synchronization. Every original critical section remains intact, including nested ownership/cleanup/clear operations. |
| `StaffModeRecoveryGate.java:41` | 1 | Prefer object value equality | Intentional identity capability. Preserve `==` and narrowly suppress only `PMD.CompareObjectsWithEquals` on `owns`, with an explanatory comment. |
| `StaffOrphanToolRecoveryTest.java:106` | 1 | Repeated `active` string | Replace generic repeated field-name lookups with the fixture's `activeSessions` helper. |
| `StaffOrphanToolRecoveryTest.java:346` | 1 | Unused store-call arguments | Assert that the durable absence query addresses the affected player, strengthening the existing test adapter. |
| `StaffOrphanToolRecoveryTest.java:425,440` | 2 | Reflection changes accessibility | Fixed-target synthetic fixture access to this manager's private completion boundary and state. Narrowly suppress `PMD.AvoidAccessibilityAlteration` on those two test helpers only. No user input or production call path reaches them. |
| `StaffOrphanToolRecoveryTest.java:478` | 1 | J2EE context-class-loader advice | This is a Paper/JUnit fixture outside J2EE. Preserve the interface's defining loader so the generated proxy can see that interface; narrowly suppress `PMD.UseProperClassLoader` on the proxy helper. |

The four context-specific suppressions are method-scoped and name exact rules.
The identity disposition follows an existing repository pattern in
`StaffToolSpectateFlow`. No class/file exclusion, Codacy configuration, quality
gate, production visibility, or authorization rule was changed. The security
category annotations concern test reflection; they do not establish an
exploitable production path.

### Architecture review and validation

The independent reviewer agreed with the bounded plan and the four contextual
dispositions, then reviewed candidate patch SHA-256
`9d7381d1b5b1fecaa6cd81adf19637e26296bf72391305e1da146600409cb483`
with no remaining actionable findings. The source/test-only patch SHA-256 is
`3c2e8981cf13413848ef668b45626d48864124a8779699c99013a5886885f554`.
The coordinator's Java 25.0.3 lifecycle run on that frozen candidate passed all
273 tests with 0 failures, 0 errors, and 0 skips in 38.0 seconds. Its first JUnit
timestamp is `2026-10-20T17:01:22.479Z`. The command was
`gradlew.bat --no-daemon :paper:test --tests net.enthusia.staff.paper.staff.*`.
The existing concurrency race test and listener-reentrancy test remain
unchanged. The original RED and prior 273-test GREEN evidence above is preserved
separately. `git diff --check` passes locally.

Local Lizard is unavailable. Actual hosted Codacy reanalysis remains required;
the source refactor alone is not recorded as a passing Codacy result.

### Hosted follow-up: class-loader rule identifier

The published refinement head `d100e6b6748880279591645a3bbc5fff2dcee038`
received one remaining annotation in [Codacy check 118769023867](https://github.com/wsg138/EnthusiaStaff/runs/118769023867),
completed at `2026-10-20T17:09:27Z` with `action_required`. The annotation at
`StaffOrphanToolRecoveryTest.java:486` repeats the J2EE class-loader advisory.
The prior annotation used `PMD.J2EEUseProperClassLoader`, which is not the
[documented PMD rule name](https://docs.pmd-code.org/latest/pmd_rules_java_errorprone.html#useproperclassloader).
The correction changes that one method-scoped identifier to
`PMD.UseProperClassLoader`; the defining class loader, test behavior, and
production code remain unchanged. Hosted reanalysis on the corrected head is
still required before claiming this finding is cleared.

PR #647 became ready for review at `2026-10-20T17:11:26Z`.
[CodeRabbit's comment](https://github.com/wsg138/EnthusiaStaff/pull/647#issuecomment-6292435605)
reports that automatic review is unavailable for this repository. Its status
description is `Review skipped: manual review required for this OSS repository`,
updated at `2026-10-20T17:11:50Z`; the successful status flag is not an executed
review. No CodeRabbit review submission or inline review comment was present.
No trigger comment was posted. Independent source review and hosted build/test
checks remain separate evidence.
