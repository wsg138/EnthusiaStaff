package net.enthusia.staff.paper.modcase;

import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.modcase.AuthorityBackedCaseActions;
import net.enthusia.staff.domain.modcase.CaseNotice;
import org.bukkit.entity.Player;

/**
 * INACTIVE, UNREGISTERED identity and authorization boundary for v1 case commands.
 *
 * The Discord verifier must independently authenticate the ORIGINAL interaction
 * request (e.g. Discord's signed HTTP interaction, with verified public key, timestamp
 * and unmodified body), NOT a StaffBot service bearer token or a user ID forwarded
 * in JSON. JDA gateway click events do not by themselves provide such proof to Paper.
 * Until this verifier and current-case probe are real, no Discord endpoint may call this.
 */
public final class PaperCaseStaffAuthority {
    private static final long MAX_INTERACTION_AGE_SECONDS = 30;
    private static final long MAX_FUTURE_SKEW_SECONDS = 5;

    /** A verifier must bind the entire button operation to the actual Discord principal. */
    public record VerifiedDiscordInteraction(
            String discordUserId, String guildId, long authenticatedAt,
            CaseNotice.Origin origin, String scope, UUID caseId,
            long revision, CaseNotice.Action action, String commandId
    ) {
        boolean matches(AuthorityBackedCaseActions.Command command) {
            return command != null && origin == command.origin()
                    && Objects.equals(scope, command.scope())
                    && Objects.equals(caseId, command.caseId())
                    && revision == command.expectedRevision()
                    && action == command.action()
                    && Objects.equals(commandId, command.commandId());
        }
    }

    /** Returns an identity only after independent cryptographic verification. */
    @FunctionalInterface
    public interface SignedDiscordInteractionVerifier {
        Optional<VerifiedDiscordInteraction> verify(byte[] originalSignedInteraction);
    }

    /**
     * Must accept only a currently connected Paper-authenticated Player object,
     * never a UUID or Minecraft name taken from an HTTP body.
     */
    @FunctionalInterface
    public interface LiveMinecraftPlayerVerifier {
        Optional<UUID> verify(Player authenticatedPlayer);
    }

    /** Current Paper / LuckPerms policy. All lookups must be live and fail on errors. */
    public interface CurrentStaffPolicy {
        Optional<UUID> uniquelyLinkedMinecraft(String verifiedDiscordUserId);
        boolean currentVerifiedLink(UUID minecraftUuid, String verifiedDiscordUserId);
        Optional<StaffRank> currentRank(UUID minecraftUuid);
        boolean currentActionPermission(UUID minecraftUuid, CaseNotice.Action action);
        boolean locallyAppliedStaffMode(UUID minecraftUuid);
        boolean explicitlyPermittedOfflineDiscord(UUID minecraftUuid);
        /** Must check the authoritative case version, source, scope, state and expiry. */
        boolean currentCaseAllows(AuthorityBackedCaseActions.Command command);
    }

    /** Non-forgeable to calling modules: private construction after all checks. */
    static final class Approval {
        private final AuthorityBackedCaseActions.Command command;
        private final UUID staffUuid;

        private Approval(AuthorityBackedCaseActions.Command command, UUID staffUuid) {
            this.command = command;
            this.staffUuid = staffUuid;
        }

        boolean matches(AuthorityBackedCaseActions.Command proposed) {
            return command.equals(proposed);
        }

        UUID staffUuid() {
            return staffUuid;
        }
    }

    private final PaperCaseActorProofIssuer issuer;
    private final SignedDiscordInteractionVerifier discordVerifier;
    private final LiveMinecraftPlayerVerifier minecraftVerifier;
    private final CurrentStaffPolicy policy;
    private final Clock clock;
    private final String expectedScope;
    private final String expectedGuild;

    public PaperCaseStaffAuthority(
            PaperCaseActorProofIssuer issuer,
            SignedDiscordInteractionVerifier discordVerifier,
            LiveMinecraftPlayerVerifier minecraftVerifier,
            CurrentStaffPolicy policy,
            Clock clock,
            String expectedScope,
            String expectedGuild
    ) {
        this.issuer = Objects.requireNonNull(issuer, "signer");
        this.discordVerifier = Objects.requireNonNull(discordVerifier, "signed interaction verifier");
        this.minecraftVerifier = Objects.requireNonNull(minecraftVerifier, "live player verifier");
        this.policy = Objects.requireNonNull(policy, "live policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (expectedScope == null || expectedScope.isBlank()
                || expectedGuild == null || !expectedGuild.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException("explicit server scope and Discord guild required");
        }
        this.expectedScope = expectedScope;
        this.expectedGuild = expectedGuild;
    }

    public PaperCaseActorProofIssuer.Proof issueFromDiscord(
            byte[] independentlySignedInteraction, AuthorityBackedCaseActions.Command command
    ) {
        VerifiedDiscordInteraction interaction = verifiedDiscordInteraction(independentlySignedInteraction);
        if (!validDiscordBinding(interaction, command)) {
            throw denied();
        }
        UUID linkedId = safelyLinked(interaction.discordUserId());
        if (linkedId == null || !safeLinkCheck(linkedId, interaction.discordUserId())) {
            throw denied();
        }
        return authorizeAndSign(linkedId, interaction.discordUserId(), command);
    }

    private VerifiedDiscordInteraction verifiedDiscordInteraction(byte[] signedInteraction) {
        if (signedInteraction == null || signedInteraction.length == 0
                || signedInteraction.length > 65536) {
            throw denied();
        }
        try {
            return discordVerifier.verify(
                    Arrays.copyOf(signedInteraction, signedInteraction.length)).orElseThrow();
        } catch (RuntimeException exception) {
            throw denied();
        }
    }

    private boolean validDiscordBinding(
            VerifiedDiscordInteraction interaction, AuthorityBackedCaseActions.Command command) {
        long now = clock.instant().getEpochSecond();
        return interaction != null && interaction.discordUserId() != null
                && interaction.discordUserId().matches("[0-9]{1,20}")
                && expectedGuild.equals(interaction.guildId())
                && interaction.matches(command)
                && currentInteractionTime(interaction.authenticatedAt(), now);
    }

    private static boolean currentInteractionTime(long authentication, long now) {
        return authentication >= now - MAX_INTERACTION_AGE_SECONDS
                && authentication <= now + MAX_FUTURE_SKEW_SECONDS;
    }

    public PaperCaseActorProofIssuer.Proof issueFromMinecraft(
            Player authenticatedPlayer, AuthorityBackedCaseActions.Command command
    ) {
        if (authenticatedPlayer == null) {
            throw denied();
        }
        UUID authenticatedId;
        try {
            authenticatedId = minecraftVerifier.verify(authenticatedPlayer).orElseThrow();
        } catch (RuntimeException exception) {
            throw denied();
        }
        return authorizeAndSign(authenticatedId, null, command);
    }

    private UUID safelyLinked(String discordUserId) {
        try {
            return policy.uniquelyLinkedMinecraft(discordUserId).orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean safeLinkCheck(UUID id, String discordId) {
        try {
            return policy.currentVerifiedLink(id, discordId);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private PaperCaseActorProofIssuer.Proof authorizeAndSign(
            UUID authenticatedId, String discordId, AuthorityBackedCaseActions.Command command
    ) {
        if (!matchesAuthenticatedActor(authenticatedId, command)) {
            throw denied();
        }
        try {
            requireCurrentAuthority(authenticatedId, discordId, command);
            if (discordId != null && (!authenticatedId.equals(safelyLinked(discordId))
                    || !safeLinkCheck(authenticatedId, discordId))) {
                throw denied();
            }
            return issuer.issue(command, new Approval(command, authenticatedId));
        } catch (RuntimeException exception) {
            throw denied();
        }
    }

    private boolean matchesAuthenticatedActor(
            UUID authenticatedId, AuthorityBackedCaseActions.Command command) {
        return authenticatedId != null && command != null
                && expectedScope.equals(command.scope())
                && ("minecraft:" + authenticatedId).equals(command.actor())
                && (command.action() == CaseNotice.Action.CLAIM
                    || command.action() == CaseNotice.Action.RESOLVE);
    }

    private void requireCurrentAuthority(
            UUID authenticatedId, String discordId, AuthorityBackedCaseActions.Command command) {
        StaffRank rank = policy.currentRank(authenticatedId).orElseThrow();
        if (!allowedRank(rank) || !policy.currentCaseAllows(command)
                || !policy.currentActionPermission(authenticatedId, command.action())
                || !currentDutyOrApprovedOffline(authenticatedId, discordId)) {
            throw denied();
        }
    }

    private static boolean allowedRank(StaffRank rank) {
        return rank == StaffRank.MOD || rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER;
    }

    private boolean currentDutyOrApprovedOffline(UUID playerId, String discordId) {
        return policy.locallyAppliedStaffMode(playerId)
                || discordId != null && policy.explicitlyPermittedOfflineDiscord(playerId);
    }

    private static SecurityException denied() {
        return new SecurityException("independent current staff authority required");
    }
}
