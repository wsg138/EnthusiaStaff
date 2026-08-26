# ES-D05 — Staff bot runtime foundation

Status: `BLOCKED` / `PARKED_BLOCKED`. Priority: 134. Depends on `ES-D01`–`ES-D03` (all `COMPLETE`). Internal package.

## Claim and terminal reconciliation

Selected by the owner-authorized Discord-program worker on 2026-08-24 and carried through implementation/review/validation on PR #160. On 2026-08-25 the package reached a genuine external acceptance blocker after all safe connected work was exhausted.

- Starting `main`: `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`.
- Implementation branch: `package/es-d05-staff-bot-runtime`.
- Implementation PR: #160.
- Frozen validated product head: `5f24ba1818c81e0a30a516fa70c8597586184b00`.
- `ES-D04` remains separate and was not modified. Its staging-control prerequisites `wsg138/EnthusiaStaff-Staging#108` and `#109` have since merged, and live D04 work is continuing independently on Staff PR #151.
- Canonical `main` Flyway boundary is V19. D05 adds no migration.
- Fresh library review selected official JDA `6.5.0`, with audio dependencies excluded because D05 has no voice scope. JDA owns Discord REST rate-limit handling and incremental Gateway reconnect/backoff; D05 adds bounded application work and fail-closed runtime identity/guild checks around it.
- Production Discord changes, production deployment, production data access, LiteBans cutover, and issue #43 acceptance remain unauthorized.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`.

## Runtime identities

The owner has created both private Discord applications and has already added the staging bot to the Enthusia guild. These identifiers are public/non-secret configuration and are intentionally committed:

- production application ID: `1541279426233376818`
- staging application ID: `1541279616881397772`
- Enthusia guild ID: `1410303324745371709`
- staff bot testing channel ID: `1541286004298752091`

Server Members Intent is enabled for the staging application, but D05 deliberately requests **no privileged Gateway intents** because this foundation layer does not consume member, presence, or message-content event streams. A later package must justify any privileged intent before enabling it.

Bot tokens remain secret and must never be committed, logged, copied into tests, requested in chat, or placed on command lines. D05 uses explicit environment/config selection so staging and production identities cannot be confused at runtime.

## Objective

Add the staff Discord bot as a third first-class Java 21 process isolated from Paper and Velocity, with no destructive moderation commands yet.

## Scope

Fresh implementation-time Discord library review; module/build/runtime packaging; Gateway lifecycle/reconnect/backoff; minimal intents; guild lock to Enthusia guild `1410303324745371709`; staging test-channel targeting to `1541286004298752091`; REST/rate-limit handling; bounded executors/queues; secret/config injection; staging/prod separation; health/readiness; structured privacy-safe logging; graceful shutdown; interaction idempotency primitives. Existing webhook delivery remains separate.

## Exclusions

No punishment side effects, rich moderation UX, AutoMod enforcement, public installable bot, token in Git, or production deployment.

## Implementation checklist

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
- [ ] Obtain the required non-destructive staging Discord connect/disconnect plus exact application/guild/test-channel evidence through an authorized secret-bearing runtime path.
- [ ] Merge normally, verify containment/cleanup, publish `COMPLETE`, and stop without starting D06.

## Frozen exact-head evidence

Frozen product head: `5f24ba1818c81e0a30a516fa70c8597586184b00`.

- Coverage/full Java 21 validation run `32874248685`, job `97888464396`: `SUCCESS`. Exact checkout, full build/tests, MariaDB/Testcontainers where applicable, aggregate JaCoCo, runtime JAR tasks, and `:staff-bot:verifyStaffBotRuntime` all passed.
- Aggregate JaCoCo: 50.76% lines, 41.41% branches, 53.21% instructions.
- Validation artifact `9573547679`; artifact digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`.
- Staff Bot Configuration Cache run `32874248800`, job `97888275507`: `SUCCESS`; `:staff-bot:check` passed twice with configuration cache and `--configuration-cache-problems=fail`.
- Sentinel Restart Artifact run `32874248693`: `SUCCESS` for the frozen head.
- Codacy PR analysis: zero new issues; 63.04% diff coverage; +0.17% coverage variation.
- CodeRabbit commit status: `SUCCESS`. Its valid runtime/build findings were corrected and regression-checked. The generic docstring-coverage warning is not a functional/security/lifecycle finding or repository package gate, consistent with prior completed-package precedent.
- Canonical Pi public run `32879118794` and correlated private run `32880103099` / job `97907230239`: `SUCCESS` for the frozen source and trusted `Lincoln-PI-4` Paper runtime/restart path. This proves the canonical Paper/Pi gate only; it does **not** substitute for the required staff-bot Discord connection smoke.

## External blocker

D05 acceptance explicitly requires a non-destructive real staging Discord connection using the staging application, followed by the exact application-ID, single-guild, test-channel visibility/send-permission readiness fence and clean disconnect/shutdown behavior. The implementation already provides `--smoke-test` for this and sends no messages or configuration changes.

Fresh reconciliation after staging-control PRs #108/#109 merged still finds no authorized secret-bearing execution path available to this worker. Code search finds no `ENTHUSIA_STAFF_BOT_TOKEN` or staff-bot smoke path in the current private staging-control repository and no public EnthusiaStaff workflow executes `--smoke-test`. Repository secret values are deliberately not inspected or exposed. Creating a new secret-bearing workflow would require a separately trusted provisioning path and would not prove that an authorized staging token is available to this worker.

This is therefore a genuine external blocker, not a CI wait and not grounds to weaken the acceptance gate. PR #160 and its validated implementation must remain preserved and unmerged.

## Exact unblock

An authorized owner/operator or trusted staging-control path must provision the **staging** bot token through a secret manager/runtime environment (never chat/Git/command line) and run the frozen product artifact/source `5f24ba1818c81e0a30a516fa70c8597586184b00` with:

```text
ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging
ENTHUSIA_STAFF_BOT_TOKEN=<secret injection>
java -jar EnthusiaStaff-StaffBot-<version>.jar --smoke-test
```

Record only sanitized evidence proving exact source/artifact provenance, successful readiness for staging application `1541279616881397772`, exact guild `1410303324745371709`, required channel `1541286004298752091` with view/send permission, and clean shutdown/disconnect. Then resume PR #160, reconcile live `main`/PR state, confirm the frozen product head is still the reviewed executable head (or revalidate if product code changes), merge normally, verify containment/cleanup, publish `COMPLETE`, and stop without starting D06.

## Validation rule

Missing staging credentials are never treated as non-applicability or a pass. The current package terminal state is `BLOCKED` / `PARKED_BLOCKED` until the real staging smoke evidence exists.
