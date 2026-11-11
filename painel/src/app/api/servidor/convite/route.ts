import { NextResponse } from "next/server";
import { z } from "zod";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

const schema = z.object({
  uuid: z.string().min(1),
  nick: z.string().min(1),
  codigo: z.string().min(1),
});

/**
 * O servidor chama quando o jogador digita /convite CÓDIGO no primeiro login.
 * Valida o código (uso único), cadastra a conta e libera o acesso.
 */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const dados = schema.safeParse(await req.json().catch(() => null));
  if (!dados.success) return NextResponse.json({ erro: "dados inválidos" }, { status: 400 });

  const codigo = dados.data.codigo.trim().toUpperCase();
  const convite = await prisma.convite.findUnique({ where: { codigo } });

  if (!convite || convite.revogado) {
    return NextResponse.json({ ok: false, motivo: "Código inválido." });
  }
  if (convite.usadoEm) {
    return NextResponse.json({ ok: false, motivo: "Este código já foi usado." });
  }

  const existente = await prisma.jogador.findUnique({ where: { uuid: dados.data.uuid } });
  if (existente?.ativo) {
    return NextResponse.json({ ok: true, motivo: "Conta já liberada." });
  }

  await prisma.$transaction(async (tx) => {
    const jogador = existente
      ? await tx.jogador.update({ where: { id: existente.id }, data: { ativo: true, nick: dados.data.nick } })
      : await tx.jogador.create({ data: { uuid: dados.data.uuid, nick: dados.data.nick, ultimoLogin: new Date() } });
    await tx.convite.update({
      where: { id: convite.id },
      data: { usadoEm: new Date(), jogadorId: jogador.id },
    });
  });

  return NextResponse.json({ ok: true, motivo: "Bem-vindo ao FRX-WORLD!" });
}
