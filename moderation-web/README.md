# Enthusia Staff moderation web

Staging-only Cloudflare Worker for the owner-approved moderation UX preview.

The browser UI is served from Cloudflare Workers Static Assets. Every asset request runs through the Worker first and requires a short-lived browser session. A SQLite-backed Durable Object provides strongly consistent one-time launch replay protection and bounded session state.

## Security boundary

- The Discord bot token is **not** deployed to Cloudflare.
- The staff bot derives a domain-separated SHA-256 launch signing key from its existing staging bot token.
- Staging deployment derives the same key inside trusted CI and uploads only the derived 32-byte key as the Worker secret `LAUNCH_SIGNING_KEY_HEX`.
- Launch tickets are HMAC-SHA256 signed, expire quickly, bind actor/guild/target, and are one-time at the Worker.
- Browser sessions use `Secure`, `HttpOnly`, `SameSite=Strict`, host-only cookies and server-side CSRF material.
- This preview contains no live punishment, Discord deletion, persistence mutation, Minecraft enforcement, LiteBans, or production authority adapter.

The canonical staging host is `https://staff-staging.enthusia.info`.

## Local validation

```bash
npm install --no-package-lock
npm run check
```

The build copies the existing moderation preview assets from `staff-bot/src/main/resources/moderation-preview` so the Discord package and Cloudflare deployment cannot silently drift to different UI files.
