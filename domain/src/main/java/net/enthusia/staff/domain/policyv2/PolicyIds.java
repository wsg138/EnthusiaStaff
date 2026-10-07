package net.enthusia.staff.domain.policyv2;

import java.util.regex.Pattern;

final class PolicyIds {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");

    private PolicyIds() {
    }

    static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (!ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a stable lowercase identifier");
        }
        return normalized;
    }
}
