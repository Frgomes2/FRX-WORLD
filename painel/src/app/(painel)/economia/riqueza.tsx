"use client";

import Link from "next/link";
import { Moedas } from "@/components/itens";
import { RostoSkin } from "../jogadores/[uuid]/boneco";

type J = { uuid: string; nick: string; moedas: number; banco: number };

export function Riqueza({ jogadores }: { jogadores: J[] }) {
  const bolsa = jogadores.reduce((s, j) => s + j.moedas, 0);
  const banco = jogadores.reduce((s, j) => s + j.banco, 0);
  const total = bolsa + banco;
  const ricos = [...jogadores].sort((a, b) => b.moedas + b.banco - (a.moedas + a.banco)).slice(0, 10);
  const max = Math.max(1, ...ricos.map((j) => j.moedas + j.banco));
  const media = jogadores.length ? Math.round(total / jogadores.length) : 0;
  const pctBanco = total ? Math.round((banco / total) * 100) : 0;

  return (
    <div className="mb-8 grid gap-4 xl:grid-cols-[1fr_1.3fr]">
      <div className="grid grid-cols-2 gap-3">
        <div className="col-span-2 rounded-xl border border-ouro/40 bg-gradient-to-br from-[#3a2c12] to-pedra-900 p-4">
          <p className="text-xs uppercase tracking-wider text-pedra-500">💰 Dinheiro no mundo</p>
          <Moedas cobre={total} tam={22} className="mt-1 text-2xl font-titulo" />
          <div className="mt-3 flex h-3 overflow-hidden rounded-full bg-pedra-800" title={`${pctBanco}% guardado no banco`}>
            <div className="bg-sky-500" style={{ width: `${pctBanco}%` }} />
            <div className="flex-1 bg-amber-500" />
          </div>
          <div className="mt-1 flex justify-between text-xs">
            <span className="text-sky-300">🏦 Banco {pctBanco}%</span><span className="text-amber-300">👝 Bolsa {100 - pctBanco}%</span>
          </div>
        </div>
        <div className="rounded-xl border border-pedra-700 bg-pedra-900 p-3">
          <p className="text-xs uppercase tracking-wider text-pedra-500">🏦 No banco</p><Moedas cobre={banco} className="mt-1" />
        </div>
        <div className="rounded-xl border border-pedra-700 bg-pedra-900 p-3">
          <p className="text-xs uppercase tracking-wider text-pedra-500">👝 Nas bolsas</p><Moedas cobre={bolsa} className="mt-1" />
        </div>
        <div className="rounded-xl border border-pedra-700 bg-pedra-900 p-3">
          <p className="text-xs uppercase tracking-wider text-pedra-500">👥 Jogadores</p><p className="font-titulo text-2xl text-ouro">{jogadores.length}</p>
        </div>
        <div className="rounded-xl border border-pedra-700 bg-pedra-900 p-3">
          <p className="text-xs uppercase tracking-wider text-pedra-500">⚖️ Média por jogador</p><Moedas cobre={media} className="mt-1" />
        </div>
      </div>

      <div className="rounded-xl border border-pedra-700 bg-pedra-900 p-4">
        <h2 className="mb-3 font-titulo text-lg text-ouro">🏆 Mais ricos</h2>
        {!ricos.length && <p className="text-sm text-pedra-500">Ninguém entrou no servidor ainda.</p>}
        <div className="space-y-2">
          {ricos.map((j, i) => {
            const t = j.moedas + j.banco;
            return (
              <Link key={j.uuid} href={`/jogadores/${j.uuid}`} className="grid grid-cols-[28px_120px_1fr_auto] items-center gap-2 rounded-md px-1 py-0.5 text-sm hover:bg-pedra-800">
                <span className="text-center">{["🥇", "🥈", "🥉"][i] ?? <span className="text-pedra-500">{i + 1}</span>}</span>
                <span className="flex items-center gap-1.5 truncate font-semibold">
                  <span className="shrink-0 overflow-hidden rounded"><RostoSkin uuid={j.uuid} tam={18} /></span>
                  {j.nick}
                </span>
                <span className="flex h-2.5 overflow-hidden rounded-full bg-pedra-800" title={`Banco ${j.banco} · Bolsa ${j.moedas}`}>
                  <span className="bg-sky-500" style={{ width: `${(j.banco / max) * 100}%` }} />
                  <span className="bg-amber-500" style={{ width: `${(j.moedas / max) * 100}%` }} />
                </span>
                <Moedas cobre={t} tam={14} className="justify-end text-xs" />
              </Link>
            );
          })}
        </div>
      </div>
    </div>
  );
}
