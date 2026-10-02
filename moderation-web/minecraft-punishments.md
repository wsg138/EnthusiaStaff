# Minecraft punishment flow

The private production panel accepts an existing network player's Minecraft
username or UUID and a configured reason. It prepares the same durable draft
used by the in-game punishment workflow. The review shows the resolved player,
configured escalation consequences, and confirmation expiry.

Confirmation rechecks the linked Discord staff member, current Minecraft staff
authority, target hierarchy, operational mode, and policy. Helper and Developer
actions retain the existing approval routing. The browser cannot supply a staff
rank, sanction type, duration, approval, or actor identity.

The bot sends these operations to the private HUB staff authority endpoint.
Requests authenticate the exact method, route, body digest, timestamp and nonce;
responses are authenticated too. Public history API credentials do not authorize
punishment creation.

Supported configured consequences are warnings, kicks, mutes, public mutes, bans,
network bans and network identity bans. Policies involving inventory, economy or
asset operations remain in the in-game workflow.

Confirmations expire after two minutes and are bound to the actor, Minecraft
target and website session. Repeating a confirmed request returns the same case
or approval request while that binding remains available. If a response is lost,
use **Check status**. After expiry or a HUB restart, check case history before
preparing a replacement; the panel cannot recover an expired in-memory binding.

Discord enforcement remains controlled separately by its existing configuration.
Enabling Minecraft actions does not enable Discord punishments or message deletion.
