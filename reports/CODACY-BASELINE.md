# Codacy and validation baseline

Captured: 2026-07-27 (America/Indianapolis)

Last updated: 2026-08-01 (America/Indianapolis)

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
