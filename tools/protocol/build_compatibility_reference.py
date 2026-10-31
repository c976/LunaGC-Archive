#!/usr/bin/env python3
"""Project user-supplied evidence into a minimal comparison reference.

Inputs may contain sensitive or restricted material: keep them private and use
only data you are authorized to process. Projection does not grant publication
rights, establish evidence truth, or constitute a privacy/licensing review.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

from wire_facts import validate_directional_wire_facts

FORMAT = 'astaps.protocol-compatibility-reference.v1'
SCOPE = re.compile(r'.*')
KINDS = {'double', 'float', 'int64', 'uint64', 'int32', 'fixed64', 'fixed32',
         'bool', 'string', 'group', 'message', 'bytes', 'uint32', 'enum',
         'sfixed32', 'sfixed64', 'sint32', 'sint64', 'map'}
STATUSES = {'verified', 'verified_client', 'verified_client_static', 'observed_client',
            'candidate', 'unknown'}
PROOF = [{'source': 'user-supplied assertion; not independently verified by producer'}]


def canonical_bytes(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode()


def digest(value):
    return hashlib.sha256(canonical_bytes(value)).hexdigest()


def file_hash(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def parse_opcodes(source):
    """Accept literal Java int initializers only; never evaluate Java expressions."""
    source = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)
    values, unknown = {}, []
    for name, expression in re.findall(r'\b(?:public\s+)?(?:static\s+)?(?:final\s+)?int\s+(\w+)\s*=\s*([^;]+);', source):
        expression = expression.strip()
        require(name not in values and name not in unknown, 'duplicate opcode constant: ' + name)
        if re.fullmatch(r'(?:0[xX][0-9a-fA-F]+|0|[1-9][0-9]*)', expression):
            values[name] = int(expression, 16 if expression.lower().startswith('0x') else 10)
        else:
            unknown.append(name)
    return values, sorted(unknown)


def unique_index(records, key, label):
    result = {}
    for record in records:
        value = record[key]
        require(value not in result, 'duplicate ' + label + ': ' + str(value))
        result[value] = record
    return result


def validate_schema(types, enums, registered):
    roots = {}
    for identity, t in types.items():
        command = t['command_id']
        if command is not None:
            require(command not in roots, 'duplicate canonical command id')
            roots[command] = identity
        fields = unique_index(t['fields'], 'number', 'field number')
        for number, field in fields.items():
            require(type(number) is int and 0 < number < 2**29, 'invalid field number')
            if field['local_field_complete']:
                require(not field['blockers'], 'false field completeness')
        if t['schema_complete']:
            require(t['wire_schema_complete'] and t['local_schema_complete'] and t['nested_closure_complete']
                    and not t['blockers'] and not t.get('unresolved_dependencies'), 'false schema completeness')
        if t['local_schema_complete']:
            require(t['wire_schema_complete'] and all(f['local_field_complete'] for f in fields.values()),
                    'false local schema completeness')
        for dep in t.get('dependencies', []):
            if t['schema_complete']:
                require(dep in types or dep in enums, 'missing complete canonical dependency: ' + dep)
                require((types.get(dep) or enums[dep])['schema_complete'], 'false recursive completeness')
    require(roots == registered, 'canonical registry identities do not match')


def project_field(field, identities):
    """Allowlist scalar facts, never recursively copy properties or evidence."""
    properties = {}
    for key, value in field['properties'].items():
        if key == 'type' and value in KINDS:
            properties[key] = value
        elif key == 'label' and value in ('optional', 'repeated', 'required'):
            properties[key] = value
        elif key == 'type_name' and value in identities:
            properties[key] = identities[value]
        elif key == 'wire_type' and type(value) is int and 0 <= value <= 5:
            properties[key] = value
        elif key in ('accepted_wire_types', 'serialized_tags'):
            validate_directional_wire_facts(field['number'], key, value)
            properties[key] = sorted(set(value))
    statuses = {key: status if status in STATUSES else 'unknown'
                for key, status in field['property_statuses'].items()
                if key == 'number' or key in properties}
    # The checker needs only to know whether a nontrivial transform was asserted,
    # not its implementation, constants, instructions or source locations.
    transforms = [[{'operation': 'user_supplied_nontrivial_transform'}]] if any(
        field.get('value_transform_chains', [])) else [[]]
    return {'number': field['number'], 'properties': properties, 'property_statuses': statuses,
            'local_field_complete': bool(field['local_field_complete']),
            'blockers': ['incomplete user-supplied field'] if field['blockers'] else [],
            'value_transform_chains': transforms}


def build_reference(registry, schema, evidence, opcode_source, hashes, expected_count=None):
    """Validate supplied joins and project facts; does not verify their provenance."""
    for key in ('registry_sha256', 'schema_sha256', 'evidence_sha256', 'opcode_source_sha256'):
        require(re.fullmatch(r'[0-9a-f]{64}', hashes[key]) is not None, 'invalid input hash')
    require(schema['registry_sha256'] == hashes['registry_sha256'], 'mixed registry hash')
    snapshot = schema.get('input_snapshots', {}).get('registry', '')
    require(Path(snapshot).name == hashes['registry_sha256'] + '.registry.json', 'mixed registry snapshot')
    require(registry['artifact'] == schema['artifact'], 'mixed artifact identity')
    artifact_hash = registry['artifact']['sha256']
    require(re.fullmatch(r'[0-9a-f]{64}', artifact_hash) is not None, 'invalid artifact hash')
    coverage = registry['coverage']
    expected_count = len(registry['entries']) if expected_count is None else expected_count
    require(expected_count > 0, 'empty registry')
    for key in ('actual_constructor_entries', 'static_type_slots', 'verified_command_class_pairs'):
        require(coverage.get(key) == expected_count, 'incomplete registry coverage: ' + key)
    require(coverage.get('unresolved_command_ids') == 0, 'unresolved registry ids')
    entries = registry['entries']
    require(len(entries) == expected_count, 'incomplete registry entries')
    by_command = unique_index(entries, 'command_id', 'registry command id')
    unique_index(entries, 'type_identity', 'registry type identity')
    for command, entry in by_command.items():
        require(type(command) is int and 0 < command <= 65535, 'invalid registry command id')
        require(entry.get('network_cached_command_id') == command, 'registry cached id mismatch')
        require(entry.get('status') == 'verified_dictionary_class_slot_leaf_getter_reader', 'unverified registry identity')
    types = unique_index(schema['types'], 'type_identity', 'canonical type identity')
    enums = unique_index(schema['enums'], 'type_identity', 'canonical enum identity')
    require(not (types.keys() & enums.keys()), 'ambiguous canonical identity')
    validate_schema(types, enums, {c: e['type_identity'] for c, e in by_command.items()})
    opcodes, _ = parse_opcodes(opcode_source)
    aliases, joins = {}, {}
    for record in evidence['records']:
        require(record['binary_sha256'] == artifact_hash, 'mixed manual artifact hash')
        name, command = record['message'], record.get('opcode')
        require(re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', name) is not None, 'invalid semantic alias')
        direction = record['direction']
        require(direction in ('client_to_server', 'server_to_client', 'unknown'), 'invalid alias direction')
        if name in joins:
            require(joins[name] == (command, direction), 'ambiguous semantic alias: ' + name)
        joins[name] = (command, direction)
        if command is None:
            continue
        require(record.get('confidence') == 'verified' and record.get('evidence'), 'unproved semantic alias: ' + name)
        require(command in by_command, 'manual alias outside registry: ' + name)
        status = record.get('client_status')
        require(status in ('verified_client_static', 'observed_client'), 'unknown alias evidence status')
        identity = by_command[command]['type_identity']
        group = aliases.setdefault(identity, {})
        alias = group.setdefault(name, {'name': name, 'status': 'observed', 'direction': direction,
                                       'evidence': PROOF})
        if status == 'verified_client_static':
            alias['status'] = 'verified'
    candidates = {}
    for name, command in opcodes.items():
        if SCOPE.search(name) and command in by_command:
            candidates.setdefault(by_command[command]['type_identity'], []).append(name)
    selected = set(candidates) | set(aliases)
    require(selected, 'empty selected schema')
    pending, selected_enums = list(selected), set()
    while pending:
        t = types[pending.pop()]
        dependencies = set(t.get('dependencies', [])) | {
            f['properties'].get('type_name') for f in t['fields']}
        for dep in dependencies:
            if dep in enums:
                selected_enums.add(dep)
            if dep in types and dep not in selected:
                selected.add(dep)
                pending.append(dep)
    identities = {name: 'type_' + str(i) for i, name in enumerate(sorted(selected | selected_enums))}
    messages = []
    for identity in sorted(selected):
        t = types[identity]
        messages.append({'type_identity': identities[identity], 'command_id': t['command_id'],
            'wire_schema_complete': bool(t['wire_schema_complete']), 'schema_complete': bool(t['schema_complete']),
            'normal_path_only': bool(t['normal_path_only']),
            'blockers': ['incomplete user-supplied schema'] if t['blockers'] else [],
            'dependencies': sorted(identities[d] for d in t.get('dependencies', []) if d in identities),
            'semantic_aliases': [aliases[identity][n] for n in sorted(aliases.get(identity, {}))],
            'candidate_aliases': sorted(set(candidates.get(identity, []))),
            'fields': [project_field(f, identities) for f in sorted(t['fields'], key=lambda f: f['number'])]})
    compact_enums = []
    for identity in sorted(selected_enums):
        enum = enums[identity]
        domain = enum['numeric_domain_evidence']
        require(all(type(n) is int for n in enum['values']), 'invalid enum values')
        require(not enum['schema_complete'] or (not enum['blockers'] and not domain.get('unresolved_field_indices')
                and domain.get('status') == 'verified_all_declared_literal_values'
                and sorted(set(enum['values'])) == sorted(set(domain.get('values', [])))), 'false enum completeness')
        compact_enums.append({'type_identity': identities[identity], 'values': sorted(set(enum['values'])),
                              'complete': bool(enum['schema_complete'])})
    result = {'format': FORMAT, 'artifact': {'sha256': artifact_hash},
              'provenance': {key: hashes[key] for key in ('registry_sha256', 'schema_sha256',
                                                        'evidence_sha256', 'opcode_source_sha256')},
              'registry': {'complete': True, 'expected_count': expected_count, 'command_ids': sorted(by_command)},
              'normal_path_only': bool(schema['normal_path_only']), 'hotfix_execution_verified': False,
              'messages': messages, 'enums': compact_enums}
    result['content_sha256'] = digest(result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ('registry', 'schema', 'evidence', 'opcode-source', 'output'):
        parser.add_argument('--' + flag, type=Path, required=True)
    parser.add_argument('--expected-count', type=int, help='Optional independently established registry count')
    args = parser.parse_args()
    try:
        hashes = {key + '_sha256': file_hash(getattr(args, key)) for key in ('registry', 'schema', 'evidence', 'opcode_source')}
        inputs = [json.loads(getattr(args, key).read_text(encoding="utf-8")) for key in ('registry', 'schema', 'evidence')]
        result = build_reference(*inputs, args.opcode_source.read_text(encoding="utf-8"), hashes, args.expected_count)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(canonical_bytes(result) + b'\n')
        print(json.dumps({'messages': len(result['messages']), 'enums': len(result['enums']),
                          'content_sha256': result['content_sha256']}, sort_keys=True))
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        parser.exit(2, f'Cannot build reference: {error}\n')


if __name__ == '__main__':
    main()
