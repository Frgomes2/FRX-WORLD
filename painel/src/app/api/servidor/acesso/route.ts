import { NextResponse } from "next/server";
import { z } from "zod";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

const schema = z.object({ uuid: z.string().min(1), nick: z.string().min(1) });

/**
 * O servidor chama ao jogador entrar.
 * Resposta: { permitido: true } se a conta já usou um convite e está ativa.
 */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const dados = schema.safeParse(await req.json().catch(() => null));
  if (!dados.success) return NextResponse.json({ erro: "dados inválidos" }, { status: 400 });

  const jogador = await prisma.jogador.findUnique({ where: { uuid: dados.data.uuid } });
  if (!jogador || !jogador.ativo) return NextResponse.json({ permitido: false });

  await prisma.jogador.update({
    where: { id: jogador.id },
    data: { nick: dados.data.nick, ultimoLogin: new Date() },
  });
  return NextResponse.json({ permitido: true });
}
