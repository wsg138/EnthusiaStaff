# ES-D06 — Read-only staff moderation UX

Status: `ACTIVE`
Priority: 135
Depends on: `ES-D04`, `ES-D05`
Internal package: yes
Claimed: 2026-08-27 from canonical `main` `500136b37c9acc30b1de8a057feb79d3d16fc400`
Implementation branch: `package/es-d06-read-only-moderation-ux`

## Objective

Provide fast, secure read-only Discord moderation surfaces before destructive actions exist.

## Scope

`/moderate <user>`, user/message context commands, `/moderate-minecraft`, `/linked`, `/history`, notes/cases read views, target resolution by Discord/Minecraft IDs/names with ambiguity selectors, compact ephemeral profile panel, organized history filters, authoritative linked-staff actor resolution, component custom-ID signing/expiry/replay protection, permission-aware discovery and reauthorization.

## Exclusions

No warn/mute/kick/ban/restrict side effects, evidence mutation beyond safe read/context capture primitives, AutoMod or website work.

## Validation

Interaction/target-resolution/authorization/replay/privacy tests, stale-component denial, missing-link denial, Discord-only and Bedrock-linked subjects, full CI/review. Prove Discord roles alone cannot grant moderation authority.

## Active checkpoint

Live GitHub was reconciled before claim. D04 and D05 are complete; no Discord package branch or PR was active; D13 is dependency-complete but lower priority. Staff PR #139 / `package/es-x03-market-provider` is unrelated concurrent work and remains untouched. Canonical migration ceiling is D04's `V20__discord_account_linking.sql`; D06 allocates no migration. Issue #43 remains open and does not block dormant read-only implementation or authorize production cutover. No production Discord configuration, production data access, or secret handling is part of this package.
