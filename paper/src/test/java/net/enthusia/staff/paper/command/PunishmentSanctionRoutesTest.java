package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PunishmentSanctionRoutesTest {
    private static final String WARN_ONE = "00000000-0000-0000-0000-000000000101";
    private static final String WARN_TWO = "00000000-0000-0000-0000-000000000102";

    @Test
    void unpunishAndRemoveSelectOneExactWarningNotLatestPlayerPunishment() {
        assertArrayEquals(
                new String[]{"sanction", "revoke", WARN_ONE, "Incorrect warning"},
                PunishmentSanctionRoutes.rewrite("unpunish", new String[]{WARN_ONE, "Incorrect warning"})
        );
        assertArrayEquals(
                new String[]{"sanction", "revoke", WARN_TWO, "Wrong", "context"},
                PunishmentSanctionRoutes.rewrite("punish",
                        new String[]{"remove", WARN_TWO, "Wrong", "context"})
        );
    }

    @Test
    void reduceChangeAndOtherCommandsDelegateToExistingSanctionLifecycle() {
        assertArrayEquals(
                new String[]{"sanction", "reduce", WARN_ONE, "2d", "Review"},
                PunishmentSanctionRoutes.rewrite("punish",
                        new String[]{"reduce", WARN_ONE, "2d", "Review"})
        );
        assertArrayEquals(
                new String[]{"sanction", "reduce", WARN_ONE, "4h", "Shorter"},
                PunishmentSanctionRoutes.rewrite("punish",
                        new String[]{"change", WARN_ONE, "4h", "Shorter"})
        );
        assertArrayEquals(
                new String[]{"sanction", "end", WARN_ONE, "No longer needed"},
                PunishmentSanctionRoutes.rewrite("punish",
                        new String[]{"change", WARN_ONE, "end", "No longer needed"})
        );
        assertArrayEquals(
                new String[]{"sanction", "revoke", WARN_ONE, "Misapplied"},
                PunishmentSanctionRoutes.rewrite("punish",
                        new String[]{"change", WARN_ONE, "remove", "Misapplied"})
        );
        assertArrayEquals(
                new String[]{"sanction", "overturn", WARN_ONE, "Appeal", "accepted"},
                PunishmentSanctionRoutes.rewrite("punish",
                        new String[]{"change", WARN_ONE, "overturn", "Appeal", "accepted"})
        );
    }

    @Test
    void ambiguousPlayerCaseAndMissingSanctionIdsCannotChooseLatestAutomatically() {
        assertNull(PunishmentSanctionRoutes.rewrite("unpunish", new String[]{"TestPlayer", "oops"}));
        assertNull(PunishmentSanctionRoutes.rewrite("punish", new String[]{"remove", "CASE001", "oops"}));
        assertNull(PunishmentSanctionRoutes.rewrite("punish", new String[]{"remove", WARN_ONE}));
        assertNull(PunishmentSanctionRoutes.rewrite("punish", new String[]{"change", WARN_ONE}));
        assertNull(PunishmentSanctionRoutes.rewrite("unpunish", new String[0]));
        assertFalse(PunishmentSanctionRoutes.handles("warn", new String[]{"remove", WARN_ONE}));
        assertFalse(PunishmentSanctionRoutes.handles("punish", new String[]{"PlayerName"}));
        assertTrue(PunishmentSanctionRoutes.usage().contains("/unpunish <player>"));
    }

    @Test
    void legacyPlayerShortcutsCannotImplicitlyTargetTheLatestCase() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/command/SanctionChangeCommand.java"
        )).replace("\r\n", "\n");
        int route = source.indexOf("String route = CommandRoute.canonicalName(command);");
        int multi = source.indexOf("sender instanceof Player && !central && arguments.length > SINGLE_ARGUMENT", route);
        int gui = source.indexOf("if (openAliasGui(sender, arguments, route, central))", route);
        assertTrue(route >= 0 && multi > route && gui > multi,
                "Player legacy text commands must reject ambiguous multi-argument writes before dispatch");
        assertTrue(source.contains("picker.open(player, arguments[0], selection,"));
    }

    @Test
    void caseDetailSuggestsSpecificUnpunishIdButNeverRunsItAutomatically() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/command/CaseCommand.java"
        )).replace("\r\n", "\n");
        assertTrue(source.contains("ClickEvent.suggestCommand("));
        assertTrue(source.contains("\"/unpunish \" + sanction.sanctionId()"));
        assertFalse(source.contains("ClickEvent.runCommand("));
    }

    @Test
    void inGameCommandsSelectPunishmentsWithGuiAndNoTypedSanctionId() {
        assertTrue(PunishmentSanctionRoutes.isPickerRequest("unpunish", new String[]{"Notch"}));
        assertTrue(PunishmentSanctionRoutes.isPickerRequest("punish", new String[]{"remove", "Notch"}));
        assertTrue(PunishmentSanctionRoutes.isPickerRequest("punish", new String[]{"reduce", "Notch"}));
        assertTrue(PunishmentSanctionRoutes.isPickerRequest("punish", new String[]{"change", "Notch"}));
        assertTrue(PunishmentSanctionRoutes.isPickerRequest("punish", new String[]{"end", "Notch"}));
        assertTrue(PunishmentSanctionRoutes.isPickerRequest("punish", new String[]{"overturn", "Notch"}));
        assertFalse(PunishmentSanctionRoutes.isPickerRequest("punish", new String[]{"Notch"}));
        assertFalse(PunishmentSanctionRoutes.isPickerRequest("warn", new String[]{"Notch"}));
        assertFalse(PunishmentSanctionRoutes.isPickerRequest("unpunish",
                new String[]{WARN_ONE, "Wrong warning"}));
        assertTrue(PunishmentSanctionRoutes.pickerTarget("punish",
                new String[]{"remove", "Notch"}).equals("Notch"));
        assertTrue(PunishmentSanctionRoutes.pickerAction("unpunish", new String[]{"Notch"})
                .equals("remove"));
        assertTrue(PunishmentSanctionRoutes.pickerAction("punish",
                new String[]{"change", "Notch"}).equals("change"));
    }

    @Test
    void menuActionsRequireBothLegacyAndExactWritePermissions() {
        var action = net.enthusia.staff.domain.sanction.SanctionChangeAction.REVOKE;
        var ui = net.enthusia.staff.paper.sanction.SanctionChangeAccess.permissionFor(action);
        var write = SanctionLifecycleCommand.REVOKE_PERMISSION;
        assertFalse(ExactSanctionPickerGui.hasActionPermissions(
                value -> value.equals(ui), action));
        assertFalse(ExactSanctionPickerGui.hasActionPermissions(
                value -> value.equals(write), action));
        assertTrue(ExactSanctionPickerGui.hasActionPermissions(
                java.util.Set.of(ui, write)::contains, action));
        for (var permittedAction : java.util.List.of(
                net.enthusia.staff.domain.sanction.SanctionChangeAction.REVOKE,
                net.enthusia.staff.domain.sanction.SanctionChangeAction.END_EARLY,
                net.enthusia.staff.domain.sanction.SanctionChangeAction.REDUCE_DURATION,
                net.enthusia.staff.domain.sanction.SanctionChangeAction.FULL_OVERTURN
        )) {
            assertFalse(ExactSanctionPickerGui.hasActionPermissions(
                    value -> false, permittedAction));
        }
    }

    @Test
    void inventorySlotSelectsOnlyTheClickedWarningIncludingAcrossPages() {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        for (int i = 0; i < 48; i++) {
            warnings.add("warning-" + i);
        }
        assertTrue(ExactSanctionPickerGui.pageEntry(warnings, 0, 0)
                .orElseThrow().equals("warning-0"));
        assertTrue(ExactSanctionPickerGui.pageEntry(warnings, 0, 1)
                .orElseThrow().equals("warning-1"));
        assertTrue(ExactSanctionPickerGui.pageEntry(warnings, 1, 0)
                .orElseThrow().equals("warning-45"));
        assertTrue(ExactSanctionPickerGui.pageEntry(warnings, 1, 2)
                .orElseThrow().equals("warning-47"));
        assertTrue(ExactSanctionPickerGui.pageEntry(warnings, 1, 3).isEmpty());
        assertTrue(ExactSanctionPickerGui.pageEntry(warnings, 0, 49).isEmpty());
        assertTrue(ExactSanctionPickerGui.pageEntry(warnings, -1, 0).isEmpty());
    }

    @Test
    void legacyUnwarnListsWarningsOnlyAndExcludesTerminalSanctions() {
        var now = java.time.Instant.parse("2026-10-10T12:00:00Z");
        var warning = new net.enthusia.staff.domain.casefile.SanctionReview(
                java.util.UUID.randomUUID(),
                net.enthusia.staff.domain.sanction.SanctionType.WARNING,
                net.enthusia.staff.domain.sanction.SanctionStatus.APPLIED,
                now, java.util.Optional.empty(), java.util.Optional.empty(), 0);
        var ban = new net.enthusia.staff.domain.casefile.SanctionReview(
                java.util.UUID.randomUUID(),
                net.enthusia.staff.domain.sanction.SanctionType.BAN,
                net.enthusia.staff.domain.sanction.SanctionStatus.ACTIVE,
                now, java.util.Optional.empty(), java.util.Optional.empty(), 0);
        var revokedWarning = new net.enthusia.staff.domain.casefile.SanctionReview(
                java.util.UUID.randomUUID(),
                net.enthusia.staff.domain.sanction.SanctionType.WARNING,
                net.enthusia.staff.domain.sanction.SanctionStatus.REVOKED,
                now, java.util.Optional.empty(), java.util.Optional.empty(), 1);
        var warnings = net.enthusia.staff.paper.sanction.SanctionChangeAccess.aliasTypes("unwarn");
        assertTrue(ExactSanctionPickerGui.selectable(warning, warnings));
        assertFalse(ExactSanctionPickerGui.selectable(ban, warnings));
        assertFalse(ExactSanctionPickerGui.selectable(revokedWarning, warnings));
        assertTrue(ExactSanctionPickerGui.selectable(ban,
                net.enthusia.staff.paper.sanction.SanctionChangeAccess.aliasTypes("unban")));
        assertFalse(ExactSanctionPickerGui.selectable(warning,
                net.enthusia.staff.paper.sanction.SanctionChangeAccess.aliasTypes("unmute")));
    }

    @Test
    void pickerUsesOnePersistedSanctionAndAuditConfirmation() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/command/ExactSanctionPickerGui.java"
        )).replace("\r\n", "\n");
        assertTrue(source.contains("for (SanctionReview sanction : review.sanctions())"));
        assertTrue(source.contains("new Entry(review, sanction)"));
        assertTrue(source.contains("new Selection(state, chosen, action(state.action))"));
        assertTrue(source.contains("service.exactRevision(state.selection.entry.sanction.sanctionId())"));
        assertTrue(source.contains("state.selection.entry.sanction.revision()"));
        assertTrue(source.contains("lifecycle.executeSelected(viewer, \"punish\", args,"));
        assertTrue(source.contains("if (!submitting.add(viewer.getUniqueId()))"));
        assertTrue(source.contains("new Confirmation(capture.selection, capture.expiration, value)"));
    }

    @Test
    void NewRoutesAreRegisteredAndAuthorityGated() throws IOException {
        Path base = Path.of("src/main/java/net/enthusia/staff/paper");
        String registrar = Files.readString(base.resolve("PaperCommandRegistrar.java"))
                .replace("\r\n", "\n");
        String command = Files.readString(base.resolve("command/PunishmentCommand.java"))
                .replace("\r\n", "\n");
        String gate = Files.readString(base.resolve("staff/StaffDutyCommandGate.java"))
                .replace("\r\n", "\n");
        assertTrue(registrar.contains("List.of(\"punish\", \"unpunish\""));
        assertTrue(registrar.contains("command.configureSanctionLifecycle(sanctionLifecycle)"));
        assertTrue(registrar.contains("command.configureExactSanctionPicker(exactPicker)"));
        assertTrue(command.contains("PunishmentSanctionRoutes.isPickerRequest(route, args)"));
        assertTrue(command.contains("exactSanctionPicker.open(player,"));
        assertTrue(command.indexOf("PunishmentSanctionRoutes.handles(route, args)")
                < command.indexOf("requireDraftPermission(sender, actor)"));
        assertTrue(command.contains("sanctionLifecycle.execute(sender, label, routed)"));
        assertTrue(gate.contains("\"punish\", \"unpunish\""));
    }
}
