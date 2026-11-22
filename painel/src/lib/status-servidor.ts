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

/** Ping de cada jogador a cada retrato (10 s), guardado por 30 minutos. Serve ao teste de conexão do admin. */
type AmostraPing = { t: number; ms: number };
const gp = globalThis as unknown as { __frxPings?: Map<string, AmostraPing[]> };
const pings: Map<string, AmostraPing[]> = (gp.__frxPings ??= new Map());

function guardarPings(s: StatusJogo, agora: number) {
  for (const j of s.jogadores ?? []) {
    const l = pings.get(j.nick) ?? [];
    l.push({ t: agora, ms: Math.max(0, Math.round(j.ping ?? 0)) });
    if (l.length > 180) l.splice(0, l.length - 180);
    pings.set(j.nick, l);
  }
  for (const [nick, l] of pings) if (!l.length || agora - l[l.length - 1].t > 30 * 60_000) pings.delete(nick);
}

export type ConexaoJogador = { nick: string; online: boolean; agora: number | null; media: number; pior: number; melhor: number; amostras: AmostraPing[] };

/** Ping por jogador desde `desde` (ms). Quem saiu há pouco continua na lista, marcado como fora. */
export function conexoes(desde: number): ConexaoJogador[] {
  const online = new Set((statusAtual().dados?.jogadores ?? []).map((j) => j.nick));
  const lista: ConexaoJogador[] = [];
  for (const [nick, todas] of pings) {
    const l = todas.filter((a) => a.t >= desde);
    if (!l.length) continue;
    const v = l.map((a) => a.ms);
    lista.push({
      nick, online: online.has(nick), agora: online.has(nick) ? v[v.length - 1] : null,
      media: Math.round(v.reduce((a, b) => a + b, 0) / v.length), pior: Math.max(...v), melhor: Math.min(...v), amostras: l,
    });
  }
  return lista.sort((a, b) => Number(b.online) - Number(a.online) || b.media - a.media);
}

export function guardarStatus(s: StatusJogo) {
  loja.dados = s;
  loja.recebidoEm = Date.now();
  guardarPings(s, loja.recebidoEm);
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

// ------------------------------------------------------------------ processador da máquina do servidor

let amostraCpu: { ocioso: number; total: number; em: number } | null = null;
let ultimoCpu: number | null = null;

/** Uso do processador da máquina (0 a 100), medido entre uma consulta e a seguinte. null até haver duas amostras. */
export function usoCpu(): number | null {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const os = require("os") as typeof import("os");
  const agora = Date.now();
  if (amostraCpu && agora - amostraCpu.em < 2000) return ultimoCpu;
  let ocioso = 0, total = 0;
  for (const c of os.cpus()) {
    const t = c.times;
    ocioso += t.idle;
    total += t.user + t.nice + t.sys + t.idle + t.irq;
  }
  if (amostraCpu && total > amostraCpu.total) {
    const uso = 1 - (ocioso - amostraCpu.ocioso) / (total - amostraCpu.total);
    ultimoCpu = Math.max(0, Math.min(100, Math.round(uso * 100)));
  }
  amostraCpu = { ocioso, total, em: agora };
  return ultimoCpu;
}
