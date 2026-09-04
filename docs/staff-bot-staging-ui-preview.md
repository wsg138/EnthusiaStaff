# Staff bot staging moderation web preview

This runbook covers the owner-directed, staging-only moderation preview. Discord is the launch surface and `https://staff-staging.enthusia.info` is the permanent web workspace. This is not ES-D07 and it has no live punishment, message deletion, Minecraft enforcement, LiteBans mutation, punishment-database mutation, or production authority path.

## Accepted architecture

- The staging Discord bot runs on Bloom.
- `/moderate-preview` returns an ephemeral `Open Moderation Panel` link.
- The moderation website is permanently hosted by Cloudflare from this repository.
- Cloudflare Workers Static Assets serve the approved UI through the Worker; direct unauthenticated workspace access is rejected.
- A SQLite-backed Durable Object owns one-time launch replay state and browser session/CSRF state.
- `EnthusiaStaff-Staging` is not the website host. It is reserved for narrow temporary diagnostics or acceptance when genuinely useful.
- The former Java `HttpServer` moderation-preview runtime has been removed from the executable path. No reverse proxy, Pi web host, or Bloom-hosted website is part of the accepted design.

The static UI source remains under `staff-bot/src/main/resources/moderation-preview` so the product contract tests and Cloudflare build share exactly one owner-approved asset set. `moderation-web/scripts/build.mjs` copies those assets into the Cloudflare deployment bundle. Their presence in the JAR resources does not start or expose a Java web server.

## Safety model

Preview mode can be enabled only for the fixed staging environment. Production configuration rejects it. When preview mode is enabled, the process does not initialize the live moderation database/authority runtime.

The browser workspace uses deterministic sample state and the final confirmation calls only `/api/simulate`. The Worker validates an explicit allowlisted target and never accepts browser-supplied authority as sufficient authorization.

A future real-data/enforcement package must reauthorize the concrete staff actor, target, sanction, duration, scope, restriction targets, permanent-action permission, approval requirement, and evidence state at the server-side commit boundary. That work is deliberately outside this PR.

## Launch and browser-session security

Each Discord launch URL contains only a short-lived signed staging ticket. Claims bind:

- staff actor ID;
- staging guild ID;
- allowlisted staging target key;
- issue and expiry timestamps;
- a random nonce.

The Worker verifies HMAC-SHA256, rejects malformed/tampered/expired/wrong-guild/wrong-target tickets, and atomically consumes the nonce in the Durable Object. First use creates a random server-side browser session; replay of the exact same launch is rejected.

The browser session cookie is host-only, `Secure`, `HttpOnly`, and `SameSite=Strict`, expires after 15 minutes, and is paired with server-side CSRF material for state-changing preview requests. Responses use `no-store`, HSTS, restrictive CSP, frame denial, referrer and MIME protections, cross-origin protections, and disabled camera/microphone/geolocation permissions.

Rejection responses intentionally do not expose internal validation/replay reasons. Live acceptance verifies the public contract by status and body.

## Signing-secret boundary and staging bootstrap debt

The raw Discord bot token is used only where the staging bot or protected deployment automation already needs it. It must never appear in browser code, public source, URLs, logs, artifacts, or the Cloudflare runtime.

Today the bot and protected GitHub Actions workflow derive the same 32-byte launch-signing key from the staging Discord bot token using a domain-separated SHA-256 derivation. Only that derived key is uploaded to Cloudflare as `LAUNCH_SIGNING_KEY_HEX`; the raw Discord token is not uploaded to Cloudflare. The deployment workflow scopes each protected secret to only the individual steps that require it rather than exposing the bot token job-wide.

This coupling is accepted **staging bootstrap debt**, not the desired long-term secret design. A future hardening change should provision a dedicated random launch-signing secret to both Bloom and the protected Cloudflare deployment environment, then remove token-derived signing in one coordinated cutover. Do not make an uncoordinated partial migration that breaks launch verification or causes either side to fall back insecurely.

## Bloom startup

Use Java 21 and the staff-bot JAR. Keep the staging bot token in a runtime-only file rather than process arguments.

```text
JAR FILE:
EnthusiaStaff-StaffBot.jar

FLAGS:
-Dterminal.jline=false -Dterminal.ansi=true

APP FLAGS:
--staging-ui-preview --token-file=staging-bot-token.txt --preview-public-url=https://staff-staging.enthusia.info
```

The equivalent environment path is:

```bash
export ENTHUSIA_STAFF_BOT_TOKEN='<staging bot token>'
export ENTHUSIA_STAFF_BOT_ENVIRONMENT='staging'
export ENTHUSIA_STAFF_BOT_UI_PREVIEW='true'
export ENTHUSIA_STAFF_BOT_UI_PREVIEW_PUBLIC_URL='https://staff-staging.enthusia.info'
java -jar EnthusiaStaff-StaffBot.jar
```

Do not configure a public Java web listener, reverse proxy, or Pi web host. The bot only issues signed links to the Cloudflare origin.

If the safe hosted origin is absent, the Discord launcher fails closed with `Panel deployment required` rather than inventing or exposing an unsafe fallback URL.

## Permanent Cloudflare deployment

The permanent staging workflow is `.github/workflows/moderation-web-staging-deploy.yml` in `wsg138/EnthusiaStaff`. It automatically deploys relevant changes from `main`, retains manual dispatch support, and also admits this feature branch during PR acceptance so the frozen candidate can be proven live before merge.

For an exact source commit it:

1. resolves the owning Cloudflare account for `enthusia.info`;
2. installs Node 22 dependencies without lifecycle scripts;
3. runs the moderation-web build/tests and Wrangler dry-run checks through `npm run check`;
4. derives and uploads only the launch-signing key, never the raw Discord token;
5. deploys the Worker + static assets + Durable Object binding;
6. verifies `https://staff-staging.enthusia.info/health` and the staging/simulation-only origin contract;
7. proves unauthenticated workspace rejection;
8. proves signed launch first use returns the expected redirect and session cookie;
9. proves the authenticated moderation page loads;
10. proves replay of the exact same signed launch returns the public 401 rejection contract.

The workflow records the exact source SHA in its run summary. A queued, skipped, failed, cancelled, or wrong-head run is not acceptance evidence.

## Owner acceptance

The owner manually exercised the real flow after the Durable Object first-use/replay bug was fixed:

```text
Discord /moderate-preview
→ Open Moderation Panel
→ https://staff-staging.enthusia.info
→ authenticated Cloudflare moderation workspace
```

The owner reported that it worked, looked great, and approved the current visual/UX foundation. Do not redesign the interface in cleanup work unless a concrete correctness, accessibility, or security defect requires it.

## Release provenance

The existing `staff-bot-staging` prerelease publishes:

```text
https://github.com/wsg138/EnthusiaStaff/releases/download/staff-bot-staging/EnthusiaStaff-StaffBot.jar
```

It also publishes `EnthusiaStaff-StaffBot.jar.sha256` and `staff-bot-staging-source.txt`. Before replacing the Bloom staging runtime, verify that the source file names the exact frozen product SHA and that the JAR checksum matches.

## Disabling preview

Stop the Bloom process and remove the staging-preview activation flags, or set `ENTHUSIA_STAFF_BOT_UI_PREVIEW=false` for environment-based startup. Production must never be configured with either preview activation path.
