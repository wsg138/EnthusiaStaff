# Privacy and Data Handling

Moderation requires sensitive evidence. EnthusiaStaff should expose only the minimum information required for the audience and decision.

The addition of StaffBot, Discord/Minecraft linking, public website APIs and the browser moderation workspace creates more projection boundaries—not permission to expose more raw data.

## Data classification

### Public

May be exposed only through approved public projections:

- player identity intended for public display;
- punishment type/public reason;
- issue/expiration/remaining duration;
- public case state/ID;
- appeal availability/status intended for that player/public view;
- other explicitly approved public leaderboard/site fields.

Public APIs should use explicit response allowlists rather than serializing domain/JDBC records directly.

### Staff-confidential

Keep inside approved staff systems:

- reporter identity;
- internal explanations/staff notes;
- report/claim ownership;
- coordinates/base locations;
- relevant chat context;
- client/anticheat evidence;
- alt confidence/evidence;
- Discord moderation/case/note context;
- linked-account relationships/history;
- confiscation/economy plans;
- recovery/quarantine/reconciliation details.

### Highly restricted

Expose only to the smallest approved operational audience:

- private messages;
- raw/recoverable network identity;
- encryption/HMAC/signing material and key rotation details;
- Discord bot token/webhook credentials;
- private StaffBot authority/read service credentials/origins;
- browser session/CSRF/authentication data;
- appeal media;
- database credentials;
- TLS keys/trust stores;
- Cloudflare/email/provider secrets;
- full confiscated asset snapshots;
- production topology details that would weaken a private boundary.

## Discord/Minecraft account links

Account-link data is private moderation/identity data.

Do not expose another player’s:

- Discord user linkage;
- linked Minecraft alts;
- historical link ownership;
- link/unlink/reassignment audit;
- main-account selection details;
- one-use link challenges.

Raw link codes exist only for the challenge recipient and are short-lived. Persistent challenge storage uses hashes rather than storing the raw code.

A public Discord bot/site should never become an account-link lookup service for other users.

## Network identity

Raw network addresses are process-only input. They must not appear in ordinary logs, Discord, GUI/site/API output, exceptions, staff notes or relationship audit reasons.

The protected subsystem stores versioned equality/encrypted forms appropriate to its security design. Staff should act on approved relationship/evidence abstractions rather than raw addresses.

If raw address text appears in a staff/public surface, treat it as a privacy defect.

## Discord webhook privacy

Legacy webhook delivery uses `DiscordEventRenderer` allowlists and must never blindly emit stored `payload_json`.

Do not send through webhooks:

- reporter/private-message evidence;
- raw network identity;
- coordinates/base locations;
- confiscated contents;
- protected link/alt history;
- secrets/private appeal media;
- arbitrary internal recovery/provider payloads.

See [[Discord Delivery]].

## StaffBot privacy

StaffBot has more privileged read capability than the legacy webhook renderer, but audience/authorization still matters.

Review every panel/command/component for:

- current linked-staff authorization;
- least-privilege field projection;
- ephemeral/private interaction behavior where appropriate;
- bounded message/history/evidence output;
- no tokens/signing material/private service details in embeds/errors/logs;
- no private linked-account/evidence data in public command responses.

Discord roles alone do not authorize sensitive reads.

See [[Discord Moderation Platform]].

## Public site / Velocity API

Public punishment/search/case routes expose sanitized projections only.

The public API/browser must not receive:

- reporter identity;
- internal staff notes;
- private-message evidence;
- raw network identity;
- hidden alt/link history;
- sensitive coordinates;
- full provider payloads;
- credentials/signing material;
- internal recovery/quarantine metadata not approved for public display.

Authenticated appeal/reviewer APIs may expose more information to the appropriate actor, but each route still needs an explicit field boundary.

See [[Website and Web API]].

## Staging moderation web workspace

The browser moderation workspace is privileged staff UI, but the browser/Cloudflare layer is not trusted as a direct database client.

Current protections include signed one-time launches, server-side sessions/CSRF state, signed body-bound StaffBot read requests, replay protection/rate limits and private/no-store responses.

Privacy rules:

- raw Discord bot token is never sent to Cloudflare/browser/URLs/logs/artifacts;
- launch/read signing material is not exposed to the browser;
- public error responses do not reveal internal replay/auth failure details;
- StaffBot read responses use explicit allowlists;
- browser caches/history/URLs must not become evidence stores;
- staging bootstrap key derivation must not be described as ideal production secret architecture.

## Logs, health and errors

Operational observability must remain useful without leaking secrets.

Never log raw:

- Discord token/webhook URLs;
- HMAC/signing/TLS/DB credentials;
- browser session cookies/CSRF values;
- full private request bodies/evidence;
- raw network identity;
- link challenge codes;
- private service credentials.

Health endpoints should report dependency/category status, not sensitive configuration values.

## Screenshots and exports

Before sharing a screenshot:

1. crop unrelated player/staff data;
2. remove coordinates, raw addresses, tokens, secrets, private messages and account-link details;
3. inspect hover text, URLs, browser tabs, terminal history, filenames and sidebars;
4. use the approved private incident/review location;
5. link the authoritative record instead of creating uncontrolled evidence copies where possible.

## Retention

Sensitive context should use bounded retention appropriate to its purpose. Do not retain private evidence indefinitely “just in case” outside approved storage.

When retention jobs purge detailed evidence while preserving an aggregate relationship/decision, reviewers must verify that:

- the correct rows are bounded/purged;
- public/staff views do not assume purged raw evidence still exists;
- maintenance runs only under the correct write/authority fence;
- audit history remains interpretable without restoring private raw data.

Exact configured retention belongs to the owning validated configuration and release evidence.

## Public explanations

A public reason should explain the rule violation without revealing private detection methods or another player’s sensitive information.

Bad:

> Banned because another account matched a raw network address and private location evidence.

Better:

> Ban evasion linked to an active network ban.

Detailed evidence belongs in the restricted case/workspace.

## Access discipline

Read access is authority. Sensitive records should be accessed only for legitimate assigned moderation, investigation, appeal, recovery, security audit, or authorized development/staging verification.

Curiosity is not a valid staff purpose.

## See also

- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Discord Delivery]]
- [[Website and Web API]]
- [[Code Review Guide]]
- [[Recovery and Troubleshooting]]