package net.enthusia.staff.persistence;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.CrossPlatformIdentityLookup;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;

/** Read-only adapter from the V19 subject store to D08's narrow identity boundary. */
public final class DiscordCrossPlatformIdentityLookup implements CrossPlatformIdentityLookup {
    private final DiscordModerationPersistenceStore store;

    public DiscordCrossPlatformIdentityLookup(DiscordModerationPersistenceStore store) {
        if (store == null) {
            throw new IllegalArgumentException("Discord moderation persistence store must be present");
        }
        this.store = store;
    }

    @Override
    public Optional<ModerationSubjectId> subjectForMinecraft(UUID playerId) {
        return store.subjectForMinecraft(playerId).map(value -> value.subject().subjectId());
    }

    @Override
    public Optional<ModerationSubjectId> subjectForDiscord(DiscordUserId userId) {
        return store.subjectForDiscord(userId).map(value -> value.subject().subjectId());
    }
}
