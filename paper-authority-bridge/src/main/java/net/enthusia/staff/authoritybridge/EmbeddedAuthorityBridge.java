package net.enthusia.staff.authoritybridge;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.sql.DataSource;
import net.luckperms.api.LuckPermsProvider;
import net.enthusia.staff.persistence.DatabaseConfig;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Opt-in embedded compatibility service. Never changes moderation policy or command authority. */
public final class EmbeddedAuthorityBridge implements AutoCloseable {
    public static final String SWITCH_FILE = "authority-bridge.properties";
    private static final String LEGACY_PLUGIN = "EnthusiaStaffAuthorityBridge";
    private static final String AUTHORITY_SWITCH = "authority.enabled";
    private static final String COLLECTOR_SWITCH = "collector.enabled";
    private static final Set<String> ALLOWED = Set.of(AUTHORITY_SWITCH, COLLECTOR_SWITCH);

    private final JavaPlugin owner;
    private final Path directory;
    private final boolean collectorEnabled;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private AuthorityEndpoint endpoint;
    private TransitionDataCollector collector;

    private EmbeddedAuthorityBridge(JavaPlugin owner, Path directory, boolean collectorEnabled) {
        this.owner = owner;
        this.directory = directory;
        this.collectorEnabled = collectorEnabled;
    }

    /** Both switches default off, including when the switch file is absent. */
    public static EmbeddedAuthorityBridge open(JavaPlugin owner, Path directory) {
        if (owner == null || directory == null) {
            throw new IllegalArgumentException("embedded bridge dependencies must be present");
        }
        Properties flags = readFlags(directory);
        boolean authority = enabled(flags, AUTHORITY_SWITCH);
        boolean collection = enabled(flags, COLLECTOR_SWITCH);
        EmbeddedAuthorityBridge bridge = new EmbeddedAuthorityBridge(owner, directory, collection);
        if (!authority && !collection) {
            return bridge;
        }
        Plugin standalone = owner.getServer().getPluginManager().getPlugin(LEGACY_PLUGIN);
        if (standalone != null) {
            throw new IllegalStateException("standalone authority bridge is installed; embedded features refused");
        }
        if (authority) {
            try {
                bridge.endpoint = AuthorityEndpoint.start(
                        AuthorityBridgeConfiguration.load(directory), LuckPermsProvider.get(), owner.getLogger());
            } catch (IOException exception) {
                throw new IllegalStateException("embedded private authority endpoint unavailable", exception);
            }
        }
        return bridge;
    }

    /** Start only after the primary Paper database bootstrap migrated the schema. */
    public void startCollector(DataSource dataSource, String serverId, DatabaseConfig primaryDatabase) {
        synchronized (lifecycleLock) {
            startCollectorLocked(dataSource, serverId, primaryDatabase);
        }
    }

    private void startCollectorLocked(DataSource dataSource, String serverId, DatabaseConfig primaryDatabase) {
        if (closed.get() || !collectorEnabled || collector != null) {
            return;
        }
        if (owner.getServer().getPluginManager().getPlugin(LEGACY_PLUGIN) != null) {
            throw new IllegalStateException("standalone authority bridge is installed; collector refused");
        }
        TransitionCollectorConfiguration.Value config = TransitionCollectorConfiguration
                .loadIfPresent(directory).orElseThrow(
                        () -> new IllegalStateException("enabled collector config is unavailable"));
        if (!config.serverId().equalsIgnoreCase(serverId)) {
            throw new IllegalStateException("collector server id differs from primary Paper server id");
        }
        verifySameDatabase(config.database(), primaryDatabase);
        TransitionDataCollector opened = TransitionDataCollector.openEmbedded(owner, config, dataSource);
        try {
            opened.start();
            collector = opened;
        } catch (RuntimeException exception) {
            opened.close();
            throw exception;
        }
    }

    /** Never attach transition imports to a different database than the configured legacy source. */
    static void verifySameDatabase(DatabaseConfig legacy, DatabaseConfig primary) {
        if (legacy == null || primary == null
                || !legacy.jdbcUrl().equals(primary.jdbcUrl())
                || !legacy.username().equals(primary.username())
                || !legacy.password().equals(primary.password())) {
            throw new IllegalStateException("collector database differs from primary Paper database");
        }
    }

    public static boolean requested(Path directory) {
        Properties flags = readFlags(directory);
        return enabled(flags, AUTHORITY_SWITCH) || enabled(flags, COLLECTOR_SWITCH);
    }

    private static Properties readFlags(Path directory) {
        Properties flags = new Properties();
        Path file = directory.resolve(SWITCH_FILE);
        if (Files.notExists(file)) {
            return flags;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            flags.load(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("embedded bridge switch configuration cannot be read", exception);
        }
        if (!ALLOWED.containsAll(flags.stringPropertyNames())) {
            throw new IllegalArgumentException("unsupported embedded bridge switch");
        }
        return flags;
    }

    private static boolean enabled(Properties flags, String key) {
        String raw = flags.getProperty(key, "false").trim();
        if (!raw.equals("true") && !raw.equals("false")) {
            throw new IllegalArgumentException("embedded bridge switches must be true or false");
        }
        return Boolean.parseBoolean(raw);
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            closeLocked();
        }
    }

    private void closeLocked() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (collector != null) {
            try {
                collector.close();
            } catch (RuntimeException exception) {
                owner.getLogger().log(Level.WARNING,
                        "embedded_transition_collector_close_failed type={0}", exception.getClass().getSimpleName());
            }
        }
        if (endpoint != null) {
            endpoint.close();
        }
    }
}
