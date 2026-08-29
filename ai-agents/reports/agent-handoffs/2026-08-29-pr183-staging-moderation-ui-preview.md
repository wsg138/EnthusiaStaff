# Owner staging moderation UI preview handoff

## Identity

- Repository: `wsg138/EnthusiaStaff`
- Date/timezone: 2026-08-29, America/Indiana/Indianapolis
- Work item: owner-directed staging-only Discord punishment UI preview; not an ES-D package
- Starting `main`: `5d6011ba6f7a1435ec981e2c3b5550d8488cd635`
- Branch: `owner/staging-moderation-ui-preview`
- PR: #183 — https://github.com/wsg138/EnthusiaStaff/pull/183

## Baseline

D06 was already complete and provided the isolated staff-bot runtime, fixed staging Discord identity fence, bounded worker/replay resources, and optional read-only database/authority moderation runtime. ES-D07 remained `PLANNED` and had not started. The owner requested a real Discord-native UX prototype before enforcement is implemented.

Concurrent ES-D13 work remained independently active on PR #178 / `package/es-d13-role-sync-replacement`. ES-X03 PR #139 and unrelated website/competition/provider work remained out of scope.

## Implementation

PR #183 adds an explicit `ENTHUSIA_STAFF_BOT_UI_PREVIEW` runtime flag. It is accepted only with `ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging`; production rejects the combination.

When preview is enabled, `StaffBotRuntime` deliberately skips initialization of the D06 moderation database/authority runtime. `JdaDiscordGateway` registers only a dedicated preview listener after the existing staging application/guild/test-channel identity validation succeeds.

The preview listener registers `/moderate-preview`, keeps responses ephemeral, and renders deterministic sample moderation data. The flow includes overview/navigation, punishment action, platform scope, reason/custom reason, duration/custom duration, options, confirmation, completion, and representative rejection/failure/partial-result appearances.

Preview state is bounded, in-memory, owner-bound, revisioned, TTL-limited, and replay-protected. Component identifiers are allowlisted/parsed and capped to Discord's custom-ID limit. The controller has no punishment service, Discord moderation REST adapter, database mutation adapter, Minecraft/Paper authority adapter, or external authority dependency. Final confirmation only transitions preview state to `COMPLETE`.

Dedicated-host instructions are tracked in `docs/staff-bot-staging-ui-preview.md`. The intended runtime requires only the staging bot token, `ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging`, and `ENTHUSIA_STAFF_BOT_UI_PREVIEW=true`; normal optional health settings retain their defaults.

## Harsh review

Merge blockers found and repaired before final freeze:

- the first Java 21 run found an existing test constructor compatibility break and a JDA union-type test API misuse; both were repaired without relaxing preview safety;
- Options back-navigation initially skipped Duration for duration-bearing actions; corrected;
- the central controller dispatch was split to keep orchestration complexity bounded;
- an unnecessary D06 lifecycle refactor was removed so non-preview D06 shutdown/readiness behavior remains unchanged;
- custom reason/duration modal completion was changed to edit the existing ephemeral panel instead of creating a second stale-looking panel;
- dedicated-host configuration and preview runtime construction receive explicit regression coverage.

At tracked-content freeze, no unresolved review thread had been reported. Live PR review state remains authoritative.

## Validation location

Exact final-head validation belongs in PR #183, not in this tracked report, to avoid changing the SHA after evidence is recorded.

Before merge, require on the exact final PR head:

- Java 21 repository Coverage/build/test gate;
- `Staff Bot Configuration Cache` gate;
- all other path-applicable repository workflows;
- Codacy Static Code Analysis with zero new valid findings;
- terminal review-thread reconciliation;
- live `main` and concurrent PR reconciliation;
- explicit diff review confirming there is no real moderation path and no ES-D13 overlap.

After merge, require the `Staff Bot Staging Release` push workflow for the exact merge/main source and verify the fixed `staff-bot-staging` release assets/source provenance.

## Intended merge or blocker state

Intended state is a normal merge commit only after exact-head evidence is terminal and current `main` is reconciled. Do not squash, rebase, force-push, or auto-merge. Delete the feature branch only after merge when safe.

## Boundaries

- ES-D07 remains not started by this work.
- No actual Warn/Mute/Kick/Ban/Restrict enforcement is implemented.
- No production Discord application or production Discord configuration is changed.
- No production database, Minecraft/Paper authority endpoint, LiteBans, production data, punishment persistence, or destructive Discord moderation action is used by preview mode.
- ES-D13 PR #178 and its branch/records remain independent and untouched.
- ES-X03 PR #139 and unrelated website/competition/provider work remain untouched.
- No secret is stored in the repository or handoff.

## Remaining work

The owner should run and visually evaluate `/moderate-preview` on the dedicated staging bot and iterate on UX as desired. ES-D07 remains a separate future package; this preview must not be treated as evidence that D07 has begun or completed.
