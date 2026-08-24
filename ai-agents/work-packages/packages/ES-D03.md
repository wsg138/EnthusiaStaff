# ES-D03 — Authorization and cross-platform policy

Status: `REVIEW`. Priority: 132. Depends on `ES-D01`, `ES-D02` (both `COMPLETE`). Internal package.

## Revisions
- Starting `main`: `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`.
- Branch: `package/es-d03-discord-authorization`.
- PR: #149.
- Frozen executable product head: `ca66a97949cd8b9733c9039084d6230b2c63fd07`.
- Highest live Flyway migration remains `V19__discord_moderation_persistence.sql`; D03 adds no migration.

## Objective and implemented scope
D03 adds the authoritative domain authorization contract for moderation initiated through Discord. It models explicit platform-scoped consequences, runtime-supplied Helper/Mod duration ceilings, permanent/custom gates, read/lifecycle/approval/overturn capabilities, self and equal/higher-staff protection, confirmation-time stale reauthorization, and external enforcement preconditions.

Developer receives Mod-equivalent Discord authority without inheriting Minecraft punishment authority. Every Minecraft mutation continues through the existing `AuthorizationPolicy` and requires final Minecraft punishment-policy revalidation. Discord roles and command origin never grant domain authority. Future entry points must resolve authoritative actor/target staff state and call this service before enforcement.

The approved product contract permits a Developer to act on Minecraft only when the existing Minecraft/domain policy independently grants the required permission. D03 therefore must not convert Discord Developer rank into Minecraft authority, but it also must not impose an unconditional Minecraft ban that overrides independently granted domain authority.

No bot/API runtime, schema migration, website or competition implementation, production Discord configuration, production/private data, deployment, LiteBans authority change, cutover, or issue #43 acceptance is part of D03.

## Harsh-review result
The first reviewed candidate was not accepted blindly:

- package completion wording was scoped to the domain-service contract instead of claiming enforcement for callers outside this package;
- workspace routing fields were completed;
- null consequence elements are rejected as `IllegalArgumentException` before defensive copying, with regression coverage;
- an independent boundary review fixed the analogous null selected-platform input case with regression coverage.

A later resumed-review hypothesis claimed that the injectable `AuthorizationPolicy` allowed Developer elevation. Verification against the authoritative product spec rejected that finding: the injected policy represents the independent Minecraft/domain permission explicitly required by the design. A temporary unconditional Developer deny was therefore reverted because it would have incorrectly prevented independently authorized Minecraft actions. This rejected finding is retained in the handoff rather than being hidden or mislabeled as a defect.

The two prior CodeRabbit body nitpicks remain non-defects under current type invariants: `SanctionLength.Kind` is exactly `INSTANT`, `TEMPORARY`, `PERMANENT`, and `DiscordConsequenceIntent` plus the Helper path exclude non-temporary mute shapes before `within`; duplicated Mod/Developer operation sets are a low-value maintainability suggestion rather than a correctness issue.

## Exact-head validation
Frozen product head `ca66a97949cd8b9733c9039084d6230b2c63fd07` passed:

- Coverage workflow `32673402553`, job `97277614870`: exact SHA checkout, Temurin Java 21.0.12+8, `clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`; `BUILD SUCCESSFUL` in 6m59s with 49 actionable tasks (40 executed, 9 up-to-date).
- Aggregate JaCoCo: 50.58% lines, 41.28% branches, 53.01% instructions.
- Runtime-JAR inspection checked 24 provider API source types with zero leaks. Paper: 9,327,594 bytes, SHA-256 `db550113d5db0b309c38faeb33bc8812d68f5bb645fb993dc0587d3ba4674c83`. Velocity: 8,068,526 bytes, SHA-256 `1265e04ba54485575879217569b517fb096a2e27789f8a4f4e612a8e7659959c`.
- Validation artifact `9502084497`, digest `sha256:59fbfcb8aae83c301bdec3a6c0d98b1d616c678e6f9c3f9dc66421f718cb652a`.
- Codacy coverage upload and final notification succeeded for the exact frozen SHA.
- Sentinel Restart Artifact workflow `32673402584`, job `97277584932`: success; artifact `9502002390`, digest `sha256:134545472c0edbe4fda685b8ca7b419ec28f12e002bccb97ad7db9685566451e`.
- CodeRabbit exact-product-head status: success; live inline review threads at that point: zero unresolved.

Before reusing this evidence, the final candidate must be proven by exact comparison to differ from `ca66a97949cd8b9733c9039084d6230b2c63fd07` only in allowed package/state documentation. Any remaining executable delta requires fresh exact-head validation.

D03 has no Discord runtime or destructive side effect, so production/staging Discord execution is not itself a package acceptance gate. No production secret, role, token, data, deployment, punishment authority, or cutover state may be touched.

## Collision and authority state
Starting `main` is `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`; PR #139 is independently parked ES-X03 work. D03 changed no website, competition or migration implementation path. Reconcile these facts again immediately before final merge.

LiteBans remains authoritative and issue #43 remains separately gated.

## Completion
D03 becomes `COMPLETE` only after exact comparison proves the accepted product tree, every applicable current check/review state is green or validly covered by the frozen-head rule, every valid finding is resolved, PR #149 merges with a normal merge commit, feature-head containment/no unique work is proven, and safe temporary-branch cleanup is completed or its tooling limitation is recorded. Only then may `ES-D04` and `ES-D05` be marked `READY`; this worker starts neither one.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-23-es-d03-discord-authorization.md`.
