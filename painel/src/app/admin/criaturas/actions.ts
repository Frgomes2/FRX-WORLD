"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { servidorMc } from "@/lib/servidor-mc";
import { GOLPES, LOCAIS, pngDaSkin } from "@/lib/criaturas-codex";

async function exigirAdmin() {
  if (!(await auth())?.user) throw new Error("Não autorizado");
}

const ID = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const criatura = z.object({
  nome: z.string().trim().min(2, "dê um nome").max(40),
  codigo: z.string().trim().toLowerCase().max(30),
  descricao: z.string().trim().max(240),
  tipo: z.enum(["chefe", "monstro"]),
  entidade: z.string().trim().toLowerCase().regex(ID, "escolha a criatura base"),
  rankMin: z.coerce.number().int().min(0).max(6),
  rankMax: z.coerce.number().int().min(0).max(6),
  vida: z.coerce.number().min(0.1).max(50),
  dano: z.coerce.number().min(0.1).max(20),
  velocidade: z.coerce.number().min(0.3).max(3),
  lacaio: z.string().trim().toLowerCase().max(80),
  peso: z.coerce.number().int().min(1).max(100),
  xp: z.coerce.number().int().min(0).max(1_000_000),
  moedas: z.coerce.number().int().min(0).max(100_000_000),
  dropItem: z.string().trim().toLowerCase().max(80),
  dropChance: z.coerce.number().min(0).max(100),
  dropQtd: z.coerce.number().int().min(1).max(64),
  escala: z.coerce.number().min(0.4).max(3),
});

export type Estado = { erro?: string; ok?: string } | null;

function slug(nome: string) {
  return nome.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 30).replace(/_+$/, "");
}

/** Pede ao servidor do jogo para reler o Códex agora (sem isso ele relê sozinho em até 1 minuto). */
function avisarServidor() {
  try { servidorMc.comando("frx criaturas recarregar"); } catch { /* servidor desligado: lê ao ligar */ }
}

export async function salvarCriatura(_e: Estado, f: FormData): Promise<Estado> {
  await exigirAdmin();
  const d = criatura.safeParse(Object.fromEntries(f.entries()));
  if (!d.success) return { erro: `${d.error.issues[0].path.join(".")}: ${d.error.issues[0].message}` };
  const v = d.data;
  if (v.rankMax < v.rankMin) return { erro: "O rank máximo não pode ser menor que o mínimo." };
  const golpes = GOLPES.map((g) => g.id).filter((g) => f.get(`golpe_${g}`) === "on");
  const locais = LOCAIS.map((l) => l.id).filter((l) => f.get(`local_${l}`) === "on");
  if (v.tipo === "chefe" && !golpes.some((g) => g !== "FURIA")) return { erro: "Um chefe precisa de pelo menos um golpe além da fúria." };
  if (golpes.includes("INVOCAR") && !ID.test(v.lacaio)) return { erro: "Escolha o lacaio que o chefe chama em “Chamar reforços”." };
  if (v.dropItem && !ID.test(v.dropItem)) return { erro: "Item da recompensa inválido. Use o seletor ou codex:código_da_arma." };
  if (v.dropItem.startsWith("codex:") && !(await prisma.armaCodex.findUnique({ where: { codigo: v.dropItem.slice(6) } })))
    return { erro: `Não existe a arma “${v.dropItem.slice(6)}” no Códex de Armas.` };
  const skin = String(f.get("skin") ?? "").replace(/^data:image\/png;base64,/, "");
  if (skin && !pngDaSkin(skin)) return { erro: "A skin precisa ser um PNG de 64×64 pixels (formato de skin de jogador)." };
  const id = String(f.get("id") ?? "");
  const { codigo: digitado, ...resto } = v;
  const valores = {
    ...resto, golpes: v.tipo === "chefe" ? golpes.join(",") : "", lacaio: golpes.includes("INVOCAR") ? v.lacaio : "", locais: locais.join(","),
    skin, slim: f.get("slim") === "on", brilho: f.get("brilho") === "on", ativa: f.get("ativa") === "on",
  };
  try {
    if (id) await prisma.criaturaCodex.update({ where: { id }, data: valores });   // o código não muda: a contagem e a skin apontam para ele
    else {
      const codigo = slug(digitado || v.nome);
      if (codigo.length < 2) return { erro: "Não consegui criar um código a partir do nome. Digite um código só com letras e números." };
      await prisma.criaturaCodex.create({ data: { ...valores, codigo } });
    }
  } catch {
    return { erro: "Já existe uma criatura com esse código." };
  }
  avisarServidor();
  revalidatePath("/admin/criaturas");
  return { ok: locais.length ? "Criatura salva. O servidor passa a usá-la em instantes." : "Criatura salva, mas sem lugar marcado: ela só aparece quando você a invocar." };
}

export async function excluirCriatura(f: FormData) {
  await exigirAdmin();
  const id = String(f.get("id") ?? "");
  if (id) await prisma.criaturaCodex.delete({ where: { id } }).catch(() => null);
  avisarServidor();
  revalidatePath("/admin/criaturas");
}

/** Faz a criatura aparecer ao lado de um jogador online, para ver como ficou. */
export async function invocarCriatura(_e: Estado, f: FormData): Promise<Estado> {
  await exigirAdmin();
  const codigo = String(f.get("codigo") ?? ""), nick = String(f.get("nick") ?? "").trim();
  if (!/^[a-z0-9_]{2,30}$/.test(codigo)) return { erro: "Criatura inválida." };
  if (!/^[A-Za-z0-9_]{2,16}$/.test(nick)) return { erro: "Digite o nick de quem está no jogo." };
  try { servidorMc.comando(`frx criaturas invocar ${codigo} ${nick}`); } catch { return { erro: "O servidor está desligado." }; }
  return { ok: `Pedido enviado: aparece a 4 blocos de ${nick}, se ele estiver online.` };
}
