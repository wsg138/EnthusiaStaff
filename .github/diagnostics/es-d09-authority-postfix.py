from pathlib import Path
import sys

source = Path(sys.argv[1])
target = Path(sys.argv[2])
text = source.read_text(encoding="utf-8")
post = []


def emit(line=""):
    post.append(line)


def emit_replace(path_expr, old, new, label):
    emit(f"content = read({path_expr})")
    emit(f"old = {old!r}")
    emit(f"new = {new!r}")
    emit(f"if content.count(old) != 1: raise RuntimeError({label!r} + f': found {{content.count(old)}}')")
    emit(f"write({path_expr}, content.replace(old, new, 1))")
    emit()

# Case allocation is single-shot inside one transaction. Any operation/punishment
# uniqueness race propagates and rolls the canonical row back instead of committing
# an orphan case while replaying another transaction's extension row.
case_store = "persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordInvestigationCaseStore.java"
emit(f"case_store = {case_store!r}")
emit_replace(
    "case_store",
    "    private static final int CASE_ID_ATTEMPTS = 4;\n",
    "",
    "case allocation attempts constant expected once",
)
emit_replace(
    "case_store",
    '''    private InvestigationCase insertPunishmentCase(Connection connection, PunishmentCaseDraft draft) throws SQLException {
        for (int attempt = 0; attempt < CASE_ID_ATTEMPTS; attempt++) {
            CaseId caseId = identifiers.newCaseId();
            try {
                insertCanonicalPunishmentCase(connection, caseId, draft);
                insertExtension(connection, caseId, draft);
                return requireById(connection, caseId, false).toDomain(false);
            } catch (SQLException exception) {
                InvestigationCase replay = duplicateReplay(connection, draft.operationKey(), exception);
                if (replay != null) {
                    return replay;
                }
            }
        }
        throw new SQLException("unable to allocate unique Discord punishment case id");
    }
''',
    '''    private InvestigationCase insertPunishmentCase(Connection connection, PunishmentCaseDraft draft) throws SQLException {
        CaseId caseId = identifiers.newCaseId();
        insertCanonicalPunishmentCase(connection, caseId, draft);
        insertExtension(connection, caseId, draft);
        return requireById(connection, caseId, false).toDomain(false);
    }
''',
    "punishment case allocation block expected once",
)
emit_replace(
    "case_store",
    '''    private InvestigationCase insertInvestigationCase(Connection connection, InvestigationCaseDraft draft)
            throws SQLException {
        for (int attempt = 0; attempt < CASE_ID_ATTEMPTS; attempt++) {
            CaseId caseId = identifiers.newCaseId();
            try {
                insertCanonicalInvestigationCase(connection, caseId, draft);
                insertExtension(connection, caseId, draft);
                return requireById(connection, caseId, false).toDomain(false);
            } catch (SQLException exception) {
                InvestigationCase replay = duplicateReplay(connection, draft.operationKey(), exception);
                if (replay != null) {
                    Current current = requireById(connection, replay.caseId(), false);
                    requireInvestigationReplay(current, draft);
                    return current.toDomain(true);
                }
            }
        }
        throw new SQLException("unable to allocate unique Discord investigation case id");
    }

    private InvestigationCase duplicateReplay(Connection connection, String operationKey, SQLException exception)
            throws SQLException {
        if (!JdbcSqlErrors.isDuplicateKey(exception)) {
            throw exception;
        }
        Current replay = byOperation(connection, operationKey, false);
        return replay == null ? null : replay.toDomain(true);
    }
''',
    '''    private InvestigationCase insertInvestigationCase(Connection connection, InvestigationCaseDraft draft)
            throws SQLException {
        CaseId caseId = identifiers.newCaseId();
        insertCanonicalInvestigationCase(connection, caseId, draft);
        insertExtension(connection, caseId, draft);
        return requireById(connection, caseId, false).toDomain(false);
    }
''',
    "investigation case allocation block expected once",
)

# Domain evidence tests use canonical CaseId.
domain_test = "domain/src/test/java/net/enthusia/staff/domain/investigation/InvestigationEvidenceTest.java"
emit(f"domain_test = {domain_test!r}")
emit_replace(
    "domain_test",
    "import java.util.UUID;\nimport net.enthusia.staff.domain.moderation.DiscordUserId;",
    "import java.util.UUID;\nimport net.enthusia.staff.common.CaseId;\nimport net.enthusia.staff.domain.moderation.DiscordUserId;",
    "domain evidence CaseId import expected once",
)
emit_replace(
    "domain_test",
    '    private static final UUID CASE_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");',
    '    private static final CaseId CASE_ID = new CaseId("0123456789ABCDEF");',
    "domain evidence case fixture expected once",
)

# Worker fake store follows CaseId interface.
worker_test = "staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordInvestigationWorkerTest.java"
emit(f"worker_test = {worker_test!r}")
emit_replace(
    "worker_test",
    "import java.util.concurrent.atomic.AtomicInteger;\nimport net.enthusia.staff.domain.investigation.EvasionAlert;",
    "import java.util.concurrent.atomic.AtomicInteger;\nimport net.enthusia.staff.common.CaseId;\nimport net.enthusia.staff.domain.investigation.EvasionAlert;",
    "worker test CaseId import expected once",
)
emit_replace(
    "worker_test",
    "        public Optional<InvestigationCase> findCase(UUID caseId) {",
    "        public Optional<InvestigationCase> findCase(CaseId caseId) {",
    "worker fake-store findCase expected once",
)

# Persistence integration test proves D09 uses authoritative case rows and V19's
# case_id link rather than a parallel UUID case authority.
integration_test = "integration-tests/src/test/java/net/enthusia/staff/integration/DiscordInvestigationPersistenceIntegrationTest.java"
emit(f"integration_test = {integration_test!r}")
emit_replace(
    "integration_test",
    "import java.util.UUID;\nimport net.enthusia.staff.domain.auth.Actor;",
    "import java.util.UUID;\nimport net.enthusia.staff.common.CaseId;\nimport net.enthusia.staff.domain.auth.Actor;",
    "integration CaseId import expected once",
)
emit_replace(
    "integration_test",
    '''            UUID caseId = UUID.fromString("20000000-0000-0000-0000-000000000001");
            DiscordInvestigationStore.InvestigationCaseDraft caseDraft = new DiscordInvestigationStore.InvestigationCaseDraft(
                    caseId, "d09:test:case:1", subjectId, Optional.empty(), "Investigation only", ACTOR, NOW
            );

            assertFalse(store.createInvestigationCase(caseDraft).replayed());
            assertTrue(store.createInvestigationCase(caseDraft).replayed());

            UUID noteId = UUID.fromString("30000000-0000-0000-0000-000000000001");
            InvestigationNote.Scope scope = new InvestigationNote.Scope(
                    InvestigationNote.ScopeType.CASE, caseId.toString());
''',
    '''            Actor actor = new Actor(ACTOR, "D09Staff", StaffRank.ADMIN);
            DiscordInvestigationStore.InvestigationCaseDraft caseDraft = new DiscordInvestigationStore.InvestigationCaseDraft(
                    "d09:test:case:1", subjectId, "Investigation only", actor, NOW
            );

            var createdCase = store.createInvestigationCase(caseDraft);
            assertFalse(createdCase.replayed());
            assertTrue(store.createInvestigationCase(caseDraft).replayed());
            CaseId caseId = createdCase.caseId();

            UUID noteId = UUID.fromString("30000000-0000-0000-0000-000000000001");
            InvestigationNote.Scope scope = new InvestigationNote.Scope(
                    InvestigationNote.ScopeType.CASE, caseId.value());
''',
    "first integration case fixture expected once",
)
emit_replace(
    "integration_test",
    '''            UUID caseId = UUID.fromString("20000000-0000-0000-0000-000000000002");
            store.createInvestigationCase(new DiscordInvestigationStore.InvestigationCaseDraft(
                    caseId, "d09:test:case:2", subjectId, Optional.empty(), "Evidence retention", ACTOR, NOW
            ));
''',
    '''            CaseId caseId = store.createInvestigationCase(new DiscordInvestigationStore.InvestigationCaseDraft(
                    "d09:test:case:2", subjectId, "Evidence retention",
                    new Actor(ACTOR, "D09Staff", StaffRank.ADMIN), NOW
            )).caseId();
''',
    "evidence integration case fixture expected once",
)
emit_replace(
    "integration_test",
    "            assertEvidenceMetadata(dataSource, evidenceId);",
    "            assertEvidenceMetadata(dataSource, evidenceId, caseId, subjectId);",
    "evidence metadata assertion call expected once",
)
emit_replace(
    "integration_test",
    '''                observation.issuerId(),
                observation.summary(),
''',
    '''                observation.issuer(),
                observation.consequenceType(),
                observation.summary(),
''',
    "punishment observation provenance expected once",
)
emit_replace(
    "integration_test",
    '''    private static void assertEvidenceMetadata(HikariDataSource dataSource, UUID evidenceId) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.action')) action,
                            JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.capturedBy')) captured_by
                     FROM discord_evidence_metadata WHERE evidence_id = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(evidenceId));
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("MESSAGE_CONTEXT", rows.getString("action"));
                assertEquals(ACTOR.toString(), rows.getString("captured_by"));
            }
        }
    }
''',
    '''    private static void assertEvidenceMetadata(
            HikariDataSource dataSource,
            UUID evidenceId,
            CaseId caseId,
            ModerationSubjectId subjectId
    ) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT m.case_id, LOWER(HEX(c.subject_id)) subject_hex, c.visibility, c.state,
                            JSON_UNQUOTE(JSON_EXTRACT(m.metadata_json, '$.action')) action,
                            JSON_UNQUOTE(JSON_EXTRACT(m.metadata_json, '$.capturedBy')) captured_by
                     FROM discord_evidence_metadata m
                     JOIN cases c ON c.case_id = m.case_id
                     WHERE m.evidence_id = ?
                     """)) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(evidenceId));
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(caseId.value(), rows.getString("case_id"));
                assertEquals(subjectId.value().toString().replace("-", ""), rows.getString("subject_hex"));
                assertEquals("PRIVATE", rows.getString("visibility"));
                assertEquals("OPEN", rows.getString("state"));
                assertEquals("MESSAGE_CONTEXT", rows.getString("action"));
                assertEquals(ACTOR.toString(), rows.getString("captured_by"));
            }
        }
    }
''',
    "evidence metadata helper expected once",
)

text += "\n" + "\n".join(post) + "\n"
target.write_text(text, encoding="utf-8")
