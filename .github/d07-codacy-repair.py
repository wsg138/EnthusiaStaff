from pathlib import Path
import textwrap


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    Path(path).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


# 1) Keep service construction below Codacy/Lizard's 8-parameter threshold.
path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentService.java"
text = read(path)
marker = '''    record MutationResult(UUID punishmentId, DiscordPunishmentState state, boolean replayed) {
    }

'''
dependencies = '''    record Dependencies(
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            DiscordPunishmentAuthorization authorization,
            DiscordPunishmentConfirmationStore confirmations,
            DiscordPunishmentRepository punishments,
            BiFunction<DiscordUserId, Instant, ModerationSubjectId> subjects
    ) {
        Dependencies {
            requireDependency(reads);
            requireDependency(actors);
            requireDependency(authorization);
            requireDependency(confirmations);
            requireDependency(punishments);
            requireDependency(subjects);
        }
    }

'''
text = replace_once(text, marker, marker + dependencies, "service dependencies record")
old = '''    DiscordPunishmentService(
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            DiscordPunishmentAuthorization authorization,
            DiscordPunishmentConfirmationStore confirmations,
            DiscordPunishmentRepository punishments,
            BiFunction<DiscordUserId, Instant, ModerationSubjectId> subjects,
            DiscordPunishmentGateway gateway,
            DiscordGuildId guildId,
            Clock clock
    ) {
        requireDependency(reads);
        requireDependency(actors);
        requireDependency(authorization);
        requireDependency(confirmations);
        requireDependency(punishments);
        requireDependency(subjects);
        requireDependency(gateway);
        requireDependency(guildId);
        requireDependency(clock);
        this.reads = reads;
        this.actors = actors;
        this.authorization = authorization;
        this.confirmations = confirmations;
        this.punishments = punishments;
        this.subjects = subjects;
        this.gateway = gateway;
        this.guildId = guildId;
        this.clock = clock;
    }
'''
new = '''    DiscordPunishmentService(
            Dependencies dependencies,
            DiscordPunishmentGateway gateway,
            DiscordGuildId guildId,
            Clock clock
    ) {
        requireDependency(dependencies);
        requireDependency(gateway);
        requireDependency(guildId);
        requireDependency(clock);
        this.reads = dependencies.reads();
        this.actors = dependencies.actors();
        this.authorization = dependencies.authorization();
        this.confirmations = dependencies.confirmations();
        this.punishments = dependencies.punishments();
        this.subjects = dependencies.subjects();
        this.gateway = gateway;
        this.guildId = guildId;
        this.clock = clock;
    }
'''
text = replace_once(text, old, new, "service constructor grouping")
write(path, text)

path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentRuntime.java"
text = read(path)
old = '''        return new DiscordPunishmentService(
                reads, actors, authorization, confirmations, persistence.punishments(),
                persistence::ensureDiscordSubject, gateway, guildId, clock
        );
'''
new = '''        DiscordPunishmentService.Dependencies dependencies = new DiscordPunishmentService.Dependencies(
                reads,
                actors,
                authorization,
                confirmations,
                persistence.punishments(),
                persistence::ensureDiscordSubject
        );
        return new DiscordPunishmentService(dependencies, gateway, guildId, clock);
'''
text = replace_once(text, old, new, "runtime service construction")
write(path, text)

# 2) Group restriction-specific command input so the public adapter method remains bounded.
path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentCommandController.java"
text = read(path)
marker = '''    record Committed(String content) {
    }

'''
record = '''    record RestrictionInput(String scopeKind, String scopeId, String mode) {
        RestrictionInput {
            if (scopeKind == null || scopeId == null || mode == null) {
                throw new IllegalArgumentException("restriction input fields must be present");
            }
        }
    }

'''
text = replace_once(text, marker, marker + record, "restriction input record")
old = '''    Prepared restrict(
            long actorId,
            String actorName,
            long targetId,
            String duration,
            String reason,
            String explanation,
            String scopeKind,
            String scopeId,
            String mode
    ) {
        DiscordDurationParser.Parsed parsed = durations.parse(duration, true);
        DiscordRestrictionTarget restriction = new DiscordRestrictionTarget(
                restrictionKind(scopeKind),
                scopeId,
                restrictionMode(mode)
        );
'''
new = '''    Prepared restrict(
            long actorId,
            String actorName,
            long targetId,
            String duration,
            String reason,
            String explanation,
            RestrictionInput input
    ) {
        if (input == null) {
            throw new IllegalArgumentException("restriction input must be present");
        }
        DiscordDurationParser.Parsed parsed = durations.parse(duration, true);
        DiscordRestrictionTarget restriction = new DiscordRestrictionTarget(
                restrictionKind(input.scopeKind()),
                input.scopeId(),
                restrictionMode(input.mode())
        );
'''
text = replace_once(text, old, new, "bounded restriction command input")
write(path, text)

path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaStaffModerationListener.java"
text = read(path)
old = '''            case RESTRICT -> () -> punishment.restrict(
                    actorId, actorName, targetId, option(event, DURATION_OPTION, ""), reason, explanation,
                    option(event, SCOPE_KIND_OPTION, ""), option(event, SCOPE_ID_OPTION, ""),
                    option(event, MODE_OPTION, ""));
'''
new = '''            case RESTRICT -> () -> punishment.restrict(
                    actorId, actorName, targetId, option(event, DURATION_OPTION, ""), reason, explanation,
                    restrictionInput(event));
'''
text = replace_once(text, old, new, "listener restriction DTO call")
marker = '''    @Override
    public void onUserContextInteraction(UserContextInteractionEvent event) {
'''
helper = '''    private static DiscordPunishmentCommandController.RestrictionInput restrictionInput(
            SlashCommandInteractionEvent event
    ) {
        return new DiscordPunishmentCommandController.RestrictionInput(
                option(event, SCOPE_KIND_OPTION, ""),
                option(event, SCOPE_ID_OPTION, ""),
                option(event, MODE_OPTION, "")
        );
    }

'''
text = replace_once(text, marker, helper + marker, "listener restriction DTO helper")
write(path, text)

# 3) Move large worker fakes out of the test class so each test source stays focused and bounded.
path = "staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordPunishmentWorkerTest.java"
text = read(path)
text = text.replace("FakeRepository", "DiscordPunishmentWorkerFakeRepository")
text = text.replace("FakeGateway", "DiscordPunishmentWorkerFakeGateway")
gateway_marker = '''    private static final class DiscordPunishmentWorkerFakeGateway implements DiscordPunishmentGateway {
'''
start = text.index(gateway_marker)
outer_close = text.rfind("\n}")
if outer_close <= start:
    raise SystemExit("worker test support extraction: outer close not found")
nested = text[start:outer_close]
text = text[:start] + "}\n"
write(path, text)

support = textwrap.dedent(nested)
support = support.replace(
    "private static final class DiscordPunishmentWorkerFakeGateway implements DiscordPunishmentGateway",
    "final class DiscordPunishmentWorkerFakeGateway implements DiscordPunishmentGateway",
    1,
)
support = support.replace(
    "private static final class DiscordPunishmentWorkerFakeRepository implements DiscordPunishmentRepository",
    "final class DiscordPunishmentWorkerFakeRepository implements DiscordPunishmentRepository",
    1,
)
# Package-private fixture state is intentional: the sibling test class asserts durable state directly.
support_lines = []
for line in support.splitlines():
    if line.startswith("    private "):
        line = "    " + line[len("    private "):]
    support_lines.append(line)
support = "\n".join(support_lines) + "\n"

imports = '''package net.enthusia.staff.discordbot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPermissionSnapshot;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository;

'''
write(
    "staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordPunishmentWorkerTestSupport.java",
    imports + support,
)
