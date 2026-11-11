"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { z } from "zod";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { EXEMPLOS_DUNGEON, guardiaoAndar, lerLoot } from "@/lib/dungeons";

async function exigirAdmin() {
  if (!(await auth())?.user) throw new Error("Não autorizado");
}

const id = z.string().trim().min(3).transform((a) => (a.includes(":") ? a : `minecraft:${a}`).toLowerCase());

const schema = z.object({
  nome: z.string().trim().min(3).max(60),
  descricao: z.string().trim().max(300),
  distanciaMin: z.coerce.number().int().min(0),
  distanciaMax: z.coerce.number().int().min(1),
  nivelRecomendado: z.coerce.number().int().min(1).max(1000),
  bossTipo: id,
  bossNome: z.string().trim().min(1).max(40),
  bossVida: z.coerce.number().int().min(1).max(100000),
  bossDano: z.coerce.number().min(0).max(1000),
  bossVelocidade: z.coerce.number().min(0.2).max(3),
  lacaioTipo: id,
  lacaioQtd: z.coerce.number().int().min(0).max(30),
  recompensaXp: z.coerce.number().int().min(0).max(10_000_000),
  recargaHoras: z.coerce.number().int().min(0).max(24 * 30),
  ativa: z.boolean(),
});

export type EstadoDungeon = { erro: string } | null;

export async function salvarDungeon(_e: EstadoDungeon, f: FormData): Promise<EstadoDungeon> {
  await exigirAdmin();
  const idDungeon = String(f.get("id") ?? "");
  const campos = Object.fromEntries([...f.entries()].filter(([k]) => !k.startsWith("$")));
  const dados = schema.safeParse({ ...campos, descricao: campos.descricao ?? "", ativa: f.get("ativa") === "on" });
  if (!dados.success) {
    const p = dados.error.issues[0];
    return { erro: `${p.path.join(".")}: ${p.message}` };
  }
  if (dados.data.distanciaMax <= dados.data.distanciaMin) return { erro: "A distância máxima precisa ser maior que a mínima." };
  let loot;
  try {
    loot = lerLoot(String(f.get("loot") ?? ""));
  } catch {
    return { erro: "Loot inválido. Use minecraft:diamond*2@50 (item*quantidade@chance%)." };
  }
  const andares = [];
  for (const n of [1, 2]) {
    const tipoA = String(f.get(`andar${n}.bossTipo`) ?? "").trim();
    const g = guardiaoAndar.safeParse({
      bossTipo: tipoA.includes(":") ? tipoA : `minecraft:${tipoA}`,
      bossNome: String(f.get(`andar${n}.bossNome`) ?? "").trim(),
      bossVida: Number(f.get(`andar${n}.bossVida`)), bossDano: Number(f.get(`andar${n}.bossDano`)),
      bossVelocidade: Number(f.get(`andar${n}.bossVelocidade`) || 1),
      lacaioTipo: (() => { const l = String(f.get(`andar${n}.lacaioTipo`) ?? "").trim(); return l.includes(":") ? l : `minecraft:${l}`; })(),
      lacaioQtd: Number(f.get(`andar${n}.lacaioQtd`) || 0),
    });
    if (!g.success) return { erro: `Guardião da camada ${n}: ${g.error.issues[0].path.join(".")} inválido.` };
    andares.push(g.data);
  }
  const valores = { ...dados.data, loot, andares };
  if (idDungeon && idDungeon !== "nova") await prisma.dungeon.update({ where: { id: idDungeon }, data: valores });
  else await prisma.dungeon.create({ data: valores });
  revalidatePath("/dungeons");
  redirect("/dungeons");
}

export async function alternarDungeon(f: FormData) {
  await exigirAdmin();
  const d = await prisma.dungeon.findUniqueOrThrow({ where: { id: String(f.get("id")) } });
  await prisma.dungeon.update({ where: { id: d.id }, data: { ativa: !d.ativa } });
  revalidatePath("/dungeons");
}

export async function excluirDungeon(f: FormData) {
  await exigirAdmin();
  await prisma.dungeon.delete({ where: { id: String(f.get("id")) } });
  revalidatePath("/dungeons");
}

export async function criarExemplosDungeon() {
  await exigirAdmin();
  for (const d of EXEMPLOS_DUNGEON) await prisma.dungeon.create({ data: d });
  revalidatePath("/dungeons");
}

// ------------------------------------------------------------------ editor visual (JSON)

const guardiaoJson = z.object({
  bossTipo: id, bossNome: z.string().trim().min(1).max(40), bossVida: z.number().int().min(1).max(100000),
  bossDano: z.number().min(0).max(1000), bossVelocidade: z.number().min(0.2).max(3), lacaioTipo: id, lacaioQtd: z.number().int().min(0).max(30),
});

const dadosJson = schema.extend({
  distanciaMin: z.number().int().min(0), distanciaMax: z.number().int().min(1), nivelRecomendado: z.number().int().min(1).max(1000),
  bossVida: z.number().int().min(1).max(100000), bossDano: z.number().min(0).max(1000), bossVelocidade: z.number().min(0.2).max(3),
  lacaioQtd: z.number().int().min(0).max(30), recompensaXp: z.number().int().min(0).max(10_000_000), recargaHoras: z.number().int().min(0).max(24 * 30),
  andares: z.array(guardiaoJson).length(2),
  loot: z.array(z.object({ item: z.string().min(3).max(120), qtd: z.number().int().min(1).max(6400), chance: z.number().min(1).max(100) })).max(30),
});

export type DadosDungeon = z.input<typeof dadosJson>;

const nomesCampo: Record<string, string> = {
  nome: "Nome", bossNome: "Nome do guardião final", bossTipo: "Criatura do guardião", lacaioTipo: "Criatura dos lacaios",
  distanciaMax: "Distância máxima", andares: "Camadas", loot: "Prêmios",
};

export async function salvarDungeonDados(idDungeon: string | null, d: DadosDungeon): Promise<{ ok: boolean; erro?: string }> {
  await exigirAdmin();
  const r = dadosJson.safeParse(d);
  if (!r.success) {
    const p = r.error.issues[0];
    const campo = p.path[0] === "andares" ? `Camada ${Number(p.path[1]) + 1} (${String(p.path[2] ?? "")})` : nomesCampo[String(p.path[0])] ?? p.path.join(".");
    return { ok: false, erro: `${campo}: ${p.message}` };
  }
  if (r.data.distanciaMax <= r.data.distanciaMin) return { ok: false, erro: "A distância máxima precisa ser maior que a mínima." };
  if (idDungeon) await prisma.dungeon.update({ where: { id: idDungeon }, data: r.data });
  else await prisma.dungeon.create({ data: r.data });
  revalidatePath("/dungeons");
  return { ok: true };
}

export async function alternarDungeonId(idDungeon: string) {
  await exigirAdmin();
  const d = await prisma.dungeon.findUniqueOrThrow({ where: { id: idDungeon } });
  await prisma.dungeon.update({ where: { id: d.id }, data: { ativa: !d.ativa } });
  revalidatePath("/dungeons");
}

export async function excluirDungeonId(idDungeon: string) {
  await exigirAdmin();
  await prisma.dungeon.delete({ where: { id: idDungeon } });
  revalidatePath("/dungeons");
}

export async function duplicarDungeon(idDungeon: string) {
  await exigirAdmin();
  const d = await prisma.dungeon.findUniqueOrThrow({ where: { id: idDungeon } });
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { id: _i, criadoEm: _c, atualizadoEm: _a, ...resto } = d;
  await prisma.dungeon.create({ data: { ...resto, nome: `${d.nome} (cópia)`.slice(0, 60), ativa: false, loot: d.loot ?? [], andares: d.andares ?? [] } });
  revalidatePath("/dungeons");
}
