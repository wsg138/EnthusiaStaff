# Copy/paste prompt — Discord program package worker

Send the text below to a fresh ChatGPT/Codex worker. Do not add a package ID unless intentionally overriding automatic Discord-program selection.

---

Work on the owner-authorized Discord moderation program in `wsg138/EnthusiaStaff`.

Use the GitHub connector immediately. This is a **full package execution run**, not a planning/review-only request. Reconcile live GitHub, select exactly one eligible `ES-Dxx` package through the dedicated Discord program registry/protocol, and keep working on that package until it is genuinely `COMPLETE` or genuinely blocked by an external condition you cannot resolve. Do not stop after making a plan, opening a PR, reaching a checkpoint, or starting CI. Do not begin a second package in the same chat.

Read and obey, in order:
1. `ai-agents/AGENTS.md`
2. `ai-agents/WORKSPACE-STATE.md`
3. `ai-agents/work-packages/PACKAGE-REGISTRY.md`
4. `ai-agents/work-packages/WORKER-PROTOCOL.md`
5. `ai-agents/work-packages/EXECUTION-ORDER.md`
6. `ai-agents/work-packages/BRANCH-AND-MIRROR-POLICY.md`
7. `ai-agents/work-packages/VALIDATION-POLICY.md`
8. `ai-agents/STAGING-TEST-OPERATING-GUIDE.md`
9. `ai-agents/work-packages/DISCORD-PROGRAM-PROTOCOL.md`
10. `ai-agents/work-packages/DISCORD-PROGRAM-REGISTRY.md`
11. `docs/discord-moderation-platform.md`
12. `docs/implementation-plans/discord-moderation-platform.md`
13. the selected `ai-agents/work-packages/packages/ES-Dxx.md`
14. its latest handoff and all source/tests/migrations/contracts/docs relevant to that package.

The owner has already authorized sequential implementation of `ES-D02` through `ES-D15`. This invocation assigns you to the Discord program lane, so **select only among `ES-Dxx` packages** even if the global registry has unrelated ready work. Still inspect all global open work for path/schema/protocol conflicts. The owner may independently be changing website or competition code; do not conflict with, overwrite, or absorb those changes. If the next Discord package materially overlaps an active worker, classify that package as parked and select another dependency-complete Discord package when possible.

Before editing, confirm current `main`, recent commits, open/draft PRs, package branches, review threads, exact check states, highest Flyway migration, current docs/contracts, and whether an existing worker/PR already owns the package. Live GitHub overrides stale tracking text. Never create a competing PR for existing actionable package work.

For the selected package: claim/resume it, create/use the required temporary branch, open a draft PR after the first coherent checkpoint, implement the **entire** package including tests and documentation, preserve the authoritative EnthusiaStaff domain/database rather than creating parallel authority, and stay within the package exclusions. Follow Java 21, Paper/Leaf/Folia/Velocity lifecycle and thread-safety, bounded async MariaDB, transaction/index/revision correctness, idempotency/retry/restart recovery, permissions/hierarchy, privacy, Bedrock behavior, staging/prod separation, and failure-isolation requirements wherever applicable.

Do not invent APIs. Inspect current provider contracts first. Do not edit old Flyway migrations; fresh-check the highest migration and add only forward migrations. Do not expose secrets/private data. Do not deploy to production, alter production Discord configuration/data, disable LiteBans/native protections, or perform final cutover unless a separate explicit owner authorization satisfies the repository production policy.

After implementation, harshly review the complete diff. Fix every valid defect and every valid human/CodeRabbit/Codacy/CI/static finding. Freeze the product head and run/inspect every applicable exact-head gate. Missing, queued, cancelled, superseded, stale, merge-ref-only, or wrong-head checks are not passes. Continue through ordinary CI in this same run when the tools allow it; do not stop just to tell me that CI is running.

When all gates are satisfied, merge normally (never squash/rebase/force-push/auto-merge), verify resulting `main`, feature-head containment, no unique work, safe branch cleanup, migrations/parity where applicable, and publish the package registry/package/workspace/handoff final state. Mark newly dependency-complete Discord packages ready but **do not start them**. Then report the exact final evidence and stop.

If the package cannot be completed, exhaust every safe actionable step first, publish durable `BLOCKED`/`PARTIAL` state with exact evidence and unblock condition, and stop. Do not manufacture a blocker merely to end the run, and do not ask me questions that source/GitHub can answer.

---
