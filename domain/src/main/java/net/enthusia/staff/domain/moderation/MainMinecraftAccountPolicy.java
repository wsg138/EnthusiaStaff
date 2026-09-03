package net.enthusia.staff.domain.moderation;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MainMinecraftAccountPolicy {
    private static final BigInteger SWITCH_NUMERATOR = BigInteger.valueOf(5);
    private static final BigInteger SWITCH_DENOMINATOR = BigInteger.valueOf(4);

    private static final Comparator<LinkedMinecraftAccount> FIRST_LINKED = Comparator
            .comparing(LinkedMinecraftAccount::linkedAt)
            .thenComparing(account -> account.playerId().toString());

    private static final Comparator<LinkedMinecraftAccount> MOST_ACTIVE = Comparator
            .comparingLong(LinkedMinecraftAccount::activeMinutes)
            .reversed()
            .thenComparing(LinkedMinecraftAccount::linkedAt)
            .thenComparing(account -> account.playerId().toString());

    private MainMinecraftAccountPolicy() {
    }

    public static MainMinecraftAccount select(
            List<LinkedMinecraftAccount> linkedAccounts,
            Optional<UUID> currentAutomaticMain,
            Optional<UUID> staffOverride
    ) {
        if (linkedAccounts == null || currentAutomaticMain == null || staffOverride == null) {
            throw new IllegalArgumentException("main-account selection inputs must be present");
        }
        if (linkedAccounts.isEmpty()) {
            throw new IllegalArgumentException("at least one linked Minecraft account is required");
        }

        Map<UUID, LinkedMinecraftAccount> byId = new HashMap<>();
        for (LinkedMinecraftAccount account : linkedAccounts) {
            if (account == null || byId.putIfAbsent(account.playerId(), account) != null) {
                throw new IllegalArgumentException("linked Minecraft accounts must be non-null and unique");
            }
        }

        if (staffOverride.isPresent()) {
            UUID override = staffOverride.orElseThrow();
            if (!byId.containsKey(override)) {
                throw new IllegalArgumentException("staff main-account override must reference a current link");
            }
            return new MainMinecraftAccount(override, MainAccountSelectionSource.STAFF_OVERRIDE);
        }

        if (currentAutomaticMain.isEmpty()) {
            LinkedMinecraftAccount first = linkedAccounts.stream().min(FIRST_LINKED).orElseThrow();
            return new MainMinecraftAccount(first.playerId(), MainAccountSelectionSource.AUTOMATIC);
        }

        LinkedMinecraftAccount current = byId.get(currentAutomaticMain.orElseThrow());
        if (current == null) {
            LinkedMinecraftAccount replacement = linkedAccounts.stream().min(MOST_ACTIVE).orElseThrow();
            return new MainMinecraftAccount(replacement.playerId(), MainAccountSelectionSource.AUTOMATIC);
        }

        LinkedMinecraftAccount candidate = linkedAccounts.stream().min(MOST_ACTIVE).orElseThrow();
        if (candidate.playerId().equals(current.playerId()) || !hasSwitchAdvantage(candidate, current)) {
            return new MainMinecraftAccount(current.playerId(), MainAccountSelectionSource.AUTOMATIC);
        }
        return new MainMinecraftAccount(candidate.playerId(), MainAccountSelectionSource.AUTOMATIC);
    }

    private static boolean hasSwitchAdvantage(
            LinkedMinecraftAccount candidate,
            LinkedMinecraftAccount current
    ) {
        if (current.activeMinutes() == 0) {
            return candidate.activeMinutes() > 0;
        }
        BigInteger candidateScaled = BigInteger.valueOf(candidate.activeMinutes()).multiply(SWITCH_DENOMINATOR);
        BigInteger currentScaled = BigInteger.valueOf(current.activeMinutes()).multiply(SWITCH_NUMERATOR);
        return candidateScaled.compareTo(currentScaled) >= 0;
    }
}
