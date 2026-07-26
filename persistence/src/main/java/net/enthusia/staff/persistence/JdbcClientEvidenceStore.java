package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.evidence.ClientEvidenceSnapshot;
import net.enthusia.staff.domain.ports.ClientEvidenceStore;

public final class JdbcClientEvidenceStore implements ClientEvidenceStore {
    private final DataSource dataSource;
    private final ObjectMapper json;

    public JdbcClientEvidenceStore(DataSource dataSource, ObjectMapper json) {
        this.dataSource = dataSource;
        this.json = json;
    }

    @Override
    public UUID save(ClientEvidenceSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must be present");
        }
        try (Connection connection = dataSource.getConnection()) {
            return ClientEvidencePersistence.insert(connection, json, snapshot);
        } catch (SQLException | JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to save client evidence", exception);
        }
    }
}
