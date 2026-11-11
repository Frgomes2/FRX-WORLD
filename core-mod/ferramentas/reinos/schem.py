"""Leitor de Sponge Schematic v2/v3 → (paleta [(nome, props-tuple)], vol int32 [y,z,x], 0 = ar)."""
import sys
sys.path.insert(0, '/home/claude/reinos/tools')
import numpy as np, nbtlib
from blocos120 import normalizar

AR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air', 'minecraft:structure_void', 'minecraft:barrier', 'minecraft:light'}

def varints(b):
    out = []; v = 0; s = 0
    for x in b:
        x &= 0xFF
        v |= (x & 0x7F) << s
        if x & 0x80: s += 7
        else: out.append(v); v = 0; s = 0
    return np.array(out, dtype=np.int32)

def parse_estado(txt):
    if '[' in txt:
        n, r = txt.split('[', 1)
        props = dict(p.split('=') for p in r.rstrip(']').split(',') if '=' in p)
    else: n, props = txt, {}
    return n, props

def carregar(caminho):
    r = nbtlib.load(caminho); r = r.get('Schematic', r)
    W, H, L = int(r['Width']), int(r['Height']), int(r['Length'])
    b = r['Blocks'] if 'Blocks' in r else r
    pal = b['Palette']; dados = b['Data'] if 'Data' in b else r['BlockData']
    idx = varints(bytes(np.array(dados, dtype=np.int8).view(np.uint8)))
    paleta = [('minecraft:air', ())]; mapa = np.zeros(max(int(v) for v in pal.values()) + 1, dtype=np.int32)
    vistos = {('minecraft:air', ()): 0}
    for txt, i in pal.items():
        n, p = parse_estado(str(txt))
        if n in AR: continue
        n, p = normalizar(n, p)
        if n in AR: continue
        k = (n, tuple(sorted(p.items())))
        if k not in vistos: vistos[k] = len(paleta); paleta.append(k)
        mapa[int(i)] = vistos[k]
    vol = mapa[idx[:W * H * L]].reshape(H, L, W)
    return paleta, vol
