package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UnrestrictedStaffAuthorityWiringTest {
    @Test
    void exactSessionAndPermanentAuthorityRemainSeparateServices() throws IOException {
        String runtime = source("net/enthusia/staff/paper/PaperRuntimeComponents.java");

        assertTrue(runtime.contains("StaffSessionService.class,\n                staffMode::authorityActive"));
        assertTrue(runtime.contains("StaffAuthorityService.class,\n                staffMode::authorityActiveOrUnrestricted"));
    }

    @Test
    void directMutationHoldoutsUseAuthorityServiceOrUnrestrictedPredicate() throws IOException {
        String freeze = source("net/enthusia/staff/paper/command/FreezeCommand.java");
        String sanction = source("net/enthusia/staff/paper/command/SanctionLifecycleCommand.java");
        String inventory = source("net/enthusia/staff/paper/inventory/InventoryEditAuthorityGate.java");
        String registrar = source("net/enthusia/staff/paper/PaperCommandRegistrar.java");

        assertTrue(freeze.contains("StaffAuthorityService.class"));
        assertTrue(sanction.contains("StaffAuthorityService.class"));
        assertTrue(inventory.contains("StaffAuthorityService.class"));
        assertTrue(registrar.contains(
                "dependencies.players().staffMode()::authorityActiveOrUnrestricted"
        ));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java").resolve(relative)).replace("\r\n", "\n");
    }
}
