import "server-only";
import { prisma } from "@/lib/prisma";
import { traduzir } from "@/lib/jar-icones";

/** Nomes em português das criaturas do Minecraft (o jar do jogo só traz o inglês). */
export const NOMES_VANILLA: Record<string, string> = {
  zombie: "Zumbi", husk: "Zumbi do deserto", drowned: "Afogado", zombie_villager: "Aldeão zumbi", skeleton: "Esqueleto", stray: "Esqueleto errante",
  wither_skeleton: "Esqueleto Wither", creeper: "Creeper", spider: "Aranha", cave_spider: "Aranha de caverna", enderman: "Enderman", endermite: "Endermite",
  silverfish: "Traça", witch: "Bruxa", slime: "Slime", magma_cube: "Cubo de magma", phantom: "Phantom", blaze: "Blaze", ghast: "Ghast",
  piglin: "Piglin", piglin_brute: "Piglin bruto", zombified_piglin: "Piglin zumbificado", hoglin: "Hoglin", zoglin: "Zoglin",
  pillager: "Saqueador", vindicator: "Vingador", evoker: "Invocador", illusioner: "Ilusionista", ravager: "Devastador", vex: "Vex",
  guardian: "Guardião", elder_guardian: "Guardião ancião", shulker: "Shulker", warden: "Warden", wither: "Wither", ender_dragon: "Dragão do End",
  cow: "Vaca", pig: "Porco", sheep: "Ovelha", chicken: "Galinha", horse: "Cavalo", donkey: "Burro", mule: "Mula", rabbit: "Coelho", wolf: "Lobo",
  cat: "Gato", ocelot: "Jaguatirica", fox: "Raposa", bee: "Abelha", goat: "Cabra", llama: "Lhama", trader_llama: "Lhama de mercador",
  parrot: "Papagaio", turtle: "Tartaruga", frog: "Sapo", tadpole: "Girino", axolotl: "Axolote", bat: "Morcego", squid: "Lula",
  glow_squid: "Lula brilhante", cod: "Bacalhau", salmon: "Salmão", tropical_fish: "Peixe tropical", pufferfish: "Baiacu", dolphin: "Golfinho",
  polar_bear: "Urso polar", panda: "Panda", mooshroom: "Vacogumelo", strider: "Lavagante", camel: "Camelo", sniffer: "Farejador",
  allay: "Allay", villager: "Aldeão", wandering_trader: "Mercador ambulante", iron_golem: "Golem de ferro", snow_golem: "Golem de neve",
  skeleton_horse: "Cavalo esqueleto", zombie_horse: "Cavalo zumbi",
};

export type Criatura = { id: string; nome: string; mod: string; categoria: string; chefe: boolean; cor1?: number; cor2?: number };

const NOME_MOD: Record<string, string> = {
  minecraft: "Minecraft", alexsmobs: "Alex's Mobs", cataclysm: "L_Ender's Cataclysm", mowziesmobs: "Mowzie's Mobs", graveyard: "The Graveyard",
  born_in_chaos_v1: "Born in Chaos", iceandfire: "Ice and Fire", aquamirae: "Aquamirae", mutantmonsters: "Mutant Monsters",
  creeperoverhaul: "Creeper Overhaul", alexscaves: "Alex's Caves", guardvillagers: "Guard Villagers", illagerinvasion: "Illager Invasion",
  netherdepthsupgrade: "Nether Depths", unusualend: "Unusual End", frxworld: "FRX-WORLD",
};

export async function listaCriaturas(): Promise<Criatura[] | null> {
  const linha = await prisma.configuracao.findUnique({ where: { chave: "entidades" } });
  const brutas = (linha?.valor as { entidades?: { id: string; categoria: string; chave: string; chefe?: boolean; cor1?: number; cor2?: number }[] } | null)?.entidades;
  if (!brutas) return null;
  return brutas.map((e) => {
    const [ns, nome] = e.id.split(":");
    let n: string | null = ns === "minecraft" ? NOMES_VANILLA[nome] ?? null : null;
    if (!n) { try { n = traduzir(e.chave); } catch { n = null; } }
    return {
      id: e.id, mod: NOME_MOD[ns] ?? ns, categoria: e.categoria, chefe: !!e.chefe, cor1: e.cor1, cor2: e.cor2,
      nome: n ?? nome.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
    };
  }).sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
}
