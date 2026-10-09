package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class StaffToolCommandRoutingTest {
    @Test
    void internalToolsCannotBeRoutedToAnotherPluginsCommand() {
        for (String command : new String[]{"inspect Target", "freeze Target investigation", "reports", "vanish", "staffchat", "staff"}) {
            assertEquals("enthusiastaff:" + command, StaffToolDispatcher.ownedCommand(command));
        }
    }
}
