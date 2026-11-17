"use client";

import { useActionState } from "react";
import { salvarUrgentes } from "./actions";

const campo = "mt-1 w-full rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 outline-none focus:border-ouro";

export function FormUrgentes({ valores }: { valores: { ativo: boolean; intervaloMin: number; duracaoMin: number; multiplicador: number } }) {
  const [estado, acao, salvando] = useActionState(salvarUrgentes, null);
  return (
    <form action={acao} className="mt-8 rounded-xl border border-pedra-700 bg-pedra-900 p-5">
      <h2 className="font-titulo text-xl text-ouro">Missões urgentes</h2>
      <p className="mb-4 text-xs text-pedra-500">
        De tempos em tempos a Guilda marca uma caçada do quadro como urgente. O Sistema avisa quem tem nível e rank para ela,
        e quem entregar dentro do prazo recebe XP e moedas multiplicados. Depois do prazo a missão continua valendo, com a recompensa normal.
      </p>
      <div className="grid gap-4 sm:grid-cols-4">
        <label className="flex items-center gap-2 self-end pb-2 text-sm">
          <input type="checkbox" name="ativo" defaultChecked={valores.ativo} className="h-4 w-4 accent-yellow-600" /> Ligadas
        </label>
        <label className="text-sm">Intervalo entre urgentes (min)
          <input name="intervaloMin" type="number" min={5} max={1440} defaultValue={valores.intervaloMin} className={campo} />
        </label>
        <label className="text-sm">Prazo para entregar (min)
          <input name="duracaoMin" type="number" min={3} max={240} defaultValue={valores.duracaoMin} className={campo} />
        </label>
        <label className="text-sm">Multiplicador da recompensa
          <input name="multiplicador" type="number" min={1} max={10} step={0.5} defaultValue={valores.multiplicador} className={campo} />
        </label>
      </div>
      <div className="mt-4 flex items-center gap-4">
        <button disabled={salvando} className="rounded-md bg-ouro px-4 py-2 font-semibold text-pedra-950 hover:bg-ouro-escuro disabled:opacity-60">
          {salvando ? "Salvando..." : "Salvar urgentes"}
        </button>
        {estado && <span className={estado.ok ? "text-green-400" : "text-red-400"}>{estado.mensagem}</span>}
        <span className="text-xs text-pedra-500">Para testar na hora, no jogo: /frx urgente</span>
      </div>
    </form>
  );
}
