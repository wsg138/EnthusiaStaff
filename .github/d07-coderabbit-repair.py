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


# 1) Durable aggregate: only applied/failed-removal sanctions may enter PENDING_REMOVE.
path = "domain/src/main/java/net/enthusia/staff/domain/discord/DiscordPunishment.java"
text = read(path)
old = '''    public DiscordPunishment requestRemoval(DiscordPunishmentTermination requested, String operationKey) {
        if (requested == null || requested == DiscordPunishmentTermination.NONE) {
            throw new IllegalArgumentException("removal termination must be explicit");
        }
        if (!intent.reversible()) {
            return markTerminal(requested, operationKey);
        }
        return new DiscordPunishment(
                punishmentId, subjectId, targetUserId, guildId, issuer, intent, issuedAt, expiresAt,
                DiscordPunishmentState.PENDING_REMOVE, requested, dmOutcome, DiscordDeliveryOutcome.NOT_ATTEMPTED,
                externalApplied, previousRestriction, Optional.empty(), Optional.of(operationKey)
        );
    }
'''
new = '''    public DiscordPunishment requestRemoval(DiscordPunishmentTermination requested, String operationKey) {
        if (requested == null || requested == DiscordPunishmentTermination.NONE) {
            throw new IllegalArgumentException("removal termination must be explicit");
        }
        if (!intent.reversible()) {
            return markTerminal(requested, operationKey);
        }
        requireRemovalSource();
        return new DiscordPunishment(
                punishmentId, subjectId, targetUserId, guildId, issuer, intent, issuedAt, expiresAt,
                DiscordPunishmentState.PENDING_REMOVE, requested, dmOutcome, DiscordDeliveryOutcome.NOT_ATTEMPTED,
                externalApplied, previousRestriction, Optional.empty(), Optional.of(operationKey)
        );
    }

    public DiscordPunishment expireWithoutEffect(String operationKey) {
        boolean pendingApply = state == DiscordPunishmentState.PENDING_APPLY
                || state == DiscordPunishmentState.RETRY_APPLY;
        if (!intent.reversible() || externalApplied || !pendingApply) {
            throw new IllegalStateException("only unapplied pending punishments may expire without removal");
        }
        return new DiscordPunishment(
                punishmentId, subjectId, targetUserId, guildId, issuer, intent, issuedAt, expiresAt,
                DiscordPunishmentState.EXPIRED, DiscordPunishmentTermination.EXPIRE,
                dmOutcome, DiscordDeliveryOutcome.NOT_ATTEMPTED, false,
                previousRestriction, Optional.empty(), Optional.of(operationKey)
        );
    }
'''
text = replace_once(text, old, new, "aggregate removal source guard")
marker = '''    private DiscordPunishment withDeliveryOutcomes(
'''
helper = '''    private void requireRemovalSource() {
        if (state != DiscordPunishmentState.APPLIED && state != DiscordPunishmentState.FAILED_REMOVE) {
            throw new IllegalStateException("reversible punishment is not in a removable state");
        }
    }

'''
text = replace_once(text, marker, helper + marker, "aggregate removal source helper")
write(path, text)

path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentWorker.java"
text = read(path)
old = '''    private static DiscordPunishment terminalWithoutEffect(DiscordPunishment punishment, WorkLease work) {
        DiscordPunishment pendingRemoval = punishment.requestRemoval(
                DiscordPunishmentTermination.EXPIRE, operationKey(work)
        );
        return pendingRemoval.markRemoved(operationKey(work));
    }
'''
new = '''    private static DiscordPunishment terminalWithoutEffect(DiscordPunishment punishment, WorkLease work) {
        return punishment.expireWithoutEffect(operationKey(work));
    }
'''
text = replace_once(text, old, new, "worker direct unapplied expiry")
write(path, text)

# Add aggregate regression tests.
write(
    "domain/src/test/java/net/enthusia/staff/domain/discord/DiscordPunishmentTest.java",
    '''package net.enthusia.staff.domain.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordPunishmentTest {
    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");

    @Test
    void reversibleRemovalRequiresAppliedOrFailedRemovalState() {
        DiscordPunishment pending = punishment();
        DiscordPunishment failedApply = pending.withProcessingResult(
                DiscordPunishmentState.FAILED_APPLY,
                DiscordDeliveryOutcome.NOT_ATTEMPTED,
                false,
                Optional.empty(),
                Optional.of("APPLY_FAILED"),
                "failed"
        );
        DiscordPunishment applied = pending.withProcessingResult(
                DiscordPunishmentState.APPLIED,
                DiscordDeliveryOutcome.DELIVERED,
                true,
                Optional.empty(),
                Optional.empty(),
                "applied"
        );

        assertThrows(IllegalStateException.class,
                () -> pending.requestRemoval(DiscordPunishmentTermination.END, "remove-pending"));
        assertThrows(IllegalStateException.class,
                () -> failedApply.requestRemoval(DiscordPunishmentTermination.END, "remove-failed"));
        assertEquals(DiscordPunishmentState.PENDING_REMOVE,
                applied.requestRemoval(DiscordPunishmentTermination.END, "remove-applied").state());
    }

    @Test
    void pendingAndRetryApplyCanExpireWithoutDiscordEffect() {
        DiscordPunishment pending = punishment();
        DiscordPunishment retry = pending.withProcessingResult(
                DiscordPunishmentState.RETRY_APPLY,
                DiscordDeliveryOutcome.NOT_ATTEMPTED,
                false,
                Optional.empty(),
                Optional.of("RETRY"),
                "retry"
        );

        DiscordPunishment expired = pending.expireWithoutEffect("expire-pending");
        assertEquals(DiscordPunishmentState.EXPIRED, expired.state());
        assertEquals(DiscordPunishmentTermination.EXPIRE, expired.termination());
        assertFalse(expired.externalApplied());
        assertEquals(DiscordPunishmentState.EXPIRED, retry.expireWithoutEffect("expire-retry").state());

        DiscordPunishment applied = pending.withProcessingResult(
                DiscordPunishmentState.APPLIED,
                DiscordDeliveryOutcome.DELIVERED,
                true,
                Optional.empty(),
                Optional.empty(),
                "applied"
        );
        assertThrows(IllegalStateException.class, () -> applied.expireWithoutEffect("invalid"));
    }

    private static DiscordPunishment punishment() {
        DiscordPunishmentIntent intent = new DiscordPunishmentIntent(
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
        return DiscordPunishment.pending(
                UUID.randomUUID(),
                new ModerationSubjectId(UUID.randomUUID()),
                new DiscordUserId("123"),
                new DiscordGuildId("456"),
                new Actor(UUID.randomUUID(), "admin", StaffRank.ADMIN),
                intent,
                NOW,
                "issue"
        );
    }
}
'''
)

# 2) Duration parser: allow all numeric representations up through the existing 3650-day cap.
path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordDurationParser.java"
text = read(path)
text = replace_once(
    text,
    '    private static final Pattern TEMPORARY = Pattern.compile("([1-9][0-9]{0,5})([smhdw])");\n',
    '    private static final Pattern TEMPORARY = Pattern.compile("([1-9][0-9]{0,8})([smhdw])");\n',
    "duration numeric width",
)
write(path, text)

path = "staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordDurationParserTest.java"
text = read(path)
marker = '''    @Test
    void rejectsMalformedDisallowedAndExcessiveDurations() {
'''
test = '''    @Test
    void acceptsLargeCustomSecondsThroughSafetyMaximum() {
        var millionSeconds = parser.parse("1000000s", true);
        var maximumSeconds = parser.parse("315360000s", true);

        assertEquals(Duration.ofSeconds(1_000_000), millionSeconds.length().temporary().orElseThrow());
        assertTrue(millionSeconds.custom());
        assertEquals(Duration.ofDays(3650), maximumSeconds.length().temporary().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> parser.parse("315360001s", true));
    }

'''
text = replace_once(text, marker, test + marker, "duration upper-bound regression")
write(path, text)

# 3) Configuration: validate IDs with the same signed-long parser used by JDA String lookups.
path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentConfiguration.java"
text = read(path)
old = '''    private static boolean snowflake(String value) {
        return value != null && SNOWFLAKE.matcher(value).matches();
    }
'''
new = '''    private static boolean snowflake(String value) {
        if (value == null || !SNOWFLAKE.matcher(value).matches()) {
            return false;
        }
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
'''
text = replace_once(text, old, new, "JDA-compatible snowflake parser")
write(path, text)

path = "staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordPunishmentConfigurationTest.java"
text = read(path)
old = '''        Map<String, String> inverted = validValues();
        inverted.put(DiscordPunishmentConfiguration.HELPER_MAX_MUTE_ENV, "7200");
        inverted.put(DiscordPunishmentConfiguration.MOD_MAX_MUTE_ENV, "3600");
        assertThrows(IllegalArgumentException.class,
                () -> DiscordPunishmentConfiguration.fromEnvironment(inverted));
'''
new = '''        Map<String, String> overflowingRole = validValues();
        overflowingRole.put(DiscordPunishmentConfiguration.MUTE_ROLE_ENV, "9223372036854775808");
        assertThrows(IllegalArgumentException.class,
                () -> DiscordPunishmentConfiguration.fromEnvironment(overflowingRole));

        Map<String, String> overflowingScope = validValues();
        overflowingScope.put(DiscordPunishmentConfiguration.SUPPORT_SCOPES_ENV, "456,9223372036854775808");
        assertThrows(IllegalArgumentException.class,
                () -> DiscordPunishmentConfiguration.fromEnvironment(overflowingScope));

        Map<String, String> inverted = validValues();
        inverted.put(DiscordPunishmentConfiguration.HELPER_MAX_MUTE_ENV, "7200");
        inverted.put(DiscordPunishmentConfiguration.MOD_MAX_MUTE_ENV, "3600");
        assertThrows(IllegalArgumentException.class,
                () -> DiscordPunishmentConfiguration.fromEnvironment(inverted));
'''
text = replace_once(text, old, new, "configuration overflow regressions")
write(path, text)

# 4) Mute ownership: prove the latest role-changing audit event is the D07-owned assignment.
path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaMuteRoleOwnership.java"
text = read(path)
text = text.replace("import java.util.List;\n", "import java.util.HashSet;\nimport java.util.List;\nimport java.util.Map;\nimport java.util.Set;\n")
text = text.replace("import net.dv8tion.jda.api.audit.ActionType;\n", "import net.dv8tion.jda.api.audit.ActionType;\nimport net.dv8tion.jda.api.audit.AuditLogChange;\nimport net.dv8tion.jda.api.audit.AuditLogKey;\n")
text = text.replace("import net.dv8tion.jda.api.entities.Member;\n", "import net.dv8tion.jda.api.entities.Member;\nimport net.dv8tion.jda.api.entities.Role;\n")
old = '''    record Observation(ActionType type, long targetId, long actorId, String reason, Instant createdAt) {
    }
'''
new = '''    record Observation(
            ActionType type,
            long targetId,
            long actorId,
            String reason,
            Instant createdAt,
            Set<Long> addedRoleIds,
            Set<Long> removedRoleIds
    ) {
        Observation {
            addedRoleIds = Set.copyOf(addedRoleIds);
            removedRoleIds = Set.copyOf(removedRoleIds);
        }

        Observation(ActionType type, long targetId, long actorId, String reason, Instant createdAt) {
            this(type, targetId, actorId, reason, createdAt, Set.of(), Set.of());
        }

        boolean changes(long roleId) {
            return addedRoleIds.contains(roleId) || removedRoleIds.contains(roleId);
        }
    }
'''
text = replace_once(text, old, new, "ownership observation role changes")
old = '''    void requireOwnedApplyRetry(Guild guild, DiscordPunishment punishment) {
        long targetId = Long.parseUnsignedLong(punishment.targetUserId().value());
        long actorId = guild.getSelfMember().getIdLong();
        List<AuditLogEntry> entries = guild.retrieveAuditLogs()
                .type(ActionType.MEMBER_ROLE_UPDATE)
                .user(guild.getSelfMember())
                .limit(AUDIT_LIMIT)
                .complete();
        boolean owned = entries.stream()
                .map(JdaMuteRoleOwnership::observation)
                .anyMatch(entry -> provesOwnership(
                        entry,
                        punishment.punishmentId(),
                        targetId,
                        actorId,
                        punishment.issuedAt()
                ));
        if (!owned) {
            throw failure("MUTE_ROLE_OWNERSHIP_UNVERIFIED", true);
        }
    }
'''
new = '''    void requireOwnedCurrentRole(Guild guild, Role role, DiscordPunishment punishment) {
        requirePermissions(guild);
        long targetId = Long.parseLong(punishment.targetUserId().value());
        long actorId = guild.getSelfMember().getIdLong();
        List<Observation> observations = guild.retrieveAuditLogs()
                .type(ActionType.MEMBER_ROLE_UPDATE)
                .limit(AUDIT_LIMIT)
                .complete().stream()
                .map(JdaMuteRoleOwnership::observation)
                .toList();
        boolean owned = provesCurrentOwnership(
                observations,
                punishment.punishmentId(),
                targetId,
                actorId,
                role.getIdLong(),
                punishment.issuedAt()
        );
        if (!owned) {
            throw failure("MUTE_ROLE_OWNERSHIP_UNVERIFIED", true);
        }
    }
'''
text = replace_once(text, old, new, "current mute ownership check")
marker = '''    static String marker(UUID punishmentId) {
'''
helper = '''    static boolean provesCurrentOwnership(
            List<Observation> observations,
            UUID punishmentId,
            long targetId,
            long actorId,
            long roleId,
            Instant issuedAt
    ) {
        if (observations == null || punishmentId == null || issuedAt == null) {
            return false;
        }
        return observations.stream()
                .filter(entry -> entry.type() == ActionType.MEMBER_ROLE_UPDATE)
                .filter(entry -> entry.targetId() == targetId && entry.changes(roleId))
                .filter(entry -> !entry.createdAt().isBefore(issuedAt.minus(AUDIT_CLOCK_SKEW)))
                .max(java.util.Comparator.comparing(Observation::createdAt))
                .map(entry -> entry.addedRoleIds().contains(roleId)
                        && !entry.removedRoleIds().contains(roleId)
                        && provesOwnership(entry, punishmentId, targetId, actorId, issuedAt))
                .orElse(false);
    }

'''
text = replace_once(text, marker, helper + marker, "latest ownership proof")
old = '''    private static Observation observation(AuditLogEntry entry) {
        return new Observation(
                entry.getType(),
                entry.getTargetIdLong(),
                entry.getUserIdLong(),
                entry.getReason(),
                entry.getTimeCreated().toInstant()
        );
    }
'''
new = '''    private static Observation observation(AuditLogEntry entry) {
        return new Observation(
                entry.getType(),
                entry.getTargetIdLong(),
                entry.getUserIdLong(),
                entry.getReason(),
                entry.getTimeCreated().toInstant(),
                roleIds(entry.getChangeByKey(AuditLogKey.MEMBER_ROLES_ADD)),
                roleIds(entry.getChangeByKey(AuditLogKey.MEMBER_ROLES_REMOVE))
        );
    }

    private static Set<Long> roleIds(AuditLogChange change) {
        if (change == null || !(change.getNewValue() instanceof List<?> values)) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        for (Object value : values) {
            if (value instanceof Map<?, ?> role && role.get("id") != null) {
                addRoleId(ids, role.get("id"));
            }
        }
        return Set.copyOf(ids);
    }

    private static void addRoleId(Set<Long> ids, Object raw) {
        try {
            ids.add(Long.parseLong(raw.toString()));
        } catch (NumberFormatException ignored) {
            // Malformed audit data cannot prove ownership.
        }
    }
'''
text = replace_once(text, old, new, "audit role change parsing")
write(path, text)

path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaDiscordPunishmentGateway.java"
text = read(path)
text = replace_once(
    text,
    '                case MUTE -> removeMute(guild, punishment.targetUserId());\n',
    '                case MUTE -> removeMute(guild, punishment);\n',
    "full punishment mute removal",
)
text = replace_once(
    text,
    '                case MUTE -> reconcileMute(guild, punishment.targetUserId());\n',
    '                case MUTE -> reconcileMute(guild, punishment);\n',
    "full punishment mute reconciliation",
)
text = replace_once(
    text,
    '            muteOwnership.requireOwnedApplyRetry(guild, punishment);\n',
    '            muteOwnership.requireOwnedCurrentRole(guild, role, punishment);\n',
    "apply retry current ownership",
)
old = '''    private void reconcileMute(Guild guild, DiscordUserId target) {
        Member member = memberRequired(guild, target);
        requireHierarchy(guild, member);
        Role role = requireMuteRole(guild);
        ensureMutePolicy(guild);
        if (!member.getRoles().contains(role)) {
            guild.addRoleToMember(UserSnowflake.fromId(target.value()), role)
                    .reason("Enthusia D07 mute reconciliation")
                    .complete();
        }
    }

    private void removeMute(Guild guild, DiscordUserId target) {
        Member member = memberOrNull(guild, target);
        if (member == null) {
            return;
        }
        Role role = requireMuteRole(guild);
        requireHierarchy(guild, member);
        if (member.getRoles().contains(role)) {
            guild.removeRoleFromMember(UserSnowflake.fromId(target.value()), role)
                    .reason("Enthusia D07 mute removal").complete();
        }
    }
'''
new = '''    private void reconcileMute(Guild guild, DiscordPunishment punishment) {
        DiscordUserId target = punishment.targetUserId();
        Member member = memberRequired(guild, target);
        requireHierarchy(guild, member);
        Role role = requireMuteRole(guild);
        muteOwnership.requirePermissions(guild);
        boolean rolePresent = member.getRoles().contains(role);
        if (rolePresent) {
            muteOwnership.requireOwnedCurrentRole(guild, role, punishment);
        }
        ensureMutePolicy(guild);
        if (!rolePresent) {
            guild.addRoleToMember(UserSnowflake.fromId(target.value()), role)
                    .reason(muteOwnership.auditReason(punishment))
                    .complete();
        }
    }

    private void removeMute(Guild guild, DiscordPunishment punishment) {
        DiscordUserId target = punishment.targetUserId();
        Member member = memberOrNull(guild, target);
        if (member == null) {
            return;
        }
        Role role = requireMuteRole(guild);
        requireHierarchy(guild, member);
        if (member.getRoles().contains(role)) {
            muteOwnership.requireOwnedCurrentRole(guild, role, punishment);
            guild.removeRoleFromMember(UserSnowflake.fromId(target.value()), role)
                    .reason("Enthusia D07 mute removal " + punishment.punishmentId()).complete();
        }
    }
'''
text = replace_once(text, old, new, "mute reconcile and removal ownership")
write(path, text)

path = "staff-bot/src/test/java/net/enthusia/staff/discordbot/JdaDiscordPunishmentGatewayTest.java"
text = read(path)
text = text.replace("import java.util.UUID;\n", "import java.util.List;\nimport java.util.Set;\nimport java.util.UUID;\n")
marker = '''    private static boolean proves(JdaMuteRoleOwnership.Observation observation) {
'''
tests = '''    @Test
    void currentMuteOwnershipRequiresLatestRoleChangeToBeOwnedAssignment() {
        long roleId = 789L;
        String ownedReason = JdaMuteRoleOwnership.marker(PUNISHMENT_ID) + " reason";
        JdaMuteRoleOwnership.Observation owned = roleObservation(
                TARGET_ID, BOT_ID, ownedReason, ISSUED_AT.plusSeconds(1), roleId
        );
        JdaMuteRoleOwnership.Observation external = roleObservation(
                TARGET_ID, BOT_ID + 1, "manual mute", ISSUED_AT.plusSeconds(2), roleId
        );

        assertTrue(currentlyOwned(List.of(owned), roleId));
        assertFalse(currentlyOwned(List.of(owned, external), roleId));
        assertFalse(currentlyOwned(List.of(external), roleId));
    }

    private static boolean currentlyOwned(List<JdaMuteRoleOwnership.Observation> observations, long roleId) {
        return JdaMuteRoleOwnership.provesCurrentOwnership(
                observations, PUNISHMENT_ID, TARGET_ID, BOT_ID, roleId, ISSUED_AT
        );
    }

    private static JdaMuteRoleOwnership.Observation roleObservation(
            long targetId,
            long actorId,
            String reason,
            Instant createdAt,
            long roleId
    ) {
        return new JdaMuteRoleOwnership.Observation(
                ActionType.MEMBER_ROLE_UPDATE,
                targetId,
                actorId,
                reason,
                createdAt,
                Set.of(roleId),
                Set.of()
        );
    }

'''
text = replace_once(text, marker, tests + marker, "current role ownership regressions")
write(path, text)
