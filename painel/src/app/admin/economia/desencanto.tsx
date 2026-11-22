"use client";

import { useState, useTransition } from "react";
import { Moedas } from "@/components/itens";
import { salvarDesencanto } from "./actions";

export type ConfigDesencanto = {
  ativo: boolean; moedasPorNivel: number; multComum: number; multIncomum: number; multRaro: number; multMuitoRaro: number; multTesouro: number;
  xpBase: number; xpPorNivel: number; xpMax: number; chance4: number; chance5: number; runa1: number; runa2: number; runa3: number;
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

/** Exemplos com o peso da raridade de cada um (o mesmo cálculo do servidor). */
const EXEMPLOS: { nome: string; nivel: number; peso: (c: ConfigDesencanto) => number }[] = [
  { nome: "Afiação V", nivel: 5, peso: (c) => c.multComum },
  { nome: "Proteção IV", nivel: 4, peso: (c) => c.multComum },
  { nome: "Inquebrável III", nivel: 3, peso: (c) => c.multIncomum },
  { nome: "Fortuna III", nivel: 3, peso: (c) => c.multRaro },
  { nome: "Remendo", nivel: 1, peso: (c) => c.multRaro * c.multTesouro },
];

export function Desencanto({ inicial }: { inicial: ConfigDesencanto }) {
  const [c, setC] = useState(inicial);
  const [salvo, setSalvo] = useState(inicial);
  const [msg, setMsg] = useState<{ ok: boolean; t: string } | null>(null);
  const [salvando, iniciar] = useTransition();
  const mudou = JSON.stringify(c) !== JSON.stringify(salvo);
  const set = (p: Partial<ConfigDesencanto>) => setC((x) => ({ ...x, ...p }));
  const custo = (nivel: number, peso: number) => ({
    moedas: Math.round(c.moedasPorNivel * peso * nivel),
    xp: Math.min(c.xpMax, Math.round(c.xpBase + c.xpPorNivel * nivel * peso)),
    chance: nivel >= 5 ? c.chance5 : nivel === 4 ? c.chance4 : 100,
  });

  const salvar = () => iniciar(async () => {
    const r = await salvarDesencanto(c);
    setMsg({ ok: r.ok, t: r.mensagem });
    if (r.ok) setSalvo(c);
  });

  return (
    <section className="mb-10 rounded-xl border border-pedra-700 bg-pedra-900 p-4 md:p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="font-titulo text-xl text-ouro">📖 Sábio — extrair encantamento e tirar runas</h2>
          <p className="text-xs text-pedra-500">
            O sábio das capitais passa um encantamento do item para um livro em branco. Cobra moedas (bolsa e, se faltar, banco), níveis de experiência e o livro.
            Encantamentos altos podem falhar: perde moedas, níveis e o livro; o item fica igual. Relíquias do Códex e maldições não saem.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={c.ativo} onChange={(e) => set({ ativo: e.target.checked })} /> Ativo</label>
          <button onClick={salvar} disabled={!mudou || salvando}
            className="rounded-md border border-ouro px-4 py-2 text-sm text-ouro hover:bg-ouro hover:text-pedra-950 disabled:opacity-40">
            {salvando ? "Salvando…" : mudou ? "Salvar" : "Salvo"}
          </button>
        </div>
      </div>

      <div className="grid gap-x-8 md:grid-cols-2">
        <div>
          <h3 className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Moedas</h3>
          <Campo rotulo="Por nível do encantamento" ajuda="Multiplicado pelo peso da raridade" valor={c.moedasPorNivel} onChange={(v) => set({ moedasPorNivel: v })} passo={100} sufixo="cobre" />
          <Campo rotulo="Peso: comum" valor={c.multComum} onChange={(v) => set({ multComum: v })} passo={0.1} sufixo="×" />
          <Campo rotulo="Peso: incomum" valor={c.multIncomum} onChange={(v) => set({ multIncomum: v })} passo={0.1} sufixo="×" />
          <Campo rotulo="Peso: raro" valor={c.multRaro} onChange={(v) => set({ multRaro: v })} passo={0.1} sufixo="×" />
          <Campo rotulo="Peso: muito raro" valor={c.multMuitoRaro} onChange={(v) => set({ multMuitoRaro: v })} passo={0.1} sufixo="×" />
          <Campo rotulo="A mais se for de tesouro" ajuda="Remendo, Pisar no Gelo..." valor={c.multTesouro} onChange={(v) => set({ multTesouro: v })} passo={0.1} sufixo="×" />
        </div>
        <div>
          <h3 className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Experiência e chance</h3>
          <Campo rotulo="Níveis fixos" valor={c.xpBase} onChange={(v) => set({ xpBase: v })} sufixo="níveis" />
          <Campo rotulo="A mais por nível" ajuda="Também multiplicado pelo peso" valor={c.xpPorNivel} onChange={(v) => set({ xpPorNivel: v })} passo={0.5} sufixo="níveis" />
          <Campo rotulo="Máximo" valor={c.xpMax} onChange={(v) => set({ xpMax: v })} sufixo="níveis" />
          <Campo rotulo="Chance no nível IV" valor={c.chance4} onChange={(v) => set({ chance4: v })} sufixo="%" />
          <Campo rotulo="Chance do nível V para cima" valor={c.chance5} onChange={(v) => set({ chance5: v })} sufixo="%" />
          <p className="pt-2 text-xs text-pedra-500">Até o nível III nunca falha.</p>
          <h3 className="mb-1 mt-4 text-xs uppercase tracking-wider text-pedra-500">Tirar runa (aba Runas; a runa volta inteira)</h3>
          <Campo rotulo="Runa I" valor={c.runa1} onChange={(v) => set({ runa1: v })} passo={100} sufixo="cobre" />
          <Campo rotulo="Runa II" valor={c.runa2} onChange={(v) => set({ runa2: v })} passo={100} sufixo="cobre" />
          <Campo rotulo="Runa III" valor={c.runa3} onChange={(v) => set({ runa3: v })} passo={100} sufixo="cobre" />
        </div>
      </div>

      <div className="mt-4 rounded-lg border border-ouro/30 bg-ouro/5 p-3 text-sm">
        <p className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Quanto custa</p>
        <div className="grid gap-1 sm:grid-cols-2">
          {EXEMPLOS.map((e) => {
            const k = custo(e.nivel, e.peso(c));
            return (
              <span key={e.nome} className="flex flex-wrap items-center gap-2">
                <b className="w-32">{e.nome}</b> <Moedas cobre={k.moedas} /> <span className="text-pedra-500">· {k.xp} níveis · {k.chance}%</span>
              </span>
            );
          })}
        </div>
      </div>

      {msg && <p className={`mt-3 text-sm ${msg.ok ? "text-green-400" : "text-red-400"}`}>{msg.t}</p>}
    </section>
  );
}
