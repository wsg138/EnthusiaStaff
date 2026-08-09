package net.enthusia.staff.domain.ports;

import net.enthusia.staff.domain.tester.FakeBaseAuditEvent;

@FunctionalInterface
public interface FakeBaseAuditStore {
    void record(FakeBaseAuditEvent event);
}
