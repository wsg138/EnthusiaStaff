# ES-P09 alt and network-identity handoff

Date: 2026-08-07
Package: `ES-P09 — Alt and network-identity completion`
Worker: `ChatGPT sequential package worker`
Status: `ACTIVE`

## Selection and routing

- Legitimate aggregate `main` at selection: `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7` (merge PR #82).
- Implementation branch/integration authority: `package/es-p09-alt-network-identity`.
- No pre-existing ES-P09 branch, PR, or package handoff existed before claim.
- ES-P02 PR #70 and ES-P05 PR #81 remain `BLOCKED` / `PARKED_BLOCKED` on the unchanged private Actions Billing & plans zero-runner condition; they were not retried or modified.
- ES-P10 remains READY but unassigned. No second package is activated.

## Reconciled package boundary

- Current ES-P09 contract owns protected network identity, alt graph/confidence/manual relationships, inheritance safety, bounded retention/restart behavior, `/alts` and `/alt` operator workflow, privacy, tests, and documentation.
- ES-P03 owns canonical Java/Floodgate platform identity and normalization; ES-P09 consumes that completed boundary without redefining it.
- Raw/reversible network addresses must never persist, log, or appear in staff output.
- Production/private representative address datasets, false-positive campaign, and distributed Java/Bedrock acceptance remain deferred to ES-V02.
- Production deployment/cutover and issue #43 remain excluded; LiteBans stays authoritative.

## Existing implementation baseline

- `NetworkIdentityProtector` already provides versioned HMAC equality tokens plus AES-GCM protected values.
- Velocity already zeroes the temporary raw address byte buffer after protection.
- `JdbcNetworkIdentityStore` already persists protected observations, creates same-network evidence, models manual relationship states, and contains sanction inheritance plumbing.
- Velocity already registers `/alts` and manual `/alt` relationship commands.
- The audit still classifies graph/ambiguity/retention/restart proof as incomplete or weakly verified, so ES-P09 must harden rather than replace these foundations.

## Active checkpoint

Package state is claimed and published on the canonical branch. Product implementation, tests, review, exact-head validation, merge/containment, terminal state publication, and cleanup remain.
