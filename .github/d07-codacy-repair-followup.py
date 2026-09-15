from pathlib import Path

combined_path = Path(
    "staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordPunishmentWorkerTestSupport.java"
)
source = combined_path.read_text(encoding="utf-8")
gateway_name = "final class DiscordPunishmentWorkerFakeGateway implements DiscordPunishmentGateway"
repository_name = "final class DiscordPunishmentWorkerFakeRepository implements DiscordPunishmentRepository"
gateway_start = source.index(gateway_name)
repository_start = source.index(repository_name)
header = source[:gateway_start]
gateway = source[gateway_start:repository_start].rstrip() + "\n"
repository = source[repository_start:]
repository = repository.replace(
    "final class DiscordPunishmentWorkerFakeRepository implements DiscordPunishmentRepository {\n",
    "final class DiscordPunishmentWorkerFakeRepository implements DiscordPunishmentRepository {\n"
    "    private static final Instant TEST_NOW = Instant.parse(\"2026-09-14T20:00:00Z\");\n",
    1,
)
repository = repository.replace(
    "new LeaseSeed(next.id(), next.type(), NOW, next.attempt())",
    "new LeaseSeed(next.id(), next.type(), TEST_NOW, next.attempt())",
    1,
)
base = combined_path.parent
(base / "DiscordPunishmentWorkerFakeGateway.java").write_text(header + gateway, encoding="utf-8")
(base / "DiscordPunishmentWorkerFakeRepository.java").write_text(header + repository, encoding="utf-8")
combined_path.unlink()
