"""Run with: python3 -m unittest discover -s tools/tests -v"""

import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'graphics_migration_compare.py'
SPEC = importlib.util.spec_from_file_location('graphics_migration_compare', SCRIPT)
compare = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(compare)


def fixture(directory, backend, optional=True):
    directory.mkdir(parents=True, exist_ok=True)
    report = {
        'schemaVersion': 'graphics-migration/1', 'suiteId': 'graphics-migration',
        'suiteRevision': 1, 'seed': 602263, 'scenarioId': 'graphics-migration.suite', 'status': 'PASS',
        'environment': {'minecraft': '26.2', 'requestedBackend': backend, 'actualBackend': backend,
                        'deviceName': 'Test GPU', 'vendor': 'NVIDIA', 'driver': backend + ' driver'},
        'scenes': [],
    }
    for scene_id, (width, height, tolerance) in compare.SCENES.items():
        pixels = bytes((index * 17 + 31) % 256 for index in range(width * height * 4))
        digest = hashlib.sha256(pixels).hexdigest()
        scene = {'id': scene_id, 'revision': 1, 'status': 'PASS', 'reason': '',
                 'width': width, 'height': height, 'channels': 4, 'tolerancePerChannel': tolerance,
                 'maxBadPixelRatio': 0, 'expectedSha256': digest, 'actualSha256': digest,
                 'expectedArtifact': scene_id + '-expected.rgba', 'actualArtifact': scene_id + '-actual.rgba',
                 'checks': {'readback': 'PASS'}, 'metrics': {'bytes': len(pixels)}}
        for key in ('expectedArtifact', 'actualArtifact'):
            (directory / scene[key]).write_bytes(pixels)
        report['scenes'].append(scene)
    if optional:
        report['scenes'].append({'id': 'engine-resource-reload', 'revision': 1, 'status': 'SKIP',
                                'reason': 'No stable public resource-reload hook in this version',
                                'width': 0, 'height': 0, 'channels': 4, 'tolerancePerChannel': 0,
                                'maxBadPixelRatio': 0, 'expectedSha256': '', 'actualSha256': '',
                                'expectedArtifact': '', 'actualArtifact': '', 'checks': {}, 'metrics': {}})
    path = directory / 'graphics-migration.json'
    path.write_text(json.dumps(report), encoding='utf-8')
    return path


class GraphicsMigrationComparisonTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.baseline = fixture(self.directory / 'opengl', 'OpenGL')
        self.candidate = fixture(self.directory / 'vulkan', 'Vulkan')

    def edit(self, callback, path=None):
        path = path or self.candidate
        report = json.loads(path.read_text())
        callback(report)
        path.write_text(json.dumps(report))

    def verdict(self):
        return compare.compare_reports(self.baseline, self.candidate)

    def assert_invalid(self, message):
        result = self.verdict()
        self.assertEqual('INCONCLUSIVE', result['status'])
        self.assertIn(message, str(result['issues']))

    def change_pixels(self, scene_id, kind='actual', delta=1, index=0, path=None):
        path = path or self.candidate
        report = json.loads(path.read_text())
        scene = next(scene for scene in report['scenes'] if scene['id'] == scene_id)
        raw_path = path.parent / scene[kind + 'Artifact']
        pixels = bytearray(raw_path.read_bytes())
        pixels[index] += delta
        raw_path.write_bytes(pixels)
        scene[kind + 'Sha256'] = hashlib.sha256(pixels).hexdigest()
        path.write_text(json.dumps(report))

    def test_identical_pixels_pass_with_different_api_drivers(self):
        result = self.verdict()
        self.assertEqual('PASS', result['status'])
        self.assertTrue(result['driverChanged'])
        self.assertEqual(7, len(result['scenes']))
        self.assertEqual([], result['issues'])
        required = [scene for scene in result['scenes'] if scene['status'] == 'PASS']
        self.assertEqual(6, len(required))
        self.assertTrue(all(len(scene['comparisons']) == 3 for scene in required))

    def test_optional_scene_can_be_absent_from_both_reports(self):
        for path in (self.baseline, self.candidate):
            self.edit(lambda report: report['scenes'].pop(), path)
        self.assertEqual('PASS', self.verdict()['status'])

    def test_scene_order_is_not_identity(self):
        self.edit(lambda report: report['scenes'].reverse())
        self.assertEqual('PASS', self.verdict()['status'])

    def test_unknown_metadata_is_forward_compatible(self):
        self.edit(lambda report: report.update({'futureMetadata': {'version': 2}}))
        self.edit(lambda report: report['scenes'][0].update({'futureCheck': True}))
        self.assertEqual('PASS', self.verdict()['status'])

    def test_backend_aliases_are_normalized(self):
        self.edit(lambda report: report['environment'].update({'requestedBackend': 'gl', 'actualBackend': 'OpenGL Backend'}), self.baseline)
        self.edit(lambda report: report['environment'].update({'requestedBackend': 'VK', 'actualBackend': 'Vulkan_Backend'}))
        self.assertEqual('PASS', self.verdict()['status'])
        self.assertIsNone(compare.normalize_backend('not-vulkan'))
        self.assertIsNone(compare.normalize_backend('OpenGL Vulkan'))

    def test_opengl_fallback_is_not_vulkan_success(self):
        self.edit(lambda report: report['environment'].update({'actualBackend': 'OpenGL'}))
        self.assert_invalid('BACKEND_MISMATCH')

    def test_same_backend_pair_is_not_certified(self):
        self.edit(lambda report: report['environment'].update({'actualBackend': 'gl', 'requestedBackend': 'gl'}))
        self.assert_invalid('BACKEND_MISMATCH')

    def test_backend_direction_cannot_be_reversed(self):
        self.assertEqual('INCONCLUSIVE', compare.compare_reports(self.candidate, self.baseline)['status'])

    def test_device_vendor_and_minecraft_must_match(self):
        for key in ('deviceName', 'vendor', 'minecraft'):
            original = self.candidate.read_text()
            with self.subTest(key=key):
                self.edit(lambda report: report['environment'].update({key: 'different'}))
                self.assert_invalid('environment.' + key)
            self.candidate.write_text(original)

    def test_explicit_vendor_aliases_preserve_raw_and_normalized_names(self):
        for baseline_vendor, candidate_vendor, normalized in (
                ('NVIDIA Corporation', 'NVIDIA', 'NVIDIA'),
                ('Intel', 'INTEL', 'INTEL'),
                ('Intel Inc.', 'Intel', 'INTEL'),
                ('Intel Corporation', 'Intel', 'INTEL'),
                ('ATI Technologies Inc.', 'AMD', 'AMD'),
                ('Advanced Micro Devices, Inc.', 'AMD', 'AMD'),
                (' nvidia   corporation ', 'nvidia', 'NVIDIA')):
            with self.subTest(baseline_vendor=baseline_vendor):
                self.edit(lambda report: report['environment'].update({'vendor': baseline_vendor}), self.baseline)
                self.edit(lambda report: report['environment'].update({'vendor': candidate_vendor}))
                result = self.verdict()
                self.assertEqual('PASS', result['status'])
                self.assertEqual(normalized, result['baseline']['normalizedVendor'])
                self.assertEqual(normalized, result['candidate']['normalizedVendor'])
                self.assertEqual(baseline_vendor, result['baseline']['environment']['vendor'])
                self.assertEqual(candidate_vendor, result['candidate']['environment']['vendor'])

    def test_different_known_vendors_are_not_equivalent(self):
        self.edit(lambda report: report['environment'].update({'vendor': 'AMD'}))
        self.assert_invalid('environment.vendor differs')

    def test_unknown_or_vendor_like_strings_never_infer_hardware(self):
        for vendor in ('Unknown', 'NVIDIA compatible', 'Not NVIDIA Corporation', 'Google Inc. (NVIDIA)',
                       'Mesa', 'Intel custom', 'ATI', 'Advanced Micro Devices Inc.'):
            with self.subTest(vendor=vendor):
                self.edit(lambda report: report['environment'].update({'vendor': vendor}))
                self.assert_invalid('UNKNOWN_VENDOR')
                self.assertIsNone(compare.normalize_vendor(vendor))
        self.edit(lambda report: report['environment'].update({'vendor': 'Unknown'}), self.baseline)
        self.edit(lambda report: report['environment'].update({'vendor': 'Unknown'}))
        self.assertEqual('PASS', self.verdict()['status'])
        self.assertIsNone(self.verdict()['baseline']['normalizedVendor'])
        self.edit(lambda report: report['environment'].update({'vendor': 'unknown'}))
        self.assert_invalid('UNKNOWN_VENDOR')

    def test_vendor_aliases_do_not_weaken_exact_device_identity(self):
        self.edit(lambda report: report['environment'].update({'vendor': 'NVIDIA Corporation'}), self.baseline)
        for device in ('Test GPU/PCIe/SSE2', 'test gpu', 'Test  GPU', ' Test GPU '):
            with self.subTest(device=device):
                self.edit(lambda report: report['environment'].update({'deviceName': device}))
                self.assert_invalid('environment.deviceName')

    def test_suite_identity_is_frozen(self):
        for key, value in (('scenarioId', 'other'), ('suiteId', 'other'),
                           ('schemaVersion', 'graphics-migration/2'), ('suiteRevision', 2)):
            original = self.candidate.read_text()
            with self.subTest(key=key):
                self.edit(lambda report: report.update({key: value}))
                self.assert_invalid(key)
            self.candidate.write_text(original)

    def test_seed_override_is_supported_but_must_match(self):
        self.edit(lambda report: report.update({'seed': 42}))
        self.assert_invalid('SEED_MISMATCH')
        self.edit(lambda report: report.update({'seed': 42}), self.baseline)
        self.assertEqual('PASS', self.verdict()['status'])
        self.assertEqual(42, self.verdict()['policy']['seed'])
        self.edit(lambda report: report.update({'seed': True}))
        self.assert_invalid('seed must be an integer')

    def test_environment_invalidations_fail_closed(self):
        self.edit(lambda report: report.update({'environmentValid': False}))
        self.assert_invalid('ENVIRONMENT_INVALID')
        self.edit(lambda report: report.update({'environmentValid': True, 'invalidations': ['device lost']}))
        self.assert_invalid('ENVIRONMENT_INVALID')
        self.edit(lambda report: report.update({'invalidations': []}))
        self.assertEqual('PASS', self.verdict()['status'])

    def test_environment_invalidation_fields_are_typed(self):
        original = self.candidate.read_text()
        for key, value in (('environmentValid', 'false'), ('invalidations', 'reason'), ('invalidations', [42])):
            with self.subTest(key=key):
                self.edit(lambda report: report.update({key: value}))
                self.assert_invalid(key)
            self.candidate.write_text(original)

    def test_missing_required_scene_is_invalid(self):
        self.edit(lambda report: report['scenes'].pop(0))
        self.assert_invalid('missing required scene')

    def test_duplicate_scene_is_invalid(self):
        self.edit(lambda report: report['scenes'].__setitem__(1, copy.deepcopy(report['scenes'][0])))
        self.assert_invalid('duplicate scene id')

    def test_unknown_scene_is_invalid(self):
        self.edit(lambda report: report['scenes'][0].update({'id': 'surprise'}))
        self.assert_invalid('not a recognized v1 scene')

    def test_optional_presence_must_match(self):
        self.edit(lambda report: report['scenes'].pop())
        self.assert_invalid('SCENE_SET_MISMATCH')

    def test_scene_revision_dimensions_and_policy_cannot_drift(self):
        for key, value in (('revision', 2), ('width', 18), ('height', 10), ('channels', 3),
                           ('tolerancePerChannel', 255), ('maxBadPixelRatio', 1)):
            original = self.candidate.read_text()
            with self.subTest(key=key):
                self.edit(lambda report: report['scenes'][0].update({key: value}))
                self.assert_invalid(key)
            self.candidate.write_text(original)

    def test_booleans_are_not_numeric_metadata(self):
        self.edit(lambda report: report['scenes'][0].update({'revision': True}))
        self.assert_invalid('revision')

    def test_required_scene_cannot_skip(self):
        self.edit(lambda report: report['scenes'][0].update({'status': 'SKIP', 'reason': 'unsupported'}))
        self.assert_invalid('required scenes cannot SKIP')

    def test_optional_scene_requires_reason_zero_dimensions_and_skip(self):
        for key, value in (('reason', '  '), ('width', 1), ('status', 'PASS')):
            original = self.candidate.read_text()
            with self.subTest(key=key):
                self.edit(lambda report: report['scenes'][-1].update({key: value}))
                self.assert_invalid('engine-resource-reload')
            self.candidate.write_text(original)

    def test_pass_cannot_omit_artifacts(self):
        self.edit(lambda report: report['scenes'][0].update({'actualSha256': '', 'actualArtifact': ''}))
        self.assert_invalid('required for PASS')

    def test_unpaired_hash_and_artifact_are_invalid(self):
        self.edit(lambda report: report['scenes'][0].update({'actualSha256': ''}))
        self.assert_invalid('must both be present')

    def test_missing_artifact_is_invalid(self):
        (self.candidate.parent / 'rgba-stride-orientation-actual.rgba').unlink()
        self.assert_invalid('actualArtifact')

    def test_corrupt_artifact_hash_is_invalid(self):
        raw_path = self.candidate.parent / 'rgba-stride-orientation-actual.rgba'
        data = bytearray(raw_path.read_bytes())
        data[0] += 1
        raw_path.write_bytes(data)
        self.assert_invalid('SHA-256')

    def test_truncated_and_oversized_artifacts_are_invalid(self):
        raw_path = self.candidate.parent / 'rgba-stride-orientation-actual.rgba'
        original = raw_path.read_bytes()
        for data in (original[:-1], original + b'x'):
            with self.subTest(length=len(data)):
                raw_path.write_bytes(data)
                self.assert_invalid('actualArtifact')

    def test_expected_pixels_must_be_identical(self):
        self.change_pixels('rgba-stride-orientation', kind='expected')
        self.assert_invalid('EXPECTED_IDENTITY_MISMATCH')

    def test_exact_scene_detects_single_channel_error(self):
        self.change_pixels('rgba-stride-orientation')
        result = self.verdict()
        self.assertEqual('FAIL', result['status'])
        scene = next(scene for scene in result['scenes'] if scene['id'] == 'rgba-stride-orientation')
        self.assertEqual(1, scene['comparisons']['candidateExpected']['badPixels'])

    def test_tolerance_is_inclusive(self):
        self.change_pixels('rg8-uv-channels', delta=2)
        self.assertEqual('PASS', self.verdict()['status'])
        self.change_pixels('rg8-uv-channels', delta=1)
        self.assertEqual('FAIL', self.verdict()['status'])

    def test_baseline_must_pass_its_own_expected_pixels(self):
        self.change_pixels('rgba-stride-orientation', path=self.baseline)
        result = self.verdict()
        self.assertEqual('FAIL', result['status'])
        scene = next(scene for scene in result['scenes'] if scene['id'] == 'rgba-stride-orientation')
        self.assertEqual('FAIL', scene['comparisons']['baselineExpected']['status'])

    def test_cross_backend_parity_is_independent_of_golden_tolerance(self):
        self.change_pixels('rg8-uv-channels', delta=2, path=self.baseline)
        self.change_pixels('rg8-uv-channels', delta=-2)
        result = self.verdict()
        self.assertEqual('FAIL', result['status'])
        scene = next(scene for scene in result['scenes'] if scene['id'] == 'rg8-uv-channels')
        self.assertEqual('PASS', scene['comparisons']['baselineExpected']['status'])
        self.assertEqual('PASS', scene['comparisons']['candidateExpected']['status'])
        self.assertEqual('FAIL', scene['comparisons']['backendParity']['status'])

    def test_failed_report_is_nonzero_and_blocked_report_is_inconclusive(self):
        for status, expected in (('FAIL', 'FAIL'), ('BLOCKED', 'INCONCLUSIVE')):
            with self.subTest(status=status):
                self.edit(lambda report: report.update({'status': status}))
                self.assertEqual(expected, self.verdict()['status'])

    def test_blocked_scene_can_have_no_artifacts_but_cannot_pass(self):
        self.edit(lambda report: report.update({'status': 'BLOCKED'}))
        self.edit(lambda report: report['scenes'][0].update({'status': 'BLOCKED', 'reason': 'unavailable',
                  'expectedArtifact': '', 'expectedSha256': '', 'actualArtifact': '', 'actualSha256': ''}))
        self.assertEqual('INCONCLUSIVE', self.verdict()['status'])

    def test_artifact_path_traversal_absolute_and_subdirectories_are_rejected(self):
        original = self.candidate.read_text()
        for value in ('../escape.rgba', '/tmp/escape.rgba', r'..\escape.rgba', r'C:\escape.rgba',
                      'nested/image.rgba', './image.rgba', 'https://host/image.rgba'):
            with self.subTest(path=value):
                self.edit(lambda report: report['scenes'][0].update({'actualArtifact': value}))
                self.assert_invalid('adjacent relative')
            self.candidate.write_text(original)

    def test_symlink_escape_is_rejected(self):
        source = self.candidate.parent / 'rgba-stride-orientation-actual.rgba'
        outside = self.directory / 'outside.rgba'
        outside.write_bytes(source.read_bytes())
        source.unlink()
        source.symlink_to(outside)
        self.assert_invalid('escapes report directory')

    def test_both_reports_are_validated_before_verdict(self):
        self.baseline.write_text('{')
        self.candidate.write_text('[]')
        result = self.verdict()
        self.assertEqual({'baseline', 'candidate'}, {issue['report'] for issue in result['issues']})

    def test_duplicate_json_properties_and_nonfinite_numbers_are_rejected(self):
        original = self.candidate.read_text()
        self.candidate.write_text(original.replace('"seed": 602263', '"seed": 1, "seed": 602263'))
        self.assert_invalid('duplicate JSON property')
        self.candidate.write_text(original.replace('"bytes": 612', '"bytes": NaN'))
        self.assert_invalid('non-finite')
        self.candidate.write_text(original.replace('"bytes": 612', '"bytes": 1e999'))
        self.assert_invalid('non-finite')

    def test_metrics_and_checks_have_correct_types(self):
        original = self.candidate.read_text()
        for key, value in (('metrics', {'value': True}), ('metrics', {'value': '42'}), ('checks', {'value': False})):
            with self.subTest(key=key, value=value):
                self.edit(lambda report: report['scenes'][0].update({key: value}))
                self.assert_invalid(key)
            self.candidate.write_text(original)

    def test_report_read_is_bounded(self):
        self.candidate.write_bytes(b' ' * (compare.MAX_REPORT_BYTES + 1))
        self.assert_invalid('exceeds')

    def test_alpha_only_difference_appears_in_ppm(self):
        self.change_pixels('rgba-stride-orientation', index=3, delta=9)
        result = compare.compare_reports(self.baseline, self.candidate, self.directory / 'diffs')
        self.assertEqual('FAIL', result['status'])
        path = self.directory / 'diffs/rgba-stride-orientation-candidateExpected.ppm'
        data = path.read_bytes()
        self.assertTrue(data.startswith(b'P6\n17 9\n255\n'))
        self.assertEqual(bytes([9, 9, 9]), data[len(b'P6\n17 9\n255\n'):][:3])
        self.assertEqual(17 * 9 * 3 + len(b'P6\n17 9\n255\n'), len(data))

    def test_cli_writes_detailed_json_and_exit_codes(self):
        output = self.directory / 'output/comparison.json'
        command = [sys.executable, str(SCRIPT), str(self.baseline), str(self.candidate), '--output', str(output)]
        success = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(0, success.returncode, success.stderr)
        self.assertEqual('PASS', json.loads(output.read_text())['status'])
        self.change_pixels('rgba-stride-orientation')
        failure = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(1, failure.returncode, failure.stderr)
        self.assertEqual('FAIL', json.loads(output.read_text())['status'])
        self.candidate.unlink()
        invalid = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(1, invalid.returncode, invalid.stderr)
        self.assertEqual('INCONCLUSIVE', json.loads(output.read_text())['status'])

    def test_cli_output_error_is_nonzero(self):
        output = self.directory / 'already-a-directory'
        output.mkdir()
        process = subprocess.run([sys.executable, str(SCRIPT), str(self.baseline), str(self.candidate),
                                  '--output', str(output)], capture_output=True)
        self.assertEqual(2, process.returncode)

    def test_cli_refuses_to_overwrite_evidence(self):
        before = self.candidate.read_bytes()
        process = subprocess.run([sys.executable, str(SCRIPT), str(self.baseline), str(self.candidate),
                                  '--output', str(self.candidate)], capture_output=True)
        self.assertEqual(2, process.returncode)
        self.assertEqual(before, self.candidate.read_bytes())


if __name__ == '__main__':
    unittest.main()
