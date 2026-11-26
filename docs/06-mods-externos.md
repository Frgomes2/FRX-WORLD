# 06 — Mods externos

[← Índice](README.md)

O pack é gerenciado pelo **packwiz** em `pack/`. Cada mod é um `mods/<slug>.pw.toml`, exceto o nosso `frxworld-<versao>.jar`, que vai como arquivo.

## Base do pack (instalados)

| Categoria | Mods |
|---|---|
| Criaturas e chefes | Alex's Mobs, Alex's Caves, Aquamirae, Born in Chaos, L_Ender's Cataclysm, Mowzie's Mobs, Ice and Fire, Mutant Monsters, Creeper Overhaul, Illager Invasion |
| Estruturas | When Dungeons Arise, Dungeons Enhanced, Dungeons and Taverns, IDAS, Repurposed Structures, Structory (+ Towers), Towns and Towers, The Graveyard, Moogs (Voyager/Nether/End), YUNG's (todos), Explorify, Incendium, Nether Depths Upgrade, Nullscape, Unusual End |
| Mundo | Terralith |
| NPCs e vilas | Easy NPC (+ config UI), Guard Villagers, Villagers Trading Plus |
| Equipamento | Rings of Ascension (29 anéis no slot do Curios; só achados em baús, inclusive nas masmorras e no baú do chefe). O Anel de Voo só funciona a partir do rank B da Guilda (`AneisUnicos`) |
| Utilidade | Waystones, JourneyMap, JEI, Lootr, Sophisticated Backpacks/Storage, Curios, Supplementaries, Quark, Create, Portable Pet Sphere, KubeJS |
| Desempenho | Embeddium, ModernFix, FerriteCore, Chunky (pré-geração), spark |
| Bibliotecas | Architectury, Balm, Blueprint, Citadel, Cristel Lib, GeckoLib, Integrated API, Lionfish API, Moonlight, Obscure API, Puzzles Lib, Resourceful Config/Lib, Rhino, Structure Gel, YUNG's API, Zeta, Fragmentum, MezzConfig, Moogs Structure Lib |

## Combate antigo e zoom (13/11/2026)

| Mod | Fonte | Lado | Por quê |
|---|---|---|---|
| Golden Age Combat (v8.0.3) | Modrinth | cliente e servidor | Combate como na 1.7/1.8: sem recarga do ataque. Config em `server/config/goldenagecombat-server.toml` |
| Sword Blocking Mechanics (v8.0.2) | Modrinth | cliente e servidor | Defender com a espada (botão direito). Por padrão só espadas vanilla; espadas de outros mods precisam de datapack. Config em `server/config/swordblockingmechanics-server.toml` |
| Just Zoom (v3.1.0) | Modrinth | só cliente | Zoom por tecla, com a roda do mouse. Substitui o que se queria do OptiFine |
| Konkrete | Modrinth | os dois | Biblioteca exigida pelo Just Zoom |

- Todos entraram pelo `pack\pendentes.txt` e chegam aos jogadores pelo AutoModpack.
- **OptiFine não entra no pack:** conflita com o Embeddium, quebra com mods grandes e a licença não permite redistribuir.
- **Atenção ao balanceamento:** sem recarga do ataque, o dano por segundo sobe. A vida dos monstros e os efeitos por golpe ainda não foram recalibrados.

## Fase 2 (`pack\adicionar-mods-fase2.bat`)

> **Status:** script pronto, **ainda não executado** (os mods ainda não estão em `pack/mods`). Rode o `.bat` no PC: o ambiente do Claude não alcança Modrinth/CurseForge.

| Mod | Fonte | Por quê | Dependências |
|---|---|---|---|
| FTB Quests (2001.x) | CurseForge | Espinha dorsal da campanha | FTB Library, FTB Teams, Architectury |
| Apothic Attributes | Modrinth | Atributos de combate (crítico, roubo de vida, esquiva...) | Placebo |
| The Twilight Forest | CurseForge (só lá) | Dimensão nível 10 | — |
| The Aether | Modrinth | Dimensão nível 30 | — |
| Blue Skies | Modrinth | Everbright (25) e Everdawn (35) | Structure Gel |
| The Undergarden | Modrinth | Dimensão nível 20 | — |
| Deeper and Darker | Modrinth (`deeperdarker`) | Otherside, nível 45 | — |

### Decisões

- **Apotheosis completo: não.** Só o Apothic Attributes. A progressão, o +N, a Reforja Lendária e os Elites/Campeões continuam nossos. O Apotheosis traria raridades e afixos que brigam com a forja.
- **Advent of Ascension: fora.** A versão 1.20.1 está praticamente vazia.
- O namespace do Apothic na 1.20.1 é **`attributeslib`**. Veja [sistemas/atributos-forja.md](sistemas/atributos-forja.md).

## Pendências depois de instalar

- Conferir os IDs dos chefes usados nas tasks do FTB Quests, por exemplo `undergarden:forgotten_guardian`, `blue_skies:arachnarch` e `deeperdarker:stalker` (`/summon` + Tab).
- Testar se `Apoth.ativo()` detecta o mod (a ficha deve mostrar crítico e esquiva vindos do Apothic).
- Pré-gerar as dimensões novas com o Chunky.

## Skins, aparência e pets (15/11/2026)

| Mod | Fonte | Lado | Por quê |
|---|---|---|---|
| SkinRestorer (2.11.1) | Modrinth | servidor | Devolve a skin dos jogadores no servidor offline: busca na Mojang pelo nick ao entrar. `/skin set mojang <nick>` ou `/skin set web <link>` para quem usa nick inventado. Config em `server/config/skinrestorer/` |
| Cosmetic Armor Reworked (1.20.1-v1a) | CurseForge | os dois | Botão no inventário para esconder cada peça de armadura e mostrar a skin |
| Naturalist (2.0.9) | Modrinth | os dois | Animais comuns pelo mundo (veados, ursos, cobras, vaga-lumes) |
| Mythic Mounts (7.4.2) | Modrinth | os dois | Montarias lendárias. Pede Geckolib e Patchouli |
| Dragon Mounts: Legacy (1.2.0-beta) | Modrinth (`dragon-mounts-legacy`) | os dois | Ovos de dragão em baús; chocar, criar e voar. Pedido em 18/11 por `pack/pendentes.txt`; conferir a versão depois do primeiro início |
| Artifacts (9.5.19) | Modrinth | os dois | Artefatos raros em baús, usados nos espaços do Curios. Pede Cloth Config; sem ele o servidor não sobe |
| Macaw's Bridges, Roofs, Doors, Furniture, Windows, Fences and Walls | Modrinth | os dois | Blocos de construção e decoração |
| Bygone Nether (1.3.2) | Modrinth | os dois | Estruturas e monstros próprios de cada bioma do Nether. Só aparecem em chunks novos |
| Piglin Proliferation (1.2.8) | Modrinth | os dois | Tipos novos de piglin (Alquimista, Viajante) e piglins zumbis com armas de longe |
| Connectivity (7.7) + Cupboard (4.2) | CurseForge | os dois | Evita quedas e tempo esgotado no login de pack grande |
| Krypton Reno (26.1.1) | Modrinth (`krypton-fnp`) | os dois | Otimiza o tráfego de rede |
| Woodwalkers + CraftedCore | Modrinth (`woodwalkers`, `crafted-core`) | os dois | Dá o corpo de animal às formas do Druida. O FRX o tranca em "só por código": nenhuma outra classe se transforma |
| Patchouli, Cloth Config API | Modrinth | os dois | Bibliotecas exigidas por Mythic Mounts e Artifacts |
| Domestication Innovation (1.7.1) | Modrinth | os dois | Mais pets (raposa, coelho, sapo, axolote), cama de pet, coleira encantável e comandos de seguir/ficar. Usa o Citadel |

Ajustes em mods que já estavam no pack:

- **Curios:** até 27 espaços de anel (`server/kubejs/data/frxworld/curios/slots/ring.json`), liberados pelo rank da Guilda: F 2, E 4, D 7, C 11, B 15, A 20, S 27 (`protecao/AneisUnicos.java`). Sem anéis iguais ao mesmo tempo; Anel de Voo só a partir do rank B, com fôlego de voo (90 s, recarga de 45 s no chão), cortado por 5 s em combate e sem efeito na Torre, X1, Fenda e perto de chefão (`protecao/VooAnel.java`). Anel de Voo só nos baús do salão do Labirinto. Anel de Vida pelo rank (F 2, E 3, D 4, C 5, B 6, A 8, S 10 corações; `ringHealthHearts = 0` no config do mod).
- **JourneyMap:** os marcadores de waypoint não aparecem mais flutuando no jogo, só no mapa (`showInGameBeacons false` em `server/journeymap/server/6.0/journeymap.server.global.config`).
- **The Graveyard:** névoa desligada (`fogParticles.enabled false` em `server/config/graveyard/graveyard.json5`; o AutoModpack envia o arquivo aos clientes).
- **Ice and Fire:** dragões não quebram blocos (`"Dragon Griefing" = 2` em `server/config/iceandfire-common.toml`).
- **Waystones:** qualquer jogador cria waystone global (`globalWaystoneSetupRequiresCreativeMode = false`), e a config é distribuída pelo AutoModpack.

## Ferramenta de admin fora do pack: Forgematica

- **Forgematica 0.1.13-mc1.20.1** (porte do Litematica para Forge) + biblioteca **MaFgLib 0.1.14-mc1.20.1**, do Modrinth. Mostra o "fantasma" de uma construção no mundo e, para quem é admin ou está no criativo, cola a construção inteira.
- É só do lado do jogador e **não entra no pack**: fica instalado à mão na pasta `mods` do jogo de quem é admin. No PC do Flavio o jogo roda em `cliente\jogo`, então os dois arquivos estão em `cliente\jogo\mods` (instalados em 18/11; há uma cópia sem uso em `%APPDATA%\.minecraft\mods`). Os outros jogadores não recebem.
- As construções ficam na pasta `schematics` do jogo (no PC do Flavio, `cliente\jogo\schematics`).

## Mods de 18/11 (versão 1.1.2)

| Mod | Arquivo | Lado | Observações |
|---|---|---|---|
| Samurai Dynasty | `samurai_dynasty-0.0.51-1.20.1-forge.jar` | os dois | Katanas, odachi, wakizashi, naginata e armaduras. **Exige AzureLib 3.0+**, que a página do Modrinth não declara: sem ela o servidor não sobe |
| AzureLib | `azurelib-neo-1.20.1-3.2.9.jar` | os dois | Biblioteca do Samurai Dynasty |
| Enhanced Celestials | `Enhanced-Celestials-forge-1.20.1-5.0.3.3.jar` | os dois | Lua de sangue, azul e da colheita. Trouxe CorgiLib e Data Anchor |

**The Man From The Fog e Tense Ambience**: removidos em 19/11 (versão 1.1.6), a pedido dos jogadores. O `.pw.toml` ficou guardado em `logs/removidos/`; a config `server/config/man_config.toml` e a tradução `assets/man/lang/pt_br.json` continuam no projeto, sem efeito.

**FallingTree** (só servidor, desde 1.1.8): árvore inteira cai ao cortar um tronco com machado; agachado quebra só um bloco. Ajustes nossos em `server/config/fallingtree.json` (original em `logs/fallingtree.original.json`): `minimumLeavesAroundRequired = 3` e `includePersistentLeavesInRequiredCount = false` (folha colocada por jogador não conta), para não confundir construção de tora com árvore; `maxSize = 100` com `ABORT` (árvore maior que isso não cai de uma vez).

## Adicionados em 20/11 (versão 1.2.3)

| Mod | Origem | Lado | Observação |
|---|---|---|---|
| Farmer's Delight 1.3.4 | Modrinth (`farmers-delight`) | os dois | Cozinha e plantações |
| Aquaculture 2 2.5.7 | Modrinth (`aquaculture`) | os dois | Pesca; conferir se o equipamento de Neptúnio entra na classificação por rank |
| Serene Seasons 9.1.0.3 + GlitchCore | Modrinth (`serene-seasons`) | os dois | Estações; ajustar neve/gelo e plantações na configuração depois do primeiro início |
| Better Archeology 1.2.1 + SuperMartijn642's Config Lib | Modrinth (`better-archeology`) | os dois | Estruturas só em chunks novos |
| Sound Physics Remastered 1.5.1 | Modrinth (`sound-physics-remastered`) | os dois | Efeito só no cliente |
| AmbientSounds 6.3.11 + CreativeCore | Modrinth (`ambientsounds`) | cliente | Sons de natureza |

Mods do CurseForge com download automático bloqueado (Storage Finder, SmartChest) não podem ir para `pack/pendentes.txt`: travam a atualização dos mods do servidor. Só entram com o `.jar` posto à mão em `pack/mods`.

## Mods pedidos em 26/11/2026 (`pack\pendentes.txt`)

| Mod | Fonte (Modrinth) | Lado | Por quê |
|---|---|---|---|
| Traveler's Backpack | `travelersbackpack` | os dois | Mochilas temáticas com tanques, cama e habilidades. Vai no espaço "costas" do Curios; a lápide guarda a mochila |
| Advanced Netherite | `advanced-netherite` | os dois | Quatro níveis de netherita acima do comum (ferro, ouro, esmeralda, diamante), com bônus de armadura |
| Integrated Stronghold | `KUJdfPQz` | os dois | Fortaleza gigante com salas detalhadas. Pede Integrated API, Create, Supplementaries e Quark (já instalados) |
| Stalwart Dungeons | `BCLngNKp` | os dois | Duas masmorras difíceis no Nether e uma no Fim, com chefes e relíquias |
| Better Villages (Forge) | `dGVX5JbJ` | servidor | Vilas de aldeões mais bonitas. Pede a biblioteca Ferret |
| Shield Expansion | `sjxWxSao` | os dois | Aparar golpes, níveis e encantamentos de escudo |
| Medieval Music | CurseForge `medieval-music` (pendente) | só cliente | Trilha medieval no lugar da música normal |

- Estruturas novas (fortaleza, masmorras, vilas) só aparecem em chunks ainda não gerados.
- **Integrated Stronghold e YUNG's Better Strongholds** ficam juntos: o autor do Integrated recomenda usar só um, mas tirar o YUNG's agora mexeria nas fortalezas já geradas.
- **Shield Expansion** convive com o Sword Blocking Mechanics e o Golden Age Combat; conferir se o aparar do escudo não briga com a defesa da espada.
- Instalados em 26/11 às 07:00, menos o Medieval Music: no Modrinth não há versão Forge 1.20.1. Ficou na fila pelo CurseForge (`cf medieval-music`), que às vezes bloqueia o download automático; nesse caso, baixar o `.jar` à mão para `pack/mods`.

## Desempenho e qualidade de vida (pedidos em 26/11/2026, `pack\pendentes.txt`)

Escolhidos do top 100 do CurseForge (Forge 1.20.1, por popularidade).

| Mod | Fonte | Lado | Por quê |
|---|---|---|---|
| Clumps | Modrinth `clumps` | os dois | Junta as bolinhas de XP em uma só (menos lag em farm e chefe) |
| Entity Culling | Modrinth `entityculling` | só cliente | Não desenha mob e bloco com entidade que está fora de vista |
| ImmediatelyFast | Modrinth `immediatelyfast` | só cliente | Desenha HUD, textos e mapas mais rápido |
| Enchantment Descriptions | Modrinth `enchantment-descriptions` | os dois | Explica cada encantamento no item. Pede Bookshelf |
| AppleSkin | Modrinth `appleskin` | os dois | Quanto cada comida recupera e a saturação |
| Controlling | Modrinth `controlling` | só cliente | Busca no menu de teclas e mostra teclas repetidas. Pede Searchables |
| Packet Fixer | Modrinth `packet-fixer` | os dois | Evita desconexão por pacote grande demais (inventário e baú cheios de itens com dados) |
| AllTheLeaks | CurseForge `alltheleaks` (só lá) | os dois | Corrige vazamentos de memória de vários mods e do Forge |

- Ficaram de fora da lista: Xaero's (já temos JourneyMap), Oculus (pesado), Essential (contas e loja próprias), Comforts (o Traveler's Backpack já tem saco de dormir), Nature's Compass e Explorer's Compass (tiram a graça de explorar).

## Dinossauros (26/11)

- **Fossils and Archeology: Revival** (Modrinth `fossils-and-archeology-revival`): fósseis no terreno, extração de DNA, embriões e ovos; mais de 60 criaturas pré-históricas e bioma de vulcão. Dependências: TerraBlender e More Hitboxes.
- **Jurassic Saga**: testado e retirado no mesmo dia. Com Terralith e The Rings of Power no pack, derruba o servidor ao gerar terreno novo ("Feature order cycle found"). Não readicionar sem testar num mundo de teste.

