# `ES-P03` — Bedrock identity correctness

## 1. Package identity
`ES-P03`; Internal; primary `COMP-STAFF`; priority 30; sequential identity-correctness package.

## 2. Status
`COMPLETE`.

## 3. Objective
Persist and resolve Java and Floodgate/Bedrock identity correctly across Paper, Velocity, moderation, and network identity.

## 4. Why the package exists
The audit confirmed unconditional Java platform persistence and related offline/name-history risks that affected later staff, reports, alts, and staging.

## 5. Included audit IDs
`AUD-ID-001`, `AUD-ID-004`, and only the canonical Java/Floodgate platform-identity and normalization fields of `AUD-ALT-004`.

## 6. Completed behavior
- Replaced unconditional Java persistence with proof-bearing Java/Bedrock/unknown observations.
- Added a shared `PlayerPlatformDetection` policy and Paper Floodgate resolver.
- Paper join persistence uses the verified observation path.
- Legacy/unverified proxy observations retain UUID, name history, and presence but persist platform as `UNKNOWN`.
- Only an available Floodgate per-player observation proves Java or Bedrock; absent, unavailable, or incompatible local providers remain `UNKNOWN`, including proxy-hosted layouts.
- Preserved configured single-`*` Bedrock aliases for current names, historical names, exact lookup, and prefix lookup without using username text as platform proof.
- Verified Bedrock repairs legacy Java/unknown rows and cannot be downgraded by weaker later observations; verified Java upgrades only unknown records.
- Ordered unequal timestamps by event time and equal timestamps by a stable data-derived binary tie-breaker independent of database arrival order.
- Required disconnects to be strictly later than the matching connection, preventing stale or equal-time disconnects from clearing presence.
- Escaped SQL `LIKE` wildcard characters so valid underscores are matched literally.
- Added domain and MariaDB/Testcontainers regressions for provider states, aliases/history, non-downgrade, unequal/equal ordering, disconnect races, literal underscore prefixes, and invalid alias shapes.
- Updated integration and package documentation while preserving the ES-P09 and ES-V02 boundaries.

## 7. Explicit exclusions preserved
Alt graph/confidence/ambiguity/manual-relationship/inheritance completion (`ES-P09`); changing Floodgate rules; representative live Bedrock acceptance; provider invention; unrelated identity redesign; production or issue #43 work.

## 8. Dependency and routing record
The ordinary dependency requires `ES-P02` complete, but ES-P02 remains `BLOCKED` / `PARKED_BLOCKED`. The repository owner explicitly directed the worker on 2026-08-06 to continue another productive package while ES-P02 and ES-X05 remained parked. The narrow routing exception permitted ES-P03 only; it did not mark ES-P02 complete, import PR #70, waive any ES-P03 gate, or automatically authorize later dependency exceptions.

## 9. Component and repository boundaries
Root identity/runtime/persistence/tests/docs only. No external source import or external parity requirement.

## 10. Branch and PR record
- Starting legitimate `main`: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Implementation branch: `package/es-p03-bedrock-identity`.
- Implementation PR: #75.
- Exact reviewed and validated product head: `15608bc3099dc34aa080c80ca8e824ffd51cdae4`.
- Normal implementation merge: `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.
- Containment: merge base between product head and implementation merge is the product head; `main` is one merge commit ahead with no changed files or unique branch product work.
- Remote branch deletion: not performed because the connected GitHub tool does not expose ref deletion; containment and absence of unique work were verified. The branch must not be treated as active work.

## 11. Acceptance criteria result
PASS for implementation correctness and automated evidence. Representative Java/Bedrock distributed staging remains deferred to `ES-V02` and is not claimed here.

## 12. Exact-head validation evidence
Frozen head `15608bc3099dc34aa080c80ca8e824ffd51cdae4`:

- Coverage workflow run `31133176482`, job `92726659126`: success on an allocated Ubuntu 24.04 hosted runner.
- Java: Temurin `21.0.12+8`.
- Command: `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- Result: `BUILD SUCCESSFUL`; all module tests and MariaDB/Testcontainers integration/migration checks passed.
- Wiki workflow run `31133176536`, job `92726609318`: success.
- CodeRabbit: success on the exact head; all six review threads resolved.
- Codacy: `0` new issues and `61.54%` diff coverage on the exact head; coverage upload/final notification succeeded.
- Aggregate JaCoCo: lines `47.69%`, branches `38.65%`, instructions `50.35%`.
- Artifact `java-21-validation`: ID `8977006850`, 18,400,042 bytes, archive SHA-256 `fc93e698ba4ee81e38f05c307f61d52627e1735f6ffc642756fd4cd696ba261e`.
- Paper runtime JAR: 8,932,381 bytes, SHA-256 `81ff00cb50bc808db63ece6675b15e9a594e2350f84b113295037f03951e1c4c`, 4,762 entries.
- Velocity runtime JAR: 7,830,636 bytes, SHA-256 `65c87b47ef27d09ef9f36515365f62a4f238207452468f726979fa8f01006975`, 4,135 entries.
- Provider API types checked: 24; provider API leaks: 0.
- Migration boundary: V1–V17 unchanged; no ES-P03 migration.

## 13. Security and privacy result
No raw addresses, player rows, credentials, private data, production routes, deployment, authority activation, or issue #43 action was used or committed.

## 14. Bedrock and distributed boundary
Implementation correctness is complete. `ES-V02` still owns representative Java/Bedrock distributed staging and must not treat this hosted test evidence as live-client acceptance.

## 15. Final checkpoint
Implementation merged normally; exact-head evidence, review resolution, containment, exclusions, and persistent completion state are recorded. No next package is activated.

## 16. Canonical handoff
[`2026-08-06-es-p03-bedrock-identity.md`](../../reports/package-handoffs/2026-08-06-es-p03-bedrock-identity.md)
