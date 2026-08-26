# ES-D05 — Staff bot runtime foundation

Status: `COMPLETE`. Priority: 134. Depends on `ES-D01`–`ES-D03` (all `COMPLETE`). Internal package.

## Completion record

ES-D05 was selected through the owner-authorized Discord-program lane and completed through Staff PR #160.

- Starting `main`: `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`.
- Implementation PR: #160.
- Final executable/review head: `936155cc356075aff10fd966de19e3d4bd8ca5f0`.
- Merge commit: `7bc8739bdc3f77db23c8b649f8c227f008162e47`.
- Merge parents: canonical pre-merge `main` `977216dc42a169a966c518545cc08cbc55617ebc` and exact final D05 head `936155cc356075aff10fd966de19e3d4bd8ca5f0`.
- Post-merge comparison from `936155...` to `7bc873...`: one merge commit, zero changed files, proving exact feature-head containment with no unmerged product delta.
- Temporary implementation branch `package/es-d05-staff-bot-runtime` is deleted after containment was verified.
- Canonical `main` Flyway boundary remains V19. D05 adds no migration and does not consume D04's unmerged V20.
- `ES-D04` remains independent on Staff PR #151 and was not modified, merged, rebased, or absorbed by D05.
- Production Discord changes, production deployment/data access, LiteBans cutover, and issue #43 acceptance remain unauthorized.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-discord-acceptance.md`.
Historical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`.

## Runtime identities and secret boundary

These identifiers are public/non-secret configuration:

- production application ID: `1541279426233376818`
- staging application ID: `1541279616881397772`
- Enthusia guild ID: `1410303324745371709`
- staff bot testing channel ID: `1541286004298752091`

D05 requests no privileged Gateway intents because this foundation layer does not consume member, presence, or message-content event streams. Discord credentials remain secret and are not committed, logged, copied into tests or tracking text, requested in chat, exposed in artifacts, or placed on command lines.

## Completed scope

The merged package adds the staff Discord bot as a third first-class Java 21 process isolated from Paper and Velocity, with no destructive moderation commands. The completed foundation includes JDA 6.5.0, staging/production identity fencing, Enthusia-only guild locking, staging test-channel view/send fencing, Gateway reconnect/backoff and REST rate-limit handling, bounded executors/queues, health/readiness, privacy-safe lifecycle logging, graceful shutdown, bounded read-only interaction replay protection, shaded runtime packaging/integrity verification, and a non-destructive `--smoke-test`. Existing webhook delivery remains separate.

Exclusions remain punishment side effects, rich moderation UX, AutoMod enforcement, public installable bot behavior, production deployment/configuration, DiscordSRV role-sync replacement, credential storage, and production cutover.

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
- [x] Obtain owner-authorized non-destructive staging Discord acceptance on the frozen product source.
- [x] Reconcile current `main` with normal merge history and obtain fresh exact-final-head executable validation.
- [x] Repeat the live Discord smoke on the exact final executable head after later executable review fixes.
- [x] Merge #160 normally, verify exact containment and cleanup, publish `COMPLETE`, and stop without starting D06.

## Original accepted product evidence

Original frozen D05 product head: `5f24ba1818c81e0a30a516fa70c8597586184b00`.

- Coverage/full Java 21 validation run `32874248685`, job `97888464396`: `SUCCESS`.
- Staff Bot Configuration Cache run `32874248800`, job `97888275507`: `SUCCESS` twice with configuration-cache problems treated as failures.
- Sentinel Restart Artifact run `32874248693`: `SUCCESS`.
- Validation artifact `9573547679`; digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`.
- Aggregate JaCoCo: 50.76% lines, 41.41% branches, 53.21% instructions.
- Codacy: zero new issues; 63.04% diff coverage; +0.17% coverage variation.
- CodeRabbit: successful after valid findings were repaired; all live inline review threads resolved.
- Canonical Pi public `32879118794` and correlated private `32880103099` / job `97907230239`: `SUCCESS`.

## Owner-authorized live Discord acceptance

The original external blocker was cleared by trusted staging run `32926306691`, latest attempt 3 / job `98071453002`, which fetched and executed exact source `5f24ba1818c81e0a30a516fa70c8597586184b00`. Exact-source fetch, Java 21 setup/requirement, runtime build/integrity verification, the non-destructive live smoke, and sanitized PASS publication all completed successfully.

Sanitized acceptance facts:

- staging application `1541279616881397772`: `PASS`;
- Enthusia guild `1410303324745371709`: `PASS`;
- required test channel `1541286004298752091`: `PASS` for view/send fence;
- readiness fence: `PASS`;
- smoke process exit: `0`;
- graceful close/shutdown path: `PASS`.

Because later valid review/static repairs changed executable Java code, the live acceptance was repeated rather than relabeling the older run. Trusted staging control merge `c544d6e3e800ee83fa0a7bb60f894e847c23ca3c` pinned exact final Staff source `936155cc356075aff10fd966de19e3d4bd8ca5f0`. Staging run `33006830844` / job `98302641683` completed `SUCCESS`: trusted runner identity, exact-source fetch, Java 21 requirement, exact runtime build/integrity verification, non-destructive live Discord smoke, sanitized PASS publication, process exit 0, and graceful shutdown all passed. Staging PR #116 then restored that workflow to manual-only without changing the exact source pin or identity/safety fences.

Neither smoke sent a moderation action or test message, changed Discord configuration, or accessed production moderation data.

## Exact final-head validation

For exact executable/review head `936155cc356075aff10fd966de19e3d4bd8ca5f0`:

- Coverage/full Java 21 run `33006430216`, job `98301256132`: `SUCCESS`; runtime jars built and inspected.
- Validation artifact `9620975030`; digest `sha256:0645ffb1fe1bc0163df8f8ace0903417d7b2fde910c6eca606af006ddac2c8d9`.
- Staff Bot Configuration Cache run `33006430238`, job `98301258008`: `SUCCESS`, including successful cache reuse.
- Sentinel Restart Artifact run `33006430207`, job `98301255587`: `SUCCESS`.
- Codacy Static Code Analysis: `SUCCESS`, zero annotations; diff coverage 62.74%; coverage variation +0.18%.
- CodeRabbit commit status: `SUCCESS`; all visible inline review threads resolved.
- Canonical Pi public run `33007222310`: terminal `SUCCESS`, including exact PR/source binding, trusted runtime build, private self-hosted staging verdict, transient-transfer cleanup, and terminal result publication.
- Exact-final-head live Discord run `33006830844`, job `98302641683`: `SUCCESS`.

## Merge and containment

Immediately before merge, live `main` remained `977216dc42a169a966c518545cc08cbc55617ebc`; PR #160 was ahead by 33 commits, behind by 0, and mergeable with merge base equal to that exact `main` head. The final diff was D05-only: the staff-bot runtime source/tests/build integration, its configuration-cache workflow, runtime documentation, and D05 state/handoff records.

PR #160 merged normally as `7bc8739bdc3f77db23c8b649f8c227f008162e47`. The merge commit has the exact validated D05 head as its second parent. Comparing `936155...` to `7bc873...` reports one commit and zero file changes, proving complete containment and no unique unmerged product work. The implementation branch is deleted.

## Terminal state

ES-D05 is `COMPLETE`. There is no remaining D05 implementation, validation, acceptance, merge, containment, or cleanup blocker. D04 remains active and independent, so D06 and D13 are not dependency-complete yet. This worker stops here and does not begin D06.