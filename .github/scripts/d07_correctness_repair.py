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


# Fail-closed exact-scope unrestrict orchestration.
path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentService.java"
text = read(path)
text = replace_once(
    text,
    "    private static final int ACTIVE_LOOKUP_LIMIT = 20;\n",
    "    private static final int ACTIVE_LOOKUP_LIMIT = 500;\n"
    "    private static final long INVALID_SNOWFLAKE = 0L;\n",
    "service active lookup bound",
)
old = """    Confirmation prepareRemoval(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            DiscordConsequenceType type,
            DiscordPunishmentTermination termination
    ) {
        requireRemovable(type, termination);
        DiscordUserId targetUserId = discordUser(targetDiscordId);
        StoredPunishment active = newestActive(targetUserId, type);
        StaffModerationReadService.Target target = reads.discordTarget(targetUserId);
        Actor actor = actor(actorDiscordId, actorName);
        DiscordAuthorizationSnapshot snapshot = authorization.captureMutation(
                actor, actors.targetStaff(target), operationFor(termination)
        );
        UUID token = confirmations.put(expires -> new DiscordPunishmentConfirmationStore.Draft(
                DiscordPunishmentConfirmationStore.Kind.REMOVE,
                targetUserId,
                Optional.of(active.punishment().punishmentId()),
                Optional.empty(),
                Optional.of(type),
                termination,
                snapshot,
                expires
        ));
        return new Confirmation(token, type, targetUserId.value(), "remove");
    }
"""
new = """    Confirmation prepareRemoval(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            DiscordConsequenceType type,
            DiscordPunishmentTermination termination
    ) {
        requireGenericRemovable(type, termination);
        DiscordUserId targetUserId = discordUser(targetDiscordId);
        StoredPunishment active = newestActive(targetUserId, type);
        return prepareRemovalConfirmation(
                actorDiscordId, actorName, targetUserId, active, type, termination
        );
    }

    Confirmation prepareRestrictionRemoval(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            String scopeId,
            DiscordPunishmentTermination termination
    ) {
        requireInteractiveTermination(termination);
        DiscordUserId targetUserId = discordUser(targetDiscordId);
        StoredPunishment active = exactActiveRestriction(targetUserId, scopeId);
        return prepareRemovalConfirmation(
                actorDiscordId,
                actorName,
                targetUserId,
                active,
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                termination
        );
    }

    private Confirmation prepareRemovalConfirmation(
            long actorDiscordId,
            String actorName,
            DiscordUserId targetUserId,
            StoredPunishment active,
            DiscordConsequenceType type,
            DiscordPunishmentTermination termination
    ) {
        StaffModerationReadService.Target target = reads.discordTarget(targetUserId);
        Actor actor = actor(actorDiscordId, actorName);
        DiscordAuthorizationSnapshot snapshot = authorization.captureMutation(
                actor, actors.targetStaff(target), operationFor(termination)
        );
        UUID token = confirmations.put(expires -> new DiscordPunishmentConfirmationStore.Draft(
                DiscordPunishmentConfirmationStore.Kind.REMOVE,
                targetUserId,
                Optional.of(active.punishment().punishmentId()),
                Optional.empty(),
                Optional.of(type),
                termination,
                snapshot,
                expires
        ));
        return new Confirmation(token, type, targetUserId.value(), "remove");
    }
"""
text = replace_once(text, old, new, "service removal orchestration")
marker = """    private StoredPunishment newestActive(DiscordUserId targetUserId, DiscordConsequenceType type) {
"""
helper = """    private StoredPunishment exactActiveRestriction(DiscordUserId targetUserId, String scopeId) {
        List<StoredPunishment> active = punishments.activeForTarget(
                guildId,
                targetUserId,
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                ACTIVE_LOOKUP_LIMIT
        );
        return selectExactRestriction(active, scopeId);
    }

    static StoredPunishment selectExactRestriction(List<StoredPunishment> active, String scopeId) {
        if (active == null) {
            throw new IllegalArgumentException("active restrictions must be present");
        }
        String normalizedScope = normalizeSnowflake(scopeId);
        List<StoredPunishment> matches = active.stream()
                .filter(stored -> stored.punishment().intent().type() == DiscordConsequenceType.CHANNEL_RESTRICTION)
                .filter(stored -> stored.punishment().intent().restriction()
                        .map(restriction -> restriction.snowflake().equals(normalizedScope))
                        .orElse(false))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("restriction scope does not resolve to exactly one active punishment");
        }
        return matches.getFirst();
    }

"""
text = replace_once(text, marker, helper + marker, "service exact restriction selector")
old = """    private static void requireRemovable(
            DiscordConsequenceType type,
            DiscordPunishmentTermination termination
    ) {
        if (type == null || termination == null || termination == DiscordPunishmentTermination.NONE
                || termination == DiscordPunishmentTermination.EXPIRE
                || (type != DiscordConsequenceType.MUTE
                    && type != DiscordConsequenceType.BAN
                    && type != DiscordConsequenceType.CHANNEL_RESTRICTION)) {
            throw new IllegalArgumentException("interactive removal request is invalid");
        }
    }
"""
new = """    private static void requireGenericRemovable(
            DiscordConsequenceType type,
            DiscordPunishmentTermination termination
    ) {
        requireInteractiveTermination(termination);
        if (type != DiscordConsequenceType.MUTE && type != DiscordConsequenceType.BAN) {
            throw new IllegalArgumentException("generic removal only supports mute or ban");
        }
    }

    private static void requireInteractiveTermination(DiscordPunishmentTermination termination) {
        if (termination == null || termination == DiscordPunishmentTermination.NONE
                || termination == DiscordPunishmentTermination.EXPIRE) {
            throw new IllegalArgumentException("interactive removal request is invalid");
        }
    }

    private static String normalizeSnowflake(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("restriction scope id is required");
        }
        try {
            long parsed = Long.parseUnsignedLong(value.trim());
            if (parsed == INVALID_SNOWFLAKE) {
                throw new IllegalArgumentException("restriction scope id must be positive");
            }
            return Long.toUnsignedString(parsed);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("restriction scope id must be an unsigned Discord snowflake", failure);
        }
    }
"""
text = replace_once(text, old, new, "service generic removal guard")
write(path, text)

path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentCommandController.java"
text = read(path)
old = """    Prepared remove(long actorId, String actorName, long targetId, DiscordConsequenceType type) {
        DiscordPunishmentService.Confirmation confirmation = service.prepareRemoval(
                actorId,
                actorName,
                targetId,
                type,
                DiscordPunishmentTermination.END
        );
        return new Prepared(
                "Confirm ending the active Discord " + display(type) + " for <@" + confirmation.targetUserId() + ">.",
                CONFIRM_REMOVE_PREFIX + confirmation.token()
        );
    }
"""
new = """    Prepared remove(long actorId, String actorName, long targetId, DiscordConsequenceType type) {
        DiscordPunishmentService.Confirmation confirmation = service.prepareRemoval(
                actorId,
                actorName,
                targetId,
                type,
                DiscordPunishmentTermination.END
        );
        return preparedRemoval(confirmation, type);
    }

    Prepared unrestrict(long actorId, String actorName, long targetId, String scopeId) {
        DiscordPunishmentService.Confirmation confirmation = service.prepareRestrictionRemoval(
                actorId,
                actorName,
                targetId,
                scopeId,
                DiscordPunishmentTermination.END
        );
        return preparedRemoval(confirmation, DiscordConsequenceType.CHANNEL_RESTRICTION);
    }

    private static Prepared preparedRemoval(
            DiscordPunishmentService.Confirmation confirmation,
            DiscordConsequenceType type
    ) {
        return new Prepared(
                "Confirm ending the active Discord " + display(type) + " for <@" + confirmation.targetUserId() + ">.",
                CONFIRM_REMOVE_PREFIX + confirmation.token()
        );
    }
"""
text = replace_once(text, old, new, "controller exact unrestrict")
write(path, text)

path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaStaffModerationListener.java"
text = read(path)
text = replace_once(
    text,
    """            case UNRESTRICT -> () -> punishment.remove(
                    actorId, actorName, targetId, DiscordConsequenceType.CHANNEL_RESTRICTION);
""",
    """            case UNRESTRICT -> () -> punishment.unrestrict(
                    actorId, actorName, targetId, option(event, SCOPE_ID_OPTION, ""));
""",
    "listener unrestrict dispatch",
)
text = replace_once(
    text,
    """                restrictSlash(discovery),
                removalSlash(UNRESTRICT, "End the active Discord restriction", discovery)
""",
    """                restrictSlash(discovery),
                restrictionRemovalSlash(discovery)
""",
    "listener unrestrict command",
)
marker = """    private static CommandData removalSlash(
"""
helper = """    private static CommandData restrictionRemovalSlash(DefaultMemberPermissions discovery) {
        return Commands.slash(UNRESTRICT, "End a Discord restriction on one exact scope")
                .addOptions(
                        userOption(),
                        stringOption(SCOPE_ID_OPTION, "Exact Discord channel/category ID", true)
                )
                .setDefaultPermissions(discovery);
    }

"""
text = replace_once(text, marker, helper + marker, "listener unrestrict command builder")
write(path, text)

# Claim one blocking Discord operation at a time and keep recoverable configuration failures periodic.
path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentWorker.java"
text = read(path)
text = replace_once(text, "    private static final int CLAIM_LIMIT = 20;\n", "    private static final int CLAIM_LIMIT = 1;\n", "worker claim limit")
text = replace_once(
    text,
    "import java.util.Optional;\n",
    "import java.util.Optional;\nimport java.util.Set;\n",
    "worker set import",
)
text = replace_once(
    text,
    """    private static final String TARGET_NOT_IN_GUILD = "TARGET_NOT_IN_GUILD";
""",
    """    private static final String TARGET_NOT_IN_GUILD = "TARGET_NOT_IN_GUILD";
    private static final Set<String> PERIODIC_RECONCILIATION_ERRORS = Set.of(
            NATIVE_BAN_OWNERSHIP_CONFLICT,
            TARGET_NOT_IN_GUILD,
            "DISCORD_HIERARCHY_DENIED",
            "MUTE_ROLE_UNAVAILABLE",
            "MUTE_ROLE_HIERARCHY_DENIED",
            "RECONCILE_PERMISSION_DENIED"
    );
""",
    "worker recoverable configuration errors",
)
text = replace_once(
    text,
    """    private static boolean keepsPeriodicReconciliation(String errorCode) {
        return NATIVE_BAN_OWNERSHIP_CONFLICT.equals(errorCode) || TARGET_NOT_IN_GUILD.equals(errorCode);
    }
""",
    """    private static boolean keepsPeriodicReconciliation(String errorCode) {
        return PERIODIC_RECONCILIATION_ERRORS.contains(errorCode);
    }
""",
    "worker periodic error set",
)
write(path, text)

# Mute ownership proof is isolated from the general JDA gateway.
path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaDiscordPunishmentGateway.java"
text = read(path)
text = replace_once(
    text,
    "    private final JdaNativeBanEnforcer nativeBans = new JdaNativeBanEnforcer();\n",
    "    private final JdaNativeBanEnforcer nativeBans = new JdaNativeBanEnforcer();\n"
    "    private final JdaMuteRoleOwnership muteOwnership = new JdaMuteRoleOwnership();\n",
    "gateway mute ownership field",
)
old = """    private void applyMute(Guild guild, DiscordPunishment punishment) {
        DiscordUserId target = punishment.targetUserId();
        Member member = memberRequired(guild, target);
        requireHierarchy(guild, member);
        Role role = requireMuteRole(guild);
        requireMuteRoleUnowned(member.getRoles().contains(role));
        ensureMutePolicy(guild);
        guild.addRoleToMember(UserSnowflake.fromId(target.value()), role)
                .reason(auditReason(punishment))
                .complete();
    }
"""
new = """    private void applyMute(Guild guild, DiscordPunishment punishment) {
        DiscordUserId target = punishment.targetUserId();
        Member member = memberRequired(guild, target);
        requireHierarchy(guild, member);
        Role role = requireMuteRole(guild);
        muteOwnership.requirePermissions(guild);
        boolean rolePresent = member.getRoles().contains(role);
        if (rolePresent) {
            muteOwnership.requireOwnedApplyRetry(guild, punishment);
        }
        ensureMutePolicy(guild);
        if (!rolePresent) {
            guild.addRoleToMember(UserSnowflake.fromId(target.value()), role)
                    .reason(muteOwnership.auditReason(punishment))
                    .complete();
        }
    }
"""
text = replace_once(text, old, new, "gateway crash-safe mute apply")
old = """    private void requireMuteAvailable(Guild guild, Member member) {
        if (member == null) {
            throw failure("TARGET_NOT_IN_GUILD", false);
        }
        Role role = requireMuteRole(guild);
        requireMuteRoleUnowned(member.getRoles().contains(role));
    }

    static void requireMuteRoleUnowned(boolean rolePresent) {
        if (rolePresent) {
            throw failure("MUTE_ROLE_ALREADY_PRESENT", false);
        }
    }
"""
new = """    private void requireMuteAvailable(Guild guild, Member member) {
        if (member == null) {
            throw failure("TARGET_NOT_IN_GUILD", false);
        }
        Role role = requireMuteRole(guild);
        muteOwnership.requirePermissions(guild);
        JdaMuteRoleOwnership.requireFreshRoleAbsent(member.getRoles().contains(role));
    }
"""
text = replace_once(text, old, new, "gateway mute preflight ownership")
write(path, text)

ownership = """package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.enthusia.staff.domain.discord.DiscordPunishment;

/** Proves managed-mute ownership across a crash without adopting externally assigned roles. */
final class JdaMuteRoleOwnership {
    private static final int AUDIT_LIMIT = 100;
    private static final int MAX_AUDIT_REASON = 500;
    private static final Duration AUDIT_CLOCK_SKEW = Duration.ofMinutes(1);
    private static final String MARKER_PREFIX = "Enthusia D07 mute ";

    record Observation(ActionType type, long targetId, long actorId, String reason, Instant createdAt) {
    }

    void requirePermissions(Guild guild) {
        Member self = guild.getSelfMember();
        if (!self.hasPermission(Permission.MANAGE_ROLES)) {
            throw failure("MUTE_ROLE_MANAGE_PERMISSION_MISSING", false);
        }
        if (!self.hasPermission(Permission.VIEW_AUDIT_LOGS)) {
            throw failure("MUTE_AUDIT_LOG_PERMISSION_MISSING", false);
        }
    }

    static void requireFreshRoleAbsent(boolean rolePresent) {
        if (rolePresent) {
            throw failure("MUTE_ROLE_ALREADY_PRESENT", false);
        }
    }

    void requireOwnedApplyRetry(Guild guild, DiscordPunishment punishment) {
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

    String auditReason(DiscordPunishment punishment) {
        String reason = marker(punishment.punishmentId()) + " " + punishment.intent().publicReason();
        return reason.length() <= MAX_AUDIT_REASON ? reason : reason.substring(0, MAX_AUDIT_REASON);
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
        return observation.type() == ActionType.MEMBER_ROLE_UPDATE
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

    private static Observation observation(AuditLogEntry entry) {
        return new Observation(
                entry.getType(),
                entry.getTargetIdLong(),
                entry.getUserIdLong(),
                entry.getReason(),
                entry.getTimeCreated().toInstant()
        );
    }

    private static DiscordPunishmentGateway.EffectException failure(String code, boolean retryable) {
        return new DiscordPunishmentGateway.EffectException(code, retryable);
    }
}
"""
write("staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaMuteRoleOwnership.java", ownership)

# Deterministic service selector coverage for multiple simultaneous restriction scopes.
service_test = """package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordRestrictionTarget;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.StoredPunishment;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordPunishmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");
    private static final String REQUESTED_SCOPE = "5001";
    private static final String OTHER_SCOPE = "5002";

    @Test
    void exactRestrictionSelectionUsesRequestedScopeInsteadOfNewest() {
        StoredPunishment requested = restriction(REQUESTED_SCOPE);
        StoredPunishment newestDifferentScope = restriction(OTHER_SCOPE);

        StoredPunishment selected = DiscordPunishmentService.selectExactRestriction(
                List.of(newestDifferentScope, requested),
                REQUESTED_SCOPE
        );

        assertEquals(requested.punishment().punishmentId(), selected.punishment().punishmentId());
    }

    @Test
    void exactRestrictionSelectionFailsClosedForMissingOrDuplicateScope() {
        StoredPunishment first = restriction(REQUESTED_SCOPE);
        StoredPunishment duplicate = restriction(REQUESTED_SCOPE);

        assertThrows(IllegalStateException.class, () -> DiscordPunishmentService.selectExactRestriction(
                List.of(first), OTHER_SCOPE
        ));
        assertThrows(IllegalStateException.class, () -> DiscordPunishmentService.selectExactRestriction(
                List.of(first, duplicate), REQUESTED_SCOPE
        ));
    }

    private static StoredPunishment restriction(String scopeId) {
        DiscordPunishmentIntent intent = new DiscordPunishmentIntent(
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                SanctionLength.temporary(Duration.ofHours(1)),
                false,
                false,
                Optional.of(new DiscordRestrictionTarget(
                        DiscordRestrictionTarget.Kind.CHANNEL,
                        scopeId,
                        DiscordRestrictionTarget.Mode.READ_ONLY
                )),
                "Reason",
                "",
                0,
                true
        );
        DiscordPunishment punishment = DiscordPunishment.pending(
                UUID.randomUUID(),
                new ModerationSubjectId(UUID.randomUUID()),
                new DiscordUserId("123"),
                new DiscordGuildId("456"),
                new Actor(UUID.randomUUID(), "staff", StaffRank.ADMIN),
                intent,
                NOW,
                "issue"
        );
        return new StoredPunishment(punishment, 0, false);
    }
}
"""
write("staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordPunishmentServiceTest.java", service_test)

# Mute marker matching covers crash recovery without unsafe role adoption.
gateway_test = """package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.dv8tion.jda.api.audit.ActionType;
import org.junit.jupiter.api.Test;

class JdaDiscordPunishmentGatewayTest {
    private static final UUID PUNISHMENT_ID = UUID.fromString("6ebc2fc4-cbce-4a52-92f8-aa1183899fd3");
    private static final Instant ISSUED_AT = Instant.parse("2026-09-14T20:00:00Z");
    private static final long TARGET_ID = 123L;
    private static final long BOT_ID = 456L;

    @Test
    void freshMuteRejectsAnAlreadyPresentManagedRole() {
        DiscordPunishmentGateway.EffectException failure = assertThrows(
                DiscordPunishmentGateway.EffectException.class,
                () -> JdaMuteRoleOwnership.requireFreshRoleAbsent(true)
        );

        assertEquals("MUTE_ROLE_ALREADY_PRESENT", failure.errorCode());
        assertFalse(failure.retryable());
    }

    @Test
    void freshMuteAcceptsAnAbsentManagedRole() {
        assertDoesNotThrow(() -> JdaMuteRoleOwnership.requireFreshRoleAbsent(false));
    }

    @Test
    void applyRetryRequiresExactBotTargetAndPunishmentMarker() {
        String ownedReason = JdaMuteRoleOwnership.marker(PUNISHMENT_ID) + " reason";
        JdaMuteRoleOwnership.Observation owned = observation(
                ActionType.MEMBER_ROLE_UPDATE, TARGET_ID, BOT_ID, ownedReason, ISSUED_AT.plusSeconds(1)
        );

        assertTrue(proves(owned));
        assertFalse(proves(observation(
                ActionType.MEMBER_UPDATE, TARGET_ID, BOT_ID, ownedReason, ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.MEMBER_ROLE_UPDATE, TARGET_ID + 1, BOT_ID, ownedReason, ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.MEMBER_ROLE_UPDATE, TARGET_ID, BOT_ID + 1, ownedReason, ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.MEMBER_ROLE_UPDATE, TARGET_ID, BOT_ID, "manual role assignment", ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.MEMBER_ROLE_UPDATE,
                TARGET_ID,
                BOT_ID,
                JdaMuteRoleOwnership.marker(UUID.randomUUID()) + " reason",
                ISSUED_AT.plusSeconds(1)
        )));
        assertFalse(proves(observation(
                ActionType.MEMBER_ROLE_UPDATE,
                TARGET_ID,
                BOT_ID,
                ownedReason,
                ISSUED_AT.minusSeconds(120)
        )));
    }

    private static boolean proves(JdaMuteRoleOwnership.Observation observation) {
        return JdaMuteRoleOwnership.provesOwnership(
                observation, PUNISHMENT_ID, TARGET_ID, BOT_ID, ISSUED_AT
        );
    }

    private static JdaMuteRoleOwnership.Observation observation(
            ActionType type,
            long targetId,
            long actorId,
            String reason,
            Instant createdAt
    ) {
        return new JdaMuteRoleOwnership.Observation(type, targetId, actorId, reason, createdAt);
    }
}
"""
write("staff-bot/src/test/java/net/enthusia/staff/discordbot/JdaDiscordPunishmentGatewayTest.java", gateway_test)

# Listener definition must require scope-id for /unrestrict.
path = "staff-bot/src/test/java/net/enthusia/staff/discordbot/JdaStaffModerationListenerTest.java"
text = read(path)
marker = """    private static Set<String> names(java.util.List<CommandData> commands) {
"""
test = """    @Test
    void unrestrictRequiresExactScopeId() {
        CommandData unrestrict = command(JdaStaffModerationListener.commands(true), "unrestrict");

        assertEquals(
                java.util.List.of("user", "scope-id"),
                unrestrict.getOptions().stream().map(option -> option.getName()).toList()
        );
        assertTrue(unrestrict.getOptions().stream().allMatch(option -> option.isRequired()));
    }

"""
text = replace_once(text, marker, test + marker, "listener unrestrict scope test")
write(path, text)

# Worker boundary tests for lease safety and configuration recovery.
path = "staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordPunishmentWorkerTest.java"
text = read(path)
marker = """    @Test
    void nativeBanReconciliationConflictRemainsObservableWithoutMutationLoop() {
"""
tests = """    @Test
    void cycleClaimsOnlyOneBlockingDiscordWorkItem() {
        FakeRepository repository = new FakeRepository(punishment(warning(), NOW));
        FakeGateway gateway = new FakeGateway();
        repository.enqueue(WorkType.APPLY, NOW, 1);
        repository.enqueue(WorkType.APPLY, NOW, 1);

        int claimed = newWorker(repository, gateway).runCycle();

        assertEquals(1, claimed);
        assertEquals(1, repository.work.size());
        assertEquals(1, gateway.notifyAppliedCalls);
    }

    @Test
    void recoverablePermissionConfigurationUsesPeriodicReconciliation() {
        FakeRepository repository = new FakeRepository(appliedMute());
        FakeGateway gateway = new FakeGateway();
        gateway.reconcileFailure = new DiscordPunishmentGateway.EffectException(
                "RECONCILE_PERMISSION_DENIED", false
        );
        repository.enqueue(WorkType.RECONCILE, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(1, repository.work.size());
        assertEquals(WorkType.RECONCILE, repository.work.peek().type());
        assertEquals(NOW.plus(Duration.ofMinutes(1)), repository.work.peek().dueAt());
    }

"""
text = replace_once(text, marker, tests + marker, "worker lease and recovery tests")
write(path, text)
