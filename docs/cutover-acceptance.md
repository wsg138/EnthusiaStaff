# LiteBans cutover acceptance plan

This plan produces the production-like evidence required before EnthusiaStaff may replace LiteBans as moderation authority. It is a staging procedure. It does not authorize a live cutover, production deployment, database repair, or deletion of legacy data.

## Evidence identity

Create one acceptance record before starting and keep every artifact under that record.

Record:

- exact EnthusiaStaff repository SHA;
- exact Paper and Velocity jar SHA-256 values;
- exact configuration revision with secrets removed;
- MariaDB version and restored backup timestamp;
- Velocity, HUB, SMP, Java, Geyser/Floodgate, and provider versions;
- operators, start time, end time, and every interruption;
- GitHub Actions run IDs and staging evidence artifact names.

Any runtime jar, migration configuration, source backup, comparison algorithm, or schema change invalidates the active acceptance record and starts a new shadow window.

## Phase 1: representative restore and dry-run proof

1. Restore a recent sanitized production backup into an isolated MariaDB staging environment.
2. Keep LiteBans authoritative and prevent the staging environment from contacting production players or webhooks.
3. Run schema inspection and save the complete supported-column and blocker report.
4. Run a dry migration, then run it again without changing the source.
5. Confirm the rerun is idempotent: no duplicate cases, sanctions, identities, mappings, or audit events.
6. Interrupt an import after durable progress exists, restart the runtime, and run it again.
7. Confirm the abandoned run is marked `FAILED` with `ABANDONED_AFTER_PROCESS_FAILURE`, the replacement run receives a new ID, and final state matches a clean run.
8. Explain every rejected row, orphan mapping, schema variation, and count difference.

Required artifacts:

- restore source and timestamp;
- schema report;
- first dry-run report;
- rerun report;
- interrupted-run row before restart;
- failed abandoned-run row after restart;
- replacement-run report;
- sanitized count, checksum, mapping, expiration, and decision comparison output.

## Phase 2: uninterrupted 168-hour shadow window

1. Enter `SHADOW_MIGRATION` while LiteBans remains the only authority.
2. Run real comparisons continuously for at least 168 hours.
3. Produce seven valid daily summaries covering the complete interval without a gap.
4. Save all comparison dimensions for every summary:
   - counts;
   - checksums;
   - active sanctions;
   - UUID mappings;
   - expirations;
   - login decisions;
   - mute decisions;
   - IP/network-ban decisions.
5. Treat a process outage, missed summary, changed release candidate, changed source dataset, unexplained mismatch, or shadow abort as a broken window. Restart the 168-hour clock.
6. Resolve and document every mismatch. Do not use the Founder early-window override as normal acceptance evidence.

Daily operator record:

| Day | Start | End | Run ID | All dimensions matched | Mismatches explained | Runtime interruption | Artifact |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | | | | | | | |
| 2 | | | | | | | |
| 3 | | | | | | | |
| 4 | | | | | | | |
| 5 | | | | | | | |
| 6 | | | | | | | |
| 7 | | | | | | | |

## Phase 3: maintenance and final incremental rehearsal

Use the representative staging restore and the exact release candidate.

1. Stop simulated player traffic and legacy write permissions.
2. Save `/estaff cutover status`.
3. Enter maintenance with `/estaff cutover maintenance`.
4. Verify every EnthusiaStaff authoritative writer is rejected and no delegate executes.
5. Verify legacy punishment writes are also disabled for the rehearsal window.
6. Run `/estaff migration final`.
7. Confirm the final `CUTOVER` run began after maintenance, completed successfully, and is the newest final run.
8. Confirm exact affected-row counts and all comparison dimensions.
9. Restart Velocity and both Paper backends while still in maintenance.
10. Confirm maintenance mode, the final run, writer fencing, authenticated backend state, and cutover blockers survive restart.
11. Abort once with `/estaff cutover abort CONFIRM-ABORT-MAINTENANCE <reason>` and confirm the shadow continuity resets.
12. Repeat the final incremental rehearsal from a fresh acceptance dataset before activation testing.

## Phase 4: activation, ambiguous response, and freeze rehearsal

This phase remains isolated from production.

1. Enter maintenance and complete a valid final incremental run.
2. Activate once with `/estaff cutover activate CONFIRM-ACTIVE-CUTOVER`.
3. Simulate an ambiguous client outcome by discarding the success response or terminating the command-side process after the database commit is known to have completed.
4. Restart the runtime and retry activation.
5. Confirm the retry does not create a second cutover record or activation audit and reports that maintenance is required because authority is already `ACTIVE`.
6. Verify the durable cutover ID links to the exact final migration run.
7. Verify one normal Java login, one Bedrock/Geyser login, an active player ban, an IP/network ban, a mute, an expiration boundary, and a server switch across Velocity, HUB, and SMP.
8. Trigger `/estaff cutover freeze CONFIRM-READ-ONLY-FAILURE <reason>`.
9. Restart every component and confirm `READ_ONLY_FAILURE` persists, destructive services remain fenced, configured authority checks fail closed, and a duplicate freeze does not create a second transition.

## Phase 5: rollback and post-cutover reconciliation rehearsal

1. Keep player traffic stopped after the freeze.
2. Create a reviewed list of every case and sanction created or changed after the rehearsal cutover timestamp.
3. Restore the prior compatible jars without reopening traffic.
4. Reconcile post-cutover changes into the selected authority using an idempotent reviewed procedure.
5. Run the reconciliation a second time and prove it creates no duplicates or additional changes.
6. Verify exact parity for bans, mutes, IP/network bans, expirations, UUID mappings, counts, and checksums.
7. Confirm recovery quarantine, inventory/economy incomplete work, restoration reservations, outbox retries, and dead letters are zero or explicitly quarantined with an owner.
8. Enable exactly one authority and repeat staged login, chat, and server-switch checks.

## Phase 6: distributed runtime and failure acceptance

The exact release candidate must run in the intended topology:

```text
Velocity
├── HUB + EnthusiaStaff-Paper
└── SMP + EnthusiaStaff-Paper
```

Verify:

- authenticated persistent backend connections;
- proxy login and server-switch enforcement;
- HUB/SMP ownership and separate inventory scopes;
- Java and Bedrock/Geyser identity behavior;
- provider-present and provider-missing behavior;
- bounded database pools and executor queues;
- database outage and reconnect behavior;
- Discord/webhook outage, retry, dead-letter, and manual recovery behavior;
- process termination during migration, punishment, inventory, economy, and restoration workflows;
- recovery queue draining and duplicate safety after restart;
- sustained database latency and saturation limits.

## Approval gate

The cutover PR may be marked ready and merged only when all of the following are attached to one acceptance record:

- clean hosted build, tests, coverage, jar inspection, and static analysis for the exact head SHA;
- successful Paper boot and restart staging for the exact runtime SHA;
- representative backup dry run, rerun, interruption, and restart evidence;
- an uninterrupted 168-hour window with seven complete daily summaries;
- successful final incremental import rehearsal;
- restart/resume and ambiguous-activation retry evidence;
- emergency-freeze persistence evidence;
- rollback and idempotent post-cutover reconciliation evidence;
- distributed Velocity/HUB/SMP, Java/Bedrock, provider, queue, dead-letter, and latency acceptance;
- operator review confirming backups, restore procedure, prior jars, permissions, secrets, and rollback staffing.

A green unit-test or CI run alone is not production cutover approval.
