"""Gera as fatias (um StructureTemplate .nbt por chunk) de uma capital para a FeatureReinos do core mod.
Uso:
  mundo:      python3 gerar_fatias.py mundo <zip_ou_pasta_mundo> <saida> cx0 cz0 cx1 cz1 yBase yTopo chao
  schematic:  python3 gerar_fatias.py schematic <arquivo.schematic> <saida> yDesloc chao
Em cada coluna entra do primeiro bloco da construção até o mais alto (o ar no meio também, para limpar o terreno)."""
import sys, os, io, re, gzip, json, struct, zlib, zipfile, time
import numpy as np
import nbtlib
from nbtlib import Compound, List, Int, String
from anvil import desempacotar
from blocos120 import normalizar, legacy, CONTADOR
from multiprocessing import Pool

DV = 3465
AR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air', 'minecraft:structure_void', 'minecraft:barrier', 'minecraft:light'}

def regioes(fonte):
    """yield (rx, rz, bytes) de uma pasta de mundo ou .zip"""
    if fonte.endswith('.zip'):
        z = zipfile.ZipFile(fonte)
        for n in z.namelist():
            m = re.search(r'(?:^|/)region/r\.(-?\d+)\.(-?\d+)\.mca$', n)
            if m and not n.startswith('__MACOSX'):
                yield int(m.group(1)), int(m.group(2)), n
    else:
        d = os.path.join(fonte, 'region')
        for n in os.listdir(d):
            m = re.match(r'r\.(-?\d+)\.(-?\d+)\.mca$', n)
            if m: yield int(m.group(1)), int(m.group(2)), os.path.join(d, n)

def ler_bytes(fonte, nome):
    if fonte.endswith('.zip'):
        with zipfile.ZipFile(fonte) as z: return z.read(nome)
    return open(nome, 'rb').read()

def chunks_da_regiao(dados, rx, rz):
    if len(dados) < 8192: return
    for i in range(1024):
        off = struct.unpack('>I', b'\x00' + dados[i*4:i*4+3])[0]
        if off == 0: continue
        p = off * 4096
        tam = struct.unpack('>I', dados[p:p+4])[0]
        comp = dados[p+4]; bruto = dados[p+5:p+4+tam]
        if comp == 2: bruto = zlib.decompress(bruto)
        elif comp == 1: bruto = gzip.decompress(bruto)
        elif comp != 3: continue
        yield rx*32 + (i % 32), rz*32 + (i // 32), bruto

def estado_chave(nome, props):
    n2, p2 = normalizar(nome, props)
    return n2, tuple(sorted(p2.items()))

def volume_chunk(raiz, yBase, altura):
    """retorna (paleta[list of (nome, props-tuple)], vol int32 [y,z,x] relativo a yBase; 0 = ar)"""
    vol = np.zeros((altura, 16, 16), dtype=np.int32)
    paleta = [('minecraft:air', ())]; idx_de = {('minecraft:air', ()): 0}
    for s in raiz.get('sections', []):
        sy = int(s['Y']) * 16
        if sy + 15 < yBase or sy >= yBase + altura: continue
        bs = s.get('block_states')
        if bs is None: continue
        pal = bs['palette']
        mapa = []
        for e in pal:
            nome = str(e['Name'])
            if nome in AR: mapa.append(0); continue
            k = estado_chave(nome, {str(a): str(b) for a, b in e.get('Properties', {}).items()})
            if k[0] in AR: mapa.append(0); continue
            if k not in idx_de: idx_de[k] = len(paleta); paleta.append(k)
            mapa.append(idx_de[k])
        mapa = np.array(mapa, dtype=np.int32)
        if len(pal) == 1 or 'data' not in bs:
            bloco = np.full(4096, mapa[0], dtype=np.int32)
        else:
            bits = max(4, (len(pal) - 1).bit_length())
            bloco = mapa[desempacotar(bs['data'], bits)]
        bloco = bloco.reshape(16, 16, 16)
        a, b = max(yBase, sy), min(yBase + altura - 1, sy + 15)
        vol[a - yBase:b - yBase + 1] = bloco[a - sy:b - sy + 1]
    return paleta, vol

def gravar_fatia(caminho, paleta, vol):
    """Ar entra só entre o primeiro e o último bloco de cada coluna."""
    altura = vol.shape[0]
    cheio = vol != 0
    if not cheio.any(): return False
    tem = cheio.any(axis=0)
    baixo = np.argmax(cheio, axis=0)
    alto = altura - 1 - np.argmax(cheio[::-1], axis=0)
    ys = np.arange(altura)[:, None, None]
    incluir = tem[None] & (ys >= baixo[None]) & (ys <= alto[None])
    yy, zz, xx = np.nonzero(incluir)
    estados = vol[yy, zz, xx]
    usados = np.unique(estados)
    novo = {int(u): i for i, u in enumerate(usados)}
    pal_nbt = []
    for u in usados:
        nome, props = paleta[int(u)]
        c = Compound({'Name': String(nome)})
        if props: c['Properties'] = Compound({k: String(v) for k, v in props})
        pal_nbt.append(c)
    remap = np.zeros(int(usados.max()) + 1, dtype=np.int32)
    for u, i in novo.items(): remap[u] = i
    st = remap[estados]
    blocos = List[Compound]([Compound({'pos': List[Int]([Int(int(x)), Int(int(y)), Int(int(z))]), 'state': Int(int(s))})
                             for x, y, z, s in zip(xx, yy, zz, st)])
    raiz = Compound({'DataVersion': Int(DV), 'size': List[Int]([Int(16), Int(altura), Int(16)]),
                     'entities': List[Compound]([]), 'palette': List[Compound](pal_nbt), 'blocks': blocos})
    nbtlib.File(raiz, gzipped=True).save(caminho)
    return True

def tarefa_regiao(args):
    fonte, nome, rx, rz, cx0, cz0, cx1, cz1, yBase, altura, saida = args
    if rx*32 > cx1 or rx*32 + 31 < cx0 or rz*32 > cz1 or rz*32 + 31 < cz0: return [], {}
    dados = ler_bytes(fonte, nome)
    feitos = []
    for cx, cz, bruto in chunks_da_regiao(dados, rx, rz):
        if not (cx0 <= cx <= cx1 and cz0 <= cz <= cz1): continue
        try: raiz = nbtlib.File.parse(io.BytesIO(bruto))
        except Exception: continue
        paleta, vol = volume_chunk(raiz, yBase, altura)
        ox, oz = cx - cx0, cz - cz0
        if gravar_fatia(os.path.join(saida, f'{ox}_{oz}.nbt'), paleta, vol):
            feitos.append([ox, oz])
    return feitos, dict(CONTADOR)

def manifesto(saida, larg, prof, altura, yBase, chao, chunks, extra=None):
    m = {'nome': os.path.basename(saida.rstrip('/')), 'modo': 'superficie', 'larguraChunks': larg, 'profundidadeChunks': prof,
         'altura': altura, 'yBase': yBase, 'chao': chao, 'chunks': sorted(chunks, key=lambda c: (c[1], c[0]))}
    if extra: m.update(extra)
    json.dump(m, open(os.path.join(saida, 'reino.json'), 'w'))

if __name__ == '__main__':
    modo = sys.argv[1]
    t = time.time()
    if modo == 'mundo':
        fonte, saida = sys.argv[2], sys.argv[3]
        cx0, cz0, cx1, cz1, yBase, yTopo, chao = map(int, sys.argv[4:11])
        altura = yTopo - yBase + 1
        os.makedirs(saida, exist_ok=True)
        tarefas = [(fonte, n, rx, rz, cx0, cz0, cx1, cz1, yBase, altura, saida) for rx, rz, n in regioes(fonte)]
        chunks = []; cont = {}
        with Pool(2) as p:
            for i, (f, c) in enumerate(p.imap_unordered(tarefa_regiao, tarefas)):
                chunks += f; cont.update(c)
                print(f'região {i + 1}/{len(tarefas)} — {len(chunks)} fatias', flush=True)
        manifesto(saida, cx1 - cx0 + 1, cz1 - cz0 + 1, altura, yBase, chao, chunks)
        print('substituições:', sorted(cont.items(), key=lambda kv: -kv[1])[:10])
    else:
        arq, saida = sys.argv[2], sys.argv[3]
        ydesl, chao = int(sys.argv[4]), int(sys.argv[5])
        r = nbtlib.load(arq)
        W, H, L = int(r['Width']), int(r['Height']), int(r['Length'])
        blocos = np.array(r['Blocks'], dtype=np.uint8).astype(np.int32)
        meta = np.array(r['Data'], dtype=np.uint8).astype(np.int32)
        chaves = (blocos * 16 + meta).reshape(H, L, W)
        uniq, inv = np.unique(chaves, return_inverse=True)
        paleta = [('minecraft:air', ())]; mapa = []
        for k in uniq:
            n, p = legacy(int(k) // 16, int(k) % 16)
            if n in AR: mapa.append(0); continue
            mapa.append(len(paleta)); paleta.append((n, tuple(sorted(p.items()))))
        vol_total = np.array(mapa, dtype=np.int32)[inv.reshape(H, L, W)]
        ys = np.nonzero((vol_total != 0).any(axis=(1, 2)))[0]
        vol_total = vol_total[ys.min():ys.max() + 1]
        yBase = int(ys.min()) + ydesl
        altura = vol_total.shape[0]
        os.makedirs(saida, exist_ok=True)
        larg, prof = (W + 15) // 16, (L + 15) // 16
        chunks = []
        for oz in range(prof):
            for ox in range(larg):
                sub = np.zeros((altura, 16, 16), dtype=np.int32)
                pedaco = vol_total[:, oz*16:oz*16 + 16, ox*16:ox*16 + 16]
                sub[:, :pedaco.shape[1], :pedaco.shape[2]] = pedaco
                if gravar_fatia(os.path.join(saida, f'{ox}_{oz}.nbt'), paleta, sub): chunks.append([ox, oz])
        manifesto(saida, larg, prof, altura, yBase, chao, chunks)
        print('substituições:', CONTADOR.most_common(10))
    print(len(chunks), 'fatias em', int(time.time() - t), 's')
