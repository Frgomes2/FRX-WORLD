"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { z } from "zod";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { EXEMPLOS, lerItens } from "@/lib/missoes";
import { lerConfig, salvarConfig } from "@/lib/config-jogo";

async function exigirAdmin() {
  if (!(await auth())?.user) throw new Error("Não autorizado");
}

const schema = z.object({
  titulo: z.string().trim().min(3, "título muito curto").max(60),
  descricao: z.string().trim().max(300),
  tipo: z.enum(["MATAR", "COLETAR"]),
  alvo: z.string().trim().min(3).transform((a) => (a.includes(":") ? a : `minecraft:${a}`).toLowerCase()),
  quantidade: z.coerce.number().int().min(1).max(10000),
  nivelMin: z.coerce.number().int().min(1).max(1000),
  rankMin: z.coerce.number().int().min(0).max(6),
  recompensaXp: z.coerce.number().int().min(0).max(10_000_000),
  recompensaMoedas: z.coerce.number().int().min(0).max(1_000_000),
  repetivel: z.boolean(),
  intervaloHoras: z.coerce.number().int().min(0).max(24 * 30),
  preRequisitoId: z.string().nullable(),
  ativa: z.boolean(),
  ordem: z.coerce.number().int().min(0).max(9999),
});

export type EstadoMissao = { erro: string } | null;

export type DadosMissao = {
  titulo: string; descricao: string; tipo: string; alvo: string; quantidade: number; nivelMin: number; rankMin: number;
  recompensaXp: number; recompensaMoedas: number; recompensaItens: { item: string; qtd: number }[];
  repetivel: boolean; intervaloHoras: number; preRequisitoId: string | null; ativa: boolean; ordem: number;
};

/** Salva pelo editor novo (dados já estruturados). Devolve erro ou o id salvo. */
export async function salvarMissaoDados(id: string | null, d: DadosMissao): Promise<{ ok: boolean; erro?: string; id?: string }> {
  await exigirAdmin();
  const dados = schema.safeParse({ ...d, preRequisitoId: d.preRequisitoId || null });
  if (!dados.success) {
    const p = dados.error.issues[0];
    const nomes: Record<string, string> = { titulo: "Título", alvo: "Alvo", quantidade: "Quantidade", nivelMin: "Nível mínimo", recompensaXp: "XP", recompensaMoedas: "Moedas", intervaloHoras: "Intervalo" };
    return { ok: false, erro: `${nomes[String(p.path[0])] ?? p.path.join(".")}: ${p.message}` };
  }
  const itens = z.array(z.object({ item: z.string().min(3).max(120), qtd: z.number().int().min(1).max(6400) })).max(12).safeParse(d.recompensaItens);
  if (!itens.success) return { ok: false, erro: "Itens de recompensa inválidos (quantidade de 1 a 6400, até 12 itens)." };
  if (id && dados.data.preRequisitoId === id) return { ok: false, erro: "A missão não pode ser pré-requisito dela mesma." };
  const valores = { ...dados.data, recompensaItens: itens.data };
  const salva = id ? await prisma.missao.update({ where: { id }, data: valores }) : await prisma.missao.create({ data: valores });
  revalidatePath("/missoes");
  return { ok: true, id: salva.id };
}

export async function duplicarMissao(id: string) {
  await exigirAdmin();
  const m = await prisma.missao.findUniqueOrThrow({ where: { id } });
  const { id: _id, criadoEm: _c, atualizadoEm: _a, ...resto } = m;
  void _id; void _c; void _a;
  await prisma.missao.create({ data: { ...resto, titulo: `${m.titulo} (cópia)`.slice(0, 60), ativa: false, recompensaItens: m.recompensaItens ?? [] } });
  revalidatePath("/missoes");
}

export async function alternarMissaoId(id: string) {
  await exigirAdmin();
  const m = await prisma.missao.findUniqueOrThrow({ where: { id } });
  await prisma.missao.update({ where: { id }, data: { ativa: !m.ativa } });
  revalidatePath("/missoes");
}

export async function excluirMissaoId(id: string) {
  await exigirAdmin();
  await prisma.missao.updateMany({ where: { preRequisitoId: id }, data: { preRequisitoId: null } });
  await prisma.missao.delete({ where: { id } });
  revalidatePath("/missoes");
}

export async function salvarMissao(_e: EstadoMissao, f: FormData): Promise<EstadoMissao> {
  await exigirAdmin();
  const id = String(f.get("id") ?? "");
  const dados = schema.safeParse({
    titulo: f.get("titulo"),
    descricao: f.get("descricao") ?? "",
    tipo: f.get("tipo"),
    alvo: f.get("alvo"),
    quantidade: f.get("quantidade"),
    nivelMin: f.get("nivelMin"),
    rankMin: f.get("rankMin") || 0,
    recompensaXp: f.get("recompensaXp"),
    recompensaMoedas: f.get("recompensaMoedas") || 0,
    repetivel: f.get("repetivel") === "on",
    intervaloHoras: f.get("intervaloHoras") || 0,
    preRequisitoId: String(f.get("preRequisitoId") ?? "") || null,
    ativa: f.get("ativa") === "on",
    ordem: f.get("ordem") || 0,
  });
  if (!dados.success) {
    const p = dados.error.issues[0];
    return { erro: `${p.path.join(".")}: ${p.message}` };
  }
  let recompensaItens;
  try {
    recompensaItens = lerItens(String(f.get("recompensaItens") ?? ""));
  } catch {
    return { erro: "Itens de recompensa inválidos. Use o formato minecraft:bread*5, minecraft:emerald*2" };
  }
  if (dados.data.preRequisitoId && dados.data.preRequisitoId === id) return { erro: "A missão não pode ser pré-requisito dela mesma." };

  const valores = { ...dados.data, recompensaItens };
  if (id && id !== "nova") await prisma.missao.update({ where: { id }, data: valores });
  else await prisma.missao.create({ data: valores });

  revalidatePath("/missoes");
  redirect("/missoes");
}

export async function alternarMissao(f: FormData) {
  await exigirAdmin();
  const id = String(f.get("id"));
  const m = await prisma.missao.findUniqueOrThrow({ where: { id } });
  await prisma.missao.update({ where: { id }, data: { ativa: !m.ativa } });
  revalidatePath("/missoes");
}

export async function excluirMissao(f: FormData) {
  await exigirAdmin();
  const id = String(f.get("id"));
  await prisma.missao.updateMany({ where: { preRequisitoId: id }, data: { preRequisitoId: null } });
  await prisma.missao.delete({ where: { id } });
  revalidatePath("/missoes");
}

export async function criarExemplos() {
  await exigirAdmin();
  const criadas = new Map<string, string>();
  for (const { preRequisitoTitulo, ...m } of EXEMPLOS as Array<(typeof EXEMPLOS)[number] & { preRequisitoTitulo?: string }>) {
    const nova = await prisma.missao.create({
      data: { ...m, preRequisitoId: preRequisitoTitulo ? criadas.get(preRequisitoTitulo) ?? null : null },
    });
    criadas.set(m.titulo, nova.id);
  }
  revalidatePath("/missoes");
}

export async function salvarUrgentes(_e: { ok: boolean; mensagem: string } | null, f: FormData) {
  await exigirAdmin();
  const n = (chave: string, min: number, max: number, padrao: number) => {
    const v = Number(f.get(chave));
    return Number.isFinite(v) ? Math.max(min, Math.min(max, v)) : padrao;
  };
  try {
    await salvarConfig({
      ...(await lerConfig()),
      urgentes: { ativo: f.get("ativo") === "on", intervaloMin: Math.round(n("intervaloMin", 5, 1440, 45)), duracaoMin: Math.round(n("duracaoMin", 3, 240, 20)), multiplicador: n("multiplicador", 1, 10, 2) },
    });
  } catch {
    return { ok: false, mensagem: "Valores inválidos." };
  }
  revalidatePath("/missoes");
  return { ok: true, mensagem: "Salvo. Vale no servidor em até 1 minuto." };
}

export async function salvarTaverneiro(_e: { ok: boolean; mensagem: string } | null, f: FormData) {
  await exigirAdmin();
  const nome = String(f.get("nome") ?? "").trim();
  const falas = String(f.get("falas") ?? "").split("\n").map((l) => l.trim()).filter(Boolean);
  if (!nome) return { ok: false, mensagem: "Dê um nome ao taverneiro." };
  if (falas.length === 0) return { ok: false, mensagem: "Escreva pelo menos uma fala." };
  try {
    await salvarConfig({ ...(await lerConfig()), taverna: { nome: nome.slice(0, 32), falas: falas.slice(0, 12).map((l) => l.slice(0, 160)) } });
  } catch {
    return { ok: false, mensagem: "Falas muito longas ou inválidas." };
  }
  revalidatePath("/missoes");
  return { ok: true, mensagem: "Salvo. Vale no servidor em até 1 minuto." };
}
