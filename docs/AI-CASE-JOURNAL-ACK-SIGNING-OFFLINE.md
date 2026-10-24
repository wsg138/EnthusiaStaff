# Offline signed case journal acknowledgments

This is an unregistered, default-OFF library test, not a network response handler.

A Staff receiver's canonical seven-field ACK says only that its CASE JOURNAL
accepted, rejected or recognized a duplicate event. Even an authenticated ACK NEVER
proves Discord/Paper published a notice. Publication receipts must be independently
verified, and uncertain sends must not be auto-retried.

## Separate response-only signing key

The signer takes the CaseAck returned from the receiver, exact bound origin/scope/
private guild, epoch seconds and an injected 32+ byte response-only secret. It MUST
be independently provisioned from the API-to-Staff ingress signing key. This class
stores no credentials, connects to no service and registers no route.

It serializes exactly seven ACK keys to sorted, compact UTF-8 JSON: status,
operation_id, event_type, case_id, revision, origin_platform and scope_id.
Supported status values are ACCEPTED, DUPLICATE and REJECTED.
The maximum serialized body is 2048 bytes.

HMAC-SHA256 signs eight newline-separated UTF-8 lines, no trailing newline:
1. enthusia.case.journal-ack.v1
2. POST
3. /v1/ai-case-events
4. exact bound origin
5. exact bound scope
6. private destination guild ID in decimal
7. issued-at seconds in decimal
8. lowercase SHA256 hex of the exact body bytes

Proof returns copied bytes, timestamp and lowercase hex signature.

Synthetic tests use the actual Python API CASE_CREATED event fixture with no real
player/staff data. The Java signature matches an independently calculated Python
golden HMAC. A deterministic test key (bytes 32 through 63) is NEVER a real key.

## Production gates NOT satisfied

Before connection: implement API verification with strict JSON/UTF-8, MAC, expiry,
identity, body digest, status and exact pending-operation matching; provision and rotate
separate keys per direction; authenticate HTTPS/mTLS peer; add bounded replay policy and
durable cross-process state. ACK signing does not certify Discord/Paper publication
or human staff identity. No sanctions, deletes, or staff messages are activated.
