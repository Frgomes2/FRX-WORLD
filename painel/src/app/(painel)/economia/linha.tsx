"use client";

import { useActionState } from "react";
import { salvarItem, type EstadoItem } from "./actions";

type Item = { id: string; loja: string; item: string; qtd: number; preco: number; ordem: number };
const campo = "w-full rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1";

/** Linha editável de uma loja (ou formulário de item novo quando sem id). */
export function FormItem({ valores, loja }: { valores?: Item; loja: string }) {
  const [estado, acao, enviando] = useActionState<EstadoItem, FormData>(salvarItem, null);
  return (
    <form action={acao} className="grid grid-cols-[1fr_70px_110px_70px_auto] items-center gap-2">
      <input type="hidden" name="id" value={valores?.id ?? ""} />
      <input type="hidden" name="loja" value={loja} />
      <input name="item" defaultValue={valores?.item ?? ""} placeholder="minecraft:bread" className={campo} />
      <input name="qtd" type="number" min={1} max={64} defaultValue={valores?.qtd ?? 1} className={campo} title="Quantidade" />
      <input name="preco" type="number" min={1} defaultValue={valores?.preco ?? 10} className={campo} title="Preço em cobre" />
      <input name="ordem" type="number" min={0} defaultValue={valores?.ordem ?? 0} className={campo} title="Ordem" />
      <button disabled={enviando} className="rounded-md border border-ouro px-3 py-1 text-ouro hover:bg-ouro hover:text-pedra-950">
        {valores ? "Salvar" : "Adicionar"}
      </button>
      {estado && "erro" in estado && <p className="col-span-5 text-xs text-red-400">{estado.erro}</p>}
    </form>
  );
}
