package net.enthusia.staff.common;

import java.util.Collection;

public final class Checks {
    private Checks() {
    }

    public static String nonBlank(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    public static <T, C extends Collection<T>> C nonEmpty(C value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return value;
    }
}
