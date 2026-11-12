/** Faixa de nível [mínimo, máximo] de cada dimensão (o servidor lê em zonas.dimensoes). */
export const DIMENSOES_PADRAO: Record<string, [number, number]> = {
  "twilightforest:twilight_forest": [10, 25],
  "minecraft:the_nether": [15, 35],
  "undergarden:undergarden": [20, 35],
  "blue_skies:everbright": [25, 40],
  "aether:the_aether": [30, 45],
  "minecraft:the_end": [30, 50],
  "blue_skies:everdawn": [35, 50],
  "deeperdarker:otherside": [45, 60],
};
export const NOMES_DIMENSOES: Record<string, string> = {
  "twilightforest:twilight_forest": "Floresta do Crepúsculo", "minecraft:the_nether": "Nether", "undergarden:undergarden": "Undergarden",
  "blue_skies:everbright": "Everbright", "aether:the_aether": "Aether", "minecraft:the_end": "O Fim",
  "blue_skies:everdawn": "Everdawn", "deeperdarker:otherside": "O Outro Lado",
};
