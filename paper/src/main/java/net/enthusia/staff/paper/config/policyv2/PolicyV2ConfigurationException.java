package net.enthusia.staff.paper.config.policyv2;

public final class PolicyV2ConfigurationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public PolicyV2ConfigurationException(String message) {
        super(message);
    }

    public PolicyV2ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
