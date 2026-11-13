"use client";

import { useState, useTransition } from "react";
import { PAPEIS_DIALOGO } from "@/lib/dialogos";
import { salvarDialogos } from "./actions";

export function Dialogos({ inicial }: { inicial: Record<string, string[]> }) {
  const [falas, setFalas] = useState<Record<string, string[]>>(inicial);
  const [salvo, setSalvo] = useState(inicial);
  const [msg, setMsg] = useState<{ ok: boolean; t: string } | null>(null);
  const [salvando, iniciar] = useTransition();
  const mudou = JSON.stringify(falas) !== JSON.stringify(salvo);

  const lista = (id: string) => falas[id] ?? [];
  const set = (id: string, l: string[]) => setFalas((f) => ({ ...f, [id]: l }));

  const salvar = () => iniciar(async () => {
    const limpo: Record<string, string[]> = {};
    for (const p of PAPEIS_DIALOGO) limpo[p.id] = lista(p.id).map((s) => s.trim()).filter(Boolean);
    const r = await salvarDialogos(limpo);
    setMsg({ ok: r.ok, t: r.mensagem });
    if (r.ok) { setFalas(limpo); setSalvo(limpo); }
  });

  return (
    <section className="mt-10 rounded-xl border border-pedra-700 bg-pedra-900 p-4 md:p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div className="max-w-2xl">
          <h2 className="font-titulo text-xl text-ouro">💬 Falas da caixa de diálogo</h2>
          <p className="text-xs text-pedra-500">
            O que o NPC diz quando o jogador fala com ele. Escreva uma fala por linha: o servidor sorteia uma delas.
            Use <code>{"{jogador}"}</code> para o nick. Deixe vazio para usar as falas padrão do mod.
          </p>
        </div>
        <button onClick={salvar} disabled={!mudou || salvando}
          className="rounded-md border border-ouro px-4 py-2 text-sm text-ouro hover:bg-ouro hover:text-pedra-950 disabled:opacity-40">
          {salvando ? "Salvando…" : mudou ? "Salvar" : "Salvo"}
        </button>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {PAPEIS_DIALOGO.map((p) => (
          <div key={p.id} className="rounded-lg border border-pedra-800 bg-pedra-950/50 p-3">
            <div className="mb-1.5 flex items-baseline justify-between gap-2">
              <h3 className="text-sm text-pergaminho">{p.nome}</h3>
              <span className="text-xs text-pedra-500">{lista(p.id).filter(Boolean).length} fala(s)</span>
            </div>
            <textarea
              value={lista(p.id).join("\n")}
              onChange={(e) => set(p.id, e.target.value.split("\n"))}
              rows={4}
              placeholder={p.exemplo}
              className="w-full resize-y rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1.5 text-sm outline-none focus:border-ouro"
            />
          </div>
        ))}
      </div>
      <p className="mt-3 text-xs text-pedra-500">
        O rei também responde &quot;Qual é a minha missão?&quot; e &quot;Fale-me do reino&quot;; o taverneiro abre o quadro de missões.
        Essas respostas são do próprio sistema e não mudam aqui.
      </p>
      {msg && <p className={`mt-2 text-sm ${msg.ok ? "text-green-400" : "text-red-400"}`}>{msg.t}</p>}
    </section>
  );
}
