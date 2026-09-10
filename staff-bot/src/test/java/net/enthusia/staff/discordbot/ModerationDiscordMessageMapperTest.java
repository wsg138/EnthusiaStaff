package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;

class ModerationDiscordMessageMapperTest {
    @Test
    void memberLookupUsesCacheWithoutBlockingDiscordRest() {
        Member cached = proxy(Member.class, (method, args) -> null);
        AtomicBoolean restCalled = new AtomicBoolean();
        Guild guild = proxy(Guild.class, (method, args) -> switch (method) {
            case "getMemberById" -> cached;
            case "retrieveMemberById" -> {
                restCalled.set(true);
                throw new AssertionError("message mapping must not issue per-author REST requests");
            }
            default -> null;
        });

        assertSame(cached, ModerationDiscordMessageMapper.memberIfPresent(guild, 222L));
        assertFalse(restCalled.get());
    }

    @Test
    void replyPreviewUsesReferencedMessageAlreadyReturnedByDiscord() {
        AtomicBoolean restCalled = new AtomicBoolean();
        Guild guild = proxy(Guild.class, (method, args) -> switch (method) {
            case "getMemberById" -> null;
            case "retrieveMemberById" -> {
                restCalled.set(true);
                throw new AssertionError("reply previews must not issue member REST requests");
            }
            default -> null;
        });
        User author = user("222", "reply-user", "Reply User");
        Message referenced = proxy(Message.class, (method, args) -> switch (method) {
            case "getId" -> "9001";
            case "getAuthor" -> author;
            case "getContentDisplay" -> "the replied-to message";
            default -> null;
        });
        Message source = proxy(Message.class, (method, args) ->
                "getReferencedMessage".equals(method) ? referenced : null);

        Optional<ModerationReadApiModel.ReplyPreviewDto> preview =
                ModerationDiscordMessageMapper.replyPreview(guild, source);

        assertTrue(preview.isPresent());
        assertEquals("9001", preview.orElseThrow().messageId());
        assertEquals("Reply User", preview.orElseThrow().author().displayName());
        assertEquals(Optional.of("the replied-to message"), preview.orElseThrow().content());
        assertFalse(restCalled.get());
    }

    private static User user(String id, String username, String globalName) {
        return proxy(User.class, (method, args) -> switch (method) {
            case "getId" -> id;
            case "getIdLong" -> Long.parseLong(id);
            case "getName" -> username;
            case "getGlobalName" -> globalName;
            case "getEffectiveAvatarUrl" -> "https://cdn.discordapp.com/avatar.png";
            default -> null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> invocation.invoke(method.getName(), args));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }
}
