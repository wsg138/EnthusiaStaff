# Enthusia Staff moderation web

Staging-only Cloudflare Worker for the owner-approved moderation UX preview at `https://staff-staging.enthusia.info`.

Cloudflare Workers Static Assets serve the workspace only through the Worker. A SQLite-backed Durable Object provides strongly consistent one-time launch replay protection and bounded browser session/CSRF state. Direct unauthenticated workspace access is rejected.

## Security boundary

- The raw Discord bot token is **not** deployed to Cloudflare, sent to the browser, embedded in URLs, or intentionally emitted to logs/artifacts.
- The staging bot and protected deployment workflow currently derive domain-separated 32-byte launch- and read-signing keys from the staging Discord bot token. Cloudflare receives only those derived keys.
- Token-derived signing is explicit staging bootstrap debt. The long-term hardening path is one coordinated cutover to dedicated random signing secrets shared only by Bloom and the protected Cloudflare deployment environment.
- Launch tickets are HMAC-SHA256 signed, short-lived, actor/guild/target-bound, and one-time at the Durable Object.
- Direct moderation reads are independently HMAC-SHA256 signed, short-lived, request-body-bound, and replay-protected by the Bloom read API.
- Browser sessions use `Secure`, `HttpOnly`, `SameSite=Strict`, host-only cookies with server-side CSRF material.
- Public launch rejection does not expose internal validation or replay reason metadata.
- This preview contains no live punishment, Discord deletion, persistence mutation, Minecraft enforcement, LiteBans mutation, or production authority adapter.

The Discord staging bot runs on Bloom and only issues signed launch links. The website itself is not Java-hosted and does not require a Bloom/Pi reverse proxy. The former Java HTTP preview runtime is not part of the executable path.

## Static assets

The owner-approved UI source remains in `staff-bot/src/main/resources/moderation-preview` as the single shared product asset set. `scripts/build.mjs` copies those files into `dist/` for Cloudflare deployment. Keeping one source prevents the JAR product-contract tests and Cloudflare build from silently drifting; it does not expose a Java web listener.

## Validation

```bash
npm install --no-package-lock --ignore-scripts
npm run check
```

The permanent staging workflow lives at `.github/workflows/moderation-web-staging-deploy.yml` in this repository. It runs the build/tests and Wrangler dry run, deploys to Cloudflare, then proves health/origin, unauthenticated rejection, signed first use, authenticated session page access, the fixed-tunnel direct-read CORS contract, a signed synthetic unauthorized read, direct-read replay rejection, and exact-link replay rejection on the permanent staging hostname. The live direct-read check preserves the exact JSON bytes returned in the signed envelope; adding a trailing newline would intentionally invalidate the body-bound HMAC and must not be used as a transport test.
