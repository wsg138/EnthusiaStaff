package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;

final class JdaPrivateReportNotificationTransportTest {
    private static final String GUILD = "123456789012345678";
    private static final String CHANNEL = "223456789012345678";
    private static final String STAFF_ROLE = "323456789012345678";

    private static JDA unavailableDiscord() {
        return (JDA) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{JDA.class},
                (proxy, method, args) -> {
                    if ("getTextChannelById".equals(method.getName())) {
                        return null;
                    }
                    throw new AssertionError("Unexpected JDA method invoked");
                });
    }

    @Test
    void disabledTransportNeitherReadsJdaNorSendsUserText() {
        var jda = unavailableDiscord();
        var transport = new JdaPrivateReportNotificationTransport(
                jda, GUILD, CHANNEL, STAFF_ROLE, false);
        assertFalse(transport.verify().eligible());
        assertEquals(PrivateReportNotificationDispatcher.Outcome.UNCERTAIN,
                transport.send("Private text @everyone"));
    }

    @Test
    void enabledTransportFailsClosedIfPrivateChannelCannotBeResolved() {
        var transport = new JdaPrivateReportNotificationTransport(
                unavailableDiscord(), GUILD, CHANNEL, STAFF_ROLE, true);
        assertFalse(transport.verify().eligible());
        assertEquals(PrivateReportNotificationDispatcher.Outcome.UNCERTAIN,
                transport.send("Report description"));
    }

    @Test
    void invalidDestinationIdsAreRejectedBeforeAnySend() {
        var jda = unavailableDiscord();
        assertThrows(IllegalArgumentException.class, () ->
                new JdaPrivateReportNotificationTransport(
                        jda, GUILD, "not-a-channel", STAFF_ROLE, true));
        assertThrows(IllegalArgumentException.class, () ->
                new JdaPrivateReportNotificationTransport(
                        jda, "0", CHANNEL, STAFF_ROLE, false));
    }
}
