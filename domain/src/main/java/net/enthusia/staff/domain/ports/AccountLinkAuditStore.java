package net.enthusia.staff.domain.ports;

import java.util.Optional;
import net.enthusia.staff.domain.moderation.AccountLinkAudit;

/** Private, idempotent staff audit trail for identity recovery and main-account overrides. */
public interface AccountLinkAuditStore {
    boolean append(AccountLinkAudit audit);

    Optional<AccountLinkAudit> findByOperationKey(String operationKey);
}
