from pathlib import Path

PATH = Path("reports/REQUIREMENTS-MATRIX.md")


def replace_once(text: str, old: str, new: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match, found {count}: {old[:120]!r}")
    return text.replace(old, new, 1)


text = PATH.read_text(encoding="utf-8")
text = replace_once(
    text,
    "Source of truth: `ENTHUSIASTAFF-GOALS.md` (read completely on 2026-07-27).",
    "Source of truth: `ENTHUSIASTAFF-GOALS.md` (read completely and updated for Helper and spectator-tab policy on 2026-07-30).",
)
text = replace_once(
    text,
    """Current root checkpoint: PRs #1 through #11 are merged, `main` is
`664792487e4fc1f9333957cd7f48e8a7f447c3b2`, and PR #12 is the active
confiscation-journal persistence checkpoint. The last exact hosted `main`
aggregate reports an A badge, 427 active findings, and 69 ignored findings;
PRs #11 and #12 are separately up to standards with zero new issues. A newer
exact aggregate for current `main` remains pending. These facts do not
establish production readiness.""",
    """Current root checkpoint: `main` is `66e2219a874ed61676df5287a1d83cc5ad6be860` after the Wiki checkpoint. PR #16 is the validated Helper/rank/spectator-tab authority slice at `1be3d7884cd244524d9ed0ef757a539de5be7ec6`, and stacked draft PR #18 is the active in-code command-authorization checkpoint. PR #16 passed the complete Java 21 and MariaDB/Testcontainers aggregate workflow and Codacy reported zero new issues. These facts do not establish production readiness; provider integration and production-like staging remain outstanding.""",
)
text = replace_once(
    text,
    "| Rank authority with Developer punishment read-only everywhere | EnthusiaStaff and enthusia-site | Domain, Paper commands/GUI, Velocity website bridge | PARTIAL | `domain/auth/DefaultAuthorizationPolicy.java`; `paper/auth/PaperActorResolver.java`; `paper/punishment/PunishmentCommandFilter.java`; `paper/sanction/SanctionChangeAccess.java`; `velocity/WebsiteApiServer.java` | `paper/plugin.yml` rank permissions | Root authorization, actor, command-filter, permission, and website-actor tests passed | ACTION_REQUIRED | Inspect every remaining mutation entry point, adapter, GUI, command, and site action for Developer denial while retaining read-only history and diagnostics. | The related website branch is absent and runtime boundary staging remains. |",
    "| Helper trial authority, Developer request-only separation, and approval boundaries | EnthusiaStaff and enthusia-site | Domain, Paper commands/GUI, Velocity website bridge | TESTED | `domain/auth/DefaultAuthorizationPolicy.java`; `domain/application/PunishmentService.java`; `paper/auth/PaperStaffRankResolver.java`; `paper/punishment/PunishmentCommandFilter.java`; `paper/sanction/SanctionChangeAccess.java`; `velocity/WebsiteApiServer.java` | `paper/plugin.yml` rank permissions | Authorization, actor resolution, permission inheritance, punishment approval boundary, GUI catalog, and website actor tests passed on PR #16 | ACTION_REQUIRED | Complete durable punishment-request persistence, approval/denial/expiry/alert workflows, exact-match external fulfillment, and related website controls. Continue explicit adapter/command boundary checks in PR #18. | The related website branch and production-like runtime staging are unavailable. |",
)
text = replace_once(
    text,
    "| Staff mode durable snapshot, restore, crash/reconnect, restrictions, and no item leakage | EnthusiaStaff | domain, persistence, Paper | PARTIAL | `domain/staff/*`; `persistence/JdbcStaffSessionStore.java`; `paper/staff/StaffModeManager.java`; `paper/staff/StaffStateCodec.java` | `plugin.yml`; modular `staff-mode.yml` absent | No focused staff-mode automated tests identified | ACTION_REQUIRED | Verify complete state snapshot/checksum/revision, crash resume, safe location/server restore, CombatLogX gate, rank Ender/creative rules, reload/disable behavior, and leak prevention. | Paper staging and failure-injection tests absent. |",
    "| Staff mode durable snapshot, restore, crash/reconnect, restrictions, and no item leakage | EnthusiaStaff | domain, persistence, Paper | PARTIAL | `domain/staff/*`; `persistence/JdbcStaffSessionStore.java`; `paper/staff/StaffModeManager.java`; `paper/staff/StaffModeAccessPolicy.java`; `paper/staff/StaffStateCodec.java` | `plugin.yml`; modular `staff-mode.yml` absent | `StaffModeAccessPolicyTest` covers Helper/Mod/Developer/Admin/Founder game-mode and tool boundaries; full lifecycle staging remains | ACTION_REQUIRED | PR #16 enforces explicit rank identity, Helper item/Ender restrictions, and required game modes even with accidental vanilla permissions. Verify complete snapshot/checksum/revision, crash resume, location/server restore, CombatLogX integration, reload/disable recovery, and leak prevention in staging. | Paper staging and failure-injection tests remain unavailable. |",
)
text = replace_once(
    text,
    "| Rank-aware vanish and central visibility coverage | EnthusiaStaff | Paper API/visibility | PARTIAL | `paper/api/StaffVisibilityService.java`; `paper/visibility/DefaultStaffVisibilityService.java`; `paper/visibility/VanishManager.java`; `persistence/JdbcVanishStore.java` | Paper visibility matrix; modular `vanish.yml` absent | No focused visibility hierarchy/integration test identified | ACTION_REQUIRED | Cover tab/counts/seen/completions/notifications/playtime/chat/voice/sounds/particles/containers/entities/APIs, staff-mode requirement, noclip, reload, and Java/Bedrock behavior. | Multiple external providers and staging unavailable. |",
    "| Rank-aware vanish, spectator tab masking, and central visibility coverage | EnthusiaStaff | Paper API/visibility | PARTIAL | `paper/api/StaffVisibilityService.java`; `paper/visibility/DefaultStaffVisibilityService.java`; `paper/visibility/VanishManager.java`; `paper/visibility/SpectatorTabPolicy.java`; `paper/visibility/PlayerInfoTabMasker.java`; `paper/visibility/ProtocolLibSpectatorTabPacketAdapter.java`; `persistence/JdbcVanishStore.java` | Paper visibility matrix; ProtocolLib soft dependency; modular `vanish.yml` absent | Visibility hierarchy, legacy matrix migration, spectator policy, player-info masking, field preservation, unauthorized removal, and fail-closed tests passed on PR #16 | ACTION_REQUIRED | Player-info tab masking and senior-staff clickable choices are implemented. Complete entity/tracker packet suppression and counts/seen/completions/notifications/playtime/chat/voice/sounds/particles/containers/public-API coverage; verify reload, Java/Bedrock, and actual client/mod behavior in staging. | Multiple external providers, Geyser/Floodgate clients, and production-like staging are unavailable. |",
)
text = replace_once(
    text,
    "| Commands, namespaced fallbacks, permissions, and `/estaff verify full` | EnthusiaStaff | Paper and Velocity command registration | PARTIAL | `paper/command/*`; `paper/EnthusiaStaffPaperPlugin.java`; `velocity/EnthusiaStaffVelocityPlugin.java` | `paper/plugin.yml`; Velocity plugin metadata in source annotation | Permission/config tests | ACTION_REQUIRED | Add missing `/history`, `/alts`, `/alt`, `/fakebase`, full recovery/status controls, command conflict ownership inspection, namespaced fallbacks, concise/debug verification, and registry/staging tests. | Several authoritative commands are not registered. |",
    "| Commands, namespaced fallbacks, permissions, and `/estaff verify full` | EnthusiaStaff | Paper and Velocity command registration | PARTIAL | `paper/command/*`; `paper/EnthusiaStaffPaperPlugin.java`; `velocity/EnthusiaStaffVelocityPlugin.java` | `paper/plugin.yml`; Velocity plugin metadata in source annotation | Permission inheritance/config tests plus explicit command-gate tests in PR #18 | ACTION_REQUIRED | PR #18 adds in-code defense-in-depth for staff mode, vanish, freeze, inventory viewing, and inspector entry/completion while preserving domain checks on punishment/removal and public `/report`. Add missing `/history`, `/alts`, `/alt`, `/fakebase`, recovery/status controls, conflict ownership inspection, namespaced fallbacks, and registry/staging tests. | Several authoritative commands are not registered. |",
)
text = replace_once(
    text,
    "| Automated tests, coverage thresholds, Docker MariaDB, failure injection, and CI | EnthusiaStaff and related repositories | All test modules and CI | PARTIAL | Unit/integration test trees; Gradle JaCoCo setup | Root Gradle configuration; no CI workflow identified | PR #12 exact-head clean build passed 148 tests in 53 suites, including 25 tests across eight MariaDB Testcontainers suites, with no skips | ACTION_REQUIRED | Add coverage aggregation/gates, missing critical-path/concurrency/property/failure tests, website tests, CI, dependency/secret scans, and targeted mutation tests. | No GitHub Actions workflow or production-like staging environment is present. |",
    "| Automated tests, coverage thresholds, Docker MariaDB, failure injection, and CI | EnthusiaStaff and related repositories | All test modules and CI | PARTIAL | Unit/integration test trees; Gradle aggregate JaCoCo setup; `.github/workflows/coverage-measurement.yml` | Root Gradle and GitHub Actions configuration | PR #16 exact-head Java 21 aggregate passed all unit and MariaDB/Testcontainers tests; line 30.23%, branch 25.42%, changed-line 31.64%, Codacy zero new issues | ACTION_REQUIRED | Add enforceable coverage floors after critical gaps are covered, dependency/secret scans, mutation tests, website/provider tests, and additional concurrency/crash/failure injection. | Production-like staging and several provider repositories remain unavailable. |",
)
text = replace_once(
    text,
    """## Immediate execution order

1. Complete PR #12 hosted Codacy and one lightweight
   CodeRabbit checkpoint after all valid findings are resolved.
2. Merge PR #12 and fast-forward `section/plugin` from the merged `main`.
3. Continue inventory-journal pending, quarantine, audit-helper, and
   responsibility-extraction work without changing transaction or recovery
   behavior.
4. Continue genuine Paper and persistence complexity and duplication cleanup
   without changing established behavior or hiding findings.
5. Maintain related Wiki pages incrementally, then reconstruct provider APIs
   after a reasonable root cleanup checkpoint.""",
    """## Immediate execution order

1. Keep PR #16 unchanged at its validated head and merge it only after explicit authorization.
2. Complete stacked PR #18 command-entry authorization, exact-head CI, Codacy, and review.
3. Implement durable Helper/Developer punishment-request persistence, approval/denial/expiry/alerts, and exact-match `FULFILLED_EXTERNALLY` closure as a separate transactionally tested slice.
4. Extend vanish from player-info masking to entity/tracker and integration-specific visibility, then stage with ProtocolLib, Leaf/Paper, Velocity, Geyser/Floodgate, and representative client mods.
5. Continue missing authoritative commands, modular configuration, provider API reconstruction, failure injection, and production runbooks without claiming readiness before staging evidence exists.""",
)
PATH.write_text(text, encoding="utf-8")
