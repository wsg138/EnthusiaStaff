package net.enthusia.staff.paper.modcase;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.modcase.CaseNotice;
import org.junit.jupiter.api.Test;

class PaperCasePrivateDeliveryTest {
    @Test
    void noticeUsesOnlySafeMetadataAndNeverQuotesPlayerEvidence() {
        Instant now = Instant.parse("2026-10-14T16:00:00Z");
        CaseNotice notice = new CaseNotice(UUID.randomUUID(), CaseNotice.Origin.MINECRAFT,
                "smp/sensitive-channel", 3, CaseNotice.Priority.URGENT, CaseNotice.State.OPEN,
                List.of("evt:opaque"), now, now.plusSeconds(60), null);
        String rendered = PaperCasePrivateDelivery.message(notice);
        assertTrue(rendered.contains(notice.caseId().toString()));
        assertTrue(rendered.contains("Revision 3"));
        assertFalse(rendered.contains("evt:opaque"));
        assertFalse(rendered.contains("sensitive-channel"));
        assertFalse(rendered.contains("@everyone"));
    }
}
