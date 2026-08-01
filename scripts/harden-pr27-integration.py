from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    matches = text.count(old)
    if matches != 1:
        raise RuntimeError(f"{label}: expected one match, found {matches}")
    return text.replace(old, new, 1)


path = Path("paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java")
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    "        attachPunishmentRequestAlertStorage(bindings);\n",
    "        schedulePunishmentRequestAlertStorageAttachment(bindings);\n",
    "scheduled alert attachment call",
)
old_method = '''    private void attachPunishmentRequestAlertStorage(PaperStorageBindings bindings) {
        if (alertController == null || lifecycle.stopping()) {
            return;
        }
        PunishmentRequestAlertController.ApplyResult result = alertController.attachStorage(
                new PunishmentRequestAlertController.Storage(
                        bindings.punishmentRequestAlertStore(),
                        bindings.punishmentRequestStore(),
                        bindings.playerDirectory()
                )
        );
        if (result.failure() != null) {
            getLogger().log(Level.SEVERE, "Punishment-request alert subsystem startup failed", result.failure());
        }
    }

'''
new_method = '''    private void schedulePunishmentRequestAlertStorageAttachment(PaperStorageBindings bindings) {
        if (alertController == null || lifecycle.stopping()) {
            return;
        }
        try {
            getServer().getGlobalRegionScheduler().execute(this, () -> {
                if (lifecycle.stopping()
                        || lifecycle.storage().filter(current -> current == bindings).isEmpty()) {
                    return;
                }
                PunishmentRequestAlertController.ApplyResult result = alertController.attachStorage(
                        new PunishmentRequestAlertController.Storage(
                                bindings.punishmentRequestAlertStore(),
                                bindings.punishmentRequestStore(),
                                bindings.playerDirectory()
                        )
                );
                if (result.failure() != null) {
                    getLogger().log(
                            Level.SEVERE,
                            "Punishment-request alert subsystem startup failed",
                            result.failure()
                    );
                }
            });
        } catch (RuntimeException exception) {
            featureIssues.put(
                    "punishment-request-alerts",
                    "Alert startup could not be scheduled on the global region thread"
            );
            refreshHealth(mode.get());
            getLogger().log(Level.SEVERE, "Punishment-request alert startup scheduling failed", exception);
        }
    }

'''
path.write_text(
    replace_once(text, old_method, new_method, "global-thread alert attachment"),
    encoding="utf-8",
)
