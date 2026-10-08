package net.enthusia.staff.paper.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.enthusia.staff.domain.auth.StaffRank;

/** Strict, side-effect-free parser for the C2 preview-only ranks.yml. */
public final class RankConfigurationLoader {
    private static final String RESOURCE_NAME = "ranks.yml";
    private static final String RANKS_PATH = "root.ranks";
    private static final Set<String> ROOT_KEYS = Set.of("schema-version", "ranks");
    private static final Set<String> RANK_KEYS = Set.of("inherits", "grants");
    private final ObjectMapper yaml;

    public RankConfigurationLoader() {
        YAMLFactory factory = new YAMLFactory();
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        yaml = new ObjectMapper(factory);
    }

    public RankConfigurationSnapshot load(Path file) {
        if (file == null) {
            throw invalid("ranks.yml path must be present");
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return load(reader);
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to read ranks.yml", exception);
        }
    }

    public RankConfigurationSnapshot load(InputStream resource) {
        if (resource == null) {
            throw invalid("ranks.yml resource was not found");
        }
        try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return load(reader);
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to read ranks.yml resource", exception);
        }
    }

    RankConfigurationSnapshot load(Reader reader) {
        try {
            JsonNode root = yaml.readTree(reader);
            requireObject(root, "root");
            exactFields(root, ROOT_KEYS, "root");
            JsonNode version = required(root, "schema-version", "root");
            if (!version.isIntegralNumber() || !version.canConvertToInt()
                    || version.intValue() != RankConfigurationSnapshot.CURRENT_SCHEMA_VERSION) {
                throw invalid("root.schema-version must be "
                        + RankConfigurationSnapshot.CURRENT_SCHEMA_VERSION);
            }
            JsonNode ranks = required(root, "ranks", "root");
            requireObject(ranks, RANKS_PATH);

            Set<String> names = new HashSet<>();
            for (StaffRank rank : StaffRank.values()) {
                names.add(rank.name());
            }
            exactFields(ranks, names, RANKS_PATH);

            EnumMap<StaffRank, Set<StaffRank>> inherits = new EnumMap<>(StaffRank.class);
            EnumMap<StaffRank, Set<StaffCapability>> grants = new EnumMap<>(StaffRank.class);
            for (StaffRank rank : StaffRank.values()) {
                JsonNode fields = required(ranks, rank.name(), RANKS_PATH);
                String path = "root.ranks." + rank;
                requireObject(fields, path);
                exactFields(fields, RANK_KEYS, path);
                inherits.put(rank, parseRanks(required(fields, "inherits", path), path + ".inherits"));
                grants.put(rank, parseCapabilities(required(fields, "grants", path), path + ".grants"));
            }
            return new RankConfigurationSnapshot(version.intValue(), inherits, grants);
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to parse ranks.yml", exception);
        } catch (IllegalArgumentException exception) {
            throw exception instanceof ConfigurationValidationException
                    ? exception
                    : new ConfigurationValidationException("Invalid ranks.yml: " + exception.getMessage(), exception);
        }
    }

    private static Set<StaffRank> parseRanks(JsonNode value, String path) {
        requireArray(value, path);
        EnumSet<StaffRank> parsed = EnumSet.noneOf(StaffRank.class);
        for (JsonNode member : value) {
            StaffRank rank = enumValue(member, StaffRank.class, path);
            if (!parsed.add(rank)) {
                throw invalid(path + " contains a duplicate " + rank);
            }
        }
        return parsed;
    }

    private static Set<StaffCapability> parseCapabilities(JsonNode value, String path) {
        requireArray(value, path);
        EnumSet<StaffCapability> parsed = EnumSet.noneOf(StaffCapability.class);
        for (JsonNode member : value) {
            StaffCapability capability = enumValue(member, StaffCapability.class, path);
            if (!parsed.add(capability)) {
                throw invalid(path + " contains a duplicate " + capability);
            }
        }
        return parsed;
    }

    private static <E extends Enum<E>> E enumValue(JsonNode value, Class<E> type, String path) {
        if (!value.isTextual()) {
            throw invalid(path + " entries must be exact enum names");
        }
        try {
            return Enum.valueOf(type, value.textValue());
        } catch (IllegalArgumentException exception) {
            throw invalid(path + " contains an unknown " + type.getSimpleName() + " " + value.textValue());
        }
    }

    private static void requireArray(JsonNode value, String path) {
        if (!value.isArray()) {
            throw invalid(path + " must be an array");
        }
    }

    private static JsonNode required(JsonNode parent, String key, String path) {
        JsonNode value = parent.get(key);
        if (value == null || value.isNull()) {
            throw invalid(path + "." + key + " is required");
        }
        return value;
    }

    private static void exactFields(JsonNode node, Set<String> allowed, String path) {
        Iterator<String> names = node.fieldNames();
        Set<String> actual = new HashSet<>();
        names.forEachRemaining(actual::add);
        if (!actual.equals(allowed)) {
            Set<String> missing = new HashSet<>(allowed);
            missing.removeAll(actual);
            Set<String> unknown = new HashSet<>(actual);
            unknown.removeAll(allowed);
            throw invalid(path + " missing " + missing + ", unknown " + unknown);
        }
    }

    private static void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw invalid(path + " must be an object");
        }
    }

    private static ConfigurationValidationException invalid(String message) {
        return new ConfigurationValidationException(message);
    }
}
