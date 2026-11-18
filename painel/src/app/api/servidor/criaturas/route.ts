import { NextResponse } from "next/server";
import { z } from "zod";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";
import { pngDaSkin } from "@/lib/criaturas-codex";

export const dynamic = "force-dynamic";

const lista = (s: string) => s.split(",").map((x) => x.trim()).filter(Boolean);

/** O servidor lê os chefes e monstros do Códex ao ligar e a cada minuto. As skins vão junto com as dos NPCs. */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const todas = await prisma.criaturaCodex.findMany({ where: { ativa: true }, orderBy: [{ tipo: "asc" }, { rankMin: "asc" }, { nome: "asc" }] });
  return NextResponse.json({
    criaturas: todas.map((c) => ({
      codigo: c.codigo, nome: c.nome, tipo: c.tipo, entidade: c.entidade, rankMin: c.rankMin, rankMax: c.rankMax,
      vida: c.vida, dano: c.dano, velocidade: c.velocidade, golpes: lista(c.golpes), lacaio: c.lacaio, locais: lista(c.locais), peso: c.peso,
      xp: c.xp, moedas: c.moedas, dropItem: c.dropItem, dropChance: c.dropChance, dropQtd: c.dropQtd,
      escala: c.escala, brilho: c.brilho, skin: !!pngDaSkin(c.skin),
    })),
  });
}

const schema = z.object({ codigo: z.string().regex(/^[a-z0-9_]{2,30}$/) });

/** O servidor avisa cada vez que uma criatura do Códex é derrotada. */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const dados = schema.safeParse(await req.json().catch(() => null));
  if (!dados.success) return NextResponse.json({ erro: "dados inválidos" }, { status: 400 });
  await prisma.criaturaCodex.updateMany({ where: { codigo: dados.data.codigo }, data: { derrotas: { increment: 1 } } });
  return NextResponse.json({ ok: true });
}
