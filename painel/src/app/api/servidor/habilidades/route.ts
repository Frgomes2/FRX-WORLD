import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const dynamic = "force-dynamic";

/** Habilidades ativas, lidas pelo servidor ao ligar e a cada minuto. */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  try {
    const lista = await prisma.habilidade.findMany({ where: { ativa: true }, orderBy: [{ classe: "asc" }, { nivelReq: "asc" }] });
    return NextResponse.json(lista.map(({ id, criadoEm, atualizadoEm, ativa, ...h }) => h));
  } catch (e) {
    console.error("[/api/servidor/habilidades]", e);
    return NextResponse.json({ erro: String(e instanceof Error ? e.message : e).slice(0, 500) }, { status: 500 });
  }
}
