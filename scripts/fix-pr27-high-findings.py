from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


store_path = Path(
    "persistence/src/main/java/net/enthusia/staff/persistence/JdbcPunishmentRequestAlertStore.java"
)
store = store_path.read_text(encoding="utf-8")
store = replace_once(
    store,
    "            if (sql == SELECT_REVIEWER_DUE) {\n",
    "            if (SELECT_REVIEWER_DUE.equals(sql)) {\n",
    "reviewer selection SQL comparison",
)
store = replace_once(
    store,
    "                if (sql == LEASE_REVIEWER) {\n",
    "                if (LEASE_REVIEWER.equals(sql)) {\n",
    "reviewer lease SQL comparison",
)
store_path.write_text(store, encoding="utf-8")

writer_path = Path(
    "persistence/src/main/java/net/enthusia/staff/persistence/JdbcPunishmentRequestAlertWriter.java"
)
writer = writer_path.read_text(encoding="utf-8")
writer = replace_once(
    writer,
    "            return first == second;\n",
    "            return Objects.equals(first, second);\n",
    "nullable instant comparison",
)
writer_path.write_text(writer, encoding="utf-8")

lifecycle_path = Path(
    "paper/src/main/java/net/enthusia/staff/paper/alert/PunishmentRequestAlertLifecycle.java"
)
lifecycle = lifecycle_path.read_text(encoding="utf-8")
lifecycle = replace_once(
    lifecycle,
    "    public boolean start() {\n",
    "    // Ownership transfers to joinRegistration on success and close() releases it.\n"
    "    @SuppressWarnings(\"PMD.CloseResource\")\n"
    "    public boolean start() {\n",
    "listener ownership suppression",
)
lifecycle = replace_once(
    lifecycle,
    "        AutoCloseable registration = joinRegistration.getAndSet(null);\n"
    "        closeQuietly(registration);\n",
    "        closeQuietly(joinRegistration.getAndSet(null));\n",
    "direct listener cleanup",
)
lifecycle_path.write_text(lifecycle, encoding="utf-8")

integration_path = Path(
    "integration-tests/src/test/java/net/enthusia/staff/integration/MariaDbDriverIntegrationTest.java"
)
integration = integration_path.read_text(encoding="utf-8")
integration = replace_once(
    integration,
    "@Testcontainers\nclass MariaDbDriverIntegrationTest {\n",
    "@Testcontainers\n"
    "// Reflection is intentional: these tests validate a package-private runtime factory in both\n"
    "// the normal module and an isolated shaded-JAR class loader without widening production API.\n"
    "@SuppressWarnings(\"PMD.AvoidAccessibilityAlteration\")\n"
    "class MariaDbDriverIntegrationTest {\n",
    "controlled reflection suppression",
)
old_stream = '''            InputStream serviceStream = loader.getResourceAsStream(DRIVER_SERVICE_ENTRY);
            assertNotNull(serviceStream, "MariaDB JDBC service registration must be present");
            try (InputStream input = serviceStream) {
                String providers = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(
                        providers.lines().map(String::trim).anyMatch(DRIVER_CLASS_NAME::equals),
                        "MariaDB JDBC service registration must name the driver"
                );
            }
'''
new_stream = '''            try (InputStream serviceStream = loader.getResourceAsStream(DRIVER_SERVICE_ENTRY)) {
                assertNotNull(serviceStream, "MariaDB JDBC service registration must be present");
                String providers = new String(serviceStream.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(
                        providers.lines().map(String::trim).anyMatch(DRIVER_CLASS_NAME::equals),
                        "MariaDB JDBC service registration must name the driver"
                );
            }
'''
integration = replace_once(integration, old_stream, new_stream, "JDBC service stream lifetime")
integration_path.write_text(integration, encoding="utf-8")
