import "server-only";
import { prisma } from "@/lib/prisma";

/**
 * Crônicas do mundo: o painel guarda os acontecimentos em vez de jogar fora.
 * - Progresso (rota /api/servidor/progresso): compara com o retrato anterior do MESMO personagem e registra nível, rank e conquistas.
 * - Marcos com chave única (conferidos junto com os dados do site): recordes da Torre de 5 em 5 andares, criaturas descobertas, primeiros do Mural.
 * Na primeira conferência, o que já era verdade entra como "semente": fica guardado só para não repetir e não aparece no site.
 * Custo: só grava quando algo muda; as leituras ficam em memória por alguns segundos. Guarda 90 dias de acontecimentos comuns.
 */
export type CronicaPublica = { tipo: string; icone: string; texto: string; nome: string; conta: string; vaga: number; destaque: boolean; quando: number };
type Linha = { chave: string | null; tipo: string; icone: string; texto: string; nome: string; conta: string; vaga: number; destaque: boolean; semente: boolean; quando: Date };
type Tabela = {
  findMany: (a?: Record<string, unknown>) => Promise<Linha[]>;
  create: (a: { data: Record<string, unknown> }) => Promise<unknown>;
  deleteMany: (a: { where: Record<string, unknown> }) => Promise<unknown>;
};
type Foto = { dia: string; conta: string; vaga: number; nivel: number; posicao: number };
type TabelaFoto = { findMany: (a?: Record<string, unknown>) => Promise<Foto[]>; createMany: (a: { data: Foto[]; skipDuplicates: boolean }) => Promise<unknown>; deleteMany: (a: { where: Record<string, unknown> }) => Promise<unknown> };
const db = prisma as unknown as { cronica: Tabela; fotoRanking: TabelaFoto; personagem: { findFirst: (a: Record<string, unknown>) => Promise<{ vaga: number; nome: string; base: unknown } | null> } };

const g = globalThis as unknown as {
  __frxChaves?: Set<string>; __frxCronicas?: { em: number; lista: CronicaPublica[] }; __frxFotos?: { em: number; lista: Foto[] }; __frxFotoDia?: string; __frxLimpeza?: number;
};
const RANKS = ["F", "E", "D", "C", "B", "A", "S"];
const publica = (l: Linha): CronicaPublica => ({ tipo: l.tipo, icone: l.icone, texto: l.texto, nome: l.nome, conta: l.conta, vaga: l.vaga, destaque: l.destaque, quando: new Date(l.quando).getTime() });
/** Dia no horário de Brasília (UTC−3), com deslocamento em dias. */
export const diaBr = (menos = 0) => new Date(Date.now() - 3 * 3_600_000 - menos * 86_400_000).toISOString().slice(0, 10);

async function gravar(d: { chave?: string; tipo: string; icone: string; texto: string; nome?: string; conta?: string; vaga?: number; destaque?: boolean; semente?: boolean }) {
  try { await db.cronica.create({ data: d }); g.__frxCronicas = undefined; } catch { /* chave repetida ou tabela ainda não criada */ }
}

// ------------------------------------------------------------------ progresso de um personagem

export type Antes = { nome: string; conta: string; vaga: number; nivel: number; rank: string; conquistas: number } | null;

/** O retrato do personagem ANTES de o progresso novo ser gravado (para comparar o mesmo personagem, não a conta). */
export async function retratoAntes(uuid: string, vaga: number | undefined, conta: string): Promise<Antes> {
  try {
    const p = await db.personagem.findFirst({ where: vaga ? { uuid, vaga } : { uuid, emUso: true } });
    const b = (p?.base && typeof p.base === "object" ? p.base : null) as { nivel?: number; rank?: string; conquistas?: number } | null;
    if (!p || !b || typeof b.nivel !== "number") return null;
    return { nome: p.nome || conta, conta, vaga: p.vaga, nivel: b.nivel, rank: b.rank ?? "F", conquistas: b.conquistas ?? 0 };
  } catch { return null; }
}

export async function registrarProgresso(antes: Antes, novo: { nivel: number; rank: string; conquistas?: number }) {
  if (!antes) return;
  const quem = { nome: antes.nome, conta: antes.conta, vaga: antes.vaga };
  if (novo.nivel > antes.nivel) await gravar({ ...quem, tipo: "nivel", icone: "⚔️", texto: `subiu para o nível ${novo.nivel}`, destaque: novo.nivel % 25 === 0 });
  if (RANKS.indexOf(novo.rank) > RANKS.indexOf(antes.rank)) await gravar({ ...quem, tipo: "rank", icone: "🛡️", texto: `alcançou o Rank ${novo.rank} na Guilda`, destaque: true });
  if (novo.conquistas !== undefined && novo.conquistas > antes.conquistas) {
    const n = novo.conquistas - antes.conquistas;
    await gravar({ ...quem, tipo: "conquista", icone: "🏆", texto: n === 1 ? `desbloqueou uma conquista (${novo.conquistas} no total)` : `desbloqueou ${n} conquistas (${novo.conquistas} no total)` });
  }
}

// ------------------------------------------------------------------ marcos com chave única

export type Marco = { chave: string; tipo: string; icone: string; texto: string; nome?: string; conta?: string; vaga?: number; destaque?: boolean };

/** Registra os marcos que ainda não existem. Chamado junto com os dados do site (a cada 10 s, no máximo). */
export async function registrarMarcos(marcos: Marco[]) {
  try {
    if (!g.__frxChaves) {
      const linhas = await db.cronica.findMany({ where: { chave: { not: null } }, select: { chave: true } });
      g.__frxChaves = new Set(linhas.map((l) => l.chave as string));
    }
  } catch { return; }
  const tem = g.__frxChaves;
  const primeiraVez = !tem.has("_inicio");
  for (const m of marcos) {
    if (tem.has(m.chave)) continue;
    tem.add(m.chave);
    await gravar({ ...m, semente: primeiraVez });
  }
  if (primeiraVez) { tem.add("_inicio"); await gravar({ chave: "_inicio", tipo: "inicio", icone: "", texto: "", semente: true }); }
  // limpeza uma vez por dia: acontecimentos comuns com mais de 90 dias saem (manchetes e marcos ficam)
  if ((g.__frxLimpeza ?? 0) < Date.now() - 86_400_000) {
    g.__frxLimpeza = Date.now();
    await db.cronica.deleteMany({ where: { chave: null, destaque: false, quando: { lt: new Date(Date.now() - 90 * 86_400_000) } } }).catch(() => null);
    await db.fotoRanking.deleteMany({ where: { dia: { lt: diaBr(30) } } }).catch(() => null);
  }
}

// ------------------------------------------------------------------ leitura

/** Últimos acontecimentos (sem as sementes). Fica 10 s em memória. */
export async function ultimasCronicas(): Promise<CronicaPublica[]> {
  if (g.__frxCronicas && Date.now() - g.__frxCronicas.em < 10_000) return g.__frxCronicas.lista;
  let lista: CronicaPublica[] = [];
  try { lista = (await db.cronica.findMany({ where: { semente: false }, orderBy: { quando: "desc" }, take: 60 })).map(publica); } catch { /* tabela ainda não criada */ }
  g.__frxCronicas = { em: Date.now(), lista };
  return lista;
}

export async function historicoDe(conta: string, vaga: number): Promise<CronicaPublica[]> {
  try {
    return (await db.cronica.findMany({ where: { semente: false, conta, ...(vaga ? { vaga } : {}) }, orderBy: { quando: "desc" }, take: 20 })).map(publica);
  } catch { return []; }
}

// ------------------------------------------------------------------ foto diária do ranking

/** Guarda a foto de hoje (uma vez por dia) e devolve, por personagem, quanto subiu de nível em 7 dias e quantas posições mudou desde ontem. */
export async function evolucao(ranking: { conta: string; vaga: number; nivel: number }[]): Promise<Map<string, { semana: number; mudou: number }>> {
  const saida = new Map<string, { semana: number; mudou: number }>();
  try {
    const hoje = diaBr();
    if (g.__frxFotoDia !== hoje) {
      await db.fotoRanking.createMany({ data: ranking.map((r, i) => ({ dia: hoje, conta: r.conta, vaga: r.vaga, nivel: r.nivel, posicao: i + 1 })), skipDuplicates: true });
      g.__frxFotoDia = hoje; g.__frxFotos = undefined;
    }
    if (!g.__frxFotos || Date.now() - g.__frxFotos.em > 600_000) g.__frxFotos = { em: Date.now(), lista: await db.fotoRanking.findMany({ where: { dia: { gte: diaBr(7) } } }) };
    const ontem = diaBr(1);
    ranking.forEach((r, i) => {
      const dele = g.__frxFotos!.lista.filter((f) => f.conta === r.conta && f.vaga === r.vaga).sort((a, b) => a.dia.localeCompare(b.dia));
      const anterior = dele.find((f) => f.dia === ontem);
      saida.set(`${r.conta}#${r.vaga}`, { semana: dele.length ? Math.max(0, r.nivel - dele[0].nivel) : 0, mudou: anterior ? anterior.posicao - (i + 1) : 0 });
    });
  } catch { /* tabela ainda não criada */ }
  return saida;
}
