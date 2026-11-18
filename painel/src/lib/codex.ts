import "server-only";
import crypto from "crypto";
import zlib from "zlib";
import { prisma } from "@/lib/prisma";
import fs from "fs";
import { modeloDoItem } from "@/lib/jar-icones";
import { arquivoDoDisco } from "@/lib/discos";

/** O CustomModelData das armas do Códex começa aqui, para não bater com outros pacotes. */
export const BASE_MODELO = 880000;
/** O CustomModelData dos discos com desenho próprio começa aqui (1 a 16 são as aparências prontas). */
export const BASE_DISCO = 1000;
export const RANKS_CODEX = ["F", "E", "D", "C", "B", "A", "S"];
export const RARIDADES_CODEX = ["Comum", "Incomum", "Raro", "Épico", "Lendário"];

/** Endereço público do site: é por ele que o jogo dos jogadores baixa o pacote de recursos. */
export function urlPublica() {
  return (process.env.SITE_URL ?? "https://craft.frgomes.com.br").replace(/\/+$/, "");
}

/** PNG válido e pequeno (16x16 ou 32x32)? Devolve os bytes ou null. */
export function pngDaTextura(base64: string): Buffer | null {
  if (!base64) return null;
  try {
    const b = Buffer.from(base64.replace(/^data:image\/png;base64,/, ""), "base64");
    if (b.length < 60 || b.length > 20_000 || b.readUInt32BE(0) !== 0x89504e47) return null;
    const w = b.readUInt32BE(16), h = b.readUInt32BE(20);
    return w === h && (w === 16 || w === 32) ? b : null;
  } catch { return null; }
}

// ------------------------------------------------------------------ zip (sem compressão; os arquivos são minúsculos)

function zip(arquivos: { nome: string; dados: Buffer }[]): Buffer {
  const partes: Buffer[] = [], central: Buffer[] = [];
  let pos = 0;
  for (const a of arquivos) {
    const nome = Buffer.from(a.nome, "utf8"), crc = zlib.crc32(a.dados) >>> 0;
    const cab = Buffer.alloc(30);
    cab.writeUInt32LE(0x04034b50, 0); cab.writeUInt16LE(20, 4); cab.writeUInt16LE(0x0800, 6); cab.writeUInt16LE(0, 8);
    cab.writeUInt16LE(0, 10); cab.writeUInt16LE(0x21, 12);   // hora e data fixas: o mesmo conteúdo dá sempre o mesmo arquivo
    cab.writeUInt32LE(crc, 14); cab.writeUInt32LE(a.dados.length, 18); cab.writeUInt32LE(a.dados.length, 22);
    cab.writeUInt16LE(nome.length, 26); cab.writeUInt16LE(0, 28);
    partes.push(cab, nome, a.dados);
    const c = Buffer.alloc(46);
    c.writeUInt32LE(0x02014b50, 0); c.writeUInt16LE(20, 4); c.writeUInt16LE(20, 6); c.writeUInt16LE(0x0800, 8); c.writeUInt16LE(0, 10);
    c.writeUInt16LE(0, 12); c.writeUInt16LE(0x21, 14);
    c.writeUInt32LE(crc, 16); c.writeUInt32LE(a.dados.length, 20); c.writeUInt32LE(a.dados.length, 24);
    c.writeUInt16LE(nome.length, 28); c.writeUInt32LE(0, 30); c.writeUInt32LE(0, 34); c.writeUInt32LE(0, 38); c.writeUInt32LE(pos, 42);
    central.push(c, nome);
    pos += 30 + nome.length + a.dados.length;
  }
  const dir = Buffer.concat(central), fim = Buffer.alloc(22);
  fim.writeUInt32LE(0x06054b50, 0); fim.writeUInt16LE(arquivos.length, 8); fim.writeUInt16LE(arquivos.length, 10);
  fim.writeUInt32LE(dir.length, 12); fim.writeUInt32LE(pos, 16);
  return Buffer.concat([...partes, dir, fim]);
}

// ------------------------------------------------------------------ pacote de recursos

export type Pacote = { zip: Buffer; sha1: string; armas: number; discos: number };
const g = globalThis as unknown as { __frxPacote?: { chave: string; pacote: Pacote } };

/**
 * Pacote de recursos com a aparência das armas do Códex. Para cada item base, o modelo original ganha um "override":
 * quando o item tem o CustomModelData da arma, o jogo usa a textura dela. Sem armas com textura, o pacote sai vazio (armas: 0).
 */
export async function pacoteRecursos(): Promise<Pacote> {
  const armas = await prisma.armaCodex.findMany({ where: { ativa: true, NOT: { textura: "" } }, orderBy: { modelo: "asc" }, select: { codigo: true, itemBase: true, textura: true, modelo: true, atualizadoEm: true } });
  // todos os discos, à venda ou não: quem já comprou continua ouvindo e vendo o desenho
  const discos = await prisma.disco.findMany({ orderBy: { modelo: "asc" }, select: { codigo: true, tamanho: true, textura: true, modelo: true, atualizadoEm: true } });
  const chave = armas.map((a) => `${a.codigo}:${a.itemBase}:${a.modelo}:${crypto.createHash("md5").update(a.textura).digest("hex")}`).join("|")
    + "#" + discos.map((d) => `${d.codigo}:${d.tamanho}:${d.modelo}:${d.atualizadoEm.getTime()}`).join("|");
  const guardado = g.__frxPacote;
  if (guardado && guardado.chave === chave) return guardado.pacote;

  const arquivos: { nome: string; dados: Buffer }[] = [
    { nome: "pack.mcmeta", dados: Buffer.from(JSON.stringify({ pack: { pack_format: 15, description: "FRX-WORLD · armas do Códex e discos de música" } })) },
  ];
  const porBase = new Map<string, { codigo: string; modelo: number }[]>();
  let n = 0;
  for (const a of armas) {
    const png = pngDaTextura(a.textura);
    if (!png || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(a.itemBase) || !/^[a-z0-9_]+$/.test(a.codigo)) continue;
    arquivos.push({ nome: `assets/frxworld/textures/item/codex/${a.codigo}.png`, dados: png });
    arquivos.push({ nome: `assets/frxworld/models/item/codex/${a.codigo}.json`, dados: Buffer.from(JSON.stringify({ parent: "minecraft:item/handheld", textures: { layer0: `frxworld:item/codex/${a.codigo}` } })) });
    porBase.set(a.itemBase, [...(porBase.get(a.itemBase) ?? []), { codigo: a.codigo, modelo: BASE_MODELO + a.modelo }]);
    n++;
  }
  for (const [base, lista] of porBase) {
    const [ns, nome] = base.split(":");
    const original = modeloDoItem(base) ?? { parent: "minecraft:item/handheld", textures: { layer0: `${ns}:item/${nome}` } };
    const antigos = Array.isArray(original.overrides) ? (original.overrides as unknown[]) : [];
    const novos = lista.sort((a, b) => a.modelo - b.modelo).map((l) => ({ predicate: { custom_model_data: l.modelo }, model: `frxworld:item/codex/${l.codigo}` }));
    arquivos.push({ nome: `assets/${ns}/models/item/${nome}.json`, dados: Buffer.from(JSON.stringify({ ...original, overrides: [...antigos, ...novos] })) });
  }
  // discos de música: cada um vira o som frxworld:disco.<codigo>, tocado pelo mod na jukebox
  const sons: Record<string, unknown> = {};
  let nd = 0;
  for (const d of discos) {
    if (!/^[a-z0-9_]+$/.test(d.codigo)) continue;
    let audio: Buffer;
    try { audio = fs.readFileSync(arquivoDoDisco(d.codigo)); } catch { continue; }
    arquivos.push({ nome: `assets/frxworld/sounds/discos/${d.codigo}.ogg`, dados: audio });
    sons[`disco.${d.codigo}`] = { category: "record", sounds: [{ name: `frxworld:discos/${d.codigo}`, stream: true }] };
    nd++;
  }
  if (nd > 0) arquivos.push({ nome: "assets/frxworld/sounds.json", dados: Buffer.from(JSON.stringify(sons)) });
  // desenho próprio dos discos: o modelo do item frxworld:disco ganha um "override" por disco (CustomModelData 1000 + modelo)
  const desenhados: { codigo: string; modelo: number }[] = [];
  for (const d of discos) {
    const png = pngDaTextura(d.textura);
    if (!png || !/^[a-z0-9_]+$/.test(d.codigo)) continue;
    arquivos.push({ nome: `assets/frxworld/textures/item/discos/${d.codigo}.png`, dados: png });
    arquivos.push({ nome: `assets/frxworld/models/item/discos/${d.codigo}.json`, dados: Buffer.from(JSON.stringify({ parent: "minecraft:item/generated", textures: { layer0: `frxworld:item/discos/${d.codigo}` } })) });
    desenhados.push({ codigo: d.codigo, modelo: BASE_DISCO + d.modelo });
  }
  if (desenhados.length > 0) {
    const prontos = Array.from({ length: 16 }, (_, i) => ({ predicate: { custom_model_data: i + 1 }, model: `frxworld:item/disco_${i + 1}` }));
    const proprios = desenhados.sort((a, b) => a.modelo - b.modelo).map((l) => ({ predicate: { custom_model_data: l.modelo }, model: `frxworld:item/discos/${l.codigo}` }));
    arquivos.push({ nome: "assets/frxworld/models/item/disco.json", dados: Buffer.from(JSON.stringify({ parent: "minecraft:item/generated", textures: { layer0: "minecraft:item/music_disc_13" }, overrides: [...prontos, ...proprios] })) });
  }
  const z = zip(arquivos);
  const pacote: Pacote = { zip: z, sha1: crypto.createHash("sha1").update(z).digest("hex"), armas: n, discos: nd };
  g.__frxPacote = { chave, pacote };
  return pacote;
}
