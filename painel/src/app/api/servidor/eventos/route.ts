import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";
import { lerConfigEventos } from "@/lib/eventos";

export const dynamic = "force-dynamic";

/** Eventos de estrada: configuração e lista ativa, lidas pelo servidor a cada minuto. */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  try {
    const [config, eventos] = await Promise.all([lerConfigEventos(), prisma.eventoEstrada.findMany({ where: { ativo: true } })]);
    return NextResponse.json({
      config,
      eventos: eventos.map(({ criadoEm, atualizadoEm, ativo, ...e }) => e),
    });
  } catch (e) {
    console.error("[/api/servidor/eventos]", e);
    return NextResponse.json({ erro: String(e instanceof Error ? e.message : e).slice(0, 500) }, { status: 500 });
  }
}
