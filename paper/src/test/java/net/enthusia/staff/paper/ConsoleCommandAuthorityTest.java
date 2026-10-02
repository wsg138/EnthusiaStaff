package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.bukkit.permissions.Permission;
import org.junit.jupiter.api.Test;

class ConsoleCommandAuthorityTest {

    @Test
    void grantsOnlyDeclaredEnthusiaStaffPermissionNames() {
        assertEquals(
                List.of("enthusiastaff.freeze", "enthusiastaff.punish"),
                ConsoleCommandAuthority.permissionNames(List.of(
                        new Permission("enthusiastaff.punish"),
                        new Permission("otherplugin.admin"),
                        new Permission("enthusiastaff.freeze"),
                        new Permission("enthusiastaff.freeze")
                ))
        );
    }
}
