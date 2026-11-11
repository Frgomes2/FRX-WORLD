"use client";

import { useEffect, useMemo, useState } from "react";

type Grupo = {
  chave: string; nivel: "ERROR" | "FATAL" | "WARN"; origem: string; fonte: "servidor" | "anteriores" | "painel" | "compilação";
  exemplo: string; pilha: string[]; total: number; primeira: number | null; ultima: number | null; inicio: boolean; categoria: string;
};
type Alerta = { nivel: "critico" | "aviso" | "info"; titulo: string; detalhe: string };
type Dados = {
  agora: number;
  resumo: {
    errosSessao: number; errosSessaoJogo: number; avisosSessao: number; fatais: number; errosInicio: number;
    lagsSessao: number; lagMsTotal: number; lagMsMaximo: number; erros7d: number; crashes7d: number;
    desconexoes: number; entradasJogo: number; iniciadoEm: number | null; tempoInicio: number | null; painelErros: number;
    compilacao: { ok: boolean | null; quando: number | null; erros: number };
    disco: { livreGb: number; totalGb: number } | null; logsMb: number; tpsMedio5min: number; msptPico5min: number;
  };
  alertas: Alerta[];
  grupos: Grupo[];
  porHora: { hora: number; erros: number; avisos: number; lag: number }[];
  porDia: { dia: string; erros: number; avisos: number; lag: number }[];
  lags: { t: number | null; ms: number; ticks: number }[];
  desconexoes: { t: number | null; msg: string }[];
  crashes: { arquivo: string; quando: number; descricao: string; erro: string }[];
  historico: { t: number; tps: number; mspt?: number; memMb?: number; memMaxMb?: number; chunks?: number; jogadores: number; entidades: number }[];
};

const fmt = (n: number) => n.toLocaleString("pt-BR");
const hora = (t: number | null) => (t ? new Date(t).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" }) : "—");
const dataHora = (t: number | null) => (t ? new Date(t).toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" }) : "—");
function ha(t: number | null, agora: number) {
  if (!t) return "—";
  const s = Math.max(0, Math.floor((agora - t) / 1000));
  if (s < 60) return `há ${s}s`;
  if (s < 3600) return `há ${Math.floor(s / 60)} min`;
  if (s < 86400) return `há ${Math.floor(s / 3600)} h`;
  return `há ${Math.floor(s / 86400)} d`;
}

const COR_NIVEL = { ERROR: "text-red-400 border-red-500/40 bg-red-950/30", FATAL: "text-fuchsia-300 border-fuchsia-500/50 bg-fuchsia-950/40", WARN: "text-yellow-300 border-yellow-500/40 bg-yellow-950/20" };
const COR_ALERTA = { critico: "border-red-500/60 bg-red-950/40 text-red-200", aviso: "border-yellow-500/50 bg-yellow-950/30 text-yellow-100", info: "border-green-500/40 bg-green-950/30 text-green-200" };
const ICONE_ALERTA = { critico: "🚨", aviso: "⚠️", info: "✅" };

function Painel({ titulo, icone, extra, children, className = "" }: { titulo: string; icone: string; extra?: React.ReactNode; children: React.ReactNode; className?: string }) {
  return (
    <section className={`rounded-xl border border-pedra-700 bg-pedra-900/80 p-4 ${className}`}>
      <div className="mb-3 flex items-center justify-between gap-2">
        <h2 className="font-titulo text-lg text-ouro"><span className="mr-1.5">{icone}</span>{titulo}</h2>
        {extra}
      </div>
      {children}
    </section>
  );
}

function Numero({ icone, titulo, valor, detalhe, cor = "text-ouro" }: { icone: string; titulo: string; valor: React.ReactNode; detalhe?: React.ReactNode; cor?: string }) {
  return (
    <div className="relative overflow-hidden rounded-xl border border-pedra-700 bg-gradient-to-br from-pedra-900 to-pedra-950 p-4">
      <span className="pointer-events-none absolute -right-1 -top-2 select-none text-5xl opacity-10">{icone}</span>
      <p className="text-xs uppercase tracking-wider text-pedra-500">{titulo}</p>
      <p className={`mt-1 font-titulo text-3xl ${cor}`}>{valor}</p>
      {detalhe && <p className="mt-1 text-xs text-pedra-500">{detalhe}</p>}
    </div>
  );
}

/** Barras empilhadas (erros, avisos, travadas). */
function Barras({ dados, rotulo }: { dados: { k: string; erros: number; avisos: number; lag: number }[]; rotulo: (k: string) => string }) {
  const max = Math.max(1, ...dados.map((d) => d.erros + d.avisos + d.lag));
  return (
    <div>
      <div className="flex h-36 items-end gap-1">
        {dados.map((d) => {
          const tot = d.erros + d.avisos + d.lag;
          return (
            <div key={d.k} className="group relative flex h-full flex-1 flex-col justify-end">
              <div className="flex flex-col-reverse overflow-hidden rounded-t" style={{ height: `${(tot / max) * 100}%` }}>
                <div className="bg-red-500/80" style={{ flex: d.erros }} />
                <div className="bg-yellow-400/70" style={{ flex: d.avisos }} />
                <div className="bg-sky-400/70" style={{ flex: d.lag }} />
              </div>
              <span className="pointer-events-none absolute -top-14 left-1/2 z-10 hidden -translate-x-1/2 whitespace-nowrap rounded bg-black/90 px-2 py-1 text-[11px] group-hover:block">
                {rotulo(d.k)}<br />🔴 {d.erros} · 🟡 {d.avisos} · 🔵 {d.lag}
              </span>
            </div>
          );
        })}
      </div>
      <div className="mt-1 flex justify-between text-[10px] text-pedra-500">
        <span>{dados[0] && rotulo(dados[0].k)}</span>
        <span>{dados.length > 0 && rotulo(dados[dados.length - 1].k)}</span>
      </div>
      <div className="mt-2 flex gap-3 text-xs text-pedra-500">
        <span><span className="mr-1 inline-block h-2 w-2 rounded-sm bg-red-500" />erros</span>
        <span><span className="mr-1 inline-block h-2 w-2 rounded-sm bg-yellow-400" />avisos</span>
        <span><span className="mr-1 inline-block h-2 w-2 rounded-sm bg-sky-400" />travadas (lag)</span>
      </div>
    </div>
  );
}

/** Linha do tempo de uma métrica. */
function Linha({ pontos, cor, max, unidade, limite }: { pontos: { t: number; v: number }[]; cor: string; max?: number; unidade: string; limite?: number }) {
  if (pontos.length < 2) return <p className="py-8 text-center text-sm text-pedra-500">Aparece com o servidor ligado (um ponto a cada 10 s).</p>;
  const m = max ?? Math.max(1, ...pontos.map((p) => p.v)) * 1.1;
  const t0 = pontos[0].t, t1 = pontos[pontos.length - 1].t;
  const x = (t: number) => ((t - t0) / Math.max(1, t1 - t0)) * 100;
  const y = (v: number) => 58 - (Math.min(v, m) / m) * 56;
  const d = pontos.map((p) => `${x(p.t).toFixed(2)},${y(p.v).toFixed(2)}`).join(" ");
  const ultimo = pontos[pontos.length - 1].v;
  return (
    <div>
      <svg viewBox="0 0 100 60" preserveAspectRatio="none" className="h-28 w-full">
        {limite !== undefined && <line x1="0" x2="100" y1={y(limite)} y2={y(limite)} stroke="#e05a4a" strokeDasharray="1.5 1.5" strokeWidth="0.4" vectorEffect="non-scaling-stroke" />}
        <polyline points={`0,60 ${d} 100,60`} fill={cor} fillOpacity="0.15" />
        <polyline points={d} fill="none" stroke={cor} strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
      </svg>
      <div className="flex justify-between text-[10px] text-pedra-500">
        <span>{hora(t0)}</span>
        <span className="text-pergaminho">agora: {Math.round(ultimo * 10) / 10} {unidade}</span>
        <span>{hora(t1)}</span>
      </div>
    </div>
  );
}

export function Monitoramento() {
  const [d, setD] = useState<Dados | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [nivel, setNivel] = useState<"todos" | "erros" | "avisos">("erros");
  const [fonte, setFonte] = useState<"todas" | Grupo["fonte"]>("servidor");
  const [semInicio, setSemInicio] = useState(true);
  const [busca, setBusca] = useState("");
  const [aberto, setAberto] = useState<string | null>(null);
  const [cat, setCat] = useState("todas");

  useEffect(() => {
    let vivo = true;
    async function buscar() {
      try {
        const r = await fetch("/api/painel/monitor", { cache: "no-store" });
        const j = await r.json();
        if (!vivo) return;
        if (!r.ok) throw new Error(j.erro ?? "falhou");
        setD(j); setErro(null);
      } catch (e) { if (vivo) setErro(e instanceof Error ? e.message : "falhou"); }
    }
    buscar();
    const t = setInterval(buscar, 15_000);
    return () => { vivo = false; clearInterval(t); };
  }, []);

  const categorias = useMemo(() => {
    const m = new Map<string, number>();
    for (const g of d?.grupos ?? []) m.set(g.categoria, (m.get(g.categoria) ?? 0) + g.total);
    return [...m.entries()].sort((a, b) => b[1] - a[1]);
  }, [d]);

  const grupos = useMemo(() => {
    const q = busca.trim().toLowerCase();
    return (d?.grupos ?? []).filter((g) =>
      (nivel === "todos" || (nivel === "erros" ? g.nivel !== "WARN" : g.nivel === "WARN")) &&
      (fonte === "todas" || g.fonte === fonte) &&
      (!semInicio || !g.inicio) &&
      (cat === "todas" || g.categoria === cat) &&
      (!q || g.exemplo.toLowerCase().includes(q) || g.origem.toLowerCase().includes(q) || g.pilha.some((p) => p.toLowerCase().includes(q))));
  }, [d, nivel, fonte, semInicio, busca, cat]);

  if (!d) return <p className="text-sm text-pedra-500">{erro ? `Não foi possível ler os logs: ${erro}` : "Lendo os logs…"}</p>;
  const r = d.resumo;
  const hist = d.historico;
  const botao = (ativo: boolean) => `rounded-md border px-2.5 py-1 text-xs ${ativo ? "border-ouro bg-ouro/15 text-ouro" : "border-pedra-700 text-pedra-500 hover:text-pergaminho"}`;

  return (
    <div className="space-y-5">
      {/* alertas */}
      <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
        {d.alertas.map((a, i) => (
          <div key={i} className={`rounded-xl border p-3 ${COR_ALERTA[a.nivel]} ${a.nivel === "critico" ? "animate-pulse" : ""}`}>
            <p className="font-semibold">{ICONE_ALERTA[a.nivel]} {a.titulo}</p>
            <p className="mt-0.5 text-xs opacity-80">{a.detalhe}</p>
          </div>
        ))}
      </div>

      {/* números */}
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-8">
        <Numero icone="🛑" titulo="Erros (jogo)" valor={fmt(r.errosSessaoJogo)} detalhe={`+${fmt(r.errosInicio)} no carregamento`} cor={r.errosSessaoJogo ? "text-red-400" : "text-green-400"} />
        <Numero icone="⚠️" titulo="Avisos" valor={fmt(r.avisosSessao)} detalhe="nesta sessão" cor="text-yellow-300" />
        <Numero icone="🐢" titulo="Travadas" valor={fmt(r.lagsSessao)} detalhe={r.lagsSessao ? `pior: ${fmt(r.lagMsMaximo)} ms · total ${(r.lagMsTotal / 1000).toFixed(1)} s` : "nenhuma"} cor={r.lagsSessao ? "text-sky-300" : "text-green-400"} />
        <Numero icone="💥" titulo="Crashes (7 dias)" valor={r.crashes7d} detalhe={d.crashes[0] ? `último ${ha(d.crashes[0].quando, d.agora)}` : "nenhum"} cor={r.crashes7d ? "text-red-400" : "text-green-400"} />
        <Numero icone="⚡" titulo="TPS médio (5 min)" valor={r.tpsMedio5min || "—"} detalhe={r.msptPico5min ? `pico ${r.msptPico5min} ms/tick` : "sem dados ao vivo"} cor={!r.tpsMedio5min ? "text-pedra-500" : r.tpsMedio5min >= 18 ? "text-green-400" : r.tpsMedio5min >= 14 ? "text-yellow-300" : "text-red-400"} />
        <Numero icone="🔌" titulo="Desconexões" valor={r.desconexoes} detalhe={`${r.entradasJogo} entradas na sessão`} cor="text-pergaminho" />
        <Numero icone="⏱️" titulo="Sessão" valor={r.iniciadoEm ? ha(r.iniciadoEm, d.agora).replace("há ", "") : "—"} detalhe={r.tempoInicio ? `ligou em ${r.tempoInicio.toFixed(1)} s` : ""} cor="text-pergaminho" />
        <Numero icone="💾" titulo="Disco livre" valor={r.disco ? `${r.disco.livreGb} GB` : "—"} detalhe={`${r.disco ? `de ${r.disco.totalGb} GB · ` : ""}logs ${r.logsMb} MB`} cor={r.disco && r.disco.livreGb < 10 ? "text-red-400" : "text-pergaminho"} />
      </div>

      {/* gráficos */}
      <div className="grid gap-5 xl:grid-cols-3">
        <Painel titulo="Erros por hora (24 h)" icone="📈">
          <Barras dados={d.porHora.map((h) => ({ k: String(h.hora), ...h }))} rotulo={(k) => new Date(Number(k)).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })} />
        </Painel>
        <Painel titulo="TPS ao vivo" icone="⚡" extra={<span className="text-xs text-pedra-500">últimas 2 h · ideal 20</span>}>
          <Linha pontos={hist.map((h) => ({ t: h.t, v: h.tps }))} cor="#7cd67c" max={20} unidade="TPS" limite={15} />
          <p className="mt-3 text-xs text-pedra-500">Tempo por tick (ms) · limite 50</p>
          <Linha pontos={hist.map((h) => ({ t: h.t, v: h.mspt ?? 0 }))} cor="#d4a93a" unidade="ms" limite={50} />
        </Painel>
        <Painel titulo="Memória e mundo" icone="🧠">
          <Linha pontos={hist.map((h) => ({ t: h.t, v: (h.memMb ?? 0) / 1024 }))} cor="#c58cf0" max={hist.length ? (hist[hist.length - 1].memMaxMb ?? 10240) / 1024 : undefined} unidade="GB" />
          <p className="mt-3 text-xs text-pedra-500">Entidades no mundo</p>
          <Linha pontos={hist.map((h) => ({ t: h.t, v: h.entidades }))} cor="#e05a4a" unidade="" />
        </Painel>
      </div>

      <div className="grid gap-5 xl:grid-cols-3">
        <Painel titulo="Últimos 7 dias" icone="🗓️">
          {d.porDia.length ? <Barras dados={d.porDia.map((x) => ({ k: x.dia, ...x }))} rotulo={(k) => k.split("-").reverse().slice(0, 2).join("/")} /> : <p className="text-sm text-pedra-500">Sem logs antigos.</p>}
        </Painel>
        <Painel titulo="Travadas desta sessão" icone="🐢">
          {!d.lags.length ? <p className="text-sm text-green-400">Nenhuma travada. 👌</p> : (
            <ul className="max-h-48 space-y-1 overflow-y-auto text-sm">
              {[...d.lags].reverse().map((l, i) => (
                <li key={i} className="flex items-center gap-2">
                  <span className="w-16 font-mono text-xs text-pedra-500">{hora(l.t)}</span>
                  <div className="h-2 flex-1 overflow-hidden rounded-full bg-pedra-800"><div className="h-full bg-sky-400" style={{ width: `${Math.min(100, (l.ms / Math.max(1, r.lagMsMaximo)) * 100)}%` }} /></div>
                  <span className="w-20 text-right font-mono text-xs">{fmt(l.ms)} ms</span>
                </li>
              ))}
            </ul>
          )}
        </Painel>
        <Painel titulo="Crashes e compilação" icone="💥">
          <p className="mb-2 text-sm">
            Última compilação do core mod:{" "}
            {r.compilacao.ok === true ? <span className="text-green-400">✅ ok</span> : r.compilacao.ok === false ? <span className="text-red-400">❌ falhou</span> : <span className="text-pedra-500">—</span>}
            <span className="text-xs text-pedra-500"> · {dataHora(r.compilacao.quando)}</span>
          </p>
          {!d.crashes.length ? <p className="text-sm text-green-400">Nenhum relatório de crash.</p> : (
            <ul className="max-h-44 space-y-2 overflow-y-auto text-sm">
              {d.crashes.map((c) => (
                <li key={c.arquivo} className="rounded-lg border border-red-500/30 bg-red-950/20 p-2">
                  <p className="text-red-300">{c.descricao || "Crash"} <span className="text-xs text-pedra-500">· {dataHora(c.quando)}</span></p>
                  {c.erro && <p className="truncate font-mono text-xs text-pedra-500" title={c.erro}>{c.erro}</p>}
                  <p className="font-mono text-[10px] text-pedra-500">server\crash-reports\{c.arquivo}</p>
                </li>
              ))}
            </ul>
          )}
        </Painel>
      </div>

      {/* log de erros agrupado */}
      <Painel titulo="Log de erros" icone="🧾" extra={<span className="text-xs text-pedra-500">{grupos.length} tipos · repetidos ficam agrupados</span>}>
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <button className={botao(nivel === "erros")} onClick={() => setNivel("erros")}>🔴 Erros</button>
          <button className={botao(nivel === "avisos")} onClick={() => setNivel("avisos")}>🟡 Avisos</button>
          <button className={botao(nivel === "todos")} onClick={() => setNivel("todos")}>Todos</button>
          <span className="mx-1 h-5 w-px bg-pedra-700" />
          {([["servidor", "Sessão atual"], ["anteriores", "Sessões anteriores"], ["painel", "Painel"], ["compilação", "Compilação"], ["todas", "Tudo"]] as const).map(([k, n]) => (
            <button key={k} className={botao(fonte === k)} onClick={() => setFonte(k)}>{n}</button>
          ))}
          <span className="mx-1 h-5 w-px bg-pedra-700" />
          <label className="flex items-center gap-1.5 text-xs text-pedra-500">
            <input type="checkbox" checked={semInicio} onChange={(e) => setSemInicio(e.target.checked)} /> esconder ruído do carregamento dos mods
          </label>
          <input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Buscar mensagem, mod ou classe…"
            className="ml-auto w-64 rounded-md border border-pedra-700 bg-pedra-950 px-2.5 py-1 text-sm" />
        </div>
        <div className="mb-3 flex flex-wrap gap-1.5">
          <button className={botao(cat === "todas")} onClick={() => setCat("todas")}>Todas as categorias</button>
          {categorias.map(([c, n]) => <button key={c} className={botao(cat === c)} onClick={() => setCat(c)}>{c} <span className="opacity-60">{fmt(n)}</span></button>)}
        </div>

        {!grupos.length ? <p className="py-6 text-center text-sm text-green-400">Nada aqui com esses filtros. 🎉</p> : (
          <ul className="space-y-1.5">
            {grupos.map((g) => (
              <li key={g.chave} className={`rounded-lg border ${COR_NIVEL[g.nivel]}`}>
                <button className="flex w-full items-start gap-3 px-3 py-2 text-left" onClick={() => setAberto(aberto === g.chave ? null : g.chave)}>
                  <span className="mt-0.5 min-w-10 rounded bg-black/40 px-1.5 text-center font-mono text-xs">{g.total > 999 ? "999+" : g.total}×</span>
                  <span className="min-w-0 flex-1">
                    <span className="block break-words text-sm text-pergaminho">{g.exemplo}</span>
                    <span className="mt-0.5 block text-xs text-pedra-500">
                      {g.nivel} · {g.categoria} · <span className="font-mono">{g.origem}</span> · {g.fonte}{g.inicio ? " · carregamento" : ""}
                    </span>
                  </span>
                  <span className="shrink-0 text-right text-xs text-pedra-500">{ha(g.ultima, d.agora)}<br />{g.total > 1 && g.primeira ? `desde ${hora(g.primeira)}` : ""}</span>
                </button>
                {aberto === g.chave && (
                  <div className="border-t border-black/30 px-3 py-2">
                    {g.pilha.length ? <pre className="max-h-72 overflow-auto whitespace-pre-wrap font-mono text-[11px] text-pedra-500">{g.pilha.join("\n")}</pre>
                      : <p className="text-xs text-pedra-500">Sem rastro de pilha (stack trace) nesta mensagem.</p>}
                    <button className="mt-2 rounded border border-pedra-700 px-2 py-0.5 text-xs hover:border-ouro" onClick={() => navigator.clipboard?.writeText(`${g.exemplo}\n${g.pilha.join("\n")}`)}>📋 copiar para me mandar</button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </Painel>

      {!!d.desconexoes.length && (
        <Painel titulo="Desconexões desta sessão" icone="🔌">
          <ul className="space-y-1 text-sm">
            {[...d.desconexoes].reverse().map((x, i) => <li key={i}><span className="mr-2 font-mono text-xs text-pedra-500">{hora(x.t)}</span>{x.msg}</li>)}
          </ul>
        </Painel>
      )}
    </div>
  );
}
