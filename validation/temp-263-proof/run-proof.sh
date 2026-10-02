#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROBE_JAR="${1:?usage: run-proof.sh <probe-jar>}"
PAPER_SHA='16c5494aed1015de4e7c6e5aefece74e0b398f733bd3fcd8975c820598c19bca'
PAPER_URL='https://fill-data.papermc.io/v1/objects/16c5494aed1015de4e7c6e5aefece74e0b398f733bd3fcd8975c820598c19bca/paper-26.3-134.jar'
FROZEN_SHA='8f2c9fbd6ac15523304ea6759d40fec576f1fcd4'
PORT=25594
WORK="${RUNNER_TEMP:-/tmp}/enthusiastaff-temp263-proof"
SERVER="$WORK/server"
EVIDENCE="$ROOT/build/reports/runtime-jars/temp263-proof"

rm -rf "$WORK" "$EVIDENCE"
mkdir -p "$SERVER/plugins" "$EVIDENCE"

mapfile -t STAFF_JARS < <(
  find "$ROOT/paper/build/libs" -maxdepth 1 -type f     -name 'EnthusiaStaff-Paper-*.jar' ! -name '*-sources.jar' | sort
)
if (( ${#STAFF_JARS[@]} != 1 )); then
  printf 'Expected one frozen Staff JAR, found %d\n' "${#STAFF_JARS[@]}" >&2
  exit 1
fi
STAFF_JAR="${STAFF_JARS[0]}"

node_major="$(node --version | sed -E 's/^v([0-9]+).*/\1/')"
if [[ -z "$node_major" || "$node_major" -lt 18 ]]; then
  echo "Node >=18 is required for the disposable real-client harness." >&2
  exit 1
fi

curl --fail --silent --show-error --location --retry 3   -H 'User-Agent: EnthusiaStaff TEMP Paper 26.3 compatibility proof'   "$PAPER_URL" -o "$WORK/paper-26.3.jar"
actual_paper_sha="$(sha256sum "$WORK/paper-26.3.jar" | awk '{print $1}')"
if [[ "$actual_paper_sha" != "$PAPER_SHA" ]]; then
  echo "Exact TEMP Paper artifact hash mismatch: $actual_paper_sha" >&2
  exit 1
fi

mkdir -p "$WORK/mineflayer"
npm install --prefix "$WORK/mineflayer" --no-save --package-lock=false --ignore-scripts   'mineflayer@npm:@wp2508/mineflayer@4.42.2' >/dev/null
NODE_PATH="$WORK/mineflayer/node_modules" node -e   "const p=require('mineflayer/package.json'); if (p.version !== '4.42.2') throw new Error(p.version)"

cp "$WORK/paper-26.3.jar" "$SERVER/paper.jar"
cp "$STAFF_JAR" "$SERVER/plugins/EnthusiaStaff-Paper.jar"
cp "$PROBE_JAR" "$SERVER/plugins/Temp263Proof.jar"

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
motd=TEMP 26.3 frozen Staff proof
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

PIPE="$WORK/console.pipe"
mkfifo "$PIPE"
exec {CONSOLE_FD}<>"$PIPE"
SERVER_LOG="$EVIDENCE/server.log"
CLIENT_LOG="$EVIDENCE/client.log"
LAST_PID=''

cleanup() {
  local rc=$?
  if [[ -n "$LAST_PID" ]] && kill -0 "$LAST_PID" 2>/dev/null; then
    printf 'stop\n' >&"$CONSOLE_FD" || true
    for _ in $(seq 1 20); do
      kill -0 "$LAST_PID" 2>/dev/null || break
      sleep 1
    done
    kill "$LAST_PID" 2>/dev/null || true
  fi
  if (( rc != 0 )); then
    echo '--- TEMP 26.3 proof server tail ---' >&2
    tail -n 500 "$SERVER_LOG" >&2 || true
    echo '--- TEMP 26.3 proof client log ---' >&2
    cat "$CLIENT_LOG" >&2 || true
  fi
  return "$rc"
}
trap cleanup EXIT

pushd "$SERVER" >/dev/null
java -Xms768M -Xmx2048M -jar paper.jar --nogui <"$PIPE" >"$SERVER_LOG" 2>&1 &
LAST_PID=$!
popd >/dev/null

ready=false
for _ in $(seq 1 180); do
  if grep -qE 'Done \([0-9.]+s\)!|Done \(' "$SERVER_LOG"; then
    ready=true
    break
  fi
  if ! kill -0 "$LAST_PID" 2>/dev/null; then
    echo 'Exact Paper 26.3 process exited before readiness.' >&2
    exit 1
  fi
  sleep 1
done
[[ "$ready" == true ]] || { echo 'Exact Paper 26.3 readiness timeout.' >&2; exit 1; }

grep -q 'VPROOF_STARTUP staff_present=true staff_enabled=true' "$SERVER_LOG"

timeout 420s env NODE_PATH="$WORK/mineflayer/node_modules" VPROOF_HOST=127.0.0.1 VPROOF_PORT="$PORT" \
  node "$ROOT/validation/temp-263-proof/real-client-proof.js" >"$CLIENT_LOG" 2>&1

grep -q 'TEMP263_REAL_CLIENT_PROOF_OK' "$CLIENT_LOG"

printf 'stop\n' >&"$CONSOLE_FD"
for _ in $(seq 1 60); do
  kill -0 "$LAST_PID" 2>/dev/null || break
  sleep 1
done
if kill -0 "$LAST_PID" 2>/dev/null; then
  echo 'Exact Paper 26.3 server did not stop cleanly.' >&2
  exit 1
fi
wait "$LAST_PID"
LAST_PID=''

grep -q 'VPROOF_ABI_OK.*minecraft=26.3.*build=134' "$SERVER_LOG"
grep -q 'VPROOF_BASELINE_OK' "$SERVER_LOG"
grep -q 'VPROOF_TELEPORT_OK' "$SERVER_LOG"
grep -q 'VPROOF_FORCED_FAILURE_OK' "$SERVER_LOG"
grep -q 'VPROOF_PLUGIN_DISABLE_OK' "$SERVER_LOG"
grep -q 'VPROOF_FINAL_OK' "$SERVER_LOG"
! grep -q 'VPROOF_FAILURE' "$SERVER_LOG"
! grep -q 'TEMP263_REAL_CLIENT_PROOF_FAILED' "$CLIENT_LOG"

staff_sha="$(sha256sum "$STAFF_JAR" | awk '{print $1}')"
probe_sha="$(sha256sum "$PROBE_JAR" | awk '{print $1}')"
cat > "$EVIDENCE/runtime.txt" <<EOF
frozen_source=$FROZEN_SHA
runtime_brand=Paper
minecraft=26.3
paper_build=134
paper_sha256=$PAPER_SHA
paper_source=$PAPER_URL
java=$(java -version 2>&1 | head -1)
node=$(node --version)
mineflayer=wp2508/4.42.2
staff_artifact=$(basename "$STAFF_JAR")
staff_sha256=$staff_sha
probe_sha256=$probe_sha
EOF

grep 'VPROOF_' "$SERVER_LOG" > "$EVIDENCE/proof-markers.log"
grep -E 'CLIENT_MODE_OK|GEOMETRY_PASS|RECONNECT_PASS|TEMP263_REAL_CLIENT_PROOF_OK|GAME_STATE_CHANGE'   "$CLIENT_LOG" > "$EVIDENCE/client-markers.log"

trap - EXIT
echo "TEMP263_COMPATIBILITY_PROOF_OK paper_sha256=$PAPER_SHA staff_sha256=$staff_sha"
