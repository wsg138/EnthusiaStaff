# Persistent channel TLS

The live Paper–Velocity channel uses TLS 1.3 for transport
confidentiality and server authentication. HMAC-signed envelopes remain
mandatory: TLS authenticates the Velocity endpoint, while per-backend HMAC
keys authenticate Paper servers and preserve protocol-level replay checks.
There is no cleartext fallback.

## Required material

Velocity requires a PKCS#12 key store containing its private key and complete
certificate chain. Each Paper server requires a PKCS#12 trust store containing
the issuing CA or the exact self-signed Velocity certificate. The private key
must never be copied to Paper.

Use a certificate issued by the network's internal CA or another trusted CA in
production. Its Subject Alternative Name must match the exact `channel.host`
configured on Paper. For example, a DNS host requires a `dns:` SAN and an IP
literal requires an `ip:` SAN.

For an isolated development environment, `keytool` can create a self-signed
certificate. Omit password arguments so `keytool` prompts without exposing the
password in shell history:

```text
keytool -genkeypair -alias enthusiastaff-channel -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA -dname "CN=velocity.internal.example" -ext "SAN=dns:velocity.internal.example" -ext "EKU=serverAuth" -validity 365 -storetype PKCS12 -keystore channel-server.p12
keytool -exportcert -rfc -alias enthusiastaff-channel -keystore channel-server.p12 -file channel-server.cer
keytool -importcert -alias enthusiastaff-channel -file channel-server.cer -storetype PKCS12 -keystore channel-trust.p12
```

Treat the self-signed key store as private material. The exported certificate
and trust store do not contain that private key.

## Velocity configuration

Place `channel-server.p12` in the Velocity EnthusiaStaff data directory, or set
an absolute path owned by the service account:

```properties
channel.enabled=true
channel.bind-address=127.0.0.1
channel.port=28765
channel.tls-key-store=channel-server.p12
channel.tls-key-store-password-environment=ES_CHANNEL_TLS_KEYSTORE_PASSWORD
```

Set the named environment variable through the service manager or secret
store. Do not add its value to the properties file, startup command, logs, or
repository.

## Paper configuration

Place `channel-trust.p12` in each Paper EnthusiaStaff data directory, or use an
absolute service-owned path:

```yaml
channel:
  enabled: true
  host: velocity.internal.example
  port: 28765
  tls:
    trust-store: channel-trust.p12
    trust-store-password-environment: ES_CHANNEL_TLS_TRUSTSTORE_PASSWORD
```

The configured host must match the Velocity certificate SAN. Set the named
password environment variable through the server's secret-management path.

## Rollout and verification

1. Keep the channel disabled while creating and distributing the stores.
2. Restrict the Velocity key store to the Velocity service account.
3. Configure the password environments without placing values in files.
4. Start or restart Velocity first, then the Paper backends.
5. Verify each required backend appears in channel health before enabling
   network-wide punishment authority.
6. Test a bidirectional acknowledged message and confirm the durable outbox has
   no unexpected pending or rejected delivery.

Missing stores, wrong passwords, an untrusted certificate, an expired
certificate, or a SAN mismatch prevents connection. Paper remains degraded and
new network-wide writes remain disabled; durable outbox work is not discarded.

## Rotation and rollback

For certificate rotation, add the new CA or certificate to every Paper trust
store first and restart Paper. Then replace the Velocity key store and restart
Velocity. Remove the old trust anchor only after every backend has reconnected
with the new certificate.

If TLS material must be withdrawn, disable the channel and rely on the durable
database/outbox recovery path. Do not restore a cleartext listener. Re-enabling
requires valid TLS material and a fresh health verification.
