# Cheat Tester snapshot restore exception handling

The restore method previously decoded the saved inventory outside its `try` block.
Corrupt, non-migratable, or otherwise unreadable inventory snapshots could throw
without reaching the failure callback used to retain/report durable recovery.

This change moves decoding inside the guarded operation, so synchronous decoding
or inventory-deserialization exceptions reach the existing journal recovery
failure path. It does **not** synthesize lost items or mark corrupt snapshots
as restored. Existing journal records remain authoritative for manual recovery.

The Totem Refill probe remains disabled until complete Paper 26.2 normal,
disconnect, restart, timeout, and partial-failure restoration tests pass.
