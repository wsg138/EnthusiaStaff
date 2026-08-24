# ES-D03 — Authorization and cross-platform policy

Status: `COMPLETE`. Priority: 132. Depends on `ES-D01`, `ES-D02` (both `COMPLETE`). Internal package.

## Revisions
- Starting `main`: `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`.
- Branch: `package/es-d03-discord-authorization`.
- PR: #149.
- Frozen validated merge-ready head: `5cd98a719e30eff64d1595f1e219ea70553c66c0`.
- Earlier executable freeze used for parity proof: `ca66a97949cd8b9733c9039084d6230b2c63fd07`.
- Highest live Flyway migration remained `V19__discord_moderation_persistence.sql`; D03 adds no migration.

## Objective and completed scope
D03 adds the authoritative domain authorization contract for moderation initiated through Discord. It models explicit platform-scoped consequences, runtime-supplied Helper/Mod duration ceilings, permanent/custom gates, read/lifecycle/approval/overturn capabilities, self and equal/higher-staff protection, confirmation-time stale reauthorization, and external enforcement preconditions.

Developer receives Mod-equivalent Discord authority without inheriting Minecraft punishment authority. Every Minecraft mutation continues through the existing `AuthorizationPolicy` and requires final Minecraft punishment-policy revalidation. Discord roles and command origin never grant domain authority. Future entry points must resolve authoritative actor/target staff state and call this service before enforcement.

The approved product contract permits a Developer to act on Minecraft only when the existing Minecraft/domain policy independently grants the required permission. D03 therefore does not convert Discord Developer rank into Minecraft authority and does not override independently granted Minecraft/domain authority.

No bot/API runtime, schema migration, website or competition implementation, production Discord configuration, production/private data, deployment, LiteBans authority change, cutover, or issue #43 acceptance is part of D03.

## Harsh-review result
The package was repeatedly reviewed rather than accepting the first implementation candidate:

- package completion wording was scoped to the domain-service contract instead of claiming enforcement for callers outside this package;
- workspace routing fields were completed;
- null consequence elements are rejected as `IllegalArgumentException` before defensive copying, with regression coverage;
- an independent boundary review fixed the analogous null selected-platform input case with regression coverage.

A later resumed-review hypothesis claimed that the injectable `AuthorizationPolicy` allowed Developer elevation and briefly added an unconditional Developer Minecraft deny. Verification against the authoritative product spec rejected that finding: the injected policy represents the independent Minecraft/domain permission explicitly allowed by design. The temporary hardening and its regression test were fully reverted. Exact comparison from `ca66a97949cd8b9733c9039084d6230b2c63fd07` to merge-ready head `5cd98a719e30eff64d1595f1e219ea70553c66c0` showed only documentation/state files, proving no executable/test residue remained.

The two prior CodeRabbit body nitpicks were verified as non-defects under current type invariants: `SanctionLength.Kind` is exactly `INSTANT`, `TEMPORARY`, `PERMANENT`, and the consequence constructor/Helper path exclude invalid mute shapes before `within`; sharing the identical Mod/Developer operation set through one constant would be maintainability-only rather than a correctness repair.

All three posted inline review threads are resolved. CodeRabbit status is success on the frozen merge-ready head.

## Exact-head validation
Frozen merge-ready head `5cd98a719e30eff64d1595f1e219ea70553c66c0` passed:

- Coverage workflow `32679597750`, job `97293850461`: exact SHA checkout, Temurin Java 21.0.12+8, `clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`; `BUILD SUCCESSFUL` in 6m43s with 49 actionable tasks (40 executed, 9 up-to-date).
- Aggregate JaCoCo: 50.59% lines, 41.28% branches, 53.01% instructions. These are repository measurements, not a relabeling of any broader future target.
- Runtime-JAR inspection checked 24 provider API source types with zero leaks. Paper: 9,327,594 bytes, SHA-256 `cc44a405deee2d7aa7fc6ee7f3579b1debd5317c2dbe7cb789a9dc97dfbb9881`, 4,942 entries. Velocity: 8,068,526 bytes, SHA-256 `b7b1c27c2fccbb9db915acd782a4cbdedda2840d9af01c6d6a9a7d0917c699d5`, 4,253 entries.
- Validation artifact `9503824839`, digest `sha256:5e1b9ece92ce858475e6090dad50b53bf21060961ce4bdbd845020ea568c986d`.
- Codacy coverage upload and final notification succeeded for the exact frozen SHA.
- Sentinel Restart Artifact workflow `32679597852`, job `97293814018`: success on the exact frozen SHA; artifact `9503739521`, digest `sha256:01910f60d519208e46008d9bd986e2b04f2b7922dc4f3b56c9d93dc5f21126b1`.
- CodeRabbit exact-head status: success; live inline review threads: zero unresolved.

D03 has no Discord runtime or destructive side effect, so production/staging Discord execution is not an applicable package acceptance gate. No production secret, role, token, data, deployment, punishment authority, or cutover state was touched.

## Final collision and authority state
Immediately before terminal publication, `main` remained `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`; the merge-ready D03 head was ahead and zero behind. PR #139 remained independently parked ES-X03 work. The legacy website branch was zero commits ahead and 156 behind `main`, no competition branch was found, and D03's changed paths contain no website, competition or migration implementation.

LiteBans remains authoritative and issue #43 remains open and separately gated.

## Completion
This terminal state/documentation follow-up changes no executable, test, migration, workflow, build/runtime configuration, provider contract, dependency or production path after frozen merge-ready head `5cd98a719e30eff64d1595f1e219ea70553c66c0`. It becomes canonical through PR #149's normal merge. After merge, the worker verifies feature-head containment/no unique work and safe temporary-branch cleanup. `ES-D04` and `ES-D05` become `READY`; this D03 worker does not start either package.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-23-es-d03-discord-authorization.md`.
