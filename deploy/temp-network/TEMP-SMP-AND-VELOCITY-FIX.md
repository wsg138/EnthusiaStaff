# Temp SMP and Velocity database setup

The actual Temp server has the verified EnthusiaStaff Paper JAR and an owner-provided Staff database file in `plugins/EnthusiaStaff/database.properties`. Its LiteBans JAR and `plugins/LiteBans/config.yml` were copied unchanged from main SMP. The four remote uploads passed SHA-256 checks.

Bloom hosting did not provide usable proxy process environment variables. The Velocity plugin now accepts a private `plugins/enthusiastaff/database.properties` file for the Staff and LiteBans database groups when each complete environment group is absent. The file is outside Git and has server-user-only permissions. An incomplete environment group or incomplete file fails closed. The updated proxy JAR and private file passed remote SHA-256 checks; the prior JAR was backed up locally.

The owner-triggered network restart at about 05:16 UTC loaded the staged files. Fresh SFTP logs show `SHADOW_MIGRATION` on Velocity and the actual Temp server, with LiteBans connected on Temp. Staff remains out of `ACTIVE` mode. See `LIVE-RECOVERY-NOW.md` for the other backend results and remaining validation.

Do not print credentials, replace the authoritative LiteBans database, remove legacy moderation plugins, or activate cutover. No additional restart was triggered by the executor.
