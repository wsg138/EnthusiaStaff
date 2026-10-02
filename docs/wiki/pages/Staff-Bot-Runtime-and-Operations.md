# Staff Bot Runtime and Operations

The Enthusia Staff Bot is a **separate Java 21 runtime** that owns the privileged Discord Gateway/JDA connection for EnthusiaStaff. It is not a Paper plugin, a Velocity plugin, or a DiscordSRV extension.

Use this page when you need to build, run, configure, deploy, recover, or debug StaffBot. For product behavior and authorization rules, start with [[Discord Moderation Platform]]. For the browser moderation workspace and website APIs, use [[Website and Web API]].

## Quick answer

Current merged `main` includes a real `staff-bot` module and executable runtime. It provides the Discord gateway, staff moderation/read UI, linked-staff authority resolution, punishment execution/reconciliation support, health endpoints, private moderation-read API support, and signed launch support for the staging moderation web workspace.

**Important:** merged implementation does not by itself authorize production Discord moderation. Destructive Discord enforcement is intentionally fail-safe and remains disabled by default until the corresponding authority/cutover evidence is accepted.

## Runtime shape

```text
Discord
  |
  v
StaffBot (Java 21 / JDA)
  |-- linked staff identity + central authority checks
  |-- read-only moderation views and signed components
  |-- Discord punishment/reconciliation workers
  |-- private moderation-read API for the web workspace
  |-- health/readiness endpoint
  |
  +--> MariaDB durable Discord state
  +--> private EnthusiaStaff authority boundary
```

StaffBot is the intended single Discord Gateway/JDA owner. Other Minecraft plugins should use provider-neutral contracts rather than opening additional JDA sessions.

## Build and smoke test

Build the executable runtime from the repository root:

```bash
./gradlew --no-daemon :staff-bot:shadowJar
```

The executable JAR is written under:

```text
staff-bot/build/libs/EnthusiaStaff-StaffBot-<version>.jar
```

Run the non-network smoke test with:

```bash
java -jar staff-bot/build/libs/EnthusiaStaff-StaffBot-<version>.jar --smoke-test
```

Use the complete repository validation gate before treating a revision as a release candidate. See [[Build and Testing]].

## Startup and configuration

Normal runtime configuration is loaded by `StaffBotConfiguration` and related configuration classes. Configuration includes, at a high level:

- runtime environment and Discord application identity;
- allowed Enthusia guild and staging channel fences;
- MariaDB connectivity;
- bounded worker-pool/queue behavior;
- loopback/private health endpoint settings;
- staff moderation/read configuration;
- punishment/reconciliation configuration;
- private authority/read endpoints and signing configuration.

Do not put real bot tokens, database credentials, signing material, or private network details in the Wiki.

### File-backed staging startup

Merged StaffBot supports a staging-only file-backed startup contract:

```text
--token-file=<secret-mounted token file>
--moderation-config-file=<secret-mounted properties file>
```

The pair is fail-closed: do not mix partial file-backed configuration with the normal environment contract, and do not use the staging file-backed path to bypass production identity/configuration fences.

The token file contains one nonblank token value. The moderation configuration file is a Java properties file. Paths and token contents must not be exposed in ordinary logs or evidence.

## Enforcement safety

Discord enforcement is explicitly gated. The runtime configuration keeps destructive Discord enforcement disabled unless it is deliberately enabled for the accepted environment.

Key safety rules:

- Discord roles do not independently grant moderation authority.
- The Discord actor must resolve to an authoritative linked staff identity.
- Permissions/rank/target hierarchy are rechecked at action time.
- confirmation does not replace final reauthorization.
- durable intent/reconciliation state must exist where the workflow requires it.
- ambiguous external outcomes are verified/reconciled rather than blindly repeated.
- native Discord effects must not be reported successful only because an API request was sent.

See [[Discord Moderation Platform]] and [[Code Review Guide]].

## Health and readiness

`StaffBotHealth` and `StaffBotHealthServer` expose bounded health/readiness information for operators. Health should distinguish conditions such as startup/authentication, gateway readiness, database/authority problems, worker degradation, and shutdown without exposing credentials or private moderation data.

Keep health endpoints on the intended private/loopback boundary. Do not open a public listener just to make connectivity easier.

## Networking

StaffBot uses private service boundaries for moderation reads and authority checks. A Bloom/Pterodactyl TCP allocation does not automatically create a safe public API.

Use an approved private route or protected tunnel for the specific service that requires it. Public browser traffic for the staging moderation workspace terminates at Cloudflare; the protected read path back to StaffBot is separately authenticated and replay-protected. See [[Website and Web API]].

## Deployment and updates

The repository contains a StaffBot staging release path that builds a pinned executable, publishes/consumes the expected release artifact, and updates the authorized Bloom staging runtime through the protected workflow.

A safe update records the exact source/release artifact, checks health before and after the restart, and preserves a known-good rollback artifact. Do not hand-copy an untracked JAR and then treat it as release evidence.

The authoritative operator details live in [`staff-bot/README.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/README.md) and the protected staging workflows.

## Recovery

When StaffBot is unhealthy:

1. determine whether the failure is Discord gateway/authentication, MariaDB, private authority/read connectivity, worker saturation, or configuration;
2. preserve sanitized logs and the exact deployed artifact identity;
3. do not enable enforcement merely to test whether the bot responds;
4. if needed, roll back through the normal release path to the previous known-good artifact;
5. recheck health and outstanding durable reconciliation state before resuming destructive actions.

For general incident handling, see [[Recovery and Troubleshooting]].

## Important source paths

Runtime/composition:

- [`StaffBotApplication.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotApplication.java)
- [`StaffBotRuntime.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotRuntime.java)
- [`StaffBotConfiguration.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotConfiguration.java)
- [`JdaDiscordGateway.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaDiscordGateway.java)

Staff moderation/read UI:

- [`StaffModerationRuntime.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffModerationRuntime.java)
- [`StaffModerationController.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffModerationController.java)
- [`JdaStaffModerationListener.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaStaffModerationListener.java)
- [`LinkedStaffActorResolver.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/LinkedStaffActorResolver.java)

Punishment/reconciliation:

- [`DiscordPunishmentRuntime.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentRuntime.java)
- [`DiscordPunishmentService.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentService.java)
- [`DiscordPunishmentWorker.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentWorker.java)
- [`DiscordPunishmentAuthorization.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentAuthorization.java)

Web/read bridge:

- [`ModerationReadApiServer.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationReadApiServer.java)
- [`ModerationReadApiAuthenticator.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationReadApiAuthenticator.java)
- [`ModerationPreviewHostedLaunchIssuer.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationPreviewHostedLaunchIssuer.java)

## What is still separate/in progress

Current open development work must not be confused with the merged runtime. In particular, the DiscordSRV console replacement, broader evidence/case/alert workflows, cross-platform moderation expansion, and role-sync parity have separate active workstreams. See [[Discord Moderation Platform]] for the current status boundary.

## See also

- [[Discord Moderation Platform]]
- [[Website and Web API]]
- [[Architecture]]
- [[Configuration]]
- [[Build and Testing]]
- [[Code Review Guide]]
- [[Recovery and Troubleshooting]]