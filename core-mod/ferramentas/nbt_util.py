"""Escrita mínima de NBT (estruturas do Minecraft)."""
import gzip, struct

def _s(v):
    e = v.encode()
    return struct.pack('>H', len(e)) + e

class N:
    def __init__(self, t, v): self.t, self.v = t, v

def _enc(t, v):
    if t == 1: return struct.pack('>b', v)
    if t == 3: return struct.pack('>i', v)
    if t == 5: return struct.pack('>f', v)
    if t == 6: return struct.pack('>d', v)
    if t == 8: return _s(v)
    if t == 9:
        et, itens = v
        return bytes([et]) + struct.pack('>i', len(itens)) + b''.join(_enc(et, i) for i in itens)
    if t == 10:
        out = b''
        for k, n in v.items():
            out += bytes([n.t]) + _s(k) + _enc(n.t, n.v)
        return out + b'\x00'
    raise ValueError(t)

def s(v): return N(8, v)
def i(v): return N(3, v)
def comp(d): return N(10, d)

def salvar_estrutura(caminho, tamanho, blocos, entidades=(), nbt_blocos=None):
    """blocos: {(x,y,z): (nome, {props})}; nbt_blocos: {(x,y,z): dict de N}"""
    nbt_blocos = nbt_blocos or {}
    paleta, idx = [], {}
    lista = []
    for pos, (nome, props) in sorted(blocos.items()):
        chave = (nome, tuple(sorted(props.items())))
        if chave not in idx:
            idx[chave] = len(paleta)
            e = {'Name': s(nome)}
            if props: e['Properties'] = comp({k: s(str(v)) for k, v in props.items()})
            paleta.append(e)
        e = {'pos': N(9, (3, list(pos))), 'state': i(idx[chave])}
        if pos in nbt_blocos: e['nbt'] = comp(nbt_blocos[pos])
        lista.append(e)
    raiz = {
        'DataVersion': i(3465),
        'size': N(9, (3, list(tamanho))),
        'palette': N(9, (10, paleta)),
        'blocks': N(9, (10, lista)),
        'entities': N(9, (10, [e.v for e in entidades])),
    }
    with gzip.open(caminho, 'wb') as f:
        f.write(bytes([10]) + _s('') + _enc(10, raiz))
    return len(blocos), len(paleta)
