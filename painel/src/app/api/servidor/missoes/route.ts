import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const dynamic = "force-dynamic";

/** O servidor Minecraft lê as missões ativas ao ligar e a cada minuto. */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const missoes = await prisma.missao.findMany({
    where: { ativa: true },
    orderBy: [{ nivelMin: "asc" }, { ordem: "asc" }],
  });
  return NextResponse.json(
    missoes.map((m) => ({
      id: m.id, titulo: m.titulo, descricao: m.descricao, tipo: m.tipo, alvo: m.alvo,
      quantidade: m.quantidade, nivelMin: m.nivelMin, recompensaXp: m.recompensaXp, recompensaMoedas: m.recompensaMoedas,
      recompensaItens: m.recompensaItens, repetivel: m.repetivel, intervaloHoras: m.intervaloHoras,
      preRequisitoId: m.preRequisitoId,
    })),
  );
}
