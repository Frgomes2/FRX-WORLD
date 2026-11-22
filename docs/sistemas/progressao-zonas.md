# Progressão, zonas, dimensões e monstros com rank

[← Índice](../README.md) · Código: `progressao/` (Progressao, DadosJogador, ConfigJogo, Zonas, Ranks, Marcos, Conquistas) · Cliente: `HudNivel`, `RotulosMob`

## Nível e XP

- **Nível máximo:** 100 (`progressao.nivelMaximo`).
- **Curva de XP:** `xpBase × nível ^ xpExpoente`. O padrão é 100 × N^1,5, com multiplicador global `xpMultiplicador` (2,0 = XP em dobro).
- Esses três valores ficam em `server\config\frxworld-common.toml` e exigem reinício.
- O resto da progressão (zonas, ranks, taverna, forja, floresta...) é configurado no painel, no menu **Progressão**, e lido por `ConfigJogo`.
- O XP vem de monstros, missões, taverna, campanhas e quests do FTB (`/frx xp {p} N`). A raça Humano dá +10%.
- Admin: `/frx xp <j> <n>`, `/frx nivel <j> <n>`.

## Zonas de perigo

O nível de cada monstro é decidido pela **faixa da dimensão** onde ele nasce. Código: `Zonas.faixa(Level)` e `Zonas.nivel()`.

| Dimensão | Faixa |
|---|---|
| Superfície | 1 até `zonas.nivelMax` (60), +1 nível a cada `zonas.blocosPorNivel` (250) blocos do spawn |
| Floresta do Crepúsculo | 14–30, com dureza extra: +80% de vida e +50% de dano (`Zonas.dureza`) |
| Nether | 55–80, com dureza extra: +150% de vida e +100% de dano; exige Rank C da Guilda |
| Undergarden | 20–35 |
| Everbright | 25–40 |
| Aether | 30–45 |
| O Fim | 75–100, com dureza extra: +250% de vida e +150% de dano; exige Rank A da Guilda |
| Everdawn | 35–50 |
| O Outro Lado | 45–60 |
| Dimensão de outro mod fora da tabela | 20–40 |

- As faixas podem ser sobrescritas em `zonas.dimensoes` na configuração do painel. Ainda não há editor visual.
- Nas **capitais dos reinos** não há perigo nem monstros com rank.
- Ao trocar de dimensão aparece um título com o nome, a faixa e o aviso de perigo.

### O nível mínimo não bloqueia: muda o risco

| Situação do jogador | Efeito |
|---|---|
| Abaixo do nível do monstro | Os monstros batem +8% por nível de diferença (+50% extra se ele está abaixo do mínimo da dimensão), até ×2,2 |
| Dentro da faixa | Normal |
| Bem acima (dominando) | Os monstros batem ×0,85. O jogador bate −5% por nível abaixo, até 0,4, ou ×1,15 se está 10+ níveis acima |
| XP de monstro muito mais fraco | 6+ níveis abaixo: 75% · 10+: 40% · 15+: 10% |

Código: `Zonas.fatorContraJogador`, `fatorDoJogador`, `fatorXp` e o handler `perigo` (LivingHurtEvent, prioridade HIGH).

## Monstros com rank

Código: `progressao/Ranks.java` · rótulo no mundo: `SyncRotuloMob` → `cliente/RotulosMob`.

Todo monstro mostra o nível em cima da cabeça. A cor indica a diferença para o seu nível (cinza, verde, branco, amarelo, laranja, vermelho). O rótulo some atrás de paredes e longe (cerca de 14 blocos).

| Rank | Aparência | Como surge | Prêmio |
|---|---|---|---|
| Normal | `Zumbi Lv. 3` | Padrão | Normal |
| **Elite** | `[Elite] Zumbi Berserker Lv. 17 ★★★` + barra de vida | `eliteChance` 5%, a partir do `eliteNivelMin` (3). Nível +1 a +5 | Moedas extras |
| **Campeão** | `[Campeão] Prefixo Nome Lv. 43` + título, barra roxa no topo, anúncio | `campeaoChance` 0,5%, a partir do `campeaoNivelMin` (10). Nível +5 a +10 | Diamantes, esmeraldas, maçã dourada |
| **Chefe** | `[Chefe]` | Guardião de dungeon (nível recomendado + 5) e chefes de mods | Loot da dungeon |
| **Chefe Mundial** | `[Chefe Mundial]` + nome em maiúsculas, nível máximo | Evento de chefe mundial | Evento |

- **Elite:** de ★1 a ★5, com um **traço**:
  - Berserker: mais dano abaixo de 50% de vida;
  - Blindado;
  - Veloz;
  - Vampírico: se cura;
  - Peçonhento: envenena;
  - Incendiário: põe fogo;
  - Gélido: deixa lento.
- **Campeão:** prefixo (Ancestral, Sombrio, Colossal, Profano, Eterno, Maldito, Voraz) e título (por exemplo, "Guardião da Cripta"). É persistente e não some.
- **Respeita a faixa:** o nível do Elite/Campeão é limitado ao máximo da dimensão.
  - Se há um jogador **forte** perto (10+ níveis acima, a até 48 blocos), a chance de Elite sobe 1,5× e a de Campeão 2×.
  - O nível também sobe 1/3 da diferença, até o máximo da faixa + 5. São os "desafios especiais" para quem domina a zona.
- A barra de ação ao bater mostra o rótulo completo (`Ranks.rotulo()`).
- **Config no painel:** Progressão → Zonas de perigo → Monstros com rank (seção `zonas`).

## Marcos (conquistas ocultas)

Código: `progressao/Marcos.java` · 48 advancements ocultos em `data/frxworld/advancements/frx/` (critério `minecraft:impossible`, concedidos por código).

- **Personagem:** `classe`, `raca`, `raca_especial`, `raca_lendaria`, `despertar`.
- **Conteúdo:** `taverna_1`, `taverna_10`, `dungeon`, `chefe_mundial`, `elite`, `campeao`.
- **Nível:** `nivel_5` … `nivel_100`.
- **Por reino:** `capital_<reino>`, `honrado_<reino>`, `campanha_<reino>`.

São o elo com o **FTB Quests**: as quests usam tasks do tipo `advancement` apontando para esses marcos. Veja [quests.md](quests.md).

Há também as **Conquistas** visíveis (`/conquistas`, aba Conquistas da janela de Status).

## Rank da Guilda para entrar nas dimensões

- **Nether: Rank C. End: Rank A.** Sem o rank, o portal (ou waystone, ou qualquer teleporte) não leva e o jogador recebe o aviso.
- Por que esses ranks: o Nether e o End são as fases depois da superfície. A superfície vai até o nível 60; o Nether começa no 55, mas a porta abre no Rank C (nível 35): quem entra antes de ficar forte corre o risco por conta própria; o ideal é ir depois de explorar o mundo normal. O End começa no 75 e pede Rank A (nível 70): é o mais difícil dos três.
- Troca pelo painel: `zonas.rankMinimo = { "minecraft:the_nether": "C", "minecraft:the_end": "A" }`; `"F"` ou `"livre"` libera. Aceita qualquer dimensão (Aether, Twilight...).
- Criativo e espectador passam. Quem já está dentro da dimensão não é expulso.
- **Élitros: Rank A.** Abaixo disso o jogador veste, mas o voo é cortado na hora (`progressao/Elitros.java`).

## Nether: a dimensão mais dura

- Faixa de nível 55–80 e dureza +150% de vida e +100% de dano em todo monstro (trocável no painel em `zonas.faixas` e `zonas.dureza`).
- **Emboscadas** (`aventura/Inferno.java`): 3 min depois de entrar e depois a cada 6 a 11 min, por jogador. 75% horda (4 a 7 entre esqueletos wither, blazes, brutos piglin, hoglins e cubos de magma), 25% um chefão de `aventura/Chefes` do rank do jogador, nível da zona + 3. Criativo e espectador ficam de fora.
- Conteúdo de mods no Nether: Incendium, Moog's Nether Structures, YUNG's Better Nether Fortresses, Nether Depths Upgrade, Cataclysm (Ignis e Monstruosidade de Netherita), Bygone Nether e Piglin Proliferation.

## End: o Dragão do Fim

- `aventura/DragaoFim.java`. O dragão é chefão de Rank A: nível fixo 80, 2500 de vida, +12 de armadura e o dobro de dano nos jogadores (golpes, sopro e bolas de fogo). Abaixo do nível mínimo do Rank A, o jogador causa só 20% do dano.
- Enquanto ele vive, a cada 25 s, cada jogador a até 180 blocos do centro recebe 2 ou 3 lacaios (sorteio do Códex de Criaturas no lugar `fim`; sem cadastro, enderman, fantasma, vex ou endermite), com teto de 4 por jogador e 16 no total. Há 20% de chance de vir também um Guardião do Dragão (chefão de `aventura/Chefes`, Rank A, nível 85). Os lacaios recebem o nível do End normalmente.
- Quem causar dano ao dragão fica anotado nele (NBT `frx_dragao_ajudantes`, sobrevive a reinícios). Quando ele cai, cada ajudante online ganha 50 mil moedas de ouro (5 000 000 cobre) direto no banco e a conquista Matador de Dragões, que aparece no perfil do site; sai o aviso no chat e os lacaios somem. Ele entra na coleção de chefões como qualquer chefão.
