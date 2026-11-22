"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { servidorMc } from "@/lib/servidor-mc";
import { pngDaTextura } from "@/lib/codex";

async function exigirAdmin() {
  if (!(await auth())?.user) throw new Error("Não autorizado");
}

const arma = z.object({
  nome: z.string().trim().min(2, "dê um nome").max(40),
  codigo: z.string().trim().toLowerCase().max(40),
  descricao: z.string().trim().max(240),
  itemBase: z.string().trim().toLowerCase().regex(/^[a-z0-9_.-]+:[a-z0-9_./-]+$/, "escolha o item base"),
  rank: z.coerce.number().int().min(0).max(6),
  raridade: z.coerce.number().int().min(0).max(4),
  dano: z.coerce.number().min(0).max(1000),
  limite: z.coerce.number().int().min(0).max(100000),
  preco: z.coerce.number().int().min(0).max(100_000_000),
  nivelMin: z.coerce.number().int().min(1).max(1000),
  danoPorNivel: z.coerce.number().min(0).max(50),
  encantos: z.string().trim().max(400),
});

export type Estado = { erro?: string; ok?: string } | null;

function slug(nome: string) {
  return nome.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 40);
}

/** Pede ao servidor do jogo para reler o Códex agora (sem isso ele relê sozinho em até 1 minuto). */
function avisarServidor() {
  try { servidorMc.comando("frx codex recarregar"); } catch { /* servidor desligado: lê ao ligar */ }
}

export async function salvarArma(_e: Estado, f: FormData): Promise<Estado> {
  await exigirAdmin();
  const d = arma.safeParse(Object.fromEntries(f.entries()));
  if (!d.success) return { erro: `${d.error.issues[0].path.join(".")}: ${d.error.issues[0].message}` };
  const id = String(f.get("id") ?? "");
  const textura = String(f.get("textura") ?? "").replace(/^data:image\/png;base64,/, "");
  if (textura && !pngDaTextura(textura)) return { erro: "A aparência precisa ser um PNG quadrado de 16×16 ou 32×32." };
  for (const e of d.data.encantos.split(",").map((x) => x.trim()).filter(Boolean))
    if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+(\*\d{1,2})?$/.test(e)) return { erro: `Encantamento inválido: "${e}". Use minecraft:sharpness*3.` };
  const evolui = f.get("evolui") === "on";
  if (evolui && d.data.danoPorNivel <= 0) return { erro: "Arma que evolui precisa de um dano por nível maior que zero." };
  if (evolui && f.get("nivelUso") === "on") return { erro: "Escolha só um jeito de evoluir: pelo nível do jogador ou pelo uso." };
  const ferreiro = f.get("ferreiro") === "on";
  if (ferreiro && d.data.preco <= 0) return { erro: "Para vender no ferreiro, defina um preço." };
  const { codigo: digitado, ...resto } = d.data;
  const valores = { ...resto, textura, evolui, ferreiro, umaPorJogador: f.get("umaPorJogador") === "on", inquebravel: f.get("inquebravel") === "on", nivelUso: f.get("nivelUso") === "on", ativa: f.get("ativa") === "on" };
  try {
    if (id) await prisma.armaCodex.update({ where: { id }, data: valores as typeof valores & Record<string, unknown> as never });   // o código não muda: as armas já entregues apontam para ele
    else {
      const codigo = slug(digitado || d.data.nome);
      if (codigo.length < 2) return { erro: "Não consegui criar um código a partir do nome. Digite um código só com letras e números." };
      await prisma.armaCodex.create({ data: { ...valores, codigo } as never });
    }
  } catch {
    return { erro: "Já existe uma arma com esse código." };
  }
  avisarServidor();
  revalidatePath("/admin/codex");
  return { ok: "Arma salva. O servidor passa a usá-la em instantes." };
}

export async function excluirArma(f: FormData) {
  await exigirAdmin();
  const id = String(f.get("id") ?? "");
  if (id) await prisma.armaCodex.delete({ where: { id } }).catch(() => null);
  avisarServidor();
  revalidatePath("/admin/codex");
}

/** Zera a contagem do mundo (as armas já entregues continuam com os jogadores). */
export async function zerarContagem(f: FormData) {
  await exigirAdmin();
  const codigo = String(f.get("codigo") ?? "");
  if (!/^[a-z0-9_]{2,40}$/.test(codigo)) return;
  try { servidorMc.comando(`frx codex estoque ${codigo} 0`); } catch { return; }
  await prisma.armaCodex.updateMany({ where: { codigo }, data: { emitidas: 0, donos: {} } });
  revalidatePath("/admin/codex");
}
