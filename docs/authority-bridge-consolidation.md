# Authority bridge consolidation: default-off Paper runtime

Status: implementation candidate, **not** production cutover authorization. This change does not activate policy v2, punishment writes, or external bot permissions.

## What moves

The private `GET /v1/staff-rank` HTTP endpoint retains its existing HMAC request/response signing, nonce replay guard, legacy private-network bind, 3-second LuckPerms lookup timeout, bounded executor and allowlisted rank response. Its implementation is compiled into `EnthusiaStaff-Paper.jar`; the standalone bridge JAR remains buildable for rollback. No new command, Discord mutation, or moderation capability is introduced.

The transition collector captures player joins/quits and online observations, then performs database operations on its bounded worker. **Routine captures no longer call Bukkit `getOfflinePlayer`, `OfflinePlayer.getName`, or `getLastSeen`**. The collector looks up already-persisted UUID/name records through the player directory on the worker, with at most 128 unique offline candidates selected per scheduled pass, plus currently observed online players. Unknown offline players are skipped until an authoritative observation exists; no playerdata files are force-loaded. The collector remains read-only toward DiscordSRV and never writes to LiteBans.

Embedded collection borrows the **existing Paper MariaDB pool only after Paper finishes schema migration**. It neither opens a second pool nor runs a second Flyway pass and closes neither the borrowed datasource nor the main Staff runtime. The legacy `collector.properties` credentials must match Paper's configured database URL, username, and password; a mismatch refuses collection without logging the values. Pool size and timeout fields remain parsed for backward compatibility but are not applied to the borrowed pool.

## Switches and trust boundary

In `plugins/EnthusiaStaff/authority-bridge.properties`, optional switches are:

```properties
authority.enabled=false
collector.enabled=false
```

Both are false if this file is absent. Unknown keys and values other than literal `true` or `false` fail closed. `authority.enabled=true` additionally requires `plugins/EnthusiaStaff/authority.properties` with the **same** existing secret and port keys as the standalone plugin. The unchanged legacy endpoint binds to `0.0.0.0` on the configured port (default 8771), because approved private-network container peers must reach it. A signed-request check plus the private/loopback peer filter does **not** replace an explicit network firewall. Keep this port unreachable from the public internet; restrict it to authorized internal peers and do not publish it through a proxy or port-forward. `collector.enabled=true` requires `plugins/EnthusiaStaff/collector.properties` with the existing legacy field names, and its `collector.server-id` must match the Paper server ID. Per-node switches are deliberate: do not enable on HUB/other backends by copying SMP files without review.

If the standalone plugin **is installed**, main Paper refuses both embedded services even if the standalone plugin is disabled. Updated standalone builds also refuse to start when a main Staff configuration requests an embedded service. When either gate activates, the application reports a sanitized warning and the conflicting transition feature remains unavailable. Do not rely on just changing a filename while Paper is running.

## Automatic one-time legacy configuration migration

On every Paper startup (before attempting the embedded endpoint), Staff checks the sibling legacy plugin data directory `plugins/EnthusiaStaffAuthorityBridge/` for exactly `authority.properties` and `collector.properties`. When a source exists but the corresponding file under `plugins/EnthusiaStaff/` does not, Staff imports its bytes into a newly restricted file (owner-only access on supported POSIX/Windows ACL filesystems). This is **automatic**; no separate copy command or manual secret transfer is needed.

Existing Staff configuration files are never replaced. If the old and new copies differ, the importer aborts with a sanitized warning, rolls back new files created in that attempt, leaves the original and existing Staff files intact, and does not start the embedded service. A repeated startup with matching files is a no-op. Symlinked inputs/directories, oversized files, unsafe destination filesystems, and unknown file types are rejected. Only the two allowlisted files are imported; the old `config.yml`, secrets elsewhere, and unrelated plugin files are not touched. **The old folder and its files remain for rollback.** An unavailable legacy folder results in no action.

Migration deliberately **does not** set `authority.enabled` or `collector.enabled`. They stay disabled until an explicit cutover on the chosen server, and the standalone-JAR conflict guard still applies. This separates non-destructive file preparation from runtime activation; keeping both plugin JARs installed is safe only while the embedded features stay disabled.

## Offline cutover (owner decision required)

1. On an isolated **staging Paper server**, capture the exact currently installed standalone and Staff JAR hashes, runtime/plugin versions, port, host identity, and a sanitized migration baseline. Back up `plugins/EnthusiaStaffAuthorityBridge/authority.properties`, `collector.properties`, and database state **securely**, without printing/committing/attaching their secrets. Verify the credentials privately and validate their database identity against primary Staff's MariaDB configuration.
2. Build and independently check the exact proposed `EnthusiaStaff-Paper.jar` commit. With the new JAR installed in staging and switches **absent/off**, verify ordinary Staff startup and rollback separately before proceeding.
3. Verify that the startup importer already placed `authority.properties` and, when present, `collector.properties` in `plugins/EnthusiaStaff/` without logging credentials, copying unrelated files, or changing the originals. Check that any previously existing Staff file was preserved and conflicts are resolved **privately** before proceeding. Stop the staging Paper server normally. Preserve the original standalone JAR and its data directory in a secure off-server backup. Remove the old standalone JAR from the **staging** `plugins/` loading directory; preserve its config/data files. No manual copying or pasting of credentials is needed.
4. Set `authority.enabled=true`; set `collector.enabled=true` **only** on the specific server that owns transition collection. Verify signed valid/invalid/replay/stale requests, rank parity, documented legacy bind (`0.0.0.0`) inside the firewalled private network, correct fail-closed port collision, collector DB URL/identity, and bounded collector logs. Check that only **one** rank listener, one collector, and one Flyway/bootstrap path are active. Restart staging and repeat the checks, including reboot/recovery.
5. Run a real Paper/Folia and Spark test with representative online joins/quits, a cold offline-link directory, a DiscordSRV outage and a transient DB outage. Measure query counts, per-tick MSPT and high-percentile slow ticks against a comparable baseline. The synthetic 128-candidate cap alone does **not** prove any TPS improvement. Review DB provenance, imported/unchanged/conflicted counts, permissions, and no duplicate link writes.
6. Obtain separate owner authorization **before** any live server cutover, existing standalone plugin retirement, server stop/restart, network change, or deployment. No such action is part of this PR.

## Rollback

Stop the staging server normally. Switch off both embedded flags; replace the Paper JAR with the previously accepted version; restore the preserved standalone JAR and its original private files; then restart with only the old authority listener enabled. Do not restore an older schema or overwrite the database: both runtimes use the current migrated schema and link import uses idempotent provenance. Check rank signatures and link import counts after rollback; investigate any unexpected changes before allowing moderation decisions. Keep secrets and backups off PRs/logs.

## Validation matrix

- **Required before merge:** standalone and Paper unit/security tests, Paper shaded JAR resource check, transition bridge runtime JAR check, Linux MariaDB/Testcontainers integration tests, exact-head CI/Codacy zero new valid issues, manual review.
- **Required before staging approval:** actual private signed rank API valid/invalid/replay/timeout and port conflict; legacy-plus-main conflict; collector empty/partial/cold/restart/recovery cases; migration and data-source parity; zero duplicate imports; no publicly reachable endpoint.
- **Required before live approval:** independent real Spark acceptance comparing similar load/intervals, explicit owner change window and rollback test. The Spark slow-tick plugin percentages overlap; they are **not CPU shares** or additive.

The public deployment and policy v2 remain independently controlled. `EnthusiaStaffAuthorityBridge` is retained as a compatibility build until a separate retirement decision.
