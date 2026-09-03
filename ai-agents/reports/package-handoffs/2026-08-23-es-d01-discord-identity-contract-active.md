# ES-D01 Discord domain and identity contract — active handoff

Status: `ACTIVE`.

## Start

- Owner explicitly authorized implementation on 2026-08-23.
- Repository: `wsg138/EnthusiaStaff`.
- Starting `main`: `a0337614a85fab6e9b29beff663396cea86cdce1`.
- Branch: `package/es-d01-discord-identity-contract`.
- Approved design reference: PR #144 / `docs/discord-moderation-platform.md` on `docs/discord-moderation-expansion-spec`.
- Existing ES-X03 remains `PARKED_BLOCKED`; PR #139 and Market PR #3 are not modified.

## Scope

D01 owns pure domain contracts and tests for moderation subjects, Discord IDs, Discord↔Minecraft link cardinality/history, main-account selection, enforcement scopes and inactive-case timing. D01 deliberately excludes schema/runtime/website/competition/Discord API behavior.

## First checkpoint

Prepared type-safe Discord snowflake IDs, subject identities, link history/cardinality validation, 25%-threshold active-playtime main selection with staff override, explicit guild/server/network scopes, 30-day case inactivity policy and focused JUnit tests.

## Systems not to disturb

- `components/enthusia-site/` and standalone website work.
- competition-related work.
- parked ES-X03 / EnthusiaMarket branches and PRs.
- production Discord, DiscordSRV link data, player data, MariaDB, LiteBans authority and cutover state.

## Next action

Create the first coherent commit, open the required draft PR, run exact-head hosted checks/review, and fix only valid D01-scope defects.
