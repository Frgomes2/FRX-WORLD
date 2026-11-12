import { z } from "zod";
import { prisma } from "@/lib/prisma";
import { DIMENSOES_PADRAO } from "@/lib/dimensoes";
import type { Prisma } from "@/generated/prisma/client";

/** Configuração de jogo editada no painel e lida pelo servidor Minecraft. */

export const RANKS = ["F", "E", "D", "C", "B", "A", "S"] as const;

const faixaMorte = z.object({
  ate: z.number().int().min(1),             // vale até este nível (inclusive)
  xpPct: z.number().min(0).max(100),        // % da XP do nível perdida
  podePerderNivel: z.boolean(),
  durabilidadePct: z.number().min(0).max(100), // % de durabilidade perdida no equipamento
  feridoMin: z.number().min(0).max(120),    // minutos do efeito Ferido
});

export const FORJA_PADRAO = { anunciarAPartirDe: 7, niveis: [{ moedas: 15, material: "minecraft:iron_ingot", qtd: 2, chance: 100 }, { moedas: 60, material: "minecraft:iron_ingot", qtd: 4, chance: 100 }, { moedas: 135, material: "minecraft:iron_ingot", qtd: 6, chance: 100 }, { moedas: 240, material: "minecraft:diamond", qtd: 1, chance: 90 }, { moedas: 375, material: "minecraft:diamond", qtd: 2, chance: 80 }, { moedas: 540, material: "minecraft:diamond", qtd: 3, chance: 70 }, { moedas: 735, material: "minecraft:diamond", qtd: 4, chance: 55 }, { moedas: 960, material: "minecraft:diamond", qtd: 6, chance: 45 }, { moedas: 1215, material: "minecraft:diamond", qtd: 8, chance: 35 }, { moedas: 1500, material: "minecraft:netherite_scrap", qtd: 2, chance: 25 }], reforja: { moedasBase: 50, material: "minecraft:amethyst_shard", qtdBase: 2, qtdPorRaridade: 2 } };

export const FLORESTA_PADRAO = { ativo: true, reino: "tempest", inicio: 40, largura: 220, maxMonstros: 10, intervaloSeg: 5, chanceForte: 35, escuridao: true, soAPe: true, monstros1: ["minecraft:zombie", "minecraft:spider", "minecraft:cave_spider", "graveyard:ghoul", "born_in_chaos_v1:decaying_zombie", "born_in_chaos_v1:decrepit_skeleton", "born_in_chaos_v1:restless_spirit", "mowziesmobs:foliaath"], monstros2: ["graveyard:revenant", "graveyard:wraith", "born_in_chaos_v1:dread_hound", "born_in_chaos_v1:nightmare_stalker", "born_in_chaos_v1:skeleton_thrasher", "minecraft:witch", "graveyard:nightmare"] };

export const configSchema = z.object({
  progressao: z.object({
    nivelMaximo: z.number().int().min(10).max(1000),
    xpBase: z.number().min(1).max(100000),
    xpExpoente: z.number().min(1).max(4),
    xpMultiplicador: z.number().min(0).max(100),
  }),
  ranks: z.array(z.object({ rank: z.enum(RANKS), nivelMin: z.number().int().min(1) })).length(7),
  morte: z.array(faixaMorte).min(1).max(10),
  ferido: z.object({
    vidaPct: z.number().min(0).max(90),
    danoPct: z.number().min(0).max(90),
  }),
  zonas: z
    .object({
      blocosPorNivel: z.number().int().min(50).max(5000),   // a cada X blocos do spawn os monstros sobem 1 nível
      nivelMax: z.number().int().min(1).max(200),
      vidaPct: z.number().min(0).max(100),                  // % de vida extra por nível
      danoPct: z.number().min(0).max(100),                  // % de dano extra por nível
      armaduraPorNivel: z.number().min(0).max(5),
      moedaChance: z.number().min(0).max(100),              // % de chance de derrubar moedas
      moedaPorNivel: z.number().min(0).max(100),            // cobre médio por nível da zona
      eliteChance: z.number().min(0).max(50).default(5),       // % de um monstro nascer Elite
      eliteNivelMin: z.number().int().min(1).max(200).default(3),
      campeaoChance: z.number().min(0).max(10).default(0.5),   // % de nascer Campeão
      campeaoNivelMin: z.number().int().min(1).max(200).default(10),
      dimensoes: z.record(z.string().regex(/^[a-z0-9_.-]+:[a-z0-9_./-]+$/), z.tuple([z.number().int().min(1).max(200), z.number().int().min(1).max(200)])).default(DIMENSOES_PADRAO),
    })
    .default({ blocosPorNivel: 250, nivelMax: 60, vidaPct: 10, danoPct: 6, armaduraPorNivel: 0.25, moedaChance: 30, moedaPorNivel: 1,
      eliteChance: 5, eliteNivelMin: 3, campeaoChance: 0.5, campeaoNivelMin: 10, dimensoes: DIMENSOES_PADRAO }),
  invasoes: z
    .object({
      ativo: z.boolean(),
      intervaloMinutos: z.number().int().min(5).max(1440),   // de quanto em quanto tempo sorteia
      chance: z.number().min(0).max(100),                    // % de chance a cada sorteio (precisa de jogador numa capital)
      ondas: z.number().int().min(1).max(10),
      monstrosBase: z.number().int().min(1).max(40),
      monstrosPorJogador: z.number().int().min(0).max(20),
      moedasPorJogador: z.number().int().min(0).max(100000),
      moedasPorAbate: z.number().int().min(0).max(10000),
      repVitoria: z.number().int().min(0).max(10000),
      repPorAbate: z.number().int().min(0).max(1000),
      monstros1: z.array(z.string().min(3).max(80)).min(1).max(30),
      monstros2: z.array(z.string().min(3).max(80)).min(1).max(30),
      monstros3: z.array(z.string().min(3).max(80)).min(1).max(30),
      chefe: z.string().min(3).max(80),
    })
    .default({ ativo: true, intervaloMinutos: 30, chance: 35, ondas: 3, monstrosBase: 6, monstrosPorJogador: 3, moedasPorJogador: 150, moedasPorAbate: 5, repVitoria: 100, repPorAbate: 5, monstros1: ["minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:husk"], monstros2: ["minecraft:zombie", "minecraft:skeleton", "minecraft:vindicator", "minecraft:pillager", "minecraft:witch"], monstros3: ["minecraft:vindicator", "minecraft:pillager", "minecraft:evoker", "minecraft:wither_skeleton"], chefe: "minecraft:ravager" }),
  chefeMundial: z
    .object({
      ativo: z.boolean(),
      horarios: z.array(z.string().regex(/^(SEG|TER|QUA|QUI|SEX|SAB|DOM|TODO) \d{1,2}:\d{2}$/, "use DIA HH:MM, ex.: SAB 20:00")).max(20),
      chefes: z.array(z.string().min(3).max(80)).min(1).max(30),
      vidaMult: z.number().min(1).max(50),
      danoMult: z.number().min(1).max(10),
      moedas: z.number().int().min(0).max(1_000_000),   // pote em cobre repartido pelo dano
      avisoMinutos: z.number().int().min(1).max(60),
      duracaoMinutos: z.number().int().min(5).max(240),
      distanciaMin: z.number().int().min(100).max(20000),
      distanciaMax: z.number().int().min(200).max(30000),
    })
    .default({ ativo: true, horarios: ["SAB 20:00"], chefes: ["mowziesmobs:frostmaw", "mowziesmobs:ferrous_wroughtnaut", "cataclysm:netherite_monstrosity", "cataclysm:ignis", "minecraft:wither"], vidaMult: 4, danoMult: 1.5, moedas: 3000, avisoMinutos: 10, duracaoMinutos: 60, distanciaMin: 400, distanciaMax: 1500 }),
  forja: z
    .object({
      anunciarAPartirDe: z.number().int().min(1).max(11),   // anuncia no chat a partir deste +N
      niveis: z
        .array(z.object({
          moedas: z.number().int().min(0).max(10_000_000),   // cobre
          material: z.string().min(3).max(80),
          qtd: z.number().int().min(0).max(64),
          chance: z.number().int().min(0).max(100),
        }))
        .length(10),
      reforja: z.object({
        moedasBase: z.number().int().min(0).max(1_000_000),  // × (raridade+1)²
        material: z.string().min(3).max(80),
        qtdBase: z.number().int().min(0).max(64),
        qtdPorRaridade: z.number().int().min(0).max(16),
      }),
    })
    .default(FORJA_PADRAO),
  floresta: z
    .object({
      ativo: z.boolean(),
      reino: z.string().min(2).max(40),
      inicio: z.number().int().min(10).max(500),       // blocos depois da borda da capital
      largura: z.number().int().min(30).max(500),
      maxMonstros: z.number().int().min(0).max(40),    // por jogador, num raio de 40 blocos
      intervaloSeg: z.number().int().min(2).max(60),
      chanceForte: z.number().int().min(0).max(100),   // % de vir da lista forte (do primeiro terço para dentro)
      escuridao: z.boolean(),
      soAPe: z.boolean(),
      monstros1: z.array(z.string().min(3).max(80)).min(1).max(30),
      monstros2: z.array(z.string().min(3).max(80)).min(1).max(30),
    })
    .default(FLORESTA_PADRAO),
  taverna: z
    .object({
      nome: z.string().trim().min(1).max(32),
      falas: z.array(z.string().trim().min(1).max(160)).min(1).max(12),
    })
    .default({ nome: "Taverneiro", falas: ["Bem-vindo, {jogador}! Dê uma olhada no quadro de missões."] }),
});

export type ConfigJogo = z.infer<typeof configSchema>;

export const CONFIG_PADRAO: ConfigJogo = {
  progressao: { nivelMaximo: 100, xpBase: 100, xpExpoente: 1.5, xpMultiplicador: 1 },
  ranks: [
    { rank: "F", nivelMin: 1 },
    { rank: "E", nivelMin: 10 },
    { rank: "D", nivelMin: 20 },
    { rank: "C", nivelMin: 35 },
    { rank: "B", nivelMin: 50 },
    { rank: "A", nivelMin: 70 },
    { rank: "S", nivelMin: 90 },
  ],
  morte: [
    { ate: 10, xpPct: 0, podePerderNivel: false, durabilidadePct: 0, feridoMin: 0 },
    { ate: 30, xpPct: 5, podePerderNivel: false, durabilidadePct: 0, feridoMin: 2 },
    { ate: 60, xpPct: 10, podePerderNivel: false, durabilidadePct: 10, feridoMin: 5 },
    { ate: 90, xpPct: 15, podePerderNivel: true, durabilidadePct: 15, feridoMin: 10 },
    { ate: 1000, xpPct: 20, podePerderNivel: true, durabilidadePct: 20, feridoMin: 15 },
  ],
  ferido: { vidaPct: 20, danoPct: 20 },
  invasoes: { ativo: true, intervaloMinutos: 30, chance: 35, ondas: 3, monstrosBase: 6, monstrosPorJogador: 3, moedasPorJogador: 150, moedasPorAbate: 5, repVitoria: 100, repPorAbate: 5, monstros1: ["minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:husk"], monstros2: ["minecraft:zombie", "minecraft:skeleton", "minecraft:vindicator", "minecraft:pillager", "minecraft:witch"], monstros3: ["minecraft:vindicator", "minecraft:pillager", "minecraft:evoker", "minecraft:wither_skeleton"], chefe: "minecraft:ravager" },
  chefeMundial: { ativo: true, horarios: ["SAB 20:00"], chefes: ["mowziesmobs:frostmaw", "mowziesmobs:ferrous_wroughtnaut", "cataclysm:netherite_monstrosity", "cataclysm:ignis", "minecraft:wither"], vidaMult: 4, danoMult: 1.5, moedas: 3000, avisoMinutos: 10, duracaoMinutos: 60, distanciaMin: 400, distanciaMax: 1500 },
  forja: FORJA_PADRAO,
  floresta: FLORESTA_PADRAO,
  zonas: { blocosPorNivel: 250, nivelMax: 60, vidaPct: 6, danoPct: 3.5, armaduraPorNivel: 0.2, moedaChance: 30, moedaPorNivel: 1,
    eliteChance: 5, eliteNivelMin: 3, campeaoChance: 0.5, campeaoNivelMin: 10, dimensoes: DIMENSOES_PADRAO },
  taverna: {
    nome: "Bartolomeu",
    falas: [
      "Bem-vindo, {jogador}! O quadro está cheio de trabalho hoje.",
      "Mais um aventureiro com sede de glória... Dê uma olhada no quadro.",
      "Dizem que os mortos andam pelas estradas à noite. A Guilda paga bem por isso.",
      "Senta, descansa. Mas não esquece de pagar a próxima rodada, {jogador}.",
    ],
  },
};

const CHAVE = "jogo";

export async function lerConfig(): Promise<ConfigJogo> {
  const linha = await prisma.configuracao.findUnique({ where: { chave: CHAVE } });
  const dados = configSchema.safeParse(linha?.valor);
  return dados.success ? dados.data : CONFIG_PADRAO;
}

export async function salvarConfig(config: ConfigJogo) {
  const valor = configSchema.parse(config) as unknown as Prisma.InputJsonValue;
  await prisma.configuracao.upsert({
    where: { chave: CHAVE },
    update: { valor },
    create: { chave: CHAVE, valor },
  });
}

export function xpParaProximo(c: ConfigJogo, nivel: number) {
  return Math.max(1, Math.round(c.progressao.xpBase * Math.pow(nivel, c.progressao.xpExpoente)));
}

export function rankDoNivel(c: ConfigJogo, nivel: number) {
  let atual: string = "F";
  for (const r of c.ranks) if (nivel >= r.nivelMin) atual = r.rank;
  return atual;
}
