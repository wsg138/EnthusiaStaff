CREATE TABLE IF NOT EXISTS report_client_evidence_snapshots (
    report_id BINARY(16) NOT NULL,
    snapshot_id BINARY(16) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (report_id, snapshot_id),
    INDEX idx_report_client_evidence_snapshot (snapshot_id),
    CONSTRAINT fk_report_client_evidence_report FOREIGN KEY (report_id) REFERENCES reports(report_id),
    CONSTRAINT fk_report_client_evidence_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES client_evidence_snapshots(snapshot_id)
) ENGINE=InnoDB;
