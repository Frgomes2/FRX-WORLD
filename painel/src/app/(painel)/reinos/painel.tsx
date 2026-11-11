"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { RostoSkin } from "../jogadores/[uuid]/boneco";
import { MapaMundo, type MapaDados } from "../visao/mapa-mundo";
import { NIVEIS_REPUTACAO, REINOS, VISUAL_REINO, nivelReputacao, proximoNivel, type Campanha } from "@/lib/reinos";
import { NOMES_NPC, RACA_DO_REINO } from "@/lib/npcs";

type Jogador = { uuid: string; nick: string; nivel: number; titulo: string; classe: string; online: boolean; reputacao: Record<string, number>; campanhas: Record<string, Campanha> };
type ReinoSrv = { id: string; nome: string; x: number; z: number; larguraChunks: number; profundidadeChunks: number; modo: string; altura: number; npcsFixos: number; x0: number; z0: number; x1: number; z1: number };
type Servidor = { reinos: ReinoSrv[]; spawn: [number, number] | null; masmorras: { id: string; nome: string; chefe: string; x: number; z: number }[] };
type Masm = { id: string; nome: string; x: number; z: number; colocada: boolean };
type JogVivo = { nick: string; uuid: string; x: number; z: number; dim: string; reino?: string };

const COR_NIVEL: Record<string, string> = { Neutro: "#6b6257", "Amigável": "#4ade80", Honrado: "#67e8f9", Reverenciado: "#60a5fa", Exaltado: "#d8b4fe", "Herói": "#d4a93a" };
const fmt = (n: number) => n.toLocaleString("pt-BR");

/** Rosto 8×8 (+ chapéu) de uma skin qualquer pela URL. */
function RostoUrl({ url, tam = 32 }: { url: string; tam?: number }) {
  const k = tam / 8;
  const camada = (u: number, v: number) => ({ backgroundImage: `url("${url}")`, backgroundSize: `${64 * k}px ${64 * k}px`, backgroundPosition: `${-u * k}px ${-v * k}px` });
  return (
    <span className="relative block shrink-0 overflow-hidden rounded [image-rendering:pixelated]" style={{ width: tam, height: tam }}>
      <span className="absolute inset-0 bg-pedra-700" style={camada(8, 8)} />
      <span className="absolute inset-0" style={camada(40, 8)} />
    </span>
  );
}

function urlRei(reino: string, skins: Set<string>) {
  if (skins.has(`reino:${reino}:rei`)) return `/api/painel/npc-skins/png?chave=${encodeURIComponent(`reino:${reino}:rei`)}`;
  if (skins.has("papel:rei")) return `/api/painel/npc-skins/png?chave=${encodeURIComponent("papel:rei")}`;
  return `/api/painel/raca-skin?raca=${RACA_DO_REINO[reino] ?? "humano"}&k=0`;
}

function estadoCampanha(c?: Campanha) {
  if (!c || c.etapa < 0) return { tipo: "nada" as const, frac: 0 };
  if (c.etapa >= c.total) return { tipo: "fim" as const, frac: 1 };
  const parcial = c.qtd && c.qtd > 1 ? Math.min(c.progresso ?? 0, c.qtd) / c.qtd : 0;
  return { tipo: "andando" as const, frac: (c.etapa + parcial) / Math.max(1, c.total) };
}

export function PainelReinos({ jogadores, servidor, skins, masmorras }: { jogadores: Jogador[]; servidor: Servidor; skins: string[]; masmorras: Masm[] }) {
  const [sel, setSel] = useState<string>(REINOS[0].id);
  const [foco, setFoco] = useState<{ x: number; z: number; esc: number; n: number } | null>(null);
  const [mapa, setMapa] = useState<MapaDados | null>(null);
  const [vivos, setVivos] = useState<JogVivo[]>([]);
  const [editando, setEditando] = useState<{ uuid: string; valor: string } | null>(null);
  const [msg, setMsg] = useState<{ ok: boolean; t: string } | null>(null);
  const [rep, setRep] = useState(() => Object.fromEntries(jogadores.map((j) => [j.uuid, j.reputacao])));
  const skinSet = useMemo(() => new Set(skins), [skins]);
  const srv = useMemo(() => new Map(servidor.reinos.map((r) => [r.id, r])), [servidor]);

  // mapa explorado (uma vez) + jogadores ao vivo
  useEffect(() => {
    let vivo = true, n = 0;
    const ler = async () => {
      try {
        const r = await fetch(`/api/painel/visao${n++ === 0 ? "?mapa=1" : ""}`, { cache: "no-store" });
        const j = await r.json();
        if (!vivo) return;
        if (j.mapa) setMapa(j.mapa);
        setVivos(j.status?.dados?.jogadores ?? []);
      } catch { /* servidor desligado: segue sem jogadores */ }
    };
    ler();
    const t = setInterval(ler, 15_000);
    return () => { vivo = false; clearInterval(t); };
  }, []);

  const reinosMapa = servidor.reinos.map((r) => ({ id: r.id, nome: r.nome, x0: r.x0, z0: r.z0, x1: r.x1, z1: r.z1, subterraneo: r.modo === "subterraneo" }));

  const resumo = useMemo(() => REINOS.map((r) => {
    const porNivel = new Map<string, number>();
    let concl = 0, andando = 0, total = 0;
    let top: { j: Jogador; p: number } | null = null;
    for (const j of jogadores) {
      const p = rep[j.uuid]?.[r.id] ?? 0;
      total += p;
      const n = nivelReputacao(p).nome;
      porNivel.set(n, (porNivel.get(n) ?? 0) + 1);
      const c = estadoCampanha(j.campanhas[r.id]);
      if (c.tipo === "fim") concl++; else if (c.tipo === "andando") andando++;
      if (p > 0 && (!top || p > top.p)) top = { j, p };
    }
    return { r, porNivel, concl, andando, total, top };
  }), [jogadores, rep]);

  const totais = {
    concluidas: resumo.reduce((s, x) => s + x.concl, 0),
    herois: jogadores.reduce((s, j) => s + REINOS.filter((r) => (rep[j.uuid]?.[r.id] ?? 0) >= 10000).length, 0),
    online: vivos.length,
  };

  const verNoMapa = (id: string) => {
    const r = srv.get(id);
    if (!r) return;
    setFoco({ x: r.x, z: r.z, esc: Math.min(2, 520 / Math.max(160, r.x1 - r.x0) / 1.3), n: (foco?.n ?? 0) + 1 });
  };
  const escolher = (id: string) => { setSel(id); setEditando(null); setMsg(null); verNoMapa(id); };

  const salvarRep = async (uuid: string, reino: string, valor: string) => {
    setMsg(null);
    const v = Math.max(0, Math.round(Number(valor) || 0));
    try {
      const r = await fetch(`/api/painel/jogadores/${uuid}/acao`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ acao: "reputacao", alvo: reino, valor: v }) });
      const j = await r.json();
      if (!r.ok) throw new Error(j.erro ?? "Erro");
      setRep((x) => ({ ...x, [uuid]: { ...x[uuid], [reino]: v } }));
      setEditando(null);
      setMsg({ ok: true, t: "Reputação enviada ao servidor ✓" });
    } catch (e) {
      setMsg({ ok: false, t: e instanceof Error ? e.message : "Erro" });
    }
  };

  const atual = REINOS.find((r) => r.id === sel)!;
  const vis = VISUAL_REINO[sel];
  const s = srv.get(sel);
  const rei = NOMES_NPC[sel]?.rei ?? "Rei";
  const listaSel = [...jogadores].sort((a, b) => (rep[b.uuid]?.[sel] ?? 0) - (rep[a.uuid]?.[sel] ?? 0));

  return (
    <>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-titulo text-3xl">Reinos</h1>
          <p className="text-pedra-500">As capitais, seus soberanos e como cada aventureiro está com cada coroa.</p>
        </div>
        <Link href="/npcs" className="rounded-md border border-pedra-700 px-3 py-1.5 text-sm hover:border-ouro">🎨 Skins dos NPCs</Link>
      </div>

      <div className="mb-5 grid grid-cols-2 gap-3 md:grid-cols-4">
        {([["👑", "Reinos", REINOS.length, "text-ouro"], ["🧭", "Aventureiros", jogadores.length, "text-sky-300"],
          ["📜", "Campanhas concluídas", totais.concluidas, "text-green-400"], ["🏅", "Títulos de Herói", totais.herois, "text-purple-300"]] as const).map(([ic, n, v, cor]) => (
          <div key={n} className="rounded-xl border border-pedra-700 bg-pedra-900 p-3">
            <p className="text-xs uppercase tracking-wider text-pedra-500">{ic} {n}</p>
            <p className={`font-titulo text-3xl ${cor}`}>{v}</p>
          </div>
        ))}
      </div>

      <div className="mb-6 grid gap-5 xl:grid-cols-[1.6fr_1fr]">
        <section className="rounded-xl border border-pedra-700 bg-pedra-900 p-3">
          <div className="mb-2 flex items-center gap-2">
            <h2 className="font-titulo text-lg text-ouro">🗺️ Mapa dos reinos</h2>
            <span className="ml-auto text-xs text-pedra-500">{totais.online ? `${totais.online} online` : "ninguém online"} · clique num reino para centralizar</span>
          </div>
          {servidor.reinos.length === 0
            ? <p className="p-6 text-sm text-pedra-500">Não achei a pasta <code>server/frxworld/reinos</code>. Ligue o servidor uma vez para ela ser criada.</p>
            : <MapaMundo mapa={mapa} reinos={reinosMapa} jogadores={vivos.filter((j) => j.dim === "overworld")} masmorras={masmorras}
                spawn={servidor.spawn} chefe={null} foco={foco} />}
        </section>

        {/* reino escolhido */}
        <section className="overflow-hidden rounded-xl border bg-pedra-900" style={{ borderColor: `${vis.cor}88` }}>
          <div className="p-4" style={{ background: `linear-gradient(135deg, ${vis.cor}33, transparent 70%)` }}>
            <div className="flex items-start gap-3">
              <span className="rounded-lg border-2 p-0.5" style={{ borderColor: vis.cor }}><RostoUrl url={urlRei(sel, skinSet)} tam={56} /></span>
              <div className="min-w-0">
                <p className="text-xs uppercase tracking-wider" style={{ color: vis.cor }}>{vis.emblema} {atual.nome}</p>
                <h2 className="font-titulo text-2xl leading-tight">{s?.nome ?? atual.nome}</h2>
                <p className="text-sm">👑 {rei}</p>
              </div>
            </div>
            <div className="mt-3 grid grid-cols-2 gap-x-3 gap-y-1 text-xs text-pedra-500">
              <span>🧬 {vis.raca}</span><span>🌄 {vis.terreno}</span>
              {s ? <>
                <span>📍 {fmt(s.x)}, {fmt(s.z)}</span><span>📐 {s.larguraChunks * 16}×{s.profundidadeChunks * 16} blocos</span>
                <span>{s.modo === "subterraneo" ? "⛏ subterrâneo" : "☀ superfície"}</span><span>🧍 {s.npcsFixos ? `${s.npcsFixos} NPCs posicionados` : "NPCs automáticos"}</span>
              </> : <span className="col-span-2 text-yellow-300">Capital não encontrada na pasta do servidor.</span>}
              <span className="col-span-2">📜 Campanha: <b className="text-pergaminho">{atual.campanha}</b></span>
            </div>
            <div className="mt-3 flex gap-2 text-xs">
              <button onClick={() => verNoMapa(sel)} className="rounded-md border border-pedra-700 px-2.5 py-1 hover:border-ouro">🔎 Ver no mapa</button>
              <Link href="/npcs" className="rounded-md border border-pedra-700 px-2.5 py-1 hover:border-ouro">🎨 Trocar skins</Link>
            </div>
          </div>
          <div className="max-h-[300px] space-y-1.5 overflow-y-auto border-t border-pedra-800 p-3">
            {msg && <p className={`text-xs ${msg.ok ? "text-green-400" : "text-red-400"}`}>{msg.t}</p>}
            {!listaSel.length && <p className="text-sm text-pedra-500">Ninguém entrou ainda.</p>}
            {listaSel.map((j) => {
              const p = rep[j.uuid]?.[sel] ?? 0, n = nivelReputacao(p), prox = proximoNivel(p);
              const c = j.campanhas[sel], ec = estadoCampanha(c);
              return (
                <div key={j.uuid} className="rounded-lg border border-pedra-800 bg-pedra-950/60 p-2">
                  <div className="flex items-center gap-2">
                    <Link href={`/jogadores/${j.uuid}`} className="shrink-0 overflow-hidden rounded"><RostoSkin uuid={j.uuid} tam={22} /></Link>
                    <Link href={`/jogadores/${j.uuid}`} className="truncate text-sm font-semibold hover:text-ouro">{j.nick}</Link>
                    {j.online && <span className="h-2 w-2 rounded-full bg-green-400" title="online" />}
                    <span className="text-xs text-pedra-500">Nv {j.nivel}</span>
                    <span className="ml-auto text-xs font-semibold" style={{ color: COR_NIVEL[n.nome] }}>{n.nome}</span>
                  </div>
                  <div className="mt-1 flex items-center gap-2 text-[11px]">
                    <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-pedra-800"><span className="block h-full" style={{ width: `${(prox?.frac ?? 1) * 100}%`, background: COR_NIVEL[n.nome] }} /></span>
                    {editando?.uuid === j.uuid ? (
                      <span className="flex items-center gap-1">
                        <input autoFocus type="number" min={0} value={editando.valor} onChange={(e) => setEditando({ uuid: j.uuid, valor: e.target.value })}
                          onKeyDown={(e) => { if (e.key === "Enter") salvarRep(j.uuid, sel, editando.valor); if (e.key === "Escape") setEditando(null); }}
                          className="w-20 rounded border border-ouro bg-pedra-950 px-1 py-0.5" />
                        <button onClick={() => salvarRep(j.uuid, sel, editando.valor)} className="text-green-400">✔</button>
                        <button onClick={() => setEditando(null)} className="text-pedra-500">✕</button>
                      </span>
                    ) : (
                      <button onClick={() => setEditando({ uuid: j.uuid, valor: String(p) })} className="text-pedra-500 hover:text-ouro" title="Alterar reputação (servidor ligado)">
                        {fmt(p)} pts{prox ? ` → ${prox.prox.nome} ${fmt(prox.prox.minimo)}` : ""} ✏️
                      </button>
                    )}
                  </div>
                  <div className="mt-1 flex items-center gap-2 text-[11px] text-pedra-500">
                    📜 <span className="h-1 w-16 overflow-hidden rounded-full bg-pedra-800"><span className={`block h-full ${ec.tipo === "fim" ? "bg-green-500" : "bg-yellow-400"}`} style={{ width: `${ec.frac * 100}%` }} /></span>
                    {ec.tipo === "nada" ? "campanha não iniciada" : ec.tipo === "fim" ? <span className="text-green-400">concluída</span>
                      : <span className="truncate text-yellow-300">etapa {c!.etapa + 1}/{c!.total}{c!.etapaTitulo ? `: ${c!.etapaTitulo}` : ""}{c!.qtd && c!.qtd > 1 ? ` (${Math.min(c!.progresso ?? 0, c!.qtd)}/${c!.qtd})` : ""}</span>}
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      </div>

      {/* cartões dos reinos */}
      <div className="mb-8 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {resumo.map(({ r, porNivel, concl, andando, top }) => {
          const v = VISUAL_REINO[r.id];
          const marcado = sel === r.id;
          return (
            <button key={r.id} onClick={() => escolher(r.id)}
              className={`overflow-hidden rounded-xl border bg-pedra-900 text-left transition hover:-translate-y-0.5 ${marcado ? "ring-2" : ""}`}
              style={{ borderColor: marcado ? v.cor : "#3a3128", ["--tw-ring-color" as string]: v.cor }}>
              <div className="flex items-center gap-2.5 p-3" style={{ background: `linear-gradient(120deg, ${v.cor}40, transparent 75%)` }}>
                <RostoUrl url={urlRei(r.id, skinSet)} tam={36} />
                <div className="min-w-0">
                  <p className="font-titulo text-lg leading-tight">{v.emblema} {r.nome}</p>
                  <p className="truncate text-[11px] text-pedra-500">{NOMES_NPC[r.id]?.rei}</p>
                </div>
              </div>
              <div className="space-y-2 p-3 pt-1 text-xs">
                <p className="truncate text-pedra-500">📜 {r.campanha}</p>
                <div>
                  <div className="flex h-2 overflow-hidden rounded-full bg-pedra-800">
                    {NIVEIS_REPUTACAO.map((n) => {
                      const q = porNivel.get(n.nome) ?? 0;
                      return q ? <span key={n.nome} title={`${n.nome}: ${q}`} style={{ width: `${(q / Math.max(1, jogadores.length)) * 100}%`, background: COR_NIVEL[n.nome] }} /> : null;
                    })}
                  </div>
                  <p className="mt-1 text-[10px] text-pedra-500">
                    {NIVEIS_REPUTACAO.filter((n) => n.nome !== "Neutro" && porNivel.get(n.nome)).map((n) => `${porNivel.get(n.nome)} ${n.nome}`).join(" · ") || "ninguém ganhou reputação ainda"}
                  </p>
                </div>
                <div className="flex justify-between">
                  <span className="text-green-400">✔ {concl} concluíram</span><span className="text-yellow-300">⏳ {andando} em andamento</span>
                </div>
                <p className="truncate">🏆 {top ? <><b>{top.j.nick}</b> <span className="text-pedra-500">· {fmt(top.p)} pts</span></> : <span className="text-pedra-500">sem campeão</span>}</p>
              </div>
            </button>
          );
        })}
      </div>

      {/* matriz */}
      <section className="rounded-xl border border-pedra-700 bg-pedra-900 p-4">
        <div className="mb-3 flex flex-wrap items-center gap-3">
          <h2 className="font-titulo text-lg text-ouro">📊 Todos os aventureiros × reinos</h2>
          <div className="ml-auto flex flex-wrap gap-2 text-[11px]">
            {NIVEIS_REPUTACAO.map((n) => (
              <span key={n.nome} className="flex items-center gap-1"><span className="h-2.5 w-2.5 rounded-sm" style={{ background: COR_NIVEL[n.nome] }} />{n.nome} {fmt(n.minimo)}+{n.desconto ? ` (−${n.desconto}%)` : ""}</span>
            ))}
          </div>
        </div>
        {!jogadores.length ? <p className="text-sm text-pedra-500">Ninguém entrou ainda.</p> : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="text-pedra-500">
                  <th className="px-2 py-1 text-left">Jogador</th>
                  {REINOS.map((r) => <th key={r.id} className="px-1 py-1"><button onClick={() => escolher(r.id)} className="hover:text-ouro">{VISUAL_REINO[r.id].emblema} {r.nome}</button></th>)}
                </tr>
              </thead>
              <tbody>
                {jogadores.map((j) => (
                  <tr key={j.uuid} className="border-t border-pedra-800">
                    <td className="px-2 py-1"><Link href={`/jogadores/${j.uuid}`} className="font-semibold hover:text-ouro">{j.nick}</Link> <span className="text-pedra-500">Nv {j.nivel}</span></td>
                    {REINOS.map((r) => {
                      const p = rep[j.uuid]?.[r.id] ?? 0, n = nivelReputacao(p), ec = estadoCampanha(j.campanhas[r.id]);
                      return (
                        <td key={r.id} className="px-1 py-1">
                          <button onClick={() => escolher(r.id)} title={`${n.nome} · ${fmt(p)} pts`}
                            className="flex w-full items-center justify-center gap-1 rounded px-1 py-1" style={{ background: `${COR_NIVEL[n.nome]}${p ? "33" : "11"}`, color: p ? COR_NIVEL[n.nome] : "#6b6257" }}>
                            {p ? fmt(p) : "—"}{ec.tipo === "fim" ? " ✔" : ec.tipo === "andando" ? " ⏳" : ""}
                          </button>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
