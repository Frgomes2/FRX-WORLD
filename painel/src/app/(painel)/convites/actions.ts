"use server";

import { revalidatePath } from "next/cache";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { gerarCodigo } from "@/lib/codigo";

async function exigirAdmin() {
  const sessao = await auth();
  if (!sessao?.user) throw new Error("Não autorizado");
}

export async function criarConvite(form: FormData) {
  await exigirAdmin();
  const nota = String(form.get("nota") ?? "").trim() || null;
  await prisma.convite.create({ data: { codigo: gerarCodigo(), nota } });
  revalidatePath("/convites");
}

export async function revogarConvite(form: FormData) {
  await exigirAdmin();
  await prisma.convite.update({ where: { id: String(form.get("id")) }, data: { revogado: true } });
  revalidatePath("/convites");
}
