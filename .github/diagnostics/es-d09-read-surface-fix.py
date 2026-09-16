from pathlib import Path
import sys

source = Path(sys.argv[1])
target = Path(sys.argv[2])
text = source.read_text(encoding="utf-8")


def raw_replacement_block(start_marker: str, next_marker: str) -> None:
    global text
    start = text.find(start_marker)
    if start < 0:
        raise RuntimeError(f"missing renderer block start: {start_marker}")
    end = text.find(next_marker, start)
    if end < 0:
        raise RuntimeError(f"missing renderer block end: {next_marker}")
    block = text[start:end]
    parts = block.split("'''")
    if len(parts) != 5:
        raise RuntimeError(f"expected two triple-quoted arguments, found {len(parts) - 1} delimiters")
    rebuilt = parts[0] + "r'''" + parts[1] + "'''" + parts[2] + "r'''" + parts[3] + "'''" + parts[4]
    text = text[:start] + rebuilt + text[end:]


raw_replacement_block(
    "replace_once(renderer, '''    static String notes",
    "replace_once(renderer, '''    private static void appendNotes",
)
raw_replacement_block(
    "replace_once(renderer, '''    private static void appendNotes",
    "# Minecraft-only Paper views",
)
target.write_text(text, encoding="utf-8")
