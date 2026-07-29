package net.enthusia.staff.paper.inventory;

import java.time.Clock;

public final class InventoryOperationContext {
    private final Clock operationClock;
    private final String scopeIdentifier;
    private final String serverIdentifier;

    public InventoryOperationContext(Clock clock, String scopeId, String serverId) {
        this.operationClock = java.util.Objects.requireNonNull(clock, "clock");
        this.scopeIdentifier = requireIdentifier(scopeId, "scopeId");
        this.serverIdentifier = requireIdentifier(serverId, "serverId");
    }

    public Clock clock() {
        return operationClock;
    }

    public String scopeId() {
        return scopeIdentifier;
    }

    public String serverId() {
        return serverIdentifier;
    }

    private static String requireIdentifier(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException(field + " must contain 1-64 characters");
        }
        return value;
    }
}
