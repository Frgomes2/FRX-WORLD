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
