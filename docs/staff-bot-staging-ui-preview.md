# Staff bot staging moderation web preview

This runbook covers the owner-directed staging moderation product preview. Discord is the launch surface; the primary moderation workspace is web-first. This work is not ES-D07 punishment enforcement.

## Safety model

The preview remains staging-only and non-destructive. Enable it only through one of the existing explicit staging paths:

- environment configuration: `ENTHUSIA_STAFF_BOT_ENVIRONMENT=staging` together with `ENTHUSIA_STAFF_BOT_UI_PREVIEW=true`; or
- the dedicated-host convenience CLI: `--staging-ui-preview` together with `--token-file=<path>`.

The convenience CLI selects the fixed `STAGING` environment and rejects an explicitly configured production environment. The production configuration cannot enable preview mode. The fixed staging Discord application, guild, and test-channel identity fence still applies before interactions are enabled.

When preview mode is enabled, the process does not initialize the D06 moderation database/authority runtime. The browser workspace uses deterministic bounded in-memory sample state. It has no punishment service, Discord moderation adapter, Discord message-delete adapter, Minecraft/Paper authority adapter, LiteBans adapter, persistence adapter, or production moderation dependency. Final confirmation calls only the staging simulation endpoint.

The browser is never an authority boundary. A future enforcement implementation must server-side reauthorize the concrete actor, target, sanction, duration, scope, restriction targets, permanent-action permission, and approval requirement immediately before commit.

## Discord launcher

Run `/moderate-preview` in the staging guild. Discord now shows a compact target summary rather than the full moderation wizard:

- Discord and Minecraft identity;
- active moderation status;
- linked-account count;
- concise recent-history indicator;
- `Open Moderation Panel`.

The interaction remains ephemeral. A clickable panel button is emitted only when a safe public staging origin has been explicitly configured. Without one, the command deliberately shows a disabled `Panel deployment required` button rather than inventing an unsafe URL.

## Web launch/session security

The launcher does not put a permanent bearer credential or arbitrary target ID in the URL.

For each launch, the staging process creates a bounded one-time signed ticket. Server-side ticket state binds:

- staff actor ID;
- staging guild ID;
- allowlisted staging target key;
- issue time;
- expiry;
- random nonce.

Tickets use an in-process HMAC key, expire after two minutes, and are consumed once. A successful launch exchanges the URL ticket for a random `HttpOnly`, `SameSite=Strict` browser-session cookie. Browser sessions are bounded and expire after 15 minutes. State-changing preview requests also require the session CSRF token. Static and API responses use `no-store` plus a restrictive CSP, frame denial, referrer policy, MIME sniffing protection, and disabled camera/microphone/geolocation permissions.

This is a staging session contract, not production authentication. A future production web console must integrate canonical staff authentication and preserve server-side authorization at the action boundary.

## Web moderation workspace

The player moderation workspace is intentionally structured like a staff SaaS/admin product rather than a Discord embed translated into HTML.

Primary navigation:

- Overview
- Messages
- History
- Cases
- Notes
- Accounts

The target header keeps raw Discord and Minecraft IDs available as secondary technical metadata while leading with recognizable identities, status, sanctions, and linked-account context.

### Messages and evidence

The deterministic message investigation fixture includes multiple dates and channels, target and surrounding authors, exact timestamps, replies, edited state, deleted source-context state, and an attachment example.

Staff can filter by message text, channel, date, or selected state and can inspect surrounding conversation around a triggering message. Shift-selection supports a contiguous visible message range.

Selection and case actions are deliberately separate concepts:

- selecting a message does not itself make it evidence;
- `Add to Evidence` marks selected messages for the case;
- `Mark Violating` records that the selected content is considered violating;
- `Delete on Confirm` is a separate preview-only instruction;
- evidence can therefore be preserved in Discord;
- deletion does not silently imply evidence.

The final review distinguishes evidence count, messages marked for deletion, and selected evidence that will be preserved.

### Offense and punishment ladder

The normal workflow is:

```text
Messages / history
→ Issue Punishment
→ Offense
→ Relevant History
→ Ladder Recommendation
→ Use Recommendation OR Custom Punishment
→ Options
→ Review
→ Simulation complete
```

The offense is chosen before ladder evaluation. The preview always distinguishes total moderation history from history relevant to the selected offense. Unrelated records remain visible but do not advance the selected offense's ladder.

`Use Recommendation` is the primary path and carries the recommended action, duration, and scope forward automatically.

`Custom Punishment` is an explicit override. Only that path exposes direct controls for Warning, Mute, Kick, Ban, Restrict, scope, and applicable durations. Review labels the result `Custom override` when it differs from the recommendation. The preview does not imply that custom controls can bypass future authority checks.

### Restrict

`Restrict` means limiting a user's permissions in exact Discord locations. It is not a vague account-wide punishment.

The preview supports searchable channel/category targets and visibly distinguishes:

- `Read only` — the user can view the selected location but cannot send/respond;
- `No access` — the user cannot view/access the selected location.

Representative fixtures include one channel, multiple channels, and a category. Review shows the chosen mode, exact targets, and duration only when Restrict is the actual action.

### Final review

The review screen conditionally summarizes the information needed to understand the action in seconds:

- target;
- offense;
- relevant vs total history;
- ladder recommendation;
- actual action;
- recommendation followed vs custom override;
- duration and scope;
- restriction mode and exact targets when applicable;
- evidence messages;
- messages to delete;
- evidence preserved;
- user DM choice;
- approval requirement;
- case/evidence summary;
- staff explanation.

The stale-evidence fixture blocks final confirmation until the recommendation is recalculated against the current preview state. Permanent Ban, Mute, and Restrict examples display the representative Admin+ approval requirement.

Final confirmation returns `Simulation complete` with one concise note that no live moderation action was performed.

## Deterministic evaluation scenarios

The web selector provides bounded scenarios for:

1. minor first offense;
2. repeat spam offense;
3. severe harassment/hate offense;
4. admin-level/permanent recommendation;
5. custom override;
6. many unrelated historical punishments with only a small relevant subset;
7. multi-message evidence where only some messages are selected for deletion;
8. one-channel and multi-target/category restrictions with Read only and No access modes;
9. edited message;
10. message with attachment;
11. stale evidence/recalculation;
12. approval required.

These fixtures are product-evaluation data, not permanent production moderation policy.

## Bloom startup

The existing Bloom preview startup remains valid and safe even without a web deployment.

Place a runtime-only `staging-bot-token.txt` beside the JAR and use:

```text
Java Version:
Java 21

JAR FILE:
EnthusiaStaff-StaffBot.jar

FLAGS:
-Dterminal.jline=false -Dterminal.ansi=true

APP FLAGS:
--staging-ui-preview --token-file=staging-bot-token.txt
```

With no additional web environment variables, the preview web server binds to an ephemeral loopback port and no external launch URL is advertised. This preserves existing staging startup/smoke behavior without exposing a new network listener.

The environment-variable startup path also remains supported:

```bash
export ENTHUSIA_STAFF_BOT_TOKEN='<staging bot token>'
export ENTHUSIA_STAFF_BOT_ENVIRONMENT='staging'
export ENTHUSIA_STAFF_BOT_UI_PREVIEW='true'
java -jar EnthusiaStaff-StaffBot.jar
```

## Clickable staging web deployment

A real remote staging link requires infrastructure outside the repository. Do not weaken the launch-ticket model merely to avoid this deployment step.

Configure a fixed loopback bind behind an HTTPS reverse proxy, for example:

```bash
export ENTHUSIA_STAFF_BOT_UI_PREVIEW_WEB_BIND='127.0.0.1:8765'
export ENTHUSIA_STAFF_BOT_UI_PREVIEW_PUBLIC_URL='https://<staff-staging-host>'
```

Requirements:

- the public URL must be an HTTPS origin with no path, query, fragment, or embedded credentials;
- the bind port must be explicit when a public URL is configured;
- TLS should terminate at the staging reverse proxy and proxy only to the loopback bind;
- do not expose the loopback HTTP port directly to the Internet;
- do not add tokens or long-lived credentials to the public URL;
- preserve the fixed staging Discord identity fence.

For local-only development, omit the bind variable for an ephemeral loopback listener, or use the fixed staging bind port `8765` on `127.0.0.1`, `localhost`, or `[::1]`. A loopback HTTP public origin is permitted only for that local development case. Other explicit bind ports and non-loopback public HTTP are rejected.

No external staging hostname, reverse-proxy route, TLS certificate, or hosting credential is provisioned by this repository change. A disabled launcher button therefore means the deployment contract is intentionally incomplete, not that the bot should fall back to an insecure link.

## Fixed staging release

After a safely merged `main` build, the existing `staff-bot-staging` prerelease publishes:

```text
https://github.com/wsg138/EnthusiaStaff/releases/download/staff-bot-staging/EnthusiaStaff-StaffBot.jar
```

The release also publishes `EnthusiaStaff-StaffBot.jar.sha256` and `staff-bot-staging-source.txt`. Verify the exact source SHA and checksum before replacing a dedicated-host runtime.

## Disabling preview

On Bloom, stop the process and remove `--staging-ui-preview --token-file=staging-bot-token.txt` from APP FLAGS before starting a normal runtime. For the environment path, stop the process and remove or set `ENTHUSIA_STAFF_BOT_UI_PREVIEW=false`. Production must never be configured with either preview activation path.
