# Minecraft player safety-check GUI — isolated integration draft

Status: **code/test-server candidate, not deployed or validated on a real server**.
Only the Paper module changes. StaffBot transport and existing punishment paths
are not modified. Related: [EnthusiaStaff issue #582](https://github.com/wsg138/EnthusiaStaff/issues/582)
and [Moderation API draft #129](https://github.com/wsg138/AI-Moderation-API/pull/129).

## Behavior

When an explicitly enabled API has FINALized a **fresh, credible**
`SELF_HARM_CHECK` source decision, the API creates a private pending check.
The Paper adapter periodically polls for a pending check matching each
connected player's UUID and the configured Minecraft server scope. On a
matching check it opens a private 27-slot inventory with four options:

- **I'm safe:** `SAFE`; no new help alert solely for this response.
- **I might hurt myself / need help:** `NEEDS_HELP`, provides private
  trusted-person/emergency-help guidance, queues staff support alert.
- **I'm not sure:** `UNSURE`, same supportive guidance and staff alert.
- **I'd rather not answer:** `DECLINED`, no new alert solely for declining.

Closing the inventory does not block play. No message deletion, mute, ban,
strike, reputation penalty, public broadcast or chat-model retraining occurs.

Authorized staff with permission `enthusiastaff.safety.alerts` receive a
**private in-game notification**, with only urgency, player UUID and server
scope. When no authorized recipient is online the adapter does not ACK and
the API retains the pending alert. After a successful `sendMessage` call to
an authorized recipient on its entity scheduler, the adapter sends a
receipt-claim ACK. Delivery is **at least once**: duplicate staff
notifications after crash/reconnect are possible and harmless; the receipt
proves only that the trusted adapter asserted a send, not that a person read
the notification. Real staff response and escalation rules are still needed.

The GUI is not a clinical triage service or a replacement for emergency
services. Prompts are sensitive and should not activate on obvious Minecraft
gameplay hyperbole without independently accepted risk/context detection.

## Configuration

Both sides default to disabled. Do not enable on production based only on
unit tests.

In Paper `config.yml`:

```yaml
ai-safety-check:
  enabled: false
  base-url: "https://your-private-api"
  scope-id: "smp"
  reply-client-id: "paper-safety-reply"
  alerts-client-id: "paper-safety-alerts"
  reply-token-environment: ES_SAFETY_REPLY_TOKEN
  alerts-token-environment: ES_SAFETY_ALERTS_TOKEN
```

In API service configuration, use `AI_MOD_SAFETY_CHECKS_ENABLED=true`
**only on isolated staging** with separate credential entries:

- Paper reply client: `safety:respond` only.
- Paper alert client: `safety:alerts` only.
- No `safety:open` client is required for the automated API trigger.
- Staff permission `enthusiastaff.safety.alerts`: grant explicitly to
  staff authorized to see sensitive welfare alerts; default false.
- Supply credentials through secured environment variables (never config,
  Git commits, Java stack traces or Minecraft client payloads).
- Enforce HTTPS except loopback test traffic; don't use a public proxy or
  redirect a bearer-bearing request.

The API source must actually classify a credible `SELF_HARM_CHECK`.
API main still uses a fail-open stub and the model is **not accepted**.
The two opt-in toggles alone do **not** make the trigger safe.

## Staging acceptance — required before any production activation

1. In an isolated Paper test server, send an invented safe gameplay phrase;
   verify no inventory prompt, staff alert, deletion or punishment.
2. With a synthetic, authorized support result, verify only the matching
   subject UUID/scope sees the GUI; another player cannot click it.
3. Click each choice, verify API state and that `NEEDS_HELP`/`UNSURE`
   provide immediate help information and a private authorized staff alert.
4. Revoke `enthusiastaff.safety.alerts` for everyone; verify no alert ACK.
   Restore authorized on-duty staff and verify delivery/ACK with receipt.
5. Test API offline, network timeouts, read-only duplicate retries, server
   reboot, player disconnect/rejoin, cooldowns, expired prompts and urgent
   escalation with no response. Confirm no secrets/logs/public messages.
6. Validate actual accepted classifier false-positive/false-negative
   evidence against independently adjudicated prospective data, plus
   human-approved wording and consent/privacy/retention policies.
7. Verify a production-like sender integration actually populates
   FINAL API moderation responses with a validated support-flow signal.
   A compile success is not evidence this happens.
8. Obtain owner's explicit deployment/enable authorization, assign
   trained staff response ownership, and keep rollback instructions.

Local `:paper:compileJava` and `:paper:test` are not substitutes
for a live Minecraft test-server run. Do not set either flag to true or
grant staff notification permissions without these acceptance gates.
