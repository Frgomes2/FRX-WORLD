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
  PALADINO: ["Égide da Aurora", "Apanhando, batendo de perto e gastando mana.", "Cúpula de luz por 8 a 14 s que segue você: quem está dentro leva 60% menos dano. Tudo o que ela barrar volta num clarão final sobre os inimigos, em dobro nos mortos-vivos."],
  BARDO: ["Bis!", "Gastando mana nas canções e ferindo inimigos.", "Na hora, zera as recargas e enche o recurso de todos os jogadores a até 12 blocos, você incluso. Por 8 s o grupo causa 20% a mais de dano e regenera."],
  MONGE: ["Portão Interior", "Acertando socos e golpes de perto.", "Por 8 a 12 s os socos causam o dobro e estouram em volta do alvo, você corre e salta mais e as habilidades não gastam chi. No fim vem a exaustão: fraqueza e lentidão por 5 s."],
  DRUIDA: ["Coração da Floresta", "Gastando mana, batendo de perto e apanhando.", "Por 10 s as habilidades não gastam mana, as garras batem 50% mais forte, você regenera e as formas de lobo e de ave deixam de apanhar a mais."],
  CARTOMANTE: ["Mão Perfeita", "Acertando habilidades.", "Por 8 a 12 s a mão vira três Coringas e toda carta nova também é Coringa: cada golpe fere 40% a mais, cura você, devolve mana e prende o alvo."],
  SAMURAI: ["Lâmina das Três Formas", "Acertando golpes de perto; com uma katana na mão enche bem mais rápido.", "Por 8 a 12 s todo golpe de katana é um Saque e alterna três formas: Brasa (queima), Maré (deixa lento) e Relâmpago (dano extra). Você corre e golpeia mais rápido."],
};
const ICONE: Record<string, string> = { CARTOMANTE: "🃏", GUERREIRO: "⚔️", ARQUEIRO: "🏹", MAGO: "🔮", CLERIGO: "✨", LADINO: "🗡️", ASSASSINO: "🥷", INVOCADOR: "👻", PALADINO: "🛡️", BARDO: "🎵", SAMURAI: "🎌", MONGE: "👊", DRUIDA: "🌿" };
const COR: Record<string, string> = { CARTOMANTE: "#1f6f7a", SAMURAI: "#9c2f2f", GUERREIRO: "#8f2f2f", ARQUEIRO: "#3f6b3a", MAGO: "#4b3f8f", CLERIGO: "#9c7a22", LADINO: "#4a4a55", ASSASSINO: "#5a2346", INVOCADOR: "#3a2a5f", PALADINO: "#a8892e", BARDO: "#8a3f8f", MONGE: "#2f7f78", DRUIDA: "#3d6b2a" };
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
          lista("Nível máximo: 100.", "Tecla K: janela de Status (inventário, habilidades, talentos, reinos, missões e conquistas).", "Tecla B: Despertar, o poder especial da sua classe, a partir do nível 10.", "Tecla F8: escolher ou trocar de personagem.", "Livro de missões: a campanha principal, capítulo por capítulo."),
        ] },
        { titulo: "Seus personagens", blocos: [
          p("Cada conta pode ter até 3 personagens. Ao entrar no servidor aparece a tela de escolha, com um estandarte para cada um; a tecla F8 abre a mesma tela a qualquer hora."),
          lista("Vaga livre: clique em Criar personagem e dê um nome. Ele começa do zero, escolhendo raça e classe.", "Jogar: troca de personagem. O jogo sai e entra de novo no servidor sozinho, em poucos segundos.", "Nome: muda o nome, que aparece no chat, em cima da cabeça e na lista de jogadores."),
          tabela(["De cada personagem", "Da conta (vale para os três)"], [
            ["Raça, classe, nível e habilidades", "Banco"],
            ["Itens, equipamento e bolsa de moedas", "Terrenos"],
            ["Rank da Guilda, missões e contratos", "Anúncios e valores a receber no leiloeiro"],
            ["Clãs, placar da Torre e temporada", "Progressos do jogo e livro de missões"],
          ]),
          nota("O limite de aldeões do Mercador de Escravos também é da conta: soma os três personagens."),
          nota("Todo personagem novo nasce com um kit: armadura de couro já vestida, espada, machado, pá e picareta de pedra e 32 pães. É tudo de classe F, que qualquer um pode usar."),
        ] },
        { titulo: "Sua conta no site", blocos: [
          p("No site, o botão Minha conta mostra o seu inventário, as moedas da bolsa e do banco, os terrenos e a ficha de cada personagem, sem abrir o jogo."),
          lista("Primeiro acesso: dentro do jogo, aperte F9. O jogo mostra um código de 6 números que vale por 10 minutos.", "No site, clique em Minha conta, escolha Primeiro acesso, digite o seu nick, o código e a senha que quer usar.", "Das próximas vezes, entre só com o nick e a senha.", "Esqueceu a senha? Faça o Primeiro acesso de novo, com um código novo."),
          nota("A senha é só do site, não é a da sua conta do Minecraft. Não passe o código para ninguém. A página é só de consulta: nada muda no jogo por ela."),
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
        { titulo: "Proteja seus baús", blocos: [
          p("Qualquer baú, barril ou fornalha pode ser trancado. Agachado, clique nele com um gancho de armadilha na mão: o gancho é gasto e o baú passa a ser só seu."),
          lista("Só o dono abre e quebra o baú trancado.", "Explosões não o destroem.", "Para destrancar, agachado, clique com um gancho de novo. O gancho usado volta para você."),
          nota("O gancho de armadilha é feito com um lingote de ferro, um graveto e uma tábua."),
        ] },
        { titulo: "Reinos", blocos: [
          p("O mundo tem oito reinos, cada um com rei, moradores, lojas, banqueiro, taverna e uma campanha própria. Alguns estão longe e ainda esperam ser encontrados."),
          lista("Reputação: ajudar um reino melhora os preços e libera itens, raças especiais, o Favor Real (Honrado) e o Corcel do reino (Reverenciado).", "Rivais: Lubelius e Lordes Demônios, Farmus e Tempest, Império do Leste e Sarion. Ajudar um desagrada o outro. Dwargon e Sakura são neutros.", "Má fama: Malvisto paga mais caro, Hostil não é atendido e é atacado pelos guardas, Inimigo tem a cabeça a prêmio. O rei aceita uma multa para limpar o nome.", "Contrabandista: fora das capitais, dá serviços contra os reinos e vende mercadoria só para quem tem má fama.", "Moradores: os lojistas lembram das suas compras e dão desconto a freguês. À noite a loja fica com o aprendiz, mais cara, e os mestres vão para a taverna, onde contam boatos e pedem favores.", "Ferreiro e armeiro são rivais: ajudar um dá desconto na loja dele e encarece a do outro.", "Encontros: longe das capitais, às vezes aparece um viajante ferido, um mercador perdido, uma estrela caída, um baú que foge ou um mensageiro com uma carta. São raros e nenhum obriga a lutar.", "Campanha do rei: cinco etapas. No fim, o rei faz uma cerimônia e concede um nome a você.", "Os moradores reagem a quem você é: rank, raça, título e nome mudam o jeito como falam."),
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
          lista("Missões: +10% de experiência e moedas por rank acima do F.", "Lojas e forja: 2% de desconto por rank, somando com a reputação (até 50%).", "Portais: você entra nos portais do seu rank ou abaixo.", "Dimensões: o Nether pede Rank C e o End pede Rank A.", "Élitros: só voam a partir do Rank A.", "Habilidades: cada rank até o B libera mais um espaço na barra (veja o livro Talentos e Poderes).", "Terrenos: quanto maior o rank, maior a área e a quantidade de terrenos.", "Mercador de Escravos: só vende a partir do Rank D, e o limite de aldeões cresce com o rank."),
          tabela(["Rank", "Lado do terreno", "Área somada", "Terrenos", "Aldeões comprados"], [
            ["F", "24", "512", "1", "não compra"], ["E", "34", "1.200", "2", "não compra"], ["D", "46", "2.200", "3", "4"], ["C", "58", "3.600", "4", "8"],
            ["B", "68", "5.600", "5", "16"], ["A", "92", "9.000", "6", "34"], ["S", "114", "14.000", "7", "68"],
          ]),
        ] },
        { titulo: "Contratos da Guilda", blocos: [
          p("Todo dia a Guilda sorteia três contratos para você, e toda semana mais um. Eles contam sozinhos enquanto você joga e pagam na hora, sem entregar nada."),
          lista("Abra o quadro pelo botão Contratos do Cartão de Aventureiro ou no balcão da recepcionista.", "Cada cartão mostra o objetivo, a barra de progresso e a recompensa em moedas e experiência.", "Fechar os três do dia paga um extra.", "Os do dia renovam à meia-noite; o da semana, na segunda-feira.", "Quanto maior o seu rank, maior o alvo e o pagamento."),
        ] },
        { titulo: "Coleção de criaturas", blocos: [
          p("Cada tipo de criatura que você derrota pela primeira vez entra na sua coleção. Ao bater certas marcas, a Guilda paga moedas e experiência."),
          lista("Marcas: 10, 25, 50, 100, 150 e 200 tipos diferentes.", "Chefes contam à parte: 3, 7 e 14 chefes diferentes.", "O resumo aparece na última linha do quadro de contratos e na sua ficha no site."),
        ] },
        { titulo: "Classes de equipamento", blocos: [
          p("Toda arma, armadura, ferramenta, escudo e élitro tem uma classe, de F a S, mostrada na descrição do item. Só usa quem tem aquele rank. Guardar na mochila e nos baús é livre."),
          nota("Anéis: você tem 10 espaços de anel, mas não pode usar dois anéis iguais ao mesmo tempo."),
          tabela(["Rank", "Pode usar até"], LETRAS.map((l, i) => [l, EQUIP[i]])),
          nota("Armadura acima do seu rank sai do corpo e volta para a mochila. Arma acima do rank não acerta ninguém, arco não dispara e ferramenta não minera. As lojas só mostram o que o seu rank já pode usar."),
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
            ["Contratos", "Cartão de Aventureiro", "Três tarefas por dia e uma por semana, que contam sozinhas"],
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
          lista("Clique no banqueiro para ver o saldo, depositar e sacar.", "Compras caras tiram da bolsa e, se faltar, do banco.", "O banco é da conta: o saldo é o mesmo para os seus três personagens. A bolsa é de cada um."),
          p("O cofre rende: todo dia, à meia-noite, o banco paga juros sobre o que está guardado. A taxa começa em 0,5% ao dia e sobe 0,1% a cada 7 dias sem sacar, até 1% ao dia. Sacar volta à taxa inicial; depositar não atrapalha."),
          lista("Só rende sobre até 500 ouro guardados; o resto fica seguro, mas parado.", "Quem fica fora do jogo recebe ao voltar, contando no máximo os últimos 7 dias.", "A tela do banqueiro mostra a taxa atual, quanto rende amanhã e quando a taxa sobe."),
        ] },
        { titulo: "O sábio e os encantamentos", blocos: [
          p("O sábio das capitais consegue tirar um encantamento de um item e guardá-lo num livro em branco. Depois, é só usar a bigorna para passar o livro para outro item."),
          lista("Converse com o sábio e escolha \"Extrair um encantamento\". Na bancada, coloque o item de um lado e um livro em branco do outro, como na bigorna, e escolha qual encantamento vai para o livro.", "Custa caro: moedas (da bolsa e, se faltar, do banco), níveis de experiência e um livro. Afiação V, por exemplo, custa 100 ouro.", "Até o nível III sempre dá certo. No nível IV e acima pode falhar: o pagamento se perde, mas o item fica como estava.", "Relíquias do Códex e maldições não podem ter o encantamento extraído."),
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
      id: "terras", titulo: "Terras, Clãs e Comércio", subtitulo: "Terrenos, clãs e os mercadores", icone: "🏡", cor: "#3f5a2a", grupo: "Mundo",
      paginas: [
        { titulo: "Comprar um terreno", blocos: [
          p("No mundo normal você pode comprar uma área retangular. Nela só você, e quem você liberar, coloca, quebra e usa blocos; monstros e explosões não destroem nada. O terreno vai do fundo ao céu."),
          lista("Abra o mapa (tecla J), clique com o botão direito e marque o 1º e o 2º canto.", "O painel mostra o desenho da área, o preço e os limites do seu rank.", "Sem o mapa: no painel de terrenos, os botões 1º canto aqui e 2º canto aqui marcam onde você está.", "Preço: 1,2 de cobre por bloco de área. Vender devolve metade."),
          nota("Capitais, a Torre e o ponto de partida são terras do reino e não estão à venda. Os limites de cada rank estão no livro A Guilda e os Ranks."),
        ] },
        { titulo: "Ampliar e dar permissões", blocos: [
          p("Para ampliar, escolha uma área que passe por cima de um terreno que já é seu: você paga só os blocos novos, e só eles contam no limite."),
          lista("Aba Meus terrenos: vender, mostrar as divisas no chão e abrir ou fechar os terrenos para todos.", "Aba Quem pode mexer: para cada jogador você liga ou desliga três permissões."),
          tabela(["Permissão", "O que libera"], [["Colocar", "Colocar blocos e usar baldes"], ["Quebrar", "Quebrar blocos e bater em bichos e molduras"], ["Baús e portas", "Abrir baús, portas e alavancas e mexer nos bichos"]]),
          nota("Os terrenos comprados aparecem pintados no mapa: verde os seus, laranja os liberados para você e vermelho os dos outros."),
        ] },
        { titulo: "Clãs", blocos: [
          p("O Mestre dos Clãs fica nas capitais. Cada personagem pode estar em até 3 clãs, e cada clã tem até 30 membros."),
          lista("Fundar um clã ou pedir entrada num que já existe.", "Cofre do clã: depositar e sacar moedas, com o registro de quem mexeu.", "O líder escolhe o nome, a cor e quem pode sacar, nomeia oficiais e passa a liderança.", "Líder e oficiais aceitam ou recusam os pedidos de entrada."),
          nota("Os clãs são de cada personagem: um personagem novo não entra sozinho nos clãs dos outros."),
        ] },
        { titulo: "O Leiloeiro", blocos: [
          p("O Leiloeiro, nas capitais, é o mercado entre jogadores."),
          lista("Comprar: vitrine em cartões com o item, o preço e quem vende. Dá para procurar por item ou vendedor e ordenar por preço.", "Vender: clique no item na sua bolsa e defina o preço em ouro, prata e cobre.", "Receber: o dinheiro das vendas fica guardado com o Leiloeiro, mesmo que você esteja fora do jogo.", "A taxa é de 5% e o anúncio vale por 7 dias."),
          nota("O item chega ao comprador com encantamentos, forja e raridade intactos."),
        ] },
        { titulo: "Mercador de Discos", blocos: [
          p("Vende discos de música feitos para o servidor. Clique numa jukebox com o disco na mão para tocar; clique de novo para tirar."),
          lista("Ouvir: toca a música antes de comprar.", "Estilo: mostra só os discos de um estilo de música.", "Edição limitada: alguns discos têm poucas unidades no mundo. O cartão mostra quantas restam e cada disco sai numerado.", "Alguns discos pedem rank na Guilda."),
          nota("Para ouvir as músicas é preciso aceitar o pacote de recursos do servidor ao entrar."),
        ] },
        { titulo: "Mercador de Escravos", blocos: [
          p("Vende aldeões sem profissão, só a aventureiros de Rank D ou mais. Você escolhe a roupa do aldeão e recebe um contrato."),
          lista("Use o contrato no chão, onde quiser, para o aldeão aparecer.", "Com uma bancada de trabalho livre por perto, ele aprende o ofício.", "Cada aldeão custa 5 de ouro.", "Quando um aldeão comprado morre, a vaga volta."),
          tabela(["Rank", "Aldeões que você pode ter"], [["D", "4"], ["C", "8"], ["B", "16"], ["A", "34"], ["S", "68"]]),
        ] },
        { titulo: "Costureira", blocos: [
          p("A Costureira costura bolsos na sua calça. Cada bolso é um espaço a mais para guardar itens, além da mochila."),
          p("Na mesa de costura dela há cinco serviços: costurar bolsos (2 ouro cada), passar os bolsos de uma calça velha para uma nova (50 cobre por bolso), remendar armaduras e élitros, bordar (acabamento de armadura por 15 ouro e nome colorido por 5 ouro) e o manto de disfarce: por 30 ouro, 30 minutos em que os guardas não reconhecem quem é Hostil ao reino. Não vale para Inimigo, e atacar alguém na cidade rasga o manto."),
          lista("Na mesa, ponha a calça no espaço da peça. Serve qualquer calça, de couro a netherita.", "Cada bolso custa 2 de ouro. A calça aceita até 12 bolsos.", "Vestindo a calça, os bolsos aparecem dentro do próprio inventário, num painel à esquerda da mochila: é só clicar para guardar e pegar. Segurando Shift, o item volta direto para a mochila. A tecla O também abre os bolsos sem abrir o inventário.", "Caixas de shulker e outros itens que guardam itens não entram no bolso."),
          nota("Os bolsos ficam na calça, mas o que está dentro é do personagem. Se você tirar a calça ou vestir uma com menos bolsos, o que não couber volta para a mochila ou cai no chão. Ao morrer, o que está nos bolsos cai junto com a mochila."),
        ] },
        { titulo: "Criador de Dragões", blocos: [
          p("O Criador de Dragões vende ovos de dragão. São o item mais caro do reino: é coisa para quem já juntou uma fortuna."),
          tabela(["Dragão", "Raridade", "Preço em ouro"], [["Floresta", "★", "1.500"], ["Água", "★", "1.500"], ["Gelo", "★★", "2.000"], ["Fogo", "★★", "2.500"], ["Éter", "★★★", "4.000"], ["Nether", "★★★", "5.000"], ["Fantasma", "★★★★", "7.500"], ["Fim", "★★★★★", "12.000"]]),
          lista("Coloque o ovo no chão e clique nele para começar a chocar.", "O filhote nasce selvagem: dome com peixe e, quando crescer, ponha uma sela para voar.", "Fora do Criador, só o ovo do Dragão do Fim pode ser chocado."),
          nota("O ovo choca sempre do tipo que você comprou, em qualquer lugar. Cada dragão tem a sua marca: o do Éter voa mais rápido, o do Nether tem couraça, o do Fim tem a maior vida; os de Fogo e do Nether não se queimam."),
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
            ["Superfície", "1 a 60", "—"], ["Floresta do Crepúsculo", "14 a 30", "—"], ["Undergarden", "20 a 35", "—"], ["Nether", "55 a 80", "Rank C"],
            ["Everbright", "25 a 40", "—"], ["Aether", "30 a 45", "—"], ["O Fim", "75 a 100", "Rank A"], ["Everdawn", "35 a 50", "—"], ["O Outro Lado", "45 a 60", "—"],
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
          lista("Cada habilidade sobe de nível com o uso, de 1 a 30.", "Nível 2 com 5 usos, 3 com 20, 5 com 80, 10 com 405, 20 com 1.805 e 30 com 4.205.", "Até o nível 10, cada nível reduz a recarga em 3% e aumenta o dano em 8%. Do 11 ao 30 o ganho é menor: 1% de recarga e 3% de dano por nível."),
        ] },
        { titulo: "A barra de habilidades", blocos: [
          p("Todo aventureiro tem 4 espaços de habilidade e 1 de ultimate. O rank da Guilda libera mais espaços, para você levar mais habilidades prontas para a luta."),
          tabela(["Rank da Guilda", "Espaços", "Tecla do espaço novo"], [["F", "4 + ultimate", "—"], ["E", "5 + ultimate", "Z"], ["D", "6 + ultimate", "H"], ["C", "7 + ultimate", "N"], ["B, A e S", "8 + ultimate", "Y"]]),
          lista("Os espaços novos aparecem numa linha acima da barra.", "Tecla K: escolha qual habilidade vai em cada espaço e troque a tecla de qualquer um.", "Ao subir de rank, o espaço novo aparece na hora.", "Cada classe tem pelo menos 12 habilidades: escolher quais levar faz parte da estratégia."),
        ] },
        { titulo: "Despertar", blocos: [
          p("A partir do nível 10, cada classe tem um poder especial na tecla B. Uma barra fina enche conforme você luta do jeito da sua classe; em 100%, está pronto."),
          nota("O Despertar de cada classe está no livro dela."),
        ] },
        { titulo: "Habilidade única", blocos: [
          p("Todo aventureiro recebe uma habilidade única, só dele, que evolui com os abates. Ela aparece na ficha do jogador aqui no site. São 20 habilidades diferentes, e o sorteio dá preferência às que menos gente tem."),
        ] },
        { titulo: "Runas", blocos: [
          p("Runas se encaixam na bigorna: item à esquerda, runa à direita. Cada item aceita até 2 runas de tipos diferentes. Duas runas iguais do mesmo nível viram uma do nível seguinte."),
          tabela(["De arma", "Efeito"], [["Sangue", "Cura parte do dano causado"], ["Gelo", "Chance de congelar"], ["Fogo", "Dano extra e incendeia"], ["Trovão", "Chance de raio"], ["Fortuna", "Chance de experiência em dobro"], ["Execução", "Mais dano em alvos feridos"]]),
          tabela(["De armadura", "Efeito"], [["Pedra", "Reduz o dano recebido"], ["Espinhos", "Devolve parte do dano"], ["Vigor", "Mais corações"], ["Vento", "Mais velocidade"], ["Fênix", "Salva de um golpe fatal"]]),
          nota("Quer tirar uma runa? O sábio das capitais tira, na aba Runas da bancada dele: a runa sai inteira e volta para você. Custa 10 ouro (nível I), 25 (II) ou 50 (III)."),
        ] },
      ],
    },
    {
      id: "eventos", titulo: "Almanaque de Eventos", subtitulo: "O que acontece sem aviso", icone: "🌙", cor: "#6b1f2a", grupo: "Mundo",
      paginas: [
        { titulo: "Temporadas", blocos: [
          p("Todo mês começa um placar novo. Na virada, os três primeiros ganham runas e um título. O placar é de cada personagem, não da conta."),
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
          p("Dizem que um ladrão corre pelas estradas roubando uma moeda de quem passa, e que quem o alcança recebe dez de volta. Dizem também que, muito de vez em quando, um craque aparece do nada, dribla você e some. E há quem jure ter visto um velho de barba branca passar voando num trenó, distribuindo presentes."),
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
