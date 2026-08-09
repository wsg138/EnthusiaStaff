#!/usr/bin/env bash
set -Eeuo pipefail

readonly BUILD_COMMAND_DISPLAY='./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain'
readonly BUILD_REPOSITORY='wsg138/EnthusiaStaff'

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
SOURCE_VALIDATOR="$SCRIPT_DIR/validate-enthusiastaff-source.sh"
[[ -f "$SOURCE_VALIDATOR" && ! -L "$SOURCE_VALIDATOR" ]] || {
    printf 'ERROR: Missing trusted source-selection validator: %s\n' "$SOURCE_VALIDATOR" >&2
    exit 1
}
# shellcheck source=scripts/staging/validate-enthusiastaff-source.sh
source "$SOURCE_VALIDATOR"

fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

write_manifest() {
    local manifest_path="$1"

    MANIFEST_PATH="$manifest_path" python3 - <<'PY'
import json
import os
from pathlib import Path

main_ancestor = os.environ["MANIFEST_SOURCE_IS_ANCESTOR_OF_MAIN"]
if main_ancestor not in {"true", "false"}:
    raise SystemExit("MANIFEST_SOURCE_IS_ANCESTOR_OF_MAIN must be true or false")

manifest = {
    "schema_version": 2,
    "project": "EnthusiaStaff",
    "component": "paper",
    "source_repository": "wsg138/EnthusiaStaff",
    "requested_source_sha": os.environ["MANIFEST_REQUESTED_SOURCE_SHA"],
    "resolved_source_sha": os.environ["MANIFEST_RESOLVED_SOURCE_SHA"],
    "source_is_ancestor_of_main": main_ancestor == "true",
    "build_repository": "wsg138/EnthusiaStaff",
    "build_workflow_path": ".github/workflows/pi-staging-check.yml",
    "build_workflow_sha": os.environ["MANIFEST_BUILD_WORKFLOW_SHA"],
    "build_run_id": os.environ["MANIFEST_BUILD_RUN_ID"],
    "build_run_attempt": os.environ["MANIFEST_BUILD_RUN_ATTEMPT"],
    "runtime_filename": os.environ["MANIFEST_RUNTIME_FILENAME"],
    "runtime_sha256": os.environ["MANIFEST_RUNTIME_SHA256"],
    "runtime_size_bytes": int(os.environ["MANIFEST_RUNTIME_SIZE_BYTES"]),
    "java_version": os.environ["MANIFEST_JAVA_VERSION"],
    "gradle_version": os.environ["MANIFEST_GRADLE_VERSION"],
    "build_command": os.environ["MANIFEST_BUILD_COMMAND"],
    "build_result": "success",
    "built_at_utc": os.environ["MANIFEST_BUILT_AT_UTC"],
}

Path(os.environ["MANIFEST_PATH"]).write_text(
    json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
)
PY
}

assert_package_allowlist() {
    local package_dir="$1"
    local runtime_filename="$2"
    local expected actual unexpected_entry

    [[ -d "$package_dir" ]] || fail "Missing package directory: $package_dir"
    unexpected_entry="$(find "$package_dir" -mindepth 1 -maxdepth 1 ! -type f -print -quit)"
    [[ -z "$unexpected_entry" ]] || fail 'runtime-package contains a directory, symlink, or other non-regular entry'

    expected="$(printf '%s\n' "$runtime_filename" 'SHA256SUMS' 'manifest.json' | sort)"
    actual="$(find "$package_dir" -mindepth 1 -maxdepth 1 -type f -printf '%f\n' | sort)"
    [[ "$actual" == "$expected" ]] || fail 'runtime-package contains a missing or unexpected file'
}

main() {
    local source_dir="${SOURCE_DIR:?SOURCE_DIR is required}"
    local package_dir="${PACKAGE_DIR:?PACKAGE_DIR is required}"
    local source_sha_input="${SOURCE_SHA_INPUT:?SOURCE_SHA_INPUT is required}"
    local build_workflow_sha="${BUILD_WORKFLOW_SHA:?BUILD_WORKFLOW_SHA is required}"
    local output_file="${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"
    local summary_file="${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY is required}"
    local github_run_id="${GITHUB_RUN_ID:?GITHUB_RUN_ID is required}"
    local github_run_attempt="${GITHUB_RUN_ATTEMPT:?GITHUB_RUN_ATTEMPT is required}"
    local jar_directory runtime_jar runtime_filename packaged_jar runtime_sha256
    local runtime_size_bytes java_output java_version gradle_output gradle_version
    local built_at_utc artifact_name short_source_sha jar_entries_file
    local -a jar_candidates=()

    [[ "$build_workflow_sha" =~ ^[0-9a-fA-F]{40}$ ]] || fail 'BUILD_WORKFLOW_SHA must be a full commit SHA'
    [[ "${GITHUB_REPOSITORY:-}" == "$BUILD_REPOSITORY" ]] || fail 'Builder must run in wsg138/EnthusiaStaff'

    validate_source_commit "$source_dir" "$source_sha_input" \
        || fail 'Requested source SHA did not pass the trusted source-selection policy'

    git -C "$source_dir" checkout --detach --force "$RESOLVED_SOURCE_SHA"
    git -C "$source_dir" clean -ffdqx
    [[ "$(git -C "$source_dir" rev-parse HEAD)" == "$RESOLVED_SOURCE_SHA" ]] \
        || fail 'Detached checkout does not match the resolved source SHA'

    cd "$source_dir"
    chmod +x ./gradlew
    java_output="$(java -version 2>&1)"
    java_version="${java_output%%$'\n'*}"
    gradle_output="$(./gradlew --version --no-daemon)"
    gradle_version="$(awk '/^Gradle / { print; exit }' <<< "$gradle_output")"
    [[ -n "$gradle_version" ]] || fail 'Unable to determine the Gradle wrapper version'

    ./gradlew clean build jacocoAggregateReport runtimeJars \
        --no-daemon --no-build-cache --no-configuration-cache --console=plain

    jar_directory="$source_dir/paper/build/libs"
    [[ -d "$jar_directory" ]] || fail "Missing expected Paper output directory: $jar_directory"
    while IFS= read -r -d '' candidate; do
        jar_candidates+=("$candidate")
    done < <(find "$jar_directory" -maxdepth 1 -type f -name 'EnthusiaStaff-Paper-*.jar' \
        ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print0)
    ((${#jar_candidates[@]} == 1)) || fail "Expected exactly one deployable Paper runtime JAR, found ${#jar_candidates[@]}"

    runtime_jar="${jar_candidates[0]}"
    runtime_filename="$(basename "$runtime_jar")"
    [[ "$runtime_filename" =~ ^EnthusiaStaff-Paper-[A-Za-z0-9._+-]+\.jar$ ]] \
        || fail 'Paper runtime filename is outside the bounded staging bridge format'
    jar_entries_file="$source_dir/build/enthusiastaff-paper-runtime-entries.txt"
    mkdir -p "$(dirname "$jar_entries_file")"
    unzip -tq "$runtime_jar"
    unzip -Z1 "$runtime_jar" > "$jar_entries_file"
    grep -Fxq 'plugin.yml' "$jar_entries_file" || fail 'Paper runtime JAR does not contain plugin.yml'
    grep -Fxq 'net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.class' "$jar_entries_file" \
        || fail 'Paper runtime JAR does not contain the declared plugin main class'
    grep -Eq '^(com/zaxxer/hikari/HikariDataSource|org/flywaydb/core/Flyway|com/fasterxml/jackson/databind/ObjectMapper)\.class$' "$jar_entries_file" \
        || fail 'Paper runtime JAR does not appear to be the shaded deployable archive'
    rm -f "$jar_entries_file"

    rm -rf "$package_dir"
    mkdir -p "$package_dir"
    packaged_jar="$package_dir/$runtime_filename"
    cp -- "$runtime_jar" "$packaged_jar"
    runtime_sha256="$(sha256sum "$packaged_jar" | awk '{print $1}')"
    runtime_size_bytes="$(stat -c '%s' "$packaged_jar")"
    ((runtime_size_bytes > 0 && runtime_size_bytes <= 52428800)) || fail 'Runtime JAR exceeds the 50 MiB staging bridge bound'
    (
        cd "$package_dir"
        printf '%s  %s\n' "$runtime_sha256" "$runtime_filename" > SHA256SUMS
        sha256sum -c SHA256SUMS
    )

    built_at_utc="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    short_source_sha="${RESOLVED_SOURCE_SHA:0:12}"
    artifact_name="enthusiastaff-paper-${short_source_sha}-${github_run_id}-${github_run_attempt}"

    export MANIFEST_REQUESTED_SOURCE_SHA="$VALIDATED_SOURCE_SHA"
    export MANIFEST_RESOLVED_SOURCE_SHA="$RESOLVED_SOURCE_SHA"
    export MANIFEST_SOURCE_IS_ANCESTOR_OF_MAIN="$SOURCE_IS_ANCESTOR_OF_MAIN"
    export MANIFEST_BUILD_WORKFLOW_SHA="${build_workflow_sha,,}"
    export MANIFEST_BUILD_RUN_ID="$github_run_id"
    export MANIFEST_BUILD_RUN_ATTEMPT="$github_run_attempt"
    export MANIFEST_RUNTIME_FILENAME="$runtime_filename"
    export MANIFEST_RUNTIME_SHA256="$runtime_sha256"
    export MANIFEST_RUNTIME_SIZE_BYTES="$runtime_size_bytes"
    export MANIFEST_JAVA_VERSION="$java_version"
    export MANIFEST_GRADLE_VERSION="$gradle_version"
    export MANIFEST_BUILD_COMMAND="$BUILD_COMMAND_DISPLAY"
    export MANIFEST_BUILT_AT_UTC="$built_at_utc"
    write_manifest "$package_dir/manifest.json"
    assert_package_allowlist "$package_dir" "$runtime_filename"

    {
        printf 'resolved_source_sha=%s\n' "$RESOLVED_SOURCE_SHA"
        printf 'source_short_sha=%s\n' "$short_source_sha"
        printf 'source_selection=%s\n' "$SOURCE_SELECTION"
        printf 'source_is_ancestor_of_main=%s\n' "$SOURCE_IS_ANCESTOR_OF_MAIN"
        printf 'artifact_name=%s\n' "$artifact_name"
        printf 'runtime_filename=%s\n' "$runtime_filename"
        printf 'runtime_sha256=%s\n' "$runtime_sha256"
        printf 'runtime_size_bytes=%s\n' "$runtime_size_bytes"
    } >> "$output_file"

    {
        echo '## Public trusted EnthusiaStaff Paper build'
        echo
        echo "- Requested SHA: \`$VALIDATED_SOURCE_SHA\`"
        echo "- Resolved SHA: \`$RESOLVED_SOURCE_SHA\`"
        echo "- Source selection: \`$SOURCE_SELECTION\`"
        echo "- Build control SHA: \`${build_workflow_sha,,}\`"
        echo "- Runtime JAR: \`$runtime_filename\`"
        echo "- Runtime size: \`$runtime_size_bytes bytes\`"
        echo "- Runtime SHA-256: \`$runtime_sha256\`"
        echo "- Actions artifact: \`$artifact_name\`"
    } >> "$summary_file"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
