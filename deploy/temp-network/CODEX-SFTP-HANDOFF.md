# Temp Network Staging — Codex SFTP Handoff

This handoff is for the temporary SMP maintenance window. Codex is the remote executor/validator only. Do not redesign EnthusiaStaff, change moderation authority, merge unrelated PRs, or remove legacy moderation plugins.

## Exact EnthusiaStaff artifact

The Paper artifact remains the validated staging release from source SHA:

`6a2db9f9cfb2516cff57b323f1956ccc98cff5c8`

Expected deployed hashes:

- `EnthusiaStaff-Paper.jar` — `eb42779b06fd2f40084e7dc7b65bd6525df16a5ba7a14d0da75fefb92e0a0057`
- `EnthusiaStaff-Velocity.jar` — `07a532b8dfa1ab206dc8b3c36de2618bdefd793fdbce0440b75321d8e5b815ad`

The Velocity artifact includes the focused private database file fallback requested for Bloom hosting. Its source is the deployment branch after that change. The earlier release Velocity artifact hash was `6dc73e57ea12be142b2f0d8b7214a1b9810b4d2545aab7da586196a8cf8c8965`; keep it only as a rollback reference.

Run `deploy/temp-network/prepare-staging-bundle.ps1` to verify the original release and Paper artifact. It still downloads the earlier Velocity release for rollback. Build the updated Velocity artifact with `gradlew.bat :velocity:test :velocity:shadowJar`, then verify the deployed Velocity hash above before uploading. Refuse upload if a hash differs.

## URGENT: proxy-first step

The owner has a scheduled network restart and may not be able to restart Velocity again for a long time.

Do this first, before any Paper/LiteBans work:

1. Connect to the authorized Velocity SFTP target.
2. Download/back up any existing EnthusiaStaff Velocity JAR locally.
3. Upload the verified `EnthusiaStaff-Velocity.jar` to the Velocity `plugins/` directory.
4. Preserve all existing proxy files/configs. Do not remove or rename unrelated plugins.
5. Do not trigger an extra proxy restart. Let the owner's already scheduled network restart load the JAR.
6. After restart, download `logs/latest.log` and verify:
   - Velocity loads `EnthusiaStaff`;
   - there is no jar/class/linkage error;
   - record any database/configuration blocker exactly;
   - do not print secrets from configuration or environment.

Velocity reads complete `ES_DATABASE_*` and `ES_LITEBANS_DATABASE_*` groups when they are available. On Bloom hosting, when a group is unavailable, place a private `database.properties` file in the Velocity plugin data directory (`plugins/enthusiastaff/`) with `db.jdbc-url`, `db.username`, `db.password`, `litebans.jdbc-url`, `litebans.username`, and `litebans.password`. Source the Staff values from the owner's existing Staff database file and the LiteBans values from the authoritative normal SMP LiteBans config. Keep this file out of Git, restrict it to the server user, and never print its contents. A partial environment group or incomplete file fails closed. The current proxy JAR and private file are staged; a later proxy restart is needed to validate startup. Do not trigger one during this handoff.

## LiteBans on Temp SMP

LiteBans on Temp SMP must use the same authoritative network LiteBans database as the normal SMP.

Do not invent a new LiteBans DB configuration and do not expose its credentials.

1. From the authorized main SMP SFTP connection, download locally:
   - `/plugins/LiteBans.jar`
   - `/plugins/LiteBans/config.yml`
2. Record the JAR SHA-256 locally.
3. On Temp SMP, back up any existing LiteBans JAR/config locally before replacement.
4. Upload the exact main-SMP LiteBans JAR to Temp SMP `/plugins/LiteBans.jar`.
5. Upload the exact main-SMP `/plugins/LiteBans/config.yml` to Temp SMP `/plugins/LiteBans/config.yml`.
6. Never print the config contents in chat, logs, PRs, or handoffs. Copying that existing config is what preserves the network DB connection.
7. After Temp SMP restart, validate from `logs/latest.log` that LiteBans enabled and connected without SQL/schema/auth errors.

LiteBans remains authoritative. Do not disable it, remove it, or perform EnthusiaStaff cutover.

## EnthusiaStaff Paper deployment

Install the verified `EnthusiaStaff-Paper.jar` on every real Paper backend participating in this maintenance test. Current network inventory includes the normal SMP, HUB, test/test2 backends, and a separate entry named `Temp server`; do not install onto Sentinel/build containers. Confirm each target before writing.

For each Paper target:

1. Back up any existing EnthusiaStaff Paper JAR locally.
2. Upload the verified JAR to `/plugins/EnthusiaStaff-Paper.jar`.
3. Preserve all other plugin jars and data.
4. If an owner-provided EnthusiaStaff DB credential file exists for that backend, upload it as:
   `/plugins/EnthusiaStaff/database.properties`
5. The file format is exactly:
   - `db.jdbc-url=jdbc:mariadb://HOST:3306/DATABASE`
   - `db.username=...`
   - `db.password=...`
6. Do not reuse LiteBans credentials for EnthusiaStaff unless the owner explicitly confirms that account/database is intended for EnthusiaStaff writes.
7. Do not put DB credentials in Git, PR comments, terminal transcripts, or the final report.

Paper starts in `BOOTSTRAP`; EnthusiaStaff must not become the active moderation authority. LiteBans stays authoritative during this maintenance period.

## Network safety rules

- Do not remove LiteBans from any backend.
- Do not remove Staff++/Punishments/legacy moderation jars during this task.
- Do not activate EnthusiaStaff cutover or `ACTIVE` mode.
- Do not edit production punishment/player records manually.
- Do not run ad-hoc SQL migrations. Let the reviewed EnthusiaStaff Flyway path handle its own schema.
- Do not deploy open/draft Staff PRs. Use only the exact release above unless the owner explicitly replaces this handoff.
- Do not touch the Staff website worker, Market PRs, Sentinel migration, or Discord bot deployment.
- SFTP secrets stay local to the authorized executor.

## Validation after restarts

For Velocity and each Paper backend, collect only sanitized evidence from `logs/latest.log`:

- plugin version/load success;
- source/JAR SHA being tested;
- MariaDB/Flyway success or sanitized failure class;
- operational mode (`BOOTSTRAP`/`SHADOW_MIGRATION`/degraded as applicable);
- LiteBans successful startup on Temp SMP;
- no duplicate plugin jar loaded;
- no `NoClassDefFoundError`, `ClassNotFoundException`, linkage error, migration checksum error, or repeated startup exception.

On Temp SMP, also test that a LiteBans punishment created through the normal command path is visible through the shared network source exactly as expected. Do not create destructive test punishments against real players; use an approved test account.

## Stop conditions

Stop and report instead of improvising if any of these occur:

- SFTP authentication/host mismatch;
- destination server identity is ambiguous;
- Velocity required DB environment is absent;
- EnthusiaStaff DB credentials are unavailable;
- Flyway checksum/future-schema failure;
- LiteBans source config/JAR cannot be read from main SMP;
- expected artifact hash differs;
- existing server file would need destructive deletion to proceed.

## Final report

Report a compact matrix:

`target | jar uploaded | restart observed | plugin enabled | DB connected | mode | blocker`

Include SHA-256 values. Never include passwords, JDBC credentials, raw player data, private evidence, or network addresses that are not already public.
