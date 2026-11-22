import "server-only";
import { prisma } from "@/lib/prisma";

/**
 * Mural dos Primeiros: o primeiro personagem do servidor a alcançar cada marco (rank da Guilda, nível, andar da Torre, coração do Labirinto do Dragão).
 * Quem confere é o painel, com os números que o servidor já manda; cada marco é gravado uma vez só (tabela Feito).
 * Na primeira conferência (mural recém-criado), um marco que só um personagem tem vai para ele, sem data;
 * um marco que vários já tinham fica registrado como "antes do mural", sem nome, porque não dá para saber quem foi primeiro.
 */
export type MarcoMural = { chave: string; titulo: string; icone: string; grupo: "Guilda" | "Nível" | "Torre" | "Labirinto" | "Enigma" };
export type FeitoPublico = MarcoMural & { feito: boolean; nome: string; conta: string; vaga: number; quando: number | null };
export type Candidato = { nome: string; conta: string; vaga: number; nivel: number; rank: string; conquistas: number };

const RANKS = ["F", "E", "D", "C", "B", "A", "S"];
export const MARCOS: MarcoMural[] = [
  ...["E", "D", "C", "B", "A", "S"].map((r) => ({ chave: `rank:${r}`, titulo: `Primeiro Rank ${r}`, icone: r === "S" ? "👑" : "🛡️", grupo: "Guilda" as const })),
  ...[10, 25, 50, 75, 100].map((n) => ({ chave: `nivel:${n}`, titulo: `Primeiro nível ${n}`, icone: n === 100 ? "🌟" : "⚔️", grupo: "Nível" as const })),
  ...[10, 25, 50, 100].map((n) => ({ chave: `torre:${n}`, titulo: n === 100 ? "Primeiro a zerar a Torre" : `Primeiro no andar ${n} da Torre`, icone: "🗼", grupo: "Torre" as const })),
  { chave: "chegada:labirinto", titulo: "Primeiro a chegar ao coração do Labirinto do Dragão", icone: "🐉", grupo: "Labirinto" },
  { chave: "chegada:enigma", titulo: "Desvendou o Primeiro Enigma", icone: "🗝️", grupo: "Enigma" },
  { chave: "chegada:enigma_elmo", titulo: "Desvendou o Enigma do Elmo", icone: "🪖", grupo: "Enigma" },
  { chave: "chegada:enigma_peitoral", titulo: "Desvendou o Enigma do Peitoral", icone: "🛡️", grupo: "Enigma" },
  { chave: "chegada:enigma_perneiras", titulo: "Desvendou o Enigma das Perneiras", icone: "👖", grupo: "Enigma" },
  { chave: "chegada:enigma_botas", titulo: "Desvendou o Enigma das Botas", icone: "🥾", grupo: "Enigma" },
];

type Linha = { chave: string; nome: string; conta: string; vaga: number; antigo: boolean; quando: Date };
type Tabela = { findMany: () => Promise<Linha[]>; create: (a: { data: Partial<Linha> & { chave: string } }) => Promise<unknown> };
const tabela = () => (prisma as unknown as { feito: Tabela }).feito;

/** Confere os marcos que ainda não têm dono e devolve o mural inteiro. torre = placar da Torre (nome → andar recorde). */
/** chegadas = área → nome de quem chegou primeiro ao refúgio dela (o servidor registra em frx_chegadas.json). */
export async function conferirMural(personagens: Candidato[], torre: { nome: string; recorde: number }[], chegadas: Record<string, string> = {}): Promise<FeitoPublico[]> {
  let linhas: Linha[] = [];
  try { linhas = await tabela().findMany(); } catch { return MARCOS.map((m) => ({ ...m, feito: false, nome: "", conta: "", vaga: 0, quando: null })); }
  const tem = new Map(linhas.map((l) => [l.chave, l]));
  const primeiraVez = !tem.has("_inicio");
  const gravar = async (dados: Partial<Linha> & { chave: string }) => {
    try { await tabela().create({ data: dados }); tem.set(dados.chave, { nome: "", conta: "", vaga: 0, antigo: false, quando: new Date(), ...dados }); } catch { /* outro pedido gravou primeiro */ }
  };
  for (const m of MARCOS) {
    if (tem.has(m.chave)) continue;
    const [tipo, alvo] = m.chave.split(":");
    let quem: { nome: string; conta: string; vaga: number }[];
    if (tipo === "rank") quem = personagens.filter((p) => RANKS.indexOf(p.rank) >= RANKS.indexOf(alvo));
    else if (tipo === "nivel") quem = personagens.filter((p) => p.nivel >= Number(alvo));
    else if (tipo === "chegada") {
      const nome = chegadas[alvo] ?? "";
      const p = personagens.find((x) => x.nome.toLowerCase() === nome.toLowerCase());
      quem = nome ? [{ nome, conta: p?.conta ?? "", vaga: p?.vaga ?? 0 }] : [];
      if (quem.length) { await gravar({ chave: m.chave, nome: quem[0].nome, conta: quem[0].conta, vaga: quem[0].vaga, antigo: false }); continue; }
    }
    else quem = torre.filter((t) => t.recorde >= Number(alvo)).map((t) => {
      const p = personagens.find((x) => x.nome.toLowerCase() === t.nome.toLowerCase());
      return { nome: t.nome, conta: p?.conta ?? "", vaga: p?.vaga ?? 0 };
    });
    if (!quem.length) continue;
    if (primeiraVez && quem.length > 1) await gravar({ chave: m.chave, antigo: true });
    else await gravar({ chave: m.chave, nome: quem[0].nome, conta: quem[0].conta, vaga: quem[0].vaga, antigo: primeiraVez });
  }
  if (primeiraVez) await gravar({ chave: "_inicio" });
  return MARCOS.map((m) => {
    const l = tem.get(m.chave);
    return { ...m, feito: !!l, nome: l?.nome ?? "", conta: l?.conta ?? "", vaga: l?.vaga ?? 0, quando: l && !l.antigo ? new Date(l.quando).getTime() : null };
  });
}
