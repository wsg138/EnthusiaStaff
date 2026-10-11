# EnthusiaStaff staff-action audit: staging acceptance

**Status: staging preparation only — not an acceptance result or live deployment authorization.** Tracks [#488](https://github.com/wsg138/EnthusiaStaff/issues/488). This plan covers the already-merged durable JSONL/MariaDB outbox work (#489), event-audit additions (#499, #501–503, #508), and the pending confirmed-lifecycle and item-frame changes (#507, #509). Execute applicable cases only after the exact build containing them has passed CI and been selected.

## 1. Safe environment and build selection

1. Select an **isolated staging** Paper server/proxy, separate staging MariaDB schema/credentials, and a *private* Discord channel for the `logs-staffmode` destination. No production server restart, existing production punishment policy, or public channel is required. Do not test with real player punishment or unsolicited staff pings.
2. Record an exact **40-character main source SHA** and verify [Staff Platform Staging Release](../.github/workflows/staff-platform-staging-release.yml) succeeded on that SHA. The `staff-platform-staging` GitHub prerelease is **mutable**, so do not rely on its tag name alone. Obtain `staff-platform-staging-source.txt`, both `*.jar.sha256` files, and the Paper/Velocity runtime JARs; check the manifest SHA against the planned source and verify both hashes *before* copying anything. A release built from an older main does not contain pending #507/#509 work.
3. For PR-only dry runs, exact-head [Sentinel Restart Artifact](../.github/workflows/sentinel-restart-artifact.yml) builds a Paper JAR but **does not install it** or prove a plugin restart. The optional canonical public [Pi Staging workflow](../.github/workflows/pi-staging-check.yml) is a separate protected disposable runtime path; follow [STAGING-TEST-OPERATING-GUIDE.md](../ai-agents/STAGING-TEST-OPERATING-GUIDE.md). Do not dispatch the private Pi workflow directly.
4. Back up existing **staging** JARs and privately record their hashes, current config (with secrets redacted), MariaDB schema version, player inventory test baseline, and the version/commit selected for rollback. Configure Paper, Velocity, DB and webhooks from the staging secret store only. Never paste credentials, Discord tokens, signed requests, real player data or raw chat/audit rows into PR comments or test reports.
5. The new Paper JAR has the embedded AuthorityBridge from #505. Keep `authority.enabled=false` and `collector.enabled=false` in staging unless **separately** exercising that cutover. Preserve any standalone AuthorityBridge JAR and its private files for rollback. See [authority-bridge-consolidation.md](authority-bridge-consolidation.md). Policy v2, live enforcement, and DiscordSRV retirement are **not** authorized by this audit test.
6. Stop **only staging** processes normally, install the verified Paper/Velocity artifacts, start DB then proxy/Paper, and confirm startup/Flyway success, Staff Mode session restoration, healthy outbox worker and correct private webhook destination. The StaffBot JDA process is separate and not required for the existing webhook outbox delivery path unless the staging configuration explicitly routes through it.

## 2. Action and lifecycle matrix

Use two disposable, consented test player identities with explicit staging HELPER and MOD permissions, plus an ADMIN identity if available. Give each row a short test ID and capture *sanitized* timestamps; do not include secrets or raw command arguments in evidence.

| Test | Procedure on staging | Expected audit/effect |
| --- | --- | --- |
| L01 | Successful fresh `/staff` enter | `staff-mode-entered` with matching session UUID, `path=fresh-entry`; native inventory snapshot preserved |
| L02 | Denied enter while combat-tagged or while session DB unavailable | **No** `staff-mode-entered` for the failed attempt; player state is not lost |
| L03 | Normal `/staff` exit | `staff-mode-exited`, `reason=manual`, only after original inventory/location restore and durable `completeExit` |
| L04 | Forced exit on rank removal/reconciliation | `staff-mode-exited` with `reason=rank-removed` or the documented profile failure; no falsely successful exit when restoration fails |
| L05 | Active session resume after staging restart | `staff-mode-resumed` with `path=active-recovery`, **not** a fresh entry |
| L06 | Detached backend rebind | `staff-mode-resumed` with `path=detached-rebind`; network handoff/normal quit is **not** `staff-mode-exited` |
| L07 | Interruption after state restore but before durable closure | Recovery remains pending; no false confirmed exit; preserve evidence for a retry |
| A01 | HELPER attempts non-tool item drop | Exactly one `item-drop-denied`; item not dropped |
| A02 | HELPER attempts protected staff-tool drop | Distinct `staff-tool-drop-denied`, not a duplicate `item-drop-denied` |
| A03 | HELPER attempts main/offhand swap | One `inventory-swap-hands-denied`; existing inventory unchanged |
| A04 | HELPER tries protected staff-tool transfer in inventory | One `staff-tool-transfer-denied` per click; only click/container type in detail, never NBT |
| A05 | HELPER manipulates armor-stand equipment | `armor-stand-edit-denied`; unchanged equipment; no generic interact duplicate |
| A06 | HELPER right-clicks item frame with main hand, including at-entity variant | `item-frame-edit-denied` once per main-hand generic interaction; precise variant must not log a duplicate; offhand-only behavior must be observed and any missing event logged as a staging limitation |
| A07 | HELPER edits protected container top inventory/drag | Existing `container-edit-denied` path, with no redundant lower-inventory noise |
| A08 | On-duty MOD performs permitted drop/swap and edits | Existing allowed-action IDs; no denied audit for allowed actions |
| A09 | Off-duty identity attempts a gated command, including arguments | `staff-command-denied-off-duty`; audit detail is only allowlisted command root + `arguments withheld`; no target, reason or command text leaked |
| A10 | Repeat normal player pickup attempts near staff or idle beside items | No new high-rate passive-pickup denial audit flood; measure event and queue rate |

Also sample existing block place/break, bucket, shearing, harvesting, fishing, consuming, combat and staff mode visibility logs. **Do not claim all possible Staff Mode actions have been audited** based only on this matrix.

## 3. Durable delivery and privacy proof

For **every** intended action, trace a sanitized `eventId` and session ID (for lifecycle) through all stages:

1. A single valid JSON line in `plugins/EnthusiaStaff/discord-outbox/staff-actions-YYYY-MM-DD.jsonl` with `action`, `rank`, `staffMode`, and non-sensitive `detail`. The ordinary log has the same `eventId`. Never share full raw rows publicly.
2. **Exactly one MariaDB `discord_outbox` row** keyed by `idempotency_key=staff-action:<eventId>`, destination `logs-staffmode`. Duplicates should be ignored by the DB uniqueness guard; no duplicate role pings.
3. At least one matching **private** Discord embed or a recorded failed/dead-letter delivery. Discord HTTP may be **at least once** after ambiguous network timeouts; do not assert exactly-once Discord delivery.
4. Confirm `rank` on recovered exits uses cached rank when present, otherwise resolves the explicit live rank (and legitimately may be `NONE` after removal). No credentials, passwords, token-like tool PDC, private command arguments, or unnecessary item metadata should appear in details.

Verify recovery separately: reject the worker submission, briefly disconnect the *staging* database, restart between outbox insertion and cursor update, simulate webhook 429/5xx, and recover a 30+ day unacknowledged journal. On replay, check cursor/checksum consistency, bounded 200-record/15-second processing, no duplicate database rows, and no premature journal deletion. Preserve malformed/partial records for manual inspection; **do not delete evidence or mass-replay production logs**. Record disk/backlog/dead-letter metrics, not private payloads.

## 4. Pass conditions, evidence, rollback

Accept only with the exact source SHA, successful build/Codacy/hosted jobs, independently verified hash, and test case table showing observed `PASS`/`FAIL`/`NOT RUN`, each with a sanitized evidence pointer. Record the staging schema version, outbox counts, start/end time, sanitized Discord channel identity, two-cycle startup/shutdown and cleanup status, and unresolved duplicate/missing-action defects.

**Stop before promotion** on unexpected channel routing, actor permissions changing, missing audit events, secret/argument leakage, restoration loss, unexpected duplicate Discord pings, unsafe journal cleanup, wrong checksums, non-staging database connection, or a failed rollback. Restore staging's prior JAR/config from verified backups and let the owner decide when to retry.

A green local/JVM CI build is **not** evidence of live interactions or Discord delivery. The staging release, separate Sentinel proof, canonical Pi proof, and full private Discord acceptance are distinct checks. **Production rollout, runtime flags, standalone bridge retirement, and Policy v2 activation require separate approval.**
