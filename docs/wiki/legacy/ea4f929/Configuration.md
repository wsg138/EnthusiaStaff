# Configuration

EnthusiaStaff currently uses:

- Paper `config.yml`
- Paper `reason-policies.yml`
- Velocity `velocity-config.properties`

The required modular configuration tree is not yet complete. Treat the shipped
files as a pre-release configuration surface and review upgrade notes before
carrying settings between builds.

## Secret handling

Configuration files contain environment-variable names, not secret values.
Provide values through the service manager or an approved secret store.

Common secret environments include:

| Purpose | Environment name |
| --- | --- |
| MariaDB URL | `ES_DATABASE_URL` |
| MariaDB application user | `ES_DATABASE_USER` |
| MariaDB application password | `ES_DATABASE_PASSWORD` |
| Velocity channel key-store password | `ES_CHANNEL_TLS_KEYSTORE_PASSWORD` |
| Paper channel trust-store password | `ES_CHANNEL_TLS_TRUSTSTORE_PASSWORD` |
| Per-backend channel HMAC | `ES_CHANNEL_<SERVER>_SECRET` |
| Velocity channel HMAC | `ES_CHANNEL_VELOCITY_SECRET` |
| Network identity HMAC key | `ES_IDENTITY_HMAC_KEY_V1` |
| Network identity AES key | `ES_IDENTITY_ENCRYPTION_KEY_V1` |

Website, Discord, and LiteBans settings also reference environment variables in
the shipped Velocity properties. Do not paste their values into files, startup
commands, logs, screenshots, or support messages.

## Paper settings

Key Paper sections are:

| Section | Purpose |
| --- | --- |
| `operational-mode` | Starts in `BOOTSTRAP`; `ACTIVE` requires a successful cutover record |
| `storage` | Environment-backed MariaDB settings and bounded pool limits |
| `workers` | Bounded worker thread and queue limits |
| `network.server-id` | Stable backend identity such as `HUB` or `SMP` |
| `inventory.scope-id` | Authoritative inventory profile scope for the backend |
| `economy.removal-order` | Unique ordered Currency sources |
| `rosechat` | Staff, global, and private channel identifiers |
| `channel` | Persistent Velocity endpoint, HMAC environments, and TLS trust store |
| `automod` | Disabled by default; exact variants require private review |
| `visibility.matrix` | Rank-aware staff visibility policy |

Every backend needs a stable server ID. Do not reuse an inventory scope for
servers that do not share the same authoritative inventory.

## Velocity settings

Key Velocity groups are:

| Prefix | Purpose |
| --- | --- |
| `storage.*` | Environment-backed MariaDB settings |
| `enforcement.*` | Fail-closed active-authority behavior |
| `website-api.*` | Restricted loopback bridge, authentication, request bounds, and workers |
| `channel.*` | TLS listener, proxy identity, and per-backend HMAC allowlist |
| `network-identity.*` | Versioned HMAC equality and AES recovery keys |
| `discord.*` | Disabled-by-default durable webhook delivery and retry bounds |
| `litebans.*` | Source database, table prefix, batch size, and shadow schedule |

The website bridge should remain loopback-bound behind an authenticated
same-host relay or tunnel. The persistent channel and network identity
features remain disabled until their key material and health checks pass.

## Operational modes

- `BOOTSTRAP`: validate storage, schema, and dependencies.
- `SHADOW_MIGRATION`: compare imported state while LiteBans remains
  authoritative.
- `ACTIVE`: permit the new authority only after the cutover gate.
- `DEGRADED`: disable affected unsafe capabilities while retaining safe
  diagnostics.
- `MAINTENANCE`: suppress ordinary authority while controlled maintenance runs.
- `READ_ONLY_FAILURE`: block writes when authoritative safety cannot be proved.

Do not change mode merely to bypass a failed dependency or migration check.

## Key rotation

HMAC and AES network identity keys are versioned and distinct. Recovery fails
closed when a stored encryption-key version does not match the configured key.
Plan key rotation as a data migration with retained rollback material; changing
only the version number or secret can make older recoverable values
unavailable.

TLS certificate rotation must update Paper trust stores before replacing the
Velocity identity. See the
[TLS rotation procedure](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/channel-tls.md#rotation-and-rollback).

## Validation

After any configuration change:

1. validate syntax and cross-references before activating the snapshot;
2. confirm restart-required settings are handled by a controlled restart;
3. review runtime health for each independently optional dependency;
4. verify Paper and Velocity agree on shared configuration where required;
5. keep destructive features disabled if any authority or recovery prerequisite
   is uncertain.
