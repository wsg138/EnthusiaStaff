# Live temp-network recovery status

Validated from fresh SFTP `logs/latest.log` files after the owner-triggered network restart on 2026-09-29 at about 05:16 UTC:

- Velocity proxy: EnthusiaStaff reached `SHADOW_MIGRATION`; LiteBans connected. The private database file fallback is loaded by the updated proxy JAR. No Staff database retry failure was observed.
- Actual Temp server (the separate `Temp server` SFTP inventory entry): EnthusiaStaff storage verified and reached `SHADOW_MIGRATION`; LiteBans enabled and connected to the existing authoritative database.
- Main SMP, HUB, and Test server 1: EnthusiaStaff storage verified and reached `SHADOW_MIGRATION`; LiteBans connected.
- No `Freeze storage is unavailable` line was found in these fresh startup logs. No Staff `ACTIVE` mode was observed.
- Main SMP and Test server 1 have class-loading errors from other plugins. This deployment did not change those plugins.

The older `Test server 2` connection was not the separate Temp server. The current inventory names the correct target `Temp server`.

## Remaining validation

Use an owner-approved test account to verify a normal LiteBans punishment is visible through the shared network source. Do not punish a real player for testing. Keep LiteBans authoritative and do not activate EnthusiaStaff cutover.

The current proxy startup confirms the EnthusiaStaff database connection; the Staff shadow migration path's separate LiteBans-source credentials have not been exercised through a migration command. Do not run a final migration or cutover as a connectivity test.
