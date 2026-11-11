"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { lerLoot } from "@/lib/dungeons";
import { SUGESTOES_CHEFES, SUGESTOES_ESTRUTURAS } from "@/lib/externos";

async function exigirAdmin() {
  if (!(await auth())?.user) throw new Error("Não autorizado");
}

const idMod = z.string().trim().toLowerCase().regex(/^[a-z0-9_.-]+:[a-z0-9_./-]+$/, "use o formato mod:nome");

const chefe = z.object({
  entidade: idMod,
  nome: z.string().trim().min(1).max(60),
  origem: z.string().trim().max(40),
  nivelRecomendado: z.coerce.number().int().min(1).max(1000),
  recompensaXp: z.coerce.number().int().min(0).max(10_000_000),
  recargaHoras: z.coerce.number().int().min(0).max(24 * 30),
});

const estrutura = z.object({
  estrutura: idMod,
  nome: z.string().trim().min(1).max(60),
  origem: z.string().trim().max(40),
  nivelRecomendado: z.coerce.number().int().min(1).max(1000),
});

export type Estado = { erro?: string; ok?: string } | null;

export async function salvarChefe(_e: Estado, f: FormData): Promise<Estado> {
  await exigirAdmin();
  const d = chefe.safeParse(Object.fromEntries(f.entries()));
  if (!d.success) return { erro: `${d.error.issues[0].path.join(".")}: ${d.error.issues[0].message}` };
  let loot;
  try { loot = lerLoot(String(f.get("loot") ?? "")); }
  catch { return { erro: "Loot inválido. Use minecraft:diamond*2@50 (item*quantidade@chance%)." }; }
  const valores = { ...d.data, loot, ativo: f.get("ativo") === "on" };
  const id = String(f.get("id") ?? "");
  try {
    if (id) await prisma.chefeExterno.update({ where: { id }, data: valores });
    else await prisma.chefeExterno.create({ data: valores });
  } catch {
    return { erro: "Já existe um chefe com essa entidade." };
  }
  revalidatePath("/externos");
  return { ok: "Salvo" };
}

export async function salvarEstrutura(_e: Estado, f: FormData): Promise<Estado> {
  await exigirAdmin();
  const d = estrutura.safeParse(Object.fromEntries(f.entries()));
  if (!d.success) return { erro: `${d.error.issues[0].path.join(".")}: ${d.error.issues[0].message}` };
  const valores = { ...d.data, ativa: f.get("ativa") === "on" };
  const id = String(f.get("id") ?? "");
  try {
    if (id) await prisma.estruturaExterna.update({ where: { id }, data: valores });
    else await prisma.estruturaExterna.create({ data: valores });
  } catch {
    return { erro: "Já existe essa estrutura." };
  }
  revalidatePath("/externos");
  return { ok: "Salvo" };
}

export async function excluirChefe(f: FormData) {
  await exigirAdmin();
  await prisma.chefeExterno.delete({ where: { id: String(f.get("id")) } });
  revalidatePath("/externos");
}

export async function excluirEstrutura(f: FormData) {
  await exigirAdmin();
  await prisma.estruturaExterna.delete({ where: { id: String(f.get("id")) } });
  revalidatePath("/externos");
}

/** Cria as sugestões que ainda não existem (não mexe nas já cadastradas). */
export async function carregarSugestoes() {
  await exigirAdmin();
  for (const c of SUGESTOES_CHEFES) await prisma.chefeExterno.upsert({ where: { entidade: c.entidade }, update: {}, create: c });
  for (const e of SUGESTOES_ESTRUTURAS) await prisma.estruturaExterna.upsert({ where: { estrutura: e.estrutura }, update: {}, create: e });
  revalidatePath("/externos");
}
