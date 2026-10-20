package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
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
    void privateMessageActionDisablesAllDiscordMentionsBeforeAnySend() {
        AtomicReference<Collection<?>> mentioned = new AtomicReference<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        var action = (MessageCreateAction) Proxy.newProxyInstance(
                loader, new Class<?>[]{MessageCreateAction.class},
                (proxy, method, args) -> {
                    if ("setAllowedMentions".equals(method.getName())) {
                        mentioned.set((Collection<?>) args[0]);
                        return proxy;
                    }
                    throw new AssertionError("Unexpected action " + method.getName());
                });
        var channel = (TextChannel) Proxy.newProxyInstance(
                loader, new Class<?>[]{TextChannel.class},
                (proxy, method, args) -> {
                    if ("sendMessage".equals(method.getName())) {
                        assertEquals("Untrusted @everyone <@&123>", args[0]);
                        return action;
                    }
                    throw new AssertionError("Unexpected channel " + method.getName());
                });

        assertSame(action, JdaPrivateReportNotificationTransport.privateMessageAction(
                channel, "Untrusted @everyone <@&123>"));
        assertTrue(mentioned.get().isEmpty(), "User text must never ping anyone");
    }

    @Test
    void authorizedPrivateChannelSendsOnlySyntheticTextWithNoMentions() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        AtomicReference<Object> mentions = new AtomicReference<>();
        var delivered = (Message) Proxy.newProxyInstance(
                loader, new Class<?>[]{Message.class},
                (proxy, method, args) -> { throw new AssertionError(method.getName()); });
        var action = (MessageCreateAction) Proxy.newProxyInstance(
                loader, new Class<?>[]{MessageCreateAction.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "setAllowedMentions" -> {
                            mentions.set(args[0]);
                            yield proxy;
                        }
                        case "submit" -> CompletableFuture.completedFuture(delivered);
                        default -> throw new AssertionError(method.getName());
                    };
                });
        var publicRole = (Role) Proxy.newProxyInstance(
                loader, new Class<?>[]{Role.class},
                (proxy, method, args) -> {
                    if ("hasPermission".equals(method.getName())) {
                        return false;
                    }
                    throw new AssertionError(method.getName());
                });
        var staffRole = (Role) Proxy.newProxyInstance(
                loader, new Class<?>[]{Role.class},
                (proxy, method, args) -> {
                    if ("hasPermission".equals(method.getName())) {
                        return true;
                    }
                    throw new AssertionError(method.getName());
                });
        var guild = (Guild) Proxy.newProxyInstance(
                loader, new Class<?>[]{Guild.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> GUILD;
                    case "getRoleById" -> staffRole;
                    case "getPublicRole" -> publicRole;
                    case "getRoles", "getMemberPermissionOverrides" -> List.of();
                    default -> throw new AssertionError(method.getName());
                });
        var channel = (TextChannel) Proxy.newProxyInstance(
                loader, new Class<?>[]{TextChannel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getGuild" -> guild;
                    case "getMemberPermissionOverrides" -> List.of();
                    case "sendMessage" -> {
                        assertEquals("Synthetic test report", args[0]);
                        yield action;
                    }
                    default -> throw new AssertionError(method.getName());
                });
        var self = (SelfUser) Proxy.newProxyInstance(
                loader, new Class<?>[]{SelfUser.class},
                (proxy, method, args) -> {
                    if ("getIdLong".equals(method.getName())) {
                        return 99L;
                    }
                    throw new AssertionError(method.getName());
                });
        var api = (JDA) Proxy.newProxyInstance(
                loader, new Class<?>[]{JDA.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTextChannelById" -> channel;
                    case "getCacheFlags" -> EnumSet.of(CacheFlag.MEMBER_OVERRIDES);
                    case "getSelfUser" -> self;
                    default -> throw new AssertionError(method.getName());
                });
        var sender = new JdaPrivateReportNotificationTransport(
                api, GUILD, CHANNEL, STAFF_ROLE, true);
        assertTrue(sender.verify().eligible());
        assertEquals(PrivateReportNotificationDispatcher.Outcome.CONFIRMED,
                sender.send("Synthetic test report"));
        assertTrue(((java.util.Collection<?>) mentions.get()).isEmpty());
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
