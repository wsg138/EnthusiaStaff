# Codacy and validation baseline

Captured: 2026-07-27 (America/Indianapolis)

This report records the state of `agent/complete-staff-platform` at pushed commit
`c4fd4129f7a34ad011f87f146fb72c236e611b89` before the current remediation.
It is a baseline, not a claim that the branch is clean.

## Codacy branch analysis

| Measure | Baseline |
| --- | ---: |
| Codacy grade | B |
| Numeric grade | 78 |
| Open issues | 846 |
| Lines of code | 40,488 |
| Issue percentage | 19% |
| Complex files | 71 (18%) |
| Duplication | 10% |
| Last analyzed commit | `c4fd4129f7a34ad011f87f146fb72c236e611b89` |

Configured quality goals were a maximum 20% issue percentage, maximum 10%
duplication, minimum 60% coverage, and maximum 10% complex files. Meeting an
aggregate threshold does not make an individual correctness or security finding
acceptable.

### Issues by severity

| Severity | Count |
| --- | ---: |
| Error | 21 |
| High | 190 |
| Warning | 635 |

### Issues by category

| Category | Count |
| --- | ---: |
| Complexity | 309 |
| Error-prone | 268 |
| Compatibility | 133 |
| Performance | 77 |
| Best practice | 30 |
| Security | 29 |

### Issues by tool

| Tool | Count |
| --- | ---: |
| PMD | 516 |
| Lizard | 255 |
| SQLint | 38 |
| Opengrep | 29 |
| TSQLLint | 8 |

### Largest pattern groups

| Pattern | Count |
| --- | ---: |
| Lizard cyclomatic complexity | 131 |
| Lizard method length | 94 |
| PMD `AvoidFieldNameMatchingMethodName` | 93 |
| PMD `DoNotUseThreads` | 93 |
| PMD `AvoidLiteralsInIfCondition` | 80 |
| PMD `AvoidDuplicateLiterals` | 69 |
| PMD `NPathComplexity` | 45 |
| SQLint parser findings | 38 |
| PMD `UseConcurrentHashMap` | 37 |
| PMD `AvoidSynchronizedAtMethodLevel` | 19 |
| Lizard file length | 16 |
| Lizard parameter count | 14 |
| PMD `NullAssignment` | 12 |
| PMD `AvoidInstantiatingObjectsInLoops` | 12 |

The highest issue-count files were the Velocity bootstrap (57),
`JdbcInventoryJournalStore` (47), `ConfiscationCoordinator` (45),
`WebsiteApiServer` (43), `JdbcWebsiteModerationStore` (33),
`JdbcEconomyJournalStore` (32), and the Paper bootstrap (30).

## Pull-request analysis

Draft PR: <https://github.com/wsg138/EnthusiaStaff/pull/1>

| Measure | Baseline |
| --- | ---: |
| New issues | 846 |
| Fixed issues | 0 |
| Delta complexity | 5,605 |
| Clone fragments | 236 |
| Clone groups | 118 |
| Quality result | Not up to standards |

GitHub displayed only capped subsets of the annotations and bot summary. The
Codacy API was paged until all 846 issues were obtained, so the API totals in
this report are authoritative for the baseline.

CodeRabbit did not produce review findings. Its check succeeded only because
review was skipped for the draft pull request; that status is not an independent
code-quality pass.

## Security baseline

The 29 security-category results comprised:

- six dynamic/formatted SQL findings in production persistence code;
- six dynamic/formatted SQL findings in migration or integration-test code;
- five unrelated `RAC_*` table-policy findings from a generic SQL rule;
- three dependency coordinates misidentified as API keys;
- three AES-GCM review findings;
- three SSRF findings on server bind or administrator-configured addresses;
- two unencrypted-socket findings on the authenticated persistent channel;
- one hard-coded cryptographic-key finding in test code.

No security issue is being ignored merely to reduce the total. The remediation
must either remove the unsafe source-to-sink path, prove the path is not
exploitable and record that proof with the suppression, or leave the finding
open for review.

## Direct Codacy CLI analysis

Codacy CLI `1.0.0-main.380.sha.27e119a` was installed into the local ignored
`.codacy/` directory. Live repository settings were fetched for
`gh/wsg138/EnthusiaStaff`.

The Codacy integration wrapper attempted to invoke
`.codacy/cli.sh analyze 'undefined'` through WSL. That wrapper call failed
because it passed the literal tool name `undefined`; invoking the installed CLI
directly through WSL works.

| Local tool | Raw results | Execution state | Notes |
| --- | ---: | --- | --- |
| PMD 6.55.0 | 557 | Partial | Four Java 21 pattern-switch parser failures and one MariaDB SQL parser failure prevented a complete run. |
| Lizard 1.23.0 | 257 | Complete | 132 cyclomatic-complexity, 94 method-length, 17 file-length, and 14 parameter-count results. |
| Opengrep 1.23.0 | 24 | Complete | Six `RAC_*`, four AES-GCM review, three false secret, six SQL, three SSRF, and two socket-encryption results. |
| Trivy 0.70.0 | 0 | Complete | Vulnerability and secret scanning both enabled. |

SQLint, TSQLLint, Agentlinter, markdownlint, and detekt were enabled in cloud
analysis but are not supported by this local CLI runtime. Cloud API results
remain authoritative for those tools.

The SQLint findings are PostgreSQL-parser errors on valid MariaDB constructs
such as `UNSIGNED`, `ENGINE=InnoDB`, `INSERT IGNORE`, `KEY`, `AFTER`, and
`MODIFY`. The TSQLLint findings require SQL Server-only session statements such
as `SET NOCOUNT ON` and `SET QUOTED_IDENTIFIER ON`. Those two tools do not
analyze the repository's MariaDB dialect correctly. MariaDB migrations must
instead be validated against the actual supported database version.

## Build and Docker baseline

The initial clean Windows build used the repository's Java 21 Gradle toolchain:

```text
gradlew.bat --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

It completed successfully with 39 tasks. Test XML contained 108 tests:
102 passed and six were skipped because Docker was unavailable. The skipped
tests were four asset-journal tests, one LiteBans migration test, and one
punishment-draft test; they are not recorded as passed.

The resulting runtime artifacts were exactly one Paper jar and one Velocity
jar:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,030,908 | `4B46310E7AEB70A8275C7F98049F873AE12FE8961B31D357BD99E53305C497C2` |
| Velocity runtime jar | 7,321,357 | `902329B92ED5A6DEC13F7036AF6B09D3C56B6EE08D6B520F8D034B18BD6BF7EF` |

Both jars contained their expected plugin metadata and contained no provider
API classes from `integration-contracts`. No private Polar jar was packaged.
The artifacts were subsequently removed by `clean`; final hashes must be
captured again after remediation.

Docker Desktop installation downloaded successfully but could not complete
without an administrator elevation prompt. As a non-interactive fallback,
Docker Engine 29.1.3 and OpenJDK 21.0.11 were installed inside Ubuntu 24.04
WSL2. A real `hello-world` container ran successfully.

The first WSL Gradle run still skipped Testcontainers tests. Removing the
skip-on-missing-Docker behavior exposed the cause: Testcontainers 1.21.3 used
Docker API 1.32, while Docker Engine 29 requires at least API 1.44. The
Testcontainers compatibility update and the first real MariaDB migration result
belong to the remediation checkpoint, not this baseline.

## Remediation checkpoint

PR #1 was merged into `main` with merge commit
`b5e55ed9ffd7309cacabf6b0a07af220068f3c30`. The feature history and source
branch were retained.

The clean, cache-disabled Java 21 build completed all 39 tasks. Test XML
contained 108 tests with zero failures, errors, or skips, including all six
MariaDB Testcontainers tests.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,033,006 | `69348CB2DBB57FF0E8308CC670E377104BCAC2004CD27BB1558A7130ED7B7B15` |
| Velocity runtime jar | 7,322,431 | `2B18C229F4AC4BEEEF80C24B904AAD3262EE8E398DCCA9E7763B588D22A3E44A` |

The checked-in PMD ruleset parses all Java source and currently returns 303
raw findings, down from 557 partial results. Opengrep returns 19 raw findings:
18 remain actionable or pending review, and one exact self-comparison report is
suppressed in source because the analyzer misread
`checksumMismatches == 0` as a comparison of the variable to itself. The two
unencrypted-socket reports remain active.

The final feature-branch cloud run reported 600 active issues, down from the
846-issue recovery baseline. After the merge, the Codacy PMD setting was
changed and read back successfully with `hasConfigurationFile=true` and
`usesConfigurationFile=true`. A subsequent `main` analysis is required before
the configured-ruleset count is authoritative.

CodeRabbit attempted to review PR #1 after it left draft state, but its
100-file limit rejected the 397-file recovery diff. It produced no code review
findings. Future section branches must keep review diffs below that limit where
practical.

Portable validation and cleanup instructions are in `docs/development.md`.
Exact workstation package versions, locations, and removal commands are kept
in the ignored `.codacy/LOCAL-TOOLING.md` log so machine-specific paths do not
enter public documentation.

## Plugin security checkpoint

The `section/plugin` branch reached commit
`a9636abdae9ea81d5b5a1e3230368dc8dd6e76dd` in
[PR #2](https://github.com/wsg138/EnthusiaStaff/pull/2). Codacy reported zero
new issues, 26 fixed issues, zero clone delta, and no potential issues. The
branch's only active security-category results are three AES-GCM nonce-review
findings in `NetworkIdentityProtector`; they remain open because they are not
proven analyzer errors.

The persistent channel now requires TLS 1.3 with a PKCS#12 Velocity identity,
Paper trust store, certificate-chain validation, and certificate-host
verification. Local Opengrep selected 604 applicable rules from the 2,267-rule
configuration and reports neither unencrypted sockets nor weak TLS; hosted
Codacy confirms the two prior socket findings are removed. Bidirectional
acknowledgement, certificate-host rejection, invalid-store rejection, and
concurrent replay claims are covered by tests.

Codacy currently records 66 exact ignored findings:

- 38 PostgreSQL-parser reports and 12 SQL Server policy reports against tested
  MariaDB migrations;
- five Oracle `RAC_*` policy reports against the project's MariaDB schema;
- three public Maven coordinates misidentified as secrets;
- three SSRF reports where two addresses only bind inbound listeners and the
  sole outbound address is service-owned startup configuration protected by
  TLS host verification;
- three LiteBans SQL reports where every interpolated identifier passes a
  single-identifier allowlist and all values remain parameter-bound;
- one self-comparison report whose cited expression is not a self-comparison;
- one test-only command-injection report where `ProcessBuilder` receives a
  shell-free argument vector consisting of the JDK `keytool`, fixed flags, and
  JUnit-owned temporary paths.

No analyzer rule was disabled for those cases, and no first-party source path
was excluded. Each ignore is attached to the exact current findings with a
review reason.

The configured PMD run completes without parser or execution errors and returns
243 raw findings: 99 literal-in-condition, 75 duplicate-literal, 46 NPath, 14
loop-allocation, and nine excessive-parameter reports. That is down from 303 at
the prior checkpoint. The clean Java 21 build completed all 39 tasks and 111
tests with zero failures, errors, or skips, including all six MariaDB
Testcontainers tests.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,041,501 | `E0B9428021A9242D68FB219597F24AE385415D63EA839FE99FE989313F4AB3F9` |
| Velocity runtime jar | 7,330,813 | `8FF219950C0D9992A12B0E9CB452653E5A01271EDEB31EAF9500AC9733094246` |

CodeRabbit reviewed the initial 12-file PR diff and requested one PMD-version
compatibility note, which was added. Its later incremental runs were
rate-limited, so they are not represented as independent reviews of the TLS
commits. GitHub shows no unresolved review thread.

## Current merged checkpoint

As of 2026-07-29, PRs #1 through #11 are merged and `main` is
`664792487e4fc1f9333957cd7f48e8a7f447c3b2`. The latest exact aggregate issue
snapshot recorded for `main` remains pinned to
`e6da2117fddb6b8e165f0dd41277b21bed296f90`; the PR #10 branch analysis was
separately up to standards before merge.

| PR | Coherent section | Merge commit |
| ---: | --- | --- |
| [#1](https://github.com/wsg138/EnthusiaStaff/pull/1) | Moderation platform and safe LiteBans cutover | `b5e55ed9ffd7309cacabf6b0a07af220068f3c30` |
| [#2](https://github.com/wsg138/EnthusiaStaff/pull/2) | Plugin security, concurrency, and TLS transport | `25ab696137aa5f1e582951233eada60837e15998` |
| [#3](https://github.com/wsg138/EnthusiaStaff/pull/3) | Paper startup and shutdown lifecycle | `f84e402fa70a2d5198f394ceebd0fb01c7195b6a` |
| [#4](https://github.com/wsg138/EnthusiaStaff/pull/4) | Discord outbox transaction integrity | `237d12b8bd98c2fe06c4e1b59ff57a5830bdbad5` |
| [#5](https://github.com/wsg138/EnthusiaStaff/pull/5) | Network outbox delivery integrity | `4a9c8aec9527b351608edf1c726f9c22dea7cd49` |
| [#6](https://github.com/wsg138/EnthusiaStaff/pull/6) | Asset-journal transaction fencing | `358c099443dea727a50818cb57d3826cd58ea6c7` |
| [#7](https://github.com/wsg138/EnthusiaStaff/pull/7) | Confiscated-asset restoration integrity | `2867ad30b8ee725701874fa34596502e75ba7105` |
| [#8](https://github.com/wsg138/EnthusiaStaff/pull/8) | Economy rollback integrity | `80b2635917bcf71a187ec27ae0bf5e38b35610ef` |
| [#9](https://github.com/wsg138/EnthusiaStaff/pull/9) | Network identity and inventory recovery integrity | `e6da2117fddb6b8e165f0dd41277b21bed296f90` |
| [#10](https://github.com/wsg138/EnthusiaStaff/pull/10) | Paper inventory workflow maintainability | `3444cc154e26454baaf4eefc40390108bf2903b6` |
| [#11](https://github.com/wsg138/EnthusiaStaff/pull/11) | Inventory patch preparation persistence | `664792487e4fc1f9333957cd7f48e8a7f447c3b2` |

The hosted repository badge remains A. The latest exact `main` snapshot reports
427 active warnings and 69 ignored findings. Three generic AES-GCM review
detections were added to the ignored set only after the nonce source, encryption
path, decryption path, and deterministic tests established that no nonce-reuse
path exists. The grade therefore does not establish that the repository is
clean or production-ready.

| Module | Active findings |
| --- | ---: |
| persistence | 166 |
| paper | 157 |
| velocity | 78 |
| domain | 15 |
| integration-tests | 6 |
| common | 3 |
| integration-contracts | 2 |
| **Total** | **427** |

At the PR #8 branch head, the clean Java 21
`clean test check runtimeJars` checkpoint passed 134 tests in 51 suites with
zero failures, errors, or skips. The executed Docker-backed portion comprised
21 tests across seven MariaDB Testcontainers suites, including three focused
economy rollback-integrity scenarios.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,075,203 | `242210468BC5149BB48AED09C37CB814A682D388932ECE1818FFEBBC6B92709B` |
| Velocity runtime jar | 7,348,824 | `8E909EA2C47A4CCAD02B010BE8059274E9D9B72EE0DBBFD1723675704091CE16` |

The final PR #8 local analyzer checkpoint reported 232 PMD findings, 234 Lizard
findings, 17 Opengrep findings, and four CPD clone groups at the 100-token
threshold. These tool-specific totals overlap and must not be added to the
hosted active-issue count. PR #8 introduced zero hosted Codacy issues and solved
one; its complexity and duplication deltas remain visible rather than
suppressed. No analyzer rule or first-party source path was disabled.

CodeRabbit found one critical pre-plan rollback regression and one
maintainability duplication in PR #8. Both findings were fixed and covered by
the final clean validation; the exact PR head then reported a successful
CodeRabbit check.

### PR #9 merged root-plugin integrity checkpoint

PR #9 hardens protected network identity recovery without changing the stored
encryption format. Recovery now rejects mismatched encryption-key versions,
non-AES keys, malformed IPv4/IPv6 envelopes, altered ciphertext, and equality
token substitution. A deterministic random source verifies that every
encryption requests a fresh nonce.

The same checkpoint extracts inventory patch transition persistence from the
oversized journal store. A patch and its operation row must now retain the same
profile, transition state, and fencing token. Claim, commit, and quarantine use
conditional paired updates with exact row counts. A `PENDING` patch cannot
finalize directly, divergence rolls back instead of resurrecting a terminal
operation, fencing-token exhaustion fails closed, and a quarantined patch
continues to block destructive work after its lease is released.

At exact code and test head
`eba335b179bc43d1cd52839bc69af595ae1e456c`, the clean Java 21
`clean test check runtimeJars` run had 39 actionable tasks: 38 executed and one
root aggregate task was up-to-date. The build passed 143 tests in 51 suites
with zero failures, errors, or skips, including all 23 tests across seven
MariaDB Testcontainers suites.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,083,151 | `0A9FCE8457DB31A1DAC4923AD61EA88908AA9107EC2B897BA43B4C8B08B8B0A4` |
| Velocity runtime jar | 7,356,772 | `6F2229195B670587796DAC600A07ADFE5E0D451311000C5FD22DFEA53B386820` |

Both jars contain their expected plugin metadata and no provider API classes
from `integration-contracts`.

The local checkpoint reports 215 PMD, 228 Lizard, 17 Opengrep, four CPD clone
groups at the 100-token threshold, and zero Trivy findings. The new inventory
transition helper and updated lease support have zero local findings. The
asset-journal test has zero PMD, Opengrep, or Trivy findings; fixture extraction
removed its introduced CPD group and reduced its method-length results by two.
The remaining store results are eight PMD and 15 Lizard findings, down from 23
and 20 at the start of this subsection.

The only PR-changed Opengrep results are the three generic GCM review detections
described above. Hosted Codacy reports the exact PR head is up to standards
with nine solved and zero new issues. Its lower-threshold metrics still show
three added test clone fragments and the full complexity delta; they remain
visible and are not suppressed. Local 100-token CPD reports only the four
pre-existing repository groups.

CodeRabbit's initial network-identity review found no production-code defect.
Its valid malformed-envelope test request was addressed. The exact final review
found one Markdown heading-spacing defect, which was fixed before merge. The
follow-up review contained no finding and GitHub had no unresolved review
thread when PR #9 merged.

### PR #10 Paper inventory maintainability checkpoint

PR #10 separates inventory and confiscation click decisions, paging, rendering,
durable start and renewal, restoration planning, commit preparation, lease
claim, apply, rollback, and finalization into bounded methods. A validated
`InventoryOperationContext` now carries the shared clock and backend identity,
removing the confiscation constructor's excessive-parameter finding.

At exact code and test head
`d73ddfc57b83e5c0465347b848a30248340c7996`, the clean Java 21
`clean test check runtimeJars` run had 39 actionable tasks: 38 executed and one
root aggregate task was up-to-date. The build passed 146 tests in 52 suites
with zero failures, errors, or skips, including all 23 tests across seven
MariaDB Testcontainers suites.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,087,683 | `29B9B23177A39AA887F1B08AAFD362F9C76A50E38A11EA3510EB5D466EEF0C4E` |
| Velocity runtime jar | 7,356,772 | `3904C2BB6C932B9E83ACFCEA69E106754309A92F28F6FBD74C074ACF9E3382B7` |

Both jars contain their expected plugin metadata and no provider API classes
from `integration-contracts`.

The comparable checked-in PMD ruleset reports 210 findings, down from 215 at
the PR #9 checkpoint. Lizard reports 205, down from 228. Opengrep remains at
17, Trivy reports zero, and CPD reports three pre-existing 100-token clone
groups with none in the files changed by PR #10. Direct Codacy CLI execution
with the organization-standard PMD patterns reports 465; this larger,
non-comparable total is retained in the local logs and was not presented as a
reduction against the checked-in-ruleset baseline.

Neither coordinator has a remaining method-level PMD or Lizard result. Both
still exceed the file-length threshold, so the oversized-class risk remains
visible for a later responsibility-extraction checkpoint. No analyzer rule or
first-party path was disabled, and no finding was ignored in this section.

Hosted Codacy reports implementation and report head
`7703991919ee2fed1a3c8659d81771d11c1cc8f6` is up to standards with zero new
issues and one fixed issue. Its aggregate complexity delta of 55 and one clone
delta remain visible; they were not hidden or dispositioned.

The single exact-head CodeRabbit attempt was rate-limited before a review
started. It produced no findings or review object, and GitHub reports zero
review threads. The attempt was not retried.

### PR #11 inventory patch preparation checkpoint

PR #11 decomposes generic inventory patch preparation into replay, constraint,
profile, lease, commit, operation-row, before-snapshot, and pending-patch
stages while retaining one database transaction. Each inserted durable row
must affect exactly one row or the transaction fails closed. Replay validation
still compares the complete operation binding and persisted mutation fields;
the MariaDB integration suite now explicitly rejects a changed-slot replay.

At validation head
`ecd45b358822fb8cec847419fbd58805baca0ee2`, the clean Java 21
`clean test check runtimeJars` run executed all 39 actionable tasks. The build
passed 146 tests in 52 suites with zero failures, errors, or skips, including
all 23 tests across seven MariaDB Testcontainers suites.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,088,941 | `220B419A72E9DAD32BE5F085D6B5BB3E947805EBFB00FACD066A5E39DE4170F2` |
| Velocity runtime jar | 7,358,030 | `FD9F999F7DB9F73C1709562CEB023DEBB7507A5F54051213CA4887665C64B8CB` |

Both jars contain their expected plugin metadata and no provider API classes
from `integration-contracts`.

The comparable checked-in PMD ruleset reports 204 findings, down from 210 at
the PR #10 checkpoint. Lizard reports 201, down from 205. Opengrep remains at
17, Trivy reports zero, and CPD reports the same three pre-existing 100-token
clone groups with none in the changed files. The generic prepare, insert, and
replay-validation methods have no remaining PMD or Lizard result. The store
retains two PMD and eleven Lizard results in confiscation, pending, quarantine,
audit, and overall file-size areas. No analyzer rule, first-party path, or
finding was suppressed or ignored.

Hosted Codacy reports validation head
`ecd45b358822fb8cec847419fbd58805baca0ee2` is up to standards with zero new
issues, complexity delta 13, and duplication delta zero. CodeRabbit skipped the
draft automatically. Its single ready-for-review attempt was then rate-limited
before a review started, produced no finding or review object, and was not
retried. PR #11 merged as
`664792487e4fc1f9333957cd7f48e8a7f447c3b2`.

### PR #12 confiscation journal lifecycle checkpoint

PR #12 separates durable confiscation start, replay, renewal, preparation,
cancellation, operation update, pending-patch insert, and confiscated-asset
snapshot insert into bounded transaction stages. Renewal and cancellation
retain exact operation/fence/state/lease checks. Preparation rechecks the
selection session and authoritative observation, and every changed or inserted
durable row must affect the expected row or the transaction fails closed.

Two new MariaDB scenarios exercise start replay, wrong-fence rejection, valid
renewal, fenced and idempotent cancellation, exact preparation replay, and
changed-slot conflict rejection.

At validation head
`c818c131d492473d9be6f18937fd18286de3b382`, the clean Java 21
`clean test check runtimeJars` run had 39 actionable tasks: 38 executed and one
root aggregate task was up-to-date. The build passed 148 tests in 53 suites
with zero failures, errors, or skips, including all 25 tests across eight
MariaDB Testcontainers suites.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,090,200 | `E1A25F90ACBCE0EB744A4944327A618029D3F40D390BC0E09699907A50682A41` |
| Velocity runtime jar | 7,359,289 | `A723363FF3F6A0CDD06D523153D582F45664234782B6B2426953597CE1D39317` |

Both jars contain their expected plugin metadata and no provider API classes
from `integration-contracts`.

The comparable checked-in PMD ruleset reports 203 findings, down from 204 at
the PR #11 checkpoint. Lizard 1.23.0 reports 194, down from 201. Opengrep
remains at 17, Trivy reports zero, and CPD reports the same three pre-existing
100-token clone groups with none in the changed files. The store's remaining
local results are the pending and quarantine method complexities, the audit
helper parameter count, and the overall file-size result. No analyzer rule,
first-party path, or finding was suppressed or ignored.

Hosted Codacy reports validation head
`c818c131d492473d9be6f18937fd18286de3b382` is up to standards with zero new
issues, complexity delta 26, and duplication delta zero. The complexity metric
remains visible; it was not suppressed or dispositioned. CodeRabbit skipped the
draft automatically; one lightweight ready-for-review checkpoint remains.

## Remediation order

1. Fix reachable correctness, security, transaction, resource-ownership, and
   migration failures.
2. Re-run focused tests and the real Docker-backed MariaDB tests.
3. Resolve PMD and Opengrep findings, suppressing only demonstrated analyzer
   mismatches or non-exploitable reports with an exact reason.
4. Reduce structural complexity and duplication without changing public
   behavior.
5. Run all available local analyzers, the clean build, artifact inspection, and
   remote Codacy reanalysis before declaring the root checkpoint clean.
