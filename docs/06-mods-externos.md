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
| Equipamento | Rings of Ascension (29 anéis no slot do Curios; só achados em baús, inclusive nas masmorras e no baú do chefe) |
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
| Artifacts (9.5.19) | Modrinth | os dois | Artefatos raros em baús, usados nos espaços do Curios. Pede Cloth Config; sem ele o servidor não sobe |
| Macaw's Bridges, Roofs, Doors, Furniture, Windows, Fences and Walls | Modrinth | os dois | Blocos de construção e decoração |
| Bygone Nether (1.3.2) | Modrinth | os dois | Estruturas e monstros próprios de cada bioma do Nether. Só aparecem em chunks novos |
| Piglin Proliferation (1.2.8) | Modrinth | os dois | Tipos novos de piglin (Alquimista, Viajante) e piglins zumbis com armas de longe |
| Patchouli, Cloth Config API | Modrinth | os dois | Bibliotecas exigidas por Mythic Mounts e Artifacts |
| Domestication Innovation (1.7.1) | Modrinth | os dois | Mais pets (raposa, coelho, sapo, axolote), cama de pet, coleira encantável e comandos de seguir/ficar. Usa o Citadel |

Ajustes em mods que já estavam no pack:

- **Curios:** 10 espaços de anel (`server/kubejs/data/frxworld/curios/slots/ring.json`).
- **Ice and Fire:** dragões não quebram blocos (`"Dragon Griefing" = 2` em `server/config/iceandfire-common.toml`).
- **Waystones:** qualquer jogador cria waystone global (`globalWaystoneSetupRequiresCreativeMode = false`), e a config é distribuída pelo AutoModpack.
