"""Run with: python -m unittest discover -s tools/d13-parity -p 'test_*.py'."""
from copy import deepcopy
import io
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import parity


FIXTURE = Path(__file__).with_name('fixtures') / 'synthetic-window.json'


class ParityTests(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(FIXTURE.read_text(encoding='utf-8'))
        self.now = datetime(2026, 10, 15, 0, 10, tzinfo=timezone.utc)

    def run_one(self, value=None):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'private.json'
            path.write_text(json.dumps(value or self.data))
            return parity.report([str(path)], now=self.now)

    def assert_blocked(self, code):
        result = self.run_one()
        self.assertEqual('BLOCKED', result['status'])
        self.assertEqual([code], result['errors'])
        return result

    def test_synthetic_multi_account_union_match(self):
        result = self.run_one()
        self.assertEqual('FIXTURE_MATCH_ONLY', result['status'])
        self.assertEqual(2, result['discord'])
        self.assertEqual(3, result['minecraft'])
        self.assertEqual(2, result['managed_roles'])
        self.assertEqual(0, result['missing_assignments'])

    def test_missing_role_holder_export_rejected(self):
        role = self.data['legacy_mapping']['mod']
        del self.data['legacy_role_holders'][role]
        self.assert_blocked('ROLE_HOLDER_COVERAGE_INCOMPLETE')

    def test_omitted_linked_role_holder_rejected(self):
        role = self.data['legacy_mapping']['helper']
        self.data['legacy_role_holders'][role].pop()
        self.assert_blocked('ROLE_HOLDER_MEMBERSHIP_MISMATCH')

    def test_unlinked_legacy_staff_role_holder_rejected(self):
        role = self.data['legacy_mapping']['helper']
        self.data['legacy_role_holders'][role].append('1000000000000000999')
        self.assert_blocked('UNLINKED_MANAGED_ROLE_HOLDER')

    def test_duplicate_role_holder_rejected(self):
        role = self.data['legacy_mapping']['helper']
        self.data['legacy_role_holders'][role].append(self.data['legacy_role_holders'][role][0])
        self.assert_blocked('DUPLICATE_ROLE_HOLDER')

    def test_missing_managed_role_counts_drift(self):
        role = self.data['legacy_mapping']['mod']
        self.data['legacy_members'][0]['roles'].remove(role)
        self.data['legacy_role_holders'][role].remove(self.data['links'][0]['discord'])
        self.data['d13_shadow'][0]['observed_roles'].remove(role)
        self.data['d13_shadow'][0]['state'] = 'SHADOW_DRIFT'
        self.assertEqual(1, self.assert_blocked('ROLE_DRIFT_OR_COLLISION')['missing_assignments'])

    def test_extra_managed_role_counts_drift(self):
        role = self.data['legacy_mapping']['mod']
        self.data['legacy_members'][1]['roles'].append(role)
        self.data['legacy_role_holders'][role].append(self.data['links'][1]['discord'])
        self.data['d13_shadow'][1]['observed_roles'].append(role)
        self.data['d13_shadow'][1]['state'] = 'SHADOW_DRIFT'
        self.assertEqual(1, self.assert_blocked('ROLE_DRIFT_OR_COLLISION')['extra_assignments'])

    def test_unmanaged_role_is_not_extra(self):
        self.data['legacy_members'][1]['roles'].append('2000000000000000999')
        self.data['d13_shadow'][1]['observed_roles'].append('2000000000000000999')
        self.assertEqual('FIXTURE_MATCH_ONLY', self.run_one()['status'])

    def test_expected_mapping_missing(self):
        del self.data['d13_mapping']['mod']
        self.assert_blocked('MAPPING_MISMATCH')

    def test_mapping_mismatch(self):
        self.data['d13_mapping']['mod'] = '2000000000000000099'
        self.assert_blocked('MAPPING_MISMATCH')

    def test_legacy_mapping_incomplete(self):
        self.data['mapping_complete'] = False
        self.assert_blocked('INCOMPLETE_SNAPSHOT')

    def test_unapproved_enforce_rejected(self):
        self.data['d13_mode'] = 'ENFORCE'
        self.assert_blocked('UNSAFE_MODE')

    def test_inactive_legacy_writer_rejected(self):
        self.data['legacy_writer_active'] = False
        self.assert_blocked('INCOMPLETE_SNAPSHOT')

    def test_missing_link_rejected(self):
        self.data['links'].pop()
        self.assert_blocked('DISCORD_COVERAGE_INCOMPLETE')

    def test_revoked_link_rejected(self):
        self.data['links'][0]['status'] = 'REVOKED'
        self.assert_blocked('REVOKED_LINK')

    def test_stale_link_rejected(self):
        self.data['links'][0]['captured_at'] = '2026-10-14T23:45:00Z'
        self.assert_blocked('STALE_LINK')

    def test_stale_groups_rejected(self):
        self.data['accounts'][0]['captured_at'] = '2026-10-14T23:45:00Z'
        self.assert_blocked('STALE_GROUP_SNAPSHOT')

    def test_stale_legacy_role_observation_rejected(self):
        self.data['legacy_members'][0]['captured_at'] = '2026-10-14T23:45:00Z'
        self.assert_blocked('STALE_LEGACY_ROLES')

    def test_stale_d13_shadow_observation_rejected(self):
        self.data['d13_shadow'][0]['captured_at'] = '2026-10-14T23:45:00Z'
        self.assert_blocked('STALE_D13_SHADOW')

    def test_duplicate_link_to_other_discord_rejected(self):
        self.data['links'][1]['minecraft'] = self.data['links'][0]['minecraft'][:]
        self.assert_blocked('DUPLICATE_CROSS_DISCORD_LINK')

    def test_missing_minecraft_account_rejected(self):
        self.data['accounts'].pop()
        self.assert_blocked('MINECRAFT_COVERAGE_INCOMPLETE')

    def test_missing_legacy_member_rejected(self):
        self.data['legacy_members'].pop()
        self.assert_blocked('MEMBERSHIP_COVERAGE_INCOMPLETE')

    def test_missing_d13_shadow_identity_rejected(self):
        self.data['d13_shadow'].pop()
        self.assert_blocked('SHADOW_COVERAGE_INCOMPLETE')

    def test_mismatched_d13_desired_rejected(self):
        self.data['d13_shadow'][0]['desired_roles'].pop()
        self.assert_blocked('D13_DESIRED_MISMATCH')

    def test_mismatched_d13_observed_rejected(self):
        self.data['d13_shadow'][0]['observed_roles'].pop()
        self.assert_blocked('D13_OBSERVED_MISMATCH')

    def test_retry_shadow_record_is_incomplete(self):
        self.data['d13_shadow'][0]['state'] = 'RETRY'
        self.assert_blocked('INVALID_SHADOW_STATE')

    def test_missing_discord_member_is_not_match(self):
        self.data['d13_shadow'][0]['state'] = 'MEMBER_ABSENT'
        self.assert_blocked('INVALID_SHADOW_STATE')

    def test_wrong_group_source_rejected(self):
        self.data['group_source'] = 'discordsrv'
        self.assert_blocked('INVALID_GROUP_SOURCE')

    def test_forged_shadow_match_rejected(self):
        role = self.data['legacy_mapping']['mod']
        self.data['legacy_members'][0]['roles'].remove(role)
        self.data['legacy_role_holders'][role].remove(self.data['links'][0]['discord'])
        self.data['d13_shadow'][0]['observed_roles'].remove(role)
        self.assert_blocked('D13_STATE_MISMATCH')

    def test_missing_role_catalog_entry_rejected(self):
        self.data['role_catalog'].pop()
        self.assert_blocked('MISSING_EXPECTED_ROLE')

    def test_duplicate_role_names_rejected(self):
        self.data['role_catalog'][1]['name'] = 'helper'
        self.assert_blocked('DUPLICATE_ROLE_NAME')

    def test_public_role_rejected(self):
        self.data['role_catalog'][0]['public'] = True
        self.assert_blocked('ROLE_UNMANAGEABLE')

    def test_integration_role_rejected(self):
        self.data['role_catalog'][0]['integration_managed'] = True
        self.assert_blocked('ROLE_UNMANAGEABLE')

    def test_hierarchy_role_rejected(self):
        self.data['role_catalog'][0]['can_interact'] = False
        self.assert_blocked('ROLE_UNMANAGEABLE')

    def test_protected_role_rejected(self):
        self.data['protected_roles'].append(self.data['legacy_mapping']['mod'])
        self.assert_blocked('PROTECTED_ROLE_COLLISION')

    def test_role_ownership_collision_counted(self):
        role = self.data['legacy_mapping']['mod']
        self.data['ownership_claims'].extend([{'role_id':role,'namespace':'luma-guild','resource':'x'},
                                               {'role_id':role,'namespace':'playtime','resource':'y'}])
        self.assertEqual(1, self.assert_blocked('ROLE_DRIFT_OR_COLLISION')['colliding_role_ids'])

    def test_catalog_ownership_collision_rejected(self):
        self.data['role_catalog'][0]['owner'] = 'playtime-numerals'
        self.assert_blocked('ROLE_OWNERSHIP_COLLISION')

    def test_invalid_group_rejected(self):
        self.data['accounts'][0]['groups'].append('weird group')
        self.assert_blocked('INVALID_GROUP')

    def test_duplicate_member_role_rejected(self):
        self.data['legacy_members'][0]['roles'].append(self.data['legacy_members'][0]['roles'][0])
        self.assert_blocked('DUPLICATE_ROLE')

    def test_unknown_schema_field_rejected(self):
        self.data['unrecognized'] = 'extra'
        self.assert_blocked('INVALID_SCHEMA')

    def test_staging_requires_three_windows(self):
        self.data['kind'] = 'staging'
        self.assert_blocked('INSUFFICIENT_STAGING_WINDOWS')

    def test_old_staging_capture_rejected(self):
        self.data['kind'] = 'staging'
        self.now += timedelta(hours=2)
        self.assert_blocked('STALE_STAGING_CAPTURE')

    def test_three_stage_windows_require_owner_review(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for index in range(3):
                data = deepcopy(self.data)
                data['kind'] = 'staging'
                t = self.now - timedelta(minutes=3-index)
                data['captured_at'] = t.isoformat().replace('+00:00','Z')
                for section in ('links', 'accounts', 'legacy_members', 'd13_shadow'):
                    for row in data[section]:
                        row['captured_at'] = data['captured_at']
                path = Path(directory) / f'{index}.json'
                path.write_text(json.dumps(data))
                paths.append(path)
            result = parity.report(paths, now=self.now)
        self.assertEqual('OWNER_REVIEW_REQUIRED', result['status'])

    def test_repeated_stable_drift_is_counted_once(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for index in range(3):
                data = deepcopy(self.data)
                data['kind'] = 'staging'
                role = data['legacy_mapping']['mod']
                data['legacy_members'][0]['roles'].remove(role)
                data['legacy_role_holders'][role].remove(data['links'][0]['discord'])
                data['d13_shadow'][0]['observed_roles'].remove(role)
                data['d13_shadow'][0]['state'] = 'SHADOW_DRIFT'
                captured = (self.now - timedelta(minutes=3-index)).isoformat().replace('+00:00', 'Z')
                data['captured_at'] = captured
                for section in ('links', 'accounts', 'legacy_members', 'd13_shadow'):
                    for row in data[section]:
                        row['captured_at'] = captured
                path = Path(directory) / f'{index}.json'
                path.write_text(json.dumps(data))
                paths.append(path)
            result = parity.report(paths, now=self.now)
        self.assertEqual('BLOCKED', result['status'])
        self.assertEqual(1, result['missing_assignments'])

    def test_nonincreasing_capture_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for index in range(2):
                path = Path(directory) / f'{index}.json'
                path.write_text(json.dumps(self.data))
                paths.append(path)
            self.assertEqual(['NONINCREASING_WINDOWS'], parity.report(paths,now=self.now)['errors'])

    def test_duplicate_json_key_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'input.json'
            path.write_text('{"schema_version":1,"schema_version":2}')
            self.assertEqual(['DUPLICATE_JSON_KEY'], parity.report([path],now=self.now)['errors'])

    def test_staging_review_status_never_exits_successfully(self):
        with patch.object(parity, 'report', return_value={'status': 'OWNER_REVIEW_REQUIRED'}):
            with patch('sys.stdout', new_callable=io.StringIO):
                self.assertEqual(2, parity.main(['private.json']))

    def test_fixture_has_no_mutations(self):
        before = (FIXTURE.read_bytes(), FIXTURE.stat().st_mtime_ns)
        self.run_one()
        self.assertEqual(before, (FIXTURE.read_bytes(), FIXTURE.stat().st_mtime_ns))

    def test_cli_output_does_not_expose_ids_or_values(self):
        with patch('sys.stdout', new_callable=io.StringIO) as output:
            self.assertEqual(0, parity.main([str(FIXTURE)]))
        self.assertNotIn(self.data['links'][0]['discord'], output.getvalue())
        self.assertNotIn(self.data['role_catalog'][0]['name'], output.getvalue())
        self.assertNotIn('00000000-0000', output.getvalue())


if __name__ == '__main__':
    unittest.main()
