package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction;
import net.dv8tion.jda.api.utils.TimeUtil;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordAuthorizationLimits;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

final class JdaMuteRolePaginationTest {
    private static final UUID PUNISHMENT_ID = UUID.fromString("6ebc2fc4-cbce-4a52-92f8-aa1183899fd3");
    private static final Instant ISSUED_AT = Instant.parse("2026-09-14T20:00:00Z");
    private static final long TARGET_ID = 123L;
    private static final long BOT_ID = 456L;
    private static final long ROLE_ID = 789L;
    private static final long GUILD_ID = 987L;
    private static final String OWNED_REASON = JdaMuteRoleOwnership.marker(PUNISHMENT_ID) + " reason";

    @Test
    void requireOwnedCurrentRoleRequestsLaterAuditPage() {
        AuditPages pages = pages(List.of(unrelatedPage(), List.of(ownedAssignment(ISSUED_AT.plusSeconds(1)))));
        JdaMuteRoleOwnership ownership = new JdaMuteRoleOwnership();

        assertDoesNotThrow(() -> ownership.requireOwnedCurrentRole(guild(pages), role(), punishment()));

        assertEquals(2, pages.pageRequests());
        assertEquals(1, pages.streamCalls());
        assertEquals(0, pages.completeCalls());
    }

    @Test
    void newerExternalAddOverridesOlderBotOwnershipAcrossPages() {
        assertLatestExternalChangeRejected(externalAssignment(ISSUED_AT.plusSeconds(2)));
    }

    @Test
    void newerExternalRemovalOverridesOlderBotOwnershipAcrossPages() {
        assertLatestExternalChangeRejected(externalRemoval(ISSUED_AT.plusSeconds(2)));
    }

    @Test
    void issuanceBoundaryStopsBeforeOlderOwnership() {
        AuditLogEntry beforeBoundary = unrelatedEntry(TARGET_ID + 500, ISSUED_AT.minusSeconds(61));
        AuditLogEntry staleOwned = ownedAssignment(ISSUED_AT.minusSeconds(120));
        AuditPages pages = pages(List.of(unrelatedPage(), List.of(beforeBoundary, staleOwned)));

        DiscordPunishmentGateway.EffectException failure = assertOwnershipRejected(pages);

        assertEquals("MUTE_ROLE_OWNERSHIP_UNVERIFIED", failure.errorCode());
        assertTrue(failure.retryable());
        assertEquals(2, pages.pageRequests());
    }

    @Test
    void emptyLaterPageTerminatesCleanly() {
        AuditPages pages = pages(List.of(unrelatedPage(), List.of()));

        DiscordPunishmentGateway.EffectException failure = assertOwnershipRejected(pages);

        assertEquals("MUTE_ROLE_OWNERSHIP_UNVERIFIED", failure.errorCode());
        assertTrue(failure.retryable());
        assertEquals(2, pages.pageRequests());
    }

    @Test
    void laterPageTransportFailureFailsClosedAndRemainsRetryable() {
        AuditPages pages = pages(List.of(unrelatedPage(), List.of())).failAtPage(1);
        AtomicInteger removals = new AtomicInteger();
        Guild guild = gatewayGuild(pages, removals);
        JdaDiscordPunishmentGateway gateway = new JdaDiscordPunishmentGateway(configuration());
        gateway.bind(jda(guild));

        DiscordPunishmentGateway.EffectException failure = assertThrows(
                DiscordPunishmentGateway.EffectException.class,
                () -> gateway.remove(appliedPunishment())
        );

        assertEquals("REMOVE_TRANSPORT_FAILURE", failure.errorCode());
        assertTrue(failure.retryable());
        assertEquals(2, pages.pageRequests());
        assertEquals(0, removals.get());
    }

    private static void assertLatestExternalChangeRejected(AuditLogEntry external) {
        AuditPages pages = pages(List.of(
                unrelatedPage(),
                List.of(external, ownedAssignment(ISSUED_AT.plusSeconds(1)))
        ));

        DiscordPunishmentGateway.EffectException failure = assertOwnershipRejected(pages);

        assertEquals("MUTE_ROLE_OWNERSHIP_UNVERIFIED", failure.errorCode());
        assertEquals(2, pages.pageRequests());
    }

    private static DiscordPunishmentGateway.EffectException assertOwnershipRejected(AuditPages pages) {
        JdaMuteRoleOwnership ownership = new JdaMuteRoleOwnership();
        return assertThrows(
                DiscordPunishmentGateway.EffectException.class,
                () -> ownership.requireOwnedCurrentRole(guild(pages), role(), punishment())
        );
    }

    private static List<AuditLogEntry> unrelatedPage() {
        List<AuditLogEntry> entries = new ArrayList<>(100);
        for (int index = 0; index < 100; index++) {
            entries.add(unrelatedEntry(TARGET_ID + index + 1, ISSUED_AT.plusSeconds(200L - index)));
        }
        return List.copyOf(entries);
    }

    private static AuditLogEntry ownedAssignment(Instant createdAt) {
        return roleChange(TARGET_ID, BOT_ID, OWNED_REASON, createdAt, true);
    }

    private static AuditLogEntry externalAssignment(Instant createdAt) {
        return roleChange(TARGET_ID, BOT_ID + 1, "manual mute", createdAt, true);
    }

    private static AuditLogEntry externalRemoval(Instant createdAt) {
        return roleChange(TARGET_ID, BOT_ID + 1, "manual unmute", createdAt, false);
    }

    private static AuditLogEntry unrelatedEntry(long targetId, Instant createdAt) {
        return roleChange(targetId, BOT_ID + 1, "unrelated", createdAt, true);
    }

    private static AuditLogEntry roleChange(
            long targetId,
            long actorId,
            String reason,
            Instant createdAt,
            boolean add
    ) {
        AuditLogKey key = add ? AuditLogKey.MEMBER_ROLES_ADD : AuditLogKey.MEMBER_ROLES_REMOVE;
        AuditLogChange change = new AuditLogChange(
                null,
                List.of(Map.of("id", Long.toString(ROLE_ID))),
                key.getKey()
        );
        return new AuditLogEntry(
                ActionType.MEMBER_ROLE_UPDATE,
                25,
                TimeUtil.getDiscordTimestamp(createdAt.toEpochMilli()),
                actorId,
                targetId,
                null,
                null,
                null,
                reason,
                Map.of(key.getKey(), change),
                Map.of()
        );
    }

    private static AuditPages pages(List<List<AuditLogEntry>> pages) {
        return new AuditPages(pages);
    }

    private static Guild guild(AuditPages pages) {
        SelfMember self = selfMember();
        return proxy(Guild.class, (proxy, method, args) -> switch (method.getName()) {
            case "getSelfMember" -> self;
            case "retrieveAuditLogs" -> pages.action();
            default -> objectOrUnexpected(proxy, method, args);
        });
    }

    private static Guild gatewayGuild(AuditPages pages, AtomicInteger removals) {
        Role muteRole = role();
        SelfMember self = selfMember();
        Member target = member(TARGET_ID, List.of(muteRole));
        return proxy(Guild.class, (proxy, method, args) -> switch (method.getName()) {
            case "getSelfMember" -> self;
            case "retrieveAuditLogs" -> pages.action();
            case "getRoleById" -> muteRole;
            case "retrieveMemberById" -> completedAction(target);
            case "removeRoleFromMember" -> removalAction(removals);
            default -> objectOrUnexpected(proxy, method, args);
        });
    }

    private static SelfMember selfMember() {
        return proxy(SelfMember.class, (proxy, method, args) -> switch (method.getName()) {
            case "getIdLong" -> BOT_ID;
            case "hasPermission", "canInteract" -> true;
            default -> objectOrUnexpected(proxy, method, args);
        });
    }

    private static Member member(long id, List<Role> roles) {
        return proxy(Member.class, (proxy, method, args) -> switch (method.getName()) {
            case "getIdLong" -> id;
            case "getRoles" -> roles;
            case "hasPermission", "canInteract" -> true;
            default -> objectOrUnexpected(proxy, method, args);
        });
    }

    private static Role role() {
        return proxy(Role.class, (proxy, method, args) -> switch (method.getName()) {
            case "getIdLong" -> ROLE_ID;
            default -> objectOrUnexpected(proxy, method, args);
        });
    }

    private static JDA jda(Guild guild) {
        return proxy(JDA.class, (proxy, method, args) -> switch (method.getName()) {
            case "getGuildById" -> guild;
            default -> objectOrUnexpected(proxy, method, args);
        });
    }

    @SuppressWarnings("unchecked")
    private static CacheRestAction<Member> completedAction(Member member) {
        return proxy(CacheRestAction.class, (proxy, method, args) -> switch (method.getName()) {
            case "complete" -> member;
            default -> objectOrUnexpected(proxy, method, args);
        });
    }

    private static Object removalAction(AtomicInteger removals) {
        removals.incrementAndGet();
        throw new AssertionError("role removal must not start without ownership proof");
    }

    private static DiscordPunishment punishment() {
        return DiscordPunishment.pending(
                PUNISHMENT_ID,
                new ModerationSubjectId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                new DiscordUserId(Long.toString(TARGET_ID)),
                new DiscordGuildId(Long.toString(GUILD_ID)),
                new Actor(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "staff", StaffRank.ADMIN),
                muteIntent(),
                ISSUED_AT,
                "issue"
        );
    }

    private static DiscordPunishment appliedPunishment() {
        return punishment().withProcessingResult(
                DiscordPunishmentState.APPLIED,
                DiscordDeliveryOutcome.DELIVERED,
                true,
                Optional.empty(),
                Optional.empty(),
                "applied"
        );
    }

    private static DiscordPunishmentIntent muteIntent() {
        return new DiscordPunishmentIntent(
                DiscordConsequenceType.MUTE,
                SanctionLength.temporary(Duration.ofHours(1)),
                false,
                false,
                Optional.empty(),
                "Reason",
                "",
                0,
                true
        );
    }

    private static DiscordPunishmentConfiguration configuration() {
        Duration hour = Duration.ofHours(1);
        return new DiscordPunishmentConfiguration(
                new DiscordAuthorizationLimits(hour, hour, hour, hour),
                Long.toString(ROLE_ID),
                Set.of("555"),
                "Support",
                Duration.ofSeconds(1),
                Duration.ofMillis(100)
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object objectOrUnexpected(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() != Object.class) {
            throw new AssertionError("unexpected JDA call: " + method);
        }
        return switch (method.getName()) {
            case "toString" -> proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> System.identityHashCode(proxy) == System.identityHashCode(args[0]);
            default -> throw new AssertionError("unexpected Object call: " + method);
        };
    }

    private static final class AuditPages implements InvocationHandler {
        private final List<List<AuditLogEntry>> pages;
        private final AuditLogPaginationAction action;
        private final AtomicInteger pageRequests = new AtomicInteger();
        private final AtomicInteger streamCalls = new AtomicInteger();
        private final AtomicInteger completeCalls = new AtomicInteger();
        private int failingPage = -1;

        private AuditPages(List<List<AuditLogEntry>> pages) {
            this.pages = List.copyOf(pages);
            this.action = proxy(AuditLogPaginationAction.class, this);
        }

        AuditPages failAtPage(int pageIndex) {
            failingPage = pageIndex;
            return this;
        }

        AuditLogPaginationAction action() {
            return action;
        }

        int pageRequests() {
            return pageRequests.get();
        }

        int streamCalls() {
            return streamCalls.get();
        }

        int completeCalls() {
            return completeCalls.get();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "type" -> configuredType(args);
                case "limit" -> configuredLimit(args);
                case "cache" -> configuredCache(args);
                case "stream" -> stream();
                case "complete" -> completeFirstPage();
                default -> objectOrUnexpected(proxy, method, args);
            };
        }

        private AuditLogPaginationAction configuredType(Object[] args) {
            assertEquals(ActionType.MEMBER_ROLE_UPDATE, args[0]);
            return action;
        }

        private AuditLogPaginationAction configuredLimit(Object[] args) {
            assertEquals(100, args[0]);
            return action;
        }

        private AuditLogPaginationAction configuredCache(Object[] args) {
            assertEquals(false, args[0]);
            return action;
        }

        private Stream<AuditLogEntry> stream() {
            streamCalls.incrementAndGet();
            Iterable<AuditLogEntry> iterable = () -> new PageIterator();
            return StreamSupport.stream(iterable.spliterator(), false);
        }

        private List<AuditLogEntry> completeFirstPage() {
            completeCalls.incrementAndGet();
            requestPage(0);
            return pages.isEmpty() ? List.of() : pages.get(0);
        }

        private List<AuditLogEntry> requestPage(int index) {
            pageRequests.incrementAndGet();
            if (index == failingPage) {
                throw new IllegalStateException("simulated audit transport failure");
            }
            return index < pages.size() ? pages.get(index) : List.of();
        }

        private final class PageIterator implements Iterator<AuditLogEntry> {
            private int pageIndex;
            private Iterator<AuditLogEntry> current = List.<AuditLogEntry>of().iterator();
            private boolean finished;

            @Override
            public boolean hasNext() {
                while (!finished && !current.hasNext()) {
                    List<AuditLogEntry> page = requestPage(pageIndex++);
                    if (page.isEmpty()) {
                        finished = true;
                    } else {
                        current = page.iterator();
                    }
                }
                return current.hasNext();
            }

            @Override
            public AuditLogEntry next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return current.next();
            }
        }
    }
}
