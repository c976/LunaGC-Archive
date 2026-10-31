"""Invented, nonauthoritative inputs only; no client material is needed or represented."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import build_compatibility_reference as p
import check_compatibility as c


class ReferenceTests(unittest.TestCase):
    def setUp(self):
        self.hashes = {k + '_sha256': p.digest(k) for k in ('registry', 'schema', 'evidence', 'opcode_source')}
        self.registry = {'artifact': {'sha256': 'a' * 64},
                         'coverage': {'actual_constructor_entries': 2, 'static_type_slots': 2,
                                      'verified_command_class_pairs': 2, 'unresolved_command_ids': 0},
                         'entries': [{'command_id': n, 'network_cached_command_id': n, 'type_identity': t,
                                      'status': 'verified_dictionary_class_slot_leaf_getter_reader'}
                                     for n, t in [(60001, 'root'), (60002, 'other')]]}
        field = {'number': 4, 'properties': {'name': 'synthetic_value', 'type': 'message', 'type_name': 'nested',
                                           'future_property': {'private_path': 'SYNTHETIC_DO_NOT_EXPORT'}},
                 'property_statuses': {'number': 'verified_client_static', 'name': 'unknown',
                                       'type': 'verified_client_static', 'type_name': 'verified_client_static'},
                 'evidence': [{'source': 'SYNTHETIC_DO_NOT_EXPORT'}],
                 'local_field_complete': True, 'blockers': [], 'value_transform_chains': [[]]}
        def message(identity, command, dependencies, fields):
            return {'type_identity': identity, 'command_id': command, 'fields': fields,
                    'dependencies': dependencies, 'unresolved_dependencies': [], 'schema_complete': True,
                    'wire_schema_complete': True, 'local_schema_complete': True, 'nested_closure_complete': True,
                    'normal_path_only': True, 'blockers': [], 'oneofs': []}
        self.schema = {'artifact': copy.deepcopy(self.registry['artifact']),
                       'registry_sha256': self.hashes['registry_sha256'],
                       'input_snapshots': {'registry': self.hashes['registry_sha256'] + '.registry.json'},
                       'normal_path_only': True,
                       'types': [message('root', 60001, ['nested'], [field]), message('other', 60002, [], []),
                                 message('nested', None, ['enum'], [])],
                       'enums': [{'type_identity': 'enum', 'schema_complete': True, 'blockers': [], 'values': [0, 2],
                                  'numeric_domain_evidence': {'status': 'verified_all_declared_literal_values',
                                                              'values': [0, 2], 'unresolved_field_indices': []}}]}
        self.evidence = {'records': [{'message': 'ExampleReq', 'opcode': 60001,
                                     'binary_sha256': 'a' * 64, 'confidence': 'verified',
                                     'direction': 'client_to_server', 'client_status': 'observed_client',
                                     'evidence': [{'private_path': 'SYNTHETIC_DO_NOT_EXPORT'}]}]}
        self.source = 'public static final int ExampleCandidateReq = 60001;'

    def build(self):
        return p.build_reference(self.registry, self.schema, self.evidence, self.source, self.hashes, expected_count=2)

    def root(self):
        return next(m for m in self.build()['messages'] if m['command_id'] == 60001)

    def test_deterministic_replay_and_hash(self):
        first = self.build()
        self.assertEqual(p.canonical_bytes(first), p.canonical_bytes(self.build()))
        expected = first.pop('content_sha256')
        self.assertEqual(expected, p.digest(first))
        self.registry['entries'].reverse()
        self.schema['types'].reverse()
        self.assertEqual(expected, self.build()['content_sha256'])

    def test_nested_closure_and_compaction(self):
        result = self.build()
        self.assertEqual(2, len(result['messages']))
        self.assertEqual(['ExampleCandidateReq'], self.root()['candidate_aliases'])
        self.assertEqual('ExampleReq', self.root()['semantic_aliases'][0]['name'])
        self.assertEqual([0, 2], result['enums'][0]['values'])

    def test_unknown_properties_are_not_exported(self):
        field = self.root()['fields'][0]
        self.assertNotIn('name', field['properties'])
        self.assertNotIn('future_property', field['properties'])
        self.assertNotIn('evidence', field)
        self.assertEqual('message', field['properties']['type'])

    def test_projection_excludes_private_sentinels_at_every_depth(self):
        marker = 'SYNTHETIC_DO_NOT_EXPORT'
        self.registry['artifact']['preferred_base'] = marker
        self.schema['artifact']['preferred_base'] = marker
        field = self.schema['types'][0]['fields'][0]
        field['properties']['map_components'] = {'value': {'native_bytes': marker, 'address': marker}}
        field['value_transform_chains'] = [[{'operation': marker, 'value': marker}]]
        self.schema['enums'][0]['numeric_domain_evidence']['members'] = [{'native_bytes': marker}]
        self.schema['types'][0]['oneofs'] = [{'source': marker}]
        result = self.build()
        text = json.dumps(result)
        for sentinel in (marker, 'native_bytes', 'preferred_base', 'private_path', 'map_components'):
            self.assertNotIn(sentinel, text)
        self.assertTrue(self.root()['fields'][0]['value_transform_chains'][0])

    def test_identities_are_remapped(self):
        result = self.build()
        self.assertTrue(all(m['type_identity'].startswith('type_') for m in result['messages']))
        self.assertNotIn('"nested"', json.dumps(result))

    def test_unselected_enum_is_not_exported(self):
        extra = copy.deepcopy(self.schema['enums'][0]); extra['type_identity'] = 'unselected'
        self.schema['enums'].append(extra)
        self.assertEqual(1, len(self.build()['enums']))

    def test_candidates_never_verified(self):
        self.evidence['records'] = []
        self.source += ' public static final int ExampleOtherRsp = 60001;'
        self.assertEqual([], self.root()['semantic_aliases'])
        self.assertEqual(['ExampleCandidateReq', 'ExampleOtherRsp'], self.root()['candidate_aliases'])

    def test_zero_server_opcode_does_not_override_independent_join(self):
        self.source += ' public static final int ExampleReq = 0;'
        self.assertEqual('observed', self.root()['semantic_aliases'][0]['status'])

    def test_duplicate_manual_records_collapse(self):
        self.evidence['records'] *= 2
        self.assertEqual(1, len(self.root()['semantic_aliases']))

    def test_ambiguous_alias_id(self):
        row = copy.deepcopy(self.evidence['records'][0]); row['opcode'] = 60002
        self.evidence['records'].append(row)
        with self.assertRaisesRegex(ValueError, 'ambiguous semantic alias'): self.build()

    def test_ambiguous_alias_direction(self):
        row = copy.deepcopy(self.evidence['records'][0]); row['direction'] = 'server_to_client'
        self.evidence['records'].append(row)
        with self.assertRaisesRegex(ValueError, 'ambiguous semantic alias'): self.build()

    def test_wrong_registry_hash(self):
        self.schema['registry_sha256'] = 'wrong'
        with self.assertRaisesRegex(ValueError, 'mixed registry hash'): self.build()

    def test_wrong_snapshot(self):
        self.schema['input_snapshots']['registry'] = 'wrong.registry.json'
        with self.assertRaisesRegex(ValueError, 'mixed registry snapshot'): self.build()

    def test_wrong_artifact(self):
        self.schema['artifact']['extra'] = True
        with self.assertRaisesRegex(ValueError, 'mixed artifact identity'): self.build()

    def test_wrong_manual_artifact(self):
        self.evidence['records'][0]['binary_sha256'] = 'wrong'
        with self.assertRaisesRegex(ValueError, 'mixed manual artifact'): self.build()

    def test_duplicate_registry_id(self):
        self.registry['entries'][1]['command_id'] = 60001
        with self.assertRaisesRegex(ValueError, 'duplicate registry command id'): self.build()

    def test_false_registry_coverage(self):
        self.registry['coverage']['verified_command_class_pairs'] = 1
        with self.assertRaisesRegex(ValueError, 'incomplete registry coverage'): self.build()

    def test_wrong_registry_identity(self):
        self.registry['entries'][0]['type_identity'] = 'wrong'
        with self.assertRaisesRegex(ValueError, 'registry identities'): self.build()

    def test_false_completeness(self):
        self.schema['types'][0]['blockers'] = ['synthetic incomplete']
        with self.assertRaisesRegex(ValueError, 'false schema completeness'): self.build()

    def test_false_field_completeness(self):
        self.schema['types'][0]['fields'][0]['blockers'] = ['synthetic incomplete']
        with self.assertRaisesRegex(ValueError, 'false field completeness'): self.build()

    def test_false_recursive_completeness(self):
        self.schema['types'][2]['schema_complete'] = False
        with self.assertRaisesRegex(ValueError, 'false recursive completeness'): self.build()

    def test_false_enum_completeness(self):
        self.schema['enums'][0]['numeric_domain_evidence']['status'] = 'unknown'
        with self.assertRaisesRegex(ValueError, 'false enum completeness'): self.build()

    def test_false_enum_numeric_domain(self):
        self.schema['enums'][0]['numeric_domain_evidence']['values'] = [0]
        with self.assertRaisesRegex(ValueError, 'false enum completeness'): self.build()

    def test_unproved_alias_is_rejected(self):
        self.evidence['records'][0]['confidence'] = 'candidate'
        with self.assertRaisesRegex(ValueError, 'unproved semantic alias'): self.build()

    def test_duplicate_canonical_field_is_rejected(self):
        self.schema['types'][0]['fields'] *= 2
        with self.assertRaisesRegex(ValueError, 'duplicate field number'): self.build()

    def test_incomplete_missing_dependency_stays_incomplete(self):
        root = self.schema['types'][0]
        root['schema_complete'] = False
        root['dependencies'].append('unresolved')
        root['blockers'].append('synthetic incomplete')
        self.assertFalse(self.root()['schema_complete'])

    def test_no_join_from_unregistered_nested_alias(self):
        row = copy.deepcopy(self.evidence['records'][0]); row.pop('opcode')
        row['message'] = 'ExampleNested'; row['direction'] = 'unknown'
        self.evidence['records'].append(row)
        self.assertEqual(1, len(self.root()['semantic_aliases']))

    def test_literal_parser(self):
        values, unknown = p.parse_opcodes('''// int Ignored = 9;
        public static final int ExampleHex = 0xea61;
        public static final int ExampleDecimal = 60001;
        public static final int ExampleExpression = 1 << 4;
        public static final int ExampleOctal = 012;
        public static final int ExampleLong = 1L;''')
        self.assertEqual({'ExampleHex': 60001, 'ExampleDecimal': 60001}, values)
        self.assertEqual(['ExampleExpression', 'ExampleLong', 'ExampleOctal'], unknown)

    def test_synthetic_reference_roundtrip_loads(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'synthetic.json'
            path.write_bytes(p.canonical_bytes(self.build()))
            self.assertEqual(2, len(c.load_reference(path)['registry']['command_ids']))

    def test_producer_cli_to_checker_cli_roundtrip(self):
        from test_check_compatibility import fixture, cli
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            output, _ = fixture(root)
            self.schema['types'][0]['fields'] = [{
                'number': 5, 'properties': {'type': 'uint32', 'wire_type': 0,
                    'accepted_wire_types': [0], 'serialized_tags': [40], 'label': 'optional'},
                'property_statuses': {k: 'verified_client_static' for k in
                    ('number', 'type', 'accepted_wire_types', 'serialized_tags', 'label')},
                'local_field_complete': True, 'blockers': [], 'value_transform_chains': [[]]}]
            self.schema['types'][0]['dependencies'] = []
            self.evidence['records'][0].update(message='ExampleRsp', direction='server_to_client')
            registry = root / 'registry.json'
            registry.write_text(json.dumps(self.registry))
            registry_hash = p.file_hash(registry)
            self.schema['registry_sha256'] = registry_hash
            self.schema['input_snapshots']['registry'] = registry_hash + '.registry.json'
            schema = root / 'schema.json'; schema.write_text(json.dumps(self.schema))
            evidence = root / 'evidence.json'; evidence.write_text(json.dumps(self.evidence))
            source = root / 'src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java'
            result = subprocess.run([sys.executable, p.__file__, '--registry', str(registry),
                '--schema', str(schema), '--evidence', str(evidence), '--opcode-source', str(source),
                '--output', str(output), '--expected-count', '2'], text=True, capture_output=True)
            self.assertEqual(0, result.returncode, result.stderr)
            result = cli(root, '--reference', output)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(1, json.loads(result.stdout)['message_comparisons'])

    def test_invalid_provenance_hash_is_rejected(self):
        self.hashes['evidence_sha256'] = 'SYNTHETIC_DO_NOT_EXPORT'
        with self.assertRaisesRegex(ValueError, 'invalid input hash'): self.build()

    def test_producer_cli_requires_explicit_inputs(self):
        result = subprocess.run([sys.executable, p.__file__], capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn('--registry', result.stderr)


if __name__ == '__main__':
    unittest.main()
