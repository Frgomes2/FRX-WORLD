/**
 * Notas de atualização do site público (seção "Novidades"), em linguagem de jogador.
 * A mais nova vem primeiro. A cada /publicar, acrescente aqui o que mudou para quem joga
 * (sem nomes de arquivo; os detalhes técnicos ficam em docs/07-changelog.md).
 */
export type NotaAtualizacao = { versao: string; data: string; titulo: string; itens: string[] };

export const NOTAS: NotaAtualizacao[] = [
  {
    versao: "0.41.0", data: "16/11/2026", titulo: "Site do servidor, portais em grupo e chefes de fim de jogo",
    itens: [
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
    versao: "0.41.0", data: "16/11/2026", titulo: "Classes de equipamento, dungeons de portal e moedas mais raras",
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
    versao: "0.41.0", data: "15/11/2026", titulo: "Aventura: Torre Infinita, História, Temporadas e Runas",
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
    versao: "0.41.0", data: "13/11/2026", titulo: "Mundo que reage a você",
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
