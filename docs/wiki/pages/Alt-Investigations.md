# Alt Investigations

Alts are allowed on the Enthusia Network. EnthusiaStaff tracks relationships to
support ban/mute inheritance and evasion investigations without exposing raw
network addresses to staff.

> **Current status:** Protected network-identity primitives and inheritance
> policy tests exist, but `/alts`, `/alt`, the complete evidence lifecycle,
> staff GUI, key-rotation operations, and staging verification are incomplete.

## Privacy boundary

Staff must never see raw IP addresses. The platform uses:

- Authenticated encryption for recoverable sensitive values
- HMAC equality tokens for matching
- Versioned keys and rotation
- Sanitized logs, Discord messages, GUI text, site output, APIs, and exceptions

Do not request screenshots of IPs, paste addresses into case notes, or build
side databases outside the platform.

## Relationship states

| State | Meaning and expected action |
| --- | --- |
| `SAME_NETWORK` | Network overlap only; not proof of same person |
| `LOW_CONFIDENCE` | Weak indicators; investigate only |
| `SEMI_CONFIDENT` | Multiple indicators, still not enforcement certainty |
| `CONFIDENT` | Strong relationship; active ban/mute may inherit |
| `VERY_CONFIDENT` | Very strong relationship; active ban/mute may inherit |
| `CONFIRMED_ALT` | Staff-confirmed same person; active ban/mute may inherit |
| `APPROVED_ALT` | Recognized alt that is allowed and not suspicious |
| `SHARED_HOUSEHOLD` | Independent people sharing a network; no inheritance |
| `NOT_RELATED` | Staff decision that accounts are unrelated; no inheritance |

`NOT_RELATED` remains until Admin/Founder explicitly reopens it.

## Evidence interpretation

Evidence may include:

- Network overlap
- Rapid account switching
- Timing and session patterns
- Simultaneous independent play
- Long-term network changes
- Client metadata
- Gameplay and social behavior
- Staff confirmation

Independent simultaneous play should reduce same-person confidence. A family,
school, dorm, workplace, VPN exit, mobile carrier, or public network can produce
shared network evidence without shared ownership.

## Restart suppression

Mass reconnects, proxy restarts, backend restarts, startup, and maintenance can
create misleading switching patterns. The platform must suppress this evidence
during recognized maintenance windows.

Do not manually label mass reconnects as evasion.

## Inheritance

Target policy:

- A new same-network account without an established exception inherits the
  exact remaining active ban or mute and links the original case.
- `CONFIDENT`, `VERY_CONFIDENT`, and `CONFIRMED_ALT` inherit active bans/mutes.
- Lower confidence creates alerts only.
- `SHARED_HOUSEHOLD`, `NOT_RELATED`, and `APPROVED_ALT` do not inherit.
- A separate evasion punishment is applied only when intentional evasion is
  proven.

Inheritance is not a fresh full-duration punishment. It mirrors the exact
remaining active sanction unless policy explicitly says otherwise.

## Intended commands

The goals require:

```text
/alts <player>
/alt link ...
/alt approve ...
/alt household ...
/alt notrelated ...
/alt unlink ...
/alt reopen ...
```

These commands are not currently registered in Paper metadata and must not be
documented to staff as available until [[Implementation Status]] changes.

## Investigation procedure

1. Verify both UUIDs and account histories.
2. Review confidence and each evidence category separately.
3. Check maintenance/restart suppression.
4. Look for independent simultaneous play.
5. Check existing approved, household, or not-related decisions.
6. Distinguish automatic inheritance from intentional evasion.
7. Record a factual explanation.
8. Escalate permanent relationship decisions to the required rank.

Do not punish solely because usernames, skins, writing style, or one network
token are similar.
