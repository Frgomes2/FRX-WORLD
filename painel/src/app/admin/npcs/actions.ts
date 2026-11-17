"use server";

import { revalidatePath } from "next/cache";
import { auth } from "@/auth";
import { configSchema, lerConfig, salvarConfig } from "@/lib/config-jogo";

/** Falas da caixa de diálogo dos NPCs, por papel. */
export async function salvarDialogos(dialogos: Record<string, string[]>): Promise<{ ok: boolean; mensagem: string }> {
  if (!(await auth())?.user) return { ok: false, mensagem: "Sessão expirada. Entre de novo." };
  const atual = await lerConfig();
  const dados = configSchema.safeParse({ ...atual, dialogos });
  if (!dados.success) {
    const p = dados.error.issues[0];
    return { ok: false, mensagem: `${p.path.slice(1).join(" → ")}: ${p.message}` };
  }
  await salvarConfig(dados.data);
  revalidatePath("/admin/npcs");
  return { ok: true, mensagem: "Salvo. O servidor aplica em até 1 minuto (ou use /frx recarregar)." };
}
