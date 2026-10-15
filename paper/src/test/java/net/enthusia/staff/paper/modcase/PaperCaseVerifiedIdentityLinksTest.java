package net.enthusia.staff.paper.modcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLink;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;
import org.junit.jupiter.api.Test;

class PaperCaseVerifiedIdentityLinksTest {
    private static final UUID A = UUID.fromString("3b0d5bf6-355a-48b5-a701-9c0236d7fd99");
    private static final UUID B = UUID.fromString("1b0d5bf6-355a-48b5-a701-9c0236d7fd99");
    private static final String DISCORD = String.valueOf(100000000000000000L + 345678L);
    private static final DiscordUserId USER = new DiscordUserId(DISCORD);
    private final ModerationSubjectId subjectId = new ModerationSubjectId(UUID.randomUUID());
    private final Map<UUID, DiscordModerationPersistenceStore.VersionedLink> links = new HashMap<>();
    private Set<UUID> subjects = Set.of(A);

    private DiscordModerationPersistenceStore store() {
        return (DiscordModerationPersistenceStore) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{DiscordModerationPersistenceStore.class},
                (instance, method, args) -> switch (method.getName()) {
                    case "subjectForDiscord" -> {
                        if (!USER.equals(args[0])) {
                            yield Optional.empty();
                        }
                        var identities = new java.util.HashSet<net.enthusia.staff.domain.moderation.ModerationIdentity>();
                        identities.add(new DiscordIdentityRef(USER));
                        subjects.forEach(id -> identities.add(new MinecraftIdentityRef(id)));
                        yield Optional.of(new DiscordModerationPersistenceStore.VersionedSubject(
                                new ModerationSubject(subjectId, identities, Optional.empty()), 1));
                    }
                    case "currentLink" -> Optional.ofNullable(links.get(args[0]));
                    default -> throw new UnsupportedOperationException("unexpected operation");
                });
    }

    private void link(UUID uuid, DiscordMinecraftLinkSource source, boolean active) {
        var original = new DiscordMinecraftLink(USER, uuid, Instant.EPOCH,
                active ? Optional.empty() : Optional.of(Instant.EPOCH.plusSeconds(1)), source);
        links.put(uuid, new DiscordModerationPersistenceStore.VersionedLink(
                UUID.randomUUID(), subjectId, original, 1, false));
    }

    @Test
    void requiresOneCurrentlyVerifiedLinkRatherThanSubjectMembership() {
        var store = store();
        var reverse = PaperCaseVerifiedIdentityLinks.uniqueCurrentVerified(() -> store);
        var forward = PaperCaseVerifiedIdentityLinks.currentVerified(() -> store);
        assertEquals(Optional.empty(), reverse.apply(DISCORD));
        link(A, DiscordMinecraftLinkSource.MIGRATED_DISCORDSRV, true);
        assertEquals(Optional.empty(), reverse.apply(DISCORD));
        assertFalse(forward.test(A, DISCORD));
        link(A, DiscordMinecraftLinkSource.DISCORD_CODE, true);
        assertEquals(Optional.of(A), reverse.apply(DISCORD));
        assertTrue(forward.test(A, DISCORD));
        assertFalse(forward.test(A, "99999999999999"));
        link(A, DiscordMinecraftLinkSource.MINECRAFT_CODE, false);
        assertEquals(Optional.empty(), reverse.apply(DISCORD));
    }

    @Test
    void ambiguityAndRecoveryLinkFailClosed() {
        subjects = Set.of(A, B);
        link(A, DiscordMinecraftLinkSource.DISCORD_CODE, true);
        link(B, DiscordMinecraftLinkSource.MINECRAFT_CODE, true);
        var reverse = PaperCaseVerifiedIdentityLinks.uniqueCurrentVerified(this::store);
        assertEquals(Optional.empty(), reverse.apply(DISCORD));
        link(B, DiscordMinecraftLinkSource.STAFF_RECOVERY, true);
        assertEquals(Optional.of(A), reverse.apply(DISCORD));
    }
}
