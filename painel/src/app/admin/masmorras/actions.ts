"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";

async function exigirAdmin() {
  if (!(await auth())?.user) throw new Error("Não autorizado");
}

const inteiro = z.coerce.number().int().min(-30_000_000).max(30_000_000);
const opcional = z.preprocess((v) => (v === "" || v === null || v === undefined ? null : v), z.coerce.number().int().min(-30_000_000).max(30_000_000).nullable());

const schema = z.object({
  id: z.string().trim().toLowerCase().regex(/^[a-z0-9-]{2,40}$/, "use letras minúsculas, números e hífen"),
  nome: z.string().trim().min(2).max(100),
  chefe: z.string().trim().max(60),
  estrutura: z.string().trim().min(3).max(300)
    .transform((e) => e.split("|").map((x) => x.trim().toLowerCase()).filter(Boolean).join("|"))
    .refine((e) => e.split("|").every((x) => /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(x)), "ids no formato mod:estrutura, separados por |"),
  x: inteiro,
  z: inteiro,
  entradaX: opcional,
  entradaY: opcional,
  entradaZ: opcional,
  ordem: z.coerce.number().int().min(0).max(9999),
  nivel: z.preprocess((v) => (v === "" || v === null || v === undefined ? 0 : v), z.coerce.number().int().min(0).max(1000)),
});

export type EstadoMasmorra = { erro: string } | { ok: true } | null;

export async function salvarMasmorra(_e: EstadoMasmorra, f: FormData): Promise<EstadoMasmorra> {
  await exigirAdmin();
  const original = String(f.get("original") ?? "");
  const dados = schema.safeParse(Object.fromEntries(
    ["id", "nome", "chefe", "estrutura", "x", "z", "entradaX", "entradaY", "entradaZ", "ordem", "nivel"].map((k) => [k, f.get(k) ?? ""]),
  ));
  if (!dados.success) {
    const p = dados.error.issues[0];
    return { erro: `${p.path.join(".")}: ${p.message}` };
  }
  const d = dados.data;
  const e = [d.entradaX, d.entradaY, d.entradaZ];
  if (e.some((v) => v !== null) && e.some((v) => v === null)) return { erro: "Entrada: preencha X, Y e Z ou deixe as três vazias." };

  if (original) {
    if (original !== d.id && (await prisma.masmorra.findUnique({ where: { id: d.id } }))) return { erro: "Já existe uma masmorra com esse id." };
    await prisma.masmorra.update({ where: { id: original }, data: d });
  } else {
    if (await prisma.masmorra.findUnique({ where: { id: d.id } })) return { erro: "Já existe uma masmorra com esse id." };
    await prisma.masmorra.create({ data: d });
  }
  revalidatePath("/admin/masmorras");
  return { ok: true };
}

export async function alternarMasmorra(f: FormData) {
  await exigirAdmin();
  const id = String(f.get("id"));
  const m = await prisma.masmorra.findUniqueOrThrow({ where: { id } });
  await prisma.masmorra.update({ where: { id }, data: { ativo: !m.ativo } });
  revalidatePath("/admin/masmorras");
}

export async function refazerMasmorra(f: FormData) {
  await exigirAdmin();
  const id = String(f.get("id"));
  const m = await prisma.masmorra.findUniqueOrThrow({ where: { id } });
  await prisma.masmorra.update({ where: { id }, data: { refazer: !m.refazer } });
  revalidatePath("/admin/masmorras");
}

export async function excluirMasmorra(f: FormData) {
  await exigirAdmin();
  await prisma.masmorra.delete({ where: { id: String(f.get("id")) } });
  revalidatePath("/admin/masmorras");
}
