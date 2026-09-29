# Protected identity + LiteBans migration recovery

Use this only for the current staging/shadow deployment on branch `deploy/temp-network-sftp-staging`.

## Current verified state

- EnthusiaStaff and LiteBans databases are reachable.
- LiteBans schema inspection/dry-run reports `schema-blockers=0`.
- Dry-run reports 190 source sanctions and 8 rejected legacy rows.
- Shadow/import currently fails because protected network identity support is disabled.
- LiteBans remains authoritative; do not activate cutover.

## Velocity protected-identity configuration

In the active Velocity `plugins/EnthusiaStaff/config.properties`, set:

```properties
network-identity.enabled=true
network-identity.hmac-key-version=1
network-identity.hmac-secret-environment=ES_IDENTITY_HMAC_KEY_V1
network-identity.encryption-key-version=1
network-identity.encryption-secret-environment=ES_IDENTITY_ENCRYPTION_KEY_V1
```

Provision BOTH referenced secrets in the Velocity process secret/environment store:

- `ES_IDENTITY_HMAC_KEY_V1`
- `ES_IDENTITY_ENCRYPTION_KEY_V1`

Requirements:

- generate the two values independently using a cryptographically secure RNG;
- values are standard Base64;
- each decodes to exactly 32 random bytes (AES requires exactly 32; HMAC accepts at least 32 and should use 32 here);
- never commit, echo, screenshot, or include either value in a handoff/report;
- do not reuse database passwords, Discord secrets, channel secrets, or the same value for both keys.

This configuration is startup-owned. Restart Velocity only with owner authorization; `/estaff reload` must not be treated as proof that new process secret variables were loaded.

## After restart

Require `/estaff status` to show `SHADOW_MIGRATION` and protected network identity support no longer disabled. Then run, in order:

```text
/estaff migration inspect
/estaff migration dry-run
```

Do not run `final` or `cutover activate`.

After protected identity is enabled and the dry-run is understood, run one shadow pass:

```text
/estaff migration shadow
```

Record the durable run ID and sanitized summary only.

## Eight rejected legacy rows

Current known rejects:

- `litebans_bans#78` — `INVALID_SOURCE_ROW`
- `litebans_bans#101` — `INVALID_SOURCE_ROW`
- `litebans_history#1` — `INVALID_HISTORY_ROW`
- `litebans_history#1183` — `INVALID_HISTORY_ROW`
- `litebans_history#1354` — `INVALID_HISTORY_ROW`
- `litebans_history#3397` — `INVALID_HISTORY_ROW`
- `litebans_history#4282` — `INVALID_HISTORY_ROW`
- `litebans_history#4649` — `INVALID_HISTORY_ROW`

Inspect only these eight rows using the existing read-only LiteBans DB access. Do not print raw IP/network addresses or credentials. Classify which required field is malformed/missing/incompatible with the importer. Do not delete, rewrite, or normalize production LiteBans rows yet.

Determine for each reject whether it is:

1. genuinely corrupt/invalid legacy source data; or
2. valid LiteBans data that exposes an importer compatibility bug.

If source repair is actually required, take/verify a DB backup first and present the exact minimal proposed repair for owner approval. If the importer is wrong, fix/test the importer instead of changing valid punishment history.

Rejected rows contribute to migration mismatch and therefore must be resolved before final cutover. They do not prevent non-destructive Staff feature testing while LiteBans remains authoritative.

## Safety boundaries

- Keep mode `SHADOW_MIGRATION`.
- Keep LiteBans authoritative.
- No `migration final`.
- No `cutover activate`.
- No manual edits to EnthusiaStaff migration mappings.
- No raw network identity in logs/reports/chat.
- Do not rotate or change key versions after protected identities have been imported unless a reviewed key-rotation procedure is used.
