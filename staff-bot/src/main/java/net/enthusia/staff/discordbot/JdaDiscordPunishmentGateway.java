package net.enthusia.staff.discordbot;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
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
    private static final int MAX_AUDIT_REASON = 500;

    private final DiscordPunishmentConfiguration configuration;
    private final AtomicReference<JDA> jda = new AtomicReference<>();

    JdaDiscordPunishmentGateway(DiscordPunishmentConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("Discord punishment configuration must be present");
        }
        this.configuration = configuration;
    }

    void bind(JDA value) {
        if (value == null || !jda.compareAndSet(null, value)) {
            throw new IllegalStateException("Discord punishment gateway is already bound or JDA is missing");
        }
    }

    void unbind() {
        jda.set(null);
    }

    @Override
    public void preflight(DiscordGuildId guildId, DiscordUserId target, DiscordPunishmentIntent intent) {
        Guild guild = guild(guildId);
        Member member = memberOrNull(guild, target);
        if (intent.type() != DiscordConsequenceType.BAN && member == null) {
            throw failure("TARGET_NOT_IN_GUILD", false);
        }
        if (member != null) {
            requireHierarchy(guild, member);
        }
        switch (intent.type()) {
            case MUTE -> requireMuteRole(guild);
            case CHANNEL_RESTRICTION -> restrictionContainer(guild, intent.restriction().orElseThrow());
            case WARNING, KICK, BAN -> { }
        }
    }

    @Override
    public ApplyResult apply(DiscordPunishment punishment) {
        Guild guild = guild(punishment.guildId());
        DiscordDeliveryOutcome delivery = notify(punishment);
        if (punishment.intent().type() == DiscordConsequenceType.WARNING) {
            if (delivery != DiscordDeliveryOutcome.DELIVERED) {
                throw new EffectException("WARNING_DM_FAILED", false, delivery, null);
            }
            return new ApplyResult(delivery, Optional.empty());
        }
        try {
            Optional<DiscordPermissionSnapshot> snapshot = applyEffect(guild, punishment);
            return new ApplyResult(delivery, snapshot);
        } catch (RuntimeException failure) {
            throw classify("APPLY", failure, delivery);
        }
    }

    @Override
    public void remove(DiscordPunishment punishment) {
        try {
            Guild guild = guild(punishment.guildId());
            switch (punishment.intent().type()) {
                case MUTE -> removeMute(guild, punishment.targetUserId());
                case BAN -> unban(guild, punishment.targetUserId());
                case CHANNEL_RESTRICTION -> removeRestriction(guild, punishment);
                case WARNING, KICK -> { }
            }
        } catch (RuntimeException failure) {
            throw classify("REMOVE", failure, DiscordDeliveryOutcome.NOT_ATTEMPTED);
        }
    }

    @Override
    public void reconcile(DiscordPunishment punishment) {
        try {
            Guild guild = guild(punishment.guildId());
            switch (punishment.intent().type()) {
                case MUTE -> reconcileMute(guild, punishment.targetUserId());
                case BAN -> guild.ban(UserSnowflake.fromId(punishment.targetUserId().value()), 0, TimeUnit.SECONDS)
                        .reason(auditReason(punishment)).complete();
                case CHANNEL_RESTRICTION -> reconcileRestriction(guild, punishment);
                case WARNING, KICK -> { }
            }
        } catch (RuntimeException failure) {
            throw classify("RECONCILE", failure, DiscordDeliveryOutcome.NOT_ATTEMPTED);
        }
    }

    private Optional<DiscordPermissionSnapshot> applyEffect(Guild guild, DiscordPunishment punishment) {
        DiscordUserId target = punishment.targetUserId();
        return switch (punishment.intent().type()) {
            case MUTE -> {
                applyMute(guild, target, auditReason(punishment));
                yield Optional.empty();
            }
            case KICK -> {
                Member member = memberOrNull(guild, target);
                if (member != null) {
                    requireHierarchy(guild, member);
                    guild.kick(UserSnowflake.fromId(target.value())).reason(auditReason(punishment)).complete();
                }
                yield Optional.empty();
            }
            case BAN -> {
                Member member = memberOrNull(guild, target);
                if (member != null) {
                    requireHierarchy(guild, member);
                }
                guild.ban(
                        UserSnowflake.fromId(target.value()),
                        punishment.intent().messageDeleteSeconds(),
                        TimeUnit.SECONDS
                ).reason(auditReason(punishment)).complete();
                yield Optional.empty();
            }
            case CHANNEL_RESTRICTION -> Optional.of(applyRestriction(guild, punishment));
            case WARNING -> Optional.empty();
        };
    }

    private void applyMute(Guild guild, DiscordUserId target, String reason) {
        ensureMutePolicy(guild);
        Member member = memberRequired(guild, target);
        requireHierarchy(guild, member);
        Role role = requireMuteRole(guild);
        guild.addRoleToMember(UserSnowflake.fromId(target.value()), role).reason(reason).complete();
    }

    private void reconcileMute(Guild guild, DiscordUserId target) {
        ensureMutePolicy(guild);
        Member member = memberOrNull(guild, target);
        if (member == null) {
            return;
        }
        requireHierarchy(guild, member);
        Role role = requireMuteRole(guild);
        if (!member.getRoles().contains(role)) {
            guild.addRoleToMember(UserSnowflake.fromId(target.value()), role)
                    .reason("Enthusia D07 mute reconciliation").complete();
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

    private void ensureMutePolicy(Guild guild) {
        Role role = requireMuteRole(guild);
        for (GuildChannel channel : guild.getChannels()) {
            if (!(channel instanceof IPermissionContainer container)) {
                continue;
            }
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
    }

    private DiscordPermissionSnapshot applyRestriction(Guild guild, DiscordPunishment punishment) {
        Member member = memberRequired(guild, punishment.targetUserId());
        requireHierarchy(guild, member);
        DiscordRestrictionTarget target = punishment.intent().restriction().orElseThrow();
        IPermissionContainer container = restrictionContainer(guild, target);
        PermissionOverride previous = container.getPermissionOverride(member);
        DiscordPermissionSnapshot snapshot = previous == null
                ? DiscordPermissionSnapshot.absent()
                : new DiscordPermissionSnapshot(true, previous.getAllowedRaw(), previous.getDeniedRaw());
        enforceRestriction(container, member, target.mode());
        return snapshot;
    }

    private void reconcileRestriction(Guild guild, DiscordPunishment punishment) {
        Member member = memberOrNull(guild, punishment.targetUserId());
        if (member == null) {
            return;
        }
        requireHierarchy(guild, member);
        DiscordRestrictionTarget target = punishment.intent().restriction().orElseThrow();
        enforceRestriction(restrictionContainer(guild, target), member, target.mode());
    }

    private void enforceRestriction(
            IPermissionContainer container,
            Member member,
            DiscordRestrictionTarget.Mode mode
    ) {
        long mask = restrictionMask(mode);
        PermissionOverride current = container.getPermissionOverride(member);
        long allowed = current == null ? 0L : current.getAllowedRaw();
        long denied = current == null ? 0L : current.getDeniedRaw();
        container.upsertPermissionOverride(member)
                .setAllowed(allowed & ~mask)
                .setDenied(denied | mask)
                .reason("Enthusia D07 member restriction")
                .complete();
    }

    private void removeRestriction(Guild guild, DiscordPunishment punishment) {
        Member member = memberOrNull(guild, punishment.targetUserId());
        if (member == null) {
            return;
        }
        DiscordRestrictionTarget target = punishment.intent().restriction().orElseThrow();
        IPermissionContainer container = restrictionContainer(guild, target);
        PermissionOverride current = container.getPermissionOverride(member);
        if (current == null) {
            return;
        }
        DiscordPermissionSnapshot snapshot = punishment.previousRestriction().orElseThrow();
        long mask = restrictionMask(target.mode());
        long allowed = (current.getAllowedRaw() & ~mask) | (snapshot.allowedRaw() & mask);
        long denied = (current.getDeniedRaw() & ~mask) | (snapshot.deniedRaw() & mask);
        if (!snapshot.existed() && allowed == 0L && denied == 0L) {
            current.delete().reason("Enthusia D07 restriction removal").complete();
        } else {
            container.upsertPermissionOverride(member)
                    .setAllowed(allowed)
                    .setDenied(denied)
                    .reason("Enthusia D07 restriction removal")
                    .complete();
        }
    }

    private void unban(Guild guild, DiscordUserId target) {
        try {
            guild.unban(UserSnowflake.fromId(target.value())).reason("Enthusia D07 ban removal").complete();
        } catch (ErrorResponseException exception) {
            if (!"UNKNOWN_BAN".equals(exception.getErrorResponse().name())) {
                throw exception;
            }
        }
    }

    private DiscordDeliveryOutcome notify(DiscordPunishment punishment) {
        if (!punishment.intent().notifyTarget()) {
            return DiscordDeliveryOutcome.NOT_ATTEMPTED;
        }
        try {
            User user = boundJda().retrieveUserById(punishment.targetUserId().value()).complete();
            user.openPrivateChannel().complete().sendMessage(dmMessage(punishment)).complete();
            return DiscordDeliveryOutcome.DELIVERED;
        } catch (RuntimeException failure) {
            return DiscordDeliveryOutcome.FAILED;
        }
    }

    private String dmMessage(DiscordPunishment punishment) {
        return "Enthusia moderation action: " + punishment.intent().type()
                + "\nDuration: " + duration(punishment)
                + "\nReason: " + punishment.intent().publicReason()
                + "\n" + configuration.supportMessage();
    }

    private static String duration(DiscordPunishment punishment) {
        return switch (punishment.intent().length().kind()) {
            case INSTANT -> "instant";
            case PERMANENT -> "permanent";
            case TEMPORARY -> punishment.intent().length().temporary().orElseThrow().toString();
        };
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
            throw failure("TARGET_NOT_IN_GUILD", false);
        }
        return member;
    }

    private Member memberOrNull(Guild guild, DiscordUserId target) {
        try {
            return guild.retrieveMemberById(target.value()).complete();
        } catch (ErrorResponseException exception) {
            if ("UNKNOWN_MEMBER".equals(exception.getErrorResponse().name())) {
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

    private static EffectException classify(
            String phase,
            RuntimeException failure,
            DiscordDeliveryOutcome delivery
    ) {
        if (failure instanceof EffectException effect) {
            return effect;
        }
        if (failure instanceof InsufficientPermissionException || failure instanceof HierarchyException) {
            return new EffectException(phase + "_PERMISSION_DENIED", false, delivery, failure);
        }
        if (failure instanceof ErrorResponseException response) {
            String code = response.getErrorResponse().name();
            boolean retryable = code.contains("SERVER") || code.contains("TEMPORAR");
            return new EffectException(phase + "_DISCORD_" + code, retryable, delivery, failure);
        }
        return new EffectException(phase + "_TRANSPORT_FAILURE", true, delivery, failure);
    }

    private static EffectException failure(String code, boolean retryable) {
        return new EffectException(code, retryable);
    }

    private static String auditReason(DiscordPunishment punishment) {
        String reason = "Enthusia D07 " + punishment.intent().type() + ": " + punishment.intent().publicReason();
        return reason.length() <= MAX_AUDIT_REASON ? reason : reason.substring(0, MAX_AUDIT_REASON);
    }
}
