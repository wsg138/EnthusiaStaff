# Owner staging moderation UI preview handoff

## Terminal state

`COMPLETE` — owner-directed staging-only Discord moderation UI preview. This work is not ES-D07 and does not begin punishment enforcement.

## Identity

- Repository: `wsg138/EnthusiaStaff`
- Date: 2026-08-29
- Feature branch: `owner/staging-moderation-ui-preview`
- PR: #183 — `[Owner] Add staging-only moderation UI preview`
- Frozen validated product head: `35f67005d5d2927266a8c509a930069af58f2c89`
- Pre-merge `main`: `5d6011ba6f7a1435ec981e2c3b5550d8488cd635`
- Normal merge commit: `8940f09a8c1a99c04446952a584491e2c2aa9417`

## Delivered preview

The staging staff bot now supports `/moderate-preview` when both `ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging` and `ENTHUSIA_STAFF_BOT_UI_PREVIEW=true` are enabled. The preview uses deterministic in-memory sample data and includes overview/navigation, Warn/Mute/Kick/Ban/Restrict presentation, Discord/Minecraft/Both visual scope, preset/custom reasons and durations, options, confirmation, approval/rejection examples, failure/partial-result examples, owner-bound revisioned sessions, replay protection, TTL/capacity limits, and ephemeral Discord interactions.

Preview mode deliberately skips initialization of the D06 moderation database/authority runtime. It has no punishment service, destructive Discord moderation adapter, database mutation adapter, Minecraft/Paper authority adapter, LiteBans path, production data path, or other enforcement dependency. Final confirmation changes preview state only.

## Exact-head validation

The frozen product head `35f67005d5d2927266a8c509a930069af58f2c89` passed the required pre-merge gates:

- Java 21 Coverage/build/test workflow: run `33259336672` — SUCCESS;
- Staff Bot Configuration Cache: run `33259336697` — SUCCESS;
- Sentinel Restart Artifact: run `33259336682` — SUCCESS;
- Codacy Static Code Analysis check `99119301945` — SUCCESS, no issues;
- Codacy coverage variation and diff-coverage checks — SUCCESS;
- all live inline review threads resolved;
- canonical Pi staging public run `33259378492` — SUCCESS;
- correlated private staging run `wsg138/EnthusiaStaff-Staging` `33259838366` — SUCCESS.

The private staging run passed trusted runner identity, exact bridge artifact verification, guarded disposable Paper boot/restart, sanitized evidence publication, and durable evidence enforcement. The public run collected the private verdict, removed the transient transfer, and published the terminal exact-head PASS.

## Merge and containment

PR #183 merged using GitHub's normal merge method only. Merge commit `8940f09a8c1a99c04446952a584491e2c2aa9417` has parents:

1. `5d6011ba6f7a1435ec981e2c3b5550d8488cd635`
2. `35f67005d5d2927266a8c509a930069af58f2c89`

A post-merge comparison from validated product head `35f67005...` to merge commit `8940f09a...` reports zero file differences, proving the merge tree is exactly the validated candidate with no conflict-resolution or unrelated content added.

Concurrent work remained untouched:

- ES-D13 PR #178 remains open/draft on `package/es-d13-role-sync-replacement`, observed head `732ecb14ebefdf17b15a3eeabf5d28fe7a67f40c`;
- ES-X03 PR #139 remains open on `package/es-x03-market-provider`, observed head `702b13438fd95da235b4a87218901be04999aaea`;
- ES-D07 remains `PLANNED` in `ai-agents/work-packages/packages/ES-D07.md`;
- unrelated website, competition, provider, production, and secret/configuration work was not modified.

## Staging release

The merge-triggered `Staff Bot Staging Release` workflow run `33262789759` completed SUCCESS for exact merge source `8940f09a8c1a99c04446952a584491e2c2aa9417`. It rebuilt and republished the fixed `staff-bot-staging` prerelease and verified the published assets.

Published fixed assets include:

- `EnthusiaStaff-StaffBot.jar` — SHA-256 `b9a5e4bb83b85749613525ced5c60c6dc18c6968a18d9547aae2a92806cdde45` for the merge-triggered publication;
- `EnthusiaStaff-StaffBot.jar.sha256`;
- `staff-bot-staging-source.txt`.

The release workflow writes `source_sha=${TESTED_SHA}` into `staff-bot-staging-source.txt`; for the successful merge-triggered run, `TESTED_SHA` was the merge commit `8940f09a8c1a99c04446952a584491e2c2aa9417`. The release notes also record that exact source. Therefore the published runtime provenance contains the merged staging preview.

Fixed runtime download:

`https://github.com/wsg138/EnthusiaStaff/releases/download/staff-bot-staging/EnthusiaStaff-StaffBot.jar`

## Owner Bloom startup

The Bloom host must already provide `ENTHUSIA_STAFF_BOT_TOKEN` in its environment. Start the downloaded fixed staging JAR with:

```bash
ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging ENTHUSIA_STAFF_BOT_UI_PREVIEW=true java -jar EnthusiaStaff-StaffBot.jar
```

Do not put the token on the command line or in source control. The process reads `ENTHUSIA_STAFF_BOT_TOKEN` from the host environment.

## Owner verification

After startup, run `/moderate-preview` in the dedicated staging guild/test channel. The UI is expected to be ephemeral and fake-data-only. Selecting final confirmation must report that no moderation action was applied.

## Boundary after completion

This preview is a UX checkpoint only. ES-D07 remains a separate future package and must not be inferred to have started or completed from this work.
