# Inactive moderation-case ledger: concurrency and recovery review

This document records the limited, **preproduction** semantics of `FileCaseLedger`
and `CaseDispatchWorker` reviewed in Worker G. **Do not wire the dormant staff
cards, controls, or notifications into startup based on these tests.**

## Canonical authority

- The Moderation API alone owns authoritative AI review case states and revisions.
  The local file ledger projects authenticated canonical events.
- `FileCaseLedger.action()` and `CaseStaffActions` are synthetic-only fixtures.
  Production staff commands must use `AuthorityBackedCaseActions`, which forwards
  requests and waits for authenticated canonical events; no local synthetic
  CLAIM/RESOLVE or punishment may be activated.
- Case revision equality and full-payload equality dedupe replay of a canonical
  event; an older revision is stale and an equal-revision different payload
  conflicts. This is **not** canonical operation-ID deduplication: that requires
  the API transport contract and durable authoritative idempotency records.

## Local concurrency and delivery invariants

1. The ledger's method-level `synchronized` is intentional. It serializes
   ingress revision CAS, reads, lease acquisition, acknowledgement, reconciliation,
   durable snapshot replacement, and publication of in-memory maps.
   Removing the synchronization to silence static analysis breaks the atomic
   projection/outbox contract. A future refactor may use one explicit lock only
   with equivalent coverage and concurrent regression tests.
2. The entire case/outbox snapshot is written to a temporary file, forced, and
   atomically renamed before the new maps become visible after a successful call.
   If persistence throws, the operation must not return success.
3. Each case revision enqueues distinct `DISCORD` and `MINECRAFT` deliveries.
   `begin` persists one unique in-flight token before a send is permitted.
   Acknowledgements require the matching token, and one surface's receipt never
   acknowledges the other surface.
4. A persisted in-flight attempt becomes `UNCERTAIN` after restart. Unknown send
   outcomes stay fenced rather than automatically re-sent. A newer revision's
   delivery on the same surface remains fenced while an older send is ambiguous.
   Only trusted confirmed receipts or verified proof of non-delivery clear the
   uncertainty; the code does not discover that proof by itself.
5. A custom synthetic authorization callback can reenter ledger methods on
   the same thread. After the callback, the ledger checks that the old projection
   has not changed before committing a local synthetic state transition.

## Limits — not release ready

- **Single process/instance only**: no multi-process locking, distributed CAS,
  MariaDB-backed leases, or simultaneous owners of the same journal directory.
  Multiple file-ledger instances may be constructed for sequential restart
  simulations, but may not be used concurrently for mutations.
- **Crash durability limit**: temporary-file fsync and atomic rename are present,
  but the parent directory is not fsynced; a sudden power failure may therefore
  lose the latest rename on some file systems. This needs OS-specific acceptance.
- An unreadable/corrupt committed snapshot fails closed on load; pending temporary
  files are not authoritative. No silent reinitialization is allowed.
- A confirmed remote receipt, or proof that a remote message is absent, depends
  on a trusted external adapter. HTTP/JDA/Paper side-effect idempotency and
  cross-process restart continuity remain integration work.
- No canonical API operation ID is stored by this projection. No authenticated
  ingress, production authority gateway, current-role proof, real guild/permission
  test, player-facing GUI, punishment, or deployment is enabled here.

## Synthetic tests

`FileCaseLedgerConcurrencyTest` covers parallel identical canonical event
replay, competing revisions, canonical CLAIM/RESOLVE projections, competing
delivery leases, independent Discord/Paper receipts, stale completion tokens,
restart ambiguity barriers, receipt reconciliation, canonical reopen after
restart, reentrant authorization, orphaned pending files, and corrupt committed
snapshots. The existing `FileCaseLedgerTest` tests retry/backoff, expiry,
cross-scope denial, and asynchronous possibly-sent outcomes.

The Windows offline regression command is:
`gradlew.bat :domain:check :persistence:check :paper:check :staff-bot:check --offline --no-daemon`.
Windows-only exclusions (POSIX permissions/symlinks and real Python API tests)
must be tracked separately. The existing intentionally crashing synthetic
AiMirror probe may emit an exception before successful recovery.

Fresh hosted Java 25 / Codacy / MariaDB / coverage checks and independent
production security acceptance are required; an offline Windows pass is not
a release approval.
