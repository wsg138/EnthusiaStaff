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


## Owner continuation: transition recovery, 2026-10-25 UTC

The owner assigned the diagnosed Staff Mode transition repair to the existing
MARKET-02 / PR #647 lane. Work is isolated in `EnthusiaStaff-transition-20261025`
on `package/market-02-transition-recovery`; the existing PR head `1d44dc533fda`
and canonical main `958e2ace71358bf57e7f60e261eb3c7a1fe60870` were reconciled by
a normal merge. No other checkout was reset. This source slice does not authorize
live recovery, ranks, TEST/client use, deployment, restart, or product merge.

### Transition-recovery requirements and proof

- A rankless ACTIVE detached lease shall retire only after the store compares its
  exact session, revision, detached ownership, state and checksum under the same
  row lock used by entry/rebind. A detached marker represents the source backend's
  completed restoration verification. Destination inventory shall never be
  replaced with that source snapshot.
- A rejected/stale/failed retirement shall preserve the snapshot and recovery
  fence. Durable close, audit and outbox publication shall commit atomically.
- A normal enter or exit request during a retryable hold shall invoke durable
  recovery rather than fresh snapshot capture. A genuinely owned in-flight
  attempt shall not be stolen. Recovery can resume ACTIVE intent; it is not an
  unconditional forced-exit command.
- Deferred or rejected entity work shall retain retryability, and stale callbacks
  shall not clean inventory or clear a newer attempt. Cleanup after committed
  detached closure shall retry using durable absence without replaying snapshots.
- Durable vanish-clear recovery shall not apply an old selected gamemode while
  Staff Mode owns an active session or transition. Existing manual mode, rank,
  inventory, permission and vanish-preference semantics remain unchanged.

Two manager regressions failed on the pre-fix behavior: rankless detached
retirement was never scheduled. The initial fixture instead raised an empty-queue
exception; that run is not counted as behavioral red proof. Corrected assertions
produced 17 tests with two expected assertion failures. The first implementation
passed the focused manager/gate/detached suites; later refinements add stale lease,
stale callback, rejected scheduling, held-enter/exit and in-flight protection.
These synthetic tests use no private snapshot or player identity. MariaDB tests
cover exact close, stale revision and destination rebind; database execution and
hosted results must be recorded separately from compilation and local units.

### Transition-recovery engine and architecture

`StaffSessionStore.retireDetached` is a default fail-closed capability for older
adapters. JDBC implements it transactionally without a schema or migration change,
retaining snapshot bytes and history; it does not enable auto-commit after an
uncertain rollback. Paper schedules only player-owned cleanup after commit and
notifies the existing exit listener afterward. Recovery callbacks carry opaque
attempt identities. The existing orphan-tool repair and its regressions remain.

Project-local EARS/state helpers and the conventional four SPEAR documents are
absent; this requirement/task/evidence section is the bounded record. No tooling
pass is invented. Source review, local unit/build checks, MariaDB execution,
hosted exact-head gates, TEST activation and real client acceptance are distinct.
Status remains REVIEW / ACTIONABLE_CONTINUATION until the exact-head gates and
hosted review and runtime acceptance gates are resolved. Raw runtime evidence stays
private and is not committed or copied into the public PR.


### Local validation checkpoint

Final clean Java 25.0.3 `clean test check runtimeJars
:integration-tests:compileTestJava -x :integration-tests:test` completed with
2,678 reported tests, zero failures/errors and 16 explicitly skipped cases.
The manager recovery suite has 24 passing cases and recovery gate has seven;
none of those 31 focused cases skipped. All existing orphan-tool checks remain.
Wiki validation passed for 41 pages; `git diff --check` passed. MariaDB tests
compiled but did not execute locally: Docker is absent. Hosted database,
exact-head static/review, TEST and client gates remain open. The local Paper and
Velocity JARs are unmerged/local test artifacts, not approved deployment inputs.
No private operational evidence was added to the public source tree.


### Publication ownership handoff

The owner explicitly confirmed this Staff chat taking over publication to existing
PR #647 on 2026-10-25 UTC. Roundtable records the matching handoff under MARKET-02.
No competing PR is created. Executable local validation belongs to `2f11622c`;
this ownership/checkpoint amendment changes documentation only. Existing fork
head `1d44dc533fda` and canonical base `958e2ace` were rechecked unchanged before
publication. Hosted results must be attributed to the resulting exact PR head;
old green checks do not certify this continuation. Merge, deployment and live
recovery remain unauthorized.


### Publication-head review refinements

Codacy on `e0c38aaa` reported five findings: a duplicate heading, oversized test
file, repeated recovery-gate and exit-event literals, and JDBC method complexity.
The refinement renames the continuation heading, extracts the unchanged synthetic
scheduler/inventory fixture into a package-local test helper, and decomposes JDBC
validation/transaction/state-update operations while retaining the row lock and
single commit. No analysis rule is suppressed or weakened. All 31 manager/gate
regressions pass after this refactoring; integration test compilation passes.
New database checks read the persisted snapshot bytes/checksum after closure and
inject an audit-insert failure to prove closure/revision rollback when executed.
The unavailable local Docker gate is not retried or reported passing. Updated
hosted results must certify the new executable/test head independently.


The refined executable/test tree passed another clean local Java 25 run of
`clean test check runtimeJars :integration-tests:compileTestJava` with local
`:integration-tests:test` explicitly excluded. All 2,678 tests were reported
again with zero failures/errors and 16 disclosed skips; all 31 focused recovery
cases passed. Wiki/diff validation passed. Database execution remains a hosted
gate, not a local pass. No additional live permissions or operations were used.


### Rankless destination join continuation (2026-10-28 UTC)

Owner-directed continuation of the same MARKET-02 / PR #647 lane, not a new
package. Starting product head a38002e95791, canonical main
9efaa05699f78f1f1133d13117fd033fef99cb39 incorporated by normal merge without
conflicts; prior name/vanish and durable retirement repairs are retained.

Requirement SJ-01: consuming a destination handoff hint must not bypass durable
recovery when the destination has no explicit staff rank. The hint is advisory,
not a grant of Staff authority. Eligible detached ACTIVE, EXITING and
RECOVERY_REQUIRED leases must use existing fenced retirement and preserve saved
snapshot bytes and ordinary destination inventory. ENTERING remains held, storage
unavailability stays retryable, and rejected/stale closure must not remove tools.
No new SQL, migration, permission, platform API or companion integration is added.

Proof: five executable event-path regressions failed on the pre-fix merged tree
at the expected missing-work/hold assertions. Earlier test-scaffold compile
failures are not behavioral red evidence. Removing the rank-null early return
lets the existing recover(playerId, rank) call handle the durable state; existing
handoff abandonment and rank-unavailable message remain. The first focused
recovery/gate/policy/join-ordering run passed; final full checks are pending on
the frozen source head. An additional rejected-retirement event-path case checks
inventory/snapshot preservation. No EARS/state helper is available; this is the
small requirement/task/evidence record rather than a claimed tooling pass.

Task: exact-head local/hosted validation and source review, then publish the
same PR. TEST/client recovery remains separate, with exact durable-state evidence
still required under SHARED-02. No live recovery command, inventory/rank mutation,
TEST use, deployment/restart, merge-to-main or production action in this slice.


SJ-01 local validation at executable head
4c80022121b7f34c32399b7e0887953ed0aef389 passed Java25 clean test/check,
runtimeJars and integration-test compilation, with local database execution
explicitly excluded because its unchanged Docker prerequisite is unavailable.
Reports contain 2,688 tests, zero failures/errors and 16 disclosed skips;
41 focused recovery/policy/join-ordering cases passed without skips, including
all six new join-event cases. Wiki41 pages and diff checks passed. Build reused
29 valid cached tasks; 35 tasks executed. The synthetic post-fsync exception is
the intentional existing crash/reopen durability probe, whose recovery passed.
This follow-up changes tracking files only; hosted results remain pending for
the published exact PR head, with no inference from the earlier a380 checks.
Manual review found no new API, persistence, permission or migration change;
no independent human review or real client recovery acceptance is claimed.


### Read-only offline session evidence (2026-10-28 UTC)

Owner-directed same-lane continuation, requirements SD-01..05. Isolated worktree
EnthusiaStaff-session-diagnostic-20261028 starts at current main9efaa056 then
fast-forwards the existing PR647 head9a09. No existing worktree is overwritten.

SD-01: a hard console-only diagnostic accepts exactly one canonical UUID,
requires no player connection, and performs no recovery, session, snapshot,
player, rank, inventory, file, configuration or authority mutation.
SD-02: a fixed bounded read-only consistent JDBC query uses that UUID, LEFT JOINs
the snapshot and checks at most two non-closed rows. Missing snapshot, duplicate
session, invalid length/metadata or mismatched digest fail closed. No history or
other-player query, arbitrary SQL, explicit row lock or credential readback.
SD-03: only safe metadata is returned: source/runtime/session identity, state,
revision, schema, checksum/length, verified header backend and observation time.
No snapshot bytes/items, database endpoint/credentials or provider errors.
Header plus digest integrity is not full payload/player restoration validation
or recovery authorization; pending proxy handoff ownership is not inspected.
SD-04: use the existing bounded worker pool and reply scheduler; reject overload,
keep one inspection running, query timeout, sanitize metadata and fail closed on
missing providers. Reuse the owned application's pool without changing TLS or
introducing an external database connection or promising its negotiated TLS.
SD-05: preserve existing source fixes/options/APIs with a default unsupported
port method. Build an exact-source explicitly UNMERGED-TEST local artifact for
review; no deployment, shared TEST/client/DB invocation or production action.

Prove/engine/refine pending: synthetic JDBC, console/header tests and isolated
MariaDB fixture when its gate is available; current unavailable local Docker gate
is not retried. Existing EARS/state helpers remain absent; this requirement/task/
evidence record is the bounded SPEAR record. Local source/build ownership is
Roundtable1837, with current compute owner's explicit nonoverlap acknowledgment1835.


Diagnostic proof checkpoint: seven synthetic JDBC feature-contract tests failed
against the default unsupported inspection scaffold, then passed with the fixed
bounded reader. This is new-feature contract red evidence, not a fabricated
historical bug reproduction. Seventeen focused JDBC/header/console cases now
pass, including query failure rollback, missing/ambiguous/mismatched rows,
invalid header/backend/digest, hard console gating, canonical UUID checks,
overload and worker rejection. Broader Paper suite initially correctly rejected
the new root command in its exact surface contract; explicitly added only the
new hard-console command to the reviewed surface and no-outer-permission set,
following the existing console-only staffapi pattern. Existing permissions and
allowlist equality checks remain. Paper/persistence suites and integration-test
compilation passed before the last manifest-only refinement; full final checks
and exact-source labelled artifact pending. One real MariaDB inspection fixture
checks detached lookup, unchanged session revision/bytes and missing-snapshot
failure; it is compiled locally, with execution left to the existing hosted gate
because local Docker remains unavailable. No live database was used as a stand-in.


Privacy review refinement: the unpublished initial reader privately fetched a
bounded full snapshot to hash. Final reader instead returns metadata plus at most
74 header-prefix bytes; SHA2 is computed inside the read-only SQL query only for
snapshots within the 8MiB limit. The diagnostic result has its own non-restorable
metadata type; it is never passed to restoration. Constructor/schema/header and
owner checks remain separate from full payload validation, explicitly not claimed.
The real MariaDB fixture also changes only its disposable blob to prove digest
mismatch rejection, then removes its disposable snapshot to prove LEFT JOIN does
not convert missing evidence into absence. No live data was involved. The first
full Java25 build passed before this privacy refinement; final checks must apply
to the refined executable head. No earlier artifact is a delivery candidate.


Exact520 Codacy found six valid quality issues: repeated test literal, a test array
created inside a loop, three test proxy class-loader sites, and catching NPE in
the reader. Refinement uses a constant, reuses the test arguments, uses the test
thread context loader and validates required metadata explicitly before conversion.
Three missing-field regressions preserve fail-closed behavior without catching
NPE. No analysis rule is suppressed or weakened. Earlier520 artifact remains a
non-ready intermediate candidate; new executable head requires validation/build.
