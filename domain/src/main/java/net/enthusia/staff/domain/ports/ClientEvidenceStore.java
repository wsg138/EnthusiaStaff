package net.enthusia.staff.domain.ports;

import java.util.UUID;
import net.enthusia.staff.domain.evidence.ClientEvidenceSnapshot;

public interface ClientEvidenceStore {
    UUID save(ClientEvidenceSnapshot snapshot);
}
