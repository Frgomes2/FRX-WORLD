"use client";

import Link from "next/link";
import { useMemo, useState, useTransition } from "react";
import { IconeItem, useMapaItens } from "@/components/itens";
import { alternarDungeonId, criarExemplosDungeon, duplicarDungeon, excluirDungeonId } from "./actions";
import { avaliacao, CORES_DUNGEON, Criatura } from "./comum";

type G = { bossTipo: string; bossNome: string; bossVida: number; bossDano: number; bossVelocidade: number; lacaioTipo: string; lacaioQtd: number };
export type DungeonLista = G & {
  id: string; nome: string; descricao: string; distanciaMin: number; distanciaMax: number; nivelRecomendado: number;
  recompensaXp: number; recargaHoras: number; ativa: boolean; andares: G[]; loot: { item: string; qtd: number; chance: number }[];
};

const INF = 100000;
const fmt = (n: number) => n.toLocaleString("pt-BR");
const dist = (n: number) => (n >= INF ? "∞" : fmt(n));

/** Anéis de distância em volta do spawn: mostra que dungeon aparece em cada faixa. */
function Aneis({ dungeons, foco, setFoco }: { dungeons: DungeonLista[]; foco: string | null; setFoco: (id: string | null) => void }) {
  const ativas = dungeons.filter((d) => d.ativa);
  const finitos = ativas.flatMap((d) => [d.distanciaMin, d.distanciaMax]).filter((n) => n < INF);
  const maxR = Math.max(500, ...finitos) * 1.25;
  const R = 150, esc = (n: number) => (Math.min(n, maxR) / maxR) * R;
  // buracos (faixas sem dungeon)
  const ord = [...ativas].sort((a, b) => a.distanciaMin - b.distanciaMin);
  const buracos: [number, number][] = [];
  let ate = 0;
  for (const d of ord) { if (d.distanciaMin > ate) buracos.push([ate, d.distanciaMin]); ate = Math.max(ate, d.distanciaMax); }
  if (ate < INF) buracos.push([ate, INF]);
  const sobrepostas = ord.some((d, i) => i > 0 && d.distanciaMin < ord[i - 1].distanciaMax);

  return (
    <div className="flex flex-col items-center gap-3 md:flex-row md:items-start">
      <svg viewBox="-160 -160 320 320" className="h-72 w-72 shrink-0">
        <defs>
          <radialGradient id="fundoAneis"><stop offset="0%" stopColor="#2a2217" /><stop offset="100%" stopColor="#0f0d0b" /></radialGradient>
        </defs>
        <circle r={R + 4} fill="url(#fundoAneis)" stroke="#5a3a1e" />
        {buracos.map(([a, b], i) => (
          <path key={`b${i}`} fillRule="evenodd" fill="#e05a4a22" stroke="#e05a4a66" strokeDasharray="3 3"
            d={`M ${esc(b)} 0 A ${esc(b)} ${esc(b)} 0 1 0 ${-esc(b)} 0 A ${esc(b)} ${esc(b)} 0 1 0 ${esc(b)} 0 M ${esc(a)} 0 A ${esc(a)} ${esc(a)} 0 1 1 ${-esc(a)} 0 A ${esc(a)} ${esc(a)} 0 1 1 ${esc(a)} 0`} />
        ))}
        {ord.map((d) => {
          const i = dungeons.indexOf(d), cor = CORES_DUNGEON[i % CORES_DUNGEON.length];
          const a = esc(d.distanciaMin), b = esc(d.distanciaMax);
          const ativo = !foco || foco === d.id;
          return (
            <path key={d.id} fillRule="evenodd" fill={cor} fillOpacity={ativo ? 0.35 : 0.08} stroke={cor} strokeOpacity={ativo ? 0.9 : 0.3}
              className="cursor-pointer transition-all" onMouseEnter={() => setFoco(d.id)} onMouseLeave={() => setFoco(null)}
              d={`M ${b} 0 A ${b} ${b} 0 1 0 ${-b} 0 A ${b} ${b} 0 1 0 ${b} 0 M ${a} 0 A ${a} ${a} 0 1 1 ${-a} 0 A ${a} ${a} 0 1 1 ${a} 0`}>
              <title>{`${d.nome}: ${dist(d.distanciaMin)}–${dist(d.distanciaMax)} blocos`}</title>
            </path>
          );
        })}
        {[0.25, 0.5, 0.75, 1].map((f) => (
          <text key={f} x={2} y={-R * f + 9} fontSize={8} fill="#8a7d6a">{fmt(Math.round(maxR * f))}</text>
        ))}
        <circle r={5} fill="#ffd84a" stroke="#000" />
        <text y={16} fontSize={9} textAnchor="middle" fill="#ffd84a">spawn</text>
      </svg>
      <div className="space-y-1.5 text-sm">
        {ord.map((d) => {
          const i = dungeons.indexOf(d);
          return (
            <div key={d.id} onMouseEnter={() => setFoco(d.id)} onMouseLeave={() => setFoco(null)}
              className={`flex items-center gap-2 rounded px-2 py-0.5 ${foco === d.id ? "bg-pedra-800" : ""}`}>
              <span className="h-3 w-3 rounded-sm" style={{ background: CORES_DUNGEON[i % CORES_DUNGEON.length] }} />
              <b>{d.nome}</b><span className="text-pedra-500">{dist(d.distanciaMin)}–{dist(d.distanciaMax)} blocos · Nv {d.nivelRecomendado}+</span>
            </div>
          );
        })}
        {buracos.length > 0 && (
          <p className="mt-2 rounded-md border border-red-500/40 bg-red-950/30 px-2 py-1 text-xs text-red-300">
            ⚠ Sem dungeon em: {buracos.map(([a, b]) => `${dist(a)}–${dist(b)}`).join(", ")} blocos. Quem estiver lá não acha cripta.
          </p>
        )}
        {sobrepostas && <p className="rounded-md border border-yellow-500/40 bg-yellow-950/20 px-2 py-1 text-xs text-yellow-300">⚠ Há faixas sobrepostas: vale a primeira da lista (menor distância mínima).</p>}
        <p className="pt-1 text-xs text-pedra-500">Passe o mouse para destacar. Quanto mais longe do spawn, mais difícil a cripta.</p>
      </div>
    </div>
  );
}

function Barra({ v, max, cor }: { v: number; max: number; cor: string }) {
  return <span className="block h-1.5 w-full overflow-hidden rounded-full bg-pedra-800"><span className="block h-full rounded-full" style={{ width: `${Math.min(100, (v / Math.max(1, max)) * 100)}%`, background: cor }} /></span>;
}

export function ListaDungeons({ dungeons }: { dungeons: DungeonLista[] }) {
  const [foco, setFoco] = useState<string | null>(null);
  const [, iniciar] = useTransition();
  const itens = useMapaItens();
  const maxVida = useMemo(() => Math.max(1, ...dungeons.flatMap((d) => [d.bossVida, ...d.andares.map((a) => a.bossVida)])), [dungeons]);
  const maxDano = useMemo(() => Math.max(1, ...dungeons.flatMap((d) => [d.bossDano, ...d.andares.map((a) => a.bossDano)])), [dungeons]);

  return (
    <>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-titulo text-3xl">Dungeons</h1>
          <p className="max-w-3xl text-pedra-500">
            Catacumbas em 3 camadas: cada camada tem um guardião que dá a chave do Portão Selado, e no fundo fica o guardião final.
            Qual cripta aparece depende da distância do spawn.
          </p>
        </div>
        <Link href="/admin/dungeons/nova" className="shrink-0 rounded-md bg-ouro px-4 py-2 font-semibold text-pedra-950 hover:bg-ouro-escuro">+ Nova dungeon</Link>
      </div>

      {dungeons.length === 0 ? (
        <div className="rounded-xl border border-dashed border-pedra-700 p-10 text-center">
          <p className="mb-4 text-pedra-500">Nenhuma dungeon ainda.</p>
          <form action={criarExemplosDungeon}><button className="rounded-md border border-ouro px-4 py-2 text-ouro hover:bg-ouro hover:text-pedra-950">Criar 3 dungeons de exemplo</button></form>
        </div>
      ) : (
        <>
          <section className="mb-6 rounded-xl border border-pedra-700 bg-pedra-900 p-4">
            <h2 className="mb-3 font-titulo text-lg text-ouro">🧭 Onde cada cripta aparece</h2>
            <Aneis dungeons={dungeons} foco={foco} setFoco={setFoco} />
          </section>

          <div className="grid gap-4 xl:grid-cols-2">
            {dungeons.map((d, i) => {
              const cor = CORES_DUNGEON[i % CORES_DUNGEON.length];
              const av = avaliacao(d.nivelRecomendado, d);
              const camadas = [...d.andares.map((a, k) => ({ ...a, rot: `Camada ${k + 1}` })), { ...d, rot: "Final" }];
              return (
                <div key={d.id} onMouseEnter={() => setFoco(d.id)} onMouseLeave={() => setFoco(null)}
                  className={`overflow-hidden rounded-xl border bg-pedra-900 transition ${foco === d.id ? "border-ouro/70" : "border-pedra-700"} ${d.ativa ? "" : "opacity-55"}`}>
                  <div className="flex items-start gap-3 p-4" style={{ background: `linear-gradient(135deg, ${cor}22, transparent 60%)` }}>
                    <div className="grid h-16 w-16 shrink-0 place-items-center rounded-lg border border-pedra-700 bg-pedra-950/70"><Criatura id={d.bossTipo} tam={44} soIcone /></div>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <h2 className="font-titulo text-xl" style={{ color: cor }}>{d.nome}</h2>
                        <span className="rounded bg-sangue px-2 py-0.5 text-xs font-semibold">Nv {d.nivelRecomendado}+</span>
                        <span className="rounded px-2 py-0.5 text-xs font-semibold text-black/80" style={{ background: av.cor }} title="Estimativa pela vida e dano do guardião final contra um jogador do nível recomendado">{av.texto}</span>
                      </div>
                      <p className="line-clamp-2 text-xs text-pedra-500">{d.descricao || "—"}</p>
                      <p className="mt-1 text-xs text-pedra-500">📍 {dist(d.distanciaMin)}–{dist(d.distanciaMax)} blocos do spawn · ⏳ prêmio a cada {d.recargaHoras}h</p>
                    </div>
                  </div>

                  {/* descida pelas camadas */}
                  <div className="grid grid-cols-3 gap-2 px-4">
                    {camadas.map((g, k) => (
                      <div key={k} className={`relative rounded-lg border p-2 ${k === 2 ? "border-red-500/50 bg-red-950/20" : "border-pedra-700 bg-pedra-950/50"}`}>
                        {k < 2 && <span className="absolute -right-2 top-1/2 z-10 -translate-y-1/2 text-pedra-500">›</span>}
                        <p className="text-[10px] uppercase tracking-wider text-pedra-500">{k === 2 ? "☠ " : "🗝 "}{g.rot}</p>
                        <div className="my-1 flex items-center gap-1.5"><Criatura id={g.bossTipo} tam={26} soIcone /><span className="truncate text-xs font-semibold" title={g.bossNome}>{g.bossNome}</span></div>
                        <div className="space-y-0.5 text-[10px] text-pedra-500">
                          <span className="flex items-center gap-1">❤<Barra v={g.bossVida} max={maxVida} cor="#e05a4a" /><b className="text-pergaminho">{fmt(g.bossVida)}</b></span>
                          <span className="flex items-center gap-1">⚔<Barra v={g.bossDano} max={maxDano} cor="#f0a35d" /><b className="text-pergaminho">{g.bossDano}</b></span>
                        </div>
                        {g.lacaioQtd > 0 && <p className="mt-1 flex items-center gap-1 text-[10px] text-pedra-500">+{g.lacaioQtd}× <Criatura id={g.lacaioTipo} tam={14} soIcone /></p>}
                      </div>
                    ))}
                  </div>

                  <div className="flex flex-wrap items-center gap-2 px-4 pt-3 text-sm">
                    <span className="rounded-full bg-green-900/40 px-2 py-0.5 text-xs text-green-300">✨ {fmt(d.recompensaXp)} XP</span>
                    {d.loot.map((l, k) => (
                      <span key={k} title={`${itens.get(l.item)?.nome ?? l.item} · ${l.chance}% de chance`} className="relative flex items-center gap-1 rounded-full bg-pedra-800 px-1.5 py-0.5 text-xs">
                        <IconeItem id={l.item} tam={18} />×{l.qtd}{l.chance < 100 && <span className="text-[10px] text-yellow-300">{l.chance}%</span>}
                      </span>
                    ))}
                  </div>

                  <div className="mt-3 flex items-center gap-2 border-t border-pedra-800 px-4 py-3 text-xs">
                    <button onClick={() => iniciar(() => alternarDungeonId(d.id))} className="flex items-center gap-1.5">
                      <span className={`relative h-4 w-8 rounded-full ${d.ativa ? "bg-green-500" : "bg-pedra-700"}`}><span className={`absolute top-0.5 h-3 w-3 rounded-full bg-white transition-all ${d.ativa ? "left-[18px]" : "left-0.5"}`} /></span>
                      {d.ativa ? "Ativa" : "Desativada"}
                    </button>
                    <Link href={`/admin/dungeons/${d.id}`} className="ml-auto rounded-md border border-ouro/50 px-2.5 py-1 text-ouro hover:bg-ouro/15">✏️ Editar</Link>
                    <button onClick={() => iniciar(() => duplicarDungeon(d.id))} className="rounded-md border border-pedra-700 px-2.5 py-1 hover:border-ouro">⧉ Duplicar</button>
                    <button onClick={() => { if (confirm(`Excluir "${d.nome}"?`)) iniciar(() => excluirDungeonId(d.id)); }} className="rounded-md border border-pedra-700 px-2.5 py-1 text-red-300 hover:border-red-400">🗑</button>
                  </div>
                </div>
              );
            })}
          </div>
        </>
      )}
    </>
  );
}
