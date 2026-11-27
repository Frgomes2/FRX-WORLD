import type { ReactNode } from "react";
import type { Linha } from "@/lib/expedicoes";
import { Skin } from "@/components/publico/jogadores";
import { Casca, Miolo } from "@/components/publico/casca";

const FUSO = "America/Sao_Paulo";
export const dataHora = (ms: number) => new Date(ms).toLocaleString("pt-BR", { timeZone: FUSO, day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
export const data = (ms: number) => new Date(ms).toLocaleDateString("pt-BR", { timeZone: FUSO, day: "2-digit", month: "2-digit", year: "numeric" });
export const fmtKm2 = (v: number) => `${v.toLocaleString("pt-BR", { maximumFractionDigits: v < 10 ? 2 : 1 })} km²`;
export const fmtMin = (m: number) => (m >= 60 ? `${Math.floor(m / 60)} h ${m % 60} min` : `${m} min`);
const MEDALHA = ["🥇", "🥈", "🥉"];

export function Moldura({ children }: { children: ReactNode }) {
  return <Casca pagina="expedicoes"><Miolo>{children}</Miolo></Casca>;
}

export function Numero({ valor, rotulo, verde = false }: { valor: string; rotulo: string; verde?: boolean }) {
  return (
    <div className={`rounded-xl border px-3 py-3 text-center ${verde ? "border-emerald-800/70 bg-pedra-950/70 backdrop-blur-sm" : "border-pedra-700 bg-pedra-900/70"}`}>
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

/** Pódio dos três primeiros da expedição. */
export function PodioExpedicao({ linhas }: { linhas: Linha[] }) {
  const top = linhas.slice(0, 3);
  if (!top.length) return null;
  const ordem = [top[1], top[0], top[2]];
  const altura = ["h-24", "h-32", "h-16"];
  return (
    <div className="mb-6 grid grid-cols-3 items-end gap-3">
      {ordem.map((l, i) => l ? (
        <div key={l.nick} className="flex flex-col items-center text-center">
          <span className="text-3xl">{MEDALHA[l.posicao - 1]}</span>
          <Skin nick={l.nick} modo="corpo" tam={i === 1 ? 96 : 72} />
          <p className={`mt-1 max-w-full truncate font-semibold ${i === 1 ? "text-ouro" : ""}`}>{l.nick}</p>
          <p className="text-xs text-pedra-500">{l.pontos.toLocaleString("pt-BR")} pts</p>
          <div className={`mt-2 w-full rounded-t-lg border border-b-0 ${i === 1 ? "border-ouro-escuro bg-gradient-to-b from-ouro-escuro/40 to-pedra-900" : "border-pedra-700 bg-gradient-to-b from-pedra-700/60 to-pedra-900"} ${altura[i]}`} />
        </div>
      ) : <div key={i} />)}
    </div>
  );
}
