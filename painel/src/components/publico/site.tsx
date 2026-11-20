"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { CriaturaPublica, DadosPublicos, LinhaRank, MapaPublico as MapaDados, ModPublico } from "@/lib/publico";
import { Bestiario } from "./bestiario";
import { MapaPublico } from "./mapa";
import { Dragao } from "./dragao";
import { Clima } from "./clima";
import { Biblioteca } from "./biblioteca";
import type { Livro } from "@/lib/livros";
import type { NotaAtualizacao } from "@/lib/notas";
import { Galeria, ICONE_CLASSE, Nick, Perfil, Podio, Skin } from "./jogadores";

const RANKS = ["F", "E", "D", "C", "B", "A", "S"];
const SISTEMAS: [string, string, string][] = [
  ["⚔️", "13 classes", "Guerreiro, Arqueiro, Mago, Clérigo, Ladino, Assassino, Invocador, Paladino, Bardo, Monge, Druida, Samurai e Cartomante, cada uma com habilidades próprias e um Despertar."],
  ["🛡️", "Guilda de Aventureiros", "Suba do rank F ao S cumprindo missões. O rank libera equipamentos melhores, descontos e portais mais perigosos."],
  ["🗼", "Torre Infinita", "100 andares temáticos com chefes. A cada 10 andares, um andar seguro com baú, cozinheiro e mercador."],
  ["📜", "História principal", "14 capítulos que levam do primeiro passo ao confronto com o Rei Demônio."],
  ["🏆", "Temporadas", "Todo mês um placar novo. Os três primeiros levam runas e um título exclusivo."],
  ["🌀", "Portais e dungeons", "Fendas abrem pelo mundo com masmorras por rank e chefões sorteados."],
  ["👑", "Reinos", "Capitais com moradores, lojas, reputação e campanhas. Algumas ainda esperam ser encontradas."],
  ["💠", "Runas e forja", "11 runas para encaixar no equipamento e armas com raridade."],
  ["🎯", "Mural de procurados", "Caçadas a monstros de elite com recompensa em experiência."],
  ["🗺️", "Mapas do tesouro", "Mapas raros que apontam para baús enterrados."],
  ["🌙", "Eventos do céu", "Lua de Sangue com XP em dobro e chuvas de meteoros."],
  ["☠️", "Chefes mundiais", "Monstros gigantes que surgem de tempos em tempos e pedem o servidor inteiro."],
];
const ICONE_FEED: Record<string, string> = { entrou: "➜", saiu: "←", morte: "☠", conquista: "🏆", chefe: "☠", invasao: "⚔", forja: "⚒" };

function moedas(c: number) {
  const ouro = Math.floor(c / 100), prata = Math.floor((c % 100) / 10), cobre = c % 10;
  const p: string[] = [];
  if (ouro) p.push(`${ouro.toLocaleString("pt-BR")} ouro`);
  if (prata) p.push(`${prata} prata`);
  if (cobre || !p.length) p.push(`${cobre} cobre`);
  return p.join(", ");
}
function relogio(hora: number) {
  const h = (hora / 1000 + 6) % 24, hh = Math.floor(h), mm = Math.floor((h - hh) * 60);
  return `${String(hh).padStart(2, "0")}:${String(mm).padStart(2, "0")}`;
}

/** Número que sobe até o valor (e anda de novo quando o valor muda). */
function Subindo({ valor }: { valor: number }) {
  const [n, setN] = useState(0);
  useEffect(() => {
    let quadro = 0;
    const de = n, inicio = performance.now(), dur = 900;
    const passo = (t: number) => {
      const f = Math.min(1, (t - inicio) / dur), suave = 1 - Math.pow(1 - f, 3);
      setN(Math.round(de + (valor - de) * suave));
      if (f < 1) quadro = requestAnimationFrame(passo);
    };
    quadro = requestAnimationFrame(passo);
    return () => cancelAnimationFrame(quadro);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [valor]);
  return <>{n.toLocaleString("pt-BR")}</>;
}

/** Quanto falta para a temporada virar: meia-noite do dia 1 do mês seguinte, no horário de Brasília (UTC−3). */
function FimDaTemporada({ mes }: { mes: string }) {
  const [agora, setAgora] = useState<number | null>(null);
  useEffect(() => { setAgora(Date.now()); const t = setInterval(() => setAgora(Date.now()), 1000); return () => clearInterval(t); }, []);
  const [ano, m] = mes.split("-").map(Number);
  if (!agora || !ano || !m) return null;
  const resta = Math.max(0, Date.UTC(ano, m, 1, 3, 0, 0) - agora);
  const partes: [number, string][] = [[Math.floor(resta / 86_400_000), "dias"], [Math.floor(resta / 3_600_000) % 24, "horas"], [Math.floor(resta / 60_000) % 60, "min"], [Math.floor(resta / 1000) % 60, "seg"]];
  return (
    <div className="mb-4">
      <p className="mb-1.5 text-center text-xs uppercase tracking-[0.25em] text-pedra-500">{resta ? "A temporada termina em" : "A temporada terminou"}</p>
      <div className="flex justify-center gap-2">
        {partes.map(([n, r]) => (
          <div key={r} className="w-16 rounded-lg border border-ouro-escuro/50 bg-pedra-950 py-2 text-center">
            <p className="font-titulo text-2xl tabular-nums text-ouro">{String(n).padStart(2, "0")}</p>
            <p className="text-[10px] uppercase tracking-wider text-pedra-500">{r}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

/** Pontos da temporada por feito (os mesmos pesos de aventura/Temporadas.java). */
const PONTOS_TEMPORADA: [string, string, number][] = [["📖", "Capítulo da história", 100], ["🌀", "Portal fechado", 80], ["🎯", "Procurado capturado", 60], ["☠", "Chefe derrotado", 50], ["⚔️", "Invasão repelida", 40], ["💎", "Tesouro encontrado", 30], ["🗼", "Andar novo na Torre", 20], ["🗡️", "Monstro abatido", 1]];
const MEDALHAS = ["🥇", "🥈", "🥉"];

function Cartao({ titulo, sub, children, id }: { titulo: string; sub?: string; children: React.ReactNode; id?: string }) {
  return (
    <section id={id} className="rounded-xl border border-pedra-700 bg-pedra-900 p-5">
      <h3 className="font-titulo text-xl text-ouro">{titulo}</h3>
      {sub && <p className="mb-3 text-sm text-pedra-500">{sub}</p>}
      <div className={sub ? "" : "mt-3"}>{children}</div>
    </section>
  );
}

function Rank({ linhas, valor, vazio, abrir }: { linhas: LinhaRank[]; valor: (n: number) => string; vazio: string; abrir: (n: string) => void }) {
  if (!linhas.length) return <p className="text-sm text-pedra-500">{vazio}</p>;
  const max = Math.max(1, ...linhas.map((l) => l.valor));
  return (
    <ol className="space-y-1.5">
      {linhas.map((l, i) => (
        <li key={`${l.conta}#${l.vaga}`} style={{ animationDelay: `${i * 45}ms` }}
          className={`frx-surge relative overflow-hidden rounded-md px-3 py-2 ${i === 0 ? "border border-ouro-escuro/60 bg-gradient-to-r from-pedra-700/80 to-pedra-800/60" : "bg-pedra-800/60"}`}>
          <span className="flex items-center gap-3">
            <span className={`w-7 shrink-0 text-center ${i < 3 ? "text-xl" : "font-titulo text-lg text-pedra-500"}`}>{MEDALHAS[i] ?? i + 1}</span>
            <Skin nick={l.conta} tam={28} />
            <span className="min-w-0 flex-1">
              <span className="block truncate"><button type="button" onClick={() => abrir(`${l.conta}#${l.vaga}`)} className="font-semibold hover:text-ouro">{l.nick}</button>{l.titulo && <span className="ml-2 text-xs font-normal text-ouro-escuro">«{l.titulo}»</span>}</span>
              <span className="block truncate text-xs text-pedra-500">{l.extra}{!!l.semana && <span className="ml-2 text-green-400">+{l.semana} {l.semana === 1 ? "nível" : "níveis"} na semana</span>}</span>
            </span>
            {!!l.mudou && <span className={`shrink-0 text-xs font-semibold ${l.mudou > 0 ? "text-green-400" : "text-red-400"}`} title={l.mudou > 0 ? "Subiu desde ontem" : "Caiu desde ontem"}>{l.mudou > 0 ? "▲" : "▼"}{Math.abs(l.mudou)}</span>}
            <span className="shrink-0 text-right text-sm text-ouro">{valor(l.valor)}</span>
          </span>
          {/* barra: quanto este valor representa perto do primeiro colocado */}
          <span className="mt-1.5 block h-1 overflow-hidden rounded bg-pedra-950/70">
            <span className={`frx-enche block h-full rounded ${i === 0 ? "bg-ouro" : "bg-ouro-escuro"}`} style={{ width: `${Math.max(3, (l.valor / max) * 100)}%`, animationDelay: `${i * 45 + 150}ms` }} />
          </span>
        </li>
      ))}
    </ol>
  );
}

function Barras({ dados, ordem }: { dados: Record<string, number>; ordem?: string[] }) {
  const chaves = ordem ? ordem.filter((k) => dados[k]) : Object.keys(dados).sort((a, b) => dados[b] - dados[a]);
  const max = Math.max(1, ...chaves.map((k) => dados[k]));
  if (!chaves.length) return <p className="text-sm text-pedra-500">Ninguém por aqui ainda.</p>;
  return (
    <div className="space-y-2">
      {chaves.map((k) => (
        <div key={k} className="flex items-center gap-3 text-sm">
          <span className="w-24 shrink-0 truncate">{k}</span>
          <span className="h-3 flex-1 overflow-hidden rounded bg-pedra-800"><span className="frx-enche block h-full rounded bg-ouro-escuro" style={{ width: `${(dados[k] / max) * 100}%` }} /></span>
          <span className="w-6 shrink-0 text-right text-pedra-500">{dados[k]}</span>
        </div>
      ))}
    </div>
  );
}

function Clas({ clas, abrir }: { clas: DadosPublicos["clas"]; abrir: (n: string) => void }) {
  const [ordem, setOrdem] = useState<"poder" | "riqueza">("poder");
  if (!clas.length) return <p className="rounded-xl border border-pedra-700 bg-pedra-900 p-5 text-sm text-pedra-500">Nenhum clã formado ainda. O primeiro a criar um leva o topo do ranking.</p>;
  const lista = [...clas].sort((a, b) => b[ordem] - a[ordem] || b.poder - a.poder);
  const maior = Math.max(1, ...lista.map((c) => c[ordem]));
  return (
    <>
      <div className="mb-4 flex flex-wrap gap-2">
        {([["poder", "⚔ Mais fortes"], ["riqueza", "💰 Mais ricos"]] as const).map(([k, r]) => (
          <button key={k} type="button" onClick={() => setOrdem(k)}
            className={`rounded-md border px-3 py-1.5 text-sm ${ordem === k ? "border-ouro bg-pedra-700 text-ouro" : "border-pedra-700 hover:border-ouro-escuro"}`}>{r}</button>
        ))}
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        {lista.map((c, i) => (
          <div key={c.nome + c.lider} className="rounded-xl border bg-pedra-900 p-4" style={{ borderColor: `${c.cor}88` }}>
            <div className="flex items-start gap-3">
              <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full font-titulo text-xl" style={{ background: `${c.cor}22`, color: c.cor }}>{i + 1}º</span>
              <div className="min-w-0 flex-1">
                <p className="truncate font-titulo text-2xl" style={{ color: c.cor }}>⚑ {c.nome}</p>
                <p className="text-xs text-pedra-500">Líder: {c.lider} · {c.membros.length} {c.membros.length === 1 ? "membro" : "membros"} · nível médio {c.nivelMedio}</p>
                {c.descricao && <p className="mt-1 text-xs text-pergaminho/70">{c.descricao}</p>}
              </div>
            </div>
            <div className="mt-3 grid grid-cols-3 gap-2 text-center text-xs">
              <div className="rounded-lg border border-pedra-700 bg-pedra-800/60 py-1.5"><p className="font-titulo text-lg text-ouro">{c.poder}</p><p className="text-pedra-500">poder (soma dos níveis)</p></div>
              <div className="rounded-lg border border-pedra-700 bg-pedra-800/60 py-1.5"><p className="font-titulo text-lg text-ouro">{moedas(c.riqueza)}</p><p className="text-pedra-500">riqueza{c.cofre > 0 ? ` (cofre: ${moedas(c.cofre)})` : ""}</p></div>
              <div className="rounded-lg border border-pedra-700 bg-pedra-800/60 py-1.5"><p className="font-titulo text-lg text-ouro">{c.conquistas}</p><p className="text-pedra-500">conquistas</p></div>
            </div>
            <div className="mt-2 h-1.5 overflow-hidden rounded bg-pedra-800"><div className="h-full" style={{ width: `${(c[ordem] / maior) * 100}%`, background: c.cor }} /></div>
            <ul className="mt-3 flex flex-wrap gap-1.5">
              {c.membros.map((m) => (
                <li key={m.nick}>
                  <button type="button" onClick={() => abrir(m.nick)} className="flex items-center gap-1.5 rounded-full border border-pedra-700 bg-pedra-800/60 py-0.5 pl-0.5 pr-2 text-xs hover:border-ouro">
                    <Skin nick={m.nick} tam={18} className="rounded-full" />
                    <span className={m.online ? "text-green-400" : ""}>{m.cargo === "dono" ? "👑 " : m.cargo === "oficial" ? "★ " : ""}{m.nick}</span>
                    <span className="text-pedra-500">Nv {m.nivel}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </>
  );
}

/** Atalhos do cabeçalho: os principais ficam à vista; os outros, na lista "Mais". */
const NAV_PRINCIPAL = [["#aventureiros", "Aventureiros"], ["#rankings", "Rankings"], ["#mural", "Mural"], ["#clas", "Clãs"], ["#mapa", "Mapa"], ["#biblioteca", "Biblioteca"]] as const;
const NAV_MAIS = [["#agora", "Agora no servidor", "🟢"], ["#bestiario", "Bestiário", "🐉"], ["#mundo", "O mundo", "🌍"], ["#mods", "Mods", "🧩"], ["#cronicas", "Crônicas", "📜"], ["#novidades", "Novidades", "📰"], ["#entrar", "Como entrar", "🚪"]] as const;

export function SitePublico({ inicial, mods, mapa, notas, livros, bestiario }: { inicial: DadosPublicos | null; mods: ModPublico[]; mapa: MapaDados; notas: NotaAtualizacao[]; livros: Livro[]; bestiario: CriaturaPublica[] }) {
  const [todasNotas, setTodasNotas] = useState(false);
  const [perfil, setPerfil] = useState<string | null>(null);
  const [verOnline, setVerOnline] = useState(false);
  const [menu, setMenu] = useState(false);   // cabeçalho: painel de atalhos nas telas estreitas
  const [mais, setMais] = useState(false);   // cabeçalho: lista "Mais" nas telas largas
  const abrir = useCallback((n: string) => setPerfil(n), []);
  const fechar = useCallback(() => setPerfil(null), []);
  const [d, setD] = useState(inicial);
  const [busca, setBusca] = useState("");
  const [aba, setAba] = useState<"fortes" | "fracos" | "ricos" | "conquistas" | "mortes">("fortes");

  useEffect(() => {
    let vivo = true;
    const buscar = () => fetch("/api/publico/dados").then((r) => (r.ok ? r.json() : null)).then((j) => { if (vivo && j && !j.erro) setD(j); }).catch(() => {});
    const t = setInterval(buscar, 15_000);
    return () => { vivo = false; clearInterval(t); };
  }, []);

  const modsFiltrados = useMemo(() => {
    const b = busca.trim().toLowerCase();
    return b ? mods.filter((m) => m.nome.toLowerCase().includes(b)) : mods;
  }, [mods, busca]);

  const s = d?.status ?? null;
  const online = !!d?.online;
  // Cena do topo: sai do estado real do servidor (ligado, chefe mundial, clima, dia ou noite)
  const noite = !!s && s.hora >= 12000;
  const cena = !online || !s ? "desligado" : s.chefe?.fase === "ativo" ? "chefe" : s.clima === "tempestade" ? "tempestade" : s.clima === "chuva" ? "chuva" : noite ? "noite" : "dia";
  const jogando = d?.onlineAgora.length ?? 0;
  const gente = jogando === 0 ? "Ninguém na estrada agora: o mundo é todo seu." : jogando === 1 ? "1 aventureiro está no mundo agora." : `${jogando} aventureiros estão no mundo agora.`;
  const momento: [string, string, string] =
    cena === "desligado" ? ["🌑", "O mundo dorme", "O servidor está desligado agora. Veja os horários em Como entrar e volte quando os portões abrirem."]
    : cena === "chefe" ? ["☠", `Chefe mundial: ${s?.chefe?.nome ?? "?"}`, `Um chefe mundial está à solta. ${gente}`]
    : s?.chefe ? ["⚠️", `${s.chefe.nome} se aproxima`, `Um chefe mundial foi avistado e está a caminho. ${gente}`]
    : cena === "tempestade" ? ["⛈️", `Tempestade · dia ${s?.dia}`, `Raios cortam o céu dos reinos. ${gente}`]
    : cena === "chuva" ? ["🌧️", `Chove nos reinos · dia ${s?.dia}`, `A chuva cai sobre as capitais. ${gente}`]
    : cena === "noite" ? ["🌙", `Noite do dia ${s?.dia} · ${relogio(s?.hora ?? 0)}`, `Os monstros saem das sombras. ${gente}`]
    : ["☀️", `Dia ${s?.dia} · ${relogio(s?.hora ?? 0)}`, `O sol brilha sobre os reinos. ${gente}`];
  const abas: [typeof aba, string][] = [["fortes", "Mais fortes"], ["fracos", "Começando agora"], ["ricos", "Mais ricos"], ["conquistas", "Conquistas"], ["mortes", "Mais mortes"]];
  const fmt: Record<typeof aba, (n: number) => string> = {
    fortes: (n) => `Nível ${n}`, fracos: (n) => `Nível ${n}`, ricos: moedas, conquistas: (n) => `${n} 🏆`, mortes: (n) => `${n} ☠`,
  };

  return (
    <div className="relative isolate min-h-screen">
      <Dragao />
      <nav className="sticky top-0 z-30 border-b border-ouro-escuro/30 bg-pedra-950/90 shadow-lg shadow-black/40 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-2.5">
          <a href="#topo" onClick={() => setMenu(false)} className="group flex shrink-0 items-center gap-2.5">
            <span className="grid h-9 w-9 place-items-center rounded-lg border border-ouro-escuro bg-gradient-to-br from-pedra-700 to-pedra-950 text-lg transition group-hover:border-ouro" aria-hidden="true">⚔️</span>
            <span className="leading-none">
              <span className="block font-titulo text-xl text-ouro">FRX-WORLD</span>
              <span className="mt-0.5 block text-[9px] uppercase tracking-[0.28em] text-pedra-500">MMORPG · 1.20.1</span>
            </span>
          </a>

          {/* telas largas: os atalhos mais usados à vista e o resto em "Mais" */}
          <div className="ml-3 hidden flex-1 items-center gap-0.5 lg:flex">
            {NAV_PRINCIPAL.map(([h, r]) => (
              <a key={h} href={h} className="rounded-md px-2.5 py-1.5 text-sm text-pedra-500 transition hover:bg-pedra-800 hover:text-ouro">{r}</a>
            ))}
            <div className="relative" onMouseLeave={() => setMais(false)}>
              <button type="button" onClick={() => setMais((v) => !v)} aria-expanded={mais}
                className={`flex items-center gap-1 rounded-md px-2.5 py-1.5 text-sm transition hover:bg-pedra-800 hover:text-ouro ${mais ? "bg-pedra-800 text-ouro" : "text-pedra-500"}`}>
                Mais <span className={`text-[10px] transition-transform ${mais ? "rotate-180" : ""}`} aria-hidden="true">▼</span>
              </button>
              {mais && (
                <div className="absolute left-0 top-full w-52 pt-2">
                  <div className="rounded-xl border border-pedra-700 bg-pedra-900 p-1.5 shadow-2xl shadow-black/60">
                    {NAV_MAIS.map(([h, r, i]) => (
                      <a key={h} href={h} onClick={() => setMais(false)} className="flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm text-pedra-500 transition hover:bg-pedra-800 hover:text-ouro">
                        <span className="w-5 text-center" aria-hidden="true">{i}</span>{r}
                      </a>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="ml-auto flex shrink-0 items-center gap-2">
            <span className={`hidden items-center gap-2 rounded-full border px-3 py-1.5 text-xs sm:flex ${online ? "border-green-700/70 bg-green-950/40 text-green-400" : "border-pedra-700 text-pedra-500"}`}>
              <span className={`h-2 w-2 rounded-full ${online ? "animate-pulse bg-green-400" : "bg-sangue"}`} />
              {online ? `${d?.onlineAgora.length ?? 0} online` : "Servidor desligado"}
            </span>
            <a href="/conta" className="flex items-center gap-1.5 rounded-full bg-gradient-to-b from-ouro to-ouro-escuro px-3.5 py-1.5 text-xs font-semibold text-pedra-950 shadow shadow-black/40 transition hover:brightness-110">
              <span aria-hidden="true">👤</span> Minha conta
            </a>
            <button type="button" onClick={() => setMenu((v) => !v)} aria-expanded={menu} aria-label="Abrir o menu"
              className="grid h-9 w-9 place-items-center rounded-lg border border-pedra-700 text-lg text-pedra-500 hover:border-ouro hover:text-ouro lg:hidden">{menu ? "✕" : "☰"}</button>
          </div>
        </div>

        {/* telas estreitas: tudo num painel que abre pelo botão */}
        {menu && (
          <div className="border-t border-pedra-800 bg-pedra-950 lg:hidden">
            <div className="mx-auto grid max-w-6xl grid-cols-2 gap-1 px-4 py-3 sm:grid-cols-3">
              {[...NAV_PRINCIPAL.map(([h, r]) => [h, r, ""] as const), ...NAV_MAIS].map(([h, r, i]) => (
                <a key={h} href={h} onClick={() => setMenu(false)} className="flex items-center gap-2 rounded-lg border border-pedra-800 px-3 py-2 text-sm text-pedra-500 hover:border-ouro hover:text-ouro">
                  {i && <span aria-hidden="true">{i}</span>}{r}
                </a>
              ))}
            </div>
          </div>
        )}
      </nav>

      <header id="topo" className={`frx-heroi frx-cena frx-cena-${cena} relative overflow-hidden border-b border-pedra-700`}>
        <Clima key={cena} tipo={cena} />
        <div className="relative mx-auto max-w-6xl px-4 py-14 text-center md:py-20">
          <p className="text-sm uppercase tracking-[0.3em] text-ouro-escuro">Minecraft 1.20.1 · Forge · RPG</p>
          <h1 className="mt-3 font-titulo text-5xl text-ouro drop-shadow-[0_2px_12px_rgba(0,0,0,0.6)] md:text-7xl">FRX-WORLD</h1>
          {/* o que está acontecendo no mundo agora, com os dados que o servidor manda */}
          <p className={`mx-auto mt-5 inline-flex items-center gap-2 rounded-full border px-4 py-1.5 text-sm font-semibold uppercase tracking-[0.18em] ${cena === "chefe" ? "animate-pulse border-red-500 bg-red-950/70 text-red-200" : "border-ouro-escuro/50 bg-pedra-950/60 text-pergaminho"}`}>
            <span aria-hidden="true">{momento[0]}</span>{momento[1]}
          </p>
          <p className="mx-auto mt-4 max-w-2xl text-lg text-pergaminho/80">{momento[2]}</p>
          <a href="#entrar" className="mt-6 inline-block rounded-lg bg-gradient-to-b from-ouro to-ouro-escuro px-7 py-3 font-titulo text-lg uppercase tracking-[0.15em] text-pedra-950 shadow-lg shadow-black/50 transition hover:-translate-y-0.5 hover:brightness-110">Entrar no mundo</a>
          <div className="mx-auto mt-8 grid max-w-3xl grid-cols-2 gap-3 md:grid-cols-4">
            <button type="button" onClick={() => setVerOnline((v) => !v)} aria-expanded={verOnline}
              className={`rounded-xl border bg-pedra-900/80 px-3 py-4 transition hover:-translate-y-0.5 hover:border-ouro ${verOnline ? "border-ouro" : "border-pedra-700"}`}>
              <p className="font-titulo text-3xl text-ouro">{online ? `${d?.onlineAgora.length ?? 0}/${s?.maxJogadores ?? "?"}` : "—"}</p>
              <p className="text-xs text-pedra-500">{online ? "jogando agora" : "servidor desligado"} <span className="text-ouro-escuro">{verOnline ? "▴" : "▾"}</span></p>
            </button>
            {([
              [d?.totais.jogadores ?? 0, "aventureiros", "#aventureiros"],
              [d?.totais.mods ?? mods.length, "mods", "#mods"],
              [d?.totais.chunks ?? 0, "chunks explorados", "#mapa"],
            ] as [number, string, string][]).map(([n, r, h]) => (
              <a key={r} href={h} className="rounded-xl border border-pedra-700 bg-pedra-900/80 px-3 py-4 transition hover:-translate-y-0.5 hover:border-ouro">
                <p className="font-titulo text-3xl text-ouro"><Subindo valor={n} /></p>
                <p className="text-xs text-pedra-500">{r}</p>
              </a>
            ))}
          </div>
          {verOnline && (
            <div className="mx-auto mt-3 max-w-3xl rounded-xl border border-ouro-escuro bg-pedra-900/95 p-4 text-left">
              <p className="mb-3 text-xs uppercase tracking-wider text-pedra-500">{online ? "Quem está jogando agora" : "Servidor desligado"}</p>
              {d?.onlineAgora.length ? (
                <ul className="grid gap-2 sm:grid-cols-2">
                  {d.onlineAgora.map((j) => (
                    <li key={j.nick}>
                      <button type="button" onClick={() => abrir(j.nick)} className="flex w-full items-center gap-3 rounded-lg border border-pedra-700 bg-pedra-800/60 px-3 py-2 text-left transition hover:border-ouro">
                        <Skin nick={j.nick} tam={36} />
                        <span className="min-w-0 flex-1">
                          <span className="flex items-center gap-2 font-semibold"><span className="h-2 w-2 shrink-0 animate-pulse rounded-full bg-green-400" /><span className="truncate">{j.nick}</span></span>
                          <span className="block truncate text-xs text-pedra-500">Nv {j.nivel} · {ICONE_CLASSE[j.classe] ?? ""} {j.classe}{j.reino ? ` · em ${j.reino}` : ""}</span>
                        </span>
                        <span className="shrink-0 text-xs text-ouro-escuro">ver ficha ›</span>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : <p className="text-sm text-pedra-500">{online ? "Ninguém no mundo neste momento." : "Quando o servidor ligar, quem entrar aparece aqui."}</p>}
            </div>
          )}
        </div>
      </header>

      <main className="mx-auto max-w-6xl space-y-10 px-4 py-10">
        {!d && <p className="rounded-xl border border-sangue bg-pedra-900 p-4 text-sm">Não deu para carregar os dados agora. A página tenta de novo sozinha em alguns segundos.</p>}

        <div id="agora" className="grid scroll-mt-20 gap-5 md:grid-cols-3">
          <Cartao titulo="Servidor" sub={online ? "Atualiza sozinho a cada 15 segundos." : "Quando ligar, o status aparece aqui."}>
            {s ? (
              <dl className="space-y-2 text-sm">
                <div className="flex justify-between"><dt className="text-pedra-500">Desempenho</dt><dd>{s.tps >= 18 ? "Liso" : s.tps >= 14 ? "Um pouco pesado" : "Lento"} · {s.tps.toFixed(1)} TPS</dd></div>
                <div className="flex justify-between"><dt className="text-pedra-500">No mundo</dt><dd>Dia {s.dia} · {relogio(s.hora)} {s.clima === "tempestade" ? "⛈️" : s.clima === "chuva" ? "🌧️" : s.hora < 12000 ? "☀️" : "🌙"}</dd></div>
                <div className="flex justify-between"><dt className="text-pedra-500">Versão do FRX</dt><dd>{s.versaoMod}</dd></div>
                <div className="flex justify-between"><dt className="text-pedra-500">Tamanho do mundo</dt><dd>{d?.totais.mundoMb.toLocaleString("pt-BR")} MB</dd></div>
                {s.chefe && <p className="rounded-md border border-sangue bg-sangue/20 px-3 py-2">☠ Chefe mundial {s.chefe.fase === "ativo" ? "em campo" : "a caminho"}: <b>{s.chefe.nome}</b></p>}
              </dl>
            ) : <p className="text-sm text-pedra-500">O servidor está desligado ou reiniciando.</p>}
          </Cartao>
          <Cartao titulo="Jogando agora" sub={online ? `${d?.onlineAgora.length ?? 0} aventureiro(s) no mundo` : undefined}>
            {d?.onlineAgora.length ? (
              <ul className="space-y-1.5">
                {d.onlineAgora.map((j) => (
                  <li key={j.nick} className="flex items-center justify-between gap-3 rounded-md bg-pedra-800/60 px-3 py-2 text-sm">
                    <span className="flex min-w-0 items-center gap-2"><Skin nick={j.nick} tam={24} /><Nick nick={j.nick} abrir={abrir} /></span>
                    <span className="shrink-0 text-xs text-pedra-500">Nv {j.nivel} · {ICONE_CLASSE[j.classe] ?? ""} {j.classe}{j.reino ? ` · ${j.reino}` : ""}</span>
                  </li>
                ))}
              </ul>
            ) : <p className="text-sm text-pedra-500">Ninguém no mundo neste momento.</p>}
          </Cartao>
          <Cartao titulo="⚡ Acontecendo" sub="O que o mundo viveu por último; atualiza sozinho">
            {(() => {
              // junta o que o painel guardou (crônicas) com o movimento ao vivo do servidor (entradas, saídas, mortes)
              const hm = (t: number) => new Date(t).toLocaleTimeString("pt-BR", { timeZone: "America/Sao_Paulo", hour: "2-digit", minute: "2-digit" });
              const hoje = new Date().toDateString();
              const guardadas = (d?.cronicas ?? []).slice(0, 25).map((c) => ({
                hora: new Date(c.quando).toDateString() === hoje ? hm(c.quando) : new Date(c.quando).toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo", day: "2-digit", month: "2-digit" }),
                icone: c.icone || "•", texto: c.nome ? `${c.nome} ${c.texto}` : c.texto, destaque: c.destaque,
              }));
              const aoVivo = (d?.feed ?? []).slice(0, 8).map((e) => ({ hora: e.hora, icone: ICONE_FEED[e.tipo] ?? "•", texto: e.texto, destaque: false }));
              const tudo = [...aoVivo, ...guardadas];
              return tudo.length ? (
                <ul className="max-h-72 space-y-1.5 overflow-y-auto pr-1 text-sm">
                  {tudo.map((e, i) => (
                    <li key={`${i}-${e.hora}-${e.texto}`} style={{ animationDelay: `${Math.min(i, 10) * 40}ms` }} className={`frx-surge flex gap-2 rounded px-1.5 py-0.5 ${e.destaque ? "bg-ouro/10 text-ouro" : ""}`}>
                      <span className="w-11 shrink-0 font-mono text-xs leading-5 text-pedra-500">{e.hora}</span><span className="shrink-0">{e.icone}</span><span className="min-w-0 break-words">{e.texto}</span>
                    </li>
                  ))}
                </ul>
              ) : <p className="text-sm text-pedra-500">Tudo calmo por enquanto.</p>;
            })()}
          </Cartao>
        </div>

        <div id="aventureiros" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Aventureiros</h2>
          <p className="mb-4 text-sm text-pedra-500">Clique em alguém para ver a ficha: skin, nível, Guilda e habilidades.</p>
          <Galeria lista={d?.aventureiros ?? []} abrir={abrir} />
        </div>

        {(d?.cronicas ?? []).some((c) => c.destaque) && (
          <div id="cronicas" className="scroll-mt-20">
            <h2 className="font-titulo text-3xl">📰 Crônicas do FRX</h2>
            <p className="mb-4 text-sm text-pedra-500">Os grandes feitos do servidor, escritos na hora em que aconteceram. Ficam guardados para sempre.</p>
            <div className="grid gap-3 md:grid-cols-2">
              {(d?.cronicas ?? []).filter((c) => c.destaque).slice(0, 8).map((c, i) => (
                <article key={`${c.quando}-${i}`} style={{ animationDelay: `${i * 60}ms` }} className="frx-surge flex gap-3 rounded-xl border border-pedra-700 bg-gradient-to-br from-pedra-800 to-pedra-900 p-4">
                  <span className="grid h-12 w-12 shrink-0 place-items-center rounded-lg border border-ouro-escuro/50 bg-pedra-950 text-2xl" aria-hidden="true">{c.icone || "📜"}</span>
                  <div className="min-w-0">
                    <p className="text-xs uppercase tracking-wider text-pedra-500">{new Date(c.quando).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo", day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" })}</p>
                    <p className="font-titulo text-lg leading-snug text-ouro">
                      {c.nome ? <><button type="button" onClick={() => abrir(c.conta ? `${c.conta}#${c.vaga}` : c.nome)} className="hover:underline">{c.nome}</button> {c.texto}</> : c.texto}
                    </p>
                  </div>
                </article>
              ))}
            </div>
          </div>
        )}

        <div id="mural" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Mural dos Primeiros</h2>
          <p className="mb-4 text-sm text-pedra-500">O primeiro aventureiro do servidor a alcançar cada marco fica gravado aqui para sempre. Os que estão em branco ainda esperam um dono.</p>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {(d?.mural ?? []).map((m) => {
              const dono = m.feito && m.nome;
              return (
                <div key={m.chave} className={`flex items-center gap-3 rounded-xl border px-4 py-3 ${dono ? "border-ouro-escuro bg-gradient-to-r from-pedra-800 to-pedra-900" : "border-dashed border-pedra-700 bg-pedra-900/60"}`}>
                  <span className={`text-3xl ${dono ? "" : "opacity-30 grayscale"}`} aria-hidden="true">{m.icone}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-xs uppercase tracking-wider text-pedra-500">{m.grupo}</span>
                    <span className={`block truncate font-semibold ${dono ? "text-ouro" : "text-pedra-500"}`}>{m.titulo}</span>
                    {dono ? (
                      <span className="mt-1 flex items-center gap-2 text-sm">
                        {m.conta && <Skin nick={m.conta} tam={20} />}
                        <button type="button" onClick={() => abrir(m.conta ? `${m.conta}#${m.vaga}` : m.nome)} className="truncate font-semibold hover:text-ouro">{m.nome}</button>
                        {m.quando && <span className="shrink-0 text-xs text-pedra-500">{new Date(m.quando).toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo" })}</span>}
                      </span>
                    ) : (
                      <span className="mt-1 block text-sm text-pedra-500">{m.feito ? "Alcançado antes de o mural existir" : "Ainda ninguém"}</span>
                    )}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        <div id="rankings" className="scroll-mt-20">
          <h2 className="mb-4 font-titulo text-3xl">Rankings</h2>
          <Podio fortes={d?.rankings.fortes ?? []} abrir={abrir} />
          <div className="grid gap-5 lg:grid-cols-3">
            <div className="rounded-xl border border-pedra-700 bg-pedra-900 p-5 lg:col-span-2">
              <div className="mb-4 flex flex-wrap gap-2">
                {abas.map(([k, r]) => (
                  <button key={k} type="button" onClick={() => setAba(k)}
                    className={`rounded-md border px-3 py-1.5 text-sm ${aba === k ? "border-ouro bg-pedra-700 text-ouro" : "border-pedra-700 hover:border-ouro-escuro"}`}>{r}</button>
                ))}
              </div>
              <Rank linhas={d?.rankings[aba] ?? []} valor={fmt[aba]} vazio="Ainda não há ninguém neste ranking." abrir={abrir} />
              {aba === "fracos" && <p className="mt-3 text-xs text-pedra-500">Todo herói começou no nível 1. Dê uma força para quem está chegando.</p>}
            </div>
            <div className="space-y-5">
              <Cartao titulo="Guilda de Aventureiros" sub="Quantos em cada rank"><Barras dados={Object.fromEntries(Object.entries(d?.guilda ?? {}).map(([k, v]) => [`Rank ${k}`, v]))} ordem={[...RANKS].reverse().map((r) => `Rank ${r}`)} /></Cartao>
              <Cartao titulo="Classes" sub="O que o pessoal escolheu"><Barras dados={d?.classes ?? {}} /></Cartao>
            </div>
          </div>
          <div className="mt-5 grid gap-5 md:grid-cols-2">
            <Cartao titulo="🗼 Torre Infinita" sub="Quem chegou mais alto nos 100 andares">
              {d?.torre.length ? (
                <ol className="space-y-1.5">
                  {d.torre.map((t, i) => (
                    <li key={t.nome} className="flex items-center gap-3 rounded-md bg-pedra-800/60 px-3 py-2 text-sm">
                      <span className={`w-6 text-center font-titulo text-lg ${i === 0 ? "text-ouro" : "text-pedra-500"}`}>{i + 1}</span>
                      <Skin nick={t.nome} tam={24} />
                      <span className="min-w-0 flex-1"><Nick nick={t.nome} abrir={abrir} /></span>
                      <span className="text-pedra-500">{t.abates} abates</span>
                      <span className="w-20 text-right text-ouro">Andar {t.recorde}</span>
                    </li>
                  ))}
                </ol>
              ) : <p className="text-sm text-pedra-500">Ninguém entrou na Torre ainda.</p>}
            </Cartao>
            <Cartao titulo="🏆 Temporada" sub={d?.temporada ? `Placar de ${d.temporada.mes.split("-").reverse().join("/")} · zera todo mês, e os três primeiros levam prêmio` : "Placar do mês"}>
              {d?.temporada && <FimDaTemporada mes={d.temporada.mes} />}
              {d?.temporada?.linhas.length ? (
                <ol className="space-y-1.5">
                  {d.temporada.linhas.map((t, i) => (
                    <li key={t.nome} style={{ animationDelay: `${i * 45}ms` }}
                      className={`frx-surge flex items-center gap-3 rounded-md px-3 py-2 text-sm ${i === 0 ? "border border-ouro-escuro/60 bg-gradient-to-r from-pedra-700/80 to-pedra-800/60" : "bg-pedra-800/60"}`}>
                      <span className={`w-7 text-center ${i < 3 ? "text-xl" : "font-titulo text-lg text-pedra-500"}`}>{MEDALHAS[i] ?? i + 1}</span>
                      <Skin nick={t.nome} tam={24} />
                      <span className="min-w-0 flex-1"><Nick nick={t.nome} abrir={abrir} /></span>
                      <span className="hidden text-xs text-pedra-500 sm:inline">{t.abates} abates · cap. {t.historia}</span>
                      <span className="w-20 text-right text-ouro">{t.pts.toLocaleString("pt-BR")} pts</span>
                    </li>
                  ))}
                </ol>
              ) : <p className="text-sm text-pedra-500">A temporada ainda não tem pontos.</p>}
              <details className="mt-4 rounded-lg border border-pedra-700 bg-pedra-950/50 px-3 py-2">
                <summary className="cursor-pointer text-sm text-ouro">Como ganhar pontos</summary>
                <ul className="mt-2 grid gap-1 text-sm sm:grid-cols-2">
                  {PONTOS_TEMPORADA.map(([i, r, n]) => (
                    <li key={r} className="flex items-center justify-between gap-2 rounded bg-pedra-800/60 px-2 py-1"><span><span aria-hidden="true">{i}</span> {r}</span><span className="text-ouro">+{n}</span></li>
                  ))}
                </ul>
              </details>
            </Cartao>
          </div>
          <div className="mt-5 grid grid-cols-2 gap-3 md:grid-cols-4">
            {([
              [String(d?.totais.nivelMedio ?? 0), "nível médio"],
              [(d?.totais.conquistas ?? 0).toLocaleString("pt-BR"), "conquistas somadas"],
              [(d?.totais.mortes ?? 0).toLocaleString("pt-BR"), "mortes no total"],
              [String(d?.reinos.length ?? 0), "reinos no mundo"],
            ] as [string, string][]).map(([n, r]) => (
              <div key={r} className="rounded-xl border border-pedra-700 bg-pedra-900 px-3 py-4 text-center">
                <p className="font-titulo text-2xl text-ouro">{n}</p><p className="text-xs text-pedra-500">{r}</p>
              </div>
            ))}
          </div>
        </div>

        <div id="clas" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Clãs</h2>
          <p className="mb-4 text-sm text-pedra-500">Grupos fixos de aventureiros. No jogo, procure o Mestre dos Clãs numa capital para fundar o seu, pedir entrada ou usar o cofre. Dá para participar de até 3 clãs.</p>
          <Clas clas={d?.clas ?? []} abrir={abrir} />
        </div>

        <div id="mapa" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Mapa do mundo</h2>
          <p className="mb-4 text-sm text-pedra-500">O que já foi explorado até agora. O resto, só indo lá para ver.</p>
          <MapaPublico mapa={mapa} />
        </div>

        <div id="bestiario" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Bestiário</h2>
          <p className="mb-4 text-sm text-pedra-500">Os chefes e monstros próprios do servidor. Cada um só revela o nome, a história e os golpes depois que alguém o derrota pela primeira vez.</p>
          <Bestiario criaturas={bestiario} />
        </div>

        <div id="biblioteca" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Biblioteca</h2>
          <p className="mb-4 text-sm text-pedra-500">Tudo sobre como o mundo funciona: ranks, missões, economia, a Torre e um livro para cada classe. Clique num livro para abrir e folhear.</p>
          <Biblioteca livros={livros} />
        </div>

        <div id="mundo" className="scroll-mt-20">
          <h2 className="mb-4 font-titulo text-3xl">O que tem no mundo</h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {SISTEMAS.map(([ic, t, tx]) => (
              <div key={t} className="rounded-xl border border-pedra-700 bg-pedra-900 p-4">
                <p className="font-titulo text-lg text-ouro"><span className="mr-2">{ic}</span>{t}</p>
                <p className="mt-1 text-sm text-pergaminho/75">{tx}</p>
              </div>
            ))}
          </div>
          {!!d?.reinos.length && <p className="mt-4 text-sm text-pedra-500">Reinos: {d.reinos.join(" · ")}</p>}
        </div>

        <div id="mods" className="scroll-mt-20">
          <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 className="font-titulo text-3xl">Mods</h2>
              <p className="text-sm text-pedra-500">{mods.length} mods no pack, mais o FRX-WORLD, feito só para este servidor. O launcher instala tudo sozinho.</p>
            </div>
            <input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Procurar mod..." aria-label="Procurar mod"
              className="w-full rounded-md border border-pedra-700 bg-pedra-900 px-3 py-2 text-sm outline-none focus:border-ouro sm:w-64" />
          </div>
          <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {modsFiltrados.map((m) => (
              <li key={m.nome} className="flex items-center justify-between gap-2 rounded-md border border-pedra-700 bg-pedra-900 px-3 py-2 text-sm">
                {m.link ? <a href={m.link} target="_blank" rel="noreferrer" className="truncate hover:text-ouro">{m.nome} ↗</a> : <span className="truncate">{m.nome}</span>}
                {m.lado === "client" && <span className="shrink-0 rounded bg-pedra-800 px-1.5 py-0.5 text-[10px] uppercase text-pedra-500">visual</span>}
              </li>
            ))}
          </ul>
          {!modsFiltrados.length && <p className="text-sm text-pedra-500">Nenhum mod com esse nome.</p>}
        </div>

        <div id="novidades" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Notas de atualização</h2>
          <p className="mb-4 text-sm text-pedra-500">O que mudou em cada versão, da mais nova para a mais antiga.</p>
          <ol className="space-y-4 border-l border-pedra-700 pl-5">
            {(todasNotas ? notas : notas.slice(0, 3)).map((n, i) => (
              <li key={`${n.versao}-${n.titulo}`} className="relative rounded-xl border border-pedra-700 bg-pedra-900 p-4">
                <span className={`absolute -left-[27px] top-5 h-3 w-3 rounded-full ${i === 0 ? "bg-ouro" : "bg-pedra-500"}`} />
                <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                  <span className="rounded bg-pedra-800 px-2 py-0.5 font-mono text-xs text-ouro">v{n.versao}</span>
                  <span className="text-xs text-pedra-500">{n.data}{n.hora ? ` às ${n.hora}` : ""}</span>
                  {i === 0 && <span className="rounded bg-ouro-escuro/30 px-2 py-0.5 text-[10px] uppercase tracking-wider text-ouro">mais recente</span>}
                </p>
                <h3 className="mt-1 font-titulo text-xl text-ouro">{n.titulo}</h3>
                <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-pergaminho/85">{n.itens.map((t) => <li key={t}>{t}</li>)}</ul>
              </li>
            ))}
          </ol>
          {notas.length > 3 && (
            <button type="button" onClick={() => setTodasNotas((v) => !v)} className="mt-4 rounded-md border border-pedra-700 px-4 py-2 text-sm hover:border-ouro">
              {todasNotas ? "Mostrar só as últimas" : `Ver todas as ${notas.length} atualizações`}
            </button>
          )}
        </div>

        <div id="entrar" className="scroll-mt-20 rounded-xl border border-ouro-escuro bg-pedra-900 p-6">
          <h2 className="font-titulo text-3xl text-ouro">Como entrar</h2>
          <ol className="mt-3 list-decimal space-y-2 pl-5 text-sm text-pergaminho/85">
            <li>O FRX-WORLD é um servidor privado, entre amigos. Peça um convite para quem já joga ou para o administrador.</li>
            <li>Com o convite você recebe o launcher, que baixa o Minecraft 1.20.1, o Forge e todos os mods sozinho.</li>
            <li>Abra o launcher, escolha seu nick e entre. Na primeira vez o jogo pede para escolher raça e classe.</li>
          </ol>
        </div>
      </main>

      {perfil && <Perfil nick={perfil} fechar={fechar} />}

      <footer className="border-t border-pedra-700 px-4 py-6 text-center text-xs text-pedra-500">
        FRX-WORLD · servidor não oficial, sem vínculo com a Mojang ou a Microsoft · <a href="/admin" className="hover:text-ouro">Administração</a>
      </footer>
    </div>
  );
}
