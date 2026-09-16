from pathlib import Path
import sys

root = Path(sys.argv[1])
store = root / "persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordEvasionAlertStore.java"
text = store.read_text(encoding="utf-8")
old = "import net.enthusia.staff.domain.moderation.DiscordUserId;\n"
new = old + "import net.enthusia.staff.domain.moderation.ModerationSubjectId;\n"
if text.count(old) != 1:
    raise RuntimeError(f"expected one DiscordUserId import, found {text.count(old)}")
store.write_text(text.replace(old, new, 1), encoding="utf-8")
