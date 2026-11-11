import { z } from "zod";
import { prisma } from "@/lib/prisma";
import type { Prisma } from "@/generated/prisma/client";

export const TIPOS_EVENTO = {
  EMBOSCADA: "Emboscada (grupo de mobs ao redor)",
  CHEFE: "Chefe errante (um mob forte com barra)",
  MERCADOR: "Mercador raro (vende o loot por esmeraldas)",
  TESOURO: "Tesouro (baú perto do jogador)",
} as const;
export type TipoEvento = keyof typeof TIPOS_EVENTO;

export const configEventos = z.object({
  ativo: z.boolean().default(true),
  checagemSegundos: z.number().int().min(20).max(3600).default(90),
  chance: z.number().min(0).max(100).default(25),
  recargaMinutos: z.number().int().min(0).max(1440).default(8),
  distanciaSpawn: z.number().int().min(0).max(100000).default(150),
  andarMinimo: z.number().int().min(0).max(5000).default(48),
});
export type ConfigEventos = z.infer<typeof configEventos>;
export const CONFIG_EVENTOS_PADRAO: ConfigEventos = configEventos.parse({});

const CHAVE = "eventos";

export async function lerConfigEventos(): Promise<ConfigEventos> {
  const linha = await prisma.configuracao.findUnique({ where: { chave: CHAVE } });
  const r = configEventos.safeParse(linha?.valor ?? {});
  return r.success ? r.data : CONFIG_EVENTOS_PADRAO;
}

export async function salvarConfigEventos(c: ConfigEventos) {
  const valor = configEventos.parse(c) as unknown as Prisma.InputJsonValue;
  await prisma.configuracao.upsert({ where: { chave: CHAVE }, update: { valor }, create: { chave: CHAVE, valor } });
}

type Loot = { item: string; qtd: number; chance: number };
const l = (item: string, qtd: number, chance = 100): Loot => ({ item: item.includes(":") ? item : `minecraft:${item}`, qtd, chance });

export const SUGESTOES_EVENTOS = [
  { nome: "Horda Errante", tipo: "EMBOSCADA", mensagem: "Gemidos vêm da estrada... uma horda te cercou!", peso: 12, nivelMin: 1, nivelMax: 20, mobTipo: "minecraft:zombie", mobQtd: 5, vida: 0, dano: 0, preco: 0, recompensaXp: 120, loot: [l("iron_ingot", 3), l("bread", 4)] },
  { nome: "Teia na Trilha", tipo: "EMBOSCADA", mensagem: "Você pisou numa teia. As donas vieram ver.", peso: 10, nivelMin: 3, nivelMax: 30, mobTipo: "minecraft:spider", mobQtd: 4, vida: 0, dano: 0, preco: 0, recompensaXp: 180, loot: [l("string", 8), l("emerald", 2, 50)] },
  { nome: "Bandidos da Estrada", tipo: "EMBOSCADA", mensagem: "\"A bolsa ou a vida!\" — bandidos saem do mato.", peso: 10, nivelMin: 8, nivelMax: 45, mobTipo: "minecraft:pillager", mobQtd: 4, vida: 0, dano: 0, preco: 0, recompensaXp: 350, loot: [l("emerald", 5), l("crossbow", 1, 30)] },
  { nome: "Patrulha dos Ossos", tipo: "EMBOSCADA", mensagem: "Flechas cortam o ar. Esqueletos marcham em formação.", peso: 9, nivelMin: 10, nivelMax: 50, mobTipo: "minecraft:skeleton", mobQtd: 6, vida: 30, dano: 0, preco: 0, recompensaXp: 450, loot: [l("bone", 10), l("arrow", 16), l("diamond", 1, 20)] },
  { nome: "Esquadrão Illager", tipo: "EMBOSCADA", mensagem: "Machados brilham entre as árvores. É um esquadrão de caça!", peso: 7, nivelMin: 20, nivelMax: 70, mobTipo: "minecraft:vindicator", mobQtd: 4, vida: 40, dano: 0, preco: 0, recompensaXp: 900, loot: [l("emerald", 10), l("diamond", 2, 40)] },
  { nome: "Cavaleiro Sem Nome", tipo: "CHEFE", mensagem: "Um cavaleiro de armadura negra bloqueia o caminho.", peso: 5, nivelMin: 15, nivelMax: 60, mobTipo: "minecraft:wither_skeleton", mobQtd: 2, vida: 160, dano: 10, preco: 0, recompensaXp: 1500, loot: [l("diamond", 2, 70), l("wither_skeleton_skull", 1, 15)] },
  { nome: "Besta da Fenda", tipo: "CHEFE", mensagem: "O chão se abre. Algo enorme sai da fenda!", peso: 3, nivelMin: 30, nivelMax: 1000, mobTipo: "minecraft:ravager", mobQtd: 3, vida: 300, dano: 14, preco: 0, recompensaXp: 3500, loot: [l("diamond", 4), l("netherite_scrap", 1, 40)] },
  { nome: "Mercador das Sombras", tipo: "MERCADOR", mensagem: "Um mercador encapuzado acena: \"Coisas raras, viajante...\"", peso: 6, nivelMin: 1, nivelMax: 1000, mobTipo: "minecraft:wandering_trader", mobQtd: 0, vida: 0, dano: 0, preco: 12, recompensaXp: 0, loot: [l("diamond", 1), l("golden_apple", 2), l("ender_pearl", 4), l("experience_bottle", 8), l("name_tag", 1)] },
  { nome: "Mapa Rasgado", tipo: "TESOURO", mensagem: "Você acha um pedaço de mapa. Um raio marca o lugar!", peso: 8, nivelMin: 1, nivelMax: 1000, mobTipo: "", mobQtd: 0, vida: 0, dano: 0, preco: 0, recompensaXp: 100, loot: [l("gold_ingot", 6), l("iron_ingot", 10), l("diamond", 1, 40), l("enchanted_book", 1, 25)] },
  { nome: "Carniçais do Cemitério", tipo: "EMBOSCADA", mensagem: "A terra se mexe. Mãos podres saem do chão!", peso: 9, nivelMin: 10, nivelMax: 45, mobTipo: "graveyard:ghoul", mobQtd: 4, vida: 0, dano: 0, preco: 0, recompensaXp: 400, loot: [l("bone", 12), l("emerald", 3)] },
  { nome: "Batedores Saqueadores", tipo: "EMBOSCADA", mensagem: "Saqueadores com machados enormes cortam o caminho.", peso: 8, nivelMin: 15, nivelMax: 55, mobTipo: "illagerinvasion:marauder", mobQtd: 3, vida: 0, dano: 0, preco: 0, recompensaXp: 700, loot: [l("emerald", 8), l("diamond", 1, 40)] },
  { nome: "Espectros da Névoa", tipo: "EMBOSCADA", mensagem: "Sombras flutuam na névoa e sussurram seu nome.", peso: 6, nivelMin: 25, nivelMax: 1000, mobTipo: "graveyard:wraith", mobQtd: 3, vida: 0, dano: 0, preco: 0, recompensaXp: 1100, loot: [l("diamond", 2), l("ender_pearl", 4)] },
  { nome: "O Inquisidor", tipo: "CHEFE", mensagem: "\"Herege! Ajoelhe-se diante da Inquisição!\"", peso: 4, nivelMin: 25, nivelMax: 1000, mobTipo: "illagerinvasion:inquisitor", mobQtd: 2, vida: 220, dano: 12, preco: 0, recompensaXp: 2500, loot: [l("diamond", 3), l("totem_of_undying", 1, 20)] },
  { nome: "Cavaleiro do Caos", tipo: "CHEFE", mensagem: "Um cavaleiro caído ergue a espada. O caos te escolheu.", peso: 4, nivelMin: 20, nivelMax: 1000, mobTipo: "born_in_chaos_v1:fallen_chaos_knight", mobQtd: 0, vida: 200, dano: 0, preco: 0, recompensaXp: 2000, loot: [l("diamond", 3), l("iron_block", 1)] },

];
