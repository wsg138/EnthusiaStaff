# `ES-D01` — Discord domain and identity contract

## 1. Package identity
`ES-D01`; Internal; primary `COMP-STAFF`; priority 130; parallel safe only with work that does not touch shared domain/package-state files.

## 2. Status
`ACTIVE` — explicitly owner-activated on 2026-08-23. Starting `main`: `a0337614a85fab6e9b29beff663396cea86cdce1`.

## 3. Objective
Establish the type-safe domain contract for moderation subjects, Discord identities, Discord↔Minecraft linking cardinality/history, automatic/staff-overridden main Minecraft account selection, explicit enforcement scopes, and 30-day inactive-case closure policy without adding persistence or runtime enforcement.

## 4. Why the package exists
The approved Discord expansion requires Discord-only moderation targets and explicit cross-platform scope. Existing authority is UUID/player-centric and current sanction types do not provide a safe identity/scope foundation to retrofit after bot UI or schema work exists.

## 5. Included audit IDs
Owner-approved Discord expansion design in PR #144 and `docs/discord-moderation-platform.md` on `docs/discord-moderation-expansion-spec`. No historical audit ID is repurposed.

## 6. Included behavior
Moderation subject identity set; Discord snowflake value objects; Minecraft and Discord identity references; one Discord→many Minecraft link allowance with one-current-Discord-per-Minecraft enforcement; historical unlink representation; link origin; first-link main-account selection; 25% active-playtime switch threshold; staff override precedence; explicit Discord-guild/Minecraft-server/Minecraft-network enforcement scopes; nonempty multi-scope selection; 30-day OPEN-case inactivity policy.

## 7. Explicit exclusions
No Flyway migration/repository implementation (`ES-D02`); no authorization changes (`ES-D03`); no link codes, DiscordSRV import, PlayTimePlugin adapter or staff command (`ES-D04`); no JDA/bot runtime (`ES-D05`); no Discord punishment enforcement, AutoMod, website, public bot, role sync, live Discord API call, production data, or cutover.

## 8. Dependencies
Explicit owner activation in this conversation. PR #144 remains the approved design reference until its documentation is reconciled/merged. Existing `ES-X03` remains parked and is not disturbed.

## 9. Component and repository boundaries
Allowed: `domain/` source/tests plus directly necessary `ai-agents/` package routing records. No `components/enthusia-site/`, website implementation, competition-related work, persistence migrations, Paper/Velocity runtime behavior, or external repository changes.

## 10. Required branches
`package/es-d01-discord-identity-contract`, created from exact `main` `a0337614a85fab6e9b29beff663396cea86cdce1`.

## 11. Required PRs
One draft-then-reviewed PR to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
- [x] Reconcile live `main`, open PRs and parked ES-X03.
- [x] Define moderation subject and platform identity contracts.
- [x] Define Discord/Minecraft link cardinality/history policy.
- [x] Define main-account selection policy and 25% active-playtime stability threshold.
- [x] Define explicit enforcement-scope types without Cartesian sanction enums.
- [x] Define 30-day inactive-case domain policy.
- [x] Add focused domain unit tests.
- [ ] Open draft implementation PR.
- [ ] Run exact-head CI/static/review gates and repair valid findings.
- [ ] Reconcile package state/handoff and documentation.
- [ ] Freeze final head, validate, merge normally, verify containment, clean branch.

## 13. Acceptance criteria
Domain code permits Discord-only subjects; permits one Discord account to own several current Minecraft links; rejects two current Discord owners for one Minecraft UUID; preserves ended link records; first linked Minecraft account starts as main; automatic main changes only at >=25% active-playtime advantage; staff override wins and stale override fails closed; cross-platform selection is multiple explicit scopes rather than a magic BOTH state; only OPEN cases become inactivity-close candidates at 30 days.

## 14. Test requirements
Focused JUnit domain tests plus repository Java 21 build/test/coverage and architecture checks. No MariaDB/Testcontainers migration test is newly required because D01 adds no persistence.

## 15. Static-analysis requirements
All applicable repository static/review checks on the exact final PR head; zero valid unresolved findings.

## 16. Documentation requirements
Package routing/handoff records now; final user/developer docs may be reconciled with PR #144 without modifying website content.

## 17. Security and privacy requirements
Discord identifiers are opaque IDs only. No tokens, credentials, private messages, player data, legacy link list, raw network identity, or production Discord data may enter Git.

## 18. Migration impact
None. Existing Flyway history remains byte-identical; schema work belongs to `ES-D02`.

## 19. Bedrock considerations
Minecraft identity remains the canonical UUID identity from existing Player/Floodgate work. This package does not infer platform from names or prefixes.

## 20. Distributed-runtime considerations
Pure domain package only. Scope values are explicit so later workers can persist separate platform intent and recover partial external enforcement independently.

## 21. External-provider considerations
No provider call is added. Future main-account active minutes must come from PlayTimePlugin's supported service API, never direct SQLite reads.

## 22. Completion definition
One normal-merge PR, complete domain behavior/tests, exact-head applicable checks green, zero valid unresolved review threads, package state published, containment verified, temporary branch safely removed.

## 23. Resume state
Worker: ChatGPT package worker. Branch: `package/es-d01-discord-identity-contract`. PR: pending first checkpoint. Start SHA: `a0337614a85fab6e9b29beff663396cea86cdce1`.

## 24. Last completed checkpoint
Owner assignment and live-GitHub preflight complete; domain contract and tests prepared for first branch checkpoint.

## 25. Remaining checklist
Open draft PR, execute hosted validation/review, fix confirmed defects, update routing records, finalize and merge if every required gate passes.

## 26. Known blockers
NONE. ES-X03 remains independently parked on its runtime-host blocker and is not a dependency of D01.

## 27. Final evidence
Pending.

## 28. Merge and synchronization record
Pending.
