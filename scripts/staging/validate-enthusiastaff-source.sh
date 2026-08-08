#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

source_selection_fail() {
    printf 'ERROR: %s\n' "$*" >&2
    return 1
}

normalize_source_sha() {
    local raw_sha="${1:-}"
    [[ "$raw_sha" =~ ^[0-9a-fA-F]{40}$ ]] || return 1
    printf '%s\n' "${raw_sha,,}"
}

validate_authorized_pr_metadata() {
    local metadata_file="$1"
    local requested_sha="$2"

    AUTHORIZED_PR_METADATA_FILE="$metadata_file" \
    AUTHORIZED_PR_REQUESTED_SHA="$requested_sha" \
    python3 - <<'PY'
import json
import os
import re
from pathlib import Path

path = Path(os.environ["AUTHORIZED_PR_METADATA_FILE"])
try:
    payload = json.loads(path.read_text(encoding="utf-8"))
except (OSError, UnicodeError, json.JSONDecodeError) as exc:
    raise SystemExit(f"Authorized PR metadata is malformed: {exc}")

expected_repository = os.environ["AUTHORIZED_SOURCE_REPOSITORY"]
expected_number_text = os.environ["AUTHORIZED_PR_NUMBER"]
expected_head_repository = os.environ["AUTHORIZED_PR_HEAD_REPOSITORY"]
expected_head_ref = os.environ["AUTHORIZED_PR_HEAD_REF"]
expected_head_sha = os.environ["AUTHORIZED_PR_HEAD_SHA"].lower()
requested_sha = os.environ["AUTHORIZED_PR_REQUESTED_SHA"].lower()

if not re.fullmatch(r"[1-9][0-9]*", expected_number_text):
    raise SystemExit("Authorized PR number must be a positive integer")
expected_number = int(expected_number_text)

head = payload.get("head") or {}
head_repo = head.get("repo") or {}
base = payload.get("base") or {}
base_repo = base.get("repo") or {}

checks = [
    (payload.get("number") == expected_number, "PR number does not match the authorized PR"),
    (payload.get("state") == "open", "Authorized PR is not open"),
    (payload.get("merged") is not True and payload.get("merged_at") is None, "Authorized PR is merged"),
    (base_repo.get("full_name") == expected_repository, "Authorized PR targets an unexpected repository"),
    (base.get("ref") == "main", "Authorized PR does not target main"),
    (head_repo.get("full_name") == expected_head_repository, "Authorized PR head is not the expected same repository"),
    (head.get("ref") == expected_head_ref, "Authorized PR head branch does not match the requested PR metadata"),
    (head.get("sha", "").lower() == expected_head_sha, "Current PR head no longer matches the requested exact SHA"),
    (requested_sha == expected_head_sha, "Requested SHA is not the exact PR head selected for staging"),
    (head.get("sha", "").lower() == requested_sha, "Requested SHA does not equal the current authorized PR head"),
]

if not re.fullmatch(r"[0-9a-f]{40}", expected_head_sha):
    checks.append((False, "Authorized PR head is not a normalized full SHA"))

failures = [message for passed, message in checks if not passed]
if failures:
    raise SystemExit("Authorized PR validation failed: " + "; ".join(failures))
PY
}

fetch_authorized_pr_metadata() {
    local destination="$1"

    if [[ "${SOURCE_SELECTION_TEST_MODE:-}" == '1' ]]; then
        [[ -n "${SOURCE_SELECTION_TEST_PR_JSON:-}" ]] || {
            source_selection_fail 'SOURCE_SELECTION_TEST_PR_JSON is required in test mode'
            return 1
        }
        cp -- "$SOURCE_SELECTION_TEST_PR_JSON" "$destination" || return 1
        return 0
    fi

    [[ -z "${SOURCE_SELECTION_TEST_PR_JSON:-}" ]] || {
        source_selection_fail 'Fixture PR metadata is forbidden outside explicit test mode'
        return 1
    }

    local api_root="${GITHUB_API_URL:-https://api.github.com}"
    local api_url="${api_root%/}/repos/${AUTHORIZED_SOURCE_REPOSITORY}/pulls/${AUTHORIZED_PR_NUMBER}"
    local -a curl_headers=(
        --header 'Accept: application/vnd.github+json'
        --header 'X-GitHub-Api-Version: 2022-11-28'
        --header 'User-Agent: EnthusiaStaff-staging-source-validator'
    )
    if [[ -n "${GITHUB_TOKEN:-}" ]]; then
        curl_headers+=(--header "Authorization: Bearer ${GITHUB_TOKEN}")
    fi

    curl --fail --silent --show-error --location \
        --proto '=https' --tlsv1.2 \
        --connect-timeout 15 --max-time 45 --retry 2 \
        "${curl_headers[@]}" \
        --output "$destination" \
        "$api_url"
}

validate_source_commit() {
    local source_dir="$1"
    local raw_sha="$2"
    local normalized_sha
    local resolved_sha
    local metadata_file
    local fetched_pr_sha

    if ! normalized_sha="$(normalize_source_sha "$raw_sha")"; then
        source_selection_fail 'source_sha must be exactly 40 hexadecimal characters'
        return 1
    fi

    [[ -d "$source_dir/.git" ]] || {
        source_selection_fail "Missing Git checkout: $source_dir"
        return 1
    }
    [[ "${AUTHORIZED_SOURCE_REPOSITORY:-}" == 'wsg138/EnthusiaStaff' ]] || {
        source_selection_fail 'AUTHORIZED_SOURCE_REPOSITORY must be wsg138/EnthusiaStaff'
        return 1
    }

    git -C "$source_dir" fetch --no-tags --prune origin \
        '+refs/heads/main:refs/remotes/origin/main' || {
        source_selection_fail 'Failed to refresh the trusted origin/main ref'
        return 1
    }

    if git -C "$source_dir" cat-file -e "${normalized_sha}^{commit}" 2>/dev/null \
        && git -C "$source_dir" merge-base --is-ancestor \
            "$normalized_sha" refs/remotes/origin/main; then
        resolved_sha="$(git -C "$source_dir" rev-parse --verify "${normalized_sha}^{commit}")" || return 1
        [[ "$resolved_sha" == "$normalized_sha" ]] || {
            source_selection_fail 'Resolved main source commit does not equal the normalized requested SHA'
            return 1
        }

        VALIDATED_SOURCE_SHA="$normalized_sha"
        RESOLVED_SOURCE_SHA="$resolved_sha"
        SOURCE_SELECTION='main'
        SOURCE_IS_ANCESTOR_OF_MAIN='true'
        return 0
    fi

    [[ "${AUTHORIZED_PR_NUMBER:-}" =~ ^[1-9][0-9]*$ ]] || {
        source_selection_fail 'AUTHORIZED_PR_NUMBER must be a positive integer for non-main commits'
        return 1
    }
    [[ "${AUTHORIZED_PR_HEAD_REPOSITORY:-}" == 'wsg138/EnthusiaStaff' ]] || {
        source_selection_fail 'Authorized PR head repository must be wsg138/EnthusiaStaff'
        return 1
    }
    [[ -n "${AUTHORIZED_PR_HEAD_REF:-}" ]] \
        && git check-ref-format --branch "$AUTHORIZED_PR_HEAD_REF" >/dev/null 2>&1 || {
        source_selection_fail 'AUTHORIZED_PR_HEAD_REF must be a valid branch name'
        return 1
    }
    normalize_source_sha "${AUTHORIZED_PR_HEAD_SHA:-}" >/dev/null || {
        source_selection_fail 'AUTHORIZED_PR_HEAD_SHA must be a full 40-character SHA'
        return 1
    }

    metadata_file="$(mktemp)" || return 1
    if ! fetch_authorized_pr_metadata "$metadata_file"; then
        rm -f -- "$metadata_file"
        return 1
    fi
    if ! validate_authorized_pr_metadata "$metadata_file" "$normalized_sha"; then
        rm -f -- "$metadata_file"
        return 1
    fi
    rm -f -- "$metadata_file"

    git -C "$source_dir" update-ref -d refs/remotes/origin/authorized-pr-head 2>/dev/null || true
    git -C "$source_dir" fetch --no-tags --no-write-fetch-head origin \
        "+refs/heads/${AUTHORIZED_PR_HEAD_REF}:refs/remotes/origin/authorized-pr-head" || {
        source_selection_fail 'Failed to fetch the exact authorized same-repository PR branch'
        return 1
    }

    fetched_pr_sha="$(git -C "$source_dir" rev-parse --verify 'refs/remotes/origin/authorized-pr-head^{commit}')" || return 1
    [[ "$fetched_pr_sha" == "${AUTHORIZED_PR_HEAD_SHA,,}" ]] || {
        source_selection_fail 'Fetched authorized branch head no longer matches the requested exact SHA'
        return 1
    }
    [[ "$fetched_pr_sha" == "$normalized_sha" ]] || {
        source_selection_fail 'Fetched authorized branch head does not equal the requested SHA'
        return 1
    }

    VALIDATED_SOURCE_SHA="$normalized_sha"
    RESOLVED_SOURCE_SHA="$fetched_pr_sha"
    SOURCE_SELECTION='authorized_pr'
    SOURCE_IS_ANCESTOR_OF_MAIN='false'
    return 0
}
