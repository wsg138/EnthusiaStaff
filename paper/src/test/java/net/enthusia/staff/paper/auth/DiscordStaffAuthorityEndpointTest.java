package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DiscordStaffAuthorityEndpointTest {
    @Test
    void roleEligibilityNormalizesValidGroupsAndSkipsOutOfContractNames() {
        assertEquals(Optional.of("helper.team"), DiscordStaffAuthorityEndpoint.normalizedRoleGroup(" Helper.Team "));
        assertTrue(DiscordStaffAuthorityEndpoint.normalizedRoleGroup("staff role").isEmpty());
        assertTrue(DiscordStaffAuthorityEndpoint.normalizedRoleGroup("x".repeat(65)).isEmpty());
        assertTrue(DiscordStaffAuthorityEndpoint.normalizedRoleGroup(null).isEmpty());
    }
}
