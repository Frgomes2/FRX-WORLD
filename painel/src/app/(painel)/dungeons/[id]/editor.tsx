"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { SeletorCriaturas } from "@/components/criaturas";
import { CampoItem } from "@/components/itens";
import { salvarDungeonDados, type DadosDungeon } from "../actions";
import { avaliacao, Criatura, poder } from "../comum";

type G = { bossTipo: string; bossNome: string; bossVida: number; bossDano: number; bossVelocidade: number; lacaioTipo: string; lacaioQtd: number };
type Dados = Omit<DadosDungeon, "andares" | "loot"> & { andares: G[]; loot: { item: string; qtd: number; chance: number }[] } & G;

const INF = 100000;
const rotulo = "mb-1 block text-xs font-semibold uppercase tracking-wider text-pedra-500";
const campo = "w-full rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 outline-none focus:border-ouro";

function Caixa({ titulo, children, extra }: { titulo: string; children: React.ReactNode; extra?: React.ReactNode }) {
  return (
    <section className="rounded-xl border border-pedra-700 bg-pedra-900 p-4">
      <div className="mb-3 flex items-center gap-2"><h2 className="font-titulo text-lg text-ouro">{titulo}</h2><div className="ml-auto">{extra}</div></div>
      {children}
    </section>
  );
}

function Num({ valor, onChange, min = 0, max = 100000, passo = 1, sufixo }: { valor: number; onChange: (n: number) => void; min?: number; max?: number; passo?: number; sufixo?: string }) {
  return (
    <div className="flex items-center gap-1">
      <input type="number" min={min} max={max} step={passo} className={campo} value={valor}
        onChange={(e) => onChange(Math.min(max, Math.max(min, Number(e.target.value) || 0)))} />
      {sufixo && <span className="shrink-0 text-xs text-pedra-500">{sufixo}</span>}
    </div>
  );
}

function BotaoCriatura({ id, onClick }: { id: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="flex w-full items-center gap-2 rounded-md border border-pedra-700 bg-pedra-950 px-3 py-1.5 text-left hover:border-ouro">
      <Criatura id={id} tam={26} /><span className="ml-auto text-xs text-ouro">trocar ▾</span>
    </button>
  );
}

/** Editor de um guardião (camada 1, 2 ou final). */
function Guardiao({ g, onChange, escolher }: { g: G; onChange: (g: G) => void; escolher: (campo: "bossTipo" | "lacaioTipo") => void }) {
  const m = <K extends keyof G>(k: K, v: G[K]) => onChange({ ...g, [k]: v });
  return (
    <div className="grid gap-3 md:grid-cols-2">
      <div><label className={rotulo}>Nome do guardião</label><input className={campo} maxLength={40} value={g.bossNome} onChange={(e) => m("bossNome", e.target.value)} /></div>
      <div><label className={rotulo}>Criatura</label><BotaoCriatura id={g.bossTipo} onClick={() => escolher("bossTipo")} /></div>
      <div><label className={rotulo}>❤ Vida</label><Num valor={g.bossVida} min={1} onChange={(n) => m("bossVida", Math.round(n))} /></div>
      <div><label className={rotulo}>⚔ Dano por golpe</label><Num valor={g.bossDano} max={1000} passo={0.5} onChange={(n) => m("bossDano", n)} /></div>
      <div>
        <label className={rotulo}>💨 Velocidade ×{g.bossVelocidade.toFixed(2)}</label>
        <input type="range" min={0.2} max={3} step={0.05} value={g.bossVelocidade} onChange={(e) => m("bossVelocidade", Number(e.target.value))} className="w-full accent-yellow-600" />
        <div className="flex justify-between text-[10px] text-pedra-500"><span>lento</span><span>normal</span><span>muito rápido</span></div>
      </div>
      <div>
        <label className={rotulo}>Lacaios: {g.lacaioQtd}</label>
        <div className="flex items-center gap-2">
          <input type="range" min={0} max={30} value={g.lacaioQtd} onChange={(e) => m("lacaioQtd", Number(e.target.value))} className="w-28 accent-yellow-600" />
          <div className="flex-1"><BotaoCriatura id={g.lacaioTipo} onClick={() => escolher("lacaioTipo")} /></div>
        </div>
      </div>
    </div>
  );
}

export function EditorDungeon({ id, inicial, outras }: { id: string | null; inicial: Dados; outras: { id: string; nome: string; distanciaMin: number; distanciaMax: number; ativa: boolean }[] }) {
  const [d, setD] = useState<Dados>(inicial);
  const [camada, setCamada] = useState(2);   // 0, 1 = camadas; 2 = final
  const [escolhendo, setEscolhendo] = useState<{ camada: number; campo: "bossTipo" | "lacaioTipo" } | null>(null);
  const [erro, setErro] = useState("");
  const [salvando, iniciar] = useTransition();
  const router = useRouter();
  const mudar = <K extends keyof Dados>(k: K, v: Dados[K]) => setD((x) => ({ ...x, [k]: v }));
  const alterado = JSON.stringify(d) !== JSON.stringify(inicial);

  const final: G = { bossTipo: d.bossTipo, bossNome: d.bossNome, bossVida: d.bossVida, bossDano: d.bossDano, bossVelocidade: d.bossVelocidade, lacaioTipo: d.lacaioTipo, lacaioQtd: d.lacaioQtd };
  const camadas = [d.andares[0], d.andares[1], final];
  const setCamadaG = (k: number, g: G) => {
    if (k === 2) setD((x) => ({ ...x, ...g }));
    else setD((x) => ({ ...x, andares: x.andares.map((a, j) => (j === k ? g : a)) }));
  };
  const derivar = () => setD((x) => ({
    ...x,
    andares: [0.4, 0.6].map((f, k) => ({
      ...x.andares[k], bossVida: Math.max(1, Math.round(x.bossVida * f)), bossDano: Math.round(x.bossDano * (0.55 + 0.2 * k) * 2) / 2,
      bossVelocidade: x.bossVelocidade, lacaioTipo: x.lacaioTipo, lacaioQtd: Math.max(0, Math.round(x.lacaioQtd * (0.5 + 0.25 * k))),
    })),
  }));
  const sugerirXp = () => mudar("recompensaXp", Math.round((150 + d.nivelRecomendado ** 2 * 5) * (1 + d.bossVida / 400) / 50) * 50);

  const infinito = d.distanciaMax >= INF;
  const conflito = outras.filter((o) => o.ativa && o.distanciaMin < d.distanciaMax && d.distanciaMin < o.distanciaMax);
  const av = avaliacao(d.nivelRecomendado, final);
  const maxPoder = Math.max(...camadas.map(poder));

  const salvar = () => iniciar(async () => {
    setErro("");
    const r = await salvarDungeonDados(id, d);
    if (!r.ok) { setErro(r.erro ?? "Erro"); return; }
    router.push("/dungeons");
  });

  return (
    <div className="pb-24">
      <div className="mb-5 flex items-center gap-3">
        <Link href="/dungeons" className="text-pedra-500 hover:text-ouro">← Dungeons</Link>
        <h1 className="font-titulo text-3xl">{id ? `Editar: ${inicial.nome}` : "Nova dungeon"}</h1>
      </div>

      <div className="grid gap-5 xl:grid-cols-[1fr_340px]">
        <div className="space-y-4">
          <Caixa titulo="🏚 Cripta">
            <div className="grid gap-3 md:grid-cols-[1fr_140px_160px]">
              <div><label className={rotulo}>Nome</label><input className={campo} maxLength={60} value={d.nome} onChange={(e) => mudar("nome", e.target.value)} placeholder="Ex.: Cripta dos Esquecidos" /></div>
              <div><label className={rotulo}>Nível recomendado</label><Num valor={d.nivelRecomendado} min={1} max={1000} onChange={(n) => mudar("nivelRecomendado", Math.round(n))} /></div>
              <div><label className={rotulo}>Prêmio a cada</label><Num valor={d.recargaHoras} max={720} sufixo="horas" onChange={(n) => mudar("recargaHoras", Math.round(n))} /></div>
            </div>
            <label className={`${rotulo} mt-3`}>Descrição (aparece ao entrar)</label>
            <textarea className={campo} rows={2} maxLength={300} value={d.descricao} onChange={(e) => mudar("descricao", e.target.value)} />
          </Caixa>

          <Caixa titulo="📍 Onde aparece">
            <div className="grid items-end gap-3 md:grid-cols-[1fr_1fr_auto]">
              <div><label className={rotulo}>De (blocos do spawn)</label><Num valor={d.distanciaMin} max={99999} onChange={(n) => mudar("distanciaMin", Math.round(n))} /></div>
              <div><label className={rotulo}>Até</label>
                {infinito ? <div className={`${campo} text-pedra-500`}>∞ sem limite</div> : <Num valor={d.distanciaMax} min={1} max={99999} onChange={(n) => mudar("distanciaMax", Math.round(n))} />}
              </div>
              <label className="flex items-center gap-2 pb-2 text-sm"><input type="checkbox" checked={infinito} onChange={(e) => mudar("distanciaMax", e.target.checked ? INF : Math.max(d.distanciaMin + 500, 1000))} className="h-4 w-4 accent-yellow-600" />até o fim do mundo</label>
            </div>
            {conflito.length > 0 && <p className="mt-2 text-xs text-yellow-300">⚠ Sobrepõe {conflito.map((o) => `“${o.nome}” (${o.distanciaMin}–${o.distanciaMax >= INF ? "∞" : o.distanciaMax})`).join(", ")}. Nessa faixa vale a de menor distância mínima.</p>}
          </Caixa>

          <Caixa titulo="⛓ Camadas e guardiões" extra={<button type="button" onClick={derivar} className="rounded-md border border-pedra-700 px-2.5 py-1 text-xs hover:border-ouro" title="Camada 1 = 40% e camada 2 = 60% da vida do final">✨ Gerar camadas 1 e 2 a partir do final</button>}>
            <div className="mb-4 grid grid-cols-3 gap-2">
              {camadas.map((g, k) => (
                <button key={k} type="button" onClick={() => setCamada(k)}
                  className={`relative rounded-lg border p-2 text-left transition ${camada === k ? "border-ouro bg-ouro/10" : "border-pedra-700 bg-pedra-950/50 hover:border-pedra-500"}`}>
                  <p className="text-[10px] uppercase tracking-wider text-pedra-500">{k === 2 ? "☠ Guardião final" : `🗝 Camada ${k + 1}`}</p>
                  <div className="mt-1 flex items-center gap-1.5"><Criatura id={g.bossTipo} tam={24} soIcone /><span className="truncate text-sm font-semibold">{g.bossNome || "—"}</span></div>
                  <span className="mt-1.5 block h-1 rounded-full bg-pedra-800"><span className="block h-full rounded-full bg-red-500" style={{ width: `${(poder(g) / maxPoder) * 100}%` }} /></span>
                </button>
              ))}
            </div>
            <p className="mb-3 text-xs text-pedra-500">
              {camada === 2 ? "No fundo da cripta. Derrotado, entrega o prêmio final a quem estiver por perto."
                : `Fica na antecâmara antes do Portão Selado. Derrotado, dá a Chave da Camada ${camada + 1} a cada jogador por perto.`}
            </p>
            <Guardiao key={camada} g={camadas[camada]} onChange={(g) => setCamadaG(camada, g)} escolher={(c) => setEscolhendo({ camada, campo: c })} />
            {camadas[0] && poder(camadas[0]) > poder(camadas[1]) && <p className="mt-3 text-xs text-yellow-300">⚠ A camada 1 está mais forte que a camada 2.</p>}
            {poder(camadas[1]) > poder(final) && <p className="mt-1 text-xs text-yellow-300">⚠ A camada 2 está mais forte que o guardião final.</p>}
          </Caixa>

          <Caixa titulo="🎁 Prêmio final">
            <label className={rotulo}>Experiência</label>
            <div className="flex max-w-sm gap-2">
              <Num valor={d.recompensaXp} max={10_000_000} onChange={(n) => mudar("recompensaXp", Math.round(n))} />
              <button type="button" onClick={sugerirXp} className="shrink-0 rounded-md border border-ouro/50 px-3 text-sm text-ouro hover:bg-ouro/15">✨ Sugerir</button>
            </div>
            <label className={`${rotulo} mt-4`}>Itens ({d.loot.length}/30) — cada um com a sua chance</label>
            <div className="space-y-2">
              {d.loot.map((l, i) => (
                <div key={i} className="grid grid-cols-2 items-center gap-2 sm:grid-cols-[1fr_80px_170px_auto]">
                  <CampoItem valor={l.item} onChange={(v) => mudar("loot", d.loot.map((x, j) => (j === i ? { ...x, item: v } : x)))} titulo="Item do prêmio" />
                  <Num valor={l.qtd} min={1} max={6400} sufixo="×" onChange={(n) => mudar("loot", d.loot.map((x, j) => (j === i ? { ...x, qtd: Math.round(n) || 1 } : x)))} />
                  <div className="flex items-center gap-2">
                    <input type="range" min={1} max={100} value={l.chance} onChange={(e) => mudar("loot", d.loot.map((x, j) => (j === i ? { ...x, chance: Number(e.target.value) } : x)))} className="w-full accent-yellow-600" />
                    <span className={`w-10 text-right text-xs ${l.chance < 25 ? "text-purple-300" : l.chance < 100 ? "text-yellow-300" : "text-green-300"}`}>{l.chance}%</span>
                  </div>
                  <button type="button" onClick={() => mudar("loot", d.loot.filter((_, j) => j !== i))} className="rounded-md border border-pedra-700 px-2 py-2 text-red-300 hover:border-red-400">🗑</button>
                </div>
              ))}
              {d.loot.length < 30 && (
                <button type="button" onClick={() => mudar("loot", [...d.loot, { item: "minecraft:diamond", qtd: 1, chance: 100 }])}
                  className="rounded-md border border-dashed border-pedra-700 px-3 py-1.5 text-sm text-pedra-500 hover:border-ouro hover:text-ouro">+ Adicionar item</button>
              )}
            </div>
          </Caixa>
        </div>

        <aside className="space-y-3 xl:sticky xl:top-4 xl:self-start">
          <div className="rounded-xl border border-pedra-700 bg-gradient-to-b from-[#1a1410] to-pedra-950 p-4">
            <p className="text-xs uppercase tracking-wider text-pedra-500">Prévia</p>
            <p className="font-titulo text-2xl text-ouro">{d.nome || "Sem nome"}</p>
            <p className="text-xs text-pedra-500">Nv {d.nivelRecomendado}+ · {d.distanciaMin}–{infinito ? "∞" : d.distanciaMax} blocos</p>
            <div className="relative mt-3 space-y-2 border-l-2 border-dashed border-pedra-700 pl-4">
              {camadas.map((g, k) => (
                <div key={k} className="relative">
                  <span className={`absolute -left-[23px] top-2 h-3 w-3 rounded-full border-2 ${k === 2 ? "border-red-400 bg-red-900" : "border-ouro bg-pedra-900"}`} />
                  <p className="text-[10px] uppercase tracking-wider text-pedra-500">{k === 2 ? "Fundo · guardião final" : `Camada ${k + 1}`}</p>
                  <div className="flex items-center gap-2"><Criatura id={g.bossTipo} tam={30} soIcone />
                    <div className="text-xs"><b className="text-sm">{g.bossNome}</b><br />❤ {g.bossVida} · ⚔ {g.bossDano}{g.lacaioQtd > 0 && <> · +{g.lacaioQtd} lacaios</>}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="rounded-xl border border-pedra-700 bg-pedra-900 p-4">
            <p className="text-xs uppercase tracking-wider text-pedra-500">Dificuldade estimada (guardião final × jogador Nv {d.nivelRecomendado})</p>
            <p className="mt-1 font-titulo text-2xl" style={{ color: av.cor }}>{av.texto}</p>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-gradient-to-r from-green-500 via-sky-500 via-60% to-red-500">
              <div className="relative h-full" style={{ marginLeft: `${Math.min(97, (Math.log10(av.r * 10 + 1) / Math.log10(51)) * 100)}%`, width: 4, background: "#fff", boxShadow: "0 0 4px #000" }} />
            </div>
            <p className="mt-2 text-[11px] text-pedra-500">É só uma referência pela vida e dano; equipamento, grupo e habilidades mudam muito.</p>
          </div>
        </aside>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-40 border-t border-pedra-700 bg-pedra-950/95 backdrop-blur left-0 md:left-60">
        <div className="flex flex-wrap items-center gap-3 px-4 py-3 md:px-6">
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={d.ativa} onChange={(e) => mudar("ativa", e.target.checked)} className="h-4 w-4 accent-yellow-600" />Ativa</label>
          {erro ? <span className="text-sm text-red-400">⚠ {erro}</span> : <span className="text-sm text-pedra-500">{alterado ? "Alterações não salvas" : "Sem alterações"}</span>}
          <Link href="/dungeons" className="ml-auto rounded-md border border-pedra-700 px-4 py-2 text-sm hover:border-ouro">Cancelar</Link>
          <button onClick={salvar} disabled={salvando} className="rounded-md bg-ouro px-5 py-2 font-semibold text-pedra-950 hover:bg-ouro-escuro disabled:opacity-50">{salvando ? "Salvando…" : "💾 Salvar dungeon"}</button>
        </div>
      </div>

      {escolhendo && (
        <SeletorCriaturas titulo={escolhendo.campo === "bossTipo" ? "Criatura do guardião" : "Criatura dos lacaios"} unico
          selecionadas={[camadas[escolhendo.camada][escolhendo.campo]]}
          aoMudar={(ids) => { if (ids[0]) setCamadaG(escolhendo.camada, { ...camadas[escolhendo.camada], [escolhendo.campo]: ids[0] }); }}
          aoFechar={() => setEscolhendo(null)} />
      )}
    </div>
  );
}
