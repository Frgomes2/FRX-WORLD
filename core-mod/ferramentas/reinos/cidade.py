"""Gerador de capitais procedurais para o FRX-WORLD (Oeste, Lubelius, Lordes Demônios).

Monta a cidade num volume numpy [y, z, x] e grava as fatias por chunk no formato do FeatureReinos
(mesmo de gerar_fatias.py). Usa as casas dos asset-packs (MAPA HOUSE PACK e Medieval House Pack)
com materiais trocados por tema, mais construções feitas aqui (muralha, torres, portões,
castelo/catedral/cidadela, praça, ruas, postes, árvores, plantações, moinhos).

Uso: python3 cidade.py <tema> <saida> [semente]
"""
import sys, os, json, random, pickle
sys.path.insert(0, '/home/claude/reinos/tools')
sys.path.insert(0, '/home/claude/cidades')
import numpy as np
from scipy import ndimage
from gerar_fatias import gravar_fatia, manifesto
from schem import carregar

VALIDOS = json.load(open('/home/claude/reinos/tools/dados_blocos.json'))['validos']

N = 20            # chunks por lado
S = N * 16        # blocos por lado
FUND = 10         # camadas de alicerce abaixo do chão
H = 104           # altura total do volume
G = FUND          # índice y do chão (superfície)
C = S // 2        # centro


def valido(nome):
    return ('minecraft:' + nome if ':' not in nome else nome) in VALIDOS


class Mundo:
    def __init__(self):
        self.vol = np.zeros((H, S, S), dtype=np.int32)
        self.paleta = [('minecraft:air', ())]
        self.idx = {('minecraft:air', ()): 0}

    def id(self, nome, **props):
        if ':' not in nome: nome = 'minecraft:' + nome
        if nome not in VALIDOS:
            raise ValueError('bloco inválido ' + nome)
        perm = VALIDOS[nome]
        p = tuple(sorted((k, str(v).lower()) for k, v in props.items() if k in perm))
        k = (nome, p)
        if nome.endswith(':air'): return 0
        if k not in self.idx:
            self.idx[k] = len(self.paleta); self.paleta.append(k)
        return self.idx[k]

    def p(self, x, y, z, b):
        if 0 <= x < S and 0 <= z < S and 0 <= y < H:
            self.vol[y, z, x] = b if isinstance(b, (int, np.integer)) else self.id(*b) if isinstance(b, tuple) else self.id(b)

    def caixa(self, x0, y0, z0, x1, y1, z1, b):
        b = b if isinstance(b, (int, np.integer)) else self.id(b)
        x0, x1 = max(0, min(x0, x1)), min(S - 1, max(x0, x1))
        z0, z1 = max(0, min(z0, z1)), min(S - 1, max(z0, z1))
        y0, y1 = max(0, min(y0, y1)), min(H - 1, max(y0, y1))
        if x0 > x1 or z0 > z1 or y0 > y1: return
        self.vol[y0:y1 + 1, z0:z1 + 1, x0:x1 + 1] = b

    def nome(self, x, y, z):
        if 0 <= x < S and 0 <= z < S and 0 <= y < H:
            return self.paleta[self.vol[y, z, x]][0].split(':')[1]
        return 'air'


# ---------------------------------------------------------------------------------------------- temas

TEMAS = {
    'oeste': dict(
        nome='Nações do Oeste',
        chao='grass_block', rua='dirt_path', rua2='coarse_dirt', borda='cobblestone', praca=['stone_bricks', 'cobblestone', 'andesite', 'stone_bricks'],
        muro='stone_bricks', muro2='mossy_stone_bricks', muro_esc='stone_brick_stairs', muro_laje='stone_brick_slab', muro_parede='stone_brick_wall',
        torre_teto='deepslate_tile_stairs', torre_teto_bloco='deepslate_tiles',
        poste='spruce_fence', luz='lantern', madeira='spruce', arvores=['oak', 'birch', 'spruce'], flores=['poppy', 'dandelion', 'oxeye_daisy', 'cornflower', 'azure_bluet'],
        fosso=None,
        casas=dict(paredes=['white_terracotta', 'birch_planks', 'bricks', 'mud_bricks', 'stripped_oak_wood', 'smooth_sandstone'],
                   telhados=['deepslate_tiles', 'dark_oak_planks', 'spruce_planks', 'hay_block', 'bricks', 'brown_terracotta'],
                   madeiras=['spruce', 'dark_oak', 'oak'], base=[('cobblestone', 'stone_bricks')], vidro='glass_pane', luz='lantern'),
    ),
    'lubelius': dict(
        nome='Império Sagrado de Lubelius',
        chao='grass_block', rua='smooth_stone', rua2='polished_diorite', borda='quartz_bricks', praca=['quartz_bricks', 'smooth_quartz', 'calcite', 'polished_diorite'],
        muro='quartz_bricks', muro2='calcite', muro_esc='smooth_quartz_stairs', muro_laje='smooth_quartz_slab', muro_parede='diorite_wall',
        torre_teto='waxed_oxidized_cut_copper_stairs', torre_teto_bloco='waxed_oxidized_cut_copper',
        poste='birch_fence', luz='lantern', madeira='birch', arvores=['birch', 'cherry', 'oak'], flores=['white_tulip', 'lily_of_the_valley', 'azure_bluet', 'oxeye_daisy', 'allium'],
        fosso='water',
        casas=dict(paredes=['calcite', 'white_concrete', 'smooth_quartz', 'quartz_bricks', 'white_terracotta'],
                   telhados=['waxed_oxidized_cut_copper', 'dark_prismarine', 'light_blue_terracotta', 'cyan_terracotta', 'blue_terracotta'],
                   madeiras=['birch', 'birch', 'oak'], base=[('polished_diorite', 'polished_diorite')], vidro='white_stained_glass_pane', luz='lantern'),
    ),
    'demonios': dict(
        nome='Territórios dos Lordes Demônios',
        chao='blackstone', rua='polished_blackstone', rua2='basalt', borda='polished_blackstone_bricks', praca=['polished_blackstone_bricks', 'blackstone', 'gilded_blackstone', 'polished_blackstone'],
        muro='polished_blackstone_bricks', muro2='cracked_polished_blackstone_bricks', muro_esc='polished_blackstone_brick_stairs', muro_laje='polished_blackstone_brick_slab',
        muro_parede='polished_blackstone_brick_wall',
        torre_teto='nether_brick_stairs', torre_teto_bloco='red_nether_bricks',
        poste='blackstone_wall', luz='soul_lantern', madeira='crimson', arvores=['crimson', 'warped'], flores=['crimson_roots', 'crimson_fungus', 'warped_roots'],
        fosso='lava',
        casas=dict(paredes=['polished_blackstone_bricks', 'red_nether_bricks', 'nether_bricks', 'polished_deepslate', 'blackstone'],
                   telhados=['nether_wart_block', 'red_nether_bricks', 'polished_blackstone', 'crimson_planks', 'warped_wart_block'],
                   madeiras=['crimson', 'crimson', 'warped'], base=[('blackstone', 'polished_blackstone_bricks')], vidro='red_stained_glass_pane', luz='soul_lantern'),
    ),
}


def familia_madeira(m):
    """nomes da família de madeira (crimson/warped usam stem/hyphae)."""
    fungo = m in ('crimson', 'warped')
    return dict(planks=f'{m}_planks', stairs=f'{m}_stairs', slab=f'{m}_slab', fence=f'{m}_fence', trapdoor=f'{m}_trapdoor', door=f'{m}_door',
                log=f'{m}_stem' if fungo else f'{m}_log', wood=f'{m}_hyphae' if fungo else f'{m}_wood',
                stripped=f'stripped_{m}_hyphae' if fungo else f'stripped_{m}_wood', gate=f'{m}_fence_gate')


def familia_pedra(b):
    esp = {'stone_bricks': ('stone_brick_stairs', 'stone_brick_slab', 'stone_brick_wall'),
           'polished_blackstone_bricks': ('polished_blackstone_brick_stairs', 'polished_blackstone_brick_slab', 'polished_blackstone_brick_wall'),
           'bricks': ('brick_stairs', 'brick_slab', 'brick_wall'), 'quartz_bricks': ('smooth_quartz_stairs', 'smooth_quartz_slab', 'diorite_wall'),
           'nether_bricks': ('nether_brick_stairs', 'nether_brick_slab', 'nether_brick_wall'),
           'red_nether_bricks': ('red_nether_brick_stairs', 'red_nether_brick_slab', 'red_nether_brick_wall'),
           'mud_bricks': ('mud_brick_stairs', 'mud_brick_slab', 'mud_brick_wall')}
    if b in esp: return (b,) + esp[b]
    return (b, f'{b}_stairs', f'{b}_slab', f'{b}_wall')


# ---------------------------------------------------------------------------------------------- casas dos packs

def biblioteca_casas():
    """26 formatos de casa (linhas 18-19 do MAPA HOUSE PACK), cada um com volume [y,z,x] e paleta."""
    cache = '/tmp/casas_lib.pkl'
    if os.path.exists(cache): return pickle.load(open(cache, 'rb'))
    pal, vol = carregar('/home/claude/assets/mapa/MAPA HOUSE PACK/1,16-1,21/HousePackassets.schem')
    v = vol[2:]
    lab, n = ndimage.label((v != 0).any(axis=0), structure=np.ones((3, 3)))
    casas = []
    for i, s in enumerate(ndimage.find_objects(lab)):
        if s[0].start // 26 not in (18, 19): continue
        m = lab[s] == i + 1
        sub = v[:, s[0], s[1]].copy()
        sub[:, ~m] = 0
        ys = np.nonzero((sub != 0).any(axis=(1, 2)))[0]
        sub = sub[:ys.max() + 1]
        casas.append(sub)
    lib = (pal, casas)
    pickle.dump(lib, open(cache, 'wb'))
    return lib


def mansoes():
    """mansões do Medieval House Pack (só as que cabem num quarteirão)."""
    pal, vol = carregar('/home/claude/reinos/src/Medieval_House_pack/house_pack.schem')
    v = vol[1:]
    lab, n = ndimage.label(ndimage.binary_dilation((v != 0).any(axis=0), iterations=1))
    out = []
    for i, s in enumerate(ndimage.find_objects(lab)):
        m = lab[s] == i + 1
        sub = v[:, s[0], s[1]].copy(); sub[:, ~m] = 0
        ys = np.nonzero((sub != 0).any(axis=(1, 2)))[0]
        sub = sub[:ys.max() + 1]
        if sub.shape[1] <= 40 and sub.shape[2] <= 40: out.append(sub)
    return pal, out


def moinho():
    pal, vol = carregar('/home/claude/reinos/src/widndspiremill.schem')
    ys = np.nonzero((vol != 0).any(axis=(1, 2)))[0]
    return pal, vol[ys.min():ys.max() + 1]


def receita(tema, rnd):
    """troca de materiais de uma casa: nome original → nome novo."""
    t = TEMAS[tema]['casas']
    parede, telhado = rnd.choice(t['paredes']), rnd.choice(t['telhados'])
    while telhado == parede: telhado = rnd.choice(t['telhados'])
    mad = familia_madeira(rnd.choice(t['madeiras']))
    b1, b2 = rnd.choice(t['base'])
    f1, f2 = familia_pedra(b1), familia_pedra(b2)
    r = {'brown_mushroom_block': parede, 'cyan_wool': telhado,
         'spruce_stairs': mad['stairs'], 'spruce_slab': mad['slab'], 'spruce_fence': mad['fence'], 'spruce_trapdoor': mad['trapdoor'],
         'spruce_planks': mad['planks'], 'stripped_spruce_wood': mad['stripped'], 'oak_wood': mad['wood'],
         'stone': f1[0], 'stone_stairs': f1[1], 'cobblestone': f1[0], 'cobblestone_stairs': f1[1], 'cobblestone_slab': f1[2],
         'stone_bricks': f2[0], 'stone_brick_stairs': f2[1], 'stone_brick_slab': f2[2],
         'sea_lantern': 'shroomlight' if tema == 'demonios' else ('pearlescent_froglight' if tema == 'lubelius' else 'sea_lantern'),
         'brown_stained_glass_pane': t['vidro']}
    if tema == 'demonios':
        r.update({'grass_block': 'crimson_nylium', 'fern': 'crimson_roots', 'grass': 'air', 'short_grass': 'air', 'tall_grass': 'air',
                  'dandelion': 'crimson_fungus', 'poppy': 'crimson_fungus', 'azure_bluet': 'warped_roots', 'oxeye_daisy': 'crimson_roots',
                  'jungle_leaves': 'nether_wart_block'})
    return r


def colar_volume(m, pal, sub, x0, y0, z0, troca=None, eixo_ar=False):
    """cola sub [y,z,x] com a paleta pal em (x0,y0,z0), trocando nomes. Ar do sub não apaga."""
    mapa = np.zeros(len(pal), dtype=np.int32)
    for i, (nome, props) in enumerate(pal):
        if i == 0: continue
        curto = nome.split(':')[1]
        novo = troca.get(curto, curto) if troca else curto
        if novo == 'air': mapa[i] = 0; continue
        if not valido(novo): novo = curto
        pd = dict(props)
        if novo != curto:  # props de folha/cogumelo não servem para bloco cheio
            perm = VALIDOS['minecraft:' + novo]
            pd = {k: v for k, v in pd.items() if k in perm}
            if 'persistent' in perm: pd['persistent'] = 'true'
        if novo.endswith('_leaves'): pd['persistent'] = 'true'
        mapa[i] = m.id(novo, **pd)
    h, d, w = sub.shape
    y1, z1, x1 = min(H, y0 + h), min(S, z0 + d), min(S, x0 + w)
    if y1 <= y0 or z1 <= z0 or x1 <= x0: return
    alvo = m.vol[y0:y1, z0:z1, x0:x1]
    fonte = sub[:y1 - y0, :z1 - z0, :x1 - x0]
    novo = mapa[fonte]
    cheio = fonte != 0
    alvo[cheio] = novo[cheio]
    if eixo_ar:  # limpa o espaço da construção acima do chão
        pass


# ---------------------------------------------------------------------------------------------- peças

def escada(m, nome, x, y, z, facing, half='bottom'):
    m.p(x, y, z, m.id(nome, facing=facing, half=half, shape='straight', waterlogged='false'))


def porta(m, mad, x, y, z, facing):
    m.p(x, y, z, m.id(mad + '_door', facing=facing, half='lower', hinge='left', open='false', powered='false'))
    m.p(x, y + 1, z, m.id(mad + '_door', facing=facing, half='upper', hinge='left', open='false', powered='false'))


def arvore(m, rnd, x, z, tipo):
    y = G + 1
    if tipo in ('crimson', 'warped'):
        alt = rnd.randint(5, 9)
        m.caixa(x, y, z, x, y + alt, z, m.id(f'{tipo}_stem', axis='y'))
        copa = 'nether_wart_block' if tipo == 'crimson' else 'warped_wart_block'
        for dy in range(-2, 2):
            r = 3 - max(0, dy + 1)
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if abs(dx) + abs(dz) <= r + 1 and rnd.random() < 0.9 and m.nome(x + dx, y + alt + dy, z + dz) == 'air':
                        m.p(x + dx, y + alt + dy, z + dz, m.id(copa))
        if rnd.random() < 0.5: m.p(x + 1, y + alt - 3, z, m.id('shroomlight'))
        return
    alt = rnd.randint(4, 6) + (2 if tipo == 'spruce' else 0)
    log = m.id(f'{tipo}_log', axis='y')
    folha = m.id(f'{tipo}_leaves', persistent='true', distance='1')
    m.caixa(x, y, z, x, y + alt - 1, z, log)
    if tipo == 'spruce':
        for dy in range(2, alt + 2):
            r = max(0, (alt + 2 - dy) // 2)
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if abs(dx) + abs(dz) <= r + (dy % 2) and m.nome(x + dx, y + dy, z + dz) == 'air':
                        m.p(x + dx, y + dy, z + dz, folha)
        m.p(x, y + alt + 1, z, folha)
        return
    raio = 3 if tipo == 'cherry' else 2
    for dy in range(alt - 3, alt + 2):
        r = raio if dy < alt else raio - 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if dx * dx + dz * dz <= r * r + 1 and m.nome(x + dx, y + dy, z + dz) == 'air' and rnd.random() < 0.93:
                    m.p(x + dx, y + dy, z + dz, folha)


def poste(m, t, x, z, alt=3):
    if t['poste'].endswith('_wall'):
        m.caixa(x, G + 1, z, x, G + alt, z, m.id(t['poste'], up='true'))
    else:
        m.caixa(x, G + 1, z, x, G + alt, z, m.id(t['poste']))
    m.p(x, G + alt + 1, z, m.id(t['luz'], hanging='false'))


# ---------------------------------------------------------------------------------------------- cidade

class Cidade:
    def __init__(self, tema, semente):
        self.t = TEMAS[tema]; self.tema = tema
        self.rnd = random.Random(semente)
        self.m = Mundo()
        self.ocupado = np.zeros((S, S), dtype=bool)   # [z, x] já tem algo (rua, casa, etc.)
        self.rua = np.zeros((S, S), dtype=bool)
        self.muro_i = 10        # borda interna do muro
        self.ruas = [C + 46 * j for j in range(-3, 4)]  # 22 … 298

    # ---------- terreno
    def terreno(self):
        m, t = self.m, self.t
        pedra = 'blackstone' if self.tema == 'demonios' else 'stone'
        terra = 'blackstone' if self.tema == 'demonios' else 'dirt'
        m.caixa(0, 0, 0, S - 1, G - 4, S - 1, pedra)
        m.caixa(0, G - 3, 0, S - 1, G - 1, S - 1, terra)
        m.caixa(0, G, 0, S - 1, G, S - 1, t['chao'])
        if self.tema == 'demonios':  # manchas de nylium, basalto e magma
            for _ in range(900):
                x, z = self.rnd.randrange(S), self.rnd.randrange(S)
                b = self.rnd.choice(['crimson_nylium', 'crimson_nylium', 'polished_basalt', 'magma_block', 'netherrack', 'soul_soil'])
                r = self.rnd.randint(1, 3)
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        if dx * dx + dz * dz <= r * r: m.p(x + dx, G, z + dz, m.id(b, axis='y'))

    # ---------- ruas
    def pavimentar(self, x0, z0, x1, z1, principal=False):
        m, t = self.m, self.t
        for z in range(max(0, z0), min(S, z1 + 1)):
            for x in range(max(0, x0), min(S, x1 + 1)):
                if self.ocupado[z, x] and not self.rua[z, x]: continue
                borda = x in (x0, x1) or z in (z0, z1)
                if borda and not principal:
                    b = t['borda']
                else:
                    b = t['rua'] if self.rnd.random() < 0.78 else t['rua2']
                m.vol[G, z, x] = m.id(b, axis='y')
                m.vol[G + 1:G + 4, z, x] = 0
                self.rua[z, x] = True; self.ocupado[z, x] = True

    def ruas_grade(self):
        a, b = self.muro_i + 2, S - 1 - self.muro_i - 2
        for i, r in enumerate(self.ruas):
            w = 4 if r == C else 2
            self.pavimentar(r - w, a, r + w, b, r == C)
            self.pavimentar(a, r - w, b, r + w, r == C)

    # ---------- muralha
    def muralha(self):
        m, t, rnd = self.m, self.t, self.rnd
        i0, i1 = self.muro_i - 4, S - 1 - (self.muro_i - 4)   # borda externa
        alt = 12
        muro, muro2 = m.id(t['muro']), m.id(t['muro2'])
        # fosso
        if t['fosso']:
            f = m.id(t['fosso'], level=0)
            orla = m.id(t['borda'])
            for z in range(S):
                for x in range(S):
                    d = min(x, z, S - 1 - x, S - 1 - z)
                    if 1 <= d <= 4:
                        m.caixa(x, G - 2, z, x, G - 1, z, f); m.vol[G, z, x] = 0
                        m.vol[G - 3, z, x] = orla
                    elif d == 0 or d == 5:
                        m.caixa(x, G - 2, z, x, G, z, orla)
            self.ocupado[:5, :] = self.ocupado[-5:, :] = self.ocupado[:, :5] = self.ocupado[:, -5:] = True
        for z in range(i0, i1 + 1):
            for x in range(i0, i1 + 1):
                d = min(x - i0, z - i0, i1 - x, i1 - z)
                if d > 3: continue
                b = muro if rnd.random() < 0.8 else muro2
                m.caixa(x, G, z, x, G + alt, z, b)
                self.ocupado[z, x] = True
                if d == 0:  # ameias por fora
                    if (x + z) % 2 == 0: m.p(x, G + alt + 1, z, b)
                elif d == 3:
                    m.p(x, G + alt + 1, z, m.id(t['muro_parede'], up='true') if (x + z) % 6 else m.id(t['luz'], hanging='false'))
        # torres
        pos = list(range(i0, i1 + 1, 50)) + [i1 - 9]
        for tz in (i0, i1 - 9):
            for tx in sorted(set(pos)):
                tx = min(tx, i1 - 9); self.torre(tx, tz, 10, alt + 10)
        for tx in (i0, i1 - 9):
            for tz in sorted(set(pos)):
                tz = min(tz, i1 - 9); self.torre(tx, tz, 10, alt + 10)
        # portões (4 lados, no centro)
        for lado in range(4):
            self.portao(lado, alt)

    def torre(self, x0, z0, lado, alt):
        m, t = self.m, self.t
        muro = m.id(t['muro'])
        m.caixa(x0, G, z0, x0 + lado - 1, G + alt, z0 + lado - 1, muro)
        m.caixa(x0 + 1, G + 1, z0 + 1, x0 + lado - 2, G + alt - 1, z0 + lado - 2, 0)
        for y in range(G + 1, G + alt, 6):  # pisos e janelas
            m.caixa(x0 + 1, y - 1, z0 + 1, x0 + lado - 2, y - 1, z0 + lado - 2, m.id(t['madeira'] + '_planks'))
            m.p(x0 + lado // 2, y + 3, z0 + lado // 2, m.id(t['luz'], hanging='true'))
            for (wx, wz) in ((x0, z0 + lado // 2), (x0 + lado - 1, z0 + lado // 2), (x0 + lado // 2, z0), (x0 + lado // 2, z0 + lado - 1)):
                m.p(wx, y + 1, wz, m.id('iron_bars')); m.p(wx, y + 2, wz, m.id('iron_bars'))
        # telhado piramidal
        topo = G + alt + 1
        for k in range(lado // 2 + 1):
            a, b = x0 - 1 + k, x0 + lado - k
            c, d = z0 - 1 + k, z0 + lado - k
            if a > b or c > d: break
            for x in range(a, b + 1):
                escada(m, t['torre_teto'], x, topo + k, c, 'south'); escada(m, t['torre_teto'], x, topo + k, d, 'north')
            for z in range(c + 1, d):
                escada(m, t['torre_teto'], a, topo + k, z, 'east'); escada(m, t['torre_teto'], b, topo + k, z, 'west')
            if a + 1 <= b - 1 and c + 1 <= d - 1:
                m.caixa(a + 1, topo + k, c + 1, b - 1, topo + k, d - 1, m.id(t['torre_teto_bloco']))
        m.p(x0 + lado // 2, topo + lado // 2 + 1, z0 + lado // 2, m.id(t['muro_parede'], up='true'))
        self.ocupado[max(0, z0):z0 + lado, max(0, x0):x0 + lado] = True

    def portao(self, lado, alt):
        m, t = self.m, self.t
        i0, i1 = self.muro_i - 4, S - 1 - (self.muro_i - 4)
        larg = 4
        if lado in (0, 1):  # norte / sul (abertura em x)
            z0 = i0 if lado == 0 else i1 - 3
            m.caixa(C - larg, G + 1, z0, C + larg, G + 7, z0 + 3, 0)
            for x in range(C - larg, C + larg + 1):
                for z in range(z0, z0 + 4): m.vol[G, z, x] = m.id(t['rua'], axis='y')
            for x in range(C - larg, C + larg + 1):  # arco
                m.p(x, G + 7, z0, m.id(t['muro_esc'], facing='north' if lado else 'south', half='top'))
            self.torre(C - larg - 10, z0 - 3, 9, alt + 8); self.torre(C + larg + 2, z0 - 3, 9, alt + 8)
            # ponte sobre o fosso
            if t['fosso']:
                zz = range(0, 6) if lado == 0 else range(S - 6, S)
                for z in zz: m.caixa(C - larg, G, z, C + larg, G, z, m.id(t['borda']))
        else:
            x0 = i0 if lado == 2 else i1 - 3
            m.caixa(x0, G + 1, C - larg, x0 + 3, G + 7, C + larg, 0)
            for z in range(C - larg, C + larg + 1):
                for x in range(x0, x0 + 4): m.vol[G, z, x] = m.id(t['rua'], axis='y')
            for z in range(C - larg, C + larg + 1):
                m.p(x0, G + 7, z, m.id(t['muro_esc'], facing='west' if lado == 3 else 'east', half='top'))
            self.torre(x0 - 3, C - larg - 10, 9, alt + 8); self.torre(x0 - 3, C + larg + 2, 9, alt + 8)
            if t['fosso']:
                xx = range(0, 6) if lado == 2 else range(S - 6, S)
                for x in xx: m.caixa(x, G, C - larg, x, G, C + larg, m.id(t['borda']))
        # estrada de saída até a borda (pisável) — reabre o vão nas torres do portão
        if lado in (0, 1):
            zr = range(0, i0 + 4) if lado == 0 else range(i1 - 3, S)
            for z in zr:
                m.caixa(C - larg, G + 1, z, C + larg, G + 8, z, 0)
                if not t['fosso'] or not (1 <= min(z, S - 1 - z) <= 4) or True:
                    for x in range(C - larg, C + larg + 1): m.vol[G, z, x] = m.id(t['rua'], axis='y')
        else:
            xr = range(0, i0 + 4) if lado == 2 else range(i1 - 3, S)
            for x in xr:
                m.caixa(x, G + 1, C - larg, x, G + 8, C + larg, 0)
                for z in range(C - larg, C + larg + 1): m.vol[G, z, x] = m.id(t['rua'], axis='y')

    # ---------- praça
    def praca(self):
        m, t, rnd = self.m, self.t, self.rnd
        R = 26
        for z in range(C - R, C + R + 1):
            for x in range(C - R, C + R + 1):
                d = ((x - C) ** 2 + (z - C) ** 2) ** 0.5
                if d > R: continue
                anel = int(d) // 4 % len(t['praca'])
                m.vol[G, z, x] = m.id(t['praca'][anel])
                m.vol[G + 1:G + 6, z, x] = 0
                self.ocupado[z, x] = True; self.rua[z, x] = True
        # fonte / monumento
        if self.tema == 'oeste':
            agua = m.id('water', level=0)
            for z in range(C - 6, C + 7):
                for x in range(C - 6, C + 7):
                    d = ((x - C) ** 2 + (z - C) ** 2) ** 0.5
                    if d <= 5: m.vol[G, z, x] = agua; m.vol[G - 1, z, x] = m.id('stone_bricks')
                    elif d <= 6.2: m.p(x, G + 1, z, m.id('stone_brick_slab', type='bottom'))
            m.caixa(C, G, C, C, G + 4, C, m.id('stone_bricks'))
            m.caixa(C - 1, G + 5, C - 1, C + 1, G + 5, C + 1, m.id('stone_brick_slab', type='bottom'))
            m.p(C, G + 5, C, m.id('lantern', hanging='false'))
        elif self.tema == 'lubelius':
            agua = m.id('water', level=0)
            for z in range(C - 8, C + 9):
                for x in range(C - 8, C + 9):
                    d = ((x - C) ** 2 + (z - C) ** 2) ** 0.5
                    if d <= 7: m.vol[G, z, x] = agua; m.vol[G - 1, z, x] = m.id('quartz_bricks')
                    elif d <= 8.2: m.p(x, G + 1, z, m.id('smooth_quartz_slab', type='bottom'))
            m.caixa(C - 1, G, C - 1, C + 1, G + 2, C + 1, m.id('quartz_bricks'))
            m.caixa(C, G + 3, C, C, G + 13, C, m.id('quartz_pillar', axis='y'))
            m.caixa(C - 1, G + 14, C - 1, C + 1, G + 14, C + 1, m.id('gold_block'))
            m.p(C, G + 15, C, m.id('end_rod', facing='up'))
            for dx, dz in ((-2, 0), (2, 0), (0, -2), (0, 2)):
                m.p(C + dx, G + 14, C + dz, m.id('smooth_quartz_stairs', facing={(-2, 0): 'east', (2, 0): 'west', (0, -2): 'south', (0, 2): 'north'}[(dx, dz)], half='top'))
        else:
            lava, obs = m.id('lava', level=0), m.id('polished_blackstone_bricks')
            for z in range(C - 8, C + 9):
                for x in range(C - 8, C + 9):
                    d = ((x - C) ** 2 + (z - C) ** 2) ** 0.5
                    if d <= 6.5: m.vol[G, z, x] = lava; m.vol[G - 1, z, x] = obs
                    elif d <= 8.2: m.caixa(x, G, z, x, G + 1, z, obs)
            m.caixa(C - 1, G - 1, C - 1, C + 1, G + 6, C + 1, m.id('crying_obsidian'))
            m.caixa(C, G + 7, C, C, G + 9, C, m.id('obsidian'))
            for dx, dz in ((-2, 0), (2, 0), (0, -2), (0, 2)):
                m.caixa(C + dx, G + 6, C + dz, C + dx, G + 6, C + dz, m.id('polished_blackstone_wall', up='true'))
                m.p(C + dx, G + 7, C + dz, m.id('soul_lantern', hanging='false'))
        # bancas de mercado em anel
        cores = ['red', 'blue', 'yellow', 'green', 'white', 'orange', 'purple', 'cyan'] if self.tema != 'demonios' else ['red', 'black', 'purple', 'gray']
        for k in range(8):
            ang = k / 8 * 2 * np.pi + 0.39
            bx, bz = int(C + 17 * np.cos(ang)), int(C + 17 * np.sin(ang))
            cor = rnd.choice(cores)
            for dx in (-1, 1):
                for dz in (-1, 1): m.caixa(bx + dx, G + 1, bz + dz, bx + dx, G + 2, bz + dz, m.id(t['madeira'] + '_fence'))
            m.caixa(bx - 2, G + 3, bz - 2, bx + 2, G + 3, bz + 2, m.id(cor + '_wool'))
            m.p(bx, G + 1, bz, m.id('barrel', facing='up')); m.p(bx + 1, G + 1, bz, m.id('composter') if self.tema == 'oeste' else m.id('barrel', facing='up'))
        for k in range(16):
            ang = k / 16 * 2 * np.pi
            poste(m, t, int(C + 24 * np.cos(ang)), int(C + 24 * np.sin(ang)), 4)

    # ---------- construção principal (ao norte da praça)
    def principal(self):
        x0, z0, x1, z1 = C - 44, self.ruas[1] + 3, C + 44, C - 28   # ~ 89 × 73
        m, t = self.m, self.t
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                m.vol[G, z, x] = m.id(t['praca'][0]); m.vol[G + 1:, z, x] = 0
        self.ocupado[z0:z1 + 1, x0:x1 + 1] = True
        self.rua[z0:z1 + 1, x0:x1 + 1] = False
        getattr(self, 'p_' + self.tema)(x0, z0, x1, z1)
        # ligação com a praça
        self.pavimentar(C - 4, z1, C + 4, C - 24, True)

    def p_oeste(self, x0, z0, x1, z1):
        """castelo: muralha interna, 4 torres, torre de menagem com sala do trono."""
        m, t = self.m, self.t
        pedra, musgo = m.id('stone_bricks'), m.id('mossy_stone_bricks')
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                if x in (x0, x0 + 1, x1 - 1, x1) or z in (z0, z0 + 1, z1 - 1, z1):
                    m.caixa(x, G, z, x, G + 9, z, pedra if self.rnd.random() < .85 else musgo)
                    if (x + z) % 2 == 0 and (x in (x0, x1) or z in (z0, z1)): m.p(x, G + 10, z, pedra)
        m.caixa(C - 3, G + 1, z1 - 1, C + 3, G + 6, z1, 0)       # portão do castelo
        for x in range(C - 3, C + 4): escada(m, 'stone_brick_stairs', x, G + 6, z1, 'north', 'top')
        for tx, tz in ((x0 - 2, z0 - 2), (x1 - 8, z0 - 2), (x0 - 2, z1 - 8), (x1 - 8, z1 - 8)):
            self.torre(tx, tz, 11, 24)
        # menagem
        kx0, kz0, kx1, kz1 = C - 20, z0 + 10, C + 20, z0 + 44
        m.caixa(kx0, G, kz0, kx1, G + 30, kz1, pedra)
        m.caixa(kx0 + 1, G + 1, kz0 + 1, kx1 - 1, G + 29, kz1 - 1, 0)
        for y in (G + 11, G + 20):
            m.caixa(kx0 + 1, y, kz0 + 1, kx1 - 1, y, kz1 - 1, m.id('spruce_planks'))
            m.caixa(C - 1, y, kz0 + 2, C + 1, y, kz0 + 4, 0)  # vão de escada
        for y in range(G + 1, G + 31):  # escada em caracol simples junto à parede norte
            k = (y - G - 1) % 10
            m.p(C - 1 + (k % 3), y, kz0 + 2 + (k // 3) % 3, m.id('spruce_planks'))
        # janelas
        for y in (G + 5, G + 15, G + 24):
            for x in range(kx0 + 4, kx1 - 3, 6):
                m.caixa(x, y, kz0, x, y + 2, kz0, m.id('glass_pane')); m.caixa(x, y, kz1, x, y + 2, kz1, m.id('glass_pane'))
            for z in range(kz0 + 4, kz1 - 3, 6):
                m.caixa(kx0, y, z, kx0, y + 2, z, m.id('glass_pane')); m.caixa(kx1, y, z, kx1, y + 2, z, m.id('glass_pane'))
        # ameias no topo e torrinhas nos cantos
        for x in range(kx0, kx1 + 1):
            for z in (kz0, kz1):
                if x % 2 == 0: m.p(x, G + 31, z, pedra)
        for z in range(kz0, kz1 + 1):
            for x in (kx0, kx1):
                if z % 2 == 0: m.p(x, G + 31, z, pedra)
        for cx, cz in ((kx0 - 2, kz0 - 2), (kx1 - 4, kz0 - 2), (kx0 - 2, kz1 - 4), (kx1 - 4, kz1 - 4)):
            self.torre(cx, cz, 7, 36)
        # sala do trono
        m.caixa(C - 3, G + 1, kz0 + 6, C + 3, G + 1, kz1 - 2, m.id('red_carpet'))
        m.caixa(C - 2, G + 1, kz0 + 3, C + 2, G + 1, kz0 + 5, m.id('gold_block'))
        escada(m, 'dark_oak_stairs', C, G + 2, kz0 + 4, 'north')
        m.caixa(C - 1, G + 2, kz0 + 3, C + 1, G + 4, kz0 + 3, m.id('gold_block'))
        for z in range(kz0 + 8, kz1 - 2, 6):
            for x in (C - 7, C + 7):
                m.caixa(x, G + 1, z, x, G + 10, z, m.id('stripped_spruce_log', axis='y'))
                m.p(x, G + 9, z + 1, m.id('lantern', hanging='false'))
        for x in range(C - 15, C + 16, 5):
            m.p(x, G + 1, kz0 + 1, m.id('red_banner', rotation=0) if valido('red_banner') else m.id('bookshelf'))
        porta_x = C
        m.caixa(porta_x - 2, G + 1, kz1, porta_x + 2, G + 5, kz1, 0)
        # jardim do pátio
        for k in range(14):
            arvore(m, self.rnd, self.rnd.randint(x0 + 5, x1 - 5), self.rnd.choice([self.rnd.randint(z0 + 4, kz0 - 4), self.rnd.randint(kz1 + 4, z1 - 5)]), 'oak')

    def p_lubelius(self, x0, z0, x1, z1):
        """catedral: nave, transepto, duas torres com agulhas, vitrais."""
        m, t = self.m, self.t
        q, qb, pil = m.id('smooth_quartz'), m.id('quartz_bricks'), m.id('quartz_pillar', axis='y')
        ouro = m.id('gold_block')
        nx0, nx1, nz0, nz1 = C - 11, C + 11, z0 + 8, z1 - 6      # nave
        tx0, tx1, tz0, tz1 = C - 30, C + 30, z0 + 16, z0 + 32    # transepto
        alt = 24

        def corpo(a, b, c, d):
            m.caixa(a, G + 1, c, b, G + alt, d, q)
            m.caixa(a + 1, G + 1, c + 1, b - 1, G + alt - 1, d - 1, 0)
            m.caixa(a, G, c, b, G, d, m.id('polished_diorite'))
        corpo(nx0, nx1, nz0, nz1); corpo(tx0, tx1, tz0, tz1)
        m.caixa(nx0 + 1, G + 1, tz0 + 1, nx1 - 1, G + alt - 1, tz1 - 1, 0)
        m.caixa(tx0 + 1, G + 1, tz0 + 1, tx1 - 1, G + alt - 1, tz1 - 1, 0)
        # pilastras e vitrais
        for z in range(nz0, nz1 + 1, 5):
            for x in (nx0, nx1):
                m.caixa(x, G + 1, z, x, G + alt, z, pil)
        for z in range(nz0 + 2, nz1 - 1, 5):
            for x in (nx0, nx1):
                cor = self.rnd.choice(['light_blue', 'yellow', 'white', 'blue'])
                m.caixa(x, G + 5, z, x, G + 17, z + 1, m.id(cor + '_stained_glass'))
        for x in range(tx0 + 3, tx1 - 2, 5):
            if nx0 <= x <= nx1: continue
            for z in (tz0, tz1): m.caixa(x, G + 5, z, x + 1, G + 17, z, m.id('light_blue_stained_glass'))
        # rosácea na fachada sul
        for dz in range(-4, 5):
            for dx in range(-4, 5):
                if dx * dx + dz * dz <= 17: m.p(C + dx, G + 15 + dz, nz1, m.id('yellow_stained_glass' if (dx + dz) % 2 else 'white_stained_glass'))
        m.caixa(C - 3, G + 1, nz1, C + 3, G + 8, nz1, 0)  # portal
        for x in range(C - 3, C + 4): m.p(x, G + 9, nz1, ouro)
        # telhado de cobre em duas águas (nave em z, transepto em x)
        topo = G + alt + 1
        for k in range(13):
            for z in range(nz0 - 1, nz1 + 2):
                if nx0 - 1 + k <= C: escada(m, t['torre_teto'], nx0 - 1 + k, topo + k, z, 'east')
                if nx1 + 1 - k >= C: escada(m, t['torre_teto'], nx1 + 1 - k, topo + k, z, 'west')
            for x in range(tx0 - 1, tx1 + 2):
                if tz0 - 1 + k <= (tz0 + tz1) // 2: escada(m, t['torre_teto'], x, topo + k, tz0 - 1 + k, 'south')
                if tz1 + 1 - k >= (tz0 + tz1) // 2: escada(m, t['torre_teto'], x, topo + k, tz1 + 1 - k, 'north')
        for z in range(nz0 - 1, nz1 + 2): m.p(C, topo + 12, z, m.id(t['torre_teto_bloco']))
        # frontões (preenche o triângulo das pontas)
        for k in range(12):
            m.caixa(nx0 + k, topo + k - 1, nz1, nx1 - k, topo + k - 1, nz1, q)
            m.caixa(nx0 + k, topo + k - 1, nz0, nx1 - k, topo + k - 1, nz0, q)
            m.caixa(tx0, topo + k - 1, tz0 + k, tx0, topo + k - 1, tz1 - k, q)
            m.caixa(tx1, topo + k - 1, tz0 + k, tx1, topo + k - 1, tz1 - k, q)
        # torres da fachada com agulhas
        for bx in (nx0 - 6, nx1 - 2):
            m.caixa(bx, G, nz1 - 8, bx + 8, G + 44, nz1, qb)
            m.caixa(bx + 1, G + 1, nz1 - 7, bx + 7, G + 43, nz1 - 1, 0)
            for y in (G + 20, G + 32):
                m.caixa(bx + 3, y, nz1, bx + 5, y + 6, nz1, m.id('white_stained_glass'))
                m.caixa(bx, y, nz1 - 5, bx, y + 6, nz1 - 3, m.id('white_stained_glass'))
                m.caixa(bx + 8, y, nz1 - 5, bx + 8, y + 6, nz1 - 3, m.id('white_stained_glass'))
            m.p(bx + 4, G + 42, nz1 - 4, m.id('bell', attachment='ceiling', facing='north') if valido('bell') else ouro)
            base = G + 45
            for k in range(5):
                a, b, c, d = bx + k, bx + 8 - k, nz1 - 8 + k, nz1 - k
                for x in range(a, b + 1):
                    for z in range(c, d + 1):
                        if x in (a, b) or z in (c, d): m.caixa(x, base + 3 * k, z, x, base + 3 * k + 2, z, m.id(t['torre_teto_bloco']))
            m.caixa(bx + 4, base + 15, nz1 - 4, bx + 4, base + 19, nz1 - 4, ouro)
            m.p(bx + 4, base + 20, nz1 - 4, m.id('end_rod', facing='up'))
        # interior: bancos, altar, luz
        for z in range(nz0 + 20, nz1 - 3, 3):
            for x in list(range(nx0 + 2, C - 1)) + list(range(C + 2, nx1 - 1)):
                escada(m, 'birch_stairs', x, G + 1, z, 'south')
        m.caixa(C - 5, G + 1, nz0 + 2, C + 5, G + 2, nz0 + 6, m.id('quartz_bricks'))
        m.caixa(C - 2, G + 3, nz0 + 3, C + 2, G + 3, nz0 + 4, ouro)
        m.caixa(C, G + 4, nz0 + 3, C, G + 9, nz0 + 3, m.id('gold_block'))
        m.caixa(C - 2, G + 7, nz0 + 3, C + 2, G + 7, nz0 + 3, m.id('gold_block'))
        m.caixa(C - 1, G + 1, nz0 + 7, C + 1, G + 1, nz1 - 1, m.id('light_blue_carpet'))
        for z in range(nz0 + 3, nz1 - 2, 5):
            for x in (nx0 + 1, nx1 - 1):
                m.p(x, G + alt - 2, z, m.id('pearlescent_froglight', axis='y'))
            m.caixa(C, G + alt - 3, z, C, G + alt - 1, z, m.id('chain', axis='y')); m.p(C, G + alt - 4, z, m.id('lantern', hanging='true'))
        # claustro: canteiros e cerejeiras ao redor
        for k in range(18):
            x = self.rnd.choice([self.rnd.randint(x0 + 3, tx0 - 4), self.rnd.randint(tx1 + 4, x1 - 3)])
            z = self.rnd.randint(z0 + 3, z1 - 3)
            arvore(m, self.rnd, x, z, 'cherry')

    def p_demonios(self, x0, z0, x1, z1):
        """cidadela: torre negra em degraus, chifres, cascatas de lava presas, trono."""
        m, t = self.m, self.t
        bb, bl, rn = m.id('polished_blackstone_bricks'), m.id('blackstone'), m.id('red_nether_bricks')
        lava = m.id('lava', level=0)
        # fosso de lava em volta
        for z in range(z0 + 2, z1 - 1):
            for x in range(x0 + 2, x1 - 1):
                d = min(x - x0 - 2, z - z0 - 2, x1 - 2 - x, z1 - 2 - z)
                if d in (1, 2) and not (C - 3 <= x <= C + 3 and z > C - 40):
                    m.vol[G, z, x] = lava; m.vol[G - 1, z, x] = bb
                elif d in (0, 3): m.caixa(x, G, z, x, G + 1, z, bb)
        m.caixa(C - 3, G, z1 - 6, C + 3, G, z1 - 1, bb)      # ponte
        # corpo em 3 degraus
        niveis = [(34, 28, 0, 18), (26, 20, 18, 16), (16, 12, 34, 18)]
        cz = (z0 + z1) // 2 - 4
        for lx, lz, y0, h in niveis:
            a, b, c, d = C - lx // 2 * 2 // 2 - lx // 2, C + lx // 2, cz - lz // 2 - lz // 2, cz + lz // 2
            a, b, c, d = C - lx, C + lx, cz - lz, cz + lz
            m.caixa(a, G + y0, c, b, G + y0 + h, d, bb)
            m.caixa(a + 1, G + y0 + 1, c + 1, b - 1, G + y0 + h - 1, d - 1, 0)
            for x in range(a, b + 1, 4):  # faixas vermelhas e janelas
                m.caixa(x, G + y0 + h - 2, c, x, G + y0 + h - 2, c, rn); m.caixa(x, G + y0 + h - 2, d, x, G + y0 + h - 2, d, rn)
                if a < x < b and (x - a) % 8 == 4:
                    m.caixa(x, G + y0 + 4, d, x, G + y0 + 9, d, m.id('red_stained_glass'))
                    m.caixa(x, G + y0 + 4, c, x, G + y0 + 9, c, m.id('red_stained_glass'))
            for x in range(a, b + 1):
                for z in (c, d):
                    if x % 3 == 0: m.caixa(x, G + y0 + h + 1, z, x, G + y0 + h + 2, z, bl)
            for z in range(c, d + 1):
                for x in (a, b):
                    if z % 3 == 0: m.caixa(x, G + y0 + h + 1, z, x, G + y0 + h + 2, z, bl)
            # chifres nos cantos
            for hx, hz in ((a, c), (b, c), (a, d), (b, d)):
                for k in range(8):
                    m.p(hx + (k // 3) * (1 if hx > C else -1), G + y0 + h + 1 + k, hz, bl if k < 6 else m.id('polished_blackstone_wall', up='true'))
            if y0 > 0:
                m.caixa(a + 1, G + y0, c + 1, b - 1, G + y0, d - 1, m.id('polished_blackstone'))
        # cascatas de lava em nichos fechados na fachada sul
        for x in (C - 22, C + 22):
            m.caixa(x - 1, G + 1, cz + 29, x + 1, G + 17, cz + 31, bb)
            m.caixa(x, G + 1, cz + 30, x, G + 17, cz + 30, lava)
            m.caixa(x, G + 2, cz + 31, x, G + 15, cz + 31, m.id('tinted_glass') if valido('tinted_glass') else bb)
        # portal e trono
        m.caixa(C - 3, G + 1, cz + 28, C + 3, G + 9, cz + 28, 0)
        for x in range(C - 4, C + 5): m.p(x, G + 10, cz + 28, m.id('gilded_blackstone'))
        m.caixa(C - 2, G + 1, cz - 25, C + 2, G + 2, cz - 22, m.id('gilded_blackstone'))
        escada(m, 'polished_blackstone_stairs', C, G + 3, cz - 24, 'north')
        m.caixa(C - 2, G + 3, cz - 26, C + 2, G + 8, cz - 26, m.id('crying_obsidian'))
        m.caixa(C - 1, G + 1, cz - 20, C + 1, G + 1, cz + 27, m.id('red_carpet'))
        for z in range(cz - 18, cz + 26, 6):
            for x in (C - 8, C + 8):
                m.caixa(x, G + 1, z, x, G + 16, z, m.id('polished_basalt', axis='y'))
                m.p(x, G + 2, z + 1, m.id('soul_campfire', lit='true', facing='north'))
        for z in range(cz - 20, cz + 26, 7):
            m.caixa(C, G + 14, z, C, G + 17, z, m.id('chain', axis='y')); m.p(C, G + 13, z, m.id('soul_lantern', hanging='true'))
        # estátuas/obeliscos no pátio
        for ox in (x0 + 10, x1 - 10):
            for oz in (z0 + 12, z1 - 14):
                m.caixa(ox - 1, G + 1, oz - 1, ox + 1, G + 2, oz + 1, bb)
                m.caixa(ox, G + 3, oz, ox, G + 12, oz, m.id('obsidian'))
                m.p(ox, G + 13, oz, m.id('soul_lantern', hanging='false'))

    # ---------- quarteirões
    def quarteiroes(self):
        """retângulos entre as ruas (só os livres)."""
        m = self.m
        cortes = [self.muro_i + 2] + [r for r in self.ruas] + [S - 1 - self.muro_i - 2]
        bl = []
        for i in range(len(cortes) - 1):
            for j in range(len(cortes) - 1):
                xa, xb = cortes[i] + (5 if cortes[i] == C else 3), cortes[i + 1] - (5 if cortes[i + 1] == C else 3)
                za, zb = cortes[j] + (5 if cortes[j] == C else 3), cortes[j + 1] - (5 if cortes[j + 1] == C else 3)
                if xb - xa < 8 or zb - za < 8: continue
                bl.append((xa, za, xb, zb))
        return bl

    def livre(self, xa, za, xb, zb):
        if xa < 0 or za < 0 or xb >= S or zb >= S: return False
        return not self.ocupado[za:zb + 1, xa:xb + 1].any()

    def casas(self, lib, extras=None):
        pal, formas = lib
        m, rnd = self.m, self.rnd
        n = 0
        especiais = list(extras or [])
        for (xa, za, xb, zb) in self.quarteiroes():
            # quarteirão especial (mansão/moinho/plantação)
            if especiais and self.livre(xa, za, xb, zb) and rnd.random() < 0.5:
                tipo, dado = especiais.pop(0)
                if tipo == 'fazenda':
                    self.fazenda(xa, za, xb, zb, dado)
                    continue
                pal2, sub, troca = dado
                h, d, w = sub.shape
                if w <= xb - xa + 1 and d <= zb - za + 1:
                    ox, oz = xa + (xb - xa + 1 - w) // 2, za + (zb - za + 1 - d) // 2
                    m.vol[G + 1:, oz:oz + d, ox:ox + w] = 0
                    colar_volume(m, pal2, sub, ox, G + 1, oz, troca)
                    self.ocupado[za:zb + 1, xa:xb + 1] = True
                    self.jardim(xa, za, xb, zb)
                    n += 1
                    continue
                especiais.append((tipo, dado))
            # prateleiras de casas
            z = za
            while z < zb - 6:
                x = xa
                prof_linha = 0
                while x < xb - 6:
                    cabe = [f for f in formas if f.shape[2] <= xb - x + 1 and f.shape[1] <= zb - z + 1]
                    if not cabe: break
                    f = rnd.choice(cabe)
                    h, d, w = f.shape
                    if self.livre(x, z, x + w - 1, z + d - 1):
                        m.vol[G + 1:, z:z + d, x:x + w] = 0
                        colar_volume(m, pal, f, x, G, z, receita(self.tema, rnd))
                        self.ocupado[z:z + d, x:x + w] = True
                        n += 1
                    x += w + rnd.randint(1, 3)
                    prof_linha = max(prof_linha, d)
                z += max(prof_linha, 8) + rnd.randint(1, 3)
            self.jardim(xa, za, xb, zb)
        return n

    def jardim(self, xa, za, xb, zb):
        m, t, rnd = self.m, self.t, self.rnd
        for z in range(za, zb + 1):
            for x in range(xa, xb + 1):
                if self.ocupado[z, x]: continue
                r = rnd.random()
                if r < 0.012 and self.livre(x - 2, z - 2, x + 2, z + 2):
                    arvore(m, rnd, x, z, rnd.choice(t['arvores']))
                    self.ocupado[z - 2:z + 3, x - 2:x + 3] = True
                elif r < 0.09 and m.vol[G + 1, z, x] == 0:
                    m.p(x, G + 1, z, m.id(rnd.choice(t['flores'])))
                elif r < 0.30 and self.tema != 'demonios' and m.vol[G + 1, z, x] == 0:
                    m.p(x, G + 1, z, m.id('short_grass') if valido('short_grass') else m.id('grass'))

    def fazenda(self, xa, za, xb, zb, moinho_dado):
        m, rnd = self.m, self.rnd
        culturas = ['wheat', 'carrots', 'potatoes', 'beetroots']
        for z in range(za, zb + 1):
            linha = (z - za) % 5
            for x in range(xa, xb + 1):
                if linha == 2:
                    m.vol[G, z, x] = m.id('water', level=0)
                else:
                    m.vol[G, z, x] = m.id('farmland', moisture=7)
                    c = culturas[((x - xa) // 9 + (z - za) // 10) % 4]
                    m.p(x, G + 1, z, m.id(c, age=3 if c == 'beetroots' else 7))
        for x in range(xa, xb + 1):   # cercas
            for z in (za - 1, zb + 1): m.p(x, G + 1, z, m.id('oak_fence'))
        if moinho_dado:
            pal, sub = moinho_dado
            h, d, w = sub.shape
            ox, oz = xb - w + 1, za
            m.vol[G + 1:, oz:oz + d, ox:ox + w] = 0
            colar_volume(m, pal, sub, ox, G - 1, oz, None)
        self.ocupado[za - 1:zb + 2, xa:xb + 1] = True

    def postes_nas_ruas(self):
        m, t = self.m, self.t
        for r in self.ruas:
            w = 5 if r == C else 3
            for k in range(self.muro_i + 6, S - self.muro_i - 6, 10):
                for (x, z) in ((r - w, k), (r + w, k), (k, r - w), (k, r + w)):
                    if 0 <= x < S and 0 <= z < S and m.vol[G + 1, z, x] == 0 and not self.casa_em(x, z):
                        poste(m, t, x, z, 3)

    def casa_em(self, x, z):
        return any(self.m.vol[G + 1, z + dz, x + dx] != 0 for dx in (-1, 0, 1) for dz in (-1, 0, 1)
                   if 0 <= x + dx < S and 0 <= z + dz < S)

    # ---------- ligações de cercas/painéis/muros
    def conectar(self):
        m = self.m
        conecta = {}
        for i, (nome, props) in enumerate(m.paleta):
            n = nome.split(':')[1]
            if n.endswith('_pane') or n.endswith('_fence') or n.endswith('_wall') or n == 'iron_bars':
                conecta[i] = n
        if not conecta: return
        alvo = np.isin(m.vol, list(conecta.keys()))
        solido = np.zeros(len(m.paleta) + 4096, dtype=bool)
        for i, (nome, props) in enumerate(m.paleta):
            n = nome.split(':')[1]
            plano = any(s in n for s in ('_slab', '_stairs', 'carpet', 'lantern', 'flower', 'grass', 'roots', 'fungus', 'torch', 'water', 'lava',
                                          '_door', 'trapdoor', 'sign', 'banner', 'bed', 'rail', 'chain', 'end_rod', 'campfire', 'tulip', 'poppy',
                                          'dandelion', 'daisy', 'bluet', 'allium', 'lily', 'cornflower', 'wheat', 'carrots', 'potatoes', 'beetroots',
                                          'leaves', 'bell'))
            solido[i] = i != 0 and not plano
        solido[len(m.paleta):] = True  # estados novos de cerca/muro também conectam
        ys, zs, xs = np.nonzero(alvo)
        cache = {}
        for y, z, x in zip(ys, zs, xs):
            i = int(m.vol[y, z, x]); n = conecta[i]
            viz = {}
            for lado, dx, dz in (('east', 1, 0), ('west', -1, 0), ('south', 0, 1), ('north', 0, -1)):
                xx, zz = x + dx, z + dz
                ok = 0 <= xx < S and 0 <= zz < S and solido[m.vol[y, zz, xx]]
                if n.endswith('_wall'): viz[lado] = 'low' if ok else 'none'
                else: viz[lado] = 'true' if ok else 'false'
            if n.endswith('_wall'):
                cima = y + 1 < H and m.vol[y + 1, z, x] != 0
                retos = (viz['east'] != 'none' and viz['west'] != 'none' and viz['north'] == 'none' and viz['south'] == 'none') or \
                        (viz['north'] != 'none' and viz['south'] != 'none' and viz['east'] == 'none' and viz['west'] == 'none')
                viz['up'] = 'true' if (cima or not retos) else 'false'
            k = (n, tuple(sorted(viz.items())))
            if k not in cache: cache[k] = m.id(n, **viz)
            m.vol[y, z, x] = cache[k]

    # ---------- tudo
    def gerar(self):
        self.terreno()
        self.muralha()
        self.praca()
        self.principal()
        self.ruas_grade()
        lib = biblioteca_casas()
        extras = []
        if self.tema == 'oeste':
            pal_m, mans = mansoes()
            for sub in mans: extras.append(('mansao', (pal_m, sub, None)))
            mo = moinho()
            extras.insert(1, ('fazenda', mo)); extras.insert(3, ('fazenda', None)); extras.append(('fazenda', mo))
        elif self.tema == 'lubelius':
            pal_m, mans = mansoes()
            branco = {'red_concrete': 'white_concrete', 'red_concrete_powder': 'white_concrete_powder', 'red_terracotta': 'light_blue_terracotta',
                      'dark_oak_planks': 'birch_planks', 'dark_oak_stairs': 'birch_stairs', 'dark_oak_slab': 'birch_slab', 'dark_oak_trapdoor': 'birch_trapdoor',
                      'dark_oak_fence': 'birch_fence', 'spruce_slab': 'birch_slab', 'spruce_stairs': 'birch_stairs', 'stripped_spruce_wood': 'stripped_birch_wood',
                      'bricks': 'quartz_bricks', 'red_nether_bricks': 'white_terracotta', 'netherrack': 'calcite', 'nether_wart_block': 'cyan_terracotta',
                      'granite': 'diorite', 'andesite': 'polished_diorite', 'andesite_stairs': 'polished_diorite_stairs', 'oak_log': 'birch_log'}
            for sub in mans[:3]: extras.append(('mansao', (pal_m, sub, branco)))
        else:
            pal_m, mans = mansoes()
            negro = {'red_concrete': 'red_nether_bricks', 'red_concrete_powder': 'nether_wart_block', 'red_terracotta': 'red_nether_bricks',
                     'dark_oak_planks': 'crimson_planks', 'dark_oak_stairs': 'crimson_stairs', 'dark_oak_slab': 'crimson_slab', 'dark_oak_trapdoor': 'crimson_trapdoor',
                     'dark_oak_fence': 'crimson_fence', 'spruce_slab': 'crimson_slab', 'spruce_stairs': 'crimson_stairs', 'stripped_spruce_wood': 'stripped_crimson_hyphae',
                     'smooth_quartz': 'polished_blackstone', 'calcite': 'blackstone', 'diorite': 'basalt', 'light_gray_wool': 'black_wool',
                     'andesite': 'blackstone', 'andesite_stairs': 'blackstone_stairs', 'polished_andesite': 'polished_blackstone', 'stone_bricks': 'polished_blackstone_bricks',
                     'oak_log': 'crimson_stem', 'granite': 'blackstone', 'glass_pane': 'red_stained_glass_pane', 'lantern': 'soul_lantern', 'torch': 'soul_torch',
                     'wall_torch': 'soul_wall_torch', 'grass_block': 'crimson_nylium', 'oak_leaves': 'nether_wart_block', 'spruce_leaves': 'nether_wart_block'}
            for sub in mans[:3]: extras.append(('mansao', (pal_m, sub, negro)))
        n = self.casas(lib, extras)
        self.postes_nas_ruas()
        self.conectar()
        return n


def gravar(cid, saida, chao):
    m = cid.m
    os.makedirs(saida, exist_ok=True)
    chunks = []
    altura = H
    for oz in range(N):
        for ox in range(N):
            sub = m.vol[:, oz * 16:oz * 16 + 16, ox * 16:ox * 16 + 16]
            if gravar_fatia(os.path.join(saida, f'{ox}_{oz}.nbt'), m.paleta, sub): chunks.append([ox, oz])
    manifesto(saida, N, N, altura, chao - FUND, chao, chunks)
    return len(chunks)


if __name__ == '__main__':
    tema, saida = sys.argv[1], sys.argv[2]
    semente = int(sys.argv[3]) if len(sys.argv) > 3 else 7
    cid = Cidade(tema, semente)
    n = cid.gerar()
    print(tema, 'casas/especiais:', n, 'paleta:', len(cid.m.paleta))
    np.save(f'/tmp/cid_{tema}.npy', cid.m.vol); pickle.dump(cid.m.paleta, open(f'/tmp/cid_{tema}.pal', 'wb'))
    if saida != '-':
        print('fatias:', gravar(cid, saida, int(os.environ.get('CHAO', '70'))))
