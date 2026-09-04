# ES-D05 — Staff bot runtime foundation

Status: `COMPLETE`. Priority: 134. Depends on `ES-D01`–`ES-D03` (all `COMPLETE`). Internal package.

## Terminal state

Selected by the owner-authorized Discord-program worker on 2026-08-24 and completed on 2026-08-26 through implementation, review repair, hosted validation, real Discord staging acceptance, canonical Pi validation, normal merge, containment, and cleanup.

- Starting `main`: `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`.
- Implementation PR: #160.
- Temporary implementation branch: `package/es-d05-staff-bot-runtime` — deleted/absent after merge.
- Frozen reviewed D05 product head: `5f24ba1818c81e0a30a516fa70c8597586184b00`.
- Final synchronized/reviewed head: `936155cc356075aff10fd966de19e3d4bd8ca5f0`.
- Normal merge commit: `7bc8739bdc3f77db23c8b649f8c227f008162e47`.
- Post-merge containment: merge is one commit ahead of the feature head, zero behind, with zero file differences.
- D05 adds no Flyway migration. D04's migration/shared work remains independent.
- Production Discord changes, production deployment/data access, LiteBans cutover, and issue #43 acceptance remain unauthorized and were not performed.

Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-complete.md`.
Acceptance checkpoint handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-discord-acceptance.md`.
Historical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`.

## Runtime identities

These identifiers are public/non-secret configuration:

- production application ID: `1541279426233376818`
- staging application ID: `1541279616881397772`
- Enthusia guild ID: `1410303324745371709`
- staff bot testing channel ID: `1541286004298752091`

D05 deliberately requests no privileged Gateway intents because this foundation layer does not consume member, presence, or message-content event streams. Bot tokens remain secret and must never be committed, logged, copied into tests, requested in chat, exposed in artifacts, or placed on command lines.

## Objective and delivered scope

D05 adds the staff Discord bot as a third first-class Java 21 process isolated from Paper and Velocity, with no destructive moderation commands yet. Delivered scope includes JDA 6.5.0, staging/production identity fencing, Enthusia-only guild locking, staging test-channel view/send fencing, Gateway reconnect/backoff and REST rate-limit handling, bounded executors/queues, secret injection, health/readiness, privacy-safe lifecycle logging, graceful shutdown, bounded read-only interaction replay protection, shaded runtime packaging/integrity verification, tests/documentation, and the non-destructive `--smoke-test`. Existing webhook delivery remains separate.

Exclusions remain punishment side effects, rich moderation UX, AutoMod enforcement, public installable bot behavior, production deployment/configuration, DiscordSRV role-sync replacement, token storage, and production cutover.

## Completion checklist

- [x] Add isolated Java 21 `staff-bot` runtime module and executable shaded artifact.
- [x] Implement explicit staging/production configuration with secret redaction and exact application identity fencing.
- [x] Implement minimal Gateway intents, reconnect/backoff, Enthusia-only guild fence, and staging test-channel fence.
- [x] Add bounded application executor and bounded interaction replay/idempotency primitive.
- [x] Add local health/readiness endpoint and privacy-safe structured lifecycle logging.
- [x] Implement deterministic graceful shutdown and restart-safe lifecycle behavior.
- [x] Add unit/lifecycle/reconnect/rate-limit/shutdown/config/guild-fence/idempotency tests.
- [x] Update runtime packaging/integrity checks and operator documentation without changing the webhook subsystem.
- [x] Perform harsh full-diff review and resolve all valid review/static/CI findings.
- [x] Obtain exact-head hosted validation for the frozen product head.
- [x] Obtain the required non-destructive staging Discord connection acceptance with exact application/guild/test-channel evidence.
- [x] Complete fresh applicable exact-head validation after current-main executable reconciliation.
- [x] Merge normally, verify containment/cleanup, publish `COMPLETE`, and stop without starting D06.

## Frozen product evidence

Frozen D05 product head: `5f24ba1818c81e0a30a516fa70c8597586184b00`.

- Coverage/full Java 21 validation `32874248685` / job `97888464396`: `SUCCESS`.
- Validation artifact `9573547679`; digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`.
- Aggregate JaCoCo: 50.76% lines, 41.41% branches, 53.21% instructions.
- Staff Bot Configuration Cache `32874248800` / job `97888275507`: `SUCCESS` twice with configuration-cache problems treated as failures.
- Sentinel Restart Artifact `32874248693`: `SUCCESS`.
- Codacy: zero new issues; 63.04% diff coverage; +0.17% coverage variation.
- CodeRabbit: valid findings repaired and all visible inline threads resolved.
- Canonical Pi public `32879118794` and correlated private `32880103099` / job `97907230239`: `SUCCESS` on trusted `Lincoln-PI-4`.

## Required live Discord acceptance — PASS

Trusted staging workflow `wsg138/EnthusiaStaff-Staging` run `32926306691`, attempt 3 / job `98071453002`, completed successfully on trusted `Lincoln-PI-4` (`Linux/ARM64`). Trusted staging-control head `03b3fce61bffe552d7905a4e4aa18e3015ea4e00` pins exact frozen D05 source `5f24ba1818c81e0a30a516fa70c8597586184b00`, builds/verifies the runtime under Java 21 before secret scope, and executes the existing non-destructive `--smoke-test`.

Sanitized acceptance evidence:

- staging application `1541279616881397772`: `PASS`;
- Enthusia guild `1410303324745371709`: `PASS`;
- required test channel `1541286004298752091`: `PASS` for view/send fence;
- readiness fence: `PASS`;
- smoke process exit: `0`;
- graceful close/shutdown path: `PASS`.

The workflow sends no moderation action or test message and performs no Discord configuration change or production-data access. No bot-token value was inspected, requested, committed, logged, copied into tracking text, or placed on a command line. This acceptance is attributed only to the exact frozen product source it executed.

## Final synchronized exact-head evidence

Final synchronized/reviewed head: `936155cc356075aff10fd966de19e3d4bd8ca5f0`.

- Coverage run `33006430216`: `SUCCESS` on exact head.
- Staff Bot Configuration Cache run `33006430238`: `SUCCESS` on exact head.
- Sentinel Restart Artifact run `33006430207`: `SUCCESS` on exact head.
- Canonical public Pi staging run `33007222310`: `SUCCESS`, including exact-head binding, build/package verification, private dispatch, final verdict collection, transient-transfer removal, and terminal result publication.
- Correlated private staging run `33008160488`, job `98307232213`: `SUCCESS` on runner ID 2 `Lincoln-PI-4`; exact bridge verification, guarded disposable Paper boot/restart, sanitized evidence publication, and durable-evidence requirement passed.
- All visible inline review threads were resolved/outdated; no valid unresolved review thread remained.

## Merge, containment, and routing

PR #160 merged normally as `7bc8739bdc3f77db23c8b649f8c227f008162e47` with parents pre-merge `main` `977216dc42a169a966c518545cc08cbc55617ebc` and final feature head `936155cc356075aff10fd966de19e3d4bd8ca5f0`. No squash, rebase, force-push, or auto-merge was used.

Exact post-merge compare proves one commit ahead, zero behind, and zero file differences. The temporary implementation branch is absent, so no unique D05 work remains.

`ES-D04` remains an independent active package on PR #151. Therefore `ES-D06` and `ES-D13` are still dependency-blocked by D04 even though their D05 dependency is now satisfied. This worker does not start D06.