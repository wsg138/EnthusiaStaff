package net.enthusia.staff.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.EnforcementTarget;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;

public final class JdbcDiscordModerationPersistenceStore implements DiscordModerationPersistenceStore {
    private final JdbcDiscordIdentityRepository identities;
    private final JdbcDiscordMainAccountRepository mainAccounts;
    private final JdbcDiscordOperationalRepository operations;

    public JdbcDiscordModerationPersistenceStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.identities = new JdbcDiscordIdentityRepository(dataSource);
        this.mainAccounts = new JdbcDiscordMainAccountRepository(dataSource, identities);
        this.operations = new JdbcDiscordOperationalRepository(dataSource);
    }

    @Override
    public VersionedSubject ensureMinecraftSubject(UUID playerId, Instant now) {
        return identities.ensureMinecraftSubject(playerId, now);
    }

    @Override
    public VersionedSubject ensureDiscordSubject(DiscordUserId userId, Instant now) {
        return identities.ensureDiscordSubject(userId, now);
    }

    @Override
    public Optional<VersionedSubject> subjectForMinecraft(UUID playerId) {
        return identities.subjectForMinecraft(playerId);
    }

    @Override
    public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
        return identities.subjectForDiscord(userId);
    }

    @Override
    public Optional<VersionedLink> currentLink(UUID playerId) {
        return identities.currentLink(playerId);
    }

    @Override
    public VersionedLink link(
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            DiscordMinecraftLinkSource source,
            String operationKey,
            Instant linkedAt
    ) {
        return identities.link(discordUserId, minecraftPlayerId, source, operationKey, linkedAt);
    }

    @Override
    public VersionedLink unlink(
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            long expectedRevision,
            String operationKey,
            Instant unlinkedAt
    ) {
        return identities.unlink(
                discordUserId,
                minecraftPlayerId,
                expectedRevision,
                operationKey,
                unlinkedAt
        );
    }

    @Override
    public VersionedSubject setMainMinecraftAccount(
            ModerationSubjectId subjectId,
            MainMinecraftAccount mainAccount,
            long expectedSubjectRevision,
            Instant selectedAt
    ) {
        return mainAccounts.setMainMinecraftAccount(
                subjectId,
                mainAccount,
                expectedSubjectRevision,
                selectedAt
        );
    }

    @Override
    public StoredEnforcementTarget recordEnforcementTarget(
            ModerationSubjectId subjectId,
            EnforcementTarget target,
            String operationKey,
            Instant now
    ) {
        return operations.recordEnforcementTarget(subjectId, target, operationKey, now);
    }

    @Override
    public StoredEvidence recordEvidence(EvidenceMetadata metadata) {
        return operations.recordEvidence(metadata);
    }

    @Override
    public SecurityLock activateSecurityLock(
            ModerationSubjectId subjectId,
            DiscordUserId discordUserId,
            String reasonCode,
            String operationKey,
            Instant now
    ) {
        return operations.activateSecurityLock(subjectId, discordUserId, reasonCode, operationKey, now);
    }

    @Override
    public SecurityLock releaseSecurityLock(
            UUID lockId,
            long expectedRevision,
            String operationKey,
            Instant now
    ) {
        return operations.releaseSecurityLock(lockId, expectedRevision, operationKey, now);
    }

    @Override
    public ReconciliationState saveReconciliation(
            ReconciliationState state,
            long expectedRevision,
            Instant now
    ) {
        return operations.saveReconciliation(state, expectedRevision, now);
    }

    @Override
    public MaintenanceWork enqueueMaintenance(
            String workType,
            String resourceKey,
            Instant dueAt,
            Instant now
    ) {
        return operations.enqueueMaintenance(workType, resourceKey, dueAt, now);
    }

    @Override
    public List<MaintenanceWork> claimDueMaintenance(
            Instant now,
            int limit,
            String leaseOwner,
            Instant leaseUntil
    ) {
        return operations.claimDueMaintenance(now, limit, leaseOwner, leaseUntil);
    }

    @Override
    public boolean completeMaintenance(
            UUID workId,
            long expectedRevision,
            String leaseOwner,
            Instant now
    ) {
        return operations.completeMaintenance(workId, expectedRevision, leaseOwner, now);
    }
}
