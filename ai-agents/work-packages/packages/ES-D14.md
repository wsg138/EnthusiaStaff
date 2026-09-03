# ES-D14 — Public bot and sanitized public API

Status: `PLANNED`. Priority: 143. Depends on completed identity/persistence foundations and verified sanitized provider/API contracts. Separate public Discord application.

## Objective
Provide installable public information commands with a sharply isolated trust boundary from staff moderation.

## Scope
Sanitized public API and commands such as `/player`, `/whois`, `/guild`, `/baltop`, `/playtime`, `/leaderboards`, `/store`, `/website`, `/discord`, `/rules`, `/ip` using supported authoritative services/providers. Prefer HTTP interactions/Cloudflare Workers when Gateway state is unnecessary. Separate app credentials, rate limits, caching and abuse controls.

## Security boundary
The public bot must have no moderation DB credential, staff API credential, linked-alt/history/private-note/evidence access, or network path that turns compromise into privileged moderation access. `/ip` means the public server connection address, never player/network identity data.

## Validation
Public-data allowlist/schema tests, negative privacy tests, auth/network boundary review, provider outage/cache/rate-limit tests, install-in-arbitrary-guild behavior and full relevant CI. Treat public compromise as an explicit threat model.
