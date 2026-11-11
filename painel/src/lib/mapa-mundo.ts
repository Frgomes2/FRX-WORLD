import "server-only";
import fs from "fs";
import path from "path";

function pastaServidor() {
  return path.resolve(process.cwd(), process.env.SERVIDOR_DIR ?? "../server");
}

function nomeMundo(pasta: string) {
  const arq = path.join(pasta, "server.properties");
  const props = fs.existsSync(arq) ? fs.readFileSync(arq, "utf8") : "";
  return props.match(/^level-name=(.*)$/m)?.[1]?.trim() || "world";
}

export type Mapa = {
  regioes: { rx: number; rz: number; b: string }[]; // b = 128 bytes em base64, 1 bit por chunk (x + z*32)
  chunks: number;
  tamanhoMb: number;
  porDimensao: Record<string, { chunks: number; mb: number }>;
  /** Imagens do JourneyMap (o mapa que o jogo desenha), por camada: "day" = superfície, números = cavernas. */
  jm: { pasta: string; camadas: string[]; tiles: Record<string, [number, number][]>; atualizadoEm: number } | null;
};

/** Lê o cabeçalho de cada arquivo de região (.mca): cada entrada não vazia = um chunk já gerado. */
function lerRegioes(dir: string, comBits: boolean) {
  const regioes: Mapa["regioes"] = [];
  let chunks = 0, bytes = 0;
  if (!fs.existsSync(dir)) return { regioes, chunks, bytes };
  const cab = Buffer.alloc(4096);
  for (const nome of fs.readdirSync(dir)) {
    const m = nome.match(/^r\.(-?\d+)\.(-?\d+)\.mca$/);
    if (!m) continue;
    const arq = path.join(dir, nome);
    let tam = 0;
    try { tam = fs.statSync(arq).size; } catch { continue; }
    if (tam < 8192) continue;
    bytes += tam;
    let fd: number | null = null;
    try {
      fd = fs.openSync(arq, "r");
      fs.readSync(fd, cab, 0, 4096, 0);
    } catch { continue; } finally { if (fd !== null) fs.closeSync(fd); }
    const bits = Buffer.alloc(128);
    let n = 0;
    for (let i = 0; i < 1024; i++) {
      if (cab.readUInt32BE(i * 4) !== 0) { n++; bits[i >> 3] |= 1 << (i & 7); }
    }
    if (!n) continue;
    chunks += n;
    if (comBits) regioes.push({ rx: Number(m[1]), rz: Number(m[2]), b: bits.toString("base64") });
  }
  return { regioes, chunks, bytes };
}

const g = globalThis as unknown as { __frxMapa?: { em: number; mapa: Mapa } };

/** Mapa do que já foi explorado (cache de 90 s: ler centenas de regiões leva um tempinho). */
export function mapaExplorado(): Mapa {
  if (g.__frxMapa && Date.now() - g.__frxMapa.em < 90_000) return g.__frxMapa.mapa;
  const pasta = pastaServidor();
  const mundo = path.join(pasta, nomeMundo(pasta));
  const sobre = lerRegioes(path.join(mundo, "region"), true);
  const nether = lerRegioes(path.join(mundo, "DIM-1", "region"), false);
  const end = lerRegioes(path.join(mundo, "DIM1", "region"), false);
  const mb = (b: number) => Math.round(b / 1048576);
  const mapa: Mapa = {
    jm: lerJourneyMap(),
    regioes: sobre.regioes,
    chunks: sobre.chunks,
    tamanhoMb: mb(sobre.bytes + nether.bytes + end.bytes),
    porDimensao: {
      overworld: { chunks: sobre.chunks, mb: mb(sobre.bytes) },
      the_nether: { chunks: nether.chunks, mb: mb(nether.bytes) },
      the_end: { chunks: end.chunks, mb: mb(end.bytes) },
    },
  };
  g.__frxMapa = { em: Date.now(), mapa };
  return mapa;
}

/** Pasta do JourneyMap do mundo atual: a de dados modificada por último. */
export function pastaJourneyMap(): string | null {
  const raiz = process.env.JOURNEYMAP_DIR
    ? path.resolve(process.cwd(), process.env.JOURNEYMAP_DIR)
    : path.resolve(pastaServidor(), "..", "cliente", "jogo", "journeymap", "data");
  let melhor: { dir: string; t: number } | null = null;
  for (const tipo of ["mp", "sp"]) {
    const base = path.join(raiz, tipo);
    if (!fs.existsSync(base)) continue;
    for (const nome of fs.readdirSync(base)) {
      const dir = path.join(base, nome, "overworld");
      try {
        const t = fs.statSync(dir).mtimeMs;
        if (!melhor || t > melhor.t) melhor = { dir, t };
      } catch { /* sem overworld */ }
    }
  }
  return melhor?.dir ?? null;
}

function lerJourneyMap(): Mapa["jm"] {
  const dir = pastaJourneyMap();
  if (!dir) return null;
  const tiles: Record<string, [number, number][]> = {};
  let atualizadoEm = 0;
  for (const camada of fs.readdirSync(dir)) {
    if (!/^(day|night|topo|-?\d+)$/.test(camada)) continue;
    const lista: [number, number][] = [];
    for (const arq of fs.readdirSync(path.join(dir, camada))) {
      const m = arq.match(/^(-?\d+),(-?\d+)\.png$/);
      if (!m) continue;
      lista.push([Number(m[1]), Number(m[2])]);
      try { atualizadoEm = Math.max(atualizadoEm, fs.statSync(path.join(dir, camada, arq)).mtimeMs); } catch { /* */ }
    }
    if (lista.length) tiles[camada] = lista;
  }
  const camadas = Object.keys(tiles).sort((a, b) => (a === "day" ? -1 : b === "day" ? 1 : Number(b) - Number(a)));
  return camadas.length ? { pasta: path.basename(path.dirname(dir)), camadas, tiles, atualizadoEm } : null;
}

/** Quantos mods tem o servidor e o pack dos jogadores. */
export function contarMods() {
  const pasta = pastaServidor();
  const contar = (dir: string, re: RegExp) => (fs.existsSync(dir) ? fs.readdirSync(dir).filter((n) => re.test(n)).length : 0);
  return {
    servidor: contar(path.join(pasta, "mods"), /\.jar$/i),
    pack: contar(path.resolve(pasta, "..", "pack", "mods"), /\.pw\.toml$/i),
  };
}

export type EventoFeed = { hora: string; tipo: "entrou" | "saiu" | "morte" | "conquista" | "chefe" | "invasao" | "forja" | "chat" | "outro"; texto: string };

/** Últimos acontecimentos lidos do console (ou do latest.log se o servidor foi ligado fora do painel). */
export function feed(linhasConsole: string[], max = 25): EventoFeed[] {
  let linhas = linhasConsole;
  if (linhas.length < 5) {
    const arq = path.join(pastaServidor(), "logs", "latest.log");
    try {
      const tam = fs.statSync(arq).size;
      const ler = Math.min(tam, 300_000);
      const fd = fs.openSync(arq, "r");
      const buf = Buffer.alloc(ler);
      fs.readSync(fd, buf, 0, ler, tam - ler);
      fs.closeSync(fd);
      linhas = buf.toString("utf8").split(/\r?\n/);
    } catch { linhas = []; }
  }
  const saida: EventoFeed[] = [];
  for (let i = linhas.length - 1; i >= 0 && saida.length < max; i--) {
    const l = linhas[i];
    if (!/\[(minecraft\/)?MinecraftServer\]|\/MinecraftServer\//.test(l)) continue;
    const hora = l.match(/(\d{2}:\d{2}):\d{2}/)?.[1] ?? "";
    let texto = l.replace(/^.*?\]: /, "").replace(/§./g, "").trim();
    if (!texto || /Can't keep up|moved too quickly|Set own game mode|Teleported|issued server command/i.test(texto)) continue;
    texto = texto.replace(/^\[[A-Z]{1,3} \S{1,2} Nv \d+\] /, "").replace(/^\[\[[A-Z]{1,3} \S{1,2} Nv \d+\] /, "[");
    let tipo: EventoFeed["tipo"] = "outro";
    if (/joined the game/.test(texto)) { tipo = "entrou"; texto = texto.replace("joined the game", "entrou no jogo"); }
    else if (/left the game/.test(texto)) { tipo = "saiu"; texto = texto.replace("left the game", "saiu do jogo"); }
    else if (/has made the advancement|has completed the challenge|conquista|🏆/i.test(texto)) {
      tipo = "conquista";
      texto = texto.replace("has made the advancement", "conquistou").replace("has completed the challenge", "completou o desafio");
    }
    else if (/☠|chefe mundial/i.test(texto)) tipo = "chefe";
    else if (/invas|horda|onda/i.test(texto)) tipo = "invasao";
    else if (/forjou|⚒/.test(texto)) tipo = "forja";
    else if (/^<[^>]+>/.test(texto)) tipo = "chat";
    else if (/was slain|was shot|died|drowned|burned|blew up|fell|was killed|hit the ground|tried to swim|starved|suffocated|was blown|withered|was pricked|went up in flames|was fireballed|was impaled/.test(texto)) tipo = "morte";
    else continue;
    if (tipo === "morte") texto = traduzirMorte(texto);
    saida.push({ hora, tipo, texto });
  }
  return saida;
}

const MORTES: [RegExp, string][] = [
  [/ was slain by /, " foi morto por "],
  [/ was shot by /, " levou uma flechada de "],
  [/ was blown up by /, " foi explodido por "],
  [/ was killed by /, " foi morto por "],
  [/ was fireballed by /, " levou uma bola de fogo de "],
  [/ was impaled by /, " foi empalado por "],
  [/ was pricked to death/, " morreu espetado"],
  [/ hit the ground too hard/, " caiu com força demais"],
  [/ fell from a high place/, " caiu de um lugar alto"],
  [/ tried to swim in lava/, " tentou nadar na lava"],
  [/ went up in flames/, " pegou fogo"],
  [/ burned to death/, " morreu queimado"],
  [/ drowned/, " se afogou"],
  [/ starved to death/, " morreu de fome"],
  [/ suffocated in a wall/, " sufocou numa parede"],
  [/ blew up/, " explodiu"],
  [/ withered away/, " definhou"],
  [/ died/, " morreu"],
  [/ whilst fighting /, " lutando contra "],
  [/ using /, " usando "],
];

function traduzirMorte(t: string) {
  for (const [re, pt] of MORTES) t = t.replace(re, pt);
  return t;
}
