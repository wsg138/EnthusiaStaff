# One-shot Bloom StaffBot TLS connectivity probe

This optional **read-only** Java helper checks one preapproved endpoint,
`25319956-7c92-49d1-9afe-ea6e18758016:28765`, from the process/network
namespace in which it runs. The code hard-pins the host and port: it cannot
be redirected to scan other targets using an edited config file.

**It has NOT yet been executed from inside Bloom StaffBot.**
Running it on a laptop, build runner, or Codex browser does not prove
StaffBot-origin reachability. Do not use the Minecraft, Discord, or Pterodactyl
**application console** as a shell.

## Requirements

- A documented **authorized** command execution route inside the StaffBot
  container (or an approved equivalent with the identical network namespace).
  No restart or mutation of the live StaffBot JVM is required.
- Java 21 or later on that host.
- A private `chat-bridge.properties` file containing the host, port,
  existing trusted **public-only** PKCS#12 store path, and private store
  password in its normal keys. Do **not** put secrets in CLI arguments.
- Trusted public store file copied into the probe's actual runtime filesystem.
  Do not use Velocity's private server keystore.
- No live peer/HMAC settings are needed for this **TCP/TLS-only** test.

The helper itself is a standalone default-package Java 21 class and is
separate from any StaffBot application JAR:

```sh
# Compile elsewhere (once), Java 21 class-file format:
javac --release 21 -d ./probe-classes tools/chat-bridge-preflight/StaffBotTlsProbe.java

# Only from an authorized shell inside the actual StaffBot runtime network:
java -cp ./probe-classes StaffBotTlsProbe ./private-chat-bridge.properties
```

The config filename is passed as a path only. Do **not** pass credentials on
the command line or log the contents of the config.

Possible outcomes: `PREFLIGHT=TLS_VERIFIED` with the verified live public
certificate's SHA-256 fingerprint, `DNS_FAILED`, `TCP_UNREACHABLE`,
`TCP_TIMEOUT`, `TLS_FAILED`, `TLS_CERT_INVALID`, `TIMEOUT`,
`INVALID_LOCAL_CONFIG`, or `ERROR`. Only `TLS_VERIFIED` produces a
success exit code. The probe opens only a single connection, uses 3-second
TCP and 4-second handshake timeouts with a 10-second outer deadline,
TLS 1.3, HTTPS endpoint hostname verification, and the supplied trusted
PKCS#12 file. It **never** sends a chat frame, auth/HMAC frame, application
payload, Discord message, or player data.

**A TLS success is not proof of authenticated STAFFBOT channel readiness.**
That requires matching HMAC configuration on Velocity and StaffBot, separately
approved reviewed artifacts, and observation of the real application
connection during a controlled SHADOW deployment.

Only report the exit status, `PREFLIGHT` classification and the public
certificate fingerprint. Don't share the private file or password.

The complete network proof requirements are documented in
[`docs/discord-chat-bloom-network-preflight.md`](../../docs/discord-chat-bloom-network-preflight.md).
