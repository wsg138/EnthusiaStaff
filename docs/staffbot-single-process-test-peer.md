# StaffBot single-process TEST chat peer

Status: SOURCE ONLY, NOT DEPLOYED OR ACCEPTED. Tracks #264 and draft #658.

Reuse the existing Bloom StaffBot Java process, Enthusia Discord guild,
public-chat Discord application/token and private staff-bot-testing channel.
No second JDA login, new Discord application, or new Bloom service.

## Isolation
The existing production STAFFBOT Velocity connection stays pinned to
SMP/global -> private test channel. The optional separate authenticated
STAFFBOT_TEST connection in the SAME JVM accepts only TEST/global -> that
same private channel. Both deliver through the existing public-chat JDA.
No TEST Discord ingress, staff role writes or console command route.

Default-off private chat file additions:
- ENTHUSIA_STAFF_BOT_TEST_CHAT_ENABLED=true
- ENTHUSIA_STAFF_BOT_TEST_CHAT_CLIENT_SECRET=<distinct base64 HMAC key>

Keep the primary chat configuration in SHADOW, with exactly the SMP/global
route to the private channel and no ingress. Actual decoded HMAC material
must differ between primary and TEST peers. Never copy tokens or keys to Git,
issue comments or another active Java process.

## Controlled rollout (operator authorization needed for each live step)
1. Preserve SHA hashes, active JAR/config backups, and an exact rollback.
   Verify the existing production public-chat JDA is healthy and unique.
2. Provision STAFFBOT_TEST on Velocity with a separate environment-secret
   identifier and a distinct actual key matching TEST_CHAT_CLIENT_SECRET.
   Preserve STAFFBOT/TEST and all SMP peers. Restart only in approved
   proxy maintenance and verify no SMP interruption.
3. Deploy a validated StaffBot build and opt in the TEST peer in its existing
   private chat settings. Restart only the existing StaffBot service in
   maintenance; verify main staff interactions, public-chat JDA, both
   authenticated peers, and role/console stability WITHOUT posting messages.
4. Validate the precise TEST Paper build, contain TEST DiscordSRV console and
   other writers, then enable outgoing TEST Paper SHADOW in an approved
   TEST maintenance window. Leave live SMP DiscordSRV unchanged.
5. Publish synthetic TEST player messages to the already existing private
   Discord channel; test accurate attribution, correct routing, TTL,
   permissions, redaction, duplicates, reconnect and rejection of ingress.
6. Test rich item/inventory/endchest previews only with PR #568's approved
   candidate; run IC-01..IC-14 screenshots before fidelity acceptance.
   Separately collect real D13/LumaGuilds/PlayTime role parity windows.

## Immediate rollback
On unexpected public posts, repeated JDA session, SMP chat regression,
unauthorized role/console change, leaked inventory, duplicate delivery,
or lost transport readiness: disable TEST publishing and TEST peer, restore
the recorded configs and JARs only through an approved maintenance window.
Never remove production DiscordSRV or enable authoritative chat in this test.

No live changes, credential access, or Discord sends are authorized by this
runbook alone.
