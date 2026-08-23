# ES-D03 — Authorization and cross-platform policy

Status: `COMPLETE`. Priority: 132. Depends on `ES-D01`, `ES-D02` (both `COMPLETE`). Internal package.

## Revisions
- Starting `main`: `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`.
- Branch: `package/es-d03-discord-authorization`.
- PR: #149.
- Frozen product head: `ca66a97949cd8b9733c9039084d6230b2c63fd07`.
- Highest live Flyway migration remained `V19__discord_moderation_persistence.sql`; D03 adds no migration.

## Objective and completed scope
D03 adds the authoritative domain authorization contract for moderation initiated through Discord. It models explicit platform-scoped consequences, runtime-supplied Helper/Mod duration ceilings, permanent/custom gates, read/lifecycle/approval/overturn capabilities, self and equal/higher-staff protection, confirmation-time stale reauthorization, and external enforcement preconditions.

Developer receives Mod-equivalent Discord authority without inheriting Minecraft punishment authority. Every Minecraft mutation continues through the existing `AuthorizationPolicy` and requires final Minecraft punishment-policy revalidation. Discord roles and command origin never grant domain authority. Future entry points must resolve authoritative actor/target staff state and call this service before enforcement.

No bot/API runtime, schema migration, website or competition implementation, production Discord configuration, production/private data, deployment, LiteBans authority change, cutover, or issue #43 acceptance is part of D03.

## Harsh-review repairs
The first reviewed candidate was not accepted blindly. Before freezing the product head:

- the package completion wording was corrected so it describes the domain-service contract instead of claiming enforcement for callers outside this package;
- `WORKSPACE-STATE.md` was repaired to include the required PR, intended terminal status, completed/next work, next owner priority, migration boundary, blocker and handoff fields;
- null consequence elements are now rejected as `IllegalArgumentException` before defensive copying, with regression coverage;
- an independent boundary review found the analogous null selected-platform case and hardened it with regression coverage;
- the complete executable diff was manually harsh-reviewed after the bot review. Two bot-body nitpicks were verified as non-defects under the current type invariants and were not converted into speculative refactors.

All three live inline review threads are resolved. CodeRabbit reports success on the frozen product head. The post-review executable delta was manually reviewed; no second full bot-review approval is claimed.

## Exact-product-head validation
Frozen product head `ca66a97949cd8b9733c9039084d6230b2c63fd07` passed:

- Coverage workflow `32673402553`, job `97277614870`: exact SHA checkout, Temurin Java 21.0.12+8, `clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`; `BUILD SUCCESSFUL` in 6m59s with 49 actionable tasks (40 executed, 9 up-to-date).
- Aggregate JaCoCo: 50.58% lines, 41.28% branches, 53.01% instructions. These are recorded repository measurements, not a relabeling of any broader future target.
- Runtime-JAR inspection checked 24 provider API source types with zero leaks. Paper: 9,327,594 bytes, SHA-256 `db550113d5db0b309c38faeb33bc8812d68f5bb645fb993dc0587d3ba4674c83`, 4,942 entries. Velocity: 8,068,526 bytes, SHA-256 `1265e04ba54485575879217569b517fb096a2e27789f8a4f4e612a8e7659959c`, 4,253 entries.
- Validation artifact `9502084497`, digest `sha256:59fbfcb8aae83c301bdec3a6c0d98b1d616c678e6f9c3f9dc66421f718cb652a`.
- Codacy coverage upload and final notification succeeded for the exact frozen SHA.
- Sentinel Restart Artifact workflow `32673402584`, job `97277584932`: success on the exact frozen SHA; artifact `9502002390`, digest `sha256:134545472c0edbe4fda685b8ca7b419ec28f12e002bccb97ad7db9685566451e`.
- CodeRabbit exact-product-head status: success; live inline review threads: zero unresolved.

D03 has no Discord runtime or destructive side effect, so production/staging Discord execution is not an applicable acceptance gate. No production secret, role, token, data, deployment, punishment authority, or cutover state was touched.

## Final collision and authority state
Immediately before terminal publication, `main` remained `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`; the frozen product head was five commits ahead and zero behind. PR #139 remained the independently parked ES-X03 work. The old website branch was zero commits ahead of `main`, no competition branch was found, and D03's changed paths contain no website, competition or migration implementation.

LiteBans remains authoritative and issue #43 remains separately gated.

## Completion
This terminal state/documentation record follows the frozen executable product head and is part of PR #149. It becomes canonical through that PR's normal merge. After merge, the worker verifies feature-head containment/no unique work and temporary-branch cleanup. `ES-D04` and `ES-D05` become `READY`; this D03 worker does not start either package.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-23-es-d03-discord-authorization.md`.
