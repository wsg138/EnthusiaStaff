package net.enthusia.staff.paper.staff;

/** Result of decoding and validating a potentially tagged staff-mode hotbar item. */
record StaffToolResolution(
        boolean tagged,
        StaffToolDefinition tool,
        StaffToolSessionPolicy.Status status
) {
    StaffToolResolution {
        if (!tagged && (tool != null || status != null)) {
            throw new IllegalArgumentException("untagged resolution cannot carry tool state");
        }
        if (tagged && status == null) {
            throw new IllegalArgumentException("tagged resolution requires a validation status");
        }
    }

    static StaffToolResolution untagged() {
        return new StaffToolResolution(false, null, null);
    }

    static StaffToolResolution tagged(
            StaffToolDefinition tool,
            StaffToolSessionPolicy.Status status
    ) {
        return new StaffToolResolution(true, tool, status);
    }

    boolean valid() {
        return tagged && status == StaffToolSessionPolicy.Status.VALID;
    }
}
