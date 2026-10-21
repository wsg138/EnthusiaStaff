# StaffBot private report notifications — isolated acceptance gate

Date: 2026-10-20. Scope: combined #648 → #649 → #654. **Not an authorization to deploy.**

## Baseline and safety boundary

The feature is **not registered** in the running StaffBot. The safe
template (#648), V33 reference-only `INERT` journal (#649) and isolated
dispatcher/JDA adapter (#654) exist as draft components, but there is no
owner-approved runtime activation, production sender registration,
or automatic channel delivery. Do not treat passing CI as consent.

Use a disposable MariaDB clone with backup/snapshot, and migrate in order: V32 for message provenance, V33 for inert intents,
and V34 for verified Discord receipt IDs. Existing live `reason-policies.yml` is customized: never silently
replace it. Keep `guided-punishment.enabled=false`. Use only generated,
synthetic report bodies and identities in the authorized private test
Discord channel; never copy a real player's report into that channel.

## Acceptance sequence

1. **Source/hash isolation**: pin the exact Git revision, verify the JAR SHA-256,
   record the private-channel test guild/channel/role IDs **outside repository
   source**, and confirm the user/owner authorized synthetic delivery there.
   Neither credentials nor real report descriptions may appear in CI artifacts.
2. **Schema**: restore a *disposable* database snapshot, migrate to V32/V33,
   verify the existing report/audit/webhook records are intact, and confirm
   all new journal entries begin `INERT`. No report body or evidence may
   exist in the intent table. Save backup/rollback results.
3. **Replay and follow-up**: generate one initial report, one merged follow-up,
   and replay the follow-up with the same idempotency key. Expect exactly
   two reference intents (`REPORT_CREATED`, `REPORT_MERGED`), no duplicates,
   and no `ARMED`/sent Discord action. Historical intents must not be
   auto-armed after restarting StaffBot or applying configuration.
4. **Authority**: with a synthetic, explicitly armed test-only intent,
   inspect exact guild, channel, configured role and current JDA member-override
   coverage. `@everyone`, unauthorized administrator/other roles, permission
   overrides, missing staff view, missing role, wrong guild or missing channel
   **all fail closed before report text retrieval**. Revoke role permissions
   after claim and again before delivery; no message may be sent.
5. **Plain text**: use malicious synthetic descriptions containing `@everyone`,
   user/role mentions, Markdown links, raw URLs, emoji, control characters,
   very long text and newline/command-looking strings. Verify the outbound
   `MessageCreateAction` has an **empty allowed-mentions set**. No active
   clickable masked links, pings, truncation ambiguity, or raw evidence.
   Only original report descriptions may be shown; staff notes and legacy
   unverified messages remain restricted.
6. **Sensitive cases**: privacy/safety/account/personal/sexual-harassment
   report families must be redacted in Discord with a route to the
   permission-gated in-game case view. Unknown reasons and missing names
   must render safely without claims of guilt.
7. **One confirmed send**: with owner-approved ephemeral staging setup only,
   verify the actual test-channel message, its Discord message ID,
   retained audit reference and a single terminal delivered state.
   Confirm subsequent polls/restarts do not duplicate the message.
8. **Ambiguous send**: introduce an artificial REST timeout after an HTTP
   request might have reached Discord. The lease must become
   `REVIEW_REQUIRED` at expiry or immediately if uncertain; **never**
   retry blindly. Human review determines whether a Discord message was
   received. Do not re-arm without an explicit audited policy.
9. **Recovery**: restart between lease and acknowledgement, revoke
   authorization mid-request, and simulate database unavailability.
   Neither new nor historical private text may be sent without a fresh
   complete authorization check. Restore the staging database from
   snapshot and confirm the production database was never touched.
10. **Release sign-off**: owner approves exact private guild/channel/roles,
    whether merged follow-ups alert and which sensitive report classes
    are permitted. Capture staging evidence, rollback/JAR hashes and
    cross-platform Paper/Leaf/Folia + Java/Bedrock acceptance separately.
    Until all gates pass, keep the dispatcher unregistered and the
    notification feature disabled.

## Current implementation limits

The test-only dispatcher does not schedule a worker or persist a Discord
message ID. Its JDA adapter confirms response receipt, but a production
registration cannot be approved until durable external-message audit and
a reviewed retry/reconciliation policy exist. CI unit mocks are not the
same as proving an end-to-end Discord permission boundary. The approval
decision must remain explicitly owned by the server operator.

## Current V34 and isolation release gates

The V34 schema records a validated, unique Discord message ID atomically
with DELIVERED. A null, malformed, duplicate or uncertain receipt must
not complete an intent. Synthetic tests must verify this and ensure no
notification is retried automatically after an ambiguous REST outcome.

The dispatcher currently quarantines REPORT_MERGED intents without
delivery because the exact-id read model can access the original report
description but not the merged follow-up body. It also rejects results
whose case ID differs from the claimed intent UUID, with no text sent.

The earlier read-only bloom-test1 audit observed production-identical
legacy DiscordSRV global, staff and console destinations. Its current
on-disk candidate was blocked in 3 configuration preflight checks:
Paper SHADOW disabled, TEST/global route mismatch, and populated legacy
console destination. Source synthetic preflight tests pass 11/11, but do
not prove the running test server is isolated. Do not send real staging
reports into Discord until the production-colliding routes are corrected
in a coordinated test-only change, current JDA permissions are verified,
and the owner-approved private test destination is independently proven
isolated from public/chat/console/role writers.

## Updated read-only test1/SMP exposure inspection (2026-10-21)

A fresh pinned-key, read-only SFTP comparison of
`bloom-test1/plugins/DiscordSRV/config.yml` and
`bloom-smp/plugins/DiscordSRV/config.yml` confirms:

| Safety observation | Current result |
|---|---|
| Legacy global and staff channel maps are identical | **YES - BLOCKED** |
| Legacy console destination identical | **YES - BLOCKED** |
| Test1 console destination still populated | **YES - BLOCKED** |
| Test1 Minecraft to Discord forwarding | disabled in on-disk config |
| Test1 Discord to Minecraft forwarding | disabled in on-disk config |
| Test1 console-command execution | disabled in on-disk config |
| Test1 Paper backend identifier present | yes |
| Test1 Paper `discord-chat-bridge` SHADOW section present | **NO - BLOCKED** |

These observations were derived without logging token or channel values,
reading Discord member data, sending Discord messages, running console
commands, or editing remote files. They represent **on-disk configuration**,
not proof that the running process has reloaded it. A prior test1 DiscordSRV
invalid-token error is not an acceptable substitute for channel isolation.
The independent real JDA bot identity, effective permissions, runtime
endpoints and safe rollback status also remain unverified.

Do not proceed to even a synthetic *network* message until the authorized
test1 operator resolves the legacy shared routes under rollback, chooses
the approved dedicated private StaffBot test channel/rank, and completes
the current-role/membership/permission checks. Tests using mock JDA and
disposable MariaDB are still allowed.

## 2026-10-21 read-only SFTP verification and case-state guard

Read-only pinned-SFTP reads of the currently configured bloom-test1 and
bloom-smp DiscordSRV settings confirmed all three destination classes
(global, staff, console) are still identical. Test1's legacy
Minecraft-to-Discord, Discord-to-Minecraft and console command forwarding
flags are currently disabled, but its console destination remains present
and points at the same production destination. The test1 Paper
EnthusiaStaff configuration has backend TEST, yet still has no
discord-chat-bridge SHADOW section. No secret value, actual destination ID,
or credential is recorded here. These are configuration observations only;
they do not establish runtime or current JDA permission state.

The private report template is independently restricted to report states
OPEN, CLAIMED and AWAITING_REVIEW; CLOSED, DISMISSED, RESOLVED, blank or
unrecognized states result in no content rather than a generic
Review label and stale original report text. Negative tests also show
that the unregistered dispatcher quarantines a case closed between initial
intent creation and the eventual read. These checks do not remove the
requirement for fresh destination/role verification immediately before send.

STOP: Do not use the shared test server for private Discord sends while
the documented channel collision and SHADOW activation gates remain open.
