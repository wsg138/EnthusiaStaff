# PR #84 — ES-P09 alt and network-identity handoff

Date: 2026-08-07
Package: `ES-P09 — Alt and network-identity completion`
PR: `#84`
Branch: `package/es-p09-alt-network-identity`
Base at selection: `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7`
Status: `ACTIVE — final review/validation in progress`

## Scope completed in the PR

- Protected-token alt matching remains address-private.
- Shared-network matching and sanction reads are bounded.
- Large shared networks suppress automated graph expansion.
- Simultaneous independent play lowers automatic confidence without overwriting manual decisions.
- The narrow authoritative new-account inheritance rule now requires an unambiguous single match; inherited sanctions preserve exact remaining expiry and remain idempotent.
- Duplicate evidence is rate-limited.
- Manual relationship reasons reject raw IPv4/IPv6 literals before durable audit storage.
- Sensitive identity tokens and detailed evidence have bounded batched retention; durable relationship decisions survive retention/restart.
- Retention no longer runs in the login transaction or derives its cutoff from an observation timestamp. `MariaDbRuntime` schedules retention from an injected trusted UTC clock, and the authority fence prevents non-authoritative deletion.
- Direct unit/MariaDB tests cover privacy, key-version isolation, ambiguity, concurrent proxies, sanction inheritance, retention/restart, and authority fencing.
- Wiki privacy/investigation guidance is updated.

## Review state

CodeRabbit identified four actionable threads after the first ready-for-review pass. Three were package-state bookkeeping corrections. The product finding correctly identified observation-timestamp-derived retention inside the login transaction; that flow was removed and replaced by trusted-clock runtime maintenance. Final exact-head validation and thread verification remain before merge.

## Boundaries

ES-P03 remains authoritative for Java/Floodgate platform identity. Production/private representative network data, false-positive acceptance, distributed Java/Bedrock acceptance, production key rotation, deployment/cutover, and issue #43 remain excluded/deferred. ES-P02 and ES-P05 remain parked on the unchanged private Actions Billing & plans zero-runner condition. No second package is active.
