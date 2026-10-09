package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PunishmentConfirmationTargetTest {
    private static final UUID DRAFT = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void playerNameResolvesToTheStoredDraftIdentifier() {
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId("TargetPlayer", id -> false, input -> {
            assertEquals("TargetPlayer", input);
            return Optional.of(DRAFT);
        }));
    }

    @Test
    void bedrockNamesKeepTheirPrefixWhenResolved() {
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId(".BedrockPlayer", id -> false, input -> {
            assertEquals(".BedrockPlayer", input);
            return Optional.of(DRAFT);
        }));
    }

    @Test
    void existingDraftIdDoesNotGetInterpretedAsAPlayer() {
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId(DRAFT.toString(), id -> id.equals(DRAFT), input -> {
            fail("Draft IDs must retain their exact existing confirmation path");
            return Optional.empty();
        }));
    }

    @Test
    void missingDraftCannotProduceAConfirmationIdentifier() {
        assertTrue(PunishmentCommand.confirmationDraftId("UnknownPlayer", id -> false, input -> Optional.empty()).isEmpty());
    }
    @Test
    void playerUuidWithoutAnActorDraftResolvesItsTargetBoundDraft() {
        UUID player = UUID.fromString("30000000-0000-0000-0000-000000000001");
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId(player.toString(),
                id -> false, input -> {
                    assertEquals(player.toString(), input);
                    return Optional.of(DRAFT);
                }));
    }

    @Test
    void looseUuidLikeNameIsNotCoercedIntoADraftId() {
        assertEquals(Optional.of(DRAFT), PunishmentCommand.confirmationDraftId("1-1-1-1-1",
                id -> { fail("Only canonical UUIDs can be draft identifiers"); return false; },
                input -> { assertEquals("1-1-1-1-1", input); return Optional.of(DRAFT); }));
    }

    @Test
    void unknownOrForeignDraftUuidCannotConfirmWithoutATargetDraft() {
        assertTrue(PunishmentCommand.confirmationDraftId(DRAFT.toString(), id -> false,
                input -> Optional.empty()).isEmpty());
    }
    @Test
    void confirmationHintRejectsBlankAmbiguousAndDifferentTargetNames() {
        UUID player = UUID.randomUUID();
        var named = identity(player, "SharedName");
        assertEquals(DRAFT.toString(), PunishmentCommand.confirmationTarget(named, DRAFT, input -> Optional.empty()));
        assertEquals(DRAFT.toString(), PunishmentCommand.confirmationTarget(named, DRAFT,
                input -> Optional.of(identity(UUID.randomUUID(), "SharedName"))));
        assertEquals(DRAFT.toString(), PunishmentCommand.confirmationTarget(identity(player, " "), DRAFT,
                input -> { fail("blank names must not be resolved"); return Optional.empty(); }));
        assertEquals("SharedName", PunishmentCommand.confirmationTarget(named, DRAFT, input -> Optional.of(named)));
    }

    private static net.enthusia.staff.domain.player.PlayerIdentity identity(UUID id, String name) {
        return new net.enthusia.staff.domain.player.PlayerIdentity(id, Optional.of(name),
                net.enthusia.staff.domain.player.PlayerPlatform.UNKNOWN, java.time.Instant.EPOCH, java.time.Instant.EPOCH);
    }
}
