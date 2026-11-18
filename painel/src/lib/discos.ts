import "server-only";
import fs from "fs";
import path from "path";

/** Pasta dos áudios dos discos (fora do git). */
export const PASTA_DISCOS = path.join(process.cwd(), "dados", "discos");

export function arquivoDoDisco(codigo: string) {
  return path.join(PASTA_DISCOS, `${codigo}.ogg`);
}

export function temArquivo(codigo: string) {
  try { return fs.statSync(arquivoDoDisco(codigo)).size > 0; } catch { return false; }
}

/** Confere se o arquivo é OGG Vorbis (o único formato que o Minecraft toca) e lê canais e duração. */
export function lerOgg(b: Buffer): { canais: number; taxa: number; duracao: number } | null {
  if (b.length < 200 || b.toString("latin1", 0, 4) !== "OggS") return null;
  const i = b.indexOf("\x01vorbis", 0, "latin1");
  if (i < 0 || i > 300) return null;
  const canais = b.readUInt8(i + 11), taxa = b.readUInt32LE(i + 12);
  if (canais < 1 || canais > 2 || taxa < 8000 || taxa > 192000) return null;
  // a duração está na última página do arquivo: posição (em amostras) dividida pela taxa
  let duracao = 0, fim = b.length;
  for (let tent = 0; tent < 8 && fim > 0; tent++) {
    const j = b.lastIndexOf("OggS", fim - 1, "latin1");
    if (j < 0 || j + 14 > b.length) break;
    const pos = b.readBigInt64LE(j + 6);
    if (pos > BigInt(0)) { duracao = Math.round(Number(pos) / taxa); break; }
    fim = j;
  }
  return { canais, taxa, duracao };
}
