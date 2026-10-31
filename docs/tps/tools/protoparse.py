"""Minimal parser for the flat text proto dumps (messages, enums, nested types, oneofs, maps)."""
import re

SCALARS = {'double','float','int32','int64','uint32','uint64','sint32','sint64','fixed32',
           'fixed64','sfixed32','sfixed64','bool','string','bytes'}

class Field:
    def __init__(s, label, type_, name, num, oneof=None):
        s.label, s.type, s.name, s.num, s.oneof = label, type_, name, num, oneof
    def __repr__(s): return f'{s.label+" " if s.label else ""}{s.type} {s.name} = {s.num}'

class Msg:
    def __init__(s, kind, name, cmd=None, parent=None):
        s.kind, s.name, s.cmd, s.parent = kind, name, cmd, parent
        s.fields, s.values, s.nested = [], [], []
    @property
    def full(s): return f'{s.parent.full}.{s.name}' if s.parent else s.name

tok_re = re.compile(r'//[^\n]*|map\s*<\s*\w+\s*,\s*[\w.]+\s*>|[{};=]|[^\s{};=]+')

def parse(path):
    text = open(path, encoding='utf-8').read()
    out = {}
    stack, cmd, oneof, stmt = [], None, None, []
    for t in tok_re.findall(text):
        if t.startswith('//'):
            m = re.match(r'//\s*CmdId:\s*(\d+)', t)
            if m: cmd = int(m.group(1))
            elif re.match(r'//\s*CmdId', t): cmd = None
            continue
        if t == '{':
            if stmt[:1] in (['message'], ['enum']):
                m = Msg(stmt[0], stmt[1], cmd if not stack else None, stack[-1] if stack else None)
                if stack: stack[-1].nested.append(m)
                out[m.full] = m; stack.append(m); cmd = None
            elif stmt[:1] == ['oneof']:
                oneof = stmt[1]; stack.append('oneof')
            else:
                stack.append(None)
            stmt = []; continue
        if t == '}':
            top = stack.pop()
            if top == 'oneof': oneof = None
            stmt = []; continue
        if t == ';':
            cur = next((x for x in reversed(stack) if isinstance(x, Msg)), None)
            if cur and stmt and stmt[0] not in ('syntax','option','import','package','reserved'):
                if cur.kind == 'enum' and len(stmt) >= 3:
                    cur.values.append((stmt[0], int(stmt[2])))
                elif cur.kind == 'message':
                    label = stmt[0] if stmt[0] in ('repeated','optional') else ''
                    rest = stmt[1:] if label else stmt
                    if len(rest) >= 4 and rest[2] == '=':
                        cur.fields.append(Field(label, re.sub(r'\s+','',rest[0]), rest[1], int(rest[3]), oneof))
            stmt = []; continue
        stmt.append(t)
    return out

def resolve(msgs, m, tname):
    """Resolve a field type name relative to message m."""
    if tname in SCALARS or tname.startswith('map<'): return tname
    scope = m
    while scope is not None:
        cand = f'{scope.full}.{tname}'
        if cand in msgs: return cand
        scope = scope.parent
    return tname
