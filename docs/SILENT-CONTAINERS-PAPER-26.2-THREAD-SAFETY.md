# Silent vanished-container packet thread safety (Paper 26.2)

ProtocolLib BLOCK_ACTION packet callbacks may run on network threads. The old
adapter accessed recipient.getWorld() inside that callback. If Bukkit world
access throws from the network thread, its fail-open circuit breaker disables
masking for all subsequent chest and shulker animations, exposing staff actions.

The adapter now reads only an immutable, concurrent cached world UUID. Player
join, world change, quit and inventory open events maintain the cache; the
VanishManager seeds already-online players when installed. Unknown recipients
fail open for that packet, without disabling masking globally.

Staging acceptance required: vanished helper/mod/admin chest and shulker opens,
visible/vanished simultaneous viewers, double chests, world hops, relogging, a
recipient who logs in during staff interaction, no packet-thread IllegalStateException,
and unrelated container animations remain visible. Do not mark fully verified
without real-client testing and passing exact-head CI.
