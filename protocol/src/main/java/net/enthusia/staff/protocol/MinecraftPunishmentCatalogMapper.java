package net.enthusia.staff.protocol;

import java.util.List;
import net.enthusia.staff.domain.application.PunishmentReasonOption;
import net.enthusia.staff.domain.auth.Actor;

/** Conversion between D08 Minecraft reason catalog wire and domain values. */
public final class MinecraftPunishmentCatalogMapper {
    private MinecraftPunishmentCatalogMapper() {
    }

    public static MinecraftPunishmentCatalogWire.Request request(Actor actor) {
        if (actor == null) {
            throw new IllegalArgumentException("catalog actor must be present");
        }
        return new MinecraftPunishmentCatalogWire.Request(
                MinecraftPunishmentCatalogWire.VERSION, actor.id(), actor.displayName());
    }

    public static MinecraftPunishmentCatalogWire.Response response(List<PunishmentReasonOption> reasons) {
        if (reasons == null) {
            throw new IllegalArgumentException("catalog reasons must be present");
        }
        return MinecraftPunishmentCatalogWire.Response.available(
                reasons.stream().map(MinecraftPunishmentCatalogMapper::wire).toList());
    }

    public static List<PunishmentReasonOption> reasons(MinecraftPunishmentCatalogWire.Response response) {
        if (response == null) {
            throw new IllegalArgumentException("catalog response must be present");
        }
        if (response.outcome() == MinecraftPunishmentCatalogWire.Outcome.REJECTED) {
            throw new IllegalArgumentException(response.code() + ": " + response.message());
        }
        return response.reasons().stream()
                .map(reason -> new PunishmentReasonOption(reason.id(), reason.family(), reason.label()))
                .toList();
    }

    private static MinecraftPunishmentCatalogWire.Reason wire(PunishmentReasonOption reason) {
        return new MinecraftPunishmentCatalogWire.Reason(reason.id(), reason.family(), reason.label());
    }
}
