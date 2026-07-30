from pathlib import Path

PATH = Path("paper/src/main/java/net/enthusia/staff/paper/command/InspectCommand.java")


def replace_once(text: str, old: str, new: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match, found {count}: {old[:100]!r}")
    return text.replace(old, new, 1)


text = PATH.read_text(encoding="utf-8")
text = replace_once(
    text,
    '''public final class InspectCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
''',
    '''public final class InspectCommand implements CommandExecutor, TabCompleter {
    private static final String INSPECT_PERMISSION = "enthusiastaff.inspect";
    private static final String INVENTORY_VIEW_PERMISSION = "enthusiastaff.inventory.view";

    private final JavaPlugin plugin;
''',
)
text = replace_once(
    text,
    '''    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player viewer)) {
''',
    '''    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!CommandPermissionGate.require(
                sender,
                INSPECT_PERMISSION,
                "You do not have permission to inspect players."
        )) {
            return true;
        }
        if (!(sender instanceof Player viewer)) {
''',
)
text = replace_once(
    text,
    '''        if (arguments.length == 2
                && (arguments[0].equalsIgnoreCase("inventory")
                || arguments[0].equalsIgnoreCase("ender"))) {
            submitOrMessage(
''',
    '''        if (arguments.length == 2
                && (arguments[0].equalsIgnoreCase("inventory")
                || arguments[0].equalsIgnoreCase("ender"))) {
            if (!CommandPermissionGate.require(
                    viewer,
                    INVENTORY_VIEW_PERMISSION,
                    "You do not have permission to inspect inventories."
            )) {
                return true;
            }
            submitOrMessage(
''',
)
text = replace_once(
    text,
    '''    ) {
        if (arguments.length == 1) {
            List<String> actions = new ArrayList<>(List.of("inventory", "ender"));
''',
    '''    ) {
        if (!CommandPermissionGate.allows(sender::hasPermission, INSPECT_PERMISSION)) {
            return List.of();
        }
        if (arguments.length == 1) {
            List<String> actions = new ArrayList<>();
            if (CommandPermissionGate.allows(sender::hasPermission, INVENTORY_VIEW_PERMISSION)) {
                actions.add("inventory");
                actions.add("ender");
            }
''',
)
text = replace_once(
    text,
    '''    private boolean visibleTargetSubcommand(CommandSender sender, String input) {
        if (input.equalsIgnoreCase("inventory") || input.equalsIgnoreCase("ender")) {
            return true;
        }
''',
    '''    private boolean visibleTargetSubcommand(CommandSender sender, String input) {
        if (input.equalsIgnoreCase("inventory") || input.equalsIgnoreCase("ender")) {
            return CommandPermissionGate.allows(sender::hasPermission, INVENTORY_VIEW_PERMISSION);
        }
''',
)
PATH.write_text(text, encoding="utf-8")
