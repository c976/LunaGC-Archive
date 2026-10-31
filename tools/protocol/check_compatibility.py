#!/usr/bin/env python3
"""Read-only, evidence-qualified server/client protocol comparison; never remaps packets."""
import argparse
import hashlib
import json
import re
from pathlib import Path

from google.protobuf import descriptor_pb2
from google.protobuf.message import DecodeError
from wire_facts import validate_directional_wire_facts

ROOT = Path(__file__).resolve().parents[2]
SCOPE = r".*"
VERIFIED = {"verified", "verified_client", "verified_client_static", "observed_client"}
TYPES = {1: "double", 2: "float", 3: "int64", 4: "uint64", 5: "int32", 6: "fixed64",
         7: "fixed32", 8: "bool", 9: "string", 10: "group", 11: "message", 12: "bytes",
         13: "uint32", 14: "enum", 15: "sfixed32", 16: "sfixed64", 17: "sint32", 18: "sint64"}
WIRES = {"double": 1, "float": 5, "int64": 0, "uint64": 0, "int32": 0, "fixed64": 1,
         "fixed32": 5, "bool": 0, "string": 2, "group": 3, "message": 2, "bytes": 2,
         "uint32": 0, "enum": 0, "sfixed32": 5, "sfixed64": 1, "sint32": 0, "sint64": 0,
         "map": 2}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def canonical_bytes(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode()


def load_reference(path):
    reference = json.loads(Path(path).read_text(encoding="utf-8"))
    if reference.get("format") != "astaps.protocol-compatibility-reference.v1":
        raise ValueError("Unsupported client reference format")
    unsigned = {k: v for k, v in reference.items() if k != "content_sha256"}
    if reference.get("content_sha256") != digest(canonical_bytes(unsigned)):
        raise ValueError("Client reference content hash differs; rebuild the reference")
    registry = reference.get("registry", {})
    ids = registry.get("command_ids", [])
    if (registry.get("complete") is not True or len(ids) != registry.get("expected_count")
            or not ids or ids != sorted(set(ids))
            or any(type(n) is not int or not 0 < n <= 65535 for n in ids)):
        raise ValueError("Client registry is not a complete unique command inventory")
    if not re.fullmatch(r"[0-9a-f]{64}", reference.get("artifact", {}).get("sha256", "")):
        raise ValueError("Missing client artifact identity")
    identities, commands = set(), set()
    for row in reference.get("messages", []):
        identity = row["type_identity"]
        if identity in identities:
            raise ValueError("Duplicate native type identity")
        identities.add(identity)
        cmd = row.get("command_id")
        if cmd is not None:
            if cmd not in ids or cmd in commands:
                raise ValueError("Duplicate or unregistered reference command")
            commands.add(cmd)
        numbers = [f["number"] for f in row.get("fields", [])]
        if len(numbers) != len(set(numbers)) or any(type(n) is not int or not 0 < n < 2**29 for n in numbers):
            raise ValueError("Invalid or duplicate native field numbers")
        for field in row.get("fields", []):
            properties = field.get("properties", {})
            for key in ("serialized_tags", "accepted_wire_types"):
                if key in properties:
                    validate_directional_wire_facts(field["number"], key, properties[key])
    return reference


def proto_source_fingerprint(repo):
    rows = []
    for folder in ("compat", "compat-imports"):
        root = repo / "protocol/7.1" / folder
        if not root.is_dir():
            raise ValueError(f"Missing protocol source directory: {root}")
        for path in sorted(root.rglob("*.proto")):
            rows.append(f"{folder}/{path.relative_to(root).as_posix()}\0{digest(path.read_bytes())}\n")
    return digest("".join(sorted(rows)).encode())


def generator_source_fingerprint(repo):
    root = repo / "tools/protocol/java"
    if not root.is_dir():
        raise ValueError(f"Missing protocol generator source directory: {root}")
    paths = sorted(path for path in root.rglob("*.java") if path.is_file())
    if not paths:
        raise ValueError(f"Missing protocol generator Java sources: {root}")
    rows = [f"{path.relative_to(root).as_posix()}\0{digest(path.read_bytes())}\n" for path in paths]
    return digest("".join(sorted(rows)).encode("utf-8"))


def load_server_descriptor(repo, path):
    data = path.read_bytes()
    metadata = json.loads(Path(str(path) + ".json").read_text(encoding="utf-8"))
    if metadata.get("format") != "astaps.server-protocol-descriptor.v1":
        raise ValueError("Unsupported server descriptor manifest")
    expected = {"descriptor_sha256": digest(data),
                "base_sha256": digest((repo / "protocol/7.1/protocol.desc").read_bytes()),
                "proto_sources_sha256": proto_source_fingerprint(repo),
                "generator_sources_sha256": generator_source_fingerprint(repo)}
    if any(metadata.get(k) != v for k, v in expected.items()):
        raise ValueError("Server descriptor is stale or modified; run ./gradlew generateProtocolJava")
    file_set = descriptor_pb2.FileDescriptorSet.FromString(data)
    messages, enums = {}, {}

    def enum(row, prefix):
        name = "." + ".".join(filter(None, (prefix, row.name)))
        if name in enums:
            raise ValueError(f"Duplicate server enum {name}")
        enums[name] = sorted(set(v.number for v in row.value))

    def message(row, prefix, syntax):
        name = "." + ".".join(filter(None, (prefix, row.name)))
        if name in messages:
            raise ValueError(f"Duplicate server message {name}")
        fields = {}
        for field in row.field:
            kind = TYPES[field.type]
            repeated = field.label == field.LABEL_REPEATED
            packable = kind not in {"string", "bytes", "message", "group"}
            packed = repeated and packable and (field.options.packed if field.options.HasField("packed") else syntax == "proto3")
            fields[field.number] = {"number": field.number, "name": field.name, "type": kind,
                "type_name": field.type_name, "label": "repeated" if repeated else "optional",
                "packed": packed, "wire_type": WIRES[kind],
                "serialization_wire_type": 2 if packed else WIRES[kind],
                "accepted_wire_types": [0, 2] if repeated and packable and WIRES[kind] == 0
                    else sorted({WIRES[kind], 2}) if repeated and packable else [WIRES[kind]]}
        messages[name] = {"name": name, "fields": fields, "map_entry": row.options.map_entry}
        for child in row.nested_type:
            message(child, name.lstrip("."), syntax)
        for child in row.enum_type:
            enum(child, name.lstrip("."))

    for file in file_set.file:
        for row in file.message_type:
            message(row, file.package, file.syntax)
        for row in file.enum_type:
            enum(row, file.package)
    for row in messages.values():
        for field in row["fields"].values():
            target = messages.get(field["type_name"])
            if field["type"] == "message" and target and target["map_entry"]:
                field["type"] = "map"
    return messages, enums, expected


def mask_java(text):
    # Preserve offsets/newlines while ignoring comments, literals, and text blocks.
    pattern = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*'
    return re.sub(pattern, lambda m: re.sub(r"[^\n]", " ", m.group()), text)


def java_int_literal(text):
    if not re.fullmatch(r"-?(?:0[xX][0-9a-fA-F]+|0|[1-9][0-9]*)", text):
        return None
    hexadecimal = "0x" in text.lower()
    value = int(text, 16 if hexadecimal else 10)
    if hexadecimal:
        if abs(value) > 0xffffffff:
            return None
        return (value + 2**31) % 2**32 - 2**31
    return value if -(2**31) <= value < 2**31 else None


def read_opcodes(path):
    text = mask_java(path.read_text(encoding="utf-8"))
    expressions = dict(re.findall(r"\bpublic\s+static\s+final\s+int\s+(\w+)\s*=\s*([^;]+);", text))
    result = {}
    for _ in range(len(expressions) + 1):
        before = len(result)
        for name, expression in expressions.items():
            value = expression.strip()
            literal = java_int_literal(value)
            if literal is not None:
                result[name] = literal
            elif re.fullmatch(r"(?:PacketOpcodes\.)?\w+", value):
                alias = value.removeprefix("PacketOpcodes.")
                if alias in result:
                    result[name] = result[alias]
        if len(result) == before:
            break
    return result, sorted(set(expressions) - set(result))


def scan_packets(repo, opcodes, scope):
    usage, unresolved = [], []
    roots = [("server_to_client", repo / "src/main/java/emu/grasscutter/server/packet/send"),
             ("client_to_server", repo / "src/main/java/emu/grasscutter/server/packet/recv")]
    for direction, root in roots:
        for path in sorted(root.rglob("*.java")):
            text = mask_java(path.read_text(encoding="utf-8"))
            if direction == "server_to_client":
                matches = list(re.finditer(r"\bsuper\s*\(\s*(?:emu\.grasscutter\.net\.packet\.)?PacketOpcodes\.(\w+)\s*(?=[,)])", text))
                literal = list(re.finditer(r"\bsuper\s*\(\s*(-?(?:0[xX][0-9a-fA-F]+|0|[1-9][0-9]*))\s*[,)]", text))
                matches += literal
            else:
                # Match the complete annotation argument, not a prefix of an expression.
                # Extra attributes may disable registration and are deliberately unsupported.
                matches = list(re.finditer(
                    r"@Opcodes\s*\(\s*(?:value\s*=\s*)?"
                    r"(?:PacketOpcodes\.(\w+)|(-?(?:0[xX][0-9a-fA-F]+|0|[1-9][0-9]*)))\s*\)", text))
            if not matches and scope.search(path.stem):
                unresolved.append({"path": path.relative_to(repo).as_posix(), "direction": direction,
                                   "reason": "no supported literal opcode binding"})
            for match in matches:
                name = match.group(1) or match.group(2)
                prefix = "Packet" if direction == "server_to_client" else "Handler"
                symbol = path.stem.removeprefix(prefix) if java_int_literal(name) is not None else name
                if not scope.search(symbol) and not scope.search(path.stem):
                    continue
                number = opcodes.get(name)
                if number is None:
                    number = java_int_literal(name)
                item = {"symbol": symbol, "opcode": number, "direction": direction,
                        "path": path.relative_to(repo).as_posix(), "line": text[:match.start()].count("\n") + 1}
                if number is None:
                    unresolved.append({**item, "reason": "opcode expression unresolved"})
                elif item not in usage:
                    usage.append(item)
    return usage, unresolved


def finding(code, severity, symbol, expected=None, actual=None, field=None, **context):
    item = {"code": code, "severity": severity, "symbol": symbol, "field": field,
            "expected": expected, "actual": actual, **context}
    stable = {k: item.get(k) for k in ("code", "symbol", "field", "expected", "actual", "direction", "native_type")}
    if code == "source_binding_unresolved":
        stable.update({k: item.get(k) for k in ("path", "reason")})
    item["fingerprint"] = digest(canonical_bytes(stable))
    return item


def compare_message(symbol, server, native, messages, enums, native_types, native_enums,
                    strong=False, direction="unknown", visited=None):
    visited = set() if visited is None else visited
    pair = (server["name"], native["type_identity"])
    if pair in visited:
        return []
    visited.add(pair)
    results = []
    context = {"direction": direction, "native_type": native["type_identity"],
               "binding": "verified_semantic_identity" if strong else "opcode_name_candidate"}
    severity = "error" if strong else "review"
    if not native.get("schema_complete"):
        results.append(finding("incomplete_native_schema", "unknown", symbol, actual=native.get("blockers", []), **context))
    for field in native.get("fields", []):
        number = field["number"]
        statuses, properties = field.get("property_statuses", {}), field.get("properties", {})
        if statuses.get("number") not in VERIFIED:
            continue
        target = server["fields"].get(number)
        accepted = properties.get("accepted_wire_types")
        serialized = properties.get("serialized_tags")
        directional_proof = (direction == "server_to_client" and statuses.get("accepted_wire_types") in VERIFIED and bool(accepted)) or (direction == "client_to_server" and statuses.get("serialized_tags") in VERIFIED and bool(serialized))
        field_severity = severity if directional_proof else "review"
        if not directional_proof:
            results.append(finding("directional_wire_contract_unproved", "unknown", symbol,
                field=number, note="Nominal wire type does not prove this direction's acceptance or emission", **context))
        if target is None:
            results.append(finding("missing_client_field", field_severity, symbol, expected=number, field=number,
                                   evidence=field.get("evidence", []), **context))
            continue
        # Direction matters: a packed writer tag is not a reader's acceptance set.
        if direction == "server_to_client" and statuses.get("accepted_wire_types") in VERIFIED and accepted:
            wire_ok = target["serialization_wire_type"] in accepted
        elif direction == "client_to_server" and statuses.get("serialized_tags") in VERIFIED and serialized:
            wire_ok = all((tag & 7) in target["accepted_wire_types"] for tag in serialized)
        else:
            wire_ok = None
        if wire_ok is False:
            results.append(finding("wire_type_mismatch", severity, symbol,
                expected={k: properties.get(k) for k in ("wire_type", "accepted_wire_types", "serialized_tags")},
                actual={k: target[k] for k in ("wire_type", "serialization_wire_type", "accepted_wire_types")},
                field=number, evidence=field.get("evidence", []), **context))
        kind = properties.get("type")
        if statuses.get("type") in VERIFIED and kind and kind != target["type"]:
            results.append(finding("scalar_or_container_type_mismatch", "review", symbol,
                expected=kind, actual=target["type"], field=number, evidence=field.get("evidence", []), **context))
        if statuses.get("label") in VERIFIED and properties.get("label") != target["label"]:
            results.append(finding("cardinality_mismatch", "review", symbol, expected=properties.get("label"),
                                   actual=target["label"], field=number, **context))
        chains = field.get("value_transform_chains", [])
        if any(chain for chain in chains):
            results.append(finding("value_transform_requires_code_review", "review", symbol,
                expected=chains, actual="server value transformation not analyzed", field=number,
                evidence=field.get("evidence", []), **context))
        if kind == target["type"] == "map":
            results.append(finding("map_component_comparison_unavailable", "unknown", symbol, field=number,
                note="Map outer wire is checked; key/value and nested-map semantics require further evidence-aware support", **context))
        identity = properties.get("type_name")
        if statuses.get("type_name") in VERIFIED and kind == target["type"] == "message":
            child, native_child = messages.get(target["type_name"]), native_types.get(identity)
            if child and native_child:
                results += compare_message(symbol + "." + target["name"], child, native_child,
                    messages, enums, native_types, native_enums, strong, direction, visited)
            else:
                results.append(finding("nested_binding_unresolved", "unknown", symbol, field=number, **context))
        if statuses.get("type_name") in VERIFIED and kind == target["type"] == "enum":
            domain = native_enums.get(identity)
            server_values = enums.get(target["type_name"])
            if domain and domain.get("complete") and server_values is not None:
                missing = sorted(set(domain["values"]) - set(server_values))
                if missing:
                    results.append(finding("enum_values_missing", "review", symbol,
                        expected=missing, actual=server_values, field=number,
                        note="Proto3 can carry unknown enum numbers; inspect value-based consumers", **context))
                extra = sorted(set(server_values) - set(domain["values"]))
                if extra:
                    results.append(finding("server_enum_values_not_in_client_domain", "review", symbol,
                        expected=domain["values"], actual=extra, field=number,
                        note="Numeric domain only; enum names and consumer behavior are not inferred", **context))
    if native.get("wire_schema_complete"):
        numbers = {f["number"] for f in native.get("fields", []) if f.get("property_statuses", {}).get("number") in VERIFIED}
        for number in sorted(set(server["fields"]) - numbers):
            results.append(finding("server_field_not_in_complete_client_set", "review", symbol,
                actual=number, field=number, note="Unknown protobuf fields may be skipped; not automatically a fatal mismatch", **context))
    return results


def audit(reference, opcodes, usage, messages, enums):
    registered = set(reference["registry"]["command_ids"])
    native_types = {row["type_identity"]: row for row in reference["messages"]}
    by_command = {row["command_id"]: row for row in reference["messages"] if row.get("command_id") is not None}
    native_enums = {row["type_identity"]: row for row in reference.get("enums", [])}
    aliases = {}
    for row in reference["messages"]:
        for alias in row.get("semantic_aliases", []):
            if alias.get("status") not in VERIFIED | {"observed"} or not alias.get("evidence"):
                continue
            name = alias["name"]
            if name in aliases and (aliases[name][0]["command_id"] != row.get("command_id")
                    or aliases[name][1].get("direction") != alias.get("direction")):
                raise ValueError(f"Conflicting verified alias: {name}")
            aliases[name] = (row, alias)
    simple_messages = {}
    for name, message in messages.items():
        simple_messages.setdefault(name.rsplit(".", 1)[-1], []).append(message)
    results, checked = [], set()
    for item in usage:
        symbol, opcode = item["symbol"], item["opcode"]
        context = {k: item[k] for k in ("path", "line", "direction")}
        known = aliases.get(symbol)
        native, alias = known if known else (by_command.get(opcode), {})
        strong = bool(known)
        if opcode <= 0:
            results.append(finding("opcode_unavailable", "review", symbol, actual=opcode,
                note="A declaration alone does not prove the unavailable packet is actually sent", **context))
        elif opcode not in registered:
            results.append(finding("opcode_not_registered", "error", symbol, actual=opcode,
                expected="registered client command", **context))
        if strong and native["command_id"] != opcode:
            results.append(finding("semantic_opcode_mismatch", "error", symbol,
                expected=native["command_id"], actual=opcode, evidence=alias["evidence"], **context))
        if strong and alias.get("direction") not in {None, "unknown", item["direction"]}:
            results.append(finding("direction_mismatch", "error", symbol,
                expected=alias["direction"], actual=item["direction"], **context))
        if opcode <= 0 and not strong:
            continue
        if native is None:
            results.append(finding("schema_not_in_reference", "unknown", symbol, actual=opcode, **context))
            continue
        choices = simple_messages.get(symbol, [])
        if len(choices) != 1:
            results.append(finding("server_descriptor_binding_unresolved", "unknown", symbol,
                actual=len(choices), note="Cannot infer the serialized payload from a wrapper class name", **context))
            continue
        key = (symbol, native["type_identity"], item["direction"])
        if key not in checked:
            checked.add(key)
            results += compare_message(symbol, choices[0], native, messages, enums,
                                       native_types, native_enums, strong, item["direction"])
    # Constructor overloads should not multiply a single finding.
    unique = {item["fingerprint"]: item for item in results}
    return sorted(unique.values(), key=lambda f: (f["severity"], f["symbol"], f["code"], f["field"] or 0)), len(checked)


def lost_bindings(repo, scope, usage, baseline):
    seen = {(u["path"], u["symbol"], u["direction"]) for u in usage}
    result = []
    for item in baseline.get("monitored_bindings", []):
        path = Path(item["path"])
        if path.is_absolute() or ".." in path.parts:
            raise ValueError("Monitored bindings must use repository-relative paths")
        key = (item["path"], item["symbol"], item["direction"])
        if (scope.search(item["symbol"]) and (repo / path).is_file() and key not in seen):
            result.append(finding("source_binding_no_longer_resolved", "error", item["symbol"],
                expected={"path": item["path"], "symbol": item["symbol"]},
                actual="existing source no longer has a supported opcode binding",
                direction=item["direction"], path=item["path"],
                note="Audit coverage regressed; this is not proof of a live protocol failure"))
    return result


PLACEHOLDER_REASON = "Existing mismatch; review and add a tracking reference"


def classify_debt(findings, baseline):
    if baseline.get("format") != "astaps.protocol-compatibility-debt.v1":
        raise ValueError("Unsupported compatibility debt format")
    entries = baseline.get("entries", [])
    if any(not e.get("reason") or not re.fullmatch(r"[0-9a-f]{64}", e.get("fingerprint", "")) for e in entries):
        raise ValueError("Debt entries require exact fingerprints and reasons")
    if any(e.get("reason", "").strip() == PLACEHOLDER_REASON for e in entries):
        raise ValueError("Debt entries still carry the proposed placeholder reason")
    if len({e["fingerprint"] for e in entries}) != len(entries):
        raise ValueError("Duplicate debt fingerprints")
    known = {e["fingerprint"]: e["reason"] for e in entries}
    active = set()
    for row in findings:
        row["known_debt"] = row["severity"] == "error" and row["fingerprint"] in known
        if row["known_debt"]:
            active.add(row["fingerprint"])
            row["debt_reason"] = known[row["fingerprint"]]
    return sorted(set(known) - active)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=ROOT)
    parser.add_argument("--reference", type=Path, required=True,
                        help="User-supplied reference you are authorized to use; none is bundled")
    parser.add_argument("--descriptor", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--scope", default=SCOPE, help="Regex selecting source packet names")
    parser.add_argument("--output", type=Path, help="Deterministic JSON report")
    parser.add_argument("--write-baseline", type=Path, help="Explicitly record current hard-error debt for human review")
    args = parser.parse_args()
    repo = args.repo.resolve()
    try:
        reference = load_reference(args.reference)
        messages, enums, provenance = load_server_descriptor(repo, args.descriptor or repo / "build/generated/protocol/7.1/server.desc")
        opcodes, unresolved_constants = read_opcodes(repo / "src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java")
        scope = re.compile(args.scope)
        usage, unresolved_usage = scan_packets(repo, opcodes, scope)
        if not usage:
            raise ValueError("Scope contains no supported packet usages; refusing an empty successful audit")
        findings, comparisons = audit(reference, opcodes, usage, messages, enums)
        if not comparisons:
            raise ValueError("No descriptor comparisons were possible; refusing an empty schema audit")
        # No implicit repository debt: a missing explicitly requested baseline is an error.
        baseline = json.loads(args.baseline.read_text(encoding="utf-8")) if args.baseline else {"format": "astaps.protocol-compatibility-debt.v1", "entries": []}
        if args.baseline and (baseline.get("client_artifact_sha256") != reference["artifact"]["sha256"]
                or baseline.get("reference_sha256") != reference["content_sha256"]):
            raise ValueError("Debt belongs to a different reference; review it before rebaselining")
        for item in unresolved_usage:
            context = {k: v for k, v in item.items() if k != "symbol"}
            findings.append(finding("source_binding_unresolved", "unknown",
                item.get("symbol", Path(item["path"]).stem), **context))
        findings += lost_bindings(repo, scope, usage, baseline)
        resolved = classify_debt(findings, baseline)
        if args.write_baseline:
            debt = {"format": "astaps.protocol-compatibility-debt.v1",
                    "client_artifact_sha256": reference["artifact"]["sha256"],
                    "reference_sha256": reference["content_sha256"],
                    "monitored_bindings": sorted({(u["path"], u["symbol"], u["direction"]) for u in usage}),
                    "entries": [
                {"fingerprint": row["fingerprint"], "reason": PLACEHOLDER_REASON,
                 "code": row["code"], "symbol": row["symbol"]} for row in findings if row["severity"] == "error"]}
            debt["monitored_bindings"] = [dict(zip(("path", "symbol", "direction"), item)) for item in debt["monitored_bindings"]]
            args.write_baseline.parent.mkdir(parents=True, exist_ok=True)
            args.write_baseline.write_text(json.dumps(debt, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        summary = {"packet_usages": len(usage), "message_comparisons": comparisons,
                   "new_errors": sum(f["severity"] == "error" and not f["known_debt"] for f in findings),
                   "known_errors": sum(f["known_debt"] for f in findings),
                   "review": sum(f["severity"] == "review" for f in findings),
                   "unknown": sum(f["severity"] == "unknown" for f in findings),
                   "resolved_debt_entries": len(resolved)}
        report = {"format": "astaps.protocol-compatibility-report.v1", "summary": summary,
                  "scope": args.scope, "client_artifact": reference["artifact"],
                  "reference_sha256": reference["content_sha256"], "server_inputs": provenance,
                  "opcode_source_sha256": digest((repo / "src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java").read_bytes()),
                  "normal_native_paths_only": True, "runtime_acceptance_proved": False,
                  "not_automatically_proved": ["payload builder control flow", "actual packet delivery",
                      "handler registration completeness", "server-side value transformations",
                      "map component and oneof semantics", "loaded client hotfix behavior"],
                  "unresolved_constants": unresolved_constants, "unresolved_usage": unresolved_usage,
                  "resolved_debt_fingerprints": resolved, "findings": findings}
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(summary, sort_keys=True))
        for row in [f for f in findings if f["severity"] == "error"][:30]:
            print(f"{'KNOWN' if row['known_debt'] else 'NEW'} {row['code']}: {row['symbol']} field={row['field']} expected={row['expected']} actual={row['actual']}")
        return 1 if summary["new_errors"] else 0
    except (OSError, ValueError, KeyError, TypeError, AttributeError, DecodeError, re.error) as error:
        parser.exit(2, f"Cannot audit protocol: {error}\n")


if __name__ == "__main__":
    raise SystemExit(main())
