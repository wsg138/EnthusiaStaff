package net.enthusia.staff.domain.sanction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

final class SanctionChangeTypeScopeTest {
    private static SanctionChangeRequest request(
            SanctionChangeAction action, Optional<Set<SanctionType>> scope
    ) {
        return new SanctionChangeRequest(
                new IdempotencyKey("test-alias-type-scope"),
                new CaseId("01HZX3K8M2N4P123"),
                new Actor(UUID.randomUUID(), "Admin", StaffRank.ADMIN),
                action,
                Optional.empty(),
                "Remove only selected sanction types",
                Optional.empty(),
                scope
        );
    }

    @Test
    void aliasesCanConstrainEndEarlyAndRevokeToTheirSanctionFamilies() {
        assertEquals(Set.of(SanctionType.BAN),
                request(SanctionChangeAction.END_EARLY,
                        Optional.of(Set.of(SanctionType.BAN))).typeScope().orElseThrow());
        assertEquals(Set.of(SanctionType.WARNING),
                request(SanctionChangeAction.REVOKE,
                        Optional.of(Set.of(SanctionType.WARNING))).typeScope().orElseThrow());
    }

    @Test
    void scopedRequestCopiesCallerOwnedMutableSets() {
        Set<SanctionType> input = new HashSet<>(Set.of(SanctionType.BAN));
        SanctionChangeRequest request =
                request(SanctionChangeAction.END_EARLY, Optional.of(input));
        input.add(SanctionType.MUTE);
        assertEquals(Set.of(SanctionType.BAN), request.typeScope().orElseThrow());
        assertThrows(UnsupportedOperationException.class,
                () -> request.typeScope().orElseThrow().add(SanctionType.MUTE));
    }

    @Test
    void scopedRequestRejectsEmptyScopesAndUnrelatedCaseWideActions() {
        assertThrows(IllegalArgumentException.class,
                () -> request(SanctionChangeAction.END_EARLY, Optional.of(Set.of())));
        assertThrows(IllegalArgumentException.class,
                () -> request(SanctionChangeAction.FULL_OVERTURN,
                        Optional.of(Set.of(SanctionType.BAN))));
    }
}
