"use client";

import { useState, useTransition } from "react";
import { Moedas } from "@/components/itens";
import { salvarComprador } from "./actions";

export type ConfigComprador = {
  precoComum: number; precoIncomum: number; precoRaro: number; precoEpico: number;
  fracaoVenda: number; bonusEncanto: number; multiplicador: number;
  limiteBaseOuro: number; limitePorNivelOuro: number;
};

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
        {sufixo && <span className="w-12 text-xs text-pedra-500">{sufixo}</span>}
      </span>
    </label>
  );
}

export function Comprador({ inicial }: { inicial: ConfigComprador }) {
  const [c, setC] = useState(inicial);
  const [salvo, setSalvo] = useState(inicial);
  const [msg, setMsg] = useState<{ ok: boolean; t: string } | null>(null);
  const [salvando, iniciar] = useTransition();
  const mudou = JSON.stringify(c) !== JSON.stringify(salvo);
  const set = (p: Partial<ConfigComprador>) => setC((x) => ({ ...x, ...p }));
  const limite = (nivel: number) => Math.round((c.limiteBaseOuro + c.limitePorNivelOuro * (nivel - 1)) * 100);

  const salvar = () => iniciar(async () => {
    const r = await salvarComprador(c);
    setMsg({ ok: r.ok, t: r.mensagem });
    if (r.ok) setSalvo(c);
  });

  return (
    <section className="mb-10 rounded-xl border border-pedra-700 bg-pedra-900 p-4 md:p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="font-titulo text-xl text-ouro">⚖️ Comprador — avaliação automática</h2>
          <p className="text-xs text-pedra-500">
            O comprador aceita qualquer item e calcula o preço pela raridade. A lista fixa acima vale só para o mercador.
          </p>
        </div>
        <button onClick={salvar} disabled={!mudou || salvando}
          className="rounded-md border border-ouro px-4 py-2 text-sm text-ouro hover:bg-ouro hover:text-pedra-950 disabled:opacity-40">
          {salvando ? "Salvando…" : mudou ? "Salvar" : "Salvo"}
        </button>
      </div>

      <div className="grid gap-x-8 md:grid-cols-2">
        <div>
          <h3 className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Preço por raridade (cobre, por unidade)</h3>
          <Campo rotulo="Comum" valor={c.precoComum} onChange={(v) => set({ precoComum: v })} />
          <Campo rotulo="Incomum" valor={c.precoIncomum} onChange={(v) => set({ precoIncomum: v })} />
          <Campo rotulo="Raro" valor={c.precoRaro} onChange={(v) => set({ precoRaro: v })} />
          <Campo rotulo="Épico" valor={c.precoEpico} onChange={(v) => set({ precoEpico: v })} />
          <Campo rotulo="Multiplicador geral" ajuda="Mexe em todos os preços de uma vez" valor={c.multiplicador} onChange={(v) => set({ multiplicador: v })} passo={0.1} sufixo="×" />
        </div>
        <div>
          <h3 className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Ajustes</h3>
          <Campo rotulo="Fração do preço de venda" ajuda="Armas, armaduras e comidas valem esta parte do que o ferreiro cobra"
            valor={c.fracaoVenda} onChange={(v) => set({ fracaoVenda: v })} passo={0.05} />
          <Campo rotulo="Bônus por encantamento" ajuda="Por nível somado de todos os encantamentos" valor={c.bonusEncanto} onChange={(v) => set({ bonusEncanto: v })} passo={0.05} />
          <h3 className="mb-1 mt-4 text-xs uppercase tracking-wider text-pedra-500">Limite de ouro por dia</h3>
          <Campo rotulo="Base (nível 1)" valor={c.limiteBaseOuro} onChange={(v) => set({ limiteBaseOuro: v })} sufixo="ouro" />
          <Campo rotulo="A mais por nível" valor={c.limitePorNivelOuro} onChange={(v) => set({ limitePorNivelOuro: v })} passo={0.5} sufixo="ouro" />
        </div>
      </div>

      <div className="mt-4 rounded-lg border border-ouro/30 bg-ouro/5 p-3 text-sm">
        <p className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Quanto cada jogador pode receber por dia</p>
        <div className="flex flex-wrap gap-x-6 gap-y-1">
          {[1, 10, 25, 50, 100].map((n) => (
            <span key={n} className="flex items-center gap-1">
              <span className="text-pedra-500">Nv {n}:</span> <Moedas cobre={limite(n)} />
            </span>
          ))}
        </div>
        <p className="mt-1 text-xs text-pedra-500">O teto zera todo dia. Itens danificados valem menos; moedas e o cartão da Guilda não são aceitos.</p>
      </div>

      {msg && <p className={`mt-3 text-sm ${msg.ok ? "text-green-400" : "text-red-400"}`}>{msg.t}</p>}
    </section>
  );
}
