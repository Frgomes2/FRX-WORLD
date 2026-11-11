"""Expande Dwargon: grande caverna anã (avenidas, pilares, casas, forjas, palácio, mercado) ao redor da cidade original."""
import sys, os, glob, json, random
import numpy as np
import nbtlib
from scipy.ndimage import zoom
sys.path.insert(0, '/home/claude/reinos/ferr')
from exportar import salvar

ORIG, SAIDA = sys.argv[1], sys.argv[2]
os.makedirs(SAIDA, exist_ok=True)
random.seed(7); rng = np.random.default_rng(7)

W, D = 56, 60             # chunks (896 x 960)
OX, OZ = 29, 22           # offset da cidade original (chunks): mesma posição absoluta da versão 40x40 (x 576, z 2160)
CENTRO_X, CENTRO_Z = 560, 2300   # posicoes.json (borda leste em x 1008, longe da masmorra em 1120)
OW, OD = 13, 16
H = 71; YB = -40
FL = 28                   # índice Y do piso (y = -12); anda em FL+1
SX, SZ = W * 16, D * 16

# ---------------------------------------------------------------- paleta
PAL, IDX = [], {}
def B(nome, **p):
    k = (nome, tuple(sorted(p.items())))
    if k not in IDX:
        IDX[k] = len(PAL); PAL.append(('minecraft:' + nome if ':' not in nome else nome, dict(p)))
    return IDX[k]

AR = B('air')
mundo = np.full((SX, H, SZ), B('deepslate'), dtype=np.uint16)
mundo[:, 50:, :] = B('stone')
eh_orig = np.zeros((SX, SZ), bool)
x0o, z0o = OX * 16, OZ * 16
eh_orig[x0o:x0o + OW * 16, z0o:z0o + OD * 16] = True
CX, CZ = x0o + OW * 8, z0o + OD * 8       # centro da cidade original

# ---------------------------------------------------------------- caverna
def ruido(res, amp):
    g = rng.random((SX // res + 2, SZ // res + 2))
    r = zoom(g, res, order=3)[:SX, :SZ]
    return (r - r.mean()) / (r.std() + 1e-9) * amp

teto = (FL + 26 + ruido(48, 4)).clip(FL + 18, 64).astype(int)
borda = 12 + ruido(24, 4)
ii, kk = np.meshgrid(np.arange(SX), np.arange(SZ), indexing='ij')
dist_borda = np.minimum.reduce([ii, kk, SX - 1 - ii, SZ - 1 - kk])
caverna = (dist_borda > borda) & ~eh_orig

ys = np.arange(H)[None, :, None]
oco = caverna[:, None, :] & (ys > FL) & (ys < teto[:, None, :])
mundo[oco] = AR
# piso padrão: mistura de pedra
piso = rng.choice([B('cobbled_deepslate'), B('deepslate_tiles'), B('polished_deepslate'), B('deepslate_bricks')],
                  size=(SX, SZ), p=[.35, .25, .25, .15]).astype(np.uint16)
mundo[:, FL, :][caverna] = piso[caverna]
# estalactites no teto
for _ in range(2500):
    x, z = random.randrange(SX), random.randrange(SZ)
    if not caverna[x, z]: continue
    t = teto[x, z]
    for i in range(random.randint(1, 4)):
        if t - 1 - i > FL + 8: mundo[x, t - 1 - i, z] = B('pointed_dripstone', vertical_direction='down', thickness='tip' if i == 0 else 'frustum')
# luz no teto (cristais)
for _ in range(1400):
    x, z = random.randrange(SX), random.randrange(SZ)
    if caverna[x, z]: mundo[x, teto[x, z], z] = random.choice([B('shroomlight'), B('glowstone'), B('amethyst_block')])

livre = caverna.copy()
reservado = np.zeros((SX, SZ), bool)          # colunas ainda sem construção
def ocupar(x0, z0, x1, z1):
    livre[max(x0, 0):x1, max(z0, 0):z1] = False

def caixa(x0, y0, z0, x1, y1, z1, b):   # fim exclusivo, y em índice
    mundo[max(x0, 0):x1, y0:y1, max(z0, 0):z1] = b

def area_ok(x0, z0, x1, z1):
    if x0 < 0 or z0 < 0 or x1 > SX or z1 > SZ: return False
    return livre[x0:x1, z0:z1].all() and (teto[x0:x1, z0:z1].min() > FL + 14)

# ---------------------------------------------------------------- palácio do Rei Gazel
PX0, PX1 = CX - 40, CX + 40
PZ0 = z0o - 122
PZ1 = PZ0 + 80
def palacio():
    alt = 20
    caixa(PX0 - 4, FL + 1, PZ0 - 4, PX1 + 4, FL + alt + 6, PZ1 + 42, AR)   # abre espaço
    teto[PX0 - 4:PX1 + 4, PZ0 - 4:PZ1 + 42] = np.maximum(teto[PX0 - 4:PX1 + 4, PZ0 - 4:PZ1 + 42], FL + alt + 6)
    caixa(PX0 - 4, FL, PZ0 - 4, PX1 + 4, FL + 1, PZ1 + 4, B('polished_blackstone_bricks'))
    # paredes
    caixa(PX0, FL + 1, PZ0, PX1, FL + alt, PZ1, B('deepslate_bricks'))
    caixa(PX0 + 1, FL + 1, PZ0 + 1, PX1 - 1, FL + alt - 1, PZ1 - 1, AR)
    caixa(PX0, FL + alt, PZ0, PX1, FL + alt + 1, PZ1, B('deepslate_tiles'))
    for y in (FL + 6, FL + 13):
        caixa(PX0, y, PZ0, PX1, y + 1, PZ1, B('gold_block'))
        caixa(PX0 + 1, y, PZ0 + 1, PX1 - 1, y + 1, PZ1 - 1, AR)
    # ameias
    for x in range(PX0, PX1, 2):
        mundo[x, FL + alt + 1, PZ0] = mundo[x, FL + alt + 1, PZ1 - 1] = B('deepslate_brick_wall')
    for z in range(PZ0, PZ1, 2):
        mundo[PX0, FL + alt + 1, z] = mundo[PX1 - 1, FL + alt + 1, z] = B('deepslate_brick_wall')
    # torres nos cantos
    for tx, tz in ((PX0 - 3, PZ0 - 3), (PX1 - 4, PZ0 - 3), (PX0 - 3, PZ1 - 4), (PX1 - 4, PZ1 - 4)):
        caixa(tx, FL + 1, tz, tx + 7, FL + alt + 8, tz + 7, B('polished_blackstone_bricks'))
        caixa(tx + 1, FL + 1, tz + 1, tx + 6, FL + alt + 7, tz + 6, AR)
        caixa(tx, FL + alt + 8, tz, tx + 7, FL + alt + 9, tz + 7, B('gold_block'))
        mundo[tx + 3, FL + alt + 9, tz + 3] = B('lantern', hanging='false')
    # portão sul
    caixa(CX - 3, FL + 1, PZ1 - 1, CX + 3, FL + 9, PZ1, AR)
    caixa(CX - 4, FL + 9, PZ1 - 1, CX + 4, FL + 10, PZ1, B('gold_block'))
    # piso interno, tapete, colunas
    caixa(PX0 + 1, FL, PZ0 + 1, PX1 - 1, FL + 1, PZ1 - 1, B('polished_deepslate'))
    caixa(CX - 2, FL + 1, PZ0 + 12, CX + 2, FL + 2, PZ1 - 1, B('red_carpet'))
    for z in range(PZ0 + 10, PZ1 - 4, 10):
        for x in (CX - 14, CX + 13, CX - 28, CX + 27):
            caixa(x, FL + 1, z, x + 2, FL + alt, z + 2, B('polished_blackstone_bricks'))
            mundo[x, FL + 8, z - 1] = B('lantern', hanging='false') if False else mundo[x, FL + 8, z - 1]
        for x in range(CX - 20, CX + 21, 20):
            mundo[x, FL + alt - 1, z] = B('chain', axis='y'); mundo[x, FL + alt - 2, z] = B('chain', axis='y')
            mundo[x, FL + alt - 3, z] = B('lantern', hanging='true')
    # trono
    caixa(CX - 6, FL + 1, PZ0 + 3, CX + 7, FL + 2, PZ0 + 11, B('polished_blackstone_bricks'))
    caixa(CX - 4, FL + 2, PZ0 + 3, CX + 5, FL + 3, PZ0 + 9, B('polished_blackstone_bricks'))
    caixa(CX - 2, FL + 3, PZ0 + 3, CX + 3, FL + 4, PZ0 + 7, B('gold_block'))
    for x in range(CX - 6, CX + 7):
        mundo[x, FL + 1, PZ0 + 11] = B('polished_blackstone_brick_stairs', facing='north', half='bottom', shape='straight', waterlogged='false')
    mundo[CX, FL + 4, PZ0 + 4] = B('polished_blackstone_stairs', facing='north', half='bottom', shape='straight', waterlogged='false')
    for dx in (-1, 1):
        mundo[CX + dx, FL + 4, PZ0 + 4] = B('gold_block'); mundo[CX + dx, FL + 5, PZ0 + 4] = B('gold_block')
    caixa(CX - 1, FL + 4, PZ0 + 3, CX + 2, FL + 8, PZ0 + 4, B('gold_block'))
    for x in (CX - 5, CX + 5):
        mundo[x, FL + 3, PZ0 + 5] = B('deepslate_brick_wall'); mundo[x, FL + 4, PZ0 + 5] = B('lantern', hanging='false')
    # estandartes dourados (lava em vitrine)
    for x in (CX - 10, CX + 10):
        caixa(x - 1, FL + 1, PZ0 + 1, x + 2, FL + 12, PZ0 + 2, B('magma_block'))
    ocupar(PX0 - 6, PZ0 - 6, PX1 + 6, PZ1 + 42)
    reservado[PX0 - 8:PX1 + 8, PZ0 - 8:z0o] = True
    return (CX, FL + 4, PZ0 + 4)

# ---------------------------------------------------------------- mercado (praça entre palácio e cidade)
def mercado():
    z0, z1 = PZ1 + 2, z0o - 1
    caixa(PX0 - 4, FL, z0, PX1 + 4, FL + 1, z1, B('deepslate_tiles'))
    caixa(CX - 3, FL, z0, CX + 4, FL + 1, z1, B('polished_blackstone_bricks'))
    # fonte de lava protegida
    fx, fz = CX, (z0 + z1) // 2
    caixa(fx - 4, FL + 1, fz - 4, fx + 5, FL + 2, fz + 5, B('polished_blackstone_bricks'))
    caixa(fx - 3, FL + 1, fz - 3, fx + 4, FL + 2, fz + 4, B('lava'))
    caixa(fx - 4, FL + 2, fz - 4, fx + 5, FL + 3, fz + 5, B('iron_bars'))
    caixa(fx - 3, FL + 2, fz - 3, fx + 4, FL + 3, fz + 4, AR)
    caixa(fx, FL + 1, fz, fx + 1, FL + 6, fz + 1, B('gold_block'))
    mundo[fx, FL + 6, fz] = B('lantern', hanging='false')
    barracas = {}
    lados = [('ferreiro', -22, -8), ('armeiro', -22, 8), ('mercador', 22, -8), ('cartografo', 22, 8),
             ('sacerdote', -12, 14), ('sabio', 12, 14), ('taverneiro', 0, -14)]
    cores = ['red', 'orange', 'yellow', 'blue', 'purple', 'cyan', 'brown']
    for (papel, dx, dz), cor in zip(lados, cores):
        bx, bz = fx + dx, fz + dz
        caixa(bx - 2, FL + 1, bz - 2, bx + 3, FL + 2, bz - 1, B('spruce_planks'))     # balcão
        for cx_, cz_ in ((bx - 2, bz - 2), (bx + 2, bz - 2), (bx - 2, bz + 2), (bx + 2, bz + 2)):
            caixa(cx_, FL + 1, cz_, cx_ + 1, FL + 4, cz_ + 1, B('spruce_fence', north='false', south='false', east='false', west='false', waterlogged='false'))
        caixa(bx - 3, FL + 4, bz - 3, bx + 4, FL + 5, bz + 4, B(cor + '_wool'))
        mundo[bx, FL + 3, bz] = B('lantern', hanging='true') if False else mundo[bx, FL + 3, bz]
        extra = {'ferreiro': B('grindstone', face='floor', facing='north'), 'armeiro': B('blast_furnace', facing='north', lit='false'),
                 'mercador': B('barrel', facing='up', open='false'), 'cartografo': B('cartography_table'),
                 'sacerdote': B('brewing_stand', has_bottle_0='false', has_bottle_1='false', has_bottle_2='false'),
                 'sabio': B('lectern', facing='north', has_book='false', powered='false'), 'taverneiro': B('barrel', facing='up', open='false')}[papel]
        mundo[bx + 1, FL + 1, bz + 1] = extra
        barracas[papel] = (bx, FL + 1, bz)
    # postes de luz no mercado
    for x in range(PX0 - 2, PX1 + 3, 10):
        for z in (z0 + 1, z1 - 2):
            caixa(x, FL + 1, z, x + 1, FL + 4, z + 1, B('deepslate_brick_wall'))
            mundo[x, FL + 4, z] = B('lantern', hanging='false')
    ocupar(PX0 - 6, z0, PX1 + 6, z1 + 1)
    return barracas

# ---------------------------------------------------------------- avenidas, canais e pilares
LARG = 9
linhas_x = [CX + 64 * k for k in range(-8, 9) if 14 < CX + 64 * k < SX - 14]
linhas_z = [CZ + 64 * k for k in range(-8, 9) if 14 < CZ + 64 * k < SZ - 14]
def avenidas():
    for lx in linhas_x:
        m = (caverna & ~reservado)[lx - LARG // 2:lx + LARG // 2 + 1, :]
        reg = mundo[lx - LARG // 2:lx + LARG // 2 + 1, FL, :]
        reg[m] = B('polished_deepslate')
        mundo[lx, FL, :][caverna[lx, :] & ~reservado[lx, :]] = B('polished_blackstone_bricks')
        livre[lx - LARG // 2 - 1:lx + LARG // 2 + 2, :] = False
    for lz in linhas_z:
        m = (caverna & ~reservado)[:, lz - LARG // 2:lz + LARG // 2 + 1]
        reg = mundo[:, FL, lz - LARG // 2:lz + LARG // 2 + 1]
        reg[m] = B('polished_deepslate')
        mundo[:, FL, lz][caverna[:, lz] & ~reservado[:, lz]] = B('polished_blackstone_bricks')
        livre[:, lz - LARG // 2 - 1:lz + LARG // 2 + 2] = False
    # canais de lava nas avenidas externas (bordas protegidas)
    for lz in (linhas_z[0], linhas_z[-1]):
        for x in range(SX):
            if not caverna[x, lz] or reservado[x, lz] or not caverna[x, lz - 2] or not caverna[x, lz + 2]: continue
            mundo[x, FL, lz] = B('lava'); mundo[x, FL - 1, lz] = B('deepslate_bricks')
            for dz in (-1, 1):
                mundo[x, FL, lz + dz] = B('polished_blackstone_bricks')
                if x % 3: mundo[x, FL + 1, lz + dz] = B('iron_bars', north='false', south='false', east='true', west='true', waterlogged='false')
    # postes de luz nas calçadas
    for lx in linhas_x:
        for z in range(0, SZ, 10):
            for x in (lx - LARG // 2 - 1, lx + LARG // 2 + 1):
                poste(x, z)
    for lz in linhas_z:
        for x in range(5, SX, 10):
            for z in (lz - LARG // 2 - 1, lz + LARG // 2 + 1):
                poste(x, z)
    # pilares gigantes nos cruzamentos
    for lx in linhas_x:
        for lz in linhas_z:
            if not caverna[lx - 5:lx + 6, lz - 5:lz + 6].all() or reservado[lx - 8:lx + 9, lz - 8:lz + 9].any(): continue
            t = teto[lx - 6:lx + 7, lz - 6:lz + 7].max() + 1
            caixa(lx - 2, FL + 1, lz - 2, lx + 3, t, lz + 3, B('deepslate_bricks'))
            for cx_, cz_ in ((lx - 2, lz - 2), (lx + 2, lz - 2), (lx - 2, lz + 2), (lx + 2, lz + 2)):
                caixa(cx_, FL + 1, cz_, cx_ + 1, t, cz_ + 1, B('polished_blackstone_bricks'))
            caixa(lx - 3, FL + 1, lz - 3, lx + 4, FL + 3, lz + 4, B('polished_blackstone_bricks'))
            caixa(lx - 3, t - 4, lz - 3, lx + 4, t, lz + 4, B('deepslate_bricks'))
            caixa(lx - 4, t - 2, lz - 4, lx + 5, t, lz + 5, B('deepslate_bricks'))
            for y in (FL + 8, FL + 16):
                caixa(lx - 2, y, lz - 2, lx + 3, y + 1, lz + 3, B('gold_block'))
                for dx, dz in ((0, -3), (0, 3), (-3, 0), (3, 0)):
                    mundo[lx + dx, y - 1, lz + dz] = B('lantern', hanging='true')
                    mundo[lx + dx, y, lz + dz] = B('chain', axis='y')

def poste(x, z):
    if 0 <= x < SX and 0 <= z < SZ and caverna[x, z] and not reservado[x, z] and mundo[x, FL + 1, z] == AR and teto[x, z] > FL + 5:
        mundo[x, FL + 1, z] = mundo[x, FL + 2, z] = B('deepslate_brick_wall')
        mundo[x, FL + 3, z] = B('lantern', hanging='false')

# ---------------------------------------------------------------- casas e forjas
PORTA = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
def casa(x0, z0, w, d, forja=False):
    h = random.randint(5, 8)
    parede = random.choice([B('deepslate_bricks'), B('stone_bricks'), B('polished_deepslate'), B('cobbled_deepslate')])
    canto = random.choice([B('polished_blackstone_bricks'), B('spruce_log', axis='y'), B('dark_oak_log', axis='y')])
    x1, z1 = x0 + w, z0 + d
    caixa(x0, FL + 1, z0, x1, FL + h, z1, parede)
    caixa(x0 + 1, FL + 1, z0 + 1, x1 - 1, FL + h - 1, z1 - 1, AR)
    caixa(x0 + 1, FL, z0 + 1, x1 - 1, FL + 1, z1 - 1, B('spruce_planks'))
    for cx_, cz_ in ((x0, z0), (x1 - 1, z0), (x0, z1 - 1), (x1 - 1, z1 - 1)):
        caixa(cx_, FL + 1, cz_, cx_ + 1, FL + h, cz_ + 1, canto)
    # telhado em degraus
    telha = random.choice(['deepslate_tile', 'spruce', 'dark_oak', 'polished_blackstone_brick'])
    caixa(x0 - 1, FL + h, z0 - 1, x1 + 1, FL + h + 1, z1 + 1, B(telha + '_slab', type='bottom', waterlogged='false'))
    caixa(x0, FL + h, z0, x1, FL + h + 1, z1, B('deepslate_tiles' if telha.startswith('deepslate') else telha + ('_planks' if telha in ('spruce', 'dark_oak') else 's')))
    if w > 6 and d > 6:
        caixa(x0 + 2, FL + h + 1, z0 + 2, x1 - 2, FL + h + 2, z1 - 2, B('deepslate_tiles'))
    # porta voltada para a avenida mais próxima
    cxm, czm = x0 + w // 2, z0 + d // 2
    dxa = min(linhas_x, key=lambda l: abs(l - cxm)) - cxm
    dza = min(linhas_z, key=lambda l: abs(l - czm)) - czm
    lado = ('east' if dxa > 0 else 'west') if abs(dxa) < abs(dza) else ('south' if dza > 0 else 'north')
    if lado in ('north', 'south'):
        pz = z0 if lado == 'north' else z1 - 1; px = cxm
    else:
        px = x0 if lado == 'west' else x1 - 1; pz = czm
    oposto = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}[lado]
    if forja:
        if lado in ('north', 'south'): caixa(x0 + 2, FL + 1, pz, x1 - 2, FL + 4, pz + 1, AR)
        else: caixa(px, FL + 1, z0 + 2, px + 1, FL + 4, z1 - 2, AR)
    else:
        mundo[px, FL + 1, pz] = B('spruce_door', facing=oposto, half='lower', hinge='left', open='false', powered='false')
        mundo[px, FL + 2, pz] = B('spruce_door', facing=oposto, half='upper', hinge='left', open='false', powered='false')
        ddx, ddz = PORTA[lado]
        mundo[px + ddx * 2, FL + 3, pz + ddz * 2] = mundo[px + ddx * 2, FL + 3, pz + ddz * 2]
    # janelas
    for x in range(x0 + 2, x1 - 2, 3):
        for z in (z0, z1 - 1):
            if mundo[x, FL + 2, z] == parede: mundo[x, FL + 3, z] = B('iron_bars', north='false', south='false', east='true', west='true', waterlogged='false')
    for z in range(z0 + 2, z1 - 2, 3):
        for x in (x0, x1 - 1):
            if mundo[x, FL + 2, z] == parede: mundo[x, FL + 3, z] = B('iron_bars', north='true', south='true', east='false', west='false', waterlogged='false')
    # interior
    mundo[cxm, FL + h - 2, czm] = B('lantern', hanging='true')
    moveis = ([B('blast_furnace', facing='south', lit='false'), B('anvil', facing='north'), B('smithing_table'), B('lava_cauldron'),
               B('grindstone', face='floor', facing='north'), B('furnace', facing='south', lit='false')] if forja else
              [B('barrel', facing='up', open='false'), B('crafting_table'), B('furnace', facing='south', lit='false'),
               B('bookshelf'), B('chest', facing='south', type='single', waterlogged='false'), B('red_bed', facing='south', part='foot', occupied='false') if False else B('cartography_table'),
               B('barrel', facing='up', open='false')])
    pontos = [(x, z0 + 1) for x in range(x0 + 1, x1 - 1)] + [(x, z1 - 2) for x in range(x0 + 1, x1 - 1)]
    random.shuffle(pontos)
    for (x, z), mv in zip(pontos, moveis):
        if abs(x - px) + abs(z - pz) > 2: mundo[x, FL + 1, z] = mv
    if forja:   # poço de lava gradeado no meio da forja
        caixa(cxm - 1, FL, czm - 1, cxm + 1, FL + 1, czm + 1, B('lava'))
        caixa(cxm - 2, FL - 1, czm - 2, cxm + 2, FL, czm + 2, B('deepslate_bricks'))
        caixa(cxm - 1, FL + 1, czm - 1, cxm + 1, FL + 2, czm + 1, B('iron_bars', north='false', south='false', east='false', west='false', waterlogged='false'))
        mundo[cxm, FL + h - 1, czm] = AR
        mundo[cxm, FL + h, czm] = B('iron_bars', north='false', south='false', east='false', west='false', waterlogged='false')
    ocupar(x0 - 2, z0 - 2, x1 + 2, z1 + 2)

def bairros():
    n = 0
    for _ in range(24000):
        w, d = random.randint(7, 14), random.randint(7, 14)
        x0, z0 = random.randrange(10, SX - w - 10), random.randrange(10, SZ - d - 10)
        if not area_ok(x0 - 3, z0 - 3, x0 + w + 3, z0 + d + 3): continue
        # só perto de avenida (até 24 blocos)
        if min(abs(l - (x0 + w // 2)) for l in linhas_x) > 26 and min(abs(l - (z0 + d // 2)) for l in linhas_z) > 26: continue
        casa(x0, z0, w, d, forja=random.random() < 0.25)
        n += 1
    return n

# ---------------------------------------------------------------- luz em todo espaço aberto (sem mobs)
def iluminar():
    for x in range(4, SX - 4, 9):
        for z in range(4, SZ - 4, 9):
            if livre[x, z] and caverna[x, z] and mundo[x, FL + 1, z] == AR:
                poste(x, z)

# ---------------------------------------------------------------- cidade original + túneis
NATURAIS = set('deepslate stone clay tuff andesite diorite granite moss_block gravel cave_vines_plant dirt cave_vines grass iron_ore copper_ore moss_carpet deepslate_iron_ore coal_ore smooth_basalt tall_grass calcite deepslate_gold_ore deepslate_redstone_ore amethyst_block cave_air glow_lichen pointed_dripstone dripstone_block budding_amethyst deepslate_copper_ore deepslate_coal_ore gold_ore redstone_ore lapis_ore deepslate_lapis_ore diamond_ore deepslate_diamond_ore small_amethyst_bud medium_amethyst_bud large_amethyst_bud amethyst_cluster sculk sculk_vein rooted_dirt hanging_roots cobblestone mossy_cobblestone'.split())
FLUIDOS = set(['water', 'lava', 'bubble_column', 'kelp', 'kelp_plant', 'seagrass', 'tall_seagrass'])
bes = {}
def colar_original():
    natural = np.zeros((SX, H, SZ), bool)
    fluido = np.zeros((SX, H, SZ), bool)
    for f in glob.glob(os.path.join(ORIG, '*.nbt')):
        ox, oz = map(int, os.path.basename(f)[:-4].split('_'))
        t = nbtlib.load(f)
        mp = []
        for p in t['palette']:
            nome = str(p['Name']); props = {str(k): str(v) for k, v in p.get('Properties', {}).items()}
            mp.append((B(nome, **props), nome[10:]))
        bx, bz = x0o + ox * 16, z0o + oz * 16
        for b in t['blocks']:
            x, y, z = map(int, b['pos']); s, nome = mp[int(b['state'])]
            X, Z = bx + x, bz + z
            mundo[X, y, Z] = s
            if nome in NATURAIS or nome == 'air': natural[X, y, Z] = True
            if nome in FLUIDOS: fluido[X, y, Z] = True
            if 'nbt' in b: bes[(X, y, Z)] = b['nbt']
    return natural, fluido

def tuneis(natural, fluido):
    """Corredores de 5x5 na altura do piso cortando a rocha natural da cidade original, nas 4 direções."""
    cam = []
    zs = range(z0o - 2, z0o + OD * 16 + 2)
    xs = range(x0o - 2, x0o + OW * 16 + 2)
    for z in zs: cam.append((CX, z, 'z'))
    for x in xs: cam.append((x, CZ, 'x'))
    tunel = np.zeros((SX, SZ), bool)
    for x, z, eixo in cam:
        if eixo == 'z': tunel[x - 2:x + 3, z] = True
        else: tunel[x, z - 2:z + 3] = True
    tunel &= eh_orig
    # casca: fluidos vizinhos viram tijolo
    from scipy.ndimage import binary_dilation
    casca2d = binary_dilation(tunel, iterations=1) & eh_orig & ~tunel
    for y in range(FL, FL + 7):
        f = fluido[:, y, :] & (casca2d | tunel)
        mundo[:, y, :][f] = B('deepslate_bricks')
    for y in range(FL + 1, FL + 6):
        m = tunel & natural[:, y, :]
        mundo[:, y, :][m] = AR
    m = tunel & (natural[:, FL, :] | fluido[:, FL, :])
    mundo[:, FL, :][m] = B('polished_deepslate')
    m = tunel & (natural[:, FL + 6, :] | fluido[:, FL + 6, :])
    mundo[:, FL + 6, :][m] = B('deepslate_bricks')
    # luz no túnel
    for x, z, eixo in cam[::8]:
        if eh_orig[x, z] and mundo[x, FL + 5, z] == AR: mundo[x, FL + 5, z] = B('lantern', hanging='true') if mundo[x, FL + 6, z] != AR else AR

# ---------------------------------------------------------------- execução
natural, fluido = colar_original()
tuneis(natural, fluido)
trono = palacio()
barracas = mercado()
avenidas()
ncasas = bairros()
iluminar()
print('casas', ncasas, 'paleta', len(PAL))
from PIL import Image
cores = {}
img = np.zeros((SZ, SX, 3), np.uint8)
for y in range(FL, FL + 12):
    camada = mundo[:, y, :]
    for i, (nome, props) in enumerate(PAL):
        n = nome.split(':')[-1]
        c = (60,60,70) if 'deepslate' in n else (200,170,40) if 'gold' in n else (255,120,0) if 'lava' in n or 'magma' in n else (120,90,50) if 'spruce' in n or 'oak' in n else (250,240,150) if 'lantern' in n or 'glow' in n or 'shroom' in n else (30,30,35) if 'blackstone' in n else (150,150,150) if 'stone' in n else None
        if n == 'air' or c is None: continue
        img[(camada == i).T] = c
Image.fromarray(img).save('/home/claude/dwx/mapa.png')

# ---------------------------------------------------------------- exportar fatias
chunks = []
for cx in range(W):
    for cz in range(D):
        sub = mundo[cx * 16:cx * 16 + 16, :, cz * 16:cz * 16 + 16]
        xs_, ys_, zs_ = np.indices(sub.shape)
        blocos = np.stack([xs_.ravel(), ys_.ravel(), zs_.ravel(), sub.ravel().astype(np.int64)], axis=1)
        be_lista = []
        chaves = set()
        for (X, Y, Z), comp in bes.items():
            if X >> 4 == cx and Z >> 4 == cz:
                be_lista.append((X - cx * 16, Y, Z - cz * 16, int(mundo[X, Y, Z]), comp)); chaves.add((X - cx * 16, Y, Z - cz * 16))
        if chaves:
            msk = np.array([(a, b, c) not in chaves for a, b, c in blocos[:, :3].tolist()])
            blocos = blocos[msk]
        salvar(os.path.join(SAIDA, f'{cx}_{cz}.nbt'), (16, H, 16), PAL, blocos, be_lista)
        chunks.append([cx, cz])

ent = [CX - 32, FL + 1 + YB, (PZ1 + 2 + z0o - 1) // 2]
json.dump({'nome': 'dwargon', 'modo': 'subterraneo', 'larguraChunks': W, 'profundidadeChunks': D, 'altura': H, 'yBase': YB,
           'chunks': chunks, 'chao': 63, 'entrada': ent}, open(os.path.join(SAIDA, 'reino.json'), 'w'))

# posições dos NPCs em coordenadas absolutas (centro 700,2300)
cx0 = ((CENTRO_X >> 4) - W // 2) * 16; cz0 = ((CENTRO_Z >> 4) - D // 2) * 16
def absol(p): return [p[0] + cx0, p[1] + YB, p[2] + cz0]
npcs = {'rei': absol(trono)}
for papel, p in barracas.items(): npcs[papel] = absol(p)
gx = [(CX - 5, PZ1 + 3), (CX + 5, PZ1 + 3), (CX - 12, PZ1 + 20), (CX + 12, PZ1 + 20), (CX - 5, z0o - 3), (CX + 5, z0o - 3)]
for i, (x, z) in enumerate(gx): npcs[f'guarda{i + 1}'] = absol((x, FL + 1, z))
json.dump({'npcs': npcs}, open(os.path.join(SAIDA, '..', 'dwargon_npcs.json'), 'w'), indent=1)
print('ok', len(chunks), 'trono', absol(trono))
