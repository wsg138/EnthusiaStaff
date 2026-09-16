from pathlib import Path
import sys

root = Path(sys.argv[1])


def p(name):
    return root / name


def read(name):
    return p(name).read_text(encoding="utf-8")


def write(name, content):
    p(name).parent.mkdir(parents=True, exist_ok=True)
    p(name).write_text(content, encoding="utf-8")


def replace_once(name, old, new):
    text = read(name)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{name}: expected one replacement, found {count}: {old[:140]!r}")
    write(name, text.replace(old, new, 1))


# Canonical case review can represent a subject-authoritative case without a Minecraft target.
case_review = "domain/src/main/java/net/enthusia/staff/domain/casefile/CaseReview.java"
replace_once(case_review, "import net.enthusia.staff.common.Checks;\n", "import net.enthusia.staff.common.Checks;\nimport net.enthusia.staff.domain.moderation.ModerationSubjectId;\n")
replace_once(case_review, '''public record CaseReview(
        CaseId caseId,
        UUID targetId,
        UUID actorId,
''', '''public record CaseReview(
        CaseId caseId,
        UUID targetId,
        Optional<ModerationSubjectId> subjectId,
        UUID actorId,
''')
replace_once(case_review, '''    public CaseReview {
        if (caseId == null || targetId == null || actorId == null || visibility == null
                || state == null || issuedAt == null || revision < 0 || punishmentStep == null
                || sanctions == null || openOverturnRequest == null) {
            throw new IllegalArgumentException("case review fields must be present");
        }
''', '''    public CaseReview {
        if (caseId == null || subjectId == null || (targetId == null && subjectId.isEmpty())
                || actorId == null || visibility == null || state == null || issuedAt == null || revision < 0
                || punishmentStep == null || sanctions == null || openOverturnRequest == null) {
            throw new IllegalArgumentException("case review fields must be present");
        }
''')
# Preserve the mature Minecraft-facing constructor so unrelated call sites do not churn.
needle = '''        sanctions = List.copyOf(sanctions);
    }

    public boolean hasActiveSanctions() {
'''
replacement = '''        sanctions = List.copyOf(sanctions);
    }

    public CaseReview(
            CaseId caseId,
            UUID targetId,
            UUID actorId,
            String actorName,
            String actorRank,
            String publicReason,
            String exactReasonId,
            String sanctionFamily,
            String internalExplanation,
            String configurationVersion,
            CaseVisibility visibility,
            CaseState state,
            Instant issuedAt,
            long revision,
            Optional<PunishmentStepReview> punishmentStep,
            List<SanctionReview> sanctions,
            Optional<OverturnRequestReview> openOverturnRequest
    ) {
        this(caseId, targetId, Optional.empty(), actorId, actorName, actorRank, publicReason, exactReasonId,
                sanctionFamily, internalExplanation, configurationVersion, visibility, state, issuedAt, revision,
                punishmentStep, sanctions, openOverturnRequest);
    }

    public Optional<UUID> minecraftTargetId() {
        return Optional.ofNullable(targetId);
    }

    public boolean hasActiveSanctions() {
'''
replace_once(case_review, needle, replacement)

# Subject-aware case reads and bounded recent-by-subject query.
case_store = "persistence/src/main/java/net/enthusia/staff/persistence/JdbcCaseReviewStore.java"
replace_once(case_store, "import net.enthusia.staff.domain.casefile.SanctionReview;\n", "import net.enthusia.staff.domain.casefile.SanctionReview;\nimport net.enthusia.staff.domain.moderation.ModerationSubjectId;\n")
replace_once(case_store, '''    private CaseReview read(Connection connection, CaseId caseId) throws SQLException {
''', '''    public List<CaseReview> recentBySubject(ModerationSubjectId subjectId, int limit) {
        if (subjectId == null || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("subject and a limit from 1 to 100 are required");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT case_id FROM cases WHERE subject_id = ?
                     ORDER BY issued_at DESC LIMIT ?
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            statement.setInt(2, limit);
            return readCases(connection, statement);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read subject case reviews", exception);
        }
    }

    private List<CaseReview> readCases(Connection connection, PreparedStatement statement) throws SQLException {
        List<CaseId> identifiers = new ArrayList<>();
        try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                identifiers.add(new CaseId(result.getString("case_id")));
            }
        }
        List<CaseReview> reviews = new ArrayList<>();
        for (CaseId identifier : identifiers) {
            CaseReview review = read(connection, identifier);
            if (review != null) {
                reviews.add(review);
            }
        }
        return List.copyOf(reviews);
    }

    private CaseReview read(Connection connection, CaseId caseId) throws SQLException {
''')
# Refactor existing recent target method onto same helper to keep orchestration small.
old_recent_body = '''            List<CaseId> identifiers = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    identifiers.add(new CaseId(result.getString("case_id")));
                }
            }
            List<CaseReview> cases = new ArrayList<>();
            for (CaseId identifier : identifiers) {
                CaseReview review = read(connection, identifier);
                if (review != null) {
                    cases.add(review);
                }
            }
            return List.copyOf(cases);
'''
replace_once(case_store, old_recent_body, '''            return readCases(connection, statement);
''')
replace_once(case_store, '''                SELECT c.case_id, c.target_id, c.actor_id, c.actor_name, c.actor_rank,
''', '''                SELECT c.case_id, c.target_id, c.subject_id, c.actor_id, c.actor_name, c.actor_rank,
''')
replace_once(case_store, '''                return new CaseReview(
                        caseId,
                        UuidBytes.fromBytes(result.getBytes("target_id")),
                        UuidBytes.fromBytes(result.getBytes("actor_id")),
''', '''                byte[] targetBytes = result.getBytes("target_id");
                byte[] subjectBytes = result.getBytes("subject_id");
                return new CaseReview(
                        caseId,
                        targetBytes == null ? null : UuidBytes.fromBytes(targetBytes),
                        subjectBytes == null
                                ? Optional.empty()
                                : Optional.of(new ModerationSubjectId(UuidBytes.fromBytes(subjectBytes))),
                        UuidBytes.fromBytes(result.getBytes("actor_id")),
''')

# Identity repository exposes a read-only subject lookup for subject-authoritative cases.
identity_repo = "persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordIdentityRepository.java"
replace_once(identity_repo, '''    Optional<VersionedLink> currentLink(UUID playerId) {
''', '''    Optional<VersionedSubject> subject(ModerationSubjectId subjectId) {
        requirePresent(subjectId, "subjectId");
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to read moderation subject",
                connection -> subjectExists(connection, subjectId)
                        ? Optional.of(loadSubject(connection, subjectId))
                        : Optional.empty()
        );
    }

    Optional<VersionedLink> currentLink(UUID playerId) {
''')
replace_once(identity_repo, '''    private static long readSubjectRevision(Connection connection, ModerationSubjectId subjectId)
''', '''    private static boolean subjectExists(Connection connection, ModerationSubjectId subjectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM moderation_subjects WHERE subject_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static long readSubjectRevision(Connection connection, ModerationSubjectId subjectId)
''')

# Bounded current D09 notes by subject.
note_store = "persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordInvestigationNoteStore.java"
replace_once(note_store, '''    List<InvestigationNote.Version> history(UUID noteId, int limit) {
''', '''    List<InvestigationNote> recent(ModerationSubjectId subjectId, int limit) {
        if (subjectId == null || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("private note subject query is invalid");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to read recent Discord private notes", connection -> {
            List<InvestigationNote> notes = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT note_id, subject_id, scope_type, scope_value, visibility, current_text,
                           created_by, created_at, updated_by, updated_at, revision
                    FROM discord_private_notes
                    WHERE subject_id = ?
                    ORDER BY updated_at DESC, note_id
                    LIMIT ?
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
                statement.setInt(2, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        notes.add(read(rows).toDomain(false));
                    }
                }
            }
            return List.copyOf(notes);
        });
    }

    List<InvestigationNote.Version> history(UUID noteId, int limit) {
''')

# Read runtime exposes only explicit private read projections needed by staff-bot.
read_runtime = "persistence/src/main/java/net/enthusia/staff/persistence/DiscordStaffReadRuntime.java"
replace_once(read_runtime, "import net.enthusia.staff.domain.history.ModerationHistoryPage;\n", "import net.enthusia.staff.domain.history.ModerationHistoryPage;\nimport net.enthusia.staff.domain.investigation.InvestigationNote;\n")
replace_once(read_runtime, "import net.enthusia.staff.domain.moderation.DiscordUserId;\n", "import net.enthusia.staff.domain.moderation.DiscordUserId;\nimport net.enthusia.staff.domain.moderation.ModerationSubjectId;\n")
replace_once(read_runtime, '''    private final JdbcStaffNoteStore notes;
''', '''    private final JdbcStaffNoteStore notes;
    private final JdbcDiscordInvestigationNoteStore investigationNotes;
''')
replace_once(read_runtime, '''        this.notes = new JdbcStaffNoteStore(dataSource);
''', '''        this.notes = new JdbcStaffNoteStore(dataSource);
        this.investigationNotes = new JdbcDiscordInvestigationNoteStore(dataSource);
''')
replace_once(read_runtime, '''    public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
        return identities.subjectForDiscord(userId);
    }
''', '''    public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
        return identities.subjectForDiscord(userId);
    }

    public Optional<VersionedSubject> subject(ModerationSubjectId subjectId) {
        return identities.subject(subjectId);
    }
''')
replace_once(read_runtime, '''    public List<CaseReview> recentCases(UUID targetId, int limit) {
        return cases.recent(targetId, limit);
    }
''', '''    public List<CaseReview> recentCases(UUID targetId, int limit) {
        return cases.recent(targetId, limit);
    }

    public List<CaseReview> recentCases(ModerationSubjectId subjectId, int limit) {
        return cases.recentBySubject(subjectId, limit);
    }
''')
replace_once(read_runtime, '''    public List<StaffNote> recentNotes(UUID targetId, int limit) {
        return notes.recent(targetId, limit);
    }
''', '''    public List<StaffNote> recentNotes(UUID targetId, int limit) {
        return notes.recent(targetId, limit);
    }

    public List<InvestigationNote> recentInvestigationNotes(ModerationSubjectId subjectId, int limit) {
        return investigationNotes.recent(subjectId, limit);
    }
''')

# Subject-aware staff-bot reads, bounded merging, and visibility filtering.
read_service = "staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffModerationReadService.java"
replace_once(read_service, "import java.util.List;\n", "import java.util.LinkedHashMap;\nimport java.util.List;\n")
replace_once(read_service, "import net.enthusia.staff.domain.casefile.CaseReview;\n", "import net.enthusia.staff.domain.auth.StaffRank;\nimport net.enthusia.staff.domain.casefile.CaseReview;\n")
replace_once(read_service, "import net.enthusia.staff.domain.history.ModerationHistoryPage;\n", "import net.enthusia.staff.domain.history.ModerationHistoryPage;\nimport net.enthusia.staff.domain.investigation.InvestigationNote;\n")
replace_once(read_service, '''        Optional<VersionedSubject> subjectForMinecraft(UUID playerId);

        PlayerResolution resolvePlayer(String uuidOrUsername);
''', '''        Optional<VersionedSubject> subjectForMinecraft(UUID playerId);

        default Optional<VersionedSubject> subject(ModerationSubjectId subjectId) {
            return Optional.empty();
        }

        PlayerResolution resolvePlayer(String uuidOrUsername);
''')
replace_once(read_service, '''        List<CaseReview> recentCases(UUID targetId, int limit);

        Optional<CaseReview> caseReview(CaseId caseId);
''', '''        List<CaseReview> recentCases(UUID targetId, int limit);

        default List<CaseReview> recentCases(ModerationSubjectId subjectId, int limit) {
            return List.of();
        }

        Optional<CaseReview> caseReview(CaseId caseId);
''')
replace_once(read_service, '''        List<StaffNote> recentNotes(UUID targetId, int limit);
''', '''        List<StaffNote> recentNotes(UUID targetId, int limit);

        default List<InvestigationNote> recentInvestigationNotes(ModerationSubjectId subjectId, int limit) {
            return List.of();
        }
''')
replace_once(read_service, '''    Optional<CaseReview> caseReview(CaseId caseId) {
        return data.caseReview(caseId);
    }

    Snapshot snapshot(Target target) {
''', '''    Optional<CaseReview> caseReview(CaseId caseId) {
        return data.caseReview(caseId);
    }

    Target caseTarget(CaseReview review) {
        if (review == null) {
            throw new IllegalArgumentException("case review must be present");
        }
        if (review.minecraftTargetId().isPresent()) {
            return minecraftTarget(review.minecraftTargetId().orElseThrow());
        }
        ModerationSubjectId subjectId = review.subjectId()
                .orElseThrow(() -> new IllegalStateException("case has no moderation subject"));
        VersionedSubject subject = data.subject(subjectId)
                .orElseThrow(() -> new IllegalStateException("case moderation subject is unavailable"));
        DiscordUserId discord = subject.subject().discordUserIds().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Discord-only case has no Discord identity"));
        return checked(new Target(TargetKind.DISCORD, Optional.of(discord), Optional.empty(), Optional.of(subject)));
    }

    List<InvestigationNote> investigationNotes(Target target, StaffRank viewerRank) {
        Target checkedTarget = checked(target);
        if (viewerRank == null || checkedTarget.subject().isEmpty()) {
            return List.of();
        }
        ModerationSubjectId subjectId = checkedTarget.subject().orElseThrow().subject().subjectId();
        boolean management = viewerRank == StaffRank.ADMIN || viewerRank == StaffRank.FOUNDER;
        return data.recentInvestigationNotes(subjectId, PANEL_LIMIT).stream()
                .filter(note -> note.visibility() == InvestigationNote.Visibility.STAFF || management)
                .limit(PANEL_LIMIT)
                .toList();
    }

    Snapshot snapshot(Target target) {
''')
replace_once(read_service, '''                recentNotes(accountIds),
                recentCases(accountIds),
                historicalLinkCount(checkedTarget)
''', '''                recentNotes(accountIds),
                recentCases(subject, accountIds),
                historicalLinkCount(checkedTarget)
''')
replace_once(read_service, '''    private List<CaseReview> recentCases(Set<UUID> accountIds) {
        return accountIds.stream()
                .flatMap(id -> data.recentCases(id, PER_ACCOUNT_LIMIT).stream())
                .sorted(Comparator.comparing(CaseReview::issuedAt).reversed())
                .limit(PANEL_LIMIT)
                .toList();
    }
''', '''    private List<CaseReview> recentCases(ModerationSubject subject, Set<UUID> accountIds) {
        LinkedHashMap<CaseId, CaseReview> unique = new LinkedHashMap<>();
        data.recentCases(subject.subjectId(), PANEL_LIMIT).forEach(review -> unique.put(review.caseId(), review));
        accountIds.stream()
                .flatMap(id -> data.recentCases(id, PER_ACCOUNT_LIMIT).stream())
                .forEach(review -> unique.putIfAbsent(review.caseId(), review));
        return unique.values().stream()
                .sorted(Comparator.comparing(CaseReview::issuedAt).reversed())
                .limit(PANEL_LIMIT)
                .toList();
    }
''')
replace_once(read_service, '''        @Override
        public Optional<VersionedSubject> subjectForMinecraft(UUID playerId) {
            return runtime.subjectForMinecraft(playerId);
        }
''', '''        @Override
        public Optional<VersionedSubject> subjectForMinecraft(UUID playerId) {
            return runtime.subjectForMinecraft(playerId);
        }

        @Override
        public Optional<VersionedSubject> subject(ModerationSubjectId subjectId) {
            return runtime.subject(subjectId);
        }
''')
replace_once(read_service, '''        @Override
        public List<CaseReview> recentCases(UUID targetId, int limit) {
            return runtime.recentCases(targetId, limit);
        }
''', '''        @Override
        public List<CaseReview> recentCases(UUID targetId, int limit) {
            return runtime.recentCases(targetId, limit);
        }

        @Override
        public List<CaseReview> recentCases(ModerationSubjectId subjectId, int limit) {
            return runtime.recentCases(subjectId, limit);
        }
''')
replace_once(read_service, '''        @Override
        public List<StaffNote> recentNotes(UUID targetId, int limit) {
            return runtime.recentNotes(targetId, limit);
        }
''', '''        @Override
        public List<StaffNote> recentNotes(UUID targetId, int limit) {
            return runtime.recentNotes(targetId, limit);
        }

        @Override
        public List<InvestigationNote> recentInvestigationNotes(ModerationSubjectId subjectId, int limit) {
            return runtime.recentInvestigationNotes(subjectId, limit);
        }
''')

controller = "staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffModerationController.java"
replace_once(controller, '''    private Response notes(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            SignedComponentCodec.TargetRef targetRef
    ) {
        authorize(invokerId, invokerName, target, DiscordModerationOperation.VIEW_NOTES);
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(target);
        return Response.text(StaffModerationTextRenderer.notes(snapshot), historyNavigation(invokerId, targetRef));
    }
''', '''    private Response notes(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            SignedComponentCodec.TargetRef targetRef
    ) {
        Actor actor = authorize(invokerId, invokerName, target, DiscordModerationOperation.VIEW_NOTES);
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(target);
        return Response.text(
                StaffModerationTextRenderer.notes(snapshot, reads.investigationNotes(target, actor.rank())),
                historyNavigation(invokerId, targetRef)
        );
    }
''')
replace_once(controller, '''        Actor actor = requireInvoker(
                invokerId,
                invokerName,
                DiscordModerationOperation.VIEW_HISTORY,
                ModerationPlatform.MINECRAFT
        );
        CaseReview review = reads.caseReview(caseId).orElse(null);
        if (review == null) {
            return Response.text("No case exists with that ID.", List.of());
        }
        StaffModerationReadService.Target target = reads.minecraftTarget(review.targetId());
''', '''        Actor actor = actors.invoker(discord(invokerId), invokerName);
        CaseReview review = reads.caseReview(caseId).orElse(null);
        if (review == null) {
            authorization.require(actor, Optional.empty(), DiscordModerationOperation.VIEW_HISTORY, ModerationPlatform.MINECRAFT);
            return Response.text("No case exists with that ID.", List.of());
        }
        StaffModerationReadService.Target target = reads.caseTarget(review);
''')

renderer = "staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffModerationTextRenderer.java"
replace_once(renderer, "import net.enthusia.staff.domain.history.ModerationHistoryEntry;\n", "import net.enthusia.staff.domain.history.ModerationHistoryEntry;\nimport net.enthusia.staff.domain.investigation.InvestigationNote;\n")
replace_once(renderer, '''    static String notes(StaffModerationReadService.Snapshot snapshot) {
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**Recent staff notes**\n");
        appendNotes(content, snapshot);
        return limit(content);
    }
''', '''    static String notes(StaffModerationReadService.Snapshot snapshot) {
        return notes(snapshot, java.util.List.of());
    }

    static String notes(
            StaffModerationReadService.Snapshot snapshot,
            java.util.List<InvestigationNote> investigationNotes
    ) {
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**Recent staff notes**\n");
        appendInvestigationNotes(content, investigationNotes);
        appendNotes(content, snapshot, investigationNotes.isEmpty());
        return limit(content);
    }
''')
replace_once(renderer, '''    private static void appendNotes(StringBuilder content, StaffModerationReadService.Snapshot snapshot) {
        if (snapshot.recentNotes().isEmpty()) {
            content.append("No staff notes are available for the current linked Minecraft identities.");
            return;
        }
        for (StaffNote note : snapshot.recentNotes()) {
            content.append("• ").append(TIME.format(note.createdAt())).append(ITEM_SEPARATOR)
                    .append(shorten(escape(note.noteText()), ITEM_TEXT)).append('\n');
        }
    }
''', '''    private static void appendInvestigationNotes(
            StringBuilder content,
            java.util.List<InvestigationNote> notes
    ) {
        for (InvestigationNote note : notes) {
            content.append("• ").append(TIME.format(note.updatedAt())).append(ITEM_SEPARATOR)
                    .append(note.visibility()).append(" / ").append(note.scope().type()).append(ITEM_SEPARATOR)
                    .append(shorten(escape(note.text()), ITEM_TEXT)).append('\n');
        }
    }

    private static void appendNotes(
            StringBuilder content,
            StaffModerationReadService.Snapshot snapshot,
            boolean investigationEmpty
    ) {
        if (snapshot.recentNotes().isEmpty()) {
            if (investigationEmpty) {
                content.append("No staff notes are available for this moderation subject.");
            }
            return;
        }
        for (StaffNote note : snapshot.recentNotes()) {
            content.append("• ").append(TIME.format(note.createdAt())).append(ITEM_SEPARATOR)
                    .append("LEGACY_MINECRAFT").append(ITEM_SEPARATOR)
                    .append(shorten(escape(note.noteText()), ITEM_TEXT)).append('\n');
        }
    }
''')

# Minecraft-only Paper views handle subject-only cases explicitly rather than dereferencing null.
case_command = "paper/src/main/java/net/enthusia/staff/paper/command/CaseCommand.java"
replace_once(case_command, '''                "Case " + review.caseId().value() + " | subject " + review.targetId()
''', '''                "Case " + review.caseId().value() + " | subject "
                        + review.minecraftTargetId().map(UUID::toString).orElse("Discord-only moderation subject")
''')

sanction_gui = "paper/src/main/java/net/enthusia/staff/paper/sanction/SanctionChangeGuiController.java"
replace_once(sanction_gui, '''            if (review != null && matchesCommandCase(commandName, direct, caseLookup)) {
                openState(viewer, new SanctionChangeGuiState.Actions(
                        viewer.getUniqueId(), commandName, review.targetId().toString(), review, Optional.empty()
                ));
                return;
            }
''', '''            if (review != null && matchesCommandCase(commandName, direct, caseLookup)) {
                UUID targetId = review.minecraftTargetId().orElse(null);
                if (targetId == null) {
                    message(viewer, "That case is Discord-only and cannot be changed from the Minecraft sanction GUI.");
                    return;
                }
                openState(viewer, new SanctionChangeGuiState.Actions(
                        viewer.getUniqueId(), commandName, targetId.toString(), review, Optional.empty()
                ));
                return;
            }
''')

# Read-service unit coverage for visibility filtering and subject-only case routing.
read_test = "staff-bot/src/test/java/net/enthusia/staff/discordbot/StaffModerationReadServiceTest.java"
replace_once(read_test, "import net.enthusia.staff.domain.casefile.CaseReview;\n", "import net.enthusia.staff.domain.auth.StaffRank;\nimport net.enthusia.staff.domain.casefile.CaseReview;\nimport net.enthusia.staff.domain.casefile.CaseState;\nimport net.enthusia.staff.domain.casefile.CaseVisibility;\n")
replace_once(read_test, "import net.enthusia.staff.domain.history.ModerationHistoryPage;\n", "import net.enthusia.staff.domain.history.ModerationHistoryPage;\nimport net.enthusia.staff.domain.investigation.InvestigationNote;\n")
insert_before_helper = '''    private static VersionedSubject subject(
'''
new_tests = '''    @Test
    void privateInvestigationNotesRespectManagementVisibility() {
        DiscordUserId discord = new DiscordUserId("323456789012345678");
        VersionedSubject subject = subject(Set.of(new DiscordIdentityRef(discord)), Optional.empty());
        ModerationSubjectId subjectId = subject.subject().subjectId();
        FakeReadData data = new FakeReadData();
        data.discordSubjects.put(discord, subject);
        data.subjects.put(subjectId, subject);
        data.investigationNotes = List.of(
                note(subjectId, InvestigationNote.Visibility.MANAGEMENT, "management only", 2),
                note(subjectId, InvestigationNote.Visibility.STAFF, "staff visible", 1)
        );
        StaffModerationReadService service = new StaffModerationReadService(data, CLOCK);
        StaffModerationReadService.Target target = service.discordTarget(discord);

        assertEquals(List.of("staff visible"), service.investigationNotes(target, StaffRank.MOD).stream()
                .map(InvestigationNote::text).toList());
        assertEquals(List.of("management only", "staff visible"), service.investigationNotes(target, StaffRank.ADMIN).stream()
                .map(InvestigationNote::text).toList());
    }

    @Test
    void discordOnlyCaseRoutesThroughAuthoritativeModerationSubject() {
        DiscordUserId discord = new DiscordUserId("423456789012345678");
        VersionedSubject subject = subject(Set.of(new DiscordIdentityRef(discord)), Optional.empty());
        ModerationSubjectId subjectId = subject.subject().subjectId();
        FakeReadData data = new FakeReadData();
        data.subjects.put(subjectId, subject);
        StaffModerationReadService service = new StaffModerationReadService(data, CLOCK);
        CaseReview review = new CaseReview(
                new CaseId("0123456789ABCDEF"), null, Optional.of(subjectId), UUID.randomUUID(), "Staff", "MOD",
                "Private Discord investigation", "DISCORD_INVESTIGATION", "DISCORD_INVESTIGATION", "summary",
                "discord-d09-v1", CaseVisibility.PRIVATE, CaseState.OPEN, NOW, 0,
                Optional.empty(), List.of(), Optional.empty()
        );

        StaffModerationReadService.Target target = service.caseTarget(review);
        assertEquals(StaffModerationReadService.TargetKind.DISCORD, target.kind());
        assertEquals(Optional.of(discord), target.discordId());
        assertTrue(target.minecraftId().isEmpty());
    }

    private static InvestigationNote note(
            ModerationSubjectId subjectId,
            InvestigationNote.Visibility visibility,
            String text,
            long revision
    ) {
        UUID actor = UUID.fromString("50000000-0000-0000-0000-000000000001");
        return new InvestigationNote(
                UUID.randomUUID(), subjectId,
                new InvestigationNote.Scope(InvestigationNote.ScopeType.SUBJECT, subjectId.value().toString()),
                visibility, text, actor, NOW.minusSeconds(revision), actor, NOW, revision, false
        );
    }

'''
replace_once(read_test, insert_before_helper, new_tests + insert_before_helper)
replace_once(read_test, '''        private final Map<UUID, VersionedSubject> minecraftSubjects = new ConcurrentHashMap<>();
        private final Map<UUID, PlayerIdentity> players = new ConcurrentHashMap<>();
        private PlayerResolution resolution = new PlayerResolution.Missing();
''', '''        private final Map<UUID, VersionedSubject> minecraftSubjects = new ConcurrentHashMap<>();
        private final Map<ModerationSubjectId, VersionedSubject> subjects = new ConcurrentHashMap<>();
        private final Map<UUID, PlayerIdentity> players = new ConcurrentHashMap<>();
        private List<InvestigationNote> investigationNotes = List.of();
        private PlayerResolution resolution = new PlayerResolution.Missing();
''')
replace_once(read_test, '''        @Override
        public PlayerResolution resolvePlayer(String uuidOrUsername) {
''', '''        @Override
        public Optional<VersionedSubject> subject(ModerationSubjectId subjectId) {
            return Optional.ofNullable(subjects.get(subjectId));
        }

        @Override
        public PlayerResolution resolvePlayer(String uuidOrUsername) {
''')
replace_once(read_test, '''        @Override
        public List<StaffNote> recentNotes(UUID targetId, int limit) {
            return List.of();
        }
''', '''        @Override
        public List<StaffNote> recentNotes(UUID targetId, int limit) {
            return List.of();
        }

        @Override
        public List<InvestigationNote> recentInvestigationNotes(ModerationSubjectId subjectId, int limit) {
            return investigationNotes.stream().filter(note -> note.subjectId().equals(subjectId)).limit(limit).toList();
        }
''')

# Renderer coverage proves private notes appear only after service filtering.
write("staff-bot/src/test/java/net/enthusia/staff/discordbot/StaffModerationInvestigationNoteRendererTest.java", r'''package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.investigation.InvestigationNote;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import org.junit.jupiter.api.Test;

class StaffModerationInvestigationNoteRendererTest {
    @Test
    void investigationNotesAreEscapedAndRenderedSeparatelyFromLegacyNotes() {
        Instant now = Instant.parse("2026-09-15T12:00:00Z");
        ModerationSubjectId subject = new ModerationSubjectId(UUID.randomUUID());
        UUID actor = UUID.randomUUID();
        InvestigationNote note = new InvestigationNote(
                UUID.randomUUID(), subject,
                new InvestigationNote.Scope(InvestigationNote.ScopeType.SUBJECT, subject.value().toString()),
                InvestigationNote.Visibility.STAFF, "private @everyone `note`", actor, now, actor, now, 0, false
        );
        StaffModerationReadService.Target target = new StaffModerationReadService.Target(
                StaffModerationReadService.TargetKind.DISCORD,
                Optional.of(new DiscordUserId("123456789012345678")), Optional.empty(), Optional.empty()
        );
        StaffModerationReadService.Snapshot snapshot = new StaffModerationReadService.Snapshot(
                target, List.of(), List.of(), List.of(), 0, Map.of(), List.of(), List.of(), 0
        );

        String rendered = StaffModerationTextRenderer.notes(snapshot, List.of(note));
        assertTrue(rendered.contains("private ＠everyone 'note'"));
        assertTrue(rendered.contains("STAFF / SUBJECT"));
        assertFalse(rendered.contains("@everyone"));
    }
}
''')

# Integration coverage: subject-only authoritative case review and D09 note read projection.
integration = "integration-tests/src/test/java/net/enthusia/staff/integration/DiscordInvestigationPersistenceIntegrationTest.java"
replace_once(integration, "import com.zaxxer.hikari.HikariDataSource;\n", "import com.fasterxml.jackson.databind.ObjectMapper;\nimport com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;\nimport com.zaxxer.hikari.HikariDataSource;\n")
replace_once(integration, "import java.time.Duration;\n", "import java.time.Clock;\nimport java.time.Duration;\nimport java.time.ZoneOffset;\n")
replace_once(integration, "import net.enthusia.staff.domain.investigation.InvestigationNote;\n", "import net.enthusia.staff.domain.investigation.InvestigationNote;\nimport net.enthusia.staff.domain.casefile.CaseReview;\n")
replace_once(integration, "import net.enthusia.staff.persistence.JdbcDiscordPunishmentRepository;\n", "import net.enthusia.staff.persistence.JdbcDiscordPunishmentRepository;\nimport net.enthusia.staff.persistence.DiscordStaffReadRuntime;\nimport net.enthusia.staff.persistence.JdbcCaseReviewStore;\n")
# Add explicit assertions to first case/note test after create replay.
replace_once(integration, '''            assertFalse(store.createInvestigationCase(caseDraft).replayed());
            assertTrue(store.createInvestigationCase(caseDraft).replayed());

            UUID noteId = UUID.fromString("30000000-0000-0000-0000-000000000001");
''', '''            var createdCase = store.createInvestigationCase(caseDraft);
            assertFalse(createdCase.replayed());
            assertTrue(store.createInvestigationCase(caseDraft).replayed());
            ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
            CaseReview caseReview = new JdbcCaseReviewStore(dataSource, Clock.fixed(NOW, ZoneOffset.UTC), json)
                    .find(createdCase.caseId()).orElseThrow();
            assertTrue(caseReview.minecraftTargetId().isEmpty());
            assertEquals(Optional.of(subjectId), caseReview.subjectId());

            UUID noteId = UUID.fromString("30000000-0000-0000-0000-000000000001");
''')
replace_once(integration, '''            assertEquals(List.of(1L, 0L), store.noteHistory(noteId, 10).stream()
                    .map(InvestigationNote.Version::revision).toList());
''', '''            assertEquals(List.of(1L, 0L), store.noteHistory(noteId, 10).stream()
                    .map(InvestigationNote.Version::revision).toList());
            try (DiscordStaffReadRuntime reads = DiscordStaffReadRuntime.open(
                    MariaDbIntegrationSupport.databaseConfig(DATABASE), Clock.fixed(NOW, ZoneOffset.UTC))) {
                assertEquals(List.of("second private version"), reads.recentInvestigationNotes(subjectId, 10).stream()
                        .map(InvestigationNote::text).toList());
                assertEquals(createdCase.caseId(), reads.recentCases(subjectId, 10).getFirst().caseId());
            }
''')

print("D09 private read-surface repair applied")
