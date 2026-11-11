import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { tokenValido, naoAutorizado } from "@/lib/servidor";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** O mod manda a ficha completa do jogador (ao entrar, a cada minuto, ao sair e depois de ações de admin). */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const corpo = (await req.json().catch(() => null)) as ({ uuid?: string; online?: boolean } & Record<string, unknown>) | null;
  if (!corpo?.uuid) return NextResponse.json({ erro: "sem uuid" }, { status: 400 });
  await prisma.jogador.updateMany({
    where: { uuid: corpo.uuid },
    data: { detalhe: corpo as object, detalheEm: new Date(), online: corpo.online !== false },
  });
  return NextResponse.json({ ok: true });
}
