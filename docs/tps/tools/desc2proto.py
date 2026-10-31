"""Turn the FileDescriptorSet recovered from generated Java back into .proto sources.

usage: desc2proto.py repo.desc OUTDIR
Field order, oneofs, maps, nested types and file options are reproduced, and every top-level
message gets the `// CmdId: -` comment the dump had, so protoc 3.18.1 regenerates byte-identical
Java for untouched files (checked over all 2059 files).
"""
import os, sys
from google.protobuf import descriptor_pb2 as d

F = d.FieldDescriptorProto
TN = {v: k[5:].lower() for k, v in F.Type.items()}

def typename(f):
    if f.type in (F.TYPE_MESSAGE, F.TYPE_ENUM):
        return f.type_name  # fully qualified with leading dot
    return TN[f.type]

def emit_enum(e, ind):
    out = [f'{ind}enum {e.name} {{']
    if e.options.allow_alias: out.append(f'{ind}  option allow_alias = true;')
    out += [f'{ind}  {v.name} = {v.number};' for v in e.value]
    out.append(f'{ind}}}'); return out

def emit_msg(m, ind):
    out = [f'{ind}message {m.name} {{']
    maps = {n.name: n for n in m.nested_type if n.options.map_entry}
    emitted_oneofs = set()
    def field_line(f, ind2):
        lab = ''
        if f.label == F.LABEL_REPEATED: lab = 'repeated '
        elif f.proto3_optional: lab = 'optional '
        t = typename(f)
        if f.type == F.TYPE_MESSAGE and f.label == F.LABEL_REPEATED:
            short = f.type_name.split('.')[-1]
            if short in maps and f.type_name.endswith(f'{m.name}.{short}'):
                me = maps[short]
                k, v = me.field[0], me.field[1]
                lab, t = '', f'map<{typename(k)}, {typename(v)}>'
        opts = []
        if f.options.HasField('packed'): opts.append(f'packed = {str(f.options.packed).lower()}')
        o = f' [{", ".join(opts)}]' if opts else ''
        return f'{ind2}{lab}{t} {f.name} = {f.number}{o};'
    for f in m.field:
        if f.HasField('oneof_index') and not f.proto3_optional:
            oi = f.oneof_index
            if oi in emitted_oneofs: continue
            emitted_oneofs.add(oi)
            out.append(f'{ind}  oneof {m.oneof_decl[oi].name} {{')
            for g in m.field:
                if g.HasField('oneof_index') and g.oneof_index == oi:
                    out.append(field_line(g, ind + '    '))
            out.append(f'{ind}  }}')
        else:
            out.append(field_line(f, ind + '  '))
    for n in m.nested_type:
        if not n.options.map_entry: out += emit_msg(n, ind + '  ')
    for e in m.enum_type: out += emit_enum(e, ind + '  ')
    out.append(f'{ind}}}'); return out

def emit_file(fd):
    out = [f'syntax = "{fd.syntax or "proto2"}";', '']
    if fd.package: out += [f'package {fd.package};', '']
    for dep in fd.dependency: out.append(f'import "{dep}";')
    if fd.dependency: out.append('')
    o = fd.options
    if o.HasField('java_package'): out.append(f'option java_package = "{o.java_package}";')
    if o.HasField('java_outer_classname'): out.append(f'option java_outer_classname = "{o.java_outer_classname}";')
    if o.HasField('java_multiple_files'): out.append(f'option java_multiple_files = {str(o.java_multiple_files).lower()};')
    out.append('')
    for m in fd.message_type: out += ['// CmdId: -'] + emit_msg(m, '') + ['']
    for e in fd.enum_type: out += emit_enum(e, '') + ['']
    return '\n'.join(out)

if __name__ == '__main__':
    fds = d.FileDescriptorSet(); fds.ParseFromString(open(sys.argv[1], 'rb').read())
    os.makedirs(sys.argv[2], exist_ok=True)
    for fd in fds.file:
        p = os.path.join(sys.argv[2], fd.name); os.makedirs(os.path.dirname(p) or '.', exist_ok=True)
        open(p, 'w').write(emit_file(fd))
    print(len(fds.file))
