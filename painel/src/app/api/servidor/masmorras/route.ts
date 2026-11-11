import { NextResponse } from "next/server";
import { z } from "zod";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const dynamic = "force-dynamic";

/** O servidor lê as masmorras ao ligar (mesmo formato do posicoes.json). */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const lista = await prisma.masmorra.findMany({ where: { ativo: true }, orderBy: [{ ordem: "asc" }, { id: "asc" }] });
  return NextResponse.json(lista.map((m) => ({
    id: m.id,
    nome: m.nome,
    chefe: m.chefe,
    estrutura: m.estrutura,
    x: m.x,
    z: m.z,
    ...(m.entradaX !== null && m.entradaY !== null && m.entradaZ !== null ? { entrada: [m.entradaX, m.entradaY, m.entradaZ] } : {}),
    ...(m.refazer ? { refazer: true } : {}),
  })));
}

const schema = z.object({
  colocadas: z.array(z.object({
    id: z.string().min(1).max(60),
    x: z.number().int(),
    y: z.number().int(),
    z: z.number().int(),
    estrutura: z.string().max(200),
    nova: z.boolean().optional(),
  })).max(100),
});

/** O servidor avisa onde cada masmorra ficou (ao ligar e quando coloca uma nova). */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const dados = schema.safeParse(await req.json().catch(() => null));
  if (!dados.success) return NextResponse.json({ erro: "dados inválidos" }, { status: 400 });
  for (const c of dados.data.colocadas) {
    await prisma.masmorra.updateMany({
      where: { id: c.id },
      data: {
        colocadaX: c.x, colocadaY: c.y, colocadaZ: c.z, colocadaEstrutura: c.estrutura,
        ...(c.nova ? { colocadaEm: new Date(), refazer: false } : {}),
      },
    });
  }
  return NextResponse.json({ ok: true });
}
