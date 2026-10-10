package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class JdbcAltMuteEvasionAlertStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-10T18:00:00Z");

    private final DataSource unreachable = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{DataSource.class},
            (proxy, method, args) -> { throw new AssertionError("Unqualified alerts must not access the database"); }
    );
    private final JdbcAltMuteEvasionAlertStore store =
            new JdbcAltMuteEvasionAlertStore(unreachable, new ObjectMapper());

    @Test
    void noAlertForDirectMuteOrNonMuteInheritedSanction() {
        UUID player = UUID.randomUUID();
        assertFalse(store.recordBlockedChat(player, sanction(player, SanctionType.MUTE, Optional.empty()), "SMP", NOW));
        assertFalse(store.recordBlockedChat(player,
                sanction(player, SanctionType.BAN, Optional.of(UUID.randomUUID())), "SMP", NOW));
    }

    @Test
    void mismatchedTargetCannotCreateAltAlert() {
        UUID actual = UUID.randomUUID();
        assertFalse(store.recordBlockedChat(UUID.randomUUID(),
                sanction(actual, SanctionType.PUBLIC_MUTE, Optional.of(UUID.randomUUID())), "SMP", NOW));
    }

    @Test
    void validationRejectsMissingInputsWithoutPersisting() {
        UUID player = UUID.randomUUID();
        ActiveSanction mute = sanction(player, SanctionType.MUTE, Optional.of(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> store.recordBlockedChat(null, mute, "SMP", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> store.recordBlockedChat(player, mute, "", NOW));
    }

    @Test
    void suspectedChatRequiresExactActorServerAndTime() {
        UUID player = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> store.recordSuspectedChat(null, "SMP", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> store.recordSuspectedChat(player, "", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> store.recordSuspectedChat(player, "SMP", null));
    }

    private static ActiveSanction sanction(UUID player, SanctionType type, Optional<UUID> inherited) {
        return new ActiveSanction(
                UUID.randomUUID(), new CaseId("0123456789ABCDEF"), player,
                type, "Staff moderation", NOW, Optional.empty(), inherited
        );
    }
}
