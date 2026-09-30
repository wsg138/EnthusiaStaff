package net.enthusia.staff.paper.integration;

import dev.rosewood.rosechat.api.staff.AutomatedModerationResult;
import dev.rosewood.rosechat.api.staff.AutomatedPublicMuteRequest;
import dev.rosewood.rosechat.api.staff.RoseChatAutomatedModerationService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.enforcement.MuteEnforcementListener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

final class RoseChatAutomatedModerationProvider implements RoseChatAutomatedModerationService, AutoCloseable {
    static final String REASON_ID = "chat.ai-moderation";
    private static final Duration REQUIRED_MUTE = Duration.ofDays(30);
    private static final Set<SanctionType> PUBLIC_MUTE_TYPES = Set.of(SanctionType.PUBLIC_MUTE);
    private static final Actor SYSTEM_ACTOR = new Actor(
            new UUID(0L, 0L),
            "Enthusia AI Moderation",
            StaffRank.SYSTEM
    );
    private static final ReasonPolicy POLICY = new ReasonPolicy(
            REASON_ID,
            "chat-ai",
            "Repeated AI-enforced public chat violation",
            70,
            true,
            List.of(new PunishmentStep(
                    0,
                    "30 day public mute",
                    List.of(new SanctionSpec(SanctionType.PUBLIC_MUTE, SanctionLength.temporary(REQUIRED_MUTE)))
            )),
            List.of(),
            true,
            true,
            false,
            StaffRank.MOD,
            true,
            AltInheritanceMode.NONE
    );

    private final JavaPlugin plugin;
    private final ServicesManager services;
    private final Supplier<OperationalMode> mode;
    private final Supplier<PunishmentService> punishments;
    private final AtomicReasonPolicyRepository reasons;
    private final Supplier<MuteEnforcementListener> mutes;
    private boolean closed;

    RoseChatAutomatedModerationProvider(
            JavaPlugin plugin,
            ServicesManager services,
            Supplier<OperationalMode> mode,
            Supplier<PunishmentService> punishments,
            AtomicReasonPolicyRepository reasons,
            Supplier<MuteEnforcementListener> mutes
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.services = Objects.requireNonNull(services, "services");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.punishments = Objects.requireNonNull(punishments, "punishments");
        this.reasons = Objects.requireNonNull(reasons, "reasons");
        this.mutes = Objects.requireNonNull(mutes, "mutes");
        ensurePolicy();
        services.register(RoseChatAutomatedModerationService.class, this, plugin, ServicePriority.Normal);
    }

    static ReasonPolicy policy() {
        return POLICY;
    }

    @Override
    public synchronized AutomatedModerationResult applyPublicMute(AutomatedPublicMuteRequest request) {
        Objects.requireNonNull(request, "request");
        if (closed) {
            return AutomatedModerationResult.unavailable("EnthusiaStaff AI moderation provider is closed");
        }
        String rejection = AutomatedModerationRequestPolicy.rejectionReason(request);
        if (rejection != null) {
            return AutomatedModerationResult.rejected(rejection);
        }
        PunishmentService punishmentService = punishments.get();
        OperationalMode currentMode = mode.get();
        if (punishmentService == null || currentMode == null) {
            return AutomatedModerationResult.unavailable("EnthusiaStaff punishment service is not ready");
        }
        try {
            if (!punishmentService.activeSanctions(
                    request.targetId(),
                    PUBLIC_MUTE_TYPES,
                    Instant.now()
            ).isEmpty()) {
                MuteEnforcementListener enforcement = mutes.get();
                if (enforcement != null) {
                    enforcement.invalidate(request.targetId());
                }
                return AutomatedModerationResult.applied("an active AI public mute already exists");
            }
            ensurePolicy();
            PunishmentResult result = punishmentService.create(
                    new CreatePunishmentRequest(
                            new IdempotencyKey(request.idempotencyKey()),
                            request.targetId(),
                            SYSTEM_ACTOR,
                            REASON_ID,
                            AutomatedModerationEvidenceFormatter.format(request),
                            CaseVisibility.PUBLIC,
                            List.of()
                    ),
                    currentMode
            );
            if (result instanceof PunishmentResult.Accepted accepted) {
                MuteEnforcementListener enforcement = mutes.get();
                if (enforcement != null) {
                    enforcement.invalidate(request.targetId());
                }
                return AutomatedModerationResult.applied(
                        "case=" + accepted.caseId() + (accepted.replayed() ? " (idempotent replay)" : "")
                );
            }
            PunishmentResult.Rejected rejected = (PunishmentResult.Rejected) result;
            return AutomatedModerationResult.rejected(rejected.code() + ": " + rejected.message());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning(
                    "RoseChat AI moderation mute request failed: " + exception.getClass().getSimpleName()
            );
            return AutomatedModerationResult.unavailable("EnthusiaStaff could not apply the mute");
        }
    }

    private void ensurePolicy() {
        synchronized (reasons) {
            ReasonPolicy existing = reasons.find(REASON_ID).orElse(null);
            if (existing != null) {
                if (!POLICY.equals(existing)) {
                    throw new IllegalStateException("Configured " + REASON_ID + " policy does not match the built-in AI policy");
                }
                return;
            }
            AtomicReasonPolicyRepository.PolicySnapshot snapshot = reasons.snapshot();
            List<ReasonPolicy> policies = new ArrayList<>(snapshot.policies());
            policies.add(POLICY);
            reasons.replace(
                    snapshot.version() + "+rosechat-ai-v1",
                    policies,
                    snapshot.aliases(),
                    snapshot.removedReasons()
            );
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        services.unregister(RoseChatAutomatedModerationService.class, this);
    }
}
