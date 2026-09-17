package net.enthusia.staff.discordbot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPermissionSnapshot;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository;

final class DiscordPunishmentWorkerFakeRepository implements DiscordPunishmentRepository {
    private static final Instant TEST_NOW = Instant.parse("2026-09-14T20:00:00Z");
    StoredPunishment current;
    final Queue<LeaseSeed> work = new PriorityQueue<>(Comparator.comparing(LeaseSeed::dueAt));

    DiscordPunishmentWorkerFakeRepository(DiscordPunishment punishment) {
        current = new StoredPunishment(punishment, 0, false);
    }

    void enqueue(WorkType type, Instant dueAt, int attempt) {
        work.add(new LeaseSeed(UUID.randomUUID(), type, dueAt, attempt));
    }

    void makeNextDue() {
        LeaseSeed next = work.remove();
        work.add(new LeaseSeed(next.id(), next.type(), TEST_NOW, next.attempt()));
    }

    @Override
    public StoredPunishment create(DiscordPunishment punishment, String operationKey, Instant now) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<StoredPunishment> find(UUID punishmentId) {
        return current.punishment().punishmentId().equals(punishmentId)
                ? Optional.of(current)
                : Optional.empty();
    }

    @Override
    public List<StoredPunishment> activeForTarget(
            DiscordGuildId guildId,
            DiscordUserId userId,
            DiscordConsequenceType type,
            int limit
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StoredPunishment transition(
            StoredPunishment expected,
            DiscordPunishment replacement,
            String operationKey,
            List<WorkSchedule> schedules,
            Instant now
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<WorkLease> claimDue(Instant now, int limit, String leaseOwner, Instant leaseUntil) {
        java.util.ArrayList<WorkLease> leases = new java.util.ArrayList<>();
        while (!work.isEmpty() && leases.size() < limit && !work.peek().dueAt().isAfter(now)) {
            LeaseSeed seed = work.remove();
            leases.add(new WorkLease(
                    seed.id(), seed.type(), current.punishment().punishmentId(), seed.dueAt(),
                    leaseOwner, leaseUntil, seed.attempt(), seed.attempt()
            ));
        }
        return List.copyOf(leases);
    }

    @Override
    public StoredPunishment settle(
            WorkLease lease,
            StoredPunishment expected,
            DiscordPunishment replacement,
            List<WorkSchedule> nextWork,
            Instant now
    ) {
        if (expected.revision() != current.revision()) {
            throw new IllegalStateException("stale test revision");
        }
        current = new StoredPunishment(replacement, current.revision() + 1, false);
        nextWork.forEach(schedule -> enqueue(schedule.type(), schedule.dueAt(), 1));
        return current;
    }

    @Override
    public List<StoredPunishment> activeNativeBans(DiscordGuildId guildId, int limit) {
        return List.of();
    }

    record LeaseSeed(UUID id, WorkType type, Instant dueAt, int attempt) {
    }
}
