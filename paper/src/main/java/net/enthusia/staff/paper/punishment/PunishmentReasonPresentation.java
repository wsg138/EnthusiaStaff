package net.enthusia.staff.paper.punishment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

final class PunishmentReasonPresentation {
    private PunishmentReasonPresentation() {
    }

    static String name(Optional<ReasonPolicyRepository.ReasonDescriptor> descriptor) {
        return descriptor.map(ReasonPolicyRepository.ReasonDescriptor::publicReason)
                .orElse("Unknown reason");
    }

    static List<Component> lore(
            String reasonId,
            Optional<ReasonPolicyRepository.ReasonDescriptor> descriptor
    ) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(reasonId, NamedTextColor.DARK_GRAY));
        if (descriptor.isEmpty()) {
            addUnknown(lore);
        } else {
            addKnown(lore, descriptor.orElseThrow());
        }
        return List.copyOf(lore);
    }

    private static void addUnknown(List<Component> lore) {
        lore.add(Component.text("No current policy metadata exists for this ID", NamedTextColor.RED));
        lore.add(Component.text("Confirmation will fail safely", NamedTextColor.YELLOW));
    }

    private static void addKnown(
            List<Component> lore,
            ReasonPolicyRepository.ReasonDescriptor descriptor
    ) {
        switch (descriptor.availability()) {
            case ACTIVE -> addActive(lore);
            case ALIAS -> addAlias(lore, descriptor.canonicalId());
            case REMOVED -> addRemoved(lore);
        }
    }

    private static void addActive(List<Component> lore) {
        lore.add(Component.text("This exact reason ID will be audited", NamedTextColor.GRAY));
    }

    private static void addAlias(List<Component> lore, String canonicalId) {
        lore.add(Component.text("Renamed to " + canonicalId, NamedTextColor.AQUA));
        lore.add(Component.text("Confirmation uses the current canonical policy", NamedTextColor.GRAY));
    }

    private static void addRemoved(List<Component> lore) {
        lore.add(Component.text("Removed from the active policy catalog", NamedTextColor.RED));
        lore.add(Component.text("Readable for history; cannot be selected or confirmed", NamedTextColor.YELLOW));
    }
}
