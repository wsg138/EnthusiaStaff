# Alt Investigations

Alt accounts are allowed on the Enthusia Network. Staff investigate related accounts only when there is a moderation reason, such as punishment evasion, report abuse, or another rule violation.

This page explains the protected network-identity model, staff judgment, and privacy boundaries. Production deployment/cutover is separate from repository implementation status.

## Quick navigation

- General privacy: [[Privacy and Data Handling]]
- Punishment behavior: [[Punishment System]]
- Commands and permissions: [[Commands and Permissions]]
- Feature status and source files: [[Staff Tools, Investigations, and Player-State Safety]]

## The main rule

A shared network does not prove that two accounts belong to the same person. Families, roommates, schools, dorms, workplaces, mobile carriers, public Wi-Fi, and VPN services can all produce legitimate overlap.

The automated model therefore distinguishes a narrow new-account inheritance rule from ordinary relationship confidence:

- a genuinely new account that matches exactly one established network identity after cutover, has no protected exception history, and is not simultaneously online with the matched account may inherit the exact remaining active ban/mute;
- `CONFIDENT`, `VERY_CONFIDENT`, and `CONFIRMED_ALT` relationships may inherit active bans/mutes;
- lower-confidence relationships alert staff rather than inheriting automatically;
- `APPROVED_ALT`, `SHARED_HOUSEHOLD`, and `NOT_RELATED` suppress automatic inheritance;
- broad shared networks are treated as ambiguous and automated graph expansion is suppressed.

Intentional punishment evasion remains a separate staff decision. Automatic inheritance must not be converted into an evasion punishment without evidence of intent.

## Privacy boundary

Staff never need raw IP addresses for this workflow. Runtime address bytes are protected in process using versioned HMAC equality tokens plus authenticated encryption. Staff-facing relationship, audit, alert, Discord, site, and API output contains no raw or reversible address value.

Do not:

- request or send raw addresses;
- paste addresses into notes, Discord, tickets, or `/alt` reasons;
- keep a separate spreadsheet of network information;
- reveal network/household evidence to players;
- copy protected identity data into public output.

Manual relationship reasons reject IPv4/IPv6 literals before the audit transaction, so an address cannot be persisted accidentally through that note path.

## Relationship outcomes

The durable relationship states are:

- `SAME_NETWORK`
- `LOW_CONFIDENCE`
- `SEMI_CONFIDENT`
- `CONFIDENT`
- `VERY_CONFIDENT`
- `CONFIRMED_ALT`
- `APPROVED_ALT`
- `SHARED_HOUSEHOLD`
- `NOT_RELATED`

Automatic network observations create only conservative relationship evidence. Independent simultaneous play lowers an automatically derived `SAME_NETWORK` relationship to `LOW_CONFIDENCE`; it does not overwrite a manual staff decision. Evidence is refreshed at a bounded cadence rather than appended on every reconnect.

`NOT_RELATED` is locked until an authorized reopen action. Reopening changes it to `LOW_CONFIDENCE` so new evidence can be considered again.

## Commands and permissions

Velocity registers:

```text
/alts <player>
/alt <link|approve|household|notrelated|unlink|reopen> <player1> <player2> <reason>
```

- `/alts` requires `enthusiastaff.alts.view`.
- Relationship changes require `enthusiastaff.alts.manage`.
- `reopen` additionally requires `enthusiastaff.alts.reopen`.
- Successful manual decisions are durably audited with the actor, ordered player pair, action, time, and privacy-checked reason.

Command permission is only the entry gate; production rank assignment must still follow the approved LuckPerms/rank policy.

## Punishment inheritance

Inherited sanctions copy the original active ban/mute state and exact expiration. They link to the original sanction/case and are idempotent, so repeated or concurrent proxy observations cannot create the same inherited sanction twice.

Lower-confidence evidence creates staff alerts instead of severe automatic action. Approved-alt, household, and not-related states never inherit automatically.

## Ambiguity controls

The repository implementation adds several fail-safe controls around shared networks:

- automatic matching reads are capped;
- a network with more than the automated match cap suppresses relationship expansion/inheritance for that observation;
- a new-account inheritance candidate must have exactly one match;
- a matched account that is currently online makes the new-account observation ambiguous;
- protected exception history prevents the narrow new-account rule;
- manually managed states are not downgraded by automatic simultaneous-play evidence;
- active sanction reads and relationship listings are bounded.

These controls reduce false-positive risk without redefining the authoritative new-account rule.

## Retention and restart behavior

Relationship decisions are retained indefinitely. Sensitive network identity tokens and detailed alt evidence are separately bounded: the current development implementation removes rows older than 90 days in small ordered batches, while leaving the relationship decision itself intact.

Retention mutations use the same authoritative write fence as other network-identity writes. During maintenance/shadow/non-authoritative modes, automated evidence is suppressed and retention does not mutate authoritative identity state. On restart, durable relationships remain available even when old sensitive evidence has expired.

The current key version is stored with every equality token and encrypted value. Equality matching only occurs inside the same HMAC key version; unknown/mismatched encryption-key versions fail closed during recovery. Production key rotation and representative private-data acceptance remain separate operational work.

## Investigation process

1. Start with the moderation reason for review.
2. Use `/alts <player>` to read the relationship summary; do not seek raw addresses.
3. Check for approved-alt, household, or not-related decisions before acting.
4. Treat independent simultaneous play, large/shared networks, maintenance, and mass reconnects as ambiguity signals.
5. Separate automatic inheritance from intentional evasion.
6. Use the narrowest correct manual state and write a factual reason without network literals.
7. Use `notrelated` for a durable false-positive decision; use `reopen` only with the required authority and new evidence.

## Stop and ask for help when

- the only evidence is ordinary network overlap;
- a real household may be involved;
- accounts play independently at the same time;
- inheritance does not match the original sanction's remaining state;
- maintenance/restart events may explain the evidence;
- raw network information appears anywhere staff can see or store it;
- a command reports conflict, stale state, or recovery.

## Developer source map

- `common/.../NetworkIdentityProtector.java` — HMAC/encryption protection and recovery checks.
- `common/.../NetworkAddressTextGuard.java` — rejects raw address literals from durable staff text.
- `domain/.../alt/AltRelationshipState.java` — relationship states and inheritance-safe exceptions.
- `domain/.../alt/AltInheritancePolicy.java` — narrow new-account and confident-relationship inheritance rules.
- `domain/.../ports/NetworkIdentityStore.java` — graph/manual/retention persistence contract.
- `persistence/.../JdbcNetworkIdentityStore.java` — protected observations, ambiguity controls, inheritance, audit, evidence, and retention.
- `persistence/.../migration/FencedNetworkIdentityStore.java` — authority/write fencing.
- `velocity/.../EnthusiaStaffVelocityPlugin.java` — protected address capture and `/alts`/`/alt` operator surface.

Canonical Java/Floodgate platform identity is owned by ES-P03 and consumed here; do not reimplement that normalization in the alt subsystem.

## Related pages

- [[Staff Handbook]]
- [[Punishment System]]
- [[Privacy and Data Handling]]
- [[Commands and Permissions]]
- [[Staff Tools, Investigations, and Player-State Safety]]
