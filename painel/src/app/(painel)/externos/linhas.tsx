"use client";

import { useActionState } from "react";
import { salvarChefe, salvarEstrutura, type Estado } from "./actions";

const campo = "w-full rounded border border-pedra-700 bg-pedra-950 px-2 py-1 text-sm";

export type ChefeLinha = {
  id?: string; entidade: string; nome: string; origem: string; nivelRecomendado: number;
  recompensaXp: number; recargaHoras: number; loot: string; ativo: boolean;
};
export type EstruturaLinha = { id?: string; estrutura: string; nome: string; origem: string; nivelRecomendado: number; ativa: boolean };

function Aviso({ estado }: { estado: Estado }) {
  if (!estado) return null;
  return <span className={`text-xs ${estado.erro ? "text-red-400" : "text-green-400"}`}>{estado.erro ?? estado.ok}</span>;
}

export function LinhaChefe({ c }: { c: ChefeLinha }) {
  const [estado, acao, enviando] = useActionState<Estado, FormData>(salvarChefe, null);
  return (
    <form action={acao} className={`grid grid-cols-[1.4fr_1.2fr_0.6fr_0.7fr_0.6fr_1.6fr_auto] items-center gap-2 border-b border-pedra-800 py-2 ${c.ativo ? "" : "opacity-50"}`}>
      {c.id && <input type="hidden" name="id" value={c.id} />}
      <input type="hidden" name="origem" value={c.origem} />
      <input name="entidade" defaultValue={c.entidade} placeholder="cataclysm:ignis" className={`${campo} font-mono text-xs`} readOnly={!!c.id} />
      <input name="nome" defaultValue={c.nome} placeholder="Nome" className={campo} />
      <input name="nivelRecomendado" type="number" defaultValue={c.nivelRecomendado} className={campo} title="Nível recomendado" />
      <input name="recompensaXp" type="number" defaultValue={c.recompensaXp} className={campo} title="XP do FRX" />
      <input name="recargaHoras" type="number" defaultValue={c.recargaHoras} className={campo} title="Recarga (h)" />
      <input name="loot" defaultValue={c.loot} placeholder="minecraft:diamond*2@50" className={`${campo} font-mono text-xs`} />
      <div className="flex items-center gap-2">
        <label className="flex items-center gap-1 text-xs"><input type="checkbox" name="ativo" defaultChecked={c.ativo} /> ativo</label>
        <button disabled={enviando} className="rounded bg-ouro px-2 py-1 text-xs font-semibold text-pedra-950 hover:bg-ouro-escuro">{c.id ? "Salvar" : "Adicionar"}</button>
        <Aviso estado={estado} />
      </div>
    </form>
  );
}

export function LinhaEstrutura({ e }: { e: EstruturaLinha }) {
  const [estado, acao, enviando] = useActionState<Estado, FormData>(salvarEstrutura, null);
  return (
    <form action={acao} className={`grid grid-cols-[1.6fr_1.4fr_0.8fr_0.6fr_auto] items-center gap-2 border-b border-pedra-800 py-2 ${e.ativa ? "" : "opacity-50"}`}>
      {e.id && <input type="hidden" name="id" value={e.id} />}
      <input name="estrutura" defaultValue={e.estrutura} placeholder="cataclysm:acropolis" className={`${campo} font-mono text-xs`} readOnly={!!e.id} />
      <input name="nome" defaultValue={e.nome} placeholder="Nome" className={campo} />
      <input name="origem" defaultValue={e.origem} placeholder="Mod" className={campo} />
      <input name="nivelRecomendado" type="number" defaultValue={e.nivelRecomendado} className={campo} title="Nível recomendado" />
      <div className="flex items-center gap-2">
        <label className="flex items-center gap-1 text-xs"><input type="checkbox" name="ativa" defaultChecked={e.ativa} /> ativa</label>
        <button disabled={enviando} className="rounded bg-ouro px-2 py-1 text-xs font-semibold text-pedra-950 hover:bg-ouro-escuro">{e.id ? "Salvar" : "Adicionar"}</button>
        <Aviso estado={estado} />
      </div>
    </form>
  );
}
