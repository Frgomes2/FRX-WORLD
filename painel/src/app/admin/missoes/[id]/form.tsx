"use client";

import Link from "next/link";
import { useActionState } from "react";
import { salvarMissao } from "../actions";

type Valores = {
  id: string; titulo: string; descricao: string; tipo: string; alvo: string; quantidade: number;
  nivelMin: number; rankMin?: number; recompensaXp: number; recompensaMoedas: number; recompensaItens: string; repetivel: boolean;
  intervaloHoras: number; preRequisitoId: string | null; ativa: boolean; ordem: number;
};

const campo = "mt-1 w-full rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 outline-none focus:border-ouro";

export function FormMissao({ valores, outras }: { valores: Valores; outras: { id: string; titulo: string }[] }) {
  const [estado, acao, salvando] = useActionState(salvarMissao, null);
  return (
    <form action={acao} className="grid max-w-3xl gap-4 rounded-xl border border-pedra-700 bg-pedra-900 p-6 sm:grid-cols-2">
      <input type="hidden" name="id" value={valores.id} />
      <label className="sm:col-span-2">Título<input name="titulo" defaultValue={valores.titulo} required className={campo} /></label>
      <label className="sm:col-span-2">Descrição (texto que o jogador lê no quadro)
        <textarea name="descricao" defaultValue={valores.descricao} rows={2} className={campo} /></label>

      <label>Tipo
        <select name="tipo" defaultValue={valores.tipo} className={campo}>
          <option value="MATAR">Caçar (matar mobs)</option>
          <option value="COLETAR">Coletar e entregar itens</option>
        </select>
      </label>
      <label>Alvo (ID do mob ou item)
        <input name="alvo" defaultValue={valores.alvo} list="alvos" required className={campo} placeholder="minecraft:zombie" />
        <datalist id="alvos">
          {["zombie", "skeleton", "spider", "creeper", "enderman", "witch", "pillager", "drowned", "husk", "blaze",
            "iron_ingot", "gold_ingot", "diamond", "leather", "wheat", "coal", "oak_log", "string", "bone", "rotten_flesh"]
            .map((a) => <option key={a} value={`minecraft:${a}`} />)}
        </datalist>
      </label>
      <label>Quantidade<input name="quantidade" type="number" min={1} defaultValue={valores.quantidade} className={campo} /></label>
      <label>Nível mínimo<input name="nivelMin" type="number" min={1} defaultValue={valores.nivelMin} className={campo} /></label>
      <label>Rank da Guilda
        <select name="rankMin" defaultValue={valores.rankMin ?? 0} className={campo}>
          {["F", "E", "D", "C", "B", "A", "S"].map((r, i) => <option key={r} value={i}>Rank {r}</option>)}
        </select>
      </label>

      <label>Recompensa em XP<input name="recompensaXp" type="number" min={0} defaultValue={valores.recompensaXp} className={campo} /></label>
      <label>Recompensa em moedas (cobre: 10 = 1 prata, 100 = 1 ouro)<input name="recompensaMoedas" type="number" min={0} defaultValue={valores.recompensaMoedas} className={campo} /></label>
      <label>Itens de recompensa
        <input name="recompensaItens" defaultValue={valores.recompensaItens} className={campo} placeholder="minecraft:bread*5, minecraft:emerald*2" /></label>

      <label>Pré-requisito
        <select name="preRequisitoId" defaultValue={valores.preRequisitoId ?? ""} className={campo}>
          <option value="">Nenhum</option>
          {outras.map((o) => <option key={o.id} value={o.id}>{o.titulo}</option>)}
        </select>
      </label>
      <label>Ordem no quadro<input name="ordem" type="number" min={0} defaultValue={valores.ordem} className={campo} /></label>

      <div className="flex flex-wrap items-center gap-6 sm:col-span-2">
        <label className="flex items-center gap-2"><input type="checkbox" name="repetivel" defaultChecked={valores.repetivel} className="h-4 w-4 accent-yellow-600" /> Repetível</label>
        <label className="flex items-center gap-2">a cada
          <input name="intervaloHoras" type="number" min={0} defaultValue={valores.intervaloHoras} className="w-20 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1" /> horas</label>
        <label className="flex items-center gap-2"><input type="checkbox" name="ativa" defaultChecked={valores.ativa} className="h-4 w-4 accent-yellow-600" /> Ativa</label>
      </div>

      {estado?.erro && <p className="text-red-400 sm:col-span-2">{estado.erro}</p>}
      <div className="flex gap-3 sm:col-span-2">
        <button disabled={salvando} className="rounded-md bg-ouro px-5 py-2 font-semibold text-pedra-950 hover:bg-ouro-escuro disabled:opacity-60">
          {salvando ? "Salvando..." : "Salvar"}
        </button>
        <Link href="/admin/missoes" className="rounded-md border border-pedra-700 px-5 py-2 hover:border-ouro">Cancelar</Link>
      </div>
    </form>
  );
}
