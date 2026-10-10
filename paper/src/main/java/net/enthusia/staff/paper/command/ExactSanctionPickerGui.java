package net.enthusia.staff.paper.command;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.casefile.SanctionReview;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.CaseReviewStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionStatus;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.sanction.SanctionChangeAccess;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Exact-sanction picker. Unlike the older case-wide GUI, a click carries one
 * persisted sanction UUID and the review revision. It never modifies a case-wide selection.
 */
public final class ExactSanctionPickerGui implements Listener {
    static final int PAGE_SIZE = 45;
    private static final int BACK = 45;
    private static final int CLOSE = 49;
    private static final int NEXT = 53;
    private static final int CONFIRM = 50;
    private static final Duration INPUT_TTL = Duration.ofMinutes(3);
    private static final List<SanctionChangeAction> ACTIONS = List.of(
            SanctionChangeAction.REVOKE, SanctionChangeAction.END_EARLY,
            SanctionChangeAction.REDUCE_DURATION, SanctionChangeAction.FULL_OVERTURN
    );

    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<PlayerDirectory> players;
    private final Supplier<CaseReviewStore> cases;
    private final Supplier<SanctionChangeService> changes;
    private final AuthorizationPolicy authorization;
    private final ExecutorService workers;
    private final SanctionLifecycleCommand lifecycle;
    private final Map<UUID, UUID> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Capture> captures = new ConcurrentHashMap<>();
    private final Set<UUID> submitting = ConcurrentHashMap.newKeySet();

    public ExactSanctionPickerGui(
            JavaPlugin plugin, Clock clock,
            Supplier<PlayerDirectory> players, Supplier<CaseReviewStore> cases,
            Supplier<SanctionChangeService> changes, AuthorizationPolicy authorization,
            ExecutorService workers, SanctionLifecycleCommand lifecycle
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.players = java.util.Objects.requireNonNull(players);
        this.cases = java.util.Objects.requireNonNull(cases);
        this.changes = java.util.Objects.requireNonNull(changes);
        this.authorization = java.util.Objects.requireNonNull(authorization);
        this.workers = java.util.Objects.requireNonNull(workers);
        this.lifecycle = java.util.Objects.requireNonNull(lifecycle);
    }

    public void open(Player viewer, String playerName, String requestedAction) {
        open(viewer, playerName, requestedAction, Set.of(SanctionType.values()));
    }

    /** Alias-specific filters prevent /unwarn from showing bans and vice versa. */
    public void open(Player viewer, String playerName, String requestedAction, Set<SanctionType> allowedTypes) {
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            throw new IllegalArgumentException("At least one sanction type is required");
        }
        Set<SanctionType> selectedTypes = Set.copyOf(allowedTypes);
        if (!authorized(viewer, requestedAction)) {
            notice(viewer, "You do not have authority to change these punishments.");
            return;
        }
        UUID session = UUID.randomUUID();
        UUID viewerId = viewer.getUniqueId();
        sessions.put(viewerId, session);
        captures.remove(viewerId);
        submitting.remove(viewerId);
        try {
            workers.execute(() -> load(viewer, session, playerName, requestedAction, selectedTypes));
        } catch (RejectedExecutionException exception) {
            notice(viewer, "Moderation work queue is busy. No change was made.");
        }
    }

    private void load(Player viewer, UUID session, String playerName, String action,
            Set<SanctionType> allowedTypes) {
        try {
            PlayerDirectory directory = players.get();
            CaseReviewStore reviews = cases.get();
            if (directory == null || reviews == null) {
                notice(viewer, "Punishment history is unavailable.");
                return;
            }
            PlayerIdentity player = directory.find(playerName).orElse(null);
            if (player == null) {
                notice(viewer, "Player was not found. No changes were made.");
                return;
            }
            List<Entry> entries = new ArrayList<>();
            for (CaseReview review : reviews.recent(player.playerId(), 100)) {
                for (SanctionReview sanction : review.sanctions()) {
                    if (selectable(sanction, allowedTypes)) {
                        entries.add(new Entry(review, sanction));
                    }
                }
            }
            if (entries.isEmpty()) {
                notice(viewer, "No selectable punishments were found for " + playerName + '.');
                return;
            }
            List<Entry> immutable = List.copyOf(entries);
            onEntity(viewer, () -> {
                if (session.equals(sessions.get(viewer.getUniqueId())) && authorized(viewer, action)) {
                    show(viewer, new Listing(session, action, immutable, 0));
                }
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Exact punishment picker could not load history", exception);
            notice(viewer, "Punishment selection is unavailable. No changes were made.");
        }
    }

    /** Every entry is one sanction; aliases only expose matching punishment types. */
    static boolean selectable(SanctionReview sanction, Set<SanctionType> allowedTypes) {
        return sanction != null && allowedTypes != null
                && allowedTypes.contains(sanction.type())
                && sanction.status() != SanctionStatus.REVOKED
                && sanction.status() != SanctionStatus.OVERTURNED;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)
                || !(event.getView().getTopInventory().getHolder(false) instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!holder.owner.equals(viewer.getUniqueId())
                || !holder.session.equals(sessions.get(viewer.getUniqueId()))) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }
        if (slot == CLOSE) {
            viewer.closeInventory();
            return;
        }
        if (holder.state instanceof Listing state) {
            listingClick(viewer, state, slot);
        } else if (holder.state instanceof Choice state) {
            choiceClick(viewer, state, slot);
        } else if (holder.state instanceof Confirmation state) {
            confirmationClick(viewer, state, slot);
        }
    }

    private void listingClick(Player viewer, Listing state, int slot) {
        if (!authorized(viewer, state.action)) {
            viewer.closeInventory();
            return;
        }
        if (slot == BACK && state.page > 0) {
            show(viewer, new Listing(state.session, state.action, state.entries, state.page - 1));
        } else if (slot == NEXT && (state.page + 1) * PAGE_SIZE < state.entries.size()) {
            show(viewer, new Listing(state.session, state.action, state.entries, state.page + 1));
        } else {
            pageEntry(state.entries, state.page, slot).ifPresent(chosen -> {
                if ("change".equals(state.action)) {
                    show(viewer, new Choice(state, chosen));
                } else {
                    beginInput(viewer, new Selection(state, chosen, action(state.action)));
                }
            });
        }
    }

    /** Stable slot-to-record mapping even when a case contains multiple warnings. */
    static <T> Optional<T> pageEntry(List<T> entries, int page, int slot) {
        if (entries == null || page < 0 || slot < 0 || slot >= PAGE_SIZE) {
            return Optional.empty();
        }
        long index = (long) page * PAGE_SIZE + slot;
        return index < entries.size()
                ? Optional.of(entries.get((int) index)) : Optional.empty();
    }

    private void choiceClick(Player viewer, Choice state, int slot) {
        if (slot == BACK) {
            show(viewer, state.listing);
            return;
        }
        if (!authorized(viewer, "change") || slot < 0 || slot >= ACTIONS.size()) {
            return;
        }
        SanctionChangeAction chosen = ACTIONS.get(slot);
        if (allowed(viewer, chosen)) {
            beginInput(viewer, new Selection(state.listing, state.entry, chosen));
        }
    }

    private void confirmationClick(Player viewer, Confirmation state, int slot) {
        if (slot == BACK) {
            show(viewer, state.selection.listing);
            return;
        }
        if (slot != CONFIRM || !allowed(viewer, state.selection.action)) {
            return;
        }
        if (!submitting.add(viewer.getUniqueId())) {
            return;
        }
        viewer.closeInventory();
        // Do not trust an old inventory snapshot: validate the exact sanction revision
        // on a worker before delegating to the already audited exact lifecycle command.
        try {
            workers.execute(() -> submitSelected(viewer, state));
        } catch (RejectedExecutionException exception) {
            submitting.remove(viewer.getUniqueId());
            notice(viewer, "Moderation work queue is full. No change was submitted.");
        }
    }

    private void submitSelected(Player viewer, Confirmation state) {
        try {
            SanctionChangeService service = changes.get();
            if (service == null || service.exactRevision(state.selection.entry.sanction.sanctionId())
                    .stream().noneMatch(revision -> revision == state.selection.entry.sanction.revision())) {
                notice(viewer, "This punishment changed since it was displayed. Reopen the menu before trying again.");
                return;
            }
            onEntity(viewer, () -> {
                if (!state.selection.listing.session.equals(sessions.get(viewer.getUniqueId()))
                        || !allowed(viewer, state.selection.action)) {
                    return;
                }
                String[] args = exactArguments(state);
                // execute rechecks Staff Mode and rank; applyExact checks latest revision
                // and records the actor, reason, and exact sanction identity.
                lifecycle.executeSelected(viewer, "punish", args,
                        state.selection.entry.sanction.revision());
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Exact sanction preflight failed", exception);
            notice(viewer, "Punishment status could not be confirmed. Reopen its history before retrying.");
        }
    }

    static String[] exactArguments(Confirmation state) {
        Selection selection = state.selection;
        String command = switch (selection.action) {
            case REVOKE -> "revoke";
            case END_EARLY -> "end";
            case REDUCE_DURATION -> "reduce";
            case FULL_OVERTURN -> "overturn";
            default -> throw new IllegalArgumentException("Unsupported exact punishment action");
        };
        String sanctionId = selection.entry.sanction.sanctionId().toString();
        if (state.expiration.isPresent()) {
            return new String[]{"sanction", command, sanctionId,
                    state.expiration.orElseThrow().toString(), state.reason};
        }
        return new String[]{"sanction", command, sanctionId, state.reason};
    }

    private void beginInput(Player viewer, Selection selection) {
        if (!allowed(viewer, selection.action)) {
            notice(viewer, "You do not have permission to change this punishment.");
            return;
        }
        boolean duration = selection.action == SanctionChangeAction.REDUCE_DURATION;
        captures.put(viewer.getUniqueId(), new Capture(selection,
                duration ? Stage.DURATION : Stage.REASON, Optional.empty(),
                clock.instant().plus(INPUT_TTL)));
        viewer.closeInventory();
        viewer.sendMessage(StaffMessageStyle.info(duration
                ? "Type a shorter duration (e.g. 2d) or UTC expiration in chat, or 'cancel'."
                : "Type the private reason for changing only this punishment, or 'cancel'."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void chat(AsyncChatEvent event) {
        Player viewer = event.getPlayer();
        Capture capture = captures.remove(viewer.getUniqueId());
        if (capture == null) {
            return;
        }
        event.setCancelled(true);
        String value = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        onEntity(viewer, () -> acceptInput(viewer, capture, value));
    }

    private void acceptInput(Player viewer, Capture capture, String value) {
        if (!capture.selection.listing.session.equals(sessions.get(viewer.getUniqueId()))
                || clock.instant().isAfter(capture.expiresAt) || value.equalsIgnoreCase("cancel")) {
            notice(viewer, "Punishment change cancelled or expired.");
            return;
        }
        if (!allowed(viewer, capture.selection.action)) {
            notice(viewer, "You no longer have authority to change this punishment.");
            return;
        }
        if (capture.stage == Stage.DURATION) {
            Optional<Instant> parsed = new SanctionExpirationParser(clock).parse(value);
            if (parsed.isEmpty()) {
                notice(viewer, "Invalid duration. No change was made; reopen /punish to retry.");
                return;
            }
            captures.put(viewer.getUniqueId(), new Capture(capture.selection, Stage.REASON,
                    parsed, clock.instant().plus(INPUT_TTL)));
            viewer.sendMessage(StaffMessageStyle.info("Type the private audit reason in chat, or 'cancel'."));
            return;
        }
        if (value.isBlank() || value.length() > 2_000) {
            notice(viewer, "A written audit reason of 1–2000 characters is required.");
            return;
        }
        show(viewer, new Confirmation(capture.selection, capture.expiration, value));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sessions.remove(id);
        captures.remove(id);
        submitting.remove(id);
    }

    private void show(Player viewer, State state) {
        Holder holder = new Holder(viewer.getUniqueId(), state.session(), state);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Select exact punishment"));
        holder.inventory = inventory;
        if (state instanceof Listing listing) {
            for (int slot = 0; slot < PAGE_SIZE; slot++) {
                int index = listing.page * PAGE_SIZE + slot;
                if (index >= listing.entries.size()) {
                    break;
                }
                Entry entry = listing.entries.get(index);
                SanctionReview sanction = entry.sanction;
                inventory.setItem(slot, item(sanction.active() ? Material.PAPER : Material.MAP,
                        sanction.type().name() + " • " + entry.review.publicReason(), List.of(
                        "Status: " + sanction.status(),
                        "Issued: " + sanction.issuedAt(),
                        "Expires: " + sanction.expirationAt().map(Instant::toString).orElse("Never"),
                        "Case: " + entry.review.caseId(),
                        "Reference: ..." + sanction.sanctionId().toString().substring(28),
                        "Click to select this punishment"
                )));
            }
            if (listing.page > 0) inventory.setItem(BACK, item(Material.ARROW, "Previous page", List.of()));
            if ((listing.page + 1) * PAGE_SIZE < listing.entries.size()) {
                inventory.setItem(NEXT, item(Material.ARROW, "Next page", List.of()));
            }
        } else if (state instanceof Choice choice) {
            for (int index = 0; index < ACTIONS.size(); index++) {
                SanctionChangeAction action = ACTIONS.get(index);
                if (allowed(viewer, action)) {
                    inventory.setItem(index, item(Material.WRITABLE_BOOK,
                            action.name().replace('_', ' '), List.of(
                            choice.entry.sanction.type().name(),
                            choice.entry.review.publicReason(),
                            "Applies to this selected punishment only"
                    )));
                }
            }
            inventory.setItem(BACK, item(Material.ARROW, "Back to punishments", List.of()));
        } else if (state instanceof Confirmation confirm) {
            inventory.setItem(13, item(Material.WRITTEN_BOOK,
                    confirm.selection.entry.sanction.type().name() + " • "
                            + confirm.selection.entry.review.publicReason(), List.of(
                    "Status: " + confirm.selection.entry.sanction.status(),
                    "Issued: " + confirm.selection.entry.sanction.issuedAt(),
                    "Selected action: " + confirm.selection.action,
                    "New expiry: " + confirm.expiration.map(Instant::toString).orElse("Unchanged"),
                    "Reason: " + confirm.reason
            )));
            inventory.setItem(CONFIRM, item(Material.LIME_CONCRETE,
                    "Confirm selected punishment", List.of("Applies only to the selected sanction")));
            inventory.setItem(BACK, item(Material.ARROW, "Back (discard input)", List.of()));
        }
        inventory.setItem(CLOSE, item(Material.BARRIER, "Cancel", List.of()));
        viewer.openInventory(inventory);
    }

    private boolean authorized(Player viewer, String action) {
        Actor actor = PaperActorResolver.resolve(viewer).orElse(null);
        if (actor == null || !actor.id().equals(viewer.getUniqueId())) return false;
        if ("change".equals(action)) return ACTIONS.stream().anyMatch(next -> permitted(viewer, actor, next));
        return permitted(viewer, actor, action(action));
    }

    private boolean allowed(Player viewer, SanctionChangeAction action) {
        Actor actor = PaperActorResolver.resolve(viewer).orElse(null);
        return actor != null && actor.id().equals(viewer.getUniqueId())
                && permitted(viewer, actor, action);
    }

    private boolean permitted(Player viewer, Actor actor, SanctionChangeAction action) {
        return hasActionPermissions(viewer::hasPermission, action)
                && authorization.permits(actor, action.requiredModerationAction());
    }

    /** Both UI and exact-write permissions are required before offering an action. */
    static boolean hasActionPermissions(Predicate<String> hasPermission, SanctionChangeAction action) {
        String exactPermission = switch (action) {
            case REVOKE -> SanctionLifecycleCommand.REVOKE_PERMISSION;
            case END_EARLY -> SanctionLifecycleCommand.END_PERMISSION;
            case REDUCE_DURATION -> SanctionLifecycleCommand.REDUCE_PERMISSION;
            case FULL_OVERTURN -> SanctionLifecycleCommand.OVERTURN_PERMISSION;
            default -> throw new IllegalArgumentException("Unsupported exact punishment action");
        };
        return hasPermission.test(SanctionChangeAccess.permissionFor(action))
                && hasPermission.test(exactPermission);
    }

    static SanctionChangeAction action(String action) {
        return switch (action) {
            case "remove", "unpunish", "revoke" -> SanctionChangeAction.REVOKE;
            case "end" -> SanctionChangeAction.END_EARLY;
            case "reduce" -> SanctionChangeAction.REDUCE_DURATION;
            default -> throw new IllegalArgumentException("Unsupported punishment picker action");
        };
    }

    private void notice(Player viewer, String message) {
        onEntity(viewer, () -> viewer.sendMessage(StaffMessageStyle.style(
                Component.text(message, NamedTextColor.YELLOW))));
    }

    private void onEntity(Player viewer, Runnable action) {
        viewer.getScheduler().execute(plugin, action, null, 1L);
    }

    private static ItemStack item(Material type, String name, List<String> lore) {
        ItemStack stack = ItemStack.of(type);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private sealed interface State permits Listing, Choice, Confirmation {
        UUID session();
    }
    private record Entry(CaseReview review, SanctionReview sanction) { }
    private record Listing(UUID session, String action, List<Entry> entries, int page) implements State { }
    private record Choice(Listing listing, Entry entry) implements State {
        @Override public UUID session() { return listing.session; }
    }
    private record Selection(Listing listing, Entry entry, SanctionChangeAction action) { }
    private record Confirmation(Selection selection, Optional<Instant> expiration, String reason) implements State {
        @Override public UUID session() { return selection.listing.session; }
    }
    private enum Stage { DURATION, REASON }
    private record Capture(Selection selection, Stage stage, Optional<Instant> expiration, Instant expiresAt) { }
    private static final class Holder implements InventoryHolder {
        final UUID owner;
        final UUID session;
        final State state;
        Inventory inventory;
        Holder(UUID owner, UUID session, State state) {
            this.owner = owner;
            this.session = session;
            this.state = state;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
