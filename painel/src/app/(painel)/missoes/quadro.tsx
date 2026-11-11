"use client";

import Link from "next/link";
import { useMemo, useState, useTransition } from "react";
import { IconeItem, Moedas, useMapaItens } from "@/components/itens";
import { alternarMissaoId, criarExemplos, duplicarMissao, excluirMissaoId } from "./actions";
import { Alvo } from "./alvo";

export type MissaoLista = {
  id: string; titulo: string; descricao: string; tipo: string; alvo: string; quantidade: number; nivelMin: number;
  recompensaXp: number; recompensaMoedas: number; recompensaItens: { item: string; qtd: number }[];
  repetivel: boolean; intervaloHoras: number; preRequisitoId: string | null; ativa: boolean; ordem: number;
};

const faixa = (n: number) => (n >= 50 ? { nome: "Lendárias (50+)", cor: "#ffaa00" } : n >= 25 ? { nome: "Difíceis (25–49)", cor: "#c58cf0" } : n >= 10 ? { nome: "Intermediárias (10–24)", cor: "#5dade2" } : { nome: "Iniciantes (1–9)", cor: "#7cd67c" });

export function QuadroMissoes({ missoes }: { missoes: MissaoLista[] }) {
  const [busca, setBusca] = useState("");
  const [tipo, setTipo] = useState("todos");
  const [estado, setEstado] = useState("todas");
  const [, iniciar] = useTransition();
  const mapaItens = useMapaItens();
  const titulos = new Map(missoes.map((m) => [m.id, m.titulo]));

  const filtradas = useMemo(() => {
    const q = busca.trim().toLowerCase();
    return missoes.filter((m) => (tipo === "todos" || m.tipo === tipo) && (estado === "todas" || (estado === "ativas" ? m.ativa : !m.ativa))
      && (!q || m.titulo.toLowerCase().includes(q) || m.descricao.toLowerCase().includes(q) || m.alvo.includes(q)));
  }, [missoes, busca, tipo, estado]);

  const grupos = useMemo(() => {
    const g = new Map<string, { cor: string; lista: MissaoLista[] }>();
    for (const m of filtradas) { const f = faixa(m.nivelMin); if (!g.has(f.nome)) g.set(f.nome, { cor: f.cor, lista: [] }); g.get(f.nome)!.lista.push(m); }
    return [...g.entries()];
  }, [filtradas]);

  const btn = (ativo: boolean) => `rounded-md border px-2.5 py-1 text-xs ${ativo ? "border-ouro bg-ouro/15 text-ouro" : "border-pedra-700 text-pedra-500 hover:text-pergaminho"}`;

  return (
    <>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-titulo text-3xl">Missões</h1>
          <p className="text-pedra-500">O Quadro de Missões das tavernas. O servidor lê a lista a cada minuto.</p>
        </div>
        <Link href="/missoes/nova" className="rounded-md bg-ouro px-4 py-2 font-semibold text-pedra-950 hover:bg-ouro-escuro">+ Nova missão</Link>
      </div>

      <div className="mb-5 grid grid-cols-2 gap-3 md:grid-cols-4">
        {[
          ["📜", "Missões", missoes.length, ""], ["✅", "Ativas", missoes.filter((m) => m.ativa).length, "text-green-400"],
          ["🔁", "Repetíveis", missoes.filter((m) => m.repetivel).length, "text-sky-300"], ["🔗", "Em cadeia", missoes.filter((m) => m.preRequisitoId).length, "text-purple-300"],
        ].map(([ic, n, v, cor]) => (
          <div key={String(n)} className="rounded-xl border border-pedra-700 bg-pedra-900 p-3">
            <p className="text-xs uppercase tracking-wider text-pedra-500">{ic} {n}</p>
            <p className={`font-titulo text-3xl ${cor || "text-ouro"}`}>{v}</p>
          </div>
        ))}
      </div>

      {missoes.length === 0 ? (
        <div className="rounded-xl border border-dashed border-pedra-700 p-10 text-center">
          <p className="mb-4 text-pedra-500">Nenhuma missão ainda.</p>
          <form action={criarExemplos}><button className="rounded-md border border-ouro px-4 py-2 text-ouro hover:bg-ouro hover:text-pedra-950">Criar 6 missões de exemplo</button></form>
        </div>
      ) : (
        <>
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="🔎 Buscar missão…" className="w-64 rounded-md border border-pedra-700 bg-pedra-950 px-3 py-1.5 text-sm outline-none focus:border-ouro" />
            <button className={btn(tipo === "todos")} onClick={() => setTipo("todos")}>Todos os tipos</button>
            <button className={btn(tipo === "MATAR")} onClick={() => setTipo("MATAR")}>⚔️ Caçar</button>
            <button className={btn(tipo === "COLETAR")} onClick={() => setTipo("COLETAR")}>📦 Coletar</button>
            <span className="mx-1 h-5 w-px bg-pedra-700" />
            <button className={btn(estado === "todas")} onClick={() => setEstado("todas")}>Todas</button>
            <button className={btn(estado === "ativas")} onClick={() => setEstado("ativas")}>Ativas</button>
            <button className={btn(estado === "pausadas")} onClick={() => setEstado("pausadas")}>Pausadas</button>
          </div>

          {grupos.map(([nome, g]) => (
            <section key={nome} className="mb-6">
              <h2 className="mb-2 flex items-center gap-2 font-titulo text-lg" style={{ color: g.cor }}>
                <span className="h-2.5 w-2.5 rounded-full" style={{ background: g.cor }} />{nome} <span className="text-sm text-pedra-500">· {g.lista.length}</span>
              </h2>
              <div className="grid gap-3 md:grid-cols-2 2xl:grid-cols-3">
                {g.lista.map((m) => (
                  <div key={m.id} className={`group relative flex flex-col rounded-xl border bg-gradient-to-br from-[#2a2217] to-pedra-900 p-4 transition hover:border-ouro/60 ${m.ativa ? "border-pedra-700" : "border-dashed border-pedra-700 opacity-60"}`}>
                    <div className="flex items-start gap-3">
                      <div className="grid h-14 w-14 shrink-0 place-items-center rounded-lg border border-pedra-700 bg-pedra-950/70"><Alvo tipo={m.tipo} alvo={m.alvo} tam={40} soIcone /></div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="rounded px-1.5 py-0.5 font-titulo text-sm text-black/80" style={{ background: faixa(m.nivelMin).cor }}>Nv {m.nivelMin}+</span>
                          <p className="truncate font-titulo text-lg">{m.titulo}</p>
                        </div>
                        <p className="line-clamp-2 text-xs text-pedra-500">{m.descricao || "—"}</p>
                      </div>
                    </div>
                    <p className="mt-3 text-sm">
                      {m.tipo === "MATAR" ? "⚔️ Caçar" : "📦 Entregar"} <b>{m.quantidade}×</b> <Alvo tipo={m.tipo} alvo={m.alvo} tam={18} />
                    </p>
                    <div className="mt-2 flex flex-wrap items-center gap-2 text-sm">
                      <span className="rounded-full bg-green-900/40 px-2 py-0.5 text-xs text-green-300">✨ {m.recompensaXp.toLocaleString("pt-BR")} XP</span>
                      {m.recompensaMoedas > 0 && <span className="rounded-full bg-yellow-900/30 px-2 py-0.5 text-xs"><Moedas cobre={m.recompensaMoedas} tam={14} /></span>}
                      {m.recompensaItens.map((i) => (
                        <span key={i.item} title={mapaItens.get(i.item)?.nome ?? i.item} className="flex items-center gap-1 rounded-full bg-pedra-800 px-1.5 py-0.5 text-xs"><IconeItem id={i.item} tam={16} />×{i.qtd}</span>
                      ))}
                    </div>
                    <div className="mt-2 flex flex-wrap gap-1.5 text-[11px]">
                      <span className="rounded-full border border-pedra-700 px-2 py-0.5 text-pedra-500">{m.repetivel ? `🔁 a cada ${m.intervaloHoras}h` : "1️⃣ uma vez"}</span>
                      {m.preRequisitoId && <span className="rounded-full border border-purple-500/40 px-2 py-0.5 text-purple-300">🔗 depois de “{titulos.get(m.preRequisitoId) ?? "?"}”</span>}
                    </div>
                    <div className="mt-3 flex items-center gap-2 border-t border-pedra-800 pt-3 text-xs">
                      <button onClick={() => iniciar(() => alternarMissaoId(m.id))} className="flex items-center gap-1.5">
                        <span className={`relative h-4 w-8 rounded-full ${m.ativa ? "bg-green-500" : "bg-pedra-700"}`}><span className={`absolute top-0.5 h-3 w-3 rounded-full bg-white transition-all ${m.ativa ? "left-[18px]" : "left-0.5"}`} /></span>
                        {m.ativa ? "Ativa" : "Pausada"}
                      </button>
                      <Link href={`/missoes/${m.id}`} className="ml-auto rounded-md border border-ouro/50 px-2.5 py-1 text-ouro hover:bg-ouro/15">✏️ Editar</Link>
                      <button onClick={() => iniciar(() => duplicarMissao(m.id))} className="rounded-md border border-pedra-700 px-2.5 py-1 hover:border-ouro">⧉ Duplicar</button>
                      <button onClick={() => { if (confirm(`Excluir "${m.titulo}"?`)) iniciar(() => excluirMissaoId(m.id)); }} className="rounded-md border border-pedra-700 px-2.5 py-1 text-red-300 hover:border-red-400">🗑</button>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          ))}
          {!filtradas.length && <p className="text-sm text-pedra-500">Nenhuma missão com esses filtros.</p>}
        </>
      )}
    </>
  );
}
