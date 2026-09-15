from pathlib import Path
import sys

source = Path(sys.argv[1])
target = Path(sys.argv[2])
lines = source.read_text(encoding="utf-8").splitlines(keepends=True)


def drop_replace_call(marker: str) -> None:
    matches = [index for index, line in enumerate(lines) if marker in line]
    if not matches:
        raise SystemExit(f"no transformation line contains marker {marker!r}")
    marker_index = matches[0]
    start = marker_index
    while start >= 0 and "replace_once(" not in lines[start]:
        start -= 1
    if start < 0:
        raise SystemExit(f"unable to find replace_once start for {marker!r}")
    end = marker_index
    while end < len(lines) and lines[end].strip() != ")":
        end += 1
    if end >= len(lines):
        raise SystemExit(f"unable to find replace_once end for {marker!r}")
    del lines[start:end + 1]


drop_replace_call('UUID caseId,\\n            ModerationSubjectId subjectId')
drop_replace_call('m.captured_at, e.investigation_case_id, e.last_observed_at')
drop_replace_call('observation.issuerId()')
text = "".join(lines)

post_lines = []


def emit(line=""):
    post_lines.append(line)


def emit_replace(path_expr, old, new, label):
    emit(f"content = read({path_expr})")
    emit(f"old = {old!r}")
    emit(f"new = {new!r}")
    emit(f"if content.count(old) != 1: raise RuntimeError({label!r} + f': found {{content.count(old)}}')")
    emit(f"write({path_expr}, content.replace(old, new, 1))")
    emit()

emit("# Complete exact source-shape repairs after the base transformation.")
emit("worker = 'staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordInvestigationWorker.java'")
emit_replace(
    "worker",
    "                observation.issuerId(),\n                observation.summary(),",
    "                observation.issuer(),\n                observation.consequenceType(),\n                observation.summary(),",
    "worker provenance mapping expected once",
)

emit("evidence_store = 'persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordInvestigationEvidenceStore.java'")
emit_replace(
    "evidence_store",
    "            UUID caseId,\n            ModerationSubjectId subjectId\n    ) throws SQLException {",
    "            CaseId caseId,\n            ModerationSubjectId subjectId\n    ) throws SQLException {",
    "evidence open-case signature expected once",
)
emit_replace(
    "evidence_store",
    'UuidBytes.fromBytes(rows.getBytes("investigation_case_id"))',
    'new CaseId(rows.getString("case_id"))',
    "evidence legacy case read expected once",
)

emit("note_store = 'persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordInvestigationNoteStore.java'")
emit_replace(
    "note_store",
    "import javax.sql.DataSource;\nimport net.enthusia.staff.domain.investigation.InvestigationNote;",
    "import javax.sql.DataSource;\nimport net.enthusia.staff.common.CaseId;\nimport net.enthusia.staff.domain.investigation.InvestigationNote;",
    "note CaseId import expected once",
)
emit_replace(
    "note_store",
    "        UUID caseId;\n",
    "        CaseId caseId;\n",
    "case-scoped note identifier declaration expected once",
)
emit_replace(
    "note_store",
    "            caseId = UUID.fromString(scope.value());",
    "            caseId = new CaseId(scope.value());",
    "case-scoped note parser expected once",
)
emit_replace(
    "note_store",
    "                UPDATE discord_investigation_cases\n",
    "                UPDATE discord_investigation_cases i\n                JOIN cases c ON c.case_id = i.case_id\n",
    "case-scoped note update expected once",
)
emit_replace(
    "note_store",
    "                SET last_activity_at = GREATEST(last_activity_at, ?), revision = revision + 1\n                WHERE case_id = ? AND state = 'OPEN'",
    "                SET i.last_activity_at = GREATEST(i.last_activity_at, ?), i.revision = i.revision + 1\n                WHERE i.case_id = ? AND i.closed_at IS NULL AND c.state = 'OPEN'",
    "case-scoped note state predicate expected once",
)
emit_replace(
    "note_store",
    "            statement.setBytes(2, UuidBytes.toBytes(caseId));",
    "            statement.setString(2, caseId.value());",
    "case-scoped note binding expected once",
)

text += "\n" + "\n".join(post_lines) + "\n"
target.write_text(text, encoding="utf-8")
