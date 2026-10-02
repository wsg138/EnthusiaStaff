package net.enthusia.staff.paper.visibility;

import io.papermc.paper.ServerBuildInfo;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

final class VanishClientGameModeAdapterFactory {
    static final String PAPER_BRAND_ID = String.valueOf(ServerBuildInfo.BRAND_PAPER_ID);
    static final String LEAF_BRAND_ID = "winds-studio:leaf";

    private static final List<SupportedRuntime> SUPPORTED_RUNTIMES = List.of(
            new SupportedRuntime(PAPER_BRAND_ID, "26.2", 129, "Paper 26.2 build 129"),
            new SupportedRuntime(LEAF_BRAND_ID, "1.21.11", 115, "Leaf 1.21.11 build 115")
    );

    private VanishClientGameModeAdapterFactory() { }

    static VanishClientGameModeAdapter install(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        ServerBuildInfo info = ServerBuildInfo.buildInfo();
        return select(
                logger,
                String.valueOf(info.brandId()),
                info.minecraftVersionId(),
                info.buildNumber(),
                runtime -> ReflectiveVanishClientGameModeAdapter.install(logger, runtime.label())
        );
    }

    static VanishClientGameModeAdapter select(
            Logger logger,
            String brandId,
            String minecraftVersion,
            OptionalInt buildNumber,
            Function<SupportedRuntime, VanishClientGameModeAdapter> installer
    ) {
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(installer, "installer");
        Optional<SupportedRuntime> runtime = supportedRuntime(brandId, minecraftVersion, buildNumber);
        if (runtime.isPresent()) {
            return installer.apply(runtime.orElseThrow());
        }
        String reason = "unsupported runtime identity: " + describe(brandId, minecraftVersion, buildNumber);
        logger.log(Level.WARNING, "Vanish no-clip client adapter disabled: {0}", reason);
        return unavailable(reason);
    }

    static Optional<SupportedRuntime> supportedRuntime(
            String brandId,
            String minecraftVersion,
            OptionalInt buildNumber
    ) {
        return SUPPORTED_RUNTIMES.stream()
                .filter(runtime -> runtime.matches(brandId, minecraftVersion, buildNumber))
                .findFirst();
    }

    static List<SupportedRuntime> supportedRuntimes() {
        return SUPPORTED_RUNTIMES;
    }

    static VanishClientGameModeAdapter unavailable(String reason) {
        return new UnavailableClientGameModeAdapter(reason);
    }

    private static String describe(String brandId, String minecraftVersion, OptionalInt buildNumber) {
        String build = buildNumber.isPresent() ? Integer.toString(buildNumber.getAsInt()) : "unknown";
        return String.valueOf(brandId) + "/" + String.valueOf(minecraftVersion) + "/" + build;
    }

    record SupportedRuntime(String brandId, String minecraftVersion, int buildNumber, String label) {
        SupportedRuntime {
            Objects.requireNonNull(brandId, "brandId");
            Objects.requireNonNull(minecraftVersion, "minecraftVersion");
            Objects.requireNonNull(label, "label");
        }

        boolean matches(String actualBrandId, String actualMinecraftVersion, OptionalInt actualBuildNumber) {
            return brandId.equals(actualBrandId)
                    && minecraftVersion.equals(actualMinecraftVersion)
                    && actualBuildNumber.isPresent()
                    && buildNumber == actualBuildNumber.getAsInt();
        }
    }

    private record UnavailableClientGameModeAdapter(String unavailableReason)
            implements VanishClientGameModeAdapter {
        private UnavailableClientGameModeAdapter {
            Objects.requireNonNull(unavailableReason, "unavailableReason");
        }

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean present(org.bukkit.entity.Player player, org.bukkit.GameMode gameMode) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(gameMode, "gameMode");
            return false;
        }
    }
}
