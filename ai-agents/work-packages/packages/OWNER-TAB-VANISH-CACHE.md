# Owner-directed TAB vanish-cache repair

Owner instruction: reproduce stale-cache player-list churn, preserve vanished-staff privacy, isolate backend TAB versus proxy Velocitab on TEST, and never change production.

Base: canonical main fc813a535a23eabfcb695b5e12a7e9d55c9fb676. Internal Staff component; no provider pin or migration change.

## Spec and acceptance

- During a delayed presence refresh, previously verified ordinary non-staff players shall remain visible if no previous vanish provider denies them.
- Unknown players, known vanished players, previously verified staff and currently promoted staff shall remain hidden while presence is stale or absent.
- Fresh presence shall retain the existing rank hierarchy, self visibility and previous-provider restrictions.
- Callback decisions shall read one immutable published presence generation and shall never access storage.
- TEST shall separately exercise delayed snapshot refresh and backend TAB list ownership. The production Velocity proxy shall not be used for activation of this unmerged fix.

## Tasks / evidence

Spec -> baseline regressions -> smallest visibility-policy repair -> atomic cache and provider-contract review -> focused/full checks and reviewable PR -> isolated TEST acceptance.

No project-local EARS/state executable helper has been found. This package, registry routing and handoff record actual evidence; no helper pass is claimed. Shared production-proxy activation is excluded. An isolated TEST proxy is required for live proxy acceptance; owner clarification is pending.

## Checkpoint

- Baseline ordinary-player expiry regression FAIL: expected not vanished, actual vanished. Actual baseline red XML and log retained privately.
- Fixed callback reads one immutable verification generation. Only verified ordinary non-staff may retain public visibility during staleness. Unknown/new players, known vanished, cached/current staff, identity-only staff, unrestricted staff and staff-mode subjects remain protected. Previous-provider denial and fresh hierarchy recovery covered.
- Permanent identity nodes are resolved before legacy rank nodes, matching the existing Paper/Velocity authority contract; no permissions granted or staff hierarchy changed.
- Local Java25 clean build, runtimeJars and all unit suites PASS. Integration-tests execution excluded because no local Docker runtime is available; no MariaDB acceptance pass claimed. Focused Velocity reproduction/matrix PASS; live TEST proxy/client reproduction NOT RUN.
- TEST5d109214 config backed up privately. Only TAB global-playerlist.enabled changed true -> false; TAB reload confirmed successful at20:53:38 EDT. Zero players connected. Proxy25319956 and SMP41f458f0 were not modified. This isolates competing membership ownership for subsequent client acceptance; it is not evidence of a visual A/B pass.
- Open PR476 overlaps bridge/presence files with a separate staff-mode privacy policy. That policy was not imported or overwritten; this PR must be reconciled if either lands first.
- No project-local EARS/state helper; lightweight record only. Full-diff security review preserved closed startup, hidden staff, prior provider restrictions, immutable generation publication and existing public-count staleness policy.
- Remaining: exact-head hosted CI/review, isolated TEST proxy and at least two test clients, delayed-refresh/recovery and separate TAB A/B matrix. Shared production proxy cannot be patched under the owner's boundary.
