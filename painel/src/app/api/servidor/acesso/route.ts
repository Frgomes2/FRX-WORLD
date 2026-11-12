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

  let jogador = await prisma.jogador.findUnique({ where: { uuid: dados.data.uuid } });
  if (!jogador) {
    // Servidor offline (TLauncher/Shiginima): o UUID muda conforme o modo do servidor, mas o nick é o mesmo.
    // Quem já usou convite com esse nick continua liberado; a ficha passa para o UUID novo.
    const peloNick = await prisma.jogador.findFirst({ where: { nick: { equals: dados.data.nick, mode: "insensitive" }, ativo: true } });
    if (peloNick) jogador = await prisma.jogador.update({ where: { id: peloNick.id }, data: { uuid: dados.data.uuid } });
  }
  if (!jogador || !jogador.ativo) return NextResponse.json({ permitido: false });

  // Fichas antigas do mesmo nick (UUID de quando o servidor era online-mode) viram duplicadas: junta na atual.
  const antigas = await prisma.jogador.findMany({ where: { nick: { equals: dados.data.nick, mode: "insensitive" }, id: { not: jogador.id } }, select: { id: true } });
  if (antigas.length) {
    const ids = antigas.map((a) => a.id);
    await prisma.$transaction([
      prisma.convite.updateMany({ where: { jogadorId: { in: ids } }, data: { jogadorId: jogador.id } }),
      prisma.jogador.deleteMany({ where: { id: { in: ids } } }),
    ]);
  }

  await prisma.jogador.update({
    where: { id: jogador.id },
    data: { nick: dados.data.nick, ultimoLogin: new Date() },
  });
  return NextResponse.json({ permitido: true });
}
