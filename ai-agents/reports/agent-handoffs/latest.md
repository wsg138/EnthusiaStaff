# Latest AI handoff

Current handoff:

[`2026-08-04-freeze-precise-world-interactions.md`](2026-08-04-freeze-precise-world-interactions.md)

Related PR:

[`#63 — Block precise world interactions while frozen`](https://github.com/wsg138/EnthusiaStaff/pull/63)

## Summary

| Field | Value |
| --- | --- |
| Work item | Close precise entity and resource-specific Paper interaction bypasses for restricted frozen players |
| PR | `#63` |
| Branch | `fix/freeze-precise-world-interactions` |
| Starting main | `1cf4277bdc6ec8f3e50c7db97f6fe99d9054db0f` |
| State | `ACTIVE — prior exact-head validation failures repaired; final unchanged-head validation and review resolution pending` |
| Implementation | Explicit handlers for precise entity interaction, armor-stand manipulation, harvesting, shearing and fishing; shared cancellation now uses Bukkit `Cancellable` |
| Tests | Exact handler presence, priority and cancelled-event metadata; direct restricted and ordinary-player event behavior; existing runtime-state tests prove unrestricted, pending, confirmed and fenced lifecycle decisions |
| Superseded evidence | Source `591324324b721c21b4c2b86f71501d0bc2210f59` failed Pi wrapper `30903529787`; staging run `30903538014` failed in trusted-runtime build job `91973196566`, so Pi boot/restart did not run. Later Coverage failures identified a deprecated event constructor and serverless Paper-registry fixture; both were repaired. None of those old heads is passing evidence. |
| Migration boundary | V16 is highest; PR #63 adds no migration; V1–V16 remain immutable |
| Commands, permissions, configuration | None changed |
| External provider blocker | RoseChat private-message evidence remains blocked pending the supported provider contract. Do not route it through issue #43. |
| Production boundary | No deployment, authority activation, LiteBans change, production Discord use, production access, migration repair or cutover |
| Next owner-priority workstream | Freshly select one remaining vanish or freeze restriction/lifecycle item only after PR #63 completes |

Final build, test, Coverage, static-analysis, Codacy, CodeRabbit, review-thread, Pi and merge evidence belongs in PR #63 live metadata and must bind to one unchanged head. Before merge, require terminal successful exact-head Pi evidence or direct evidence for the permitted GitHub Actions quota, billing, disabled-Actions or equivalent platform-unavailability exception. In that exception case, record that Pi did not run and do not claim it passed.

The next agent must first reconcile live GitHub and repository state, resume PR #63 rather than opening another branch, resolve every valid review finding, require the complete exact-head gate and stop after merge or a verified blocker. Do not edit V1–V16, use Flyway repair, deploy, access production data, alter LiteBans authority, begin issue #43 acceptance or combine another feature into PR #63.
