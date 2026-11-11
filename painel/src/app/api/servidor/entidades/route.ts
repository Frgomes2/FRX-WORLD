import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { tokenValido, naoAutorizado } from "@/lib/servidor";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** O mod manda, ao ligar, a lista de criaturas do modpack. */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const corpo = await req.json().catch(() => null);
  if (!corpo?.entidades) return NextResponse.json({ erro: "sem lista" }, { status: 400 });
  await prisma.configuracao.upsert({ where: { chave: "entidades" }, update: { valor: corpo }, create: { chave: "entidades", valor: corpo } });
  return NextResponse.json({ ok: true });
}
