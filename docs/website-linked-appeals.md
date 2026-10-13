# Linked website punishment appeals

The signed website API can list and submit appeals for punishments belonging to
current Discord-to-Minecraft account links. It does not require punishment-code
claims. The existing bearer/HMAC authentication and replay protection apply.

## Owned punishment list

`POST /v1/website/appeals/linked` accepts `discordUserId`, optional
`minecraftUuid`, `scope` (`ACTIVE`, `EXPIRED`, or `ALL`; default `ACTIVE`) and
optional `cursor`. Each page contains at most 100 punishments and a nullable
`nextCursor`. Records include public reason, type, issue/expiry timestamps,
linked Minecraft identity, active status, appeal availability and the next
available appeal timestamp. Internal case evidence and staff identities are
excluded. Revoked, overturned, pending and fully overturned cases are excluded.

## Submission and interval

`POST /v1/website/appeals/linked/submit` accepts `discordUserId`, `minecraftUuid`,
`punishmentId`, `accountId`, `reason`, and `idempotencyKey`. The website must
supply Discord and website account identity from the verified session. Native
storage checks the current link again and derives the player name from its own
player record. Expired punishments, punishments ended early and warnings are eligible for review.

A canonical sanction row lock serializes submissions. Current locking reads
check the last submission across all appeal attempts. A new attempt is allowed
at the exact 14-day boundary; earlier requests return `APPEAL_COOLDOWN` with
HTTP 429. An identical retry returns the original attempt without extending the
interval. Different punishments retain separate intervals. The existing
account-wide submission rate limit remains in effect.

Each later attempt receives a fresh appeal ID. Existing records, events and
staff decisions remain intact. Replies and permitted edits belong to the
existing appeal thread during cooldown. Acceptance still uses the exact
sanction-change service and existing staff hierarchy; it does not overturn
unrelated sanctions in a combined case. The legacy direct acceptance path
cannot manufacture additional attempts outside the submission workflow.

## Migration and activation

Migration 32 removes the unique punishment constraint, adds an attempt-history
index and widens stored player display names to 32 characters. No existing rows
are deleted. Approval is required before the production migration and proxy
restart. Prepare the runtime locally under the established
`EnthusiaStaff-Velocity.jar` name and keep rollback copies outside the server.
Do not enable the website's `LINKED_PUNISHMENT_APPEALS` flag until the new native
routes pass live acceptance. Do not resume appeal writes using an old runtime
after migration without reviewing compatibility. No economy, inventory or
reward behavior changes; queries remain on the existing website API executor.
