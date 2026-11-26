import { prisma } from "@/lib/prisma";

/** Pesos do MVP. Ficam na Configuracao "expedicao_pesos" (dá para mudar sem mexer no código); o que faltar usa o padrão. */
export type Pesos = { chunks: number; chefes: number; estruturas: number; tesouros: number; mortes: number; minutos: number };
export const PESOS_PADRAO: Pesos = { chunks: 0.5, chefes: 40, estruturas: 15, tesouros: 5, mortes: -10, minutos: 0.2 };

export async function pesos(): Promise<Pesos> {
  const linha = await prisma.configuracao.findUnique({ where: { chave: "expedicao_pesos" } }).catch(() => null);
  const v = (linha?.valor ?? {}) as Partial<Record<keyof Pesos, unknown>>;
  const p = { ...PESOS_PADRAO };
  for (const k of Object.keys(p) as (keyof Pesos)[]) if (typeof v[k] === "number" && Number.isFinite(v[k])) p[k] = v[k] as number;
  return p;
}

type Part = { uuid: string; nick: string; tempoSeg: number; chunks: number; mortes: number; chefes: number; estruturas: number; tesouros: number; primeira: Date; ultima: Date };
export type Linha = { nick: string; tempoMin: number; km2: number; chunks: number; mortes: number; chefes: number; estruturas: number; tesouros: number; pontos: number; posicao: number };

/** Área de um número de chunks (16 × 16 blocos = 256 m²) em km². */
export const km2 = (chunks: number) => (chunks * 256) / 1_000_000;

export function pontuar(p: Part, w: Pesos) {
  return Math.max(0, Math.round(p.chunks * w.chunks + p.chefes * w.chefes + p.estruturas * w.estruturas + p.tesouros * w.tesouros + p.mortes * w.mortes + (p.tempoSeg / 60) * w.minutos));
}

/** Ranking da expedição: maior pontuação primeiro (empate: mais área). */
export function ranquear(parts: Part[], w: Pesos): Linha[] {
  return parts
    .map((p) => ({ nick: p.nick, tempoMin: Math.round(p.tempoSeg / 60), km2: km2(p.chunks), chunks: p.chunks, mortes: p.mortes, chefes: p.chefes, estruturas: p.estruturas, tesouros: p.tesouros, pontos: pontuar(p, w), posicao: 0 }))
    .sort((a, b) => b.pontos - a.pontos || b.chunks - a.chunks)
    .map((l, i) => ({ ...l, posicao: i + 1 }));
}

function lider<K extends "chunks" | "chefes" | "estruturas" | "tesouros">(ls: Linha[], k: K) {
  const m = [...ls].sort((a, b) => b[k] - a[k])[0];
  return m && m[k] > 0 ? { nick: m.nick, valor: m[k] } : null;
}

export type ResumoExpedicao = {
  numero: number; tipo: string; nome: string; descricao: string; inicio: number; proxima: number; fim: number | null; tamanho: number; versao: string;
  ativa: boolean; jogadores: number; estruturas: number; chefes: number; tesouros: number; mortes: number; km2: number; agora: string[];
  mvp: string | null;
};

async function resumir(numeros?: number[]) {
  const w = await pesos();
  const lista = await prisma.expedicao.findMany({ where: numeros ? { numero: { in: numeros } } : undefined, orderBy: { numero: "desc" }, include: { participacoes: true } });
  return lista.map((e) => {
    const ranking = ranquear(e.participacoes, w);
    const soma = (k: "chefes" | "tesouros" | "mortes" | "chunks") => e.participacoes.reduce((t, p) => t + p[k], 0);
    const resumo: ResumoExpedicao = {
      numero: e.numero, tipo: e.tipo, nome: e.nome, descricao: e.descricao, inicio: e.inicio.getTime(), proxima: e.proxima.getTime(), fim: e.fim?.getTime() ?? null,
      tamanho: e.tamanho, versao: e.versao, ativa: e.ativa, jogadores: e.participacoes.length, estruturas: e.estruturas,
      chefes: soma("chefes"), tesouros: soma("tesouros"), mortes: soma("mortes"), km2: km2(soma("chunks")),
      agora: e.fim ? [] : (Array.isArray(e.agora) ? (e.agora as unknown[]).filter((x): x is string => typeof x === "string") : []),
      mvp: ranking[0]?.pontos ? ranking[0].nick : null,
    };
    return { resumo, ranking };
  });
}

/** Página /expedicoes: a atual (ainda sem fim), o histórico e os números gerais. `agora` = relógio do servidor. */
export async function paginaExpedicoes() {
  const todas = await resumir();
  const atualTudo = todas.find((x) => x.resumo.fim === null && x.resumo.ativa) ?? null;
  const historico = todas.filter((x) => x !== atualTudo).map((x) => x.resumo);
  const parts = await prisma.participacaoExpedicao.aggregate({ _count: { _all: true }, _sum: { chefes: true, chunks: true, tesouros: true } });
  const geral = {
    expedicoes: todas.length,
    participacoes: parts._count._all,
    estruturas: todas.reduce((t, x) => t + x.resumo.estruturas, 0),
    chefes: parts._sum.chefes ?? 0,
    km2: km2(parts._sum.chunks ?? 0),
    tesouros: parts._sum.tesouros ?? 0,
  };
  return {
    agora: Date.now(),
    atual: atualTudo ? { ...atualTudo.resumo, ranking: atualTudo.ranking.slice(0, 10) } : null,
    historico,
    geral,
  };
}

/** Página /expedicoes/[numero]. */
export async function detalheExpedicao(numero: number) {
  if (!Number.isInteger(numero) || numero <= 0) return null;
  const [x] = await resumir([numero]);
  if (!x) return null;
  const ls = x.ranking;
  return {
    ...x.resumo,
    relogio: Date.now(),
    ranking: ls,
    destaques: { explorador: lider(ls, "chunks"), chefes: lider(ls, "chefes"), estruturas: lider(ls, "estruturas"), tesouros: lider(ls, "tesouros") },
  };
}

export type ExpedicoesDoJogador = {
  participacoes: number; chefes: number; estruturas: number; km2: number; mvps: number;
  ultimas: { numero: number; posicao: number; total: number }[];
};

/** Resumo das expedições de uma conta (perfil do jogador). */
export async function expedicoesDoJogador(uuid: string): Promise<ExpedicoesDoJogador | null> {
  const minhas = await prisma.participacaoExpedicao.findMany({ where: { uuid }, select: { numero: true } });
  if (!minhas.length) return null;
  const numeros = minhas.map((m) => m.numero);
  let chefes = 0, estruturas = 0, chunks = 0, mvps = 0;
  const ultimas: ExpedicoesDoJogador["ultimas"] = [];
  const w = await pesos();
  for (const e of await prisma.expedicao.findMany({ where: { numero: { in: numeros } }, orderBy: { numero: "desc" }, include: { participacoes: true } })) {
    const eu = e.participacoes.find((p) => p.uuid === uuid);
    if (!eu) continue;
    chefes += eu.chefes; estruturas += eu.estruturas; chunks += eu.chunks;
    const r = ranquear(e.participacoes, w);
    const pos = r.findIndex((l) => l.nick === eu.nick) + 1;
    if (pos === 1 && e.fim && r[0].pontos > 0) mvps++;
    if (ultimas.length < 8) ultimas.push({ numero: e.numero, posicao: pos, total: r.length });
  }
  return { participacoes: numeros.length, chefes, estruturas, km2: km2(chunks), mvps, ultimas };
}
