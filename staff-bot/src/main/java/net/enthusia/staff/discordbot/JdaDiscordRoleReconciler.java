package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/** JDA role mutation adapter. JDA remains responsible for Discord REST bucket/global rate limits. */
final class JdaDiscordRoleReconciler implements DiscordRoleReconciler {
    private static final Duration REST_TIMEOUT = Duration.ofSeconds(15);

    private final Guild guild;
    private final DiscordRoleSyncConfiguration configuration;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    JdaDiscordRoleReconciler(Guild guild, DiscordRoleSyncConfiguration configuration) {
        if (guild == null || configuration == null) {
            throw new IllegalArgumentException("role reconciler dependencies must be present");
        }
        this.guild = guild;
        this.configuration = configuration;
    }

    @Override
    public Result reconcile(DiscordRoleSyncService.Evaluation evaluation) {
        requireActive(Set.of());
        Member member = retrieveMember(evaluation);
        if (member == null) {
            return new Result(Set.of(), "MEMBER_ABSENT");
        }
        Set<String> observed = roleIds(member);
        requireActive(observed);
        DiscordRoleDelta delta = DiscordRoleDelta.calculate(
                evaluation.desiredRoleIds(), observed, configuration.managedRoleIds());
        if (configuration.mode() == DiscordRoleSyncConfiguration.Mode.SHADOW) {
            return new Result(observed, delta.empty() ? "SHADOW_MATCH" : "SHADOW_DRIFT");
        }
        if (delta.empty()) {
            return new Result(observed, "IN_SYNC");
        }
        return new Result(apply(member, delta, observed), "APPLIED");
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    private Member retrieveMember(DiscordRoleSyncService.Evaluation evaluation) {
        try {
            return submitAndAwait(
                    () -> guild.retrieveMemberById(evaluation.userId().value()).submit(),
                    "member_lookup_failed",
                    Set.of()
            );
        } catch (RetryableException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ErrorResponseException error
                    && error.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                return null;
            }
            throw exception;
        }
    }

    private Set<String> apply(Member member, DiscordRoleDelta delta, Set<String> observed) {
        MutationProgress progress = new MutationProgress(observed);
        for (String roleId : delta.add()) {
            requireActive(progress.snapshot());
            Role role = mutableRole(roleId, progress.snapshot());
            submitAndAwait(() -> guild.addRoleToMember(member, role).submit(),
                    "role_add_failed", progress.snapshot());
            progress.added(roleId);
        }
        for (String roleId : delta.remove()) {
            requireActive(progress.snapshot());
            Role role = mutableRole(roleId, progress.snapshot());
            submitAndAwait(() -> guild.removeRoleFromMember(member, role).submit(),
                    "role_remove_failed", progress.snapshot());
            progress.removed(roleId);
        }
        return progress.snapshot();
    }

    private void requireActive(Set<String> observed) {
        if (cancelled.get()) {
            throw new RetryableException("role_sync_cancelled", observed, null);
        }
    }

    private Role mutableRole(String roleId, Set<String> observed) {
        Role role = guild.getRoleById(roleId);
        if (role == null) {
            throw new RetryableException("managed_role_missing", observed, null);
        }
        boolean canInteract = guild.getSelfMember().canInteract(role);
        if (!DiscordRoleMutationPolicy.canMutate(role.isPublicRole(), role.isManaged(), canInteract)) {
            throw new RetryableException("role_hierarchy_blocked", observed, null);
        }
        return role;
    }

    private static Set<String> roleIds(Member member) {
        Set<String> roleIds = new LinkedHashSet<>();
        member.getRoles().forEach(role -> roleIds.add(role.getId()));
        return Set.copyOf(roleIds);
    }

    static <T> T submitAndAwait(
            Supplier<CompletableFuture<T>> submission,
            String errorCode,
            Set<String> observed
    ) {
        try {
            return await(submission.get(), errorCode, observed);
        } catch (RetryableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableException(errorCode, observed, exception);
        }
    }

    static <T> T await(CompletableFuture<T> future, String errorCode, Set<String> observed) {
        try {
            return future.get(REST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RetryableException(errorCode, observed, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new RetryableException(errorCode, observed, cause);
        } catch (TimeoutException exception) {
            throw new RetryableException(errorCode, observed, exception);
        }
    }

    static final class MutationProgress {
        private final Set<String> observed;

        MutationProgress(Set<String> initial) {
            if (initial == null) {
                throw new IllegalArgumentException("initial observed roles must be present");
            }
            observed = new LinkedHashSet<>(initial);
        }

        void added(String roleId) {
            observed.add(roleId);
        }

        void removed(String roleId) {
            observed.remove(roleId);
        }

        Set<String> snapshot() {
            return Set.copyOf(observed);
        }
    }
}
