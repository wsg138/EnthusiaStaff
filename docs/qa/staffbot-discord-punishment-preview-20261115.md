# StaffBot direct Discord punishment preview: verification and staging gate

**Status:** Source and offline test verification PASS; live Discord interaction acceptance NOT RUN; no production deployment authorization.

**Scope:** StaffBot direct `/warn`, `/mute`, `/kick`, `/ban`, `/restrict` and its built-in Discord moderation panel. The website's distinct prepare/confirm/status implementation and the Minecraft punishment GUI are outside this change.

## Source and build evidence (2026-11-15)

- GitHub branch: `fix/staffbot-punishment-result-20261115`
- Functional source/test head: `d06771d5e2c7baf4374c452a3b5f43d0a36c2788`
- Base: `ac6c323ce5eda0ff2ad04705936a256dbd01659a` (`main` at branch creation)
- Windows isolated checkout: `C:\Users\racec\dev\EnthusiaStaff-staffbot-punishment-preview-20261115`
- Offline Gradle command: `gradlew.bat --offline --no-daemon :domain:test :persistence:test :staff-bot:test :staff-bot:shadowJar :staff-bot:verifyStaffBotRuntime :integration-tests:compileTestJava`
- Result: **BUILD SUCCESSFUL**, 21 actionable tasks (five executed; 16 up-to-date). The new cancellation, issuer-scoping and notification-preview tests passed. Real Python AI API and POSIX-only tests were skipped by their existing platform/dependency conditions.
- Local artifact: `staff-bot/build/libs/EnthusiaStaff-StaffBot-0.1.1.jar`; **19,090,554 bytes**, SHA-256 `10dbea62fa7b2e17a786fd8e86eb3e2bbce5e195bc8e69e34ccff1f88fea0f31`.
- Gradle's `verifyStaffBotRuntime` passed executable manifest, required JDA classes, pinned Jackson 2.22.1 versions and excluded native audio class checks.
- Additional offline checks on the same checkout: `:paper:test :velocity:test` **BUILD SUCCESSFUL** (Paper results from verified Gradle cache; Velocity tests executed; one pre-existing Windows symlink case skipped). `:staff-bot:check` **BUILD SUCCESSFUL** including the AI-mirror crash/recovery probe; the probe intentionally prints a synthetic post-fsync exception before recovery succeeds. Gradle 10 deprecation warnings remain outside this feature.
- This JAR is a local candidate, **not a released or installed artifact**. Do not confuse its path with Bloom or the live bot container.

## What the flow actually guarantees

1. Preparation checks live staff authority and target preflight, then stores a short-lived, actor-bound, one-use confirmation token. It does not create a punishment.
2. The actor receives a clearly marked DM preview using the same player-notice formatter, with Confirm and Cancel controls and the ordinary appeal link. If DMs are blocked, the same preview appears privately in the original guild interaction.
3. Cancellation consumes the token without writing an intent. Confirmation rechecks current staff/target authorization and creates exactly one durable pending request.
4. The immediate confirmation response identifies `PENDING_APPLY` as queued, not applied, and offers a read-only issuer-scoped `Check status` button.
5. The worker later records enforcement and target DM outcomes. A warning is `COMPLETED` only if Discord accepted its target DM; failure or retry has a distinct durable state. Discord accepting a DM is **not proof the recipient read it**.
6. Status controls never reissue a punishment. Removal confirmations use the existing `/history` inspection path.

## Controlled staging acceptance: not yet run

Use a separate test guild/application and synthetic actor/target accounts where permitted. Do not use actual players or the production Enthusia Discord guild for destructive dry runs. Keep any public chat cutover, account-age gate, and automatic moderation effects disabled; back up staging configuration and capture exact artifact/checksum before the session.

| Case | Action | Required outcome |
| --- | --- | --- |
| DM-01 | Prepare each direct action type and panel action | Staff sees accurate reason, duration, target and player-style appeal link in DM; target receives nothing and no durable punishment exists |
| DM-02 | Block staff DMs, then prepare | Private guild-interaction preview appears; no public notice and no punishment |
| DM-03 | Cancel, then try stale Confirm | No punishment; stale/replayed token rejected |
| DM-04 | Wait past confirmation TTL, then Confirm | Expired token rejected without a punishment |
| DM-05 | Remove/revoke staff authority after preview | Confirmation rejected without an action; new rank not trusted from old preview |
| DM-06 | Another staff actor tries the Confirm/Cancel/Check status controls | No cross-actor issue, cancellation or history disclosure |
| DM-07 | Confirm once and click Confirm again | One durable intent; second attempt does not duplicate it |
| DM-08 | Inspect immediate and later `Check status` | Initially queued; eventual actual `COMPLETED`/`APPLIED`, retry or failure, and true target-DM outcome; no false success claim |
| DM-09 | Allow test target DMs, then issue a warning | Discord accepts target DM; warning reaches `COMPLETED`, no other Discord restriction applied |
| DM-10 | Deny test target DMs, then issue a warning | Warning is not falsely completed; `RETRY_APPLY` or `FAILED_APPLY` is observable |
| DM-11 | Exercise benign mock/staging reversible mute/restriction and removal | External action, original DM, removal state and removal DM outcome are independently visible; no production effects |
| DM-12 | Restart isolated staging bot across a pending request | Durable state and retries survive as specified; no duplicate external action or DM |

For every case record the exact commit, JAR SHA, staging application/guild IDs (privately), staff action time, status snapshots, redacted Discord screenshots, and final pass/fail. Failures must be fixed and rerun before requesting production approval. Never expose tokens, private evidence, full player inventories, or individual account DMs in public PR logs.

## Release gate and rollback

- Peer review the branch against current `main` and the active StaffBot/Discord work; do not merge solely on offline tests. GitHub Actions minutes were previously exhausted, so budget hosted CI deliberately.
- Obtain explicit owner authorization before deploying or restarting any connected bot. Use a dedicated staged setup for the first real Discord tests.
- Treat an unreachable DM preview, unknown punishment state or unresolved authorization mismatch as a rollout blocker; retain the currently installed bot JAR and config for rollback.
- Live user report: `/warn @pengu_sis` returned `PENDING_APPLY` on confirmation. That screenshot proves request acceptance **only**. Whether the target was actually warned must be verified from a current durable status/history record; the original screenshot cannot establish delivery.

## Separate open issue: `[inventory]` preview missing

This is Paper/InteractiveChat rich-chat transport work under issue #264 and draft PR #759, not an excuse to modify StaffBot punishment logic. The observed `interactivechat.module.inventory` permission was effectively `true` in the tested context, and `[item]` and `[ender]` artifacts worked. The exact installed TEST Paper lineage needs an approved, reversible diagnostic run to distinguish keyword/flag, capture, rendering, timeout or size failures. Do not change LuckPerms or deploy the TEST-only PR to production by inference.
