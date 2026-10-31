# Offline protocol checker tooling

This contribution contains checker/producer code, invented tests and merged server-descriptor output support. It does **not** bundle a client reference, client evidence, or a real compatibility-debt baseline. Passing public CI validates the tooling with synthetic inputs; it does **not** establish compatibility of the upstream server with any client.

## Public validation

Use JDK 21 and Python with `tools/protocol/compatibility-requirements.txt` installed:

```sh
python -m pip install -r tools/protocol/compatibility-requirements.txt
python -m unittest discover -s tools/protocol -p 'test_*compatibility*.py' -v
bash gradlew generateProtocolJava --max-workers=2
```

`.github/workflows/protocol-compatibility.yml` runs these checks. The tests construct nonauthoritative `ExampleReq`/`ExampleRsp` contracts, sentinel command IDs, Java source snippets and protobuf descriptors in temporary directories. They exercise the actual command-line checker for success, mismatches, missing inputs, empty audits and exact debt handling. No game files, capture directory, server or database is needed. CI reloads the freshly generated server descriptor through the checker to verify the generator's manifest hashes across Java and Python. It does not load a real client reference, audit actual client compatibility, or upload references/reports.

Descriptor generation uses the existing base descriptor, supplemental sources and current descriptor patches, not the legacy checked-in Java dump. Its sidecar binds the descriptor, base descriptor, both supplemental source directories and the complete Java generator source tree by SHA-256. Gradle explicitly tracks that implementation directory as an input; direct checker runs also reject retained descriptors after generator/helper source changes or when an older sidecar lacks that binding. Changed/stale inputs fail with instructions to regenerate. This validates server build inputs, not client behavior.

## Audit with your own authorized reference

Supply a reference that you are authorized to use. Keep sensitive inputs and reports outside public repositories; do not assume that projection or factual-looking data grants redistribution rights. This document makes no determination about ownership, legality, licences or the publication history of other material.

```sh
python tools/protocol/check_compatibility.py \
  --reference /path/to/authorized-reference.json \
  --scope 'YourPacketNamePattern' \
  --output /private/path/report.json
```

`--reference` is mandatory. There is no bundled/default reference and no implicit debt file. Omit `--baseline` to treat every hard error as new; explicitly supplying a missing baseline fails. Use `--repo` or `--descriptor` for nondefault server inputs. The default scope selects all source names, but a reference may cover only a subset; broader scopes yield unknown schema cases. Empty packet or descriptor-comparison runs fail rather than pass.

Exit codes: 0 means no new hard errors in the selected comparisons; 1 means new hard errors; 2 means missing/stale/inconsistent inputs or an empty audit. Exit 0 is not proof of client acceptance, runtime delivery or gameplay correctness.

### Verdicts and limits

- **Error:** a declared positive command ID is absent from a supplied complete inventory, an independently identified command has a discrepant ID/direction, or a direction-proved field contract disagrees with the server descriptor. Evidence truth and completeness are user-supplied assertions, not established by this tool.
- **Review:** differences joined only by server opcode/name candidates; wire-compatible type/cardinality differences; extra server fields; enum-domain differences; unavailable opcodes; nontrivial transformations. Matching IDs do not verify semantic names. Descriptors cannot establish whether handwritten server code implements a transformation.
- **Unknown:** incomplete schemas, unsupported source expressions, missing descriptor bindings or directional wire proof, and unsupported map-component contracts.

Provided directional wire facts must be lists of genuine integers, not booleans or strings. Reader wire types must be in 0–5; writer tags must be positive, use a wire type in that range, and identify their own field. Invalid facts are input errors (exit 2), not compatibility mismatches, even for externally prepared references. The producer and loader share these syntax checks.

The checker uses supplied reader acceptance for server-to-client packets and writer tags for client-to-server packets, distinguishing packed emission from accepted packed/unpacked scalar forms. Synthetic field names are not compared with server field names. Nested comparisons inherit parent confidence; enum differences do not prove rejection of unknown proto3 values. Reports do not prove oneof semantics, loaded hotfix behavior or runtime message semantics.

Java scanning handles simple literal/alias opcode constants and direct packet constructor/handler declarations, ignoring comments and literals. Receive annotations support named opcode constants, decimal/hex integer literals and the optional `value =` spelling. Unsupported bindings remain explicit unknown findings, retain source context in the report, and count in the printed unknown summary; unknowns alone do not make the exit status fail. The scanner does not execute Java, analyze general control flow, establish actual delivery or prove handler-registration completeness. These caveats apply even when a reference labels its inputs verified.

### Optional debt

`--baseline /private/path/debt.json` accepts exact finding fingerprints with specific human tracking reasons, bound to the supplied reference/artifact hashes. It never whitelists message names or error categories. Lost previously monitored source bindings also fail when the source still exists. Resolved debt is reported.

`--write-baseline /private/path/proposed-debt.json` writes a proposal and does not suppress errors in that run. Review every finding before adopting it; do not rebaseline to hide regressions. No real debt baseline is included in this contribution.

## Optional reference producer

The producer consumes your own authorized registry, canonical schema, semantic evidence and opcode-source files. All input/output paths are explicit:

```sh
python tools/protocol/build_compatibility_reference.py \
  --registry /private/path/registry.json \
  --schema /private/path/schema.json \
  --evidence /private/path/evidence.json \
  --opcode-source src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java \
  --output /private/path/reference.json
```

The source/API and synthetic tests specify the input structure. Required sections include registry artifact identity, complete inventory/coverage assertions and type identities; schema input hashes, message fields/dependencies/completeness and enum domains; and independently justified semantic aliases. `--expected-count N` optionally checks an independently established inventory count. Otherwise coverage counts must agree with the supplied inventory length; this does not independently prove inventory completeness. Names derived only from server constants remain candidates, never verified identities.

Output is an explicit allowlist projection: artifact/input hashes, command inventory, remapped type identities, selected messages and dependent enum domains, directional wire facts, scalar kinds/cardinality, completeness/status flags, candidate/semantic names and a flag for nontrivial transformations. Unknown properties are discarded. Raw evidence, bytes, native addresses, metadata indices, source paths, wire examples, oneof implementation details and transformation constants are not copied. Alias evidence is replaced with a fixed user-supplied-assertion marker. Public synthetic sentinel tests guard against accidental recursive copying, not all possible sensitive content or encoding. Necessary retained facts (including names, IDs, enum values and hashes) can still carry restrictions; projection is **not** a legal/privacy clearance or an assurance that output may be published.

The checker may also accept externally prepared references carrying additional evidence. Their reports may retain that evidence. Review inputs and outputs yourself; do not upload them automatically.
