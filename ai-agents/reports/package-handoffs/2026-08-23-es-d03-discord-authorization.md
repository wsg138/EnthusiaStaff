# ES-D03 Discord authorization — review handoff

Status: `REVIEW`.

## Scope result
ES-D03 establishes the authoritative domain authorization contract for staff moderation initiated through Discord. It adds explicit platform-scoped consequences, runtime-supplied Helper/Mod ceilings, permanent/custom gates, read/lifecycle/approval/overturn capabilities, self/equal-higher staff protection, external role/punishment-policy preconditions, and confirmation-time stale-state reauthorization.

Developer has Mod-equivalent Discord authority without inheriting Minecraft punishment authority. Discord roles and command origin never grant domain authority. Future callers must resolve authoritative actor and target-staff state before authorization and must satisfy every returned external precondition immediately before enforcement.

No Discord bot/API runtime, schema migration, website or competition work, production Discord configuration, private production data, deployment, LiteBans authority change, cutover, or issue #43 acceptance is part of this package.

## Revisions
- Starting `main`: `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`.
- Branch: `package/es-d03-discord-authorization`.
- PR: #149, open and non-draft.
- Earlier validated executable candidate: `ca66a97949cd8b9733c9039084d6230b2c63fd07`.
- Independent-review repair: `6f0b3a8c264f8f7644a9bfafacb8b6cd29061950`.
- Migration: none; live ceiling remains `V19__discord_moderation_persistence.sql`.

## Review findings and repairs
Three valid inline findings from the first CodeRabbit review were previously repaired: over-broad completion wording, incomplete workspace routing fields, and null consequence-element validation ordering. A prior independent boundary review also fixed the analogous null selected-platform input case.

A fresh independent full-diff review after resuming PR #149 found an additional valid authorization defect: the service publicly accepts an injectable `AuthorizationPolicy`, and the Discord-specific Developer prohibition originally depended on the default policy continuing to deny Minecraft actions. A permissive injected policy could therefore weaken the explicit Developer exception.

Commit `6f0b3a8c264f8f7644a9bfafacb8b6cd29061950` fixes this by denying Developer Minecraft mutations before the injected Minecraft policy is consulted. A regression test constructs an intentionally permissive `AuthorizationPolicy` and proves the Developer request still fails with `MINECRAFT_AUTHORIZATION_DENIED`.

The earlier two CodeRabbit body nitpicks remain non-defects under current type invariants unless new evidence changes that assessment. Live threads and bot/static results must be rechecked on the repaired candidate.

## Validation state
The earlier candidate `ca66a97949cd8b9733c9039084d6230b2c63fd07` passed Coverage workflow `32673402553`, Sentinel Restart Artifact `32673402584`, Codacy coverage finalization and CodeRabbit status, with zero unresolved inline threads at that time. Because executable code changed after those runs, they are historical evidence only and are not sufficient for merge acceptance.

The repaired candidate must pass every applicable exact-head repository gate. Record the new Java/build/test/coverage/runtime-artifact/static/review evidence after those runs complete. Any subsequent executable repair invalidates that evidence and requires another exact-head cycle.

## Collision and safety state
At package start `main` was `3c340d6333d7e25b33b2f2af1e32a5cc15d5ee4b`. PR #139 remains unrelated parked ES-X03 work. D03 touches no website, competition or migration implementation path. Fresh-check `main`, open PRs/branches, migration ceiling and review/check states immediately before merge.

No production Discord, private data, token, deployment, LiteBans authority or issue #43 cutover state changed.

## Exact next action
Validate the repaired exact PR head, inspect all CI/static/review results, harsh-review the full diff, fix every valid defect, then publish terminal `COMPLETE` state only if every gate is satisfied. Merge PR #149 by normal merge commit, prove feature-head containment/no unique work, clean the temporary branch when safe, and mark D04/D05 `READY` without starting either package.
