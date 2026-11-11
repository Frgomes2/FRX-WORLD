import "server-only";
import fs from "fs";
import path from "path";

/** Dados das capitais lidos direto da pasta do servidor (frxworld/reinos/posicoes.json e reino.json de cada capital). */
export type ReinoServidor = {
  id: string; nome: string; x: number; z: number; larguraChunks: number; profundidadeChunks: number;
  modo: string; altura: number; npcsFixos: number; praca: [number, number] | null;
  x0: number; z0: number; x1: number; z1: number;
};
export type MasmorraPosicao = { id: string; nome: string; chefe: string; x: number; z: number };
export type DadosReinosServidor = { reinos: ReinoServidor[]; spawn: [number, number] | null; masmorras: MasmorraPosicao[] };

function pasta() {
  return path.join(path.resolve(process.cwd(), process.env.SERVIDOR_DIR ?? "../server"), "frxworld", "reinos");
}

const g = globalThis as unknown as { __frxReinos?: { chave: string; dados: DadosReinosServidor } };

export function reinosDoServidor(): DadosReinosServidor {
  const dir = pasta();
  const arqPos = path.join(dir, "posicoes.json");
  let pos: Record<string, unknown>;
  let chave = "";
  try {
    const st = fs.statSync(arqPos);
    chave = `${st.mtimeMs}`;
    for (const d of fs.readdirSync(dir)) {
      const m = path.join(dir, d, "reino.json");
      if (fs.existsSync(m)) chave += `|${d}:${fs.statSync(m).mtimeMs}`;
    }
    if (g.__frxReinos?.chave === chave) return g.__frxReinos.dados;
    pos = JSON.parse(fs.readFileSync(arqPos, "utf8"));
  } catch {
    return { reinos: [], spawn: null, masmorras: [] };
  }

  const reinos: ReinoServidor[] = [];
  for (const d of fs.readdirSync(dir)) {
    const arq = path.join(dir, d, "reino.json");
    const p = pos[d] as { nome?: string; x?: number; z?: number; praca?: [number, number]; npcs?: Record<string, unknown> } | undefined;
    if (!fs.existsSync(arq) || !p || typeof p.x !== "number" || typeof p.z !== "number") continue;
    try {
      const m = JSON.parse(fs.readFileSync(arq, "utf8")) as { modo: string; larguraChunks: number; profundidadeChunks: number; altura: number };
      // mesma conta de Reino.java (chunkX0 = (centroX >> 4) - largura / 2)
      const cx0 = (p.x >> 4) - Math.trunc(m.larguraChunks / 2), cz0 = (p.z >> 4) - Math.trunc(m.profundidadeChunks / 2);
      reinos.push({
        id: d, nome: p.nome ?? d, x: p.x, z: p.z, larguraChunks: m.larguraChunks, profundidadeChunks: m.profundidadeChunks,
        modo: m.modo, altura: m.altura, npcsFixos: p.npcs ? Object.keys(p.npcs).length : 0, praca: p.praca ?? null,
        x0: cx0 * 16, z0: cz0 * 16, x1: (cx0 + m.larguraChunks) * 16 - 1, z1: (cz0 + m.profundidadeChunks) * 16 - 1,
      });
    } catch { /* reino com arquivo quebrado: ignora */ }
  }
  const spawn = Array.isArray(pos.spawn) ? (pos.spawn as [number, number]) : null;
  const masmorras = Array.isArray(pos.masmorras)
    ? (pos.masmorras as { id: string; nome: string; chefe?: string; x: number; z: number }[]).map((m) => ({ id: m.id, nome: m.nome, chefe: m.chefe ?? "", x: m.x, z: m.z }))
    : [];
  const dados = { reinos, spawn, masmorras };
  g.__frxReinos = { chave, dados };
  return dados;
}
