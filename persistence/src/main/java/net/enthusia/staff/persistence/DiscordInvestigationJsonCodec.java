package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import net.enthusia.staff.domain.investigation.InvestigationEvidence;

final class DiscordInvestigationJsonCodec {
    private final ObjectMapper mapper = new ObjectMapper();

    String attachments(List<InvestigationEvidence.Attachment> attachments) {
        try {
            return mapper.writeValueAsString(attachments);
        } catch (JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to encode private evidence attachment metadata", exception);
        }
    }

    String evidenceMetadata(InvestigationEvidence.Capture capture) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "kind", "D09_MESSAGE",
                    "contentStored", true,
                    "contextBefore", capture.before().size(),
                    "contextAfter", capture.after().size(),
                    "capturedBy", capture.capturedBy().toString(),
                    "action", capture.action()
            ));
        } catch (JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to encode private evidence metadata", exception);
        }
    }
}
