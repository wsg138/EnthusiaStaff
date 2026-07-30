# Shadow Mode and Cutover

Shadow mode proves that EnthusiaStaff reaches the same enforcement decisions as
LiteBans without enforcing them.

## Shadow mode

Default duration: exactly **168 continuous hours**.

During shadow:

- LiteBans remains authoritative.
- EnthusiaStaff mirrors new and imported punishment state.
- EnthusiaStaff calculates expected login, mute, and network decisions.
- The decisions are compared and recorded.
- Daily summaries are produced.
- EnthusiaStaff does not enforce.
- EnthusiaStaff does not write to LiteBans.
- Old jars remain installed.

A restart, mismatch, invalid source snapshot, recovery blocker, or incomplete
writer fence may require restarting the observation window according to policy.

## Mismatch categories

Compare at minimum:

- Source and target counts
- External IDs
- UUID/name mapping
- Active/expired state
- Issue time
- Exact expiration
- Ban login decision
- Mute/chat decision
- Network/IP decision
- New punishments created during shadow
- Duplicate and skipped records
- Recovery/quarantine state

Do not waive a mismatch because the total count is “close.”

## Founder override

Founder may waive only the time or cadence requirement where policy allows.
Founder may not waive:

- Unresolved mismatch
- Recovery/quarantine blocker
- Final import failure
- Writer fencing failure
- Ambiguous identity/network mapping
- Inability to prove exactly one authority

## Cutover prerequisites

- Full shadow window complete
- No unresolved mismatch
- Final source snapshot available
- Application and migration schemas healthy
- Paper and Velocity backends healthy
- Network channel healthy
- Required providers healthy or explicitly disabled
- Recovery queues clear
- Commands owned by expected plugins
- Backups and rollback instructions verified
- Exact commit and jar hashes recorded
- Staff and maintenance communication ready

## Maintenance procedure

1. Enter scheduled maintenance.
2. Stop player traffic and suppress reconnect-based alt evidence.
3. Freeze LiteBans and EnthusiaStaff writers as designed.
4. Take final backups.
5. Run final import.
6. Reconcile counts, active state, UUID, expiration, login, mute, and network
   decisions.
7. Block activation on any mismatch.
8. Transactionally switch EnthusiaStaff to `ACTIVE`.
9. Verify one authoritative writer and one enforcement path.
10. Test disposable ban/mute/login/chat cases.
11. Reopen traffic gradually.
12. Monitor recovery, outbox, database, and channel health.
13. Remove old jars only after the approved post-cutover checkpoint.

## Abort before activation

Before EnthusiaStaff becomes authoritative, aborting returns to LiteBans and
requires a fresh shadow window as defined by policy.

## Emergency after activation

If post-cutover authority is unsafe, enter `READ_ONLY_FAILURE` and stop
destructive work. Do not automatically switch back to LiteBans; post-cutover
sanctions may exist only in EnthusiaStaff.

Reconcile every post-cutover action into the selected authority before
reopening.

## Old plugin removal

The goals identify old jars for removal after successful cutover. Removal is
manual and server-specific. Verification may report duplicates but must never
delete jars.
