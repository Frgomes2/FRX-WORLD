"""
Gera a Catacumba (dungeon em camadas) como peças de jigsaw:
  structures/catacumba/<peça>.nbt  e  worldgen/template_pool/catacumba/*.json
Uso: python3 gerar_catacumba.py <pasta resources/data/frxworld>
"""
import json, os, random, sys
from nbt_util import salvar_estrutura, s, i, N, comp

SAIDA = sys.argv[1]
os.makedirs(f"{SAIDA}/structures/catacumba", exist_ok=True)
os.makedirs(f"{SAIDA}/worldgen/template_pool/catacumba", exist_ok=True)
random.seed(42)

CAMADAS = {
    1: dict(paredes=['stone_bricks'] * 5 + ['mossy_stone_bricks'] * 3 + ['cracked_stone_bricks'] * 2,
            chao=['stone_bricks', 'mossy_stone_bricks', 'cobblestone'], teto='stone_bricks', luz='lantern',
            escada='stone_brick_stairs', destaque='chiseled_stone_bricks', mobs=['minecraft:zombie', 'minecraft:spider'],
            loot='minecraft:chests/simple_dungeon'),
    2: dict(paredes=['deepslate_bricks'] * 5 + ['cracked_deepslate_bricks'] * 3 + ['deepslate_tiles'] * 2,
            chao=['deepslate_tiles', 'cobbled_deepslate', 'deepslate_bricks'], teto='deepslate_bricks', luz='soul_lantern',
            escada='deepslate_brick_stairs', destaque='chiseled_deepslate', mobs=['minecraft:skeleton', 'minecraft:zombie'],
            loot='minecraft:chests/abandoned_mineshaft'),
    3: dict(paredes=['polished_blackstone_bricks'] * 6 + ['cracked_polished_blackstone_bricks'] * 3 + ['gilded_blackstone'],
            chao=['polished_blackstone', 'blackstone', 'polished_blackstone_bricks'], teto='polished_blackstone_bricks',
            luz='soul_lantern', escada='polished_blackstone_brick_stairs', destaque='chiseled_polished_blackstone',
            mobs=['minecraft:skeleton', 'minecraft:cave_spider'], loot='minecraft:chests/bastion_other'),
}

NOME = 'frxworld:passagem'
DIRS = {'west': (-1, 0), 'east': (1, 0), 'north': (0, -1), 'south': (0, 1)}


class Peca:
    def __init__(self, tam, camada):
        self.W, self.H, self.D = tam
        self.c = CAMADAS[camada]
        self.B, self.NBT = {}, {}

    def b(self, x, y, z, nome, **p):
        self.B[(x, y, z)] = (nome if ':' in nome else 'minecraft:' + nome, p)

    def parede(self, x, y, z): self.b(x, y, z, random.choice(self.c['paredes']))
    def chao(self, x, y, z): self.b(x, y, z, random.choice(self.c['chao']))

    def caixa(self, x0, x1, y0, y1, z0, z1):
        """Sala oca: paredes/piso/teto nas bordas, ar dentro."""
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    borda_x, borda_z = x in (x0, x1), z in (z0, z1)
                    if y == y0: self.chao(x, y, z)
                    elif y == y1: self.b(x, y, z, self.c['teto'])
                    elif borda_x or borda_z: self.parede(x, y, z)
                    else: self.b(x, y, z, 'air')

    def abertura(self, x, y, z, eixo_x, largura=1, altura=3):
        """Abre uma passagem 3x3 centrada em (x,z) na parede."""
        for d in range(-largura, largura + 1):
            for h in range(altura):
                if eixo_x: self.b(x, y + h, z + d, 'air')
                else: self.b(x + d, y + h, z, 'air')

    def jigsaw(self, x, y, z, frente, pool, final='minecraft:air'):
        self.b(x, y, z, 'jigsaw', orientation=f'{frente}_up')
        self.NBT[(x, y, z)] = {
            'id': s('minecraft:jigsaw'), 'name': s(NOME), 'target': s(NOME), 'pool': s(pool),
            'joint': s('aligned'), 'final_state': s(final),
        }

    def spawner(self, x, y, z):
        mob = random.choice(self.c['mobs'])
        self.b(x, y, z, 'spawner')
        self.NBT[(x, y, z)] = {
            'id': s('minecraft:mob_spawner'),
            'SpawnData': comp({'entity': comp({'id': s(mob)})}),
            'Delay': N(2, 20) if False else i(20),
            'MinSpawnDelay': i(200), 'MaxSpawnDelay': i(600), 'SpawnCount': i(3),
            'MaxNearbyEntities': i(5), 'RequiredPlayerRange': i(14), 'SpawnRange': i(4),
        }

    def bau(self, x, y, z, facing='north'):
        self.b(x, y, z, 'chest', facing=facing, type='single', waterlogged='false')
        self.NBT[(x, y, z)] = {'id': s('minecraft:chest'), 'LootTable': s(self.c['loot'])}

    def luz(self, x, y, z, pendurada=True):
        self.b(x, y, z, self.c['luz'], hanging='true' if pendurada else 'false', waterlogged='false')

    def teia(self, x, y, z, chance=0.08):
        if self.B.get((x, y, z), ('minecraft:air',))[0] == 'minecraft:air' and random.random() < chance:
            self.b(x, y, z, 'cobweb')

    def salvar(self, nome):
        n, p = salvar_estrutura(f"{SAIDA}/structures/catacumba/{nome}.nbt", (self.W, self.H, self.D), self.B, nbt_blocos=self.NBT)
        print(f"  {nome}: {n} blocos")


def pool(camada): return f'frxworld:catacumba/camada{camada}'


# ------------------------------------------------------------------ peças por camada
def corredor(c):
    p = Peca((9, 6, 5), c)
    p.caixa(0, 8, 0, 5, 0, 4)
    p.abertura(0, 1, 2, True); p.abertura(8, 1, 2, True)
    p.jigsaw(0, 1, 2, 'west', pool(c)); p.jigsaw(8, 1, 2, 'east', pool(c))
    p.luz(4, 4, 2)
    for x in range(1, 8):
        for z in (1, 3): p.teia(x, 4, z, 0.12)
    p.salvar(f'corredor{c}')


def curva(c):
    p = Peca((5, 6, 5), c)
    p.caixa(0, 4, 0, 5, 0, 4)
    p.abertura(0, 1, 2, True); p.abertura(2, 1, 4, False)
    p.jigsaw(0, 1, 2, 'west', pool(c)); p.jigsaw(2, 1, 4, 'south', pool(c))
    p.luz(2, 4, 2)
    p.salvar(f'curva{c}')


def cruzamento(c):
    p = Peca((7, 7, 7), c)
    p.caixa(0, 6, 0, 6, 0, 6)
    for (x, z, f, ex) in [(0, 3, 'west', True), (6, 3, 'east', True), (3, 0, 'north', False), (3, 6, 'south', False)]:
        p.abertura(x, 1, z, ex); p.jigsaw(x, 1, z, f, pool(c))
    for y in range(1, 6): p.b(3, y, 3, p.c['destaque'])
    p.luz(3, 5, 2); p.luz(3, 5, 4)
    p.salvar(f'cruzamento{c}')


def sala(c):
    p = Peca((13, 8, 13), c)
    p.caixa(0, 12, 0, 7, 0, 12)
    for (x, z, f, ex) in [(0, 6, 'west', True), (12, 6, 'east', True), (6, 0, 'north', False), (6, 12, 'south', False)]:
        p.abertura(x, 1, z, ex); p.jigsaw(x, 1, z, f, pool(c))
    for (px, pz) in [(3, 3), (3, 9), (9, 3), (9, 9)]:
        for y in range(1, 7): p.b(px, y, pz, p.c['destaque'] if y in (1, 6) else random.choice(p.c['paredes']))
        p.luz(px, 6 - 0, pz) if False else p.luz(px + 1, 6, pz)
    p.spawner(6, 1, 6)
    p.bau(1, 1, 1, 'south'); p.bau(11, 1, 11, 'north')
    for _ in range(25):
        p.teia(random.randint(1, 11), random.randint(1, 6), random.randint(1, 11), 0.5)
    p.salvar(f'sala{c}')


def descida(c):
    """Antecâmara com o guardião do andar + Portão Selado + escadaria que desce 8 blocos."""
    p = Peca((22, 16, 9), c)
    # antecâmara (x 0..10, piso y=8)
    for x in range(0, 11):
        for z in range(0, 9):
            for y in range(8, 16):
                if y == 8: p.chao(x, y, z)
                elif y == 15: p.b(x, y, z, p.c['teto'])
                elif x == 0 or z in (0, 8): p.parede(x, y, z)
                else: p.b(x, y, z, 'air')
    p.abertura(0, 9, 4, True); p.jigsaw(0, 9, 4, 'west', pool(c))
    for (px, pz) in [(3, 2), (3, 6), (8, 2), (8, 6)]:
        for y in range(9, 15): p.b(px, y, pz, p.c['destaque'] if y in (9, 14) else random.choice(p.c['paredes']))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            p.b(5 + dx, 8, 4 + dz, p.c['destaque'])
    p.b(5, 9, 4, 'frxworld:nucleo_dungeon', andar=str(c))
    p.luz(5, 14, 2); p.luz(5, 14, 6)
    # parede com o Portão Selado (x=11)
    for z in range(0, 9):
        for y in range(8, 16):
            if 3 <= z <= 5 and 9 <= y <= 11: p.b(11, y, z, 'frxworld:portao_selado', camada=str(c))
            else: p.parede(11, y, z) if y < 15 else p.b(11, y, z, p.c['teto'])
    # escadaria (x 12..20, z 2..6) descendo até o piso 0
    for x in range(12, 22):
        for z in range(2, 7):
            for y in range(0, 16):
                if z in (2, 6): p.parede(x, y, z)
                elif y == 15: p.b(x, y, z, p.c['teto'])
                else: p.b(x, y, z, 'air')
        piso = max(0, 8 - (x - 11))
        for z in (3, 4, 5):
            for y in range(0, piso): p.chao(x, y, z) if y == 0 else p.parede(x, y, z)
            if 12 <= x <= 18: p.b(x, piso, z, p.c['escada'], facing='west', half='bottom', shape='straight', waterlogged='false')
            else: p.chao(x, piso, z)
    for z in (3, 4, 5):
        for y in range(1, 15):
            if not (1 <= y <= 3): p.parede(21, y, z)
    p.jigsaw(21, 1, 4, 'east', pool(c + 1))
    for x in (13, 16, 19): p.luz(x, 14, 4)
    p.salvar(f'descida{c}')


def chefe():
    """Sala do guardião (fim da camada 3) com o Núcleo da Dungeon."""
    p = Peca((21, 14, 21), 3)
    p.caixa(0, 20, 0, 13, 0, 20)
    p.abertura(0, 1, 10, True); p.jigsaw(0, 1, 10, 'west', pool(3))
    for (px, pz) in [(4, 4), (4, 16), (16, 4), (16, 16), (4, 10), (16, 10), (10, 4), (10, 16)]:
        for y in range(1, 13): p.b(px, y, pz, 'polished_blackstone_bricks' if y % 4 else 'chiseled_polished_blackstone')
        p.b(px, 1, pz, 'gilded_blackstone')
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            p.b(10 + dx, 1, 10 + dz, 'polished_blackstone_slab', type='bottom', waterlogged='false')
    p.b(10, 1, 10, 'chiseled_polished_blackstone')
    p.b(10, 2, 10, 'frxworld:nucleo_dungeon', andar='0')
    for (dx, dz) in [(-3, 0), (3, 0), (0, -3), (0, 3)]:
        p.b(10 + dx, 1, 10 + dz, 'soul_campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')
    for (x, z) in [(7, 7), (13, 7), (7, 13), (13, 13)]:
        for y in range(8, 13): p.b(x, y, z, 'chain', axis='y', waterlogged='false')
        p.b(x, 7, z, 'soul_lantern', hanging='true', waterlogged='false')
    p.bau(19, 1, 1, 'west'); p.bau(19, 1, 19, 'west')
    p.salvar('chefe')


def fim(c):
    """Tampa usada quando a geração para: fecha a passagem com parede."""
    p = Peca((1, 5, 5), c)
    for z in range(5):
        for y in range(5): p.parede(0, y, z)
    p.jigsaw(0, 1, 2, 'west', 'minecraft:empty', final='minecraft:' + CAMADAS[c]['paredes'][0])
    p.salvar(f'fim{c}')


def entrada():
    """Ruína na superfície + poço em espiral que desce 17 blocos até a camada 1."""
    p = Peca((13, 26, 13), 1)
    S = 17  # altura do piso da superfície dentro da peça
    # poço
    for x in range(2, 11):
        for z in range(2, 11):
            for y in range(0, S):
                if x in (2, 10) or z in (2, 10):
                    p.b(x, y, z, 'shroomlight' if (y % 5 == 2 and (x + z) % 4 == 0) else random.choice(p.c['paredes']))
                elif 4 <= x <= 8 and 4 <= z <= 8:
                    p.b(x, y, z, p.c['destaque'] if y % 6 == 0 else random.choice(p.c['paredes']))
                elif y == 0:
                    p.chao(x, y, z)
    anel = [(x, 3) for x in range(3, 10)] + [(9, z) for z in range(4, 10)] + [(x, 9) for x in range(8, 2, -1)] + [(3, z) for z in range(8, 3, -1)]
    assert len(anel) == 24
    inicio = (anel.index((9, 6)) - S) % 24
    usados = set()
    for k in range(S + 1):
        x, z = anel[(inicio + k) % 24]
        usados.add((x, z))
        topo = S - k
        for y in range(0, topo): p.chao(x, y, z) if y == 0 else p.b(x, y, z, random.choice(p.c['paredes']))
        ax, az = anel[(inicio + k - 1) % 24] if k > 0 else anel[(inicio + 1) % 24]
        dx, dz = ax - x, az - z
        if k == 0: dx, dz = -dx, -dz
        frente = {(-1, 0): 'west', (1, 0): 'east', (0, -1): 'north', (0, 1): 'south'}[(dx, dz)]
        p.b(x, topo, z, p.c['escada'], facing=frente, half='bottom', shape='straight', waterlogged='false')
        for y in range(topo + 1, S + 1): p.b(x, y, z, 'air')
    for (x, z) in anel:
        if (x, z) not in usados:
            for y in range(0, S + 1): p.b(x, y, z, random.choice(p.c['paredes']))
    # saída no fundo, a leste
    for x in (10, 11, 12):
        for z in range(4, 9):
            for y in range(0, 5):
                if y == 0: p.chao(x, y, z)
                elif y == 4 or z in (4, 8): p.parede(x, y, z)
                else: p.b(x, y, z, 'air')
    p.jigsaw(12, 1, 6, 'east', pool(1))
    # superfície: piso, ruína e ar acima
    for x in range(13):
        for z in range(13):
            if (x, z) in usados and anel.index((x, z)) != inicio: continue
            if not (2 <= x <= 10 and 2 <= z <= 10) or (4 <= x <= 8 and 4 <= z <= 8) or (x, z) == anel[inicio]:
                if (x, z) != anel[inicio]: p.chao(x, S, z)
            for y in range(S + 1, 26): p.b(x, y, z, 'air')
    for x in range(13):
        for z in range(13):
            if x in (0, 12) or z in (0, 12):
                if z == 0 and 5 <= x <= 7: continue
                for y in range(S + 1, S + 1 + random.choice([1, 2, 3, 4, 4, 5])): p.parede(x, y, z)
    for (x, z) in [(0, 0), (12, 0), (0, 12), (12, 12)]:
        for y in range(S + 1, S + 8): p.b(x, y, z, p.c['destaque'])
        p.b(x, S + 8, z, 'soul_lantern', hanging='false', waterlogged='false')
    p.salvar('entrada')


print('Peças:')
for c in (1, 2, 3):
    corredor(c); curva(c); cruzamento(c); sala(c); fim(c)
    if c < 3: descida(c)
chefe(); entrada()

# ------------------------------------------------------------------ pools
def elemento(nome, peso):
    return {'element': {'element_type': 'minecraft:single_pool_element', 'location': f'frxworld:catacumba/{nome}',
                        'processors': {'processors': []}, 'projection': 'rigid'}, 'weight': peso}

pools = {
    'inicio': ([elemento('entrada', 1)], 'minecraft:empty'),
    'camada1': ([elemento('corredor1', 4), elemento('curva1', 2), elemento('cruzamento1', 2), elemento('sala1', 2), elemento('descida1', 3)], 'frxworld:catacumba/fim1'),
    'camada2': ([elemento('corredor2', 4), elemento('curva2', 2), elemento('cruzamento2', 2), elemento('sala2', 2), elemento('descida2', 3)], 'frxworld:catacumba/fim2'),
    'camada3': ([elemento('corredor3', 4), elemento('curva3', 2), elemento('cruzamento3', 1), elemento('sala3', 2), elemento('chefe', 3)], 'frxworld:catacumba/fim3'),
    'fim1': ([elemento('fim1', 1)], 'minecraft:empty'),
    'fim2': ([elemento('fim2', 1)], 'minecraft:empty'),
    'fim3': ([elemento('fim3', 1)], 'minecraft:empty'),
}
for nome, (elementos, fallback) in pools.items():
    with open(f"{SAIDA}/worldgen/template_pool/catacumba/{nome}.json", 'w') as f:
        json.dump({'elements': elementos, 'fallback': fallback}, f, indent=2)
print('Pools:', ', '.join(pools))
