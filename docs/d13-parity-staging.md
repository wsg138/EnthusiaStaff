# ES-D13 staff-rank DiscordSRV parity — offline staging preflight

**Status: real staging gate NOT RUN.** The `tools/d13-parity/` runner is a synthetic/offline, **non-authorizing** comparison. It neither talks to Discord/Paper/MariaDB nor starts or configures StaffBot. Its result is **not** permission to merge the historical #178 branch, enable D13 ENFORCE, disable DiscordSRV, or deploy any JAR.

## Why this exists, and source reconciliation

EnthusiaStaff #178 is closed and unmerged; #330 separately merged its current-live-main D13 role-sync implementation, including `DiscordRoleSyncConfiguration`, `DiscordRoleSyncService` and `JdaDiscordRoleReconciler`. Keep #178 parked for historical review, rather than re-merging it. Current `docs/discord-role-sync.md` describes runtime SHADOW parity. Generic managed-role SHADOW in #603/#604 and proposed #614 is a **different feature** and does not prove staff-rank D13 parity.

Current D13 code takes the **union of inherited/effective LuckPerms groups** across all canonical Minecraft accounts linked to each Discord user, regardless of main account or `on_duty` state; maps groups to explicit role IDs; and reports `SHADOW_MATCH`/`SHADOW_DRIFT` based on the configured managed-role delta. An unavailable member may yield `MEMBER_ABSENT`, which this preflight intentionally refuses to accept as a complete parity observation. D13's current SHADOW path calculates drift before ENFORCE's Discord hierarchy checks; therefore this preflight separately rejects public, integration-managed, missing, non-interactable, protected and ownership-conflicted managed roles. None of those safety conditions may be concealed by a zero-drift SHADOW record.

## Local execution (Python standard library, no dependencies)

Run from the repository root:

```sh
python3 -m unittest discover -s tools/d13-parity -p 'test_*.py' -v
python3 tools/d13-parity/parity.py tools/d13-parity/fixtures/synthetic-window.json
# PRIVATE staging-only snapshots, supplied in increasing timestamp order:
python3 tools/d13-parity/parity.py /private/d13/window-1.json /private/d13/window-2.json /private/d13/window-3.json
```

The script reads at most 12 JSON files (1 MiB each) and prints a **single counts-only JSON object**. It never prints usernames, Minecraft UUIDs, Discord member/role IDs, mappings, role names, private paths, arbitrary exception messages, or raw records. It never writes any file and does not make network requests. Store real snapshots only in an approved private owner-controlled location; **do not commit, upload or paste them into GitHub, CI logs or the worker chat.** Do not substitute production snapshots or credentials.

A `FIXTURE_MATCH_ONLY` result proves the synthetic test data is internally consistent. A staging `OWNER_REVIEW_REQUIRED` result (**exit 2, deliberately non-success**) means three consistent complete windows passed this offline arithmetic and safety screen; it **does not attest** that the snapshots came from genuine, authorized legacy/StaffBot runtime or that operator acceptance is complete. `BLOCKED` (exit 1) includes a sanitized error code and may include partial aggregate counts. Input completeness flags and counts are self-asserted and must be independently verified by the owner. No state from this tool authorizes actions.

## Private JSON snapshot v1 contract

Each window follows `fixtures/synthetic-window.json` exactly. Unknown fields and duplicate JSON keys are rejected, as are duplicate IDs, group/role entries, invalid decimal Discord snowflakes, invalid UUID syntax, and malformed/nonunique role names. The dummy IDs and the example role mapping are fixtures only; they are **not** the actual DiscordSRV mapping.

- `kind`: `synthetic` or `staging`; `captured_at`: UTC ISO-8601 timestamp; staging capture cannot be in the future or older than 30 minutes when checked. Windows must be increasing and, for staging, at least 30 seconds apart.
- All nine boolean flags (`legacy_writer_active`, `mapping_complete`, `links_complete`, `accounts_complete`, `membership_complete`, `d13_shadow_complete`, `role_catalog_complete`, `ownership_complete`, `scan_complete`) must be `true`, independently substantiated by the operator. A failed/retried/unfinished D13 sweep must instead be marked incomplete, never synthesized as a match.
- `group_source`: must be `luckperms-effective-inherited`. `duty_policy`: must be `ignored` (matching *current* D13 source behavior); all `accounts` must include their observed `on_duty` boolean. If legacy behavior depends on duty, do not change or omit that distinction; classify as a real semantic discrepancy and block migration.
- `d13_mode`: must be exactly `SHADOW`. `legacy_mapping` and `d13_mapping`: exact canonical lowercase group to decimal role-ID mappings, including ALL authorized legacy groups. These must match. D13's actual configured values must be independently captured, not inferred or copied from the legacy side for convenience.
- `links`: all canonical **current active** Discord→Minecraft link sets, including multi-Minecraft→one-Discord. Every link must have `captured_at` within five minutes of the window. Revoked/stale links, missing accounts, duplicate Minecraft-to-two-Discord assignments, missing authoritative coverage counts or empty subject scans are rejected. Link snapshots must be sourced from the canonical Enthusia link authority, **not solely DiscordSRV**.
- `accounts`: one complete effective/inherited LuckPerms group snapshot for every linked Minecraft UUID, with `on_duty` and each record's `captured_at` recorded within five minutes of the window. `expected_discord`/`expected_minecraft`: independently sourced total coverage counts, not counters derived by taking the length of truncated exports.
- `legacy_role_holders`: an **independently exported exhaustive member-ID list for every mapped managed Discord role**, including members without current canonical Minecraft links. It must provide an entry for each managed role, even when no member holds it (empty list). Unknown/unlinked holders are an explicit hard blocker because D13 only scans active linked identities; omissions/disagreement against per-member inventories also fail closed. Do **not** derive this role-holder inventory from `legacy_members`; source it independently from authorized read-only legacy Discord staging state.
- `legacy_members`: complete actual managed/unmanaged role inventories for *each* linked Discord identity while the legacy DiscordSRV writer is active. Every linked identity must have an entry, even if no roles are assigned; each legacy membership record must carry a fresh `captured_at`. Keep unmapped roles in the input to prove they are never treated as removals.
- `role_catalog`: full relevant managed-role metadata: exact IDs, names, namespace owner (`staff-ranks` for D13), presence, public/integration-managed flags and whether the target bot can interact with the role. Names must be unambiguous. `protected_roles` must not intersect managed roles.
- `ownership_claims`: complete other managed-role claim ownership observations, if any, with `role_id`, `namespace`, `resource`. Different claim owners sharing one managed role ID block parity; multiple conflicting claims for one ID count as **one** collided ID. Declare `ownership_complete=false` if external role ownership is unknown.
- `d13_shadow`: separately observed per-Discord-user **real StaffBot SHADOW** results: `state`, `desired_roles`, `observed_roles`, `captured_at` (fresh to within five minutes). Input observed sets must be consistent with the captured legacy memberships. Never fabricate a `SHADOW_MATCH`, including for absent Discord members, missing role records, retries or outdated persistence rows.

The checker compares only roles managed by the exact mapped ID set. It counts missing and extra **Discord member↔managed-role assignments per stable window**, and distinct colliding role IDs per window; repeated windows do not multiply the same mismatch. It validates D13 state as well as computed desired/observed parity. Staging windows must have stable mapping, link/group/duty, legacy membership, role metadata and ownership; investigate any change rather than accepting a coincidental aggregate zero. A role not appearing in a snapshot is not evidence that it was deleted. Independently verify the role-holder export covers **all** holders, including Discord members whose Minecraft links are no longer active.

## Owner-only evidence and actual D13 gate

Keep the raw evidence private; return only a sanitized receipt with:

1. Non-production staging environment label, artifact/build SHAs of Paper + StaffBot + DiscordSRV, configuration revision **identifiers only**, and exact snapshot timestamps (no private config contents).
2. Evidence that the **legacy writer remained active** throughout, D13 `SHADOW` was enabled against the authorized **same role IDs**, and the owner confirmed protected-role/namespace ownership and bot hierarchy conditions.
3. Three or more complete, separately scanned stable windows, with source capture freshness/coverage counters, link scan counts, UUID group lookup success counts, D13 scan completion, and aggregate missing/extra/collision counts **all zero**. Include no actual ID or mapping lists.
4. Independent sample scenarios in authorized staging: two Minecraft accounts linked to one Discord user, main-account change, staff duty-state variation, unlink/revoke/relink, member absent, restart/retry/authority unavailability. Track blocked or inconclusive outcomes, never erase them from the receipt.
5. Private owner sign-off on legacy-vs-D13 semantics and zero **unexplained** drift; reproducible build/test/analyzer/Codacy evidence against current `main` for any future integration candidate; separately approved cutover and rollback plan.

**Still missing today:** independently authorized non-production real DiscordSRV group→role mapping, complete effective legacy role holders **with independent whole-role holder enumeration**, current canonical links/effective groups, role ownership and hierarchy metadata, and actual D13 staging SHADOW runtime windows. These private prerequisites must come from the operator. This project does not request secrets, public player data, production mutations, or writer activation. The gate stays **NOT RUN** until actual staging evidence is supplied and reviewed.
