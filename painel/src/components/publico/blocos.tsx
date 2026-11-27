"use client";

import { useEffect, useMemo, useState } from "react";
import type { CriaturaPublica, DadosPublicos, LinhaRank, MapaPublico as MapaDados, ModPublico } from "@/lib/publico";
import type { NotaAtualizacao } from "@/lib/notas";
import { Bestiario } from "./bestiario";
import { MapaPublico } from "./mapa";
import { Galeria, ICONE_CLASSE, Nick, Podio, Skin } from "./jogadores";
import { useSite } from "./casca";

/* Blocos do site público: cada página monta os que precisa. Os dados vêm do Casca (useSite). */

export const RANKS = ["F", "E", "D", "C", "B", "A", "S"];
export const SISTEMAS: [string, string, string][] = [
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
  ["🧭", "Expedições", "A cada 2 dias, às 5h, abre uma área nova de 2000 × 2000 nas Terras Inexploradas, cheia de estruturas e masmorras. Rank D ou maior."],
  ["⚔️", "Duelos X1", "Desafie outro jogador numa arena, com ou sem kit, e suba no ranking de vitórias."],
  ["☠️", "Chefes mundiais", "Monstros gigantes que surgem de tempos em tempos e pedem o servidor inteiro."],
];
export const ICONE_FEED: Record<string, string> = { entrou: "➜", saiu: "←", morte: "☠", conquista: "🏆", chefe: "☠", invasao: "⚔", forja: "⚒" };

export function moedas(c: number) {
  const ouro = Math.floor(c / 100), prata = Math.floor((c % 100) / 10), cobre = c % 10;
  const p: string[] = [];
  if (ouro) p.push(`${ouro.toLocaleString("pt-BR")} ouro`);
  if (prata) p.push(`${prata} prata`);
  if (cobre || !p.length) p.push(`${cobre} cobre`);
  return p.join(", ");
}
export function relogio(hora: number) {
  const h = (hora / 1000 + 6) % 24, hh = Math.floor(h), mm = Math.floor((h - hh) * 60);
  return `${String(hh).padStart(2, "0")}:${String(mm).padStart(2, "0")}`;
}

/** Número que sobe até o valor (e anda de novo quando o valor muda). */
export function Subindo({ valor }: { valor: number }) {
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
export function FimDaTemporada({ mes }: { mes: string }) {
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
export const PONTOS_TEMPORADA: [string, string, number][] = [["📖", "Capítulo da história", 100], ["🌀", "Portal fechado", 80], ["🎯", "Procurado capturado", 60], ["☠", "Chefe derrotado", 50], ["⚔️", "Invasão repelida", 40], ["💎", "Tesouro encontrado", 30], ["🗼", "Andar novo na Torre", 20], ["🗡️", "Monstro abatido", 1]];
export const MEDALHAS = ["🥇", "🥈", "🥉"];

export function Cartao({ titulo, sub, children, id }: { titulo: string; sub?: string; children: React.ReactNode; id?: string }) {
  return (
    <section id={id} className="scroll-mt-20 rounded-xl border border-pedra-700 bg-pedra-900 p-5">
      <h3 className="font-titulo text-xl text-ouro">{titulo}</h3>
      {sub && <p className="mb-3 text-sm text-pedra-500">{sub}</p>}
      <div className={sub ? "" : "mt-3"}>{children}</div>
    </section>
  );
}

export function Rank({ linhas, valor, vazio, abrir }: { linhas: LinhaRank[]; valor: (n: number) => string; vazio: string; abrir: (n: string) => void }) {
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

export function Barras({ dados, ordem }: { dados: Record<string, number>; ordem?: string[] }) {
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

export function Clas({ clas, abrir }: { clas: DadosPublicos["clas"]; abrir: (n: string) => void }) {
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


export function SecaoAgora() {
  const { d, abrir } = useSite();
  const s = d?.status ?? null;
  const online = !!d?.online;
  void s; void online;
  return (
    <>
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
    </>
  );
}


export function SecaoAventureiros() {
  const { d, abrir } = useSite();
  const s = d?.status ?? null;
  const online = !!d?.online;
  void s; void online;
  return (
    <>
        <div id="aventureiros" className="scroll-mt-20">
                    <p className="mb-4 text-sm text-pedra-500">Clique em alguém para ver a ficha: skin, nível, Guilda e habilidades.</p>
          <Galeria lista={d?.aventureiros ?? []} abrir={abrir} />
        </div>
    </>
  );
}


export function SecaoCronicas({ limite = 8 }: { limite?: number }) {
  const { d, abrir } = useSite();
  const s = d?.status ?? null;
  const online = !!d?.online;
  void s; void online;
  return (
    <>
        {(d?.cronicas ?? []).some((c) => c.destaque) && (
          <div id="cronicas" className="scroll-mt-20">
            <h2 className="font-titulo text-3xl">📰 Crônicas do FRX</h2>
            <p className="mb-4 text-sm text-pedra-500">Os grandes feitos do servidor, escritos na hora em que aconteceram. Ficam guardados para sempre.</p>
            <div className="grid gap-3 md:grid-cols-2">
              {(d?.cronicas ?? []).filter((c) => c.destaque).slice(0, limite).map((c, i) => (
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
    </>
  );
}


export function SecaoMural() {
  const { d, abrir } = useSite();
  const s = d?.status ?? null;
  const online = !!d?.online;
  void s; void online;
  return (
    <>
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
    </>
  );
}


export function SecaoArmas() {
  const { d, abrir } = useSite();
  const s = d?.status ?? null;
  const online = !!d?.online;
  void s; void online;
  return (
    <>
        <div id="armas" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Armas Únicas</h2>
          <p className="mb-4 text-sm text-pedra-500">As armas do Códex existem em número limitado no mundo inteiro. Aqui fica gravado quem pôs as mãos na primeira de cada uma.</p>
          {!(d?.armas ?? []).length ? (
            <p className="rounded-xl border border-dashed border-pedra-700 bg-pedra-900/60 px-4 py-6 text-center text-sm text-pedra-500">Nenhuma arma única tem dono ainda. A primeira lenda está esperando alguém.</p>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {(d?.armas ?? []).map((a) => {
                const cor = ["#cfc6b4", "#4ade80", "#60a5fa", "#c084fc", "#f5b041"][Math.max(0, Math.min(4, a.raridade))];
                const raridade = ["Comum", "Incomum", "Raro", "Épico", "Lendário"][Math.max(0, Math.min(4, a.raridade))];
                return (
                  <div key={a.codigo} className="flex gap-3 rounded-xl border bg-gradient-to-br from-pedra-800 to-pedra-900 p-4" style={{ borderColor: cor }}>
                    <span className="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg border border-pedra-700 bg-pedra-950" style={{ boxShadow: `0 0 18px ${cor}55` }}>
                      {a.textura
                        // eslint-disable-next-line @next/next/no-img-element
                        ? <img src={a.textura} alt="" width={48} height={48} style={{ imageRendering: "pixelated" }} />
                        : <span className="text-3xl" aria-hidden="true">⚔️</span>}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-xs uppercase tracking-wider" style={{ color: cor }}>{raridade}{a.limite > 0 ? ` · ${a.emitidas} de ${a.limite} no mundo` : ""}</span>
                      <span className="block truncate font-titulo text-lg" style={{ color: cor }}>{a.nome}</span>
                      {a.nivel >= 0 && <span className="block text-xs text-ouro" title="Nível do item: sobe com o uso">Nível {a.nivel} <span className="tracking-tighter">{"★".repeat(a.nivel)}<span className="text-pedra-600">{"☆".repeat(Math.max(0, 10 - a.nivel))}</span></span></span>}
                      {a.descricao && <span className="line-clamp-2 block text-xs text-pedra-500">{a.descricao}</span>}
                      <span className="mt-1 flex items-center gap-2 text-sm">
                        <span className="text-pedra-500">1º dono:</span>
                        {a.conta && <Skin nick={a.conta} tam={18} />}
                        <button type="button" onClick={() => abrir(a.conta ? `${a.conta}#${a.vaga}` : a.primeiro)} className="truncate font-semibold hover:text-ouro">{a.primeiro}</button>
                        {a.quando && <span className="shrink-0 text-xs text-pedra-500">{new Date(a.quando).toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo" })}</span>}
                      </span>
                    </span>
                  </div>
                );
              })}
            </div>
          )}
        </div>
    </>
  );
}


export function SecaoRankings() {
  const { d, abrir } = useSite();
  const s = d?.status ?? null;
  const online = !!d?.online;
  void s; void online;
  const [aba, setAba] = useState<"fortes" | "fracos" | "ricos" | "conquistas" | "mortes">("fortes");
  const abas: [typeof aba, string][] = [["fortes", "Mais fortes"], ["fracos", "Começando agora"], ["ricos", "Mais ricos"], ["conquistas", "Conquistas"], ["mortes", "Mais mortes"]];
  const fmt: Record<typeof aba, (n: number) => string> = {
    fortes: (n) => `Nível ${n}`, fracos: (n) => `Nível ${n}`, ricos: moedas, conquistas: (n) => `${n} 🏆`, mortes: (n) => `${n} ☠`,
  };
  return (
    <>
        <div id="rankings" className="scroll-mt-20">
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
            <Cartao id="temporada" titulo="🏆 Temporada" sub={d?.temporada ? `Placar de ${d.temporada.mes.split("-").reverse().join("/")} · zera todo mês, e os três primeiros levam prêmio` : "Placar do mês"}>
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
          <div className="mt-5 grid gap-5 md:grid-cols-2">
            <Cartao titulo="🎣 Torneio de pesca" sub={d?.pesca.fim ? `Maior peixe da semana para a espécie · termina ${new Date(d.pesca.fim).toLocaleDateString("pt-BR", { weekday: "long" })} à meia-noite · 1º 30 ouro, 2º 15, 3º 8` : "Maior peixe da semana para a espécie"}>
              {d?.pesca.campeao && <p className="mb-3 text-xs text-pedra-500">Campeão da semana passada: <span className="text-ouro">{d.pesca.campeao.nome}</span> ({d.pesca.campeao.especie}, {d.pesca.campeao.peso.toLocaleString("pt-BR", { maximumFractionDigits: 1 })} kg)</p>}
              {d?.pesca.linhas.length ? (
                <ol className="space-y-1.5">
                  {d.pesca.linhas.map((t, i) => (
                    <li key={t.nome} className="flex items-center gap-3 rounded-md bg-pedra-800/60 px-3 py-2 text-sm">
                      <span className={`w-7 text-center ${i < 3 ? "text-xl" : "font-titulo text-lg text-pedra-500"}`}>{MEDALHAS[i] ?? i + 1}</span>
                      <Skin nick={t.nome} tam={24} />
                      <span className="min-w-0 flex-1"><Nick nick={t.nome} abrir={abrir} /></span>
                      <span className="hidden text-xs text-pedra-500 sm:inline">{t.qtd} peixes</span>
                      <span className="text-right text-ouro">{t.pct > 1 ? "✦ " : ""}{t.especie} · {t.peso.toLocaleString("pt-BR", { maximumFractionDigits: 1 })} kg</span>
                    </li>
                  ))}
                </ol>
              ) : <p className="text-sm text-pedra-500">Ninguém pescou nada esta semana. À noite ou com chuva, dizem que o Peixe-Rei Dourado morde.</p>}
            </Cartao>
            <Cartao titulo="🏁 Corridas" sub="Os melhores tempos de cada pista · fique na largada (anel de fogo) para correr">
              {d?.corridas.length ? (
                <div className="space-y-3">
                  {d.corridas.map((c) => (
                    <div key={c.id} className="rounded-lg bg-pedra-800/60 px-3 py-2">
                      <p className="mb-1 text-sm"><span className="text-ouro">{c.nome}</span> <span className="text-xs text-pedra-500">· {({ pe: "a pé", cavalo: "a cavalo", barco: "de barco", elitro: "de élitro" } as Record<string, string>)[c.tipo] ?? c.tipo} · {c.voltas > 1 ? `${c.voltas} voltas · ` : ""}{c.pontos} pontos · {c.feitas} corridas</span></p>
                      {c.voltaRapida && (
                        <p className="mb-1.5 flex items-center gap-2 rounded-md border border-fuchsia-700/60 bg-fuchsia-950/40 px-2 py-1 text-sm">
                          <span aria-hidden="true">⚡</span><span className="text-fuchsia-300">Volta mais rápida</span>
                          <span className="min-w-0 flex-1"><Nick nick={c.voltaRapida.nome} abrir={abrir} /></span>
                          <span className="font-mono text-fuchsia-200">{Math.floor(c.voltaRapida.ms / 60000)}:{String(Math.floor(c.voltaRapida.ms / 1000) % 60).padStart(2, "0")}.{String(Math.floor(c.voltaRapida.ms / 10) % 100).padStart(2, "0")}</span>
                        </p>
                      )}
                      {c.recordes.length ? (
                        <ol className="space-y-0.5 text-sm">
                          {c.recordes.map((r, i) => (
                            <li key={r.nome} className="flex items-center gap-2">
                              <span className="w-6 text-center">{MEDALHAS[i] ?? i + 1}</span>
                              <span className="min-w-0 flex-1"><Nick nick={r.nome} abrir={abrir} /></span>
                              <span className="font-mono text-ouro">{Math.floor(r.ms / 60000)}:{String(Math.floor(r.ms / 1000) % 60).padStart(2, "0")}.{String(Math.floor(r.ms / 10) % 100).padStart(2, "0")}</span>
                            </li>
                          ))}
                        </ol>
                      ) : <p className="text-xs text-pedra-500">Ninguém completou ainda.</p>}
                    </div>
                  ))}
                </div>
              ) : <p className="text-sm text-pedra-500">As pistas de corrida ainda estão sendo traçadas.</p>}
            </Cartao>
            <Cartao titulo="⚔️ X1 — quem mais venceu" sub="Duelos combinados em arena · aba X1 do inventário">
              {d?.x1.ranking.length ? (
                <ol className="space-y-1 text-sm">
                  {d.x1.ranking.map((r, i) => (
                    <li key={r.nome} className="flex items-center gap-2 rounded-lg bg-pedra-800/60 px-3 py-1.5">
                      <span className="w-6 text-center">{MEDALHAS[i] ?? i + 1}</span>
                      <span className="min-w-0 flex-1"><Nick nick={r.nome} abrir={abrir} /></span>
                      <span className="text-xs text-pedra-500">{r.d} derrota{r.d === 1 ? "" : "s"}{r.e ? ` · ${r.e} empate${r.e === 1 ? "" : "s"}` : ""}{r.melhor > 1 ? ` · sequência de ${r.melhor}` : ""}</span>
                      <span className="font-mono text-ouro">{r.v} vitória{r.v === 1 ? "" : "s"}</span>
                    </li>
                  ))}
                </ol>
              ) : <p className="text-sm text-pedra-500">Nenhum duelo ainda. Abra o inventário, vá na aba X1 e desafie alguém.</p>}
            </Cartao>
            <Cartao titulo="⚔️ X1 — últimas lutas" sub="Quem enfrentou quem, e quem levou">
              {d?.x1.lutas.length ? (
                <ul className="space-y-1 text-sm">
                  {d.x1.lutas.map((l) => (
                    <li key={l.quando} className="rounded-lg bg-pedra-800/60 px-3 py-1.5">
                      <p className="flex flex-wrap items-center gap-x-2">
                        <span className={l.vencedor === l.a ? "text-ouro" : ""}>{l.vencedor === l.a ? "🏆 " : ""}{l.a}</span>
                        <span className="text-pedra-500">vs</span>
                        <span className={l.vencedor === l.b ? "text-ouro" : ""}>{l.vencedor === l.b ? "🏆 " : ""}{l.b}</span>
                        {l.placar && <span className="font-mono text-pedra-300">{l.placar}</span>}
                        {!l.vencedor && <span className="text-pedra-400">empate</span>}
                      </p>
                      <p className="text-xs text-pedra-500">{new Date(l.quando).toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit", timeZone: "America/Sao_Paulo" })}{l.arena ? ` · ${l.arena}` : ""} · {l.regras}</p>
                    </li>
                  ))}
                </ul>
              ) : <p className="text-sm text-pedra-500">As lutas aparecem aqui assim que acontecerem.</p>}
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
    </>
  );
}


export function SecaoClas() {
  const { d, abrir } = useSite();
  const s = d?.status ?? null;
  const online = !!d?.online;
  void s; void online;
  return (
    <>
        <div id="clas" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Clãs</h2>
          <p className="mb-4 text-sm text-pedra-500">Grupos fixos de aventureiros. No jogo, procure o Mestre dos Clãs numa capital para fundar o seu, pedir entrada ou usar o cofre. Dá para participar de até 3 clãs.</p>
          <Clas clas={d?.clas ?? []} abrir={abrir} />
        </div>
    </>
  );
}


export function SecaoMapa({ mapa }: { mapa: MapaDados }) {
  const { d, abrir } = useSite();
  const s = d?.status ?? null;
  const online = !!d?.online;
  void s; void online;
  return (
    <>
        <div id="mapa" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Mapa do mundo</h2>
          <p className="mb-4 text-sm text-pedra-500">O que já foi explorado até agora. O resto, só indo lá para ver.</p>
          <MapaPublico mapa={mapa} />
        </div>
    </>
  );
}


export function SecaoBestiario({ bestiario }: { bestiario: CriaturaPublica[] }) {
  const { d, abrir } = useSite();
  const s = d?.status ?? null;
  const online = !!d?.online;
  void s; void online;
  return (
    <>
        <div id="bestiario" className="scroll-mt-20">
          <h2 className="font-titulo text-3xl">Bestiário</h2>
          <p className="mb-4 text-sm text-pedra-500">Os chefes e monstros próprios do servidor. Cada um só revela o nome, a história e os golpes depois que alguém o derrota pela primeira vez.</p>
          <Bestiario criaturas={bestiario} />
        </div>
    </>
  );
}


export function SecaoMundo() {
  const { d, abrir } = useSite();
  const s = d?.status ?? null;
  const online = !!d?.online;
  void s; void online;
  return (
    <>
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
    </>
  );
}


export function SecaoMods({ mods }: { mods: ModPublico[] }) {
  const { d, abrir } = useSite();
  const s = d?.status ?? null;
  const online = !!d?.online;
  void s; void online;
  const [busca, setBusca] = useState("");
  const modsFiltrados = useMemo(() => {
    const b = busca.trim().toLowerCase();
    return b ? mods.filter((m) => m.nome.toLowerCase().includes(b)) : mods;
  }, [mods, busca]);
  return (
    <>
        <div id="mods" className="scroll-mt-20">
          <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 className="font-titulo text-3xl">Lista de mods</h2>
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
    </>
  );
}


export function SecaoNovidades({ notas, inicio = 3 }: { notas: NotaAtualizacao[]; inicio?: number }) {
  const { d, abrir } = useSite();
  const s = d?.status ?? null;
  const online = !!d?.online;
  void s; void online;
  const [todasNotas, setTodasNotas] = useState(false);
  return (
    <>
        <div id="novidades" className="scroll-mt-20">
                    <ol className="space-y-4 border-l border-pedra-700 pl-5">
            {(todasNotas ? notas : notas.slice(0, inicio)).map((n, i) => (
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
          {notas.length > inicio && (
            <button type="button" onClick={() => setTodasNotas((v) => !v)} className="mt-4 rounded-md border border-pedra-700 px-4 py-2 text-sm hover:border-ouro">
              {todasNotas ? "Mostrar só as últimas" : `Ver todas as ${notas.length} atualizações`}
            </button>
          )}
        </div>
    </>
  );
}
