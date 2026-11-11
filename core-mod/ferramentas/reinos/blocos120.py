"""Normaliza estados de bloco de versões mais novas (1.20.2–1.21.x) ou antigas para 1.20.1."""
import json, os, re, collections
_dados = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'dados_blocos.json')))
VALIDOS = {k: {p: (set(v) if v is not None else None) for p, v in props.items()} for k, props in _dados['validos'].items()}
BIOMAS = set(_dados['biomas'])
LEGACY = _dados['legacy']

RENOMEAR = {
    'short_grass': 'grass', 'sign': 'oak_sign', 'wall_sign': 'oak_wall_sign', 'grass_path': 'dirt_path', 'pale_moss_block': 'moss_block', 'pale_moss_carpet': 'moss_carpet', 'pale_hanging_moss': 'air',
    'creaking_heart': 'dark_oak_log', 'resin_block': 'orange_terracotta', 'resin_clump': 'air', 'chiseled_resin_bricks': 'bricks',
    'resin_bricks': 'bricks', 'resin_brick_stairs': 'brick_stairs', 'resin_brick_slab': 'brick_slab', 'resin_brick_wall': 'brick_wall',
    'tuff_bricks': 'stone_bricks', 'tuff_brick_stairs': 'stone_brick_stairs', 'tuff_brick_slab': 'stone_brick_slab', 'tuff_brick_wall': 'stone_brick_wall',
    'chiseled_tuff': 'chiseled_stone_bricks', 'chiseled_tuff_bricks': 'chiseled_stone_bricks',
    'polished_tuff': 'polished_andesite', 'polished_tuff_stairs': 'polished_andesite_stairs', 'polished_tuff_slab': 'polished_andesite_slab', 'polished_tuff_wall': 'andesite_wall',
    'tuff_stairs': 'andesite_stairs', 'tuff_slab': 'andesite_slab', 'tuff_wall': 'andesite_wall',
    'crafter': 'crafting_table', 'vault': 'chiseled_stone_bricks', 'trial_spawner': 'spawner', 'heavy_core': 'anvil',
    'open_eyeblossom': 'oxeye_daisy', 'closed_eyeblossom': 'oxeye_daisy', 'potted_open_eyeblossom': 'potted_oxeye_daisy', 'potted_closed_eyeblossom': 'potted_oxeye_daisy',
    'leaf_litter': 'air', 'wildflowers': 'pink_petals', 'firefly_bush': 'azalea', 'bush': 'grass', 'cactus_flower': 'air',
    'short_dry_grass': 'dead_bush', 'tall_dry_grass': 'dead_bush', 'dried_ghast': 'skeleton_skull', 'test_block': 'air', 'test_instance_block': 'air',
    'potted_pale_oak_sapling': 'potted_birch_sapling', 'copper_bulb': 'glowstone', 'chiseled_copper': 'cut_copper', 'copper_grate': 'cut_copper',
    'copper_door': 'iron_door', 'copper_trapdoor': 'iron_trapdoor', 'copper_chest': 'chest', 'copper_lantern': 'lantern', 'copper_torch': 'torch',
    'copper_wall_torch': 'wall_torch', 'copper_bars': 'iron_bars', 'copper_chain': 'chain', 'lightning_rod': 'lightning_rod',
}
CONTADOR = collections.Counter()

def _oxid(n):
    """exposed_copper_bulb / waxed_weathered_copper_grate ... -> nome base 1.20.1"""
    pref = ''
    m = re.match(r'(waxed_)?(exposed_|weathered_|oxidized_)?(.*)$', n)
    waxed, ox, base = m.groups()
    if base in ('copper_bulb', 'copper_door', 'copper_trapdoor', 'copper_lantern', 'copper_bars', 'copper_chain', 'copper_torch', 'copper_wall_torch', 'copper_chest'):
        return RENOMEAR[base]
    if base in ('chiseled_copper', 'copper_grate'):
        return (waxed or '') + (ox or '') + 'cut_copper'
    return None

def normalizar(nome, props):
    n = nome.split(':', 1)[1] if ':' in nome else nome
    if nome.startswith('minecraft:') or ':' not in nome:
        if 'minecraft:' + n not in VALIDOS:
            novo = RENOMEAR.get(n) or _oxid(n)
            if novo is None and n.startswith('pale_oak'):
                novo = 'birch' + n[len('pale_oak'):]
            if novo is None:
                for suf, alvo in (('_stairs', 'stone_brick_stairs'), ('_slab', 'stone_brick_slab'), ('_wall', 'stone_brick_wall'),
                                  ('_fence_gate', 'oak_fence_gate'), ('_fence', 'oak_fence'), ('_door', 'oak_door'), ('_trapdoor', 'oak_trapdoor'),
                                  ('_button', 'stone_button'), ('_pressure_plate', 'stone_pressure_plate'), ('_sign', 'oak_sign'), ('_leaves', 'oak_leaves'),
                                  ('_log', 'oak_log'), ('_planks', 'oak_planks'), ('_sapling', 'oak_sapling')):
                    if n.endswith(suf): novo = alvo; break
            if novo is None: novo = 'stone'
            CONTADOR[n + ' -> ' + novo] += 1
            n = novo
    else:
        CONTADOR[nome + ' -> stone (mod)'] += 1
        n = 'stone'
    nome = 'minecraft:' + n
    validos = VALIDOS.get(nome, {})
    p2 = {k: v for k, v in props.items() if k in validos and (validos[k] is None or v in validos[k])}
    return nome, p2

def bioma(b):
    if b in BIOMAS: return b
    m = {'minecraft:pale_garden': 'minecraft:dark_forest'}
    return m.get(b, 'minecraft:plains')

def legacy(id_, meta):
    s = LEGACY.get(f'{id_}:{meta}') or LEGACY.get(f'{id_}:0') or 'minecraft:air'
    m = re.match(r'([^\[]+)(?:\[(.*)\])?', s)
    nome, ps = m.group(1), m.group(2)
    props = dict(kv.split('=') for kv in ps.split(',')) if ps else {}
    return normalizar(nome, props)
