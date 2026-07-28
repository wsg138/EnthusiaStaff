package net.enthusia.staff.paper;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.application.PunishmentDraftWorkflow;
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
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PunishmentDraftStore;
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
                runtime.reportStore(),
                runtime.clientEvidenceStore()
        );
        AssetStores assets = new AssetStores(
                runtime.freezeStore(),
                runtime.staffSessionStore(),
                runtime.vanishStore(),
                runtime.inventoryJournalStore(),
                runtime.economyJournalStore()
        );
        PunishmentDraftStore draftStore = runtime.punishmentDraftStore();
        PunishmentService punishment = new PunishmentService(
                Clock.systemUTC(),
                new SecureIdentifiers(new SecureRandom()),
                authorization,
                reasonPolicies,
                moderation.moderationStore(),
                new EscalationEngine()
        );
        ApplicationServices services = new ApplicationServices(
                punishment,
                new PunishmentDraftWorkflow(
                        Clock.systemUTC(), Duration.ofHours(24), punishment, draftStore
                ),
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

    ReportStore reportStore() {
        return moderation.reportStore();
    }

    ClientEvidenceStore clientEvidenceStore() {
        return moderation.clientEvidenceStore();
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

    EconomyJournalStore economyJournalStore() {
        return assets.economyJournalStore();
    }

    PunishmentService punishmentService() {
        return services.punishmentService();
    }

    PunishmentDraftWorkflow punishmentDraftWorkflow() {
        return services.punishmentDraftWorkflow();
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
            ReportStore reportStore,
            ClientEvidenceStore clientEvidenceStore
    ) {
    }

    record AssetStores(
            FreezeStore freezeStore,
            StaffSessionStore staffSessionStore,
            VanishStore vanishStore,
            InventoryJournalStore inventoryJournalStore,
            EconomyJournalStore economyJournalStore
    ) {
    }

    record ApplicationServices(
            PunishmentService punishmentService,
            PunishmentDraftWorkflow punishmentDraftWorkflow,
            SanctionChangeService sanctionChangeService
    ) {
    }
}
