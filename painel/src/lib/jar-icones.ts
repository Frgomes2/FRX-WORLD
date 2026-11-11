import "server-only";
import fs from "fs";
import os from "os";
import path from "path";
import zlib from "zlib";

/**
 * Lê texturas direto dos .jar (mods do servidor/cliente e o jar do Minecraft) para mostrar
 * ícones de itens, armaduras e a skin padrão no painel. Leitor de ZIP mínimo, sem dependências.
 */

type Entrada = { jar: string; metodo: number; tam: number; offset: number };

function pastaServidor() {
  return path.resolve(process.cwd(), process.env.SERVIDOR_DIR ?? "../server");
}

function jars(): string[] {
  const lista: string[] = [];
  const add = (dir: string) => {
    if (!fs.existsSync(dir)) return;
    for (const n of fs.readdirSync(dir)) if (n.toLowerCase().endsWith(".jar")) lista.push(path.join(dir, n));
  };
  // o jar do Minecraft primeiro (texturas vanilla), depois os mods
  const appdata = process.env.APPDATA ?? path.join(os.homedir(), "AppData", "Roaming");
  const candidatos = [
    process.env.MC_CLIENT_JAR,
    path.join(appdata, ".minecraft", "versions", "1.20.1", "1.20.1.jar"),
    path.join(os.homedir(), ".minecraft", "versions", "1.20.1", "1.20.1.jar"),
  ].filter(Boolean) as string[];
  for (const c of candidatos) if (fs.existsSync(c)) { lista.push(c); break; }
  add(path.resolve(pastaServidor(), "..", "cliente", "jogo", "mods"));
  add(path.join(pastaServidor(), "mods"));
  return lista;
}

const INTERESSA = /^assets\/[^/]+\/(textures\/(item|block|models\/armor|entity\/player)\/|models\/(item|block)\/|lang\/(pt_br|en_us)\.json$)/;

function indexarJar(jar: string, indice: Map<string, Entrada>) {
  let fd: number | null = null;
  try {
    fd = fs.openSync(jar, "r");
    const tam = fs.fstatSync(fd).size;
    const ler = Math.min(tam, 66_000);
    const fim = Buffer.alloc(ler);
    fs.readSync(fd, fim, 0, ler, tam - ler);
    let eocd = -1;
    for (let i = ler - 22; i >= 0; i--) if (fim.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
    if (eocd < 0) return;
    const total = fim.readUInt16LE(eocd + 10);
    const cdTam = fim.readUInt32LE(eocd + 12);
    const cdOff = fim.readUInt32LE(eocd + 16);
    const cd = Buffer.alloc(cdTam);
    fs.readSync(fd, cd, 0, cdTam, cdOff);
    let p = 0;
    for (let i = 0; i < total && p + 46 <= cd.length; i++) {
      if (cd.readUInt32LE(p) !== 0x02014b50) break;
      const metodo = cd.readUInt16LE(p + 10);
      const tamC = cd.readUInt32LE(p + 20);
      const nLen = cd.readUInt16LE(p + 28), eLen = cd.readUInt16LE(p + 30), cLen = cd.readUInt16LE(p + 32);
      const offset = cd.readUInt32LE(p + 42);
      const nome = cd.toString("utf8", p + 46, p + 46 + nLen);
      if (INTERESSA.test(nome) && !indice.has(nome)) indice.set(nome, { jar, metodo, tam: tamC, offset });
      p += 46 + nLen + eLen + cLen;
    }
  } catch {
    /* jar ilegível: ignora */
  } finally {
    if (fd !== null) fs.closeSync(fd);
  }
}

const g = globalThis as unknown as { __frxJar?: { indice: Map<string, Entrada>; em: number }; __frxIcones?: Map<string, Buffer | null> };

function indice() {
  if (!g.__frxJar || Date.now() - g.__frxJar.em > 30 * 60_000) {
    const ind = new Map<string, Entrada>();
    for (const j of jars()) indexarJar(j, ind);
    g.__frxJar = { indice: ind, em: Date.now() };
    g.__frxIcones = new Map();
  }
  return g.__frxJar.indice;
}

function lerEntrada(nome: string): Buffer | null {
  const e = indice().get(nome);
  if (!e) return null;
  let fd: number | null = null;
  try {
    fd = fs.openSync(e.jar, "r");
    const cab = Buffer.alloc(30);
    fs.readSync(fd, cab, 0, 30, e.offset);
    if (cab.readUInt32LE(0) !== 0x04034b50) return null;
    const ini = e.offset + 30 + cab.readUInt16LE(26) + cab.readUInt16LE(28);
    const dados = Buffer.alloc(e.tam);
    fs.readSync(fd, dados, 0, e.tam, ini);
    if (e.metodo === 0) return dados;
    if (e.metodo === 8) return zlib.inflateRawSync(dados);
    return null;
  } catch {
    return null;
  } finally {
    if (fd !== null) fs.closeSync(fd);
  }
}

function lerJson(nome: string): Record<string, unknown> | null {
  const b = lerEntrada(nome);
  if (!b) return null;
  try { return JSON.parse(b.toString("utf8")); } catch { return null; }
}

/** "minecraft:item/iron_sword" ou "item/x" → caminho dentro do jar. */
function caminhoTextura(ref: string, nsPadrao: string) {
  const [ns, p] = ref.includes(":") ? ref.split(":") : [nsPadrao, ref];
  return `assets/${ns}/textures/${p}.png`;
}

/** Textura de um modelo (segue os "parent" e as referências #nome). */
function texturaDoModelo(nomeModelo: string, nsPadrao: string, profundidade = 0): string | null {
  if (profundidade > 5) return null;
  const [ns, p] = nomeModelo.includes(":") ? nomeModelo.split(":") : [nsPadrao, nomeModelo];
  const m = lerJson(`assets/${ns}/models/${p}.json`);
  if (!m) return null;
  const tex = (m.textures ?? {}) as Record<string, string>;
  const resolver = (v: string | undefined, n = 0): string | undefined => (v && v.startsWith("#") && n < 5 ? resolver(tex[v.slice(1)], n + 1) : v);
  for (const k of ["layer0", "all", "side", "front", "top", "texture", "cross", "end", "plant", "particle"]) {
    const v = resolver(tex[k]);
    if (v && !v.startsWith("#")) return caminhoTextura(v, ns);
  }
  if (typeof m.parent === "string" && !m.parent.endsWith("generated") && !m.parent.endsWith("handheld")) {
    return texturaDoModelo(m.parent, ns, profundidade + 1);
  }
  return null;
}

/** PNG do ícone de um item (ex.: "minecraft:iron_sword"). */
export function iconeItem(id: string): Buffer | null {
  indice();
  const cache = g.__frxIcones!;
  if (cache.has(id)) return cache.get(id)!;
  const [ns, nome] = id.includes(":") ? id.split(":") : ["minecraft", id];
  const tentativas: (string | null)[] = [
    texturaDoModelo(`${ns}:item/${nome}`, ns),
    `assets/${ns}/textures/item/${nome}.png`,
    `assets/${ns}/textures/block/${nome}.png`,
    `assets/${ns}/textures/block/${nome}_side.png`,
    `assets/${ns}/textures/block/${nome}_front.png`,
    `assets/${ns}/textures/block/${nome}_top.png`,
  ];
  let png: Buffer | null = null;
  for (const t of tentativas) {
    if (!t) continue;
    png = lerEntrada(t);
    if (png) break;
  }
  cache.set(id, png);
  return png;
}

/** Textura por caminho (só armaduras e skins). Ex.: "minecraft/textures/models/armor/iron_layer_1.png". */
export function textura(caminho: string): Buffer | null {
  if (!/^[a-z0-9_.-]+\/textures\/(models\/armor|entity\/player)\/[a-z0-9_./-]+\.png$/.test(caminho)
    && !/^minecraft\/textures\/item\/spawn_egg(_overlay)?\.png$/.test(caminho)) return null;
  return lerEntrada(`assets/${caminho}`);
}

const gl = globalThis as unknown as { __frxLang?: Map<string, Record<string, string>> };

/** O pt_br do Minecraft não vem no jar: fica nos assets do launcher (.minecraft/assets/objects). */
function ptBrMinecraft(): Record<string, string> {
  try {
    const appdata = process.env.APPDATA ?? path.join(os.homedir(), "AppData", "Roaming");
    const base = process.env.MC_ASSETS_DIR ?? path.join(appdata, ".minecraft", "assets");
    const dir = path.join(base, "indexes");
    const indices = fs.readdirSync(dir).filter((n) => n.endsWith(".json"))
      .map((n) => ({ n, t: fs.statSync(path.join(dir, n)).mtimeMs })).sort((a, b) => b.t - a.t);
    for (const { n } of indices) {
      const idx = JSON.parse(fs.readFileSync(path.join(dir, n), "utf8")) as { objects?: Record<string, { hash: string }> };
      const h = idx.objects?.["minecraft/lang/pt_br.json"]?.hash;
      if (h) return JSON.parse(fs.readFileSync(path.join(base, "objects", h.slice(0, 2), h), "utf8"));
    }
  } catch { /* sem launcher nesta máquina */ }
  return {};
}

function idioma(ns: string, lingua: "pt_br" | "en_us"): Record<string, string> {
  indice();
  const cache = (gl.__frxLang ??= new Map());
  const k = `${ns}/${lingua}`;
  if (!cache.has(k)) {
    const doJar = (lerJson(`assets/${ns}/lang/${lingua}.json`) as Record<string, string>) ?? {};
    cache.set(k, ns === "minecraft" && lingua === "pt_br" ? { ...ptBrMinecraft(), ...doJar } : doJar);
  }
  return cache.get(k)!;
}

/** Tradução de uma chave (ex.: entity.alexsmobs.crow) pelos arquivos de idioma do próprio mod: pt_br, senão en_us. */
export function traduzir(chave: string): string | null {
  const ns = chave.split(".")[1] ?? "minecraft";
  return idioma(ns, "pt_br")[chave] ?? idioma(ns, "en_us")[chave] ?? null;
}
