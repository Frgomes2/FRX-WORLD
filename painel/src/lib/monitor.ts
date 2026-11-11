import "server-only";
import fs from "fs";
import path from "path";
import zlib from "zlib";
import { statusAtual } from "@/lib/status-servidor";

/**
 * Monitoramento: lê os logs do servidor (sessão atual e anteriores), relatórios de crash, o log do painel e o da
 * compilação, e devolve erros agrupados, alertas e números para o painel de monitoramento.
 */

export type Nivel = "ERROR" | "FATAL" | "WARN";
export type Entrada = {
  t: number | null;      // epoch ms
  nivel: Nivel;
  thread: string;
  origem: string;        // logger (ex.: net.minecraft.tags.TagLoader) ou "painel"/"compilação"
  msg: string;
  pilha: string[];
  inicio: boolean;       // aconteceu antes do "Done" (carregamento dos mods)
  fonte: "servidor" | "anteriores" | "painel" | "compilação";
};

function pastaServidor() {
  return path.resolve(process.cwd(), process.env.SERVIDOR_DIR ?? "../server");
}

const MESES: Record<string, number> = { jan: 0, fev: 1, feb: 1, mar: 2, abr: 3, apr: 3, mai: 4, may: 4, jun: 5, jul: 6, ago: 7, aug: 7, set: 8, sep: 8, out: 9, oct: 9, nov: 10, dez: 11, dec: 11 };

/** "09nov.2026 20:52:22.974" ou "20:52:22" (com a data do arquivo). */
function quando(txt: string, dataArquivo: Date | null): number | null {
  let m = txt.match(/^(\d{1,2})([a-zç]{3})\.?(\d{4}) (\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,3}))?/i);
  if (m) {
    const mes = MESES[m[2].toLowerCase()];
    if (mes === undefined) return null;
    return new Date(Number(m[3]), mes, Number(m[1]), Number(m[4]), Number(m[5]), Number(m[6]), Number(m[7] ?? 0)).getTime();
  }
  m = txt.match(/^(\d{2}):(\d{2}):(\d{2})/);
  if (m && dataArquivo) {
    const d = new Date(dataArquivo);
    d.setHours(Number(m[1]), Number(m[2]), Number(m[3]), 0);
    return d.getTime();
  }
  return null;
}

const LINHA = /^\[([^\]]+)\] \[([^\]]*?)\/(INFO|WARN|ERROR|FATAL|DEBUG|TRACE)\] \[([^\]]*)\]: ?(.*)$/;

type Lido = { entradas: Entrada[]; infos: { t: number | null; msg: string }[] };
const g = globalThis as unknown as { __frxLogs?: Map<string, { chave: string; lido: Lido }> };
const cache = (g.__frxLogs ??= new Map());

function lerTexto(arq: string) {
  const b = fs.readFileSync(arq);
  return (arq.endsWith(".gz") ? zlib.gunzipSync(b) : b).toString("utf8");
}

/** Lê um log do Forge e separa avisos/erros (com pilha) e algumas linhas INFO úteis. */
function lerLog(arq: string, fonte: Entrada["fonte"], dataArquivo: Date | null): Lido {
  let st: fs.Stats;
  try { st = fs.statSync(arq); } catch { return { entradas: [], infos: [] }; }
  const chave = `${st.size}:${st.mtimeMs}`;
  const c = cache.get(arq);
  if (c && c.chave === chave) return c.lido;

  const entradas: Entrada[] = [];
  const infos: Lido["infos"] = [];
  let atual: Entrada | null = null;
  let inicio = true;
  for (const linha of lerTexto(arq).split(/\r?\n/)) {
    const m = linha.match(LINHA);
    if (!m) {
      if (atual && linha.trim() && atual.pilha.length < 40) atual.pilha.push(linha.replace(/\t/g, "  "));
      continue;
    }
    atual = null;
    const [, data, thread, nivel, logger, msg] = m;
    const t = quando(data, dataArquivo);
    if (nivel === "INFO") {
      if (/Done \([\d.,]+s\)!/.test(msg)) inicio = false;
      if (/Done \(|Stopping server|Starting minecraft server|lost connection|was kicked|joined the game|left the game|Can't keep up/.test(msg)) infos.push({ t, msg });
      continue;
    }
    if (nivel !== "WARN" && nivel !== "ERROR" && nivel !== "FATAL") continue;
    atual = { t, nivel, thread, origem: logger.replace(/\/[A-Z_]*$/, "").replace(/\/$/, ""), msg: msg.trim(), pilha: [], inicio, fonte };
    entradas.push(atual);
  }
  const lido = { entradas, infos };
  cache.set(arq, { chave, lido });
  return lido;
}

/** Log do painel (next dev): linhas com ⨯, Error ou respostas 500. */
function lerPainel(arq: string): Entrada[] {
  let texto = "";
  try {
    const st = fs.statSync(arq);
    const fd = fs.openSync(arq, "r");
    const ler = Math.min(st.size, 400_000);
    const b = Buffer.alloc(ler);
    fs.readSync(fd, b, 0, ler, st.size - ler);
    fs.closeSync(fd);
    texto = b.toString("utf8");
    const saida: Entrada[] = [];
    let atual: Entrada | null = null;
    for (const l of texto.split(/\r?\n/)) {
      const limpo = l.replace(/\u001b\[[0-9;]*m/g, "");
      if (/^\s*⨯|^\s*Error[: ]|TypeError|ReferenceError|PrismaClient\w*Error| 500 in /.test(limpo)) {
        atual = { t: st.mtimeMs, nivel: / 500 in |⨯/.test(limpo) ? "ERROR" : "ERROR", thread: "next", origem: "painel", msg: limpo.trim().slice(0, 400), pilha: [], inicio: false, fonte: "painel" };
        saida.push(atual);
      } else if (atual && /^\s+at /.test(limpo) && atual.pilha.length < 15) atual.pilha.push(limpo);
      else atual = null;
    }
    return saida.slice(-200);
  } catch { return []; }
}

function lerCompilacao(arq: string): { entradas: Entrada[]; ok: boolean | null; quando: number | null } {
  try {
    const st = fs.statSync(arq);
    const txt = fs.readFileSync(arq, "utf8");
    const entradas: Entrada[] = [];
    for (const l of txt.split(/\r?\n/)) {
      if (/error:|FAILED|Exception/.test(l)) entradas.push({ t: st.mtimeMs, nivel: "ERROR", thread: "gradle", origem: "compilação", msg: l.trim().slice(0, 400), pilha: [], inicio: false, fonte: "compilação" });
    }
    return { entradas, ok: /BUILD SUCCESSFUL/.test(txt) ? true : /BUILD FAILED/.test(txt) ? false : null, quando: st.mtimeMs };
  } catch { return { entradas: [], ok: null, quando: null }; }
}

/** Assinatura para agrupar mensagens iguais que só mudam números, nomes de entidades ou coordenadas. */
function assinatura(e: Entrada) {
  const msg = e.msg
    .replace(/Running \d+ms or \d+ ticks behind/, "Running #ms or # ticks behind")
    .replace(/\[[^\]]*'[^']*'[^\]]*\]/g, "[…]")
    .replace(/-?\d+(\.\d+)?/g, "#")
    .slice(0, 180);
  return `${e.nivel}|${e.origem}|${msg}`;
}

export type Grupo = {
  chave: string; nivel: Nivel; origem: string; fonte: Entrada["fonte"]; exemplo: string; pilha: string[];
  total: number; primeira: number | null; ultima: number | null; inicio: boolean; categoria: string;
};

function categoria(e: Entrada): string {
  const m = e.msg;
  if (/Can't keep up/.test(m)) return "Lag";
  if (/moved too quickly|moved wrongly/.test(m)) return "Movimento";
  if (/OutOfMemory|out of memory/i.test(m)) return "Memória";
  if (/Watchdog|Server Watchdog|tick took/i.test(m)) return "Travamento";
  if (/Mixin|mixin|coremod|transform/i.test(m) || e.origem.includes("mixin")) return "Mods (carregamento)";
  if (/tag|recipe|loot|advancement|datapack|registry|Couldn't load|Unknown|Failed to (load|parse)/i.test(m)) return "Dados/receitas";
  if (/Exception|Error|Throwable|Caused by/.test(m) || e.pilha.some((p) => /Exception|Error/.test(p))) return "Exceção";
  if (/UUID of added entity|entity/i.test(m)) return "Entidades";
  if (/chunk|region|structure/i.test(m)) return "Mundo";
  if (e.fonte === "painel") return "Painel";
  if (e.fonte === "compilação") return "Compilação";
  return "Outros";
}

function agrupar(entradas: Entrada[]): Grupo[] {
  const mapa = new Map<string, Grupo>();
  for (const e of entradas) {
    const k = assinatura(e);
    const gr = mapa.get(k);
    if (gr) {
      gr.total++;
      if (e.t && (!gr.ultima || e.t > gr.ultima)) { gr.ultima = e.t; gr.exemplo = e.msg; if (e.pilha.length) gr.pilha = e.pilha; }
      if (e.t && (!gr.primeira || e.t < gr.primeira)) gr.primeira = e.t;
      gr.inicio = gr.inicio && e.inicio;
    } else {
      mapa.set(k, { chave: k, nivel: e.nivel, origem: e.origem, fonte: e.fonte, exemplo: e.msg, pilha: e.pilha, total: 1, primeira: e.t, ultima: e.t, inicio: e.inicio, categoria: categoria(e) });
    }
  }
  return [...mapa.values()].sort((a, b) => (b.ultima ?? 0) - (a.ultima ?? 0));
}

export type Alerta = { nivel: "critico" | "aviso" | "info"; titulo: string; detalhe: string };

export function monitorar() {
  const pasta = pastaServidor();
  const raiz = path.resolve(pasta, "..");
  const agora = Date.now();

  // sessão atual
  const atual = lerLog(path.join(pasta, "logs", "latest.log"), "servidor", null);

  // sessões anteriores (últimos 7 dias)
  const anteriores: Entrada[] = [];
  const porDia = new Map<string, { erros: number; avisos: number; lag: number }>();
  const dirLogs = path.join(pasta, "logs");
  if (fs.existsSync(dirLogs)) {
    for (const n of fs.readdirSync(dirLogs)) {
      const m = n.match(/^(\d{4})-(\d{2})-(\d{2})-\d+\.log\.gz$/);
      if (!m) continue;
      const data = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
      if (agora - data.getTime() > 7 * 86400_000) continue;
      const l = lerLog(path.join(dirLogs, n), "anteriores", data);
      anteriores.push(...l.entradas);
    }
  }
  const todosServidor = [...anteriores, ...atual.entradas];
  for (const e of todosServidor) {
    if (!e.t) continue;
    const d = new Date(e.t);
    const k = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    const x = porDia.get(k) ?? { erros: 0, avisos: 0, lag: 0 };
    if (e.nivel === "WARN") x.avisos++; else x.erros++;
    if (/Can't keep up/.test(e.msg)) x.lag++;
    porDia.set(k, x);
  }

  // por hora (últimas 24 h)
  const porHora: { hora: number; erros: number; avisos: number; lag: number }[] = [];
  const h0 = Math.floor(agora / 3600_000) * 3600_000 - 23 * 3600_000;
  for (let i = 0; i < 24; i++) porHora.push({ hora: h0 + i * 3600_000, erros: 0, avisos: 0, lag: 0 });
  for (const e of todosServidor) {
    if (!e.t || e.t < h0) continue;
    const i = Math.floor((e.t - h0) / 3600_000);
    if (i < 0 || i >= 24) continue;
    if (/Can't keep up/.test(e.msg)) porHora[i].lag++;
    else if (e.nivel === "WARN") porHora[i].avisos++;
    else porHora[i].erros++;
  }

  // lag da sessão atual
  const lags = atual.entradas
    .filter((e) => /Can't keep up/.test(e.msg))
    .map((e) => ({ t: e.t, ms: Number(e.msg.match(/Running (\d+)ms/)?.[1] ?? 0), ticks: Number(e.msg.match(/or (\d+) ticks/)?.[1] ?? 0) }));

  // painel e compilação
  const painel = lerPainel(path.join(raiz, "logs", "painel.log"));
  const comp = lerCompilacao(path.join(raiz, "logs", "compilacao.log"));

  // crash reports
  const crashes: { arquivo: string; quando: number; descricao: string; erro: string }[] = [];
  const dirCrash = path.join(pasta, "crash-reports");
  if (fs.existsSync(dirCrash)) {
    for (const n of fs.readdirSync(dirCrash).filter((x) => x.endsWith(".txt"))) {
      try {
        const arq = path.join(dirCrash, n);
        const st = fs.statSync(arq);
        const fd = fs.openSync(arq, "r");
        const b = Buffer.alloc(Math.min(st.size, 6000));
        fs.readSync(fd, b, 0, b.length, 0);
        fs.closeSync(fd);
        const t = b.toString("utf8");
        const dm = n.match(/crash-(\d{4})-(\d{2})-(\d{2})_(\d{2})\.(\d{2})\.(\d{2})/);
        const quandoCrash = dm ? new Date(+dm[1], +dm[2] - 1, +dm[3], +dm[4], +dm[5], +dm[6]).getTime() : st.mtimeMs;
        crashes.push({
          arquivo: n, quando: quandoCrash,
          descricao: t.match(/^Description: (.*)$/m)?.[1]?.trim() ?? "",
          erro: t.match(/^(?:[\w.$]+(?:Exception|Error)[^\n]*)$/m)?.[0]?.trim().slice(0, 300) ?? "",
        });
      } catch { /* */ }
    }
    crashes.sort((a, b) => b.quando - a.quando);
  }

  // sessões (início/fim) e desconexões da sessão atual
  const desconexoes = atual.infos.filter((i) => /lost connection|was kicked/.test(i.msg)).map((i) => ({ t: i.t, msg: i.msg }));
  const entradasJogo = atual.infos.filter((i) => /joined the game/.test(i.msg)).length;
  const done = atual.infos.find((i) => /Done \(/.test(i.msg));

  // disco
  let disco: { livreGb: number; totalGb: number } | null = null;
  try {
    const s = fs.statfsSync(pasta);
    disco = { livreGb: Math.round((s.bavail * s.bsize) / 1e8) / 10, totalGb: Math.round((s.blocks * s.bsize) / 1e8) / 10 };
  } catch { /* Node antigo */ }
  const tamanhoPasta = (dir: string) => {
    let total = 0;
    try { for (const n of fs.readdirSync(dir)) { try { const st = fs.statSync(path.join(dir, n)); total += st.isFile() ? st.size : 0; } catch { /* */ } } } catch { /* */ }
    return total;
  };
  const logsMb = Math.round(tamanhoPasta(dirLogs) / 1048576);

  // métricas ao vivo
  const st = statusAtual();
  const hist = st.historico;
  const ultimos = hist.slice(-30); // ~5 min
  const media = (xs: number[]) => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : 0);

  // alertas
  const alertas: Alerta[] = [];
  const s = st.dados;
  if (!s) alertas.push({ nivel: "aviso", titulo: "Sem dados ao vivo", detalhe: "O servidor não mandou status nos últimos 45 s (desligado, travado ou mod antigo)." });
  if (s && s.tps < 15) alertas.push({ nivel: s.tps < 10 ? "critico" : "aviso", titulo: `TPS baixo: ${s.tps}`, detalhe: `Cada tick está levando ${s.mspt} ms (o ideal é até 50 ms). O jogo fica lento para todo mundo.` });
  if (s && s.memUsadaMb / s.memMaxMb > 0.85) alertas.push({ nivel: s.memUsadaMb / s.memMaxMb > 0.95 ? "critico" : "aviso", titulo: `Memória em ${Math.round((s.memUsadaMb / s.memMaxMb) * 100)}%`, detalhe: `${(s.memUsadaMb / 1024).toFixed(1)} de ${(s.memMaxMb / 1024).toFixed(1)} GB. Perto do limite o servidor trava ou cai.` });
  if (s && s.entidades.total > 6000) alertas.push({ nivel: "aviso", titulo: `Muitas entidades: ${s.entidades.total}`, detalhe: `${s.entidades.itens} itens no chão e ${s.entidades.monstros} monstros. Isso pesa no TPS.` });
  if (s && s.entidades.itens > 1500) alertas.push({ nivel: "aviso", titulo: `${s.entidades.itens} itens no chão`, detalhe: "Muitos itens largados deixam o servidor lento. Um /kill @e[type=item] limpa." });
  const lag10 = lags.filter((l) => l.t && agora - l.t < 10 * 60_000);
  if (lag10.length >= 3) alertas.push({ nivel: "aviso", titulo: `${lag10.length} travadas nos últimos 10 min`, detalhe: `O servidor ficou até ${Math.max(...lag10.map((l) => l.ms))} ms atrasado. Geralmente é geração de terreno (use o Chunky).` });
  const crash24 = crashes.filter((c) => agora - c.quando < 86400_000);
  if (crash24.length) alertas.push({ nivel: "critico", titulo: `${crash24.length} crash nas últimas 24 h`, detalhe: crash24[0].descricao || crash24[0].arquivo });
  if (comp.ok === false) alertas.push({ nivel: "critico", titulo: "A última compilação do core mod falhou", detalhe: comp.entradas[0]?.msg ?? "Veja logs\\compilacao.log" });
  const errosRecentes = atual.entradas.filter((e) => e.nivel !== "WARN" && !e.inicio && e.t && agora - e.t < 15 * 60_000);
  if (errosRecentes.length >= 5) alertas.push({ nivel: "aviso", titulo: `${errosRecentes.length} erros nos últimos 15 min`, detalhe: errosRecentes[errosRecentes.length - 1].msg.slice(0, 160) });
  const painelRecente = painel.filter((e) => / 500 in /.test(e.msg)).length;
  if (painelRecente >= 3) alertas.push({ nivel: "aviso", titulo: `Painel com ${painelRecente} respostas de erro (500)`, detalhe: "Veja a aba de erros com a fonte Painel." });
  if (disco && disco.livreGb < 10) alertas.push({ nivel: disco.livreGb < 3 ? "critico" : "aviso", titulo: `Pouco espaço em disco: ${disco.livreGb} GB livres`, detalhe: "Mundo, backups e logs precisam de espaço." });
  if (!alertas.length) alertas.push({ nivel: "info", titulo: "Tudo tranquilo", detalhe: "Nenhum problema detectado agora." });

  const contar = (xs: Entrada[], f: (e: Entrada) => boolean) => xs.filter(f).length;

  return {
    agora,
    resumo: {
      errosSessao: contar(atual.entradas, (e) => e.nivel !== "WARN"),
      errosSessaoJogo: contar(atual.entradas, (e) => e.nivel !== "WARN" && !e.inicio),
      avisosSessao: contar(atual.entradas, (e) => e.nivel === "WARN"),
      fatais: contar(atual.entradas, (e) => e.nivel === "FATAL"),
      errosInicio: contar(atual.entradas, (e) => e.inicio),
      lagsSessao: lags.length,
      lagMsTotal: lags.reduce((a, l) => a + l.ms, 0),
      lagMsMaximo: lags.reduce((a, l) => Math.max(a, l.ms), 0),
      erros7d: contar(todosServidor, (e) => e.nivel !== "WARN"),
      crashes7d: crashes.filter((c) => agora - c.quando < 7 * 86400_000).length,
      desconexoes: desconexoes.length,
      entradasJogo,
      iniciadoEm: done?.t ?? null,
      tempoInicio: Number(done?.msg.match(/Done \(([\d.,]+)s/)?.[1]?.replace(",", ".") ?? 0) || null,
      painelErros: painel.length,
      compilacao: { ok: comp.ok, quando: comp.quando, erros: comp.entradas.length },
      disco,
      logsMb,
      tpsMedio5min: Math.round(media(ultimos.map((h) => h.tps)) * 10) / 10,
      msptPico5min: Math.max(0, ...ultimos.map((h) => h.mspt ?? 0)),
    },
    alertas,
    grupos: agrupar([...todosServidor, ...painel, ...comp.entradas]).slice(0, 400),
    porHora,
    porDia: [...porDia.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([dia, v]) => ({ dia, ...v })),
    lags: lags.slice(-60),
    desconexoes: desconexoes.slice(-30),
    crashes: crashes.slice(0, 20),
    historico: hist,
  };
}
