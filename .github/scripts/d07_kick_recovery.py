from pathlib import Path


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    Path(path).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


# Carry the durable work-attempt number into the external-effect boundary.
path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentGateway.java"
text = read(path)
text = replace_once(
    text,
    "    void apply(DiscordPunishment punishment);\n",
    "    void apply(DiscordPunishment punishment, int attemptCount);\n",
    "gateway apply attempt signature",
)
write(path, text)

path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentWorker.java"
text = read(path)
text = replace_once(
    text,
    "            gateway.apply(stored.punishment());\n",
    "            gateway.apply(stored.punishment(), work.attemptCount());\n",
    "worker durable attempt forwarding",
)
write(path, text)

# Isolate one-shot kick ownership/idempotency from general gateway orchestration.
path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaDiscordPunishmentGateway.java"
text = read(path)
text = replace_once(text, "    private static final int MAX_AUDIT_REASON = 500;\n", "", "gateway obsolete audit constant")
text = replace_once(
    text,
    "    private final JdaNativeBanEnforcer nativeBans = new JdaNativeBanEnforcer();\n",
    "    private final JdaNativeBanEnforcer nativeBans = new JdaNativeBanEnforcer();\n"
    "    private final JdaKickEnforcer kicks = new JdaKickEnforcer();\n",
    "gateway kick enforcer field",
)
text = replace_once(
    text,
    "                case CHANNEL_RESTRICTION -> restrictionContainer(guild, intent.restriction().orElseThrow());\n                case WARNING, KICK -> { }\n",
    "                case CHANNEL_RESTRICTION -> restrictionContainer(guild, intent.restriction().orElseThrow());\n"
    "                case KICK -> kicks.preflight(guild);\n"
    "                case WARNING -> { }\n",
    "gateway kick preflight",
)
text = replace_once(
    text,
    "    public void apply(DiscordPunishment punishment) {\n        try {\n            applyEffect(guild(punishment.guildId()), punishment);\n",
    "    public void apply(DiscordPunishment punishment, int attemptCount) {\n        try {\n            applyEffect(guild(punishment.guildId()), punishment, attemptCount);\n",
    "gateway apply attempt implementation",
)
old = """    private void applyEffect(Guild guild, DiscordPunishment punishment) {
        DiscordUserId target = punishment.targetUserId();
        switch (punishment.intent().type()) {
            case MUTE -> applyMute(guild, punishment);
            case KICK -> kick(guild, target, punishment);
            case BAN -> applyBan(guild, punishment);
            case CHANNEL_RESTRICTION -> applyRestriction(guild, punishment);
            case WARNING -> { }
            default -> throw failure(UNSUPPORTED_CONSEQUENCE, false);
        }
    }

    private void kick(Guild guild, DiscordUserId target, DiscordPunishment punishment) {
        Member member = memberOrNull(guild, target);
        if (member == null) {
            return;
        }
        requireHierarchy(guild, member);
        guild.kick(UserSnowflake.fromId(target.value())).reason(auditReason(punishment)).complete();
    }
"""
new = """    private void applyEffect(Guild guild, DiscordPunishment punishment, int attemptCount) {
        switch (punishment.intent().type()) {
            case MUTE -> applyMute(guild, punishment);
            case KICK -> applyKick(guild, punishment, attemptCount);
            case BAN -> applyBan(guild, punishment);
            case CHANNEL_RESTRICTION -> applyRestriction(guild, punishment);
            case WARNING -> { }
            default -> throw failure(UNSUPPORTED_CONSEQUENCE, false);
        }
    }

    private void applyKick(Guild guild, DiscordPunishment punishment, int attemptCount) {
        Member member = memberOrNull(guild, punishment.targetUserId());
        if (member != null) {
            requireHierarchy(guild, member);
        }
        kicks.apply(guild, member, punishment, attemptCount);
    }
"""
text = replace_once(text, old, new, "gateway kick orchestration")
old = """    private static String auditReason(DiscordPunishment punishment) {
        String reason = "Enthusia D07 " + punishment.punishmentId() + " "
                + punishment.intent().type() + ": " + punishment.intent().publicReason();
        return reason.length() <= MAX_AUDIT_REASON ? reason : reason.substring(0, MAX_AUDIT_REASON);
    }
"""
text = replace_once(text, old, "", "gateway obsolete generic audit reason")
write(path, text)

kick_enforcer = """package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.enthusia.staff.domain.discord.DiscordPunishment;

/** Makes one-shot kicks crash-safe without risking duplicate enforcement on a reclaimed lease. */
final class JdaKickEnforcer {
    private static final int FIRST_ATTEMPT = 1;
    private static final int AUDIT_LIMIT = 100;
    private static final int MAX_AUDIT_REASON = 500;
    private static final Duration AUDIT_CLOCK_SKEW = Duration.ofMinutes(1);
    private static final String MARKER_PREFIX = "Enthusia D07 kick ";

    record Observation(ActionType type, long targetId, long actorId, String reason, Instant createdAt) {
    }

    void preflight(Guild guild) {
        Member self = guild.getSelfMember();
        if (!self.hasPermission(Permission.KICK_MEMBERS)) {
            throw failure("KICK_PERMISSION_MISSING", false);
        }
        if (!self.hasPermission(Permission.VIEW_AUDIT_LOGS)) {
            throw failure("KICK_AUDIT_LOG_PERMISSION_MISSING", false);
        }
    }

    void apply(Guild guild, Member target, DiscordPunishment punishment, int attemptCount) {
        requireAttempt(attemptCount);
        preflight(guild);
        if (ownedKickExists(guild, punishment)) {
            return;
        }
        if (target == null) {
            return;
        }
        if (attemptCount > FIRST_ATTEMPT) {
            throw failure("KICK_OWNERSHIP_UNVERIFIED", true);
        }
        guild.kick(UserSnowflake.fromId(punishment.targetUserId().value()))
                .reason(auditReason(punishment))
                .complete();
    }

    private boolean ownedKickExists(Guild guild, DiscordPunishment punishment) {
        long targetId = Long.parseUnsignedLong(punishment.targetUserId().value());
        long actorId = guild.getSelfMember().getIdLong();
        List<AuditLogEntry> entries = guild.retrieveAuditLogs()
                .type(ActionType.KICK)
                .user(guild.getSelfMember())
                .limit(AUDIT_LIMIT)
                .complete();
        return entries.stream()
                .map(JdaKickEnforcer::observation)
                .anyMatch(entry -> provesOwnership(
                        entry,
                        punishment.punishmentId(),
                        targetId,
                        actorId,
                        punishment.issuedAt()
                ));
    }

    static boolean provesOwnership(
            Observation observation,
            UUID punishmentId,
            long targetId,
            long actorId,
            Instant issuedAt
    ) {
        if (observation == null || punishmentId == null || issuedAt == null) {
            return false;
        }
        return observation.type() == ActionType.KICK
                && observation.targetId() == targetId
                && observation.actorId() == actorId
                && ownsReason(observation.reason(), punishmentId)
                && !observation.createdAt().isBefore(issuedAt.minus(AUDIT_CLOCK_SKEW));
    }

    static String marker(UUID punishmentId) {
        return MARKER_PREFIX + punishmentId + ":";
    }

    private static boolean ownsReason(String reason, UUID punishmentId) {
        return reason != null && reason.startsWith(marker(punishmentId));
    }

    private static String auditReason(DiscordPunishment punishment) {
        String reason = marker(punishment.punishmentId()) + " " + punishment.intent().publicReason();
        return reason.length() <= MAX_AUDIT_REASON ? reason : reason.substring(0, MAX_AUDIT_REASON);
    }

    private static Observation observation(AuditLogEntry entry) {
        return new Observation(
                entry.getType(),
                entry.getTargetIdLong(),
                entry.getUserIdLong(),
                entry.getReason(),
                entry.getTimeCreated().toInstant()
        );
    }

    private static void requireAttempt(int attemptCount) {
        if (attemptCount < FIRST_ATTEMPT) {
            throw new IllegalArgumentException("kick attempt count must be positive");
        }
    }

    private static DiscordPunishmentGateway.EffectException failure(String code, boolean retryable) {
        return new DiscordPunishmentGateway.EffectException(code, retryable);
    }
}
"""
write("staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaKickEnforcer.java", kick_enforcer)

kick_test = """package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.dv8tion.jda.api.audit.ActionType;
import org.junit.jupiter.api.Test;

class JdaKickEnforcerTest {
    private static final UUID PUNISHMENT_ID = UUID.fromString("67ab80ad-acde-4079-95c0-551b8acf7d3e");
    private static final Instant ISSUED_AT = Instant.parse("2026-09-14T20:00:00Z");
    private static final long TARGET_ID = 123L;
    private static final long BOT_ID = 456L;

    @Test
    void ownershipRequiresExactKickActorTargetAndPunishmentMarker() {
        String reason = JdaKickEnforcer.marker(PUNISHMENT_ID) + " reason";

        assertTrue(proves(observation(ActionType.KICK, TARGET_ID, BOT_ID, reason, ISSUED_AT.plusSeconds(1))));
        assertFalse(proves(observation(
                ActionType.BAN, TARGET_ID, BOT_ID, reason, ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.KICK, TARGET_ID + 1, BOT_ID, reason, ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.KICK, TARGET_ID, BOT_ID + 1, reason, ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.KICK, TARGET_ID, BOT_ID, "manual kick", ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.KICK,
                TARGET_ID,
                BOT_ID,
                JdaKickEnforcer.marker(UUID.randomUUID()) + " reason",
                ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.KICK, TARGET_ID, BOT_ID, reason, ISSUED_AT.minusSeconds(120)
        )));
    }

    private static boolean proves(JdaKickEnforcer.Observation observation) {
        return JdaKickEnforcer.provesOwnership(
                observation, PUNISHMENT_ID, TARGET_ID, BOT_ID, ISSUED_AT
        );
    }

    private static JdaKickEnforcer.Observation observation(
            ActionType type,
            long targetId,
            long actorId,
            String reason,
            Instant createdAt
    ) {
        return new JdaKickEnforcer.Observation(type, targetId, actorId, reason, createdAt);
    }
}
"""
write("staff-bot/src/test/java/net/enthusia/staff/discordbot/JdaKickEnforcerTest.java", kick_test)

# Test that the persisted lease attempt reaches the effect adapter.
path = "staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordPunishmentWorkerTest.java"
text = read(path)
marker = """    @Test
    void cycleClaimsOnlyOneBlockingDiscordWorkItem() {
"""
test = """    @Test
    void applyForwardsDurableClaimAttemptToGateway() {
        FakeRepository repository = new FakeRepository(punishment(kick(), NOW));
        FakeGateway gateway = new FakeGateway();
        repository.enqueue(WorkType.APPLY, NOW, 3);

        newWorker(repository, gateway).runCycle();

        assertEquals(3, gateway.lastApplyAttemptCount);
    }

"""
text = replace_once(text, marker, test + marker, "worker durable attempt test")
text = replace_once(
    text,
    "        private DiscordPunishment lastApplied;\n",
    "        private DiscordPunishment lastApplied;\n        private int lastApplyAttemptCount;\n",
    "fake gateway attempt field",
)
text = replace_once(
    text,
    "        public void apply(DiscordPunishment punishment) {\n            applyCalls++;\n            lastApplied = punishment;\n",
    "        public void apply(DiscordPunishment punishment, int attemptCount) {\n            applyCalls++;\n            lastApplied = punishment;\n            lastApplyAttemptCount = attemptCount;\n",
    "fake gateway apply signature",
)
write(path, text)
