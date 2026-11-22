"use client";

import { useState, useTransition } from "react";
import { CampoMoedas, Moedas } from "@/components/itens";
import { salvarPortais } from "./actions";

const RANKS = ["F", "E", "D", "C", "B", "A", "S"];

type Config = { moedasPorRank: number[]; intervaloMinutos: number; chance: number };

export function RecompensaPortais({ inicial, masmorra }: { inicial: Config; masmorra: number }) {
  const [v, setV] = useState(inicial.moedasPorRank);
  const [intervalo, setIntervalo] = useState(inicial.intervaloMinutos);
  const [chance, setChance] = useState(inicial.chance);
  const [porNivel, setPorNivel] = useState(masmorra);
  const [salvo, setSalvo] = useState(JSON.stringify({ ...inicial, masmorras: { moedasPorNivel: masmorra } }));
  const [msg, setMsg] = useState<{ ok: boolean; t: string } | null>(null);
  const [salvando, iniciar] = useTransition();
  const atual = { moedasPorRank: v, intervaloMinutos: intervalo, chance, masmorras: { moedasPorNivel: porNivel } };
  const mudou = JSON.stringify(atual) !== salvo;
  const media = chance > 0 ? Math.round(intervalo * 100 / chance) : 0;

  const salvar = () => iniciar(async () => {
    const r = await salvarPortais(atual);
    setMsg({ ok: r.ok, t: r.mensagem });
    if (r.ok) setSalvo(JSON.stringify(atual));
  });

  return (
    <section className="mb-10 rounded-xl border border-pedra-700 bg-pedra-900 p-4 md:p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="font-titulo text-xl text-ouro">🌀 Portais e masmorras: frequência e recompensa</h2>
          <p className="text-xs text-pedra-500">Moedas para cada jogador que derrota o chefão da dungeon do portal, além do XP e das chances de runa, tomo e mapa. 0 = sem moedas.</p>
        </div>
        <button onClick={salvar} disabled={!mudou || salvando}
          className="rounded-md border border-ouro px-4 py-2 text-sm text-ouro hover:bg-ouro hover:text-pedra-950 disabled:opacity-40">
          {salvando ? "Salvando…" : mudou ? "Salvar" : "Salvo"}
        </button>
      </div>
      <div className="mb-4 flex flex-wrap items-end gap-6 text-sm">
        <label><span className="block text-xs text-pedra-500">Tentativa a cada</span>
          <span className="flex items-center gap-1"><input type="number" min={1} value={intervalo} onChange={(e) => setIntervalo(Number(e.target.value) || 1)} className="w-20 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 text-right font-mono" /> min</span></label>
        <label><span className="block text-xs text-pedra-500">Chance de abrir</span>
          <span className="flex items-center gap-1"><input type="number" min={0} max={100} value={chance} onChange={(e) => setChance(Number(e.target.value) || 0)} className="w-20 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 text-right font-mono" /> %</span></label>
        <p className="text-xs text-pedra-500">Em média, um portal novo a cada {media} min depois que o anterior fecha (só abre um por vez e só com jogador online perto de uma capital).</p>
      </div>
      <div className="grid gap-x-8 gap-y-2 sm:grid-cols-2 lg:grid-cols-4">
        {RANKS.map((r, i) => (
          <div key={r} className="flex items-center justify-between gap-2 border-b border-pedra-800 py-2 text-sm">
            <span className="font-titulo text-lg text-ouro">Rank {r}</span>
            <span className="flex flex-col items-end gap-1">
              <CampoMoedas cobre={v[i] ?? 0} onChange={(c) => setV((x) => x.map((y, k) => (k === i ? c : y)))} />
              <Moedas cobre={v[i] ?? 0} />
            </span>
          </div>
        ))}
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-4 border-t border-pedra-800 pt-3 text-sm">
        <span><b>Chefões de masmorra</b> <span className="text-xs text-pedra-500">(catacumbas debaixo das vilas), por jogador. Os chefes de outros mods pagam pela dificuldade: abaixo do nível 40, 20 cobre por nível; de 40 a 54, 50 por nível; de 55 para cima, 3 × nível² (os especiais).</span></span>
        <CampoMoedas cobre={porNivel} onChange={setPorNivel} /> <span className="text-xs text-pedra-500">por nível do chefão</span>
        <span className="text-xs text-pedra-500">Ex.: nível 20 = <Moedas cobre={porNivel * 20} /> · nível 40 = <Moedas cobre={porNivel * 40} /> · guardião de andar = 20% por andar.</span>
      </div>
      {msg && <p className={`mt-3 text-sm ${msg.ok ? "text-green-400" : "text-red-400"}`}>{msg.t}</p>}
    </section>
  );
}
