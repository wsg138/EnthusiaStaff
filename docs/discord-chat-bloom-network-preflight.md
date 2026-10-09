# Bloom StaffBot-to-Velocity chat-channel network preflight

**Status as of 2026-10-08: CONNECTIVITY UNVERIFIED.**
This is a non-deployment checkpoint supporting draft PRs #422, #465, and #466
and the separate migration safety procedure in
[`discord-chat-cutover.md`](discord-chat-cutover.md). Never infer a successful
TCP connection, TLS handshake, or authenticated protocol session from allocations
or screenshots.

## Reported Bloom topology (owner-supplied Codex findings, not a live probe)

- Velocity and the StaffBot are on Bloom node `ASH-BM-1167` in the same
  parent split.
- Velocity's configured internal peer name:
  `25319956-7c92-49d1-9afe-ea6e18758016`
- **Preferred proposed endpoint:**
  `25319956-7c92-49d1-9afe-ea6e18758016:28765`
- Velocity's existing allocations were reported as IP `170.205.24.14`
  and ports `25564`, `19132`, `8192`, `28765`, `8804`.
  StaffBot's allocation was reported as `25563`.
- The currently backed-up Velocity EnthusiaStaff configuration has
  `channel.enabled=true`, `channel.bind-address=0.0.0.0`,
  `channel.port=28765`.
- Codex reported Bloom's internal UUID service routing and that a new
  public allocation would require a restart; the existing port `28765`
  is already allocated. This is not a guarantee the route is reachable
  from the **StaffBot container**.
- The browser/application console was not confirmed to be a shell. No
  command was run from inside the StaffBot container to open a TCP socket.
  Do **not** enter shell commands in the Minecraft or StaffBot application console.
- The HTTP/web reverse proxy is **not** an alternative for the raw authenticated
  TLS 1.3 persistent channel. StaffBot initiates outbound TCP and requires
  no dedicated inbound allocation for port 28765.

## Verified certificate / source-code checks

The existing SMP Paper public truststore `channel-trust.p12`, downloaded
**read-only** from its protected plugin directory, is 819 bytes with SHA-256
`fe23518c8568e82c7953aaff1e04ca6b87d34b0e7e2c626168df18a546b22f2d`.

A local Java certificate inspection, using a privately backed-up SMP password
source without displaying the password, found:

- 1 X.509 trusted certificate entry and **0 private-key entries**.
- Valid from September 30, 2026 to September 30, 2027.
- Subject `CN=Enthusia Staff network channel`.
- SANs include the proposed UUID name and IP `170.205.24.14`.
- Certificate SHA-256 `63dd196e42bb0aa08221581014166431c712b629a769b4ecdc25e0448c7ad73a`.

`TlsContextLoader.client` in the repository requires PKCS#12 material
with an explicit password and a trusted-certificate entry.
`PersistentChannelClient` enforces TLS 1.3 with HTTPS hostname verification.

## New live endpoint evidence from the owner's Windows PC (2026-10-08)

The owner authorized a one-shot, narrow connection test using the existing
backed-up public-only SMP truststore and its locally protected password file.
Java performed **no HMAC application authentication or chat publication**.

1. TCP to the internal UUID from **Windows** could not resolve the hostname
   (`DNS_FAILED`). This is consistent with Bloom's internal-only UUID routing;
   it does **not** establish internal DNS failure from StaffBot.
2. TCP to public IP `170.205.24.14:28765` from Windows **CONNECTED**.
3. TLS 1.3 completed successfully with JSSE hostname verification for the
   **public IP**, using the SMP public truststore.
4. A separate TLS 1.3 test routed the underlying TCP socket to the public IP,
   but set the **TLS peer hostname** to the proposed internal UUID. JSSE
   HTTPS endpoint identification **PASSED** for that hostname.
5. The **live presented certificate** fingerprint matched the trusted public
   certificate exactly:
   `63dd196e42bb0aa08221581014166431c712b629a769b4ecdc25e0448c7ad73a`.

**Conclusion:** The current **public-facing** Velocity channel listener
is reachable from the owner's Windows PC, presents the expected trusted
certificate, and validates against the proposed UUID hostname. This is
strong evidence that the TLS identity is configured correctly. **StaffBot
container DNS/TCP routing and authenticated STAFFBOT/HMAC handshake
remain UNVERIFIED.** It would be incorrect to call this an internal
StaffBot-origin success.

Do not reuse the local Java diagnostics to scan any other ports/hosts.
A future truststore update/rotation requires reevaluation. Never copy or
upload Velocity's **private server** `channel-server.p12` into StaffBot;
only the trusted-certificate store belongs on the client.

## Bounded live proof needed before activating SHADOW

The standalone, pinned read-only Java helper in
[`tools/chat-bridge-preflight/`](../tools/chat-bridge-preflight/README.md)
can classify TCP/TLS outcomes when such authorized execution becomes available;
it does not establish reachability by merely existing or being compiled.

A technician with **authorized command-execution inside the actual StaffBot
container**, or an approved equivalent execution environment on the
same container network path, must make these exact scope-limited checks:

1. **DNS**: resolve only the proposed UUID hostname from StaffBot's
   network namespace. Record whether it resolves, without logging credentials.
2. **TCP**: attempt one outbound connection to that hostname on **port 28765**
   with a short timeout (recommended no more than 3 seconds). Record pass,
   refused, timed out, or name-resolution failure. No port scanning or changes.
3. **TLS**: if TCP works, use a bounded TLS 1.3 client with HTTPS endpoint
   identification, the public-only SMP truststore and its protected password,
   to verify the currently served certificate. Check the proposed UUID hostname
   against live certificate SANs. Do not disable hostname verification, use a
   trust-all context, or put passwords in process arguments/logs.
4. **Authenticated protocol**: only after the new `STAFFBOT` Velocity peer,
   matching HMACs, and **reviewed** exact-head code are configured in a separately
   authorized deployment step, verify the `STAFFBOT` authenticated channel and
   check its actual readiness/reconnect logs. A TCP/TLS success alone is not
   proof of HMAC identity.
5. **Private SHADOW**: only after the preceding checks, with DiscordSRV active,
   first publish **outbound only** to
   `1541286004298752091` via
   `SMP/global=1541286004298752091`. Leave ingress routes absent initially.
   Then inspect correct content, privacy, duplicates, failures and no loops.

**If command execution in the StaffBot network namespace is unavailable:**
mark DNS/TCP/TLS **UNVERIFIED**, not passed. Do not substitute a check from the
owner's Windows PC, Codex browser, or a different Bloom server/network namespace;
those cannot prove StaffBot-origin reachability. Ask Bloom for a documented,
authorized non-disruptive exec/probe mechanism or arrange a separately approved
controlled staging environment. No shell assumption is authorized.

## Before any controlled deployment

- Keep the present production JARs, `/m`, startup flags, active Velocity config,
  private secret sources and Paper config backed up for rollback.
- Verify the complete Velocity channel secret source and install the added
  `STAFFBOT` secret atomically with its peer config. A partially populated
  source fails closed; do not replace an existing working secret source by itself.
- The previously prepared local private candidate files preserve all **six**
  old configured channel secrets and pair a newly generated 32-byte StaffBot
  HMAC, but are **not live**.
- Prepare the distinct public-chat Discord app token and app-ID pairing, its
  private test-channel permissions, and its approved restricted secret store.
- Configure the reviewed PR #466 private chat file and path-only startup option
  only with the corresponding verified new StaffBot JAR and an approved restart.
  Do not paste private credentials into `APP FLAGS`.
- Preserve the current production Staff/moderation JDA identity and read-tunnel
  settings. Never change chat mode to `AUTHORITATIVE` or disable DiscordSRV
  during SHADOW validation.

No screenshots, SFTP listings or public-network tests replace a live
StaffBot-origin probe or the separately approved production maintenance plan.

## October 9 follow-up: offline-ready, container proof still pending

- At PR #475 head `d4dca00c`, Coverage, Sentinel Restart Artifact and
  Staff state reset runtime proof all completed successfully; Codacy
  reported **zero new issues**. The runtime proof's prior isolated client
  disconnect was transient on one attempt; it passed on rerun. This CI
  status alone is not StaffBot network proof.
- The previously staged Paper, Velocity and StaffBot candidate JAR copies
  remain in **inactive** Bloom folders. On October 9, the owner-local
  WinSCP SFTP helper reauthenticated all three endpoints with verified
  host keys and listed each candidate in its matching inactive folder.
  All three independent local readback SHA-256 checks still passed.
- The owner-local offline SHADOW configuration test passed: exactly one
  pinned `SMP/global` outbound private route, no ingress route, no
  AUTHORITATIVE acknowledgement, structurally matched public Discord
  application token, correctly paired proposed Velocity/StaffBot HMAC
  keys and a valid truststore. These are **private local files not in
  GitHub and not installed as live StaffBot settings**.
- The tracked one-shot TLS test `tools/chat-bridge-preflight/StaffBotTlsProbe.java`
  now has `StaffBotTlsProbeTest.java` with **24 passing negative-input
  assertions**, performed without DNS or TCP access. A Java 21 executable
  preflight JAR was built privately in the owner's local staging folder;
  it contains only compiled probe classes, no credentials, keys, or
  private material. It has **not** been deployed or executed on Bloom.
- Bloom's documented DuckPanel **game/application console is not an
  operating-system shell**. SFTP can list/download/upload files, but
  does not execute the Java probe inside StaffBot's network namespace.
  Creating another split is not a substitute for the existing StaffBot
  namespace and Bloom documents that splitting can restart the parent.

**Owner action needed:** Contact Bloom support through their official
support channel and request an **authorized, non-disruptive shell/exec
method inside the existing StaffBot container**, or a Bloom-operated
one-shot DNS/TCP/TLS check from that container, limited to the fixed
internal Velocity peer on port 28765. Do not provide tokens or HMAC
secrets in the support request, run shell commands in the application
console, or restart the live bot as a supposed read-only test.

**After support's response:** Run the pinned TLS probe with the
public-only truststore under a restricted local config file in the
StaffBot container. This verifies TLS **only**. Authorized deployment
of matching StaffBot and Velocity private peer settings, actual HMAC
channel readiness and private outbound Discord SHADOW message proof
are separate subsequent gates. DiscordSRV remains installed and live.

## October 9: opt-in StaffBot startup TLS diagnostic

The new StaffBot option `--tls-diagnostic` is disabled by default and only
accepted with the existing production file-backed bot/moderation flags.
It runs once after the normal StaffBot services start, on a daemon thread.
The diagnostic connects only to the previously pinned internal Velocity
hostname and port 28765; it requires TLS 1.3, standard hostname checks,
and the exact verified public certificate fingerprint.

The public X.509 certificate is embedded in the StaffBot JAR. No password,
HMAC secret, private key, bot token, or public-chat route is bundled or
required by this diagnostic. No app frames or chat messages are sent.

The status is logged as `staffbot_tls_diagnostic state=...` and written
to `staffbot-tls-diagnostic-result.txt` in the StaffBot working directory
for SFTP readback. The report explicitly says HMAC is not checked.
The diagnostic does not alter StaffBot moderation readiness or enable the
new chat bridge.

Controlled activation requires retaining the exact live StaffBot JAR and
all existing startup arguments, installing the reviewed replacement at a
maintenance restart and adding ONLY `--tls-diagnostic`. Leave Velocity,
Paper, DiscordSRV, and private chat routes unchanged. If moderation or
tunnel health regresses, remove that flag and restore the matching original
JAR and startup arguments. A successful TLS result is not HMAC/SHADOW proof.
