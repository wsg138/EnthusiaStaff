# ES-D06 — Read-only staff moderation UX

Status: `PLANNED`. Priority: 135. Depends on `ES-D04`, `ES-D05`. Internal package.

## Objective
Provide fast, secure read-only Discord moderation surfaces before destructive actions exist.

## Scope
`/moderate <user>`, user/message context commands, `/moderate-minecraft`, `/linked`, `/history`, notes/cases read views, target resolution by Discord/Minecraft IDs/names with ambiguity selectors, compact ephemeral profile panel, organized history filters, authoritative linked-staff actor resolution, component custom-ID signing/expiry/replay protection, permission-aware discovery and reauthorization.

## Exclusions
No warn/mute/kick/ban/restrict side effects, evidence mutation beyond safe read/context capture primitives, AutoMod or website work.

## Validation
Interaction/target-resolution/authorization/replay/privacy tests, stale-component denial, missing-link denial, Discord-only and Bedrock-linked subjects, full CI/review. Prove Discord roles alone cannot grant moderation authority.
