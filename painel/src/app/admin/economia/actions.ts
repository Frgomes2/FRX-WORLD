"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { configSchema, lerConfig, salvarConfig } from "@/lib/config-jogo";

async function exigirAdmin() {
  if (!(await auth())?.user) throw new Error("Não autorizado");
}

const schema = z.object({
  loja: z.enum(["MERCADOR", "COMPRADOR"]),
  item: z.string().trim().min(3).max(120).transform((a) => (a.includes(":") ? a : `minecraft:${a}`).toLowerCase()),
  qtd: z.coerce.number().int().min(1).max(64),
  preco: z.coerce.number().int().min(1).max(640_000),
  ordem: z.coerce.number().int().min(0).max(9999),
});

export type EstadoItem = { erro: string } | { ok: true } | null;

export async function salvarItem(_e: EstadoItem, f: FormData): Promise<EstadoItem> {
  await exigirAdmin();
  const id = String(f.get("id") ?? "");
  const dados = schema.safeParse({
    loja: f.get("loja"), item: f.get("item"), qtd: f.get("qtd"), preco: f.get("preco"), ordem: f.get("ordem") || 0,
  });
  if (!dados.success) {
    const p = dados.error.issues[0];
    return { erro: `${p.path.join(".")}: ${p.message}` };
  }
  if (id) await prisma.itemLoja.update({ where: { id }, data: dados.data });
  else await prisma.itemLoja.create({ data: dados.data });
  revalidatePath("/admin/economia");
  return { ok: true };
}

export async function alternarItem(f: FormData) {
  await exigirAdmin();
  const id = String(f.get("id"));
  const i = await prisma.itemLoja.findUniqueOrThrow({ where: { id } });
  await prisma.itemLoja.update({ where: { id }, data: { ativo: !i.ativo } });
  revalidatePath("/admin/economia");
}

export async function excluirItem(f: FormData) {
  await exigirAdmin();
  await prisma.itemLoja.delete({ where: { id: String(f.get("id")) } });
  revalidatePath("/admin/economia");
}

/** Avaliação automática do comprador (preços por raridade e teto diário de ouro). */
export async function salvarComprador(c: unknown): Promise<{ ok: boolean; mensagem: string }> {
  if (!(await auth())?.user) return { ok: false, mensagem: "Sessão expirada. Entre de novo." };
  const atual = await lerConfig();
  const dados = configSchema.safeParse({ ...atual, comprador: c });
  if (!dados.success) {
    const p = dados.error.issues[0];
    return { ok: false, mensagem: `${p.path.slice(1).join(".")}: ${p.message}` };
  }
  await salvarConfig(dados.data);
  revalidatePath("/admin/economia");
  return { ok: true, mensagem: "Salvo. O servidor aplica em até 1 minuto (ou use /frx recarregar)." };
}

export type ItemLojaDados = { item: string; qtd: number; preco: number; ativo: boolean; nivelMin: number };

/** Salva a lista inteira de uma loja (a ordem é a posição na lista). */
export async function salvarLoja(loja: string, itens: ItemLojaDados[]): Promise<{ ok: boolean; erro?: string }> {
  await exigirAdmin();
  const lista = z.array(z.object({
    item: schema.shape.item, qtd: schema.shape.qtd, preco: schema.shape.preco, ativo: z.boolean(),
    nivelMin: z.coerce.number().int().min(1).max(1000).default(1),
  })).max(200).safeParse(itens);
  const l = z.enum(["MERCADOR", "COMPRADOR"]).safeParse(loja);
  if (!lista.success || !l.success) {
    const p = lista.success ? null : lista.error.issues[0];
    return { ok: false, erro: p ? `Item ${Number(p.path[0]) + 1}: ${p.path.slice(1).join(".")} ${p.message}` : "loja inválida" };
  }
  await prisma.$transaction([
    prisma.itemLoja.deleteMany({ where: { loja: l.data } }),
    prisma.itemLoja.createMany({ data: lista.data.map((i, ordem) => ({ ...i, loja: l.data, ordem })) }),
  ]);
  revalidatePath("/admin/economia");
  return { ok: true };
}
