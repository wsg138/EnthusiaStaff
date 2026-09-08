# ES-D16 moderation real-data read bridge — Paper migration classloader blocked handoff

Status: `BLOCKED` / `PARKED_BLOCKED`.

Date: 2026-09-04.
Repository: `wsg138/EnthusiaStaff`.
Canonical `main` at publication start: `9d0413ec17c73977fc5dc00bb93f3339c473fcb0`.
Implementation PR: #187 (open/unmerged/mergeable at checkpoint).
Implementation branch: `package/es-d16-moderation-read-bridge`.
Frozen executable candidate: `83bc4e102b85b9db904e9df4e7f956896fa938bf`.
Validated branch head: `587ea47f6e30aa468021497af6bed77d97c2975a`; only post-candidate delta is `moderation-web/README.md`.

## Outcome
Live acceptance progressed far enough to identify and repair two concrete environment-bound defects without weakening any safety boundary. First, real browser reads reached Bloom but the authoritative EnthusiaStaff MariaDB was empty. Second, after the owner authorized the temporary Paper bridge to initialize that database, Flyway connected successfully but discovered zero migrations under Paper because it was scanning the host thread-context classloader.

The executable candidate now gives Flyway the plugin-owning `TransitionDataRuntime` classloader, verifies required migration resources, and fails closed if they are not visible. Hosted build/integration/static/staging/review gates are terminal green. The package cannot become `COMPLETE` until the owner replaces the live temporary bridge JAR and performs one controlled Paper restart; this worker has no authenticated Bloom mutation surface.

## Live evidence retained in sanitized form
- A real Discord-generated panel `bootstrap` reached the private Staff Bot API and returned allowlisted `503 source_unavailable`.
- Bloom classified the backend failure as moderation persistence and recorded MariaDB 1146/42S02 for absent moderation tables, proving transport reached Bloom while schema was absent.
- The owner-authorized collector then connected to MariaDB, but Flyway logged successful validation of `0 migrations`, `No migrations found`, and an empty schema before creating only `flyway_schema_history`.
- The bridge JAR itself was independently inspected and contains repository migrations V1 through V20, isolating the fault to Paper resource discovery/classloader behavior rather than packaging.

No credentials, raw player rows, private messages, authority/component secrets, tunnel token, or reconstructable private evidence are recorded here.

## Repair and boundaries
`TransitionDataRuntime` now configures Flyway with `TransitionDataRuntime.class.getClassLoader()` rather than relying on Paper's thread context loader. Required V1/V19/V20 resources are checked before migration. Focused tests emulate a host loader that cannot see plugin resources, and a clean MariaDB/Testcontainers integration test proves migrate/import/restart semantics. The PMD `UseProperClassLoader` suppression is narrow and individually documented because the owning loader is intentionally required for this confirmed Paper boundary.

The owner-authorized transition collector remains bounded: current/cached player observation, bounded DiscordSRV snapshot/import through existing idempotency/conflict behavior, no legacy link/unlink mutation, aggregate-only logs, one worker with overlap prevention, and no LiteBans ingestion. Staff Bot remains read-only/no-Flyway. Authority remains private-only, signed, command-free, and moderation-mutation-free. Ports 8771/8766 remain non-public. No destructive moderation or full Paper runtime deployment is authorized.

## Exact validation evidence
For exact branch head `587ea47f6e30aa468021497af6bed77d97c2975a`:
- Coverage/full Java 21 `33846514820` / `100939581796`: PASS; clean build/integration tests; 27 provider API types / zero runtime leaks; JaCoCo 51.97% line / 42.27% branch / 54.27% instruction; artifact `9927145819`, digest `sha256:ef4a707b496a61d466af78909333fb7234b54419e062164ffb17dca6e153ba0a`.
- Moderation Web Staging Deploy `33846511302`: PASS on exact 587 head, including Worker deployment, fixed tunnel/DNS, launch/session, direct-read proof, staging-origin CORS, signed synthetic unauthorized 403 and replay rejection. The synthetic probe queried no real player/message data.
- Moderation Web Validation `33846514771`: PASS.
- Staff Bot Configuration Cache `33846514753`: PASS.
- Staff Bot PR Artifact `33846514759`: PASS.
- Sentinel Restart Artifact `33846514754`: PASS.
- Codacy static: PASS, zero annotations/new valid findings.
- Manual final-delta review: no new valid finding; all historical correctness threads resolved.

Exact authority-bridge artifact from run `33846514754`:
- artifact ID `9926742858`, ZIP digest `sha256:79a561c98ed05298f571cd9b214157bde3390b0fcf66af83ea7db14ead66deca`;
- `source.txt` = `587ea47f6e30aa468021497af6bed77d97c2975a`;
- `EnthusiaStaff-AuthorityBridge.jar` SHA-256 `af0e39fa63b84a397efa28fce0160008d4d65562ddb9c0461d00f9d3b5fb5a80`;
- V1 through V20 migration resources present.

Cancelled/superseded/wrong-head runs remain explicitly non-passing history.

## Exact external unblock
The owner performs one normal Paper stop/start and replaces only `plugins/EnthusiaStaff-AuthorityBridge.jar` with the exact artifact above. Keep existing `plugins/EnthusiaStaffAuthorityBridge/authority.properties` and existing `collector.properties` unchanged. Do not allocate 8771 or 8766 publicly and do not hot-reload the plugin.

Return only sanitized startup/collector lines: Flyway should discover/apply repository migrations instead of reporting `0 migrations`; then the collector should start and report an aggregate pass. Omit/redact the JDBC URL, credentials, secrets, IDs, raw rows, and private data. If migration fails, report only the exception class plus a sanitized migration version/name or SQL state where safe.

After that, open a fresh Discord-generated moderation preview. Report only whether bootstrap is 200/403/503 and whether real linked identity is visible; do not paste the signed envelope, signature, actor/guild IDs, or private message contents.

## Resume and containment
If live acceptance passes, reconcile current `main`, repair any new valid finding, rerun invalidated exact-head gates, merge PR #187 by normal merge commit only, prove containment and cleanup, update canonical D16 records to `COMPLETE`, and stop. ES-D13 PR #178, ES-X03 PR #139, D07, production/LiteBans/cutover, issue #43, destructive moderation, and message deletion remain untouched.
