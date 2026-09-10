package net.enthusia.staff.discordbot;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.dv8tion.jda.api.entities.Message;

final class ModerationMessageFilter {
    private ModerationMessageFilter() {
    }

    static List<Message> apply(List<Message> source, ModerationReadApiModel.MessageQuery query) {
        Predicate<Message> predicate = authorPredicate(query)
                .and(textPredicate(query))
                .and(datePredicate(query));
        return source.stream().filter(predicate).toList();
    }

    private static Predicate<Message> authorPredicate(ModerationReadApiModel.MessageQuery query) {
        if (query.authorId().isEmpty()) {
            return message -> true;
        }
        long author = ModerationReadRequestAuthorizer.snowflake(query.authorId().orElseThrow(), "author");
        return message -> message.getAuthor().getIdLong() == author;
    }

    private static Predicate<Message> textPredicate(ModerationReadApiModel.MessageQuery query) {
        if (query.text().isEmpty()) {
            return message -> true;
        }
        String text = query.text().orElseThrow().strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return message -> true;
        }
        return message -> message.getContentRaw().toLowerCase(Locale.ROOT).contains(text);
    }

    private static Predicate<Message> datePredicate(ModerationReadApiModel.MessageQuery query) {
        if (query.date().isEmpty()) {
            return message -> true;
        }
        LocalDate date = LocalDate.parse(query.date().orElseThrow());
        return message -> message.getTimeCreated().toLocalDate().equals(date);
    }
}
