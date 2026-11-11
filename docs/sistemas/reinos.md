# Reinos, NPCs, reputação e waystones

[← Índice](../README.md) · Código: `reinos/` · Dados: `server/frxworld/reinos/` · Painel: menus **Reinos** e **NPCs e skins**

## Os 8 reinos

| ID | Nome | Centro (x, z) | Rei |
|---|---|---|---|
| `tempest` | Federação de Jura Tempest (cidade inicial, 52×35 chunks) | 128, 8 | Lorde Rimuru Tempest |
| `dwargon` | Reino Armado de Dwargon (subterrâneo, 20×20 chunks) | 680, 2100 | Rei Gazel Dwargo |
| `sarion` | Dinastia Feiticeira de Sarion | 2200, −1400 | Imperatriz Elmesia El-Ru Sarion |
| `leste` | Império do Leste | −2600, −300 | Imperador Ludora |
| `leste-sakura` | Império do Leste — Distrito Sakura | −2600, 500 | Senhora das Cerejeiras |
| `oeste` | Nações do Oeste | −1200, 2400 | Rei Edmaris de Farmus |
| `lubelius` | Império Sagrado de Lubelius | 1800, 1600 | Luminus Valentine |
| `demonios` | Territórios dos Lordes Demônios | −1600, −2600 | Guy Crimson |

## Como os reinos são construídos

- `Reinos.java` carrega `reinos/<id>/reino.json`, as fatias `<ox>_<oz>.nbt` e o `posicoes.json` (nome, x, z, praça, NPCs, spawn, masmorras).
- `FeatureReinos` cola uma fatia por chunk, **só em chunks gerados depois**. Ele achata o terreno, suaviza 32 blocos em volta e tem o modo `subterraneo` para Dwargon.
- **Floresta Sombria** (`FlorestaSombria.java`): cinturão de 40 a 260 blocos em volta de Tempest, onde só se anda a pé.
- **Baús** (`BausReino.java`): saque por nível do reino (Tempest 1 … Demônios 5), trocados por baús do Lootr.
- **Decorações** (`Decoracoes.java`) · `/frx decor refazer`.
- **Capitais são seguras:** sem zona de perigo e sem Elites/Campeões.

## NPCs (`NpcsReino.java`)

- **Casinhas (`Oficios.java`):** cada ofício tem uma barraca 5×5 com tema próprio, e o NPC fica parado dentro dela, atrás do balcão:
  - ferreiro: forja com bigorna, alto-forno e caldeirão de lava;
  - armeiro: mesa de ferraria;
  - mercador: barraca de vendas;
  - banqueiro: balcão com grades;
  - sacerdote: altar de quartzo;
  - sábio: estantes de livros;
  - cartógrafo: mesa de cartografia;
  - taverneiro: barris.

  As barracas ficam em anel em volta da praça (raio 12) e não podem ser quebradas fora do criativo.
  - `/frx npc aqui <papel>` muda a casinha de lugar: ela é montada onde você está, aberta para a praça.
  - A barraca antiga fica no lugar; retire em criativo.

- **Papéis:** rei, ferreiro, armeiro, mercador, banqueiro, sacerdote, sábio e guardas.
- Os NPCs nascem quando o jogador entra na caixa do reino.
- `/rei` mostra o nome, as coordenadas e a direção do rei mais próximo.
- **Ferreiro e armeiro:** agachar + clicar com o item na mão. Veja [atributos-forja.md](atributos-forja.md).
- **Mercador:** loja configurada no painel. Veja [economia.md](economia.md).
- O banqueiro não vira guarda ao ser clicado com espada (bloqueado em `aoInteragir`).
- `/frx npc recriar` · `/frx npc aqui`.

### População e raças dos NPCs

- `Populacao.java` povoa os reinos com moradores. Não entram dragões do Ice and Fire. `/frx populacao refazer`.
- `reinos/Racas.java` define os `VillagerType` por raça:
  - originais: humano, anão, elfo, hobgoblin, oriental, sagrado, demônio;
  - novos: beastkin, halfling, oni, vampiro, dragonoide, espírito, gigante.
- Cada reino tem uma mistura com pesos. Tempest é o mais variado, Dwargon quase só anões, Sarion elfos.
- `cliente/RenderRacas` desenha esses moradores com corpo de jogador e skin `textures/entity/raca/<raca>_<0-4>.png`. Escalas: anão 0,78, halfling 0,68, gigante 1,18, oni 1,04.

## Reputação (`Reputacao.java`)

| Nível | Pontos | Desconto |
|---|---|---|
| Neutro | 0 | — |
| Amigável | 100 | 3% |
| Honrado | 500 | 6% |
| Reverenciado | 1500 | 10% |
| Exaltado | 4000 | 15% |
| Herói | 10000 | 20% |

- Ganha-se com missões, campanhas e quests. Humanos ganham +10%.
- **Honrado** com um reino libera a raça especial ligada a ele. Veja [racas.md](racas.md).
- `/reputacao` · `/titulo usar <título>` · admin `/frx reputacao` ou pelo painel (Reinos → ✏️).

## Campanhas dos reis

Cada rei tem uma corrente de **5 etapas** com história (clique no rei para aceitar, acompanhar e entregar). O final dá arma Lendária +5, o título "Lenda de <reino>", muita reputação e moedas.

- `/campanha aceitar` · admin `/frx campanha pular`.
- As campanhas de Oeste ("A Coroa do Oeste", 12+), Lubelius ("A Luz de Luminus", 20+) e Lordes Demônios ("O Banquete dos Lordes", 30+) se somam às de Tempest, Dwargon, Sarion, Leste e Sakura.
- Concluir as campanhas de Lubelius e dos Lordes Demônios libera as raças lendárias **Anjo** e **Rei Demônio**.

Veja [quests.md](quests.md) para o papel das campanhas na estrutura de missões.

## Invasões (`Invasoes.java`)

Ataques periódicos aos reinos. `/invasao` mostra o estado · admin `/frx invasao parar`. A configuração fica no painel (Progressão).

## Waystones dos reinos (`WaystonesReino.java`)

- Cada reino tem **5 waystones**: **Centro** (na praça) e **Portões Norte, Sul, Leste e Oeste**, 6 blocos fora do muro, com piso e 2 lanternas.
- O material combina com o reino: ardósia em Dwargon (o centro fica no subterrâneo, perto da taverna, e os portões na superfície), pedra-negra nos Lordes Demônios, musgo em Sarion e Sakura.
- **Colocação preguiçosa:** cada waystone aparece quando o chunk carrega e fica registrada em `<mundo>/frx_waystones.txt`, para não duplicar.
- Usa a API do mod Waystones por reflexão: `WaystonesAPI.placeWaystone`, `IMutableWaystone.setName`, `WaystoneManager.updateWaystone`.
- Nomes no formato "Tempest · Portão Norte".
- `/frx waystones` lista as colocadas e as que faltam · `/frx waystones refazer` recoloca.

## Masmorras dos reinos

Cada reino tem masmorras próximas. Veja [dungeons-chefes.md](dungeons-chefes.md).
