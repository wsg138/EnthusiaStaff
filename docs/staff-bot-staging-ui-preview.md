# Staff bot staging moderation web preview

This runbook covers the owner-directed, staging-only moderation preview. Discord is the launch surface and `https://staff-staging.enthusia.info` is the permanent web workspace. This is not ES-D07 and it has no live punishment, message deletion, Minecraft enforcement, LiteBans mutation, punishment-database mutation, or production authority path.

## Accepted architecture

- The staging Discord bot runs on Bloom.
- `/moderate-preview` returns an ephemeral `Open Moderation Panel` link.
- The moderation website is permanently hosted by Cloudflare from this repository.
- Cloudflare Workers Static Assets serve the approved UI through the Worker; direct unauthenticated workspace access is rejected.
- A SQLite-backed Durable Object owns one-time launch replay state and browser session/CSRF state.
- D16 optionally attaches the hosted workspace to the existing D03/D06 read-only authority/data runtime through a separately authenticated private read bridge.
- Destructive punishment, message-deletion, and permission-override operations remain simulation-only.

The static UI source remains under `staff-bot/src/main/resources/moderation-preview` so the product contract tests and Cloudflare build share exactly one owner-approved asset set. `moderation-web/scripts/build.mjs` copies those assets into the Cloudflare deployment bundle.

## Safety model

Preview mode can be enabled only for the fixed staging environment. Production configuration rejects it. Without D06/D16 runtime configuration the preview remains unable to serve real moderation reads. When a complete `--moderation-config-file` is supplied, the process initializes only the existing read-only D06 data/authority runtime and the D16 loopback read API. It does not initialize any punishment, deletion, permission-override, or moderation-database mutation adapter.

The D16 read API binds only to `127.0.0.1:8766`. The browser never receives MariaDB credentials, Discord bot credentials, component-signing secrets, authority credentials, or Cloudflare tunnel credentials. The Cloudflare Worker signs bounded service requests to the fixed private backend and applies explicit response allowlists.

## Launch and browser-session security

Each Discord launch URL contains only a short-lived signed staging ticket. Claims bind the staff actor, staging guild, concrete target, issue/expiry times, and a random nonce. The Worker verifies HMAC-SHA256 and atomically consumes the nonce before creating a short-lived browser session.

The browser session cookie is host-only, `Secure`, `HttpOnly`, and `SameSite=Strict`, and protected responses use `private, no-store` plus the existing CSP/cross-origin controls. Rejection responses intentionally do not expose internal validation details.

## Signing-secret boundary

The raw Discord bot token remains in a runtime-only file. It must never appear in browser code, public source, URLs, logs, artifacts, or the Cloudflare runtime. Protected GitHub automation derives only the staging launch/service signing material it needs; raw credentials are not uploaded to Cloudflare.

## Bloom startup — file-backed D16 configuration

Use Java 21 and the exact validated staff-bot JAR. Bloom does not need panel environment variables for the D16 read-only configuration.

```text
JAR FILE:
EnthusiaStaff-StaffBot.jar

FLAGS:
-Dterminal.jline=false -Dterminal.ansi=true

APP FLAGS:
--staging-ui-preview --token-file=staging-bot-token.txt --moderation-config-file=staff-bot-runtime.properties --preview-public-url=https://staff-staging.enthusia.info
```

Create `staff-bot-runtime.properties` in the Discord-bot split. This file is runtime-only and must never be committed or pasted into tickets/chat/logs:

```properties
db.jdbc-url=jdbc:mariadb://<database-host>:3306/<database-name>
db.username=<read-only-database-user>
db.password=<read-only-database-password>
authority.url=http://127.0.0.1:8771/v1/staff-rank
authority.secret=<random-secret-at-least-32-characters>
component.secret=<different-random-secret-at-least-32-characters>
# Optional bounded tuning:
# db.pool-size=4
# db.timeout-millis=3000
```

The file parser accepts only the documented keys, rejects partial configuration, preserves the existing loopback authority allowlist, and never renders secret values through configuration `toString()` output. Existing environment-based configuration remains supported for older deployments but is not required for this Bloom staging path.

### Paper authority secret file

The existing D06 Paper authority bridge also supports a runtime-only file so its bearer secret does not need to be a panel environment variable. Place this on the Paper split at:

```text
plugins/EnthusiaStaff/discord-staff-authority.properties
```

with:

```properties
authority.url=http://127.0.0.1:8771/v1/staff-rank
authority.secret=<the-same-authority.secret-used-by-the-staff-bot>
```

The endpoint still binds to exact IPv4 loopback only and still derives staff rank from current LuckPerms state on each request. The file option does not widen that network boundary. If either file is missing, partial, malformed, weak, or points at a non-loopback authority URL, the corresponding runtime fails closed or remains unavailable rather than silently inventing authority.

## Permanent Cloudflare deployment

The permanent staging workflow is `.github/workflows/moderation-web-staging-deploy.yml`. For an exact source commit it builds/tests the Worker, provisions the fixed staging tunnel and DNS route, deploys the Worker/assets, verifies origin/session/replay behavior, and records the exact source SHA. Queued, skipped, failed, cancelled, or wrong-head runs are not acceptance evidence.

The fixed D16 private ingress is `moderation-read-staging.enthusia.info` to `http://127.0.0.1:8766`; the Bloom connector must run in the same container/network namespace as the staff-bot process. Port 8766 must never be exposed as a public Bloom allocation.

## Owner acceptance

Acceptance for D16 requires the staging Discord application Message Content entitlement, the exact validated staff-bot artifact, a connected fixed Cloudflare tunnel, and sanitized real-data reads that prove actor/target/guild authorization and bounded Discord/D06 data without exposing private values in evidence.

## Release provenance

The staging release/artifact path publishes the staff-bot JAR plus source/checksum provenance. Before replacing the Bloom runtime, verify the source SHA and JAR checksum against the exact reviewed candidate.

## Disabling preview

Stop the Bloom process and remove the staging-preview activation flags. Remove the runtime-only configuration/tunnel files if the preview is being decommissioned. Production must never be configured with the staging-preview activation path.
