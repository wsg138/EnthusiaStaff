# Discord console bridge — Worker C security review and staging gate (2026-10-15)

**Scope:** source-only, nonproduction hardening for EnthusiaStaff Discord `/console`. This is not DiscordSRV-console replacement approval. No Bloom, guild, role, secret, migration, plugin JAR, or command mutation is authorized by this document.

## Audited execution path

1. JDA registers `/console` only when a command-bridge configuration exists. Its Discord default member permission is **discovery/visibility only**, not Minecraft authority. The listener already rejects the wrong Discord guild and duplicate interactions. This patch additionally requires the **exact configured Discord channel ID** before deferral, actor lookup, or network transport.
2. StaffBot resolves the Discord snowflake through the current canonical Enthusia subject with a selected main Minecraft UUID; unlinked or stale subjects fail closed.
3. A fresh request ID, requested-at instant, HMAC-SHA256 body-bound signature, timestamp and 32–64-character random nonce are sent once to an explicitly configured Minecraft target endpoint. The client does not redirect or retry ambiguous responses.
4. The Paper HTTP endpoint checks POST, exact path, private network peer, body size, HMAC timestamp (±30 seconds), signature and in-memory nonce replay. **A reused nonce is rejected; after restart, the durable request-ID audit is the second replay defense.**
5. Paper's domain policy checks exact target server, explicitly configured command name/rank/permission/max argument count, and malformed separators/quotes/controls. The body `requestedAt` is also checked within ±30 seconds, independent of the HMAC header.
6. Before attempting execution, Paper claims a durable audit request ID with a body fingerprint, rereads canonical account linking, rereads LuckPerms rank and explicit permission, and checks live Staff Mode duty **before and after** LuckPerms lookup. The Paper global scheduler rechecks duty again immediately before command dispatch. Helper/Developer are not implicitly Mod; Discord roles or Discord admin alone grant nothing.
7. One console command dispatch is captured (max 8192 captured characters), sanitized (20 lines / 2000 characters, selected secret/path/evidence patterns), and a bounded, signed result is returned. StaffBot neutralizes Discord mentions and backticks.
8. An unknown execution result, failed durable completion, timeout, in-flight duplicate, or server outage **must not** cause automatic command replay. Recovery is manual and requires inspection of server state/audit first.

### New opt-in channel gate

`ENTHUSIA_STAFF_BOT_COMMAND_BRIDGE_CHANNEL_ID=<exact Discord text-channel snowflake>` is **mandatory when the StaffBot console bridge is configured**. In a private StaffBot moderation properties file the corresponding allowlisted key is `command-bridge.channel-id`. A missing, malformed, zero, or out-of-range channel ID prevents bridge startup; a different channel returns an unavailable reply. It is not a general staff-channel list. Other StaffBot commands are unaffected.

Paper's console bridge also requires a usable `runtimeComponents.staffMode()::authorityActive` gate. Offline identities, handoff gaps, and Staff Mode transitions do not satisfy this gate. The Paper bridge must not be enabled without it.

## Negative and resilience matrix

| Case | Expected safe behavior | Source proof / remaining verification |
| --- | --- | --- |
| Unlinked / stale Discord subject | No dispatch | Existing coordinator + canonical Paper-link tests |
| Helper/Developer below configured MOD minimum | No dispatch | Existing service rank test; staging ranks still needed |
| Discord administrator with no Minecraft rank | No dispatch | Paper role/permission gate; targeted staging proof outstanding |
| MOD+ but off-duty | No dispatch | New LuckPerms duty-gate test |
| Duty revoked during asynchronous LuckPerms lookup | No dispatch | New pre-/post-lookup test |
| Duty revoked between permission check and scheduler execution | No dispatch | New global-scheduler race test |
| Wrong guild | Reject before `/console` work | JDA listener guard; guild staging proof outstanding |
| Wrong Discord channel | Reject before transport | New exact channel guard and unit tests |
| Wrong server | No transport / no execution | Existing StaffBot endpoint and Paper allowlist tests |
| Expired signature timestamp / invalid nonce / replay | HTTP reject | Existing protocol authenticator tests |
| Reused request ID / conflict / unresolved audit after restart | No second dispatch | Existing domain and MariaDB audit tests |
| Stale/future-dated signed request body | No dispatch, audited invalid | New independent body freshness tests |
| Newline, control, quote, semicolon, pipe, backslash / bidi formatting | Malformed command | Expanded domain policy negative tests |
| Non-allowlisted command or excessive arguments | No dispatch | Existing domain policy tests |
| Credential, private path, IP/evidence in output | Redact / bound | Existing sanitizer tests; **not** proof against every secret format |
| Discord output mentions/backticks, overlong response | Neutralize / bound | Existing formatter tests |
| Paper queue timeout after uncertain dispatch | Mark unknown, no automatic replay | Existing executor/service/transport tests |
| Paper/Velocity/StaffBot transport outage | Fail closed, no command retry | Existing transport and endpoint tests; live route not tested |
| Unsigned, spoofed or oversized HTTP response | Reject / bound | HMAC response verification, strict codec limit and new 8192-byte HTTP stream cap; **actual staging stream-limit acceptance still outstanding** |

**Caution:** An explicitly allowlisted Minecraft command may still be dangerous depending on its server plugin and argument semantics. Never allowlist wildcard/admin/op, shell-like, data export, permission/role mutation, file/system, restart, or destructive punishment commands for the first staging phase. This patch rejects several metacharacter forms but cannot establish the safety of arbitrary registered command implementations. Bound output and selected redaction patterns are **not** a universal data-loss-prevention system.

## Private acceptance checklist — no production change in this PR

1. Collect from the owner **sanitized** DiscordSRV console channel routing and legacy permission roles **without any tokens or full actor IDs**, plus the effective legacy command policy. Do not paste production secrets, raw IPs or private moderation messages in GitHub.
2. On isolated test guild and private Paper server, provision a fresh >=32-character dedicated HMAC secret in private files; use an exact test guild/channel, per-server explicit target IDs and protected loopback/private routing. Keep legacy DiscordSRV console enabled during comparison.
3. Stage disabled by default. Start with one harmless read-only command and explicit permission node/max arguments, then confirm off-duty/Helper/Developer/admin-role-only/unknown actor and wrong guild/channel target rejections. Confirm duty revocation just before dispatch via a controlled private staging test.
4. Confirm both same-request duplicate and restart/replay idempotency using **synthetic, harmless** commands; inspect persisted audit, request IDs, terminal outcome and manual unknown-outcome recovery. Do not run real punishments, kicks, bans, restarts or private-data queries.
5. Exercise HMAC rotation, 30-second freshness boundaries, malformed input, bad signatures, target mismatch, scheduler delay, Paper/Velocity outages, transport timeout and oversized/redacted output. Verify bounded logs contain no credentials.
6. Independently compare authorized legacy-console behavior and result presentation with new bridge. Capture only sanitized, non-identifying evidence. Require reviewer signoff, exact-head CI, Codacy, and a separate deployment authorization before considering any cutover.
7. Verify StaffBot and Paper environment are actually supplied with the **new** channel/duty contract in the exact artifacts staged. Passing source tests alone does not establish DiscordSRV console parity.

## Rollback

If staging rejects legitimate commands or a security boundary fails, **disable the newly staged Paper/StaffBot command bridge** and restore the last independently verified configurations/artifacts. Leave DiscordSRV enabled and do not retry ambiguous commands. Preserve redacted audit IDs and time windows for investigation. Never activate an authoritative writer, change live permissions or disable legacy console to validate this draft.

**Blocked gates:** no verified owner-supplied legacy console ACL/config parity, no isolated live Discord↔Minecraft staging execution, no positive current-duty integration proof, no full response-stream resource-limit verification, no deployed-version verification, no owner cutover approval. No physical DiscordSRV retirement is authorized.
