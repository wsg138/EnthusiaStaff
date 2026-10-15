# AI Moderation — dual private staff alerts (Worker B, draft)

**Status:** isolated, OFF by default. No JDA listener, Paper listener, scheduled worker,
API ingestion route, database migration, credential, or live plugin startup is changed.
This PR is **not approval to deploy or send real staff messages**. Owner scope:
[EnthusiaStaff #599](https://github.com/wsg138/EnthusiaStaff/issues/599).

## Architecture / staging flow

`AuthenticatedCaseIngress` accepts only a transport-authenticated peer plus an exact
server/channel scope that passes the injected allowlist. The remote API event schema
is **not final**. The adapter currently takes a typed synthetic `CaseNotice` carrying
origin (Discord or Minecraft), exact scope, case UUID, monotonic revision, priority,
OPEN/CLAIMED/RESOLVED status, exact opaque evidence references, creation and expiry.
No raw message, player welfare disclosure, target username, punishment, or raw
model rationale is accepted or placed on staff cards.

`FileCaseLedger` atomically commits case snapshots and two independent destination
jobs to one JSON file (one process only). A duplicate revision with identical body
is a replay, different body a conflict; stale revisions cannot roll back state.
Each case revision gets a Discord and Paper outbox entry. Revisions are independent
from the D09 linked-alt investigation and the AiMirror receipts owned by Worker C.

`CaseDispatchWorker` sends asynchronously; it leases/persists BEFORE trying a
destination, then records a remote receipt on completed success. A definitive
NOT_SENT result retries with bounded backoff. A timeout, exception or restart
with a leased send becomes UNCERTAIN and will **not auto-repost**. A higher revision
cannot start on that destination while an earlier send is unresolved. An operator
must reconcile a verified remote receipt or verified non-delivery before proceeding.
`unresolved()` exposes outstanding, retry, and unknown states for metrics. The
journal uses temp-file + forced-file write + atomic replacement. It deliberately
does not pretend to provide multi-JVM locking, distributed CAS or a production
MariaDB migration.

`DiscordCaseDelivery` uses the *existing* staff application JDA, private guild,
exact channel, explicit staff role, strict channel privacy checks (including public
role, other roles and member view overrides), safe no-mention metadata card,
and HMAC-authenticated Claim/Resolve button IDs. Confirmed older message IDs are
edited instead of posting a new card; first-send ambiguity blocks retry until
reconciled. `JdaDiscordCaseButtonListener` requires guild/channel, current
staff role, injected fresh linked-rank authority, and `CaseStaffActions` performs
another fresh authority check under the case CAS. **Nothing registers the listener
or constructs a JDA sink at startup.** No public pings.

`PaperCasePrivateDelivery` uses the global scheduler only to enumerate online
players and the **per-player Folia entity scheduler** for current permission
(`enthusiastaff.ai-review.queue`), active/unrestricted Staff Mode, and private
message delivery. No eligible staff -> NOT_SENT so the outbox retries when
staff become available. Paper's confirmed receipt means sendMessage returned;
it is **not a human-read acknowledgement**. `PaperCaseInbox` supplies scoped
read-only active-case entries for a private inventory/list. It is **not yet a
registered clickable GUI**; claim/resolve actions share a domain boundary but
a Paper UI must still wire in fresh permission checks and asynchronous persistence.

## Contract/coordination boundaries

- **Worker A / Moderation API:** choose the *single authority* for revision
  assignment, status updates, immutable event evidence, delivery-visible
  decision fields and claim/resolve semantics. This draft locally increments
  revisions only in a synthetic single-writer sandbox; do not pair it with
  a live independent API revision writer.
- **Worker C:** owns AiMirror publication receipts and exact removal. No
  `AiMirror*`, RoseChat removal, Discord delete, or punishment code is touched.
- **StaffBot/D09 owners:** existing linked-account evidence and evasion outbox
  remain separate; no JDA bootstrap or shared worker worktree edits.
- **Safety flow:** user welfare disclosure never enters this generic
  moderation case surface; private welfare support remains separate #582.
- The deployed service must authenticate signed, body-bound peer identity
  and scope BEFORE invoking ingress. The `Authenticator` interface is not
  itself network authentication. Paper/Discord staff rank resolution and
  persistent distributed claim/resolve authority remain integration work.

## Synthetic verification / required gates

Focused tests cover source/scope rejection, revision replay/advance/stale
and conflicting same-revision updates, independent per-destination ACK,
retry/offline, crash before ACK, unconfirmed-send hold/reconciliation,
cross-revision duplicate fencing, unauthorized/stale actions, owner-only
resolution, concurrent claims, expired notice filtering, and content-free
Discord/Paper presentations.

Before any live staff alert: add a production MariaDB journal/migration with
cross-process lease and transaction semantics; wire final API event and signed
transport; implement Paper in-game private GUI/actions; validate permissions and
rank/duty against real staff actors; verify JDA role/channel privacy and signed
control key rollout; add same-case cross-surface acceptance and restart tests;
check hosted CI/Codacy and security/privacy; perform live shadow acceptance
without real chat/pings; get explicit owner deployment authorization.

**No punishment is created by claim/resolve or delivery. No server restart,
configuration update, real Discord post, SFTP transfer or player-data retrieval
is part of this draft.**



## Coordinator follow-up: canonical staff command transport

New isolated domain adapter: AuthorityBackedCaseActions. Unlike the
development-only CaseStaffActions/FileCaseLedger.action() combination, this
adapter does NOT locally apply CLAIM or RESOLVE and does NOT increment the
read-side revision. It reads a scoped projected case, checks the current
revision/expiry/actor permission/action applicability, then forwards an
explicit case UUID, source origin/scope, expected version, authorized actor,
action and 8–128 character idempotency key through an injected Gateway.

Gateway ACCEPTED means SUBMITTED_AWAITING_EVENT, NOT a locally resolved
case. UI and Discord buttons must continue showing the last authoritative
snapshot until a verified API CASE_REVISION is received and persisted. A
concurrent claimant or new model revision returns STALE, requiring refresh.
RETRYABLE means no confirmed remote case mutation. The API must independently
authenticate the actor, scope, idempotency key, expected revision and staff
permissions at the authoritative transaction boundary; the local check
is only a preflight and may be stale.

The original local CaseStaffActions remains useful only for synthetic sandbox
tests. **Never register it as a production claim/resolve handler.** Production
bootstrap MUST use a genuinely authenticated Gateway implementation, provide
distributed persistent command deduplication and fresh staff role checks.
This PR provides only the typed, test-covered interface; it does not
implement HTTP transport or canonical claim/resolve API endpoints.

CASE_CLOSED / RESOLVED notifications must be generated by the canonical
authority and delivered to the same Discord and Paper case projections;
case IDs must be consistently UUID-formatted at both boundaries. In the
current API prototype, the canonical case revision is maintained by
ContextMonitor; the eventual authoritative service must also serialize
staff commands and AI event revisions into ONE revision sequence. Neither
repository currently has that integrated transport.



## Initial canonical revision mapping

The disabled Moderation API ContextMonitor.start_case() persists the initial
case at revision 0, then claim/follow-up advance to 1, 2, and so on.
The offline StaffBot CaseNotice, CaseDelivery.Key, and
AuthorityBackedCaseActions.Command now accept nonnegative revisions starting
at 0; negative revisions remain invalid. The StaffBot must not renumber
revisions internally, since an off-by-one projection would defeat
expected-version CAS and idempotent replay. A new isolated test validates
initial projection at revision 0, deferred staff claim at expected version 0,
and subsequent authoritative claim at version 1.

This does not create a CASE_CREATED/INITIAL notice in the API or authorize
actual StaffBot action. The eventual authenticated bridge still needs a
complete case-creation payload, scope permissions, canonical origin/UUID
normalization, case-state closure projection and versioned staff command ACK.


## Coordinator Discord delivery safety and initial revision fix

The dormant DiscordCaseDelivery module was refactored into scoped-notice, verified-private-channel, publish and permission-override helpers. It maintains its fail-closed checks for guild, destination role, @everyone public access, other visible roles, and individual member overrides. Its ambiguous HTTP/JDA delivery remains fenced as POSSIBLY_SENT, not automatically retried with a second message. This refactor does not connect a running JDA listener.

DiscordCaseControls parsing now allows the canonical initial revision 0 (but still rejects negative revisions), matching the API case ledger and the revision-zero StaffBot projection. A synthetic signed-control test verifies rev0 CLAIM parsing and cross-server signature rejection. The older revision>=1 rule would have made new initial cases unclaimable through signed controls. These controls still need fresh actor-role attestation, canonical expected revision and dispatcher/resync integration before any live clicks are enabled.
