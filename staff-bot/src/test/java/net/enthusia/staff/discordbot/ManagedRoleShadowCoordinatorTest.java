package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.Test;

final class ManagedRoleShadowCoordinatorTest {
    @Test
    void shadowGateDoesNotTreatEmptyOrIncompleteSamplesAsParity() {
        var empty = new ManagedRoleShadowService.Snapshot(List.of(), List.of(), false);
        assertEquals(ManagedRoleShadowCoordinator.SampleGate.NO_CLAIMS,
                ManagedRoleShadowCoordinator.assessSample(empty, 0, 0, 0));
        assertEquals(ManagedRoleShadowCoordinator.SampleGate.ZERO_DRIFT_SAMPLE,
                ManagedRoleShadowCoordinator.assessSample(empty, 1, 0, 0));
        assertEquals(ManagedRoleShadowCoordinator.SampleGate.ROLE_DRIFT,
                ManagedRoleShadowCoordinator.assessSample(empty, 1, 1, 0));
        assertEquals(ManagedRoleShadowCoordinator.SampleGate.ROLE_ID_UNBOUND,
                ManagedRoleShadowCoordinator.assessSample(empty, 1, 0, 1));
        assertEquals(ManagedRoleShadowCoordinator.SampleGate.SNAPSHOT_INCOMPLETE,
                ManagedRoleShadowCoordinator.assessSample(
                        new ManagedRoleShadowService.Snapshot(List.of(), List.of(), true), 10, 0, 0));
        assertEquals(ManagedRoleShadowCoordinator.SampleGate.SNAPSHOT_INCOMPLETE,
                ManagedRoleShadowCoordinator.assessSample(
                        new ManagedRoleShadowService.Snapshot(List.of(), List.of("hashed-resource"), false), 10, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ManagedRoleShadowCoordinator.assessSample(null, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ManagedRoleShadowCoordinator.assessSample(empty, -1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ManagedRoleShadowCoordinator.assessSample(empty, 0, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ManagedRoleShadowCoordinator.assessSample(empty, 1, 0, 2));
    }

    private static final String PLAYTIME_ROLE_NAME = "Playtime XII";
    private static final ClassLoader PROXY_CLASS_LOADER = Thread.currentThread().getContextClassLoader();
    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = Map.of(
            boolean.class, false,
            byte.class, (byte) 0,
            short.class, (short) 0,
            int.class, 0,
            long.class, 0L,
            float.class, 0F,
            double.class, 0D,
            char.class, '\0'
    );
    @Test
    void exactRoleIdDisambiguatesDuplicateDisplayNames() {
        Role selected = role("1552390213500928122", PLAYTIME_ROLE_NAME);
        Role duplicate = role("1552390213500928999", PLAYTIME_ROLE_NAME);
        Member desired = member("2000000000000000001", List.of(selected));
        Guild guild = guild(List.of(selected, duplicate));

        ManagedRoleShadowCoordinator.ClaimResult result = ManagedRoleShadowCoordinator.compare(
                resolved(Optional.of(selected.getId()), Set.of(desired.getId()), false),
                List.of(desired),
                guild
        );

        assertEquals(ManagedRoleShadowCoordinator.State.MATCH, result.state());
        assertEquals(1, result.desiredMembers());
        assertEquals(1, result.observedMembers());
    }

    @Test
    void nameOnlyClaimRejectsAmbiguousDuplicateRoles() {
        Role first = role("1552390213500928122", PLAYTIME_ROLE_NAME);
        Role second = role("1552390213500928999", PLAYTIME_ROLE_NAME);

        ManagedRoleShadowCoordinator.ClaimResult result = ManagedRoleShadowCoordinator.compare(
                resolved(Optional.empty(), Set.of(), false),
                List.of(),
                guild(List.of(first, second))
        );

        assertEquals(ManagedRoleShadowCoordinator.State.ROLE_AMBIGUOUS, result.state());
    }

    @Test
    void deleteWithExactRoleIdMatchesWhenRoleIsAlreadyGone() {
        ManagedRoleShadowCoordinator.ClaimResult result = ManagedRoleShadowCoordinator.compare(
                resolved(Optional.of("1552390213500928122"), Set.of(), true),
                List.of(),
                guild(List.of())
        );

        assertEquals(ManagedRoleShadowCoordinator.State.DELETE_MATCH, result.state());
    }

    private static ManagedRoleShadowService.ResolvedClaim resolved(
            Optional<String> roleId,
            Set<String> desiredDiscordUsers,
            boolean delete
    ) {
        ManagedRoleShadowService.Claim claim = new ManagedRoleShadowService.Claim(
                "managed-role:test",
                "resource",
                "playtime-numerals",
                "tier:xii",
                PLAYTIME_ROLE_NAME,
                roleId,
                Set.of(),
                delete
        );
        return new ManagedRoleShadowService.ResolvedClaim(claim, desiredDiscordUsers, 0);
    }

    private static Guild guild(List<Role> roles) {
        return (Guild) Proxy.newProxyInstance(
                PROXY_CLASS_LOADER,
                new Class<?>[] {Guild.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRoles" -> roles;
                    case "getRoleById" -> roles.stream()
                            .filter(role -> role.getId().equals(args[0]))
                            .findFirst()
                            .orElse(null);
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Role role(String id, String name) {
        return (Role) Proxy.newProxyInstance(
                PROXY_CLASS_LOADER,
                new Class<?>[] {Role.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> name;
                    case "isPublicRole", "isManaged" -> false;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Member member(String id, List<Role> roles) {
        return (Member) Proxy.newProxyInstance(
                PROXY_CLASS_LOADER,
                new Class<?>[] {Member.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getRoles" -> roles;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        Object value = PRIMITIVE_DEFAULTS.get(type);
        if (value == null) {
            throw new IllegalArgumentException("Unsupported primitive type: " + type);
        }
        return value;
    }
}
