"""Print messages of an obfuscated dump with the known names from a translation file.

usage: show.py DUMP.proto NAMES.json DEPTH NAME [NAME...]
DEPTH > 0 also prints the types the fields refer to, that many levels down.
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import protoparse as p

def main():
    msgs = p.parse(sys.argv[1])
    nt = json.load(open(sys.argv[2]))
    nm = lambda x: f'{x}[{nt[x]}]' if x in nt else x
    seen = set()

    def show(name, depth):
        if name in seen or name not in msgs: return
        seen.add(name); m = msgs[name]
        print((f'// CmdId: {m.cmd}\n' if m.cmd else '') + f'{m.kind} {nm(m.full)} {{')
        for f in m.fields:
            print(f'  {f.label + " " if f.label else ""}{nm(f.type)} {nm(f.name)} = {f.num};'
                  + (f'  // oneof {f.oneof}' if f.oneof else ''))
        for v in m.values: print(f'  {nm(v[0])} = {v[1]};')
        print('}')
        if depth > 0:
            for f in m.fields: show(p.resolve(msgs, m, f.type), depth - 1)
            for n in m.nested: show(n.full, depth - 1)

    for name in sys.argv[4:]: show(name, int(sys.argv[3]))

if __name__ == '__main__':
    main()
