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
