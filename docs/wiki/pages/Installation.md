# Installation

This page describes **private staging installation and topology**, not permission to activate EnthusiaStaff as production authority.

## Before using this page

- Current product/release state: [[Implementation Status]]
- Integration/cutover readiness: [[Integrations, Migration, and Release Readiness]]
- Configuration: [[Configuration]]
- StaffBot operations: [[Staff Bot Runtime and Operations]]
- Website/web boundaries: [[Website and Web API]]
- Recovery: [[Recovery and Troubleshooting]]
- LiteBans migration: [[LiteBans Migration]]
- Shadow/cutover: [[Shadow Mode and Cutover]]
- Build validation: [[Build and Testing]]

## Requirements

Depending on the staging group being exercised:

- Java 25;
- supported Paper/Leaf/Purpur backend(s);
- supported Velocity proxy;
- MariaDB;
- separate normal/migration credentials;
- existing LiteBans source during migration tests;
- protected Paper–Velocity identity/TLS/HMAC material;
- required provider plugins;
- StaffBot Discord application/token/identity fencing for Discord staging;
- private StaffBot authority/read connectivity where those surfaces are under test;
- Cloudflare deployment/configuration for site/moderation-web tests where applicable.

Do not copy real production credentials or private topology into Wiki/PR evidence.

## Java runtime artifacts

The root `runtimeJars` task currently builds/verifies:

```text
paper/build/libs/EnthusiaStaff-Paper-<version>.jar
paper-authority-bridge/build/libs/EnthusiaStaff-AuthorityBridge-<version>.jar
velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar
staff-bot/build/libs/EnthusiaStaff-StaffBot-<version>.jar
```

Not every staging scenario deploys every artifact. The authority bridge is a narrow transition runtime. StaffBot is a standalone application rather than a Minecraft plugin.

Never deploy an artifact whose exact source revision/hash/validation evidence are unknown.

## Web components

Web surfaces are deployed separately from the Java runtimes:

- `components/enthusia-site/` — public site + Cloudflare Pages Functions;
- `moderation-web/` — staging-only Cloudflare Worker/static-assets moderation workspace.

The moderation web workspace must not be treated as a public punishment writer. See [[Website and Web API]].

## Representative staging topology

```text
Velocity + EnthusiaStaff-Velocity
├── HUB + EnthusiaStaff-Paper
└── SMP + EnthusiaStaff-Paper

Transition work (when explicitly required)
└── EnthusiaStaff-AuthorityBridge on its approved target

StaffBot
├── Discord Gateway/JDA
├── MariaDB
├── private authority boundary
└── private moderation-read boundary

Cloudflare/web
├── public enthusia-site
└── staging moderation-web
```

Keep backend inventory/world/player-data scopes distinct and treat private StaffBot service endpoints separately from public web ingress.

## Backups and rollback material

Before destructive/private staging, preserve the applicable combination of:

- MariaDB and LiteBans source data;
- current configuration and artifact hashes;
- TLS/signing/encryption material through approved secret handling;
- provider configuration;
- known-good Java/site release artifacts;
- component/deployment version identity.

Store backups outside live runtime directories and verify that the rollback material is actually usable.

## Safe staging sequence

1. Select one exact source revision.
2. Run complete Java/MariaDB validation and relevant Node/web checks.
3. Build/record hashes for the runtime artifacts actually used.
4. Record environment/provider/component versions and configuration checksum/identity.
5. Apply migrations to private staging MariaDB.
6. Start Velocity/Paper with production authority disabled as required by the test plan.
7. Verify schema, transport, identity and degraded/disabled-feature reporting.
8. Add providers one at a time, then validate the supported combined set.
9. If StaffBot is under test, start it with the approved staging identity/config contract and verify health before any interaction testing.
10. Keep destructive Discord enforcement disabled except during an explicitly authorized isolated staging exercise.
11. If moderation-web is under test, deploy through the protected workflow and verify signed launch/session/read/replay behavior.
12. Run the intended punishment/report/staff-state/linking/recovery/migration tests with disposable accounts/data.
13. Enter shadow/cutover-specific modes only when the prerequisite runbook says to do so.
14. Keep the current production authority unchanged until its explicit cutover gate is accepted.

“The process starts” is not a staging acceptance result.

## StaffBot staging

Build/run details live in [[Staff Bot Runtime and Operations]]. Key staging rules:

- use the staging Discord application/guild/channel fences;
- keep real token/signing material in protected secret storage;
- the supported file-backed mode requires the complete token/config-file pair and is staging-only;
- verify health before/after update/restart;
- keep destructive Discord enforcement default-off unless the test explicitly authorizes it;
- do not expose private health/read/authority listeners publicly for convenience.

## Website/API staging

Velocity website API, the public site component, and moderation-web are different boundaries.

Validate:

- public allowlisted routes separately from privileged appeal/reviewer routes;
- request authentication/replay/body/rate bounds;
- browser/session/CSRF behavior;
- StaffBot signed moderation-read boundary;
- unauthenticated/replayed request rejection;
- no private evidence/credentials in public responses or logs.

See [[Website and Web API]].

## Paper–Velocity transport staging

Each runtime needs the approved identity/allowlist/protocol/TLS/authentication settings. Exercise rejection and recovery cases such as wrong identity/certificate/version, stale/replayed messages, proxy/backend restart, long outage, queue recovery and operation with no online player.

See [[Protocol and Network Traffic]].

## Provider staging

For enabled providers verify compatible presence, missing state, incompatible/unavailable state, failure during use, reload/restart behavior and classloader/service discovery. A missing optional provider should disable only dependent behavior where safe.

For Discord managed roles, compiling `discord-platform-api` does not prove the StaffBot provider/consumer migration is complete.

## Shadow and authority rules

During LiteBans shadow:

- LiteBans enforces;
- EnthusiaStaff imports/mirrors/compares;
- EnthusiaStaff does not write LiteBans or enforce its calculated result;
- known-good rollback artifacts/data remain available.

Discord authority, DiscordSRV console/role-sync retirement and website public launch have separate cutover boundaries. Do not use one successful subsystem as authorization for another.

## Staging checkpoint record

Record:

- exact source revision;
- hashes for every deployed Java artifact;
- site/moderation-web revision/deployment identity where applicable;
- Java/Paper/Velocity/MariaDB/provider versions;
- StaffBot/JDA/Discord staging identity information at a non-secret level;
- configuration checksum/version and secret **names**, not values;
- schema migration version;
- topology and test scope;
- executed/skipped/unavailable groups;
- health/verification results;
- known blockers and rollback state.

## Legacy removal

Legacy plugins/authority are removed only after the corresponding cutover and accepted observation. No build, Wiki publication or automated migration step should delete production jars/data merely because replacement code merged.

## Related pages

- [[Implementation Status]]
- [[Integrations, Migration, and Release Readiness]]
- [[Configuration]]
- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Website and Web API]]
- [[Recovery and Troubleshooting]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]
- [[Build and Testing]]