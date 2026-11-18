#!/usr/bin/env python3
# Gera o lote de missões da Guilda (100+ por rank) como migração SQL do painel.
# Uso: python3 tools/gerar-missoes.py <ids.json> <saida.sql>
#   ids.json = {"e": {id: nome}, "i": {id: nome}} tirado dos arquivos de idioma do servidor e dos mods
#   (só entra missão cujo alvo existe nessa lista; o que faltar é mostrado no fim).
import json, sys, re, math

IDS = json.load(open(sys.argv[1], encoding="utf-8"))
ENT, ITEM = IDS["e"], IDS["i"]

# nomes em português do que é do Minecraft puro (os mods usam o nome do próprio arquivo de idioma)
VN = {
 # criaturas
 "zombie":"Zumbi","skeleton":"Esqueleto","spider":"Aranha","creeper":"Creeper","slime":"Slime","drowned":"Afogado","zombie_villager":"Aldeão Zumbi",
 "husk":"Zumbi do Deserto","stray":"Esqueleto Vagante","cave_spider":"Aranha das Cavernas","silverfish":"Traça","bat":"Morcego","chicken":"Galinha",
 "cow":"Vaca","pig":"Porco","sheep":"Ovelha","rabbit":"Coelho","cod":"Bacalhau","salmon":"Salmão","squid":"Lula","pufferfish":"Baiacu","witch":"Bruxa",
 "enderman":"Enderman","phantom":"Phantom","pillager":"Saqueador","vindicator":"Vingador","guardian":"Guardião","zombified_piglin":"Piglin Zumbi",
 "piglin":"Piglin","magma_cube":"Cubo de Magma","blaze":"Blaze","ghast":"Ghast","hoglin":"Hoglin","wither_skeleton":"Esqueleto Wither","evoker":"Evocador",
 "ravager":"Devastador","piglin_brute":"Piglin Bárbaro","zoglin":"Zoglin","shulker":"Shulker","endermite":"Endermite","elder_guardian":"Guardião Ancião",
 "ender_dragon":"Dragão do Fim","wither":"Wither","warden":"Warden",
 # itens
 "wheat":"Trigo","carrot":"Cenoura","potato":"Batata","beetroot":"Beterraba","pumpkin":"Abóbora","melon_slice":"Fatia de Melancia","sugar_cane":"Cana-de-açúcar",
 "apple":"Maçã","egg":"Ovo","feather":"Pena","leather":"Couro","white_wool":"Lã Branca","string":"Linha","bone":"Osso","rotten_flesh":"Carne Podre",
 "gunpowder":"Pólvora","spider_eye":"Olho de Aranha","arrow":"Flecha","oak_log":"Tronco de Carvalho","birch_log":"Tronco de Bétula","spruce_log":"Tronco de Pinheiro",
 "cobblestone":"Pedregulho","coal":"Carvão","raw_copper":"Cobre Bruto","raw_iron":"Ferro Bruto","flint":"Sílex","clay_ball":"Bola de Argila","sand":"Areia",
 "gravel":"Cascalho","bread":"Pão","cooked_beef":"Bife","cooked_porkchop":"Costeleta de Porco Assada","cooked_chicken":"Frango Assado","cooked_mutton":"Carneiro Assado",
 "torch":"Tocha","stick":"Graveto","oak_planks":"Tábuas de Carvalho","charcoal":"Carvão Vegetal","kelp":"Alga","cactus":"Cacto","dandelion":"Dente-de-leão",
 "poppy":"Papoula","brown_mushroom":"Cogumelo Marrom","red_mushroom":"Cogumelo Vermelho","wheat_seeds":"Sementes de Trigo","ink_sac":"Bolsa de Tinta",
 "slime_ball":"Bola de Slime","bone_meal":"Farinha de Osso","paper":"Papel","glass":"Vidro","brick":"Tijolo","honeycomb":"Favo de Mel","sweet_berries":"Bagas Doces",
 "cocoa_beans":"Sementes de Cacau","stone":"Pedra","jungle_log":"Tronco da Selva","acacia_log":"Tronco de Acácia","dark_oak_log":"Tronco de Carvalho Escuro",
 "baked_potato":"Batata Assada","cooked_cod":"Bacalhau Assado","cooked_salmon":"Salmão Assado","mushroom_stew":"Ensopado de Cogumelos",
 "iron_ingot":"Barra de Ferro","gold_ingot":"Barra de Ouro","copper_ingot":"Barra de Cobre","redstone":"Pó de Redstone","lapis_lazuli":"Lápis-lazúli",
 "raw_gold":"Ouro Bruto","ender_pearl":"Pérola do Fim","phantom_membrane":"Membrana de Phantom","emerald":"Esmeralda","amethyst_shard":"Fragmento de Ametista",
 "obsidian":"Obsidiana","glass_bottle":"Frasco de Vidro","book":"Livro","bookshelf":"Estante de Livros","golden_carrot":"Cenoura Dourada","pumpkin_pie":"Torta de Abóbora",
 "cookie":"Biscoito","hay_block":"Fardo de Feno","iron_bars":"Grades de Ferro","rail":"Trilho","lantern":"Lanterna","chain":"Corrente","bricks":"Tijolos",
 "stone_bricks":"Tijolos de Pedra","smooth_stone":"Pedra Lisa","terracotta":"Terracota","glass_pane":"Painel de Vidro","honey_bottle":"Frasco de Mel",
 "glow_ink_sac":"Bolsa de Tinta Brilhante","prismarine_shard":"Fragmento de Prismarinho","nautilus_shell":"Concha de Náutilo","tropical_fish":"Peixe Tropical",
 "dried_kelp_block":"Bloco de Alga Seca","scute":"Escama de Tartaruga","rabbit_hide":"Pele de Coelho","rabbit_foot":"Pé de Coelho","bone_block":"Bloco de Ossos",
 "iron_nugget":"Pepita de Ferro","gold_nugget":"Pepita de Ouro","tnt":"Dinamite","moss_block":"Bloco de Musgo","dripstone_block":"Bloco de Espeleotema",
 "pointed_dripstone":"Espeleotema Pontiagudo","glow_berries":"Bagas Brilhantes","copper_block":"Bloco de Cobre","calcite":"Calcita","tuff":"Tufo","deepslate":"Ardósia",
 "cobbled_deepslate":"Pedregulho de Ardósia","andesite":"Andesito","diorite":"Diorito","granite":"Granito","ice":"Gelo","packed_ice":"Gelo Compactado",
 "lily_pad":"Vitória-régia","vine":"Trepadeira","bamboo":"Bambu","diamond":"Diamante","redstone_block":"Bloco de Redstone","lapis_block":"Bloco de Lápis-lazúli",
 "blaze_rod":"Vara de Blaze","nether_wart":"Fungo do Nether","glowstone_dust":"Pó de Pedra Luminosa","quartz":"Quartzo do Nether","magma_cream":"Creme de Magma",
 "ghast_tear":"Lágrima de Ghast","netherrack":"Netherrack","soul_sand":"Areia das Almas","nether_brick":"Tijolo do Nether","crimson_stem":"Tronco Carmesim",
 "warped_stem":"Tronco Distorcido","basalt":"Basalto","blackstone":"Pedra-negra","gold_block":"Bloco de Ouro","experience_bottle":"Frasco de Experiência",
 "golden_apple":"Maçã Dourada","iron_block":"Bloco de Ferro","powered_rail":"Trilho Elétrico","hopper":"Funil","piston":"Pistão","slime_block":"Bloco de Slime",
 "honey_block":"Bloco de Mel","amethyst_block":"Bloco de Ametista","lightning_rod":"Para-raios","glistering_melon_slice":"Fatia de Melancia Reluzente",
 "fermented_spider_eye":"Olho de Aranha Fermentado","sea_lantern":"Lanterna do Mar","prismarine_crystals":"Cristais de Prismarinho","sponge":"Esponja",
 "name_tag":"Etiqueta","shroomlight":"Luz-fungo","weeping_vines":"Trepadeiras Chorosas","twisting_vines":"Trepadeiras Retorcidas","crying_obsidian":"Obsidiana Chorosa",
 "gilded_blackstone":"Pedra-negra Dourada","soul_soil":"Solo das Almas","netherite_scrap":"Fragmento de Netherita","ancient_debris":"Detritos Ancestrais",
 "wither_skeleton_skull":"Crânio de Esqueleto Wither","ender_eye":"Olho do Fim","end_stone":"Pedra do Fim","chorus_fruit":"Fruta do Coro",
 "popped_chorus_fruit":"Fruta do Coro Estourada","purpur_block":"Bloco de Púrpura","end_rod":"Bastão do Fim","shulker_shell":"Casca de Shulker",
 "dragon_breath":"Bafo do Dragão","emerald_block":"Bloco de Esmeralda","diamond_block":"Bloco de Diamante","quartz_block":"Bloco de Quartzo","glowstone":"Pedra Luminosa",
 "magma_block":"Bloco de Magma","prismarine":"Prismarinho","dark_prismarine":"Prismarinho Escuro","totem_of_undying":"Totem da Imortalidade",
 "respawn_anchor":"Âncora de Renascimento","lodestone":"Magnetita","coal_block":"Bloco de Carvão","netherite_ingot":"Barra de Netherita","end_crystal":"Cristal do Fim",
 "nether_star":"Estrela do Nether","echo_shard":"Fragmento de Eco","sculk":"Sculk","sculk_catalyst":"Catalisador de Sculk","sculk_sensor":"Sensor de Sculk",
 "enchanted_golden_apple":"Maçã Dourada Encantada","chorus_flower":"Flor do Coro","purpur_pillar":"Pilar de Púrpura","end_stone_bricks":"Tijolos de Pedra do Fim",
 "beacon":"Sinalizador","conduit":"Conduíte","heart_of_the_sea":"Coração do Mar","dragon_head":"Cabeça de Dragão","netherite_block":"Bloco de Netherita",
}

def K(s):  # "id|qtd|peso" separados por espaço
    out = []
    for t in s.split():
        p = t.split("|")
        out.append((p[0] if ":" in p[0] else "minecraft:" + p[0], int(p[1]), float(p[2]) if len(p) > 2 and p[2] else 1.0))
    return out

RANKS = "FEDCBAS"
NIVEL = [1, 10, 20, 35, 50, 70, 90]
MEIO = [5, 15, 27, 42, 60, 80, 95]          # nível típico de quem está no rank (para o XP da recompensa)
MOEDA = [15, 30, 50, 80, 120, 170, 230]     # cobre de uma missão comum do rank

CACA = [None] * 7
COLETA = [None] * 7

CACA[0] = K("""zombie|8 skeleton|8 spider|8 creeper|5 slime|6 drowned|6 zombie_villager|4 husk|6 stray|6 cave_spider|6 silverfish|5 bat|5 chicken|6 cow|6 pig|6 sheep|6
 rabbit|5 cod|6 salmon|6 squid|5 pufferfish|3 naturalist:boar|5 naturalist:rat|6 naturalist:snake|5 naturalist:duck|5 naturalist:deer|4 alexsmobs:fly|6
 alexsmobs:cockroach|6 alexsmobs:crow|5 alexsmobs:rattlesnake|4 born_in_chaos_v1:decaying_zombie|6 born_in_chaos_v1:baby_skeleton|6 born_in_chaos_v1:baby_spider|6
 born_in_chaos_v1:maggot|6 born_in_chaos_v1:barrel_zombie|4 born_in_chaos_v1:decrepit_skeleton|5""")
COLETA[0] = K("""wheat|16 carrot|16 potato|16 beetroot|12 pumpkin|6 melon_slice|16 sugar_cane|16 apple|6 egg|8 feather|8 leather|6 white_wool|10 string|8 bone|10
 rotten_flesh|12 gunpowder|5 spider_eye|4 arrow|16 oak_log|24 birch_log|24 spruce_log|24 cobblestone|48 coal|12 raw_copper|12 raw_iron|6 flint|8 clay_ball|12 sand|32
 gravel|24 bread|8 cooked_beef|6 cooked_porkchop|6 cooked_chicken|6 cooked_mutton|6 cod|6 salmon|6 torch|32 stick|32 oak_planks|48 charcoal|12 kelp|16 cactus|8
 dandelion|8 poppy|8 brown_mushroom|6 red_mushroom|6 wheat_seeds|24 ink_sac|4 slime_ball|4 bone_meal|16 paper|12 glass|16 brick|16 honeycomb|3 sweet_berries|12
 cocoa_beans|8 stone|32 jungle_log|24 acacia_log|24 dark_oak_log|24 baked_potato|12 cooked_cod|8 cooked_salmon|8 mushroom_stew|1""")

CACA[1] = K("""zombie|20 skeleton|20 spider|16 creeper|12 witch|4|1.3 enderman|3|1.3 phantom|5 pillager|8 vindicator|4|1.3 drowned|15 husk|15 stray|15 cave_spider|14 slime|14
 guardian|5|1.3 zombie_villager|10 silverfish|12 naturalist:bear|3 naturalist:alligator|3 naturalist:lion|3 naturalist:desert_scorpion|5 naturalist:jungle_scorpion|5
 naturalist:vulture|4 alexsmobs:crocodile|3 alexsmobs:grizzly_bear|3 alexsmobs:tiger|2|1.3 alexsmobs:komodo_dragon|3 alexsmobs:anaconda|2 alexsmobs:hammerhead_shark|3
 alexsmobs:rocky_roller|4 alexsmobs:skelewag|4 alexsmobs:guster|4 born_in_chaos_v1:bone_imp|6 born_in_chaos_v1:dread_hound|5 born_in_chaos_v1:skeleton_thrasher|4|1.3
 born_in_chaos_v1:restless_spirit|5 born_in_chaos_v1:dark_vortex|3 born_in_chaos_v1:firelight|5 born_in_chaos_v1:phantom_creeper|4 born_in_chaos_v1:skeleton_demoman|4
 born_in_chaos_v1:door_knight|3|1.3 born_in_chaos_v1:bonescaller|3|1.3 born_in_chaos_v1:swarmer|6 born_in_chaos_v1:thornshell_crab|5 born_in_chaos_v1:corpse_fly|6
 mowziesmobs:foliaath|4 mowziesmobs:umvuthana|5""")
COLETA[1] = K("""iron_ingot|16 gold_ingot|8 copper_ingot|24 redstone|24 lapis_lazuli|16 coal|32 raw_gold|8 ender_pearl|3|1.3 gunpowder|12 bone|24 string|20 spider_eye|10
 slime_ball|10 leather|16 phantom_membrane|3|1.3 emerald|4 amethyst_shard|10 obsidian|6 glass_bottle|12 book|8 bookshelf|4 paper|32 golden_carrot|8 pumpkin_pie|8 cookie|24
 hay_block|8 iron_bars|16 rail|16 lantern|8 chain|12 bricks|16 stone_bricks|48 smooth_stone|32 terracotta|24 glass_pane|32 honey_bottle|4 glow_ink_sac|4
 prismarine_shard|8 nautilus_shell|1|1.3 tropical_fish|5 pufferfish|3 dried_kelp_block|8 scute|2|1.3 rabbit_hide|8 rabbit_foot|1|1.3 arrow|48 bone_block|8 iron_nugget|32
 gold_nugget|24 tnt|4 moss_block|16 dripstone_block|16 pointed_dripstone|12 glow_berries|12 copper_block|6 calcite|16 tuff|32 deepslate|48 cobbled_deepslate|64
 andesite|32 diorite|32 granite|32 ice|16 packed_ice|8 lily_pad|8 vine|12 bamboo|32 naturalist:venison|6 alexsmobs:bear_fur|2 alexsmobs:shark_tooth|4
 alexsmobs:crocodile_scute|2 alexsmobs:rattlesnake_rattle|3 alexsmobs:banana|8 alexsmobs:maggot|8 alexsmobs:komodo_spit|2""")

CACA[2] = K("""twilightforest:kobold|10 twilightforest:redcap|10 twilightforest:redcap_sapper|5 twilightforest:hedge_spider|8 twilightforest:swarm_spider|15
 twilightforest:skeleton_druid|6 twilightforest:wraith|6 twilightforest:mist_wolf|6 twilightforest:hostile_wolf|8 twilightforest:king_spider|3|1.3
 twilightforest:pinch_beetle|6 twilightforest:fire_beetle|6 twilightforest:slime_beetle|6 twilightforest:mosquito_swarm|6 twilightforest:maze_slime|8
 twilightforest:minotaur|6 twilightforest:death_tome|5 twilightforest:helmet_crab|6 twilightforest:towerwood_borer|6 twilightforest:naga|1|3 twilightforest:lich|1|3.5
 twilightforest:minoshroom|1|3 undergarden:rotwalker|10 undergarden:rotling|12 undergarden:rotbeast|3|1.4 undergarden:brute|6 undergarden:nargoyle|5 undergarden:muncher|5
 undergarden:sploogie|5 undergarden:scintling|8 undergarden:forgotten|4|1.3 zombified_piglin|15 piglin|10 magma_cube|10 blaze|8 ghast|4|1.3 hoglin|6 wither_skeleton|6|1.3
 witch|8 enderman|8 pillager|16 vindicator|8 evoker|2|1.5 ravager|2|1.6 alexsmobs:crimson_mosquito|6 alexsmobs:bone_serpent|3|1.4 alexsmobs:dropbear|4
 alexsmobs:soul_vulture|4 alexsmobs:straddler|4 iceandfire:cockatrice|3|1.3 iceandfire:stymphalian_bird|6 iceandfire:myrmex_worker|8 iceandfire:ghost|3
 mutantmonsters:mutant_zombie|1|2.5 mutantmonsters:mutant_skeleton|1|2.5 mutantmonsters:mutant_creeper|1|2.5 born_in_chaos_v1:fallen_chaos_knight|2|1.6
 born_in_chaos_v1:nightmare_stalker|2|1.6 born_in_chaos_v1:mother_spider|1|2.5 born_in_chaos_v1:dire_hound_leader|1|2.5""")
COLETA[2] = K("""diamond|4|1.3 gold_ingot|24 iron_ingot|48 redstone_block|8 lapis_block|4 emerald|10 obsidian|16 blaze_rod|6 nether_wart|16 glowstone_dust|24 quartz|32
 magma_cream|8 ghast_tear|2|1.4 netherrack|64 soul_sand|32 nether_brick|32 crimson_stem|24 warped_stem|24 basalt|48 blackstone|48 gold_block|2 ender_pearl|8
 experience_bottle|8 golden_apple|2|1.3 bookshelf|12 iron_block|6 tnt|12 powered_rail|16 hopper|4 piston|8 slime_block|4 honey_block|4 amethyst_block|8 lightning_rod|4
 copper_block|16 cooked_salmon|32 golden_carrot|24 glistering_melon_slice|8 fermented_spider_eye|6 sea_lantern|8 prismarine_crystals|12 sponge|2|1.3 name_tag|2|1.3
 shroomlight|8 weeping_vines|16 twisting_vines|16 crying_obsidian|6 gilded_blackstone|4 soul_soil|32 bone_block|24
 twilightforest:naga_scale|4|1.5 twilightforest:raven_feather|6 twilightforest:torchberries|12 twilightforest:liveroot|8 twilightforest:ironwood_ingot|6
 twilightforest:steeleaf_ingot|6 twilightforest:armor_shard|8 twilightforest:carminite|4|1.3 twilightforest:borer_essence|4 twilightforest:experiment_115|4
 twilightforest:twilight_oak_log|32 twilightforest:canopy_log|32 undergarden:cloggrum_ingot|12 undergarden:froststeel_ingot|6 undergarden:utherium_crystal|4|1.3
 undergarden:regalium_crystal|4|1.3 undergarden:depthrock|64 undergarden:smogstem_log|32 undergarden:wigglewood_log|32 undergarden:underbeans|16
 undergarden:blisterberry|12 undergarden:gloomgourd|6 undergarden:raw_dweller_meat|8 alexsmobs:bone_serpent_tooth|3|1.3 alexsmobs:blood_sac|6 alexsmobs:dropbear_claw|3|1.3
 alexsmobs:moose_antler|2 alexsmobs:kangaroo_hide|6 alexsmobs:lobster_tail|6 alexsmobs:gazelle_horn|4 iceandfire:silver_ingot|12 iceandfire:witherbone|6
 iceandfire:cockatrice_eye|2|1.4 iceandfire:stymphalian_bird_feather|8 iceandfire:myrmex_desert_resin|8 iceandfire:myrmex_jungle_resin|8 iceandfire:pixie_dust|6
 iceandfire:ectoplasm|4 iceandfire:shiny_scales|6""")

CACA[3] = K("""wither_skeleton|15 blaze|20 ghast|10 piglin_brute|5|1.4 zoglin|5 hoglin|14 magma_cube|20 enderman|20 shulker|8|1.3 endermite|6 elder_guardian|1|3 guardian|15
 ender_dragon|1|5 aether:cockatrice|8 aether:zephyr|6 aether:aechor_plant|8 aether:blue_swet|8 aether:golden_swet|8 aether:sentry|10 aether:mimic|4|1.3
 aether:whirlwind|4 aether:evil_whirlwind|3|1.3 aether:valkyrie|4|1.5 aether:slider|1|3.5 aether:valkyrie_queen|1|4 blue_skies:frost_spirit|8
 blue_skies:armored_frost_spirit|5|1.3 blue_skies:polargeist|4|1.3 blue_skies:crynocerous|5 blue_skies:diophyde_prowler|6 blue_skies:shade_monitor|5 blue_skies:sliv|5
 blue_skies:stonelet|8 blue_skies:spewter|4 blue_skies:summoner|1|4 twilightforest:carminite_golem|4|1.3 twilightforest:carminite_ghastling|8
 twilightforest:carminite_ghastguard|3|1.5 twilightforest:winter_wolf|8 twilightforest:yeti|6 twilightforest:troll|4|1.4 twilightforest:snow_guardian|8
 twilightforest:giant_miner|2|1.6 twilightforest:armored_giant|2|1.6 twilightforest:hydra|1|4 twilightforest:ur_ghast|1|4.5 twilightforest:alpha_yeti|1|4
 iceandfire:myrmex_soldier|8 iceandfire:myrmex_sentinel|4|1.3 iceandfire:troll|2|1.6 iceandfire:siren|2|1.5 iceandfire:deathworm|3|1.5 iceandfire:amphithere|2|1.4
 iceandfire:dread_thrall|8 iceandfire:dread_ghoul|6 iceandfire:dread_beast|4 iceandfire:dread_knight|3|1.4 iceandfire:dread_scuttler|4 iceandfire:dread_lich|1|3
 mowziesmobs:ferrous_wroughtnaut|1|3.5 mowziesmobs:umvuthi|1|4 mowziesmobs:naga|3|1.4 mutantmonsters:mutant_enderman|1|3 undergarden:forgotten_guardian|1|3
 alexsmobs:enderiophage|4|1.3 alexsmobs:mimicube|4|1.3 alexsmobs:warped_mosco|1|3""")
COLETA[3] = K("""diamond|12 netherite_scrap|2|1.5 ancient_debris|2|1.5 blaze_rod|20 ghast_tear|6 wither_skeleton_skull|1|1.6 ender_pearl|24 ender_eye|8 end_stone|64
 chorus_fruit|24 popped_chorus_fruit|16 purpur_block|48 end_rod|12 shulker_shell|4|1.4 dragon_breath|4|1.4 emerald_block|3 diamond_block|1|1.4 obsidian|48
 crying_obsidian|16 quartz_block|32 glowstone|32 magma_block|32 golden_apple|6 experience_bottle|24 prismarine|48 dark_prismarine|24 sea_lantern|24 sponge|6
 totem_of_undying|1|1.6 respawn_anchor|1|1.4 lodestone|1|1.5 honey_block|12 slime_block|12 tnt|32 redstone_block|24 gold_block|8 iron_block|24 lapis_block|12 coal_block|24
 aether:ambrosium_shard|32 aether:zanite_gemstone|12 aether:skyroot_log|48 aether:holystone|64 aether:golden_amber|8 aether:aechor_petal|8 aether:swet_ball|12
 aether:blue_berry|16 aether:enchanted_berry|8 aether:white_apple|4 aether:icestone|16 aether:aerogel|8 aether:quicksoil|32 aether:enchanted_gravitite|2|1.5
 aether:golden_oak_log|16 aether:cold_aercloud|16 aether:blue_aercloud|8 blue_skies:pyrope_gem|12 blue_skies:aquite|12 blue_skies:diopside_gem|8
 blue_skies:falsite_ingot|8 blue_skies:ventium_ingot|6 blue_skies:moonstone_shard|16 blue_skies:bluebright_log|48 blue_skies:frostbright_log|48
 blue_skies:starlit_log|32 blue_skies:turquoise_stone|64 twilightforest:fiery_blood|2|1.5 twilightforest:fiery_ingot|4|1.4 twilightforest:knightmetal_ingot|6
 twilightforest:arctic_fur|8 twilightforest:alpha_yeti_fur|4|1.5 twilightforest:ice_bomb|8 twilightforest:carminite|12 twilightforest:hydra_chop|4|1.4
 twilightforest:transformation_powder|8 twilightforest:steeleaf_ingot|16 twilightforest:ironwood_ingot|16 twilightforest:fiery_tears|2|1.5
 iceandfire:troll_tusk|2|1.4 iceandfire:siren_tear|1|1.5 iceandfire:deathworm_chitin_yellow|6 iceandfire:amphithere_feather|4 iceandfire:dread_shard|12
 iceandfire:dragonbone|8|1.4 alexsmobs:ender_residue|6 alexsmobs:mimicream|4 alexsmobs:warped_muscle|1|1.5 alexsmobs:hemolymph_sac|6 alexsmobs:straddlite|2|1.4""")

CACA[4] = K("""blue_skies:crogre|5|1.3 blue_skies:emberback|8 blue_skies:nyctofly|8 blue_skies:infested_swarmer|12 blue_skies:venom_spider|8 blue_skies:nested_spider|10
 blue_skies:alchemist|1|4 blue_skies:starlit_crusher|1|4 blue_skies:arachnarch|1|4.5 deeperdarker:shattered|10 deeperdarker:sculk_centipede|8 deeperdarker:sculk_leech|12
 deeperdarker:sculk_snapper|6 deeperdarker:shriek_worm|3|1.5 deeperdarker:sludge|6 deeperdarker:angler_fish|6 deeperdarker:stalker|1|4.5 cataclysm:deepling|10
 cataclysm:deepling_brute|4|1.4 cataclysm:deepling_angler|6 cataclysm:deepling_priest|3|1.4 cataclysm:deepling_warlock|3|1.4 cataclysm:coralssus|2|1.8 cataclysm:lionfish|6
 cataclysm:koboleton|8 cataclysm:kobolediator|1|3.5 cataclysm:wadjet|1|3.5 cataclysm:draugr|10 cataclysm:elite_draugr|5|1.3 cataclysm:royal_draugr|3|1.5
 cataclysm:aptrgangr|1|3.5 cataclysm:endermaptera|8 cataclysm:ender_golem|2|2 cataclysm:the_watcher|6 cataclysm:amethyst_crab|1|3 alexscaves:teletor|5
 alexscaves:magnetron|3|1.4 alexscaves:boundroid|5 alexscaves:notor|6 alexscaves:ferrouslime|4 alexscaves:vallumraptor|6 alexscaves:subterranodon|4
 alexscaves:grottoceratops|2|1.6 alexscaves:tremorsaurus|1|3.5 alexscaves:nucleeper|4|1.3 alexscaves:gammaroach|10 alexscaves:raycat|3 alexscaves:radgill|6
 alexscaves:brainiac|3|1.5 alexscaves:gingerbread_man|12 alexscaves:gummy_bear|4 alexscaves:caniac|4 alexscaves:licowitch|3|1.4 alexscaves:caramel_cube|6
 alexscaves:gumbeeper|5 mowziesmobs:frostmaw|1|4 iceandfire:cyclops|1|4 iceandfire:gorgon|1|3.5 iceandfire:myrmex_royal|2|1.6 iceandfire:myrmex_queen|1|4 enderman|40
 shulker|16 wither_skeleton|30 piglin_brute|10 evoker|6|1.3 ravager|5|1.4 ender_dragon|1|4 wither|1|5""")
COLETA[4] = K("""netherite_ingot|1|1.6 netherite_scrap|6 ancient_debris|6 diamond|32 diamond_block|3 emerald_block|8 shulker_shell|12 dragon_breath|12 end_crystal|2|1.4
 nether_star|1|2.5 wither_skeleton_skull|3|1.5 echo_shard|4|1.5 sculk|32 sculk_catalyst|2|1.4 sculk_sensor|4 enchanted_golden_apple|1|2 totem_of_undying|2|1.6
 golden_apple|12 experience_bottle|48 ender_eye|16 chorus_flower|8 purpur_pillar|32 end_stone_bricks|64 obsidian|64 crying_obsidian|32 respawn_anchor|2 lodestone|2|1.5
 beacon|1|2.5 conduit|1|2 heart_of_the_sea|1|1.6 ghast_tear|12 blaze_rod|40 magma_cream|32 phantom_membrane|16 rabbit_foot|6 scute|8 nautilus_shell|6
 blue_skies:charoite|12 blue_skies:horizonite_ingot|8 blue_skies:diopside_gem|16 blue_skies:lunar_log|48 blue_skies:dusk_log|48 blue_skies:maple_log|48
 deeperdarker:soul_dust|16 deeperdarker:soul_crystal|4|1.4 deeperdarker:sculk_bone|12 deeperdarker:grime_ball|16 deeperdarker:bloom_berries|16 deeperdarker:echo_log|48
 deeperdarker:gloomslate|64 deeperdarker:sculk_stone|64 deeperdarker:warden_carapace|1|2 deeperdarker:resonarium|4|1.5 deeperdarker:sculk_grime|32
 cataclysm:koboleton_bone|8 cataclysm:amethyst_crab_shell|1|2 cataclysm:amethyst_crab_meat|4|1.4 cataclysm:lionfish|6 cataclysm:crystallized_coral_fragments|8
 cataclysm:ancient_metal_ingot|4|1.5 cataclysm:ancient_metal_nugget|16 cataclysm:black_steel_ingot|8 alexscaves:scarlet_neodymium_ingot|12
 alexscaves:azure_neodymium_ingot|12 alexscaves:uranium|8 alexscaves:sulfur_dust|16 alexscaves:dinosaur_nugget|8 alexscaves:tough_hide|6 alexscaves:heavy_bone|4
 alexscaves:pine_nuts|16 alexscaves:radgill|6 alexscaves:licoroot|8 alexscaves:gingerbread_crumbs|16 alexscaves:caramel|8 alexscaves:sharpened_candy_cane|6
 alexscaves:ferrouslime_ball|6 alexscaves:notor_gizmo|4 alexscaves:telecore|2|1.4 alexscaves:guano|16 alexscaves:fissile_core|1|1.8 alexscaves:toxic_paste|8
 alexscaves:raw_scarlet_neodymium|16 alexscaves:raw_azure_neodymium|16 alexscaves:tectonic_shard|1|2 iceandfire:cyclops_eye|1|2 iceandfire:myrmex_desert_chitin|12
 iceandfire:myrmex_jungle_chitin|12 iceandfire:dragonbone|24 iceandfire:fire_lily|4 iceandfire:frost_lily|4 iceandfire:lightning_lily|4 iceandfire:silver_ingot|32""")

CACA[5] = K("""alexscaves:deep_one|8 alexscaves:deep_one_knight|5|1.4 alexscaves:deep_one_mage|4|1.4 alexscaves:hullbreaker|1|4 alexscaves:mine_guardian|5
 alexscaves:gossamer_worm|6 alexscaves:lanternfish|10 alexscaves:tripodfish|6 alexscaves:underzealot|10 alexscaves:watcher|5|1.3 alexscaves:corrodent|6 alexscaves:vesper|8
 alexscaves:forsaken|1|4.5 alexscaves:gloomoth|6 alexscaves:gum_worm|1|4 alexscaves:tremorsaurus|2|3 cataclysm:ignited_revenant|4|1.5 cataclysm:ignited_berserker|4|1.5
 cataclysm:the_prowler|3|1.8 cataclysm:royal_draugr|6 cataclysm:elite_draugr|10 cataclysm:coralssus|4 cataclysm:ender_golem|4 cataclysm:kobolediator|2|2.5
 cataclysm:wadjet|2|2.5 cataclysm:aptrgangr|2|2.5 cataclysm:amethyst_crab|2|2.5 cataclysm:deepling_warlock|6 cataclysm:deepling_priest|6
 cataclysm:netherite_monstrosity|1|5 cataclysm:ender_guardian|1|5 cataclysm:the_harbinger|1|5 cataclysm:ancient_remnant|1|5 iceandfire:fire_dragon|1|6
 iceandfire:ice_dragon|1|6 iceandfire:lightning_dragon|1|6 iceandfire:sea_serpent|1|5 iceandfire:hydra|1|5 iceandfire:cyclops|2|2.5 iceandfire:gorgon|2|2.5
 iceandfire:dread_knight|8 iceandfire:dread_lich|3|2 iceandfire:myrmex_queen|2|2.5 warden|1|6 wither|1|4.5 ender_dragon|1|3.5 elder_guardian|3|2.5 shulker|30 enderman|60
 wither_skeleton|50 piglin_brute|16 ravager|8 evoker|10 alexsmobs:farseer|1|4 alexsmobs:void_worm|1|6 deeperdarker:stalker|1|4 deeperdarker:shattered|25
 deeperdarker:sculk_centipede|16 deeperdarker:shriek_worm|6 mutantmonsters:mutant_enderman|2|2.5 mutantmonsters:mutant_creeper|3|2 mutantmonsters:mutant_zombie|3|2
 mutantmonsters:mutant_skeleton|3|2 blue_skies:arachnarch|1|4 blue_skies:starlit_crusher|1|3.5 blue_skies:alchemist|1|3.5 blue_skies:summoner|1|3
 twilightforest:snow_queen|1|4.5 twilightforest:ur_ghast|1|4 twilightforest:hydra|1|4 mowziesmobs:frostmaw|2|3 mowziesmobs:ferrous_wroughtnaut|2|3 mowziesmobs:umvuthi|1|3.5
 born_in_chaos_v1:lifestealer|1|4 born_in_chaos_v1:supreme_bonescaller|1|4 born_in_chaos_v1:lord_pumpkinhead|1|4 born_in_chaos_v1:missioner|1|3""")
COLETA[5] = K("""netherite_ingot|3|1.5 nether_star|2|2.5 enchanted_golden_apple|2|2 totem_of_undying|4|1.6 echo_shard|16 shulker_shell|24 dragon_breath|24 end_crystal|6
 beacon|2|2.5 conduit|2|2 diamond_block|8 emerald_block|16 ancient_debris|16 wither_skeleton_skull|6|1.5 sculk_catalyst|6 heart_of_the_sea|2|1.6 dragon_head|1|2.5
 experience_bottle|64 netherite_scrap|16 golden_apple|24 obsidian|64 end_stone_bricks|64 ender_eye|32 ghast_tear|24
 iceandfire:sea_serpent_scales_blue|8|1.5 iceandfire:sea_serpent_scales_red|8|1.5 iceandfire:deathworm_chitin_red|8 iceandfire:fire_dragon_flesh|6 iceandfire:ice_dragon_flesh|6
 iceandfire:lightning_dragon_flesh|6 iceandfire:fire_dragon_blood|3|1.5 iceandfire:ice_dragon_blood|3|1.5 iceandfire:lightning_dragon_blood|3|1.5
 iceandfire:fire_dragon_heart|1|3 iceandfire:ice_dragon_heart|1|3 iceandfire:lightning_dragon_heart|1|3 iceandfire:dragonsteel_fire_ingot|2|2.5
 iceandfire:dragonsteel_ice_ingot|2|2.5 iceandfire:dragonsteel_lightning_ingot|2|2.5 iceandfire:sea_serpent_fang|4|1.5 iceandfire:hydra_fang|4|1.5
 iceandfire:dragonbone|48 cataclysm:ignitium_ingot|2|3 cataclysm:witherite_ingot|2|3 cataclysm:enderite_ingot|2|3 cataclysm:cursium_ingot|2|3
 cataclysm:ancient_metal_ingot|12 cataclysm:void_core|1|3 cataclysm:lava_power_cell|2|2 cataclysm:abyssal_sacrifice|1|2.5 cataclysm:monstrous_horn|1|3
 cataclysm:burning_ashes|2|2 cataclysm:black_steel_ingot|24 cataclysm:essence_of_the_storm|2|2.5 cataclysm:chitin_claw|4|1.5 cataclysm:lacrima|6|1.5
 alexscaves:pearl|4|1.5 alexscaves:immortal_embryo|1|3 alexscaves:dark_tatters|8 alexscaves:corrodent_teeth|8 alexscaves:bioluminesscence|12 alexscaves:lanternfish|8
 alexscaves:tripodfish|6 alexscaves:heavyweight|4 alexscaves:uranium|32 alexscaves:tectonic_shard|4|1.6 alexscaves:pure_darkness|1|3 alexscaves:occult_gem|4|1.5
 alexscaves:shadow_silk|8 alexscaves:thornwood_log|32 alexscaves:pewen_log|48 alexscaves:scarlet_neodymium_ingot|32 alexscaves:azure_neodymium_ingot|32
 alexscaves:fissile_core|3|1.6 alexscaves:telecore|6 alexscaves:tough_hide|16 alexscaves:heavy_bone|12 deeperdarker:warden_carapace|4|1.6
 deeperdarker:heart_of_the_deep|1|3 deeperdarker:soul_crystal|12 deeperdarker:reinforced_echo_shard|2|2.5 deeperdarker:resonarium|12 deeperdarker:soul_dust|48
 deeperdarker:sculk_bone|32 alexsmobs:void_worm_mandible|2|2 alexsmobs:void_worm_eye|1|3 alexsmobs:farseer_arm|1|3 alexsmobs:ender_residue|16
 alexsmobs:elastic_tendon|4|1.5 alexsmobs:mimicream|12 blue_skies:horizonite_ingot|24 blue_skies:charoite|32 blue_skies:falsite_ingot|24 blue_skies:ventium_ingot|24
 twilightforest:fiery_ingot|16 twilightforest:knightmetal_ingot|24 twilightforest:fiery_blood|8 twilightforest:alpha_yeti_fur|12 twilightforest:hydra_chop|16
 twilightforest:carminite|32 aether:enchanted_gravitite|8 aether:zanite_gemstone|48 aether:golden_amber|32 undergarden:utherium_crystal|16 undergarden:regalium_crystal|16
 undergarden:froststeel_ingot|32""")

CACA[6] = K("""cataclysm:ignis|1|8 cataclysm:the_leviathan|1|8 cataclysm:maledictus|1|8 cataclysm:scylla|1|8 cataclysm:netherite_monstrosity|1|6 cataclysm:ender_guardian|1|6
 cataclysm:the_harbinger|1|6 cataclysm:ancient_remnant|1|6 cataclysm:the_prowler|6 cataclysm:ignited_revenant|10 cataclysm:ignited_berserker|10 cataclysm:royal_draugr|12
 cataclysm:kobolediator|3|2 cataclysm:wadjet|3|2 cataclysm:aptrgangr|3|2 cataclysm:coralssus|8 cataclysm:ender_golem|8 alexscaves:luxtructosaurus|1|8
 alexscaves:tremorzilla|1|9 alexscaves:forsaken|2|4 alexscaves:hullbreaker|3|3 alexscaves:deep_one_mage|10 alexscaves:deep_one_knight|12 alexscaves:tremorsaurus|4|2.5
 alexscaves:gum_worm|2|3 alexscaves:watcher|12 alexscaves:underzealot|25 alexscaves:nucleeper|12 alexscaves:brainiac|8 alexscaves:magnetron|8 iceandfire:fire_dragon|2|6
 iceandfire:ice_dragon|2|6 iceandfire:lightning_dragon|2|6 iceandfire:sea_serpent|2|5 iceandfire:hydra|2|5 iceandfire:cyclops|4|2.5 iceandfire:gorgon|4|2.5
 iceandfire:myrmex_queen|3|2.5 iceandfire:dread_lich|6|2 iceandfire:dread_knight|16 warden|2|6 wither|2|5 ender_dragon|1|4 elder_guardian|3|2 shulker|50 enderman|100
 wither_skeleton|80 piglin_brute|30 ravager|15 evoker|16 alexsmobs:void_worm|1|7 alexsmobs:farseer|2|4 alexsmobs:warped_mosco|2|3 deeperdarker:stalker|2|4
 deeperdarker:shattered|40 deeperdarker:shriek_worm|10 deeperdarker:sculk_centipede|30 mutantmonsters:mutant_enderman|4|2 mutantmonsters:mutant_creeper|5|2
 mutantmonsters:mutant_zombie|5|2 mutantmonsters:mutant_skeleton|5|2 blue_skies:arachnarch|2|3.5 blue_skies:starlit_crusher|2|3.5 blue_skies:alchemist|2|3.5
 blue_skies:summoner|2|3 twilightforest:snow_queen|1|4 twilightforest:ur_ghast|2|3.5 twilightforest:hydra|2|3.5 twilightforest:lich|3|2.5 twilightforest:naga|3|2.5
 twilightforest:alpha_yeti|2|3 mowziesmobs:frostmaw|3|3 mowziesmobs:ferrous_wroughtnaut|3|3 mowziesmobs:umvuthi|2|3.5 aether:sun_spirit|1|6 aether:valkyrie_queen|2|3.5
 aether:slider|2|3 born_in_chaos_v1:lifestealer|2|3.5 born_in_chaos_v1:supreme_bonescaller|2|3.5 born_in_chaos_v1:lord_pumpkinhead|2|3.5
 undergarden:forgotten_guardian|3|2.5""")
# Rank S entrega o que o Rank A entrega, em quantidade maior, mais algumas peças só dele
COLETA[6] = [(i, max(q + 1, int(round(q * 2.5))), w) for i, q, w in COLETA[5]] + K("""netherite_block|1|3 nether_star|6|2.5 beacon|6|2.5""")

T_CACA = ["Caçada: {n}", "Praga de {n}", "Limpeza: {n}", "Ameaça: {n}", "Contrato de caça: {n}"]
T_CHEFE = ["Caça ao chefe: {n}", "Cabeça a prêmio: {n}"]
T_COLETA = ["Encomenda: {n}", "Suprimentos: {n}", "Coleta: {n}", "Pedido da Guilda: {n}"]
D_CACA = [
 "A Guilda recebeu queixas de {n} perto das estradas. Elimine {q} e volte ao quadro.",
 "Um mercador pagou adiantado para que alguém desse fim a {q} {n}. O serviço é seu.",
 "Viajantes relatam {n} onde antes era seguro. Abata {q} para acalmar a região.",
 "Treino de campo da Guilda: prove o seu valor derrotando {q} {n}.",
 "Os batedores marcaram {n} no mapa. Cace {q} antes que se espalhem.",
 "Um aldeão perdeu o rebanho para {n}. Vingue o prejuízo: {q} abates.",
]
D_CHEFE = [
 "Poucos voltam depois de enfrentar {n}. A Guilda paga bem a quem trouxer a prova da vitória.",
 "{n} foi avistado e ninguém do seu rank ousou aceitar. Mostre que você é diferente.",
 "Um nome que assusta até os veteranos: {n}. Derrote e entre para o registro da Guilda.",
]
D_COLETA = [
 "O armazém da Guilda está em falta. Traga {q}× {n} ao balcão.",
 "Um artesão da capital encomendou {q}× {n} e tem pressa.",
 "A cozinha e as oficinas do reino pedem {q}× {n}. Entregue no quadro.",
 "Uma caravana parte em breve e precisa de {q}× {n} para a viagem.",
 "O tesoureiro quer {q}× {n} para os cofres do reino. Paga na entrega.",
 "Um alquimista viajante procura {q}× {n}. A Guilda intermedeia o negócio.",
]

def nome(alvo, caca):
    ns, cam = alvo.split(":")
    if ns == "minecraft" and cam in VN: return VN[cam]
    return (ENT if caca else ITEM).get(alvo, cam.replace("_", " ").title())

def arred(v):
    if v < 100: return int(max(5, round(v / 5) * 5))
    if v < 1000: return int(round(v / 10) * 10)
    return int(round(v / 50) * 50)

def q(s): return "'" + s.replace("'", "''") + "'"

linhas, faltou, resumo = [], [], []
for r in range(7):
    xp_base = 0.10 * 100 * (MEIO[r] ** 1.5)
    feitas, vistos = [], set()
    def por(tipo, alvo, qtd, peso, grande):
        caca = tipo == "MATAR"
        chefe = caca and qtd <= 3 and peso >= 2
        n = nome(alvo, caca)
        k = len(feitas)
        if grande: titulo = ("Grande caçada: " if caca else "Encomenda grande: ") + n
        else: titulo = (T_CHEFE if chefe else T_CACA if caca else T_COLETA)[k % len(T_CHEFE if chefe else T_CACA if caca else T_COLETA)].format(n=n)
        desc = (D_CHEFE if chefe else D_CACA if caca else D_COLETA)[(k * 7 + r) % len(D_CHEFE if chefe else D_CACA if caca else D_COLETA)].format(n=n, q=qtd)
        mid = "g%d_%s_%s%s" % (r, "c" if caca else "e", re.sub(r"[^a-z0-9]+", "_", alvo), "_x" if grande else "")
        if mid in vistos: return
        vistos.add(mid)
        feitas.append((mid, titulo, desc, tipo, alvo, qtd, NIVEL[r], arred(xp_base * peso), arred(MOEDA[r] * peso), 1000 + r * 200 + k))
    base = []
    for tipo, lista in (("MATAR", CACA[r]), ("COLETAR", COLETA[r])):
        for alvo, qtd, peso in lista:
            if alvo not in (ENT if tipo == "MATAR" else ITEM): faltou.append("%s %s %s" % (RANKS[r], tipo, alvo)); continue
            base.append((tipo, alvo, qtd, peso))
    # alterna caça e coleta para o quadro não ficar em blocos
    cs = [b for b in base if b[0] == "MATAR"]; es = [b for b in base if b[0] == "COLETAR"]
    mist = []
    for i in range(max(len(cs), len(es))):
        if i < len(cs): mist.append(cs[i])
        if i < len(es): mist.append(es[i])
    for tipo, alvo, qtd, peso in mist: por(tipo, alvo, qtd, peso, False)
    unicas = len(feitas)
    # completa até 104 com a versão grande (dobro da quantidade, 60% a mais de prêmio) das missões que não são de chefe
    for tipo, alvo, qtd, peso in mist:
        if len(feitas) >= 104: break
        if qtd <= 3: continue
        por(tipo, alvo, qtd * 2, peso * 1.6, True)
    resumo.append("Rank %s: %d missões (%d alvos diferentes; %d caça, %d coleta)" % (RANKS[r], len(feitas), unicas, sum(1 for f in feitas if f[3] == "MATAR"), sum(1 for f in feitas if f[3] == "COLETAR")))
    for (mid, titulo, desc, tipo, alvo, qtd, niv, xp, moedas, ordem) in feitas:
        linhas.append("(%s, %s, %s, %s, %s, %d, %d, %d, %d, %d, '[]', true, 20, true, %d, CURRENT_TIMESTAMP)" % (q(mid), q(titulo), q(desc), q(tipo), q(alvo), qtd, niv, r, xp, moedas, ordem))

sql = ("-- Lote de missões da Guilda: 100+ por rank (ids g<rank>_...). Gerado por tools/gerar-missoes.py; não mexe em missão que já exista.\n"
       'INSERT INTO "Missao" ("id", "titulo", "descricao", "tipo", "alvo", "quantidade", "nivelMin", "rankMin", "recompensaXp", "recompensaMoedas", "recompensaItens", "repetivel", "intervaloHoras", "ativa", "ordem", "atualizadoEm") VALUES\n'
       + ",\n".join(linhas) + '\nON CONFLICT ("id") DO NOTHING;\n')
open(sys.argv[2], "w", encoding="utf-8", newline="").write(sql)
print("\n".join(resumo)); print("total:", len(linhas)); print("alvos que não existem no pack (%d):" % len(faltou)); print("\n".join(faltou))
