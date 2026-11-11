import { z } from "zod";

export const TIPOS = { MATAR: "Caçar", COLETAR: "Coletar e entregar" } as const;

export const itemRecompensa = z.object({ item: z.string().min(3), qtd: z.number().int().min(1).max(6400) });

/** "minecraft:bread*5, minecraft:iron_ingot*2" → [{ item, qtd }] */
export function lerItens(texto: string) {
  return texto
    .split(/[,\n;]/)
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => {
      const [item, qtd] = p.split("*").map((x) => x.trim());
      return itemRecompensa.parse({ item: item.includes(":") ? item : `minecraft:${item}`, qtd: Number(qtd ?? 1) });
    });
}

export function itensParaTexto(json: unknown) {
  const lista = z.array(itemRecompensa).safeParse(json);
  return lista.success ? lista.data.map((i) => `${i.item}*${i.qtd}`).join(", ") : "";
}

export const EXEMPLOS = [
  { titulo: "Praga de zumbis", descricao: "Os mortos rondam as estradas à noite. Limpe a área.", tipo: "MATAR", alvo: "minecraft:zombie", quantidade: 10, nivelMin: 1, recompensaXp: 150, recompensaItens: [{ item: "minecraft:bread", qtd: 5 }], repetivel: true, intervaloHoras: 24, ordem: 1 },
  { titulo: "Couro para a Guilda", descricao: "A Guilda Livre precisa de couro para as mochilas dos novatos.", tipo: "COLETAR", alvo: "minecraft:leather", quantidade: 10, nivelMin: 1, recompensaXp: 120, recompensaItens: [{ item: "minecraft:emerald", qtd: 1 }], repetivel: true, intervaloHoras: 12, ordem: 2 },
  { titulo: "Esqueletos na estrada", descricao: "Arqueiros mortos-vivos atacam as caravanas.", tipo: "MATAR", alvo: "minecraft:skeleton", quantidade: 10, nivelMin: 3, recompensaXp: 250, recompensaItens: [{ item: "minecraft:arrow", qtd: 16 }], repetivel: false, intervaloHoras: 0, ordem: 3 },
  { titulo: "Ferro para a forja de Dwargon", descricao: "Os ferreiros anões pagam bem por ferro de qualidade.", tipo: "COLETAR", alvo: "minecraft:iron_ingot", quantidade: 16, nivelMin: 5, recompensaXp: 400, recompensaItens: [{ item: "minecraft:emerald", qtd: 3 }], repetivel: false, intervaloHoras: 0, ordem: 4 },
  { titulo: "Ninho de aranhas", descricao: "Depois das estradas, as cavernas. Acabe com as aranhas.", tipo: "MATAR", alvo: "minecraft:spider", quantidade: 15, nivelMin: 8, recompensaXp: 600, recompensaItens: [{ item: "minecraft:golden_apple", qtd: 1 }], repetivel: false, intervaloHoras: 0, ordem: 5, preRequisitoTitulo: "Esqueletos na estrada" },
  { titulo: "Caçador de creepers", descricao: "Rank E: prove seu valor contra a ameaça explosiva.", tipo: "MATAR", alvo: "minecraft:creeper", quantidade: 10, nivelMin: 10, recompensaXp: 1000, recompensaItens: [{ item: "minecraft:diamond", qtd: 1 }], repetivel: false, intervaloHoras: 0, ordem: 6 },
];
