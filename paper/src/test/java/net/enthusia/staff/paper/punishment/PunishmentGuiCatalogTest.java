package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.escalation.RemovedReason;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PunishmentGuiCatalogTest {
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String CHAT = "chat";
    private static final String SAFETY = "safety";
    private static final String CHAT_MOD = "chat.mod";
    private static final String SAFETY_ADMIN = "safety.admin";
    private static final String MUTE_COMMAND = "mute";
    private static final String BAN_COMMAND = "ban";

    @Test
    void developerCanReviewRequestableReasonsWithoutDirectIssueAuthority() {
        PunishmentGuiCatalog catalog = catalog();

        assertEquals(List.of(CHAT, SAFETY), catalog.categories(actor(StaffRank.DEVELOPER), "punish"));
        assertEquals(
                List.of(SAFETY_ADMIN),
                catalog.reasons(actor(StaffRank.DEVELOPER), BAN_COMMAND, SAFETY).stream()
                        .map(ReasonPolicy::id)
                        .toList()
        );
    }

    @Test
    void helperSeesModeratorReasonsButNotAdministrativeReasons() {
        PunishmentGuiCatalog catalog = catalog();

        assertEquals(
                List.of(CHAT_MOD),
                catalog.reasons(actor(StaffRank.HELPER), MUTE_COMMAND, CHAT).stream()
                        .map(ReasonPolicy::id)
                        .toList()
        );
        assertTrue(catalog.reasons(actor(StaffRank.HELPER), BAN_COMMAND, SAFETY).isEmpty());
    }

    @Test
    void modSeesOnlyAuthorizedReasonsAndCommandTypes() {
        PunishmentGuiCatalog catalog = catalog();

        assertEquals(List.of(CHAT), catalog.categories(actor(StaffRank.MOD), MUTE_COMMAND));
        assertEquals(
                List.of(CHAT_MOD),
                catalog.reasons(actor(StaffRank.MOD), MUTE_COMMAND, CHAT).stream()
                        .map(ReasonPolicy::id)
                        .toList()
        );
    }

    @Test
    void adminSeesAdminReasonsWithoutExposingUnrelatedCommandTypes() {
        PunishmentGuiCatalog catalog = catalog();

        assertEquals(
                List.of(SAFETY_ADMIN),
                catalog.reasons(actor(StaffRank.ADMIN), BAN_COMMAND, SAFETY).stream()
                        .map(ReasonPolicy::id)
                        .toList()
        );
        assertTrue(catalog.reasons(actor(StaffRank.ADMIN), MUTE_COMMAND, SAFETY).isEmpty());
    }

    @Test
    void renamedAndRemovedReasonsAreDescribedWithoutEnteringSelectionLists() {
        ReasonPolicy canonical = policy(CHAT_MOD, CHAT, StaffRank.MOD, SanctionType.MUTE);
        RemovedReason removed = new RemovedReason("chat.retired", CHAT, "Retired reason");
        PunishmentGuiCatalog catalog = new PunishmentGuiCatalog(
                new AtomicReasonPolicyRepository(
                        "v2",
                        List.of(canonical),
                        Map.of("chat.old", canonical.id()),
                        List.of(removed)
                ),
                new DefaultAuthorizationPolicy()
        );

        assertEquals(
                List.of(canonical.id()),
                catalog.reasons(actor(StaffRank.MOD), MUTE_COMMAND, CHAT).stream().map(ReasonPolicy::id).toList()
        );
        assertEquals(
                ReasonPolicyRepository.ReasonAvailability.ALIAS,
                catalog.describe("chat.old").orElseThrow().availability()
        );
        assertEquals(
                ReasonPolicyRepository.ReasonAvailability.REMOVED,
                catalog.describe(removed.id()).orElseThrow().availability()
        );
        assertFalse(catalog.describe(removed.id()).orElseThrow().selectable());
    }

    private static PunishmentGuiCatalog catalog() {
        return new PunishmentGuiCatalog(
                new AtomicReasonPolicyRepository("v1", List.of(
                        policy(CHAT_MOD, CHAT, StaffRank.MOD, SanctionType.MUTE),
                        policy(SAFETY_ADMIN, SAFETY, StaffRank.ADMIN, SanctionType.NETWORK_BAN),
                        policy("chat.warning", CHAT, StaffRank.MOD, SanctionType.WARNING)
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
