package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PunishmentGuiCatalogTest {
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void developerCanReviewRequestableReasonsWithoutDirectIssueAuthority() {
        PunishmentGuiCatalog catalog = catalog();

        assertEquals(List.of("chat", "safety"), catalog.categories(actor(StaffRank.DEVELOPER), "punish"));
        assertEquals(
                List.of("safety.admin"),
                catalog.reasons(actor(StaffRank.DEVELOPER), "ban", "safety").stream()
                        .map(ReasonPolicy::id)
                        .toList()
        );
    }

    @Test
    void helperSeesModeratorReasonsButNotAdministrativeReasons() {
        PunishmentGuiCatalog catalog = catalog();

        assertEquals(
                List.of("chat.mod"),
                catalog.reasons(actor(StaffRank.HELPER), "mute", "chat").stream()
                        .map(ReasonPolicy::id)
                        .toList()
        );
        assertTrue(catalog.reasons(actor(StaffRank.HELPER), "ban", "safety").isEmpty());
    }

    @Test
    void modSeesOnlyAuthorizedReasonsAndCommandTypes() {
        PunishmentGuiCatalog catalog = catalog();

        assertEquals(List.of("chat"), catalog.categories(actor(StaffRank.MOD), "mute"));
        assertEquals(
                List.of("chat.mod"),
                catalog.reasons(actor(StaffRank.MOD), "mute", "chat").stream()
                        .map(ReasonPolicy::id)
                        .toList()
        );
    }

    @Test
    void adminSeesAdminReasonsWithoutExposingUnrelatedCommandTypes() {
        PunishmentGuiCatalog catalog = catalog();

        assertEquals(
                List.of("safety.admin"),
                catalog.reasons(actor(StaffRank.ADMIN), "ban", "safety").stream()
                        .map(ReasonPolicy::id)
                        .toList()
        );
        assertTrue(catalog.reasons(actor(StaffRank.ADMIN), "mute", "safety").isEmpty());
    }

    private static PunishmentGuiCatalog catalog() {
        return new PunishmentGuiCatalog(
                new AtomicReasonPolicyRepository("v1", List.of(
                        policy("chat.mod", "chat", StaffRank.MOD, SanctionType.MUTE),
                        policy("safety.admin", "safety", StaffRank.ADMIN, SanctionType.NETWORK_BAN),
                        policy("chat.warning", "chat", StaffRank.MOD, SanctionType.WARNING)
                )),
                new DefaultAuthorizationPolicy()
        );
    }

    private static Actor actor(StaffRank rank) {
        return new Actor(ACTOR_ID, rank.name(), rank);
    }

    private static ReasonPolicy policy(
            String id,
            String family,
            StaffRank requiredRank,
            SanctionType sanctionType
    ) {
        SanctionLength length = sanctionType == SanctionType.WARNING
                ? SanctionLength.instant()
                : SanctionLength.temporary(Duration.ofDays(1));
        return new ReasonPolicy(
                id,
                family,
                id,
                10,
                true,
                List.of(new PunishmentStep(0, "Step", List.of(new SanctionSpec(sanctionType, length)))),
                List.of(),
                true,
                true,
                false,
                requiredRank,
                false,
                AltInheritanceMode.ACTIVE_SANCTIONS
        );
    }
}
