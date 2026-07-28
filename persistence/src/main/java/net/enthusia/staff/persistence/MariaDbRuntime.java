package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import net.enthusia.staff.common.security.NetworkIdentityProtector;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.ClientEvidenceStore;
import net.enthusia.staff.domain.ports.DiscordOutboxStore;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.InventoryJournalStore;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.ports.VanishStore;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.ports.NetworkOutboxStore;
import net.enthusia.staff.domain.ports.NetworkIdentityStore;
import net.enthusia.staff.domain.ports.OperationalStateStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PunishmentDraftStore;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.persistence.migration.LiteBansMigrationService;
import net.enthusia.staff.persistence.migration.CutoverCoordinator;

public final class MariaDbRuntime implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final ModerationStore moderationStore;
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
    private final ReportStore reportStore;
    private final InventoryJournalStore inventoryJournalStore;
    private final EconomyJournalStore economyJournalStore;
    private final ClientEvidenceStore clientEvidenceStore;
    private final PunishmentDraftStore punishmentDraftStore;

    MariaDbRuntime(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.moderationStore = new JdbcModerationStore(dataSource, new ObjectMapper());
        this.operationalStateStore = new JdbcOperationalStateStore(dataSource);
        this.sanctionLookup = new JdbcSanctionLookup(dataSource);
        this.playerDirectory = new JdbcPlayerDirectory(dataSource);
        this.networkOutboxStore = new JdbcNetworkOutboxStore(dataSource);
        this.discordOutboxStore = new JdbcDiscordOutboxStore(dataSource);
        this.freezeStore = new JdbcFreezeStore(dataSource);
        this.staffSessionStore = new JdbcStaffSessionStore(dataSource);
        this.vanishStore = new JdbcVanishStore(dataSource);
        this.networkIdentityStore = new JdbcNetworkIdentityStore(dataSource, new ObjectMapper());
        this.sanctionMutationStore = new JdbcSanctionMutationStore(dataSource, new ObjectMapper(), Clock.systemUTC());
        this.caseLookup = new JdbcCaseLookup(dataSource);
        this.reportStore = new JdbcReportStore(dataSource, new ObjectMapper());
        this.inventoryJournalStore = new JdbcInventoryJournalStore(dataSource, new ObjectMapper());
        this.economyJournalStore = new JdbcEconomyJournalStore(dataSource, new ObjectMapper());
        this.clientEvidenceStore = new JdbcClientEvidenceStore(dataSource, new ObjectMapper());
        this.punishmentDraftStore = new JdbcPunishmentDraftStore(dataSource, new ObjectMapper());
    }

    public ModerationStore moderationStore() {
        return moderationStore;
    }

    public OperationalStateStore operationalStateStore() {
        return operationalStateStore;
    }

    public SanctionLookup sanctionLookup() {
        return sanctionLookup;
    }

    public PlayerDirectory playerDirectory() {
        return playerDirectory;
    }

    public NetworkOutboxStore networkOutboxStore() {
        return networkOutboxStore;
    }

    public DiscordOutboxStore discordOutboxStore() {
        return discordOutboxStore;
    }

    public FreezeStore freezeStore() {
        return freezeStore;
    }

    public StaffSessionStore staffSessionStore() {
        return staffSessionStore;
    }

    public VanishStore vanishStore() {
        return vanishStore;
    }

    public NetworkIdentityStore networkIdentityStore() {
        return networkIdentityStore;
    }

    public SanctionMutationStore sanctionMutationStore() {
        return sanctionMutationStore;
    }

    public CaseLookup caseLookup() {
        return caseLookup;
    }

    public ReportStore reportStore() {
        return reportStore;
    }

    public InventoryJournalStore inventoryJournalStore() {
        return inventoryJournalStore;
    }

    public EconomyJournalStore economyJournalStore() {
        return economyJournalStore;
    }

    public ClientEvidenceStore clientEvidenceStore() {
        return clientEvidenceStore;
    }

    public PunishmentDraftStore punishmentDraftStore() {
        return punishmentDraftStore;
    }

    public WebsiteModerationStore websiteModerationStore(PunishmentCodeProtector codeProtector) {
        return new JdbcWebsiteModerationStore(dataSource, codeProtector, new ObjectMapper());
    }

    public LiteBansMigrationService liteBansMigrationService() {
        return new LiteBansMigrationService(dataSource, new ObjectMapper(), Clock.systemUTC());
    }

    public LiteBansMigrationService liteBansMigrationService(NetworkIdentityProtector protector) {
        if (protector == null) {
            throw new IllegalArgumentException("network identity protector must be present");
        }
        return new LiteBansMigrationService(dataSource, new ObjectMapper(), Clock.systemUTC(), protector);
    }

    public CutoverCoordinator cutoverCoordinator() {
        return new CutoverCoordinator(dataSource, new ObjectMapper(), Clock.systemUTC());
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
