ALTER TABLE sanction_events
    ADD COLUMN case_id CHAR(16) NULL AFTER sanction_id,
    ADD COLUMN subject_id BINARY(16) NULL AFTER case_id,
    ADD COLUMN previous_status VARCHAR(32) NULL AFTER event_type,
    ADD COLUMN resulting_status VARCHAR(32) NULL AFTER previous_status,
    ADD COLUMN previous_expiration TIMESTAMP(6) NULL AFTER resulting_status,
    ADD COLUMN resulting_expiration TIMESTAMP(6) NULL AFTER previous_expiration,
    ADD COLUMN linked_appeal_id BINARY(16) NULL AFTER resulting_expiration,
    ADD COLUMN linked_punishment_request_id BINARY(16) NULL AFTER linked_appeal_id,
    ADD COLUMN origin_runtime VARCHAR(64) NULL AFTER linked_punishment_request_id;

UPDATE sanction_events event
JOIN sanctions sanction ON sanction.sanction_id = event.sanction_id
SET event.case_id = sanction.case_id,
    event.subject_id = sanction.target_id
WHERE event.case_id IS NULL OR event.subject_id IS NULL;

ALTER TABLE sanction_events
    ADD CONSTRAINT fk_sanction_events_case
        FOREIGN KEY (case_id) REFERENCES cases(case_id),
    ADD CONSTRAINT fk_sanction_events_subject
        FOREIGN KEY (subject_id) REFERENCES players(player_id),
    ADD CONSTRAINT fk_sanction_events_appeal
        FOREIGN KEY (linked_appeal_id) REFERENCES website_appeal_requests(appeal_id),
    ADD CONSTRAINT fk_sanction_events_punishment_request
        FOREIGN KEY (linked_punishment_request_id) REFERENCES punishment_requests(request_id),
    ADD UNIQUE KEY uq_sanction_events_linked_appeal (linked_appeal_id),
    ADD INDEX idx_sanction_events_subject_time (subject_id, occurred_at, event_id),
    ADD INDEX idx_sanction_events_case_time (case_id, occurred_at, event_id),
    ADD INDEX idx_sanction_events_request_link (linked_punishment_request_id, occurred_at);

ALTER TABLE cases
    ADD INDEX idx_cases_history (target_id, issued_at, case_id);

ALTER TABLE sanctions
    ADD INDEX idx_sanctions_history (target_id, issued_at, sanction_id),
    ADD INDEX idx_sanctions_case_history (case_id, issued_at, sanction_id);

ALTER TABLE punishment_requests
    ADD INDEX idx_punishment_requests_history (target_id, created_at, request_id),
    ADD INDEX idx_punishment_requests_case_history (resulting_case_id, created_at, request_id);

ALTER TABLE punishment_request_events
    ADD INDEX idx_punishment_request_events_time (occurred_at, event_id),
    ADD INDEX idx_punishment_request_events_case (resulting_case_id, occurred_at, event_id);

ALTER TABLE punishment_overturn_requests
    ADD INDEX idx_overturn_case_history (case_id, requested_at, request_id),
    ADD INDEX idx_overturn_decision_history (case_id, decided_at, request_id);

ALTER TABLE website_appeal_requests
    ADD INDEX idx_website_appeal_case_history (case_id, created_at, appeal_id),
    ADD INDEX idx_website_appeal_decision_history (case_id, updated_at, appeal_id);

ALTER TABLE staff_notes
    ADD INDEX idx_staff_notes_history (target_id, created_at, note_id);
