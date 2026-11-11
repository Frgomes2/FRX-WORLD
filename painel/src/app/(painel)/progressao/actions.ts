"use server";

import { revalidatePath } from "next/cache";
import { auth } from "@/auth";
import { configSchema, lerConfig, salvarConfig, type ConfigJogo } from "@/lib/config-jogo";

export type Resultado = { ok: boolean; mensagem: string };

type Editavel = Pick<ConfigJogo, "progressao" | "ranks" | "morte" | "ferido" | "invasoes" | "chefeMundial" | "floresta" | "zonas">;

const NOMES: Record<string, string> = {
  progressao: "Curva de XP", ranks: "Ranks", morte: "Morte", ferido: "Ferido", invasoes: "Invasões",
  chefeMundial: "Chefe mundial", floresta: "Floresta sombria", zonas: "Zonas de perigo",
};

/** Salva as seções da aba Progressão (forja e taverna ficam como estão). */
export async function salvarProgressao(novo: Editavel): Promise<Resultado> {
  if (!(await auth())?.user) return { ok: false, mensagem: "Sessão expirada. Entre de novo." };
  const atual = await lerConfig();
  const dados = configSchema.safeParse({ ...atual, ...novo, forja: atual.forja, taverna: atual.taverna });
  if (!dados.success) {
    const p = dados.error.issues[0];
    return { ok: false, mensagem: `${NOMES[String(p.path[0])] ?? p.path[0]} → ${p.path.slice(1).join(".")}: ${p.message}` };
  }
  const r = dados.data.ranks;
  if (!r.every((x, i) => i === 0 || x.nivelMin > r[i - 1].nivelMin)) return { ok: false, mensagem: "Ranks: os níveis precisam crescer de F até S." };
  const m = dados.data.morte;
  if (!m.every((x, i) => i === 0 || x.ate > m[i - 1].ate)) return { ok: false, mensagem: "Morte: cada faixa precisa ir até um nível maior que a anterior." };
  if (dados.data.chefeMundial.distanciaMax <= dados.data.chefeMundial.distanciaMin) return { ok: false, mensagem: "Chefe mundial: a distância máxima precisa ser maior que a mínima." };
  await salvarConfig(dados.data);
  revalidatePath("/progressao");
  return { ok: true, mensagem: "Salvo! O servidor aplica em até 1 minuto (ou use /frx recarregar)." };
}
