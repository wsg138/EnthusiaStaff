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

final class DiscordPunishmentWorkerFakeGateway implements DiscordPunishmentGateway {
    int snapshotCalls;
    int applyCalls;
    int notifyAppliedCalls;
    int removeCalls;
    int notifyRemovedCalls;
    int reconcileCalls;
    DiscordPermissionSnapshot snapshot = DiscordPermissionSnapshot.absent();
    DiscordPunishment lastApplied;
    int lastApplyAttemptCount;
    DiscordDeliveryOutcome applyDelivery = DiscordDeliveryOutcome.DELIVERED;
    DiscordDeliveryOutcome removalDelivery = DiscordDeliveryOutcome.DELIVERED;
    EffectException applyFailure;
    EffectException removeFailure;
    EffectException reconcileFailure;

    @Override
    public void preflight(DiscordGuildId guildId, DiscordUserId target, DiscordPunishmentIntent intent) {
    }

    @Override
    public DiscordPermissionSnapshot captureRestrictionSnapshot(DiscordPunishment punishment) {
        snapshotCalls++;
        return snapshot;
    }

    @Override
    public void apply(DiscordPunishment punishment, int attemptCount) {
        applyCalls++;
        lastApplied = punishment;
        lastApplyAttemptCount = attemptCount;
        if (applyFailure != null) {
            throw applyFailure;
        }
    }

    @Override
    public DiscordDeliveryOutcome notifyApplied(DiscordPunishment punishment) {
        notifyAppliedCalls++;
        return applyDelivery;
    }

    @Override
    public void remove(DiscordPunishment punishment) {
        removeCalls++;
        if (removeFailure != null) {
            throw removeFailure;
        }
    }

    @Override
    public DiscordDeliveryOutcome notifyRemoved(DiscordPunishment punishment) {
        notifyRemovedCalls++;
        return removalDelivery;
    }

    @Override
    public void reconcile(DiscordPunishment punishment) {
        reconcileCalls++;
        if (reconcileFailure != null) {
            throw reconcileFailure;
        }
    }
}
