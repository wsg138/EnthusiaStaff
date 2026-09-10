package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class InspectActionSectionTest {
    private static final UUID PLAYER_ID = UUID.fromString("52000000-0000-0000-0000-000000000001");

    @Test
    void rendersExactUuidCommandsForEveryAuthorizedAction() {
        Component line = InspectActionSection.render(
                PLAYER_ID,
                new InspectActionSection.Access(true, true, true, true)
        ).getFirst();

        assertEquals(
                List.of(
                        "/history " + PLAYER_ID,
                        "/client " + PLAYER_ID,
                        "/inspect inventory " + PLAYER_ID,
                        "/inspect ender " + PLAYER_ID,
                        "/punish " + PLAYER_ID
                ),
                clickCommands(line)
        );
        assertEquals("Actions: [History] [Client] [Inventory] [Ender] [Punish]", plain(line));
    }

    @Test
    void hidesActionsThatTheViewerCannotUse() {
        Component line = InspectActionSection.render(
                PLAYER_ID,
                new InspectActionSection.Access(true, false, false, false)
        ).getFirst();

        assertEquals(List.of("/history " + PLAYER_ID), clickCommands(line));
        assertEquals("Actions: [History]", plain(line));
    }

    @Test
    void omitsTheSectionWhenNoActionsAreAuthorized() {
        assertTrue(InspectActionSection.render(
                PLAYER_ID,
                new InspectActionSection.Access(false, false, false, false)
        ).isEmpty());
    }

    @Test
    void derivesVisibilityFromTheDestinationCommandPermissions() {
        Set<String> permissions = Set.of(
                "enthusiastaff.history.view",
                "enthusiastaff.inventory.view",
                "enthusiastaff.punish",
                "enthusiastaff.punish.configured"
        );

        InspectActionSection.Access access = InspectActionSection.access(permissions::contains, true);

        assertTrue(access.history());
        assertTrue(access.inventory());
        assertTrue(access.punishment());
        assertFalse(access.clientEvidence());
    }

    @Test
    void hidesPunishmentWhenRankAuthorityIsMissing() {
        Set<String> permissions = Set.of(
                "enthusiastaff.punish",
                "enthusiastaff.punish.configured"
        );

        InspectActionSection.Access access = InspectActionSection.access(permissions::contains, false);

        assertFalse(access.punishment());
    }

    private static List<String> clickCommands(Component component) {
        List<String> commands = new ArrayList<>();
        collect(component, commands);
        return commands;
    }

    private static void collect(Component component, List<String> commands) {
        ClickEvent event = component.clickEvent();
        if (event != null) {
            assertEquals(ClickEvent.Action.RUN_COMMAND, event.action());
            commands.add(((ClickEvent.Payload.Text) event.payload()).value());
        }
        component.children().forEach(child -> collect(child, commands));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
