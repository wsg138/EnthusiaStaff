#!/usr/bin/env bash
set -Eeuo pipefail

STAFF_JAR="${1:?usage: run-ci-proof.sh <staff-jar> <probe-jar>}"
PROBE_JAR="${2:?usage: run-ci-proof.sh <staff-jar> <probe-jar>}"

readonly EXPECTED_PAPER_SHA='16c5494aed1015de4e7c6e5aefece74e0b398f733bd3fcd8975c820598c19bca'
readonly PAPER_URL='https://fill-data.papermc.io/v1/objects/16c5494aed1015de4e7c6e5aefece74e0b398f733bd3fcd8975c820598c19bca/paper-26.3-134.jar'
readonly PORT='25594'
readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly WORK="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/enthusiastaff-temp263-proof"
readonly SERVER="$WORK/server"
readonly EVIDENCE="$WORK/evidence"
readonly PAPER_JAR="$WORK/paper-26.3-134.jar"

rm -rf "$WORK"
mkdir -p "$SERVER/plugins" "$EVIDENCE"

curl --fail --silent --show-error --location --retry 3 \
  -H 'User-Agent: EnthusiaStaff TEMP 26.3 compatibility proof' \
  "$PAPER_URL" -o "$PAPER_JAR"

actual_paper_sha="$(sha256sum "$PAPER_JAR" | awk '{print $1}')"
if [[ "$actual_paper_sha" != "$EXPECTED_PAPER_SHA" ]]; then
  echo "Exact Paper hash mismatch: $actual_paper_sha" >&2
  exit 1
fi

cp "$PAPER_JAR" "$SERVER/paper.jar"
cp "$STAFF_JAR" "$SERVER/plugins/EnthusiaStaff-Paper.jar"
cp "$PROBE_JAR" "$SERVER/plugins/Temp263Proof.jar"

sha256sum "$SERVER/paper.jar" | tee "$EVIDENCE/paper.sha256"
sha256sum "$SERVER/plugins/EnthusiaStaff-Paper.jar" | tee "$EVIDENCE/enthusiastaff.sha256"
sha256sum "$SERVER/plugins/Temp263Proof.jar" | tee "$EVIDENCE/probe.sha256"
java -version 2>&1 | tee "$EVIDENCE/java-version.txt"

java_major="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.version = \([0-9][0-9]*\).*/\1/p' | head -1)"
if [[ "$java_major" != '25' ]]; then
  echo "Proof requires Java 25, observed: ${java_major:-unknown}" >&2
  exit 1
fi

cat > "$SERVER/eula.txt" <<'EOF'
eula=true
EOF

cat > "$SERVER/server.properties" <<EOF
allow-flight=true
allow-nether=false
difficulty=peaceful
enforce-secure-profile=false
generate-structures=false
level-name=world
level-type=minecraft:flat
max-players=4
motd=EnthusiaStaff TEMP 26.3 compatibility proof
online-mode=false
server-ip=127.0.0.1
server-port=$PORT
simulation-distance=2
spawn-animals=false
spawn-monsters=false
spawn-npcs=false
spawn-protection=0
sync-chunk-writes=false
use-native-transport=false
view-distance=2
white-list=false
EOF

readonly NODE_ROOT="$WORK/node"
mkdir -p "$NODE_ROOT"
npm install \
  --prefix "$NODE_ROOT" \
  --no-save \
  --package-lock=false \
  --ignore-scripts \
  'mineflayer@npm:@wp2508/mineflayer@4.42.2' \
  >"$EVIDENCE/npm-install.log" 2>&1

NODE_PATH="$NODE_ROOT/node_modules" \
  node -e "const p=require('mineflayer/package.json'); if (p.version !== '4.42.2') throw new Error(p.version)"

readonly PIPE="$WORK/console.pipe"
mkfifo "$PIPE"
exec {CONSOLE_FD}<>"$PIPE"
SERVER_LOG="$EVIDENCE/server.log"
CLIENT_LOG="$EVIDENCE/client.log"
SERVER_PID=''

cleanup() {
  local rc=$?
  if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    printf 'stop\n' >&"$CONSOLE_FD" || true
    for _ in $(seq 1 20); do
      kill -0 "$SERVER_PID" 2>/dev/null || break
      sleep 1
    done
    kill "$SERVER_PID" 2>/dev/null || true
  fi
  if (( rc != 0 )); then
    echo '--- TEMP 26.3 server tail ---' >&2
    tail -n 500 "$SERVER_LOG" >&2 || true
    echo '--- TEMP 26.3 real client ---' >&2
    cat "$CLIENT_LOG" >&2 || true
  fi
  return "$rc"
}
trap cleanup EXIT

pushd "$SERVER" >/dev/null
java -Xms768M -Xmx2048M -jar paper.jar --nogui <"$PIPE" >"$SERVER_LOG" 2>&1 &
SERVER_PID=$!
popd >/dev/null

ready=false
for _ in $(seq 1 180); do
  if grep -qE 'Done \([0-9.]+s\)!|Done \(' "$SERVER_LOG"; then
    ready=true
    break
  fi
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo 'Paper exited before reaching Done.' >&2
    exit 1
  fi
  sleep 1
done
[[ "$ready" == true ]] || { echo 'Paper did not reach Done in time.' >&2; exit 1; }

grep -q 'VPROOF_STARTUP staff_present=true staff_enabled=true' "$SERVER_LOG"

NODE_PATH="$NODE_ROOT/node_modules" \
VPROOF_HOST='127.0.0.1' \
VPROOF_PORT="$PORT" \
  node "$ROOT/validation/temp-263-proof/real-client-proof.js" \
  >"$CLIENT_LOG" 2>&1

grep -q 'TEMP263_REAL_CLIENT_PROOF_COMPLETE compatibility=' "$CLIENT_LOG"
grep -q 'VPROOF_ABI_OK.*minecraft=26.3.*build=134' "$SERVER_LOG"
grep -q 'VPROOF_BASELINE_OK' "$SERVER_LOG"
grep -q 'VPROOF_BEGIN_OK' "$SERVER_LOG"
grep -q 'VPROOF_TELEPORT_OK' "$SERVER_LOG"
grep -q 'VPROOF_DISABLE_OK' "$SERVER_LOG"
grep -q 'VPROOF_FORCED_FAILURE_OK' "$SERVER_LOG"
grep -q 'VPROOF_PLUGIN_DISABLE_OK' "$SERVER_LOG"
grep -q 'VPROOF_FINAL_OK' "$SERVER_LOG"
! grep -q 'VPROOF_FAILURE' "$SERVER_LOG"
! grep -q 'TEMP263_REAL_CLIENT_PROOF_FAILED' "$CLIENT_LOG"

printf 'stop\n' >&"$CONSOLE_FD"
for _ in $(seq 1 60); do
  kill -0 "$SERVER_PID" 2>/dev/null || break
  sleep 1
done
if kill -0 "$SERVER_PID" 2>/dev/null; then
  echo 'Paper did not stop cleanly.' >&2
  exit 1
fi
wait "$SERVER_PID"
SERVER_PID=''

{
  echo 'frozen_source=8f2c9fbd6ac15523304ea6759d40fec576f1fcd4'
  echo 'runtime_brand=Paper'
  echo 'minecraft=26.3'
  echo 'paper_build=134'
  echo "paper_sha256=$actual_paper_sha"
  echo "paper_source=$PAPER_URL"
  echo 'java=25'
  echo 'real_client=wp2508/mineflayer@4.42.2'
  echo "staff_sha256=$(awk '{print $1}' "$EVIDENCE/enthusiastaff.sha256")"
  echo "probe_sha256=$(awk '{print $1}' "$EVIDENCE/probe.sha256")"
} | tee "$EVIDENCE/runtime.txt"

grep 'VPROOF_' "$SERVER_LOG" > "$EVIDENCE/proof-markers.log"
grep -E 'CLIENT_MODE_OK|NO_PHYSICS_RETENTION_|GEOMETRY_|TELEPORT_NO_PHYSICS_|RECONNECT_PASS|TEMP263_REAL_CLIENT_PROOF_COMPLETE|GAME_STATE_CHANGE' \
  "$CLIENT_LOG" > "$EVIDENCE/client-markers.log"

compatibility="$(grep -o 'TEMP263_REAL_CLIENT_PROOF_COMPLETE compatibility=[A-Z]*[^[:cntrl:]]*' "$CLIENT_LOG" | tail -1)"
echo "$compatibility" | tee "$EVIDENCE/compatibility.txt"

mkdir -p "$ROOT/build/reports/runtime-jars/temp263-proof"
cp "$EVIDENCE"/* "$ROOT/build/reports/runtime-jars/temp263-proof/"

echo "TEMP263_RUNTIME_COMPATIBILITY_PROOF_COMPLETE $compatibility"
trap - EXIT
