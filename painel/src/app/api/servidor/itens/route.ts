import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { tokenValido, naoAutorizado } from "@/lib/servidor";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** O mod manda, ao ligar, a lista de itens do modpack. */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const corpo = await req.json().catch(() => null);
  if (!Array.isArray(corpo?.itens)) return NextResponse.json({ erro: "sem lista" }, { status: 400 });
  await prisma.configuracao.upsert({ where: { chave: "itens" }, update: { valor: corpo }, create: { chave: "itens", valor: corpo } });
  return NextResponse.json({ ok: true });
}
