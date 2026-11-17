"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { DadosPublicos, LinhaRank, MapaPublico as MapaDados, ModPublico } from "@/lib/publico";
import { MapaPublico } from "./mapa";
import { Dragao } from "./dragao";
import { Biblioteca } from "./biblioteca";
import type { Livro } from "@/lib/livros";
import type { NotaAtualizacao } from "@/lib/notas";
import { Galeria, ICONE_CLASSE, Nick, Perfil, Podio, Skin } from "./jogadores";

const RANKS = ["F", "E", "D", "C", "B", "A", "S"];
const SISTEMAS: [string, string, string][] = [
  ["⚔️", "7 classes", "Guerreiro, Arqueiro, Mago, Clérigo, Ladino, Assassino e Invocador, cada uma com dezenas de habilidades próprias."],
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
  return (
    <ol className="space-y-1.5">
      {linhas.map((l, i) => (
        <li key={l.nick} className="flex items-center gap-3 rounded-md bg-pedra-800/60 px-3 py-2">
          <span className={`w-6 shrink-0 text-center font-titulo text-lg ${i === 0 ? "text-ouro" : i < 3 ? "text-pergaminho" : "text-pedra-500"}`}>{i + 1}</span>
          <Skin nick={l.nick} tam={28} />
          <span className="min-w-0 flex-1">
            <span className="block truncate"><Nick nick={l.nick} abrir={abrir} />{l.titulo && <span className="ml-2 text-xs font-normal text-ouro-escuro">«{l.titulo}»</span>}</span>
            <span className="block truncate text-xs text-pedra-500">{l.extra}</span>
          </span>
          <span className="shrink-0 text-right text-sm text-ouro">{valor(l.valor)}</span>
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
          <span className="h-3 flex-1 overflow-hidden rounded bg-pedra-800"><span className="block h-full rounded bg-ouro-escuro" style={{ width: `${(dados[k] / max) * 100}%` }} /></span>
          <span className="w-6 shrink-0 text-right text-pedra-500">{dados[k]}</span>
        </div>
      ))}
    </div>
  );
}

export function SitePublico({ inicial, mods, mapa, notas, livros }: { inicial: DadosPublicos | null; mods: ModPublico[]; mapa: MapaDados; notas: NotaAtualizacao[]; livros: Livro[] }) {
  const [todasNotas, setTodasNotas] = useState(false);
  const [perfil, setPerfil] = useState<string | null>(null);
  const [verOnline, setVerOnline] = useState(false);
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
  const abas: [typeof aba, string][] = [["fortes", "Mais fortes"], ["fracos", "Começando agora"], ["ricos", "Mais ricos"], ["conquistas", "Conquistas"], ["mortes", "Mais mortes"]];
  const fmt: Record<typeof aba, (n: number) => string> = {
    fortes: (n) => `Nível ${n}`, fracos: (n) => `Nível ${n}`, ricos: moedas, conquistas: (n) => `${n} 🏆`, mortes: (n) => `${n} ☠`,
  };

  return (
    <div className="relative isolate min-h-screen">
      <Dragao />
      <nav className="sticky top-0 z-20 border-b border-pedra-700 bg-pedra-950/95 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center gap-4 px-4 py-3">
          <a href="#topo" className="font-titulo text-xl text-ouro">FRX-WORLD</a>
          <div className="flex flex-1 gap-4 overflow-x-auto whitespace-nowrap text-sm text-pedra-500">
            {[["#agora", "Agora"], ["#aventureiros", "Aventureiros"], ["#rankings", "Rankings"], ["#mapa", "Mapa"], ["#biblioteca", "Biblioteca"], ["#mundo", "O mundo"], ["#mods", "Mods"], ["#novidades", "Novidades"], ["#entrar", "Como entrar"]].map(([h, r]) => (
              <a key={h} href={h} className="hover:text-ouro">{r}</a>
            ))}
          </div>
          <span className={`flex shrink-0 items-center gap-2 rounded-full border px-3 py-1 text-xs ${online ? "border-green-700 text-green-400" : "border-pedra-700 text-pedra-500"}`}>
            <span className={`h-2 w-2 rounded-full ${online ? "bg-green-400" : "bg-sangue"}`} />{online ? "Online" : "Offline"}
          </span>
        </div>
      </nav>

      <header id="topo" className="frx-heroi relative overflow-hidden border-b border-pedra-700 bg-gradient-to-b from-pedra-800/60 to-transparent">
        <div className="relative mx-auto max-w-6xl px-4 py-14 text-center md:py-20">
          <p className="text-sm uppercase tracking-[0.3em] text-ouro-escuro">Minecraft 1.20.1 · Forge · RPG</p>
          <h1 className="mt-3 font-titulo text-5xl text-ouro md:text-7xl">FRX-WORLD</h1>
          <p className="mx-auto mt-4 max-w-2xl text-lg text-pergaminho/80">Um MMORPG dentro do Minecraft: classes, guilda, reinos, uma torre de 100 andares e um Rei Demônio esperando no fim da história.</p>
          <div className="mx-auto mt-8 grid max-w-3xl grid-cols-2 gap-3 md:grid-cols-4">
            <button type="button" onClick={() => setVerOnline((v) => !v)} aria-expanded={verOnline}
              className={`rounded-xl border bg-pedra-900/80 px-3 py-4 transition hover:-translate-y-0.5 hover:border-ouro ${verOnline ? "border-ouro" : "border-pedra-700"}`}>
              <p className="font-titulo text-3xl text-ouro">{online ? `${d?.onlineAgora.length ?? 0}/${s?.maxJogadores ?? "?"}` : "—"}</p>
              <p className="text-xs text-pedra-500">{online ? "jogando agora" : "servidor desligado"} <span className="text-ouro-escuro">{verOnline ? "▴" : "▾"}</span></p>
            </button>
            {([
              [String(d?.totais.jogadores ?? 0), "aventureiros", "#aventureiros"],
              [String(d?.totais.mods ?? mods.length), "mods", "#mods"],
              [(d?.totais.chunks ?? 0).toLocaleString("pt-BR"), "chunks explorados", "#mapa"],
            ] as [string, string, string][]).map(([n, r, h]) => (
              <a key={r} href={h} className="rounded-xl border border-pedra-700 bg-pedra-900/80 px-3 py-4 transition hover:-translate-y-0.5 hover:border-ouro">
                <p className="font-titulo text-3xl text-ouro">{n}</p>
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
          <Cartao titulo="Acontecendo" sub="Últimos acontecimentos do mundo">
            {d?.feed.length ? (
              <ul className="max-h-64 space-y-1.5 overflow-y-auto pr-1 text-sm">
                {d.feed.map((e, i) => (
                  <li key={i} className="flex gap-2"><span className="shrink-0 font-mono text-xs text-pedra-500">{e.hora}</span><span className="shrink-0">{ICONE_FEED[e.tipo] ?? "•"}</span><span className="min-w-0 break-words">{e.texto}</span></li>
                ))}
              </ul>
            ) : <p className="text-sm text-pedra-500">Tudo calmo por enquanto.</p>}
          </Cartao>
        </div>

        <div id="aventureiros" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Aventureiros</h2>
          <p className="mb-4 text-sm text-pedra-500">Clique em alguém para ver a ficha: skin, nível, Guilda e habilidades.</p>
          <Galeria lista={d?.aventureiros ?? []} abrir={abrir} />
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
            <Cartao titulo="🏆 Temporada" sub={d?.temporada ? `Placar de ${d.temporada.mes.split("-").reverse().join("/")} · zera todo mês` : "Placar do mês"}>
              {d?.temporada?.linhas.length ? (
                <ol className="space-y-1.5">
                  {d.temporada.linhas.map((t, i) => (
                    <li key={t.nome} className="flex items-center gap-3 rounded-md bg-pedra-800/60 px-3 py-2 text-sm">
                      <span className={`w-6 text-center font-titulo text-lg ${i === 0 ? "text-ouro" : i < 3 ? "text-pergaminho" : "text-pedra-500"}`}>{i + 1}</span>
                      <Skin nick={t.nome} tam={24} />
                      <span className="min-w-0 flex-1"><Nick nick={t.nome} abrir={abrir} /></span>
                      <span className="hidden text-xs text-pedra-500 sm:inline">{t.abates} abates · cap. {t.historia}</span>
                      <span className="w-20 text-right text-ouro">{t.pts.toLocaleString("pt-BR")} pts</span>
                    </li>
                  ))}
                </ol>
              ) : <p className="text-sm text-pedra-500">A temporada ainda não tem pontos.</p>}
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

        <div id="mapa" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Mapa do mundo</h2>
          <p className="mb-4 text-sm text-pedra-500">O que já foi explorado até agora. O resto, só indo lá para ver.</p>
          <MapaPublico mapa={mapa} />
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
                  <span className="text-xs text-pedra-500">{n.data}</span>
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
