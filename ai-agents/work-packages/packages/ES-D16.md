# ES-D16 — Moderation console real-data read bridge

Status: `BLOCKED` / `PARKED_BLOCKED`. Priority: 135.5. Depends on `ES-D03`, `ES-D05`, `ES-D06`, and merged web-first moderation foundation PR #186. Internal package.

Run ref: `ES-D16-20260831-real-data-read-bridge`.
Implementation PR: #187, open/unmerged on `package/es-d16-moderation-read-bridge`.
Frozen executable candidate: `83bc4e102b85b9db904e9df4e7f956896fa938bf`.
Validated branch head: `587ea47f6e30aa468021497af6bed77d97c2975a`; exact compare from the executable candidate changes only `moderation-web/README.md`.

## Objective
Connect the owner-approved Cloudflare moderation console to real, read-only Enthusia Discord/Minecraft/moderation data while preserving simulation-only punishment/deletion behavior and the existing D03 authority model.

## Owner production-acceptance authorization
The owner authorized the dedicated live Paper `paper-authority-bridge` instead of deploying the full `EnthusiaStaff-Paper.jar`. After live D16 reads exposed an empty EnthusiaStaff MariaDB, the owner further authorized that temporary bridge to apply the repository's existing Flyway migrations to that owner-configured database and gather bounded transition observations, especially current/cached Minecraft identity and existing DiscordSRV links.

The exception remains narrow: DiscordSRV is read-only, LiteBans remains authoritative and untouched, ports `8771` and `8766` remain non-public, and no warn/mute/kick/ban/restrict/freeze/inventory/economy/reputation/automod/message-deletion or other player-facing moderation mutation is authorized. Production-derived private values, player data, credentials, raw messages, or reconstructable evidence must not be copied into GitHub, ChatGPT, CI artifacts, or public logs.

## Delivered implementation
PR #187 provides the real-data read bridge, loopback-only Staff Bot read API, signed and replay-resistant Worker/session/direct-read path, explicit response allowlists, D03/D06 authorization, bounded JDA message reads, private Paper authority bridge, and simulation-only destructive controls.

The temporary transition collector is opt-in through runtime-only `collector.properties`. It opens the narrow write-capable `TransitionDataRuntime`, applies repository migrations, records bounded player observations, and imports eligible DiscordSRV snapshot links through existing idempotency/conflict semantics. DiscordSRV mutators are never called, LiteBans is not ingested, overlapping passes are skipped, snapshot/cached-player work is bounded, and logs contain aggregate counts/failure classes only. The separate Staff Bot remains JDBC read-only and never runs Flyway.

## Live acceptance findings and repair
A real Discord-generated preview reached the private Staff Bot API but returned allowlisted `503 source_unavailable`. Bloom then recorded MariaDB 1146/42S02 for missing moderation tables, proving the browser/Worker/session/proof/CORS/tunnel/read-API path reached Bloom while the authoritative EnthusiaStaff database had not been initialized.

The first owner-authorized transition-collector start connected to MariaDB successfully, but Paper logged `Successfully validated 0 migrations` and `No migrations found`, created only `flyway_schema_history`, and treated the empty schema as current. The shaded bridge JAR did contain the migrations; Flyway was scanning Paper's host thread-context classloader instead of the plugin's owning loader.

Frozen executable candidate `83bc4e102b85b9db904e9df4e7f956896fa938bf` repairs that Paper-only discovery defect: `TransitionDataRuntime` deliberately gives Flyway its owning classloader, verifies required V1/V19/V20 resources are visible, and fails closed otherwise. Tests emulate a host context classloader that cannot see plugin resources. Clean-database MariaDB/Testcontainers integration coverage proves migration/import/restart behavior. The narrow PMD `UseProperClassLoader` suppression is individually documented because the owning plugin loader is the required behavior for this confirmed Paper boundary.

## Exact-head validation — PASS
Exact branch head `587ea47f6e30aa468021497af6bed77d97c2975a` is documentation-only after executable candidate `83bc4e102b85b9db904e9df4e7f956896fa938bf` and passed the applicable full gates:

- Coverage/full Java 21 `33846514820` / job `100939581796`: PASS; clean build/integration tests; 27 provider API source types / zero runtime leaks; JaCoCo 51.97% lines / 42.27% branches / 54.27% instructions; artifact `9927145819`, digest `sha256:ef4a707b496a61d466af78909333fb7234b54419e062164ffb17dca6e153ba0a`.
- Moderation Web Staging Deploy `33846511302`: PASS, including permanent Worker deployment, fixed private tunnel/DNS, signed launch/session proof, exact staging-origin CORS, synthetic unauthorized 403, direct-read replay rejection, and one-time launch replay rejection without querying real player/message data.
- Moderation Web Validation `33846514771`: PASS.
- Staff Bot Configuration Cache `33846514753`: PASS.
- Staff Bot PR Artifact `33846514759`: PASS.
- Sentinel Restart Artifact `33846514754`: PASS.
- Codacy Static Code Analysis: PASS, zero annotations/new valid findings.
- Manual final-delta review: no new valid blocker; all historical correctness threads resolved.

Exact authority-bridge artifact from `33846514754`:
- artifact `9926742858`, `enthusiastaff-authority-bridge`;
- ZIP digest `sha256:79a561c98ed05298f571cd9b214157bde3390b0fcf66af83ea7db14ead66deca`;
- source marker `587ea47f6e30aa468021497af6bed77d97c2975a`;
- contained `EnthusiaStaff-AuthorityBridge.jar` SHA-256 `af0e39fa63b84a397efa28fce0160008d4d65562ddb9c0461d00f9d3b5fb5a80`;
- independent archive inspection confirms repository migrations V1 through V20 under `db/migration/`.

Historical failed/cancelled/superseded runs remain non-passing history and are not relabeled.

## Current blocker and exact unblock
All safe repository work is complete for this checkpoint. The remaining action requires the owner-operated live Paper process; this worker has no authenticated Bloom mutation surface.

The owner must perform one controlled Paper restart after replacing only `plugins/EnthusiaStaff-AuthorityBridge.jar` with the exact artifact above. Existing `plugins/EnthusiaStaffAuthorityBridge/authority.properties` and the already-created `collector.properties` stay unchanged; ports `8771` and `8766` stay without public Bloom allocations. Do not hot-reload the plugin.

Successful unblock evidence is sanitized startup output showing Flyway discovers/applies the repository migrations rather than `0 migrations`, followed by transition collector startup and an aggregate collector pass. The MariaDB 11.8 newer-than-verified Flyway warning is informational unless a migration actually fails. Do not expose the JDBC URL, credentials, secrets, raw player rows, or private messages.

## Remaining acceptance after restart
After schema/transition data is available, open a fresh Discord-generated moderation preview and complete sanitized D16 acceptance for real linked identity and target data, sanction/history semantics, bounded readable Discord messages, actor/guild/target/session binding, unauthorized denial, replay rejection, truthful outage behavior, and zero destructive mutation/deletion. Then reconcile moving `main`, rerun any invalidated exact-head gates, update canonical records, merge PR #187 normally, prove containment/cleanup, publish `COMPLETE`, and stop without starting another package.

ES-D13 PR #178 and ES-X03 PR #139 remain separate and untouched. Do not begin ES-D07 as part of this worker.

Canonical blocked handoff: `ai-agents/reports/package-handoffs/2026-09-04-es-d16-paper-migration-classloader-blocked.md`.
