"""Recover the FileDescriptorSet embedded in protoc-generated Java sources.

usage: extract.py src/generated/main/java/emu/grasscutter/net/proto OUT.desc
"""
import os, re, sys
from google.protobuf import descriptor_pb2

SRC = sys.argv[1]
OUT = sys.argv[2]

lit_re = re.compile(r'"((?:[^"\\]|\\.)*)"')

def unescape(s):
    out = bytearray(); i = 0
    while i < len(s):
        c = s[i]
        if c != '\\':
            out += c.encode('latin-1'); i += 1; continue
        n = s[i+1]
        if n in '01234567':
            j = i+1; v = ''
            while j < len(s) and len(v) < 3 and s[j] in '01234567': v += s[j]; j += 1
            out.append(int(v, 8)); i = j; continue
        if n == 'u':
            out.append(int(s[i+2:i+6], 16)); i += 6; continue
        out += {'n': b'\n', 't': b'\t', 'r': b'\r', 'b': b'\b', 'f': b'\f', '"': b'"', "'": b"'", '\\': b'\\'}[n]
        i += 2
    return bytes(out)

fds = descriptor_pb2.FileDescriptorSet()
for fn in sorted(os.listdir(SRC)):
    if not fn.endswith('.java'): continue
    txt = open(os.path.join(SRC, fn), encoding='utf-8').read()
    m = re.search(r'java\.lang\.String\[\] descriptorData = \{(.*?)\};', txt, re.S)
    if not m: continue
    data = b''.join(unescape(x) for x in lit_re.findall(m.group(1)))
    fd = descriptor_pb2.FileDescriptorProto(); fd.ParseFromString(data)
    fds.file.append(fd)
open(OUT, 'wb').write(fds.SerializeToString())
print(len(fds.file), 'files')
