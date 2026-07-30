# Installation

> **Pre-release only:** the current artifacts are for development and isolated
> staging. Building or copying the jars does not authorize production cutover.
> LiteBans must remain authoritative until migration, the 168-hour shadow
> window, acceptance testing, and the documented cutover gate are complete.

## Requirements

- Java 21 on every Paper backend and the Velocity proxy
- Paper 1.21.11-compatible backend servers
- Velocity 3.4-compatible proxy
- MariaDB with separate application and migration credentials
- Existing LiteBans MariaDB access during migration
- Environment-backed secrets for database, channel, website, identity, and
  optional integration credentials

See [[Build and Testing]] to produce and validate the two runtime jars.

## Staging layout

Install the Paper jar unchanged on every participating backend:

```text
plugins/EnthusiaStaff-Paper-<version>.jar
```

Install the Velocity jar on the proxy:

```text
plugins/EnthusiaStaff-Velocity-<version>.jar
```

Do not install module jars, test artifacts, source jars, or private provider
dependencies as server plugins.

## Initial staging sequence

1. Back up the staging MariaDB database and preserve the LiteBans source.
2. Create least-privilege application credentials and separate migration
   credentials.
3. Configure each Paper backend with a stable `network.server-id` and its
   intended `inventory.scope-id`.
4. Configure Velocity with its stable `server.id`.
5. Supply secret values through the service manager or secret store, never in
   committed configuration.
6. Keep network identity, persistent channel, Discord, website bridge, and
   automod features disabled until their dependencies are configured and
   verified.
7. Start in `BOOTSTRAP`; do not force `ACTIVE`.
8. Start Velocity, then each Paper backend, and review schema, dependency,
   channel, and runtime-health output.
9. Run the documented migration dry run and shadow process before considering
   cutover.

## Persistent channel

The Paper–Velocity channel requires TLS 1.3 and per-backend HMAC secrets. There
is no cleartext fallback. Velocity owns the private-key store; Paper receives
only a trust store. Certificate Subject Alternative Names must match the host
configured on Paper.

Follow the
[TLS runbook](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/channel-tls.md)
before enabling the channel.

## Database changes

Flyway owns schema migrations. Do not edit Flyway history or apply ad-hoc
production schema changes. A checksum mismatch or unsupported future schema
must fail closed and be investigated deliberately.

The build itself does not migrate production data, change operational mode,
remove LiteBans, deploy jars, or perform cutover.

## Next steps

- [[Configuration]]
- [LiteBans migration specification](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/litebans-migration.md)
- [Shadow-mode runbook](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/shadow-mode.md)
- [Cutover gate](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/cutover.md)
- [Rollback runbook](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/rollback.md)
