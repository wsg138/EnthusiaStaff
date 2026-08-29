# Latest agent handoff

Current handoff: owner-directed staging-only Discord moderation UI preview on PR #183.

Canonical handoff: `ai-agents/reports/agent-handoffs/2026-08-29-pr183-staging-moderation-ui-preview.md`.

Expected state after merge:
- the existing staging staff-bot application can expose `/moderate-preview` only when `ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging` and `ENTHUSIA_STAFF_BOT_UI_PREVIEW=true`;
- the preview uses deterministic in-memory sample data and has no destructive moderation/persistence/authority adapter;
- the fixed `staff-bot-staging` prerelease is rebuilt from merged `main` and publishes `EnthusiaStaff-StaffBot.jar` plus checksum/source provenance;
- ES-D07 remains not started by this work.

Validation evidence location: PR #183 description/comments and live GitHub checks. The tracked handoff intentionally does not self-reference a final SHA.

Concurrency preserved: ES-D13 PR #178 / `package/es-d13-role-sync-replacement` remains independent; ES-X03 PR #139 and unrelated work remain out of scope.

Next step after this work: owner staging UX review/iteration only. Do not infer or begin ES-D07 from this handoff without a separate package run.
