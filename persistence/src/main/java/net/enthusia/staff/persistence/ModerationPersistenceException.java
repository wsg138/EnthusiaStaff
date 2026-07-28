package net.enthusia.staff.persistence;

public final class ModerationPersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ModerationPersistenceException(String message) {
        super(message);
    }

    public ModerationPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
