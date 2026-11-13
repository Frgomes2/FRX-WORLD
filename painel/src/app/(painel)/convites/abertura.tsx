"use client";

import { useState, useTransition } from "react";
import { REINOS } from "@/lib/reinos";
import { salvarAbertura } from "./actions";

export type ConfigAbertura = { ativo: boolean; reino: string; falas: string[] };

export function Abertura({ inicial }: { inicial: ConfigAbertura }) {
  const [c, setC] = useState(inicial);
  const [salvo, setSalvo] = useState(inicial);
  const [msg, setMsg] = useState<{ ok: boolean; t: string } | null>(null);
  const [salvando, iniciar] = useTransition();
  const mudou = JSON.stringify(c) !== JSON.stringify(salvo);

  const salvar = () => iniciar(async () => {
    const limpo = { ...c, falas: c.falas.map((f) => f.trim()).filter(Boolean) };
    const r = await salvarAbertura(limpo);
    setMsg({ ok: r.ok, t: r.mensagem });
    if (r.ok) { setC(limpo); setSalvo(limpo); }
  });

  return (
    <section className="mt-10 rounded-xl border border-pedra-700 bg-pedra-900 p-4 md:p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div className="max-w-2xl">
          <h2 className="font-titulo text-xl text-ouro">🌑 Abertura do primeiro login</h2>
          <p className="text-xs text-pedra-500">
            Logo depois do convite, o jogador vê uma tela preta com a voz do Sistema, escolhe raça e classe,
            desperta a Habilidade Única e nasce na capital. Só acontece uma vez por jogador.
          </p>
        </div>
        <button onClick={salvar} disabled={!mudou || salvando}
          className="rounded-md border border-ouro px-4 py-2 text-sm text-ouro hover:bg-ouro hover:text-pedra-950 disabled:opacity-40">
          {salvando ? "Salvando…" : mudou ? "Salvar" : "Salvo"}
        </button>
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-6">
        <label className="flex items-center gap-2 text-sm">
          <button type="button" onClick={() => setC({ ...c, ativo: !c.ativo })} aria-label="Ligar ou desligar a abertura">
            <span className={`relative block h-5 w-10 rounded-full transition-colors ${c.ativo ? "bg-green-500" : "bg-pedra-700"}`}>
              <span className={`absolute top-1 h-3 w-3 rounded-full bg-white transition-all ${c.ativo ? "left-6" : "left-1"}`} />
            </span>
          </button>
          {c.ativo ? "Abertura ligada" : "Abertura desligada"}
        </label>
        <label className="flex items-center gap-2 text-sm">
          Nasce em
          <select value={c.reino} onChange={(e) => setC({ ...c, reino: e.target.value })}
            className="rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 outline-none focus:border-ouro">
            {REINOS.map((r) => <option key={r.id} value={r.id}>{r.nome}</option>)}
          </select>
        </label>
      </div>

      <label className="block text-xs text-pedra-500">
        Falas do Sistema — uma por linha, na ordem em que aparecem. <code>{"{jogador}"}</code> vira o nick.
        <textarea value={c.falas.join("\n")} onChange={(e) => setC({ ...c, falas: e.target.value.split("\n") })} rows={7}
          className="mt-1 w-full resize-y rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 text-sm text-pergaminho outline-none focus:border-ouro" />
      </label>
      <p className="mt-1 text-xs text-pedra-500">
        {c.falas.filter(Boolean).length} fala(s) · a cena dura cerca de {Math.round(c.falas.filter(Boolean).reduce((s, f) => s + 0.65 + f.length * 0.042, 0))} s.
        Para ver como ficou, use <code className="text-ouro">/frx abertura</code> no jogo.
      </p>
      {msg && <p className={`mt-2 text-sm ${msg.ok ? "text-green-400" : "text-red-400"}`}>{msg.t}</p>}
    </section>
  );
}
