# Privacy and Data Handling

Moderation requires sensitive evidence. EnthusiaStaff is designed to expose the
minimum information needed for each staff decision.

## Data classification

### Public

May be shown on the punishment website when the case is public:

- Player identity
- Punishment type
- Broad and approved exact public reason
- Issue and expiration times
- Remaining duration
- Case state
- Case ID
- Appeal availability

### Staff-confidential

Keep inside approved staff systems:

- Reporter identity
- Internal explanations
- Staff notes
- Report status and claim ownership
- Coordinates and base locations
- Relevant chat context
- Client and anticheat evidence
- Alt confidence and evidence
- Confiscation and economy plans
- Recovery and quarantine details

### Highly restricted

Expose only to the smallest approved operational audience:

- Private messages
- Recoverable network identity
- Encryption/HMAC key versions and rotation details
- Appeal media
- Authentication/session data
- Database credentials
- TLS keys and trust stores
- Webhook and email provider secrets
- Full confiscated asset snapshots

## Discord rules

Private-message evidence must not be sent to Discord. Raw network identity,
coordinates, confiscated contents, secrets, and private appeal media must not
appear in webhook payloads.

Discord summaries should contain stable case/report/operation IDs so authorized
staff can open the detailed record in the correct system.

## Screenshots and exports

Before sharing a screenshot:

1. Crop unrelated player and staff information.
2. Remove coordinates, raw addresses, tokens, secrets, and private messages.
3. Check hover text, filenames, browser tabs, terminal history, and sidebars.
4. Use an approved private incident channel.
5. Link the authoritative case instead of creating an uncontrolled evidence
   copy when possible.

## Retention

The target design retains chat/private-message report context for 7 days,
inventory snapshots for 30 days, and relationship decisions according to the
alt policy. Exact deployed retention must come from validated configuration.

Do not manually retain sensitive data “just in case” outside approved storage.

## Public explanations

A public reason should explain the rule violation without exposing how a
private detection system works or revealing another player's information.

Bad:

> Banned because account X used IP 203.0.113.1 and messaged Y at their base.

Better:

> Ban evasion linked to an active network ban.

The detailed evidence belongs in the internal case.

## Access discipline

Read access is also authority. Staff should access sensitive records only for:

- An assigned report or case
- An active investigation
- A legitimate appeal review
- Approved recovery
- Security auditing
- Authorized development or staging verification

Curiosity is not a valid staff purpose.
