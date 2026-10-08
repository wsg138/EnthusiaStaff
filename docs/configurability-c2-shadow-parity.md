# Configurability C2-C — rank-only shadow parity gate

Status: **read-only preflight implementation**, not active authority. Depends on C2-B `ranks.yml` (#463). Tracking: #459 and #425. Legacy authority remains in production code; C2-D enforcement migration has **not** begun.

## Exact comparison surface

`RankCapabilityShadowAudit.comparePlayerRanks(candidate, legacyRankOnly)` compares each typed capability for every real staff player identity: HELPER, MOD, DEVELOPER, ADMIN and FOUNDER. Each mismatch reports rank, capability, legacy allowed and candidate allowed. It cannot mutate or publish any configuration, run a command, modify a session, or grant a permission.

- `RankCapabilityShadowParityTest` uses actual `StaffModeAccessPolicy` and `StaffToolDefinition.VANISH.availableFor` rank decisions as the legacy oracle for game-mode choices, advanced tools, inventory edit, ender chest view/edit, combat testing and vanish hotbar eligibility.
- `VanishCapabilityPreviewParityTest` independently tests `VanishRankReconciliationPolicy.mayVanish`, including SYSTEM, null and Helper, and confirms that stale Helper vanish must be disabled across every session state.
- A deliberately modified candidate removing MOD's VANISH grant is **parsed successfully** but detected as three mismatches (MOD, ADMIN and FOUNDER through inheritance). Actual legacy permissions remain unchanged.
- SYSTEM has a historical low-level Spectator fallback, but it is **not a player staff identity**. The player-rank comparator excludes SYSTEM and asserts the candidate never grants it a player capability. The null/unresolved identity has no proposed grants.

## Limits and migration gate

These tests compare **rank-only eligibility**, not live authorization. A real allow/deny decision may also depend on LuckPerms/direct permissions, active duty, operational mode, player target and hierarchy, moderator approval, network reconciliation, and asynchronous durable state. This comparator is *not* wired to server execution or any reload action, and it must not be used as permission authority.

Future shadow work must cover nonrank context with explicit runtime evidence and rate-limited, private diagnostics without leaking player/staff identifiers. No runtime shadow instrumentation is introduced here. Any later enforcement rollout needs a separate, reviewed C2-D PR with verified downgrade/revocation behavior, fail-closed invalid/missing identity, worker/thread safety and rollback.

## Local verification

```text
:paper:test --tests net.enthusia.staff.paper.staff.RankCapabilityShadowParityTest \
            --tests net.enthusia.staff.paper.visibility.VanishCapabilityPreviewParityTest \
            --tests net.enthusia.staff.paper.config.RankConfigurationLoaderTest \
            --tests net.enthusia.staff.paper.config.VersionedConfigurationValidatorTest
```

The safe completion condition is **zero mismatches for the shipped preview**, while intentional draft deviations produce explicit nonzero mismatch records. Never auto-correct, auto-apply, or promote grants based on this report.
