package net.enthusia.staff.common;

import java.util.UUID;

public record OperationId(UUID value) {
    public OperationId {
        if (value == null) {
            throw new IllegalArgumentException("operationId must not be null");
        }
    }

    public static OperationId random() {
        return new OperationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
