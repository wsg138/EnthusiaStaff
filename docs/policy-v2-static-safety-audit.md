# Policy v2 static safety audit (candidate snapshots)

Parent: [Policy v2 #355](https://github.com/wsg138/EnthusiaStaff/issues/355). Related: [OTHER provider #462](https://github.com/wsg138/EnthusiaStaff/issues/462), [owner blackmail distinction #468](https://github.com/wsg138/EnthusiaStaff/issues/468), [bound owner remedies PR #455](https://github.com/wsg138/EnthusiaStaff/pull/455).

`PolicyV2StaticSafetyAudit.inspect(PolicySnapshot)` is **read-only** and cannot alter, authorize, or deploy punishments. It returns only snapshot version and finding codes/IDs; it never surfaces private player evidence, player identities, or case contents.

The audit currently detects five classes of static cutover risk:

1. `remedy.unsupported-other`: an `OTHER` remedy cannot claim trusted completion through W3B's typed built-in enforcement. This includes the pending owner provider work for Market, Reputation, WorldGuard, and safety cleanup.
2. `remedy.missing-enforcement-binding`: a non-`OTHER` remedy lacks its versioned typed enforcement binding.
3. `blackmail.missing-real-world-evidence-fields` and `blackmail.ungated-punitive-rule`: for any punitive `safety.blackmail-extortion` rule, the offense must require enum `coercion-context` including game-only, uncertain and real-world, plus required boolean `real-world-leverage-verified`. Every punitive rule must explicitly restrict **both** to exactly `real-world` and `true`. Non-punitive review outcomes may remain broad. This contract must only be introduced in a **new owner-approved snapshot**, not by rewriting immutable snapshots in PR #455.
4. `blackmail.terminal-requires-admin-approval`: a permanently banning blackmail rule requires an explicit `ExactWithApproval` or `Bounded` action with minimum `ADMIN` or `FOUNDER` rank; direct `Exact` actions and `MOD`-approved terminal options are rejected, even when real-world evidence predicates are correct.
5. `language.network-ban-prohibited` and `language.invalid-chat-only-sanction`: the non-English public-chat offense cannot impose a ban (including via bounded options), a kick, or any other non-chat punitive consequence. Chat mutes must be temporary and **at most seven days**. Ordinary instant warnings remain permitted.

## Usage and acceptance

- Invoke the pure audit against a specific candidate `PolicySnapshot` during policy authoring/review and retain the finding codes in the private review artifact.
- A report with `passesStaticChecks() == false` indicates a **known** static blocker. A report with `true` means **only** that these defined checks passed. It does not certify any correction provider, factual reliability, real-world safety investigation, history mapping, staff authority, durable operational receipt, privacy projection, rollout plan, or rollback.
- Future provider work must extend the audit with verified typed/remedy resolution; do **not** suppress `OTHER` because a Markdown design contract or read-only preflight exists.
- Passing the audit does **not** change the feature modes; `disabled` / `shadow` remain non-authoritative and Policy v1 stays live. Any future authoritative mode needs an independent reviewed PR, shadow comparison, owner approval, deploy/restart approval and rollback proof.
- No CI action should deploy or change Policy v2 mode based on this report.

The test suite includes deliberately unsafe synthetic snapshots, verified-in-scope cases, broadened predicates, and bounded language-ban options. It intentionally avoids pinning to a historical number of unresolved issues, so owner policy refinement will not require rewriting old assertions.
