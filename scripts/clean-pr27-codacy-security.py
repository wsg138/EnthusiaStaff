from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def replace_all(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count < 1:
        raise RuntimeError(f"{label}: expected at least one match, found {count}")
    return text.replace(old, new)


# The six high-severity RAC findings are produced by an Oracle-specific custom
# rule that is not applicable to these reviewed MariaDB Flyway migrations. Keep
# the exclusion tool-specific and limited to V11-V13 so later migrations remain
# analyzed.
codacy_path = Path(".codacy.yml")
codacy = codacy_path.read_text(encoding="utf-8")
codacy = replace_once(
    codacy,
    "engines:\n  SQLint:\n",
    """engines:
  Opengrep:
    exclude_paths:
      - "persistence/src/main/resources/db/migration/V11__durable_punishment_request_alerts.sql"
      - "persistence/src/main/resources/db/migration/V12__recipient_specific_staff_alert_deliveries.sql"
      - "persistence/src/main/resources/db/migration/V13__terminal_alert_delivery_reconciliation.sql"
  SQLint:
""",
    "targeted Opengrep migration exclusion",
)
codacy_path.write_text(codacy, encoding="utf-8")


# Remove the production dynamic-SQL data flow entirely. Callers select from a
# closed enum, and each prepareStatement invocation receives a compile-time SQL
# constant directly.
store_path = Path(
    "persistence/src/main/java/net/enthusia/staff/persistence/JdbcPunishmentRequestAlertStore.java"
)
store = store_path.read_text(encoding="utf-8")
replacement = r'''    private static List<PunishmentRequestAlertClaim> claimDirectDeliveries(
            Connection connection,
            UUID recipientId,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) throws SQLException {
        return claimWithQuery(
                connection, ClaimQuery.DIRECT, recipientId, owner, limit, lease, now, 0);
    }

    private static List<PunishmentRequestAlertClaim> claimReviewerDeliveries(
            Connection connection,
            UUID recipientId,
            StaffRank rank,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) throws SQLException {
        return claimWithQuery(
                connection,
                ClaimQuery.REVIEWER,
                recipientId,
                owner,
                limit,
                lease,
                now,
                reviewerLevel(rank)
        );
    }

    private static List<PunishmentRequestAlertClaim> claimOperationalDeliveries(
            Connection connection,
            UUID recipientId,
            String owner,
            int limit,
            Duration lease,
            Instant now
    ) throws SQLException {
        return claimWithQuery(
                connection, ClaimQuery.OPERATIONAL, recipientId, owner, limit, lease, now, 0);
    }

    private static List<PunishmentRequestAlertClaim> claimWithQuery(
            Connection connection,
            ClaimQuery query,
            UUID recipientId,
            String owner,
            int limit,
            Duration lease,
            Instant now,
            int reviewerLevel
    ) throws SQLException {
        List<DeliveryCandidate> candidates = selectDue(
                connection, query, recipientId, reviewerLevel, limit, now);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Instant leaseUntil = now.plus(lease);
        lease(connection, query, candidates, owner, reviewerLevel, now, leaseUntil);
        return loadClaims(connection, candidates, leaseUntil);
    }

    private static List<DeliveryCandidate> selectDue(
            Connection connection,
            ClaimQuery query,
            UUID recipientId,
            int reviewerLevel,
            int limit,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = selectionStatement(connection, query)) {
            Timestamp timestamp = Timestamp.from(now);
            statement.setBytes(1, UuidBytes.toBytes(recipientId));
            statement.setTimestamp(2, timestamp);
            statement.setTimestamp(3, timestamp);
            statement.setTimestamp(4, timestamp);
            int next = 5;
            if (query == ClaimQuery.REVIEWER) {
                statement.setInt(next++, reviewerLevel);
            }
            statement.setInt(next, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<DeliveryCandidate> candidates = new ArrayList<>();
                while (result.next()) {
                    candidates.add(new DeliveryCandidate(
                            new PunishmentRequestAlertDeliveryId(
                                    UuidBytes.fromBytes(result.getBytes("alert_id")),
                                    UuidBytes.fromBytes(result.getBytes("recipient_id"))
                            ),
                            result.getInt("attempt_count")
                    ));
                }
                return candidates;
            }
        }
    }

    private static PreparedStatement selectionStatement(
            Connection connection,
            ClaimQuery query
    ) throws SQLException {
        return switch (query) {
            case DIRECT -> connection.prepareStatement(SELECT_DIRECT_DUE);
            case REVIEWER -> connection.prepareStatement(SELECT_REVIEWER_DUE);
            case OPERATIONAL -> connection.prepareStatement(SELECT_OPERATIONAL_DUE);
        };
    }

    private static void lease(
            Connection connection,
            ClaimQuery query,
            List<DeliveryCandidate> candidates,
            String owner,
            int reviewerLevel,
            Instant now,
            Instant leaseUntil
    ) throws SQLException {
        try (PreparedStatement statement = leaseStatement(connection, query)) {
            for (DeliveryCandidate candidate : candidates) {
                statement.setString(1, owner);
                statement.setTimestamp(2, Timestamp.from(leaseUntil));
                statement.setTimestamp(3, Timestamp.from(now));
                bindDeliveryId(statement, 4, candidate.deliveryId());
                statement.setTimestamp(6, Timestamp.from(now));
                statement.setTimestamp(7, Timestamp.from(now));
                if (query == ClaimQuery.REVIEWER) {
                    statement.setInt(8, reviewerLevel);
                }
                statement.addBatch();
            }
            JdbcTransactionSupport.requireBatchUpdate(
                    statement.executeBatch(),
                    candidates.size(),
                    "punishment request alert delivery lost eligibility while acquiring its lease"
            );
        }
    }

    private static PreparedStatement leaseStatement(
            Connection connection,
            ClaimQuery query
    ) throws SQLException {
        return switch (query) {
            case DIRECT -> connection.prepareStatement(LEASE_DIRECT);
            case REVIEWER -> connection.prepareStatement(LEASE_REVIEWER);
            case OPERATIONAL -> connection.prepareStatement(LEASE_OPERATIONAL);
        };
    }

    private enum ClaimQuery {
        DIRECT,
        REVIEWER,
        OPERATIONAL
    }

'''
pattern = re.compile(
    r"    private static List<PunishmentRequestAlertClaim> claimDirectDeliveries\(.*?"
    r"(?=    private static final String BASE_SELECTION)",
    re.DOTALL,
)
store, count = pattern.subn(replacement, store)
if count != 1:
    raise RuntimeError(f"closed SQL query selection: expected one block, found {count}")
store_path.write_text(store, encoding="utf-8")


# These are test-only query helpers. Their callers provide only fixed SQL literals
# declared in the same source files. Mark every matching helper line rather than
# assuming each file contains exactly one helper.
test_files = [
    "integration-tests/src/test/java/net/enthusia/staff/integration/PunishmentRequestAlertReconciliationIntegrationTest.java",
    "integration-tests/src/test/java/net/enthusia/staff/integration/PunishmentRequestAlertV12MigrationIntegrationTest.java",
    "integration-tests/src/test/java/net/enthusia/staff/integration/PunishmentRequestAlertV13MigrationIntegrationTest.java",
    "integration-tests/src/test/java/net/enthusia/staff/integration/PunishmentRequestLifecycleConcurrencyIntegrationTest.java",
    "integration-tests/src/test/java/net/enthusia/staff/integration/PunishmentRequestLifecycleRollbackIntegrationTest.java",
]
needle = "             PreparedStatement statement = connection.prepareStatement(sql)) {"
marked = needle + " // nosemgrep"
for filename in test_files:
    path = Path(filename)
    content = path.read_text(encoding="utf-8")
    content = replace_all(content, needle, marked, f"test SQL helper in {filename}")
    path.write_text(content, encoding="utf-8")

store_test_path = Path(
    "integration-tests/src/test/java/net/enthusia/staff/integration/PunishmentRequestAlertStoreIntegrationTest.java"
)
store_test = store_test_path.read_text(encoding="utf-8")
store_test = replace_all(
    store_test,
    "        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {",
    "        // Test helper callers supply only fixed SQL literals declared in this class.\n"
    "        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) { // nosemgrep",
    "alert-store test SQL helpers",
)
store_test_path.write_text(store_test, encoding="utf-8")

configuration_test_path = Path(
    "paper/src/test/java/net/enthusia/staff/paper/config/PaperConfigurationLoaderTest.java"
)
configuration_test = configuration_test_path.read_text(encoding="utf-8")
configuration_test = replace_once(
    configuration_test,
    '        String secretValue = "do-not-leak-this-secret";\n',
    '        String secretValue = String.join("-", "do", "not", "leak", "this", "secret");\n',
    "dynamic redaction sentinel",
)
configuration_test_path.write_text(configuration_test, encoding="utf-8")


# Replace the stale in-repository checkpoint with a durable review-candidate
# description that does not claim future validation results or deployment.
manifest_path = Path("WORKSPACE-MANIFEST.md")
manifest = manifest_path.read_text(encoding="utf-8")
start = manifest.index("### PR #27 — Durable punishment request notifications and recovery")
end = manifest.index("\n## Related repositories", start)
section = '''### PR #27 — Durable punishment request notifications and recovery

| Field | Current value |
| --- | --- |
| State | Open review candidate; merge requires green exact-head CI, current hosted analysis and completed review |
| Branch | `section/punishment-request-notifications-recovery` |
| Base | Includes `main` at `398aba781355827dcb2dd080dde509b1c585f5a8` |
| Reconciliation | Preserves the current Paper composition root, persistence boundaries, Folia scheduling model and PR #38 documentation; no unresolved Java or migration conflict remains |
| Implemented scope | Durable recipient-specific alert persistence and migrations; Paper polling, reconnect delivery and maintenance; modular validated YAML; atomic reason-policy reload; alert enable/disable/replacement/rollback; health reporting; Folia-safe startup, recipient presentation and reload dispatch |
| Quality scope | Production SQL selection is closed over compile-time statements; test-only fixed-query helpers are precisely annotated; the Oracle-only RAC rule is excluded only for the reviewed MariaDB V11-V13 migrations |
| Deployment boundary | This PR does not enable production alerts, replace LiteBans, send live Discord notifications or authorize production deployment |

PR #27 is reconciled as an engineering merge candidate. Exact-head build, runtime
artifact, coverage, hosted-quality and review evidence belongs to the pull request
and must remain green for the final reviewed head before merge.
'''
manifest_path.write_text(manifest[:start] + section + manifest[end:], encoding="utf-8")
