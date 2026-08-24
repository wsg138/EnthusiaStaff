# ES-D03 Discord authorization — review handoff

Status: `REVIEW`.

## Scope result
ES-D03 establishes the authoritative domain authorization contract for staff moderation initiated through Discord. It adds explicit platform-scoped consequences, runtime-supplied Helper/Mod ceilings, permanent/custom gates, read/lifecycle/approval/overturn capabilities, self/equal-higher staff protection, external role/punishment-policy preconditions, and confirmation-time stale-state reauthorization.

Developer has Mod-equivalent Discord authority without inheriting Minecraft punishment authority. Discord roles and command origin never grant domain authority. Minecraft mutations continue through the existing Minecraft `AuthorizationPolicy`, which is the independent permission source required by the product design.

No Discord bot/API runtime, schema migration, website or competition work, production Discord configuration, private production data, deployment, LiteBans authority change, cutover, or issue #43 acceptance is part of this package.

## Revisions
- Starting `main`: `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`.
- Branch: `package/es-d03-discord-authorization`.
- PR: #149, open and non-draft.
- Frozen executable product head: `ca66a97949cd8b9733c9039084d6230b2c63fd07`.
- Migration: none; live ceiling remains `V19__discord_moderation_persistence.sql`.

## Confirmed repairs
Three valid inline findings from the first CodeRabbit review were repaired: over-broad completion wording, incomplete workspace routing fields, and null consequence-element validation ordering. A prior independent boundary review also fixed the analogous null selected-platform input case. All originally posted inline threads were resolved after those repairs.

## Rejected resumed-review finding
A resumed full-diff review initially suspected that the public injectable `AuthorizationPolicy` could allow Developer to gain Minecraft authority and temporarily added an unconditional Developer deny plus a permissive-policy regression test.

That finding was rejected after checking the authoritative product contract. The contract says Developer's Discord Mod-equivalent rank does not grant Minecraft punishment authority, but a Developer may act on Minecraft when the Developer independently has the required Minecraft/domain permission. The injected existing `AuthorizationPolicy` is exactly that independent authority source. An unconditional Developer deny would therefore violate the approved design by blocking a separately granted Minecraft permission.

The temporary code/test change is reverted. This history remains documented so later workers do not repeat the same mistaken hardening. The final accepted product tree must be compared against frozen head `ca66a97949cd8b9733c9039084d6230b2c63fd07` to prove no executable residue from the rejected change remains.

Two earlier CodeRabbit body nitpicks remain non-defects under current invariants: `SanctionLength.Kind` has only `INSTANT`, `TEMPORARY`, and `PERMANENT`, while the consequence constructor/helper policy excludes invalid mute shapes before `within`; sharing the identical Mod/Developer operation set through one constant would be maintainability-only and not a correctness repair.

## Frozen-head evidence
Frozen product head `ca66a97949cd8b9733c9039084d6230b2c63fd07`:

- Coverage workflow `32673402553`, job `97277614870`: PASS.
- Exact SHA checkout and Temurin Java 21.0.12+8.
- `clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`: `BUILD SUCCESSFUL`; 49 actionable tasks (40 executed, 9 up-to-date).
- Aggregate JaCoCo: 50.58% line, 41.28% branch, 53.01% instruction.
- Runtime-JAR provider leak inspection: 24 API source types checked, zero leaks.
- Paper runtime SHA-256 `db550113d5db0b309c38faeb33bc8812d68f5bb645fb993dc0587d3ba4674c83`.
- Velocity runtime SHA-256 `1265e04ba54485575879217569b517fb096a2e27789f8a4f4e612a8e7659959c`.
- Validation artifact `9502084497`, digest `sha256:59fbfcb8aae83c301bdec3a6c0d98b1d616c678e6f9c3f9dc66421f718cb652a`.
- Codacy coverage upload/final notification: PASS.
- Sentinel Restart Artifact `32673402584`, job `97277584932`: PASS; artifact `9502002390`, digest `sha256:134545472c0edbe4fda685b8ca7b419ec28f12e002bccb97ad7db9685566451e`.
- CodeRabbit exact-product-head status: success; live inline threads were zero unresolved.

Reuse of this evidence is allowed only if the final candidate's exact diff from the frozen product head is package/state documentation only. Any executable, test, migration, workflow, build/runtime configuration or dependency delta requires fresh exact-head executable validation.

## Collision and safety state
At package start `main` was `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`. PR #139 remains unrelated parked ES-X03 work. D03 touches no website, competition or migration implementation path. Fresh-check live `main`, branches/PRs, migration ceiling, all check states and review threads immediately before merge.

No production Discord, private data, token, deployment, LiteBans authority or issue #43 cutover state changed.

## Exact next action
Restore the accepted product tree fully, prove the exact diff from frozen head `ca66a97949cd8b9733c9039084d6230b2c63fd07`, inspect current workflows/reviews/collisions, then publish terminal `COMPLETE` state only if every gate is satisfied. Merge PR #149 by normal merge commit, prove containment/no unique work, clean the temporary branch when safe, and mark D04/D05 `READY` without starting either package.
