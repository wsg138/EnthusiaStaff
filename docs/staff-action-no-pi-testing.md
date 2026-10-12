# Staff audit test plan — no Raspberry Pi

**Operational decision (2026-10-11): the Raspberry Pi will remain off. Do not start, enroll, dispatch to, or depend on it.** Canonical Pi staging runs and the broken private-bridge token are not prerequisites for this test track.

This is a **staging readiness plan**, not proof that the Minecraft→JSONL→MariaDB→Discord pipeline has been tested. The goal is to close [#488](https://github.com/wsg138/EnthusiaStaff/issues/488) using hosted CI plus one **independently isolated** Paper/Velocity/DB/Discord setup. See [staff-action-staging-acceptance.md](staff-action-staging-acceptance.md) for all action-by-action and failure-path cases.

## 1. Evidence already possible with the Pi off

- GitHub's existing Coverage workflow runs the Java tests and MariaDB integration jobs on ephemeral hosted runners, without the Pi.
- The Staff state reset runtime proof launches disposable hosted Paper instances with protocol clients and validates Staff Mode state transitions. It is not an end-to-end Discord staff-action acceptance.
- Sentinel's isolated Paper startup/restart evidence is separate from the above and does not show Discord delivery.
- The [staff-platform-staging release](https://github.com/wsg138/EnthusiaStaff/releases/tag/staff-platform-staging) publishes Paper/Velocity runtime JARs with SHA-256 files and `staff-platform-staging-source.txt` from `main`. **The release tag is mutable.** Always verify the manifest SHA, downloaded file hashes, and successful exact-source release run; never trust the tag alone.
- The Velocity `DiscordOutboxWorkerTest` now checks rendering and transmission of the stable `eventId` to an approved staging route, sanitization of forbidden fields, successful store acknowledgement and suppression of role pings on retries **with a fake store and fake transport**. These are not real Discord or MariaDB delivery tests.

## 2. Choose a genuinely separate test runtime

Use a disposable **non-Pi** Paper/Velocity environment, such as a spare staging-only instance on Bloom/Pterodactyl or a controlled local machine. If no spare environment exists, finish code/CI checks but mark interactive/E2E rows `NOT RUN`; do not repurpose the production SMP or its database to claim a staging pass.

Required isolation:

- A dedicated test Paper instance and matching Velocity/proxy where network behavior is in scope; correct compatible Java and Paper versions.
- A separate staging MariaDB schema and credentials; staging DB credentials must not have access to production databases.
- A private staging Discord channel/webhook, never a live staff channel, and a separate staging-only role (or no role pings). Secret values must remain in the staging environment's private secret store.
- Disposable test-player identities with HELPER and MOD permissions. No production punishments, real player audit replay, or permanent permissions changes.
- On the staging build, leave `authority.enabled=false`, `collector.enabled=false`, `alt-inheritance.enabled=false`, `network-identity.enabled=false`, and Policy v2 disabled except in separately reviewed dedicated experiments. Do not change DiscordSRV, production StaffBot, or the live Minecraft network.
- To exercise **only staging Discord delivery**, set `discord.enabled=true` in the isolated Velocity configuration; set `ES_DISCORD_ROUTE_ENVIRONMENT=STAGING`, `ES_DISCORD_STAGING_ALLOWED_HOSTS` to the staging approved hostname, and the **staging-only** `ES_DISCORD_STAFFMODE_WEBHOOK` value via secret injection. Verify the route before generating events. Other destination configurations must still satisfy Velocity's route matrix. Do not put webhook URLs or credentials in source control, test reports or chat.

If the available environment cannot safely satisfy all of these constraints, **do not attempt the live delivery test**.

## 3. Package, start and verify

1. Select a 40-character `main` SHA. Download all five release assets (Paper/Velocity JARs, their two `.sha256` files and the source manifest) from the staging-only release. Confirm that `source_sha` equals the selected commit; verify both downloaded SHA-256 hashes. Record sanitized run ID and manifest, not environment credentials.
2. Back up *staging-only* JARs/config/DB snapshot. Stop only the staging processes. Verify the embedded AuthorityBridge migration instructions and keep previous bridge-specific files for rollback.
3. Install the verified Paper and Velocity JARs into the isolated environment; start the staging MariaDB, then proxy/Paper. Confirm migrations and startup, private route resolution, and empty or controlled staging outbox state. Do not carry production player or historical logs into staging.
4. Execute [the action matrix](staff-action-staging-acceptance.md#2-action-and-lifecycle-matrix), starting with entry/exit, denied drop/swap, armor-stand and item-frame edits, and denied off-duty commands. Record PASS/FAIL/NOT RUN and test identity without dumping sensitive payload.
5. For each expected event, correlate `eventId` in the Paper local JSONL, one `discord_outbox` row keyed `staff-action:<eventId>`, and a private webhook message containing the same event ID. Compare permission effects, ensure no secret arguments or NBT leak, and check retry/duplicate behavior.
6. Only after the ordinary action matrix passes, test staging-only DB interruption, 429/5xx/timeout replay, outbox backlog and recovery. Discord delivery remains **at least once**; no exactly-once HTTP guarantee.
7. Roll back only staging artifacts if needed. Require explicit separate permission for any production deployment, restart, configuration change, Discord cutover, or Policy v2 enablement.

## 4. Owner-authorized low-impact live observations (2026-10-12)

The owner subsequently selected the **existing live SMP/Velocity server** for limited read-only and harmless-action validation rather than provisioning a spare server. This permission does **not** extend to the destructive/fault-injection tests in section 3. The independently isolated runtime above remains the requirement for simulating outages, 429/5xx, database failures, backlog pressure, and rollback.

Only after read-only verification of the installed Paper/Velocity JAR versions, current private route, and outbox health, an authorized operator may perform one or two low-frequency, reversible actions with a consenting staff test identity: staff-mode entry/normal exit and a benign audit-generating interaction. Correlate the sanitized timestamp and event ID through existing local audit journal, read-only DB outbox status and the already configured private staff log channel. If a component is missing, outdated, disabled, or routed unexpectedly, **record NOT RUN and stop** rather than enabling webhooks, changing permissions, promoting JARs, or restarting production to make the test work.

Do **not** perform production punishment/approval flows, synthetic alt-evasion logins, webhook retries/429/5xx, database outages, injected malformed journals, high-volume clicks, manual dead-letter requeue, config changes, DiscordSRV/Policy v2 cutovers or any production restart without a separate review and authorization. Never store raw player messages, staff private notes, credentials, or webhook tokens in public logs or issues. Read-only inspection alone is **not** a real Discord end-to-end PASS; every claimed delivered event must have independently observed, matching private-channel evidence.

Live remote access via Blackboard was intermittent/uninitialized at this checkpoint, so **no real server observation or live test has yet been executed**. Do not infer current production plugin versions from the mutable GitHub staging tag.

## 5. Acceptance status model

- **Code and artifact ready:** exact-source release hash verified, GitHub-hosted unit/MariaDB/runtime tests green.
- **Interactive test ready:** independently isolated Paper/Velocity/DB setup and staging-only webhook configured, backups saved, private channel confirmed.
- **Staff audit accepted:** every required row and JSONL→DB→private Discord correlation passes, retries/cleanup observed, and sanitized evidence recorded.

Never collapse these stages into one `passed` status. Without an isolated runtime or private webhook, stop at **code and artifact ready**.

**Current operator prerequisites:** For the narrowly authorized live observations, establish a reliable approved read-only view of the already running Paper/Velocity configuration, existing audit/DB status and private staff Discord evidence; do not paste secrets into chat. To run any outage, retry, failure-injection or restart test, separately provision a disposable non-Pi staging environment and dedicated private webhook/relay. Until either is available, mark those cases NOT RUN and continue hosted CI only.
