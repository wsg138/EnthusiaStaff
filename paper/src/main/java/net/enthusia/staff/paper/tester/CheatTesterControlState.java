package net.enthusia.staff.paper.tester;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.enthusia.staff.domain.tester.CheatTesterType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

final class CheatTesterControlState {
    private static final String PERMISSION = "enthusiastaff.cheattester";
    private static final String CANCEL_ANY_PERMISSION = "enthusiastaff.cheattester.cancel-any";
    private static final List<CheatTesterType> SELECTABLE = List.of(
            CheatTesterType.TOTEM_REFILL,
            CheatTesterType.NO_FALL,
            CheatTesterType.VELOCITY,
            CheatTesterType.AUTO_ARMOR,
            CheatTesterType.FAKE_ENTITY
    );

    private final Clock clock;
    private final Predicate<UUID> staffModeActive;
    private final CheatTesterSettings settings;
    private final Map<UUID, CheatTesterType> selections = new ConcurrentHashMap<>();
    private final Map<UUID, CheatTesterSession> activeByTarget;

    CheatTesterControlState(
            Clock clock,
            Predicate<UUID> staffModeActive,
            CheatTesterSettings settings,
            Map<UUID, CheatTesterSession> activeByTarget
    ) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.staffModeActive = java.util.Objects.requireNonNull(staffModeActive, "staffModeActive");
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
        this.activeByTarget = java.util.Objects.requireNonNull(activeByTarget, "activeByTarget");
    }

    CheatTesterType selected(UUID staffId) {
        return selections.getOrDefault(staffId, CheatTesterType.TOTEM_REFILL);
    }

    boolean select(Player staff, CheatTesterType type, boolean fakeAvailable) {
        if (!authorized(staff) || !providerAvailable(staff, type, fakeAvailable)) {
            return false;
        }
        selections.put(staff.getUniqueId(), type);
        staff.sendMessage(Component.text("Cheat Tester selected: " + type.displayName(), NamedTextColor.AQUA));
        return true;
    }

    void cycle(Player staff, boolean fakeAvailable) {
        if (!authorized(staff)) {
            return;
        }
        int nextIndex = (SELECTABLE.indexOf(selected(staff.getUniqueId())) + 1) % SELECTABLE.size();
        for (int attempts = 0; attempts < SELECTABLE.size(); attempts++) {
            CheatTesterType next = SELECTABLE.get((nextIndex + attempts) % SELECTABLE.size());
            if (next != CheatTesterType.FAKE_ENTITY || fakeAvailable) {
                select(staff, next, fakeAvailable);
                return;
            }
        }
    }

    boolean canStart(Player staff, Player target, CheatTesterType type, boolean fakeAvailable, boolean closed) {
        return validParticipants(staff, target, type, closed)
                && providerAvailable(staff, type, fakeAvailable)
                && capacityAvailable(staff);
    }

    boolean controllable(Player staff, CheatTesterSession session) {
        return session != null
                && (session.staffId.equals(staff.getUniqueId()) || staff.hasPermission(CANCEL_ANY_PERMISSION));
    }

    List<String> statusLines(UUID staffId, boolean includeAll) {
        List<String> lines = new ArrayList<>();
        for (CheatTesterSession session : activeByTarget.values()) {
            if (includeAll || session.staffId.equals(staffId)) {
                lines.add(session.type.id() + " target=" + session.targetId + " age="
                        + Math.max(0L, clock.instant().toEpochMilli() - session.startedAt.toEpochMilli()) + "ms");
            }
        }
        return List.copyOf(lines);
    }

    boolean authorized(Player staff) {
        if (staff == null || !staff.hasPermission(PERMISSION)) {
            if (staff != null) {
                staff.sendMessage(Component.text("You do not have permission to use Cheat Tester."));
            }
            return false;
        }
        if (!staffModeActive.test(staff.getUniqueId())) {
            staff.sendMessage(Component.text("Enter staff mode before using Cheat Tester."));
            return false;
        }
        return true;
    }

    void clearSelection(UUID playerId) {
        selections.remove(playerId);
    }

    void clear() {
        selections.clear();
    }

    private boolean validParticipants(Player staff, Player target, CheatTesterType type, boolean closed) {
        if (!authorized(staff) || target == null || type == null || closed) {
            return false;
        }
        if (staff.getUniqueId().equals(target.getUniqueId())) {
            staff.sendMessage(Component.text("Cheat Tester cannot target the controlling staff member."));
            return false;
        }
        if (!target.isOnline()) {
            staff.sendMessage(Component.text("The target must be online on this backend."));
            return false;
        }
        return true;
    }

    private static boolean providerAvailable(Player staff, CheatTesterType type, boolean fakeAvailable) {
        if (type != CheatTesterType.FAKE_ENTITY || fakeAvailable) {
            return true;
        }
        staff.sendMessage(Component.text(
                "Fake-entity testing is unavailable; ProtocolLib packet support failed closed.",
                NamedTextColor.RED
        ));
        return false;
    }

    private boolean capacityAvailable(Player staff) {
        if (activeByTarget.size() >= settings.maximumActiveGlobal()) {
            staff.sendMessage(Component.text("The global cheat-tester session limit is active."));
            return false;
        }
        long activeForStaff = activeByTarget.values().stream()
                .filter(session -> session.staffId.equals(staff.getUniqueId()))
                .count();
        if (activeForStaff >= settings.maximumActivePerStaff()) {
            staff.sendMessage(Component.text("You already control the maximum number of cheat-tester sessions."));
            return false;
        }
        return true;
    }
}
