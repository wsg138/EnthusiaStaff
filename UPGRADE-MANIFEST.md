# EnthusiaStaff upgrade manifest

This is a review and staging manifest. The EnthusiaStaff recovery baseline has
been merged to `main`; nothing has been released, deployed, or applied to
production data.

## Review branches

| Repository | Remote observed | Branch | Local checkpoint | Required update |
| --- | --- | --- | --- | --- |
| `wsg138/EnthusiaStaff` | `wsg138/EnthusiaStaff` | `section/plugin` | `a9636ab` | Recovery baseline merged through PR #1; plugin security and TLS review in PR #2 |
| `wsg138/enthusia-site` | `wsg138/enthusia-site` | `agent/punishment-platform` | `ed778df` | Private punishment/appeal pages; repository publication access must be re-verified |
| `wsg138/EnthusiaCurrency` | `wsg138/EnthusiaCurrency` | `agent/moderation-api` | `cffce5c` | Idempotent moderation economy API |
| `wsg138/EnthusiaCommend` | `wsg138/EnthusiaCommend` | `agent/reputation-blacklist-api` | `4cf9b9e` | Persistent reputation blacklist API |
| `wsg138/EnthusiaAutoClicker` | `wsg138/EnthusiaAutoClicker` | `agent/client-evidence-api` | `b494f3b` | Versioned client-evidence API |
| `Enthusia-RoseChat` (unpublished) | none; no verified `wsg138` repository exists | `agent/staff-bridge-api` | `a276749` | Moderation/chat evidence bridge; a target repository is required before publication |
| `wsg138/EnthusiaMarket` | `wsg138/EnthusiaMarket` | `agent/moderation-api` | `2438f48` | Market moderation API reconciled with the recorded provider baseline; repository access must be re-verified |
| `wsg138/LumaGuilds` | `wsg138/LumaGuilds` | `agent/staff-market-api` | `fda863a` | Public system bank contract required by Market; repository access must be re-verified |

The recovery baseline was merged through
[wsg138/EnthusiaStaff#1](https://github.com/wsg138/EnthusiaStaff/pull/1).
Plugin security work is under review in
[wsg138/EnthusiaStaff#2](https://github.com/wsg138/EnthusiaStaff/pull/2).
No verified `wsg138` RoseChat repository exists, so that provider work has no
publishable target. The last recorded publication attempts for the site,
Currency, Commend, AutoClicker, Market, and LumaGuilds work were not authorized;
re-verify repository access before planning those releases.

## Required installation order

1. Back up and verify restore for MariaDB, LiteBans, server configs, and plugin data.
2. Merge and build required compatibility APIs; install matching provider jars while old moderation authority remains active.
3. Apply the private website change behind its authenticated, unpublished environment.
4. Install the same EnthusiaStaff Paper jar on HUB and SMP with enforcement inactive.
5. Install the EnthusiaStaff Velocity jar with the TLS channel key/trust
   stores, website bridge, migration, and protected identity secrets configured.
6. Complete dry-run and the full 168-hour shadow procedure.
7. Perform the documented maintenance/final/cutover sequence.
8. Complete live acceptance checks before removing any legacy jar.

## Server jars to update

- HUB and SMP: `EnthusiaStaff-Paper-<version>.jar`
- Velocity: `EnthusiaStaff-Velocity-<version>.jar`
- Servers using the touched integrations: the reviewed Currency, Commend, AutoClicker, RoseChat, and Market builds corresponding to the commits above

No compiled jar belongs in Git. Exact release filenames and checksums remain pending a reviewed release build.

## Old jars to remove only after successful cutover

SMP:

- `LiteBans.jar`
- `staffplusplus-core`
- `staffplusplus-discord`
- `Punishments.jar`
- `TigerReportsSupports`

HUB and Velocity removal must be derived from the actual installed-plugin inventory during staging. Do not infer or delete a jar that was not positively identified.

## Rollback order

1. Stop traffic and run the audited EnthusiaStaff emergency freeze.
2. Preserve current databases, logs, configs, and durable queues.
3. Restore compatible integration providers.
4. Restore LiteBans and other legacy jars in their prior server locations.
5. Reconcile every post-cutover sanction before selecting the legacy authority.
6. Verify exactly one enforcement authority, then reopen staged traffic.

See [rollback](docs/rollback.md) for the fail-closed procedure.

## Remaining manual work

- Publish remaining provider review branches only after target ownership and repository access are verified.
- Repeat the clean Java 21 build and all six MariaDB Testcontainers tests for the final release commit.
- Perform Paper/Velocity staging, multi-backend channel, Java/Bedrock, visual GUI, voice-chat, and failure-injection checks.
- Record final jar hashes, PR URLs, review SHAs, installation inventory, and acceptance evidence here before release approval.
