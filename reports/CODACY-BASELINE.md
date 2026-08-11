# Codacy and validation baseline

Captured: 2026-07-27 (America/Indianapolis)

Last updated: 2026-08-11 (America/Indianapolis)

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
`688b2802c1756853921ea01f0c0656a9d9e9bc14`, the clean Java 21
`clean test check runtimeJars` run had 39 actionable tasks: 36 executed and
three tasks were up-to-date. The build passed 148 tests in 53 suites
with zero failures, errors, or skips, including all 25 tests across eight
MariaDB Testcontainers suites.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,090,329 | `73C7FF8DA0071C42C68ACEBF69E6D3B4A1A82C9A634A9333F12CF20C35E417DA` |
| Velocity runtime jar | 7,359,418 | `2DE3272250DE305ACEA0FC165658C959AD3CD470FEF5A65E10A0A1DEBFF71E95` |

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

### PR #30 website moderation persistence checkpoint

Draft PR #30 decomposes the website moderation persistence boundary into
separate public-registry, punishment-code, appeal, audit, and projection
collaborators. The public `WebsiteModerationStore` contract is unchanged.
Punishment-code and appeal mutations retain one MariaDB transaction and now use
the shared transaction helper for rollback on both SQL and runtime failures.

Regression coverage demonstrates two failures that existed before this
checkpoint and are now fixed:

- a punishment-code batch could commit an earlier code creation when a later
  corrupt code caused a runtime integrity exception;
- concurrent or conflicting appeal preparation could surface a raw duplicate-key
  persistence exception instead of the documented idempotency conflict.

At implementation checkpoint `103f654bccb82f31eff65176b5f325769f726424`, the clean Java
21 `clean test check runtimeJars jacocoAggregateReport` gate passed. Test XML
records 344 tests with zero failures, including 50 tests across 12 MariaDB
11.8.3 Testcontainers suites. The website-specific MariaDB coverage comprises
14 tests across public-registry, punishment-code, and appeal behavior.

| Artifact | Bytes | Local SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,239,206 | `97A10170E0D733D7112603F5FD22B26B5D44B8997BB26E307544EE8169720937` |
| Velocity runtime jar | 7,440,725 | `43C20F76C237228351D03ADAFC066B8D799EA8AEA9E046DCE5C443291DE44148` |

Hosted Codacy reports that implementation checkpoint is up to standards with zero introduced
issues, 24 fixed issues, 88.62% diff coverage, and a +2.83 percentage-point
coverage variation. It also reports complexity delta +134 and duplication
delta +38. These structural deltas remain visible and were not ignored. The
comparable local analyzers report zero PMD, Lizard, Opengrep, Trivy, or
100-token CPD finding in a changed file. Lower-threshold CPD identifies small
JDBC error factories and MariaDB test-fixture setup; these were not converted
into cross-responsibility abstractions merely to reduce the metric.

The repository-wide local analyzer snapshot at this checkpoint is 214 PMD
findings, 396 Lizard findings, 18 Opengrep findings, zero Trivy findings, and
six 100-token CPD clone groups. These are repository totals, not new PR
findings. No rule, first-party path, or finding was suppressed or ignored.

The same implementation checkpoint also passed the guarded Pi staging workflow. A trusted Java
21 build produced the Paper runtime, verified its checksum, ZIP integrity,
plugin main class, and zero provider-API leaks, then loaded it on Paper 1.21.11.
Two disposable boot/shutdown cycles each reached `SHADOW_MIGRATION`, passed
`/plugins`, `/version EnthusiaStaff`, `/estaff status`, and `/estaff verify`,
and shut down with exit code zero. The sanitized evidence reports two completed
server starts, two storage-ready cycles, and zero critical startup/runtime
failure patterns. This is a useful standalone Paper/runtime gate; it is not a
claim that multi-server, Bedrock, provider-plugin, Discord, or production-data
acceptance has been completed.

The subsequent ready-for-review pass identified four focused correctness and
documentation findings. The branch fixes those findings without requesting a
second automated review cycle; the resulting PR head must repeat the hosted
build, Codacy, and guarded Pi gates before merge.

The review-fix checkpoint passed a fresh Java 21
`clean test check runtimeJars jacocoAggregateReport` run with all tasks rerun
and build caches disabled. Test XML records 348 tests with zero failures,
including 52 tests across all 12 MariaDB 11.8.3 Testcontainers suites. The 16
website tests now include simultaneous first-code creation, non-blocking
existing-code reads, and `APPLIED` sanction eligibility and expiration.

| Review-fix artifact | Bytes | Local SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,239,769 | `559C484B5B049E21295C53A9F0242753AAFC55A9EDE6D474157FEAB585CAF2B8` |
| Velocity runtime jar | 7,441,288 | `5DBAF8B159D3EA8BA1E2A6F162DB73BAABDCB34541519CB8D73A55401ED345D5` |

Focused PMD 7 reports zero findings in the six changed Java files. The full
Lizard and Opengrep runs likewise report no changed-file findings, Trivy
reports zero findings, and the five repository-wide 100-token CPD groups do
not include a changed file. No analyzer finding was ignored or suppressed.

Exact PR head `4457888e341a99822235e54e4dc68a805c226a55` subsequently
passed the hosted build and merged as PR #30 in
`477b8c509dba7e437968ffa5a3faef0f2f9c0aad`. Hosted Codacy reported
the head up to standards with zero introduced issues, 24 fixed issues, 88.31%
diff coverage across 667 coverable lines, +2.863 percentage-point coverage
variation, complexity delta +147, and duplication delta +42. The one
non-gating potential Lizard signal was a 56-line test method; it was split at
the start of the next plugin checkpoint rather than ignored.

The exact head also passed trusted Pi staging run `30695268906`. Its Java 21
build produced an 8,239,663-byte Paper runtime with SHA-256
`4757DB24955D942F7B3DC10626BE5D9118F0DAC9D0452DEF180245C84DF21A3E`.
Paper 1.21.11 build 132 completed two boot/storage/command/shutdown cycles in
`SHADOW_MIGRATION`, with zero provider API leaks, zero critical failure
patterns, and exit code zero in both cycles. CodeRabbit's earlier findings
were addressed; its final status was rate-limited and is not represented as a
fresh approval.

### PR #31 restricted website API boundary checkpoint

Draft PR #31 decomposes the restricted Velocity website bridge without
changing its route contract. `WebsiteApiServer` is now a transport boundary,
while configuration validation, listener/executor ownership, request decoding,
route dispatch, response projection, and durable appeal mutation live in
focused collaborators. The server constructor was reduced from 14 parameters
to five dependencies. Loopback binding and client checks, bearer/HMAC
authentication, nonce replay protection, bounded bodies and queues, strict
input fields, stable error envelopes, and hardened response headers remain
explicit.

Regression tests cover authenticated loopback transport, unsigned rejection,
clean close/restart, failed-bind cleanup and retry, pre-authentication body
limits, route/query/body/content-type rejection, authorized appeal acceptance,
authority-mode replayability, terminal mutation rejection, and durable
preparation rejection. The final Java 21
`clean test check runtimeJars jacocoAggregateReport` run executed all 40 tasks
with caches disabled and passed 365 tests with zero failures, errors, or skips.
That includes all 52 tests across the 12 MariaDB 11.8.3 Testcontainers suites.

| Artifact | Bytes | Local SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,239,769 | `EC6C7441DC330C4337D41CB54E4D523FC11A49EEDFCDF79D162105CA86BE1BF4` |
| Velocity runtime jar | 7,452,204 | `D67A6C62A2F96F5EBC1674830946980BD2AAE641CABD5E0116343ECAB17DF8C3` |

Hosted Codacy initially reported exactly two new Opengrep findings. One
mistook the inbound `HttpServer` loopback bind for an outbound SSRF request.
The other mistook a test-only ephemeral loopback port reservation, which sends
no application data, for an unencrypted application transport. Both cited
constructs are analyzer mismatches. They have narrow inline `nosemgrep`
annotations on only the cited lines with the reason beside each annotation;
no rule or first-party path is disabled. The Codacy CLI could read both exact
findings, but its issue-state mutation endpoint returned `404`, so the
source-scoped dispositions keep the evidence reviewable in version control.

At exact head `6e8a4212ed17f3da135d5d844f3771614950313f`, hosted
Codacy is up to standards with zero new and 22 fixed issues, 78.59% diff
coverage across 383 coverable lines, a +1.514 percentage-point coverage
variation, complexity delta +79, and duplication delta +10. The structural
deltas remain visible. Local analyzers report 200 PMD findings, 395 Lizard
findings, 18 Opengrep results (16 active and two source-suppressed), zero Trivy
findings, and six 100-token CPD clone groups. None of the active results is
introduced on a PR #31 changed line. The unrelated `migrationMode == null`
Opengrep result in the
touched Velocity composition root is the already reviewed self-comparison
false positive recorded in the ignored baseline.

The same exact head passed trusted Pi wrapper run `30697060008`. The independent
Java 21 build produced an 8,239,663-byte Paper runtime with SHA-256
`F356B67FE7F8503A83839FF5B00941359DA7AB60219E22B201D50755E051DDC2`.
Paper 1.21.11 build 132 completed two `SHADOW_MIGRATION`
boot/storage/command/shutdown cycles, checked 24 provider API source types with
zero leaks, found zero critical startup/runtime patterns, and exited with code
zero both times. Repository-managed Wiki Architecture and Build/Testing pages
now describe the bridge and this exact-SHA gate; all 29 Wiki pages validate.
CodeRabbit skipped the draft automatically, so one lightweight ready-state pass
remains and no approval is claimed here.

The PR #31 review fixes produced final head
`d5ef5d2f5ec5209d4d11d0acf94da7777450476a`. A fresh clean Java 21 build passed
365 tests, including all 52 MariaDB tests across 12 Testcontainers suites.
Hosted Codacy reported zero new and 22 fixed issues, 80.53% diff coverage, a
+1.571 percentage-point coverage variation, complexity delta +79, and
duplication delta +10. Exact-head Pi staging run `30698581440` passed both
Paper 1.21.11 boot/storage/command/shutdown cycles. PR #31 merged in
`02fe5fa584b04939de45401818c675a94428c71a`; Wiki publication run
`30698968797` subsequently completed successfully. The automatic post-fix
CodeRabbit attempt was rate-limited and was not retried or represented as an
approval.

### PR #32 report persistence and evidence checkpoint

Draft PR #32 starts from merged PR #31 and addresses report-path correctness
before the remaining GUI work. At implementation checkpoint
`2822fdc82983aed9c23c1af5182093e377f71644`, it includes:

- configured Java-time JSON persistence for chat and private-message evidence;
- concurrency-safe state-change replay and semantic idempotency conflicts;
- exact submission fingerprints so a key cannot replay different report
  content;
- explicit rollback on SQL, JSON, runtime, and fatal-error paths before pooled
  auto-commit restoration;
- bounded 2,000-message report contexts;
- separate submission, replay, query, state, and evidence-maintenance JDBC
  responsibilities behind `JdbcReportStore`;
- physical seven-day cleanup for public-chat, private-message, and report-linked
  client evidence, with bounded asynchronous scheduling;
- sanitized asynchronous `/report` failure feedback; and
- MariaDB coverage for merges, exact replay, semantic conflicts, deterministic
  concurrent state changes, queue/state transitions, failure-injected rollback,
  and physical retention.

The exact implementation checkpoint passed the Java 21
`clean test check runtimeJars jacocoAggregateReport` gate with all 40 tasks
executed and caches disabled. Test XML records 374 tests with zero failures,
errors, or skips, including 59 tests across all 13 MariaDB 11.8.3
Testcontainers suites. The Paper jar is 8,393,471 bytes with SHA-256
`65F32E526A600FDD70CB9D77981859D4393B4F1F0CEA59F2BD0F078B6E4B9181`; the
Velocity jar is 7,602,013 bytes with SHA-256
`E242F77FEC7A8CE0EEE7080D6434EA54F146C9067678E85889B003BEEBC87E77`.
Exact-SHA Pi staging run `30700846431` passed. Source-scoped CPD reports the
same six repository duplication groups as the prior checkpoint and none touches
the report changes.

Hosted Codacy initially reported three new test-code findings at that head: one
valid file-size warning and two generic SQL-helper security warnings. Commit
`546d1e28` extracted shared fixtures and replaced the dynamic helpers with fixed
prepared statements. The seven-test MariaDB report suite passes after that
change. PMD 7 and Opengrep report zero findings on the two affected files;
Lizard measures 470 non-comment lines in the test and 158 in its fixture, below
the configured 500-line limit. The nine findings reported when the Paper
bootstrap is included are its pre-existing executor, logging, literal, and loop
findings rather than new report-line findings. No finding, rule, or first-party
path was suppressed. Hosted Codacy and an exact-final-head Pi run must pass
before merge.

Exact candidate `ce6860255f5bc76bdc81690cafe50ef04fc77c58` repeated the full
clean Java 21 gate with 374 passing tests and all 59 MariaDB tests. Hosted
Codacy reported zero new and 13 fixed issues, 76.26% diff coverage, and a
+2.458 percentage-point coverage variation. Wiki validation passed, and Pi
staging run `30701863189` passed the independently built artifact through both
boot/storage/command/shutdown cycles.

The single ready-state review found eight code concerns and one validation
provenance concern. Commit `d3356165` addresses the code findings: report
evidence cleanup no longer blocks operational-state refresh; per-category
purge counts drive backlog scheduling; expired client evidence is hidden at
read time; UUID report targets resolve online evidence correctly; and replay
comparison, conflict fallback, and fingerprint serialization are null-safe and
mapper-independent. Focused report unit and MariaDB tests pass, and PMD 7,
Opengrep, and Lizard introduce no finding outside the already recorded Paper
composition-root backlog. This documentation update corrects the provenance
finding. Final head `04c9f37d4b0ad683766218ea237428315a2f2bb6` passed the clean
Java 21 gate with 378 tests, including all 59 MariaDB tests. Hosted Codacy
reported zero new and 13 fixed issues with 76.27% diff coverage; the hosted
build and Wiki validation passed, and exact-SHA Pi run `30702688979` passed in
10 minutes 46 seconds. No second automated review was requested. PR #32 merged
as `7555d1504666ad52178005b29b94525b54e088b6`.

### PR #33 Paper bootstrap composition checkpoint

Draft PR #33 continues from merged PR #32 without overlapping draft PR #27.
Implementation head `3736933e35fd69b707b4f56f6fd455cfb34983e2`
extracts command/GUI registration, runtime-manager construction, optional
integration discovery, database environment resolution, reason-policy loading,
network inbox handling, visibility-matrix parsing, and resource cleanup from
the Paper entrypoint. The entrypoint fell from roughly 950 to 488 non-comment
lines. Its former file-length, command method-length, duplicate-literal,
loop-allocation, executor-resource, logging-guard, and branch-complexity
findings are absent locally.

The network handler now validates a sanction target and performs its idempotent
mute-cache invalidation before recording the durable inbox receipt. A malformed
payload therefore cannot be persisted as successfully applied. Focused tests
cover valid ordering, malformed targets, unrelated events, visibility defaults
and validation, and environment-name-only database configuration.

Final head `d77fe281943786da826241679bbf06944ddde720` passed the
uncached Java 21 `clean test check runtimeJars jacocoAggregateReport` gate with
all 40 tasks executed. Test XML records 389 tests with zero failures, errors,
or skips, including 59 tests across all 13 MariaDB 11.8.3 Testcontainers
suites. The Paper jar SHA-256 is
`4B708EF39D478BDCC046CFCDD19644BEDB8C890DBA9601505FE85AE98B2FE4C3`;
the Velocity jar SHA-256 is
`C2FA5977D9583F952A52C0AB6BDED6C648E5F18534A4BB9CB2C3B004FE1A1844`.
Exact-SHA Pi staging run `30705886471` passed both independently built Paper
boot/storage/command/shutdown cycles in 10 minutes 4 seconds.

Hosted Codacy reported zero new and eight fixed issues, 82 complexity findings,
six duplication findings, 11.00% diff coverage, -0.07 percentage-point coverage
variation, and 39.44% branch coverage. PMD 7, threshold-matched Lizard, source-
scoped CPD, Opengrep, and Trivy introduced no changed-file finding. The one
ready-state review identified blank database environment values as a valid
configuration defect; commit `c435858` rejects them. A hosted secret detector
then misclassified test-only constant names as credentials, so commit `d77fe28`
renamed those constants without suppressing the rule. No second automated review
was requested. PR #33 merged as
`0319ee789707b6b603a3308bb51c9454907a75b1`.

### PR #34 vanish entity-ownership checkpoint

Draft PR #34 continues from merged PR #33 without touching the active PR #27
staff-mode and freeze files. Implementation heads `e81ab2e` and `0a3d96b`
introduce a session-fenced audience coordinator and route viewer visibility,
tab-list, message, startup-recovery, and packet-adapter failure work through the
appropriate player entity scheduler. Queued callbacks are discarded after a
disconnect/reconnect session change, and target data is read again when a viewer
task executes.

Implementation head `0a3d96b2ef9472a1e6d18fd047eab4219ff4f506` passed the
uncached Java 21 clean gate with all 40 tasks executed. Test XML records 396
tests with zero failures, errors, or skips, including all 59 tests in the 13
MariaDB Testcontainers suites. The Paper jar is 8,440,464 bytes with SHA-256
`11FA09C45BB9AE29A3F91D5964F2F66B9DC9C5631FFD5F32DC7C6B545BA9C9B6`;
the Velocity jar is 7,606,354 bytes with SHA-256
`C4B2962F53A73354D682B35FB8E9D551BAE3938C51ABA10EA58639631B158EA6`.

PMD 7 and threshold-matched Lizard report no result in the three changed Java
files, and PMD CPD reports no 100-token duplicate in Paper production source.
Opengrep reports the same 16 repository-baseline findings with none in the
changed files. Trivy reports zero vulnerabilities or secrets. No issue, rule,
or first-party path was suppressed.

Final head `54c82e2d2d9a6313e7b08d8ca6b73c6112adf6c0` passed the
hosted build and Wiki validation. Hosted Codacy reported zero new and zero fixed
issues, 32 added complexity, zero duplication, 38.46% diff coverage, and a
+0.12 percentage-point coverage variation. Exact-SHA wrapper run `30707407074`
and delegated Pi staging run `30707410497` passed in 10 minutes 37 seconds. The
ready-state event also queued a redundant Pi run at the identical SHA; run
`30707812296` was cancelled because the authoritative exact-SHA result had
already passed.

The single ready-state review made two documentation-only suggestions. The
architecture text already limited the new ownership claim to vanish and kept
freeze/staff recovery separate; an ephemeral PR number was not added to the
long-lived architecture document. The pre-merge Codacy snapshot was retained,
while the PR description recorded every final hosted and Pi gate. Both review
threads were answered and resolved without another automated review cycle. PR
#34 merged as `92f9f26ba8b9b81168bfce884d21d0870108f992`.

### PR #35 LiteBans schema-inspection checkpoint

PR #35 started from merged PR #34 and did not touch active PR #27 files.
Implementation head `739c93cd10aae014aa766c19c4d7a2db5dec5ecb`
separates sanction, history, and audit-only source inspection, reads the source
catalog once, hoists bounded alias sets out of loops, and reports missing
columns in stable canonical order. Accepted source variants remain unchanged;
focused tests preserve sanction-specific staff-column precedence and the shared
legacy fallback.

All persistence unit tests and the Docker-backed
`LiteBansMigrationIntegrationTest` pass. PMD 7 and threshold-matched Lizard
report no result in the changed source or test, removing the inspector's prior
NPath, cyclomatic-complexity, method-length, duplicate-literal, and loop-
allocation findings locally. No issue or rule was suppressed.

Final head `940225015e5934babd3214017393e862b4f16eb2` passed the clean
Java 21 build with all 40 tasks, 99 suites, and 398 tests. The Docker-backed
subset comprised 15 MariaDB suites and 68 tests, with no failure, error, or
skip. Runtime artifacts were:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Paper runtime jar | 8,441,300 | `A29C74319052E0B78BE5111469566967393F1D003226DFE697A52A4B65F070A8` |
| Velocity runtime jar | 7,607,190 | `628832932AF3FDD62758A5C7C58A5E1BE3255CA82A39780630350B46EA74DCEF` |

Hosted Codacy reported zero new issues, three fixed issues, 89.23% diff
coverage, a `+0.057` coverage delta, and no clone increase. Exact-head Pi boot
and restart staging passed run `30708422761`. CodeRabbit's single ready-state
attempt was rate-limited for 31 minutes and produced no code findings; it was
not retried. A focused local diff review found no unresolved issue. The
redundant same-head Pi run triggered solely by the ready-state event was
cancelled after the exact commit had already passed. PR #35 merged as
`dbf27e1ac1055342aceefd9801b0757cdbe12702`.

The completed `main` analysis now reports 265 active findings: 180 complexity,
75 error-prone, and 10 performance findings. Severity is one High and 264
Warning. The remaining High `UseProperClassLoader` result is in `MariaDb.java`,
which remains part of active PR #27 and is intentionally not edited in the
concurrent migration section.

### PR #36 LiteBans shadow-comparison checkpoint

Draft PR #36 starts from merged PR #35 and does not touch active PR #27 files.
Implementation head `e005bfb` separates per-row comparison from aggregate
mismatch accounting, reuses one checksum calculator per run, and isolates JDBC
row materialization from result iteration. The Docker-backed lifecycle test now
asserts every comparison dimension through initial import, reconciliation,
idempotent replay, and source-row deletion, including an exact one-count orphan
mapping result.

All persistence tests and the focused MariaDB Testcontainers scenario pass.
PMD 7 and threshold-matched Lizard report zero findings in changed Java. No
issue or rule is suppressed. The full clean build, complete MariaDB suite,
hosted Codacy, exact-head Pi staging, and final review remain required before
merge.

## 2026-08-11 current main checkpoint

The exact `main` revision after merged PR #122 is
`e5352a7918d7c19bb1878117e182aac607ca9297`. Codacy Cloud reports 376 active
findings, all at Warning severity and all in Java. The current CLI response does
not expose a repository letter grade, so this report does not claim one.

| Category | Active findings |
| --- | ---: |
| Complexity | 253 |
| Error-prone | 104 |
| Performance | 19 |
| **Total** | **376** |

| Pattern | Active findings |
| --- | ---: |
| Lizard cyclomatic complexity | 95 |
| Lizard method length | 69 |
| PMD duplicate literals | 49 |
| PMD literals in conditions | 49 |
| PMD NPath complexity | 33 |
| Lizard file length | 29 |
| Lizard parameter count | 18 |
| PMD object allocation in loops | 11 |
| PMD excessive parameters | 9 |
| PMD method-level synchronization | 7 |
| PMD null assignment | 6 |
| PMD unguarded logging | 1 |

The latest remediation sequence reduced the exact active inventory without
ignoring findings or excluding first-party source:

| Pull request | Checkpoint | Active findings after analysis |
| --- | --- | ---: |
| #118 | punishment alert lifecycle complexity | 452 |
| #119 | configuration reload complexity | 440 |
| #120 | Paper and orchestration maintainability | 402 |
| #121 | persistence state transitions | 377 |
| #122 | registered command-route integrity | 376 |
| #123 | fail-closed freeze verification visibility | 376 |
| #124 | Velocity runtime and administration boundaries | 346 |
| #125 | website appeal transaction boundaries | 334 |
| #126 | economy recovery and confiscation coordination | 319 |

PR #120 passed with zero new findings and reduced the active inventory by 38.
PR #121 passed with zero new findings and reduced it by 25; its PR mapper showed
three fixes, while the complete branch inventory captured the full reduction.
PR #122 passed with zero new findings and one fixed finding. PR #123 introduced
zero findings, retained the 376-finding inventory, reached 96% diff coverage,
and passed the exact-head Pi staging gate. CodeRabbit was rate-limited on PRs
#120, #121, #123, and #124; PR #122 completed without producing a review or
inline comment. No CodeRabbit approval is claimed.

The checked-in PMD ruleset still targets the hosted PMD 6 runner and includes a
custom XPath rule class that PMD 7 cannot load. Local PMD 7 checks therefore run
the exact applicable hosted rule IDs on changed files, while Codacy Cloud
remains authoritative for the complete repository result.

## 2026-08-11 Velocity runtime boundaries checkpoint

Merged PR #123 advanced `main` to
`f0f82ce94b81502bc898de545a1d47b65d31bd31`. Codacy-analyzed PR #124
implementation head `ee260dfe93d25086c3d415631c0fb563d8075cac` decomposes the Velocity bootstrap,
login and server-switch safety, alt operations, and website, migration,
cutover, and Discord administration paths. Permission-aware completion routing
is isolated behind direct tests.

Codacy Cloud reports zero new findings. Its pull-request mapper associates six
fixes with the diff, while the complete branch inventory records the
authoritative reduction from 376 to 346 active warnings:

| Category | `main` | PR #124 branch | Change |
| --- | ---: | ---: | ---: |
| Complexity | 253 | 229 | -24 |
| Error-prone | 104 | 98 | -6 |
| Performance | 19 | 19 | 0 |
| **Total** | **376** | **346** | **-30** |

The branch removes seven method-length, nine cyclomatic-complexity, eight NPath,
and six literal-condition findings. The existing Velocity composition-root
file-length finding remains visible; no issue, rule, or first-party source path
is suppressed. The current CLI response still does not expose a repository
letter grade, so none is claimed.

Java 21 clean build completed all 46 configured non-Testcontainers tasks. The
Velocity tests pass, including direct permission, completion, unavailable-
dependency, and invalid-alt route coverage.
PMD 7.26.0 and threshold-matched Lizard 1.23.0 report zero applicable findings
in changed Java. The full Docker-backed MariaDB Testcontainers run passed 47
suites and 180 tests with zero failures, errors, or skips. Hosted coverage,
Sentinel, and exact-head Pi results remain merge gates on the documentation
head until they complete.

## 2026-08-11 website appeal workflow checkpoint

Merged PR #124 advanced `main` to
`d06ff33725023d5b43c756d9b5274178b644b803`. PR #125 code head
`57a94cc71f74f36c2f5f65fb2851232d87a3afaf` decomposes website appeal
submission, pagination, decision, replay and event-write paths while retaining
their row locks and transaction boundary. A MariaDB regression test proves that
the continuation cursor identifies the last returned row rather than the
lookahead row, so consecutive one-item pages neither skip nor duplicate an
appeal.

Codacy Cloud reports zero new findings on the code head. Its pull-request mapper
associates three fixes with the diff, while the complete branch inventory records
the authoritative reduction from 346 to 334 active warnings:

| Category | `main` | PR #125 branch | Change |
| --- | ---: | ---: | ---: |
| Complexity | 229 | 217 | -12 |
| Error-prone | 98 | 98 | 0 |
| Performance | 19 | 19 | 0 |
| **Total** | **346** | **334** | **-12** |

The branch removes five method-length, five cyclomatic-complexity and two
parameter-count findings. A newly mapped null-assignment result in the first
implementation head was fixed without suppression; the final null-assignment
inventory remains six. Three no-longer-required broad PMD suppressions were
removed. No issue, rule or first-party source path was ignored or excluded. The
current CLI response still does not expose a repository letter grade, so none is
claimed.

Threshold-matched Lizard 1.23.0 reports zero finding in changed Java, and the
focused PMD 7.26.0 run reports zero applicable finding. The Java 21 clean build
on the implementation head passed 168 suites and 733 non-container tests. The
complete Docker-backed MariaDB run passed 47 suites and 181 tests with no
failure, error or skip. After the nullable-context correction, all 62 persistence
unit tests and the five-test MariaDB website appeal workflow passed on the final
code head. Hosted coverage, Sentinel, exact-head Pi staging and documentation-
head analysis remain merge gates.

## 2026-08-11 economy recovery checkpoint

Merged PR #125 advanced `main` to
`9d3cd1eec72581401a9313d200fac654dd15c321`. PR #126 final head
`446f59f1d260e6ace8bb6b0e842a209638aefdba` separates economy recovery
assessment from Bukkit scheduling, decomposes confiscation coordination, and
groups the coordinator's runtime dependencies. Review hardening requires
complete committed-result evidence, verifies a rollback without recorded result
evidence against the durable before-state, preserves the safe evidence-free
pre-plan rollback, and quarantines unknown provider outcomes.

Codacy Cloud reports zero new findings. The complete branch inventory records
the authoritative reduction from 334 to 319 active warnings:

| Category | PR #125 `main` | PR #126 branch | Change |
| --- | ---: | ---: | ---: |
| Complexity | 217 | 205 | -12 |
| Error-prone | 98 | 95 | -3 |
| Performance | 19 | 19 | 0 |
| **Total** | **334** | **319** | **-15** |

The touched economy area retains one visible finding: the 1,509-line
`EconomyCoordinator` file-length result. No issue, rule, or first-party source
path was suppressed. The current CLI response still does not expose a repository
letter grade, so none is claimed.

The final Java 21 cache-disabled clean build passed 39 tasks and 924 tests with
zero failures, errors, or skips. All 47 MariaDB Testcontainers suites and their
181 tests executed. Hosted coverage, Sentinel packaging, and the exact-head
private Pi runtime test passed. CodeRabbit's valid rollback-evidence and unknown
provider-status findings were fixed; its follow-up run was rate-limited, so no
follow-up review approval is claimed.

## 2026-08-11 exact-sanction transaction checkpoint

Merged PR #127 advanced `main` to
`7c032c6af32f7281f518a01ed6dc3b0252cabb5b`. Final code head
`efd8cb507d8b7e4c2d2ac493a98e847442496072` separates exact-sanction row
state, linked-record validation, mutation planning, and transactional event,
audit, and outbox writes. The original row-lock order, revision check,
idempotent replay, rollback behavior, and single commit boundary remain intact.

Review hardening verifies that rejected punishment-request links leave sanction
state, revision, events, audit, and outboxes unchanged, and covers both approved
and externally fulfilled requests. The orchestration validator now excludes
generated analyzer and build directories without exceeding its configured
complexity threshold.

Codacy Cloud reports zero new issues. The complete branch inventory records the
authoritative reduction from 319 to 311 active warnings:

| Category | PR #126 `main` | PR #127 branch | Change |
| --- | ---: | ---: | ---: |
| Complexity | 205 | 197 | -8 |
| Error-prone | 95 | 95 | 0 |
| Performance | 19 | 19 | 0 |
| **Total** | **319** | **311** | **-8** |

No changed exact-sanction production file or orchestration validator retains a
Codacy result. No finding, analyzer rule, or first-party source path was ignored
or suppressed. The current CLI response still does not expose a repository
letter grade, so none is claimed.

The final Java 21 cache-disabled clean build passed 39 tasks and 926 tests with
zero failures, errors, or skips. All 47 MariaDB Testcontainers suites and their
183 tests executed. The Paper runtime jar is 9,190,872 bytes with SHA-256
`4FF32B9B413537DA255473578F88647A380F9C4BA673E529D46A1C73C924543D`; the
Velocity runtime jar is 7,937,851 bytes with SHA-256
`C45D571A42F4C36EBE6703BE19048A340ACA85070E3A560B2D8C17AAF7498E2B`.
Hosted aggregate coverage, Sentinel packaging, and the exact-head guarded Pi
boot/restart test passed.

Focused PMD 7.26.0, threshold-matched Lizard, configured Opengrep, and
100-token CPD checks introduced no changed-production finding. CodeRabbit's
initial valid test and documentation observations were addressed; its follow-up
run was rate-limited, so no follow-up approval is claimed.

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
