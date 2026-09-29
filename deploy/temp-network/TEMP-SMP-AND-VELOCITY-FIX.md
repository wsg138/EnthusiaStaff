# Immediate correction: Temp SMP + Velocity

Current observed state:

- Velocity has the EnthusiaStaff Velocity jar loaded, but `/estaff status` reports `DEGRADED` because storage startup is unavailable after bounded retries.
- Temp SMP does not have the EnthusiaStaff Paper jar installed.

## Temp SMP

Upload the already-verified Paper artifact:

- local/source artifact: `EnthusiaStaff-Paper.jar`
- expected SHA-256: `eb42779b06fd2f40084e7dc7b65bd6525df16a5ba7a14d0da75fefb92e0a0057`
- remote path: `/plugins/EnthusiaStaff-Paper.jar`

Also ensure the owner-provided Staff DB credential file is present at:

- `/plugins/EnthusiaStaff/database.properties`

Do not print its contents.

Temp SMP must remain unavailable to normal testing until its latest startup log proves:

1. LiteBans enabled and connected to the authoritative shared LiteBans DB;
2. EnthusiaStaff storage verified;
3. EnthusiaStaff mode is `SHADOW_MIGRATION`;
4. no fresh `Freeze storage is unavailable` errors after storage verification.

If MariaDB still reports `Network is unreachable`, do not alter credentials as a workaround. Diagnose host resolution/TCP reachability or DB-side/Bloom routing/allowlist.

## Velocity

The currently deployed Velocity build reads these six database values from process environment:

- `ES_DATABASE_URL`
- `ES_DATABASE_USER`
- `ES_DATABASE_PASSWORD`
- `ES_LITEBANS_DATABASE_URL`
- `ES_LITEBANS_DATABASE_USER`
- `ES_LITEBANS_DATABASE_PASSWORD`

The first group targets the EnthusiaStaff DB. The second group targets the existing authoritative LiteBans DB.

Do not guess or expose values. The Staff values must match the owner-provided Staff DB credentials; the LiteBans values must match the authoritative LiteBans connection already used by the network.

Because these are process environment values, changing them normally requires the proxy process to be restarted before `System.getenv()` can see them. Do not claim Velocity is healthy until a startup after those variables are present shows storage verified and `/estaff status` no longer reports `DEGRADED`.

## Testing boundary

Paper-only feature testing may begin on a backend that is already storage-healthy in `SHADOW_MIGRATION`, but do not treat proxy login/server-switch punishment behavior or full network acceptance as tested while Velocity remains degraded.

Keep LiteBans authoritative. Do not run final migration or cutover activation.
