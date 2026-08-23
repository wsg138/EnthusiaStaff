# ES-D01 Discord domain and identity contract — ready for review

Status: `READY_FOR_REVIEW`.

## Scope result

ES-D01 established only the pure domain foundation for the approved Discord moderation expansion. No website, competition, persistence migration, Paper/Velocity behavior, Discord API call, production data, authority, deployment, or cutover path changed.

Implemented contracts cover:

- Discord-only, Minecraft-only and linked moderation subjects;
- unsigned/full-range Discord snowflake identifiers;
- one Discord account linked to multiple current Minecraft UUIDs while each Minecraft UUID has at most one current Discord owner;
- historical link records and link-source attribution;
- first-link main-account selection, 25% active-playtime switch hysteresis, and durable staff-override semantics;
- explicit Discord-guild, Minecraft-server and Minecraft-network scopes;
- same-platform identity/scope enforcement targets;
- 30-day OPEN-case inactivity closure policy with incomplete-state fail-closed behavior.

Linked Minecraft accounts are intentionally not mapped directly to legacy automatic sanction-inheritance alt states. They remain known linked alts for staff identity/investigation, preserving the approved alert-only response to suspected linked-alt punishment evasion.

## Revisions

- Starting `main`: `a0337614a85fab6e9b29beff663396cea86cdce1`.
- Branch: `package/es-d01-discord-identity-contract`.
- PR: #146.
- Frozen product head: `8490fbf8373ee9e237d83d12dcde5f37c970e5be`.
- Product tree: `57f60f4968325b99d0eb675f8ff78a93a01ce2fb`.
- State publication follows the frozen product head and changes only `ai-agents` Markdown state/handoff records.

## Exact-product-head evidence

- Coverage/full Java 21 workflow `32656085922`, job `97234994129`: PASS.
- Gradle command: `clean build jacocoAggregateReport runtimeJars`; BUILD SUCCESSFUL in 6m48s.
- Runtime-JAR inspection: 24 provider API source types checked; 0 leaks in Paper and Velocity.
- Paper runtime SHA-256: `8477186ffca9080d69c5f8acb3937f28041823c35adb506f43bda65d310aee88`.
- Velocity runtime SHA-256: `ed83e10c8afab31aa04ddfc6b15d7b2c611022606ef2995743c713393a962eb7`.
- Aggregate JaCoCo measurement: 49.18% line, 40.27% branch, 51.69% instruction. This records the repository measurement and does not relabel the final repository-wide coverage target as achieved.
- Validation artifact `9497561103`, digest `sha256:af9cbc0a9c53f424cbe641ec2c99ff62115c745243ec048431c79d57e63d003f`.
- Codacy coverage upload and final notification: PASS within workflow `32656085922`.
- Sentinel exact-head artifact run `32656085940`: PASS.
- CodeRabbit exact-product-head status: success.
- Live PR inline review threads before publication: 0.

## Provider contract check

Current `wsg138/PlayTimePlugin` exposes lifetime snapshots through `PlaytimeService.getLifetime(UUID)` and stores `activeMinutes` as `long`; D01's type matches that provider contract. D04 must fail closed / preserve the existing automatic main when provider active-playtime data is unavailable rather than substituting guessed zero values.

## Privacy and collision check

No production link list, Discord token, webhook, private message, network identity, or player evidence entered source control. The PR changes no `components/enthusia-site/` path and no competition-related path.

## Remaining work

1. Human/owner review PR #146.
2. Normal merge only after approval.
3. Verify merged-tree containment and clean the package branch.
4. Start dependent ES-D02 from fresh `main` in a new worker/package context; fresh-check Flyway numbering because current `main` already contains V18.
