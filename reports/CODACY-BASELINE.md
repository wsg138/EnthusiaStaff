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
