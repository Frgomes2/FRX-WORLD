"use client";

import { IconeCriatura, useCriaturas } from "@/components/criaturas";
import { IconeItem, useMapaItens } from "@/components/itens";

/** Ícone + nome do alvo de uma missão (criatura para Caçar, item para Coletar). */
export function Alvo({ tipo, alvo, tam = 32, soIcone = false }: { tipo: string; alvo: string; tam?: number; soIcone?: boolean }) {
  const criaturas = useCriaturas();
  const itens = useMapaItens();
  if (tipo === "MATAR") {
    const c = criaturas?.find((x) => x.id === alvo);
    return <span className="inline-flex items-center gap-1.5"><IconeCriatura c={c} id={alvo} tam={tam} />{!soIcone && <span>{c?.nome ?? alvo.split(":")[1]?.replace(/_/g, " ")}</span>}</span>;
  }
  const i = itens.get(alvo.split("#")[0]);
  return <span className="inline-flex items-center gap-1.5"><IconeItem id={alvo} tam={tam} />{!soIcone && <span>{i?.nome ?? alvo.split(":")[1]?.replace(/_/g, " ")}</span>}</span>;
}
