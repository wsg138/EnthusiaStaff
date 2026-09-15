package net.enthusia.staff.discordbot;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPermissionSnapshot;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordRestrictionTarget;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;

/** Blocking JDA effect adapter used only by the dedicated D07 worker thread. */
final class JdaDiscordPunishmentGateway implements DiscordPunishmentGateway {
    private static final long COMMUNICATION_MASK = Permission.getRaw(
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_SEND_IN_THREADS,
            Permission.MESSAGE_ADD_REACTION,
            Permission.MESSAGE_ATTACH_FILES,
            Permission.VOICE_SPEAK
    );
    private static final long READ_ONLY_MASK = Permission.getRaw(
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_SEND_IN_THREADS,
            Permission.MESSAGE_ADD_REACTION,
            Permission.MESSAGE_ATTACH_FILES
    );
    private static final long NO_ACCESS_MASK = Permission.getRaw(Permission.VIEW_CHANNEL);
    private static final String UNKNOWN_MEMBER = "UNKNOWN_MEMBER";
    private static final String UNKNOWN_USER = "UNKNOWN_USER";
    private static final String TARGET_NOT_IN_GUILD = "TARGET_NOT_IN_GUILD";
    private static final String UNSUPPORTED_CONSEQUENCE = "UNSUPPORTED_CONSEQUENCE";

    private final DiscordPunishmentConfiguration configuration;
    private final JdaNativeBanEnforcer nativeBans = new JdaNativeBanEnforcer();
    private final JdaKickEnforcer kicks = new JdaKickEnforcer();
    private final JdaMuteRoleOwnership muteOwnership = new JdaMuteRoleOwnership();
    private final JdaPunishmentNotifier notifier;
    private final AtomicReference<JDA> jda = new AtomicReference<>();

    JdaDiscordPunishmentGateway(DiscordPunishmentConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("Discord punishment configuration must be present");
        }
        this.configuration = configuration;
        this.notifier = new JdaPunishmentNotifier(configuration);
    }

    void bind(JDA value) {
        if (value == null) {
            throw new IllegalArgumentException("JDA must be present");
        }
        JDA current = jda.get();
        if (current == value) {
            return;
        }
        if (current != null || !jda.compareAndSet(null, value)) {
            throw new IllegalStateException("Discord punishment gateway is already bound");
        }
    }

    void unbind() {
        jda.set(null);
    }

    @Override
    public void preflight(DiscordGuildId guildId, DiscordUserId target, DiscordPunishmentIntent intent) {
        try {
            Guild guild = guild(guildId);
            Member member = memberOrNull(guild, target);
            if (intent.type() != DiscordConsequenceType.BAN && member == null) {
                throw failure(TARGET_NOT_IN_GUILD, false);
            }
            if (member != null) {
                requireHierarchy(guild, member);
            }
            switch (intent.type()) {
                case MUTE -> requireMuteAvailable(guild, member);
                case BAN -> nativeBans.preflight(guild, target);
                case CHANNEL_RESTRICTION -> restrictionContainer(guild, intent.restriction().orElseThrow());
                case KICK -> kicks.preflight(guild);
                case WARNING -> { }
                default -> throw failure(UNSUPPORTED_CONSEQUENCE, false);
            }
        } catch (RuntimeException failure) {
            throw classify("PREFLIGHT", failure);
        }
    }

    @Override
    public DiscordPermissionSnapshot captureRestrictionSnapshot(DiscordPunishment punishment) {
        try {
            Guild guild = guild(punishment.guildId());
            Member member = memberRequired(guild, punishment.targetUserId());
            requireHierarchy(guild, member);
            IPermissionContainer container = restrictionContainer(
                    guild, punishment.intent().restriction().orElseThrow()
            );
            return snapshot(container.getPermissionOverride(member));
        } catch (RuntimeException failure) {
            throw classify("SNAPSHOT", failure);
        }
    }

    @Override
    public void apply(DiscordPunishment punishment, int attemptCount) {
        try {
            applyEffect(guild(punishment.guildId()), punishment, attemptCount);
        } catch (RuntimeException failure) {
            throw classify("APPLY", failure);
        }
    }

    @Override
    public DiscordDeliveryOutcome notifyApplied(DiscordPunishment punishment) {
        return notifySafely(punishment, false);
    }

    @Override
    public void remove(DiscordPunishment punishment) {
        try {
            Guild guild = guild(punishment.guildId());
            switch (punishment.intent().type()) {
                case MUTE -> removeMute(guild, punishment.targetUserId());
                case BAN -> nativeBans.remove(guild, punishment);
                case CHANNEL_RESTRICTION -> removeRestriction(guild, punishment);
                case WARNING, KICK -> { }
                default -> throw failure(UNSUPPORTED_CONSEQUENCE, false);
            }
        } catch (RuntimeException failure) {
            throw classify("REMOVE", failure);
        }
    }

    @Override
    public DiscordDeliveryOutcome notifyRemoved(DiscordPunishment punishment) {
        return notifySafely(punishment, true);
    }

    @Override
    public void reconcile(DiscordPunishment punishment) {
        try {
            Guild guild = guild(punishment.guildId());
            switch (punishment.intent().type()) {
                case MUTE -> reconcileMute(guild, punishment.targetUserId());
                case BAN -> nativeBans.reconcile(guild, punishment);
                case CHANNEL_RESTRICTION -> applyRestriction(guild, punishment);
                case WARNING, KICK -> { }
                default -> throw failure(UNSUPPORTED_CONSEQUENCE, false);
            }
        } catch (RuntimeException failure) {
            throw classify("RECONCILE", failure);
        }
    }

    private DiscordDeliveryOutcome notifySafely(DiscordPunishment punishment, boolean removal) {
        try {
            JDA current = boundJda();
            return removal
                    ? notifier.notifyRemoved(current, punishment)
                    : notifier.notifyApplied(current, punishment);
        } catch (EffectException failure) {
            return failure.retryable()
                    ? DiscordDeliveryOutcome.FAILED_RETRYABLE
                    : DiscordDeliveryOutcome.FAILED_TERMINAL;
        }
    }

    private void applyEffect(Guild guild, DiscordPunishment punishment, int attemptCount) {
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

    private void applyBan(Guild guild, DiscordPunishment punishment) {
        Member member = memberOrNull(guild, punishment.targetUserId());
        if (member != null) {
            requireHierarchy(guild, member);
        }
        nativeBans.apply(guild, punishment);
    }

    private void applyMute(Guild guild, DiscordPunishment punishment) {
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

    private void reconcileMute(Guild guild, DiscordUserId target) {
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

    private void requireMuteAvailable(Guild guild, Member member) {
        if (member == null) {
            throw failure(TARGET_NOT_IN_GUILD, false);
        }
        Role role = requireMuteRole(guild);
        muteOwnership.requirePermissions(guild);
        JdaMuteRoleOwnership.requireFreshRoleAbsent(member.getRoles().contains(role));
    }

    private void ensureMutePolicy(Guild guild) {
        Role role = requireMuteRole(guild);
        for (GuildChannel channel : guild.getChannels()) {
            if (channel instanceof IPermissionContainer container) {
                enforceMuteOverride(channel, container, role);
            }
        }
    }

    private void enforceMuteOverride(GuildChannel channel, IPermissionContainer container, Role role) {
        boolean support = supportScope(channel, configuration.supportScopeIds());
        PermissionOverride override = container.getPermissionOverride(role);
        long allowed = override == null ? 0L : override.getAllowedRaw();
        long denied = override == null ? 0L : override.getDeniedRaw();
        long nextAllowed = support ? allowed | COMMUNICATION_MASK : allowed & ~COMMUNICATION_MASK;
        long nextDenied = support ? denied & ~COMMUNICATION_MASK : denied | COMMUNICATION_MASK;
        if (nextAllowed != allowed || nextDenied != denied || override == null) {
            container.upsertPermissionOverride(role)
                    .setAllowed(nextAllowed)
                    .setDenied(nextDenied)
                    .reason("Enthusia D07 managed mute policy")
                    .complete();
        }
    }

    private void applyRestriction(Guild guild, DiscordPunishment punishment) {
        Member member = memberRequired(guild, punishment.targetUserId());
        requireHierarchy(guild, member);
        DiscordRestrictionTarget target = punishment.intent().restriction().orElseThrow();
        IPermissionContainer container = restrictionContainer(guild, target);
        DiscordPermissionSnapshot original = punishment.previousRestriction().orElseThrow(
                () -> failure("RESTRICTION_SNAPSHOT_MISSING", false)
        );
        DiscordPermissionSnapshot current = snapshot(container.getPermissionOverride(member));
        DiscordPermissionSnapshot desired = restricted(original, target.mode());
        if (same(current, desired)) {
            return;
        }
        if (!same(current, original)) {
            throw failure("RESTRICTION_STATE_CHANGED", false);
        }
        writeSnapshot(container, member, desired, "Enthusia D07 member restriction");
    }

    private void removeRestriction(Guild guild, DiscordPunishment punishment) {
        Member member = memberOrNull(guild, punishment.targetUserId());
        if (member == null) {
            throw failure(TARGET_NOT_IN_GUILD, true);
        }
        DiscordRestrictionTarget target = punishment.intent().restriction().orElseThrow();
        IPermissionContainer container = restrictionContainer(guild, target);
        DiscordPermissionSnapshot original = punishment.previousRestriction().orElseThrow(
                () -> failure("RESTRICTION_SNAPSHOT_MISSING", false)
        );
        DiscordPermissionSnapshot current = snapshot(container.getPermissionOverride(member));
        if (same(current, original)) {
            return;
        }
        if (!same(current, restricted(original, target.mode()))) {
            throw failure("RESTRICTION_STATE_CHANGED", false);
        }
        writeSnapshot(container, member, original, "Enthusia D07 restriction removal");
    }

    private static DiscordPermissionSnapshot snapshot(PermissionOverride override) {
        return override == null
                ? DiscordPermissionSnapshot.absent()
                : new DiscordPermissionSnapshot(true, override.getAllowedRaw(), override.getDeniedRaw());
    }

    private static DiscordPermissionSnapshot restricted(
            DiscordPermissionSnapshot original,
            DiscordRestrictionTarget.Mode mode
    ) {
        long mask = restrictionMask(mode);
        return new DiscordPermissionSnapshot(
                true,
                original.allowedRaw() & ~mask,
                original.deniedRaw() | mask
        );
    }

    private static boolean same(DiscordPermissionSnapshot first, DiscordPermissionSnapshot second) {
        return first.existed() == second.existed()
                && first.allowedRaw() == second.allowedRaw()
                && first.deniedRaw() == second.deniedRaw();
    }

    private static void writeSnapshot(
            IPermissionContainer container,
            Member member,
            DiscordPermissionSnapshot desired,
            String reason
    ) {
        PermissionOverride current = container.getPermissionOverride(member);
        if (!desired.existed()) {
            if (current != null) {
                current.delete().reason(reason).complete();
            }
            return;
        }
        container.upsertPermissionOverride(member)
                .setAllowed(desired.allowedRaw())
                .setDenied(desired.deniedRaw())
                .reason(reason)
                .complete();
    }

    private IPermissionContainer restrictionContainer(Guild guild, DiscordRestrictionTarget target) {
        GuildChannel channel = guild.getGuildChannelById(target.snowflake());
        if (!(channel instanceof IPermissionContainer container)) {
            throw failure("RESTRICTION_SCOPE_UNAVAILABLE", false);
        }
        boolean category = channel instanceof Category;
        if ((target.kind() == DiscordRestrictionTarget.Kind.CATEGORY) != category) {
            throw failure("RESTRICTION_SCOPE_KIND_MISMATCH", false);
        }
        return container;
    }

    private Guild guild(DiscordGuildId expected) {
        Guild guild = boundJda().getGuildById(expected.value());
        if (guild == null) {
            throw failure("GUILD_UNAVAILABLE", true);
        }
        return guild;
    }

    private JDA boundJda() {
        JDA value = jda.get();
        if (value == null) {
            throw failure("GATEWAY_NOT_READY", true);
        }
        return value;
    }

    private Member memberRequired(Guild guild, DiscordUserId target) {
        Member member = memberOrNull(guild, target);
        if (member == null) {
            throw failure(TARGET_NOT_IN_GUILD, false);
        }
        return member;
    }

    private Member memberOrNull(Guild guild, DiscordUserId target) {
        try {
            return guild.retrieveMemberById(target.value()).complete();
        } catch (ErrorResponseException exception) {
            String code = exception.getErrorResponse().name();
            if (UNKNOWN_MEMBER.equals(code) || UNKNOWN_USER.equals(code)) {
                return null;
            }
            throw exception;
        }
    }

    private void requireHierarchy(Guild guild, Member target) {
        if (target.getIdLong() == guild.getSelfMember().getIdLong() || !guild.getSelfMember().canInteract(target)) {
            throw failure("DISCORD_HIERARCHY_DENIED", false);
        }
    }

    private Role requireMuteRole(Guild guild) {
        Role role = guild.getRoleById(configuration.muteRoleId());
        if (role == null) {
            throw failure("MUTE_ROLE_UNAVAILABLE", false);
        }
        if (!guild.getSelfMember().canInteract(role)) {
            throw failure("MUTE_ROLE_HIERARCHY_DENIED", false);
        }
        return role;
    }

    private static boolean supportScope(GuildChannel channel, Set<String> scopes) {
        if (scopes.contains(channel.getId())) {
            return true;
        }
        if (channel instanceof ICategorizableChannel categorized) {
            String parentId = categorized.getParentCategoryId();
            return parentId != null && scopes.contains(parentId);
        }
        return false;
    }

    private static long restrictionMask(DiscordRestrictionTarget.Mode mode) {
        return mode == DiscordRestrictionTarget.Mode.NO_ACCESS ? NO_ACCESS_MASK : READ_ONLY_MASK;
    }

    private static EffectException classify(String phase, RuntimeException failure) {
        if (failure instanceof EffectException effect) {
            return effect;
        }
        if (failure instanceof InsufficientPermissionException || failure instanceof HierarchyException) {
            return new EffectException(phase + "_PERMISSION_DENIED", false, failure);
        }
        if (failure instanceof ErrorResponseException response) {
            String code = response.getErrorResponse().name();
            boolean retryable = retryableCode(code);
            return new EffectException(phase + "_DISCORD_" + code, retryable, failure);
        }
        return new EffectException(phase + "_TRANSPORT_FAILURE", true, failure);
    }

    private static boolean retryableCode(String code) {
        return code.contains("SERVER") || code.contains("TEMPORAR") || code.contains("RATE_LIMIT");
    }

    private static EffectException failure(String code, boolean retryable) {
        return new EffectException(code, retryable);
    }

}
