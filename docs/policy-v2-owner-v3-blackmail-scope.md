# Candidate owner.2026-10-07.3 — verified real-world blackmail boundary

Parent: [Policy v2 #355](https://github.com/wsg138/EnthusiaStaff/issues/355), owner classification [#468](https://github.com/wsg138/EnthusiaStaff/issues/468), policy owner discussion [#423](https://github.com/wsg138/EnthusiaStaff/issues/423). This change is intentionally **stacked on owner-bound remedy PR #455** and does not replace that review or authorize its merge.

## Preserved immutability and authority

- Keep the **entire** `owner.2026-10-07.1` and `owner.2026-10-07.2` records byte-semantically unchanged, including historic replay support.
- Append a candidate `owner.2026-10-07.3` by copying the full 85-offense `.2` configuration, changing **only** the blackmail offense's display name, required attributes and terminal rule predicate.
- Leave `mode: disabled` and `active-version: owner.2026-10-07.2`. This is an **inactive candidate**, not the current punishment policy. Policy v1 remains authoritative.
- The `PolicyResolver` continues to be deterministic. A mismatch or missing evidence routes to `REQUIRES_REVIEW`, not a made-up sanction. This does **not** mean ordinary game-only blackmail is a punishable finding.

## Proposed factual boundary

The owner allows threats involving ordinary Minecraft-only leverage, including demanding Minecraft items or threatening to disclose a Minecraft base or game strategy, **when the means and threatened consequences are limited to permitted gameplay**. A prohibited cheating/security/harassment act remains separately punishable on its own facts; permission for game-only blackmail is not permission to dox someone.

The severe `safety.blackmail-extortion` offense has **two required findings**, both recorded by authorized staff with private corroborating evidence:

1. `coercion-context` enum: `game-only`, `uncertain`, or `real-world`.
2. `real-world-leverage-verified` boolean: `true` only when factual review establishes a genuine real-world component (e.g., threatened doxxing, offline violence, intimate material, credentials, real money or exploitation), including threats made through in-game chat.

**Only** `coercion-context=real-world` **and** `real-world-leverage-verified=true` matches the existing Admin-authorized permanent network-ban terminal rule. Both dimensions are required independently; a checked verification flag attached to `game-only` or `uncertain` still cannot match. `false`, omitted attributes and uncertain context always resolve to review/no match. For ordinary game-only incidents, staff should decline selecting this offense rather than manufacturing a punishable incident.

## Remaining non-authoritative gates

- Staff workflow/GUI needs clear real-world versus game-only questions and must require appropriate evidence review; an approved factual classification cannot be inferred by text matching.
- Current `OTHER` safety-containment remedies and other external provider corrections remain unsupported, as tracked by #462 and draft audit PR #470. Retaining them in the candidate does not make provider writes available or justify automatic satisfaction.
- The actual severe safety policy, evidence privacy, appeal/reclassification, owner publication approval, shadow comparisons, and authorization-cutover review remain required. **Do not merge, deploy, activate shadow or alter the live server as part of this candidate.**

Regression source: `PolicyV2OwnerV3BlackmailScopeTest`, which loads the real bundled YAML, validates three retained versions and disabled mode, asserts Minecraft-only and uncertain findings fail closed, and verifies that only proven real-world coercion reaches Admin approval.
