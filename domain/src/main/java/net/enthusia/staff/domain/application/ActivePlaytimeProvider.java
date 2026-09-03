package net.enthusia.staff.domain.application;

import java.util.OptionalLong;
import java.util.UUID;

@FunctionalInterface
public interface ActivePlaytimeProvider {
    OptionalLong lifetimeActiveMinutes(UUID minecraftPlayerId);
}
