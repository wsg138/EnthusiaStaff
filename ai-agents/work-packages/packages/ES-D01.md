# `ES-D01` — Discord domain and identity contract

## 1. Package identity
`ES-D01`; Internal; primary `COMP-STAFF`; priority 130; parallel safe only with work that does not touch shared domain/package-state files.

## 2. Status
`READY_FOR_REVIEW` — implementation is frozen and exact-product-head hosted validation passed. Merge, containment verification, and branch cleanup remain pending.

Starting `main`: `a0337614a85fab6e9b29beff663396cea86cdce1`.
Frozen product head: `8490fbf8373ee9e237d83d12dcde5f37c970e5be`.
PR: #146.

## 3. Objective
Establish the type-safe domain contract for moderation subjects, Discord identities, Discord↔Minecraft linking cardinality/history, automatic/staff-overridden main Minecraft account selection, explicit enforcement scopes, and 30-day inactive-case closure policy without adding persistence or runtime enforcement.

## 4. Why the package exists
The approved Discord expansion requires Discord-only moderation targets and explicit cross-platform scope. Existing authority is UUID/player-centric and current sanction types do not provide a safe identity/scope foundation to retrofit after bot UI or schema work exists.

## 5. Included audit IDs
Owner-approved Discord expansion design in PR #144 and `docs/discord-moderation-platform.md` on `docs/discord-moderation-expansion-spec`. No historical audit ID is repurposed.

## 6. Included behavior
Moderation subject identity set; full-range Discord snowflake value objects; Minecraft and Discord identity references; one Discord→many Minecraft link allowance with one-current-Discord-per-Minecraft enforcement; historical unlink representation; link origin; first-link main-account selection; 25% active-playtime switch threshold; staff override precedence and source preservation; explicit Discord-guild/Minecraft-server/Minecraft-network enforcement scopes; platform-matched enforcement targets; nonempty multi-scope selection; 30-day OPEN-case inactivity policy.

Linked Minecraft accounts are known linked alts for staff identity/investigation purposes, but this package intentionally does not map verified Discord links directly to the legacy alt-confidence states because those states can carry automatic sanction-inheritance semantics that the approved Discord design does not authorize.

## 7. Explicit exclusions
No Flyway migration/repository implementation (`ES-D02`); no authorization changes (`ES-D03`); no link codes, DiscordSRV import, PlayTimePlugin adapter or staff command (`ES-D04`); no JDA/bot runtime (`ES-D05`); no Discord punishment enforcement, AutoMod, website, public bot, role sync, live Discord API call, production data, or cutover.

## 8. Dependencies
Explicit owner activation on 2026-08-23. PR #144 remains the approved design reference until its documentation is reconciled/merged. Existing `ES-X03` remains parked and is not disturbed.

## 9. Component and repository boundaries
Allowed: `domain/` source/tests plus directly necessary `ai-agents/` package state/handoff records. No `components/enthusia-site/`, website implementation, competition-related work, persistence migrations, Paper/Velocity runtime behavior, or external repository changes.

## 10. Required branches
`package/es-d01-discord-identity-contract`, created from exact `main` `a0337614a85fab6e9b29beff663396cea86cdce1`.

## 11. Required PRs
PR #146 to `wsg138/EnthusiaStaff:main`, opened draft and promoted for review after frozen-product validation.

## 12. Implementation checklist
- [x] Reconcile live `main`, open PRs and parked ES-X03.
- [x] Define moderation subject and platform identity contracts.
- [x] Define Discord/Minecraft link cardinality/history policy.
- [x] Define main-account selection policy and 25% active-playtime stability threshold.
- [x] Preserve automatic versus staff-overridden effective main-account source.
- [x] Define explicit enforcement-scope types without Cartesian sanction enums.
- [x] Bind enforcement targets to same-platform identity/scope pairs.
- [x] Define 30-day inactive-case domain policy with fail-closed incomplete-state handling.
- [x] Add focused domain unit tests.
- [x] Open draft implementation PR #146.
- [x] Run exact-product-head hosted build/test/coverage/runtime-JAR validation.
- [x] Check applicable review/static status and live review threads.
- [x] Freeze product head `8490fbf8373ee9e237d83d12dcde5f37c970e5be`.
- [x] Reconcile package state/handoff documentation.
- [ ] Complete human/owner PR review.
- [ ] Merge normally only after approval.
- [ ] Verify post-merge containment and clean temporary branch.

## 13. Acceptance criteria
Domain code permits Discord-only subjects; permits one Discord account to own several current Minecraft links; rejects two current Discord owners for one Minecraft UUID; preserves ended link records; first linked Minecraft account starts as main; automatic main changes only at >=25% active-playtime advantage; staff override wins and stale override fails closed; effective main preserves its selection source; enforcement targets cannot pair a Discord scope with a Minecraft identity or vice versa; cross-platform selection is multiple explicit scopes rather than a magic BOTH state; only OPEN cases become inactivity-close candidates at 30 days.

## 14. Test requirements
Focused JUnit domain tests plus repository Java 21 build/test/coverage and architecture/runtime-JAR checks. No MariaDB/Testcontainers migration test is newly required because D01 adds no persistence.

Frozen product head `8490fbf8373ee9e237d83d12dcde5f37c970e5be` passed Coverage run `32656085922`, job `97234994129`: `./gradlew clean build jacocoAggregateReport runtimeJars` completed successfully in 6m48s. Runtime-JAR inspection found zero provider API leaks. Aggregate repository coverage was 49.18% line / 40.27% branch / 51.69% instruction; these are measurements, not a claim that the repository-wide final coverage target has already been reached.

Validation artifact: `9497561103`, digest `sha256:af9cbc0a9c53f424cbe641ec2c99ff62115c745243ec048431c79d57e63d003f`.

## 15. Static-analysis requirements
Applicable exact-product-head status showed CodeRabbit success and zero live inline review threads. Codacy coverage upload/final notification succeeded in Coverage run `32656085922`. No broader static-analysis claim is made beyond the checks actually present for this PR/head.

## 16. Documentation requirements
Package state and canonical handoff are published in this PR. User/developer Discord product documentation remains in PR #144 and is not duplicated into runtime code.

## 17. Security and privacy requirements
Discord identifiers are opaque IDs only. No tokens, credentials, private messages, player data, legacy link list, raw network identity, or production Discord data entered Git.

## 18. Migration impact
None. Existing Flyway history remains byte-identical; schema work belongs to `ES-D02`. Current live `main` already contains migrations through V18, so D02 must fresh-check the next available migration number rather than assuming V18 is free.

## 19. Bedrock considerations
Minecraft identity remains the canonical UUID identity from existing Player/Floodgate work. This package does not infer platform from names or prefixes.

## 20. Distributed-runtime considerations
Pure domain package only. Scope values are explicit so later workers can persist separate platform intent and recover partial external enforcement independently.

## 21. External-provider considerations
No provider call is added. Current `wsg138/PlayTimePlugin` exposes `PlaytimeService.getLifetime(UUID)` and `PlaytimeSnapshot.activeMinutes` as a `long`, which matches this contract. D04 must not recompute an automatic main from guessed zero data when the provider is unavailable.

## 22. Completion definition
One normal-merge PR, complete domain behavior/tests, exact-head applicable checks green, zero valid unresolved review threads, package state published, containment verified, temporary branch safely removed.

## 23. Resume state
Worker: ChatGPT package worker. Branch: `package/es-d01-discord-identity-contract`. PR: #146. Starting SHA: `a0337614a85fab6e9b29beff663396cea86cdce1`. Frozen product SHA: `8490fbf8373ee9e237d83d12dcde5f37c970e5be`. Product implementation is frozen; remaining work is review/merge/containment only.

## 24. Last completed checkpoint
Exact-product-head Java 21 build/tests, aggregate coverage generation, runtime-JAR inspection, Sentinel artifact build, CodeRabbit status, and zero-thread review check passed. Package state was reconciled without changing the frozen product tree.

## 25. Remaining checklist
Human/owner review of PR #146, normal merge if approved, post-merge containment verification, branch cleanup, then a fresh worker may select dependent `ES-D02`.

## 26. Known blockers
NONE inside D01. ES-X03 remains independently parked on its runtime-host blocker and is not a dependency of D01.

## 27. Final evidence
- Frozen product head: `8490fbf8373ee9e237d83d12dcde5f37c970e5be`.
- Coverage/full Java 21 run: `32656085922`; job `97234994129`; success.
- Build: success, 49 actionable tasks (40 executed, 9 up-to-date).
- Runtime JARs: Paper SHA-256 `8477186ffca9080d69c5f8acb3937f28041823c35adb506f43bda65d310aee88`; Velocity SHA-256 `ed83e10c8afab31aa04ddfc6b15d7b2c611022606ef2995743c713393a962eb7`; provider API leaks 0.
- Aggregate coverage: line 49.18%, branch 40.27%, instruction 51.69%.
- Validation artifact: `9497561103`; digest `sha256:af9cbc0a9c53f424cbe641ec2c99ff62115c745243ec048431c79d57e63d003f`.
- Sentinel exact-head artifact run: `32656085940`; success.
- CodeRabbit exact-product-head status: success.
- Live inline review threads before state publication: 0.
- Website/competition/component paths changed: 0.

## 28. Merge and synchronization record
Pending owner/human review and normal merge. No deployment, production data change, Discord configuration change, schema migration, authority change, or cutover occurred in D01.