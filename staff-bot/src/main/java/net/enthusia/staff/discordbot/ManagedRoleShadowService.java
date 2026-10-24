package net.enthusia.staff.discordbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.ReconciliationState;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;

/** Reads provider-neutral desired-role claims and resolves only canonical current Discord links. */
final class ManagedRoleShadowService {
    private static final String RESOURCE_TYPE = "MANAGED_ROLE";
    private static final int DOCUMENT_VERSION = 1;
    private static final int MAX_ACCOUNTS_PER_CLAIM = 10_000;
    private static final Set<String> ALLOWED_NAMESPACES = Set.of("luma-guilds", "playtime-numerals");
    private static final Pattern LOCAL_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    record Claim(
            String reconciliationKey,
            String resourceId,
            String namespace,
            String localKey,
            String displayName,
            Optional<String> existingDiscordRoleId,
            Set<UUID> desiredMinecraftAccounts,
            boolean delete
    ) {
        Claim {
            desiredMinecraftAccounts = Set.copyOf(desiredMinecraftAccounts);
        }
    }

    record ResolvedClaim(
            Claim claim,
            Set<String> desiredDiscordUserIds,
            int unlinkedMinecraftAccounts
    ) {
        ResolvedClaim {
            desiredDiscordUserIds = Set.copyOf(desiredDiscordUserIds);
            if (unlinkedMinecraftAccounts < 0) {
                throw new IllegalArgumentException("unlinked account count cannot be negative");
            }
        }
    }

    record Snapshot(List<Claim> claims, List<String> invalidResourceRefs, boolean truncated) {
        Snapshot {
            claims = List.copyOf(claims);
            invalidResourceRefs = List.copyOf(invalidResourceRefs);
        }

        boolean incomplete() {
            return truncated || !invalidResourceRefs.isEmpty();
        }
    }

    private final DiscordStaffReadRuntime data;
    private final ManagedRoleShadowConfiguration configuration;
    private final ObjectMapper json;

    ManagedRoleShadowService(
            DiscordStaffReadRuntime data,
            ManagedRoleShadowConfiguration configuration,
            ObjectMapper json
    ) {
        if (data == null || configuration == null || json == null) {
            throw new IllegalArgumentException("managed-role shadow dependencies must be present");
        }
        this.data = data;
        this.configuration = configuration;
        this.json = json;
    }

    ManagedRoleShadowConfiguration configuration() {
        return configuration;
    }

    Snapshot snapshot() {
        List<ReconciliationState> states = data.managedRoleStates(configuration.maxClaims() + 1);
        return decodeStates(states, configuration.maxClaims(), json);
    }

    static Snapshot decodeStates(
            List<ReconciliationState> states,
            int maxClaims,
            ObjectMapper json
    ) {
        if (states == null || json == null || maxClaims < 1) {
            throw new IllegalArgumentException("managed-role shadow decode inputs are invalid");
        }
        boolean truncated = states.size() > maxClaims;
        int count = Math.min(states.size(), maxClaims);
        List<Claim> claims = new ArrayList<>(count);
        List<String> invalid = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ReconciliationState state = states.get(index);
            try {
                claims.add(decode(state, json));
            } catch (RuntimeException exception) {
                invalid.add(safeResourceReference(state));
            }
        }
        return new Snapshot(claims, invalid, truncated);
    }

    List<ResolvedClaim> resolve(Snapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("managed-role shadow snapshot is required");
        }
        Map<UUID, Set<String>> linkedUsers = new HashMap<>();
        List<ResolvedClaim> result = new ArrayList<>(snapshot.claims().size());
        for (Claim claim : snapshot.claims()) {
            result.add(resolveClaim(claim, id -> linkedUsers.computeIfAbsent(id, this::discordUsers)));
        }
        return List.copyOf(result);
    }

    static ResolvedClaim resolveClaim(Claim claim, Function<UUID, Set<String>> discordUsers) {
        Set<String> desiredDiscord = new LinkedHashSet<>();
        int unlinked = 0;
        for (UUID minecraftId : claim.desiredMinecraftAccounts()) {
            Set<String> users = discordUsers.apply(minecraftId);
            if (users.isEmpty()) {
                unlinked++;
            } else {
                desiredDiscord.addAll(users);
            }
        }
        return new ResolvedClaim(claim, desiredDiscord, unlinked);
    }

    private Set<String> discordUsers(UUID minecraftId) {
        return data.subjectForMinecraft(minecraftId)
                .map(versioned -> versioned.subject().discordUserIds().stream()
                        .map(userId -> userId.value())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()))
                .orElse(Set.of());
    }

    private static Claim decode(ReconciliationState state, ObjectMapper json) {
        if (!RESOURCE_TYPE.equals(state.resourceType())) {
            throw new IllegalStateException("unexpected managed-role resource type");
        }
        try {
            JsonNode root = json.readTree(state.desiredStateJson());
            requireVersion(root);
            String namespace = text(root, "namespace", 64);
            if (!ALLOWED_NAMESPACES.contains(namespace)) {
                throw new IllegalStateException("managed-role namespace is not allowlisted");
            }
            String localKey = text(root, "localKey", 128);
            if (!LOCAL_KEY.matcher(localKey).matches()) {
                throw new IllegalStateException("managed-role local key is invalid");
            }
            boolean delete = root.path("delete").asBoolean(false);
            String displayName = root.path("displayName").asText("");
            if (!delete) {
                requireDisplayName(displayName);
            } else if (!displayName.isEmpty()) {
                requireDisplayName(displayName);
            }
            Optional<String> existingRoleId = existingRoleId(root.path("existingDiscordRoleId"));
            Set<UUID> accounts = accounts(root.path("desiredMinecraftAccounts"));
            return new Claim(
                    state.reconciliationKey(),
                    state.resourceId(),
                    namespace,
                    localKey,
                    displayName,
                    existingRoleId,
                    accounts,
                    delete
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("managed-role desired state is invalid", exception);
        }
    }

    private static void requireVersion(JsonNode root) {
        if (root == null || root.path("version").asInt(-1) != DOCUMENT_VERSION) {
            throw new IllegalStateException("managed-role desired-state version is unsupported");
        }
    }

    private static String text(JsonNode root, String field, int maximum) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual()) {
            throw new IllegalStateException("managed-role desired state is missing " + field);
        }
        String value = node.asText();
        if (value.isBlank() || value.length() > maximum || hasControl(value)) {
            throw new IllegalStateException("managed-role desired-state field is invalid: " + field);
        }
        return value;
    }

    private static void requireDisplayName(String displayName) {
        if (displayName == null
                || displayName.isBlank()
                || displayName.length() > 100
                || hasControl(displayName)) {
            throw new IllegalStateException("managed-role display name is invalid");
        }
    }

    private static Optional<String> existingRoleId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isTextual()) {
            throw new IllegalStateException("managed-role existing Discord role ID must be textual");
        }
        String value = node.asText("");
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (!value.matches("[0-9]{5,30}")) {
            throw new IllegalStateException("managed-role existing Discord role ID is invalid");
        }
        return Optional.of(value);
    }

    private static Set<UUID> accounts(JsonNode node) {
        if (!node.isArray() || node.size() > MAX_ACCOUNTS_PER_CLAIM) {
            throw new IllegalStateException("managed-role desired account list is invalid");
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) {
                throw new IllegalStateException("managed-role desired account is not a UUID string");
            }
            result.add(UUID.fromString(value.asText()));
        }
        return Set.copyOf(result);
    }

    private static String safeResourceReference(ReconciliationState state) {
        String source = state.reconciliationKey() + "\n" + state.resourceId();
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8))
                .toString()
                .substring(0, 12);
    }

    private static boolean hasControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
