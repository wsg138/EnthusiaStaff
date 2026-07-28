# EnthusiaStaff upgrade manifest

This is a review and staging manifest. Nothing listed here has been merged, deployed, released, or applied to production data.

## Review branches

| Repository | Remote observed | Branch | Local checkpoint | Required update |
| --- | --- | --- | --- | --- |
| `wsg138/EnthusiaStaff` | `wsg138/EnthusiaStaff` | `agent/complete-staff-platform` | `b226242` | Paper and Velocity moderation runtime; draft PR #1 updated |
| `wsg138/enthusia-site` | `wsg138/enthusia-site` | `agent/punishment-platform` | `ed778df` | Private punishment/appeal pages; push blocked by HTTP 403 and preserved in a verified bundle |
| `wsg138/EnthusiaCurrency` | `wsg138/EnthusiaCurrency` | `agent/moderation-api` | `cffce5c` | Idempotent moderation economy API |
| `wsg138/EnthusiaCommend` | `wsg138/EnthusiaCommend` | `agent/reputation-blacklist-api` | `4cf9b9e` | Persistent reputation blacklist API |
| `wsg138/EnthusiaAutoClicker` | `wsg138/EnthusiaAutoClicker` | `agent/client-evidence-api` | `b494f3b` | Versioned client-evidence API |
| `Enthusia-RoseChat` (local only) | none; no verified `wsg138` repository exists | `agent/staff-bridge-api` | `a276749` | Moderation/chat evidence bridge preserved in a verified bundle |
| `wsg138/EnthusiaMarket` | `wsg138/EnthusiaMarket` | `agent/moderation-api` | `2438f48` | Market moderation API reconciled with current main; complete branch preserved in `enthusia-market-agent-moderation-api.bundle` |
| `wsg138/LumaGuilds` | `wsg138/LumaGuilds` | `agent/staff-market-api` | `fda863a` | Public system bank contract required by Market; complete branch preserved in `lumaguilds-agent-staff-market-api.bundle` |

Staff review is open as draft PR [wsg138/EnthusiaStaff#1](https://github.com/wsg138/EnthusiaStaff/pull/1). No `wsg138` RoseChat repository exists, so that branch remains local-only. Pushes to the site, Currency, Commend, and AutoClicker repositories currently fail with HTTP 403. Market and LumaGuilds publication is unavailable in the current review environment. All affected branch histories are preserved in verified bundles under the workspace `review-bundles` directory.

## Required installation order

1. Back up and verify restore for MariaDB, LiteBans, server configs, and plugin data.
2. Merge and build required compatibility APIs; install matching provider jars while old moderation authority remains active.
3. Apply the private website change behind its authenticated, unpublished environment.
4. Install the same EnthusiaStaff Paper jar on HUB and SMP with enforcement inactive.
5. Install the EnthusiaStaff Velocity jar with channel, website bridge, migration, and protected identity secrets configured.
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

- Publish bundle-backed review branches and open draft PRs when GitHub authentication, target ownership, and outbound approval are available.
- Run MariaDB Testcontainers tests on a Docker-capable host.
- Perform Paper/Velocity staging, multi-backend channel, Java/Bedrock, visual GUI, voice-chat, and failure-injection checks.
- Record final jar hashes, PR URLs, review SHAs, installation inventory, and acceptance evidence here before release approval.
