package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.sanction.ActiveSanction;

/** Audits blocked chat by accounts already carrying an inherited mute. */
public interface AltMuteEvasionAlertStore {
    boolean recordBlockedChat(UUID playerId, ActiveSanction inheritedMute, String serverId, Instant now);

    /** Review-only notification for below-threshold suspected alts speaking while a peer is sanctioned. */
    boolean recordSuspectedChat(UUID playerId, String serverId, Instant now);
}
