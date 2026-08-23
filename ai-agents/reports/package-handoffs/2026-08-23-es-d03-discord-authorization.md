# ES-D03 Discord authorization — complete handoff

Status: `COMPLETE`.

## Scope result
ES-D03 establishes the authoritative domain authorization contract for staff moderation initiated through Discord. It adds explicit platform-scoped consequences, runtime-supplied Helper/Mod ceilings, permanent/custom gates, read/lifecycle/approval/overturn capabilities, self/equal-higher staff protection, external role/punishment-policy preconditions, and confirmation-time stale-state reauthorization.

Developer has exactly Mod-equivalent Discord authority without inheriting Minecraft punishment authority. Minecraft mutations still use the existing `AuthorizationPolicy` and require final punishment-policy revalidation. Discord roles and command origin never grant domain authority. Future callers must resolve authoritative actor and target-staff state before authorization and must satisfy every returned external precondition immediately before enforcement.

No Discord bot/API runtime, schema migration, website or competition work, production Discord configuration, private production data, deployment, LiteBans authority change, cutover, or issue #43 acceptance was performed.

## Revisions
- Starting `main`: `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`.
- Branch: `package/es-d03-discord-authorization`.
- PR: #149.
- Frozen product head: `ca66a97949cd8b9733c9039084d6230b2c63fd07`.
- Migration: none; live ceiling remained `V19__discord_moderation_persistence.sql`.
- Terminal state publication follows the frozen product head and changes only Markdown state/documentation files.

## Review result
Three valid inline findings on the first reviewed candidate were repaired: over-broad completion wording, incomplete workspace routing fields, and null consequence-element validation ordering. Independent harsh review then found and fixed the analogous null selected-platform input case with regression coverage.

The complete executable diff was reviewed against the approved Discord policy and current Minecraft authorization invariants. Two additional bot-body nitpicks were verified as non-defects under current type invariants and were intentionally not turned into speculative refactors. All three live inline review threads are resolved. CodeRabbit reports success on the frozen product head; no second full bot-review approval is claimed for the post-review executable delta, which was manually harsh-reviewed.

## Exact-product-head evidence
Frozen product head `ca66a97949cd8b9733c9039084d6230b2c63fd07`:

- Coverage workflow `32673402553`, job `97277614870`: PASS.
- Workflow explicitly checked out the exact frozen SHA and used Temurin Java 21.0.12+8.
- Gradle command: `clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`; `BUILD SUCCESSFUL` in 6m59s; 49 actionable tasks (40 executed, 9 up-to-date).
- Aggregate JaCoCo: 50.58% line, 41.28% branch, 53.01% instruction.
- Runtime-JAR inspection checked 24 provider API source types with zero leaks.
- Paper runtime: 9,327,594 bytes; SHA-256 `db550113d5db0b309c38faeb33bc8812d68f5bb645fb993dc0587d3ba4674c83`; 4,942 entries.
- Velocity runtime: 8,068,526 bytes; SHA-256 `1265e04ba54485575879217569b517fb096a2e27789f8a4f4e612a8e7659959c`; 4,253 entries.
- Validation artifact `9502084497`, digest `sha256:59fbfcb8aae83c301bdec3a6c0d98b1d616c678e6f9c3f9dc66421f718cb652a`.
- Codacy coverage report upload and final notification: PASS on the exact frozen SHA.
- Sentinel Restart Artifact workflow `32673402584`, job `97277584932`: PASS; artifact `9502002390`, digest `sha256:134545472c0edbe4fda685b8ca7b419ec28f12e002bccb97ad7db9685566451e`.
- CodeRabbit exact-product-head status: success.
- Live PR #149 inline review threads immediately before terminal publication: zero unresolved.

The coverage values are measurements, not a claim that a broader future repository coverage target is complete. D03 has no Discord runtime/destructive side effect, so production/staging Discord execution is not an applicable acceptance gate.

## Final reconciliation
Immediately before terminal publication:

- `main` remained `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`;
- the frozen D03 product head was five commits ahead and zero behind;
- PR #139 remained independently parked ES-X03 work;
- the legacy website branch was zero commits ahead of `main`, no competition branch existed, and D03 changed no website/competition implementation path;
- live migration ceiling remained V19 and D03 added no migration;
- no production secret, Discord role/configuration, data, deployment, punishment authority, LiteBans cutover, or issue #43 state changed.

## Routing after completion
This handoff and the terminal registry/package/workspace records are included in PR #149 and become canonical through its normal merge. The worker then verifies feature-head containment/no unique work and branch cleanup. `ES-D04 — Account linking and DiscordSRV migration` and `ES-D05 — Staff bot runtime foundation` are newly dependency-complete and marked `READY`; D04 is the lower-priority-number next owner selection. This worker starts neither one.
