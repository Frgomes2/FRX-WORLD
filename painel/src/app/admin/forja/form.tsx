"use client";

import { useActionState } from "react";
import type { ConfigJogo } from "@/lib/config-jogo";
import { salvarForja } from "./actions";

const campo = "w-24 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 text-right outline-none focus:border-ouro";
const campoId = "w-full min-w-0 sm:w-56 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 outline-none focus:border-ouro";

function cobre(c: number) {
  const o = Math.floor(c / 100), p = Math.floor((c % 100) / 10), r = c % 10;
  return [o && `${o} ouro`, p && `${p} prata`, r && `${r} cobre`].filter(Boolean).join(" ") || "grátis";
}

export function FormForja({ forja }: { forja: ConfigJogo["forja"] }) {
  const [estado, acao, salvando] = useActionState(salvarForja, null);
  const r = forja.reforja;

  return (
    <form action={acao} className="space-y-5">
      <section className="rounded-xl border border-pedra-700 bg-pedra-900 p-5">
        <h2 className="font-titulo text-xl text-ouro">Aprimorar (+1 a +10)</h2>
        <p className="mb-3 text-xs text-pedra-500">
          Custo e chance de cada nível. Preço em <b>cobre</b> antes do desconto de reputação. Se falhar, o jogador perde o pagamento
          mas a arma fica como estava.
        </p>
        <div className="overflow-x-auto"><table className="w-full text-sm">
          <thead className="text-left text-pedra-500">
            <tr><th className="py-1">Nível</th><th>Moedas (cobre)</th><th>Material (id do item)</th><th>Qtd</th><th>Chance %</th></tr>
          </thead>
          <tbody>
            {forja.niveis.map((n, i) => (
              <tr key={i} className="border-t border-pedra-700">
                <td className="py-1.5 font-titulo text-ouro">+{i + 1}</td>
                <td><input name={`n.${i}.moedas`} defaultValue={n.moedas} className={campo} /> <span className="text-xs text-pedra-500">{cobre(n.moedas)}</span></td>
                <td><input name={`n.${i}.material`} defaultValue={n.material} className={campoId} /></td>
                <td><input name={`n.${i}.qtd`} defaultValue={n.qtd} className={campo} /></td>
                <td><input name={`n.${i}.chance`} defaultValue={n.chance} className={campo} /></td>
              </tr>
            ))}
          </tbody>
        </table></div>
        <label className="mt-4 flex items-center gap-2 text-sm">
          Anunciar no chat do servidor a partir de +<input name="anunciar" defaultValue={forja.anunciarAPartirDe} className={campo} />
        </label>
      </section>

      <section className="rounded-xl border border-pedra-700 bg-pedra-900 p-5">
        <h2 className="font-titulo text-xl text-ouro">Reforjar raridade</h2>
        <p className="mb-3 text-xs text-pedra-500">
          Sorteia a raridade de novo. Moedas = base × (raridade atual + 1)²; material = base + raridade × extra
          (raridade: 0 comum, 1 incomum, 2 rara, 3 épica, 4 lendária).
        </p>
        <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
          <label className="flex flex-col gap-1">Moedas base (cobre)<input name="r.moedasBase" defaultValue={r.moedasBase} className={campo} /></label>
          <label className="flex flex-col gap-1">Material<input name="r.material" defaultValue={r.material} className={campoId} /></label>
          <label className="flex flex-col gap-1">Qtd base<input name="r.qtdBase" defaultValue={r.qtdBase} className={campo} /></label>
          <label className="flex flex-col gap-1">Qtd extra por raridade<input name="r.qtdPorRaridade" defaultValue={r.qtdPorRaridade} className={campo} /></label>
        </div>
      </section>

      <div className="flex flex-wrap items-center gap-3">
        <button disabled={salvando} className="rounded-md bg-ouro px-4 py-2 font-semibold text-pedra-950 disabled:opacity-50">
          {salvando ? "Salvando…" : "Salvar forja"}
        </button>
        <button name="restaurar" value="1" disabled={salvando}
          className="rounded-md border border-pedra-700 px-4 py-2 text-sm hover:border-ouro">Voltar ao padrão</button>
        {estado && <span className={estado.ok ? "text-green-400" : "text-red-400"}>{estado.mensagem}</span>}
      </div>
    </form>
  );
}
