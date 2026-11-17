"use client";

import { useActionState } from "react";
import { salvarConfig, salvarEvento, type Estado } from "./actions";
import type { ConfigEventos } from "@/lib/eventos";

const campo = "w-full rounded border border-pedra-700 bg-pedra-950 px-2 py-1 text-sm";
const rotulo = "mb-1 block text-xs text-pedra-500";

const TIPOS: Record<string, string> = { EMBOSCADA: "Emboscada", CHEFE: "Chefe errante", MERCADOR: "Mercador raro", TESOURO: "Tesouro" };

export type EventoForm = {
  id?: string; nome: string; tipo: string; mensagem: string; peso: number; nivelMin: number; nivelMax: number;
  mobTipo: string; mobQtd: number; vida: number; dano: number; preco: number; recompensaXp: number; loot: string; ativo: boolean;
};

function Aviso({ estado }: { estado: Estado }) {
  if (!estado) return null;
  return <span className={`text-xs ${estado.erro ? "text-red-400" : "text-green-400"}`}>{estado.erro ?? estado.ok}</span>;
}

export function FormConfig({ c }: { c: ConfigEventos }) {
  const [estado, acao, enviando] = useActionState<Estado, FormData>(salvarConfig, null);
  return (
    <form action={acao} className="grid gap-3 md:grid-cols-6">
      <label className="flex items-center gap-2 text-sm md:col-span-6"><input type="checkbox" name="ativo" defaultChecked={c.ativo} /> Eventos ligados</label>
      <div><span className={rotulo}>Checar a cada (s)</span><input name="checagemSegundos" type="number" defaultValue={c.checagemSegundos} className={campo} /></div>
      <div><span className={rotulo}>Chance por checagem (%)</span><input name="chance" type="number" step="0.1" defaultValue={c.chance} className={campo} /></div>
      <div><span className={rotulo}>Recarga por jogador (min)</span><input name="recargaMinutos" type="number" defaultValue={c.recargaMinutos} className={campo} /></div>
      <div><span className={rotulo}>Sem eventos até (blocos do spawn)</span><input name="distanciaSpawn" type="number" defaultValue={c.distanciaSpawn} className={campo} /></div>
      <div><span className={rotulo}>Precisa ter andado (blocos)</span><input name="andarMinimo" type="number" defaultValue={c.andarMinimo} className={campo} /></div>
      <div className="flex items-end gap-2">
        <button disabled={enviando} className="rounded bg-ouro px-3 py-1.5 text-sm font-semibold text-pedra-950 hover:bg-ouro-escuro">Salvar</button>
        <Aviso estado={estado} />
      </div>
    </form>
  );
}

export function FormEvento({ e }: { e: EventoForm }) {
  const [estado, acao, enviando] = useActionState<Estado, FormData>(salvarEvento, null);
  return (
    <form action={acao} className={`grid gap-2 md:grid-cols-12 ${e.ativo ? "" : "opacity-50"}`}>
      {e.id && <input type="hidden" name="id" value={e.id} />}
      <div className="md:col-span-3"><span className={rotulo}>Nome</span><input name="nome" defaultValue={e.nome} className={campo} /></div>
      <div className="md:col-span-2"><span className={rotulo}>Tipo</span>
        <select name="tipo" defaultValue={e.tipo} className={campo}>
          {Object.entries(TIPOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </div>
      <div className="md:col-span-1"><span className={rotulo}>Peso</span><input name="peso" type="number" defaultValue={e.peso} className={campo} title="Quanto maior, mais frequente" /></div>
      <div className="md:col-span-1"><span className={rotulo}>Nível de</span><input name="nivelMin" type="number" defaultValue={e.nivelMin} className={campo} /></div>
      <div className="md:col-span-1"><span className={rotulo}>até</span><input name="nivelMax" type="number" defaultValue={e.nivelMax} className={campo} /></div>
      <div className="md:col-span-2"><span className={rotulo}>Mob</span><input name="mobTipo" defaultValue={e.mobTipo} placeholder="minecraft:pillager" className={`${campo} font-mono text-xs`} /></div>
      <div className="md:col-span-2"><span className={rotulo}>Qtd (chefe: lacaios)</span><input name="mobQtd" type="number" defaultValue={e.mobQtd} className={campo} /></div>
      <div className="md:col-span-6"><span className={rotulo}>Mensagem</span><input name="mensagem" defaultValue={e.mensagem} className={campo} /></div>
      <div className="md:col-span-1"><span className={rotulo}>Vida</span><input name="vida" type="number" defaultValue={e.vida} className={campo} title="0 = normal" /></div>
      <div className="md:col-span-1"><span className={rotulo}>Dano</span><input name="dano" type="number" step="0.5" defaultValue={e.dano} className={campo} title="0 = normal" /></div>
      <div className="md:col-span-1"><span className={rotulo}>Preço</span><input name="preco" type="number" defaultValue={e.preco} className={campo} title="Mercador: esmeraldas por item" /></div>
      <div className="md:col-span-1"><span className={rotulo}>XP</span><input name="recompensaXp" type="number" defaultValue={e.recompensaXp} className={campo} /></div>
      <div className="md:col-span-2 flex items-end gap-2 text-xs"><label className="flex items-center gap-1"><input type="checkbox" name="ativo" defaultChecked={e.ativo} /> ativo</label></div>
      <div className="md:col-span-10"><span className={rotulo}>Loot / itens do mercador (item*qtd@chance)</span><input name="loot" defaultValue={e.loot} className={`${campo} font-mono text-xs`} /></div>
      <div className="flex items-end gap-2 md:col-span-2">
        <button disabled={enviando} className="rounded bg-ouro px-3 py-1.5 text-sm font-semibold text-pedra-950 hover:bg-ouro-escuro">{e.id ? "Salvar" : "Adicionar"}</button>
        <Aviso estado={estado} />
      </div>
    </form>
  );
}
