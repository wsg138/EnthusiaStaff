from pathlib import Path

path = Path("staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordPunishmentWorkerTest.java")
text = path.read_text(encoding="utf-8")
old = "        FakeRepository repository = new FakeRepository(punishment(kick(), NOW));\n"
new = """        FakeRepository repository = new FakeRepository(punishment(
                intent(DiscordConsequenceType.KICK, SanctionLength.instant(), Optional.empty()),
                NOW
        ));
"""
if text.count(old) != 1:
    raise SystemExit(f"kick fixture correction expected one match, found {text.count(old)}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
