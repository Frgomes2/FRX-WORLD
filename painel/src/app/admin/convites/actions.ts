"use server";

import { revalidatePath } from "next/cache";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { configSchema, lerConfig, salvarConfig } from "@/lib/config-jogo";
import { gerarCodigo } from "@/lib/codigo";

async function exigirAdmin() {
  const sessao = await auth();
  if (!sessao?.user) throw new Error("Não autorizado");
}

export async function criarConvite(form: FormData) {
  await exigirAdmin();
  const nota = String(form.get("nota") ?? "").trim() || null;
  await prisma.convite.create({ data: { codigo: gerarCodigo(), nota } });
  revalidatePath("/admin/convites");
}

export async function revogarConvite(form: FormData) {
  await exigirAdmin();
  await prisma.convite.update({ where: { id: String(form.get("id")) }, data: { revogado: true } });
  revalidatePath("/admin/convites");
}

/** Abertura do primeiro login (tela do Sistema, capital inicial). */
export async function salvarAbertura(abertura: unknown): Promise<{ ok: boolean; mensagem: string }> {
  if (!(await auth())?.user) return { ok: false, mensagem: "Sessão expirada. Entre de novo." };
  const atual = await lerConfig();
  const dados = configSchema.safeParse({ ...atual, abertura });
  if (!dados.success) {
    const p = dados.error.issues[0];
    return { ok: false, mensagem: `${p.path.slice(1).join(".")}: ${p.message}` };
  }
  await salvarConfig(dados.data);
  revalidatePath("/admin/convites");
  return { ok: true, mensagem: "Salvo. O servidor aplica em até 1 minuto (ou use /frx recarregar)." };
}
