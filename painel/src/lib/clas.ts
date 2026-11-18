import "server-only";
import fs from "fs";
import path from "path";

/**
 * Clãs do servidor. São do próprio mod (clas/Clas.java), que grava tudo em <mundo>/frx_clas.json;
 * o painel só lê. Um jogador pode estar em mais de um clã.
 */
export type ClaArquivo = { id: string; nome: string; descricao: string; cor: string; dono: string; cofre: number; membros: { uuid: string; cargo: "dono" | "oficial" | "membro" }[] };

const CARGOS: Record<string, "dono" | "oficial" | "membro"> = { lider: "dono", oficial: "oficial", membro: "membro" };

export function lerClas(pastaMundo: string): ClaArquivo[] {
  type Bruto = { id?: string; nome?: string; corHex?: string; lider?: string; cofre?: number; membros?: Record<string, string> };
  let raiz: { clas?: Bruto[] } = {};
  try { raiz = JSON.parse(fs.readFileSync(path.join(pastaMundo, "frx_clas.json"), "utf8")); } catch { return []; }
  const clas: ClaArquivo[] = [];
  for (const c of Array.isArray(raiz.clas) ? raiz.clas : []) {
    const membros = Object.entries(c.membros ?? {}).map(([uuid, cargo]) => ({ uuid, cargo: CARGOS[cargo] ?? "membro" }));
    if (!c.id || !membros.length) continue;
    clas.push({
      id: c.id, nome: String(c.nome ?? "Clã").slice(0, 40), descricao: "",
      cor: /^#[0-9a-fA-F]{6}$/.test(c.corHex ?? "") ? c.corHex! : "#d4a93a",
      cofre: Math.max(0, Math.floor(Number(c.cofre) || 0)), dono: c.lider ?? membros.find((m) => m.cargo === "dono")?.uuid ?? "", membros,
    });
  }
  return clas;
}
