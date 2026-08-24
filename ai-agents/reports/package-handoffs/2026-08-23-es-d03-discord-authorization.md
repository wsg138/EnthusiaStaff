# ES-D03 Discord authorization — complete handoff

Status: `COMPLETE`.

## Scope result
ES-D03 establishes the authoritative domain authorization contract for staff moderation initiated through Discord. It adds explicit platform-scoped consequences, runtime-supplied Helper/Mod ceilings, permanent/custom gates, read/lifecycle/approval/overturn capabilities, self/equal-higher staff protection, external role/punishment-policy preconditions, and confirmation-time stale-state reauthorization.

Developer has Mod-equivalent Discord authority without inheriting Minecraft punishment authority. Discord roles and command origin never grant domain authority. Minecraft mutations continue through the existing Minecraft `AuthorizationPolicy`, which supplies any independently granted Minecraft/domain permission required by the product design, and allowed mutations still require final punishment-policy revalidation.

No Discord bot/API runtime, schema migration, website or competition work, production Discord configuration, private production data, deployment, LiteBans authority change, cutover, or issue #43 acceptance was performed.

## Revisions
- Starting `main`: `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`.
- Branch: `package/es-d03-discord-authorization`.
- PR: #149.
- Frozen validated merge-ready head: `5cd98a719e30eff64d1595f1e219ea70553c66c0`.
- Earlier executable freeze used for parity proof: `ca66a97949cd8b9733c9039084d6230b2c63fd07`.
- Migration: none; live ceiling remained `V19__discord_moderation_persistence.sql`.
- Terminal state publication follows the frozen merge-ready head and changes only package/state/contract documentation.

## Review result
Four valid defects/state gaps were repaired across the package lifecycle: over-broad completion wording, incomplete workspace routing fields, null consequence-element validation ordering, and the analogous null selected-platform input case. All three posted inline CodeRabbit threads are resolved.

A resumed independent review then investigated whether the injectable Minecraft `AuthorizationPolicy` could improperly elevate Developer. The first hypothesis was wrong: the authoritative product contract explicitly allows a Developer to use Discord for a Minecraft action when the Developer independently has the required Minecraft/domain permission. The injected existing `AuthorizationPolicy` is the intended source of that independent authority. A temporary unconditional Developer deny and its regression test were therefore reverted because they would have violated the approved design.

The rejected finding was not hidden. Exact comparison from `ca66a97949cd8b9733c9039084d6230b2c63fd07` to merge-ready head `5cd98a719e30eff64d1595f1e219ea70553c66c0` showed only five documentation/state files and zero executable, test, migration, workflow, build/runtime, provider-contract or dependency differences, proving the accepted executable tree was restored before fresh validation.

The two remaining CodeRabbit body nitpicks were verified as non-defects under current type invariants: Helper mute flow cannot reach `within` with a non-temporary `SanctionLength`, and sharing Mod/Developer operation-set constants is maintainability-only. CodeRabbit status is success on the frozen merge-ready head; live inline threads are zero unresolved.

## Exact-head evidence
Frozen merge-ready head `5cd98a719e30eff64d1595f1e219ea70553c66c0`:

- Coverage workflow `32679597750`, job `97293850461`: PASS.
- Workflow checked out exact SHA `5cd98a719e30eff64d1595f1e219ea70553c66c0` and used Temurin Java 21.0.12+8.
- Gradle command: `clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`; `BUILD SUCCESSFUL` in 6m43s; 49 actionable tasks (40 executed, 9 up-to-date).
- Aggregate JaCoCo: 50.59% line, 41.28% branch, 53.01% instruction.
- Runtime-JAR inspection checked 24 provider API source types with zero leaks.
- Paper runtime: 9,327,594 bytes; SHA-256 `cc44a405deee2d7aa7fc6ee7f3579b1debd5317c2dbe7cb789a9dc97dfbb9881`; 4,942 entries.
- Velocity runtime: 8,068,526 bytes; SHA-256 `b7b1c27c2fccbb9db915acd782a4cbdedda2840d9af01c6d6a9a7d0917c699d5`; 4,253 entries.
- Validation artifact `9503824839`, digest `sha256:5e1b9ece92ce858475e6090dad50b53bf21060961ce4bdbd845020ea568c986d`.
- Codacy coverage upload and final notification: PASS on exact SHA.
- Sentinel Restart Artifact workflow `32679597852`, job `97293814018`: PASS; artifact `9503739521`, digest `sha256:01910f60d519208e46008d9bd986e2b04f2b7922dc4f3b56c9d93dc5f21126b1`.
- CodeRabbit exact-head status: success.
- Live PR #149 inline review threads immediately before terminal publication: zero unresolved.

D03 has no Discord runtime or destructive side effect, so production/staging Discord execution is not an applicable acceptance gate.

## Final reconciliation
Immediately before terminal publication:

- `main` remained `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`;
- the merge-ready D03 head was ahead and zero behind;
- PR #139 remained independently parked ES-X03 work;
- the legacy website branch was zero commits ahead and 156 behind `main`; no competition branch existed; D03 changed no website/competition implementation path;
- the live migration ceiling remained V19 and D03 added no migration;
- issue #43 remained open and LiteBans remained authoritative;
- no production secret, Discord role/configuration, data, deployment, punishment authority or cutover changed.

## Routing after completion
This handoff and the terminal registry/package/workspace records are included in PR #149 and become canonical through its normal merge. The worker then verifies feature-head containment/no unique work and safe branch cleanup. `ES-D04 — Account linking and DiscordSRV migration` and `ES-D05 — Staff bot runtime foundation` are newly dependency-complete and marked `READY`; D04 is the lower-priority-number next owner selection. This worker starts neither one.
