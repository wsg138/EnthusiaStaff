package net.enthusia.staff.paper.config.policyv2;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;

public final class PolicyV2ConfigurationLoader {
    private static final String ROOT_PATH = "root";
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schema-version", "mode", "active-version", "versions"
    );

    private final ObjectMapper yaml;
    private final PolicyV2SnapshotParser snapshots = new PolicyV2SnapshotParser();

    public PolicyV2ConfigurationLoader() {
        YAMLFactory factory = new YAMLFactory();
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.yaml = new ObjectMapper(factory);
    }

    public PolicyV2Configuration load(Path file) {
        if (file == null) {
            throw new PolicyV2ConfigurationException("Policy v2 configuration path must be present");
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return load(reader, file.getFileName().toString());
        } catch (IOException exception) {
            throw new PolicyV2ConfigurationException(
                    "Unable to read Policy v2 configuration " + file.getFileName(),
                    exception
            );
        }
    }

    public PolicyV2Configuration load(InputStream input, String sourceName) {
        if (input == null) {
            throw new PolicyV2ConfigurationException(sourceName + " was not found");
        }
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return load(reader, sourceName);
        } catch (IOException exception) {
            throw new PolicyV2ConfigurationException("Unable to read " + sourceName, exception);
        }
    }

    PolicyV2Configuration load(Reader reader, String sourceName) {
        try {
            JsonNode root = yaml.readTree(reader);
            PolicyV2Yaml.object(root, ROOT_PATH);
            PolicyV2Yaml.rejectUnknown(root, ROOT_FIELDS, ROOT_PATH);
            int schemaVersion = schemaVersion(root);
            PolicyV2FeatureMode mode = PolicyV2Yaml.enumValue(
                    PolicyV2FeatureMode.class,
                    PolicyV2Yaml.text(root, "mode", ROOT_PATH),
                    ROOT_PATH + ".mode"
            );
            String activeVersion = PolicyV2Yaml.text(root, "active-version", ROOT_PATH);
            Map<String, PolicySnapshot> parsed = parseVersions(
                    PolicyV2Yaml.required(root, "versions", ROOT_PATH)
            );
            return new PolicyV2Configuration(schemaVersion, mode, activeVersion, parsed);
        } catch (IOException exception) {
            throw new PolicyV2ConfigurationException("Unable to parse " + sourceName, exception);
        } catch (PolicyV2ConfigurationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new PolicyV2ConfigurationException(
                    "Invalid Policy v2 configuration: " + exception.getMessage(),
                    exception
            );
        }
    }

    private static int schemaVersion(JsonNode root) {
        long version = PolicyV2Yaml.longValue(root, "schema-version", ROOT_PATH);
        if (version != PolicyV2Configuration.CURRENT_SCHEMA_VERSION) {
            throw PolicyV2Yaml.invalid(ROOT_PATH + ".schema-version must be "
                    + PolicyV2Configuration.CURRENT_SCHEMA_VERSION);
        }
        return (int) version;
    }

    private Map<String, PolicySnapshot> parseVersions(JsonNode node) {
        PolicyV2Yaml.array(node, ROOT_PATH + ".versions", true);
        Map<String, PolicySnapshot> parsed = new LinkedHashMap<>();
        for (int index = 0; index < node.size(); index++) {
            PolicySnapshot snapshot = snapshots.parse(node.get(index), ROOT_PATH + ".versions[" + index + "]");
            if (parsed.putIfAbsent(snapshot.version(), snapshot) != null) {
                throw PolicyV2Yaml.invalid(
                        ROOT_PATH + ".versions contains duplicate policy version " + snapshot.version()
                );
            }
        }
        return Map.copyOf(parsed);
    }
}
