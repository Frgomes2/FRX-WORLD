"""Leitor mínimo de regiões Anvil (formato 1.18+) com numpy."""
import io, os, re, struct, zlib, gzip
import numpy as np
import nbtlib

def ler_regiao(caminho):
    with open(caminho, 'rb') as f:
        dados = f.read()
    if len(dados) < 8192:
        return
    rx, rz = map(int, re.search(r'r\.(-?\d+)\.(-?\d+)\.mca', caminho).groups())
    for i in range(1024):
        off = struct.unpack('>I', b'\x00' + dados[i*4:i*4+3])[0]
        if off == 0:
            continue
        p = off * 4096
        tam = struct.unpack('>I', dados[p:p+4])[0]
        comp = dados[p+4]
        bruto = dados[p+5:p+4+tam]
        if comp == 2:
            bruto = zlib.decompress(bruto)
        elif comp == 1:
            bruto = gzip.decompress(bruto)
        elif comp == 3:
            pass
        else:
            continue
        cx, cz = rx*32 + (i % 32), rz*32 + (i // 32)
        yield cx, cz, bruto

def parse(bruto):
    return nbtlib.File.parse(io.BytesIO(bruto))

def desempacotar(longs, bits, n=4096):
    arr = np.array(longs, dtype=np.int64).view(np.uint64)
    por = 64 // bits
    mask = np.uint64((1 << bits) - 1)
    shifts = (np.arange(por, dtype=np.uint64) * np.uint64(bits))
    vals = (arr[:, None] >> shifts[None, :]) & mask
    return vals.reshape(-1)[:n].astype(np.int32)

def nome_estado(entrada):
    nome = str(entrada['Name'])
    props = entrada.get('Properties')
    if props:
        return nome, {str(k): str(v) for k, v in props.items()}
    return nome, {}

def secoes(chunk):
    """yield (secY, paleta[list of (nome, props)], indices[4096] em ordem y,z,x)"""
    raiz = chunk
    for s in raiz.get('sections', []):
        bs = s.get('block_states')
        if bs is None:
            continue
        pal = [nome_estado(e) for e in bs['palette']]
        if len(pal) == 1:
            if pal[0][0] in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'):
                continue
            idx = np.zeros(4096, dtype=np.int32)
        else:
            bits = max(4, (len(pal) - 1).bit_length())
            idx = desempacotar(bs['data'], bits)
        yield int(s['Y']), pal, idx
