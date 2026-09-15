from pathlib import Path
import sys

source = Path(sys.argv[1])
target = Path(sys.argv[2])
text = source.read_text(encoding="utf-8")
blocks = [
    '''replace_once(evidence_store,
             "            UUID caseId,\\n            ModerationSubjectId subjectId",
             "            CaseId caseId,\\n            ModerationSubjectId subjectId")
''',
    '''replace_once(evidence_store,
             "                       m.captured_at, e.investigation_case_id, e.last_observed_at, e.revision",
             "                       m.captured_at, e.case_id, e.last_observed_at, e.revision")
''',
    '''replace_once(worker,
             "                    observation.issuerId(),\\n                    observation.summary(),",
             "                    observation.issuer(),\\n                    observation.consequenceType(),\\n                    observation.summary(),")
''',
]
for block in blocks:
    count = text.count(block)
    if count != 1:
        raise SystemExit(f"expected one diagnostic replacement block, found {count}: {block[:80]!r}")
    text = text.replace(block, "", 1)

text += '''
# Source indentation differs from the draft transform; apply the worker mapping explicitly.
worker = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordInvestigationWorker.java"
content = read(worker)
old = "                observation.issuerId(),\\n                observation.summary(),"
new = "                observation.issuer(),\\n                observation.consequenceType(),\\n                observation.summary(),"
if content.count(old) != 1:
    raise RuntimeError(f"worker provenance mapping expected once, found {content.count(old)}")
write(worker, content.replace(old, new, 1))

# Complete the canonical CaseId conversion in evidence reads/guards.
evidence_store = "persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordInvestigationEvidenceStore.java"
content = read(evidence_store)
old = "            UUID caseId,\\n            ModerationSubjectId subjectId\\n    ) throws SQLException {"
new = "            CaseId caseId,\\n            ModerationSubjectId subjectId\\n    ) throws SQLException {"
if content.count(old) != 1:
    raise RuntimeError(f"evidence open-case signature expected once, found {content.count(old)}")
content = content.replace(old, new, 1)
old = 'UuidBytes.fromBytes(rows.getBytes("investigation_case_id"))'
new = 'new CaseId(rows.getString("case_id"))'
if content.count(old) != 1:
    raise RuntimeError(f"evidence legacy case read expected once, found {content.count(old)}")
content = content.replace(old, new, 1)
write(evidence_store, content)

# Case-scoped notes now target canonical CaseId and gate against canonical case state.
note_store = "persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordInvestigationNoteStore.java"
content = read(note_store)
old = "import javax.sql.DataSource;\\nimport net.enthusia.staff.domain.investigation.InvestigationNote;"
new = "import javax.sql.DataSource;\\nimport net.enthusia.staff.common.CaseId;\\nimport net.enthusia.staff.domain.investigation.InvestigationNote;"
if content.count(old) != 1:
    raise RuntimeError("note CaseId import anchor changed")
content = content.replace(old, new, 1)
old = '''        UUID caseId;
        try {
            caseId = UUID.fromString(scope.value());
        } catch (IllegalArgumentException exception) {
            throw new SQLException("case-scoped note has an invalid case identifier", exception);
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_investigation_cases
                SET last_activity_at = GREATEST(last_activity_at, ?), revision = revision + 1
                WHERE case_id = ? AND state = 'OPEN'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(caseId));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "case-scoped note case is not open");
        }
'''
new = '''        CaseId caseId;
        try {
            caseId = new CaseId(scope.value());
        } catch (IllegalArgumentException exception) {
            throw new SQLException("case-scoped note has an invalid case identifier", exception);
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_investigation_cases i
                JOIN cases c ON c.case_id = i.case_id
                SET i.last_activity_at = GREATEST(i.last_activity_at, ?), i.revision = i.revision + 1
                WHERE i.case_id = ? AND i.closed_at IS NULL AND c.state = 'OPEN'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setString(2, caseId.value());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "case-scoped note case is not open");
        }
'''
if content.count(old) != 1:
    raise RuntimeError("case-scoped note lifecycle block changed")
content = content.replace(old, new, 1)
write(note_store, content)
'''

target.write_text(text, encoding="utf-8")
