"""Gera data/frxworld/structures/cripta.nbt: ruína de pedra com o Núcleo da Dungeon no centro."""
import random, sys
from nbt_util import salvar_estrutura

random.seed(20261103)
W, H, D = 15, 10, 15
B = {}
def b(x, y, z, nome, **p): B[(x, y, z)] = (nome if ':' in nome else 'minecraft:' + nome, p)

def tijolo():
    r = random.random()
    return 'mossy_stone_bricks' if r < 0.3 else 'cracked_stone_bricks' if r < 0.5 else 'stone_bricks'

for x in range(W):
    for y in range(H):
        for z in range(D):
            b(x, y, z, 'air')

# chão
for x in range(W):
    for z in range(D):
        b(x, 0, z, tijolo())

# paredes em ruína (altura irregular), entrada no sul (z=0, x=6..8)
for x in range(W):
    for z in range(D):
        if not (x in (0, W - 1) or z in (0, D - 1)): continue
        if z == 0 and 6 <= x <= 8: continue
        altura = random.choice([2, 3, 4, 4, 5, 5, 5, 6])
        if x in (0, W - 1) and z in (0, D - 1): altura = 7
        for y in range(1, altura + 1):
            if random.random() < 0.06: continue  # buracos
            b(x, y, z, tijolo())
        if altura >= 3 and random.random() < 0.25 and not (x in (0, W - 1) and z in (0, D - 1)):
            b(x, 2, z, 'iron_bars', north='false', south='false', east='false', west='false', waterlogged='false')
# arco da entrada
for x in (5, 9):
    for y in range(1, 5): b(x, y, 0, 'chiseled_stone_bricks')
for x in range(5, 10): b(x, 5, 0, 'stone_bricks')
b(6, 4, 0, 'stone_brick_stairs', facing='east', half='top', shape='straight', waterlogged='false')
b(8, 4, 0, 'stone_brick_stairs', facing='west', half='top', shape='straight', waterlogged='false')

# pilares internos com lanternas de alma
for (px, pz) in [(3, 3), (3, 11), (11, 3), (11, 11)]:
    for y in range(1, 7): b(px, y, pz, 'chiseled_stone_bricks' if y in (1, 6) else 'stone_bricks')
    for (dx, dz) in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
        if 1 <= px + dx <= W - 2 and 1 <= pz + dz <= D - 2 and random.random() < 0.5:
            b(px + dx, 4, pz + dz, 'soul_lantern', hanging='false', waterlogged='false') if False else None
    b(px, 7, pz, 'soul_lantern', hanging='false', waterlogged='false')

# teto parcial em ruína
for x in range(1, W - 1):
    for z in range(1, D - 1):
        anel = x <= 3 or x >= W - 4 or z <= 3 or z >= D - 4
        if anel and random.random() < 0.55:
            b(x, 6, z, 'stone_brick_slab', type='bottom', waterlogged='false')

# altar central com o núcleo
for dx in range(-1, 2):
    for dz in range(-1, 2):
        b(7 + dx, 1, 7 + dz, 'polished_deepslate_slab', type='bottom', waterlogged='false')
b(7, 1, 7, 'chiseled_deepslate')
b(7, 2, 7, 'frxworld:nucleo_dungeon')
for (dx, dz, rot) in [(-2, -2, 6), (2, -2, 10), (-2, 2, 2), (2, 2, 14)]:
    b(7 + dx, 1, 7 + dz, 'skeleton_skull', rotation=str(rot))
for (dx, dz) in [(0, -3), (0, 3), (-3, 0), (3, 0)]:
    b(7 + dx, 1, 7 + dz, 'soul_campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')

# teias e entulho
for _ in range(14):
    x, z = random.randint(1, W - 2), random.randint(1, D - 2)
    if B[(x, 1, z)][0] == 'minecraft:air' and abs(x - 7) + abs(z - 7) > 3:
        b(x, random.choice([1, 1, 2, 3]), z, 'cobweb')
for _ in range(10):
    x, z = random.randint(1, W - 2), random.randint(1, D - 2)
    if B[(x, 1, z)][0] == 'minecraft:air' and abs(x - 7) + abs(z - 7) > 3:
        b(x, 1, z, random.choice(['cobblestone', 'mossy_cobblestone']))

print(salvar_estrutura(sys.argv[1], (W, H, D), B))
