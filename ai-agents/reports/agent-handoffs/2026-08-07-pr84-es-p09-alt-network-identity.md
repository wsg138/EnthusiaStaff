# PR #84 — ES-P09 alt and network-identity handoff

Date: 2026-08-07
Package: `ES-P09 — Alt and network-identity completion`
PR: `#84`
Temporary branch: `package/es-p09-alt-network-identity` (deleted after merge)
Base at selection: `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7`
Frozen implementation head: `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`
Merge: `a88201524690848f778297f140f7ee2ba5b6ce36`
Status: `COMPLETE`

## Scope completed in PR #84

- Protected-token alt matching remains address-private.
- Shared-network matching and sanction reads are bounded.
- Large shared networks suppress automated graph expansion.
- Simultaneous independent play lowers automatic confidence without overwriting manual decisions.
- The narrow authoritative new-account inheritance rule requires an unambiguous single match; inherited sanctions preserve exact remaining expiry and remain idempotent.
- Duplicate evidence is rate-limited.
- Manual relationship reasons reject raw IPv4/IPv6 literals before durable audit storage.
- Sensitive identity tokens and detailed evidence have bounded batched retention; durable relationship decisions survive retention/restart.
- Retention does not run in the login transaction or derive its cutoff from an observation timestamp. `MariaDbRuntime` schedules retention from an injected trusted UTC clock, and the authority fence prevents non-authoritative deletion.
- Direct unit/MariaDB tests cover privacy, key-version isolation, ambiguity, concurrent proxies, sanction inheritance, retention/restart, and authority fencing.
- Wiki privacy/investigation guidance is updated.
- No schema migration was required; V17 remains current and immutable.

## Review and validation

All four actionable CodeRabbit threads from the substantive review were resolved/outdated. The major product finding—observation-timestamp-derived retention inside the login transaction—was fixed before the final freeze. Zero valid unresolved review threads remained at merge. A later incremental CodeRabbit attempt was quota-limited; it is not represented as successful review evidence. Repository CodeRabbit status on the frozen head was `success`.

Exact frozen head `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`:

- Wiki/package validation run `31193764800`, job `92916829444`: `success`.
- Coverage/Java run `31193765341`, job `92916907616`: `success` on Java 21 with full build/tests, MariaDB/Testcontainers coverage, aggregate JaCoCo, runtime-JAR creation/inspection, and coverage upload; no failed-test diagnostic path ran.
- Codacy static check `92917176627`: `success`, zero annotations.

## Private staging disposition

Private staging is **NOT A PASS** and was not used as development acceptance evidence. Public wrapper run `31193762319` dispatched private run `31193769314`; required Ubuntu build `92916864019` had runner ID `0`, empty runner name, and steps `[]` because GitHub rejected the job under the account Billing & plans restriction before product execution. Pi job `92916876057` was skipped. ES-P09's explicit contract assigns private representative-network false-positive/distributed acceptance to `ES-V02`.

## Merge and containment

PR #84 merged normally as `a88201524690848f778297f140f7ee2ba5b6ce36`. Post-merge `main` has frozen implementation head `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb` as its second parent. Compare from frozen head to merged `main` reports one merge commit ahead, zero behind, and no file differences. The implementation branch was automatically deleted after merge.

## Boundaries

ES-P03 remains authoritative for Java/Floodgate platform identity. Production/private representative network data, false-positive acceptance, distributed Java/Bedrock acceptance, production key rotation, deployment/cutover, and issue #43 remain excluded/deferred. ES-P02 and ES-P05 remain parked on their own unchanged blocker. ES-P10 remains READY and unassigned; this worker did not activate it.

## Finalization

A documentation-only post-merge PR publishes the terminal package/registry/workspace/handoff state. After that publication is verified on `main`, ES-P09 is terminal and this sequential worker stops.