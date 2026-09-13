# ES-D16 — Moderation console real-data read bridge

Status: `COMPLETE`
Priority: 135.5
Implementation PR: #187
Implementation branch: `package/es-d16-moderation-read-bridge` — deleted/absent after merge
Owner-accepted D16 UI candidate: `3a79000eaa139ec107118d3fdb05b29e5e52097c`
Frozen reconciled executable head: `8811294c17532825aeae1d271fe2a3163042ba9c`
Final reviewed/validated pre-merge head: `aa32355a0d378ca4c6b03041b80d005df73f6fcd`
Normal merge: `848aba7ac6a115dc3723c034b281917d63f1f1bd`
Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-09-13-es-d16-complete.md`

## Terminal state

ES-D16 is complete. The owner accepted the moderation UI, current `main` was reconciled through a normal two-parent merge, every required exact-head repository/static/protected-staging gate passed, all valid review findings were repaired, PR #187 merged normally, exact feature-head containment was proven, and the temporary implementation branch is absent. No D07/D13 or unrelated package work was started.

## Delivered scope

D16 completes the read-only/simulation-only real-data moderation bridge between the browser, Cloudflare Worker, and StaffBot. The merged product includes:

- signed, session-bound read requests pinned to the fixed private moderation-read origin;
- explicit actor/guild/target authorization, replay protection, rate limiting, bounded request/response shapes, private/no-store responses, and public response allowlists;
- `/moderate-preview` with optional player context, channel/player navigation, bounded Discord message reads, same-channel surrounding context, reply previews, filters, paging, linked accounts, sanctions/history, cases, notes, and profile identity data;
- channel browse with no player selected until staff intentionally selects one;
- message evidence selection and non-destructive final review with arbitrary validated custom durations;
- Message Content entitlement fencing and no unnecessary Gateway intents;
- fixed private Cloudflare tunnel/origin staging transport and fail-closed signed-read behavior;
- transition support required to exercise the read bridge safely after D04 account-linking serialization.

D16 does **not** send punishments or DMs, mutate Discord permissions, delete messages, mutate punishment/case/note storage, enforce on Minecraft, change LiteBans authority, alter production Discord configuration, perform issue #43 acceptance, or cut over production moderation authority.

## Owner acceptance — PASS

The owner reviewed the current moderation UI and stated: `The UI looks good.`

That acceptance is bound to executable candidate `3a79000eaa139ec107118d3fdb05b29e5e52097c`. The commits from that candidate through checkpoint `a0bec2d4071ee46c8f55bea1ade7cb03cd021960` changed only `ai-agents` process/documentation records. The later required moving-main merge preserved D16 product paths while adding only already-current `main` runtime changes; those merged runtime changes were then covered by fresh exact-head automated validation.

No signed launch material, credentials, private message bodies, backend signatures, moderation records, or secrets are stored as acceptance evidence.

## Moving-main reconciliation

At final reconciliation, `main` was `06519c0c5acdcf6276278204201f3c8b20767805`. D16 merged it normally with two-parent commit `8811294c17532825aeae1d271fe2a3163042ba9c`, whose parents are prior D16 checkpoint `a0bec2d4071ee46c8f55bea1ade7cb03cd021960` and current `main` `06519c0c5acdcf6276278204201f3c8b20767805`.

Fresh collision review found no exact changed-file or Flyway migration collision. No rebase, squash, force push, migration rewrite, or concurrent-package takeover occurred.

## Final exact-head validation — PASS

Final pre-merge head: `aa32355a0d378ca4c6b03041b80d005df73f6fcd`.

- Coverage `34766165648` / job `103747432981`: **SUCCESS**. Java 21 checkout/build/tests, runtime-JAR generation and inspection, aggregate JaCoCo, validation-artifact upload, and Codacy coverage upload all passed.
- Validation artifact `10320537834`, digest `sha256:fa9b77ee77fec9e73c140d9cc02685da25c23f600be087ea83663f07279e06c5`.
- Moderation Web Validation `34766165643`: **SUCCESS**.
- Staff Bot PR Artifact `34766165650`: **SUCCESS**.
- Staff Bot Configuration Cache `34766165642`: **SUCCESS**.
- Sentinel Restart Artifact `34766165664`: **SUCCESS**.
- Pi Staging Supersession `34766164245`: **SUCCESS**.
- Protected Moderation Web Staging Deploy `34766163166`: **SUCCESS** on the exact head, including fixed private tunnel/origin provisioning, authenticated launch/session behavior, exact-origin CORS, signed direct-read behavior, unauthorized denial, signed replay rejection, one-time launch replay rejection, and simulation-only runtime verification. The synthetic probe queried no real player/message data.
- Codacy Static Code Analysis `103747616510`: **SUCCESS**, zero annotations / zero new valid findings.
- Codacy Diff Coverage `103748653603`: **SUCCESS**, 52.54%.
- Codacy Coverage Variation `103748653922`: **SUCCESS**, +0.03% against the -1.0% target.

The protected staging install reports four high-severity npm audit findings in the Wrangler development dependency graph. `moderation-web/package-lock.json` is byte-identical to the then-current `main` blob `8f1ff002ef318cee4ffb8351adab12d612a5054b`, so this is recorded as pre-existing dependency debt rather than a D16-introduced finding; it is not represented as fixed or suppressed.

## Review disposition

All substantive CodeRabbit correctness findings on PR #187 were fixed with regression coverage and all three visible inline threads are resolved. The final-head automatic CodeRabbit status said `Review skipped: manual review required for this OSS repository`; that skip is **not** counted as passing evidence.

A later D16 tracking edit attempted to make a fresh exact-head CodeRabbit re-review a terminal blocker. `VALIDATION-POLICY.md` expressly prohibits creating a new blocking acceptance requirement through later tracking edits. D16 therefore completed against the authoritative package/current-policy gates: harsh final review, every valid CodeRabbit/Codacy/CI finding resolved, zero valid unresolved review threads, and all required hosted/static/staging checks green.

## Merge, containment, and cleanup

Immediately before merge, `main` was re-read and remained `06519c0c5acdcf6276278204201f3c8b20767805`; PR #187 was clean/mergeable and head remained `aa32355a0d378ca4c6b03041b80d005df73f6fcd`.

PR #187 merged normally as `848aba7ac6a115dc3723c034b281917d63f1f1bd`. Its parents are pre-merge `main` `06519c0c5acdcf6276278204201f3c8b20767805` and exact feature head `aa32355a0d378ca4c6b03041b80d005df73f6fcd`. The merge tree and feature tree are identical at `c5c02a86d4db3861b4d9b7abfc2636323e9d9c12`.

Post-merge comparison from merge to feature reports feature `ahead 0 / behind 1 / files []`, proving exact containment and no unique implementation work. GitHub removed the temporary implementation branch; live branch search returns no `package/es-d16-moderation-read-bridge` branch.

## Routing after completion

`ES-D07 — Discord punishment enforcement` remains dependency-complete `READY` and lower priority than D16 now that D16 is complete. `ES-D13 — Discord role-sync replacement` remains `READY`. This worker does not activate or begin either package.

ES-X03 PR #139, ES-X01, website/competition/wiki/provider/hosting work, issue #43, and LiteBans authority remain separate and untouched.