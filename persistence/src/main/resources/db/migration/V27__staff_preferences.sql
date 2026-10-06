CREATE TABLE staff_preferences (
    staff_id BINARY(16) NOT NULL,
    tool_inventory_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (staff_id)
) ENGINE=InnoDB;
