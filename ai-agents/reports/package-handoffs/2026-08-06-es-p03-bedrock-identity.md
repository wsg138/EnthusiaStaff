# ES-P03 Bedrock identity correctness handoff

Date: 2026-08-06
Package: `ES-P03 — Bedrock identity correctness`
Worker: `ChatGPT sequential package worker`
Status: `COMPLETE`

## Selection and routing

- Legitimate aggregate `main` at selection: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Implementation branch: `package/es-p03-bedrock-identity`.
- Implementation PR: #75.
- ES-P02 remained `BLOCKED` / `PARKED_BLOCKED`; ES-X05 remained `BLOCKED` / `PARKED_BLOCKED`.
- The owner-directed 2026-08-06 exception selected ES-P03 only. It did not complete ES-P02, import PR #70, touch PR #74, waive gates, or authorize later packages.

## Completed implementation

- Shared proof-bearing `PlayerPlatformDetection` and Paper Floodgate resolver.
- Verified Paper observations; unverified proxy observations persist platform as `UNKNOWN` while retaining UUID/name/presence.
- Only available Floodgate per-player evidence proves Java or Bedrock; absent/unavailable/incompatible local providers remain `UNKNOWN`, including proxy-hosted layouts.
- Single-`*` Bedrock current/history aliases, exact lookup, and literal prefix search.
- Verified Bedrock repairs legacy rows and cannot be downgraded; verified Java upgrades only unknown records.
- Event-time ordering plus stable equal-time identity/presence tie-breaking independent of database arrival order.
- Strictly-later disconnect requirement preventing stale/equal disconnects from clearing presence.
- SQL `LIKE` escaping for literal underscores.
- Domain and MariaDB/Testcontainers regressions for provider states, aliases/history, non-downgrade, unequal/equal ordering, reconnect races, literal prefixes, and invalid shapes.
- V1–V17 unchanged; no migration added.

## Review record

CodeRabbit identified six valid findings: dependency wording, stale PR readiness, missing terminal/next-owner state, unsupported Java-proof documentation, nondeterministic equal-time updates, and SQL wildcard prefix matching. All were corrected and all six threads are resolved. Independent review also corrected proxy-hosted Geyser/Floodgate fallback behavior. Codacy reports zero new issues.

## Exact-head validation

Frozen head: `15608bc3099dc34aa080c80ca8e824ffd51cdae4`.

- Coverage run `31133176482`, job `92726659126`: success on allocated Ubuntu 24.04.
- Java: Temurin `21.0.12+8`.
- Build command: `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- Build: success; all module and MariaDB/Testcontainers integration/migration tests passed.
- Wiki run `31133176536`, job `92726609318`: success.
- CodeRabbit exact-head status: success; zero unresolved valid threads.
- Codacy: zero new issues; 61.54% diff coverage; exact-head coverage upload and final notification succeeded.
- Aggregate JaCoCo: 47.69% lines, 38.65% branches, 50.35% instructions.
- Validation artifact: ID `8977006850`, size 18,400,042 bytes, SHA-256 `fc93e698ba4ee81e38f05c307f61d52627e1735f6ffc642756fd4cd696ba261e`.
- Paper runtime JAR: 8,932,381 bytes; SHA-256 `81ff00cb50bc808db63ece6675b15e9a594e2350f84b113295037f03951e1c4c`; 4,762 entries.
- Velocity runtime JAR: 7,830,636 bytes; SHA-256 `65c87b47ef27d09ef9f36515365f62a4f238207452468f726979fa8f01006975`; 4,135 entries.
- Provider API source types checked: 24; provider API leaks: 0.

## Merge and containment

- PR #75 merged normally as `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.
- The merge commit has parents starting `main` and exact product head.
- Compare from product head to merge commit: one commit ahead, zero behind, merge base equals product head, and no changed files. Product work is fully contained with no unique branch work.
- Remote ref deletion could not be performed because the connected GitHub tool exposes no branch-delete action. This is a tooling limitation, not unmerged work; the implementation branch is inactive.

## Preserved boundaries

- ES-P02 PR #70 and branch remained untouched.
- ES-X05 PR #74 and branch remained untouched.
- ES-P09 retains alt graph/confidence/inheritance.
- ES-V02 retains representative distributed Java/Bedrock acceptance; no live-client staging pass is claimed.
- LiteBans remains authoritative; issue #43, production credentials/data/routes, deployment, migration/cutover, and authority activation remain excluded.

## Terminal state

ES-P03 is complete. Persistent package, workspace, registry, and latest-handoff state is published through the same-package documentation-only finalization. No next package is selected or activated.
