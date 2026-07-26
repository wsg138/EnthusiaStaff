# Database design

MariaDB is the authoritative store for moderation and recovery state. SQLite is not a production target. All production schema changes are Flyway migrations; operators must not make ad-hoc schema changes.

## Conventions

- UUIDs are stored as `BINARY(16)` in application tables and rendered only at boundaries.
- Timestamps are UTC `TIMESTAMP(6)` or epoch milliseconds at an import boundary, then normalized.
- Human-facing case IDs are immutable random identifiers with a unique index.
- Mutable aggregates carry a monotonically increasing `revision`.
- JSON columns contain bounded evidence or snapshots with an explicit schema version; searchable business state remains normalized.
- Every privileged operation has an opaque idempotency key and correlation ID.
- Sensitive network values are encrypted; equality queries use a separately keyed HMAC token.

## Table groups

Identity and evidence:

- `players`, `player_names`, `player_sessions`, `client_evidence_snapshots`
- `network_identity_tokens`, `alt_relationships`, `alt_evidence`

Moderation:

- `cases`, `case_evidence`, `sanctions`, `sanction_events`, `sanction_links`
- `punishment_steps`, `punishment_overturn_requests`, `warnings`, `staff_notes`
- `reports`, `report_messages`, `report_chat_snapshots`, `report_private_message_snapshots`

Stateful operations:

- `staff_sessions`, `staff_state_snapshots`
- `inventory_profiles`, `inventory_profile_revisions`, `inventory_operations`, `inventory_snapshots`, `inventory_pending_patches`
- `confiscated_asset_snapshots`, `economy_operations`, `market_compliance_cases`, `reputation_blacklists`

Delivery, configuration, and operations:

- `staff_alerts`, `discord_outbox`, `network_outbox`, `network_inbox`
- `migration_runs`, `migration_mappings`, `shadow_comparisons`, `configuration_versions`
- `audit_events`, `operation_leases`, `recovery_quarantine`

## Required integrity constraints

- One case number and one idempotency key per accepted case.
- One imported record per `(source_system, source_table, external_id)`.
- At most one open overturn request per case.
- At most one active relationship row per unordered player pair.
- `NOT_RELATED` cannot be replaced without an explicit privileged reopen audit event.
- Sanction expiration is null only for permanent sanctions and is always after issue time otherwise.
- One network inbox row per `(consumer_id, message_id)`.
- One active lease per resource key; every mutation records the fencing token it observed.
- Snapshot and pending-patch revisions must advance by one and may not overwrite newer data.
- Audit rows are append-only at the application account privilege level.

## Transaction boundaries

### Create punishment

Lock the target's active-sanction and escalation rows; validate the stored configuration checksum; insert case, step, sanctions, sanction events, audit, staff alerts, and network/Discord outbox records; commit once. No platform enforcement begins before commit.

### Remove or overturn

Lock the case and active sanctions; authorize the exact transition; append sanction events and immutable audit; update escalation contribution; enqueue enforcement removal; commit once. Full overturn hides ordinary/public history but never deletes Founder audit.

### Inventory or economy operation

Insert intent and before-snapshot metadata, acquire the fenced lease, and commit before touching external state. Each verified stage advances with an optimistic revision. If compensation cannot be proved, set `QUARANTINED` and block related destructive work.

### Outbox consumption

A worker claims due rows with a bounded lease. The consumer inserts its inbox marker and domain effect in one transaction. Duplicate delivery reads the stored outcome. Senders mark acknowledged only after the consumer's durable acknowledgement.

## Index plan

Indexes cover UUID and lowercase/current/previous names; active sanction type and expiration; family and target history; report state and assignee; unread staff alerts; relationship pairs and HMAC tokens; external LiteBans IDs; due outbox/recovery work; due market review; and public case/status/search views. Query plans are captured during load testing and redundant indexes are removed before cutover.

## Public website boundary

The website database account receives `SELECT` only on sanitized views such as `public_cases`, `public_sanctions`, and `public_player_names`. Views exclude private cases, fully overturned cases, reporter identity, coordinates, staff notes, network identity, alt evidence, confiscation data, and raw automation metadata. Website writes never target these views or moderation tables.

## Migration and recovery

Flyway validation runs during `BOOTSTRAP`. A checksum mismatch or unsupported future schema enters `READ_ONLY_FAILURE`; repair is a deliberate operator action, never automatic. Migration imports keep source IDs and checksums, use upsert-safe mappings, and write a run manifest. Rollback disables the new authority and preserves imported rows for forensic comparison rather than deleting production history.

Historical actor identity is immutable. Cases previously issued by a Developer retain the original actor ID, name, and `DEVELOPER` rank and remain valid history; the current authorization policy is applied only to new mutation requests. LiteBans does not expose a trustworthy historical rank, so its imports retain the original staff name as provenance and use the non-interactive `SYSTEM` rank instead of guessing Mod, Developer, or Admin authority.
