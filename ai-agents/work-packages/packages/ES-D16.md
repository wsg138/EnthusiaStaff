# ES-D16 — Moderation console real-data read bridge

Status: `MERGE_PENDING`
Priority: 135.5
Implementation PR: #187
Branch: `package/es-d16-moderation-read-bridge`
Frozen reconciled executable head: `8811294c17532825aeae1d271fe2a3163042ba9c`
Owner-accepted D16 UI candidate: `3a79000eaa139ec107118d3fdb05b29e5e52097c`
Canonical checkpoint handoff: `ai-agents/reports/package-handoffs/2026-09-13-es-d16-merge-pending.md`

## Scope

ES-D16 extends the completed D05/D06 Discord moderation foundation with the real-data browser/Worker/StaffBot read bridge and the bounded transition support needed to exercise that bridge safely. It does not grant destructive moderation, message deletion, LiteBans cutover, production Discord configuration changes, or issue #43 acceptance.

## Implemented product state

The D16 product delivers:

- signed, session-bound browser/Worker/StaffBot reads with explicit protected-route dispatch and fail-closed authorization;
- `/moderate-preview` with optional player context, channel/player switching, real Discord message/context/reply/attachment presentation, and real structured moderation-history/account reads;
- bounded Discord REST reads, server-side actor/guild/target binding, rate limiting, replay resistance, private/no-store responses, and a fixed private Bloom read origin;
- a Message Content entitlement fence with no unnecessary Gateway intents;
- independent evidence, violating-message, and future-delete-message selection;
- explicit public response allowlists and neutral loading/empty/error states;
- simulation/test-only destructive review. D16 does not send punishments or DMs, mutate Discord permissions, delete messages, mutate punishment/case/note storage, enforce on Minecraft, change LiteBans authority, or perform production cutover.

## Owner UI acceptance — PASS

The owner reviewed the current moderation UI and stated: `The UI looks good.`

That acceptance is bound to D16 executable candidate `3a79000eaa139ec107118d3fdb05b29e5e52097c`. Live GitHub provenance confirmed that the four commits from that candidate through documentation checkpoint `a0bec2d4071ee46c8f55bea1ade7cb03cd021960` changed only `ai-agents` Markdown/process records. The subsequent required moving-main merge preserved all D16 product paths from that accepted candidate; the additional runtime changes came only from already-current `main` and therefore require fresh automated exact-head validation, not a repeat of the same owner visual approval.

No signed launch material, credentials, private message bodies, moderation records, backend signatures, or secrets are recorded as acceptance evidence.

## Moving-main reconciliation — COMPLETE

Current `main` at reconciliation was `06519c0c5acdcf6276278204201f3c8b20767805`. It was merged normally into the D16 branch with two-parent merge commit `8811294c17532825aeae1d271fe2a3163042ba9c`:

1. prior D16 checkpoint `a0bec2d4071ee46c8f55bea1ade7cb03cd021960`;
2. current `main` `06519c0c5acdcf6276278204201f3c8b20767805`.

The live common-base comparison found 29 changed `main` paths and no exact file-path or Flyway migration collision with D16. The merge overlaid exactly those current-main blobs and no others. No rebase, squash, force push, or concurrent-package takeover occurred.

## Reconciled executable-head validation

Exact reconciled executable head: `8811294c17532825aeae1d271fe2a3163042ba9c`.

Already terminal green on that exact head at this checkpoint:

- Moderation Web Validation run `34765815458`: **SUCCESS**.
- Staff Bot PR Artifact run `34765815488`: **SUCCESS**; exact-head artifact `10320286377`, digest `sha256:9a92c853ffee5b13889dd03742e494326c6eaaa95ba0447325b6985d91d35a9e`.
- Staff Bot Configuration Cache run `34765815539`: **SUCCESS**.
- Sentinel Restart Artifact run `34765815467`: **SUCCESS**.
- Pi Staging Supersession run `34765813881`: **SUCCESS**.
- Codacy Static Code Analysis check `103746859043`: **SUCCESS**, zero annotations / zero new valid findings.

Coverage run `34765815457` was still executing when this checkpoint was prepared and is explicitly **not** counted as a pass here. CodeRabbit's automatic status on the merge head reported that manual review was required, so it is also **not** counted as terminal review evidence. Protected Moderation Web Staging Deploy must be refreshed for the final pre-merge head.

## Remaining terminal work

There is no remaining owner/live-UI blocker. The package is `MERGE_PENDING` while the same worker finishes the repository gates and normal merge:

1. freeze the final documentation-only pre-merge head without changing D16 runtime bytes;
2. require terminal Java 21 Coverage/build/tests and runtime-JAR inspection;
3. require Staff Bot executable-JAR, Configuration Cache, Moderation Web Validation, Sentinel, and Pi supersession evidence;
4. require protected Cloudflare staging deploy and its fixed private read transport checks on the final head;
5. require Codacy static analysis plus diff-coverage/coverage-variation checks;
6. request and receive a real CodeRabbit exact-head re-review; do not count an inappropriate automatic skip;
7. prove no unresolved valid review threads remain;
8. re-read `main` immediately before merge and reconcile again if it moved;
9. update PR #187 to terminal truth and merge it with GitHub's normal merge method using the exact expected head;
10. prove post-merge containment/health, clean safe temporary state, publish the `COMPLETE` handoff/status, and stop without starting ES-D07.

## Concurrency / exclusions

- ES-D13 PR #178 and ES-X03 PR #139 remain separate and untouched.
- ES-D07 is not started by this worker.
- Website/competition/wiki/provider/hosting/Market work is not absorbed.
- LiteBans remains authoritative and untouched.
- No production deployment/configuration/data access, destructive moderation, message deletion, issue #43 acceptance, or cutover is authorized by this package.