# Read-only Staff session inspection

`staffsession inspect <canonical-uuid>` is a **server-console-only** diagnostic.
It works while the target is offline. Use the exact UUID verified through the
operator's trusted identity evidence, not a partial UUID or a player-name search.
Players, including operators, cannot invoke it as players. Existing Staff Mode
commands and permissions are unchanged.

The command uses the existing Staff application's connection pool on its bounded
worker executor. It opens a read-only repeatable-read transaction, runs one fixed
UUID-bound SELECT with a five-second query timeout and two-row ceiling, and ends
the transaction by rollback. It never calls recovery, changes rows, applies a
snapshot, contacts a new external database client, or changes connection/TLS
configuration. It does not attest the existing pool's negotiated transport.

The lookup includes detached ownership and uses a left join so a missing snapshot
cannot look like no session. Only a bounded 74-byte header prefix is transferred. Full-snapshot SHA-256 is
computed inside the database only when the saved length is at most 8 MiB. More than one
non-closed row, missing snapshot, unsupported or malformed metadata, invalid size,
checksum mismatch, invalid snapshot header, or inconsistent real owner/header
backend fails closed. The saved snapshot is not returned by the SELECT; only its digest and bounded
header are used for validation, never printed or applied. Item payloads are not deserialized.

## Reading the response

Responses contain one target UUID, runtime backend, observation time and status.
A verified response additionally contains session ID, owner and embedded snapshot
backends, state, revision, session start time, schema, byte count and checksum.
No credentials, database endpoint, raw snapshot, player items or provider exception
details are emitted. Console/log output is private operator evidence: do not post
it to GitHub or public chats.

- `METADATA_VERIFIED`: digest and supported header match. This is **not full payload
  validation**, proof of source restoration, or permission to recover.
- `NO_ACTIVE_SESSION`: the bounded query found no non-closed row for this UUID.
  This does not authorize inventory changes or establish proxy handoff completion.
- `INVALID`: evidence is missing, ambiguous or inconsistent. Preserve all state
  and investigate; never clear a session to silence this response.
- `UNAVAILABLE` or `BUSY`: no successful inspection. No response is also not proof.

Every verified result says `snapshot_validation=SHA256_AND_HEADER_ONLY`,
`proxy_handoff=NOT_INSPECTED` and `recovery=NOT_AUTHORIZED`. The metadata can change
after observation; retain the exact session/revision/checksum and obtain separate
proxy transfer/attempt and current player-state evidence before any state-changing
acceptance. A different real backend owner requires that owner's coordination.
`__network_detached__` is an ownership sentinel, not a connectable server.

## TEST review procedure

1. Inspect the exact PR head, applicable hosted checks and source/artifact manifest.
   A locally built candidate is explicitly an **UNMERGED/local test artifact**.
2. Under separately authorized TEST deployment and current Roundtable handoff,
   verify the loaded version and full JAR SHA-256 and storage ACTIVE. Keep the
   affected account offline. Deployment is not implied by a source build.
3. From the trusted TEST console, invoke the diagnostic once for the verified
   canonical UUID. Read its response through the existing authenticated control
   plane; do not downgrade database TLS, open ports or use generic SQL/reflection.
4. Stop on invalid/unavailable evidence, a different owner, concurrent state drift
   or an unverified proxy transfer. No rank grant, manual SQL clear, snapshot replay,
   or blind `staff recover` is part of this procedure.

The diagnostic itself is read-only. Deploying it, restarting TEST, connecting a
client and actual recovery are separate operations with separate evidence and
authorization. Production is outside this TEST review procedure.
