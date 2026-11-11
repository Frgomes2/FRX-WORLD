"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { lerLoot } from "@/lib/dungeons";
import { configEventos, salvarConfigEventos, SUGESTOES_EVENTOS, TIPOS_EVENTO } from "@/lib/eventos";

async function exigirAdmin() {
  if (!(await auth())?.user) throw new Error("Não autorizado");
}

export type Estado = { erro?: string; ok?: string } | null;

const mob = z.string().trim().toLowerCase().transform((a) => (a === "" || a.includes(":") ? a : `minecraft:${a}`));

const evento = z.object({
  nome: z.string().trim().min(2).max(60),
  tipo: z.enum(Object.keys(TIPOS_EVENTO) as [string, ...string[]]),
  mensagem: z.string().trim().max(200),
  peso: z.coerce.number().int().min(1).max(1000),
  nivelMin: z.coerce.number().int().min(1).max(1000),
  nivelMax: z.coerce.number().int().min(1).max(1000),
  mobTipo: mob,
  mobQtd: z.coerce.number().int().min(0).max(20),
  vida: z.coerce.number().int().min(0).max(5000),
  dano: z.coerce.number().min(0).max(500),
  preco: z.coerce.number().int().min(0).max(64),
  recompensaXp: z.coerce.number().int().min(0).max(10_000_000),
});

export async function salvarEvento(_e: Estado, f: FormData): Promise<Estado> {
  await exigirAdmin();
  const d = evento.safeParse({ mensagem: "", ...Object.fromEntries(f.entries()) });
  if (!d.success) return { erro: `${d.error.issues[0].path.join(".")}: ${d.error.issues[0].message}` };
  if (d.data.nivelMax < d.data.nivelMin) return { erro: "Nível máximo menor que o mínimo." };
  if (d.data.tipo !== "TESOURO" && d.data.tipo !== "MERCADOR" && !d.data.mobTipo) return { erro: "Informe o mob." };
  let loot;
  try { loot = lerLoot(String(f.get("loot") ?? "")); }
  catch { return { erro: "Loot inválido. Use minecraft:diamond*2@50 (item*quantidade@chance%)." }; }
  const valores = { ...d.data, loot, ativo: f.get("ativo") === "on" };
  const id = String(f.get("id") ?? "");
  if (id) await prisma.eventoEstrada.update({ where: { id }, data: valores });
  else await prisma.eventoEstrada.create({ data: valores });
  revalidatePath("/eventos");
  return { ok: "Salvo" };
}

export async function excluirEvento(f: FormData) {
  await exigirAdmin();
  await prisma.eventoEstrada.delete({ where: { id: String(f.get("id")) } });
  revalidatePath("/eventos");
}

export async function carregarSugestoesEventos() {
  await exigirAdmin();
  const existentes = new Set((await prisma.eventoEstrada.findMany({ select: { nome: true } })).map((e) => e.nome));
  for (const e of SUGESTOES_EVENTOS) if (!existentes.has(e.nome)) await prisma.eventoEstrada.create({ data: e });
  revalidatePath("/eventos");
}

export async function salvarConfig(_e: Estado, f: FormData): Promise<Estado> {
  await exigirAdmin();
  const n = (k: string) => Number(f.get(k));
  const r = configEventos.safeParse({
    ativo: f.get("ativo") === "on", checagemSegundos: n("checagemSegundos"), chance: n("chance"),
    recargaMinutos: n("recargaMinutos"), distanciaSpawn: n("distanciaSpawn"), andarMinimo: n("andarMinimo"),
  });
  if (!r.success) return { erro: `${r.error.issues[0].path.join(".")}: ${r.error.issues[0].message}` };
  await salvarConfigEventos(r.data);
  revalidatePath("/eventos");
  return { ok: "Configuração salva" };
}
