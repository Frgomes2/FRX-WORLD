"""Gera data/frxworld/structures/taverna.nbt (Minecraft 1.20.1, DataVersion 3465)."""
import gzip, struct, sys

W, H, D = 13, 8, 11  # x, y, z
blocos = {}

def b(x, y, z, nome, **props):
    blocos[(x, y, z)] = (nome if ':' in nome else 'minecraft:' + nome, props)

# ---------------------------------------------------------------- volume (ar) e fundação
for x in range(W):
    for y in range(H):
        for z in range(D):
            b(x, y, z, 'air')
for x in range(1, W):
    for z in range(D):
        borda = x in (1, W - 1) or z in (0, D - 1)
        b(x, 0, z, 'cobblestone' if borda else 'spruce_planks')

# ---------------------------------------------------------------- paredes
for y in range(1, 5):
    for x in range(1, W):
        for z in range(D):
            if not (x in (1, W - 1) or z in (0, D - 1)):
                continue
            canto = x in (1, W - 1) and z in (0, D - 1)
            if canto:
                b(x, y, z, 'stripped_spruce_log', axis='y')
            elif y == 4:
                b(x, y, z, 'stripped_spruce_log', axis='x' if z in (0, D - 1) else 'z')
            else:
                b(x, y, z, 'oak_planks')
# janelas
for (x, z) in [(4, 0), (8, 0), (4, D - 1), (8, D - 1), (W - 1, 3), (W - 1, 7), (1, 2), (1, 8)]:
    eixo_x = z in (0, D - 1)
    b(x, 2, z, 'glass_pane', north=str(eixo_x).lower(), south=str(eixo_x).lower(),
      east=str(not eixo_x).lower(), west=str(not eixo_x).lower(), waterlogged='false')
# porta (parede oeste, x=1) e degrau / jigsaw de entrada
for y, metade in [(1, 'lower'), (2, 'upper')]:
    b(1, y, 5, 'oak_door', facing='east', half=metade, hinge='left', open='false', powered='false')
b(0, 0, 5, 'jigsaw', orientation='west_up')
b(0, 1, 4, 'lantern', hanging='false', waterlogged='false')
b(0, 0, 4, 'cobblestone')

# ---------------------------------------------------------------- telhado em degraus
for x in range(0, W):
    for z in range(-1 + 1, D):
        b(x, 5, z, 'dark_oak_planks') if x >= 1 else None
for x in range(2, W - 1):
    for z in range(1, D - 1):
        b(x, 6, z, 'dark_oak_planks')
for x in range(3, W - 2):
    for z in range(2, D - 2):
        b(x, 7, z, 'dark_oak_slab', type='bottom', waterlogged='false')
# chaminé
for y in range(5, 8):
    b(6, y, 0, 'cobblestone_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')

# ---------------------------------------------------------------- interior
# balcão (x=9, z=2..8) com abertura em z=8
for z in range(2, 8):
    b(9, 1, z, 'spruce_planks')
    b(9, 2, z, 'spruce_slab', type='bottom', waterlogged='false')
b(9, 2, 4, 'brewing_stand', has_bottle_0='false', has_bottle_1='false', has_bottle_2='false')
# barris e prateleiras atrás do balcão
for z in range(2, 9):
    b(11, 1, z, 'barrel', facing='west', open='false')
    if z % 2 == 0:
        b(11, 2, z, 'barrel', facing='west', open='false')
    else:
        b(11, 2, z, 'bookshelf')
# quadro de missões na parede norte, ao lado da porta
b(3, 2, D - 1, 'frxworld:quadro_missoes')
b(3, 1, D - 2, 'red_carpet')
# mesas e cadeiras
for (mx, mz) in [(4, 3), (4, 7), (6, 5)]:
    b(mx, 1, mz, 'oak_fence', north='false', south='false', east='false', west='false', waterlogged='false')
    b(mx, 2, mz, 'oak_pressure_plate', powered='false')
    b(mx - 1, 1, mz, 'spruce_stairs', facing='east', half='bottom', shape='straight', waterlogged='false')
    b(mx + 1, 1, mz, 'spruce_stairs', facing='west', half='bottom', shape='straight', waterlogged='false')
# lareira na parede sul
b(6, 1, 1, 'campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')
b(5, 1, 1, 'cobblestone'); b(7, 1, 1, 'cobblestone')
for y in (2, 3, 4):
    b(6, y, 1, 'stone_bricks')
b(5, 2, 1, 'cobblestone_slab', type='bottom', waterlogged='false')
b(7, 2, 1, 'cobblestone_slab', type='bottom', waterlogged='false')
# tapete
for x in range(3, 8):
    b(x, 1, 5, 'red_carpet') if (x, 1, 5) and blocos[(x, 1, 5)][0] == 'minecraft:air' else None
# iluminação
for (lx, lz) in [(4, 3), (4, 7), (8, 5), (10, 3), (10, 7), (2, 5)]:
    b(lx, 4, lz, 'lantern', hanging='true', waterlogged='false')

# ---------------------------------------------------------------- NBT
def s_str(v): e = v.encode(); return struct.pack('>H', len(e)) + e

class N:
    def __init__(self, t, v): self.t, self.v = t, v

def enc(t, v):
    if t == 1: return struct.pack('>b', v)
    if t == 3: return struct.pack('>i', v)
    if t == 5: return struct.pack('>f', v)
    if t == 6: return struct.pack('>d', v)
    if t == 8: return s_str(v)
    if t == 9:
        et, itens = v
        return bytes([et]) + struct.pack('>i', len(itens)) + b''.join(enc(et, i) for i in itens)
    if t == 10:
        out = b''
        for k, n in v.items():
            out += bytes([n.t]) + s_str(k) + enc(n.t, n.v)
        return out + b'\x00'
    raise ValueError(t)

def comp(d): return N(10, d)
def s(v): return N(8, v)
def i(v): return N(3, v)

paleta, idx = [], {}
for pos, (nome, props) in sorted(blocos.items()):
    chave = (nome, tuple(sorted(props.items())))
    if chave not in idx:
        idx[chave] = len(paleta)
        e = {'Name': s(nome)}
        if props:
            e['Properties'] = comp({k: s(str(v)) for k, v in props.items()})
        paleta.append(e)

lista_blocos = []
for (x, y, z), (nome, props) in sorted(blocos.items()):
    e = {'pos': N(9, (3, [x, y, z])), 'state': i(idx[(nome, tuple(sorted(props.items())))])}
    if nome == 'minecraft:jigsaw':
        e['nbt'] = comp({
            'id': s('minecraft:jigsaw'), 'name': s('minecraft:building_entrance'),
            'target': s('minecraft:building_entrance'), 'pool': s('minecraft:village/plains/streets'),
            'joint': s('aligned'),
            'final_state': s('minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]'),
        })
    lista_blocos.append(e)

taverneiro = comp({
    'id': s('minecraft:villager'),
    'CustomName': s('{"text":"Taverneiro","color":"gold"}'),
    'CustomNameVisible': N(1, 1),
    'NoAI': N(1, 1), 'Invulnerable': N(1, 1), 'PersistenceRequired': N(1, 1),
    'Tags': N(9, (8, ['frx_taverneiro'])),
    'VillagerData': comp({'profession': s('minecraft:nitwit'), 'type': s('minecraft:plains'), 'level': i(1)}),
    'Rotation': N(9, (5, [90.0, 0.0])),
    'Pos': N(9, (6, [10.5, 1.0, 5.5])),
})
entidades = [comp({'pos': N(9, (6, [10.5, 1.0, 5.5])), 'blockPos': N(9, (3, [10, 1, 5])), 'nbt': taverneiro})]

raiz = {
    'DataVersion': i(3465),
    'size': N(9, (3, [W, H, D])),
    'palette': N(9, (10, [p for p in paleta])),
    'blocks': N(9, (10, lista_blocos)),
    'entities': N(9, (10, [e.v for e in entidades])),
}
raiz['palette'] = N(9, (10, paleta))
raiz['blocks'] = N(9, (10, lista_blocos))
dados = bytes([10]) + s_str('') + enc(10, raiz)
with gzip.open(sys.argv[1], 'wb') as f:
    f.write(dados)
print('blocos', len(blocos), 'paleta', len(paleta))
