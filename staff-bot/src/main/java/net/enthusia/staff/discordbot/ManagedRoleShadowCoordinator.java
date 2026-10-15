package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * Runs bounded, read-only parity scans for provider-neutral managed-role claims.
 *
 * <p>No Discord mutation exists in this class. A zero-drift summary is therefore evidence for the
 * migration window only; it is never authorization to remove DiscordSRV or enable enforcement.</p>
 */
final class ManagedRoleShadowCoordinator implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(ManagedRoleShadowCoordinator.class.getName());
    private static final int MAX_DRIFT_LOGS = 20;
    private static final int UNIQUE_ROLE_MATCH_COUNT = 1;
    private static final Duration MEMBER_LOAD_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(20);

    enum State {
        MATCH,
        DRIFT,
        ROLE_MISSING,
        ROLE_AMBIGUOUS,
        ROLE_UNMANAGEABLE,
        DELETE_MATCH,
        DELETE_DRIFT,
        DELETE_UNRESOLVED
    }

    record ClaimResult(
            String resourceId,
            State state,
            int desiredMembers,
            int observedMembers,
            int missingMembers,
            int extraMembers,
            int unlinkedMinecraftAccounts
    ) {
        ClaimResult {
            if (resourceId == null || resourceId.isBlank()
                    || state == null
                    || desiredMembers < 0
                    || observedMembers < 0
                    || missingMembers < 0
                    || extraMembers < 0
                    || unlinkedMinecraftAccounts < 0) {
                throw new IllegalArgumentException("managed-role shadow result is invalid");
            }
        }

        boolean matches() {
            return state == State.MATCH || state == State.DELETE_MATCH;
        }
    }

    enum SampleGate {
        ZERO_DRIFT_SAMPLE,
        SNAPSHOT_INCOMPLETE,
        NO_CLAIMS,
        ROLE_DRIFT,
        ROLE_ID_UNBOUND,
        ROLE_OWNERSHIP_CONFLICT
    }

    private record Summary(
            int claims,
            int matches,
            int drifts,
            int missingMembers,
            int extraMembers,
            int unlinkedMinecraftAccounts,
            int lumaClaims,
            int playtimeClaims,
            int unboundRoleClaims,
            int conflictingRoleIds,
            List<ClaimResult> driftDetails
    ) {
        Summary {
            driftDetails = List.copyOf(driftDetails);
        }
    }

    private final ManagedRoleShadowService service;
    private final StaffBotWorkerPool workers;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<Guild> guild = new AtomicReference<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean cycleInFlight = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object cycleMonitor = new Object();

    ManagedRoleShadowCoordinator(ManagedRoleShadowService service, StaffBotWorkerPool workers) {
        if (service == null || workers == null) {
            throw new IllegalArgumentException("managed-role shadow coordinator dependencies must be present");
        }
        this.service = service;
        this.workers = workers;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("staff-bot-managed-role-shadow").factory());
    }

    void enable(Guild activeGuild) {
        if (activeGuild == null || closed.get()) {
            throw new IllegalArgumentException("managed-role shadow guild must be present");
        }
        guild.set(activeGuild);
        scheduleOnce();
        requestCycle();
    }

    void disable() {
        guild.set(null);
    }

    private void scheduleOnce() {
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        long delay = service.configuration().interval().toSeconds();
        scheduler.scheduleWithFixedDelay(this::requestCycle, delay, delay, TimeUnit.SECONDS);
    }

    private void requestCycle() {
        if (closed.get() || guild.get() == null || !cycleInFlight.compareAndSet(false, true)) {
            return;
        }
        if (!workers.tryExecute(this::runCycle)) {
            completeCycle();
        }
    }

    private void runCycle() {
        try {
            Guild active = guild.get();
            if (active == null || closed.get()) {
                return;
            }
            ManagedRoleShadowService.Snapshot snapshot = service.snapshot();
            List<ManagedRoleShadowService.ResolvedClaim> claims = service.resolve(snapshot);
            List<Member> members = loadMembers(active);
            if (guild.get() != active || closed.get()) {
                return;
            }
            logSummary(claims, members, active, snapshot);
        } catch (RuntimeException exception) {
            logFailure(exception);
        } finally {
            completeCycle();
        }
    }

    private static List<Member> loadMembers(Guild guild) {
        try {
            return List.copyOf(guild.loadMembers()
                    .setTimeout(MEMBER_LOAD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .get());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("managed-role shadow member load failed", exception);
        }
    }

    private static void logSummary(
            List<ManagedRoleShadowService.ResolvedClaim> claims,
            List<Member> members,
            Guild guild,
            ManagedRoleShadowService.Snapshot snapshot
    ) {
        Summary summary = summarize(claims, members, guild);
        logTotals(summary, snapshot);
        logTruncation(snapshot.truncated());
        logInvalidClaims(snapshot.invalidResourceRefs(), summary.driftDetails().size());
        logDrifts(summary.driftDetails());
    }

    private static Summary summarize(
            List<ManagedRoleShadowService.ResolvedClaim> claims,
            List<Member> members,
            Guild guild
    ) {
        int matches = 0;
        int missingMembers = 0;
        int extraMembers = 0;
        int unlinked = 0;
        int lumaClaims = 0;
        int playtimeClaims = 0;
        int unboundRoleClaims = 0;
        List<ClaimResult> driftDetails = new ArrayList<>();
        for (ManagedRoleShadowService.ResolvedClaim claim : claims) {
            if ("luma-guilds".equals(claim.claim().namespace())) lumaClaims++;
            if ("playtime-numerals".equals(claim.claim().namespace())) playtimeClaims++;
            if (claim.claim().existingDiscordRoleId().isEmpty()) unboundRoleClaims++;
            ClaimResult result = compare(claim, members, guild);
            missingMembers += result.missingMembers();
            extraMembers += result.extraMembers();
            unlinked += result.unlinkedMinecraftAccounts();
            if (result.matches()) {
                matches++;
            } else if (driftDetails.size() < MAX_DRIFT_LOGS) {
                driftDetails.add(result);
            }
        }
        return new Summary(
                claims.size(),
                matches,
                claims.size() - matches,
                missingMembers,
                extraMembers,
                unlinked,
                lumaClaims,
                playtimeClaims,
                unboundRoleClaims,
                conflictingRoleIds(claims),
                driftDetails
        );
    }

    /** Count exact Discord role IDs claimed by more than one logical managed resource. */
    static int conflictingRoleIds(List<ManagedRoleShadowService.ResolvedClaim> claims) {
        if (claims == null) throw new IllegalArgumentException("claims required");
        Map<String, String> owners = new HashMap<>();
        Set<String> conflicts = new HashSet<>();
        for (ManagedRoleShadowService.ResolvedClaim resolved : claims) {
            var claim = resolved.claim();
            if (claim.existingDiscordRoleId().isEmpty()) continue;
            String id = claim.existingDiscordRoleId().orElseThrow();
            String owner = claim.namespace() + ":" + claim.reconciliationKey();
            String previous = owners.putIfAbsent(id, owner);
            if (previous != null && !previous.equals(owner)) conflicts.add(id);
        }
        return conflicts.size();
    }

    /**
     * Only a populated, complete, zero-drift snapshot is a usable parity sample.
     * This is never cutover approval: operator validation of all expected
     * producers, legacy role mappings, and multiple windows remains required.
     */
    static SampleGate assessSample(ManagedRoleShadowService.Snapshot snapshot,
            int processedClaims, int detectedDrifts, int unboundRoleClaims, int conflictingRoleIds) {
        if (snapshot == null || processedClaims < 0 || detectedDrifts < 0
                || unboundRoleClaims < 0 || unboundRoleClaims > processedClaims
                || conflictingRoleIds < 0 || conflictingRoleIds > processedClaims) {
            throw new IllegalArgumentException("managed-role sample inputs are invalid");
        }
        if (snapshot.incomplete()) return SampleGate.SNAPSHOT_INCOMPLETE;
        if (processedClaims == 0) return SampleGate.NO_CLAIMS;
        if (conflictingRoleIds > 0) return SampleGate.ROLE_OWNERSHIP_CONFLICT;
        if (detectedDrifts > 0) return SampleGate.ROLE_DRIFT;
        if (unboundRoleClaims > 0) return SampleGate.ROLE_ID_UNBOUND;
        return SampleGate.ZERO_DRIFT_SAMPLE;
    }

    private static void logTotals(Summary summary, ManagedRoleShadowService.Snapshot snapshot) {
        if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            int invalid = snapshot.invalidResourceRefs().size();
            SampleGate gate = assessSample(
                    snapshot, summary.claims(), summary.drifts(),
                    summary.unboundRoleClaims(), summary.conflictingRoleIds());
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "managed_role_shadow_summary complete={0} claims={1} matches={2} drift={3} "
                            + "missing_members={4} extra_members={5} unlinked_minecraft={6} invalid_claims={7} "
                            + "sample_gate={8} luma_claims={9} playtime_claims={10} unbound_role_claims={11} "
                            + "conflicting_role_ids={12}",
                    !snapshot.incomplete(),
                    summary.claims() + invalid,
                    summary.matches(),
                    summary.drifts() + invalid,
                    summary.missingMembers(),
                    summary.extraMembers(),
                    summary.unlinkedMinecraftAccounts(),
                    invalid,
                    gate,
                    summary.lumaClaims(),
                    summary.playtimeClaims(),
                    summary.unboundRoleClaims(),
                    summary.conflictingRoleIds()
            );
        }
    }

    private static void logTruncation(boolean truncated) {
        if (truncated && LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "managed_role_shadow_incomplete reason=claim_limit_exceeded");
        }
    }

    private static void logInvalidClaims(List<String> invalidResourceRefs, int existingDriftLogs) {
        int remaining = Math.max(0, MAX_DRIFT_LOGS - existingDriftLogs);
        for (int index = 0; index < Math.min(invalidResourceRefs.size(), remaining); index++) {
            if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "managed_role_shadow_drift resource={0} state=INVALID_CLAIM "
                                + "desired=0 observed=0 missing=0 extra=0 unlinked_minecraft=0",
                        invalidResourceRefs.get(index)
                );
            }
        }
    }

    private static void logDrifts(List<ClaimResult> driftDetails) {
        for (ClaimResult result : driftDetails) {
            logDrift(result);
        }
    }

    private static void logDrift(ClaimResult result) {
        if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "managed_role_shadow_drift resource={0} state={1} desired={2} observed={3} "
                            + "missing={4} extra={5} unlinked_minecraft={6}",
                    safeResource(result.resourceId()),
                    result.state(),
                    result.desiredMembers(),
                    result.observedMembers(),
                    result.missingMembers(),
                    result.extraMembers(),
                    result.unlinkedMinecraftAccounts()
            );
        }
    }

    static ClaimResult compare(
            ManagedRoleShadowService.ResolvedClaim resolved,
            List<Member> members,
            Guild guild
    ) {
        ManagedRoleShadowService.Claim claim = resolved.claim();
        if (claim.delete() && claim.displayName().isEmpty() && claim.existingDiscordRoleId().isEmpty()) {
            return result(resolved, State.DELETE_UNRESOLVED, Set.of(), Set.of());
        }
        // An exact legacy role that still exists but is Discord-managed or public is
        // not an absent role. In particular, a deletion tombstone must not become
        // a false DELETE_MATCH merely because this observer refuses to manage it.
        if (claim.existingDiscordRoleId().isPresent()) {
            Role exactRole = guild.getRoleById(claim.existingDiscordRoleId().orElseThrow());
            if (exactRole != null && !manageable(exactRole)) {
                State state = claim.delete() ? State.DELETE_UNRESOLVED : State.ROLE_UNMANAGEABLE;
                return result(resolved, state,
                        claim.delete() ? Set.of() : resolved.desiredDiscordUserIds(), Set.of());
            }
        }
        List<Role> roles = matchingRoles(guild, claim);
        if (roles.size() > UNIQUE_ROLE_MATCH_COUNT) {
            return result(resolved, State.ROLE_AMBIGUOUS, resolved.desiredDiscordUserIds(), Set.of());
        }
        if (roles.isEmpty()) {
            return compareMissingRole(resolved);
        }
        return compareExistingRole(resolved, members, roles.getFirst());
    }

    private static List<Role> matchingRoles(Guild guild, ManagedRoleShadowService.Claim claim) {
        if (claim.existingDiscordRoleId().isPresent()) {
            Role role = guild.getRoleById(claim.existingDiscordRoleId().orElseThrow());
            return role != null && manageable(role) ? List.of(role) : List.of();
        }
        return guild.getRoles().stream()
                .filter(role -> role.getName().equals(claim.displayName()))
                .filter(ManagedRoleShadowCoordinator::manageable)
                .toList();
    }

    private static boolean manageable(Role role) {
        return !role.isPublicRole() && !role.isManaged();
    }

    private static ClaimResult compareMissingRole(ManagedRoleShadowService.ResolvedClaim resolved) {
        State state = resolved.claim().delete() ? State.DELETE_MATCH : State.ROLE_MISSING;
        return result(resolved, state, resolved.desiredDiscordUserIds(), Set.of());
    }

    private static ClaimResult compareExistingRole(
            ManagedRoleShadowService.ResolvedClaim resolved,
            List<Member> members,
            Role role
    ) {
        Set<String> observed = holders(members, role);
        if (resolved.claim().delete()) {
            return result(resolved, State.DELETE_DRIFT, Set.of(), observed);
        }
        Set<String> desired = resolved.desiredDiscordUserIds();
        State state = desired.equals(observed) ? State.MATCH : State.DRIFT;
        return result(resolved, state, desired, observed);
    }

    private static ClaimResult result(
            ManagedRoleShadowService.ResolvedClaim resolved,
            State state,
            Set<String> desired,
            Set<String> observed
    ) {
        Set<String> missing = new LinkedHashSet<>(desired);
        missing.removeAll(observed);
        Set<String> extra = new LinkedHashSet<>(observed);
        extra.removeAll(desired);
        return new ClaimResult(
                resolved.claim().resourceId(),
                state,
                desired.size(),
                observed.size(),
                missing.size(),
                extra.size(),
                resolved.unlinkedMinecraftAccounts()
        );
    }

    private static Set<String> holders(List<Member> members, Role role) {
        Set<String> holders = new LinkedHashSet<>();
        String roleId = role.getId();
        for (Member member : members) {
            boolean present = member.getRoles().stream().anyMatch(candidate -> roleId.equals(candidate.getId()));
            if (present) {
                holders.add(member.getId());
            }
        }
        return Set.copyOf(holders);
    }

    private static String safeResource(String resourceId) {
        return resourceId.length() <= 12 ? resourceId : resourceId.substring(0, 12);
    }

    private static void logFailure(RuntimeException exception) {
        if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "managed_role_shadow_cycle_failed type={0}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void completeCycle() {
        cycleInFlight.set(false);
        synchronized (cycleMonitor) {
            cycleMonitor.notifyAll();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        disable();
        scheduler.shutdownNow();
        long deadline = System.nanoTime() + CLOSE_TIMEOUT.toNanos();
        synchronized (cycleMonitor) {
            while (cycleInFlight.get()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IllegalStateException(
                            "managed-role shadow cycle did not quiesce before shutdown timeout");
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(cycleMonitor, remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("managed-role shadow shutdown was interrupted", exception);
                }
            }
        }
    }
}
