package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.enthusia.staff.domain.application.AccountLinkCodeException;
import org.junit.jupiter.api.Test;

class AccountLinkCodeMessageTest {
    @Test
    void expiredAndInvalidCodesHaveDistinctMessages() {
        assertEquals(
                "That link code is invalid.",
                AccountLinkCommand.linkCodeFailureMessage(AccountLinkCodeException.Reason.INVALID)
        );
        assertEquals(
                "That link code has expired. Request a new link code and try again.",
                AccountLinkCommand.linkCodeFailureMessage(AccountLinkCodeException.Reason.EXPIRED)
        );
    }

    @Test
    void replacedAndConsumedCodesHaveActionableMessages() {
        assertEquals(
                "That link code was replaced by a newer code. Use the newest link code.",
                AccountLinkCommand.linkCodeFailureMessage(AccountLinkCodeException.Reason.REPLACED)
        );
        assertEquals(
                "That link code was already used. Request a new link code if you still need to link.",
                AccountLinkCommand.linkCodeFailureMessage(AccountLinkCodeException.Reason.ALREADY_USED)
        );
    }
}
