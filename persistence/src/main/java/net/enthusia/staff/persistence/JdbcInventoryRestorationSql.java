package net.enthusia.staff.persistence;

final class JdbcInventoryRestorationSql {
    static final String FINALIZE_COMMITTED_RESERVATIONS = """
            UPDATE confiscated_asset_snapshots a
            JOIN inventory_operations source_operation
                ON source_operation.operation_id = a.inventory_operation_id
            JOIN inventory_profiles source_profile
                ON source_profile.profile_id = source_operation.profile_id
            JOIN cases c ON c.case_id = a.case_id
            JOIN inventory_operations restoration_operation
                ON restoration_operation.operation_id = a.restoration_operation_id
                AND restoration_operation.profile_id = source_operation.profile_id
            JOIN inventory_pending_patches restoration_patch
                ON restoration_patch.operation_id = restoration_operation.operation_id
                AND restoration_patch.profile_id = restoration_operation.profile_id
                AND restoration_patch.case_id = a.case_id
            SET a.restoration_state = 'APPLIED', a.restored_at = ?,
                a.restored_checksum = restoration_patch.replacement_checksum
            WHERE a.case_id = ? AND a.restoration_state = 'RESERVED'
                AND a.restored_at IS NULL
                AND source_operation.case_id = a.case_id
                AND source_operation.operation_type = ?
                AND source_operation.state = 'COMMITTED'
                AND c.target_id = source_profile.player_id
                AND EXISTS (
                    SELECT 1
                    FROM inventory_pending_patches source_patch
                    WHERE source_patch.operation_id = source_operation.operation_id
                        AND source_patch.profile_id = source_operation.profile_id
                        AND source_patch.case_id = a.case_id
                        AND source_patch.state = 'APPLIED'
                        AND source_patch.applied_at IS NOT NULL
                )
                AND restoration_operation.case_id = a.case_id
                AND restoration_operation.operation_type = ?
                AND restoration_operation.state = 'COMMITTED'
                AND restoration_patch.state = 'APPLIED'
                AND restoration_patch.applied_at IS NOT NULL
                AND restoration_patch.replacement_checksum IS NOT NULL
            """;

    static final String FINALIZE_COMMITTED_RESERVATION = """
            UPDATE confiscated_asset_snapshots a
            JOIN inventory_operations source_operation
                ON source_operation.operation_id = a.inventory_operation_id
            JOIN inventory_profiles source_profile
                ON source_profile.profile_id = source_operation.profile_id
            JOIN cases c ON c.case_id = a.case_id
            JOIN inventory_operations restoration_operation
                ON restoration_operation.operation_id = a.restoration_operation_id
                AND restoration_operation.profile_id = source_operation.profile_id
            JOIN inventory_pending_patches restoration_patch
                ON restoration_patch.operation_id = restoration_operation.operation_id
                AND restoration_patch.profile_id = restoration_operation.profile_id
                AND restoration_patch.case_id = a.case_id
            SET a.restoration_state = 'APPLIED', a.restored_at = ?,
                a.restored_checksum = ?
            WHERE a.case_id = ? AND a.restoration_operation_id = ?
                AND a.restoration_state = 'RESERVED' AND a.restored_at IS NULL
                AND source_operation.case_id = a.case_id
                AND source_operation.operation_type = ?
                AND source_operation.state = 'COMMITTED'
                AND c.target_id = source_profile.player_id
                AND EXISTS (
                    SELECT 1
                    FROM inventory_pending_patches source_patch
                    WHERE source_patch.operation_id = source_operation.operation_id
                        AND source_patch.profile_id = source_operation.profile_id
                        AND source_patch.case_id = a.case_id
                        AND source_patch.state = 'APPLIED'
                        AND source_patch.applied_at IS NOT NULL
                )
                AND restoration_operation.case_id = a.case_id
                AND restoration_operation.operation_type = ?
                AND restoration_operation.state = 'COMMITTED'
                AND restoration_patch.state = 'APPLIED'
                AND restoration_patch.applied_at IS NOT NULL
                AND restoration_patch.replacement_checksum = ?
            """;

    static final String LOCK_ACTIVE_SNAPSHOTS = """
            SELECT a.snapshot_id, a.inventory_operation_id, a.checksum,
                a.asset_blob, a.created_at, a.expires_at,
                a.restoration_operation_id, a.restoration_state,
                a.restoration_reserved_at, a.restored_checksum,
                source_operation.case_id AS source_case_id,
                source_operation.operation_type AS source_operation_type,
                source_operation.state AS source_operation_state,
                source_profile.player_id AS source_player_id,
                c.target_id AS case_target_id,
                EXISTS (
                    SELECT 1
                    FROM inventory_pending_patches source_patch
                    WHERE source_patch.operation_id = source_operation.operation_id
                        AND source_patch.profile_id = source_operation.profile_id
                        AND source_patch.case_id = a.case_id
                        AND source_patch.state = 'APPLIED'
                        AND source_patch.applied_at IS NOT NULL
                ) AS source_patch_applied
            FROM confiscated_asset_snapshots a
            JOIN inventory_operations source_operation
                ON source_operation.operation_id = a.inventory_operation_id
            JOIN inventory_profiles source_profile
                ON source_profile.profile_id = source_operation.profile_id
            JOIN cases c ON c.case_id = a.case_id
            WHERE a.case_id = ? AND a.restored_at IS NULL AND a.expires_at > ?
            ORDER BY a.created_at
            FOR UPDATE
            """;

    static final String VALIDATE_RESERVATION = """
            SELECT a.restoration_state, a.restoration_reserved_at,
                a.restored_at, a.restored_checksum, a.expires_at,
                source_operation.case_id AS source_case_id,
                source_operation.operation_type AS source_operation_type,
                source_operation.state AS source_operation_state,
                source_profile.player_id AS source_player_id,
                source_profile.scope_id AS source_scope_id,
                c.target_id AS case_target_id,
                EXISTS (
                    SELECT 1
                    FROM inventory_pending_patches source_patch
                    WHERE source_patch.operation_id = source_operation.operation_id
                        AND source_patch.profile_id = source_operation.profile_id
                        AND source_patch.case_id = a.case_id
                        AND source_patch.state = 'APPLIED'
                        AND source_patch.applied_at IS NOT NULL
                ) AS source_patch_applied
            FROM confiscated_asset_snapshots a
            JOIN inventory_operations source_operation
                ON source_operation.operation_id = a.inventory_operation_id
            JOIN inventory_profiles source_profile
                ON source_profile.profile_id = source_operation.profile_id
            JOIN cases c ON c.case_id = a.case_id
            WHERE a.case_id = ? AND a.restoration_operation_id = ?
            FOR UPDATE
            """;

    static final String ALREADY_FINALIZED = """
            SELECT COUNT(*) AS total_count,
                SUM(CASE
                    WHEN a.restoration_state = 'APPLIED' AND a.restored_at IS NOT NULL
                        AND a.restored_checksum = ? THEN 1
                    ELSE 0
                END) AS applied_count
            FROM confiscated_asset_snapshots a
            JOIN inventory_operations source_operation
                ON source_operation.operation_id = a.inventory_operation_id
            JOIN inventory_profiles source_profile
                ON source_profile.profile_id = source_operation.profile_id
            JOIN cases c ON c.case_id = a.case_id
            JOIN inventory_operations restoration_operation
                ON restoration_operation.operation_id = a.restoration_operation_id
                AND restoration_operation.profile_id = source_operation.profile_id
            JOIN inventory_pending_patches restoration_patch
                ON restoration_patch.operation_id = restoration_operation.operation_id
                AND restoration_patch.profile_id = restoration_operation.profile_id
                AND restoration_patch.case_id = a.case_id
            WHERE a.case_id = ? AND a.restoration_operation_id = ?
                AND source_operation.case_id = a.case_id
                AND source_operation.operation_type = ?
                AND source_operation.state = 'COMMITTED'
                AND c.target_id = source_profile.player_id
                AND EXISTS (
                    SELECT 1
                    FROM inventory_pending_patches source_patch
                    WHERE source_patch.operation_id = source_operation.operation_id
                        AND source_patch.profile_id = source_operation.profile_id
                        AND source_patch.case_id = a.case_id
                        AND source_patch.state = 'APPLIED'
                        AND source_patch.applied_at IS NOT NULL
                )
                AND restoration_operation.case_id = a.case_id
                AND restoration_operation.operation_type = ?
                AND restoration_operation.state = 'COMMITTED'
                AND restoration_patch.state = 'APPLIED'
                AND restoration_patch.applied_at IS NOT NULL
                AND restoration_patch.replacement_checksum = ?
            """;

    static final String MARK_APPLIED = """
            UPDATE confiscated_asset_snapshots a
            JOIN inventory_operations source_operation
                ON source_operation.operation_id = a.inventory_operation_id
            JOIN inventory_profiles source_profile
                ON source_profile.profile_id = source_operation.profile_id
            JOIN cases c ON c.case_id = a.case_id
            JOIN inventory_operations restoration_operation
                ON restoration_operation.operation_id = a.restoration_operation_id
                AND restoration_operation.profile_id = source_operation.profile_id
            JOIN inventory_pending_patches restoration_patch
                ON restoration_patch.patch_id = ?
                AND restoration_patch.operation_id = restoration_operation.operation_id
                AND restoration_patch.profile_id = restoration_operation.profile_id
                AND restoration_patch.case_id = a.case_id
            SET a.restoration_state = 'APPLIED', a.restored_at = ?,
                a.restored_checksum = ?
            WHERE a.case_id = ? AND a.restoration_operation_id = ?
                AND a.restoration_state = 'RESERVED' AND a.restored_at IS NULL
                AND source_operation.case_id = a.case_id
                AND source_operation.operation_type = ?
                AND source_operation.state = 'COMMITTED'
                AND c.target_id = source_profile.player_id
                AND EXISTS (
                    SELECT 1
                    FROM inventory_pending_patches source_patch
                    WHERE source_patch.operation_id = source_operation.operation_id
                        AND source_patch.profile_id = source_operation.profile_id
                        AND source_patch.case_id = a.case_id
                        AND source_patch.state = 'APPLIED'
                        AND source_patch.applied_at IS NOT NULL
                )
                AND restoration_operation.case_id = a.case_id
                AND restoration_operation.operation_type = ?
                AND restoration_operation.state = 'COMMITTED'
                AND restoration_patch.state = 'APPLIED'
                AND restoration_patch.applied_at IS NOT NULL
                AND restoration_patch.replacement_checksum = ?
            """;

    private JdbcInventoryRestorationSql() {
    }
}
