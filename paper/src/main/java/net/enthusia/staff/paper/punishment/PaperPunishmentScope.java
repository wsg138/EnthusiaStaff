package net.enthusia.staff.paper.punishment;

enum PaperPunishmentScope {
    MINECRAFT("Minecraft"),
    DISCORD("Discord"),
    BOTH("Discord + Minecraft");

    private final String label;

    PaperPunishmentScope(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }
}
