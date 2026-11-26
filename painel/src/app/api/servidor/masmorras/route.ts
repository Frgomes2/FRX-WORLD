import { NextResponse } from "next/server";
import { z } from "zod";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const dynamic = "force-dynamic";

/** O servidor lê as masmorras ao ligar (mesmo formato do posicoes.json). */
/**
 * Masmorras dos reinos que não tinham nenhuma (2.0.9). Entram no banco uma vez; depois disso o painel manda
 * (dá para editar ou desativar em Admin → Masmorras, e elas não voltam sozinhas).
 */
const NOVAS_DOS_REINOS = [
  { id: "oeste-cidadela", nome: "Cidadela em Ruínas sob as Nações do Oeste", chefe: "Ender Guardian",
    estrutura: "cataclysm:ruined_citadel|cataclysm:cursed_pyramid|cataclysm:ancient_factory", nivel: 0, x: -1200, z: 2400, ordem: 20 },
  { id: "demonios-forja", nome: "Forja das Almas sob os Lordes Demônios", chefe: "Netherite Monstrosity",
    estrutura: "cataclysm:soul_black_smith|cataclysm:acropolis|cataclysm:ancient_factory", nivel: 0, x: -1600, z: -2600, ordem: 21 },
];

export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const semeadas = await prisma.configuracao.findUnique({ where: { chave: "masmorras_reinos_2_0_9" } }).catch(() => null);
  if (!semeadas) {
    await prisma.masmorra.createMany({ data: NOVAS_DOS_REINOS, skipDuplicates: true });
    await prisma.configuracao.create({ data: { chave: "masmorras_reinos_2_0_9", valor: "1" } }).catch(() => null);
  }
  const lista = await prisma.masmorra.findMany({ where: { ativo: true }, orderBy: [{ ordem: "asc" }, { id: "asc" }] });
  return NextResponse.json(lista.map((m) => ({
    id: m.id,
    nome: m.nome,
    chefe: m.chefe,
    estrutura: m.estrutura,
    x: m.x,
    z: m.z,
    ...(m.entradaX !== null && m.entradaY !== null && m.entradaZ !== null ? { entrada: [m.entradaX, m.entradaY, m.entradaZ] } : {}),
    ...(m.nivel > 0 ? { nivel: m.nivel } : {}),
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
