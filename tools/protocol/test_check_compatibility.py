#!/usr/bin/env python3
"""Invented, nonauthoritative contracts; no game files, client data or database."""
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

from google.protobuf import descriptor_pb2 as pb
import check_compatibility as c

# These sentinels are invented test values, not real command identities.
COMMAND = 60001


def native(number=5, kind='uint32', complete=True, **properties):
    values = {'type': kind, 'wire_type': c.WIRES[kind], 'label': 'optional',
              'accepted_wire_types': [c.WIRES[kind]],
              'serialized_tags': [(number << 3) | c.WIRES[kind]], **properties}
    return {'type_identity': 'synthetic_A', 'command_id': COMMAND, 'schema_complete': complete,
            'wire_schema_complete': complete, 'blockers': [], 'semantic_aliases': [],
            'fields': [{'number': number, 'properties': values,
                        'property_statuses': {k: 'verified_client_static' for k in ['number', *values]},
                        'value_transform_chains': [[]]}]}


def server(number=5, kind='uint32', repeated=False, packed=False):
    return {'name': '.ExampleRsp', 'fields': {number: {'number': number, 'name': 'value',
        'type': kind, 'type_name': '', 'label': 'repeated' if repeated else 'optional',
        'packed': packed, 'wire_type': c.WIRES[kind],
        'serialization_wire_type': 2 if packed else c.WIRES[kind],
        'accepted_wire_types': [0, 2] if repeated and c.WIRES[kind] == 0 else [c.WIRES[kind]]}}}


def reference(row):
    result = {'format': 'astaps.protocol-compatibility-reference.v1', 'artifact': {'sha256': 'a' * 64},
              'registry': {'complete': True, 'expected_count': 1, 'command_ids': [COMMAND]},
              'messages': [row], 'enums': []}
    result['content_sha256'] = c.digest(c.canonical_bytes(result))
    return result


def compare(s, n, strong=True, direction='server_to_client'):
    return c.compare_message('ExampleRsp', s, n, {s['name']: s}, {}, {n['type_identity']: n}, {}, strong, direction)


def fixture(root, mismatch=False, unrelated=False):
    """Build an entirely synthetic source tree and hash-bound protobuf descriptor."""
    proto = root / 'protocol/7.1'
    (proto / 'compat').mkdir(parents=True)
    (proto / 'compat-imports').mkdir()
    (proto / 'protocol.desc').write_bytes(b'synthetic base descriptor identity')
    generator = root / 'tools/protocol/java/SyntheticGenerator.java'
    generator.parent.mkdir(parents=True)
    generator.write_text('class SyntheticGenerator {}')
    row = native()
    row['semantic_aliases'] = [{'name': 'ExampleRsp', 'status': 'verified',
                               'direction': 'server_to_client', 'evidence': ['synthetic assertion']}]
    ref = root / 'synthetic-reference.json'
    ref.write_text(json.dumps(reference(row)))
    desc = pb.FileDescriptorSet()
    message = desc.file.add(name='Example.proto', syntax='proto3').message_type.add(
        name='Unrelated' if unrelated else 'ExampleRsp')
    message.field.add(name='value', number=6 if mismatch else 5,
                      type=pb.FieldDescriptorProto.TYPE_UINT32, label=pb.FieldDescriptorProto.LABEL_OPTIONAL)
    path = root / 'build/generated/protocol/7.1/server.desc'
    path.parent.mkdir(parents=True)
    path.write_bytes(desc.SerializeToString())
    metadata = {'format': 'astaps.server-protocol-descriptor.v1', 'descriptor_sha256': c.digest(path.read_bytes()),
                'base_sha256': c.digest((proto / 'protocol.desc').read_bytes()),
                'proto_sources_sha256': c.proto_source_fingerprint(root),
                'generator_sources_sha256': c.generator_source_fingerprint(root)}
    Path(str(path) + '.json').write_text(json.dumps(metadata))
    code = root / 'src/main/java/emu/grasscutter'
    (code / 'net/packet').mkdir(parents=True)
    (code / 'net/packet/PacketOpcodes.java').write_text(f'public static final int ExampleRsp = {COMMAND};')
    (code / 'server/packet/send').mkdir(parents=True)
    (code / 'server/packet/send/PacketExampleRsp.java').write_text('super(PacketOpcodes.ExampleRsp);')
    return ref, path


def cli(root, *args):
    return subprocess.run([sys.executable, c.__file__, '--repo', str(root), *map(str, args)],
                          text=True, capture_output=True)


class FieldComparisonTest(unittest.TestCase):
    def test_correct_field_is_clean(self):
        self.assertEqual([], compare(server(), native()))

    def test_wrong_field_number_is_red(self):
        self.assertIn('missing_client_field', [f['code'] for f in compare(server(7), native()) if f['severity'] == 'error'])

    def test_unverified_name_cannot_make_hard_schema_errors(self):
        rows = compare(server(7), native(), strong=False)
        self.assertTrue(rows)
        self.assertFalse(any(f['severity'] == 'error' for f in rows))

    def test_unverified_property_is_not_promoted(self):
        row = native(kind='string')
        row['fields'][0]['property_statuses'] = {'number': 'verified_client_static', 'type': 'candidate'}
        self.assertEqual(['directional_wire_contract_unproved'], [r['code'] for r in compare(server(), row)])

    def test_incomplete_schema_does_not_invent_absent_fields(self):
        s = server(1); s['fields'][2] = server(2)['fields'][2]
        self.assertEqual(['incomplete_native_schema'], [r['code'] for r in compare(s, native(1, complete=False))])

    def test_reader_accepts_packed_scalar(self):
        self.assertEqual([], compare(server(repeated=True, packed=True), native(label='repeated', accepted_wire_types=[0, 2])))

    def test_reader_rejects_proved_unsupported_wire(self):
        self.assertIn('wire_type_mismatch', [r['code'] for r in compare(server(repeated=True, packed=True), native(label='repeated'))])

    def test_writer_packed_tag_is_accepted_by_unpacked_descriptor(self):
        self.assertEqual([], compare(server(repeated=True), native(label='repeated', serialized_tags=[42]), direction='client_to_server'))

    def test_signedness_and_cardinality_are_review(self):
        rows = compare(server(kind='int32', repeated=True), native())
        self.assertEqual({'scalar_or_container_type_mismatch', 'cardinality_mismatch'}, {r['code'] for r in rows})
        self.assertTrue(all(r['severity'] == 'review' for r in rows))

    def test_transform_requires_code_review(self):
        row = native(); row['fields'][0]['value_transform_chains'] = [[{'operation': 'synthetic_transform'}]]
        self.assertEqual('value_transform_requires_code_review', compare(server(), row)[0]['code'])

    def test_nested_binding_inherits_confidence(self):
        child = native(2); child.update(type_identity='synthetic_B', command_id=None)
        row = native(kind='message', type_name='synthetic_B')
        s = server(kind='message'); s['fields'][5]['type_name'] = '.ExampleChild'
        other = server(1); other['name'] = '.ExampleChild'
        rows = c.compare_message('ExampleRsp', s, row, {'.ExampleChild': other}, {},
                                 {'synthetic_A': row, 'synthetic_B': child}, {}, False, 'server_to_client')
        self.assertIn('missing_client_field', [r['code'] for r in rows])
        self.assertFalse(any(r['severity'] == 'error' for r in rows))

    def test_recursive_message_stops(self):
        s = server(kind='message'); s['fields'][5]['type_name'] = s['name']
        self.assertEqual([], compare(s, native(kind='message', type_name='synthetic_A')))

    def enum_compare(self, expected, actual):
        row = native(kind='enum', type_name='synthetic_enum')
        s = server(kind='enum'); s['fields'][5]['type_name'] = '.ExampleEnum'
        return c.compare_message('ExampleRsp', s, row, {}, {'.ExampleEnum': actual}, {},
                                 {'synthetic_enum': {'complete': True, 'values': expected}}, True, 'server_to_client')

    def test_enum_missing_is_review(self):
        row = self.enum_compare([0, 1], [0])[0]
        self.assertEqual(('enum_values_missing', 'review'), (row['code'], row['severity']))

    def test_enum_extra_is_review(self):
        row = self.enum_compare([0], [0, 9])[0]
        self.assertEqual(('server_enum_values_not_in_client_domain', 'review'), (row['code'], row['severity']))

    def test_writer_fact_cannot_prove_reader(self):
        rows = compare(server(repeated=True, packed=True), native(label='repeated', accepted_wire_types=[]))
        self.assertEqual(['directional_wire_contract_unproved'], [r['code'] for r in rows])

    def test_nominal_wire_type_cannot_create_directional_error(self):
        rows = compare(server(), native(kind='string', accepted_wire_types=[], serialized_tags=[]))
        self.assertFalse(any(r['severity'] == 'error' for r in rows))

    def test_map_gap_is_visible(self):
        self.assertEqual(['map_component_comparison_unavailable'], [r['code'] for r in compare(server(kind='map', repeated=True), native(kind='map', label='repeated'))])


class RegistryAndDebtTest(unittest.TestCase):
    def usage(self, opcode=COMMAND):
        return [{'symbol': 'ExampleRsp', 'opcode': opcode, 'direction': 'server_to_client', 'path': 'send.java', 'line': 1}]

    def test_unregistered_command_is_error(self):
        rows, _ = c.audit(reference(native()), {}, self.usage(60002), {}, {})
        self.assertIn('opcode_not_registered', [r['code'] for r in rows if r['severity'] == 'error'])

    def test_unknown_zero_is_review(self):
        rows, _ = c.audit(reference(native()), {}, self.usage(0), {}, {})
        self.assertEqual(['opcode_unavailable'], [r['code'] for r in rows])

    def test_verified_zero_checks_schema(self):
        row = native(); row['semantic_aliases'] = [{'name': 'ExampleRsp', 'status': 'verified', 'direction': 'server_to_client', 'evidence': ['synthetic assertion']}]
        rows, _ = c.audit(reference(row), {}, self.usage(0), {'.ExampleRsp': server(7)}, {})
        self.assertTrue({'semantic_opcode_mismatch', 'missing_client_field'} <= {r['code'] for r in rows if r['severity'] == 'error'})

    def test_exact_debt_does_not_whitelist_other_fields(self):
        old = c.finding('missing_client_field', 'error', 'ExampleRsp', field=5)
        new = c.finding('missing_client_field', 'error', 'ExampleRsp', field=6)
        debt = {'format': 'astaps.protocol-compatibility-debt.v1', 'entries': [{'fingerprint': old['fingerprint'], 'reason': 'synthetic regression test'}]}
        self.assertEqual([], c.classify_debt([old, new], debt))
        self.assertTrue(old['known_debt']); self.assertFalse(new['known_debt'])
        self.assertEqual([old['fingerprint']], c.classify_debt([], debt))

    def test_fingerprint_ignores_line_changes(self):
        self.assertEqual(c.finding('example', 'error', 'ExampleRsp', line=1)['fingerprint'],
                         c.finding('example', 'error', 'ExampleRsp', line=99)['fingerprint'])

    def test_reference_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'reference.json'; row = reference(native())
            path.write_text(json.dumps(row)); c.load_reference(path)
            row['registry']['command_ids'] *= 2
            path.write_text(json.dumps(row))
            with self.assertRaisesRegex(ValueError, 'content hash'): c.load_reference(path)
            row['content_sha256'] = c.digest(c.canonical_bytes({k: v for k, v in row.items() if k != 'content_sha256'}))
            path.write_text(json.dumps(row))
            with self.assertRaisesRegex(ValueError, 'unique command inventory'): c.load_reference(path)

    def test_conflicting_aliases_rejected(self):
        row = native(); row['semantic_aliases'] = [{'name': 'ExampleRsp', 'status': 'verified', 'direction': d, 'evidence': ['synthetic assertion']} for d in ['server_to_client', 'client_to_server']]
        with self.assertRaisesRegex(ValueError, 'Conflicting verified alias'):
            c.audit(reference(row), {}, self.usage(), {}, {})

    def test_bad_writer_tag_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            row = native(); row['fields'][0]['properties']['serialized_tags'] = [48]
            path = Path(tmp) / 'reference.json'; path.write_text(json.dumps(reference(row)))
            with self.assertRaisesRegex(ValueError, 'Writer tag contradicts'): c.load_reference(path)

    def test_lost_binding_is_error_until_source_removed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); path = root / 'send.java'; path.write_text('unsupported expression')
            baseline = {'monitored_bindings': [{'path': 'send.java', 'symbol': 'ExampleRsp', 'direction': 'server_to_client'}]}
            self.assertEqual('source_binding_no_longer_resolved', c.lost_bindings(root, re.compile(c.SCOPE), [], baseline)[0]['code'])
            self.assertEqual([], c.lost_bindings(root, re.compile(c.SCOPE), self.usage(), baseline))
            path.unlink(); self.assertEqual([], c.lost_bindings(root, re.compile(c.SCOPE), [], baseline))


class SourceAndCliTest(unittest.TestCase):
    def test_scanner_ignores_comments_strings_arithmetic(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); fixture(root)
            path = root / 'src/main/java/emu/grasscutter/server/packet/send/PacketExampleRsp.java'
            path.write_text('/* super(PacketOpcodes.Fake); */ String s="super(PacketOpcodes.Fake)"; super(PacketOpcodes.ExampleRsp + 1);')
            usage, unknown = c.scan_packets(root, {'ExampleRsp': COMMAND}, re.compile(c.SCOPE))
            self.assertEqual([], usage); self.assertEqual(1, len(unknown))
            path.write_text('super(PacketOpcodes.ExampleRsp);')
            self.assertEqual(COMMAND, c.scan_packets(root, {'ExampleRsp': COMMAND}, re.compile(c.SCOPE))[0][0]['opcode'])

    def test_literal_alias_parser(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'opcodes.java'
            path.write_text('public static final int A = 0xea61; public static final int B = A; public static final int C = -1; public static final int D = A + 1;')
            self.assertEqual(({'A': COMMAND, 'B': COMMAND, 'C': -1}, ['D']), c.read_opcodes(path))

    def test_hex_signedness(self):
        self.assertEqual(-1, c.java_int_literal('0xffffffff'))
        self.assertEqual(-2147483648, c.java_int_literal('0x80000000'))

    def test_unsupported_literals(self):
        for value in ['0123', '2147483648', '0x100000000', '1_000', '100L']:
            self.assertIsNone(c.java_int_literal(value))

    def test_descriptor_source_hash_binding(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); _, path = fixture(root)
            c.load_server_descriptor(root, path)
            (root / 'protocol/7.1/compat-imports/Example.proto').write_text('synthetic change')
            with self.assertRaisesRegex(ValueError, 'stale'): c.load_server_descriptor(root, path)

    def test_cli_reference_is_required(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); fixture(root)
            result = cli(root)
            self.assertEqual(2, result.returncode); self.assertIn('--reference', result.stderr)

    def test_cli_synthetic_end_to_end_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); ref, _ = fixture(root)
            report = root / 'report.json'
            result = cli(root, '--reference', ref, '--output', report)
            self.assertEqual(0, result.returncode, result.stderr)
            summary = json.loads(report.read_text())['summary']
            self.assertEqual((1, 1, 0), (summary['packet_usages'], summary['message_comparisons'], summary['new_errors']))

    def test_cli_synthetic_mismatch_is_new_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); ref, _ = fixture(root, mismatch=True)
            result = cli(root, '--reference', ref)
            self.assertEqual(1, result.returncode, result.stderr)
            self.assertIn('NEW missing_client_field', result.stdout)

    def test_cli_empty_schema_audit_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); ref, _ = fixture(root, unrelated=True)
            result = cli(root, '--reference', ref)
            self.assertEqual(2, result.returncode); self.assertIn('No descriptor comparisons', result.stderr)

    def test_cli_empty_source_audit_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); ref, _ = fixture(root)
            result = cli(root, '--reference', ref, '--scope', '^Absent$')
            self.assertEqual(2, result.returncode); self.assertIn('no supported packet usages', result.stderr)

    def test_cli_explicit_missing_baseline_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); ref, _ = fixture(root)
            self.assertEqual(2, cli(root, '--reference', ref, '--baseline', root / 'missing.json').returncode)

    def test_cli_baseline_proposal_does_not_hide_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); ref, _ = fixture(root, mismatch=True)
            debt = root / 'debt.json'
            self.assertEqual(1, cli(root, '--reference', ref, '--write-baseline', debt).returncode)
            # The proposal is not a baseline until a person replaces its placeholder reason.
            self.assertEqual(2, cli(root, '--reference', ref, '--baseline', debt).returncode)
            row = json.loads(debt.read_text())
            for entry in row['entries']:
                entry['reason'] = 'Tracked in a test'
            debt.write_text(json.dumps(row))
            self.assertEqual(0, cli(root, '--reference', ref, '--baseline', debt).returncode)
            row = json.loads(debt.read_text()); row['reference_sha256'] = 'b' * 64
            debt.write_text(json.dumps(row))
            self.assertEqual(2, cli(root, '--reference', ref, '--baseline', debt).returncode)


if __name__ == '__main__':
    unittest.main()
