"""Vista de cima (com sombreamento por altura) e isométrica simples de um volume [y,z,x]."""
import sys
sys.path.insert(0, '/home/claude/reinos/tools')
import numpy as np
from PIL import Image
from render import cor

def topo(paleta, vol, arq, escala=2, ymin=0):
    H, L, W = vol.shape
    v = vol[ymin:]
    cheio = v != 0
    tem = cheio.any(axis=0)
    alt = (v.shape[0] - 1 - np.argmax(cheio[::-1], axis=0))
    ids = np.take_along_axis(v, alt[None], axis=0)[0]
    cores = np.array([cor(p[0]) for p in paleta], dtype=np.float32)
    img = cores[ids]
    luz = 0.55 + 0.45 * (alt / max(1, v.shape[0] - 1))
    img = img * luz[..., None]
    img[~tem] = (20, 20, 25)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    im = im.resize((W * escala, L * escala), Image.NEAREST)
    im.save(arq)

def obliquo(paleta, vol, arq, x0=0, z0=0, x1=None, z1=None, escala=3, ymin=0):
    """projeção oblíqua (vista de sul, de cima): pixel (x, z - y)."""
    v = vol[ymin:, z0:z1, x0:x1]
    Hh, L, W = v.shape
    cores = np.array([cor(p[0]) for p in paleta], dtype=np.float32)
    img = np.full((L + Hh, W, 3), (25, 25, 30), dtype=np.float32)
    for y in range(Hh):
        camada = v[y]
        cheio = camada != 0
        if not cheio.any(): continue
        c = cores[camada] * (0.6 + 0.4 * y / Hh)
        # face superior mais clara
        cima = np.ones_like(cheio)
        if y + 1 < Hh: cima = v[y + 1] == 0
        c = np.where(cima[..., None], c * 1.1, c * 0.8)
        for z in range(L):
            linha = cheio[z]
            if linha.any():
                img[z + Hh - y, linha] = c[z, linha]
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    im = im.resize((W * escala, (L + Hh) * escala), Image.NEAREST)
    im.save(arq)
