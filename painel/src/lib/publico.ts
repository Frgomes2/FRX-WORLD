import "server-only";
import fs from "fs";
import path from "path";
import zlib from "zlib";
import { prisma } from "@/lib/prisma";
import { servidorMc } from "@/lib/servidor-mc";
import { statusAtual } from "@/lib/status-servidor";
import { contarMods, feed, mapaExplorado, type EventoFeed } from "@/lib/mapa-mundo";
import { reinosDoServidor } from "@/lib/reinos-servidor";
import { CLASSES } from "@/lib/habilidades";

/**
 * Tudo o que o site público (/) mostra. Regra: nada de e-mail, UUID, IP, endereço do servidor,
 * conversa do chat nem a posição dos jogadores no mapa.
 */
export type LinhaRank = { nick: string; valor: number; extra: string; titulo: string };
export type DadosPublicos = {
  agora: number;
  online: boolean;
  status: {
    tps: number; maxJogadores: number; dia: number; hora: number; clima: "limpo" | "chuva" | "tempestade";
    versaoMod: string; chefe: { fase: "aviso" | "ativo"; nome: string } | null;
  } | null;
  onlineAgora: { nick: string; nivel: number; classe: string; reino: string }[];
  totais: { jogadores: number; mortes: number; conquistas: number; nivelMedio: number; chunks: number; mundoMb: number; mods: number };
  rankings: { fortes: LinhaRank[]; fracos: LinhaRank[]; ricos: LinhaRank[]; mortes: LinhaRank[]; conquistas: LinhaRank[] };
  guilda: Record<string, number>;
  classes: Record<string, number>;
  torre: { nome: string; recorde: number; abates: number }[];
  temporada: { mes: string; linhas: { nome: string; pts: number; abates: number; torre: number; historia: number }[] } | null;
  reinos: string[];
  feed: EventoFeed[];
  aventureiros: { nick: string; nivel: number; rank: string; classe: string; titulo: string; online: boolean }[];
};
export type PerfilPublico = {
  nick: string; titulo: string; nivel: number; xp: number; rank: string; classe: string; estilo: string; recurso: string; online: boolean;
  mortes: number; conquistas: number; riqueza: number; desde: string; vistoEm: string | null;
  guilda: { indice: number; bonusMissao: number; desconto: number; proximo: string | null };
  raca: { principal: string; secundaria: string; nivel: string } | null;
  unica: { nome: string; descricao: string; nivel: number } | null;
  habilidades: { nome: string; descricao: string; nivel: number; usos: number; ultimate: boolean }[];
  totalHabilidades: number;
  stats: { tempoJogoMin: number; mobsMortos: number; blocosAndados: number } | null;
  reputacao: { reino: string; pontos: number }[];
  torre: { recorde: number; abates: number } | null;
  temporada: { pts: number; posicao: number } | null;
};
export type ModPublico = { nome: string; lado: string; link: string | null };
export type MapaPublico = {
  tiles: [number, number][]; atualizadoEm: number; spawn: [number, number] | null; torre: [number, number] | null;
  reinos: { nome: string; x: number; z: number }[];
};

function pastaServidor() {
  return path.resolve(process.cwd(), process.env.SERVIDOR_DIR ?? "../server");
}
function pastaMundo() {
  const pasta = pastaServidor();
  let props = "";
  try { props = fs.readFileSync(path.join(pasta, "server.properties"), "utf8"); } catch { /* sem arquivo */ }
  return path.join(pasta, props.match(/^level-name=(.*)$/m)?.[1]?.trim() || "world");
}
function lerJson<T>(arq: string): T | null {
  try { return JSON.parse(fs.readFileSync(arq, "utf8")) as T; } catch { return null; }
}
function nomeClasse(c: string) {
  return (CLASSES as Record<string, { nome: string }>)[c]?.nome ?? (c ? c.charAt(0) + c.slice(1).toLowerCase() : "Sem classe");
}

const g = globalThis as unknown as {
  __frxPerfis?: Map<string, { em: number; perfil: PerfilPublico | null }>;
  __frxSkinsPub?: Map<string, { em: number; png: Uint8Array | null }>;
  __frxPublico?: { em: number; dados: DadosPublicos };
  __frxPublicoMods?: { em: number; mods: ModPublico[] };
};

/** Cache de 10 s: mil visitantes não viram mil consultas ao banco. */
export async function dadosPublicos(): Promise<DadosPublicos> {
  if (g.__frxPublico && Date.now() - g.__frxPublico.em < 10_000) return g.__frxPublico.dados;

  const todos = await prisma.jogador.findMany({
    where: { ativo: true },
    select: { nick: true, nivel: true, rank: true, classe: true, titulo: true, moedas: true, banco: true, mortes: true, conquistas: true, online: true },
  });
  const linha = (j: (typeof todos)[number], valor: number, extra: string): LinhaRank => ({ nick: j.nick, valor, extra, titulo: j.titulo });
  const perfil = (j: (typeof todos)[number]) => `Rank ${j.rank} · ${nomeClasse(j.classe)}`;
  const porNivel = [...todos].sort((a, b) => b.nivel - a.nivel || b.conquistas - a.conquistas);

  const guilda: Record<string, number> = {};
  const classes: Record<string, number> = {};
  for (const j of todos) {
    guilda[j.rank] = (guilda[j.rank] ?? 0) + 1;
    const c = nomeClasse(j.classe);
    classes[c] = (classes[c] ?? 0) + 1;
  }

  const st = statusAtual().dados;
  let chunks = 0, mundoMb = 0;
  try { const m = mapaExplorado(); chunks = m.chunks; mundoMb = m.tamanhoMb; } catch { /* mundo ainda não criado */ }
  let mods = 0;
  try { mods = contarMods().pack; } catch { /* */ }

  const mundo = pastaMundo();
  const placar = lerJson<Record<string, { nome?: string; recorde?: number; abates?: number }>>(path.join(mundo, "frx_torre_placar.json")) ?? {};
  const torre = Object.values(placar)
    .map((p) => ({ nome: p.nome ?? "?", recorde: p.recorde ?? 0, abates: p.abates ?? 0 }))
    .filter((p) => p.recorde > 0 || p.abates > 0)
    .sort((a, b) => b.recorde - a.recorde || b.abates - a.abates).slice(0, 10);

  const temp = lerJson<{ mes?: string; pontos?: Record<string, { nome?: string; pts?: number; abates?: number; torre?: number; historia?: number }> }>(path.join(mundo, "frx_temporada.json"));
  const temporada = temp?.mes ? {
    mes: temp.mes,
    linhas: Object.values(temp.pontos ?? {})
      .map((p) => ({ nome: p.nome ?? "?", pts: p.pts ?? 0, abates: p.abates ?? 0, torre: p.torre ?? 0, historia: p.historia ?? 0 }))
      .sort((a, b) => b.pts - a.pts).slice(0, 10),
  } : null;

  let reinos: string[] = [];
  try { reinos = reinosDoServidor().reinos.map((r) => r.nome); } catch { /* */ }
  let eventos: EventoFeed[] = [];
  try { eventos = feed(servidorMc.linhas, 40).filter((e) => e.tipo !== "chat" && e.tipo !== "outro").slice(0, 20); } catch { /* */ }

  // online de verdade = quem o servidor acabou de informar (a marca do banco pode ficar presa depois de um reinício)
  const noJogo = new Set((st?.jogadores ?? []).map((j) => j.nick.toLowerCase()));
  const dados: DadosPublicos = {
    agora: Date.now(),
    online: !!st,
    status: st ? {
      tps: st.tps, maxJogadores: st.maxJogadores, dia: st.dia, hora: st.hora, clima: st.clima, versaoMod: st.versaoMod,
      chefe: st.chefe ? { fase: st.chefe.fase, nome: st.chefe.nome } : null,
    } : null,
    onlineAgora: (st?.jogadores ?? []).map((j) => ({ nick: j.nick, nivel: j.nivel ?? 0, classe: nomeClasse(j.classe ?? ""), reino: j.reino ?? "" })),
    totais: {
      jogadores: todos.length,
      mortes: todos.reduce((s, j) => s + j.mortes, 0),
      conquistas: todos.reduce((s, j) => s + j.conquistas, 0),
      nivelMedio: todos.length ? Math.round(todos.reduce((s, j) => s + j.nivel, 0) / todos.length) : 0,
      chunks, mundoMb, mods,
    },
    rankings: {
      fortes: porNivel.slice(0, 10).map((j) => linha(j, j.nivel, perfil(j))),
      fracos: [...porNivel].reverse().slice(0, 5).map((j) => linha(j, j.nivel, perfil(j))),
      ricos: [...todos].sort((a, b) => b.moedas + b.banco - (a.moedas + a.banco)).slice(0, 10).map((j) => linha(j, j.moedas + j.banco, perfil(j))),
      mortes: todos.filter((j) => j.mortes > 0).sort((a, b) => b.mortes - a.mortes).slice(0, 10).map((j) => linha(j, j.mortes, perfil(j))),
      conquistas: todos.filter((j) => j.conquistas > 0).sort((a, b) => b.conquistas - a.conquistas).slice(0, 10).map((j) => linha(j, j.conquistas, perfil(j))),
    },
    guilda, classes, torre, temporada, reinos, feed: eventos,
    aventureiros: porNivel.map((j) => ({ nick: j.nick, nivel: j.nivel, rank: j.rank, classe: nomeClasse(j.classe), titulo: j.titulo, online: noJogo.has(j.nick.toLowerCase()) })),
  };
  g.__frxPublico = { em: Date.now(), dados };
  return dados;
}

/** Mods do pack dos jogadores (pack/mods/*.pw.toml), com link para a página de cada um. Cache de 10 min. */
export function modsDoPack(): ModPublico[] {
  if (g.__frxPublicoMods && Date.now() - g.__frxPublicoMods.em < 600_000) return g.__frxPublicoMods.mods;
  const dir = path.resolve(pastaServidor(), "..", "pack", "mods");
  const mods: ModPublico[] = [];
  try {
    for (const arq of fs.readdirSync(dir)) {
      if (!/\.pw\.toml$/i.test(arq)) continue;
      const t = fs.readFileSync(path.join(dir, arq), "utf8");
      const nome = t.match(/^name\s*=\s*"(.*)"\s*$/m)?.[1] ?? arq.replace(/\.pw\.toml$/i, "");
      const lado = t.match(/^side\s*=\s*"(.*)"\s*$/m)?.[1] ?? "both";
      const mr = t.match(/^mod-id\s*=\s*"([A-Za-z0-9]+)"/m)?.[1];
      const cf = t.match(/^project-id\s*=\s*(\d+)/m)?.[1];
      mods.push({ nome, lado, link: mr ? `https://modrinth.com/mod/${mr}` : cf ? `https://www.curseforge.com/projects/${cf}` : null });
    }
  } catch { /* sem pasta do pack */ }
  mods.sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
  g.__frxPublicoMods = { em: Date.now(), mods };
  return mods;
}

/**
 * Ponto de partida de verdade: o que o servidor informa ou, com ele desligado, o gravado no level.dat
 * (SpawnX/SpawnZ). O "spawn" do posicoes.json é o centro de Tempest, não onde os jogadores nascem.
 */
function spawnDoMundo(): [number, number] | null {
  const vivo = statusAtual().dados?.spawn;
  if (vivo && vivo.length === 2) return [vivo[0], vivo[1]];
  try {
    const d = zlib.gunzipSync(fs.readFileSync(path.join(pastaMundo(), "level.dat")));
    const ler = (nome: string) => {
      const i = d.indexOf(Buffer.concat([Buffer.from([3, 0, nome.length]), Buffer.from(nome)]));   // TAG_Int + tamanho do nome + nome
      return i < 0 ? null : d.readInt32BE(i + 3 + nome.length);
    };
    const x = ler("SpawnX"), z = ler("SpawnZ");
    return x === null || z === null ? null : [x, z];
  } catch { return null; }
}

/** Mapa público: só as imagens da superfície, o spawn e a Torre. Reinos só com PUBLICO_MAPA_REINOS=1 no .env. */
export function mapaPublico(): MapaPublico {
  let tiles: [number, number][] = [], atualizadoEm = 0;
  try { const jm = mapaExplorado().jm; tiles = jm?.tiles.day ?? []; atualizadoEm = jm?.atualizadoEm ?? 0; } catch { /* */ }
  const spawn = spawnDoMundo();
  let reinos: MapaPublico["reinos"] = [];
  try {
    const r = reinosDoServidor();
    if (process.env.PUBLICO_MAPA_REINOS === "1") reinos = r.reinos.map((x) => ({ nome: x.nome, x: x.x, z: x.z }));
  } catch { /* */ }
  let torre: [number, number] | null = null;
  try {
    const p = fs.readFileSync(path.join(pastaMundo(), "frx_torre.txt"), "utf8").trim().split(/\s+/);
    if (p.length >= 3 && !isNaN(Number(p[0])) && !isNaN(Number(p[2]))) torre = [Number(p[0]), Number(p[2])];
  } catch { /* torre ainda não construída */ }
  return { tiles, atualizadoEm, spawn, torre, reinos };
}

const RANKS = ["F", "E", "D", "C", "B", "A", "S"];
const NICK = /^\w{3,16}$/;

/** Ficha pública de um jogador (clique no nome). Sem posição, inventário, baú de ender, UUID nem e-mail. Cache de 20 s. */
export async function perfilPublico(nick: string): Promise<PerfilPublico | null> {
  if (!NICK.test(nick)) return null;
  const perfis = (g.__frxPerfis ??= new Map());
  const c = perfis.get(nick.toLowerCase());
  if (c && Date.now() - c.em < 20_000) return c.perfil;
  const j = await prisma.jogador.findFirst({ where: { nick: { equals: nick, mode: "insensitive" }, ativo: true } });
  let perfil: PerfilPublico | null = null;
  if (j) {
    type Hab = { nome?: string; descricao?: string; nivel?: number; usos?: number; ultimate?: boolean; aprendida?: boolean };
    const d = (j.detalhe ?? {}) as {
      habilidades?: Hab[]; unica?: { nome?: string; descricao?: string; nivel?: number };
      raca?: { principal?: string; secundaria?: string; nivel?: string };
      tempoJogoMin?: number; mobsMortos?: number; blocosAndados?: number;
    };
    const cls = (CLASSES as Record<string, { nome: string; estilo: string; recurso: string }>)[j.classe];
    const indice = Math.max(0, RANKS.indexOf(j.rank));
    const mundo = pastaMundo();
    const placar = lerJson<Record<string, { recorde?: number; abates?: number }>>(path.join(mundo, "frx_torre_placar.json")) ?? {};
    const temp = lerJson<{ pontos?: Record<string, { pts?: number }> }>(path.join(mundo, "frx_temporada.json"));
    const pontos = Object.entries(temp?.pontos ?? {}).sort((a, b) => (b[1].pts ?? 0) - (a[1].pts ?? 0));
    const pos = pontos.findIndex(([u]) => u === j.uuid);
    const st = statusAtual().dados;
    perfil = {
      nick: j.nick, titulo: j.titulo, nivel: j.nivel, xp: j.xp, rank: j.rank, classe: nomeClasse(j.classe), estilo: cls?.estilo ?? "", recurso: cls?.recurso ?? "",
      online: !!st && st.jogadores.some((x) => x.uuid === j.uuid),
      mortes: j.mortes, conquistas: j.conquistas, riqueza: j.moedas + j.banco,
      desde: j.criadoEm.toISOString().slice(0, 10), vistoEm: j.ultimoLogin?.toISOString().slice(0, 10) ?? null,
      guilda: { indice, bonusMissao: indice * 10, desconto: indice * 2, proximo: RANKS[indice + 1] ?? null },
      raca: d.raca?.principal ? { principal: d.raca.principal, secundaria: d.raca.secundaria ?? "", nivel: d.raca.nivel ?? "" } : null,
      unica: d.unica?.nome ? { nome: d.unica.nome, descricao: d.unica.descricao ?? "", nivel: d.unica.nivel ?? 1 } : null,
      habilidades: (d.habilidades ?? []).filter((h) => h.aprendida && h.nome)
        .map((h) => ({ nome: h.nome!, descricao: h.descricao ?? "", nivel: h.nivel ?? 1, usos: h.usos ?? 0, ultimate: !!h.ultimate })),
      totalHabilidades: (d.habilidades ?? []).length,
      stats: typeof d.tempoJogoMin === "number" ? { tempoJogoMin: d.tempoJogoMin, mobsMortos: d.mobsMortos ?? 0, blocosAndados: d.blocosAndados ?? 0 } : null,
      reputacao: Object.entries((j.reputacao ?? {}) as Record<string, number>).filter(([, v]) => typeof v === "number" && v !== 0)
        .map(([reino, pontos]) => ({ reino, pontos })).sort((a, b) => b.pontos - a.pontos),
      torre: placar[j.uuid] ? { recorde: placar[j.uuid].recorde ?? 0, abates: placar[j.uuid].abates ?? 0 } : null,
      temporada: pos >= 0 ? { pts: pontos[pos][1].pts ?? 0, posicao: pos + 1 } : null,
    };
  }
  perfis.set(nick.toLowerCase(), { em: Date.now(), perfil });
  return perfil;
}

/**
 * Skin que o jogador usa no servidor: a que o SkinRestorer gravou em <mundo>/skinrestorer/<uuid>.json.
 * Só baixa de textures.minecraft.net e só para nick que existe no banco. Cache de 1 h. null = sem skin (o site desenha o Steve).
 */
export async function skinPublica(nick: string): Promise<Uint8Array | null> {
  if (!NICK.test(nick)) return null;
  const skins = (g.__frxSkinsPub ??= new Map());
  const chave = nick.toLowerCase();
  const c = skins.get(chave);
  if (c && Date.now() - c.em < (c.png ? 3_600_000 : 300_000)) return c.png;
  let png: Uint8Array | null = null;
  try {
    const j = await prisma.jogador.findFirst({ where: { nick: { equals: nick, mode: "insensitive" }, ativo: true }, select: { uuid: true } });
    const arq = j ? lerJson<{ value?: { value?: string } }>(path.join(pastaMundo(), "skinrestorer", `${j.uuid}.json`)) : null;
    const b64 = arq?.value?.value;
    if (b64) {
      const t = JSON.parse(Buffer.from(b64, "base64").toString("utf8")) as { textures?: { SKIN?: { url?: string } } };
      const url = t.textures?.SKIN?.url?.replace(/^http:/, "https:");
      if (url && /^https:\/\/textures\.minecraft\.net\/texture\/[0-9a-f]+$/.test(url)) {
        const r = await fetch(url, { signal: AbortSignal.timeout(6000) });
        if (r.ok) png = new Uint8Array(await r.arrayBuffer());
      }
    }
  } catch { /* sem internet ou arquivo quebrado */ }
  skins.set(chave, { em: Date.now(), png });
  return png;
}
