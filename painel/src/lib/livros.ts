import "server-only";
import { prisma } from "@/lib/prisma";
import { lerConfig } from "@/lib/config-jogo";
import { CLASSES } from "@/lib/habilidades";

/**
 * Biblioteca do site público: livros que explicam o servidor, um por assunto e um por classe.
 * Os textos ficam aqui; ranks, missões e habilidades vêm do banco, então acompanham o painel.
 * Mudou uma regra do jogo? Atualize o livro correspondente (e as notas em lib/notas.ts).
 */
export type Bloco =
  | { t: "p"; texto: string }
  | { t: "lista"; itens: string[] }
  | { t: "tabela"; cab: string[]; linhas: string[][] }
  | { t: "nota"; texto: string };
export type Pagina = { titulo: string; blocos: Bloco[] };
export type Livro = { id: string; titulo: string; subtitulo: string; icone: string; cor: string; grupo: "Mundo" | "Classes"; paginas: Pagina[] };

const p = (texto: string): Bloco => ({ t: "p", texto });
const lista = (...itens: string[]): Bloco => ({ t: "lista", itens });
const tabela = (cab: string[], linhas: string[][]): Bloco => ({ t: "tabela", cab, linhas });
const nota = (texto: string): Bloco => ({ t: "nota", texto });

const LETRAS = ["F", "E", "D", "C", "B", "A", "S"];
const LOTACAO = [2, 3, 3, 4, 4, 5, 6];
const EQUIP = [
  "Couro, madeira, pedra, ouro (armas), arco comum",
  "Ferro, cota de malha, armadura de ouro, besta",
  "Diamante, tridente",
  "Netherita",
  "Equipamentos de mods acima da netherita",
  "Equipamentos raros de mods",
  "Tudo",
];
const DESPERTAR: Record<string, [string, string, string]> = {
  GUERREIRO: ["Armadura do Berserker", "Lutando.", "Bate mais forte, não é empurrado e a vida não cai abaixo de 1. Custa 1 de vida a cada 2 s e, no fim, deixa fraco e lento por 6 s."],
  ARQUEIRO: ["Chuva de Luz", "Acertando flechas, mais ainda de longe.", "Flechas de luz por 3 s numa área a até 48 blocos. Não acerta aliados, aldeões nem pets."],
  MAGO: ["Explosão!", "Gastando mana em habilidades.", "Canto de 3 s e depois uma explosão enorme, que não quebra blocos nem fere o mago. Zera a mana e deixa 8 s lento e fraco."],
  CLERIGO: ["Estandarte da Donzela", "Curando e lutando.", "Anel de luz por 10 a 18 s: aliados curam e recebem metade do dano; mortos-vivos queimam e os outros monstros ficam lentos."],
  LADINO: ["Roubar!", "Lutando.", "Rouba armas, saque e moedas dos monstros próximos e deixa você 5 s invisível e rápido."],
  ASSASSINO: ["Respiração do Trovão", "Lutando.", "Investida de 12 blocos cortando quem está no caminho; até 3 investidas em 5 s."],
  INVOCADOR: ["Levante-se!", "Não precisa encher.", "Ergue como sombra a alma do monstro morto mais próximo."],
};
const ICONE: Record<string, string> = { GUERREIRO: "⚔️", ARQUEIRO: "🏹", MAGO: "🔮", CLERIGO: "✨", LADINO: "🗡️", ASSASSINO: "🥷", INVOCADOR: "👻" };
const COR: Record<string, string> = { GUERREIRO: "#8f2f2f", ARQUEIRO: "#3f6b3a", MAGO: "#4b3f8f", CLERIGO: "#9c7a22", LADINO: "#4a4a55", ASSASSINO: "#5a2346", INVOCADOR: "#3a2a5f" };
const TIPO: Record<string, string> = { SKILLSHOT: "Projétil", ALVO: "Alvo", AREA: "Área", MOBILIDADE: "Mobilidade", BUFF: "Reforço", INVOCAR: "Invocação" };

function moedas(c: number) {
  const o = Math.floor(c / 100), pr = Math.floor((c % 100) / 10), co = c % 10;
  return [o ? `${o} ouro` : "", pr ? `${pr} prata` : "", co || (!o && !pr) ? `${co} cobre` : ""].filter(Boolean).join(", ");
}
function fatiar<T>(l: T[], n: number): T[][] {
  const r: T[][] = [];
  for (let i = 0; i < l.length; i += n) r.push(l.slice(i, i + n));
  return r;
}

const g = globalThis as unknown as { __frxLivros?: { em: number; livros: Livro[] } };

/** Cache de 5 min. */
export async function livros(): Promise<Livro[]> {
  if (g.__frxLivros && Date.now() - g.__frxLivros.em < 300_000) return g.__frxLivros.livros;

  const [config, habs, missoes] = await Promise.all([
    lerConfig().catch(() => null),
    prisma.habilidade.findMany({ where: { ativa: true }, orderBy: [{ ultimate: "asc" }, { nivelReq: "asc" }] }).catch(() => []),
    prisma.missao.findMany({ where: { ativa: true }, orderBy: [{ rankMin: "asc" }, { nivelMin: "asc" }, { ordem: "asc" }] }).catch(() => []),
  ]);
  const nivelRank = LETRAS.map((l, i) => config?.ranks.find((r) => r.rank === l)?.nivelMin ?? [1, 10, 20, 35, 50, 70, 90][i]);

  const mundo: Livro[] = [
    {
      id: "guia", titulo: "Guia do Aventureiro", subtitulo: "Como o mundo funciona", icone: "📖", cor: "#6b4a1e", grupo: "Mundo",
      paginas: [
        { titulo: "Bem-vindo ao FRX-WORLD", blocos: [
          p("O FRX-WORLD é um MMORPG dentro do Minecraft. Você escolhe uma raça e uma classe, sobe de nível, entra para a Guilda de Aventureiros e explora um mundo com reinos, masmorras, portais e uma torre de 100 andares."),
          lista("Nível máximo: 100.", "Tecla K: janela de Status (inventário, habilidades, talentos, reinos, missões, conquistas e grupo).", "Tecla B: Despertar, o poder especial da sua classe, a partir do nível 10.", "Livro de missões: a campanha principal, capítulo por capítulo."),
        ] },
        { titulo: "Nível e experiência", blocos: [
          p("Você ganha experiência matando monstros, cumprindo missões e avançando na história. Cada nível pede mais que o anterior."),
          lista("A cada 2 níveis você ganha 1 ponto de talento.", "Habilidades novas da classe são aprendidas sozinhas quando você chega ao nível delas.", "Monstros muito mais fracos que você dão pouca experiência: 6 níveis abaixo, 75%; 10 abaixo, 40%; 15 abaixo, 10%."),
        ] },
        { titulo: "Zonas de perigo", blocos: [
          p("Todo monstro tem nível. Na superfície, o nível cresce com a distância do ponto de partida: mais ou menos 1 nível a cada 250 blocos, até o 60. Nas outras dimensões o nível segue a faixa de cada uma."),
          lista("Monstro acima do seu nível bate 8% mais forte por nível de diferença, até pouco mais que o dobro.", "Se você está bem acima, ele bate mais fraco e você bate mais forte.", "Dentro das capitais dos reinos não há perigo."),
          nota("Monstros podem nascer como Elite (estrelas e traços), Campeão (nome próprio e barra) ou Chefe. Quanto mais forte, melhor a recompensa."),
        ] },
        { titulo: "Morrer", blocos: [
          lista("Seus itens ficam num túmulo no lugar da morte, não espalhados no chão.", "Moedas na bolsa caem; as guardadas no banco, não.", "Na Torre e nas dungeons de portal, o túmulo aparece do lado de fora, junto à entrada.", "Morrer na Torre custa experiência e 10 minutos de espera para entrar de novo."),
        ] },
        { titulo: "Reinos", blocos: [
          p("O mundo tem oito reinos, cada um com rei, moradores, lojas, banqueiro, taverna e uma campanha própria. Alguns estão longe e ainda esperam ser encontrados."),
          lista("Reputação: ajudar um reino melhora os preços e libera itens e raças especiais.", "Campanha do rei: cinco etapas. No fim, o rei faz uma cerimônia e concede um nome a você.", "Os moradores reagem a quem você é: rank, raça, título e nome mudam o jeito como falam."),
        ] },
      ],
    },
    {
      id: "guilda", titulo: "A Guilda e os Ranks", subtitulo: "De F a S", icone: "🛡️", cor: "#7a5a12", grupo: "Mundo",
      paginas: [
        { titulo: "A Guilda de Aventureiros", blocos: [
          p("A recepcionista da Guilda registra você e entrega o Cartão de Aventureiro. O rank aparece acima da sua cabeça e decide quase tudo: o que você pode vestir, onde pode entrar e quanto ganha nas missões."),
          tabela(["Rank", "Nível mínimo"], LETRAS.map((l, i) => [l, String(nivelRank[i])])),
        ] },
        { titulo: "Como subir de rank", blocos: [
          lista("Chegue ao nível mínimo do próximo rank.", "Peça a prova de promoção à recepcionista.", "Derrote o Examinador em 3 minutos, sem morrer.", "Falhou? Espere 10 minutos e tente de novo."),
          nota("Passar na prova rende um prêmio em moedas, uma única vez por rank."),
        ] },
        { titulo: "O que cada rank dá", blocos: [
          lista("Missões: +10% de experiência e moedas por rank acima do F.", "Lojas e forja: 2% de desconto por rank, somando com a reputação (até 50%).", "Portais: você entra nos portais do seu rank ou abaixo.", "Dimensões: o Nether pede Rank D e o End pede Rank C."),
        ] },
        { titulo: "Classes de equipamento", blocos: [
          p("Toda arma e armadura tem uma classe, de F a S, mostrada na descrição do item. Só usa quem tem aquele rank. Guardar na mochila e nos baús é livre."),
          tabela(["Rank", "Pode usar até"], LETRAS.map((l, i) => [l, EQUIP[i]])),
          nota("Armadura acima do seu rank sai do corpo e volta para a mochila. Arma acima do rank não acerta ninguém, e arco não dispara. As lojas só mostram o que o seu rank já pode usar."),
        ] },
      ],
    },
    {
      id: "missoes", titulo: "Livro das Missões", subtitulo: "Onde achar o que fazer", icone: "📜", cor: "#5c3d1c", grupo: "Mundo",
      paginas: [
        { titulo: "Três tipos de missão", blocos: [
          tabela(["Tipo", "Onde", "Para quê"], [
            ["Campanha", "Livro de missões", "A história e os grandes objetivos"],
            ["Reis", "Falando com o rei", "A campanha de cada reino, em 5 etapas"],
            ["Quadro", "Taverneiro", "Caçar e coletar, valendo experiência e moedas"],
          ]),
          nota("Moedas só vêm das missões do quadro e da venda de itens aos NPCs. Baús, monstros e chefes não dão moedas."),
        ] },
        { titulo: "Missões urgentes", blocos: [
          p("De tempos em tempos uma caçada do quadro vira urgente por alguns minutos. O Sistema avisa quem tem nível e rank para ela."),
          lista("Entregue dentro do prazo e a recompensa dobra.", "Passou do prazo, a missão continua valendo o normal."),
        ] },
        ...fatiar(missoes, 6).map((grupo, i): Pagina => ({
          titulo: i === 0 ? "Missões do quadro" : "Missões do quadro (continuação)",
          blocos: [tabela(["Missão", "Rank", "Nível", "Recompensa"], grupo.map((m) => [
            `${m.titulo}${m.repetivel ? " ↻" : ""}`, LETRAS[m.rankMin] ?? "F", String(m.nivelMin),
            `${m.recompensaXp} XP${m.recompensaMoedas ? ` · ${moedas(Math.round(m.recompensaMoedas))}` : ""}`,
          ])), ...(i === 0 ? [nota("↻ = pode repetir. O bônus do seu rank da Guilda entra em cima desses valores.")] : [])],
        })),
        { titulo: "A história principal", blocos: [
          p("São 14 capítulos em sequência, no livro de missões. Cada um conclui sozinho quando você cumpre o objetivo: entrar para a Guilda, subir de rank, visitar reinos, fechar portais, vencer chefes, subir a Torre."),
          lista("Capítulo 5: título «Selador de Fendas».", "Capítulo 10: título «Veterano».", "Capítulo 14: o Rei Demônio. Quem vence leva o título «Matador do Rei Demônio»."),
        ] },
        { titulo: "Mural de procurados", blocos: [
          p("A cada meia hora pode aparecer um monstro procurado, com nome próprio, quatro vezes a vida normal e uma coluna de fogo marcando o lugar. O aviso traz as coordenadas."),
          lista("Contrato de 25 minutos.", "Quem matar e quem estiver por perto ganha muita experiência.", "Chance de runa e de Mapa do Tesouro."),
        ] },
      ],
    },
    {
      id: "economia", titulo: "Ouro, Prata e Cobre", subtitulo: "Economia e forja", icone: "💰", cor: "#8a6516", grupo: "Mundo",
      paginas: [
        { titulo: "As moedas", blocos: [
          tabela(["Moeda", "Vale"], [["Cobre", "1"], ["Prata", "10 cobres"], ["Ouro", "100 cobres"]]),
          lista("São itens de verdade: ficam na bolsa e caem quando você morre.", "Vêm das missões do quadro e da venda de itens aos NPCs.", "Aldeões comuns negociam em prata no lugar da esmeralda."),
        ] },
        { titulo: "O banco", blocos: [
          p("O banqueiro fica nas capitais. Moeda guardada no banco não cai na morte."),
          lista("Clique no banqueiro para ver o saldo, depositar e sacar.", "Compras caras tiram da bolsa e, se faltar, do banco."),
        ] },
        { titulo: "Lojas", blocos: [
          lista("Ferreiro: armas e ferramentas. Armeiro: armaduras e escudo. Mercador: utilidades. Cozinheiro: comida. Pedreiro: blocos.", "O estoque muda com o seu nível e, em parte, todo dia.", "Só aparecem equipamentos que o seu rank da Guilda já pode usar.", "Desconto: reputação no reino mais 2% por rank da Guilda."),
        ] },
        { titulo: "Raridade", blocos: [
          tabela(["Raridade", "Cor", "Dano extra"], [["Comum", "branco", "0%"], ["Incomum", "verde", "+8%"], ["Raro", "azul", "+15%"], ["Épico", "roxo", "+25%"], ["Lendário", "dourado", "+40%"]]),
          nota("O armeiro reforja a raridade de um item (agachar e clicar com o item na mão). Nunca piora."),
        ] },
        { titulo: "Aprimorar no ferreiro", blocos: [
          p("O ferreiro aprimora de +1 até +10, cobrando moedas e minério. Cada nível dá +6% de dano nas armas ou +0,3 de armadura por peça."),
          tabela(["Nível alvo", "Chance"], [["+1 a +4", "100%"], ["+5", "90%"], ["+6", "80%"], ["+7", "70%"], ["+8", "55%"], ["+9", "45%"], ["+10", "25%"]]),
          nota("Se falhar, você perde o material, mas o item não quebra."),
        ] },
      ],
    },
    {
      id: "torre", titulo: "A Torre Infinita", subtitulo: "100 andares", icone: "🗼", cor: "#4a3a6b", grupo: "Mundo",
      paginas: [
        { titulo: "Como funciona", blocos: [
          p("A Torre fica perto do reino principal. Cada andar é uma sala cheia de monstros; limpou, sobe. Os andares mudam de tema a cada dez: masmorra, deserto, floresta, fornalha, geleira e abismo."),
          lista("A experiência vem só ao vencer o andar inteiro, e cresce bastante com a altura.", "A cada andar você escolhe continuar ou sair.", "Não dá para colocar cama nem salvar ponto de retorno lá dentro."),
        ] },
        { titulo: "Chefes e andar seguro", blocos: [
          lista("A cada 10 andares há um chefe.", "Nos andares 20, 40, 60 e 80 o chefe é um dos grandes chefes dos mods.", "Depois de cada chefe vem um andar seguro: progresso salvo, vida cheia, baú, cozinheiro e mercador.", "Quando voltar à Torre, você recomeça do último andar seguro.", "No andar 100 espera o Senhor da Torre."),
        ] },
        { titulo: "Em grupo", blocos: [
          p("Até 5 aventureiros sobem juntos. Basta estarem dentro do saguão: quando alguém pisa no portal, começa uma contagem de 5 segundos e todos vão."),
          tabela(["Jogadores", "Experiência de cada um"], [["1", "100%"], ["2", "75%"], ["3", "67%"], ["4", "62%"], ["5", "60%"]]),
          nota("Cada companheiro a mais deixa os monstros um nível acima, com +40% de vida e +10% de dano, e traz 3 monstros a mais por andar."),
        ] },
        { titulo: "Fichas e a loja", blocos: [
          p("Vencer andares rende fichas da Torre: 1 por andar, 10 por chefe e mais 2 quando o andar é novo para você. O Mercador troca fichas por poções, runas, mapas do tesouro, um pet e equipamentos exclusivos."),
        ] },
        { titulo: "Morrer na Torre", blocos: [
          lista("A morte é de verdade: o túmulo aparece na porta da Torre.", "Você perde parte da experiência do nível, mais quanto maior o seu nível.", "Fica 10 minutos sem poder entrar."),
        ] },
      ],
    },
    {
      id: "portais", titulo: "Portais e Chefes", subtitulo: "Fendas, masmorras e monstros gigantes", icone: "🌀", cor: "#5b2a7a", grupo: "Mundo",
      paginas: [
        { titulo: "Portais", blocos: [
          p("De tempos em tempos um portal com rank abre perto de uma capital, com um pilar de luz. Ele leva a uma dungeon temporária com três câmaras e um chefão."),
          lista("Só entra quem tem o rank do portal ou maior.", "Todos que atravessam o mesmo portal caem na mesma dungeon.", "Vocês têm 18 minutos lá dentro.", "Se ninguém fechar o portal em 20 minutos, ele se rompe e vira invasão."),
          tabela(["Rank do portal", "Aventureiros lá dentro"], LETRAS.map((l, i) => [l, String(LOTACAO[i])])),
        ] },
        { titulo: "Os chefões", blocos: [
          p("São 14 chefões, sorteados pelo rank. Cada um tem golpes próprios: saltos, ondas de choque, veneno, gelo, raios, cura, invocação de ajudantes e fúria quando a vida está baixa."),
          nota("Vencer um portal rende muita experiência e chance de runa, tomo e Mapa do Tesouro."),
        ] },
        { titulo: "Masmorras dos reinos", blocos: [
          p("Embaixo e perto dos reinos há masmorras fixas com chefes dos mods. São conteúdo de fim de jogo."),
          lista("Os chefes de lá têm várias vezes a vida e o dano normais.", "Abaixo do nível 50, seus golpes mal arranham e os deles vêm em triplo."),
        ] },
        { titulo: "Chefe mundial", blocos: [
          lista("Aparece em horários marcados, longe das capitais.", "Avisos com 10, 5 e 1 minuto de antecedência e barra de vida para todos.", "A recompensa é dividida pelo dano que cada um causou. Os três primeiros levam arma Épica ou Lendária."),
        ] },
      ],
    },
    {
      id: "dimensoes", titulo: "Atlas das Dimensões", subtitulo: "Do Crepúsculo ao Outro Lado", icone: "🌌", cor: "#1f3a5f", grupo: "Mundo",
      paginas: [
        { titulo: "Níveis de cada dimensão", blocos: [
          tabela(["Dimensão", "Monstros", "Exige"], [
            ["Superfície", "1 a 60", "—"], ["Floresta do Crepúsculo", "14 a 30", "—"], ["Undergarden", "20 a 35", "—"], ["Nether", "22 a 40", "Rank D"],
            ["Everbright", "25 a 40", "—"], ["Aether", "30 a 45", "—"], ["O Fim", "30 a 50", "Rank C"], ["Everdawn", "35 a 50", "—"], ["O Outro Lado", "45 a 60", "—"],
          ]),
          nota("Entrar abaixo do nível mínimo de uma dimensão faz os monstros baterem 50% mais forte."),
        ] },
        { titulo: "O Nether", blocos: [
          p("É a dimensão mais cruel do servidor. Todo monstro de lá tem uma vez e meia a mais de vida e o dobro do dano, além do bônus de nível."),
          lista("Emboscadas: poucos minutos depois de entrar, e depois de tempos em tempos, uma horda nasce perto de você.", "Uma em cada quatro emboscadas é um chefão do seu rank.", "Há estruturas e criaturas próprias em cada bioma."),
        ] },
        { titulo: "A Floresta do Crepúsculo", blocos: [
          p("Os monstros de lá têm 80% a mais de vida e 50% a mais de dano. É o primeiro grande teste fora da superfície."),
        ] },
      ],
    },
    {
      id: "racas", titulo: "O Livro das Raças", subtitulo: "16 raças", icone: "🧬", cor: "#2f5a4a", grupo: "Mundo",
      paginas: [
        { titulo: "Principal e secundária", blocos: [
          p("Você tem uma raça principal e pode ter uma secundária. Cada raça traz vantagens e um preço. Trocar de raça custa moedas, cada vez mais."),
        ] },
        { titulo: "Raças comuns", blocos: [
          p("Livres desde a criação do personagem."),
          tabela(["Raça", "Vantagens", "Preço"], [
            ["Humano", "+10% de experiência e de reputação", "—"],
            ["Elfo", "+15% de recurso, +15% de dano com arco", "−1 coração"],
            ["Anão", "+3 de armadura, +20% de mineração", "−5% de velocidade"],
            ["Beastkin", "+10% de velocidade, visão noturna", "−10% de recurso"],
            ["Halfling", "+2 de sorte, 10% de esquiva", "−10% de dano corpo a corpo"],
          ]),
        ] },
        { titulo: "Raças especiais", blocos: [
          p("Liberam no nível 20 ou com boa reputação no reino ligado à raça."),
          tabela(["Raça", "Vantagens", "Preço"], [
            ["Demônio", "Imune a fogo, +15% de dano mágico", "Recebe +15% de dano mágico"],
            ["Vampiro", "Rouba 8% de vida, visão noturna", "Fraco sob o sol"],
            ["Dragonoide", "+3 corações, metade do dano de fogo", "Fome mais rápida"],
            ["Oni", "+12% de dano corpo a corpo, +1 coração", "−15% de recurso"],
            ["Espírito", "+25% de regeneração de recurso, queda lenta", "−2 corações"],
            ["Gigante", "+4 corações, mais alcance", "−8% de velocidade"],
          ]),
        ] },
        { titulo: "Raças lendárias", blocos: [
          p("Vêm de grandes feitos e nunca podem ser raça secundária."),
          tabela(["Raça", "Como liberar"], [
            ["Anjo", "Concluir a campanha do reino sagrado"],
            ["Rei Demônio", "Concluir a campanha dos Lordes Demônios"],
            ["Dragão", "Derrotar um dragão"],
            ["Ser Celestial", "Chegar ao nível 100"],
          ]),
        ] },
      ],
    },
    {
      id: "poderes", titulo: "Talentos e Poderes", subtitulo: "Como ficar mais forte", icone: "🌟", cor: "#7a2f5a", grupo: "Mundo",
      paginas: [
        { titulo: "Pontos de talento", blocos: [
          lista("1 ponto a cada 2 níveis. No nível 100 são 50 pontos.", "Cada classe tem 3 ramos com 5 talentos em linha.", "O 2º talento de um ramo pede 5 pontos gastos nele, o 3º pede 10, o 4º 15 e o último 20.", "Dá para devolver todos os pontos e redistribuir, pagando em moedas."),
        ] },
        { titulo: "Habilidades", blocos: [
          p("As habilidades da classe são aprendidas sozinhas quando você chega ao nível de cada uma. Algumas vêm de um Tomo Arcano."),
          lista("Cada habilidade sobe de nível com o uso, de 1 a 10.", "Nível 2 com 5 usos, 3 com 20, 5 com 80 e 10 com 405.", "Cada nível reduz a recarga em 3% e deixa a habilidade mais forte."),
        ] },
        { titulo: "Despertar", blocos: [
          p("A partir do nível 10, cada classe tem um poder especial na tecla B. Uma barra fina enche conforme você luta do jeito da sua classe; em 100%, está pronto."),
          nota("O Despertar de cada classe está no livro dela."),
        ] },
        { titulo: "Habilidade única", blocos: [
          p("Todo aventureiro recebe uma habilidade única, só dele, que evolui com os abates. Ela aparece na ficha do jogador aqui no site."),
        ] },
        { titulo: "Runas", blocos: [
          p("Runas se encaixam na bigorna: item à esquerda, runa à direita. Cada item aceita até 2 runas de tipos diferentes. Duas runas iguais do mesmo nível viram uma do nível seguinte."),
          tabela(["De arma", "Efeito"], [["Sangue", "Cura parte do dano causado"], ["Gelo", "Chance de congelar"], ["Fogo", "Dano extra e incendeia"], ["Trovão", "Chance de raio"], ["Fortuna", "Chance de experiência em dobro"], ["Execução", "Mais dano em alvos feridos"]]),
          tabela(["De armadura", "Efeito"], [["Pedra", "Reduz o dano recebido"], ["Espinhos", "Devolve parte do dano"], ["Vigor", "Mais corações"], ["Vento", "Mais velocidade"], ["Fênix", "Salva de um golpe fatal"]]),
        ] },
      ],
    },
    {
      id: "eventos", titulo: "Almanaque de Eventos", subtitulo: "O que acontece sem aviso", icone: "🌙", cor: "#6b1f2a", grupo: "Mundo",
      paginas: [
        { titulo: "Temporadas", blocos: [
          p("Todo mês começa um placar novo. Na virada, os três primeiros ganham runas e um título."),
          tabela(["Feito", "Pontos"], [["Capítulo da história", "100"], ["Portal fechado", "80"], ["Procurado", "60"], ["Chefe", "50"], ["Invasão", "40"], ["Tesouro", "30"], ["Andar novo da Torre", "20"], ["Abate", "1"]]),
        ] },
        { titulo: "Eventos do céu", blocos: [
          lista("Lua de Sangue: em algumas noites os monstros ficam mais rápidos, e a experiência e o saque deles vêm em dobro até amanhecer.", "Chuva de meteoros: meteoros caem perto de jogadores, sem destruir nada, e deixam minério raro com uma coluna de fogo."),
        ] },
        { titulo: "Mapas do tesouro", blocos: [
          p("O mapa dá uma pista de distância e direção. Com ele na mão, o botão direito diz se está frio, morno ou pelando. Perto o bastante, o baú aparece."),
          nota("Vêm de chefes, procurados, portais fechados e da Loja da Torre."),
        ] },
        { titulo: "Encontros estranhos", blocos: [
          p("Dizem que um ladrão corre pelas estradas roubando uma moeda de quem passa, e que quem o alcança recebe dez de volta. Dizem também que, muito de vez em quando, um craque aparece do nada, dribla você e some."),
        ] },
      ],
    },
  ];

  const classes: Livro[] = (Object.keys(CLASSES) as (keyof typeof CLASSES)[]).map((c) => {
    const def = CLASSES[c];
    const dele = habs.filter((h) => h.classe === c);
    const desp = DESPERTAR[c];
    const paginas: Pagina[] = [
      { titulo: def.nome, blocos: [
        p(def.estilo),
        lista(`Recurso: ${def.recurso}.`, `${dele.length} habilidades, sendo ${dele.filter((h) => h.ultimate).length} supremas.`, "3 ramos de talento para moldar o seu jeito de jogar."),
      ] },
      ...(desp ? [{ titulo: `Despertar: ${desp[0]}`, blocos: [p(desp[2]), lista(`Como enche: ${desp[1]}`, "Tecla B, a partir do nível 10.")] } as Pagina] : []),
      ...fatiar(dele, 4).map((grupo, i): Pagina => ({
        titulo: i === 0 ? "Habilidades" : "Habilidades (continuação)",
        blocos: grupo.flatMap((h): Bloco[] => [
          { t: "p", texto: `${h.ultimate ? "💥 " : ""}${h.nome} — nível ${h.nivelReq}${h.aprendizado === "ITEM" ? " (por tomo)" : ""}` },
          { t: "nota", texto: `${h.descricao || "Sem descrição."} ${TIPO[h.tipo] ?? h.tipo} · custa ${h.custo} de ${def.recurso.toLowerCase()} · recarga de ${h.cooldown} s.` },
        ]),
      })),
    ];
    if (c === "INVOCADOR") paginas.push({ titulo: "Exército de Sombras", blocos: [
      p("Ao matar um monstro, a alma dele fica 20 segundos no chão. Com mana, você a ergue como sombra e ela passa a lutar por você."),
      lista("O exército e o número de sombras em campo crescem com o seu nível.", "Sombras sobem de nível com abates, os delas e os seus enquanto estão em campo: nível 1 com 3 abates, nível 5 com 75, nível 10 com 300.", "Cada nível deixa a sombra 3% mais forte, partindo de 40% da força do monstro original.", "Sombra que morre leva 1 minuto para se recompor.", "Chefes só podem ser erguidos a partir do nível 25."),
    ] });
    return { id: `classe-${c.toLowerCase()}`, titulo: def.nome, subtitulo: `Classe · ${def.recurso}`, icone: ICONE[c] ?? "📕", cor: COR[c] ?? "#4a3a2a", grupo: "Classes", paginas };
  });

  const todos = [...mundo, ...classes];
  g.__frxLivros = { em: Date.now(), livros: todos };
  return todos;
}
