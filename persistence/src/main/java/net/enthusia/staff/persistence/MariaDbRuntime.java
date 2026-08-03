package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;
import net.enthusia.staff.common.security.NetworkIdentityProtector;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.CaseReviewStore;
import net.enthusia.staff.domain.ports.ClientEvidenceStore;
import net.enthusia.staff.domain.ports.DiscordOutboxStore;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.ports.ModerationHistoryStore;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.NetworkIdentityStore;
import net.enthusia.staff.domain.ports.NetworkOutboxStore;
import net.enthusia.staff.domain.ports.OperationalStateStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PunishmentDraftStore;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.ports.VanishStore;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.report.ReportPolicy;
import net.enthusia.staff.domain.report.ReportPolicyRuntime;
import net.enthusia.staff.persistence.migration.CutoverCoordinator;
import net.enthusia.staff.persistence.migration.FencedModerationStore;
import net.enthusia.staff.persistence.migration.FencedNetworkIdentityStore;
import net.enthusia.staff.persistence.migration.FencedPunishmentRequestStore;
import net.enthusia.staff.persistence.migration.FencedSanctionMutationStore;
import net.enthusia.staff.persistence.migration.LiteBansMigrationService;

public final class MariaDbRuntime implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final ModerationStore moderationStore;
    private final PunishmentRequestStore punishmentRequestStore;
    private final PunishmentRequestAlertStore punishmentRequestAlertStore;
    private final OperationalStateStore operationalStateStore;
    private final SanctionLookup sanctionLookup;
    private final PlayerDirectory playerDirectory;
    private final NetworkOutboxStore networkOutboxStore;
    private final DiscordOutboxStore discordOutboxStore;
    private final FreezeStore freezeStore;
    private final StaffSessionStore staffSessionStore;
    private final VanishStore vanishStore;
    private final NetworkIdentityStore networkIdentityStore;
    private final SanctionMutationStore sanctionMutationStore;
    private final CaseLookup caseLookup;
    private final CaseReviewStore caseReviewStore;
    private final ModerationHistoryStore moderationHistoryStore;
    private final ReportStore reportStore;
    private final InventoryJournalStore inventoryJournalStore;
    private final EconomyJournalStore economyJournalStore;
    private final ClientEvidenceStore clientEvidenceStore;
    private final PunishmentDraftStore punishmentDraftStore;

    MariaDbRuntime(HikariDataSource dataSource) {
        this(dataSource, ReportPolicyRuntime::current);
    }

    MariaDbRuntime(HikariDataSource dataSource, Supplier<ReportPolicy> reportPolicy) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(reportPolicy, "reportPolicy");
        ObjectMapper json = jsonMapper();
        Clock clock = Clock.systemUTC();
        JdbcModerationStore moderation = new JdbcModerationStore(dataSource, json);
        this.moderationStore = new FencedModerationStore(
                dataSource,
                new RetryingModerationStore(moderation)
        );
        this.punishmentRequestStore = new FencedPunishmentRequestStore(
                dataSource,
                new RetryingPunishmentRequestStore(
                        new JdbcPunishmentRequestStore(dataSource, json, moderation)
                )
        );
        this.punishmentRequestAlertStore = new RetryingPunishmentRequestAlertStore(
                dataSource,
                new JdbcPunishmentRequestAlertStore(dataSource)
        );
        this.operationalStateStore = new JdbcOperationalStateStore(dataSource);
        this.sanctionLookup = new JdbcSanctionLookup(dataSource);
        this.playerDirectory = new JdbcPlayerDirectory(dataSource);
        this.networkOutboxStore = new JdbcNetworkOutboxStore(dataSource);
        this.discordOutboxStore = new JdbcDiscordOutboxStore(dataSource);
        this.freezeStore = new JdbcFreezeStore(dataSource);
        this.staffSessionStore = new JdbcStaffSessionStore(dataSource);
        this.vanishStore = new JdbcVanishStore(dataSource);
        this.networkIdentityStore = new FencedNetworkIdentityStore(
                dataSource,
                new JdbcNetworkIdentityStore(dataSource, json)
        );
        SanctionMutationStore mutationStore = new CompositeSanctionMutationStore(
                new JdbcSanctionMutationStore(dataSource, json, clock),
                new JdbcExactSanctionMutationStore(dataSource, json, clock)
        );
        this.sanctionMutationStore = new FencedSanctionMutationStore(dataSource, mutationStore);
        this.caseLookup = new JdbcCaseLookup(dataSource);
        this.caseReviewStore = new JdbcCaseReviewStore(dataSource, clock, json);
        this.moderationHistoryStore = new JdbcModerationHistoryStore(dataSource, caseReviewStore);
        this.reportStore = new JdbcReportStore(dataSource, json, reportPolicy, clock);
        this.inventoryJournalStore = new JdbcInventoryJournalStore(dataSource, json);
        this.economyJournalStore = new JdbcEconomyJournalStore(dataSource, json);
        this.clientEvidenceStore = new JdbcClientEvidenceStore(dataSource, json);
        this.punishmentDraftStore = new JdbcPunishmentDraftStore(dataSource, json);
    }

    public ModerationStore moderationStore() { return moderationStore; }
    public PunishmentRequestStore punishmentRequestStore() { return punishmentRequestStore; }
    public PunishmentRequestAlertStore punishmentRequestAlertStore() { return punishmentRequestAlertStore; }
    public OperationalStateStore operationalStateStore() { return operationalStateStore; }
    public SanctionLookup sanctionLookup() { return sanctionLookup; }
    public PlayerDirectory playerDirectory() { return playerDirectory; }
    public NetworkOutboxStore networkOutboxStore() { return networkOutboxStore; }
    public DiscordOutboxStore discordOutboxStore() { return discordOutboxStore; }
    public FreezeStore freezeStore() { return freezeStore; }
    public StaffSessionStore staffSessionStore() { return staffSessionStore; }
    public VanishStore vanishStore() { return vanishStore; }
    public NetworkIdentityStore networkIdentityStore() { return networkIdentityStore; }
    public SanctionMutationStore sanctionMutationStore() { return sanctionMutationStore; }
    public CaseLookup caseLookup() { return caseLookup; }
    public CaseReviewStore caseReviewStore() { return caseReviewStore; }
    public ModerationHistoryStore moderationHistoryStore() { return moderationHistoryStore; }
    public ReportStore reportStore() { return reportStore; }
    public InventoryJournalStore inventoryJournalStore() { return inventoryJournalStore; }
    public EconomyJournalStore economyJournalStore() { return economyJournalStore; }
    public ClientEvidenceStore clientEvidenceStore() { return clientEvidenceStore; }
    public PunishmentDraftStore punishmentDraftStore() { return punishmentDraftStore; }

    public WebsiteModerationStore websiteModerationStore(PunishmentCodeProtector codeProtector) {
        return new JdbcWebsiteModerationStore(dataSource, codeProtector, jsonMapper());
    }

    public LiteBansMigrationService liteBansMigrationService() {
        return new LiteBansMigrationService(dataSource, jsonMapper(), Clock.systemUTC());
    }

    public LiteBansMigrationService liteBansMigrationService(NetworkIdentityProtector protector) {
        if (protector == null) {
            throw new IllegalArgumentException("network identity protector must be present");
        }
        return new LiteBansMigrationService(dataSource, jsonMapper(), Clock.systemUTC(), protector);
    }

    public CutoverCoordinator cutoverCoordinator() {
        return new CutoverCoordinator(dataSource, jsonMapper(), Clock.systemUTC());
    }

    private static ObjectMapper jsonMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void close() { dataSource.close(); }
}
