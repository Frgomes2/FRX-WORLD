/**
 * Notas de atualização do site público (seção "Novidades"), em linguagem de jogador.
 * A mais nova vem primeiro. A cada /publicar, acrescente aqui o que mudou para quem joga
 * (sem nomes de arquivo; os detalhes técnicos ficam em docs/07-changelog.md).
 *
 * VERSÃO (regra do Flavio, 17/11/2026) — a mesma do core mod (core-mod/gradle.properties → mod_version):
 *   - cada atualização comum soma 1 no último número: 0.41.1, 0.41.2, 0.41.3...
 *   - depois do .9 vira o número do meio e o último zera: 0.41.9 → 0.42.0;
 *   - só uma mudança muito grande, que mexe em muita coisa, abre uma versão cheia: 1.0.0.
 *   Cada nota nova leva a versão seguinte à da nota de cima, e o mod_version sobe junto.
 *
 * hora = quando a atualização entrou no ar (hh:mm:ss, horário de Brasília): a do reinício que a aplicou.
 * As notas antigas sem hora registrada ficam só com a data.
 */
export type NotaAtualizacao = { versao: string; data: string; hora?: string; titulo: string; itens: string[] };

export const NOTAS: NotaAtualizacao[] = [
  {
    versao: "3.0.6", data: "26/11/2026", hora: "14:00:00", titulo: "Pontos públicos no mapa",
    itens: [
      "Marque um ponto no JourneyMap como sempre e coloque no grupo Público (ou comece o nome com *): ele aparece no mapa de todos os jogadores.",
      "Só quem marcou pode apagar: apagou no seu JourneyMap, some para todo mundo. O número de pontos públicos cresce com o rank da Guilda: 3 no F, 4 no E, 5 no D, 6 no C, 7 no B, 8 no A e 10 no S.",
    ],
  },
  {
    versao: "3.0.5", data: "26/11/2026", hora: "13:37:00", titulo: "Armaduras samurai, Paladino com escudo e reinos no mapa",
    itens: [
      "Armaduras do Epic Samurais mais fortes: as de ferro, ouro e aço ganham +1 de armadura por peça; as de diamante e pedras preciosas, +2 de armadura, resistência e vida; as de netherite, Battlemage e Warden, +3 de armadura, resistência, 1 coração e resistência a empurrão por peça.",
      "Paladino com escudo na mão leva 15% menos dano, e cada golpe bloqueado devolve 30% do dano a quem atacou e cura meio coração.",
      "Os reinos aparecem no mapa de todos os jogadores, com o contorno e o nome da capital.",
      "Biblioteca do site atualizada: livro novo das Terras Inexploradas e páginas de anéis por rank, Bênção da Hinata, Treinador de Criaturas, equipamento samurai, Sala do Tempo, Titã, caderno negro, duelos X1 e masmorras dos reinos.",
    ],
  },
  {
    versao: "3.0.4", data: "26/11/2026", hora: "12:21:00", titulo: "Anéis, habilidades e Assassino",
    itens: [
      "O Anel de Voo agora só aparece nos baús do salão seguro do Labirinto do Dragão. Ele não sai mais nos baús de chefe das masmorras nem nas cidades do End.",
      "Anel de Vida pelo rank da Guilda: dá 2 corações no rank F, 3 no E, 4 no D, 5 no C, 6 no B, 8 no A e 10 no S.",
      "Habilidades não acertam mais bichos de jogador: animais domados, cavalos, dragões e criaturas de mods com dono, além dos NPCs.",
      "Assassino: o Passo Fantasma deixa o alvo atordoado por um instante, sem o contragolpe na hora. Mil Cortes agora some e reaparece em volta do alvo, golpeando 6 vezes. A Dança da Morte ficou mais visível, com rastro de sombra entre os saltos.",
    ],
  },
  {
    versao: "3.0.3", data: "26/11/2026", hora: "12:08:00", titulo: "Anéis por rank e voo com fôlego",
    itens: [
      "Os espaços de anel agora crescem com o rank da Guilda: F 2, E 4, D 7, C 11, B 15, A 20 e S 27. No rank S dá para usar todos os 27 anéis ao mesmo tempo.",
      "Se o seu rank libera menos anéis do que você usava, os que sobrarem voltam para a mochila. Subir de rank libera os espaços na hora.",
      "Anel de Voo com fôlego: 90 segundos de voo com o fôlego cheio, que enche de novo em 45 segundos com os pés no chão. Vale igual para todas as classes.",
      "O voo também para por 5 segundos ao bater ou apanhar, e não funciona na Torre, em X1, na Fenda nem com um chefão por perto.",
      "X1: os anéis agora saem em qualquer duelo, não só no de kit sorteado, e voltam no fim.",
    ],
  },
  {
    versao: "3.0.2", data: "26/11/2026", hora: "11:44:00", titulo: "Katanas mais fortes",
    itens: [
      "As katanas, wakizashis e odachis do Epic Samurais batem mais forte: +3 de dano na katana comum, +4 na de netherite e +5 nas katanas de yokai (Oni, Kitsune, Onibi, Akaname e Jorogumo).",
    ],
  },
  {
    versao: "3.0.1", data: "26/11/2026", hora: "10:59:00", titulo: "Expedições no site",
    itens: [
      "Nova página Expedições no site: a expedição atual, a contagem para a próxima, quem está lá agora, o ranking e o histórico de todas.",
      "Cada expedição tem seu ranking e o MVP, com área explorada, estruturas descobertas, chefes, baús de tesouro e quedas.",
      "A ficha de cada jogador agora mostra as expedições: participações, estruturas, chefes, área explorada, vezes MVP e as últimas colocações.",
    ],
  },
  {
    versao: "3.0.0", data: "26/11/2026", hora: "10:21:00", titulo: "Terras Inexploradas",
    itens: [
      "Novo mundo de expedição: uma área de terras nunca vistas, renovada a cada 2 dias às 5h, com estruturas e masmorras novas. Entrada pelo pórtico das Terras Inexploradas, a partir do Rank D da Guilda.",
      "Cair na expedição não tira seus itens: você volta ao mundo principal e espera 30 minutos para entrar de novo. Lá não funcionam portais, camas nem waystones, e tudo o que for construído some na troca.",
      "Use /expedicao para ver a expedição atual e quanto falta para a troca.",
    ],
  },
  {
    versao: "2.0.16", data: "26/11/2026", hora: "09:44:00", titulo: "Mapa pré-gerado",
    itens: [
      "O mundo em volta dos reinos (10 mil por 10 mil blocos) está sendo gerado antes, com o servidor vazio. Explorar essa área fica mais leve e sem travadas.",
      "A geração pausa sozinha quando alguém entra e continua quando o servidor fica vazio.",
    ],
  },
  {
    versao: "2.0.15", data: "26/11/2026", hora: "09:44:00", titulo: "Reiniciar só o painel",
    itens: [
      "Atualizações do site e do painel agora podem entrar sem reiniciar o jogo: o painel reinicia sozinho e ninguém é desconectado.",
    ],
  },
  {
    versao: "2.0.14", data: "26/11/2026", hora: "09:12:00", titulo: "Servidor não cai mais junto com o painel",
    itens: [
      "O jogo agora roda separado do painel de administração. Se o painel cair, o servidor continua no ar e ninguém é desconectado; o painel volta sozinho e reassume o controle.",
    ],
  },
  {
    versao: "2.0.13", data: "26/11/2026", hora: "08:56:00", titulo: "Suporte de armadura protegido e Anel de Voo no rank B",
    itens: [
      "Suportes de armadura dentro do terreno de alguém agora têm a mesma proteção dos baús: quem não tem permissão não tira nem põe peças.",
      "Flecha, explosão, fogo e lava também não destroem mais suportes de armadura, molduras e quadros dentro de terreno comprado.",
      "Anel de Voo: agora só funciona a partir do rank B da Guilda. Quem estiver abaixo não consegue colocar, e quem já estava usando recebe o anel de volta na mochila (com queda lenta para não cair do céu).",
    ],
  },
  {
    versao: "2.0.12", data: "26/11/2026", hora: "08:43:00", titulo: "Bênção da Capitã Hinata, Pokébola e mods novos",
    itens: [
      "Em Lubelius, a Capitã Hinata agora abençoa a sua armadura: cada peça vestida pode ganhar um bônus ligado à sua classe (por exemplo, roubo de vida para o Guerreiro e dano das habilidades para o Mago).",
      "São cinco graus, com chance de 100, 80, 60, 40 e 20% e preço de 10 a 150 de ouro. Os graus IV e V pedem material de chefe e reputação Honrado com Lubelius. Se falhar, perde o pagamento, mas a peça fica como estava.",
      "A bênção só vale enquanto você for da classe dela; peça abençoada para outra classe recomeça do grau I.",
      "Pokébola: compre com o Treinador de Criaturas e jogue num bicho pacífico ou num bicho domado por você para guardá-lo; jogue de novo para soltar onde ela cair. Bicho domado por você é sempre capturado; selvagem tem chance (maior se estiver ferido).",
      "Cada jogador compra até 10 pokébolas, cada uma mais cara (5 ouro a primeira, 750 a décima). Ela fica presa a quem comprou: só o dono joga, pega do chão e solta. Não queima nem explode.",
      "A pokébola não pega monstros, aldeões, golens, NPCs, chefes, bichos de outra pessoa nem bichos dentro do terreno de outra pessoa.",
      "A Esfera Portátil de Pets foi aposentada: não captura mais nem tem receita, mas quem ainda tem bicho dentro dela consegue soltar.",
      "Mods novos: Traveler's Backpack, Advanced Netherite, Integrated Stronghold, Stalwart Dungeons, Better Villages e Shield Expansion. E, para desempenho e conforto: Clumps, Entity Culling, ImmediatelyFast, AppleSkin, Enchantment Descriptions, Controlling, Packet Fixer e AllTheLeaks.",
    ],
  },
  {
    versao: "2.0.11", data: "25/11/2026", hora: "23:58:00", titulo: "Pórticos de masmorra nas capitais apertadas",
    itens: [
      "Nas capitais sem espaço para o pórtico grande (Sarion, Leste e Sakura), o portal da masmorra passa a usar uma versão menor: um pórtico de 5×5 ou só o portal no chão, com o nome flutuando.",
    ],
  },
  {
    versao: "2.0.10", data: "25/11/2026", hora: "23:46:00", titulo: "Contador do chefe na masmorra",
    itens: [
      "Quando o chefe de uma masmorra está morto, quem entra nela vê no alto da tela uma barra com o nome dele e o contador, rodando, até ele voltar.",
      "Ajuste na escolha automática do lugar dos pórticos de masmorra nas capitais (Sarion, Leste, Sakura e Lordes Demônios).",
    ],
  },
  {
    versao: "2.0.9", data: "25/11/2026", hora: "23:35:00", titulo: "Toda capital com portal de masmorra e chefe",
    itens: [
      "Todo reino agora tem um pórtico de masmorra dentro da capital: pise no portal para ir direto à masmorra do reino e use o portal de lá para voltar.",
      "Os pórticos aparecem no mapa (JourneyMap) como um quadrado roxo com o nome da masmorra.",
      "Nações do Oeste e Lordes Demônios, que não tinham masmorra, ganharam uma embaixo da capital, com chefe.",
      "O Mosteiro do Leste e o Templo de Sakura, que não tinham chefe, agora têm: o Remanescente Ancestral e o Lich.",
      "Todos os chefes de masmorra renascem 7 dias depois de mortos.",
    ],
  },
  {
    versao: "2.0.8", data: "25/11/2026", hora: "05:54:00", titulo: "Chefes de masmorra renascem e Titã corrigido",
    itens: [
      "Corrigido: o Titã só levava dano nas pernas. Agora golpes e flechas acertam também o tronco e a cabeça.",
      "A vida do Titã passou a ser fixa: 5.000, não importa quantos jogadores estejam online.",
      "NPCs dos reinos e moradores não atravessam mais portal do Nether (nem nenhum outro portal).",
      "Os chefes das masmorras que ficam embaixo dos reinos (e do coliseu) agora renascem 7 dias depois de mortos. O servidor avisa no chat quando um deles volta.",
    ],
  },
  {
    versao: "2.0.7", data: "24/11/2026", hora: "22:26:00", titulo: "Titã maior e no mapa, e regras novas no X1",
    itens: [
      "Quem ajuda a derrubar o Titã agora também ganha reputação em Lubelius: +150 para cada um que causou dano e +300 para quem mais feriu (além do ouro e do XP).",
      "O Titã ficou 3 vezes maior: agora tem uns 35 blocos de altura. O pisão alcança mais longe e a nuca fica bem mais alta.",
      "O XP da vitória contra o Titã ficou em 10% de um nível.",
      "X1 com kit sorteado: os anéis e os outros acessórios também ficam guardados durante a luta e voltam no fim, nos mesmos espaços.",
      "X1: regra nova \"Vida\". \"Igual para os dois\" deixa os dois com 20 corações (com kit) ou com a vida de quem tem menos (com os itens do personagem); \"a de cada um\" mantém a vida máxima de cada jogador.",
      "O Titã aparece no mapa: um quadrado vermelho mostra onde ele está e um pontilhado laranja mostra de onde ele veio e o caminho até Lubelius.",
    ],
  },
  {
    versao: "2.0.6", data: "24/11/2026", hora: "19:56:00", titulo: "Titã à solta: defendam Lubelius",
    itens: [
      "Evento novo: no máximo uma vez por dia, e só com 3 ou mais jogadores online, um Titã de 12 blocos de altura aparece longe de Lubelius e marcha para as muralhas. Todo o servidor é avisado e vê a barra com a vida dele e a distância que falta.",
      "Qualquer golpe fere, mas o ponto fraco é a nuca: acertar por trás e do alto vale 3 vezes. A cada 20% de vida perdida ele cambaleia e para por alguns segundos. Cuidado com o pisão: ele avisa antes.",
      "Se o Titã cair, quem ajudou ganha 30 de ouro e XP, e quem mais feriu ganha o dobro do ouro. Se ele chegar às muralhas, os NPCs de Lubelius ficam 30 minutos sem atender.",
    ],
  },
  {
    versao: "2.0.5", data: "24/11/2026", hora: "19:38:00", titulo: "Sala do Tempo: saída mais clara",
    itens: [
      "A saída da Sala do Tempo ganhou uma placa flutuante no centro (\"Agache no círculo dourado\") e o lembrete aparece na barra de ação o tempo todo.",
      "Corrigido: quem entrava na sala no modo criativo não conseguia sair pelo círculo.",
    ],
  },
  {
    versao: "2.0.4", data: "24/11/2026", hora: "19:24:00", titulo: "Death Note: agora a Morte vem buscar",
    itens: [
      "O Death Note mudou: quem tem o nome escrito não cai mais sozinho em 40 segundos. O caderno invoca a Morte, que persegue só essa pessoa, vai atrás dela onde ela for e só para quando levar o alvo ou for derrotada.",
      "A Morte tem 2.000 de vida em duas fases (com o manto e depois a forma verdadeira) e uma barra de chefe na tela. Qualquer jogador pode ajudar a derrubar.",
      "Quem cai para a Morte continua sem perder itens, XP ou nível, e quem escreveu o nome continua anônimo.",
    ],
  },
  {
    versao: "2.0.3", data: "24/11/2026", hora: "19:19:00", titulo: "Death Note, Sala do Tempo e X1 no site",
    itens: [
      "Death Note: evento raro. Um caderno negro cai do céu com um raio e o servidor inteiro é avisado do lugar. Quem pegar tem 30 minutos para clicar com ele e escolher o nome de um jogador online: a vítima tem 40 segundos e cai de ataque cardíaco. É só susto (não perde itens, XP nem nível) e ninguém fica sabendo quem escreveu.",
      "Sala do Tempo: passagem nova no fundo do salão do Templo Karin, para rank B ou maior na Guilda. Lá dentro o XP vale em dobro, o dano recebido também, a fome é mais rápida e aparecem monstros de treino no seu nível. São 30 minutos por dia; para sair, agache no círculo dourado do centro.",
      "Príncipe Vegeta com mais vida: de 700 para 1.000 por participante. A luta dura mais.",
      "O site ganhou a área do X1: o ranking de quem mais venceu (com derrotas, empates e melhor sequência) e as últimas lutas, com quem enfrentou quem e quem levou.",
    ],
  },
  {
    versao: "2.0.2", data: "23/11/2026", hora: "21:13:00", titulo: "X1: duelo em arena, Saiyajin no nome e mais anéis",
    itens: [
      "X1: nova aba no inventário (espada de diamante) para desafiar quem está online para um duelo em arena, com regras combinadas (habilidades, itens do personagem ou kit sorteado, poções, melhor de 3 e aposta). Morrer no duelo não tem punição e o inventário volta igual no fim.",
      "A raça Saiyajin agora aparece no nome e no TAB (as outras lendárias continuam disfarçadas até serem reveladas).",
      "Quando alguém acha uma Esfera do Dragão, todo o servidor vê um título na tela e ouve um som, além do aviso no chat.",
      "Mais 5 espaços de anel: agora são 15 (continua sem poder repetir o mesmo anel).",
      "Áreas protegidas (Templo Karin, labirinto): monstros não mexem mais nos blocos e balde de água ou lava não despeja lá dentro.",
    ],
  },
  {
    versao: "2.0.1", data: "23/11/2026", hora: "19:43:10", titulo: "Santuário maior no topo do Templo Karin",
    itens: [
      "O topo do Templo Karin virou um santuário bem maior (60 blocos de largura): espaço de sobra para lutar, templo de cúpulas douradas, mirantes e fileiras de árvores.",
      "A chegada da escada no topo ganhou um alçapão, para ninguém cair no meio da luta.",
      "Waystone \"Templo Karin\" no topo: quem já subiu uma vez pode voltar direto.",
      "Radar do Dragão com visor: ao clicar, abre a tela redonda verde com os pontos laranja das esferas (norte para cima, você no centro; + e − mudam o alcance). A lista no chat continua.",
    ],
  },
  {
    versao: "2.0.0", data: "22/11/2026", hora: "22:19:19", titulo: "O sangue Saiyajin: Templo Karin e Príncipe Vegeta",
    itens: [
      "Nova raça lendária: Saiyajin. +15% de dano e +2 corações; a fome gasta mais rápido.",
      "Zenkai: escapar de uma luta com menos de 20% de vida deixa o Saiyajin mais forte para sempre (+1% de dano, até +15%).",
      "Transformações com a tecla U: Super Saiyajin (nível 20), Super Saiyajin 2 (nível 40) e Super Saiyajin 3 (nível 60), com animação, aura, raio e cabelo dourado. Agachado + U volta ao normal.",
      "Templo Karin: uma torre altíssima a leste de Lubelius. Só se sobe no braço, com ventania nas partes altas (agache para se segurar). Élitro, voo, montaria e pérola não funcionam lá.",
      "No topo, o Príncipe Vegeta: rajadas de ki, Galick Ho, Big Bang, chuva de rajadas e, no fim, o Final Flash. Todo ataque avisa antes para dar tempo de desviar.",
      "Dá para lutar em grupo, mas ele fica mais forte com cada um que entra. Quem vence libera a raça Saiyajin.",
      "As 7 Esferas do Dragão: um conjunto só no servidor. Quatro estão enterradas no mundo normal, uma no Nether, uma com o Dragão do Fim e uma com a Rainha da Neve da Floresta do Crepúsculo.",
      "Radar do Dragão no mercador (50 ouro): mostra onde está cada esfera, inclusive com qual jogador. Quem morre carregando uma esfera deixa ela cair.",
      "Com as 7, clique com uma esfera a céu aberto para chamar Shenlong e escolher 1 de 8 desejos: Poder, Riqueza, Tesouro, Arsenal lendário, Divindade, Bênção do Dragão, Imortalidade ou Glória ao clã. Depois as esferas viram pedra por 7 dias.",
      "A armadura do Alvorecer (relíquia dos enigmas) não desgasta mais; as peças já gastas voltaram inteiras.",
    ],
  },
  {
    versao: "1.4.5", data: "22/11/2026", hora: "20:48:45", titulo: "Mais mariposas nas Cavernas Desoladas",
    itens: [
      "Cavernas Desoladas (Alex's Caves): bem menos morcegos e mais Mariposas Sombrias. Dica: acenda tochas, elas se juntam em volta da luz.",
      "Estabilidade: o painel volta a derrubar e religar o jogo junto quando trava, e passa a guardar um relatório do travamento para acharmos a causa das quedas.",
    ],
  },
  {
    versao: "1.4.4", data: "22/11/2026", hora: "16:29:39", titulo: "NPCs não ficam mais um dentro do outro",
    itens: [
      "Regra nova: dois NPCs nunca ficam no mesmo bloco. Se acontecer, um deles anda sozinho para o bloco livre mais perto.",
      "Distrito Sakura (Império do Leste): Leiloeiro, Mercador de Escravos, Costureira e Mercador de Discos saíram de cima dos vizinhos.",
      "Esfera Portátil de Pets traduzida (guarda bichos domados no item e solta onde você quiser).",
      "Gaiola (Supplementaries): agora guarda tartarugas, cágados e qualquer filhote, para levar bichos para a sua base.",
      "Labirinto do Dragão mais fácil: monstros dos geradores com metade da vida e da armadura, menos dano e nascendo em menor quantidade.",
    ],
  },
  {
    versao: "1.4.3", data: "22/11/2026", hora: "14:52:35", titulo: "Invocações no PvP e servidor mais estável",
    itens: [
      "Com \"Dano em jogadores\" ligado no Exército, suas invocações agora também podem ser atingidas pelas habilidades e magias de outros jogadores (antes só o ataque corpo a corpo pegava).",
      "Invocações com alvo marcado não largam mais a perseguição para voltar até você: seguem o alvo até 48 blocos de distância do dono.",
      "Correção de quedas: o servidor do jogo não cai mais junto quando a janela do painel recebe um Ctrl+C.",
      "Enigmas: quem desvenda um enigma agora pode continuar e desvendar os outros do mesmo evento (antes ficava bloqueado depois do primeiro).",
      "Artefatos traduzidos: Totem do Coro, Chapéu de Pescador, Chapéu de Caubói, Anel de Cebola, Raquetes de Neve e as descrições que ainda estavam em inglês.",
    ],
  },
  {
    versao: "1.4.2", data: "22/11/2026", hora: "12:53:00", titulo: "Armaduras lendárias, forja até +15 e corrida em grupo",
    itens: [
      "Corrida em grupo: quando alguém clica em [Iniciar corrida], todos que estão na linha de largada, do jeito certo, largam juntos.",
      "Cada um que cruza a chegada vê a sua colocação (1º, 2º, 3º...), e no fim sai o resultado no chat. Quem vence uma largada com dois ou mais leva 5 ouro.",
      "Os tempos de todos continuam valendo para o placar de recordes da pista.",
      "Invocador: com \"Dano em jogadores\" ligado no menu do Exército, marcar um jogador faz as invocações atacarem ele, mesmo que seja do seu clã. Com a opção desligada, aparece o aviso para ligar.",
      "Novo botão \"Dano em jogadores\" na janela de Habilidades (canto de baixo, à esquerda): ligado, suas habilidades e o Despertar ferem outros jogadores, inclusive do seu clã. Desligado por padrão; precisa do PvP do servidor ativo.",
      "Armaduras depois do netherite, feitas na mesa de ferraria: Draconiana (Rank B), Abissal (Rank A) e Celestial (Rank S), cada uma com bônus de conjunto. Os materiais vêm de dragões, chefes especiais, da Torre e do Dragão do Fim.",
      "Forja além do +10: quem é Rank A ou S pode aprimorar até +15 com Essências Abissais e Fragmentos Celestiais.",
      "Livro novo na biblioteca: Depois do Netherite, com todos os caminhos de armadura para os mais fortes.",
      "Mais runas por item conforme o rank da Guilda: 2 até o Rank D, 3 no C, 4 no B e 5 no A e no S.",
      "Volta mais rápida: em pistas de várias voltas, quem faz a volta mais rápida fica em destaque no placar gigante e no site, e o servidor é avisado quando ela é batida.",
    ],
  },
  {
    versao: "1.4.1", data: "22/11/2026", hora: "10:40:59", titulo: "Corridas com largada, fogos e placar gigante",
    itens: [
      "Na largada da corrida aparece o botão [Iniciar corrida] no chat. Clicando, começa uma contagem de 5 segundos com a montaria travada, e no \"Vai!\" estouram fogos de artifício.",
      "Na chegada, mais fogos, o tempo aparece na tela e, se for recorde, o servidor inteiro fica sabendo.",
      "Pista de voltas termina na linha de chegada (a mesma da largada), depois de passar por todos os pontos, e não no último ponto.",
      "Só uma corrida por vez em cada pista: quem chega na largada durante uma corrida vê que a pista está ocupada.",
      "Cada pista tem um tempo máximo: se não terminar a tempo, a corrida é cancelada.",
      "Placar gigante na pista com os 10 melhores tempos e quem está correndo agora.",
    ],
  },
  {
    versao: "1.4.0", data: "22/11/2026", hora: "10:01:00", titulo: "Torneio de pesca, corridas e cidades mais seguras",
    itens: [
      "Torneio de pesca: todo peixe agora tem peso e estrelas. Vale o maior peixe para a espécie, e domingo à meia-noite os três primeiros da semana ganham 30, 15 e 8 ouro. Placar no site e em /pesca.",
      "À noite ou com chuva, o lendário Peixe-Rei Dourado pode morder (25 ouro na hora).",
      "Hipódromo perto de Lubelius: explosão não estraga nada e monstro não nasce nem fica lá dentro.",
      "Corridas: pistas a pé, a cavalo, de barco e de élitro. Fique no anel de fogo da largada e siga a coluna de luz. Recordes no site e em /corridas; a primeira do dia em cada pista paga 2 ouro e o recorde, 10.",
      "As capitais ficam limpas: fora das invasões, monstro comum que entra andando na cidade some (os guardas expulsam).",
      "Invasões menos frequentes: uma tentativa por hora, com 25% de chance, e a mesma capital só é atacada de novo depois de 3 horas.",
      "O livro encantado vazio (sem nenhum encantamento) agora serve na bancada do sábio no lugar do livro em branco, para guardar o encantamento extraído.",
    ],
  },
  {
    versao: "1.3.9", data: "22/11/2026", hora: "02:56:07", titulo: "Ferramentas, escudos e élitros com classe",
    itens: [
      "Agora tudo tem classe: picaretas, pás e enxadas seguem o material (pedra e ouro F, ferro E, diamante D, netherite C). Acima do seu rank, a ferramenta não quebra blocos.",
      "Ferramenta reforjada com dano de arma fica com a classe do dano, se for maior que a do material.",
      "Escudos e itens de outros mods que dão armadura ou dano também ganharam classe.",
      "Élitros são de classe A: só aventureiros de Rank A ou superior da Guilda vestem e voam.",
      "Menos ouro fácil: portais pagam de 1 ouro (Rank F) a 40 ouro (Rank S), e os chefões das catacumbas pagam 0,4 ouro por nível.",
      "Chefes dos outros mods voltam a pagar uma vez a cada período de espera, e as moedas seguem a dificuldade: os comuns pagam pouco (o Aptrgangr dá 6 ouro) e só os chefes muito difíceis pagam muito (o Ignis dá 108 ouro, o Tremorzilla 192).",
      "Invocador: ao tentar marcar um companheiro de clã como alvo, agora aparece o aviso de que as invocações não atacam gente do próprio clã (antes a tropa só andava até o lugar, sem explicar).",
    ],
  },
  {
    versao: "1.3.8", data: "21/11/2026", hora: "22:55:54", titulo: "Correção na Torre Infinita",
    itens: [
      "Corrigido: depois de vencer o chefe de um andar 10, 20, 30..., quem estava perto da parede podia cair da sala quando ela muda de tema e ser mandado para fora da Torre, em vez de ir para o andar seguro.",
      "Agora todos vão para perto do centro antes de a sala nova aparecer. E se alguém ainda assim cair por baixo, volta para dentro da sala em vez de sair da Torre.",
      "O progresso já ficava salvo: quem saiu assim recomeça do andar seguro certo.",
    ],
  },
  {
    versao: "1.3.7", data: "21/11/2026", hora: "20:55:31", titulo: "Chefes dos outros mods pagam melhor",
    itens: [
      "Chefes dos outros mods (como o Aptrgangr, o Ignis e o Maledictus) agora pagam moedas em toda vitória, não só na primeira do dia.",
      "Quanto maior o seu nível, maior o prêmio: as moedas somam o nível do chefe com o seu, e a experiência também cresce com o seu nível.",
      "Experiência e itens extras continuam com tempo de espera para cada chefe.",
    ],
  },
  {
    versao: "1.3.6", data: "21/11/2026", hora: "20:24:54", titulo: "Papai Noel, Nether e End mais difíceis",
    itens: [
      "O Nether agora vem depois do mundo normal inteiro: monstros do nível 55 ao 80, ainda mais duros que os da superfície. A entrada continua liberada a partir do Rank C da Guilda, mas é perigoso ir antes de ficar forte.",
      "O End é a fase mais difícil: monstros do nível 75 ao 100, com muito mais vida e dano. Só entra quem é Rank A.",
      "O Dragão do Fim virou um chefão de Rank A: nível 80, 2500 de vida e golpes com o dobro de força. Abaixo do nível do Rank A, seus ataques mal arranham.",
      "Enquanto o dragão vive, a ilha manda reforços contra quem está lutando: endermen, fantasmas, vexes e, de vez em quando, um Guardião do Dragão.",
      "Todo mundo que ajudar a derrotar o dragão (quem causar dano nele) ganha 50 mil moedas de ouro direto no banco e a conquista Matador de Dragões, que aparece no seu perfil do site.",
      "Monstros de nível maior também dão mais experiência e mais moedas, então vale a pena para quem está pronto.",
      "Evento raro novo: o Papai Noel. De vez em quando (tão raro quanto o Ronaldinho), ele aparece perto de você de um jeito ou de outro.",
      "No trenó: ele passa voando com as renas, pousa, confere a lista e entrega um presente para quem se comportou. Quem anda brigando com os guardas ganha carvão. Na partida, ainda solta uma chuva de presentes.",
      "Perdido: o trenó caiu e as 3 renas fugiram. Você tem 5 minutos para achar e tocar nas três (elas brilham). Quem conseguir ganha 3 presentes e 20 moedas de ouro.",
      "Caixa de Presente: clique com o botão direito para abrir. Pode vir ouro, comida, fogos, frascos de experiência ou, com sorte, uma runa, um Tomo Arcano, um tesouro ou o Gorro de Natal.",
      "Gorro de Natal: um enfeite para a cabeça, sem proteção, só para o estilo.",
      "Chefões dos outros mods (como o Ignis, o Netherite Monstrosity e o Wither) agora entram na lista de chefões derrotados da coleção e aparecem no seu perfil do site.",
      "Vale para todos que estavam perto na luta, como já era com os chefões do servidor.",
    ],
  },
  {
    versao: "1.3.5", data: "21/11/2026", hora: "18:42:41", titulo: "Correção na dungeon dos portais",
    itens: [
      "Corrigido: na troca de câmara da dungeon do portal, quem estava perto da borda podia ficar sem chão, cair da sala e ser mandado para longe. Agora o grupo vai para o centro antes de cada câmara nova.",
      "Se alguém ainda assim cair da sala, volta para o centro da dungeon em vez de ser expulso.",
    ],
  },
  {
    versao: "1.3.4", data: "21/11/2026", hora: "18:10:13", titulo: "Mais portais, chefões que pagam e runas que saem",
    itens: [
      "Portais aparecem bem mais: uma tentativa a cada 15 minutos (antes 40), com 80% de chance (antes 50%). Em média, um portal novo uns 20 minutos depois que o anterior fecha.",
      "Chefões das masmorras (as catacumbas debaixo das vilas e os chefes das estruturas de outros mods) agora pagam moedas: 1,5 ouro por nível do chefão, para cada jogador. Um chefão nível 30 rende 45 ouro.",
      "Os guardiões de cada andar das catacumbas também pagam uma parte, junto com a Chave da Camada.",
      "O prêmio continua respeitando o tempo de recarga de cada masmorra.",
      "O teto de armadura subiu de 30 para 100. Cada ponto a mais ainda protege, com retorno cada vez menor: com 30 de armadura passa 25% do dano de um golpe comum; com 40, 20%; com 60, 14%; com 100, 9%.",
      "A bancada do sábio ganhou a aba Runas: tira uma runa encaixada da arma ou armadura, e ela volta inteira para você. Custa 10 ouro (nível I), 25 (II) ou 50 (III).",
    ],
  },
  {
    versao: "1.3.3", data: "21/11/2026", hora: "12:00:35", titulo: "Mesa de costura e portais que pagam, no seu nível",
    itens: [
      "A Costureira ganhou uma mesa de costura com cinco abas e espaços de verdade, como a bigorna.",
      "Bolsos: costure bolsos na calça, um por vez ou todos de uma vez (2 ouro cada).",
      "Passar bolsos: trocou de calça? Ela passa os bolsos da velha para a nova por 50 cobre cada, bem mais barato que costurar de novo.",
      "Remendos: armaduras e élitros consertados por completo, com preço pelo estrago e pelo material.",
      "Bordados: acabamento de armadura (padrão e cor à escolha, com prévia) por 15 ouro, e nome colorido em qualquer peça por 5 ouro.",
      "Disfarce: por 30 ouro, um manto de 30 minutos em que os guardas não reconhecem quem é Hostil ao reino. Não vale para Inimigo, e atacar alguém na cidade rasga o manto.",
      "Portais de masmorra agora pagam moedas: cada jogador que derrota o chefão ganha de 5 ouro (Rank F) até 150 ouro (Rank S), além do XP e das chances de runa, tomo e mapa.",
      "Portais de masmorra: o rank agora gira em volta do rank de um jogador sorteado entre os que estão online (um abaixo, o mesmo, um ou dois acima). Todo mundo passa a ver portais do próprio nível, e continuam saindo portais mais difíceis.",
    ],
  },
  {
    versao: "1.3.2", data: "21/11/2026", hora: "11:09:08", titulo: "Diário do Enigma em livro, /enigma e dano das invocações",
    itens: [
      "Novo botão no menu do Exército (Habilidades → Exército): \"Dano em jogadores\".",
      "Desligado (o padrão): suas invocações não ferem nenhum jogador e nenhum jogador fere as suas invocações. Bom para dungeons em grupo.",
      "Ligado: vale nos dois sentidos, para quem quer PvP. Você nunca fere as próprias invocações, nem é ferido por elas.",
      "Diário do Enigma de cara nova: agora abre como um livro. Na página da esquerda, cada enigma tem um marcador colorido com o estado e o progresso dos seus passos; na da direita, as pistas que você já descobriu, com a atual em destaque.",
      "Novo comando /enigma: abre o Diário e, se você não tiver o livro, entrega um novo.",
      "Lubelius: o cambista saiu do centro da capital e a costureira ficou no lugar dele. Para trocar moedas, use a aba Câmbio do banqueiro.",
    ],
  },
  {
    versao: "1.3.1", data: "21/11/2026", hora: "10:36:12", titulo: "Invocações com cara de sombra, sábio que extrai encantamentos e recuperação pelo nível",
    itens: [
      "A recuperação de vida da Armadura do Alvorecer completa agora cresce com o nível do personagem: 0,5% da vida por segundo no nível 1, cerca de 3% no 50 e 10% no 100 (vida cheia em uns 10 segundos).",
      "Logo depois de levar dano, a recuperação cai pela metade por 2 segundos. Quase imortal, mas não imortal.",
      "As invocações do Invocador agora parecem sombras: ficam escurecidas, com tom roxo, nome roxo e uma aura de fumaça roxa. Dá para reconhecer de longe numa dungeon.",
      "Jogadores não conseguem mais ferir invocações (nem as dos outros nem as próprias), então ninguém mata a invocação do colega por engano.",
      "Novo serviço do sábio das capitais: uma bancada no jeito da bigorna, com o item de um lado e um livro em branco do outro. Você escolhe qual encantamento vai para o livro e depois passa para outro item na bigorna.",
      "É caro de propósito: moedas (Afiação V custa 100 ouro), de 5 a 30 níveis de experiência e um livro. Encantamentos de nível IV ou mais podem falhar, e aí se perde o pagamento (o item fica igual).",
      "Relíquias do Códex e maldições não podem ter o encantamento extraído.",
    ],
  },
  {
    versao: "1.3.0", data: "21/11/2026", hora: "08:53:53", titulo: "Armadura de Cristal e itens que evoluem com o uso",
    itens: [
      "A armadura do evento de domingo ganhou visual próprio: placas de cristal facetado em ciano e violeta, com viseira de olhos brilhantes no elmo e uma coroa de lascas de cristal.",
      "O visual aparece tanto no ícone quanto vestido no corpo, e é diferente de qualquer outra armadura do jogo.",
      "Itens únicos podem evoluir com o uso: a arma ganha experiência com o dano causado a monstros (o golpe final vale o dobro) e a armadura com o dano recebido de monstros. São 10 níveis, e subir é bem difícil.",
      "Cada nível dá +2% de dano na arma ou −1% de dano recebido em cada peça de armadura. No nível 5 o item passa a brilhar; no nível 10 a arma solta faíscas.",
      "Há um limite de experiência por dia para cada item. Chefões valem 5 vezes mais; monstros de spawner não contam.",
      "A descrição do item mostra o nível em estrelas, a experiência e como ele evolui. O nível fica no item, mesmo se ele trocar de dono, e aparece no site em Armas Únicas.",
      "Cada item único evolui de um jeito só: pelo nível do jogador ou pelo uso.",
    ],
  },
  {
    versao: "1.2.9", data: "21/11/2026", hora: "08:26:12", titulo: "Cofre que rende, poções traduzidas e entrada protegida",
    itens: [
      "Traduzidos os nomes e as descrições das bebidas e poções especiais que aparecem nos baús das estruturas (Buraco Negro na Garrafa, Fogo-Fátuo de Cerveja, Esmeraldas Líquidas e outras).",
      "Níveis de efeito acima de VI agora aparecem em números romanos (VII, VIII, IX...) em vez de um código em inglês.",
      "O cofre do banco agora rende: todo dia, à meia-noite, você ganha juros sobre o que está guardado. Começa em 0,5% ao dia e sobe 0,1% a cada 7 dias sem sacar, até 1% ao dia. Sacar volta para 0,5%.",
      "O rendimento vale para até 500 ouro guardados. Quem fica fora do jogo recebe ao voltar, contando no máximo 7 dias.",
      "A tela do banqueiro mostra a taxa atual, quanto o cofre rende amanhã e quando a taxa sobe.",
      "Ao entrar no servidor, enquanto a tela de escolha de personagem está aberta, você não toma dano e os monstros não te atacam. A proteção acaba ao fechar a tela.",
    ],
  },
  {
    versao: "1.2.8", data: "21/11/2026", hora: "00:29:15", titulo: "Evento: a Armadura do Alvorecer",
    itens: [
      "Domingo, 22/11, às 14:00 despertam quatro enigmas ao mesmo tempo, um para cada peça da Armadura do Alvorecer: elmo, peitoral, perneiras e botas. Só existe uma unidade de cada peça.",
      "A primeira pista de cada enigma já está no Diário do Enigma, mas nada responde antes do horário marcado.",
      "Cada enigma tem 6 passos e é mais difícil que o primeiro: pede itens raros, horários certos e viagens longas.",
      "No último passo de cada enigma surge um guardião de rank D. O baú com a peça só aparece quando ele cai.",
      "Uma peça por pessoa: quem desvendar um dos enigmas não avança mais nos outros. As peças podem ser trocadas depois.",
      "Quem vestir as quatro peças juntas ganha recuperação de vida automática.",
      "O chat avisa do evento de hora em hora e, na última hora, a cada 10 minutos. Quem entra no servidor também recebe o aviso.",
      "Cada peça entra no Mural dos Primeiros com o nome de quem a desvendou.",
      "Correção: o ferreiro voltou a aprimorar armas inquebráveis (como a Aurora Decifrada) até o +10.",
    ],
  },
  {
    versao: "1.2.7", data: "20/11/2026", hora: "23:40:46", titulo: "Habilidades até o nível 30",
    itens: [
      "O nível máximo das habilidades subiu de 10 para 30. O nível 20 pede 1.805 usos e o 30 pede 4.205.",
      "Os usos que você já fez contam: quem passou dos 405 usos sobe de nível no próximo uso da habilidade.",
      "Do nível 11 ao 30, cada nível dá 3% a mais de dano e 1% a menos de recarga (até o 10 continua 8% e 3%). No nível 30 a habilidade fica com 2,3 vezes o dano do nível 1 e 47% menos recarga.",
      "Invocações também crescem até o nível 30, com ganho menor depois do 10.",
      "Traduzidos os nomes e as descrições de 324 armas, armaduras e ferramentas de mods que ainda apareciam em inglês (Born in Chaos, Aether, Undergarden, Unusual End, Mowzie's Mobs, Blue Skies e outros).",
      "Armas do Códex podem ser inquebráveis. A Aurora Decifrada, prêmio do enigma, agora nunca se desgasta.",
      "Armas do Códex: quando a administração muda o dano de uma arma, as que já têm dono são reforjadas sozinhas com o valor novo.",
      "Site: seção nova Armas Únicas. Cada arma do Códex aparece com a aparência dela e o nome de quem pegou a primeira, com a data.",
      "As waystones dos reinos (a do centro e as dos quatro portões de cada capital) não podem mais ser quebradas, explodidas nem empurradas.",
      "Quando alguém desvendar o enigma, ele sai do jogo: o Diário do Enigma desaparece da mochila de todos.",
    ],
  },
  {
    versao: "1.2.6", data: "20/11/2026", hora: "21:46:38", titulo: "Enigmas pelo mundo e teste de conexão",
    itens: [
      "Enigmas: de tempos em tempos um enigma desperta no mundo. Todos recebem o Diário do Enigma, que guarda as pistas: clique com ele na mão para ler.",
      "Cada pista leva à próxima, e só o primeiro a chegar ao fim leva o tesouro escondido. Quem chegar depois encontra o baú vazio.",
      "Quando alguém avança, todos ficam sabendo que houve progresso, mas não quem foi nem onde.",
      "Os administradores ganharam um teste de conexão no painel: mede o ping de cada jogador e mostra se um atraso vem do servidor, do túnel ou da internet de alguém.",
    ],
  },
  {
    versao: "1.2.5", data: "20/11/2026", hora: "19:22:45", titulo: "Correção: aparência dos aprendizes",
    itens: [
      "Corrigido um erro que impedia de definir a aparência dos aprendizes das lojas.",
    ],
  },
  {
    versao: "1.2.4", data: "20/11/2026", hora: "19:16:17", titulo: "Encantamentos em português e aprendizes com aparência própria",
    itens: [
      "Os aprendizes que cuidam das lojas à noite agora podem ter aparência diferente da do mestre, para ninguém confundir os dois.",
      "Os mestres de folga na taverna continuam com a aparência de sempre.",
      "Clãs: agora dá para escolher o nome na hora de fundar. No Mestre dos Clãs, digite o nome no campo ao lado do botão Fundar. Em branco, o clã nasce como \"Clã de (seu nome)\".",
      "Para trocar o nome de um clã que já existe, o líder abre o clã no Mestre dos Clãs, vai na aba Gerir, digita o nome novo e clica em Mudar o nome.",
      "Os nomes e as descrições de 221 encantamentos de mods que ainda apareciam em inglês nos livros encantados foram traduzidos.",
      "O mod dos Anéis de Poder foi traduzido por inteiro: nomes dos anéis, blocos de Mallorn, efeitos, conquistas, as descrições de cada anel e os avisos que ele manda no chat.",
    ],
  },
  {
    versao: "1.2.3", data: "20/11/2026", hora: "18:42:13", titulo: "Seis mods novos: cozinha, pesca, estações, sons e arqueologia",
    itens: [
      "Farmer's Delight: cozinha de verdade, com panela, tábua de corte, novas plantações e muitos pratos.",
      "Aquaculture 2: mais de 30 peixes novos, varas melhores, iscas e a caixa de pesca.",
      "Serene Seasons: o mundo agora tem primavera, verão, outono e inverno. As plantações crescem melhor na estação certa.",
      "Better Archeology: novas ruínas, fósseis e artefatos para escavar em terras ainda não exploradas.",
      "Sound Physics Remastered e AmbientSounds: o som ecoa em cavernas, abafa atrás de paredes, e a natureza ganhou sons de fundo.",
      "Na primeira entrada o jogo baixa os mods novos e reinicia sozinho. O download é maior que o normal.",
    ],
  },
  {
    versao: "1.2.2", data: "20/11/2026", hora: "18:33:42", titulo: "Política dos reinos, moradores com vida e encontros pelo caminho",
    itens: [
      "A reputação agora tem dois lados. Abaixo de zero você fica Malvisto, depois Hostil e por fim Inimigo do reino.",
      "Reinos rivais: Lubelius e Lordes Demônios, Farmus (Oeste) e Tempest, Império do Leste e Sarion. Ajudar um custa um quarto do ganho no outro. Dwargon e Sakura são neutros.",
      "Malvisto: tudo custa 15% a mais. Hostil: 30% a mais, os lojistas não atendem e os guardas atacam dentro da capital. Banco, Guilda, clãs e o rei continuam atendendo.",
      "Inimigo: o reino põe a sua cabeça a prêmio. Quem derrotar você recebe a recompensa e reputação com o reino, e você volta a ser Hostil. Não vale entre membros do mesmo clã.",
      "Para limpar o nome: fale com o rei e pague a multa, faça missões e derrote monstros perto da capital, ou espere, que o tempo apaga a má fama aos poucos.",
      "Bater em guarda sem motivo rende um aviso e, se insistir, perda de reputação.",
      "Contrabandista: NPC novo, fora das capitais. Dá três serviços por dia contra os reinos (carga desviada, espionar a capital, derrubar guardas), que pagam bem, custam reputação com o alvo e agradam o rival.",
      "A mercadoria do Contrabandista abre conforme a má fama, até a Lâmina e a Couraça do Renegado, lendárias, só para quem é Inimigo de algum reino.",
      "Títulos novos: Fora da Lei e Inimigo de (reino).",
      "Do lado bom: quem é Honrado pode pedir ao rei um Favor Real por dia (reputação, moedas e experiência), e quem é Reverenciado compra no mercador o Corcel do reino, um cavalo domado, selado e veloz.",
      "A aba Reinos (tecla E) mostra o rival de cada reino e a barra do lado negativo.",
      "No site, o topo ganhou dois quadros novos: uso de memória e de processador do servidor.",
      "Os lojistas agora lembram de você: comentam a sua última compra, e quem compra 10 vezes no mesmo lojista vira freguês (3% de desconto; 5% com 30 compras). O taverneiro comenta a última missão que você entregou.",
      "Dia e noite nas capitais: à noite o ferreiro, o armeiro e o mercador deixam a loja com o aprendiz, que cobra 10% a mais, e vão para a taverna.",
      "Na taverna, à noite, os mestres só conversam: contam boatos que não se ouvem de dia, e o ferreiro e o armeiro pedem favores.",
      "Encontros: andando pela superfície, longe das capitais, de vez em quando acontece algo. São cinco, raros, e nenhum obriga a lutar.",
      "Viajante ferido: dê uma poção de cura ou de regeneração e ele paga com um Mapa do Tesouro. Mercador perdido: vende um item raro, uma única vez, e vai embora.",
      "Estrela caída: fica marcada por 10 minutos e quem chegar primeiro leva o prêmio. Baú fujão: pula para longe quando você chega perto, até cansar.",
      "Mensageiro apressado: confunde você com outra pessoa e entrega uma carta. Leve ao taverneiro do reino certo para receber moedas e reputação, ou abra para ver o que tem dentro.",
      "Ferreiro e armeiro disputam a encomenda do rei. Um favor por noite em cada capital: ajudar um aumenta a estima dele e diminui a do outro. Com 3 favores, 5% de desconto na loja dele (8% com 6). Quem ajuda muito o rival paga 5% a mais.",
    ],
  },
  {
    versao: "1.2.1", data: "20/11/2026", hora: "17:43:42", titulo: "Classe nova: Cartomante, e habilidades com efeitos novos",
    itens: [
      "Classe nova: Cartomante, a classe que joga com a sorte. Você tem uma mão de 3 cartas sorteadas, mostrada acima da barra de itens, e cada habilidade de dano gasta a carta da vez.",
      "Os naipes: Espadas dá 40% a mais de dano, Copas cura você, Ouros devolve mana, Paus prende o alvo. O raro Coringa faz os quatro de uma vez.",
      "São 12 habilidades, entre elas Embaralhar (troca a mão), Cara ou Coroa (dobro ou metade), Mão Cheia (joga as três cartas) e a ultimate Casa Cheia.",
      "Despertar do Cartomante: Mão Perfeita. Por alguns segundos toda carta é um Coringa.",
      "Para testar sem perder o que você já tem, crie outro personagem (tecla F8).",
      "As habilidades ganharam animações: quem lança faz um gesto com fitas de luz, o projétil corre até o alvo deixando rastro em espiral e estoura na chegada.",
      "Golpes de área desenham um círculo rúnico no chão, com onda de choque e colunas de luz na borda. Golpes em alvo único convergem para o inimigo antes do impacto.",
      "Investidas e teleportes deixam um rastro de imagens; bênçãos e reforços sobem em espiral em volta do jogador; invocações surgem de um círculo com coluna de luz.",
      "Cada elemento tem as suas cores e o seu som: fogo, gelo, raio, sagrado, sombra, natureza e arcano.",
    ],
  },
  {
    versao: "1.2.0", data: "20/11/2026", hora: "15:32:04", titulo: "Invocador: mande a tropa para um lugar",
    itens: [
      "A tecla de marcar alvo agora também manda as invocações para um lugar: mire num ponto do chão ou da parede e aperte. Elas vão até lá, lutam com o que aparecer e voltam para o ponto.",
      "Para chamar de volta, mire no céu e aperte a tecla, ou marque um inimigo. Se você se afastar mais de 48 blocos do ponto, elas voltam a seguir você sozinhas.",
      "As invocações comuns, como o golem de ferro, agora seguem o Invocador como as sombras. Antes ficavam paradas onde nasciam.",
      "Mapa: os marcadores (losangos coloridos) não aparecem mais flutuando na tela durante o jogo. Continuam todos no mapa e no minimapa.",
      "Bestiário do site: cada criatura passa a mostrar quem foi o primeiro a derrotá-la, com a data. Vale para as que forem derrotadas pela primeira vez daqui para frente; as que já tinham caído ficam como \"sem registro\".",
      "Limpeza de itens: agora só some o que está largado no chão há mais de 2 minutos. O que você acabou de soltar não é apagado, e os avisos no chat antes da limpeza saíram.",
    ],
  },
  {
    versao: "1.1.9", data: "20/11/2026", hora: "13:01:53", titulo: "Mural: o primeiro a vencer o labirinto",
    itens: [
      "Marco novo no Mural dos Primeiros do site: o primeiro a chegar ao coração do Labirinto do Dragão. O nome fica gravado para sempre.",
      "Quando o primeiro aventureiro pisar no salão central, o servidor inteiro é avisado no chat.",
      "Vale só para quem chegar andando, em sobrevivência. Administradores não contam.",
      "Prova da Guilda mais difícil a partir do Rank D: o Examinador tem mais vida, bate mais forte, corre mais e quase não é empurrado. Quanto mais alto o rank, maior a diferença. A prova do Rank E continua igual.",
      "Invocador: o golem de ferro invocado não ataca mais as suas próprias sombras. Nenhuma invocação ataca outra invocação, aldeões ou bichos domados.",
      "Invocador: as invocações só atacam outro jogador quando o Invocador marca esse jogador com a tecla de marcar alvo. O jogador marcado recebe um aviso. Não dá para marcar quem é do mesmo clã.",
      "O sistema de Grupo saiu: a aba Grupo da janela de Status e os comandos /grupo e /g não existem mais. Para jogar junto, use os clãs.",
      "Clãs: quem é do mesmo clã e está por perto agora divide a experiência dos monstros, com bônus de 10% para cada companheiro (até 6 juntos). O abate também conta para as missões e campanhas de todos.",
      "Árvores: uma construção de tora com folhas decorativas não é mais confundida com árvore.",
      "Saiu a névoa branca que subia do chão em pântanos e cemitérios e atrapalhava a visão.",
      "Tradução: as poções e bebidas especiais dos baús das masmorras (Esmeraldas Líquidas, Soro Espesso, Cerveja Viciante e outras) agora vêm com nome e descrição em português. As que você já tem guardadas continuam em inglês; só as novas mudam.",
      "Limpeza automática: a cada 30 minutos os itens soltos no chão somem, para o servidor não pesar. O chat avisa 1 minuto e 10 segundos antes. Túmulos e o que está dentro deles não são afetados.",
      "Túmulos: ao quebrar o seu túmulo, o que você estava vestindo quando morreu volta sozinho para o lugar: armadura, mão secundária, anéis, amuletos e os outros acessórios. Só não volta se você já tiver colocado outra coisa no lugar.",
      "Túmulos: quem morrer na Torre Infinita ou perto dela, dentro de um terreno de outro jogador ou junto às casas dos moradores dos reinos agora consegue quebrar o túmulo para pegar os itens. Nenhuma proteção do servidor segura mais o túmulo.",
    ],
  },
  {
    versao: "1.1.8", data: "19/11/2026", hora: "22:38:43", titulo: "Árvores caem inteiras",
    itens: [
      "Mod novo: FallingTree. Cortando o tronco de uma árvore com um machado, a árvore inteira cai de uma vez.",
      "Só vale para árvore de verdade, com folhas. Casas, cercas e vigas de tora continuam quebrando bloco por bloco.",
      "Não precisa baixar nada: o mod roda só no servidor.",
    ],
  },
  {
    versao: "1.1.7", data: "19/11/2026", hora: "21:09:32", titulo: "Túmulo dentro do labirinto",
    itens: [
      "Quem morrer dentro do Labirinto do Dragão agora consegue quebrar o próprio túmulo para pegar os itens de volta. Antes a proteção do lugar impedia.",
    ],
  },
  {
    versao: "1.1.6", data: "19/11/2026", hora: "19:33:17", titulo: "Bolsos dentro do inventário",
    itens: [
      "Os bolsos da calça agora aparecem dentro do próprio inventário, num painel à esquerda da mochila, um espaço para cada bolso costurado. Acabou o botão Bolsos.",
      "Clique para guardar, pegar ou trocar; botão direito pega metade ou põe um; com Shift o item volta para a mochila. A tecla O continua abrindo os bolsos sem abrir o inventário.",
      "Na página Minha conta do site, os atributos e as estatísticas agora usam a largura toda em vez de ficarem espremidos numa coluna.",
      "Saiu o clima de terror, a pedido do pessoal: o Homem da Neblina (o monstro que perseguia os jogadores à noite) e os sons assustadores de ambiente foram removidos.",
    ],
  },
  {
    versao: "1.1.5", data: "19/11/2026", hora: "00:20:12", titulo: "O Labirinto do Dragão",
    itens: [
      "Surgiu um labirinto fechado no mundo. Lá dentro os monstros são muito mais fortes que os de fora, e há baús de tesouro (cada jogador tem o seu saque) nos becos mais fundos.",
      "No coração do labirinto fica um salão seguro, onde monstro nenhum entra, com o Criador de Dragões, cama, baús e bancadas.",
      "O labirinto é protegido: nada lá dentro pode ser quebrado nem construído, e explosão não abre caminho.",
      "Só quem colocou uma Pedra de Viagem pode trocar o nome dela.",
      "Ovos de dragão agora chocam sempre do tipo comprado: o lugar onde o ovo é colocado não muda mais a raça.",
      "A página Minha conta do site ficou nova: seu personagem em destaque com a barra de XP, a sua jornada (tempo de jogo, monstros, posição nos rankings, Torre e temporada), a sua história, o bestiário pessoal, a reputação nos reinos e os seus terrenos marcados no mapa.",
      "No Criador de Dragões, cada ovo agora mostra o que o dragão tem de especial.",
      "A tela \"O Enhanced Celestials 2 chegou!\", que aparecia toda vez que você entrava no mundo, não abre mais.",
    ],
  },
  {
    versao: "1.1.4", data: "18/11/2026", hora: "22:39:36", titulo: "Habilidades únicas novas e mais mods em português",
    itens: [
      "Começamos a traduzir os mods que só vinham em inglês. Neste primeiro lote: as luas (Lua de Sangue, Lua Azul, Lua da Colheita e Superlua), o Homem da Neblina, os dragões e o que faltava do mod de samurai (katanas, armaduras e blocos).",
      "A tradução aparece para quem joga com o Minecraft em português. Os outros mods vêm nos próximos lotes.",
      "Dez habilidades únicas novas (agora são 20): Sangue de Titã, Pele de Espinhos, Olho por Olho, Estrela da Sorte, Toque Gélido, Coração de Brasa, Sede de Batalha, Sangue de Troll, Golpe em Eco e Escudo do Guardião. Cada uma tem a sua Habilidade Suprema.",
      "O sorteio da habilidade única agora dá preferência às que menos gente tem, para não ficar todo mundo com a mesma. Quem já tem a sua continua com ela.",
      "O mapa do site agora mostra onde fica a capital de cada reino, com o nome.",
      "Os espaços extras de habilidade (liberados pelo rank da Guilda) agora ficam na mesma linha dos outros, entre os quatro comuns e a ultimate, em vez de soltos acima da barra.",
    ],
  },
  {
    versao: "1.1.3", data: "18/11/2026", hora: "16:14:30", titulo: "Kit inicial para personagens novos",
    itens: [
      "Todo personagem novo nasce com um kit: armadura de couro completa já vestida, espada, machado, pá e picareta de pedra e 32 pães. Tudo de classe F, então dá para usar desde o primeiro minuto.",
      "Quem escolhe a classe Samurai recebe a Katana de Aprendiz, que já permite treinar o Saque no Rank F.",
      "A criatura da neblina foi domada: só aparece à noite, não quebra portas, vidros nem blocos, e aparece com menos frequência.",
      "O kit vale para personagens criados a partir de agora; os que já existem não recebem.",
    ],
  },
  {
    versao: "1.1.2", data: "18/11/2026", hora: "15:58:56", titulo: "Classe nova: Samurai, e noites mais sombrias",
    itens: [
      "Classe nova: Samurai, o duelista da katana. Depois de 3 segundos sem golpear, a katana volta à bainha e o próximo golpe é um Saque, 75% mais forte.",
      "São 12 habilidades em três formas: Maré (gelo), Brasa (fogo) e Relâmpago (raio), além de cortes que sangram, uma postura de aparar e o golpe de misericórdia.",
      "Despertar do Samurai: Lâmina das Três Formas. Por alguns segundos todo golpe é um Saque e alterna brasa, maré e relâmpago.",
      "Mod novo: Samurai Dynasty, com katanas e armaduras de samurai. Com uma katana na mão o Samurai bate 10% mais forte; sem katana não há Saque.",
      "As noites ficaram mais sombrias: sons tensos à noite e nas cavernas, noites de lua de sangue, e dizem que uma figura anda na neblina.",
      "Para testar o Samurai sem perder o que você já tem, crie um segundo personagem (tecla F8).",
    ],
  },
  {
    versao: "1.1.1", data: "18/11/2026", hora: "14:17:33", titulo: "O céu do site acompanha o jogo",
    itens: [
      "Quando chove no jogo, o topo do site mostra chuva de verdade: gotas perto e longe, inclinadas pelo vento, com nuvens escuras passando.",
      "Na tempestade, raios cortam o céu com galhos e um clarão que ilumina tudo por um instante.",
      "De noite: estrelas piscando, lua, vaga-lumes e, de vez em quando, uma estrela cadente.",
      "De dia: sol com raios, poeira dourada no ar e pássaros cruzando o céu.",
      "Com chefe mundial à solta: fumaça vermelha e brasas subindo.",
    ],
  },
  {
    versao: "1.1.0", data: "18/11/2026", hora: "14:05:43", titulo: "Crônicas do mundo",
    itens: [
      "O site passou a guardar o que acontece: quem subiu de nível, quem mudou de rank na Guilda, conquistas, recordes na Torre e criaturas descobertas no bestiário.",
      "O quadro Acontecendo agora mostra esses acontecimentos junto com o movimento ao vivo, e não fica mais vazio quando o servidor está calmo.",
      "Seção nova: Crônicas do FRX. Os grandes feitos viram manchete com data e hora e ficam guardados para sempre.",
      "A ficha de cada personagem ganhou um Histórico com os últimos acontecimentos dele.",
      "No ranking Mais fortes aparece quantos níveis cada um ganhou na semana e, a partir de amanhã, quem subiu ou caiu de posição desde o dia anterior.",
      "Tudo isso começa a contar de agora: o que aconteceu antes desta atualização não foi registrado.",
    ],
  },
  {
    versao: "1.0.9", data: "18/11/2026", hora: "14:05:43", titulo: "Site mais vivo",
    itens: [
      "O topo do site agora acompanha o mundo de verdade: muda de cara de dia, de noite, na chuva, na tempestade e quando um chefe mundial aparece, e diz quantos aventureiros estão jogando.",
      "A temporada ganhou contagem regressiva até a virada do mês, medalhas para os três primeiros e a tabela de quantos pontos vale cada feito.",
      "O bestiário virou coleção: uma barra mostra quantos chefes e monstros o servidor já descobriu.",
      "Os rankings ganharam medalhas e uma barra que compara cada um com o primeiro colocado.",
      "Números que sobem, barras que enchem e linhas que surgem: pequenas animações por todo o site.",
    ],
  },
  {
    versao: "1.0.8", data: "18/11/2026", hora: "13:35:33", titulo: "Criador de Dragões e cabeçalho novo no site",
    itens: [
      "Chegou o Criador de Dragões. Ele vende ovos dos 8 tipos de dragão, e são caríssimos: de 1.500 de ouro (Floresta e Água) até 12.000 de ouro (Dragão do Fim). Quanto mais raro, mais caro.",
      "Coloque o ovo no chão e clique nele para começar a chocar. O lugar onde ele choca pode mudar o tipo do dragão, então escolha bem o ninho.",
      "Fora do Criador, o único jeito de conseguir um ovo continua sendo derrotar o Dragão do Fim.",
      "O site ganhou um cabeçalho novo: os atalhos principais ficam à vista, o resto fica em Mais, e no celular tudo abre por um botão.",
    ],
  },
  {
    versao: "1.0.7", data: "18/11/2026", hora: "13:24:22", titulo: "Conta no site, Guia do Aventureiro, Mural dos Primeiros e dragões",
    itens: [
      "Conta no site: o botão Minha conta mostra o seu inventário, as moedas, os terrenos e a ficha de cada personagem, sem abrir o jogo. No primeiro acesso, aperte F9 dentro do jogo para ver um código, digite no site e crie a sua senha. Depois é só nick e senha.",
      "Capítulo novo no livro de missões: Guia do Aventureiro. Ele ensina, passo a passo, a se registrar na Guilda, subir a Torre, costurar bolsos na calça, comprar um terreno e entrar num clã, com experiência de recompensa.",
      "Quem já fez essas coisas recebe as missões como concluídas e só precisa pegar a recompensa.",
      "No site, o Mural dos Primeiros mostra quem foi o primeiro do servidor a chegar em cada rank da Guilda, em cada marco de nível e nos andares 10, 25, 50 e 100 da Torre. O nome fica lá para sempre.",
      "Mod novo no pack: Dragon Mounts. Choque um ovo de dragão, crie o dragão e voe nele.",
    ],
  },
  {
    versao: "1.0.6", data: "18/11/2026", hora: "10:19:26", titulo: "Site: rankings por personagem",
    itens: [
      "No site, os aventureiros e os rankings agora mostram cada personagem em uma linha, com o nome dele. Quem tem dois ou três personagens aparece duas ou três vezes.",
      "Clicar num personagem abre a ficha dele, mesmo que o dono esteja jogando com outro.",
      "O personagem que não está em uso aparece com os números da última vez em que foi jogado. O banco é da conta, então conta para todos os personagens dela.",
    ],
  },
  {
    versao: "1.0.5", data: "18/11/2026", hora: "09:44:05", titulo: "Costureira: bolsos na calça",
    itens: [
      "Chegou a Costureira. Leve uma calça até ela (na mão ou vestida) e ela costura bolsos: cada bolso é um espaço a mais para guardar itens.",
      "Cada bolso custa 2 de ouro e a calça aceita até 12. Dá para costurar um por vez ou vários de uma vez.",
      "Vestindo a calça, abra os bolsos pelo botão Bolsos do inventário ou pela tecla O (dá para trocar a tecla nos controles).",
      "Os bolsos são da calça, e o que está dentro é do personagem: se você tirar a calça, os itens voltam para a mochila, e o que não couber cai no chão.",
      "Ao morrer, o que está nos bolsos cai junto com o resto da mochila.",
    ],
  },
  {
    versao: "1.0.4", data: "18/11/2026", hora: "09:26:24", titulo: "Anéis sem repetição",
    itens: [
      "Você continua com 10 espaços de anel, mas não dá mais para usar dois anéis iguais ao mesmo tempo.",
      "Quem já estava com anéis repetidos recebe os repetidos de volta na mochila ao entrar no jogo.",
    ],
  },
  {
    versao: "1.0.3", data: "18/11/2026", hora: "09:12:26", titulo: "Mais espaços de habilidade e habilidades novas",
    itens: [
      "A barra de habilidades cresce com o rank da Guilda: além dos 4 espaços e da ultimate, o Rank E libera o 5º espaço, o D o 6º, o C o 7º e o B o 8º.",
      "Os espaços novos aparecem numa linha acima da barra e têm teclas próprias (Z, H, N e Y), que você troca na janela de habilidades (tecla K).",
      "Ao aprender uma habilidade, ela vai sozinha para um espaço livre, inclusive os novos.",
      "Ladino, Clérigo e Invocador ganharam 6 habilidades cada e passam a ter 12, como as outras classes.",
      "Ladino: Gancho do Gatuno, Cortina de Fumaça, Marcar a Vítima, Moeda Ricochete, Troca de Lugar e Armadilha do Gatuno.",
      "Clérigo: Bênção da Vida, Correntes de Luz, Solo Sagrado, Manto da Fé, Lança de Luz e Dardos Celestiais.",
      "Invocador: Correntes Espectrais, Marca da Presa, Dreno de Alma, Espíritos Caçadores, Alcateia Sombria e Círculo de Almas.",
    ],
  },
  {
    versao: "1.0.2", data: "18/11/2026", hora: "08:37:53", titulo: "Limite de escravos por rank",
    itens: [
      "O Mercador de Escravos passou a ter limite: Rank D pode ter 4 aldeões comprados, C tem 8, B tem 16, A tem 34 e S tem 68.",
      "O painel do mercador mostra quantos você tem e quantos pode ter. Quando um aldeão comprado morre, a vaga volta.",
      "Biblioteca: livro novo, Terras, Clãs e Comércio, e páginas novas sobre personagens, contratos da Guilda e coleção.",
    ],
  },
  {
    versao: "1.0.1", data: "18/11/2026", hora: "08:07:00", titulo: "Personagens no site e na Torre",
    itens: [
      "A ficha do jogador no site mostra os personagens da conta: clique em um deles para ver a ficha completa dele, mesmo que não esteja em uso.",
      "Nos clãs do site, cada personagem aparece com o próprio nome e nível.",
      "O placar da Torre e a temporada passaram a ser de cada personagem, com o nome dele.",
    ],
  },
  {
    versao: "1.0.0", data: "18/11/2026", hora: "07:53:45", titulo: "Até 3 personagens por conta",
    itens: [
      "Cada conta pode ter até 3 personagens, cada um com raça, classe, nível, itens, rank e clãs próprios.",
      "Ao entrar no servidor aparece a tela de escolha, com um estandarte por personagem. A tecla F8 abre a mesma tela a qualquer hora.",
      "Para criar um personagem, escolha uma vaga livre e dê um nome a ele. Ele começa do zero, com a escolha de raça e classe.",
      "Trocar de personagem faz o jogo entrar de novo no servidor sozinho, em poucos segundos.",
      "Banco e terrenos são da conta e valem para os três.",
    ],
  },
  {
    versao: "0.43.1", data: "18/11/2026", hora: "07:53:45", titulo: "Mercador de Escravos",
    itens: [
      "Novo NPC nas capitais: o Mercador de Escravos vende aldeões sem profissão a quem tem Rank D ou mais na Guilda.",
      "Você escolhe a roupa do aldeão (planície, deserto, savana, taiga, neve, selva ou pântano) e recebe um contrato.",
      "Use o contrato no chão, onde quiser, para o aldeão aparecer. Com uma bancada de trabalho por perto, ele aprende o ofício.",
    ],
  },
  {
    versao: "0.43.0", data: "18/11/2026", hora: "07:11:38", titulo: "Ampliar o terreno e mapa pintado",
    itens: [
      "Dá para ampliar um terreno: a área nova pode passar por cima de um terreno que já é seu. Você paga só os blocos novos, e só eles contam no limite do rank.",
      "O painel mostra quantos blocos da área escolhida são novos e quantos já eram seus.",
      "Os terrenos comprados e a área em escolha agora são pintados no mapa pelo próprio jogo de cada jogador (terceira tentativa de conserto).",
    ],
  },
  {
    versao: "0.42.9", data: "18/11/2026", hora: "06:52:52", titulo: "Terrenos maiores",
    itens: [
      "A área de terreno que cada rank da Guilda pode ter dobrou: o Rank F passou de 256 para 512 blocos e o Rank S, de 7.000 para 14.000.",
      "O lado máximo de cada terreno também subiu, para um terreno só poder usar a área toda: de 16 para 24 blocos no Rank F, até 114 no Rank S.",
      "Nova tentativa de conserto: os terrenos comprados e a área escolhida voltam a ser pintados no mapa.",
      "Menos travadinhas: o salvamento completo do mundo passou de 5 em 5 para de 30 em 30 minutos. Inventário e progresso continuam salvos a cada 5.",
    ],
  },
  {
    versao: "0.42.8", data: "17/11/2026", hora: "20:14:32", titulo: "Discos raros e permissões de terreno",
    itens: [
      "Um disco pode ter edição limitada: só um certo número de unidades existe no mundo.",
      "No Mercador de Discos, o cartão mostra quantas restam. Quando acabam, o disco fica esgotado.",
      "Cada disco de edição limitada sai numerado (nº 3 de 10) na descrição do item.",
      "Terrenos: o dono escolhe, para cada jogador, o que ele pode fazer: colocar blocos, quebrar blocos e abrir baús e portas.",
      "Corrigido: o terreno comprado não aparecia pintado no mapa.",
    ],
  },
  {
    versao: "0.42.7", data: "17/11/2026", hora: "19:59:35", titulo: "Painéis novos: terrenos e contratos",
    itens: [
      "A compra de terreno ganhou um painel próprio: o desenho da área vista de cima, com os terrenos vizinhos, o preço e os limites do seu rank em barras.",
      "Se a área não puder ser comprada, o painel diz o motivo e a barra do limite que estourou fica vermelha.",
      "Meus terrenos virou uma lista: clique num terreno para vender (com confirmação), mostrar as divisas ou abrir e fechar para todos.",
      "Quem pode mexer: a lista de jogadores com um clique para liberar ou tirar.",
      "Dá para marcar os dois cantos pelo painel, na posição em que você está, sem abrir o mapa.",
      "Contratos da Guilda: quadro novo, com um cartão para cada contrato do dia, barra de progresso, recompensa e o tempo que falta para renovar.",
    ],
  },
  {
    versao: "0.42.6", data: "17/11/2026", hora: "19:59:35", titulo: "Discos por estilo de música",
    itens: [
      "Cada disco agora tem um estilo de música: rock, sertanejo, trilha sonora e o que mais aparecer na loja.",
      "No Mercador de Discos, o botão Estilo mostra só os discos do estilo escolhido. Clique para passar ao próximo; com Shift, volta ao anterior.",
      "O estilo aparece nos detalhes do disco na loja e na descrição do item, ao lado do artista.",
    ],
  },
  {
    versao: "0.42.5", data: "17/11/2026", hora: "19:15:13", titulo: "Painéis novos: clãs, leiloeiro e discos",
    itens: [
      "Mestre dos Clãs: painel próprio, no estilo do banco. Abas para os seus clãs e para o ranking de todos; na página do clã, cofre, membros, pedidos e gestão.",
      "O líder muda o nome do clã digitando, escolhe a cor numa paleta e passa a liderança por um botão. Não é mais preciso etiqueta nem bloco de ouro.",
      "Leiloeiro: vitrine em cartões com o item, o preço e quem vende, busca por nome do item ou do vendedor e ordenação por mais novos, mais baratos ou mais caros.",
      "Para vender, clique no item direto na sua bolsa e defina o preço em ouro, prata e cobre. A tela mostra quanto você recebe depois da taxa.",
      "Mercador de Discos: prateleira em cartões e o botão Ouvir, que toca a música antes de você comprar.",
    ],
  },
  {
    versao: "0.42.4", data: "17/11/2026", hora: "17:03:03", titulo: "Discos com desenho próprio",
    itens: [
      "Cada disco de música pode ter a sua própria aparência, em vez de repetir os discos do jogo.",
      "Quem já comprou um disco continua ouvindo e vendo o desenho dele, mesmo que o disco saia da loja.",
    ],
  },
  {
    versao: "0.42.3", data: "17/11/2026", hora: "16:47:09", titulo: "Mercador de Discos",
    itens: [
      "NPC novo nas capitais: o Mercador de Discos, que vende discos com músicas próprias do servidor.",
      "O disco toca em qualquer jukebox: clique nela com o disco na mão. Clique de novo para tirar.",
      "As músicas chegam pelo pacote de recursos do servidor. Aceite o pacote ao entrar para ouvi-las.",
      "Alguns discos pedem um rank mínimo na Guilda.",
    ],
  },
  {
    versao: "0.42.2", data: "17/11/2026", hora: "13:23:23", titulo: "Invocador: alvo das sombras",
    itens: [
      "Invocador: clique com o botão do meio do mouse (a bolinha) numa criatura para marcá-la. Todas as sombras e invocações passam a atacar só ela.",
      "Sem alvo marcado, as sombras atacam os monstros que estiverem por perto, como antes.",
      "Para soltar o alvo: clique de novo na mesma criatura ou clique sem nenhuma criatura na mira. Quando o alvo morre, o foco se solta sozinho.",
      "O botão pode ser trocado em Opções → Controles → FRX-WORLD → \"Sombras: marcar alvo\".",
    ],
  },
  {
    versao: "0.42.1", data: "17/11/2026", hora: "13:03:06", titulo: "Mais de mil missões da Guilda e Torre mais variada",
    itens: [
      "A Guilda ganhou 1.062 missões novas: mais de 100 para cada rank, de F a S, entre caçadas e encomendas.",
      "Os alvos acompanham o rank: superfície no F e no E, Floresta do Crepúsculo, Undergarden e Nether no D, Aether, Everbright e o Fim no C, e daí em diante cavernas profundas, chefes e dragões.",
      "Quadro do dia: o quadro mostra 12 missões do seu rank, 4 do rank de baixo e 2 do rank de cima (bloqueadas, para você ver o que vem depois). A seleção é a mesma para todos e muda à meia-noite.",
      "As missões novas podem ser repetidas quando voltarem ao quadro, com 20 horas de intervalo.",
      "Missões que você já aceitou continuam no seu quadro mesmo depois de a seleção mudar.",
      "Torre Infinita e masmorras de portal: os andares agora misturam monstros dos outros mods aos do Minecraft — 79 tipos, de 11 a 15 por tema, em vez de 3 ou 4.",
      "Do andar 61 em diante (e nos portais de rank B para cima) entram reforços de elite: cavaleiros, draugrs reais e raptores.",
    ],
  },
  {
    versao: "0.42.0", data: "17/11/2026", hora: "12:36:42", titulo: "Druida, o metamorfo",
    itens: [
      "Classe nova: Druida. Usa mana e se transforma em lobo, urso e ave.",
      "Forma de lobo (nível 2): 25% mais rápido e com garras fortes no lugar da arma, mas apanha 10% mais.",
      "Forma de urso (nível 10): 25% mais vida e 15% menos dano recebido, mas mais lento e com garras mais fracas.",
      "Forma de ave (nível 20): voa por 15 s. Quase não fere e apanha mais — é para atravessar e fugir.",
      "Tempo limitado: até 45 s transformado por vez. Trocar de uma forma para outra não renova o tempo, e ao voltar a humano são 15 s de descanso.",
      "Cada forma tem a sua barra de habilidades, que troca sozinha. A tecla K, dentro da forma, organiza a barra daquela forma.",
      "Na forma humana o Druida cura aos poucos, protege o grupo e prende inimigos com raízes. Essas magias não funcionam na forma de animal.",
      "Suprema Ira da Floresta e Despertar Coração da Floresta.",
    ],
  },
  {
    versao: "0.41.9", data: "17/11/2026", hora: "12:06:36", titulo: "Bardo e Monge: dez classes",
    itens: [
      "Classe nova: Bardo, o suporte. Usa mana e toca canções que deixam o grupo mais rápido, mais forte e mais vivo. Só uma canção toca por vez.",
      "Inspiração: jogadores a até 8 blocos de um Bardo causam 8% a mais de dano.",
      "Refrão Ecoante encurta as recargas dos aliados; Zombaria faz o alvo levar mais dano de todos; Canção de Ninar adormece os inimigos em volta.",
      "Despertar do Bardo, Bis!: zera as recargas e enche o recurso de todo o grupo por perto.",
      "Classe nova: Monge, a luta de mão vazia. Com a mão principal vazia, o soco fica forte e cresce com o nível.",
      "Combo de 4 socos: cada um bate mais que o anterior e o quarto é o golpe final, que empurra e atordoa.",
      "Chi: o recurso do Monge. Não volta sozinho — cada soco rende 10 e o golpe final 20. As habilidades gastam chi.",
      "Corpo leve: cada peça de armadura a menos dá ao Monge mais esquiva e menos dano recebido.",
      "Despertar do Monge, Portão Interior: socos em dobro com onda de choque e habilidades sem custo.",
      "Para as outras classes, e para o Monge com arma na mão, o combate continua igual.",
      "A tela de escolha de classe mostra a descrição da classe em que você clicar.",
    ],
  },
  {
    versao: "0.41.8", data: "17/11/2026", hora: "11:39:04", titulo: "Paladino, a oitava classe",
    itens: [
      "Classe nova: Paladino, o protetor sagrado. Usa mana, toma para si o dano dos aliados, cura e pune os mortos-vivos.",
      "Aura de Proteção: jogadores a até 6 blocos de um Paladino levam 10% menos dano.",
      "12 habilidades: Golpe do Cruzado, Imposição das Mãos, Escudo Arremessado, Desafio Sagrado, Martelo da Justiça, Voto do Guardião, Solo Consagrado, Intervenção, Aura de Retribuição, Queda do Juiz e Sacrifício.",
      "Voto do Guardião: metade do dano de um aliado passa a vir para você. Intervenção: você voa até o aliado e toma os inimigos dele.",
      "Sacrifício: você dá parte da própria vida para curar e limpar o grupo inteiro.",
      "Habilidade suprema: Juízo Final, uma coluna de luz que acompanha você, ferindo os inimigos e curando os aliados a cada segundo.",
      "Despertar: Égide da Aurora, uma cúpula que barra 60% do dano de quem está dentro e devolve tudo num clarão final.",
      "Três ramos de talento: Baluarte, Justiça e Devoção.",
    ],
  },
  {
    versao: "0.41.7", data: "17/11/2026", hora: "10:39:40", titulo: "Mercado, vários clãs, bestiário e biblioteca nova",
    itens: [
      "Mercado entre jogadores: no Leiloeiro, anuncie o item da sua mão por um preço e outro jogador compra depois, mesmo com você fora do jogo. O dinheiro fica esperando no balcão.",
      "Mestre dos Clãs: funde o seu clã, peça entrada, veja poder e riqueza e use o cofre do clã, separado do seu banco.",
      "Agora dá para participar de até 3 clãs ao mesmo tempo. O líder muda o nome, a cor, nomeia oficiais e passa a liderança.",
      "Bestiário no site: os chefes e monstros do servidor aparecem como “não descoberto” até alguém derrotá-los pela primeira vez.",
      "Biblioteca do site redesenhada em pixel art, com estante, livros e plaquinhas no estilo do jogo.",
      "As novidades do site agora mostram também a hora em que cada atualização entrou no ar.",
    ],
  },
  {
    versao: "0.41.6", data: "17/11/2026", hora: "09:04:49", titulo: "Casa própria, contratos da Guilda, clãs e coleção",
    itens: [
      "Terrenos: abra o mapa (tecla J), clique com o botão direito e marque os dois cantos da área. O jogo mostra o preço pelo tamanho e, depois da compra, só você constrói ali.",
      "O terreno comprado aparece pintado no mapa de todos com o seu nome. Monstros e explosões não destroem nada dentro dele.",
      "Você escolhe quem mais pode mexer no seu terreno, ou abre para todos. Tudo no item “Terreno: meus terrenos e permissões” do mapa.",
      "O tamanho do terreno cresce com o rank da Guilda: do Rank F (16×16, um terreno) ao Rank S (lado de até 80 blocos, sete terrenos).",
      "Contratos da Guilda: três tarefas novas por dia e uma por semana, do tamanho do seu rank. O progresso conta sozinho e o pagamento em moedas e XP cai na hora.",
      "Fechou os três contratos do dia? A Guilda paga um extra. Veja o quadro pelo botão “Contratos” do Cartão de Aventureiro.",
      "Coleção: cada tipo de criatura e cada chefão diferente que você derrota entra na sua ficha. Os marcos pagam moedas e XP e dão títulos novos, como Naturalista e Senhor dos Chefes.",
      "Clãs: crie o seu pelo botão de times do inventário e convide os amigos. O site ganhou o ranking dos clãs mais fortes e mais ricos.",
      "Chefes e monstros novos passam a aparecer nas masmorras de portal, na Torre e no Nether, alguns com aparência própria e recompensas especiais.",
      "Armas únicas do servidor: poucas unidades no mundo, vendidas no ferreiro ou dadas em missões, algumas evoluem junto com o dono.",
    ],
  },
  {
    versao: "0.41.5", data: "17/11/2026", hora: "08:02:26", titulo: "Assassino renovado",
    itens: [
      "Nove habilidades novas para o Assassino, que passa a ter 16.",
      "Passo Fantasma, Bomba de Fumaça, Lâminas Envenenadas, Leque de Adagas, Emboscada, Golpe de Misericórdia, Adaga Ricochete e Sangria Profunda.",
      "Nova habilidade suprema: Dança da Morte, que salta de inimigo em inimigo golpeando pelas costas.",
      "Comando /ping para ver a sua conexão com o servidor.",
    ],
  },
  {
    versao: "0.41.4", data: "16/11/2026", hora: "22:40:04", titulo: "Site do servidor, portais em grupo e chefes de fim de jogo",
    itens: [
      "Tranca de baú: agachado, clique com um gancho de armadilha num baú e só você abre e quebra.",
      "Este site: status, rankings, mapa, mods e novidades em um lugar só.",
      "Biblioteca no site: livros para folhear sobre ranks, missões, economia, a Torre, raças e cada classe.",
      "Tela do Exército de Sombras com barra de experiência; invocações e sombras agora evoluem de verdade.",
      "Portais: todos que atravessam o mesmo portal caem na mesma dungeon. A lotação cresce com o rank: F 2, E 3, D 3, C 4, B 4, A 5 e S 6 aventureiros.",
      "Arcos e bestas agora também têm classe e exigem o rank da Guilda, como as outras armas.",
      "Mods novos: Naturalist (animais), Mythic Mounts (montarias), Artifacts (artefatos raros) e Macaw's (pontes, telhados, portas, móveis, janelas e cercas).",
      "Torre Infinita em grupo de até 5: quanto mais gente, mais fortes e numerosos os monstros, e a XP do andar é dividida entre todos.",
      "Ferreiro, armeiro e mercador só mostram equipamentos que o seu rank da Guilda já pode usar.",
      "Nether só para Rank D ou superior da Guilda, e o End só para Rank C ou superior.",
      "O Nether virou a dimensão mais perigosa do servidor: monstros muito mais fortes, estruturas e criaturas novas e emboscadas que caçam quem entra.",
      "Chefes das masmorras embaixo dos reinos ficaram muito mais fortes: são desafio para nível 50 ou mais. Abaixo disso, seus golpes mal arranham.",
    ],
  },
  {
    versao: "0.41.3", data: "16/11/2026", titulo: "Classes de equipamento, dungeons de portal e moedas mais raras",
    itens: [
      "Armas e armaduras têm classe (F a S) e exigem o rank da Guilda para usar, inclusive as dos mods. A classe aparece na descrição do item.",
      "Portais levam a uma dungeon temporária com três câmaras e um chefão sorteado entre 14, cada um com golpes próprios.",
      "Moedas agora vêm só das missões da Guilda e da venda de itens. Baús, monstros e eventos deixaram de dar moedas.",
      "Torre Infinita: XP só ao vencer o andar, monstros mais fortes e morte de verdade, com lápide na porta, perda de XP e 10 minutos de espera.",
      "Morreu numa dungeon? A lápide com seus itens fica do lado de fora.",
      "Floresta do Crepúsculo mais difícil e mochilas protegidas: ninguém abre a mochila que está nas suas costas.",
    ],
  },
  {
    versao: "0.41.2", data: "15/11/2026", hora: "23:37:59", titulo: "Aventura: Torre Infinita, História, Temporadas e Runas",
    itens: [
      "Torre Infinita com 100 andares temáticos, andar seguro a cada 10 (baú, cozinheiro e mercador) e placar na entrada.",
      "História principal em 14 capítulos, no livro de missões e em /historia.",
      "Temporadas mensais com placar e prêmios para os três primeiros.",
      "Mural de procurados, mapas do tesouro, Lua de Sangue e chuva de meteoros.",
      "Runas: 11 tipos para encaixar no equipamento pela bigorna.",
      "Dezenas de habilidades novas para Guerreiro, Arqueiro e Mago.",
      "Benefícios por rank da Guilda: bônus em missões e desconto nas lojas.",
      "Moradores das cidades protegidos, 10 espaços para anéis, skins dos jogadores e mais pets.",
      "Eventos raros pelo mundo. Fique de olho na sua bolsa de moedas.",
    ],
  },
  {
    versao: "0.41.1", data: "13/11/2026", hora: "20:08:34", titulo: "Mundo que reage a você",
    itens: [
      "NPCs mudam a fala conforme seu rank, sua raça, seu título e seu nome.",
      "Nomeação: ao concluir a campanha de um reino, o rei faz a cerimônia e concede um nome.",
      "Bestiário, missões de rank da Guilda e Habilidade Suprema.",
    ],
  },
  {
    versao: "0.41.0", data: "11/2026", titulo: "Balanceamento e túmulo",
    itens: [
      "Monstros, Elites e Campeões menos punitivos por nível.",
      "Ao morrer, seus itens ficam num túmulo em vez de espalhados pelo chão.",
    ],
  },
  { versao: "0.40.0", data: "11/2026", titulo: "Novas dimensões e livro de missões", itens: ["Twilight Forest, Aether, Blue Skies, Undergarden e Deeper and Darker, cada uma com faixa de nível.", "Livro de missões (FTB Quests) com 11 capítulos.", "Crítico, esquiva e roubo de vida nos talentos, raças e equipamentos."] },
  { versao: "0.39.0", data: "11/2026", titulo: "Monstros com rank", itens: ["Monstros mostram nível e podem nascer como Elite, Campeão ou Chefe."] },
  { versao: "0.38.0", data: "11/2026", titulo: "Raças", itens: ["16 raças, com principal e secundária, aura e visual próprios. Comando /raca."] },
  { versao: "0.33 a 0.37", data: "2026", titulo: "Despertar e Exército de Sombras", itens: ["Despertar (tecla B): um poder especial para cada classe.", "Exército de Sombras para o Invocador.", "Waystones em todos os reinos."] },
  { versao: "até 0.28", data: "2026", titulo: "Fundação", itens: ["Reinos com rei, moradores, lojas, banqueiro e campanhas.", "Classes, talentos, forja, masmorras e a janela de Status."] },
];
