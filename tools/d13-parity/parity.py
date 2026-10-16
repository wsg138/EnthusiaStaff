#!/usr/bin/env python3
"""Offline, read-only ES-D13 parity preflight. No network, storage or role mutations."""
import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re
import sys

GROUP = re.compile(r"[a-z0-9_.-]{1,64}\Z")
DECIMAL_ID = re.compile(r"[0-9]{1,20}\Z")
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
MAX_SNOWFLAKE = 2**64 - 1
FLAGS = ("legacy_writer_active", "mapping_complete", "links_complete", "accounts_complete",
         "membership_complete", "d13_shadow_complete", "role_catalog_complete", "ownership_complete",
         "scan_complete")
MAX_BYTES = 1_000_000
MAX_SUBJECTS = 10_000


class Invalid(Exception):
    """Sanitized validation code only, never an input value."""


def require(condition, code):
    if not condition:
        raise Invalid(code)


def keys(value, expected, code):
    require(isinstance(value, dict) and set(value) == set(expected), code)


def array(value, maximum=MAX_SUBJECTS):
    require(isinstance(value, list) and len(value) <= maximum, "INVALID_ARRAY")
    return value


def unique(values, code):
    require(len(set(values)) == len(values), code)
    return set(values)


def snowflake(value):
    require(isinstance(value, str) and DECIMAL_ID.fullmatch(value) is not None, "INVALID_ROLE_ID")
    require(0 < int(value) <= MAX_SNOWFLAKE and str(int(value)) == value, "INVALID_ROLE_ID")
    return value


def group_name(value):
    require(isinstance(value, str) and GROUP.fullmatch(value) is not None, "INVALID_GROUP")
    return value


def timestamp(value):
    require(isinstance(value, str) and value.endswith("Z"), "INVALID_TIMESTAMP")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise Invalid("INVALID_TIMESTAMP") from exc
    require(parsed.tzinfo is not None, "INVALID_TIMESTAMP")
    return parsed


def paired_object(pairs):
    value = {}
    for key, item in pairs:
        require(key not in value, "DUPLICATE_JSON_KEY")
        value[key] = item
    return value


def load(path):
    require(path.is_file() and path.stat().st_size <= MAX_BYTES, "INPUT_UNAVAILABLE_OR_TOO_LARGE")
    try:
        return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=paired_object)
    except (ValueError, UnicodeError) as exc:
        raise Invalid("INVALID_JSON") from exc


def mapping(value):
    require(isinstance(value, dict) and 0 < len(value) <= 64, "INVALID_MAPPING")
    return {group_name(group): snowflake(role) for group, role in value.items()}


def link_identity(entry):
    keys(entry, ("discord", "minecraft", "status", "captured_at"), "INVALID_LINK")
    require(entry["status"] == "ACTIVE", "REVOKED_LINK")
    values = array(entry["minecraft"], 32)
    require(0 < len(values) <= 32, "INVALID_LINK")
    require(all(isinstance(x, str) and UUID.fullmatch(x) for x in values), "INVALID_MINECRAFT_ID")
    unique(values, "DUPLICATE_MINECRAFT_LINK")
    return snowflake(entry["discord"])


def account_identity(entry):
    keys(entry, ("minecraft", "groups", "on_duty", "captured_at"), "INVALID_ACCOUNT")
    ident = entry["minecraft"]
    require(isinstance(ident, str) and UUID.fullmatch(ident), "INVALID_MINECRAFT_ID")
    require(isinstance(entry["on_duty"], bool), "INVALID_DUTY_STATE")
    groups = array(entry["groups"], 128)
    unique([group_name(g) for g in groups], "DUPLICATE_GROUP")
    return ident


def member_identity(entry):
    keys(entry, ("discord", "roles", "captured_at"), "INVALID_MEMBERSHIP")
    ident = snowflake(entry["discord"])
    unique([snowflake(role) for role in array(entry["roles"], 250)], "DUPLICATE_ROLE")
    return ident


def identities(value, kind):
    validate = {"links": link_identity, "accounts": account_identity,
                "members": member_identity}[kind]
    data = {}
    for entry in array(value):
        ident = validate(entry)
        require(ident not in data, "DUPLICATE_IDENTITY")
        data[ident] = entry
    return data


def catalog_role(entry):
    keys(entry, ("id", "name", "owner", "exists", "public", "integration_managed", "can_interact"),
         "INVALID_ROLE_CATALOG")
    rid = snowflake(entry["id"])
    name = entry["name"]
    require(isinstance(name, str) and 0 < len(name) <= 100, "INVALID_ROLE_NAME")
    require(isinstance(entry["owner"], str) and bool(entry["owner"]), "INVALID_OWNER")
    require(all(isinstance(entry[k], bool) for k in
                ("exists", "public", "integration_managed", "can_interact")), "INVALID_ROLE_CATALOG")
    return rid, name.casefold()


def managed_role(role):
    require(role["owner"] == "staff-ranks", "ROLE_OWNERSHIP_COLLISION")
    require(role["exists"] and not role["public"] and not role["integration_managed"]
            and role["can_interact"], "ROLE_UNMANAGEABLE")


def validate_roles(snapshot, managed):
    catalog = {}
    seen_names = set()
    for entry in array(snapshot["role_catalog"], 500):
        rid, name = catalog_role(entry)
        require(rid not in catalog, "DUPLICATE_ROLE")
        require(name not in seen_names, "DUPLICATE_ROLE_NAME")
        seen_names.add(name)
        catalog[rid] = entry
    for rid in managed:
        require(rid in catalog, "MISSING_EXPECTED_ROLE")
        managed_role(catalog[rid])
    return catalog


def collisions(snapshot, managed):
    claims = {rid: {("staff-ranks", "d13")} for rid in managed}
    for claim in array(snapshot["ownership_claims"], 1000):
        keys(claim, ("role_id", "namespace", "resource"), "INVALID_OWNERSHIP_CLAIM")
        rid = snowflake(claim["role_id"])
        owner = (claim["namespace"], claim["resource"])
        require(all(isinstance(value, str) and 0 < len(value) <= 100 for value in owner),
                "INVALID_OWNERSHIP_CLAIM")
        claims.setdefault(rid, set()).add(owner)
    return sum(len(owners) > 1 for owners in claims.values())


def expected_count(value):
    require(isinstance(value, int) and not isinstance(value, bool) and value >= 1,
            "INVALID_EXPECTED_COUNT")


def check_header(snapshot):
    required = {"schema_version", "kind", "captured_at", "group_source", "duty_policy", "d13_mode",
                "expected_discord", "expected_minecraft", "legacy_mapping", "d13_mapping", "protected_roles",
                "links", "accounts", "legacy_members", "legacy_role_holders", "d13_shadow",
                "role_catalog", "ownership_claims", *FLAGS}
    keys(snapshot, required, "INVALID_SCHEMA")
    require(isinstance(snapshot["schema_version"], int) and not isinstance(snapshot["schema_version"], bool)
            and snapshot["schema_version"] == 1, "INVALID_VERSION")
    require(snapshot["kind"] in ("synthetic", "staging"), "INVALID_KIND")
    require(snapshot["group_source"] == "luckperms-effective-inherited", "INVALID_GROUP_SOURCE")
    require(snapshot["duty_policy"] == "ignored" and snapshot["d13_mode"] == "SHADOW", "UNSAFE_MODE")
    require(all(isinstance(snapshot[flag], bool) and snapshot[flag] for flag in FLAGS), "INCOMPLETE_SNAPSHOT")
    expected_count(snapshot["expected_discord"])
    expected_count(snapshot["expected_minecraft"])
    return timestamp(snapshot["captured_at"])


def check_identity_coverage(snapshot, captured):
    links = identities(snapshot["links"], "links")
    accounts = identities(snapshot["accounts"], "accounts")
    members = identities(snapshot["legacy_members"], "members")
    require(len(links) == snapshot["expected_discord"], "DISCORD_COVERAGE_INCOMPLETE")
    require(len(accounts) == snapshot["expected_minecraft"], "MINECRAFT_COVERAGE_INCOMPLETE")
    linked = [minecraft for link in links.values() for minecraft in link["minecraft"]]
    unique(linked, "DUPLICATE_CROSS_DISCORD_LINK")
    require(set(linked) == set(accounts), "LINK_ACCOUNT_MISMATCH")
    require(set(members) == set(links), "MEMBERSHIP_COVERAGE_INCOMPLETE")
    for link in links.values():
        require(abs(timestamp(link["captured_at"]) - captured) <= timedelta(minutes=5), "STALE_LINK")
    for account in accounts.values():
        require(abs(timestamp(account["captured_at"]) - captured) <= timedelta(minutes=5), "STALE_GROUP_SNAPSHOT")
    for member in members.values():
        require(abs(timestamp(member["captured_at"]) - captured) <= timedelta(minutes=5), "STALE_LEGACY_ROLES")
    return links, accounts, members


def check_legacy_holders(snapshot, managed, links, members):
    """Independently enumerate every holder of each D13-managed legacy role."""
    roles = snapshot["legacy_role_holders"]
    require(isinstance(roles, dict) and set(roles) == managed, "ROLE_HOLDER_COVERAGE_INCOMPLETE")
    for role_id, entries in roles.items():
        holders = unique([snowflake(user) for user in array(entries)], "DUPLICATE_ROLE_HOLDER")
        require(holders <= set(links), "UNLINKED_MANAGED_ROLE_HOLDER")
        actual = {user for user, member in members.items() if role_id in member["roles"]}
        require(holders == actual, "ROLE_HOLDER_MEMBERSHIP_MISMATCH")


def shadow_rows(snapshot, captured):
    rows = {}
    for entry in array(snapshot["d13_shadow"]):
        keys(entry, ("discord", "state", "desired_roles", "observed_roles", "captured_at"), "INVALID_SHADOW")
        ident = snowflake(entry["discord"])
        require(ident not in rows, "DUPLICATE_SHADOW_IDENTITY")
        desired = unique([snowflake(r) for r in array(entry["desired_roles"], 64)], "DUPLICATE_ROLE")
        observed = unique([snowflake(r) for r in array(entry["observed_roles"], 250)], "DUPLICATE_ROLE")
        require(entry["state"] in ("SHADOW_MATCH", "SHADOW_DRIFT"), "INVALID_SHADOW_STATE")
        require(abs(timestamp(entry["captured_at"]) - captured) <= timedelta(minutes=5), "STALE_D13_SHADOW")
        rows[ident] = entry, desired, observed
    return rows


def compare_shadow_member(link, account_groups, member, row, role_mapping):
    groups = {group for mc in link["minecraft"] for group in account_groups[mc]["groups"]}
    expected = {role_mapping[group] for group in groups if group in role_mapping}
    entry, desired, observed = row
    legacy = set(member["roles"])
    require(desired == expected, "D13_DESIRED_MISMATCH")
    require(observed == legacy, "D13_OBSERVED_MISMATCH")
    managed = set(role_mapping.values())
    absent = expected - (legacy & managed)
    surplus = (legacy & managed) - expected
    require(entry["state"] == ("SHADOW_DRIFT" if absent or surplus else "SHADOW_MATCH"),
            "D13_STATE_MISMATCH")
    return len(absent), len(surplus)


def check_shadow(snapshot, links, accounts, members, role_mapping, captured):
    rows = shadow_rows(snapshot, captured)
    require(set(rows) == set(links), "SHADOW_COVERAGE_INCOMPLETE")
    missing = extra = 0
    for ident, link in links.items():
        absent, surplus = compare_shadow_member(link, accounts, members[ident], rows[ident], role_mapping)
        missing += absent
        extra += surplus
    return missing, extra


def evaluate(snapshot, now):
    captured = check_header(snapshot)
    if snapshot["kind"] == "staging":
        require(timedelta(0) <= now - captured <= timedelta(minutes=30), "STALE_STAGING_CAPTURE")
    legacy = mapping(snapshot["legacy_mapping"])
    d13 = mapping(snapshot["d13_mapping"])
    require(legacy == d13, "MAPPING_MISMATCH")
    protected = unique([snowflake(x) for x in array(snapshot["protected_roles"], 64)], "DUPLICATE_ROLE")
    managed = set(legacy.values())
    require(not managed.intersection(protected), "PROTECTED_ROLE_COLLISION")
    links, accounts, members = check_identity_coverage(snapshot, captured)
    validate_roles(snapshot, managed)
    check_legacy_holders(snapshot, managed, links, members)
    collision_count = collisions(snapshot, managed)
    missing, extra = check_shadow(snapshot, links, accounts, members, legacy, captured)
    state = (snapshot["kind"], legacy, d13, tuple(sorted(links)),
             tuple(sorted((ident, tuple(sorted(link["minecraft"]))) for ident, link in links.items())),
             tuple(sorted((ident, tuple(sorted(record["groups"])), record["on_duty"])
                          for ident, record in accounts.items())),
             tuple(sorted((ident, tuple(sorted(member["roles"]))) for ident, member in members.items())),
             json.dumps(snapshot["role_catalog"], sort_keys=True),
             json.dumps(snapshot["ownership_claims"], sort_keys=True))
    return captured, state, dict(discord=len(links), minecraft=len(accounts), roles=len(managed),
                                 missing=missing, extra=extra, collisions=collision_count)


def check_window_order(prior_time, prior_state, captured, state, kind):
    require(prior_time is None or captured > prior_time, "NONINCREASING_WINDOWS")
    require(prior_time is None or kind != "staging"
            or captured - prior_time >= timedelta(seconds=30), "WINDOWS_TOO_CLOSE")
    require(prior_state is None or state == prior_state, "UNSTABLE_WINDOW_STATE")


def scan_windows(paths, now, summary):
    prior_time = prior_state = kind = None
    for path in paths:
        snapshot = load(Path(path))
        captured, state, counts = evaluate(snapshot, now)
        kind = snapshot["kind"]
        check_window_order(prior_time, prior_state, captured, state, kind)
        prior_time, prior_state = captured, state
        summary.update(kind=kind, discord=counts["discord"], minecraft=counts["minecraft"],
                       managed_roles=counts["roles"], missing_assignments=counts["missing"],
                       extra_assignments=counts["extra"], colliding_role_ids=counts["collisions"])
    require(kind != "staging" or len(paths) >= 3, "INSUFFICIENT_STAGING_WINDOWS")
    require(not any(summary[k] for k in ("missing_assignments", "extra_assignments", "colliding_role_ids")),
            "ROLE_DRIFT_OR_COLLISION")
    return "FIXTURE_MATCH_ONLY" if kind == "synthetic" else "OWNER_REVIEW_REQUIRED"


def report(paths, now=None):
    summary = dict(status="INVALID", kind="unknown", windows=len(paths), discord=0, minecraft=0,
                   managed_roles=0, missing_assignments=0, extra_assignments=0,
                   colliding_role_ids=0, errors=[])
    try:
        require(1 <= len(paths) <= 12, "INVALID_WINDOW_COUNT")
        summary["status"] = scan_windows(paths, now or datetime.now(timezone.utc), summary)
    except (Invalid, OSError) as error:
        summary["errors"] = [str(error) if isinstance(error, Invalid) else "INPUT_IO_FAILURE"]
        summary["status"] = "BLOCKED"
    return summary


def main(argv=None):
    parser = argparse.ArgumentParser(description="Offline D13 parity preflight (never authorizes a cutover)")
    parser.add_argument("snapshots", nargs="+", help="Private snapshot JSON paths, increasing capture-time order")
    args = parser.parse_args(argv)
    result = report(args.snapshots)
    print(json.dumps(result, sort_keys=True))
    if result["status"] == "FIXTURE_MATCH_ONLY":
        return 0
    if result["status"] == "OWNER_REVIEW_REQUIRED":
        return 2
    return 1


if __name__ == "__main__":
    sys.exit(main())
