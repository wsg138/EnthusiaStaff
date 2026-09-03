# Protocol and Network Traffic

This page explains the persistent Paper–Velocity protocol, replay/acknowledgement model, and the external network boundaries reviewers should inspect.

For the overall architecture use [[Architecture]]. For webhook-specific behavior use [[Discord Delivery]]. For review discipline use [[Code Review Guide]].

## Why the protocol exists

EnthusiaStaff runs one Paper plugin on each backend and one Velocity plugin on the proxy. Network moderation and coordination must work when no player is online, so Minecraft plugin messaging is not sufficient as the durable transport.

The `protocol` module provides a persistent authenticated TLS connection between each Paper backend and Velocity. Durable database inbox/outbox state determines **what must be delivered**; the socket is transport, not the authority or exactly-once mechanism.

Primary files:

```text
protocol/src/main/java/net/enthusia/staff/protocol/PersistentChannelClient.java
protocol/src/main/java/net/enthusia/staff/protocol/PersistentChannelServer.java
protocol/src/main/java/net/enthusia/staff/protocol/EnvelopeAuthenticator.java
protocol/src/main/java/net/enthusia/staff/protocol/ReplayGuard.java
protocol/src/main/java/net/enthusia/staff/protocol/FrameTransport.java
protocol/src/main/java/net/enthusia/staff/protocol/EnvelopeCodec.java
```

## Connection topology

Each Paper backend creates the outbound connection; Velocity listens for approved backend connections.

```text
Paper backend
  PersistentChannelClient
       |
       | TLS 1.3 persistent socket
       v
Velocity proxy
  PersistentChannelServer
```

Paper uses `channel.host` / `channel.port`; Velocity uses `channel.bind-address` / `channel.port`. The normal deployment intent is a private network boundary, not an unauthenticated public listener.

Velocity accepts only configured backend IDs with matching application keys. A replacement session for the same backend ID retires the prior connection.

## TLS and application authentication

TLS 1.3 protects the connection. Paper validates the configured server certificate/trust store with hostname verification; Velocity uses its configured key material.

Each application envelope is also HMAC-SHA256 authenticated. A message is rejected before the application handler when identity/version/time/HMAC/replay checks fail.

Secrets and key-store passwords come from protected runtime configuration/environment. They do not belong in Wiki examples, source, logs or exception text.

## Message envelope

Authenticated envelopes include:

- protocol version;
- message UUID;
- sender server ID;
- message type;
- timestamp;
- random nonce;
- bounded JSON payload;
- HMAC over the canonical envelope fields.

The durable message UUID identifies the operation/delivery. The nonce makes a transmitted envelope unique even when a durable message is retried.

## ReplayGuard versus durable idempotency

`ReplayGuard` blocks the same authenticated sender/nonce pair during a bounded in-memory window. It is process-local and intentionally disappears on restart.

It is **not** the durable exactly-once mechanism. Durable safety comes from message IDs, inbox/outbox constraints, application idempotency/revisions and handlers that recognize already-recorded outcomes.

A useful reviewer question is:

> If this process restarts after receiving or sending the bytes but before the final database update, what durable record prevents the business effect from happening twice or disappearing?

## Acknowledgements and retries

The transport is at-least-once.

A receiver verifies/authenticates the envelope, performs the bounded handler, and acknowledges only according to the handler's accepted/durable outcome. The sender retains durable delivery state and retries missing destinations with bounded backoff.

Velocity's network outbox path must distinguish:

- bytes sent;
- message accepted/authenticated;
- business outcome durably recorded;
- acknowledgement received;
- destination delivery terminal versus retryable/dead-letter.

A socket write alone is not proof of durable enforcement.

## Outbound and inbound traffic map

| Source | Destination | Transport | Purpose |
| --- | --- | --- | --- |
| Paper backend | Configured Velocity channel | Persistent TLS 1.3 | Bidirectional moderation/network messages |
| Velocity | Connected Paper backends | Same backend-initiated TLS sessions | Network outbox delivery and acknowledgements |
| Paper / Velocity | Configured MariaDB | JDBC/TCP | Cases, sanctions, reports, journals, staff state, identities, outboxes and recovery |
| Velocity migration runtime | Configured LiteBans source DB | JDBC/TCP | Import/shadow comparison during migration only |
| Velocity Discord delivery worker | Approved Discord webhook routes | HTTPS POST | Sanitized staff notification destinations |
| Trusted website service | Velocity website bridge | Inbound HTTP to `WebsiteApiServer` | Restricted punishment/appeal bridge requests |

The website bridge is inbound from EnthusiaStaff's perspective. It should not be documented as an outbound site client.

The current repository's direct webhook HTTP boundary is owned by `DiscordOutboxWorker`; redirects are disabled and request/route policy is bounded. See [[Discord Delivery]].

Future interactive Discord staff-bot work is a separate runtime boundary. The merged Discord identity/persistence/authorization foundations described in [[Discord Moderation Platform]] do not currently create another live outbound Discord client on `main`.

## Discord notification privacy

The current delivery path must **not** post raw stored payload JSON.

`DiscordEventRenderer` applies destination/event-specific allowlisting and bounds before a staff-facing webhook body is sent. Unexpected nested structures or unsupported payload shapes fail closed rather than being emitted. Automatic mentions are disabled.

This means privacy has two layers:

1. producers should still avoid placing unnecessary sensitive data in a notification intent;
2. the delivery renderer is an explicit final projection boundary and does not blindly forward arbitrary `payload_json`.

Private-message evidence, reporter identity, coordinates, raw/protected network identity, secrets, full staff snapshots, confiscated contents and private appeal material do not belong in Discord notification output.

Adding a new producer/event requires a deliberate renderer/privacy review. See [[Discord Delivery]] and [[Privacy and Data Handling]].

## What stays in-process

Most Minecraft integrations are local plugin API/service calls in the same JVM, for example supported RoseChat, EnthusiaCurrency, EnthusiaMarket, EnthusiaCommend, Floodgate, ViaVersion and CombatLogX adapters.

That does not mean the provider itself has no network behavior; it means EnthusiaStaff should not bypass its supported in-process contract with raw provider SQL, reflection into private internals, or arbitrary external requests.

`StaffVisibilityService` is also an in-process boundary. Vanish data only leaves through an integration that explicitly publishes/consumes it.

## Failure behavior

Expected conservative behavior includes:

- Paper–Velocity disconnect keeps undelivered durable network work pending and blocks unsafe network-authoritative writes as policy requires;
- invalid authentication/version/time/replay fails before the business handler;
- missing MariaDB blocks unsafe new durable writes rather than falling back to process memory;
- Discord notification failures retry/dead-letter without rolling back a valid moderation commit;
- a website/authentication failure does not create a second unauthenticated moderation path;
- reconnects and replacement sessions fence stale callbacks/acks.

## Reviewer checklist

For protocol/network changes verify:

- TLS/trust/hostname verification and secret handling;
- backend/server identity allowlisting;
- frame/message/payload bounds;
- timestamp/nonce/replay checks;
- sender/receiver thread ownership and no Paper/Velocity event-thread socket/JDBC blocking;
- durable inbox/outbox constraints and ACK timing;
- duplicate delivery/restart behavior;
- stale connection/session fencing;
- bounded queues, workers, timeouts, retries and backoff;
- partial backend outage and no-online-player transport;
- producer plus final-projection privacy for Discord/site output;
- firewall/private-network assumptions;
- test evidence versus real topology/staging evidence.

Important tests include protocol authenticator/replay/transport tests and network/Discord outbox integration tests. Passing those tests is not automatically proof of distributed staging; see [[Build and Testing]].

## Go deeper

- [[Architecture]] — module and runtime topology.
- [[Discord Delivery]] — current webhook outbox/renderer/retry behavior.
- [[Discord Moderation Platform]] — merged Discord foundations versus future bot runtime.
- [[Privacy and Data Handling]] — data exposure boundaries.
- [[Recovery and Troubleshooting]] — network/outbox failure procedure.
- [[Developer Code Guide]] — exact source traces.
- [[Code Review Guide]] — distributed/security review.
- [[Build and Testing]] — evidence interpretation.