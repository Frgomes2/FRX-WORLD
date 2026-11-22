# Reinos, NPCs, reputação e waystones

[← Índice](../README.md) · Código: `reinos/` · Dados: `server/frxworld/reinos/` · Painel: menus **Reinos** e **NPCs e skins**

## Os 8 reinos

| ID | Nome | Centro (x, z) | Rei |
|---|---|---|---|
| `tempest` | Federação de Jura Tempest (cidade inicial; só o coliseu e os quarteirões colados nele, 29×15 chunks) | 296, 104 | Lorde Rimuru Tempest |
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
- **Tempest (14/11/2026):** a caixa vai de x 64 a 527 e de z −16 a 223. A praça do reino é o centro do coliseu (`praca: [296, 105]`), e os NPCs usam o arranjo automático em anel lá dentro. As posições fixas antigas ficaram guardadas em `npcs_antes_20261114` no `posicoes.json`.
- **Reduzir um reino num mundo já gerado:** rode `tools/reduzir_reino.py` (guarda o original em `reinos-originais/`), tire do mundo as regiões que o reino ocupava (`region`, `entities` e `poi`), apague as linhas do reino em `frx_waystones.txt`, `frx_oficios.txt`, `frx_npcs.txt`, `frx_populacao.txt`, `frx_masmorras.txt` e `frx_entradas.txt`, ligue o servidor e refaça a pré-geração da área com o Chunky.
- **Ponto inicial:** por padrão o jogador novo nasce no centro da capital escolhida no painel. Se o admin mudar o spawn do mundo com `/setworldspawn`, a abertura isekai passa a nascer e renascer nesse ponto, e o Sistema informa a distância e a direção da capital.

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
- **Papel ausente numa capital:** em `server/frxworld/reinos/posicoes.json`, `"<reino>": { "sem": ["papel", ...] }` faz o NPC desse papel não nascer ali e sumir se já existir (relido a cada minuto, vale ao carregar o NPC). Hoje: Lubelius sem cambista (a costureira ocupa o lugar dele).
- Os NPCs nascem quando o jogador entra na caixa do reino.
- `/rei` mostra o nome, as coordenadas e a direção do rei mais próximo.
- **Ferreiro e armeiro:** agachar + clicar com o item na mão. Veja [atributos-forja.md](atributos-forja.md).
- **Mercador:** loja configurada no painel. Veja [economia.md](economia.md).
- O banqueiro não vira guarda ao ser clicado com espada (bloqueado em `aoInteragir`).
- `/frx npc recriar` · `/frx npc aqui`.

### População e raças dos NPCs

- `Populacao.java` povoa os reinos com moradores. Não entram dragões do Ice and Fire. `/frx populacao refazer`.
- **Proteção:** morador das ruas só leva dano de jogador (e do vazio); monstro, dragão, parede e invasão não matam. Guardas continuam podendo morrer.
- **Reposição:** morador ou guarda que morre volta em 10 minutos, perto de onde caiu, quando houver jogador na região. A fila fica em `frx_repor.txt`, na pasta do mundo (`x;y;z;morador|guarda;quando`).
- `reinos/Racas.java` define os `VillagerType` por raça:
  - originais: humano, anão, elfo, hobgoblin, oriental, sagrado, demônio;
  - novos: beastkin, halfling, oni, vampiro, dragonoide, espírito, gigante.
- Cada reino tem uma mistura com pesos. Tempest é o mais variado, Dwargon quase só anões, Sarion elfos.
- `cliente/RenderRacas` desenha esses moradores com corpo de jogador e skin `textures/entity/raca/<raca>_<0-4>.png`. Escalas: anão 0,78, halfling 0,68, gigante 1,18, oni 1,04.

### Vida dos moradores (`VidaNpc.java`)

- **Memória:** ferreiro, armeiro, mercador, cozinheiro e pedreiro lembram a última compra do jogador e quantas vezes ele comprou ali; a frase aparece ao abrir a loja. O taverneiro comenta a última missão entregue e a má fama do jogador; o rei reconhece quem é Reverenciado.
- **Freguês:** 10 compras no mesmo lojista dão 3% de desconto; 30 dão 5%. Soma com a reputação (teto de 50%).
- **Skin do aprendiz:** no painel (NPCs) há os tipos "Aprendiz do Ferreiro", "do Armeiro" e "do Mercador", com skin padrão ou por reino. Sem skin definida, o aprendiz aparece com a skin da raça do reino. O mestre de folga na taverna usa a skin do mestre.
- **Rotina:** de noite (13000 a 23000 no relógio do jogo) ferreiro, armeiro e mercador viram "Aprendiz do ..." e cobram 10% a mais. Os mestres aparecem ao lado do taverneiro, parados, e somem ao amanhecer ou quando o servidor para. Eles são criados quando há jogador na capital.
- **Na taverna, à noite:** os mestres só conversam (boatos) e é ali que ferreiro e armeiro pedem favores. O taverneiro também conta boatos em "Alguma novidade?".
- **Rixa ferreiro × armeiro:** os dois disputam a encomenda do rei. Um favor por noite do jogo em cada capital. Ferreiro pede 16 lingotes de ferro e 16 carvões; armeiro pede 12 couros e 8 lingotes de ferro. Entregar dá +1 de estima com ele, −1 com o outro, +15 de reputação e XP. Estima 3 = 5% de desconto na loja dele, 6 = 8%; −3 ou menos = 5% mais caro.
- Tudo fica guardado no jogador (`frx_vida`).

## Reputação (`Reputacao.java`)

| Nível | Pontos | Desconto |
|---|---|---|
| Neutro | 0 | — |
| Amigável | 100 | 3% |
| Honrado | 500 | 6% |
| Reverenciado | 1500 | 10% |
| Exaltado | 4000 | 15% |
| Herói | 10000 | 20% |

- Ganha-se com missões, campanhas, quests, abates perto da capital e defesa de invasões. Humanos ganham +10%.
- **Favor Real:** a partir de Honrado, o rei oferece um serviço por dia (derrotar 15 a 40 monstros a até 200 blocos da capital, conforme o nível de reputação). Paga reputação, moedas e XP.
- **Reverenciado** libera no mercador o **Corcel do reino** (cavalo domado, selado, veloz), além dos itens que já existiam.

### Rivais e má fama (`Hostilidade.java`)

- **Rivais:** Lubelius × Lordes Demônios, Oeste × Tempest, Leste × Sarion. Todo ganho de 5 pontos ou mais tira um quarto no rival. Dwargon e Sakura não têm rival.
- **Lado negativo** (piso em −3000):

| Nível | Pontos | O que acontece |
|---|---|---|
| Malvisto | −100 | Preços 15% mais caros |
| Hostil | −500 | Preços 30% mais caros; lojistas não atendem; guardas atacam dentro da capital. Banco, câmbio, Guilda, clãs e o rei continuam atendendo |
| Inimigo | −1500 | Além do anterior, recompensa pela cabeça |

- **Recompensa:** com PvP ligado, quem derrota um Inimigo do reino recebe 3000 + 150 por nível do alvo (em cobre) e +100 de reputação com o reino; o alvo volta a −500. Não vale entre membros do mesmo clã. O chat avisa quando alguém vira procurado e, a cada 15 minutos, a região onde foi visto; `/procurados` lista.
- **O que tira reputação:** ajudar o rival, os serviços do Contrabandista, bater em guarda sem ter sido atacado (primeiro um aviso, depois −10 por golpe) e matar guarda (−30).
- **A volta:** multa ao rei (30 de cobre por ponto negativo; zera a reputação), missões e abates perto da capital, e +2 pontos a cada 5 minutos de jogo.
- Títulos: "Fora da Lei" (Hostil com algum reino) e "Inimigo de <reino>".

### Contrabandista (`Contrabando.java`, tela `TelaContrabando`)

- NPC de papel `contrabandista`, só fora das capitais: `/frx npc aqui contrabandista`; para tirar, `/frx npc remover`.
- **Três serviços por dia** (mudam à meia-noite), cada um contra um reino: **Carga desviada** (entregar itens; −40), **Olhos na capital** (chegar a até 30 blocos do centro da capital; −60) e **Patrulha a menos** (derrubar 2 guardas; −100). Pagam moedas e dão metade da perda em reputação com o rival do alvo.
- **Mercadoria** por má fama com qualquer reino: Malvisto (pérolas, invisibilidade, queda lenta), Hostil (totem, maçã encantada, Adaga do Contrabandista Épica +3), Inimigo (élitros, Lâmina e Couraça do Renegado, Lendárias +5).
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

- **Indestrutíveis:** as 5 waystones de cada reino (centro e 4 portões) e o bloco de apoio embaixo de cada uma não quebram, não explodem e não são movidas por pistão. Só o admin, no criativo, consegue quebrar. As posições vêm de `<mundo>/frx_waystones.txt`.


- Cada reino tem **5 waystones**: **Centro** (na praça) e **Portões Norte, Sul, Leste e Oeste**, 6 blocos fora do muro, com piso e 2 lanternas.
- O material combina com o reino: ardósia em Dwargon (o centro fica no subterrâneo, perto da taverna, e os portões na superfície), pedra-negra nos Lordes Demônios, musgo em Sarion e Sakura.
- **Colocação preguiçosa:** cada waystone aparece quando o chunk carrega e fica registrada em `<mundo>/frx_waystones.txt`, para não duplicar.
- Usa a API do mod Waystones por reflexão: `WaystonesAPI.placeWaystone`, `IMutableWaystone.setName`, `WaystoneManager.updateWaystone`.
- Nomes no formato "Tempest · Portão Norte".
- `/frx waystones` lista as colocadas e as que faltam · `/frx waystones refazer` recoloca.

## Masmorras dos reinos

Cada reino tem masmorras próximas. Veja [dungeons-chefes.md](dungeons-chefes.md).
