package net.enthusia.staff.paper;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.application.PunishmentDraftWorkflow;
import net.enthusia.staff.domain.application.PunishmentRequestService;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.escalation.EscalationEngine;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.CaseReviewStore;
import net.enthusia.staff.domain.ports.ClientEvidenceStore;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.ports.InventoryRecoveryStore;
import net.enthusia.staff.domain.ports.ModerationHistoryStore;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PunishmentDraftStore;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.ports.VanishStore;
import net.enthusia.staff.persistence.MariaDbRuntime;

record PaperStorageBindings(
        MariaDbRuntime runtime,
        ModerationStores moderation,
        AssetStores assets,
        ApplicationServices services
) {
    static PaperStorageBindings create(
            MariaDbRuntime runtime,
            AuthorizationPolicy authorization,
            AtomicReasonPolicyRepository reasonPolicies
    ) {
        ModerationStores moderation = new ModerationStores(
                runtime.moderationStore(),
                runtime.playerDirectory(),
                runtime.sanctionLookup(),
                runtime.caseLookup(),
                runtime.caseReviewStore(),
                runtime.moderationHistoryStore(),
                runtime.reportStore(),
                runtime.clientEvidenceStore(),
                runtime.punishmentRequestAlertStore(),
                runtime.punishmentRequestStore()
        );
        AssetStores assets = new AssetStores(
                runtime.freezeStore(),
                runtime.staffSessionStore(),
                runtime.vanishStore(),
                runtime.inventoryJournalStore(),
                runtime.inventoryRecoveryStore(),
                runtime.economyJournalStore()
        );
        Clock clock = Clock.systemUTC();
        SecureIdentifiers identifiers = new SecureIdentifiers(new SecureRandom());
        PunishmentDraftStore draftStore = runtime.punishmentDraftStore();
        PunishmentService punishment = new PunishmentService(
                clock,
                identifiers,
                authorization,
                reasonPolicies,
                moderation.moderationStore(),
                moderation.sanctionLookup(),
                new EscalationEngine()
        );
        PunishmentRequestService punishmentRequests = new PunishmentRequestService(
                clock,
                Duration.ofDays(7),
                Duration.ofMinutes(2),
                identifiers,
                authorization,
                punishment,
                moderation.punishmentRequestStore()
        );
        ApplicationServices services = new ApplicationServices(
                punishment,
                new PunishmentDraftWorkflow(
                        clock,
                        Duration.ofHours(24),
                        punishment,
                        punishmentRequests,
                        draftStore
                ),
                punishmentRequests,
                new SanctionChangeService(authorization, runtime.sanctionMutationStore())
        );
        return new PaperStorageBindings(runtime, moderation, assets, services);
    }

    ModerationStore moderationStore() {
        return moderation.moderationStore();
    }

    PlayerDirectory playerDirectory() {
        return moderation.playerDirectory();
    }

    SanctionLookup sanctionLookup() {
        return moderation.sanctionLookup();
    }

    CaseLookup caseLookup() {
        return moderation.caseLookup();
    }

    CaseReviewStore caseReviewStore() {
        return moderation.caseReviewStore();
    }

    ModerationHistoryStore moderationHistoryStore() {
        return moderation.moderationHistoryStore();
    }

    ReportStore reportStore() {
        return moderation.reportStore();
    }

    ClientEvidenceStore clientEvidenceStore() {
        return moderation.clientEvidenceStore();
    }

    PunishmentRequestAlertStore punishmentRequestAlertStore() {
        return moderation.punishmentRequestAlertStore();
    }

    PunishmentRequestStore punishmentRequestStore() {
        return moderation.punishmentRequestStore();
    }

    FreezeStore freezeStore() {
        return assets.freezeStore();
    }

    StaffSessionStore staffSessionStore() {
        return assets.staffSessionStore();
    }

    VanishStore vanishStore() {
        return assets.vanishStore();
    }

    InventoryJournalStore inventoryJournalStore() {
        return assets.inventoryJournalStore();
    }

    InventoryRecoveryStore inventoryRecoveryStore() {
        return assets.inventoryRecoveryStore();
    }

    EconomyJournalStore economyJournalStore() {
        return assets.economyJournalStore();
    }

    PunishmentService punishmentService() {
        return services.punishmentService();
    }

    PunishmentDraftWorkflow punishmentDraftWorkflow() {
        return services.punishmentDraftWorkflow();
    }

    PunishmentRequestService punishmentRequestService() {
        return services.punishmentRequestService();
    }

    SanctionChangeService sanctionChangeService() {
        return services.sanctionChangeService();
    }

    record ModerationStores(
            ModerationStore moderationStore,
            PlayerDirectory playerDirectory,
            SanctionLookup sanctionLookup,
            CaseLookup caseLookup,
            CaseReviewStore caseReviewStore,
            ModerationHistoryStore moderationHistoryStore,
            ReportStore reportStore,
            ClientEvidenceStore clientEvidenceStore,
            PunishmentRequestAlertStore punishmentRequestAlertStore,
            PunishmentRequestStore punishmentRequestStore
    ) {
    }

    record AssetStores(
            FreezeStore freezeStore,
            StaffSessionStore staffSessionStore,
            VanishStore vanishStore,
            InventoryJournalStore inventoryJournalStore,
            InventoryRecoveryStore inventoryRecoveryStore,
            EconomyJournalStore economyJournalStore
    ) {
    }

    record ApplicationServices(
            PunishmentService punishmentService,
            PunishmentDraftWorkflow punishmentDraftWorkflow,
            PunishmentRequestService punishmentRequestService,
            SanctionChangeService sanctionChangeService
    ) {
    }
}
