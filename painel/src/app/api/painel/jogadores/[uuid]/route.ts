import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Ficha atual (a tela recarrega de tempos em tempos). */
export async function GET(_req: Request, { params }: { params: Promise<{ uuid: string }> }) {
  if (!(await auth())?.user) return NextResponse.json({ erro: "não autorizado" }, { status: 401 });
  const { uuid } = await params;
  const j = await prisma.jogador.findUnique({ where: { uuid } });
  if (!j) return NextResponse.json({ erro: "jogador não encontrado" }, { status: 404 });
  return NextResponse.json({ detalhe: j.detalhe, detalheEm: j.detalheEm?.getTime() ?? null, online: j.online });
}
