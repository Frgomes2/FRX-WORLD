"use client";

import { useActionState } from "react";
import { salvarMasmorra, type EstadoMasmorra } from "./actions";

type Masmorra = {
  id: string; nome: string; chefe: string; estrutura: string; x: number; z: number;
  entradaX: number | null; entradaY: number | null; entradaZ: number | null; ordem: number; nivel?: number;
};

const campo = "w-full rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 outline-none focus:border-ouro";
const num = "w-20 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 text-right outline-none focus:border-ouro";

/** Formulário de uma masmorra (ou de uma nova, sem valores). */
export function FormMasmorra({ valores }: { valores?: Masmorra }) {
  const [estado, acao, enviando] = useActionState<EstadoMasmorra, FormData>(salvarMasmorra, null);
  const v = valores;
  return (
    <form action={acao} className="space-y-2 text-sm">
      <input type="hidden" name="original" value={v?.id ?? ""} />
      <div className="grid gap-2 sm:grid-cols-[180px_1fr_200px]">
        <label className="flex flex-col gap-1 text-xs text-pedra-500">Id<input name="id" defaultValue={v?.id ?? ""} placeholder="sarion-gelo" className={campo} /></label>
        <label className="flex flex-col gap-1 text-xs text-pedra-500">Nome<input name="nome" defaultValue={v?.nome ?? ""} className={campo} /></label>
        <label className="flex flex-col gap-1 text-xs text-pedra-500">Chefe (só texto)<input name="chefe" defaultValue={v?.chefe ?? ""} className={campo} /></label>
      </div>
      <label className="flex flex-col gap-1 text-xs text-pedra-500">
        Estrutura (ids separados por | — vale a primeira que existir)
        <input name="estrutura" defaultValue={v?.estrutura ?? ""} placeholder="cataclysm:frosted_prison|dungeons_arise:keep_kayra" className={campo} />
      </label>
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-xs text-pedra-500">X<input name="x" defaultValue={v?.x ?? ""} className={num} /></label>
        <label className="flex flex-col gap-1 text-xs text-pedra-500">Z<input name="z" defaultValue={v?.z ?? ""} className={num} /></label>
        <span className="w-full pb-1 text-xs text-pedra-500 sm:w-auto">Entrada (opcional, poço com escada):</span>
        <label className="flex flex-col gap-1 text-xs text-pedra-500">X<input name="entradaX" defaultValue={v?.entradaX ?? ""} className={num} /></label>
        <label className="flex flex-col gap-1 text-xs text-pedra-500">Y<input name="entradaY" defaultValue={v?.entradaY ?? ""} className={num} /></label>
        <label className="flex flex-col gap-1 text-xs text-pedra-500">Z<input name="entradaZ" defaultValue={v?.entradaZ ?? ""} className={num} /></label>
        <label className="flex flex-col gap-1 text-xs text-pedra-500">Ordem<input name="ordem" defaultValue={v?.ordem ?? 0} className={num} /></label>
        <label className="flex flex-col gap-1 text-xs text-pedra-500" title="Nível fixo dos monstros dentro da masmorra. 0 = segue o nível da zona.">Nível fixo<input name="nivel" defaultValue={v?.nivel ?? 0} className={num} /></label>
        <button disabled={enviando} className="rounded-md border border-ouro px-3 py-1 text-ouro hover:bg-ouro hover:text-pedra-950">
          {v ? "Salvar" : "Adicionar"}
        </button>
        {estado && "erro" in estado && <span className="text-xs text-red-400">{estado.erro}</span>}
        {estado && "ok" in estado && <span className="text-xs text-green-400">Salvo.</span>}
      </div>
    </form>
  );
}
