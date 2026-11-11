"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { SeletorCriaturas } from "@/components/criaturas";
import { CampoItem, CampoMoedas, IconeItem, Moedas } from "@/components/itens";
import { salvarMissaoDados, type DadosMissao } from "../actions";
import { Alvo } from "../alvo";

const rotulo = "mb-1 block text-xs font-semibold uppercase tracking-wider text-pedra-500";
const campo = "w-full rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 outline-none focus:border-ouro";

function Interruptor({ ligado, onChange, texto }: { ligado: boolean; onChange: (v: boolean) => void; texto: string }) {
  return (
    <button type="button" onClick={() => onChange(!ligado)} className="flex items-center gap-2 text-sm">
      <span className={`relative h-5 w-10 rounded-full transition ${ligado ? "bg-green-500" : "bg-pedra-700"}`}><span className={`absolute top-0.5 h-4 w-4 rounded-full bg-white transition-all ${ligado ? "left-[22px]" : "left-0.5"}`} /></span>
      {texto}
    </button>
  );
}

function Caixa({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return <section className="rounded-xl border border-pedra-700 bg-pedra-900 p-4"><h2 className="mb-3 font-titulo text-lg text-ouro">{titulo}</h2>{children}</section>;
}

/** XP sugerido: cresce com o nível e o tamanho da tarefa. */
const sugerirXp = (nivel: number, qtd: number, tipo: string) => Math.round((40 + nivel * nivel * 6) * Math.sqrt(qtd) * (tipo === "MATAR" ? 1 : 0.7) / 3) * 5;

export function EditorMissao({ id, inicial, outras }: { id: string | null; inicial: DadosMissao; outras: { id: string; titulo: string; nivelMin: number }[] }) {
  const [d, setD] = useState<DadosMissao>(inicial);
  const [escolherCriatura, setEscolherCriatura] = useState(false);
  const [erro, setErro] = useState("");
  const [salvando, iniciar] = useTransition();
  const router = useRouter();
  const mudar = <K extends keyof DadosMissao>(k: K, v: DadosMissao[K]) => setD((x) => ({ ...x, [k]: v }));
  const alterado = JSON.stringify(d) !== JSON.stringify(inicial);

  const salvar = () => iniciar(async () => {
    setErro("");
    const r = await salvarMissaoDados(id, d);
    if (!r.ok) { setErro(r.erro ?? "Erro ao salvar"); return; }
    router.push("/missoes");
  });

  const itens = d.recompensaItens;
  const setItens = (l: { item: string; qtd: number }[]) => mudar("recompensaItens", l);

  return (
    <div className="pb-24">
      <div className="mb-5 flex items-center gap-3">
        <Link href="/missoes" className="text-pedra-500 hover:text-ouro">← Missões</Link>
        <h1 className="font-titulo text-3xl">{id ? `Editar: ${inicial.titulo}` : "Nova missão"}</h1>
      </div>

      <div className="grid gap-5 xl:grid-cols-[1fr_380px]">
        <div className="space-y-4">
          <Caixa titulo="📜 Identificação">
            <label className={rotulo}>Título</label>
            <input className={campo} value={d.titulo} maxLength={60} onChange={(e) => mudar("titulo", e.target.value)} placeholder="Ex.: Praga de Zumbis" />
            <label className={`${rotulo} mt-3`}>Descrição (o que o taverneiro conta)</label>
            <textarea className={campo} rows={3} maxLength={300} value={d.descricao} onChange={(e) => mudar("descricao", e.target.value)} placeholder="Os mortos rondam a vila à noite…" />
            <p className="mt-1 text-right text-[11px] text-pedra-500">{d.descricao.length}/300</p>
          </Caixa>

          <Caixa titulo="🎯 Objetivo">
            <div className="mb-4 inline-flex rounded-lg border border-pedra-700 p-1">
              {[["MATAR", "⚔️ Caçar criaturas", "minecraft:zombie"], ["COLETAR", "📦 Coletar e entregar", "minecraft:wheat"]].map(([t, n, padrao]) => (
                <button key={t} type="button" onClick={() => setD((x) => ({ ...x, tipo: t, alvo: x.tipo === t ? x.alvo : padrao }))}
                  className={`rounded-md px-4 py-1.5 text-sm ${d.tipo === t ? "bg-ouro font-semibold text-pedra-950" : "text-pedra-500 hover:text-pergaminho"}`}>{n}</button>
              ))}
            </div>
            <div className="grid gap-4 md:grid-cols-[1fr_160px_160px]">
              <div>
                <label className={rotulo}>{d.tipo === "MATAR" ? "Criatura" : "Item a entregar"}</label>
                {d.tipo === "MATAR" ? (
                  <button type="button" onClick={() => setEscolherCriatura(true)} className="flex w-full items-center gap-3 rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 text-left hover:border-ouro">
                    <Alvo tipo="MATAR" alvo={d.alvo} tam={28} /><span className="ml-auto text-xs text-ouro">trocar ▾</span>
                  </button>
                ) : (
                  <CampoItem valor={d.alvo} onChange={(v) => mudar("alvo", v)} titulo="Item que o jogador deve entregar" />
                )}
              </div>
              <div>
                <label className={rotulo}>Quantidade</label>
                <input type="number" min={1} max={10000} className={campo} value={d.quantidade} onChange={(e) => mudar("quantidade", Math.max(1, +e.target.value || 1))} />
              </div>
              <div>
                <label className={rotulo}>Nível mínimo</label>
                <input type="number" min={1} max={1000} className={campo} value={d.nivelMin} onChange={(e) => mudar("nivelMin", Math.max(1, +e.target.value || 1))} />
              </div>
            </div>
          </Caixa>

          <Caixa titulo="🎁 Recompensas">
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label className={rotulo}>Experiência</label>
                <div className="flex gap-2">
                  <input type="number" min={0} className={campo} value={d.recompensaXp} onChange={(e) => mudar("recompensaXp", Math.max(0, +e.target.value || 0))} />
                  <button type="button" title="Calcula pelo nível e quantidade" onClick={() => mudar("recompensaXp", sugerirXp(d.nivelMin, d.quantidade, d.tipo))}
                    className="shrink-0 rounded-md border border-ouro/50 px-3 text-sm text-ouro hover:bg-ouro/15">✨ Sugerir</button>
                </div>
              </div>
              <div>
                <label className={rotulo}>Moedas</label>
                <CampoMoedas cobre={d.recompensaMoedas} onChange={(c) => mudar("recompensaMoedas", c)} />
              </div>
            </div>
            <label className={`${rotulo} mt-4`}>Itens ({itens.length}/12)</label>
            <div className="space-y-2">
              {itens.map((it, i) => (
                <div key={i} className="flex items-center gap-2">
                  <div className="flex-1"><CampoItem valor={it.item} onChange={(v) => setItens(itens.map((x, j) => (j === i ? { ...x, item: v } : x)))} titulo="Item de recompensa" /></div>
                  <span className="text-pedra-500">×</span>
                  <input type="number" min={1} max={6400} className={`${campo} w-24`} value={it.qtd} onChange={(e) => setItens(itens.map((x, j) => (j === i ? { ...x, qtd: Math.max(1, +e.target.value || 1) } : x)))} />
                  <button type="button" onClick={() => setItens(itens.filter((_, j) => j !== i))} className="rounded-md border border-pedra-700 px-2 py-2 text-red-300 hover:border-red-400">🗑</button>
                </div>
              ))}
              {itens.length < 12 && (
                <button type="button" onClick={() => setItens([...itens, { item: "minecraft:bread", qtd: 1 }])} className="rounded-md border border-dashed border-pedra-700 px-3 py-1.5 text-sm text-pedra-500 hover:border-ouro hover:text-ouro">+ Adicionar item</button>
              )}
            </div>
          </Caixa>

          <Caixa titulo="⚙️ Regras">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-3">
                <Interruptor ligado={d.ativa} onChange={(v) => mudar("ativa", v)} texto={d.ativa ? "Ativa no quadro" : "Pausada (não aparece)"} />
                <Interruptor ligado={d.repetivel} onChange={(v) => mudar("repetivel", v)} texto="Repetível" />
                {d.repetivel && (
                  <div className="flex items-center gap-2 pl-12 text-sm">
                    pode refazer a cada
                    <input type="number" min={0} max={720} className={`${campo} w-20 py-1`} value={d.intervaloHoras} onChange={(e) => mudar("intervaloHoras", Math.max(0, +e.target.value || 0))} />
                    horas
                    {[1, 24, 168].map((h) => <button key={h} type="button" onClick={() => mudar("intervaloHoras", h)} className="rounded border border-pedra-700 px-1.5 text-xs hover:border-ouro">{h === 168 ? "semana" : h === 24 ? "dia" : "1h"}</button>)}
                  </div>
                )}
              </div>
              <div className="space-y-3">
                <div>
                  <label className={rotulo}>Pré-requisito (cadeia)</label>
                  <select className={campo} value={d.preRequisitoId ?? ""} onChange={(e) => mudar("preRequisitoId", e.target.value || null)}>
                    <option value="">— nenhum, livre —</option>
                    {outras.map((o) => <option key={o.id} value={o.id}>Nv {o.nivelMin} · {o.titulo}</option>)}
                  </select>
                </div>
                <div>
                  <label className={rotulo}>Ordem no quadro</label>
                  <input type="number" min={0} max={9999} className={`${campo} w-28`} value={d.ordem} onChange={(e) => mudar("ordem", Math.max(0, +e.target.value || 0))} />
                </div>
              </div>
            </div>
          </Caixa>
        </div>

        <aside className="xl:sticky xl:top-4 xl:self-start">
          <p className={rotulo}>Como aparece no quadro</p>
          <div className="rounded-lg border-4 border-[#5b3a1e] bg-[#e9d9b0] p-5 text-[#3b2a17] shadow-xl" style={{ backgroundImage: "radial-gradient(circle at 20% 10%, #f4e7c4, transparent 60%), radial-gradient(circle at 90% 90%, #d4bf8a, transparent 50%)" }}>
            <div className="mb-1 flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-[#7a5a2e]">
              <span>{d.tipo === "MATAR" ? "⚔ Caçada" : "📦 Encomenda"}</span><span>Nível {d.nivelMin}+</span>
            </div>
            <p className="font-titulo text-2xl leading-tight">{d.titulo || "Sem título"}</p>
            <p className="mt-2 text-sm italic">{d.descricao || "…"}</p>
            <div className="my-3 flex items-center gap-2 rounded bg-[#3b2a17]/10 p-2 text-sm">
              <b>{d.tipo === "MATAR" ? "Derrote" : "Entregue"} {d.quantidade}×</b> <Alvo tipo={d.tipo} alvo={d.alvo} tam={22} />
            </div>
            <p className="text-xs font-semibold uppercase tracking-wider text-[#7a5a2e]">Recompensa</p>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-sm">
              <span>✨ {d.recompensaXp.toLocaleString("pt-BR")} XP</span>
              {d.recompensaMoedas > 0 && <Moedas cobre={d.recompensaMoedas} />}
              {itens.map((i, j) => <span key={j} className="flex items-center gap-0.5"><IconeItem id={i.item} tam={20} />×{i.qtd}</span>)}
            </div>
            <p className="mt-3 border-t border-[#3b2a17]/20 pt-2 text-[11px]">
              {d.repetivel ? `🔁 Repetível a cada ${d.intervaloHoras}h` : "Pode ser feita uma vez"}
              {d.preRequisitoId && ` · requer “${outras.find((o) => o.id === d.preRequisitoId)?.titulo ?? "?"}”`}
            </p>
          </div>
        </aside>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-40 border-t border-pedra-700 bg-pedra-950/95 backdrop-blur left-0 md:left-60">
        <div className="flex flex-wrap items-center gap-3 px-4 py-3 md:px-6">
          {erro ? <span className="text-sm text-red-400">⚠ {erro}</span> : <span className="text-sm text-pedra-500">{alterado ? "Alterações não salvas" : "Sem alterações"}</span>}
          <Link href="/missoes" className="ml-auto rounded-md border border-pedra-700 px-4 py-2 text-sm hover:border-ouro">Cancelar</Link>
          <button onClick={salvar} disabled={salvando} className="rounded-md bg-ouro px-5 py-2 font-semibold text-pedra-950 hover:bg-ouro-escuro disabled:opacity-50">{salvando ? "Salvando…" : "💾 Salvar missão"}</button>
        </div>
      </div>

      {escolherCriatura && (
        <SeletorCriaturas titulo="Qual criatura caçar?" unico selecionadas={[d.alvo]} aoMudar={(ids) => ids[0] && mudar("alvo", ids[0])} aoFechar={() => setEscolherCriatura(false)} />
      )}
    </div>
  );
}
