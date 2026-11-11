"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { CLASSES, ELEMENTOS, SUGESTOES_HABILIDADES, TIPOS } from "@/lib/habilidades";

async function exigirAdmin() {
  if (!(await auth())?.user) throw new Error("Não autorizado");
}

export type Estado = { erro?: string; ok?: string } | null;

const chave = <T extends object>(o: T) => z.enum(Object.keys(o) as [string, ...string[]]);
const idMc = z.string().trim().toLowerCase().transform((a) => (a === "" || a.includes(":") ? a : `minecraft:${a}`));

const schema = z.object({
  codigo: z.string().trim().toLowerCase().regex(/^[a-z0-9_]{2,40}$/, "use letras minúsculas, números e _"),
  nome: z.string().trim().min(2).max(40),
  descricao: z.string().trim().max(200),
  classe: chave(CLASSES),
  tipo: chave(TIPOS),
  elemento: chave(ELEMENTOS),
  icone: idMc,
  custo: z.coerce.number().int().min(0).max(1000),
  cooldown: z.coerce.number().min(0).max(3600),
  dano: z.coerce.number().min(-500).max(1000),
  alcance: z.coerce.number().min(-30).max(64),
  raio: z.coerce.number().min(0).max(32),
  duracao: z.coerce.number().min(0).max(600),
  efeito: idMc,
  potencia: z.coerce.number().int().min(0).max(20),
  nivelReq: z.coerce.number().int().min(1).max(1000),
  aprendizado: z.enum(["NIVEL", "ITEM"]),
});

export async function salvarHabilidade(_e: Estado, f: FormData): Promise<Estado> {
  await exigirAdmin();
  const d = schema.safeParse({ descricao: "", efeito: "", ...Object.fromEntries(f.entries()) });
  if (!d.success) return { erro: `${d.error.issues[0].path.join(".")}: ${d.error.issues[0].message}` };
  if (d.data.tipo === "INVOCAR" && !d.data.efeito) return { erro: "INVOCAR precisa do mob no campo efeito (ex.: minecraft:wolf)." };
  if (d.data.tipo === "BUFF" && !d.data.efeito) return { erro: "BUFF precisa de um efeito (ex.: minecraft:speed)." };
  const valores = { ...d.data, ultimate: f.get("ultimate") === "on", ativa: f.get("ativa") === "on" };
  const id = String(f.get("id") ?? "");
  try {
    if (id) await prisma.habilidade.update({ where: { id }, data: valores });
    else await prisma.habilidade.create({ data: valores });
  } catch {
    return { erro: "Já existe uma habilidade com esse código." };
  }
  revalidatePath("/habilidades");
  return { ok: "Salvo" };
}

export async function excluirHabilidade(f: FormData) {
  await exigirAdmin();
  await prisma.habilidade.delete({ where: { id: String(f.get("id")) } });
  revalidatePath("/habilidades");
}

export async function carregarSugestoesHabilidades() {
  await exigirAdmin();
  for (const h of SUGESTOES_HABILIDADES) await prisma.habilidade.upsert({ where: { codigo: h.codigo }, update: {}, create: h });
  revalidatePath("/habilidades");
}
