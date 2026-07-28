# Threat model

## Assets

Authoritative sanctions and history, player inventories and balances, staff snapshots, network identity, private evidence, website accounts, appeal media, secrets, migration correctness, and the audit trail are protected assets.

## Threats and mitigations

| Threat | Required controls | Residual/operational check |
| --- | --- | --- |
| Staff privilege abuse | Service-boundary authorization, separate permissions, reason requirements, immutable audit, alerts for overrides | Periodic Founder audit review |
| Compromised staff account | Short sessions, revalidation, least privilege, action-specific permissions, anomaly/security events | External identity/MFA policy remains operational |
| Duplicate punishment or sanction | Idempotency key, unique constraints, locked target rows, inbox dedupe | Alert on repeated conflicting payloads |
| Partial punishment/removal | Atomic domain transaction and outbox, explicit operation state, verified acknowledgement | Quarantine if external state is ambiguous |
| Inventory duplication/deletion | Fenced per-player lease, authoritative slot revisions, before snapshot, exact dirty-slot mutation, verification | Live multi-viewer and login-race tests |
| Stale offline inventory overwrite | Network-wide offline proof, owning-server check, save-state check, optimistic revision, queued patch fallback | Direct edit disabled when any proof is missing |
| Economy over-removal | Authoritative total, exact plan, amount bound, locks, snapshot, final-total verification | Quarantine rather than retry unknown debit |
| Staff item/state leakage | Durable pre-state checksum, tagged temporary items, isolated staff inventory, verified restore | Recovery repeats until normal exit |
| Database tampering | Least privilege, append-only audit grants, Flyway validation, checksums, backups | External database access auditing |
| Website account takeover | Password hashing, verified email, secure reset, rate limiting, session rotation/revocation, CSRF and origin checks | Provider email-delivery account security |
| Punishment-code theft | High-entropy code, hashed storage, one verified-account binding, rotation/revocation, live eligibility recheck | Staff can revoke and audit claims |
| Appeal spam | Turnstile, layered rate limits, one appeal per punishment, verified accounts | Manual abuse review |
| Malicious upload | Private R2, strict size/type sniffing, random keys, metadata stripping, no active rendering, signed authorization | Malware scanning if volume/risk grows |
| Network identity leakage | HMAC equality token, authenticated encryption, distinct keys, sanitized errors and DTOs | Rotation drill and log scan |
| Discord webhook compromise | Secrets outside Git, four scoped destinations, bounded outbox, circuit breaker, sanitized payloads | Rotate webhook and replay selected events |
| Inter-server spoofing/replay | Allowlist, canonical HMAC, nonce cache, timestamps, message IDs, version negotiation, constant-time compare | Clock-skew and key-rotation tests |
| Migration corruption | Read-only source, external ID mapping, checksums/counts/decision comparison, resumable runs, 168-hour shadow, blocking cutover | Independent operator sign-off on mismatch report |
| Alt false positive | Confidence evidence, household/not-related states, no raw IP display, lower-confidence alerts, appeal path | Human review for consequential relationships |
| Restart/mass-reconnect false evidence | Maintenance epochs, mass-disconnect/reconnect suppression, durable server lifecycle state | Restart-system integration verification |
| Race during restart or reload | Intake gate, durable journals/outboxes, bounded shutdown, atomic config swap, lease fencing | Failure-injection tests at every stage |
| Chat bypass or overblocking | Pre-broadcast public-channel hook, exact normalized configured variants, no private-message automod, normal case review | Corpus false-positive tests |
| Vanish disclosure | Central visibility policy applied to tab, recipients, commands, effects, voice, and related APIs | Java and Bedrock observation tests |
| Freeze bypass | Paper event cancellation plus Velocity server-switch gate; durable reconnect state | Disconnect/reconnect and plugin-conflict tests |
| Polar metadata abuse | Treat all metadata as untrusted, bounded/sanitized fields, stable family mapping, API version gate | Automation disabled without event-capable API |
| Denial of service | Bounded queues/caches/frames/results, timeouts, backoff, circuit breakers, indexed queries | Load test at 100+ concurrent players |

## Abuse cases that must fail closed

- A client forges a higher staff rank or sends a GUI click after its session changed.
- Two staff members punish or edit the same player concurrently.
- A backend repeats an acknowledged message after reconnect.
- A target logs in while an offline inventory edit is between snapshot and replace.
- Currency or market state changes after a moderation plan is prepared.
- A LiteBans row changes during shadow or final incremental import.
- A website session remains active after role removal or code revocation.
- An upload claims to be an image but contains active or mismatched content.

These cases return a durable rejection or quarantine result and never a success message.
