package net.enthusia.staff.domain.policyv2;

public record RemedySpec(String id, Type type, String description) {
    public enum Type { REMOVE_CONTENT, CONFISCATE, ACCESS_RESTRICTION, CORRECT_PROFILE, OTHER }

    public RemedySpec {
        id = PolicyIds.require(id, "remedy id");
        if (type == null || description == null || description.isBlank()) {
            throw new IllegalArgumentException("remedy type and description must be present");
        }
        description = description.trim();
    }
}
