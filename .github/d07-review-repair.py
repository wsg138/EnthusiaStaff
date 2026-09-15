from pathlib import Path


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    Path(path).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


# Fail closed before permission-override mutation and keep post-confirmation member absence recoverable.
path = "staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaDiscordPunishmentGateway.java"
text = read(path)
text = replace_once(
    text,
    '    private static final String TARGET_NOT_IN_GUILD = "TARGET_NOT_IN_GUILD";\n',
    '    private static final String TARGET_NOT_IN_GUILD = "TARGET_NOT_IN_GUILD";\n'
    '    private static final String CHANNEL_PERMISSION_MANAGE_DENIED = "CHANNEL_PERMISSION_MANAGE_DENIED";\n',
    "gateway permission error constant",
)
text = replace_once(
    text,
    '            case CHANNEL_RESTRICTION -> restrictionContainer(guild, intent.restriction().orElseThrow());\n',
    '            case CHANNEL_RESTRICTION -> requireRestrictionAvailable(\n'
    '                    guild, intent.restriction().orElseThrow());\n',
    "restriction preflight permission fence",
)
text = replace_once(
    text,
    '''            IPermissionContainer container = restrictionContainer(
                    guild, punishment.intent().restriction().orElseThrow()
            );
            return snapshot(container.getPermissionOverride(member));
''',
    '''            IPermissionContainer container = restrictionContainer(
                    guild, punishment.intent().restriction().orElseThrow()
            );
            requireManagePermissions(guild, container);
            return snapshot(container.getPermissionOverride(member));
''',
    "restriction snapshot permission fence",
)
text = replace_once(
    text,
    '''        Role role = requireMuteRole(guild);
        muteOwnership.requirePermissions(guild);
        JdaMuteRoleOwnership.requireFreshRoleAbsent(member.getRoles().contains(role));
''',
    '''        Role role = requireMuteRole(guild);
        muteOwnership.requirePermissions(guild);
        requireMutePolicyPermissions(guild);
        JdaMuteRoleOwnership.requireFreshRoleAbsent(member.getRoles().contains(role));
''',
    "mute preflight channel permission fence",
)
text = replace_once(
    text,
    '''    private void ensureMutePolicy(Guild guild) {
        Role role = requireMuteRole(guild);
        for (GuildChannel channel : guild.getChannels()) {
''',
    '''    private void ensureMutePolicy(Guild guild) {
        Role role = requireMuteRole(guild);
        requireMutePolicyPermissions(guild);
        for (GuildChannel channel : guild.getChannels()) {
''',
    "mute mutation all-channel fence",
)
marker = '''    private void enforceMuteOverride(GuildChannel channel, IPermissionContainer container, Role role) {
'''
helper = '''    private static void requireMutePolicyPermissions(Guild guild) {
        for (GuildChannel channel : guild.getChannels()) {
            if (channel instanceof IPermissionContainer) {
                requireManagePermissions(guild, channel);
            }
        }
    }

'''
text = replace_once(text, marker, helper + marker, "mute channel permission helper")

# The restriction mutation paths each resolve their container independently.
apply_old = '''        IPermissionContainer container = restrictionContainer(guild, target);
        DiscordPermissionSnapshot original = punishment.previousRestriction().orElseThrow(
                () -> failure("RESTRICTION_SNAPSHOT_MISSING", false)
        );
'''
apply_new = '''        IPermissionContainer container = restrictionContainer(guild, target);
        requireManagePermissions(guild, container);
        DiscordPermissionSnapshot original = punishment.previousRestriction().orElseThrow(
                () -> failure("RESTRICTION_SNAPSHOT_MISSING", false)
        );
'''
if text.count(apply_old) != 2:
    raise SystemExit(
        f"restriction apply/remove permission fences: expected 2 matches, found {text.count(apply_old)}"
    )
text = text.replace(apply_old, apply_new)

marker = '''    private IPermissionContainer restrictionContainer(Guild guild, DiscordRestrictionTarget target) {
'''
helper = '''    private void requireRestrictionAvailable(Guild guild, DiscordRestrictionTarget target) {
        IPermissionContainer container = restrictionContainer(guild, target);
        requireManagePermissions(guild, container);
    }

    private static void requireManagePermissions(Guild guild, GuildChannel channel) {
        requirePermissionManagement(guild.getSelfMember().hasPermission(channel, Permission.MANAGE_PERMISSIONS));
    }

    static void requirePermissionManagement(boolean permitted) {
        if (!permitted) {
            throw failure(CHANNEL_PERMISSION_MANAGE_DENIED, false);
        }
    }

'''
text = replace_once(text, marker, helper + marker, "restriction permission helpers")
text = replace_once(
    text,
    '''        if (member == null) {
            throw failure(TARGET_NOT_IN_GUILD, false);
        }
        return member;
''',
    '''        if (member == null) {
            throw failure(TARGET_NOT_IN_GUILD, true);
        }
        return member;
''',
    "post-confirmation member absence retryability",
)
write(path, text)

# Deterministic permission-denial adapter proof without a live guild dependency.
path = "staff-bot/src/test/java/net/enthusia/staff/discordbot/JdaDiscordPunishmentGatewayTest.java"
text = read(path)
marker = '''    @Test
    void applyRetryRequiresExactBotTargetAndPunishmentMarker() {
'''
test = '''    @Test
    void channelPermissionManagementFailsClosedBeforeMutation() {
        DiscordPunishmentGateway.EffectException failure = assertThrows(
                DiscordPunishmentGateway.EffectException.class,
                () -> JdaDiscordPunishmentGateway.requirePermissionManagement(false)
        );

        assertEquals("CHANNEL_PERMISSION_MANAGE_DENIED", failure.errorCode());
        assertFalse(failure.retryable());
        assertDoesNotThrow(() -> JdaDiscordPunishmentGateway.requirePermissionManagement(true));
    }

'''
text = replace_once(text, marker, test + marker, "permission fence regression test")
write(path, text)

# Approval must pass the surface approval-rank check and still fail on the concrete sanction.
path = "staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordPunishmentAuthorizationTest.java"
text = read(path)
old = '''    @Test
    void moderatorAndDeveloperCannotApproveAdminOnlyPermanentBan() {
        Actor requester = actor(StaffRank.ADMIN);
        DiscordPunishmentIntent permanentBan = ban(SanctionLength.permanent(), false);

        assertThrows(DiscordPunishmentAuthorization.DeniedException.class, () ->
                authorization.requireApprovalOfConcreteSanction(
                        requester, actor(StaffRank.MOD), Optional.empty(), StaffRank.ADMIN, permanentBan));
        assertThrows(DiscordPunishmentAuthorization.DeniedException.class, () ->
                authorization.requireApprovalOfConcreteSanction(
                        requester, actor(StaffRank.DEVELOPER), Optional.empty(), StaffRank.ADMIN, permanentBan));
    }
'''
new = '''    @Test
    void moderatorAndDeveloperApprovalCannotBypassPermanentSanctionAuthority() {
        Actor requester = actor(StaffRank.ADMIN);
        DiscordPunishmentIntent permanentBan = ban(SanctionLength.permanent(), false);

        assertThrows(DiscordPunishmentAuthorization.DeniedException.class, () ->
                authorization.requireApprovalOfConcreteSanction(
                        requester, actor(StaffRank.MOD), Optional.empty(), StaffRank.MOD, permanentBan));
        assertThrows(DiscordPunishmentAuthorization.DeniedException.class, () ->
                authorization.requireApprovalOfConcreteSanction(
                        requester, actor(StaffRank.DEVELOPER), Optional.empty(), StaffRank.MOD, permanentBan));
    }

    @Test
    void moderatorAndDeveloperApprovalCannotBypassCustomConsequenceAuthority() {
        Actor requester = actor(StaffRank.ADMIN);
        DiscordPunishmentIntent customBan = customBan(SanctionLength.temporary(Duration.ofDays(1)));

        assertThrows(DiscordPunishmentAuthorization.DeniedException.class, () ->
                authorization.requireApprovalOfConcreteSanction(
                        requester, actor(StaffRank.MOD), Optional.empty(), StaffRank.MOD, customBan));
        assertThrows(DiscordPunishmentAuthorization.DeniedException.class, () ->
                authorization.requireApprovalOfConcreteSanction(
                        requester, actor(StaffRank.DEVELOPER), Optional.empty(), StaffRank.MOD, customBan));
    }
'''
text = replace_once(text, old, new, "concrete approval escalation tests")
old = '''    private static DiscordPunishmentIntent ban(SanctionLength length, boolean customDuration) {
        return new DiscordPunishmentIntent(
                DiscordConsequenceType.BAN,
                length,
                customDuration,
                false,
                Optional.empty(),
                "Rule violation",
                "",
                0,
                true
        );
    }
'''
new = '''    private static DiscordPunishmentIntent ban(SanctionLength length, boolean customDuration) {
        return ban(length, customDuration, false);
    }

    private static DiscordPunishmentIntent customBan(SanctionLength length) {
        return ban(length, false, true);
    }

    private static DiscordPunishmentIntent ban(
            SanctionLength length,
            boolean customDuration,
            boolean customConsequence
    ) {
        return new DiscordPunishmentIntent(
                DiscordConsequenceType.BAN,
                length,
                customDuration,
                customConsequence,
                Optional.empty(),
                "Rule violation",
                "",
                0,
                true
        );
    }
'''
text = replace_once(text, old, new, "authorization test intent helpers")
write(path, text)

# Worker recovery and non-END removal semantics required by the package contract.
path = "staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordPunishmentWorkerTest.java"
text = read(path)
marker = '''    @Test
    void applyNotificationRetryDoesNotRepeatExternalEffect() {
'''
test = '''    @Test
    void targetLeavingBeforeMuteApplyRemainsRecoverable() {
        FakeRepository repository = new FakeRepository(punishment(mute(Duration.ofHours(1)), NOW));
        FakeGateway gateway = new FakeGateway();
        gateway.applyFailure = new DiscordPunishmentGateway.EffectException(TARGET_NOT_IN_GUILD_ERROR, true);
        repository.enqueue(WorkType.APPLY, NOW, 1);

        newWorker(repository, gateway).runCycle();

        assertEquals(DiscordPunishmentState.RETRY_APPLY, repository.current.punishment().state());
        assertFalse(repository.current.punishment().externalApplied());
        assertEquals(Optional.of(TARGET_NOT_IN_GUILD_ERROR), repository.current.punishment().lastErrorCode());
        assertEquals(WorkType.APPLY, repository.work.peek().type());
        assertTrue(repository.work.peek().dueAt().isAfter(NOW));
    }

'''
text = replace_once(text, marker, test + marker, "member departure apply recovery test")
marker = '''    @Test
    void removalNotificationRetryDoesNotRepeatReversal() {
'''
tests = '''    @Test
    void successfulRevokeUsesRevokedTerminalState() {
        RemovalResult result = successfulRemoval(DiscordPunishmentTermination.REVOKE);

        assertEquals(DiscordPunishmentState.REVOKED, result.punishment().state());
        assertFalse(result.punishment().externalApplied());
        assertEquals(1, result.removeCalls());
    }

    @Test
    void successfulOverturnUsesOverturnedTerminalState() {
        RemovalResult result = successfulRemoval(DiscordPunishmentTermination.OVERTURN);

        assertEquals(DiscordPunishmentState.OVERTURNED, result.punishment().state());
        assertFalse(result.punishment().externalApplied());
        assertEquals(1, result.removeCalls());
    }

'''
text = replace_once(text, marker, tests + marker, "revoke and overturn worker tests")
marker = '''    private static DiscordPunishmentWorker newWorker(FakeRepository repository, FakeGateway gateway) {
'''
helper = '''    private static RemovalResult successfulRemoval(DiscordPunishmentTermination termination) {
        DiscordPunishment initial = appliedMute().requestRemoval(termination, REMOVE_OPERATION);
        FakeRepository repository = new FakeRepository(initial);
        FakeGateway gateway = new FakeGateway();
        repository.enqueue(WorkType.REMOVE, NOW, 1);

        newWorker(repository, gateway).runCycle();
        return new RemovalResult(repository.current.punishment(), gateway.removeCalls);
    }

    private record RemovalResult(DiscordPunishment punishment, int removeCalls) {
    }

'''
text = replace_once(text, marker, helper + marker, "removal outcome test helper")
write(path, text)
