package net.enthusia.staff.paper.aireview;

import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionAuthority;
import org.bukkit.command.CommandSender;

final class AiReviewPermissions {
    static final String QUEUE = "enthusiastaff.ai-review.queue";
    static final String DETAIL = "enthusiastaff.ai-review.detail";
    static final String CORRECT = "enthusiastaff.ai-review.correct";
    static final String ADMIN = "enthusiastaff.ai-review.admin";

    private AiReviewPermissions() {
    }

    static boolean queue(CommandSender sender) {
        return sender != null && sender.hasPermission(QUEUE);
    }

    static boolean detail(CommandSender sender) {
        return sender != null && sender.hasPermission(DETAIL);
    }

    static boolean correct(CommandSender sender) {
        return sender != null && sender.hasPermission(CORRECT);
    }

    static CorrectionAuthority authority(
            CommandSender sender,
            AiReviewConfiguration configuration,
            boolean adminRequested
    ) {
        if (!adminRequested) {
            return CorrectionAuthority.STAFF;
        }
        if (sender == null
                || configuration == null
                || !configuration.adminOverrideEnabled()
                || !sender.hasPermission(ADMIN)) {
            throw new SecurityException("AI review admin override is not authorized");
        }
        return CorrectionAuthority.ADMIN;
    }
}
