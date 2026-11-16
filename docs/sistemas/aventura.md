# Aventura: história, temporadas, procurados, Torre, céu e tesouros

[← Índice](../README.md) · Código: `aventura/` (Historia, Temporadas, Procurados, Torre, Ceu, Tesouros) · Tudo no servidor, sem tela própria: avisos do Sistema, chat e comandos.

> Situação (15/11/2026): compilado, ainda **não testado em jogo**.

## História principal (`Historia.java`)

- 14 capítulos em sequência; o progresso fica no jogador (`frx_historia`: capítulo, base do contador).
- O capítulo é apresentado 15 s depois de cada login e em `/historia`. Conclui sozinho quando o objetivo é atingido.
- Tipos de objetivo: registrar na Guilda, rank da Guilda, nível, visitar um reino, contador (`matar_total`, `portais`, `chefes`), andar da Torre e o chefe final.
- Recompensa por capítulo: moedas e XP crescentes; títulos «Selador de Fendas» (cap. 5), «Veterano» (10) e «Matador do Rei Demônio» (14).
- **Rei Demônio:** `/historia desafio` dentro do reino dos demônios, só no último capítulo. Barra de chefe, invoca Guardas Escarlates e solta uma onda que empurra e aplica Definhar; abaixo de 40% de vida fica em fúria. Quem está no último capítulo e a até 64 blocos conclui junto.
- Admin: `/frx historia <jogador> <capítulo>`.
- **No livro de missões:** capítulo «História Principal» do FTB Quests, logo depois do Prólogo, com uma missão por capítulo. Cada uma é concluída pela conquista oculta `frxworld:frx/historia_N`, que `Historia.concluir` concede via `Marcos.marcar`. Os textos ficam em `tools/gerar_ftbquests.py` (lista `HISTORIA`) e devem acompanhar `Historia.CAPS`.

## Temporadas (`Temporadas.java`)

- Placar do mês do calendário real, em `frx_temporada.json` (pasta do mundo). `/temporada` mostra os 10 primeiros e a sua linha.
- Pontos: chefe 50 · portal 80 · procurado 60 · invasão 40 · tesouro 30 · andar novo da Torre 20 · capítulo da história 100 · abate 1.
- Os contadores de conquistas alimentam o placar por `Conquistas.somar` → `Temporadas.doContador`.
- Na virada do mês: 1º, 2º e 3º ganham 50, 25 e 10 de ouro e os títulos «Campeão da Temporada» / «Pódio da Temporada»; quem está offline recebe ao entrar. Admin: `/frx temporada fechar`.

## Mural de procurados (`Procurados.java`)

- A cada 30 min (60% de chance, com jogador fora de capital) nasce um monstro com nome próprio a 70–120 blocos de alguém: nível da zona ou do jogador + 3, vida ×4, dano +40%, brilhando e com coluna de fogo.
- Contrato de 25 min, anunciado a todos com coordenadas clicáveis. `/procurados` repete o aviso.
- Quem matar e quem estiver a até 40 blocos recebe 300 + 40 × nível de cobre, 250 × nível de XP, ponto de temporada e 25% de chance de Mapa do Tesouro.
- Admin: `/frx procurado` (cria ao lado) e `/frx procurado encerrar`.

## Torre Infinita (`Torre.java`)

- **Por fora:** torre maciça de 13×13 por 48 de altura, erguida com `/frx torre aqui` (a porta fica na direção para onde o admin olha). Só o saguão é oco; não há como entrar por cima. Posição em `frx_torre.txt`.
- **Proteção:** na torre e nas salas ninguém quebra nem coloca bloco (logo, nem cama nem waystone), balde não funciona, explosões e mobs não destroem, e monstros não nascem naturalmente. Admin em criativo passa.
- **Entrar:** ficar 1,5 s sobre a obsidiana chorosa do saguão. Membros do grupo a até 10 blocos entram juntos; o andar inicial é o menor "andar seguro" do grupo + 1.
- **Salas:** cada grupo recebe uma sala fechada em 24000, 200, 24000 (até 8 ao mesmo tempo).
- **Temas** (um a cada 10 andares, em ciclo): Masmorra (21×21), Deserto (29×29, cactos), Floresta (29×29, árvores), Fornalha (21×21, magma), Geleira (29×29, gelo escorregadio e espigões), Abismo (21×21, pilares). Cada tema tem seus monstros.
- **Barra no topo da tela:** andar, tema, monstros que faltam e tempo; fica verde no andar seguro e vermelha no chefe.
- **Quadros de ranking** ao lado da porta (`frx_torre_placar.json`): os 10 que foram mais longe e os 10 com mais abates na Torre, refeitos a cada 15 s quando mudam. `/torre placar` mostra no chat.
- **Andares:** monstros de nível 2 + andar (acima do teto de nível, a vida continua subindo). Limite de 6 min por andar e 10 min no chefe.
- **A cada 10 andares:** chefe; depois dele, **andar seguro** — progresso salvo, vida cheia, e o grupo decide quando subir (ficar no centro). No 100º, o Senhor da Torre (Wither).
- **Entre os andares:** pausa de 15 s com botões no chat, [Continuar agora] e [Sair da Torre] (`/torre continuar`, `/torre sair`). Quem sai volta depois ao último andar seguro.
- **Cozinheiro da Torre:** aldeão no andar seguro que vende comida por moedas (`Lojas.comida`).
- **Fichas da Torre:** moeda própria, guardada no jogador (`frx_torre.fichas`), que só se ganha lá dentro: 1 por andar (+2 se for recorde), 10 por chefe, 12% de chance por monstro morto e o baú do andar seguro.
- **Baú do andar seguro:** baú de ender ao lado do centro. Cada jogador abre uma vez por andar seguro: moedas, fichas (5 + andar/5), cenouras douradas, diamantes e chance de tomo, mapa do tesouro e item raro.
- **Loja da Torre:** Mercador no andar seguro, ou `/torre loja` no saguão. Paga em fichas: poções, frascos de XP, tomo, mapa, maçã encantada, netherita, totem, ovo de raposa, título «Mercenário da Torre» e os exclusivos Arco do Vigia, Lâmina da Torre e Asas do Escalador (4 a 250 fichas).
- **Cair:** na Torre ninguém morre de verdade. Quem zera a vida é expulso com metade da vida, sem perder itens, e recomeça do último andar seguro.
- Recompensa por andar: (10 + 4 × andar) cobre e 20 × andar XP, ×5 no chefe; um quarto disso em andar já vencido. Títulos: «Escalador da Torre» (50) e «Conquistador da Torre» (100).
- Comandos: `/torre`, `/torre sair`; admin `/frx torre aqui|desfazer|andar <n>`.

## Eventos do céu (`Ceu.java`)

- **Lua de Sangue:** 15% de chance por noite. Monstros que nascem ganham Velocidade; XP (`Progressao.xpDoMob`) e loot empilhável de monstros em dobro, só no mundo principal, até amanhecer.
- **Chuva de meteoros:** a cada 75 min, 35% de chance. De 4 a 6 meteoros caem a 60–130 blocos de jogadores, fora das capitais, sem destruir blocos; deixam um bloco de minério (ferro bruto, ouro bruto, lápis, diamante, esmeralda ou detrito ancestral) e uma coluna de fogo por 4 min.
- Admin: `/frx ceu lua|meteoros|parar`.

## Mapas do tesouro (`Tesouros.java`)

- Papel "Mapa do Tesouro" com a pista: distância e direção aproximadas a partir da capital mais próxima.
- Botão direito com o mapa: gelado / frio / morno / quente / pelando e a direção. A até 6 blocos, o baú aparece com moedas de ouro, diamantes, esmeraldas e raridades (tomo, sucata de netherita, totem, outro mapa).
- De onde vem: chefes de masmorra e chefe mundial (35%), procurados (25%), portal fechado (20%), chefe novo da Torre (20%), Rei Demônio (100%). Admin: `/frx tesouro`.

## Runas (`Runas.java`)

- Item: fragmento de eco com a marca `frx_runa` (tipo e nível I a III). Pode ser trocado entre jogadores.
- **Encaixe na bigorna:** item à esquerda, runa à direita (3 níveis de XP por nível da runa). Até 2 runas por item, de tipos diferentes; a mesma runa de nível maior substitui a antiga. Ficam em `frx_runas` no item e aparecem na descrição.
- **Subir de nível:** duas runas iguais do mesmo nível na bigorna viram uma do nível seguinte.
- **De arma** (agem ao bater corpo a corpo ou com flecha, e nas habilidades físicas): Sangue (cura 3% do dano por nível), Gelo (10% por nível de congelar 3 s), Fogo (+1 de dano por nível e incendeia), Trovão (8% por nível de raio com dano extra), Fortuna (12% por nível de moedas ao matar), Execução (+12% por nível em alvos abaixo de 30% de vida).
- **De armadura** (somam os níveis de todas as peças vestidas): Pedra (−2% de dano por nível, teto de 30%), Espinhos (devolve 6% por nível), Vigor (+1 coração por nível), Vento (+3% de velocidade por nível), Fênix (salva de um golpe fatal; recarga de 10 min menos 1 por nível, mínimo 3).
- **De onde vêm:** chefes de masmorra e chefe mundial (60%), procurados (35%), chefe novo da Torre (35%, nível conforme o andar), baú de tesouro (30%) e Loja da Torre (30 fichas).
- Ainda não dá para remover uma runa de um item.
- Comandos: `/runas` (lista e explica); admin `/frx runa <tipo> [nível]`.
