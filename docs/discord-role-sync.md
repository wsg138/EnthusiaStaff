# Discord role synchronization

ES-D13 replaces DiscordSRV role synchronization with a one-way Enthusia/Minecraft → Discord projection. Discord roles remain presentation state only; they never grant EnthusiaStaff moderation authority.

## Authority and data flow

1. EnthusiaStaff resolves every current Minecraft UUID linked to a Discord identity from the canonical moderation-subject data.
2. StaffBot asks the existing authenticated Paper authority bridge for each linked UUID's current inherited LuckPerms groups at `/v1/role-eligibility`.
3. The configured group-to-role allowlist is applied to the union of those groups. The selected main Minecraft account does not affect eligibility.
4. StaffBot retrieves the Discord member on demand and compares only configured managed roles. Unmanaged and protected Discord roles are never removal candidates.
5. Desired/observed state, retry timing, and errors are persisted in the existing `discord_reconciliation_state` table. No D13-specific source of truth or Flyway migration is introduced.

A Discord disconnect disables reconciliation. Retry state is durable across StaffBot restarts, and Discord/LuckPerms/persistence failures converge through bounded retries rather than changing authority.

## Configuration

Role sync is disabled when no role-sync mapping is configured. When enabled, the D06/D16 read-only database configuration remains separate from D13's narrowly scoped writer credentials.

Environment variables:

| Variable | Purpose |
| --- | --- |
| `ENTHUSIA_STAFF_BOT_ROLE_SYNC_MAPPINGS` | Semicolon-separated `minecraft-group=discord-role-id` allowlist. Group names are normalized lowercase. |
| `ENTHUSIA_STAFF_BOT_ROLE_SYNC_PROTECTED_ROLE_IDS` | Optional comma-separated Discord role IDs that must never be managed by D13. |
| `ENTHUSIA_STAFF_BOT_ROLE_SYNC_MODE` | `shadow` or `enforce`; defaults to `shadow`. |
| `ENTHUSIA_STAFF_BOT_ROLE_SYNC_INTERVAL_SECONDS` | Reconciliation interval, 30–3600 seconds; defaults to 300. |
| `ENTHUSIA_STAFF_BOT_ROLE_SYNC_BATCH_SIZE` | Current-link scan batch, 1–100; defaults to 25. |
| `ENTHUSIA_STAFF_BOT_ROLE_SYNC_DB_USERNAME` | D13 reconciliation writer principal. Required when role sync is enabled. |
| `ENTHUSIA_STAFF_BOT_ROLE_SYNC_DB_PASSWORD` | D13 reconciliation writer credential. Required when role sync is enabled. |

The panel properties file uses the equivalent keys `role-sync.mappings`, `role-sync.protected-role-ids`, `role-sync.mode`, `role-sync.interval-seconds`, `role-sync.batch-size`, `role-sync.db-username`, and `role-sync.db-password`.

The D13 writer principal uses the same EnthusiaStaff MariaDB schema as the read runtime. It needs only the reads required for current Discord/Minecraft subject identities plus read/insert/update access to `discord_reconciliation_state`. It must not receive migration authority. Credentials stay in the runtime secret/config surface and are never committed.

## Modes and safety boundary

`SHADOW` is the migration/default mode. It reads the current Discord member and persists `SHADOW_MATCH` or `SHADOW_DRIFT` without adding or removing roles.

`ENFORCE` applies only the configured managed-role delta and preserves all other roles. ES-D13 explicitly rejects `ENFORCE` when StaffBot is configured for production. Production cutover/removal of legacy role sync belongs to the later authorized migration/cutover process.

Discord's own hierarchy is an enforcement precondition. Public (`@everyone`), integration-managed, missing, or non-interactable roles fail closed and are retried; they are not bypassed.

## Legacy DiscordSRV parity procedure

D13 does not invent or check in the live DiscordSRV role mapping. The authoritative legacy mapping must come from the authorized non-production DiscordSRV/runtime configuration used for the parity test.

For parity acceptance:

1. Keep legacy DiscordSRV role synchronization enabled in the non-production/staging environment.
2. Configure D13 with the same intended Minecraft-group → Discord-role mapping and any protected role IDs from that authorized staging configuration.
3. Run D13 in `SHADOW`; do not use `ENFORCE` for the comparison.
4. Allow the bounded cursor to cover every current linked Discord identity at least once after LuckPerms/Discord state is stable.
5. Require zero unexplained `SHADOW_DRIFT` records for managed roles. Any drift must be investigated as mapping, linked-account-union, legacy behavior, hierarchy, outage, or stale-state evidence rather than ignored.
6. Exercise representative multi-account, main-account-change, unlink, reconnect/restart, and transient-failure cases without changing production users or configuration.
7. Record only sanitized acceptance facts (exact source/artifact, environment identity, counts/outcome, and drift classification). Do not publish private player mappings, database rows, credentials, or raw Discord evidence.

A successful shadow comparison proves D13 matches the effective legacy managed-role state in that staging environment. It does **not** authorize production cutover. DiscordSRV role sync remains enabled until the separately authorized cutover package accepts the transition.

## Rollback and diagnostics

In `SHADOW`, rollback is simply disabling the role-sync configuration/restarting StaffBot; no Discord role mutation has occurred. In staging `ENFORCE`, disable D13 enforcement and restore the approved legacy staging role-sync path before investigating drift.

Operational diagnostics use the existing reconciliation state: desired managed roles, observed roles, state, bounded attempt count, next retry time, and normalized error code. Logs intentionally record failure classes/codes rather than credentials, player data, or role inventories.
