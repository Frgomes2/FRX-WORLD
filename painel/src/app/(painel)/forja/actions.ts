"use server";

import { revalidatePath } from "next/cache";
import { auth } from "@/auth";
import { FORJA_PADRAO, configSchema, lerConfig, salvarConfig } from "@/lib/config-jogo";

export type EstadoForja = { ok: boolean; mensagem: string } | null;

const num = (f: FormData, k: string) => Number(String(f.get(k) ?? "").replace(",", "."));
const id = (f: FormData, k: string) => {
  const v = String(f.get(k) ?? "").trim().toLowerCase();
  return v.includes(":") ? v : `minecraft:${v}`;
};

export async function salvarForja(_e: EstadoForja, f: FormData): Promise<EstadoForja> {
  if (!(await auth())?.user) return { ok: false, mensagem: "Sessão expirada." };

  const forja = f.get("restaurar") === "1" ? FORJA_PADRAO : {
    anunciarAPartirDe: num(f, "anunciar"),
    niveis: Array.from({ length: 10 }, (_, i) => ({
      moedas: num(f, `n.${i}.moedas`),
      material: id(f, `n.${i}.material`),
      qtd: num(f, `n.${i}.qtd`),
      chance: num(f, `n.${i}.chance`),
    })),
    reforja: {
      moedasBase: num(f, "r.moedasBase"),
      material: id(f, "r.material"),
      qtdBase: num(f, "r.qtdBase"),
      qtdPorRaridade: num(f, "r.qtdPorRaridade"),
    },
  };

  const dados = configSchema.safeParse({ ...(await lerConfig()), forja });
  if (!dados.success) {
    const p = dados.error.issues[0];
    const onde = p.path[0] === "forja" && p.path[1] === "niveis" ? `+${Number(p.path[2]) + 1} (${String(p.path[3])})` : p.path.join(".");
    return { ok: false, mensagem: `Valor inválido em ${onde}: ${p.message}` };
  }
  await salvarConfig(dados.data);
  revalidatePath("/forja");
  return { ok: true, mensagem: "Salvo. O servidor aplica em até 1 minuto (ou use /frx recarregar)." };
}
