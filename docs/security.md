# Security model

## Trust boundaries

Players, chat, commands, plugin messages, website forms, uploaded media, Discord responses, configuration files, legacy LiteBans rows, and optional plugin metadata are untrusted. Paper servers, the Velocity proxy, Cloudflare Workers, and MariaDB authenticate separately with least-privilege credentials. A staff rank is not proof of authorization; every service method checks the action-specific permission.

## Authorization

- Commands, GUI clicks, automation, network messages, and website actions call the same application services.
- Mod and Developer cannot raise above policy or directly overturn. Admin can use approved custom durations/types. Founder-only recovery and override operations require an explicit reason and immutable audit.
- Inventory, economy, market, reputation, alt, and private evidence permissions are separate.
- Website staff roles are revalidated for every request; stale sessions cannot retain removed authority.

## Secrets and cryptography

- Secrets come from environment variables or platform secret stores and are never written to generated configuration, logs, commits, command output, or exception messages.
- Network identity equality uses HMAC-SHA-256 with a dedicated versioned key. Recoverable material uses authenticated encryption with a distinct versioned key and random nonce.
- Inter-server envelopes use a per-server HMAC key, canonical bytes, timestamp, nonce, message ID, server ID, and protocol version. Verification uses constant-time comparison. Expired timestamps, reused nonces, unknown servers, and unsupported versions are rejected before payload parsing.
- Punishment codes and password reset/verification tokens are generated from a cryptographically secure random source and stored only as slow or keyed hashes appropriate to the token type.
- Passwords use a maintained password-hashing implementation supported by the selected Cloudflare runtime; custom cryptography is forbidden.

## Database and network

- SQL is parameterized. Identifiers used in migration-source discovery are selected from an allowlist, never user input.
- Plugin database accounts cannot alter Flyway history. The website account can read only sanitized views.
- Pools, statements, result sizes, executor queues, protocol frames, and evidence payloads have hard bounds.
- The live channel is allowlisted and authenticated. No raw IP address or encrypted address blob is sent through routine moderation messages.
- MariaDB remains the durable source of truth; in-memory caches cannot authorize destructive work when stale.

## Website

- Secure, HttpOnly, SameSite cookies; rotating server-side sessions; origin checks and CSRF tokens on state changes.
- Turnstile, per-account and per-network-token rate limits, generic authentication errors, email verification, session rotation, and secure reset flow.
- Strict output encoding and a restrictive Content Security Policy. No moderation HTML is trusted.
- Uploads are private, size-limited, content-sniffed, extension-independent, stored under random object keys, stripped of metadata where supported, and retrieved only through short-lived authorized responses.
- Media access, punishment-code claims, appeal edits/claims/decisions, and staff review are audited.

## Privacy and logging

Raw IP addresses are absent from GUIs, Discord, public/private website payloads, normal logs, metrics labels, and audit detail. Errors use opaque correlation IDs. Reporter identity, private messages, coordinates, and internal evidence never cross the public view boundary. Retention and deletion apply to derived evidence while immutable privileged-action audit remains.

## Dependency and supply-chain controls

CI builds with pinned wrapper/toolchain versions, dependency verification, vulnerability scanning, secret scanning, and static analysis. Private Polar binaries are compile-only, unshaded, gitignored, and checked by signature/API compatibility at startup. Optional integrations are disabled on version mismatch rather than reflectively probing destructive methods.

## Secure failure

Authorization, signature, replay, revision, lease, snapshot, verification, or database failures deny the state change. Failure is recorded with a sanitized reason and surfaced through status/verification. Recovery never guesses whether a destructive side effect occurred; ambiguous operations are quarantined for a privileged decision.
