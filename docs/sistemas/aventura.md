# Aventura: história, temporadas, procurados, Torre, céu e tesouros

[← Índice](../README.md) · Código: `aventura/` (Historia, Temporadas, Procurados, Torre, Ceu, Tesouros) · Tudo no servidor, sem tela própria: avisos do Sistema, chat e comandos.

> Situação (15/11/2026): compilado, ainda **não testado em jogo**.

## História principal (`Historia.java`)

- 14 capítulos em sequência; o progresso fica no jogador (`frx_historia`: capítulo, base do contador).
- O capítulo é apresentado 15 s depois de cada login e em `/historia`. Conclui sozinho quando o objetivo é atingido.
- Tipos de objetivo: registrar na Guilda, rank da Guilda, nível, visitar um reino, contador (`matar_total`, `portais`, `chefes`), andar da Torre e o chefe final.
- Recompensa por capítulo: XP crescente (sem moedas); títulos «Selador de Fendas» (cap. 5), «Veterano» (10) e «Matador do Rei Demônio» (14).
- **Rei Demônio:** `/historia desafio` dentro do reino dos demônios, só no último capítulo. Barra de chefe, invoca Guardas Escarlates e solta uma onda que empurra e aplica Definhar; abaixo de 40% de vida fica em fúria. Quem está no último capítulo e a até 64 blocos conclui junto.
- Admin: `/frx historia <jogador> <capítulo>`.
- **No livro de missões:** capítulo «História Principal» do FTB Quests, logo depois do Prólogo, com uma missão por capítulo. Cada uma é concluída pela conquista oculta `frxworld:frx/historia_N`, que `Historia.concluir` concede via `Marcos.marcar`. Os textos ficam em `tools/gerar_ftbquests.py` (lista `HISTORIA`) e devem acompanhar `Historia.CAPS`.

## Temporadas (`Temporadas.java`)

- Placar do mês do calendário real, em `frx_temporada.json` (pasta do mundo). `/temporada` mostra os 10 primeiros e a sua linha.
- Pontos: chefe 50 · portal 80 · procurado 60 · invasão 40 · tesouro 30 · andar novo da Torre 20 · capítulo da história 100 · abate 1.
- Os contadores de conquistas alimentam o placar por `Conquistas.somar` → `Temporadas.doContador`.
- Na virada do mês: 1º, 2º e 3º ganham 3, 2 e 1 runas de nível II e os títulos «Campeão da Temporada» / «Pódio da Temporada»; quem está offline recebe ao entrar. Admin: `/frx temporada fechar`.

## Mural de procurados (`Procurados.java`)

- A cada 30 min (60% de chance, com jogador fora de capital) nasce um monstro com nome próprio a 70–120 blocos de alguém: nível da zona ou do jogador + 3, vida ×4, dano +40%, brilhando e com coluna de fogo.
- Contrato de 25 min, anunciado a todos com coordenadas clicáveis. `/procurados` repete o aviso.
- Quem matar e quem estiver a até 40 blocos recebe 250 × nível de XP, ponto de temporada, 35% de chance de runa e 25% de Mapa do Tesouro. Sem moedas.
- Admin: `/frx procurado` (cria ao lado) e `/frx procurado encerrar`.

## Torre Infinita (`Torre.java`)

- **Por fora:** torre maciça de 13×13 por 48 de altura, erguida com `/frx torre aqui` (a porta fica na direção para onde o admin olha). Só o saguão é oco; não há como entrar por cima. Posição em `frx_torre.txt`.
- **Proteção:** na torre e nas salas ninguém quebra nem coloca bloco (logo, nem cama nem waystone), balde não funciona, explosões e mobs não destroem, e monstros não nascem naturalmente. Admin em criativo passa.
- **Entrar:** ficar 1,5 s sobre a obsidiana chorosa do saguão. Membros do grupo a até 10 blocos entram juntos; o andar inicial é o menor "andar seguro" do grupo + 1.
- **Salas:** cada grupo recebe uma sala fechada em 24000, 200, 24000 (até 8 ao mesmo tempo).
- **Temas** (um a cada 10 andares, em ciclo): Masmorra (21×21), Deserto (29×29, cactos), Floresta (29×29, árvores), Fornalha (21×21, magma), Geleira (29×29, gelo escorregadio e espigões), Abismo (21×21, pilares). Cada tema tem seus monstros.
- **Barra no topo da tela:** andar, tema, monstros que faltam e tempo; fica verde no andar seguro e vermelha no chefe.
- **Quadros de ranking** ao lado da porta (`frx_torre_placar.json`): os 10 que foram mais longe e os 10 com mais abates na Torre, refeitos a cada 15 s quando mudam. `/torre placar` mostra no chat.
- **Andares:** monstros de nível 3 + 1,4 × andar (até o teto de nível), com +5% de vida e +2% de dano por andar além do nível; de 4 + andar/6 + 2 por jogador monstros (máx. 18); Velocidade I a partir do andar 30 e II do 70. Limite de 6 min por andar e 10 min no chefe.
- **Chefes da Torre:** sorteados em `Chefes.java` conforme o andar (rank do chefe = andar ÷ 15), menos o do 100.
- **A cada 10 andares:** chefe; depois dele, **andar seguro** — progresso salvo, vida cheia, e o grupo decide quando subir (ficar no centro). No 100º, o Senhor da Torre (Wither).
- **Em grupo (até 5):** sem comando e sem precisar de grupo: quando alguém pisa no portal do saguão começa uma contagem de 5 s e sobem juntos todos os que estiverem dentro do saguão (os 5 mais perto do portal). Sair do portal cancela a contagem. Por companheiro a mais: +1 nível nos monstros, +40% de vida, +10% de dano, +3 monstros por andar; chefe com +90% de vida. A XP do andar cresce 50% por companheiro e é dividida igualmente (2 = 75% cada, 5 = 60% cada).
- **Chefes épicos dos mods** nos andares 20, 40, 60 e 80 (`Torre.EPICOS`): sorteio entre Mutant Zombie, Mutant Skeleton e Naga (20); Ferrous Wroughtnaut, Frostmaw, Lich e Invoker (40); Ignis, Ender Guardian, Snow Queen e Mutant Enderman (60); The Harbinger, Maledictus e Ancient Remnant (80). Só entram os que existem no servidor. Os andares 10, 30, 50, 70 e 90 usam `Chefes`; o 100 é o Wither.
- **Loja em janela** (`Torre.MenuLoja`): o Mercador abre uma janela de baú; passar o mouse mostra o preço e clicar compra. `/torre loja` abre a mesma janela.
- **Entre os andares:** pausa de 15 s com botões no chat, [Continuar agora] e [Sair da Torre] (`/torre continuar`, `/torre sair`). Quem sai volta depois ao último andar seguro.
- **Cozinheiro da Torre:** aldeão no andar seguro que vende comida por moedas (`Lojas.comida`).
- **Fichas da Torre:** moeda própria, guardada no jogador (`frx_torre.fichas`), que só se ganha lá dentro: 1 por andar (+2 se for recorde), 10 por chefe, 12% de chance por monstro morto e o baú do andar seguro.
- **Baú do andar seguro:** baú de ender ao lado do centro. Cada jogador abre uma vez por andar seguro: fichas (5 + andar/5), cenouras douradas, diamantes e chance de tomo, mapa do tesouro e item raro. Sem moedas.
- **Loja da Torre:** Mercador no andar seguro, ou `/torre loja` no saguão. Paga em fichas: poções, frascos de XP, tomo, mapa, maçã encantada, netherita, totem, ovo de raposa, título «Mercenário da Torre» e os exclusivos Arco do Vigia, Lâmina da Torre e Asas do Escalador (4 a 250 fichas).
- **Morrer:** é morte de verdade. O jogador é levado para a porta antes de cair, então a lápide com os itens fica do lado de fora. Além da penalidade normal de morte, perde XP do nível atual (2% + 0,3% por nível, teto de 25%, sem perder nível) e fica 10 minutos sem poder entrar. Volta do último andar seguro.
- **XP só ao vencer o andar:** os monstros da Torre não dão XP ao morrer. O andar paga 2 × andar^1,6 (andar 1 = 2, andar 25 = 345, andar 50 = 3.140 com chefe), o triplo no chefe, um quarto em andar já vencido. Não paga moedas. Títulos: «Escalador da Torre» (50) e «Conquistador da Torre» (100).
- Comandos: `/torre`, `/torre sair`; admin `/frx torre aqui|desfazer|andar <n>`.

## Eventos do céu (`Ceu.java`)

- **Lua de Sangue:** 15% de chance por noite. Monstros que nascem ganham Velocidade; XP (`Progressao.xpDoMob`) e loot empilhável de monstros em dobro, só no mundo principal, até amanhecer.
- **Chuva de meteoros:** a cada 75 min, 35% de chance. De 4 a 6 meteoros caem a 60–130 blocos de jogadores, fora das capitais, sem destruir blocos; deixam um bloco de minério (ferro bruto, ouro bruto, lápis, diamante, esmeralda ou detrito ancestral) e uma coluna de fogo por 4 min.
- Admin: `/frx ceu lua|meteoros|parar`.

## Mapas do tesouro (`Tesouros.java`)

- Papel "Mapa do Tesouro" com a pista: distância e direção aproximadas a partir da capital mais próxima.
- Botão direito com o mapa: gelado / frio / morno / quente / pelando e a direção. A até 6 blocos, o baú aparece com blocos de esmeralda, diamantes, ouro e raridades (tomo, sucata de netherita, totem, outro mapa).
- De onde vem: chefes de masmorra e chefe mundial (35%), procurados (25%), portal fechado (20%), chefe novo da Torre (20%), Rei Demônio (100%). Admin: `/frx tesouro`.

## Runas (`Runas.java`)

- Item: fragmento de eco com a marca `frx_runa` (tipo e nível I a III). Pode ser trocado entre jogadores.
- **Encaixe na bigorna:** item à esquerda, runa à direita (3 níveis de XP por nível da runa). Até 2 runas por item, de tipos diferentes; a mesma runa de nível maior substitui a antiga. Ficam em `frx_runas` no item e aparecem na descrição.
- **Subir de nível:** duas runas iguais do mesmo nível na bigorna viram uma do nível seguinte.
- **De arma** (agem ao bater corpo a corpo ou com flecha, e nas habilidades físicas): Sangue (cura 3% do dano por nível), Gelo (10% por nível de congelar 3 s), Fogo (+1 de dano por nível e incendeia), Trovão (8% por nível de raio com dano extra), Fortuna (12% por nível de XP em dobro ao matar), Execução (+12% por nível em alvos abaixo de 30% de vida).
- **De armadura** (somam os níveis de todas as peças vestidas): Pedra (−2% de dano por nível, teto de 30%), Espinhos (devolve 6% por nível), Vigor (+1 coração por nível), Vento (+3% de velocidade por nível), Fênix (salva de um golpe fatal; recarga de 10 min menos 1 por nível, mínimo 3).
- **De onde vêm:** chefes de masmorra e chefe mundial (60%), procurados (35%), chefe novo da Torre (35%, nível conforme o andar), baú de tesouro (30%) e Loja da Torre (30 fichas).
- Ainda não dá para remover uma runa de um item.
- Comandos: `/runas` (lista e explica); admin `/frx runa <tipo> [nível]`.

## Chefões (`Chefes.java`)

- 14 chefões com nome, barra de vida e golpes anunciados na tela, sorteados pelo rank (F a S): do Rei Goblin e da Aranha Rainha (ranks baixos) ao Colosso de Guerra e ao Arauto do Vazio (ranks altos).
- São monstros do jogo reforçados: vida × (4 + 0,7 por rank), resistência a empurrão e nível do conteúdo.
- Golpes, usados a cada 7 s (5 s em fúria): invocar lacaios, onda de choque, raio marcado no chão, chão em chamas, congelar em volta, nuvem de veneno, salto com impacto, cura, chuva de flechas. Alguns entram em **fúria** abaixo de 35% de vida.
- Usados pelas dungeons de portal e pelos andares de chefe da Torre.

## Dungeon de portal (`Fenda.java`)

- O portal (`reinos/Portais.java`) abre com um rank tirado do maior rank da Guilda online. **Só entra quem tem aquele rank ou maior**; os outros são repelidos.
- Encostar 2 s no portal leva o jogador à área temporária do portal (em 24000, 200, 24128). Todos que atravessam o mesmo portal caem na mesma dungeon. Lotação por rank: F 2, E 3, D 3, C 4, B 4, A 5, S 6 (`Fenda.LOTACAO`); quem já entrou conta na lotação mesmo depois de morrer ou sair, e pode voltar.
- Três câmaras de monstros com temas sorteados e, na quarta, um chefão sorteado pelo rank. Limite de 18 minutos; barra no topo da tela.
- **Vitória:** 300 × nível de XP, chance de runa (40%), tomo e mapa do tesouro; o portal fecha. Sem moedas.
- **Morte:** o jogador é levado para o portal antes de cair, e a lápide com os itens fica do lado de fora. Pode entrar de novo enquanto o portal estiver aberto.
- Se ninguém vencer até o portal expirar (20 min, segurado enquanto houver grupo lá dentro), ele se rompe e vira invasão, como antes.
- `/portal sair` desiste.
