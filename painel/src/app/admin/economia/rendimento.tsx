"use client";

import { useState, useTransition } from "react";
import { Moedas } from "@/components/itens";
import { salvarBanco } from "./actions";

export type ConfigBanco = { ativo: boolean; taxaInicial: number; aumento: number; aCadaDias: number; taxaMax: number; teto: number; diasOffline: number };

const campo = "w-24 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 text-right font-mono text-sm outline-none focus:border-ouro";

function Campo({ rotulo, ajuda, valor, onChange, passo = 1, sufixo }: {
  rotulo: string; ajuda?: string; valor: number; onChange: (v: number) => void; passo?: number; sufixo?: string;
}) {
  return (
    <label className="flex items-center justify-between gap-3 border-b border-pedra-800 py-2 text-sm">
      <span className="min-w-0 flex-1">
        <span className="block">{rotulo}</span>
        {ajuda && <span className="block text-xs text-pedra-500">{ajuda}</span>}
      </span>
      <span className="flex shrink-0 items-center gap-1">
        <input type="number" step={passo} value={valor} onChange={(e) => onChange(Number(e.target.value) || 0)} className={campo} />
        {sufixo && <span className="w-14 text-xs text-pedra-500">{sufixo}</span>}
      </span>
    </label>
  );
}

/** Simula o cofre dia a dia, sem sacar: devolve o saldo ao fim de cada dia pedido. */
function simular(c: ConfigBanco, saldo: number, dias: number) {
  let s = saldo, resto = 0;
  for (let d = 1; d <= dias; d++) {
    const taxa = Math.min(c.taxaMax, c.taxaInicial + c.aumento * Math.floor(d / Math.max(1, c.aCadaDias)));
    const r = (Math.min(s, c.teto) * taxa) / 100 + resto;
    const inteiro = Math.floor(r);
    resto = r - inteiro;
    s += inteiro;
  }
  return s;
}

export function Rendimento({ inicial }: { inicial: ConfigBanco }) {
  const [c, setC] = useState(inicial);
  const [salvo, setSalvo] = useState(inicial);
  const [msg, setMsg] = useState<{ ok: boolean; t: string } | null>(null);
  const [salvando, iniciar] = useTransition();
  const mudou = JSON.stringify(c) !== JSON.stringify(salvo);
  const set = (p: Partial<ConfigBanco>) => setC((x) => ({ ...x, ...p }));
  const semanasAteMax = c.aumento > 0 ? Math.ceil((c.taxaMax - c.taxaInicial) / c.aumento) * c.aCadaDias : 0;

  const salvar = () => iniciar(async () => {
    const r = await salvarBanco(c);
    setMsg({ ok: r.ok, t: r.mensagem });
    if (r.ok) setSalvo(c);
  });

  return (
    <section className="mb-10 rounded-xl border border-pedra-700 bg-pedra-900 p-4 md:p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="font-titulo text-xl text-ouro">🏦 Rendimento do cofre</h2>
          <p className="text-xs text-pedra-500">
            O dinheiro guardado no banco rende uma vez por dia, à meia-noite (Brasília). Quanto mais tempo sem sacar, maior a taxa; sacar volta à taxa inicial.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={c.ativo} onChange={(e) => set({ ativo: e.target.checked })} /> Ativo
          </label>
          <button onClick={salvar} disabled={!mudou || salvando}
            className="rounded-md border border-ouro px-4 py-2 text-sm text-ouro hover:bg-ouro hover:text-pedra-950 disabled:opacity-40">
            {salvando ? "Salvando…" : mudou ? "Salvar" : "Salvo"}
          </button>
        </div>
      </div>

      <div className="grid gap-x-8 md:grid-cols-2">
        <div>
          <h3 className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Taxa</h3>
          <Campo rotulo="Taxa inicial" valor={c.taxaInicial} onChange={(v) => set({ taxaInicial: v })} passo={0.05} sufixo="% ao dia" />
          <Campo rotulo="Aumento" ajuda="Somado à taxa a cada período sem sacar" valor={c.aumento} onChange={(v) => set({ aumento: v })} passo={0.05} sufixo="%" />
          <Campo rotulo="Período" valor={c.aCadaDias} onChange={(v) => set({ aCadaDias: v })} sufixo="dias" />
          <Campo rotulo="Taxa máxima" valor={c.taxaMax} onChange={(v) => set({ taxaMax: v })} passo={0.05} sufixo="% ao dia" />
        </div>
        <div>
          <h3 className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Limites</h3>
          <Campo rotulo="Teto que rende" ajuda="O que passar disso fica guardado, mas não rende" valor={c.teto} onChange={(v) => set({ teto: v })} passo={100} sufixo="cobre" />
          <p className="-mt-1 mb-1 text-right text-xs text-pedra-500"><Moedas cobre={c.teto} /></p>
          <Campo rotulo="Dias offline que rendem" ajuda="Quem some recebe ao voltar, só pelos últimos dias" valor={c.diasOffline} onChange={(v) => set({ diasOffline: v })} sufixo="dias" />
        </div>
      </div>

      <div className="mt-4 rounded-lg border border-ouro/30 bg-ouro/5 p-3 text-sm">
        <p className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Simulação: 100 ouro guardados, sem sacar</p>
        <div className="flex flex-wrap gap-x-6 gap-y-1">
          {[1, 7, 30, 60, 90].map((d) => (
            <span key={d} className="flex items-center gap-1">
              <span className="text-pedra-500">{d === 1 ? "1 dia" : `${d} dias`}:</span> <Moedas cobre={simular(c, 10000, d)} />
            </span>
          ))}
        </div>
        <p className="mt-1 text-xs text-pedra-500">
          {semanasAteMax > 0 ? `A taxa máxima chega depois de ${semanasAteMax} dias sem sacar.` : "A taxa não sobe com o tempo."}
        </p>
      </div>

      {msg && <p className={`mt-3 text-sm ${msg.ok ? "text-green-400" : "text-red-400"}`}>{msg.t}</p>}
    </section>
  );
}
