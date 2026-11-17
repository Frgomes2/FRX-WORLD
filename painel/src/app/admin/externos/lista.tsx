"use client";

import { useActionState, useMemo, useState } from "react";
import { IconeCriatura, useCriaturas } from "@/components/criaturas";
import { IconeItem, NomeItem, useMapaItens } from "@/components/itens";
import { excluirChefe, excluirEstrutura, salvarChefe, salvarEstrutura, type Estado } from "./actions";

export type Chefe = {
  id?: string; entidade: string; nome: string; origem: string; nivelRecomendado: number;
  recompensaXp: number; recargaHoras: number; loot: string; itens: { item: string; qtd: number; chance: number }[]; ativo: boolean;
};
export type Estrutura = { id?: string; estrutura: string; nome: string; origem: string; nivelRecomendado: number; ativa: boolean };

const campo = "w-full rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1.5 text-sm outline-none focus:border-ouro";
const rotulo = "flex flex-col gap-1 text-xs text-pedra-500";
const fmt = (n: number) => n.toLocaleString("pt-BR");
const corNivel = (n: number) => (n >= 60 ? "border-red-500/60 text-red-300" : n >= 35 ? "border-orange-400/60 text-orange-300" : n >= 15 ? "border-yellow-400/60 text-yellow-200" : "border-green-500/60 text-green-300");
const modDe = (origem: string, id: string) => origem || id.split(":")[0] || "outros";

function Aviso({ estado }: { estado: Estado }) {
  if (!estado) return null;
  return <span className={`text-xs ${estado.erro ? "text-red-400" : "text-green-400"}`}>{estado.erro ?? estado.ok}</span>;
}

function FormChefe({ c, aoFechar }: { c: Chefe; aoFechar?: () => void }) {
  const [estado, acao, enviando] = useActionState<Estado, FormData>(salvarChefe, null);
  return (
    <form action={acao} className="space-y-2">
      {c.id && <input type="hidden" name="id" value={c.id} />}
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-[1.3fr_1.3fr_1fr]">
        <label className={rotulo}>Entidade (mod:nome)<input name="entidade" defaultValue={c.entidade} placeholder="cataclysm:ignis" readOnly={!!c.id} className={`${campo} font-mono text-xs ${c.id ? "opacity-60" : ""}`} /></label>
        <label className={rotulo}>Nome no jogo<input name="nome" defaultValue={c.nome} className={campo} /></label>
        <label className={rotulo}>Mod (para agrupar)<input name="origem" defaultValue={c.origem} placeholder="Cataclysm" className={campo} /></label>
      </div>
      <div className="grid grid-cols-3 gap-2 sm:max-w-md">
        <label className={rotulo}>Nível recomendado<input name="nivelRecomendado" type="number" defaultValue={c.nivelRecomendado} className={campo} /></label>
        <label className={rotulo}>XP do FRX<input name="recompensaXp" type="number" defaultValue={c.recompensaXp} className={campo} /></label>
        <label className={rotulo}>Recarga (horas)<input name="recargaHoras" type="number" defaultValue={c.recargaHoras} className={campo} /></label>
      </div>
      <label className={rotulo}>Loot extra — item*quantidade@chance%, separados por vírgula
        <input name="loot" defaultValue={c.loot} placeholder="minecraft:diamond*2@50, frxworld:moeda_ouro*3" className={`${campo} font-mono text-xs`} /></label>
      <div className="flex flex-wrap items-center gap-3">
        <label className="flex items-center gap-1.5 text-sm"><input type="checkbox" name="ativo" defaultChecked={c.ativo} /> Ativo</label>
        <button disabled={enviando} className="rounded-md bg-ouro px-3 py-1.5 text-sm font-semibold text-pedra-950 hover:bg-ouro-escuro disabled:opacity-50">{c.id ? "Salvar" : "Adicionar chefe"}</button>
        {aoFechar && <button type="button" onClick={aoFechar} className="text-sm text-pedra-500 hover:text-pergaminho">Fechar</button>}
        <Aviso estado={estado} />
      </div>
    </form>
  );
}

function FormEstrutura({ e, aoFechar }: { e: Estrutura; aoFechar?: () => void }) {
  const [estado, acao, enviando] = useActionState<Estado, FormData>(salvarEstrutura, null);
  return (
    <form action={acao} className="flex flex-wrap items-end gap-2">
      {e.id && <input type="hidden" name="id" value={e.id} />}
      <label className={`${rotulo} min-w-52 flex-1`}>Estrutura (mod:nome)<input name="estrutura" defaultValue={e.estrutura} placeholder="cataclysm:acropolis" readOnly={!!e.id} className={`${campo} font-mono text-xs ${e.id ? "opacity-60" : ""}`} /></label>
      <label className={`${rotulo} min-w-40 flex-1`}>Nome<input name="nome" defaultValue={e.nome} className={campo} /></label>
      <label className={`${rotulo} w-32`}>Mod<input name="origem" defaultValue={e.origem} className={campo} /></label>
      <label className={`${rotulo} w-20`}>Nível<input name="nivelRecomendado" type="number" defaultValue={e.nivelRecomendado} className={campo} /></label>
      <label className="flex items-center gap-1.5 pb-2 text-sm"><input type="checkbox" name="ativa" defaultChecked={e.ativa} /> Ativa</label>
      <button disabled={enviando} className="rounded-md bg-ouro px-3 py-1.5 text-sm font-semibold text-pedra-950 hover:bg-ouro-escuro disabled:opacity-50">{e.id ? "Salvar" : "Adicionar"}</button>
      {aoFechar && <button type="button" onClick={aoFechar} className="pb-2 text-sm text-pedra-500 hover:text-pergaminho">Fechar</button>}
      <Aviso estado={estado} />
    </form>
  );
}

function Excluir({ acao, id, nome }: { acao: (f: FormData) => void; id: string; nome: string }) {
  return (
    <form action={acao} onSubmit={(e) => { if (!confirm(`Excluir ${nome}?`)) e.preventDefault(); }}>
      <input type="hidden" name="id" value={id} />
      <button className="text-xs text-red-400 hover:underline">Excluir</button>
    </form>
  );
}

export function ListaExternos({ chefes, estruturas }: { chefes: Chefe[]; estruturas: Estrutura[] }) {
  const [aba, setAba] = useState<"chefes" | "estruturas">("chefes");
  const [busca, setBusca] = useState("");
  const [mod, setMod] = useState("");
  const [aberto, setAberto] = useState<string | null>(null);
  const [novo, setNovo] = useState(false);
  const todasCriaturas = useCriaturas();
  const criaturas = useMemo(() => new Map((todasCriaturas ?? []).map((c) => [c.id, c])), [todasCriaturas]);
  const itens = useMapaItens();

  const mods = useMemo(() => {
    const s = new Set<string>();
    (aba === "chefes" ? chefes.map((c) => modDe(c.origem, c.entidade)) : estruturas.map((e) => modDe(e.origem, e.estrutura))).forEach((m) => s.add(m));
    return [...s].sort((a, b) => a.localeCompare(b));
  }, [aba, chefes, estruturas]);
  const q = busca.trim().toLowerCase();
  const chefesVisiveis = chefes.filter((c) => (!mod || modDe(c.origem, c.entidade) === mod) && (!q || c.nome.toLowerCase().includes(q) || c.entidade.includes(q)));
  const estruturasVisiveis = estruturas.filter((e) => (!mod || modDe(e.origem, e.estrutura) === mod) && (!q || e.nome.toLowerCase().includes(q) || e.estrutura.includes(q)));
  const botaoAba = (id: "chefes" | "estruturas", texto: string) => (
    <button onClick={() => { setAba(id); setMod(""); setAberto(null); setNovo(false); }}
      className={`rounded-md px-3 py-1.5 text-sm ${aba === id ? "bg-pedra-700 text-ouro" : "text-pedra-500 hover:text-pergaminho"}`}>{texto}</button>
  );

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div className="flex rounded-lg border border-pedra-700 bg-pedra-900 p-1">
          {botaoAba("chefes", `👹 Chefes (${chefes.length})`)}
          {botaoAba("estruturas", `🏛️ Estruturas (${estruturas.length})`)}
        </div>
        <input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Buscar por nome ou id…" className={`${campo} w-full sm:w-64`} />
        <select value={mod} onChange={(e) => setMod(e.target.value)} className={`${campo} w-full sm:w-48`}>
          <option value="">Todos os mods</option>
          {mods.map((m) => <option key={m} value={m}>{m}</option>)}
        </select>
        <button onClick={() => setNovo((v) => !v)} className="rounded-md border border-ouro px-3 py-1.5 text-sm text-ouro hover:bg-ouro hover:text-pedra-950 sm:ml-auto">
          {novo ? "Cancelar" : aba === "chefes" ? "+ Novo chefe" : "+ Nova estrutura"}
        </button>
      </div>

      {novo && (
        <section className="mb-4 rounded-xl border border-dashed border-ouro/50 bg-pedra-900 p-4">
          <h2 className="mb-3 font-titulo text-lg text-ouro">{aba === "chefes" ? "Novo chefe" : "Nova estrutura"}</h2>
          {aba === "chefes"
            ? <FormChefe c={{ entidade: "", nome: "", origem: "", nivelRecomendado: 10, recompensaXp: 1000, recargaHoras: 24, loot: "", itens: [], ativo: true }} />
            : <FormEstrutura e={{ estrutura: "", nome: "", origem: "", nivelRecomendado: 10, ativa: true }} />}
        </section>
      )}

      {aba === "chefes" && (
        <div className="grid gap-3 lg:grid-cols-2">
          {chefesVisiveis.map((c) => (
            <section key={c.id} className={`rounded-xl border border-pedra-700 bg-pedra-900 p-4 ${c.ativo ? "" : "opacity-50"} ${aberto === c.id ? "lg:col-span-2" : ""}`}>
              <div className="flex items-start gap-3">
                <span className="grid h-12 w-12 shrink-0 place-items-center rounded-lg bg-pedra-950"><IconeCriatura c={criaturas.get(c.entidade)} id={c.entidade} tam={36} /></span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-titulo text-lg text-ouro">{c.nome}</h3>
                    <span className={`rounded-full border px-2 py-0.5 text-xs ${corNivel(c.nivelRecomendado)}`}>Nv {c.nivelRecomendado}</span>
                    {!c.ativo && <span className="rounded-full bg-pedra-700 px-2 py-0.5 text-xs">desativado</span>}
                  </div>
                  <p className="truncate font-mono text-xs text-pedra-500">{c.entidade} · {modDe(c.origem, c.entidade)}</p>
                  <p className="mt-1.5 text-sm">
                    ✨ <b>{fmt(c.recompensaXp)}</b> XP <span className="text-pedra-500">·</span> ⏳ {c.recargaHoras > 0 ? `recarga de ${c.recargaHoras} h` : "sem recarga"}
                  </p>
                  {c.itens.length > 0 && (
                    <ul className="mt-2 flex flex-wrap gap-1.5">
                      {c.itens.map((i, n) => (
                        <li key={n} className="flex items-center gap-1 rounded-md border border-pedra-700 bg-pedra-950 px-1.5 py-0.5 text-xs" title={i.item}>
                          <IconeItem id={i.item} tam={16} /> {i.qtd > 1 ? `${i.qtd}× ` : ""}<NomeItem id={i.item} mapa={itens} />{i.chance < 100 && <span className="text-pedra-500">{i.chance}%</span>}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
                <div className="flex shrink-0 flex-col items-end gap-2">
                  <button onClick={() => setAberto(aberto === c.id ? null : c.id!)} className="rounded-md border border-pedra-700 px-2 py-1 text-xs hover:border-ouro">{aberto === c.id ? "Fechar" : "Editar"}</button>
                  <Excluir acao={excluirChefe} id={c.id!} nome={c.nome} />
                </div>
              </div>
              {aberto === c.id && <div className="mt-4 border-t border-pedra-800 pt-4"><FormChefe c={c} aoFechar={() => setAberto(null)} /></div>}
            </section>
          ))}
          {chefesVisiveis.length === 0 && <p className="text-sm text-pedra-500">Nenhum chefe {chefes.length ? "com esse filtro" : "cadastrado — use “Carregar sugestões”"}.</p>}
        </div>
      )}

      {aba === "estruturas" && (
        <section className="rounded-xl border border-pedra-700 bg-pedra-900">
          <ul className="divide-y divide-pedra-800">
            {estruturasVisiveis.map((e) => (
              <li key={e.id} className={`p-3 ${e.ativa ? "" : "opacity-50"}`}>
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span className={`w-14 shrink-0 rounded-full border px-2 py-0.5 text-center text-xs ${corNivel(e.nivelRecomendado)}`}>Nv {e.nivelRecomendado}</span>
                  <div className="min-w-0 flex-1">
                    <p>{e.nome} {!e.ativa && <span className="text-xs text-pedra-500">(desativada)</span>}</p>
                    <p className="truncate font-mono text-xs text-pedra-500">{e.estrutura} · {modDe(e.origem, e.estrutura)}</p>
                  </div>
                  <button onClick={() => setAberto(aberto === e.id ? null : e.id!)} className="rounded-md border border-pedra-700 px-2 py-1 text-xs hover:border-ouro">{aberto === e.id ? "Fechar" : "Editar"}</button>
                  <Excluir acao={excluirEstrutura} id={e.id!} nome={e.nome} />
                </div>
                {aberto === e.id && <div className="mt-3 border-t border-pedra-800 pt-3"><FormEstrutura e={e} aoFechar={() => setAberto(null)} /></div>}
              </li>
            ))}
          </ul>
          {estruturasVisiveis.length === 0 && <p className="p-4 text-sm text-pedra-500">Nenhuma estrutura {estruturas.length ? "com esse filtro" : "cadastrada — use “Carregar sugestões”"}.</p>}
        </section>
      )}
    </>
  );
}
