"use client";

import { useActionState } from "react";
import { salvarTaverneiro } from "./actions";

export function FormTaverneiro({ nome, falas }: { nome: string; falas: string[] }) {
  const [estado, acao, salvando] = useActionState(salvarTaverneiro, null);
  return (
    <form action={acao} className="mt-8 rounded-xl border border-pedra-700 bg-pedra-900 p-5">
      <h2 className="font-titulo text-xl text-ouro">Taverneiro</h2>
      <p className="mb-4 text-xs text-pedra-500">
        Ao clicar no taverneiro, o jogador ouve uma destas falas (sorteada) e o quadro de missões abre. Use {"{jogador}"} para o nick.
      </p>
      <div className="grid gap-4 sm:grid-cols-[220px_1fr]">
        <label className="text-sm">Nome
          <input name="nome" defaultValue={nome} className="mt-1 w-full rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 outline-none focus:border-ouro" />
        </label>
        <label className="text-sm">Falas (uma por linha)
          <textarea name="falas" defaultValue={falas.join("\n")} rows={4} className="mt-1 w-full rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 outline-none focus:border-ouro" />
        </label>
      </div>
      <div className="mt-4 flex items-center gap-4">
        <button disabled={salvando} className="rounded-md bg-ouro px-4 py-2 font-semibold text-pedra-950 hover:bg-ouro-escuro disabled:opacity-60">
          {salvando ? "Salvando..." : "Salvar taverneiro"}
        </button>
        {estado && <span className={estado.ok ? "text-green-400" : "text-red-400"}>{estado.mensagem}</span>}
      </div>
    </form>
  );
}
