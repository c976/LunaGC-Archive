"""Synthetic regressions for validated checker review findings; no client data."""
import json
from pathlib import Path
import re
import tempfile
import unittest

import check_compatibility as c
import build_compatibility_reference as p
from test_check_compatibility import cli, fixture, native, reference


class ValidatedReviewTest(unittest.TestCase):
    def test_generator_edits_additions_and_removals_stale_descriptor(self):
        for change in ('edit', 'add', 'remove'):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp); ref, desc = fixture(root)
                folder = root / 'tools/protocol/java'
                folder.mkdir(parents=True, exist_ok=True)
                source = folder / 'SyntheticGenerator.java'
                source.write_text('class SyntheticGenerator {}')
                (folder / 'Unchanged.java').write_text('class Unchanged {}')
                # Bind the starting implementation, then change only generator sources.
                manifest = Path(str(desc) + '.json')
                metadata = json.loads(manifest.read_text())
                metadata['generator_sources_sha256'] = c.generator_source_fingerprint(root)
                manifest.write_text(json.dumps(metadata))
                before = {p: p.read_bytes() for p in [desc, root / 'protocol/7.1/protocol.desc']}
                if change == 'edit':
                    source.write_text('class SyntheticGenerator { int revision; }')
                elif change == 'add':
                    (folder / 'nested').mkdir()
                    (folder / 'nested/Additional.java').write_text('class Additional {}')
                else:
                    source.unlink()
                with self.assertRaises(ValueError):
                    c.load_server_descriptor(root, desc)
                result = cli(root, '--reference', ref)
                self.assertEqual(2, result.returncode)
                self.assertIn('stale', result.stderr)
                self.assertEqual(before, {p: p.read_bytes() for p in before})

    def test_generator_hash_uses_sorted_relative_paths_and_file_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); folder = root / 'tools/protocol/java'
            (folder / 'nested').mkdir(parents=True)
            (folder / 'nested/Z.java').write_bytes(b'// second\r\n')
            (folder / 'A.java').write_bytes(b'// first\n')
            (folder / 'ignored.txt').write_bytes(b'not Java')
            (folder / 'not-a-source.java').mkdir()
            rows = 'A.java\0' + c.digest(b'// first\n') + '\n'
            rows += 'nested/Z.java\0' + c.digest(b'// second\r\n') + '\n'
            self.assertEqual(c.digest(rows.encode('utf-8')), c.generator_source_fingerprint(root))

    def test_generator_sources_are_required_and_nonempty(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaises(ValueError): c.generator_source_fingerprint(root)
            (root / 'tools/protocol/java').mkdir(parents=True)
            with self.assertRaises(ValueError): c.generator_source_fingerprint(root)

    def test_legacy_manifest_missing_generator_hash_is_input_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); ref, desc = fixture(root)
            manifest = Path(str(desc) + '.json')
            metadata = json.loads(manifest.read_text()); metadata.pop('generator_sources_sha256', None)
            manifest.write_text(json.dumps(metadata))
            self.assertEqual(2, cli(root, '--reference', ref).returncode)

    def test_invalid_directional_facts_are_input_errors_even_unverified(self):
        invalid = {'accepted_wire_types': [[True], [False], [6], [7], [-1], '0', 0, None],
                   'serialized_tags': [[46], [47], [0], [-1], [True], [48], '40', 40, None]}
        for key, values in invalid.items():
            for value in values:
                for status in ('verified_client_static', 'unknown'):
                    with self.subTest(key=key, value=value, status=status), tempfile.TemporaryDirectory() as tmp:
                        root = Path(tmp); ref, _ = fixture(root)
                        row = native(); row['fields'][0]['properties'][key] = value
                        row['fields'][0]['property_statuses'][key] = status
                        ref.write_text(json.dumps(reference(row)))
                        self.assertEqual(2, cli(root, '--reference', ref).returncode)

    def test_producer_and_loader_share_directional_syntax(self):
        for key in ('accepted_wire_types', 'serialized_tags'):
            invalid = ([True], [6], None, '0') if key == 'accepted_wire_types' else ([True], [46], [0], [48], None, '40')
            for value in invalid:
                with self.subTest(key=key, value=value):
                    field = native()['fields'][0]
                    field.update(local_field_complete=False, blockers=[])
                    field['properties'][key] = value
                    field['property_statuses'][key] = 'unknown'
                    with self.assertRaisesRegex(ValueError, 'directional wire facts'):
                        p.project_field(field, {})

    def test_valid_wire_facts_keep_status_and_packed_unpacked_alternatives(self):
        with tempfile.TemporaryDirectory() as tmp:
            ref = Path(tmp) / 'reference.json'
            row = native(accepted_wire_types=[0, 2], serialized_tags=[40, 42])
            row['fields'][0]['property_statuses']['accepted_wire_types'] = 'unknown'
            ref.write_text(json.dumps(reference(row)))
            loaded = c.load_reference(ref)['messages'][0]['fields'][0]
            self.assertEqual([40, 42], loaded['properties']['serialized_tags'])
            self.assertEqual([0, 2], loaded['properties']['accepted_wire_types'])
            self.assertEqual('unknown', loaded['property_statuses']['accepted_wire_types'])

    def test_legal_directional_wire_values_and_empty_arrays_are_accepted(self):
        for values in ([], list(range(6))):
            with self.subTest(values=values), tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp) / 'reference.json'
                row = native(accepted_wire_types=values,
                             serialized_tags=[(5 << 3) | wire for wire in values])
                path.write_text(json.dumps(reference(row)))
                self.assertEqual(values, c.load_reference(path)['messages'][0]['fields'][0]
                                 ['properties']['accepted_wire_types'])

    def test_receive_literal_and_named_annotations(self):
        forms = [('60002', 60002, 'SyntheticReq'), ('value = 60003', 60003, 'SyntheticReq'),
                 ('0xea62', 60002, 'SyntheticReq'), ('value=0xea63', 60003, 'SyntheticReq'),
                 ('PacketOpcodes.ExampleRsp', 60001, 'ExampleRsp'),
                 ('value = PacketOpcodes.ExampleRsp', 60001, 'ExampleRsp')]
        for expression, opcode, symbol in forms:
            with self.subTest(expression=expression), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp); fixture(root)
                path = root / 'src/main/java/emu/grasscutter/server/packet/recv/HandlerSyntheticReq.java'
                path.parent.mkdir(parents=True)
                path.write_text('@Opcodes(' + expression + ') class HandlerSyntheticReq {}')
                usages, unknown = c.scan_packets(root, {'ExampleRsp': 60001}, re.compile(c.SCOPE))
                received = [u for u in usages if u['direction'] == 'client_to_server']
                self.assertEqual([(symbol, opcode)], [(u['symbol'], u['opcode']) for u in received])
                self.assertEqual([], unknown)

    def test_receive_literals_reach_cli_registry_check(self):
        for expression in ('60002', 'value=60003', '0xea62', 'value=0xea63'):
            with self.subTest(expression=expression), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp); ref, _ = fixture(root)
                path = root / 'src/main/java/emu/grasscutter/server/packet/recv/HandlerSyntheticReq.java'
                path.parent.mkdir(parents=True)
                path.write_text('@Opcodes(' + expression + ') class HandlerSyntheticReq {}')
                result = cli(root, '--reference', ref)
                self.assertEqual(1, result.returncode, result.stderr)
                self.assertIn('NEW opcode_not_registered: SyntheticReq', result.stdout)

    def test_unsupported_receive_forms_remain_unknown(self):
        expressions = ['60002 + 1', 'value=60002 + 1', 'resolve(60002)',
                       'PacketOpcodes.ExampleRsp + 1', 'value=PacketOpcodes.ExampleRsp, disabled=true',
                       '60002, disabled=true', 'value=60002, disabled=false']
        for expression in expressions:
            with self.subTest(expression=expression), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp); fixture(root)
                path = root / 'src/main/java/emu/grasscutter/server/packet/recv/HandlerSyntheticReq.java'
                path.parent.mkdir(parents=True)
                path.write_text('@Opcodes(' + expression + ') class HandlerSyntheticReq {}')
                usages, unknown = c.scan_packets(root, {'ExampleRsp': 60001}, re.compile(c.SCOPE))
                self.assertFalse(any(u['direction'] == 'client_to_server' for u in usages))
                self.assertEqual(1, len(unknown))

    def test_unresolved_named_bindings_keep_distinct_source_context(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); ref, _ = fixture(root)
            folder = root / 'src/main/java/emu/grasscutter/server/packet/recv'
            folder.mkdir(parents=True)
            for name in ('First', 'Second'):
                (folder / ('Handler' + name + '.java')).write_text('@Opcodes(PacketOpcodes.MissingSynthetic)')
            report = root / 'report.json'
            result = cli(root, '--reference', ref, '--output', report)
            self.assertEqual(0, result.returncode, result.stderr)
            rows = json.loads(report.read_text())['findings']
            self.assertEqual(2, len(rows))
            self.assertEqual({'MissingSynthetic'}, {r['symbol'] for r in rows})
            self.assertEqual(2, len({r['fingerprint'] for r in rows}))
            self.assertTrue(all(r['line'] == 1 and r['severity'] == 'unknown' for r in rows))

    def test_unknown_fingerprints_include_path_and_reason_but_not_line(self):
        def fingerprint(path='first.java', reason='unsupported', line=1):
            return c.finding('source_binding_unresolved', 'unknown', 'Synthetic',
                             path=path, reason=reason, line=line)['fingerprint']
        self.assertEqual(fingerprint(), fingerprint(line=99))
        self.assertNotEqual(fingerprint(), fingerprint(path='second.java'))
        self.assertNotEqual(fingerprint(), fingerprint(reason='unresolved constant'))

    def test_mixed_unknown_sources_are_counted_without_failing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); ref, _ = fixture(root)
            folder = root / 'src/main/java/emu/grasscutter/server/packet/recv'
            folder.mkdir(parents=True)
            for name in ('First', 'Second'):
                (folder / ('Handler' + name + '.java')).write_text('// @Opcodes(60002)\nString text="@Opcodes(60003)";')
            report = root / 'report.json'
            result = cli(root, '--reference', ref, '--output', report)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(2, json.loads(result.stdout)['unknown'])
            data = json.loads(report.read_text())
            rows = [f for f in data['findings'] if f['severity'] == 'unknown']
            self.assertEqual(2, len(data['unresolved_usage']))
            self.assertEqual(2, len({f['fingerprint'] for f in rows}))
            self.assertTrue(all(f['path'] and f['reason'] and f['direction'] for f in rows))


if __name__ == '__main__':
    unittest.main()
