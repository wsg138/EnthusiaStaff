# Policy v2 persistence contract

Policy v2 persistence is additive and non-authoritative. Policy v1 remains the live moderation path until a later, separately approved cutover.

## Migration ownership

W2 owns `V28__policy_v2_persistence.sql`.

W2 reserves `V28`. PR #352 has since merged, so current `main` owns V27 and the migration ordering dependency is satisfied. This stacked branch still targets the accepted W1 head, which predates V27; the upgrade test therefore targets V27 when present and otherwise recreates the exact merged V27 `staff_preferences` schema before applying V28. Once W1 is merged/rebased, the same test exercises the real V27 migration automatically. W2 does not edit or renumber an existing migration and does not deploy or execute a production cutover.

## Durable model

`JdbcPolicyV2Store` implements the domain-facing `PolicyV2Store` port.

The store persists:

- immutable version-keyed `PolicySnapshot` JSON plus a SHA-256 integrity digest; version reuse is validated by typed snapshot equality rather than JSON collection order;
- the original `IncidentFinding` and the current effective finding state;
- typed structured incident attributes as part of the W1 `IncidentFinding` representation;
- an immutable committed `PolicyResolution`, including `HistoryAssessment`, plus the exact `BehavioralHistoryEntry` inputs used to resolve it;
- remedies separately from sanctions, with independent REQUIRED / SATISFIED / WAIVED compliance state;
- an independent sanction revision stream;
- an independent finding revision stream;
- generic appeal linkage/history;
- append-only Policy v2 audit events;
- shadow evaluations that never become enforcement authority;
- an explicit allowlisted public projection that cannot represent evidence, incident attributes, history math, actors, internal notes, detection metadata, or identity/alt signals.

Existing Policy v1 `cases`, `sanctions`, and appeal rows are not rewritten or backfilled. A v1 case with no `policy_v2_cases` row means **legacy/unknown Policy v2 metadata**. W2 never reconstructs an old incident from the current policy.

## Revision semantics

Two counters are deliberately independent.

### Finding revision

Finding reclassification or overturn advances only `finding_revision`.

- Reclassification preserves the original finding and append-only revision row, changes the effective finding used by future history, and keeps the audit trail.
- Overturn preserves the historical case/revision rows, clears the effective finding, and changes the W1 `BehavioralHistoryEntry` state to `OVERTURNED`. W1 therefore exposes no contributing offense for future escalation.
- An overturned finding cannot be revised again through this API.

### Sanction revision

Sentence or sanction changes advance only `sanction_revision`.

- A leniency reduction does not mutate the original/effective finding.
- Behavioral-history reads use only finding state. They do not inspect the sanction stream.
- The initial applied sanction set is revision 0; subsequent leniency/other revisions are append-only.

This separation is the persistence guarantee behind the owner rule that sentence leniency must not reduce historical behavioral severity.

## Idempotency and concurrency

Policy v2 mutations use a global `policy_v2_operations` journal plus operation-specific unique constraints.

- Exact retries replay safely.
- Reusing an operation key for different input raises `PolicyV2Store.Conflict`.
- Finding, sanction, remedy, and public-projection writes use expected revisions and reject stale writes.
- Transactions roll back state, revision rows, operation records, and audit records together.
- Immutable policy snapshots are deduplicated by policy version and validated by typed equality; committed cases and shadow evaluations share the same snapshot ID without depending on JSON set ordering.

## W3 integration boundary

W3 should use `PolicyV2Store` rather than writing these tables directly.

W3 still owns:

- authorization/rank checks before calling privileged mutation methods;
- orchestration that creates/links the base moderation case and actual enforcement state;
- selecting a bounded W1 sanction option;
- policy-gap/manual-review workflow;
- converting resolved staff/player state into the narrow `PolicyV2PublicProjection`;
- deciding when shadow results are compared or surfaced;
- no-cutover safeguards while Policy v1 remains authoritative.

The persistence layer requires an actor UUID for privileged factual/sanction/remedy changes so anonymous privileged writes fail validation, but it deliberately does not duplicate W3 authorization policy.

## Public projection boundary

`PolicyV2PublicProjection` is the only Policy-v2 player-facing storage DTO in W2. Its fields are limited to:

- case ID;
- public offense label;
- public reason;
- public case status;
- incident time;
- public sanction type/status/end time;
- projection revision.

Do not expose internal JDBC rows or serialize `PolicyV2Store.CaseRecord` to a public API.

## Test coverage

`PolicyV2PersistenceIntegrationTest` covers:

- typed snapshot/finding/resolution/history-input round trips;
- operation replay and collision rejection;
- restart recovery;
- leniency/history independence;
- reclassification and future-history replacement;
- overturn/no future contribution;
- concurrent expected-revision writers;
- transaction rollback when audit persistence fails;
- remedy compliance;
- appeal linkage;
- shadow evaluation replay;
- public-projection persistence;
- legacy v1 cases remaining unknown to Policy v2;
- validation rejection when a privileged revision has no actor identity.

`PolicyV2V28MigrationIntegrationTest` upgrades from the current V27 schema shape with a seeded v1 case to V28 and verifies that the old case remains intact while no fabricated Policy-v2 metadata is created. On the current stacked W1 base, V27's exact merged table shape is recreated because W1 predates V27; after W1 is rebased/merged the test uses Flyway's real V27 directly.
