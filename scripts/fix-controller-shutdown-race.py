from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


test_path = Path(
    "paper/src/test/java/net/enthusia/staff/paper/alert/PunishmentRequestAlertControllerTest.java"
)
test = test_path.read_text(encoding="utf-8")
test = replace_once(
    test,
    '''        Thread shutdown = Thread.ofPlatform().start(controller::close);
        factory.releaseReplacement.countDown();
''',
    '''        Thread shutdown = Thread.ofPlatform().start(controller::close);
        assertTrue(awaitState(shutdown, Thread.State.BLOCKED, Duration.ofSeconds(5)));
        factory.releaseReplacement.countDown();
''',
    "deterministic shutdown fence",
)
anchor = '''    private static PunishmentRequestAlertController controller(
'''
helper = '''    private static boolean awaitState(
            Thread thread,
            Thread.State expected,
            Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (thread.getState() == expected) {
                return true;
            }
            Thread.sleep(1L);
        }
        return thread.getState() == expected;
    }

'''
test = replace_once(test, anchor, helper + anchor, "thread-state helper")
test_path.write_text(test, encoding="utf-8")

coordinator_path = Path(
    "paper/src/main/java/net/enthusia/staff/paper/config/reload/ConfigurationReloadCoordinator.java"
)
coordinator = coordinator_path.read_text(encoding="utf-8")
coordinator = replace_once(
    coordinator,
    "        return finish(candidate, previousConfiguration, previousPolicies, policiesChanged, alertResult);\n",
    "        return finish(candidate, previousConfiguration, policiesChanged, alertResult);\n",
    "finish invocation cleanup",
)
coordinator = replace_once(
    coordinator,
    '''    private ConfigurationReloadResult finish(
            Candidate candidate,
            PaperConfigurationSnapshot previousConfiguration,
            ReasonPolicyState previousPolicies,
            boolean policiesChanged,
            PunishmentRequestAlertController.ApplyResult alertResult
    ) {
''',
    '''    private ConfigurationReloadResult finish(
            Candidate candidate,
            PaperConfigurationSnapshot previousConfiguration,
            boolean policiesChanged,
            PunishmentRequestAlertController.ApplyResult alertResult
    ) {
''',
    "unused policy-state parameter cleanup",
)
coordinator = replace_once(
    coordinator,
    "                    publishAccepted(candidate, previousConfiguration, previousPolicies, policiesChanged, alertResult);\n",
    "                    publishAccepted(candidate, previousConfiguration, policiesChanged, alertResult);\n",
    "accepted publication invocation cleanup",
)
coordinator = replace_once(
    coordinator,
    '''    private ConfigurationReloadResult publishAccepted(
            Candidate candidate,
            PaperConfigurationSnapshot previousConfiguration,
            ReasonPolicyState previousPolicies,
            boolean policiesChanged,
            PunishmentRequestAlertController.ApplyResult alertResult
    ) {
''',
    '''    private ConfigurationReloadResult publishAccepted(
            Candidate candidate,
            PaperConfigurationSnapshot previousConfiguration,
            boolean policiesChanged,
            PunishmentRequestAlertController.ApplyResult alertResult
    ) {
''',
    "unused accepted policy-state parameter cleanup",
)
coordinator_path.write_text(coordinator, encoding="utf-8")
