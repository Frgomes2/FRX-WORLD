"use server";

import { revalidatePath } from "next/cache";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";

export async function alternarAcesso(form: FormData) {
  const sessao = await auth();
  if (!sessao?.user) throw new Error("Não autorizado");
  const id = String(form.get("id"));
  const jogador = await prisma.jogador.findUniqueOrThrow({ where: { id } });
  await prisma.jogador.update({ where: { id }, data: { ativo: !jogador.ativo } });
  revalidatePath("/admin/jogadores");
}
