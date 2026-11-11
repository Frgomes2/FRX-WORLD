import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const dynamic = "force-dynamic";

/** O servidor Minecraft lê as dungeons ativas ao ligar e a cada minuto. */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const lista = await prisma.dungeon.findMany({ where: { ativa: true }, orderBy: { distanciaMin: "asc" } });
  return NextResponse.json(lista.map(({ criadoEm, atualizadoEm, ativa, ...d }) => d));
}
