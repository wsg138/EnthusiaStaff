from pathlib import Path
import sys

source = Path(sys.argv[1])
target = Path(sys.argv[2])
text = source.read_text(encoding="utf-8")
replacements = [
    ("replace_once(renderer, '''    static String notes", "replace_once(renderer, r'''    static String notes"),
    ("''', '''    static String notes(\n            StaffModerationReadService.Snapshot snapshot,", "''', r'''    static String notes(\n            StaffModerationReadService.Snapshot snapshot,"),
    ("replace_once(renderer, '''    private static void appendNotes", "replace_once(renderer, r'''    private static void appendNotes"),
    ("''', '''    private static void appendInvestigationNotes(", "''', r'''    private static void appendInvestigationNotes("),
]
for old, new in replacements:
    if text.count(old) != 1:
        raise RuntimeError(f"expected one renderer transform marker, found {text.count(old)}: {old!r}")
    text = text.replace(old, new, 1)
target.write_text(text, encoding="utf-8")
