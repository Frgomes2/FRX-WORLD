"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { MapaMundo, type MapaDados } from "./mapa-mundo";

// ------------------------------------------------------------------ tipos

type Ranking = { nick: string; uuid: string; valor: number; extra: string; titulo: string };

export type DadosBanco = {
  jogadores: number;
  ativos: number;
  convitesLivres: number;
  economia: { bolsa: number; banco: number; mortes: number; conquistas: number };
  porNivel: Ranking[];
  porRiqueza: Ranking[];
  porMortes: Ranking[];
  classes: Record<string, number>;
  masmorras: { id: string; nome: string; chefe: string; x: number; z: number; colocada: boolean }[];
};

type Jogador = {
  nick: string; uuid: string; x: number; y: number; z: number; dim: string;
  vida: number; vidaMax: number; ping: number; nivel?: number; classe?: string; reino?: string;
};

type Status = {
  mspt: number; tps: number; memUsadaMb: number; memMaxMb: number; versaoMod: string; mods: number; maxJogadores: number;
  dia: number; hora: number; clima: "limpo" | "chuva" | "tempestade"; spawn: [number, number];
  jogadores: Jogador[];
  entidades: { total: number; monstros: number; animais: number; aldeoes: number; itens: number; outros: number; porDimensao: Record<string, number>; porTipo: { id: string; n: number }[] };
  chunksCarregados: number;
  reinos: { id: string; nome: string; x0: number; z0: number; x1: number; z1: number; subterraneo: boolean }[];
  chefe?: { fase: "aviso" | "ativo"; nome: string; x: number; z: number; surgeEmS?: number; fogeEmS?: number; vida?: number; participantes?: number };
};

type Evento = { hora: string; tipo: "entrou" | "saiu" | "morte" | "conquista" | "chefe" | "invasao" | "forja" | "chat" | "outro"; texto: string };

type Resposta = {
  agora: number;
  servidor: { estado: "parado" | "iniciando" | "online" | "parando"; jogadores: string[]; iniciadoEm: number | null } | null;
  status: { dados: Status | null; recebidoEm: number | null; historico: { t: number; tps: number; jogadores: number; entidades: number }[] };
  mods: { servidor: number; pack: number };
  feed: Evento[];
  mapa: MapaDados | null;
};

// ------------------------------------------------------------------ utilidades

const fmt = (n: number) => n.toLocaleString("pt-BR");

function moedas(cobre: number) {
  const o = Math.floor(cobre / 100), p = Math.floor((cobre % 100) / 10), c = cobre % 10;
  const partes = [];
  if (o) partes.push(`${fmt(o)} 🟡`);
  if (p) partes.push(`${p} ⚪`);
  if (c || !partes.length) partes.push(`${c} 🟤`);
  return partes.join(" ");
}

function duracao(ms: number) {
  const s = Math.floor(ms / 1000);
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60);
  if (d) return `${d}d ${h}h ${m}min`;
  if (h) return `${h}h ${m}min`;
  return `${m}min ${s % 60}s`;
}

function relogio(seg: number) {
  const m = Math.floor(seg / 60), s = seg % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

const NOMES_MOB: Record<string, string> = {
  zombie: "Zumbi", skeleton: "Esqueleto", creeper: "Creeper", spider: "Aranha", cave_spider: "Aranha de caverna", enderman: "Enderman",
  witch: "Bruxa", drowned: "Afogado", husk: "Zumbi do deserto", stray: "Esqueleto gélido", slime: "Slime", phantom: "Phantom",
  cow: "Vaca", pig: "Porco", sheep: "Ovelha", chicken: "Galinha", horse: "Cavalo", donkey: "Burro", rabbit: "Coelho", wolf: "Lobo",
  cat: "Gato", fox: "Raposa", bee: "Abelha", goat: "Cabra", llama: "Lhama", parrot: "Papagaio", turtle: "Tartaruga", frog: "Sapo",
  axolotl: "Axolote", bat: "Morcego", squid: "Lula", glow_squid: "Lula brilhante", cod: "Bacalhau", salmon: "Salmão",
  tropical_fish: "Peixe tropical", pufferfish: "Baiacu", dolphin: "Golfinho", villager: "Aldeão", wandering_trader: "Mercador viajante",
  iron_golem: "Golem de ferro", guard: "Guarda", pillager: "Saqueador", vindicator: "Vindicador", evoker: "Invocador", ravager: "Devastador",
  piglin: "Piglin", zombified_piglin: "Piglin zumbi", hoglin: "Hoglin", ghast: "Ghast", magma_cube: "Cubo de magma", blaze: "Blaze",
  wither_skeleton: "Esqueleto Wither", strider: "Lavagante", camel: "Camelo", sniffer: "Farejador", polar_bear: "Urso polar",
  panda: "Panda", ocelot: "Jaguatirica", mooshroom: "Vacogumelo", silverfish: "Traça", endermite: "Endermite", allay: "Allay",
};

function nomeMob(id: string) {
  const [ns, nome] = id.includes(":") ? id.split(":") : ["minecraft", id];
  const pt = ns === "minecraft" || ns === "guardvillagers" ? NOMES_MOB[nome] : undefined;
  return { nome: pt ?? nome.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()), mod: ns === "minecraft" ? "" : ns };
}

const ICONE_EVENTO: Record<Evento["tipo"], string> = {
  entrou: "🟢", saiu: "⚪", morte: "💀", conquista: "🏆", chefe: "☠️", invasao: "⚔️", forja: "⚒️", chat: "💬", outro: "•",
};

const CLASSE_ICONE: Record<string, string> = {
  GUERREIRO: "⚔️", MAGO: "🔮", ARQUEIRO: "🏹", CLERIGO: "✨", LADINO: "🎭", ASSASSINO: "🗡️", INVOCADOR: "👻", "SEM CLASSE": "❔",
};

// ------------------------------------------------------------------ peças visuais

function Painel({ titulo, icone, children, className = "", extra }: { titulo: string; icone?: string; children: React.ReactNode; className?: string; extra?: React.ReactNode }) {
  return (
    <section className={`rounded-xl border border-pedra-700 bg-pedra-900/80 p-4 shadow-lg shadow-black/30 ${className}`}>
      <div className="mb-3 flex items-center justify-between gap-2">
        <h2 className="font-titulo text-lg text-ouro">{icone && <span className="mr-1.5">{icone}</span>}{titulo}</h2>
        {extra}
      </div>
      {children}
    </section>
  );
}

function Bloco({ icone, titulo, valor, detalhe, cor = "text-ouro", children }: { icone: string; titulo: string; valor: React.ReactNode; detalhe?: React.ReactNode; cor?: string; children?: React.ReactNode }) {
  return (
    <div className="group relative overflow-hidden rounded-xl border border-pedra-700 bg-gradient-to-br from-pedra-900 to-pedra-950 p-4 transition hover:-translate-y-0.5 hover:border-ouro/60">
      <span className="pointer-events-none absolute -right-2 -top-3 select-none text-6xl opacity-10 transition group-hover:scale-110 group-hover:opacity-20">{icone}</span>
      <p className="text-xs uppercase tracking-wider text-pedra-500">{titulo}</p>
      <p className={`mt-1 font-titulo text-3xl ${cor}`}>{valor}</p>
      {detalhe && <p className="mt-1 text-xs text-pedra-500">{detalhe}</p>}
      {children}
    </div>
  );
}

function Barra({ valor, max, cor }: { valor: number; max: number; cor: string }) {
  const pct = max > 0 ? Math.min(100, (valor / max) * 100) : 0;
  return (
    <div className="mt-2 h-2 overflow-hidden rounded-full bg-pedra-800">
      <div className={`h-full rounded-full transition-all duration-700 ${cor}`} style={{ width: `${pct}%` }} />
    </div>
  );
}

/** Semicírculo tipo velocímetro para o TPS. */
function Medidor({ valor, max = 20 }: { valor: number; max?: number }) {
  const frac = Math.max(0, Math.min(1, valor / max));
  const cor = valor >= 18 ? "#7cd67c" : valor >= 14 ? "#d4a93a" : "#e05a4a";
  const ang = Math.PI * (1 - frac);
  const x = 50 + 38 * Math.cos(ang), y = 50 - 38 * Math.sin(ang);
  return (
    <svg viewBox="0 0 100 58" className="mt-1 h-16 w-full">
      <path d="M12 50 A38 38 0 0 1 88 50" fill="none" stroke="#332b22" strokeWidth="9" strokeLinecap="round" />
      <path d={`M12 50 A38 38 0 0 1 ${x.toFixed(2)} ${y.toFixed(2)}`} fill="none" stroke={cor} strokeWidth="9" strokeLinecap="round" className="transition-all duration-700" />
      <text x="50" y="47" textAnchor="middle" fill={cor} fontSize="17" fontFamily="Georgia, serif">{valor.toFixed(1)}</text>
      <text x="50" y="57" textAnchor="middle" fill="#6b5f50" fontSize="7">TPS</text>
    </svg>
  );
}

/** Mini gráfico de linha. */
function Faisca({ pontos, cor, max }: { pontos: number[]; cor: string; max?: number }) {
  if (pontos.length < 2) return <div className="h-8" />;
  const m = max ?? Math.max(1, ...pontos);
  const d = pontos.map((p, i) => `${(i / (pontos.length - 1)) * 100},${30 - (p / m) * 28}`).join(" ");
  return (
    <svg viewBox="0 0 100 32" preserveAspectRatio="none" className="mt-2 h-8 w-full">
      <polyline points={`0,32 ${d} 100,32`} fill={cor} fillOpacity="0.15" stroke="none" />
      <polyline points={d} fill="none" stroke={cor} strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

/** Rosca de categorias. */
function Rosca({ fatias }: { fatias: { nome: string; valor: number; cor: string }[] }) {
  const total = fatias.reduce((s, f) => s + f.valor, 0);
  let acc = 0;
  const R = 40, C = 2 * Math.PI * R;
  return (
    <div className="flex items-center gap-4">
      <svg viewBox="0 0 100 100" className="h-32 w-32 shrink-0 -rotate-90">
        <circle cx="50" cy="50" r={R} fill="none" stroke="#221d17" strokeWidth="16" />
        {total > 0 && fatias.map((f) => {
          const len = (f.valor / total) * C;
          const el = <circle key={f.nome} cx="50" cy="50" r={R} fill="none" stroke={f.cor} strokeWidth="16" strokeDasharray={`${len} ${C - len}`} strokeDashoffset={-acc} className="transition-all duration-700" />;
          acc += len;
          return el;
        })}
      </svg>
      <ul className="space-y-1.5 text-sm">
        {fatias.map((f) => (
          <li key={f.nome} className="flex items-center gap-2">
            <span className="h-3 w-3 rounded-sm" style={{ background: f.cor }} />
            <span className="text-pedra-500">{f.nome}</span>
            <span className="ml-auto pl-3 font-mono text-pergaminho">{fmt(f.valor)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Relógio do mundo: sol e lua girando sobre o horizonte. */
function RelogioMundo({ hora, dia, clima }: { hora: number; dia: number; clima: Status["clima"] }) {
  const h = ((hora / 1000 + 6) % 24);
  const hh = Math.floor(h), mm = Math.floor((h - hh) * 60);
  // 0 = nascer do sol (6h), 6000 = meio-dia, 12000 = pôr do sol, 18000 = meia-noite
  const ang = Math.PI - (hora / 24000) * 2 * Math.PI;
  const sx = 60 + 44 * Math.cos(ang), sy = 62 - 44 * Math.sin(ang);
  const lx = 60 - 44 * Math.cos(ang), ly = 62 + 44 * Math.sin(ang);
  const dia_ = hora < 12000;
  const crepusculo = (hora > 11000 && hora < 13500) || hora > 22500 || hora < 1000;
  const ceu = crepusculo ? ["#f08a4b", "#5b3a6b"] : dia_ ? ["#6fb3e8", "#bfe1f7"] : ["#0b1030", "#1d2350"];
  const periodo = crepusculo ? (hora > 20000 || hora < 2000 ? "Amanhecer" : "Entardecer") : dia_ ? "Dia" : "Noite";
  const iconeClima = clima === "tempestade" ? "⛈️" : clima === "chuva" ? "🌧️" : dia_ ? "☀️" : "🌙";
  return (
    <div className="flex items-center gap-4">
      <svg viewBox="0 0 120 70" className="h-24 w-40 shrink-0 overflow-hidden rounded-lg">
        <defs>
          <linearGradient id="ceu" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor={ceu[0]} />
            <stop offset="1" stopColor={ceu[1]} />
          </linearGradient>
        </defs>
        <rect width="120" height="70" fill="url(#ceu)" className="transition-all duration-1000" />
        {!dia_ && [[12, 10], [30, 22], [88, 12], [104, 28], [70, 6], [50, 16]].map(([x, y]) => <circle key={`${x}${y}`} cx={x} cy={y} r="0.9" fill="#fff" opacity="0.8" />)}
        <circle cx={sx} cy={sy} r="7" fill="#ffd54a" stroke="#ffef9a" strokeWidth="2" />
        <circle cx={lx} cy={ly} r="5.5" fill="#e8e8f0" />
        <circle cx={lx + 2} cy={ly - 1} r="1.2" fill="#c9c9d6" />
        {clima !== "limpo" && <g opacity="0.85"><ellipse cx="40" cy="14" rx="16" ry="6" fill="#8a8f99" /><ellipse cx="78" cy="18" rx="20" ry="7" fill="#767b85" /></g>}
        {clima !== "limpo" && Array.from({ length: 14 }).map((_, i) => <line key={i} x1={8 + i * 8} y1={24 + (i % 3) * 6} x2={6 + i * 8} y2={30 + (i % 3) * 6} stroke="#9ec5ff" strokeWidth="0.8" />)}
        <rect y="62" width="120" height="8" fill="#3b6e2c" />
        <rect y="66" width="120" height="4" fill="#6b4a2b" />
      </svg>
      <div>
        <p className="font-titulo text-4xl text-pergaminho">{String(hh).padStart(2, "0")}:{String(mm).padStart(2, "0")}</p>
        <p className="text-sm text-pedra-500">{iconeClima} {periodo} · Dia {fmt(dia)} do mundo</p>
        <p className="text-xs text-pedra-500">{clima === "limpo" ? "Céu limpo" : clima === "chuva" ? "Chovendo" : "Tempestade! Cuidado com raios"}</p>
      </div>
    </div>
  );
}

function Avatar({ uuid, nick, tam = 32 }: { uuid: string; nick: string; tam?: number }) {
  const [falhou, setFalhou] = useState(false);
  const ref = useRef<HTMLImageElement>(null);
  useEffect(() => { const im = ref.current; if (im && im.complete && im.naturalWidth === 0) setFalhou(true); }, []);
  if (falhou) {
    const cor = ["#b45309", "#047857", "#1d4ed8", "#7e22ce", "#be123c", "#0e7490"][nick.length % 6];
    return <span className="grid shrink-0 place-items-center rounded-md font-bold text-white" style={{ width: tam, height: tam, background: cor, fontSize: tam * 0.5 }}>{nick[0]?.toUpperCase()}</span>;
  }
  // eslint-disable-next-line @next/next/no-img-element
  return <img ref={ref} src={`https://mc-heads.net/avatar/${uuid || nick}/${tam}`} alt={nick} width={tam} height={tam} onError={() => setFalhou(true)} className="shrink-0 rounded-md [image-rendering:pixelated]" />;
}

function ListaRanking({ itens, formato, vazio }: { itens: Ranking[]; formato: (v: number) => string; vazio: string }) {
  if (!itens.length) return <p className="text-sm text-pedra-500">{vazio}</p>;
  const medalha = ["🥇", "🥈", "🥉", "4º", "5º"];
  return (
    <ol className="space-y-2">
      {itens.map((j, i) => (
        <li key={`${j.nick}-${i}`} className="flex items-center gap-2.5 rounded-lg bg-pedra-950/60 px-2 py-1.5">
          <span className="w-6 text-center text-sm">{medalha[i]}</span>
          <Avatar uuid={j.uuid} nick={j.nick} tam={24} />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm">{j.nick} {j.titulo && <span className="text-xs text-ouro/80">· {j.titulo}</span>}</p>
            {j.extra && <p className="truncate text-xs text-pedra-500">{j.extra}</p>}
          </div>
          <span className="font-mono text-sm text-ouro">{formato(j.valor)}</span>
        </li>
      ))}
    </ol>
  );
}

// ------------------------------------------------------------------ tela

export function VisaoAoVivo({ banco }: { banco: DadosBanco }) {
  const [r, setR] = useState<Resposta | null>(null);
  const [mapa, setMapa] = useState<MapaDados | null>(null);
  const [agora, setAgora] = useState(() => Date.now());
  const [erro, setErro] = useState(false);

  useEffect(() => {
    let vivo = true;
    let ciclo = 0;
    async function buscar() {
      try {
        const comMapa = ciclo % 9 === 0; // mapa a cada ~90 s
        const res = await fetch(`/api/painel/visao${comMapa ? "?mapa=1" : ""}`, { cache: "no-store" });
        if (!res.ok) throw new Error();
        const j = (await res.json()) as Resposta;
        if (!vivo) return;
        setR(j);
        if (j.mapa) setMapa(j.mapa);
        setErro(false);
      } catch {
        if (vivo) setErro(true);
      }
      ciclo++;
    }
    buscar();
    const t = setInterval(buscar, 10_000);
    const t2 = setInterval(() => setAgora(Date.now()), 1000);
    return () => { vivo = false; clearInterval(t); clearInterval(t2); };
  }, []);

  const s = r?.status.dados ?? null;
  const online = !!s || r?.servidor?.estado === "online";
  const estado = s ? "online" : r?.servidor?.estado ?? "parado";
  const hist = r?.status.historico ?? [];

  const classes = useMemo(() => Object.entries(banco.classes).sort((a, b) => b[1] - a[1]), [banco.classes]);

  const corEstado = { online: "bg-green-500", iniciando: "bg-yellow-400", parando: "bg-orange-400", parado: "bg-red-500" }[estado];
  const textoEstado = { online: "Servidor online", iniciando: "Servidor ligando…", parando: "Servidor desligando…", parado: "Servidor desligado" }[estado];

  return (
    <div className="space-y-5">
      {/* ---------------- faixa de topo */}
      <div className="relative overflow-hidden rounded-2xl border border-ouro/30 bg-gradient-to-r from-pedra-900 via-pedra-800 to-pedra-900 p-5">
        <div className="pointer-events-none absolute inset-0 opacity-[0.07] [background-image:repeating-linear-gradient(45deg,#d4a93a_0_2px,transparent_2px_14px)]" />
        <div className="relative flex flex-wrap items-center gap-x-10 gap-y-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="relative flex h-3.5 w-3.5">
                {online && <span className={`absolute inline-flex h-full w-full animate-ping rounded-full ${corEstado} opacity-60`} />}
                <span className={`relative inline-flex h-3.5 w-3.5 rounded-full ${corEstado}`} />
              </span>
              <span className="font-titulo text-xl">{textoEstado}</span>
            </div>
            <p className="mt-1 text-sm text-pedra-500">
              {r?.servidor?.iniciadoEm ? <>No ar há <span className="text-pergaminho">{duracao(agora - r.servidor.iniciadoEm)}</span></> : online ? "Ligado fora do painel" : "Ligue pela aba Servidor"}
              {s && <> · core mod <span className="text-pergaminho">v{s.versaoMod}</span></>}
            </p>
            <p className="text-xs text-pedra-500" suppressHydrationWarning>
              {new Date(agora).toLocaleDateString("pt-BR", { weekday: "long", day: "2-digit", month: "long" })} · {new Date(agora).toLocaleTimeString("pt-BR")}
              {erro && <span className="ml-2 text-red-400">· sem resposta do painel</span>}
            </p>
          </div>
          {s ? <RelogioMundo hora={s.hora} dia={s.dia} clima={s.clima} /> : <p className="text-sm text-pedra-500">O relógio do mundo aparece quando o servidor estiver ligado.</p>}
          {s?.chefe && (
            <div className={`rounded-xl border px-4 py-2 ${s.chefe.fase === "ativo" ? "animate-pulse border-red-500/70 bg-red-950/40" : "border-orange-400/60 bg-orange-950/30"}`}>
              <p className="font-titulo text-red-300">☠ Chefe mundial: {s.chefe.nome}</p>
              <p className="text-xs text-pergaminho/80">
                {s.chefe.fase === "aviso" ? `Surge em ${relogio(s.chefe.surgeEmS ?? 0)}` : `Vida ${s.chefe.vida ?? "?"}% · ${s.chefe.participantes ?? 0} lutando · foge em ${relogio(s.chefe.fogeEmS ?? 0)}`}
                {" "}· {s.chefe.x}, {s.chefe.z}
              </p>
            </div>
          )}
        </div>
      </div>

      {/* ---------------- números */}
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-7">
        <Bloco icone="🧙" titulo="Jogando agora" valor={<>{s?.jogadores.length ?? r?.servidor?.jogadores.length ?? 0}<span className="text-lg text-pedra-500">/{s?.maxJogadores ?? "?"}</span></>}
          detalhe={`${banco.ativos} com acesso · ${banco.jogadores} cadastrados`}>
          <Faisca pontos={hist.map((h) => h.jogadores)} cor="#d4a93a" />
        </Bloco>
        <Bloco icone="⚡" titulo="Desempenho" valor={s ? `${s.mspt} ms` : "—"} detalhe="por tick (ideal ≤ 50 ms)" cor={!s ? "text-pedra-500" : s.mspt <= 50 ? "text-green-400" : "text-red-400"}>
          {s && <Medidor valor={s.tps} />}
        </Bloco>
        <Bloco icone="🧠" titulo="Memória" valor={s ? `${(s.memUsadaMb / 1024).toFixed(1)} GB` : "—"} detalhe={s ? `de ${(s.memMaxMb / 1024).toFixed(1)} GB` : ""}>
          {s && <Barra valor={s.memUsadaMb} max={s.memMaxMb} cor={s.memUsadaMb / s.memMaxMb > 0.85 ? "bg-red-500" : "bg-ouro"} />}
        </Bloco>
        <Bloco icone="👾" titulo="Criaturas vivas" valor={s ? fmt(s.entidades.total - s.entidades.itens) : "—"} detalhe={s ? `${fmt(s.entidades.monstros)} monstros agora` : ""} cor="text-red-300">
          <Faisca pontos={hist.map((h) => h.entidades)} cor="#e05a4a" />
        </Bloco>
        <Bloco icone="🧩" titulo="Mods" valor={r ? r.mods.servidor : "—"} detalhe={r ? `no servidor · ${r.mods.pack} no pack dos jogadores` : ""} cor="text-purple-300" />
        <Bloco icone="🗺️" titulo="Mundo explorado" valor={mapa ? fmt(mapa.chunks) : "—"} detalhe={mapa ? `chunks · ${fmt(Math.round(mapa.chunks * 256 / 1_000_000 * 10) / 10)} km² · ${fmt(mapa.tamanhoMb)} MB` : "lendo…"} cor="text-green-300" />
        <Bloco icone="📦" titulo="Chunks carregados" valor={s ? fmt(s.chunksCarregados) : "—"} detalhe="agora na memória" cor="text-sky-300" />
      </div>

      {/* ---------------- mapa + jogadores */}
      <div className="grid gap-5 xl:grid-cols-3">
        <Painel titulo="Mapa do mundo" icone="🗺️" className="xl:col-span-2"
          extra={<span className="text-xs text-pedra-500">arraste para mover · roda do mouse para zoom</span>}>
          <MapaMundo mapa={mapa} reinos={s?.reinos ?? []} jogadores={(s?.jogadores ?? []).filter((j) => j.dim === "overworld")}
            masmorras={banco.masmorras} spawn={s?.spawn ?? null} chefe={s?.chefe ?? null} />
        </Painel>

        <div className="space-y-5">
          <Painel titulo="Aventureiros online" icone="🧭">
            {!s?.jogadores.length ? (
              <p className="text-sm text-pedra-500">{online ? "Ninguém jogando agora. O reino descansa… 💤" : "Servidor desligado."}</p>
            ) : (
              <ul className="space-y-2">
                {s.jogadores.map((j) => (
                  <li key={j.uuid} className="rounded-lg border border-pedra-700 bg-pedra-950/60 p-2.5">
                    <div className="flex items-center gap-2.5">
                      <Avatar uuid={j.uuid} nick={j.nick} />
                      <div className="min-w-0 flex-1">
                        <p className="truncate">{j.nick} <span className="text-xs text-ouro">Nv {j.nivel ?? "?"}</span></p>
                        <p className="truncate text-xs text-pedra-500">{CLASSE_ICONE[j.classe || "SEM CLASSE"] ?? "❔"} {j.classe || "sem classe"} · {j.reino ?? (j.dim === "overworld" ? "explorando" : j.dim === "the_nether" ? "no Nether 🔥" : j.dim === "the_end" ? "no End 🌌" : j.dim)}</p>
                      </div>
                      <span className={`text-xs ${j.ping < 80 ? "text-green-400" : j.ping < 200 ? "text-yellow-400" : "text-red-400"}`}>📶 {j.ping}ms</span>
                    </div>
                    <div className="mt-1.5 flex items-center gap-2 text-xs">
                      <span>❤️</span>
                      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-pedra-800">
                        <div className="h-full bg-red-500 transition-all" style={{ width: `${Math.min(100, (j.vida / Math.max(1, j.vidaMax)) * 100)}%` }} />
                      </div>
                      <span className="font-mono text-pedra-500">{j.x}, {j.y}, {j.z}</span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Painel>

          <Painel titulo="Crônicas do reino" icone="📜" extra={<span className="text-xs text-pedra-500">últimos acontecimentos</span>}>
            {!r?.feed.length ? <p className="text-sm text-pedra-500">Nada aconteceu ainda… por enquanto.</p> : (
              <ul className="max-h-80 space-y-1.5 overflow-y-auto pr-1 text-sm">
                {r.feed.map((e, i) => (
                  <li key={i} className="flex gap-2 border-b border-pedra-800 pb-1.5 last:border-0">
                    <span className="w-5 shrink-0 text-center">{ICONE_EVENTO[e.tipo]}</span>
                    <span className="flex-1 break-words">{e.texto}</span>
                    <span className="shrink-0 font-mono text-xs text-pedra-500">{e.hora}</span>
                  </li>
                ))}
              </ul>
            )}
          </Painel>
        </div>
      </div>

      {/* ---------------- criaturas */}
      <div className="grid gap-5 xl:grid-cols-3">
        <Painel titulo="População do mundo" icone="🐾">
          {s ? (
            <>
              <Rosca fatias={[
                { nome: "Monstros", valor: s.entidades.monstros, cor: "#e05a4a" },
                { nome: "Animais", valor: s.entidades.animais, cor: "#7cd67c" },
                { nome: "Aldeões e NPCs", valor: s.entidades.aldeoes, cor: "#d4a93a" },
                { nome: "Itens no chão", valor: s.entidades.itens, cor: "#8a7d6a" },
                { nome: "Outros", valor: s.entidades.outros, cor: "#6b8ac5" },
              ]} />
              <div className="mt-4 grid grid-cols-3 gap-2 text-center text-xs">
                {[["overworld", "🌍 Superfície"], ["the_nether", "🔥 Nether"], ["the_end", "🌌 End"]].map(([k, n]) => (
                  <div key={k} className="rounded-lg bg-pedra-950/60 p-2">
                    <p className="text-pedra-500">{n}</p>
                    <p className="font-mono text-base text-pergaminho">{fmt(s.entidades.porDimensao[k] ?? 0)}</p>
                  </div>
                ))}
              </div>
            </>
          ) : <p className="text-sm text-pedra-500">Aparece com o servidor ligado.</p>}
        </Painel>

        <Painel titulo="Quem mais aparece por aí" icone="🔎" className="xl:col-span-2" extra={<span className="text-xs text-pedra-500">as 12 criaturas mais numerosas</span>}>
          {s?.entidades.porTipo.length ? (
            <ul className="grid gap-x-6 gap-y-2 sm:grid-cols-2">
              {s.entidades.porTipo.map((t) => {
                const { nome, mod } = nomeMob(t.id);
                const max = s.entidades.porTipo[0].n;
                return (
                  <li key={t.id}>
                    <div className="flex items-baseline justify-between text-sm">
                      <span>{nome} {mod && <span className="text-xs text-purple-300/70">· {mod}</span>}</span>
                      <span className="font-mono text-pedra-500">{fmt(t.n)}</span>
                    </div>
                    <Barra valor={t.n} max={max} cor={mod ? "bg-purple-400/80" : "bg-ouro/80"} />
                  </li>
                );
              })}
            </ul>
          ) : <p className="text-sm text-pedra-500">Aparece com o servidor ligado.</p>}
        </Painel>
      </div>

      {/* ---------------- rankings */}
      <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
        <Painel titulo="Mais fortes" icone="⚔️"><ListaRanking itens={banco.porNivel} formato={(v) => `Nv ${v}`} vazio="Ninguém jogou ainda." /></Painel>
        <Painel titulo="Mais ricos" icone="💰"><ListaRanking itens={banco.porRiqueza} formato={moedas} vazio="Ninguém tem moedas ainda." /></Painel>
        <Painel titulo="Mais azarados" icone="💀"><ListaRanking itens={banco.porMortes} formato={(v) => `${v} mortes`} vazio="Ninguém morreu. Ainda." /></Painel>
        <Painel titulo="Economia e feitos" icone="📊">
          <ul className="space-y-2 text-sm">
            <li className="flex justify-between"><span className="text-pedra-500">Moedas nas bolsas</span><span>{moedas(banco.economia.bolsa)}</span></li>
            <li className="flex justify-between"><span className="text-pedra-500">Guardado nos bancos</span><span>{moedas(banco.economia.banco)}</span></li>
            <li className="flex justify-between"><span className="text-pedra-500">Conquistas ganhas</span><span>🏆 {fmt(banco.economia.conquistas)}</span></li>
            <li className="flex justify-between"><span className="text-pedra-500">Mortes no total</span><span>💀 {fmt(banco.economia.mortes)}</span></li>
            <li className="flex justify-between"><span className="text-pedra-500">Convites livres</span><span>✉️ {banco.convitesLivres}</span></li>
          </ul>
          <p className="mb-1.5 mt-4 text-xs uppercase tracking-wider text-pedra-500">Classes escolhidas</p>
          <div className="flex flex-wrap gap-1.5">
            {classes.map(([c, n]) => (
              <span key={c} className="rounded-full border border-pedra-700 bg-pedra-950/60 px-2 py-0.5 text-xs">{CLASSE_ICONE[c] ?? "❔"} {c.toLowerCase()} <span className="text-ouro">{n}</span></span>
            ))}
          </div>
        </Painel>
      </div>
    </div>
  );
}
