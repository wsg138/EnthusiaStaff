package net.enthusia.staff.paper.modcase;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.modcase.AuthorityBackedCaseActions;
import net.enthusia.staff.domain.modcase.CaseNotice;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;

/**
 * Unregistered runtime policy adapter. Reuses the LuckPerms, verified-link and
 * StaffMode/session sources used by existing private staff review.
 * Requires an authoritative fresh case probe and independently signed Discord ingress.
 */
public final class PaperCaseCurrentStaffPolicy implements PaperCaseStaffAuthority.CurrentStaffPolicy {
    private static final long LOOKUP_TIMEOUT_SECONDS = 2;
    private final LuckPerms luckPerms;
    private final StaffModeManager staffMode;
    private final Supplier<StaffSessionStore> sessions;
    private final Predicate<UUID> appliedStaffMode;
    private final Function<String, Optional<UUID>> uniquelyLinked;
    private final BiPredicate<UUID, String> currentVerifiedLink;
    private final Supplier<Boolean> offlineReviewEnabled;
    private final Predicate<AuthorityBackedCaseActions.Command> currentCanonicalCase;

    public PaperCaseCurrentStaffPolicy(
            LuckPerms luckPerms,
            StaffModeManager staffMode,
            Supplier<StaffSessionStore> sessions,
            Predicate<UUID> locallyAppliedStaffMode,
            Function<String, Optional<UUID>> uniquelyLinked,
            BiPredicate<UUID, String> currentVerifiedLink,
            Supplier<Boolean> offlineReviewEnabled,
            Predicate<AuthorityBackedCaseActions.Command> currentCanonicalCase
    ) {
        this.luckPerms = Objects.requireNonNull(luckPerms);
        this.staffMode = Objects.requireNonNull(staffMode);
        this.sessions = Objects.requireNonNull(sessions);
        this.appliedStaffMode = Objects.requireNonNull(locallyAppliedStaffMode);
        this.uniquelyLinked = Objects.requireNonNull(uniquelyLinked);
        this.currentVerifiedLink = Objects.requireNonNull(currentVerifiedLink);
        this.offlineReviewEnabled = Objects.requireNonNull(offlineReviewEnabled);
        this.currentCanonicalCase = Objects.requireNonNull(currentCanonicalCase);
    }

    /** Invoke only in the player's authorized Paper scheduler. */
    public static PaperCaseStaffAuthority.LiveMinecraftPlayerVerifier onlinePaperPlayer() {
        return player -> {
            if (player == null || !player.isOnline()) {
                return Optional.empty();
            }
            UUID uuid = player.getUniqueId();
            return Bukkit.getPlayer(uuid) == player ? Optional.of(uuid) : Optional.empty();
        };
    }

    @Override
    public Optional<UUID> uniquelyLinkedMinecraft(String verifiedDiscordUserId) {
        return Objects.requireNonNull(uniquelyLinked.apply(verifiedDiscordUserId));
    }

    @Override
    public boolean currentVerifiedLink(UUID minecraftUuid, String verifiedDiscordUserId) {
        return currentVerifiedLink.test(minecraftUuid, verifiedDiscordUserId);
    }

    @Override
    public Optional<StaffRank> currentRank(UUID minecraftUuid) {
        User user = loadUser(minecraftUuid);
        var permissions = user.getCachedData().getPermissionData();
        return PaperStaffRankResolver.resolve(
                permission -> permissions.checkPermission(permission).asBoolean());
    }

    @Override
    public boolean currentActionPermission(UUID minecraftUuid, CaseNotice.Action action) {
        String permission = switch (action) {
            case CLAIM -> "enthusiastaff.modcase.claim";
            case RESOLVE -> "enthusiastaff.modcase.resolve";
        };
        return hasPermission(minecraftUuid, permission);
    }

    @Override
    public boolean locallyAppliedStaffMode(UUID minecraftUuid) {
        StaffSessionStore store = sessions.get();
        return store != null && staffMode.authorityActive(minecraftUuid)
                && !staffMode.transitioning(minecraftUuid)
                && appliedStaffMode.test(minecraftUuid)
                && store.active(minecraftUuid)
                    .filter(session -> session.state() == StaffSessionState.ACTIVE).isPresent();
    }

    @Override
    public boolean explicitlyPermittedOfflineDiscord(UUID minecraftUuid) {
        return Boolean.TRUE.equals(offlineReviewEnabled.get())
                && hasPermission(minecraftUuid, "enthusiastaff.modcase.discord-offline");
    }

    @Override
    public boolean currentCaseAllows(AuthorityBackedCaseActions.Command command) {
        return currentCanonicalCase.test(command);
    }

    private boolean hasPermission(UUID playerId, String permission) {
        User user = loadUser(playerId);
        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    private User loadUser(UUID playerId) {
        try {
            return luckPerms.getUserManager().loadUser(playerId)
                    .get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("staff lookup interrupted", exception);
        } catch (java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("current staff authority unavailable", exception);
        }
    }
}
