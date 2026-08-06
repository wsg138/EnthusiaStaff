# EnthusiaStaff workspace state

Last updated: 2026-08-06

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed package | `ES-P01 — Exact-sanction appeal isolation` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-X05 — Website UX, authentication, and appeals` |
| ES-P02 status/classification | `BLOCKED` / `PARKED_BLOCKED` while its hosted/private runner condition is unchanged |
| Preserved ES-P02 work | branch `package/es-p02-runtime-db-recovery`, open PR #70, package-record head `80d4ea840f34017c09afb618f623581b31c6223d`; untouched by this worker |
| ES-X05 status/classification | `BLOCKED` / `PARKED_BLOCKED` at finalization |
| Preserved ES-X05 finalization | branch `package/es-x05-finalization`, open PR #74, head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`; untouched by this worker |
| ES-X05 implementation | aggregate PR #73 merged normally as current starting `main` `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`; standalone PR `wsg138/enthusia-site#2` merged as `b385f78c522f452cc48d78ed19fd2ee82573f64d` |
| ES-X05 remaining blocker | ordinary hosted exact-head Coverage gate for PR #74; do not rerun until material runner recovery evidence exists |
| Active package | `ES-P03 — Bedrock identity correctness` |
| ES-P03 status | `ACTIVE` |
| ES-P03 classification | owner-directed ready continuation under the narrow dependency-routing exception below |
| ES-P03 branch | `package/es-p03-bedrock-identity` |
| ES-P03 starting SHA | `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da` |
| Migration boundary | immutable V17 on aggregate `main`; V1–V17 must remain unchanged unless ES-P03 proves a new migration essential |
| Canonical handoff | [`2026-08-06-es-p03-bedrock-identity.md`](reports/package-handoffs/2026-08-06-es-p03-bedrock-identity.md) |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |

## Owner-directed routing exception

The ordinary execution graph requires ES-P02 to be complete before ES-P03. On 2026-08-06 the repository owner explicitly directed the next sequential worker to continue another productive package while leaving ES-P02 and ES-X05 parked until GitHub-hosted runners recover. No other ordinary implementation package was dependency-complete. The worker therefore selected the lowest-priority next implementation package, ES-P03, and records this as a narrow owner-directed routing exception.

This exception:

- does not mark ES-P02 or ES-X05 complete;
- does not merge, synchronize, or modify PR #70, PR #74, or their branches;
- does not import unmerged ES-P02 lifecycle/reload work into ES-P03;
- does not waive ES-P03 review, exact-head hosted validation, migration integrity, or merge gates;
- does not make any later package ready automatically; and
- requires later reconciliation if ES-P02 integration exposes a real source conflict or behavioral dependency.

## ES-P03 package boundary

ES-P03 owns verified Java/Floodgate platform observations, `*`-prefixed Bedrock current/history names, deterministic UUID/name resolution, duplicate and out-of-order identity writes, and the canonical identity fields later consumed by ES-P09. It excludes alt-graph confidence/inheritance, live Bedrock acceptance, provider invention, production data, and issue #43 work.

## Current known defects

- Paper mute enforcement records every joining player as `PlayerPlatform.JAVA`.
- Velocity records every backend connection as `PlayerPlatform.JAVA`.
- `JdbcPlayerDirectory` rejects the configured `*` Bedrock prefix and its prefix search rejects `*` aliases.
- Directory upserts can overwrite a stronger known platform with a weaker or incorrect later observation.

## Safety boundaries

No production credentials, Cloudflare secrets, punishment records, player records, raw addresses, or private database data may be committed or inspected. No deployment, authority activation, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, or cutover is authorized. Representative Java/Bedrock staging remains owned by `ES-V02`.
