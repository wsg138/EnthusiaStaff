# Latest AI handoff

Current package handoff:

[`2026-08-07-es-p11-fake-bases.md`](../package-handoffs/2026-08-07-es-p11-fake-bases.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P11 — Fake-base generation and cleanup` is the selected package and remains in `REVIEW` on PR #88 / `package/es-p11-fake-bases`.

This sequential worker resumed ES-P11 as the only live `ACTIONABLE_CONTINUATION`. ES-P02 PR #70 and ES-P05 PR #81 remain unchanged `PARKED_BLOCKED`; exactly one package is being worked. Legitimate `main` remained `68a6d936066383f5b8139304f40b2d01d0dfe036` at the latest pre-checkpoint reconciliation, issue #43 remains deferred, LiteBans remains authoritative, and ES-P11 adds no migration beyond immutable V18.

ES-P11 implements `AUD-TESTER-003` as a bounded client-only fake base with one-loaded-chunk placement, real-air/safe-floor/build-height checks, no chunk loads or real block writes, exact target/global/controller limits, target plus successful-Teleport staff viewer isolation, five-minute bounded lifecycle, expired-Extend refusal, idempotent authoritative cleanup, coordinate-free durable audit, and `/cheattester base` text fallback with explicit least-privilege permissions.

Manual review and actual CodeRabbit findings are repaired. Codacy remediation reduced static findings 23 → 9 → 5 → 1 → 0 while keeping lifecycle/ownership decisions in the manager and extracting low-level audit/world-view/presentation helpers. Product head `dafa710e44cc3c4ff7af1ee367d679f95ea8fd3f` passed Wiki `31239760132` / `93058657612`, full Java/MariaDB/Testcontainers/runtime-JAR/coverage `31239760133` / `93058683147`, artifact `9016711895`, Codacy static `93059044531` with zero annotations, Diff Coverage `93059225105`, and Coverage Variation `93059224975`.

The automatic private product-head run remains **NOT A PASS** and outside the ES-P11 completion gate: private run `31239763060`, Ubuntu job `93058666371` runner ID `0` / empty runner / `steps: []` / Billing & plans rejection, Pi `93058670370` skipped. Representative Java/Bedrock/distributed acceptance remains `ES-V02`.

This state publication becomes the final frozen candidate. Merge requires the full exact-head gate set on that unchanged SHA, including an actual CodeRabbit/reviewer completion with zero valid unresolved findings. Then merge PR #88 normally, verify the two-parent merge/resulting `main`/frozen-head containment/live result, delete the implementation branch only after those checks, publish terminal `COMPLETE` state through the documentation-only finalization pattern, recompute routing without activating another package, and stop.
