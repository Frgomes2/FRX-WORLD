import type { ReactNode } from "react";
import type { Linha } from "@/lib/expedicoes";
import { Skin } from "@/components/publico/jogadores";

export const fmtKm2 = (v: number) => `${v.toLocaleString("pt-BR", { maximumFractionDigits: v < 10 ? 2 : 1 })} km²`;
export const fmtMin = (m: number) => (m >= 60 ? `${Math.floor(m / 60)} h ${m % 60} min` : `${m} min`);
const MEDALHA = ["🥇", "🥈", "🥉"];

export function Moldura({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-pedra-950 text-pergaminho">
      <header className="border-b border-pedra-800 bg-pedra-900/80">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
          <a href="/" className="font-titulo text-xl text-ouro hover:brightness-110">← FRX-WORLD</a>
          <a href="/expedicoes" className="text-sm text-pedra-500 hover:text-ouro">🧭 Expedições</a>
        </div>
      </header>
      <main className="mx-auto max-w-5xl space-y-8 px-4 py-8">{children}</main>
    </div>
  );
}

export function Numero({ valor, rotulo }: { valor: string; rotulo: string }) {
  return (
    <div className="rounded-xl border border-pedra-700 bg-pedra-900/70 px-3 py-3 text-center">
      <p className="font-titulo text-2xl text-ouro sm:text-3xl">{valor}</p>
      <p className="mt-0.5 text-[11px] uppercase tracking-wider text-pedra-500">{rotulo}</p>
    </div>
  );
}

export function Secao({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section>
      <h2 className="mb-3 font-titulo text-2xl text-ouro">{titulo}</h2>
      {children}
    </section>
  );
}

/** Ranking da expedição (pontuação geral = MVP). */
export function Ranking({ linhas }: { linhas: Linha[] }) {
  if (!linhas.length) return <p className="text-sm text-pedra-500">Ninguém explorou esta expedição ainda.</p>;
  return (
    <ol className="space-y-2">
      {linhas.map((l) => (
        <li key={l.nick} className={`flex flex-wrap items-center gap-x-4 gap-y-1 rounded-xl border px-4 py-3 ${l.posicao === 1 ? "border-ouro-escuro bg-ouro-escuro/10" : "border-pedra-700 bg-pedra-900/60"}`}>
          <span className="w-8 text-center font-titulo text-xl">{MEDALHA[l.posicao - 1] ?? `${l.posicao}º`}</span>
          <Skin nick={l.nick} tam={28} />
          <span className="min-w-[8rem] font-semibold">{l.nick}</span>
          <span className="text-sm text-pergaminho/80">🧭 {fmtKm2(l.km2)} · 🏰 {l.estruturas} · ⚔️ {l.chefes} · 💎 {l.tesouros} · ☠️ {l.mortes}</span>
          <span className="ml-auto font-titulo text-lg text-ouro">{l.pontos.toLocaleString("pt-BR")} pts</span>
        </li>
      ))}
    </ol>
  );
}
