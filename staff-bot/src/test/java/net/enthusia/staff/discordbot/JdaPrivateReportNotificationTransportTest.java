package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import java.util.Collection;



import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;

final class JdaPrivateReportNotificationTransportTest {
    private static final String GUILD = "123456789012345678";
    private static final String CHANNEL = "223456789012345678";
    private static final String STAFF_ROLE = "323456789012345678";
    private static final String MESSAGE_ID = "423456789012345678";

    private static JDA unavailableDiscord() {
        return (JDA) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{JDA.class},
                (proxy, method, args) -> {
                    if ("getStatus".equals(method.getName())) {
                        return JDA.Status.CONNECTED;
                    }
                    if ("getUnavailableGuilds".equals(method.getName())) {
                        return Set.of();
                    }
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
        assertEquals(PrivateReportNotificationDispatcher.DeliveryReceipt.uncertain(),
                transport.send("Private text @everyone"));
    }

    @Test
    void enabledTransportFailsClosedIfPrivateChannelCannotBeResolved() {
        var transport = new JdaPrivateReportNotificationTransport(
                unavailableDiscord(), GUILD, CHANNEL, STAFF_ROLE, true);
        assertFalse(transport.verify().eligible());
        assertEquals(PrivateReportNotificationDispatcher.DeliveryReceipt.uncertain(),
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
        AtomicReference<Object> mentions = new AtomicReference<>();
        var delivered = proxy(Message.class,
                (instance, method, args) -> {
                    if ("getId".equals(method.getName())) {
                        return MESSAGE_ID;
                    }
                    throw new AssertionError(method.getName());
                });
        var action = syntheticAction(mentions, delivered);
        var guild = syntheticGuild(syntheticRole(false), syntheticRole(true));
        var channel = syntheticChannel(guild, action);
        var api = syntheticApi(channel, syntheticSelfUser());
        var sender = new JdaPrivateReportNotificationTransport(
                api, GUILD, CHANNEL, STAFF_ROLE, true);

        assertTrue(sender.verify().eligible());
        assertEquals(PrivateReportNotificationDispatcher.DeliveryReceipt.confirmed(MESSAGE_ID),
                sender.send("Synthetic test report"));
        assertTrue(((java.util.Collection<?>) mentions.get()).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type}, handler);
    }

    private static MessageCreateAction syntheticAction(
            AtomicReference<Object> mentions, Message delivered) {
        return proxy(MessageCreateAction.class, (instance, method, args) -> {
            if ("setAllowedMentions".equals(method.getName())) {
                mentions.set(args[0]);
                return instance;
            }
            if ("submit".equals(method.getName())) {
                return CompletableFuture.completedFuture(delivered);
            }
            throw new AssertionError(method.getName());
        });
    }

    private static Role syntheticRole(boolean mayView) {
        return proxy(Role.class, (instance, method, args) -> {
            if ("hasPermission".equals(method.getName())) {
                return mayView;
            }
            throw new AssertionError(method.getName());
        });
    }

    private static Guild syntheticGuild(Role publicRole, Role staffRole) {
        return syntheticGuild(publicRole, staffRole, true, true);
    }

    private static Guild syntheticGuild(
            Role publicRole, Role staffRole, boolean botCanView, boolean botCanSend) {
        return syntheticGuild(publicRole, staffRole, () -> botCanView, () -> botCanSend);
    }

    private static Guild syntheticGuild(
            Role publicRole, Role staffRole,
            java.util.function.BooleanSupplier botCanView,
            java.util.function.BooleanSupplier botCanSend) {
        var self = proxy(SelfMember.class, (instance, method, args) -> {
            if ("hasPermission".equals(method.getName())) {
                var requested = (Permission[]) args[1];
                return java.util.Arrays.stream(requested).allMatch(permission ->
                        (permission == Permission.VIEW_CHANNEL && botCanView.getAsBoolean())
                        || (permission == Permission.MESSAGE_SEND && botCanSend.getAsBoolean()));
            }
            throw new AssertionError(method.getName());
        });
        return proxy(Guild.class, (instance, method, args) -> switch (method.getName()) {
            case "getId" -> GUILD;
            case "getRoleById" -> staffRole;
            case "getPublicRole" -> publicRole;
            case "getRoles" -> List.of();
            case "getSelfMember" -> self;
            default -> throw new AssertionError(method.getName());
        });
    }

    private static TextChannel syntheticChannel(Guild guild, MessageCreateAction action) {
        return proxy(TextChannel.class, (instance, method, args) -> {
            if ("getGuild".equals(method.getName())) {
                return guild;
            }
            if ("getId".equals(method.getName())) {
                return CHANNEL;
            }
            if ("getMemberPermissionOverrides".equals(method.getName())) {
                return List.of();
            }
            if ("sendMessage".equals(method.getName())) {
                assertEquals("Synthetic test report", args[0]);
                return action;
            }
            throw new AssertionError(method.getName());
        });
    }

    private static SelfUser syntheticSelfUser() {
        return proxy(SelfUser.class, (instance, method, args) -> {
            if ("getIdLong".equals(method.getName())) {
                return 99L;
            }
            throw new AssertionError(method.getName());
        });
    }

    private static JDA syntheticApi(TextChannel channel, SelfUser selfUser) {
        return syntheticApi(channel, selfUser, EnumSet.of(CacheFlag.MEMBER_OVERRIDES));
    }

    private static JDA syntheticApi(
            TextChannel channel, SelfUser selfUser, EnumSet<CacheFlag> cacheFlags) {
        return syntheticApi(channel, selfUser, cacheFlags, JDA.Status.CONNECTED, Set.of());
    }

    private static JDA syntheticApi(
            TextChannel channel, SelfUser selfUser, EnumSet<CacheFlag> cacheFlags,
            JDA.Status status, Set<String> unavailableGuilds) {
        return proxy(JDA.class, (instance, method, args) -> switch (method.getName()) {
            case "getStatus" -> status;
            case "getUnavailableGuilds" -> unavailableGuilds;
            case "getTextChannelById" -> channel;
            case "getCacheFlags" -> cacheFlags;
            case "getSelfUser" -> selfUser;
            default -> throw new AssertionError(method.getName());
        });
    }

    @Test
    void publicViewOrMissingStaffAccessNeverSendsReportContent() {
        assertPrivateSendBlocked(syntheticGuild(syntheticRole(true), syntheticRole(true)));
        assertPrivateSendBlocked(syntheticGuild(syntheticRole(false), syntheticRole(false)));
    }

    private static void assertPrivateSendBlocked(Guild guild) {
        var api = syntheticApi(neverSendChannel(guild, List.of()), syntheticSelfUser());
        var sender = new JdaPrivateReportNotificationTransport(
                api, GUILD, CHANNEL, STAFF_ROLE, true);
        assertFalse(sender.verify().eligible());
        assertEquals(PrivateReportNotificationDispatcher.DeliveryReceipt.uncertain(),
                sender.send("Synthetic private report text"));
    }

    @Test
    void staffRoleAccessWithoutBotViewOrSendPermissionCannotLeaseOrSend() {
        var publicRole = syntheticRole(false);
        var staffRole = syntheticRole(true);
        assertPrivateSendBlocked(syntheticGuild(publicRole, staffRole, false, true));
        assertPrivateSendBlocked(syntheticGuild(publicRole, staffRole, true, false));
        assertPrivateSendBlocked(syntheticGuild(publicRole, staffRole, false, false));
    }

    @Test
    void revokingBotSendPermissionAfterPreflightStopsDelivery() {
        var canSend = new AtomicBoolean(true);
        var guild = syntheticGuild(syntheticRole(false), syntheticRole(true),
                () -> true, canSend::get);
        var channel = neverSendChannel(guild, List.of());
        var sender = new JdaPrivateReportNotificationTransport(
                syntheticApi(channel, syntheticSelfUser()),
                GUILD, CHANNEL, STAFF_ROLE, true);

        assertTrue(sender.verify().eligible());
        canSend.set(false);
        assertEquals(PrivateReportNotificationDispatcher.DeliveryReceipt.uncertain(),
                sender.send("Must not send after bot permission revocation"));
    }

    @Test
    void missingOverrideCacheOrUnexpectedMemberViewCannotSendReportContent() {
        var guild = syntheticGuild(syntheticRole(false), syntheticRole(true));
        var channel = neverSendChannel(guild, List.of());
        var noCache = syntheticApi(channel, syntheticSelfUser(),
                EnumSet.noneOf(CacheFlag.class));
        var noCacheSender = new JdaPrivateReportNotificationTransport(
                noCache, GUILD, CHANNEL, STAFF_ROLE, true);
        assertFalse(noCacheSender.verify().eligible());
        assertEquals(PrivateReportNotificationDispatcher.DeliveryReceipt.uncertain(),
                noCacheSender.send("Never send"));

        var extraViewer = proxy(PermissionOverride.class, (instance, method, args) -> {
            if ("getIdLong".equals(method.getName())) {
                return 123L;
            }
            if ("getAllowed".equals(method.getName())) {
                return EnumSet.of(Permission.VIEW_CHANNEL);
            }
            throw new AssertionError(method.getName());
        });
        var extraChannel = neverSendChannel(guild, List.of(extraViewer));
        var extraSender = new JdaPrivateReportNotificationTransport(
                syntheticApi(extraChannel, syntheticSelfUser()),
                GUILD, CHANNEL, STAFF_ROLE, true);
        assertFalse(extraSender.verify().eligible());
        assertEquals(PrivateReportNotificationDispatcher.DeliveryReceipt.uncertain(),
                extraSender.send("Never send"));
    }

    private static TextChannel neverSendChannel(
            Guild guild, List<PermissionOverride> overrides) {
        return proxy(TextChannel.class, (instance, method, args) -> {
            if ("getGuild".equals(method.getName())) {
                return guild;
            }
            if ("getId".equals(method.getName())) {
                return CHANNEL;
            }
            if ("getMemberPermissionOverrides".equals(method.getName())) {
                return overrides;
            }
            throw new AssertionError("Private channel must not be used: " + method.getName());
        });
    }

    @Test
    void disconnectedGatewayAndUnavailableGuildCannotSendFromCachedPermissions() {
        var guild = syntheticGuild(syntheticRole(false), syntheticRole(true));
        var staleChannel = neverSendChannel(guild, List.of());
        var cache = EnumSet.of(CacheFlag.MEMBER_OVERRIDES);
        var disconnected = new JdaPrivateReportNotificationTransport(
                syntheticApi(staleChannel, syntheticSelfUser(), cache,
                        JDA.Status.DISCONNECTED, Set.of()),
                GUILD, CHANNEL, STAFF_ROLE, true);
        assertFalse(disconnected.verify().eligible());
        assertEquals(PrivateReportNotificationDispatcher.DeliveryReceipt.uncertain(),
                disconnected.send("Must not send while Discord is offline"));

        var guildUnavailable = new JdaPrivateReportNotificationTransport(
                syntheticApi(staleChannel, syntheticSelfUser(), cache,
                        JDA.Status.CONNECTED, Set.of(GUILD)),
                GUILD, CHANNEL, STAFF_ROLE, true);
        assertFalse(guildUnavailable.verify().eligible());
        assertEquals(PrivateReportNotificationDispatcher.DeliveryReceipt.uncertain(),
                guildUnavailable.send("Must not send to an unavailable guild"));
    }

    @Test
    void mismatchedCachedChannelIdCannotReceivePrivateReportText() {
        var guild = syntheticGuild(syntheticRole(false), syntheticRole(true));
        var wrongChannel = proxy(TextChannel.class, (instance, method, args) -> {
            if ("getId".equals(method.getName())) {
                return "923456789012345678";
            }
            if ("getGuild".equals(method.getName())) {
                return guild;
            }
            throw new AssertionError("Mismatched channel must not be used: " + method.getName());
        });
        var sender = new JdaPrivateReportNotificationTransport(
                syntheticApi(wrongChannel, syntheticSelfUser()),
                GUILD, CHANNEL, STAFF_ROLE, true);
        assertFalse(sender.verify().eligible());
        assertEquals(PrivateReportNotificationDispatcher.DeliveryReceipt.uncertain(),
                sender.send("Must not send to another channel"));
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
