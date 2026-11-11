import "server-only";

/** Retrato que o mod manda a cada 10 s (acesso/StatusPainel.java). */
export type StatusJogo = {
  mspt: number;
  tps: number;
  memUsadaMb: number;
  memMaxMb: number;
  versaoMod: string;
  mods: number;
  maxJogadores: number;
  dia: number;
  hora: number;
  clima: "limpo" | "chuva" | "tempestade";
  spawn: [number, number];
  jogadores: {
    nick: string; uuid: string; x: number; y: number; z: number; dim: string;
    vida: number; vidaMax: number; ping: number; nivel?: number; classe?: string; reino?: string;
  }[];
  entidades: {
    total: number; monstros: number; animais: number; aldeoes: number; itens: number; outros: number;
    porDimensao: Record<string, number>;
    porTipo: { id: string; n: number }[];
  };
  chunksCarregados: number;
  reinos: { id: string; nome: string; x0: number; z0: number; x1: number; z1: number; subterraneo: boolean }[];
  chefe?: { fase: "aviso" | "ativo"; nome: string; x: number; z: number; surgeEmS?: number; fogeEmS?: number; vida?: number; participantes?: number };
};

type Guardado = { dados: StatusJogo | null; recebidoEm: number; historico: { t: number; tps: number; mspt: number; memMb: number; memMaxMb: number; chunks: number; jogadores: number; entidades: number }[] };

const g = globalThis as unknown as { __frxStatus?: Guardado };
const loja: Guardado = (g.__frxStatus ??= { dados: null, recebidoEm: 0, historico: [] });

export function guardarStatus(s: StatusJogo) {
  loja.dados = s;
  loja.recebidoEm = Date.now();
  loja.historico.push({
    t: loja.recebidoEm, tps: s.tps, mspt: s.mspt, memMb: s.memUsadaMb, memMaxMb: s.memMaxMb, chunks: s.chunksCarregados,
    jogadores: s.jogadores?.length ?? 0, entidades: s.entidades?.total ?? 0,
  });
  if (loja.historico.length > 720) loja.historico.splice(0, loja.historico.length - 720); // ~2 h
}

/** null se o servidor não mandou nada nos últimos 45 s. */
export function statusAtual() {
  const fresco = loja.dados && Date.now() - loja.recebidoEm < 45_000;
  return { dados: fresco ? loja.dados : null, recebidoEm: loja.recebidoEm || null, historico: loja.historico };
}
