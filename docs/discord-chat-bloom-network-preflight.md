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

**Limits:** The local certificate inspection does not establish the certificate
currently served by the live Velocity listener, its process liveness,
or its accessibility from StaffBot. A future truststore update/rotation
requires reevaluation. Never copy or upload Velocity's **private server**
`channel-server.p12` into StaffBot; only the trusted-certificate store
belongs on the client.

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
