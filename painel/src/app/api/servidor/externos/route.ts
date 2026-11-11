import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const dynamic = "force-dynamic";

/** Chefes e estruturas de outros mods (Cataclysm, Dungeon Now Loading, IDAS, YUNG's) lidos pelo servidor. */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const [chefes, estruturas] = await Promise.all([
    prisma.chefeExterno.findMany({ where: { ativo: true } }),
    prisma.estruturaExterna.findMany({ where: { ativa: true } }),
  ]);
  return NextResponse.json({
    chefes: chefes.map((c) => ({ entidade: c.entidade, nome: c.nome, nivelRecomendado: c.nivelRecomendado, recompensaXp: c.recompensaXp, loot: c.loot, recargaHoras: c.recargaHoras })),
    estruturas: estruturas.map((e) => ({ estrutura: e.estrutura, nome: e.nome, nivelRecomendado: e.nivelRecomendado })),
  });
}
