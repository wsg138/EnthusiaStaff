# Protocol and Network Traffic

This page explains what the `protocol` module does, why `ReplayGuard` exists, and
where EnthusiaStaff sends network traffic.

## Quick navigation

- [[Purpose of the protocol|Protocol-and-Network-Traffic#purpose-of-the-protocol]]
- [[Connection topology|Protocol-and-Network-Traffic#connection-topology]]
- [[Message envelope|Protocol-and-Network-Traffic#message-envelope]]
- [[ReplayGuard|Protocol-and-Network-Traffic#replayguard]]
- [[Acknowledgements and retries|Protocol-and-Network-Traffic#acknowledgements-and-retries]]
- [[Outbound traffic map|Protocol-and-Network-Traffic#outbound-traffic-map]]
- [[What does not leave the process|Protocol-and-Network-Traffic#what-does-not-leave-the-process]]
- [[Review checklist|Protocol-and-Network-Traffic#review-checklist]]

## Purpose of the protocol

EnthusiaStaff has one Paper plugin on each backend and one Velocity plugin on the
proxy. They need to exchange moderation events even when no player is online.
Minecraft plugin messaging is tied to player connections, so it is not sufficient
for durable network-wide enforcement.

The `protocol` module provides a persistent, authenticated, bidirectional TLS
connection between each Paper backend and Velocity. It carries messages such as
network enforcement updates and acknowledgements. Durable database outboxes decide
what must be sent; the socket is only the transport.

Important source files:

```text
protocol/src/main/java/net/enthusia/staff/protocol/PersistentChannelClient.java
protocol/src/main/java/net/enthusia/staff/protocol/PersistentChannelServer.java
protocol/src/main/java/net/enthusia/staff/protocol/EnvelopeAuthenticator.java
protocol/src/main/java/net/enthusia/staff/protocol/ReplayGuard.java
protocol/src/main/java/net/enthusia/staff/protocol/FrameTransport.java
protocol/src/main/java/net/enthusia/staff/protocol/EnvelopeCodec.java
```

## Connection topology

Each Paper backend creates the outbound connection. Velocity listens for backend
connections.

```text
Paper backend
  PersistentChannelClient
       |
       | TLS 1.3 persistent socket
       v
Velocity proxy
  PersistentChannelServer
```

Paper reads the destination from:

```text
channel.host
channel.port
```

The current defaults are `127.0.0.1` and `28765`. In deployment, `channel.host`
should point to the private address or hostname where the Velocity channel server
is listening. It should not be exposed to the public internet unless the network
and firewall design explicitly require that and have been reviewed.

Velocity reads its listening address and port from:

```text
channel.bind-address
channel.port
```

The server allows only configured backend IDs with matching keys. A second
connection for the same backend ID replaces the earlier session.

## TLS and authentication

The channel uses TLS 1.3. Paper validates the configured server certificate using
its trust store and enables hostname verification. Velocity uses its configured
key store for the listening socket.

TLS protects the connection in transit. EnthusiaStaff also signs each application
message with HMAC-SHA256. This provides an application-level identity and integrity
check even inside the TLS connection.

A message is rejected when:

- the backend or proxy ID is unknown;
- the protocol version does not match;
- the timestamp is too old or too far in the future;
- the HMAC is malformed or incorrect;
- the nonce was already accepted during the replay window.

Secrets and key-store passwords are loaded from environment variables. They must
not be written into Wiki pages, YAML, logs, or Git history.

## Message envelope

Every authenticated envelope includes:

- protocol version;
- message UUID;
- sender server ID;
- message type;
- timestamp;
- random nonce;
- JSON payload;
- HMAC over the canonical form of all fields above.

`EnvelopeAuthenticator` serializes the fields in a fixed order and signs the
result. Verification recomputes the HMAC with the key assigned to the sender and
uses constant-time comparison.

The message UUID identifies the durable operation or delivery. The nonce serves a
different purpose: it makes every transmitted envelope unique even when the same
message must be retried.

## ReplayGuard

`ReplayGuard` prevents an already accepted authenticated envelope from being
accepted again during a short time window.

It stores this key in memory:

```text
server ID + nonce
```

Before accepting a message, it:

1. removes expired entries;
2. checks whether the sender/nonce pair has already been seen;
3. rejects the envelope as `REPLAYED` when it is a duplicate;
4. otherwise stores it until the retention time expires;
5. evicts the oldest entries if the configured maximum is exceeded.

The current verification guards retain up to 100,000 entries for three minutes.
Access is synchronized because a `LinkedHashMap` is used for ordered eviction.

### What ReplayGuard does not do

ReplayGuard is process-local and intentionally short-lived. Restarting the process
clears its memory. It is not the durable exactly-once mechanism.

Durable protection comes from message IDs, inbox/outbox database constraints,
idempotency keys, revision checks, and application handlers that return the prior
result rather than repeating an effect. ReplayGuard blocks immediate wire replay;
the persistence layer prevents a retried durable message from applying the same
punishment or mutation twice.

## Acknowledgements and retries

The transport is at-least-once, not exactly-once.

A sender keeps a pending future for the message ID. The receiver verifies the
envelope, handles it, and sends a signed `ACK` only when the handler reports that
the message was accepted. The ACK payload contains the original message ID.

Velocity's `NetworkOutboxWorker`:

1. claims a bounded batch of due messages from MariaDB;
2. prepares one delivery record per required backend;
3. sends through the backend's existing authenticated session;
4. waits for an acknowledgement;
5. records successful destinations;
6. retries missing destinations with bounded backoff;
7. dead-letters the message after the configured attempt limit.

An acknowledgement should mean the receiver durably recorded the outcome, not
merely that bytes reached the socket.

## Outbound traffic map

| Source | Destination | Transport | Purpose |
| --- | --- | --- | --- |
| Paper backend | Configured Velocity channel host and port | Persistent TLS 1.3 socket | Bidirectional moderation and network messages |
| Velocity | Connected Paper backends | The same backend-initiated TLS sessions | Durable network-outbox delivery and acknowledgements |
| Paper and Velocity | Configured MariaDB JDBC endpoint | JDBC/TCP | Cases, sanctions, reports, journals, outboxes, staff state, audit, and recovery |
| Velocity migration worker | Configured LiteBans database | JDBC/TCP | Import and shadow comparison during migration only |
| Velocity Discord worker | Four configured Discord webhook URLs | HTTPS POST | `punishments`, `reports`, `logs-staffmode`, and `alerts` destinations |
| Website or trusted site service | Velocity website API bind address | Inbound HTTP to `WebsiteApiServer` | Restricted punishment/appeal bridge requests |

The website API row is inbound from the perspective of EnthusiaStaff. The plugin
listens on its configured bind address and does not use `HttpClient` to call the
website.

The current repository contains one direct outbound HTTP client:
`DiscordOutboxWorker`. It sends HTTPS webhook requests, disables redirects, sets a
request timeout, and disables automatic mentions in the JSON body.

## What data goes to Discord

Discord delivery begins with a durable outbox row. The worker chooses one of four
configured webhook destinations and sends the event type plus a bounded summary of
the sanitized JSON payload.

Review the producer of every Discord event, not only the worker. Reporter identity,
private messages, coordinates, raw network identity, internal secrets, and private
appeal material must not be placed into an outbound payload.

## What does not leave the process

Most optional Minecraft integrations are local plugin API calls inside the same
server process. Examples include supported integrations with RoseChat,
EnthusiaCurrency, EnthusiaMarket, EnthusiaCommend, Floodgate, ViaVersion, and
CombatLogX. They may have their own network behavior, but EnthusiaStaff is not
supposed to bypass them with raw database writes or arbitrary external requests.

`WebsiteApiServer` is an inbound server. `StaffVisibilityService` is an in-process
Bukkit service. Vanish does not transmit visibility data to an external service
unless an integration adapter explicitly consumes it.

## Failure behavior

- If Paper cannot connect to Velocity, network-authoritative writes remain blocked
  or degraded according to operational mode.
- Undelivered network messages stay in the durable outbox.
- A disconnected backend is retried; the worker does not assume delivery.
- Discord failures use bounded retries, a circuit breaker, and dead-letter state.
- Missing MariaDB blocks unsafe new writes rather than falling back to memory.
- Invalid authentication, unsupported versions, expired messages, and replays are
  rejected before the application handler runs.

## Review checklist

When reviewing protocol or network changes, check:

- the exact configured bind and destination addresses;
- TLS 1.3, trust-store, hostname-verification, and secret handling;
- server-ID allowlisting and key ownership;
- message-size and frame bounds;
- timestamp and nonce validation;
- replay retention and maximum-entry bounds;
- ACK timing and durable handler semantics;
- duplicate delivery after restart;
- bounded connection threads, queues, retries, and backoff;
- no game-thread or Velocity event-thread socket/JDBC blocking;
- payload sanitization before Discord or website exposure;
- firewall expectations and whether the channel is limited to the private network.

Related tests include `PersistentChannelTransportTest`,
`EnvelopeAuthenticatorTest`, `ReplayGuardTest`, and the network/Discord outbox
integration tests.