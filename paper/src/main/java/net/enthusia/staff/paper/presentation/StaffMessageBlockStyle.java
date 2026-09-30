package net.enthusia.staff.paper.presentation;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/** Adds hierarchy to known multi-line Staff command responses without changing their semantic text. */
public final class StaffMessageBlockStyle {
    private static final String PIPE = " | ";

    private StaffMessageBlockStyle() {
    }

    public static List<Component> style(List<Component> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        String first = plain(messages.getFirst());
        if (first != null && first.startsWith("Case ") && first.contains(" | subject ")) {
            return styleCase(messages);
        }
        return messages.stream().map(StaffMessageStyle::style).toList();
    }

    private static List<Component> styleCase(List<Component> messages) {
        List<Component> styled = new ArrayList<>(messages.size() + 1);
        styled.add(StaffMessageStyle.header("EnthusiaStaff • Case"));
        styled.add(caseHeader(plain(messages.getFirst())));
        for (int index = 1; index < messages.size(); index++) {
            Component original = messages.get(index);
            String text = plain(original);
            styled.add(text == null ? original : caseLine(text));
        }
        return List.copyOf(styled);
    }

    private static Component caseHeader(String line) {
        String[] parts = line.split(" \\| ", -1);
        String caseId = parts[0].substring("Case ".length());
        String subject = parts.length > 1 && parts[1].startsWith("subject ")
                ? parts[1].substring("subject ".length())
                : "unknown";
        Component result = Component.text("  Case  ", NamedTextColor.GRAY)
                .append(StaffMessageStyle.id(caseId))
                .append(Component.text("  Subject  ", NamedTextColor.GRAY))
                .append(StaffMessageStyle.id(subject));
        for (int index = 2; index < parts.length; index++) {
            result = result.append(Component.text(" • " + parts[index], NamedTextColor.GRAY));
        }
        return result;
    }

    private static Component caseLine(String text) {
        if (text.equals("Sanctions:") || text.equals("Timeline:")) {
            return StaffMessageStyle.section(text.substring(0, text.length() - 1));
        }
        if (text.startsWith("Policy snapshot:")) {
            return Component.text("  Policy  ", NamedTextColor.GRAY)
                    .append(Component.text(text.substring("Policy snapshot:".length()).trim(), NamedTextColor.DARK_GRAY));
        }
        if (text.startsWith("Actor:")) {
            return Component.text("  Actor  ", NamedTextColor.GRAY)
                    .append(Component.text(text.substring("Actor:".length()).trim(), NamedTextColor.AQUA));
        }
        if (text.startsWith("Internal explanation:")) {
            return Component.text("  Internal  ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(text.substring("Internal explanation:".length()).trim(), NamedTextColor.GRAY));
        }
        if (text.startsWith("Created ")) {
            return accentPipes(text, NamedTextColor.GRAY);
        }
        if (text.startsWith("- ")) {
            return Component.text("  • ", NamedTextColor.DARK_GRAY)
                    .append(accentPipes(text.substring(2), NamedTextColor.GRAY));
        }
        return StaffMessageStyle.style(text);
    }

    private static Component accentPipes(String text, NamedTextColor color) {
        String[] parts = text.split(" \\| ", -1);
        Component line = Component.text(parts[0], color);
        for (int index = 1; index < parts.length; index++) {
            line = line.append(Component.text(PIPE, NamedTextColor.DARK_GRAY))
                    .append(Component.text(parts[index], color));
        }
        return line;
    }

    private static String plain(Component component) {
        if (!(component instanceof TextComponent text)
                || !component.children().isEmpty()
                || !component.style().isEmpty()) {
            return null;
        }
        return text.content();
    }
}
