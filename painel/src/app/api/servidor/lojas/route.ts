import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const dynamic = "force-dynamic";

/** O servidor Minecraft lê as tabelas das lojas das capitais ao ligar e a cada minuto. */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const itens = await prisma.itemLoja.findMany({ where: { ativo: true }, orderBy: [{ ordem: "asc" }, { criadoEm: "asc" }] });
  const linha = (i: (typeof itens)[number]) => ({ item: i.item, qtd: i.qtd, preco: i.preco });
  return NextResponse.json({
    mercador: itens.filter((i) => i.loja === "MERCADOR").map(linha),
    comprador: itens.filter((i) => i.loja === "COMPRADOR").map(linha),
  });
}
