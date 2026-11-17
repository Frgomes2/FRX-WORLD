"use client";

import { IconeCriatura, useCriaturas } from "@/components/criaturas";

/** Ícone + nome de uma criatura pelo ID. */
export function Criatura({ id, tam = 24, soIcone = false, className = "" }: { id: string; tam?: number; soIcone?: boolean; className?: string }) {
  const criaturas = useCriaturas();
  const c = criaturas?.find((x) => x.id === id);
  return (
    <span className={`inline-flex items-center gap-1.5 ${className}`} title={id}>
      <IconeCriatura c={c} id={id} tam={tam} />
      {!soIcone && <span>{c?.nome ?? id.split(":")[1]?.replace(/_/g, " ")}</span>}
    </span>
  );
}

export const CORES_DUNGEON = ["#7cd67c", "#5dade2", "#c58cf0", "#f0a35d", "#e05a4a", "#ffd84a", "#4fd1c5", "#ff7ad9"];

/** Força de um guardião (vida × dano × velocidade), para comparar camadas. */
export const poder = (g: { bossVida: number; bossDano: number; bossVelocidade: number }) => g.bossVida * Math.max(1, g.bossDano) * g.bossVelocidade;

/** Avalia se o guardião final combina com o nível recomendado (referência: jogador nível N tem ~20 + N×2 de vida e bate ~N×0,8+7). */
export function avaliacao(nivel: number, g: { bossVida: number; bossDano: number; bossVelocidade: number }) {
  const golpesParaMatar = g.bossVida / (7 + nivel * 0.8);
  const golpesParaMorrer = (20 + nivel * 2) / Math.max(0.5, g.bossDano * g.bossVelocidade);
  const r = golpesParaMatar / Math.max(1, golpesParaMorrer * 3);
  if (r < 0.6) return { texto: "Fácil", cor: "#7cd67c", r };
  if (r < 1.6) return { texto: "Equilibrado", cor: "#5dade2", r };
  if (r < 3) return { texto: "Difícil", cor: "#f0a35d", r };
  return { texto: "Brutal", cor: "#e05a4a", r };
}
