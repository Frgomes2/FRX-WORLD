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
- **Encaixe na bigorna:** item à esquerda, runa à direita (3 níveis de XP por nível da runa). Vagas por item conforme o rank da Guilda de quem encaixa (`Runas.vagas`): 2 até D, 3 no C, 4 no B, 5 no A e S, de tipos diferentes (runas já encaixadas continuam valendo se o item passar para alguém de rank menor); a mesma runa de nível maior substitui a antiga. Ficam em `frx_runas` no item e aparecem na descrição.
- **Subir de nível:** duas runas iguais do mesmo nível na bigorna viram uma do nível seguinte.
- **De arma** (agem ao bater corpo a corpo ou com flecha, e nas habilidades físicas): Sangue (cura 3% do dano por nível), Gelo (10% por nível de congelar 3 s), Fogo (+1 de dano por nível e incendeia), Trovão (8% por nível de raio com dano extra), Fortuna (12% por nível de XP em dobro ao matar), Execução (+12% por nível em alvos abaixo de 30% de vida).
- **De armadura** (somam os níveis de todas as peças vestidas): Pedra (−2% de dano por nível, teto de 30%), Espinhos (devolve 6% por nível), Vigor (+1 coração por nível), Vento (+3% de velocidade por nível), Fênix (salva de um golpe fatal; recarga de 10 min menos 1 por nível, mínimo 3).
- **De onde vêm:** chefes de masmorra e chefe mundial (60%), procurados (35%), chefe novo da Torre (35%, nível conforme o andar), baú de tesouro (30%) e Loja da Torre (30 fichas).
- Ainda não dá para remover uma runa de um item.
- Comandos: `/runas` (lista e explica); admin `/frx runa <tipo> [nível]`.

- **Tirar runa:** no sábio das capitais, aba **Runas** da bancada (`reinos/Desencanto.TIRAR_RUNA`, `Runas.tirar`): o jogador põe a arma ou armadura no espaço do item, escolhe a runa e ela sai inteira para a mochila. Preço por nível: I 10 ouro, II 25, III 50 (`desencanto.runa1..3` no painel, Economia → Sábio). Pago com bolsa e, se faltar, banco.

## Chefões (`Chefes.java`)

- 14 chefões com nome, barra de vida e golpes anunciados na tela, sorteados pelo rank (F a S): do Rei Goblin e da Aranha Rainha (ranks baixos) ao Colosso de Guerra e ao Arauto do Vazio (ranks altos).
- São monstros do jogo reforçados: vida × (4 + 0,7 por rank), resistência a empurrão e nível do conteúdo.
- Golpes, usados a cada 7 s (5 s em fúria): invocar lacaios, onda de choque, raio marcado no chão, chão em chamas, congelar em volta, nuvem de veneno, salto com impacto, cura, chuva de flechas. Alguns entram em **fúria** abaixo de 35% de vida.
- Usados pelas dungeons de portal e pelos andares de chefe da Torre.

## Dungeon de portal (`Fenda.java`)

- **Frequência:** uma tentativa a cada `portais.intervaloMinutos` (15) com `portais.chance` % (80) de abrir; só um portal por vez.
- **Recompensa ao derrotar o chefão:** para cada jogador, `300 × nível` de XP e moedas pelo rank do portal (padrão: F 1 ouro, E 2, D 4, C 8, B 15, A 25, S 40; painel **Economia → Recompensa dos portais**, seção `portais`), mais as chances de mapa do tesouro, runa e tomo.
- O portal (`reinos/Portais.java`) abre perto da capital mais próxima de um jogador sorteado entre os que estão a até 800 blocos de uma capital, com rank em volta do rank da Guilda dele: 25% um abaixo, 45% o mesmo, 20% um acima, 10% dois acima (até 1.3.2 era o maior rank online ou um abaixo). **Só entra quem tem aquele rank ou maior**; os outros são repelidos.
- Encostar 2 s no portal leva o jogador à área temporária do portal (em 24000, 200, 24128). Todos que atravessam o mesmo portal caem na mesma dungeon. Lotação por rank: F 2, E 3, D 3, C 4, B 4, A 5, S 6 (`Fenda.LOTACAO`); quem já entrou conta na lotação mesmo depois de morrer ou sair, e pode voltar.
- Três câmaras de monstros com temas sorteados e, na quarta, um chefão sorteado pelo rank. Limite de 18 minutos; barra no topo da tela.
- **Vitória:** 300 × nível de XP, chance de runa (40%), tomo e mapa do tesouro; o portal fecha. Sem moedas.
- **Morte:** o jogador é levado para o portal antes de cair, e a lápide com os itens fica do lado de fora. Pode entrar de novo enquanto o portal estiver aberto.
- Se ninguém vencer até o portal expirar (20 min, segurado enquanto houver grupo lá dentro), ele se rompe e vira invasão, como antes.
- `/portal sair` desiste.

## Encontros (`eventos/Encontros.java`)

Acontecimentos raros e pequenos, sem combate obrigatório. Só na superfície do mundo normal, a mais de 64 blocos das capitais e fora de terrenos e áreas protegidas.

- **Quando:** a cada minuto, o jogador que andou pelo menos 40 blocos tem 10% de chance. Um encontro por jogador de cada vez e no máximo um a cada 40 minutos. O tipo é sorteado.
- **Viajante ferido:** pede uma poção de cura ou de regeneração (clicar nele com a poção na mão). Paga com um Mapa do Tesouro e +20 de reputação com o reino dele. Fica 5 minutos.
- **Mercador perdido:** vende um único item raro, uma única vez, em moedas (totem, maçã encantada, pérolas, netherite, coração do mar, élitros a partir do nível 20, Lâmina do Viajante a partir do 15). Some depois da venda ou em 5 minutos.
- **Estrela caída:** cai a 60–120 blocos, com coluna de luz e coordenada clicável, por 10 minutos. O primeiro que chegar a 4 blocos leva moedas, XP e um item (5% de chance de Estrela do Nether). O chat avisa quem alcançou.
- **Baú fujão:** aparece perto e pula 6 a 9 blocos para longe sempre que o jogador chega a 3 blocos, de 4 a 6 vezes. Depois cansa, enche de loot e fica 3 minutos. O chat avisa quem alcançou.
- **Mensageiro apressado:** entrega uma Carta Lacrada para o taverneiro de um reino sorteado. Entregar (clicar no taverneiro certo com a carta na mão): moedas, XP e +60 de reputação; vale mesmo para quem é Hostil no reino. Abrir (clicar com a carta olhando para o céu): 30% Mapa do Tesouro, 45% moedas, 25% nada.
- NPCs de encontro que sobram de uma queda do servidor somem ao carregar. Teste: `/frx encontro <tipo>`.

## Enigma (`aventura/Enigma.java`) e Conjuntos (`aventura/Conjuntos.java`)

Corridas de pistas pelo mundo. Em cada enigma, só o primeiro personagem a chegar ao fim leva o prêmio, um item do Códex; quem chega depois não encontra nada. Vários enigmas podem correr ao mesmo tempo.

- **Conteúdo fora do código:** pistas, lugares, falas e prêmios ficam em JSON Base64 na pasta do mundo, fora do git (para ninguém estragar a surpresa lendo o projeto): `<mundo>/frx_enigma.dat` (o primeiro enigma) e `<mundo>/frx_enigmas/<id>.dat` (os demais).
- **Campos:** `nome`, `premio` (código no Códex), `abertura` (primeira pista), `etapas` e, opcionais, `inicio` (epoch em ms), `grupo`, `conjunto` e `conjuntoNome`. Cada etapa tem `tipo` (`lugar`, `npc` ou `final`), `fala` (dita ao cumprir) e `pista` (a próxima); `lugar`/`final` usam `dim`, `x`, `z`, `raio` e, opcional, `y`; `npc` usa `papel`, `reino` e, opcional, `item` e `qtd` (consumidos); qualquer etapa aceita `quando` (`dia`, `noite`, `meianoite`). Uma etapa `final` com `chefe` (índice do rank, 0 = F … 6 = S; opcional `chefeNivel`) tem guardião.
- **Começo:** sem `inicio`, até 1 minuto depois de o prêmio existir no Códex. Com `inicio`, o enigma fica **anunciado** antes da hora: o Diário já mostra a primeira pista e a contagem, os NPCs da primeira etapa respondem "volte no dia marcado", e ninguém avança. Na hora (e com o prêmio criado no Códex) começa sozinho, com aviso para todos. Se o prêmio não existir na hora, o enigma espera e o log avisa.
- **Avisos do evento:** antes do início, o chat avisa de hora em hora; na última hora, a cada 10 minutos (e a 5 e a 1 minuto). Quem entra no servidor recebe o aviso com o dia, a hora e quanto falta.
- **Diário do Enigma:** um livro só para todos os enigmas, entregue a quem estiver online ou entrar enquanto houver enigma anunciado ou em andamento (`<mundo>/frx_enigmas/diarios.json`). Clicar com ele na mão abre a tela de livro (`cliente/TelaDiarioEnigma.java`): na página da esquerda, um marcador de fita colorida por enigma, com o estado (aguardando com contagem, em andamento, guardião à espera, desvendado por X, não responde mais a você) e seis contas de progresso; na da direita, o enigma escolhido, com as pistas já descobertas (a atual em destaque, rolagem com a roda do mouse). O servidor só manda ao cliente as pistas que o jogador já descobriu.
- **`/enigma`:** abre o Diário; se o jogador não tem o livro na mochila nem no baú de Ender, recebe um novo (só enquanto houver enigma anunciado ou em andamento).
- **Andamento:** por personagem, no arquivo de estado de cada enigma (`frx_enigma_estado.json` ou `frx_enigmas/<id>.estado.json`). Cada etapa só vale para quem cumpriu a anterior. Criativo e espectador não contam; operador conta (o admin pode jogar).
- **Grupo:** o campo continua nos dados, mas não bloqueia mais (desde a 1.4.3): quem vence um enigma pode fazer e vencer os outros do mesmo grupo. Os prêmios continuam negociáveis.
- **Ao avançar:** a pessoa recebe a fala e a próxima pista; os outros recebem só "alguém desvendou o passo N do <enigma>".
- **Guardião:** ao chegar ao ponto final de um enigma com `chefe`, surge um chefão do rank indicado (os mesmos chefões das dungeons, com golpes e barra de vida) chamado "Guardião do <enigma>". Só um por enigma de cada vez; todos sabem que alguém o despertou. Quando ele cai, vence quem deu o golpe final, se estiver no último passo; se não, quem estiver no último passo mais perto (até 40 blocos). Sem ninguém no último passo a menos de 48 blocos por 60 segundos, ele volta a dormir e pode ser despertado de novo. Guardiões não sobrevivem a um reinício.
- **Fim:** surge um baú (no ponto final, ou onde o guardião caiu) com o prêmio (emitido pelo Códex, contando no limite) e uma Estrela do Nether; anúncio para todos e Mural dos Primeiros (`chegada:enigma` para o primeiro, `chegada:enigma_<id>` para os demais, gravados em `frx_chegadas.json`). Quando não sobra enigma anunciado nem em andamento, o Diário do Enigma some da mochila e do baú de Ender de cada um.
- **Prêmio:** crie no painel (Códex) o item com o código indicado em `premio`, limite 1 e sem venda no ferreiro/armeiro.
- **Conjuntos:** os prêmios com o mesmo `conjunto` formam um conjunto. Quem veste todas as peças ao mesmo tempo recupera vida sozinho enquanto estiver com elas: a cada segundo, `0,5 + 9,5 × (nível/100)²` % da vida máxima (nível 1: 0,5%; 50: ~2,9%; 100 ou mais: 10%, vida cheia em ~10 s). Nos 2 segundos depois de levar dano, a cura cai pela metade. Aviso ao completar, com o ritmo no nível do jogador.


## Moedas dos chefões de masmorra (`dungeon/Recompensa.java`)

O chefão das catacumbas (`Dungeons`) e os chefes de estruturas de outros mods (`Externos`) pagam, a cada jogador perto, `masmorras.moedasPorNivel` (padrão 150 cobre) × nível do chefão, junto com o XP e o loot do painel e dentro da mesma recarga. O guardião de cada andar das catacumbas paga 20% disso por andar, junto com a Chave da Camada. Painel: **Economia → Portais e masmorras**.

## Torneio de pesca (`eventos/Pesca.java`)

- Todo peixe pescado (vanilla e Aquaculture, tag `minecraft:fishes`) ganha peso em kg sorteado na faixa da espécie (grandes são raros: `r^2,2`), nota de 1 a 5 estrelas e a linha "Pescado por" na descrição (NBT `frx_peso`, `frx_peso_pct`).
- Placar semanal (segunda a domingo, horário de Brasília): melhor peixe de cada jogador pela porcentagem da faixa da espécie. Na virada, 1º 30 ouro, 2º 15, 3º 8 (offline recebe no próximo login, por `AjustesAdmin.pendente`); o campeão ganha a conquista Rei da Pesca. Recorde de peso por espécie é permanente (aviso no chat quando um peixe de 50%+ bate o recorde).
- Peixe-Rei Dourado: 0,4% por peixe à noite ou com chuva; 8 a 40 kg, vale 150% no placar, paga 25 ouro na hora e avisa o servidor.
- Conquistas: Pescador (100 peixes), Mestre Pescador (1.000), Rei da Pesca. `/pesca` mostra o placar; `/frx pesca encerrar` fecha a semana na hora. Dados em `<mundo>/frx_pesca.json`; o site mostra o placar.

## Corridas (`aventura/Corridas.java`)

- Pistas criadas pelo admin (`/frx corrida`), cada uma de um tipo: a pé, a cavalo, de barco ou de élitro. Pontos: largada, pontos de controle e chegada (o último).
- Largada: anel de fogo visível a até 48 blocos. Quem fica a até 3 blocos dele do jeito certo recebe no chat o botão **[Iniciar corrida]** (`/corrida iniciar <id>`). Clicando: contagem de 5 s com a montaria travada (lentidão e volta à posição) e fogos no "Vai!". Quem estiver na linha (até raio + 1 blocos), do jeito certo, quando alguém clica larga junto: é uma corrida em grupo, com colocação na chegada, 5 ouro para o vencedor de uma largada com 2 ou mais e o resultado no chat quando o último termina. Uma largada por vez em cada pista; quem chega durante uma corrida vê "pista ocupada".
- Durante a corrida: coluna de luz azul no próximo ponto (dourada na chegada), anel do raio, tempo e distância na barra de ação. O ponto conta dentro do raio (padrão 5; élitro 10) e até 1,5 × raio de altura.
- Cancela: sair do jeito certo por mais de 4 s, teleporte (mais de 40 blocos entre leituras), morrer, sair do jogo, trocar de dimensão ou passar do limite da pista (`/frx corrida limite`, padrão 15 min).
- Volta mais rápida (pistas de 2+ voltas): cada volta é cronometrada; a melhor de todas fica guardada (`voltaRapida`), é anunciada no chat quando batida e aparece em destaque no placar gigante e no site.
- Placar gigante (`/frx corrida placar <id> [tamanho]`): texto flutuante (`text_display`, tag `frx_placar_corrida:<id>`) com os 10 melhores tempos e quem está correndo; refeito a cada largada, chegada e cancelamento (ou quando o chunk carregar).
- Pista de voltas (`/frx corrida voltas <id> <n>`) é um circuito: depois do último ponto marcado, o alvo é a própria linha de largada (não precisa marcar a chegada; se o último ponto já estiver em cima da linha, ele vale como chegada). Ao cruzar a linha antes da última volta, a contagem recomeça e aparece "Volta 2/3"; na última, é a chegada. Pista de uma volta só termina no último ponto (ida de A a B). Depois de chegar, só larga de novo quem sair do anel (mais de 8 blocos).
- Chegada: fogos de artifício; 10 melhores tempos por pista (um por jogador); 1ª chegada do dia em cada pista = 2 ouro; recorde da pista = 10 ouro e aviso no chat. Conquistas: Na Pista, Piloto (50 corridas), Recordista. Dados em `<mundo>/frx_corridas.json`; o site mostra os 5 melhores de cada pista.

