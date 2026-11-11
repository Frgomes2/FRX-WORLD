import { z } from "zod";

export const itemLoot = z.object({
  item: z.string().min(3),
  qtd: z.number().int().min(1).max(6400),
  chance: z.number().min(1).max(100),
});

/** "minecraft:diamond*2@50, minecraft:emerald*5" → [{ item, qtd, chance }] (chance padrão 100%) */
export function lerLoot(texto: string) {
  return texto
    .split(/[,\n;]/)
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => {
      const [antes, chance] = p.split("@").map((x) => x.trim());
      const [item, qtd] = antes.split("*").map((x) => x.trim());
      return itemLoot.parse({
        item: item.includes(":") ? item : `minecraft:${item}`,
        qtd: Number(qtd ?? 1),
        chance: Number(chance ?? 100),
      });
    });
}

export function lootParaTexto(json: unknown) {
  const lista = z.array(itemLoot).safeParse(json);
  return lista.success ? lista.data.map((i) => `${i.item}*${i.qtd}${i.chance < 100 ? `@${i.chance}` : ""}`).join(", ") : "";
}

export const guardiaoAndar = z.object({
  bossTipo: z.string().min(3), bossNome: z.string().min(1).max(40), bossVida: z.number().int().min(1).max(100000),
  bossDano: z.number().min(0).max(1000), bossVelocidade: z.number().min(0.2).max(3),
  lacaioTipo: z.string().min(3), lacaioQtd: z.number().int().min(0).max(30),
});
export type GuardiaoAndar = z.infer<typeof guardiaoAndar>;

export function lerAndares(json: unknown): GuardiaoAndar[] {
  const r = z.array(guardiaoAndar).safeParse(json);
  return r.success ? r.data : [];
}

export const EXEMPLOS_DUNGEON = [
  {
    nome: "Cripta dos Esquecidos", descricao: "Ruínas perto das cidades, onde os primeiros aventureiros testam a coragem.",
    distanciaMin: 0, distanciaMax: 600, nivelRecomendado: 5,
    bossTipo: "minecraft:zombie", bossNome: "Rei dos Mortos", bossVida: 120, bossDano: 7, bossVelocidade: 1.1,
    lacaioTipo: "minecraft:zombie", lacaioQtd: 4, recompensaXp: 800, recargaHoras: 12,
    andares: [
      { bossTipo: "minecraft:husk", bossNome: "Sentinela Ressecada", bossVida: 50, bossDano: 4, bossVelocidade: 1, lacaioTipo: "minecraft:zombie", lacaioQtd: 2 },
      { bossTipo: "minecraft:skeleton", bossNome: "Arqueiro Carcereiro", bossVida: 80, bossDano: 5, bossVelocidade: 1, lacaioTipo: "minecraft:skeleton", lacaioQtd: 3 },
    ],
    loot: [{ item: "minecraft:iron_ingot", qtd: 8, chance: 100 }, { item: "minecraft:golden_apple", qtd: 1, chance: 60 }, { item: "minecraft:emerald", qtd: 4, chance: 100 }],
  },
  {
    nome: "Cripta Sombria", descricao: "Mais longe da civilização, os mortos usam armadura e espada.",
    distanciaMin: 600, distanciaMax: 1800, nivelRecomendado: 20,
    bossTipo: "minecraft:wither_skeleton", bossNome: "Cavaleiro Sombrio", bossVida: 300, bossDano: 12, bossVelocidade: 1.15,
    lacaioTipo: "minecraft:skeleton", lacaioQtd: 6, recompensaXp: 3000, recargaHoras: 24,
    andares: [
      { bossTipo: "minecraft:stray", bossNome: "Vigia Gélido", bossVida: 120, bossDano: 7, bossVelocidade: 1.05, lacaioTipo: "minecraft:stray", lacaioQtd: 3 },
      { bossTipo: "minecraft:vindicator", bossNome: "Carrasco da Cripta", bossVida: 180, bossDano: 10, bossVelocidade: 1.05, lacaioTipo: "minecraft:skeleton", lacaioQtd: 4 },
    ],
    loot: [{ item: "minecraft:diamond", qtd: 2, chance: 100 }, { item: "minecraft:golden_apple", qtd: 2, chance: 100 }, { item: "minecraft:enchanted_golden_apple", qtd: 1, chance: 10 }],
  },
  {
    nome: "Cripta Ancestral", descricao: "Nas terras de ninguém, algo antigo guarda tesouros esquecidos.",
    distanciaMin: 1800, distanciaMax: 100000, nivelRecomendado: 45,
    bossTipo: "minecraft:ravager", bossNome: "Fera Ancestral", bossVida: 700, bossDano: 18, bossVelocidade: 1.0,
    lacaioTipo: "minecraft:vindicator", lacaioQtd: 5, recompensaXp: 12000, recargaHoras: 48,
    andares: [
      { bossTipo: "minecraft:wither_skeleton", bossNome: "Guarda Ancestral", bossVida: 280, bossDano: 12, bossVelocidade: 1.1, lacaioTipo: "minecraft:wither_skeleton", lacaioQtd: 3 },
      { bossTipo: "minecraft:evoker", bossNome: "Conjurador Esquecido", bossVida: 400, bossDano: 8, bossVelocidade: 1, lacaioTipo: "minecraft:vindicator", lacaioQtd: 4 },
    ],
    loot: [{ item: "minecraft:diamond", qtd: 6, chance: 100 }, { item: "minecraft:netherite_scrap", qtd: 2, chance: 50 }, { item: "minecraft:totem_of_undying", qtd: 1, chance: 25 }],
  },
];
