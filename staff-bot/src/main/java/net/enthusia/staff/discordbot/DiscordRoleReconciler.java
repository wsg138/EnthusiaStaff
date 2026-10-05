package net.enthusia.staff.discordbot;

import java.util.Set;

/** Discord-side presentation adapter for one evaluated role-sync subject. */
interface DiscordRoleReconciler {
    Result reconcile(DiscordRoleSyncService.Evaluation evaluation);

    default void cancel() {
        // Adapters without asynchronous work need no cancellation hook.
    }

    record Result(Set<String> observedRoleIds, String state) {
        public Result {
            if (observedRoleIds == null || state == null || state.isBlank()) {
                throw new IllegalArgumentException("role reconciliation result is invalid");
            }
            observedRoleIds = Set.copyOf(observedRoleIds);
        }
    }

    final class RetryableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String errorCode;
        private final transient Set<String> observedRoleIds;

        RetryableException(String errorCode, Set<String> observedRoleIds, Throwable cause) {
            super(errorCode, cause);
            this.errorCode = errorCode;
            this.observedRoleIds = Set.copyOf(observedRoleIds);
        }

        String errorCode() {
            return errorCode;
        }

        Set<String> observedRoleIds() {
            return observedRoleIds;
        }
    }
}
