import "server-only";
import fs from "fs";
import path from "path";

/**
 * Chefes das masmorras dos reinos e do Twilight Forest para o site público.
 * Lê os arquivos que o servidor grava no mundo (frx_chefes_masmorra.json e frx_chefes_crepusculo.json).
 * Regra do site: nada de UUID, posição ou conversa; aqui só aparece o nome do personagem que deu o golpe final.
 */
export type ChefePublico = {
  chave: string;
  grupo: "reino" | "crepusculo";
  /** Reino ou "Floresta do Crepúsculo". */
  lugar: string;
  /** Nome da masmorra ou do covil. */
  covil: string;
  nome: string;
  vivo: boolean;
  /** Geração do chefe vivo agora, ou da próxima volta (0 = original; -1 = não se sabe). */
  geracao: number;
  /** Quando volta (ms), se está derrotado. */
  voltaEm: number | null;
  ultima: { matador: string; duracao: number; quando: number } | null;
};

const ESPERA_REINO = 24 * 3600_000;
const ESPERA_TF = 24 * 3600_000;

/** Masmorras fixas: reino, nome da masmorra e o chefe de sempre (quando não há registro de luta). */
const MASMORRAS: Record<string, { reino: string; covil: string; chefe: string }> = {
  "coliseu-tempest": { reino: "Tempest", covil: "Fábrica Ancestral", chefe: "The Harbinger" },
  "lubelius-abismo": { reino: "Lubelius", covil: "Arena Flamejante", chefe: "Ignis" },
  "sarion-gelo": { reino: "Sarion", covil: "Prisão Congelada", chefe: "Maledictus" },
  "dwargon-ferro": { reino: "Dwargon", covil: "Câmara do Cavaleiro de Ferro", chefe: "Ferrous Wroughtnaut" },
  "leste-mosteiro": { reino: "Leste", covil: "Mosteiro da Montanha", chefe: "Ancient Remnant" },
  "sakura-gorgona": { reino: "Distrito Sakura", covil: "Templo da Górgona", chefe: "Lich" },
  "oeste-cidadela": { reino: "Oeste", covil: "Cidadela em Ruínas", chefe: "Ender Guardian" },
  "demonios-forja": { reino: "Lordes Demônios", covil: "Forja das Almas", chefe: "Netherite Monstrosity" },
};

const COVIS_TF: Record<string, string> = {
  "twilightforest:naga": "Pátio da Naga", "twilightforest:lich": "Torre do Lich", "twilightforest:minoshroom": "Labirinto do Minoshroom",
  "twilightforest:hydra": "Covil da Hydra", "twilightforest:knight_phantom": "Fortaleza dos Cavaleiros", "twilightforest:ur_ghast": "Torre Sombria",
  "twilightforest:alpha_yeti": "Caverna do Yeti", "twilightforest:snow_queen": "Palácio de Gelo",
};

type Reg = { masmorra?: string; tipo?: string; nome?: string; geracao?: number; morreu?: number; estado?: string; matador?: string; duracao?: number; quando?: number; geracaoMorta?: number };

function lerJson(arq: string): Record<string, Reg> {
  try { return JSON.parse(fs.readFileSync(arq, "utf8")) as Record<string, Reg>; } catch { return {}; }
}

export function chefesPublicos(pastaMundo: string): ChefePublico[] {
  const agora = Date.now();
  const lista: ChefePublico[] = [];
  const m = lerJson(path.join(pastaMundo, "frx_chefes_masmorra.json"));
  const pendentes = Object.entries(m).filter(([k]) => !k.startsWith("_"));
  const ultimos = Object.entries(m).filter(([k]) => k.startsWith("_ultimo|"));
  for (const [id, info] of Object.entries(MASMORRAS)) {
    const pend = pendentes.find(([, r]) => r.masmorra === id)?.[1];
    const ult = ultimos.filter(([, r]) => r.masmorra === id).map(([, r]) => r).sort((a, b) => (b.quando ?? 0) - (a.quando ?? 0))[0];
    const vivoReg = Object.entries(m).find(([k, r]) => k.startsWith("_vivo|") && r.masmorra === id)?.[1];
    const voltaEm = pend?.morreu ? pend.morreu + ESPERA_REINO : null;
    const vivo = !pend || (voltaEm !== null && voltaEm <= agora);
    const geracao = !vivo ? (pend?.geracao ?? -1) : vivoReg ? (vivoReg.geracao ?? 0) : pend ? (pend.geracao ?? -1) : ult ? Math.min(10, (ult.geracao ?? 0) + 1) : -1;
    lista.push({
      chave: id, grupo: "reino", lugar: info.reino, covil: info.covil, nome: pend?.nome || vivoReg?.nome || ult?.nome || info.chefe,
      vivo, geracao, voltaEm: vivo ? null : voltaEm,
      ultima: ult ? { matador: ult.matador ?? "", duracao: ult.duracao ?? -1, quando: ult.quando ?? 0 } : pend?.morreu ? { matador: "", duracao: -1, quando: pend.morreu } : null,
    });
  }
  const c = lerJson(path.join(pastaMundo, "frx_chefes_crepusculo.json"));
  for (const [k, r] of Object.entries(c)) {
    const morto = r.estado === "morto" || r.estado === "pronto";
    const voltaEm = morto && r.morreu ? r.morreu + ESPERA_TF : null;
    const vivo = !morto || (voltaEm !== null && voltaEm <= agora);
    lista.push({
      chave: k, grupo: "crepusculo", lugar: "Floresta do Crepúsculo", covil: COVIS_TF[r.tipo ?? ""] ?? "Covil", nome: r.nome ?? "Chefe",
      vivo, geracao: r.geracao ?? -1, voltaEm: vivo ? null : voltaEm,
      ultima: r.morreu ? { matador: r.matador ?? "", duracao: r.duracao ?? -1, quando: r.morreu } : null,
    });
  }
  return lista;
}
