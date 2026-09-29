# Live recovery now

Current maintenance-window state from the authorized executor:

- HUB: EnthusiaStaff storage verified and `SHADOW_MIGRATION` reached.
- Main SMP: EnthusiaStaff storage verified and `SHADOW_MIGRATION` reached before the host installation/restart cycle. Revalidate the newest boot before reopening traffic.
- Test 1: EnthusiaStaff storage verified and `SHADOW_MIGRATION` reached.
- Temp SMP / Test 2: Staff and LiteBans both fail to reach MariaDB with a network-reachability failure. Keep this backend offline until the database route is corrected.
- Velocity: the validated EnthusiaStaff JAR is loaded, but storage is degraded because the required Staff/LiteBans database environment is unavailable. Do not restart Velocity again during this maintenance window unless the owner explicitly authorizes it.

## Immediate order

1. Revalidate Main SMP's newest `latest.log` after the host installation/start cycle. Require `Storage verified` and `SHADOW_MIGRATION`; require no new `Freeze storage is unavailable` line after the successful storage initialization.
2. Keep HUB and Test 1 running if their latest logs remain storage-healthy.
3. Keep Temp SMP offline while either its Staff or LiteBans database connection reports network unreachable. Do not alter database credentials to work around a routing failure.
4. Diagnose Temp SMP database reachability without exposing credentials: compare the configured database hostname/port with Main SMP, resolve the hostname on Temp SMP, and test TCP reachability to the configured MariaDB port. If Main SMP reaches the same endpoint but Temp SMP cannot, treat this as hosting/network routing or DB allowlist work, not a plugin configuration defect.
5. Do not activate `ACTIVE`, do not run cutover, and do not remove LiteBans or other legacy moderation jars. LiteBans remains authoritative.
6. Do not perform ad-hoc Staff schema changes. The reviewed Flyway path owns the Staff schema after a valid Staff DB connection.
7. Once Temp SMP can reach MariaDB, start it and require both LiteBans and EnthusiaStaff to connect. EnthusiaStaff should move from `BOOTSTRAP` to `SHADOW_MIGRATION`; LiteBans remains the actual punishment authority.

## Freeze incident

The all-player restriction occurred because `FreezeManager` deliberately fail-closes while durable freeze storage cannot be verified. Do not use `/unfreeze` on everyone as a workaround. Restoring the Staff database connection is the correct recovery; after storage verification, joining players are checked against the authoritative freeze store normally.

## Velocity follow-up

Velocity currently requires these six environment-backed values before a fresh process start:

- `ES_DATABASE_URL`
- `ES_DATABASE_USER`
- `ES_DATABASE_PASSWORD`
- `ES_LITEBANS_DATABASE_URL`
- `ES_LITEBANS_DATABASE_USER`
- `ES_LITEBANS_DATABASE_PASSWORD`

Do not guess or print them. The currently running degraded proxy cannot acquire newly added process environment values without another process restart. Leave the validated JAR installed and schedule that configuration/restart separately if the proxy cannot be restarted again now.

## Reopen traffic gate

Do not reopen a backend until its newest boot shows the expected plugin load, database connection, and no repeated startup/freeze-storage failures. For this maintenance period, `SHADOW_MIGRATION` is the correct Staff mode; `ACTIVE` is not.
