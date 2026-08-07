# ES-P09 alt and network-identity handoff

Date: 2026-08-07
Package: `ES-P09 — Alt and network-identity completion`
Worker: `ChatGPT sequential package worker`
Status: `COMPLETE`

## Selection and routing

- Legitimate aggregate `main` at selection: `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7` (merge PR #82).
- Temporary implementation branch: `package/es-p09-alt-network-identity`; automatically deleted after merge.
- Implementation PR: #84 to primary `main`.
- Frozen implementation head: `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`.
- Normal merge: `a88201524690848f778297f140f7ee2ba5b6ce36`.
- ES-P02 PR #70 and ES-P05 PR #81 remain `BLOCKED` / `PARKED_BLOCKED` on the unchanged private Actions Billing & plans zero-runner condition; they were not modified by ES-P09.
- ES-P10 remains READY but unassigned. This worker did not activate another package.

## Completed development scope

- Protected-token alt matching remains address-private; no raw-address lookup surface was added.
- Shared-network matching and active-sanction reads are bounded.
- Large shared networks suppress automated graph expansion.
- Simultaneous independent play lowers automatically derived confidence without overwriting staff-managed relationships.
- The narrow authoritative new-account inheritance rule requires an unambiguous single match; inherited sanctions preserve exact remaining expiry and are duplicate-safe.
- Duplicate evidence is rate-limited.
- Manual relationship reasons reject raw IPv4/IPv6 literals before durable audit storage.
- Sensitive protected identity tokens and detailed evidence have bounded batched retention; durable relationship decisions survive retention/restart.
- Retention runs outside login transactions from a trusted injected UTC clock and remains behind the operational authority fence.
- Direct unit and MariaDB/Testcontainers tests cover privacy, key-version isolation, ambiguity, concurrent proxies, sanction inheritance, retention/restart, and write fencing.
- Wiki privacy/investigation guidance was updated.
- No schema migration was needed; V17 remains current and immutable.

## Review and exact-head evidence

Frozen head `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`:

- Wiki/package validation: run `31193764800`, job `92916829444` — `success`.
- Java 21 build/tests, MariaDB/Testcontainers, aggregate JaCoCo, and runtime-JAR inspection: run `31193765341`, job `92916907616` — `success`. Build/test/JAR/coverage steps all completed successfully; failure-summary steps were skipped because no Java test failed.
- Codacy static analysis: check `92917176627` — `success`, zero annotations.
- CodeRabbit: repository status context `success`; four actionable review threads were resolved/outdated, including the trusted-clock retention correction; zero valid unresolved threads remained at merge. A later incremental review attempt was quota-limited and is not treated as completed review evidence.

## Private staging disposition

Private representative-network/staging acceptance is **not passed** and remains assigned to `ES-V02` by ES-P09's explicit package boundary.

On frozen head:

- public wrapper run `31193762319` / check `92916821249` dispatched private run `31193769314`;
- required Ubuntu build `92916864019` received runner ID `0`, empty runner name, and steps `[]`;
- GitHub reported the account Billing & plans payment/spending-limit restriction before any product build/test/boot step executed;
- Pi job `92916876057` was skipped.

This is infrastructure-unavailable evidence only, never a staging pass and never production acceptance.

## Merge, containment, and cleanup

- PR #84 merged normally as `a88201524690848f778297f140f7ee2ba5b6ce36`.
- Post-merge `main` is `a88201524690848f778297f140f7ee2ba5b6ce36`.
- The merge has parents `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7` and frozen implementation head `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`.
- Comparing frozen implementation head to merged `main` reports `ahead_by=1`, `behind_by=0`, and no file differences, so product containment is exact.
- `package/es-p09-alt-network-identity` no longer exists after merge; cleanup is complete.

## Boundaries carried forward

ES-P03 remains authoritative for Java/Floodgate platform identity. Production/private representative network data, false-positive acceptance, distributed Java/Bedrock staging, production key rotation, deployment/cutover, and issue #43 remain excluded/deferred. LiteBans remains authoritative until separately approved cutover work completes.

## Terminal handoff

ES-P09 development is complete. The only remaining action by this worker is publication of these terminal records through the documentation-only finalization PR, verification that those records reached `main`, then stop. Do not select or activate another package.