"use client";

import { useState } from "react";
import { Clima } from "./clima";
import { ICONE_CLASSE, Skin } from "./jogadores";
import { relogio, Subindo } from "./blocos";
import { useSite } from "./casca";
import type { Linha, ResumoExpedicao } from "@/lib/expedicoes";
import { Contagem, Rosa } from "./expedicoes";
import { Vagalumes } from "./efeitos";

/** Topo da página inicial: a cena muda com o estado real do servidor (ligado, chefe, clima, dia ou noite). */
export function Heroi() {
  const { d, abrir } = useSite();
  const [verOnline, setVerOnline] = useState(false);
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
  void noite;
  return (
      <header id="topo" className={`frx-heroi frx-cena frx-cena-${cena} relative overflow-hidden border-b border-pedra-700`}>
        <Clima key={cena} tipo={cena} />
        <Vagalumes quantos={10} />
        <div className="relative mx-auto max-w-6xl px-4 py-14 text-center md:py-20">
          <p className="text-sm uppercase tracking-[0.3em] text-ouro-escuro">Minecraft 1.20.1 · Forge · RPG</p>
          <h1 className="frx-brilho mt-3 font-titulo text-5xl drop-shadow-[0_2px_12px_rgba(0,0,0,0.6)] md:text-7xl">FRX-WORLD</h1>
          <p className="mx-auto mt-2 max-w-2xl text-base text-pergaminho/85 md:text-lg">Servidor brasileiro de <strong className="text-ouro">Minecraft MMORPG e RPG</strong>: modpack Forge 1.20.1 com classes, raças, Guilda, reinos e chefes.</p>
          {/* o que está acontecendo no mundo agora, com os dados que o servidor manda */}
          <p className={`mx-auto mt-5 inline-flex items-center gap-2 rounded-full border px-4 py-1.5 text-sm font-semibold uppercase tracking-[0.18em] ${cena === "chefe" ? "animate-pulse border-red-500 bg-red-950/70 text-red-200" : "border-ouro-escuro/50 bg-pedra-950/60 text-pergaminho"}`}>
            <span aria-hidden="true">{momento[0]}</span>{momento[1]}
          </p>
          <p className="mx-auto mt-4 max-w-2xl text-lg text-pergaminho/80">{momento[2]}</p>
          <div className="mt-6 flex flex-wrap justify-center gap-3">
            <a href="/entrar" className="inline-block rounded-lg bg-gradient-to-b from-ouro to-ouro-escuro px-7 py-3 font-titulo text-lg uppercase tracking-[0.15em] text-pedra-950 shadow-lg shadow-black/50 transition hover:-translate-y-0.5 hover:brightness-110">Entrar no mundo</a>
            <a href="/expedicoes" className="inline-flex items-center gap-2 rounded-lg border border-emerald-500/60 bg-emerald-950/50 px-6 py-3 font-titulo text-lg uppercase tracking-[0.15em] text-emerald-200 shadow-lg shadow-black/50 transition hover:-translate-y-0.5 hover:border-emerald-300">
              <span className="h-2 w-2 animate-pulse rounded-full bg-emerald-400" />Expedição ao vivo
            </a>
          </div>
          <div className="mx-auto mt-8 grid max-w-3xl grid-cols-2 gap-3 md:grid-cols-3">
            <button type="button" onClick={() => setVerOnline((v) => !v)} aria-expanded={verOnline}
              className={`rounded-xl border bg-pedra-900/80 px-3 py-4 transition hover:-translate-y-0.5 hover:border-ouro ${verOnline ? "border-ouro" : "border-pedra-700"}`}>
              <p className="font-titulo text-3xl text-ouro">{online ? `${d?.onlineAgora.length ?? 0}/${s?.maxJogadores ?? "?"}` : "—"}</p>
              <p className="text-xs text-pedra-500">{online ? "jogando agora" : "servidor desligado"} <span className="text-ouro-escuro">{verOnline ? "▴" : "▾"}</span></p>
            </button>
            {([
              [d?.totais.jogadores ?? 0, "aventureiros", "/aventureiros"],
              [d?.totais.mods ?? 0, "mods", "/mods"],
              [d?.totais.chunks ?? 0, "chunks explorados", "/mundo#mapa"],
            ] as [number, string, string][]).map(([n, r, h]) => (
              <a key={r} href={h} className="rounded-xl border border-pedra-700 bg-pedra-900/80 px-3 py-4 transition hover:-translate-y-0.5 hover:border-ouro">
                <p className="font-titulo text-3xl text-ouro"><Subindo valor={n} /></p>
                <p className="text-xs text-pedra-500">{r}</p>
              </a>
            ))}
            {([
              ["memória do servidor", online && s && s.memMaxMb > 0 ? Math.round((s.memUsadaMb / s.memMaxMb) * 100) : null,
                online && s && s.memMaxMb > 0 ? `${(s.memUsadaMb / 1024).toFixed(1).replace(".", ",")} de ${(s.memMaxMb / 1024).toFixed(1).replace(".", ",")} GB` : ""],
              ["processador do servidor", online && s ? s.cpu : null, ""],
            ] as [string, number | null, string][]).map(([r, pct, detalhe]) => (
              <div key={r} title={detalhe || undefined} className="rounded-xl border border-pedra-700 bg-pedra-900/80 px-3 py-4">
                <p className={`font-titulo text-3xl ${pct !== null && pct >= 90 ? "text-red-400" : "text-ouro"}`}>{pct === null ? "—" : `${pct}%`}</p>
                <div className="mx-auto mt-1 h-1 w-3/4 overflow-hidden rounded-full bg-pedra-800">
                  <div className={`h-full rounded-full transition-all duration-700 ${pct !== null && pct >= 90 ? "bg-red-400" : "bg-ouro"}`} style={{ width: `${pct ?? 0}%` }} />
                </div>
                <p className="mt-1 text-xs text-pedra-500">{r}{detalhe ? ` · ${detalhe}` : ""}</p>
              </div>
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
  );
}

// ------------------------------------------------------------------ blocos da página inicial


type Atual = (ResumoExpedicao & { ranking: Linha[] }) | null;
const MEDALHA = ["🥇", "🥈", "🥉"];

/** Faixa da expedição ao vivo: o destaque da página inicial. */
export function ExpedicaoAoVivo({ atual, agora }: { atual: Atual; agora: number }) {
  return (
    <section className="frx-exp relative overflow-hidden rounded-2xl border border-emerald-700/60 p-6 shadow-2xl shadow-black/50 sm:p-8">
      <div className="frx-exp-mapa" aria-hidden="true" />
      <Rosa className="absolute -right-12 -top-12 h-56 w-56 text-emerald-300/15 sm:h-80 sm:w-80" />
      <Vagalumes quantos={9} verde />
      <div className="relative grid gap-6 lg:grid-cols-[1.25fr_1fr] lg:items-center">
        <div>
          <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.3em] text-emerald-300">
            <span className={`h-2 w-2 rounded-full ${atual ? "animate-pulse bg-emerald-400" : "bg-pedra-500"}`} />{atual ? "Ao vivo agora" : "Em breve"}
          </p>
          <h2 className="mt-2 font-titulo text-4xl text-pergaminho sm:text-5xl">Terras Inexploradas</h2>
          <p className="mt-3 max-w-xl text-pergaminho/80">A cada 2 dias, às 5h, nasce do zero uma área de 2000 × 2000 blocos que ninguém nunca viu, cheia de estruturas, masmorras e tesouros. Quando o tempo acaba, tudo some e outra começa.</p>
          <ul className="mt-4 flex flex-wrap gap-2 text-xs">
            {["🌀 Pórtico no mundo principal", "🎖️ Rank D ou maior", "💀 Morreu? Volta com tudo", "🏆 Ranking e MVP"].map((t) => (
              <li key={t} className="rounded-full border border-emerald-800/80 bg-pedra-950/60 px-3 py-1 text-pergaminho/85">{t}</li>
            ))}
          </ul>
          <div className="mt-6 flex flex-wrap gap-3">
            <a href="/expedicoes" className="rounded-lg bg-gradient-to-b from-emerald-400 to-emerald-700 px-5 py-2.5 font-titulo uppercase tracking-[0.12em] text-pedra-950 shadow-lg shadow-black/40 transition hover:-translate-y-0.5 hover:brightness-110">Ver a expedição</a>
            <a href="/expedicoes#como-funciona" className="rounded-lg border border-emerald-700 px-5 py-2.5 text-sm text-emerald-200 transition hover:border-emerald-300">Como funciona</a>
          </div>
        </div>
        <div className="rounded-xl border border-emerald-800/70 bg-pedra-950/75 p-5 text-center backdrop-blur-sm">
          {atual ? (
            <>
              <p className="font-titulo text-3xl text-emerald-200">Expedição #{atual.numero}</p>
              <p className="mt-3 text-[11px] uppercase tracking-[0.3em] text-pedra-500">Uma área nova abre em</p>
              <Contagem alvo={atual.proxima} servidor={agora} className="frx-pulsa font-titulo text-4xl tabular-nums tracking-wider text-ouro sm:text-5xl" />
              <div className="mt-4 grid grid-cols-3 gap-2">
                {([[atual.jogadores, "exploradores"], [atual.estruturas, "estruturas"], [atual.chefes, "chefes"]] as [number, string][]).map(([n, r]) => (
                  <div key={r} className="rounded-lg border border-pedra-700 bg-pedra-900/80 py-2"><p className="font-titulo text-xl text-ouro">{n}</p><p className="text-[10px] uppercase tracking-wider text-pedra-500">{r}</p></div>
                ))}
              </div>
              {atual.ranking.length > 0 && (
                <ol className="mt-4 space-y-1 text-left text-sm">
                  {atual.ranking.slice(0, 3).map((l, i) => (
                    <li key={l.nick} className="flex items-center gap-2 rounded-md bg-pedra-900/80 px-2.5 py-1.5">
                      <span className="w-6 text-center">{MEDALHA[i]}</span><Skin nick={l.nick} tam={20} /><span className="min-w-0 flex-1 truncate">{l.nick}</span>
                      <span className="text-ouro">{l.pontos.toLocaleString("pt-BR")} pts</span>
                    </li>
                  ))}
                </ol>
              )}
              <p className="mt-3 text-xs text-pedra-500">{atual.agora.length ? <>Lá agora: <span className="text-emerald-300">{atual.agora.join(", ")}</span></> : "Ninguém explorando neste momento. A área é sua."}</p>
            </>
          ) : <p className="py-10 text-pedra-500">As Terras Inexploradas ainda não foram abertas.</p>}
        </div>
      </div>
    </section>
  );
}

const PILARES: [string, string, string][] = [
  ["⚔️", "13 classes e várias raças", "Guerreiro, Mago, Samurai, Cartomante e outras, cada uma com habilidades próprias, ultimate e um Despertar."],
  ["🛡️", "Guilda do F ao S", "Missões, ranks e recompensas. O rank libera equipamentos, portais mais perigosos e as Terras Inexploradas."],
  ["🗼", "Torre Infinita", "100 andares temáticos com chefes, andares seguros a cada 10 e um ranking de quem chegou mais alto."],
  ["👑", "Reinos vivos", "Capitais com moradores, lojas, reputação, invasões e chefes mundiais que pedem o servidor inteiro."],
];

/** O que o servidor oferece, em quatro pilares. */
export function Pilares() {
  return (
    <section>
      <div className="mb-5 text-center">
        <p className="text-xs uppercase tracking-[0.3em] text-ouro-escuro">O que te espera</p>
        <h2 className="mt-1 font-titulo text-3xl md:text-4xl">Um MMORPG inteiro dentro do Minecraft</h2>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {PILARES.map(([i, t, x]) => (
          <a key={t} href="/mundo" className="frx-card group rounded-xl border border-pedra-700 bg-gradient-to-b from-pedra-800 to-pedra-900 p-5 transition hover:-translate-y-1 hover:border-ouro-escuro">
            <span className="grid h-12 w-12 place-items-center rounded-lg border border-ouro-escuro/50 bg-pedra-950 text-2xl transition group-hover:-translate-y-1 group-hover:rotate-6 group-hover:border-ouro" aria-hidden="true">{i}</span>
            <p className="mt-3 font-titulo text-xl text-ouro">{t}</p>
            <p className="mt-1 text-sm text-pergaminho/75">{x}</p>
          </a>
        ))}
      </div>
      <p className="mt-4 text-center"><a href="/mundo" className="text-sm text-ouro-escuro hover:text-ouro">Ver tudo o que tem no mundo →</a></p>
    </section>
  );
}

/** Atalhos para as outras páginas, cada um com um número ao vivo. */
export function Explorar({ ultima, livros }: { ultima: { versao: string; titulo: string } | null; livros: number }) {
  const { d } = useSite();
  const lider = d?.rankings.fortes[0];
  const itens: [string, string, string, string, string][] = [
    ["/rankings", "🏆", "Rankings", "Mais fortes, temporada, Torre, pesca, corridas, X1 e clãs.", lider ? `1º lugar: ${lider.nick}` : "Seja o primeiro"],
    ["/aventureiros", "🛡️", "Aventureiros", "Fichas de todos, crônicas, Mural dos Primeiros e armas únicas.", `${(d?.totais.jogadores ?? 0).toLocaleString("pt-BR")} aventureiros`],
    ["/mundo", "🌍", "O Mundo", "Mapa explorado, bestiário dos chefes e os sistemas do servidor.", `${(d?.totais.chunks ?? 0).toLocaleString("pt-BR")} chunks explorados`],
    ["/biblioteca", "📚", "Biblioteca", "Como tudo funciona: ranks, classes, economia, Torre e mais.", `${livros} livros na estante`],
    ["/novidades", "📰", "Novidades", "Tudo o que mudou em cada atualização do servidor.", ultima ? `v${ultima.versao} · ${ultima.titulo}` : ""],
    ["/mods", "🧩", "Mods", "A lista completa do modpack. O launcher instala tudo sozinho.", `${d?.totais.mods ?? 0} mods + FRX-WORLD`],
  ];
  return (
    <section>
      <div className="mb-5 text-center">
        <p className="text-xs uppercase tracking-[0.3em] text-ouro-escuro">Explore</p>
        <h2 className="mt-1 font-titulo text-3xl md:text-4xl">Tudo do FRX-WORLD</h2>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {itens.map(([h, i, t, x, n]) => (
          <a key={h} href={h} className="frx-card group flex gap-4 rounded-xl border border-pedra-700 bg-pedra-900 p-5 transition hover:-translate-y-0.5 hover:border-ouro">
            <span className="grid h-14 w-14 shrink-0 place-items-center rounded-xl border border-pedra-700 bg-pedra-950 text-3xl transition group-hover:scale-110 group-hover:-rotate-6 group-hover:border-ouro-escuro" aria-hidden="true">{i}</span>
            <span className="min-w-0">
              <span className="flex items-center gap-2 font-titulo text-xl text-ouro">{t}<span className="text-sm text-ouro-escuro transition group-hover:translate-x-1">→</span></span>
              <span className="mt-0.5 block text-sm text-pergaminho/75">{x}</span>
              {n && <span className="mt-2 block truncate text-xs font-semibold uppercase tracking-wider text-pedra-500">{n}</span>}
            </span>
          </a>
        ))}
      </div>
    </section>
  );
}

/** Chamada final para entrar no servidor. */
export function ChamadaEntrar() {
  return (
    <section className="frx-heroi relative overflow-hidden rounded-2xl border border-ouro-escuro p-8 text-center">
      <Vagalumes quantos={8} />
      <h2 className="relative font-titulo text-3xl text-ouro md:text-4xl">Pronto para a aventura?</h2>
      <p className="relative mx-auto mt-2 max-w-xl text-pergaminho/80">O FRX-WORLD é um servidor entre amigos. Com um convite você recebe o launcher, que instala o Minecraft, o Forge e todos os mods sozinho.</p>
      <a href="/entrar" className="relative mt-5 inline-block rounded-lg bg-gradient-to-b from-ouro to-ouro-escuro px-7 py-3 font-titulo text-lg uppercase tracking-[0.15em] text-pedra-950 shadow-lg shadow-black/50 transition hover:-translate-y-0.5 hover:brightness-110">Como entrar</a>
    </section>
  );
}
