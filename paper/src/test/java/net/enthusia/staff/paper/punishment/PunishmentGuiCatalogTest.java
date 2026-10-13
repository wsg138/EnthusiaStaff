package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;
import org.junit.jupiter.api.Test;
import java.util.stream.Collectors;

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

        assertEquals(List.of(CHAT, SAFETY), categoryIds(catalog, StaffRank.DEVELOPER, "punish"));
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

        assertEquals(List.of(CHAT), categoryIds(catalog, StaffRank.MOD, MUTE_COMMAND));
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
        assertFalse(catalog.describe(removed.id()).orElseThrow().resolvesToActivePolicy());
    }

    @Test
    void categoriesHaveUniqueIconsAndPolarTemplateStaysOutOfManualMenu() {
        assertEquals(PunishmentGuiCategory.values().length,
                Arrays.stream(PunishmentGuiCategory.values()).map(PunishmentGuiCategory::material)
                        .collect(Collectors.toSet()).size());
        PunishmentGuiCatalog catalog = new PunishmentGuiCatalog(
                new AtomicReasonPolicyRepository("v1", List.of(
                        policy("cheating.manual", "cheating", StaffRank.MOD, SanctionType.BAN),
                        policy("cheating.polar.template", "cheating.polar.template", StaffRank.MOD, SanctionType.BAN)
                )), new DefaultAuthorizationPolicy());

        assertEquals(List.of("gameplay"), categoryIds(catalog, StaffRank.MOD, "punish"));
        assertEquals(List.of("cheating.manual"), catalog.reasons(actor(StaffRank.MOD), "punish", "gameplay")
                .stream().map(ReasonPolicy::id).toList());
        assertTrue(PunishmentGuiCategory.GAMEPLAY.families().contains("cheating"));
    }

    @Test
    void categoryGridKeepsEveryIconClickableAndAwayFromControls() {
        for (int total = 1; total <= PunishmentGuiCategory.values().length; total++) {
            java.util.Set<Integer> slots = new java.util.HashSet<>();
            for (int index = 0; index < total; index++) {
                int slot = PunishmentGuiRenderer.categorySlot(index, total);
                assertTrue(slot >= PunishmentGuiRenderer.CONTENT_START && slot < 45);
                assertTrue(slots.add(slot));
                assertEquals(index, PunishmentGuiRenderer.categoryIndex(slot, total));
            }
            assertEquals(-1, PunishmentGuiRenderer.categoryIndex(PunishmentGuiRenderer.CLOSE_SLOT, total));
        }
    }

    @Test
    void defaultReasonsHaveOneClearCategoryAndNoPolarMenuEntry() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("reason-policies.yml")) {
            ReasonPolicyConfigurationLoader.LoadedPolicies loaded = new ReasonPolicyConfigurationLoader()
                    .load(java.util.Objects.requireNonNull(input), "reason-policies.yml");
            PunishmentGuiCatalog catalog = new PunishmentGuiCatalog(
                    new AtomicReasonPolicyRepository(loaded.version(), loaded.policies(),
                            loaded.aliases(), loaded.removedReasons()),
                    new DefaultAuthorizationPolicy());
            List<PunishmentGuiCategory> categories = catalog.categories(actor(StaffRank.FOUNDER), "punish");
            assertEquals(6, categories.size());
            assertFalse(categories.contains(PunishmentGuiCategory.OTHER));
            List<String> visibleIds = categories.stream()
                    .flatMap(category -> catalog.reasons(actor(StaffRank.FOUNDER), "punish", category.id()).stream())
                    .map(ReasonPolicy::id).toList();
            assertEquals(loaded.policies().size() - 1, visibleIds.size());
            assertEquals(visibleIds.size(), Set.copyOf(visibleIds).size());
            assertFalse(visibleIds.contains("cheating.polar.template"));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void bundledPolicyEveryManualReasonIsSearchableByExactId() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("reason-policies.yml")) {
            var loaded = new ReasonPolicyConfigurationLoader().load(
                    java.util.Objects.requireNonNull(input), "reason-policies.yml");
            var catalog = new PunishmentGuiCatalog(
                    new AtomicReasonPolicyRepository(loaded.version(), loaded.policies(),
                            loaded.aliases(), loaded.removedReasons()),
                    new DefaultAuthorizationPolicy());
            var founder = actor(StaffRank.FOUNDER);
            for (ReasonPolicy policy : loaded.policies()) {
                if (policy.id().startsWith("cheating.polar.") || policy.family().startsWith("cheating.polar")) {
                    assertTrue(catalog.search(founder, "punish", policy.id()).isEmpty());
                } else {
                    var results = catalog.search(founder, "punish", policy.id());
                    assertFalse(results.isEmpty(), "Not searchable: " + policy.id());
                    assertEquals(policy.id(), results.getFirst().id());
                }
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void unknownNewFamilyFallsBackToOtherWithoutChangingPolicyFamily() {
        ReasonPolicy extra = policy("new.unknown-offense", "new-family", StaffRank.MOD, SanctionType.MUTE);
        PunishmentGuiCatalog catalog = new PunishmentGuiCatalog(
                new AtomicReasonPolicyRepository("vFuture", List.of(extra)),
                new DefaultAuthorizationPolicy());
        assertEquals(List.of("other"), categoryIds(catalog, StaffRank.MOD, "punish"));
        assertEquals(extra.id(), catalog.reasons(actor(StaffRank.MOD), "punish", "other").getFirst().id());
        assertEquals("new-family", extra.family());
    }

    @Test
    void allSixGroupsHaveNoOverlappingFamilyKeys() {
        Set<String> families = new java.util.HashSet<>();
        for (PunishmentGuiCategory category : PunishmentGuiCategory.ordered()) {
            for (String family : category.families()) {
                assertTrue(families.add(family), "Mapped to two categories: " + family);
                assertEquals(category, PunishmentGuiCategory.forFamily(family));
            }
        }
        assertEquals(6, PunishmentGuiCategory.values().length - 1);
    }

    @Test
    void searchRespectsRankCommandFiltersAndHidesSystemOnlyTemplates() {
        PunishmentGuiCatalog catalog = new PunishmentGuiCatalog(
                new AtomicReasonPolicyRepository("v1", List.of(
                        policy("cheating.manual", "cheating", StaffRank.MOD, SanctionType.NETWORK_BAN),
                        policy("cheating.polar.template", "cheating.polar.template",
                                StaffRank.MOD, SanctionType.NETWORK_BAN),
                        policy("safety.admin", "safety", StaffRank.ADMIN, SanctionType.NETWORK_BAN),
                        policy("spam.low-level", "spam", StaffRank.MOD, SanctionType.MUTE)
                )), new DefaultAuthorizationPolicy());

        assertEquals(List.of("cheating.manual"),
                catalog.search(actor(StaffRank.MOD), "ban", "cheating").stream()
                        .map(ReasonPolicy::id).toList());
        assertTrue(catalog.search(actor(StaffRank.MOD), "mute", "cheating").isEmpty());
        assertTrue(catalog.search(actor(StaffRank.HELPER), "ban", "admin").isEmpty());
        assertEquals(List.of("safety.admin"),
                catalog.search(actor(StaffRank.DEVELOPER), "ban", "admin").stream()
                        .map(ReasonPolicy::id).toList());
    }

    @Test
    void searchIsCaseInsensitiveAndRejectsUnboundedQueries() {
        PunishmentGuiCatalog catalog = catalog();
        assertEquals(List.of("chat.mod"),
                catalog.search(actor(StaffRank.MOD), "mute", "  CHAT.MOD  ").stream()
                        .map(ReasonPolicy::id).toList());
        assertTrue(catalog.search(actor(StaffRank.MOD), "mute", "a").isEmpty());
        assertTrue(catalog.search(actor(StaffRank.MOD), "mute", "a".repeat(65)).isEmpty());
        assertTrue(catalog.search(actor(StaffRank.MOD), "mute", null).isEmpty());
    }

    private static List<String> categoryIds(PunishmentGuiCatalog catalog, StaffRank rank, String command) {
        return catalog.categories(actor(rank), command).stream().map(PunishmentGuiCategory::id).toList();
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
