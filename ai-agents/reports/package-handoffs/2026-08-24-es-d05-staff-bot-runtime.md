# ES-D05 staff bot runtime foundation — blocked handoff

Date: 2026-08-25
Status: `BLOCKED` / `PARKED_BLOCKED`
Classification: terminal blocker publication
Package: `ES-D05 — Staff bot runtime foundation`

## Starting and preserved state

- Staff `main` at claim: `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`.
- Implementation branch: `package/es-d05-staff-bot-runtime`.
- Implementation PR: #160.
- Frozen validated product head: `5f24ba1818c81e0a30a516fa70c8597586184b00`.
- D01–D03: `COMPLETE`.
- D04 remains separate and unmodified by this worker; Staff PR #151 and private staging-control PR `wsg138/EnthusiaStaff-Staging#109` remain independent work.
- D05 owner identity contract PR #153 merged normally as `168145d76efb13ed15f21f8a31ece3e96f7b7c7b` before package claim.
- Canonical migration boundary on `main`: V19. D05 adds no migration.
- Issue #43 remains open; no production cutover authority exists.

## Runtime identity contract

- production application ID: `1541279426233376818`
- staging application ID: `1541279616881397772`
- Enthusia guild ID: `1410303324745371709`
- staging staff-bot testing channel ID: `1541286004298752091`
- bot tokens: secret; never commit, log, request in chat, place on command lines, or copy into test fixtures

## Library/security review

Official JDA `6.5.0` is the selected maintained Discord library. D05 excludes the optional voice/audio-native stack because no voice functionality exists. JDA owns REST bucket/global rate limiting and Gateway reconnect scheduling. D05 adds strict runtime identity fencing, bounded application work, health/readiness, callback generation fencing, and shutdown rules around it.

JDA 6.5.0 publishes Jackson 2.22.0; the implementation pins `jackson-core` and `jackson-databind` to 2.22.1 because CVE-2026-59889 is fixed there, and the runtime-integrity verifier checks those packaged versions. JDA 6.5.0 is also beyond the 6.1.3 fix for GHSA-93fv-4pm9-xp28.

D05 deliberately requests **no privileged Gateway intents**. An earlier candidate requested `GUILD_MEMBERS`, but review correctly identified that D05 consumes no privileged member/presence/message-content streams; the final frozen product head uses a no-intent `createLight` configuration with member cache/chunking disabled.

## Completed implementation surface

- isolated Java 21 `staff-bot` process/module and executable shaded artifact;
- explicit staging/production environment selection and token redaction;
- post-connect exact Discord application-ID validation and private-bot requirement;
- exact one-guild fence requiring only Enthusia guild `1410303324745371709`;
- staging channel fence requiring channel `1541286004298752091` in that guild plus `VIEW_CHANNEL` and `MESSAGE_SEND` permission;
- no test messages or Discord configuration changes;
- JDA-owned REST rate limiting and auto-reconnect with bounded maximum reconnect delay;
- generation-fenced asynchronous application-info callbacks so stale sessions cannot restore readiness or poison a newer session;
- bounded application worker queue with rejection accounting and daemon fallback;
- bounded read-only interaction replay guard, with durable database/domain idempotency explicitly required for later destructive work;
- loopback-only `/health` and `/ready` endpoints with privacy-safe payloads, no-store responses, method restrictions, and complete U+0000–U+001F JSON escaping;
- deterministic health/readiness transitions and fail-closed terminal behavior;
- graceful JDA shutdown with forced fallback and terminal timeout/interruption semantics;
- deterministic unit/lifecycle/reconnect/config/guild/channel/replay/worker/health/shutdown coverage;
- configuration-cache-safe custom Gradle runtime-integrity verification;
- executable shaded-JAR inspection for entry point, JDA presence, Jackson patch versions, no-audio class exclusions, full ZIP readability, size, and SHA-256 evidence;
- `--smoke-test` mode that waits for the full staging identity/guild/channel readiness fence, sends no messages, and exits nonzero on failure;
- operator documentation covering secret injection, health, identities, acceptance boundary, and production exclusions.

## Harsh review and repair history

A full current diff and the runtime/build/test surface were manually reconciled after automated review. No additional functional/security/lifecycle defect was found on the frozen product head.

CodeRabbit's substantive review identified three actionable items plus one low-severity build/configuration-cache concern:

1. remove the unnecessary privileged `GUILD_MEMBERS` intent;
2. escape every JSON U+0000–U+001F control character in the health response;
3. synchronize stale D05 package/workflow records;
4. move Gradle verification execution state into a custom task with task properties/providers so configuration-cache execution is valid.

Items 1, 2, and 4 were fixed before the frozen product head and have focused tests or exact workflow proof. Item 3 is resolved by this terminal state publication. The remaining generic CodeRabbit docstring-coverage warning is not a correctness/security/lifecycle finding or repository package gate, consistent with prior completed-package precedent such as ES-P06.

Codacy reports zero new issues on PR #160. No valid unresolved functional/security/lifecycle review finding remains.

## Frozen exact-head hosted evidence

Frozen product head: `5f24ba1818c81e0a30a516fa70c8597586184b00`.

Passing evidence:

- Coverage/full validation run `32874248685`, job `97888464396`: success on exact checkout `5f24ba1818c81e0a30a516fa70c8597586184b00`, Temurin Java 21.0.12, using `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- Full repository unit/integration suite and the new `:staff-bot:test` suite passed; `:staff-bot:verifyStaffBotRuntime` passed.
- Aggregate JaCoCo: 50.76% lines, 41.41% branches, 53.21% instructions.
- Hosted validation artifact `9573547679`, digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`.
- Codacy coverage upload/final notification succeeded for the exact frozen SHA; PR analysis reports zero new static issues, 63.04% diff coverage, and +0.17% coverage variation.
- Staff Bot Configuration Cache run `32874248800`, job `97888275507`: success on the exact frozen SHA. It ran `:staff-bot:check` twice with configuration cache enabled and `--configuration-cache-problems=fail`.
- Sentinel Restart Artifact run `32874248693`: success for the exact frozen SHA.
- Combined commit status records CodeRabbit `success` and Pi Staging `success` for the frozen SHA.

Superseded candidate failures remain non-passing history and are not reused. In particular, an earlier candidate used the Python-only `ZipFile.testzip()` name in the Kotlin verifier; it was corrected to a Java full-entry read-through before final freeze.

## Canonical public-to-private Pi evidence

The ordinary canonical Paper/Pi staging gate also passed for the frozen source:

- public Pi run `32879118794`: success;
- exact public Paper build and bridge jobs: success;
- correlated private run `32880103099`, job `97907230239`: success on trusted `Lincoln-PI-4`;
- provenance/artifact verification, guarded disposable Paper boot/restart, sanitized evidence publication, and cleanup all succeeded.

This evidence proves the repository's canonical Paper/Pi runtime path only. It is **not** a Discord staff-bot connection test and is not relabeled as one.

## Genuine external blocker

The D05 package contract requires a real non-destructive staging Discord connect/disconnect acceptance because the staging bot exists. The final smoke must prove, on the actual staging token and exact executable source/artifact:

- connected application ID is exactly `1541279616881397772`;
- the application is private/non-public;
- the bot is in exactly one guild and it is `1410303324745371709`;
- test channel `1541286004298752091` resolves in that guild and the bot has view/send permission;
- readiness becomes true only after that complete fence;
- disconnect removes readiness, a valid resumed/recreated session can revalidate, and shutdown completes cleanly;
- no message, punishment, configuration mutation, production action, or production data access occurs.

The implementation already exposes `--smoke-test` for this exact non-destructive check. However, exhaustive connected-state inspection found no authorized secret-bearing runtime path available to this worker:

- neither the public repository nor the connected private staging-control repository contains staff-bot smoke wiring or a discoverable token-injection path;
- the current trusted public/private Pi workflow is Paper-only and injects database credentials, not `ENTHUSIA_STAFF_BOT_TOKEN`;
- repository secret values are deliberately not inspected/exposed;
- adding a pull-request workflow that consumes a Discord bot token would weaken the trust boundary;
- modifying the independent staging-control lane solely to invent a credential path would collide with separate open staging-control work and still would not conjure an authorized token.

Therefore the missing real staging Discord smoke is an external acceptance blocker. It is not an ordinary CI wait and must not be bypassed. PR #160 remains open and unmerged with the validated implementation preserved.

## Exact unblock

An authorized owner/operator or trusted staging runtime must securely provision the **staging** token through a secret manager/runtime environment and execute the frozen product artifact/source `5f24ba1818c81e0a30a516fa70c8597586184b00` with `ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging` and `--smoke-test`. Do not put the token in Git, chat, logs, artifacts, issue comments, PR text, or the command line.

Publish only sanitized evidence containing exact source/artifact provenance and the pass/fail facts for the application/guild/channel/readiness/disconnect/shutdown contract. Then resume existing PR #160, re-reconcile live `main`, branches, review threads, and checks. If executable code has not changed, the frozen-product-head exception may preserve the existing exact executable evidence while later state-only commits receive their applicable docs/static validation. If executable code changes, re-freeze and rerun all applicable exact-head gates. Merge #160 normally only after the real staging smoke passes, verify containment/cleanup, publish D05 `COMPLETE`, and stop without starting D06.

## Systems not disturbed

- No D04 implementation or staging-control change was made.
- No D06 or second Discord package was started.
- No website, competition, X03, X04, webhook-delivery, or provider work was modified.
- No V1–V19 migration was edited; D05 adds no migration.
- No production Discord configuration, token, deployment, message, moderation side effect, private production data, LiteBans authority change, issue #43 acceptance, or cutover occurred.

Terminal routing: `ES-D05` is `BLOCKED` / `PARKED_BLOCKED` until the real staging Discord smoke exists. The implementation remains preserved on PR #160.
