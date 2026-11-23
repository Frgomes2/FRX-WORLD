import { personagensGuardados, porPersonagem } from "@/lib/personagens";
import "server-only";
import fs from "fs";
import path from "path";
import zlib from "zlib";
import { prisma } from "@/lib/prisma";
import { servidorMc } from "@/lib/servidor-mc";
import { statusAtual, usoCpu } from "@/lib/status-servidor";
import { contarMods, feed, mapaExplorado, type EventoFeed } from "@/lib/mapa-mundo";
import { reinosDoServidor } from "@/lib/reinos-servidor";
import { CLASSES } from "@/lib/habilidades";
import { lerClas } from "@/lib/clas";
import { conferirMural, type FeitoPublico } from "@/lib/feitos";
import { evolucao, historicoDe, registrarMarcos, ultimasCronicas, type CronicaPublica, type Marco } from "@/lib/cronicas";
import { GOLPES, LOCAIS, pngDaSkin } from "@/lib/criaturas-codex";
import { pngDaTextura } from "@/lib/codex";

/**
 * Tudo o que o site público (/) mostra. Regra: nada de e-mail, UUID, IP, endereço do servidor,
 * conversa do chat nem a posição dos jogadores no mapa.
 */
/** nick = nome do personagem (o que aparece); conta + vaga dizem de quem é e servem para a skin e para abrir a ficha (vaga 0 = conta sem personagens registrados). */
export type LinhaRank = { nick: string; conta: string; vaga: number; valor: number; extra: string; titulo: string;
  /** só em "Mais fortes": níveis ganhos em 7 dias e posições ganhas (+) ou perdidas (−) desde ontem */ semana?: number; mudou?: number };
export type DadosPublicos = {
  agora: number;
  online: boolean;
  status: {
    tps: number; maxJogadores: number; dia: number; hora: number; clima: "limpo" | "chuva" | "tempestade";
    versaoMod: string; chefe: { fase: "aviso" | "ativo"; nome: string } | null;
    /** Memória do servidor do jogo, em MB, e uso do processador da máquina (0 a 100; null enquanto mede). */
    memUsadaMb: number; memMaxMb: number; cpu: number | null;
  } | null;
  onlineAgora: { nick: string; nivel: number; classe: string; reino: string }[];
  totais: { jogadores: number; mortes: number; conquistas: number; nivelMedio: number; chunks: number; mundoMb: number; mods: number };
  rankings: { fortes: LinhaRank[]; fracos: LinhaRank[]; ricos: LinhaRank[]; mortes: LinhaRank[]; conquistas: LinhaRank[] };
  guilda: Record<string, number>;
  classes: Record<string, number>;
  torre: { nome: string; recorde: number; abates: number }[];
  temporada: { mes: string; linhas: { nome: string; pts: number; abates: number; torre: number; historia: number }[] } | null;
  /** Torneio de pesca da semana (frx_pesca.json): melhor peixe de cada um, pela porcentagem da faixa da espécie. */
  pesca: { fim: number; linhas: { nome: string; especie: string; peso: number; pct: number; qtd: number }[]; campeao: { semana: string; nome: string; especie: string; peso: number } | null };
  /** Pistas de corrida (frx_corridas.json) com os melhores tempos. */
  corridas: { id: string; nome: string; tipo: string; pontos: number; voltas: number; feitas: number; recordes: { nome: string; ms: number }[]; voltaRapida: { nome: string; ms: number } | null }[];
  reinos: string[];
  feed: EventoFeed[];
  aventureiros: { nick: string; conta: string; vaga: number; nivel: number; rank: string; classe: string; titulo: string; online: boolean }[];
  clas: ClaPublico[];
  /** Mural dos Primeiros: quem alcançou primeiro cada marco do servidor. */
  mural: FeitoPublico[];
  /** Crônicas: acontecimentos guardados pelo painel (os 60 mais recentes). */
  cronicas: CronicaPublica[];
  /** Mural das armas únicas do Códex: quem recebeu a primeira de cada uma. */
  armas: ArmaPublica[];
};
export type ArmaPublica = {
  codigo: string; nome: string; descricao: string; raridade: number; limite: number; emitidas: number;
  textura: string | null; primeiro: string; conta: string; vaga: number; quando: number | null;
  /** Nível de uso (0 = item sem nível ou ainda no 0; -1 = item que não ganha nível). */
  nivel: number;
};
/** Clã (do Mestre dos Clãs). riqueza = cofre do clã + bolsa e banco de todos; poder = soma dos níveis. */
export type ClaPublico = {
  nome: string; descricao: string; cor: string; lider: string; riqueza: number; cofre: number; poder: number; nivelMedio: number; conquistas: number;
  membros: { nick: string; nivel: number; rank: string; cargo: "dono" | "oficial" | "membro"; online: boolean }[];
};
export type PerfilPublico = {
  /** Personagens da conta (até 3); o resto da ficha é do que está em uso. */
  personagens: { vaga: number; nome: string; classe: string; raca: string; nivel: number; rank: string; emUso: boolean; temFicha: boolean }[];
  /** Nome do personagem desta ficha e se é o que está em uso na conta. */
  personagem: string; personagemEmUso: boolean;
  /** Últimos acontecimentos deste personagem. */
  historico: CronicaPublica[];
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
  clas: { nome: string; cor: string; cargo: string }[];
  colecao: { criaturas: number; total: number; proximoMarco: number; chefes: string[] } | null;
};
/** Criatura do Códex no bestiário público. Enquanto ninguém a derrotou, só saem o tipo, a faixa de rank e onde aparece. */
export type CriaturaPublica = {
  descoberto: boolean; tipo: "chefe" | "monstro"; rankMin: number; rankMax: number; locais: string[];
  codigo: string; nome: string; descricao: string; derrotas: number; skin: boolean; slim: boolean; golpes: { nome: string; icone: string; desc: string }[];
  /** Quem derrotou primeiro e quando ("" = derrotada antes de existir o registro). */
  primeiro: string; primeiroEm: number | null;
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
  __frxBestiario?: { em: number; lista: CriaturaPublica[] };
};

/** Cache de 10 s: mil visitantes não viram mil consultas ao banco. */
export async function dadosPublicos(): Promise<DadosPublicos> {
  if (g.__frxPublico && Date.now() - g.__frxPublico.em < 10_000) return g.__frxPublico.dados;

  const todos = await prisma.jogador.findMany({
    where: { ativo: true },
    select: { uuid: true, nick: true, nivel: true, rank: true, classe: true, titulo: true, moedas: true, banco: true, mortes: true, conquistas: true, online: true },
  });
  // Uma linha por PERSONAGEM (não por conta). O que está em uso leva os números ao vivo da conta; os outros, o retrato guardado na última vez em que jogaram.
  // O banco é da conta: vale para todos os personagens dela.
  const fichas = porPersonagem(todos, await personagensGuardados());
  type Ficha = (typeof fichas)[number];
  const linha = (j: Ficha, valor: number, extra: string): LinhaRank => ({ nick: j.nick, conta: j.conta, vaga: j.vaga, valor, extra, titulo: j.titulo });
  const perfil = (j: Ficha) => `Rank ${j.rank} · ${nomeClasse(j.classe)}`;
  const porNivel = [...fichas].sort((a, b) => b.nivel - a.nivel || b.conquistas - a.conquistas);

  const guilda: Record<string, number> = {};
  const classes: Record<string, number> = {};
  for (const j of fichas) {
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

  const pe = lerJson<{ fim?: number; linhas?: Record<string, { nome?: string; especie?: string; peso?: number; pct?: number; qtd?: number }>; campeoes?: { semana: string; nome: string; especie: string; peso: number }[] }>(path.join(mundo, "frx_pesca.json"));
  const pesca = {
    fim: pe?.fim ?? 0,
    linhas: Object.values(pe?.linhas ?? {}).map((l) => ({ nome: l.nome ?? "?", especie: l.especie ?? "?", peso: l.peso ?? 0, pct: l.pct ?? 0, qtd: l.qtd ?? 0 }))
      .sort((a, b) => b.pct - a.pct).slice(0, 10),
    campeao: pe?.campeoes?.length ? pe.campeoes[pe.campeoes.length - 1] : null,
  };
  const co = lerJson<{ pistas?: Record<string, { nome?: string; tipo?: string; pontos?: unknown[]; voltas?: number; feitas?: number; recordes?: { nome?: string; ms?: number }[]; voltaRapida?: { nome?: string; ms?: number } }> }>(path.join(mundo, "frx_corridas.json"));
  const corridas = Object.entries(co?.pistas ?? {}).filter(([, p]) => (p.pontos?.length ?? 0) >= 2).map(([id, p]) => ({
    id, nome: p.nome ?? id, tipo: p.tipo ?? "pe", pontos: Math.max(0, (p.pontos?.length ?? 1) - 1), voltas: p.voltas ?? 1, feitas: p.feitas ?? 0,
    voltaRapida: p.voltaRapida?.ms ? { nome: p.voltaRapida.nome ?? "?", ms: p.voltaRapida.ms } : null,
    recordes: (p.recordes ?? []).map((r) => ({ nome: r.nome ?? "?", ms: r.ms ?? 0 })).slice(0, 5),
  }));

  let reinos: string[] = [];
  try { reinos = reinosDoServidor().reinos.map((r) => r.nome); } catch { /* */ }
  let eventos: EventoFeed[] = [];
  try { eventos = feed(servidorMc.linhas, 40).filter((e) => e.tipo !== "chat" && e.tipo !== "outro").slice(0, 20); } catch { /* */ }

  // online de verdade = quem o servidor acabou de informar (a marca do banco pode ficar presa depois de um reinício)
  const noJogo = new Set((st?.jogadores ?? []).map((j) => j.nick.toLowerCase()));
  // clãs (frx_clas.json) com pelo menos um membro conhecido do painel
  const porUuid = new Map(todos.map((j) => [j.uuid, j]));
  // personagens das vagas 2 e 3 entram nos clãs com identidade própria: apontam para a conta dona
  try {
    const contas = new Map(porUuid);
    for (const p of await prisma.personagem.findMany() as { uuid: string; vaga: number; idJogo: string; nome: string; nivel: number; rank: string; emUso: boolean }[]) {
      const dono = contas.get(p.uuid);
      if (!dono) continue;
      // a linha da conta guarda os números de quem está em uso; para os outros valem os do retrato
      porUuid.set(p.vaga <= 1 ? p.uuid : p.idJogo || `${p.uuid}#${p.vaga}`, { ...dono, nick: p.nome || dono.nick, ...(p.emUso ? {} : { nivel: p.nivel, rank: p.rank || dono.rank }) });
    }
  } catch { /* tabela ainda não criada */ }
  const clas: ClaPublico[] = [];
  try {
    for (const c of lerClas(mundo)) {
      const membros = c.membros.flatMap((m) => { const j = porUuid.get(m.uuid); return j ? [{ j, cargo: m.cargo }] : []; });
      if (!membros.length) continue;
      const poder = membros.reduce((s, m) => s + m.j.nivel, 0);
      clas.push({
        nome: c.nome, descricao: c.descricao, cor: c.cor, lider: porUuid.get(c.dono)?.nick ?? membros[0].j.nick,
        riqueza: c.cofre + membros.reduce((s, m) => s + m.j.moedas + m.j.banco, 0), cofre: c.cofre, poder, nivelMedio: Math.round(poder / membros.length),
        conquistas: membros.reduce((s, m) => s + m.j.conquistas, 0),
        membros: membros.sort((a, b) => b.j.nivel - a.j.nivel)
          .map((m) => ({ nick: m.j.nick, nivel: m.j.nivel, rank: m.j.rank, cargo: m.cargo, online: noJogo.has(m.j.nick.toLowerCase()) })),
      });
    }
  } catch { /* nenhum clã ainda */ }
  clas.sort((a, b) => b.poder - a.poder);

  // o placar inteiro (não só os 10 primeiros) e todos os personagens entram na conferência do mural
  const mural = await conferirMural(
    porNivel.map((j) => ({ nome: j.nick, conta: j.conta, vaga: j.vaga, nivel: j.nivel, rank: j.rank, conquistas: j.conquistas })),
    Object.values(placar).map((p) => ({ nome: p.nome ?? "?", recorde: p.recorde ?? 0 })).sort((a, b) => b.recorde - a.recorde),
    Object.fromEntries(Object.entries(lerJson<Record<string, { primeiro?: { nome?: string } }>>(path.join(mundo, "frx_chegadas.json")) ?? {})
      .map(([area, v]) => [area, v.primeiro?.nome ?? ""])),
  );

  // crônicas: marcos com chave única (só grava o que é novo) e a foto diária do ranking
  const marcos: Marco[] = [];
  for (const t of Object.values(placar)) {
    const nome = t.nome ?? "", ate = Math.floor((t.recorde ?? 0) / 5) * 5;
    const dono = porNivel.find((j) => j.nick.toLowerCase() === nome.toLowerCase());
    for (let andar = 5; nome && andar <= ate; andar += 5) {
      marcos.push({ chave: `torre:${nome.toLowerCase()}:${andar}`, tipo: "torre", icone: "🗼", texto: andar >= 100 ? "zerou a Torre Infinita" : `chegou ao andar ${andar} da Torre`,
        nome, conta: dono?.conta ?? "", vaga: dono?.vaga ?? 0, destaque: andar % 25 === 0 });
    }
  }
  for (const m of mural) {
    if (m.feito && m.nome && m.quando) marcos.push({ chave: `mural:${m.chave}`, tipo: "primeiro", icone: m.icone, texto: `entrou para o Mural: ${m.titulo}`, nome: m.nome, conta: m.conta, vaga: m.vaga, destaque: true });
  }
  try {
    for (const c of await prisma.criaturaCodex.findMany({ where: { ativa: true, derrotas: { gt: 0 } }, select: { codigo: true, nome: true } })) {
      marcos.push({ chave: `codex:${c.codigo}`, tipo: "descoberta", icone: "☠", texto: `Nova descoberta no bestiário: ${c.nome} foi derrotado pela primeira vez`, destaque: true });
    }
  } catch { /* */ }
  const armas: ArmaPublica[] = [];
  try {
    type Linha = { codigo: string; nome: string; descricao: string; raridade: number; limite: number; emitidas: number; textura: string; primeiroNome?: string | null; primeiroEm?: Date | null; nivelUso?: boolean; nivelUsoMax?: number };
    const linhas = (await prisma.armaCodex.findMany({ where: { emitidas: { gt: 0 } } })) as unknown as Linha[];
    for (const a of linhas) {
      if (!a.primeiroNome) continue;
      const dono = porNivel.find((j) => j.nick.toLowerCase() === a.primeiroNome!.toLowerCase());
      armas.push({
        codigo: a.codigo, nome: a.nome, descricao: a.descricao, raridade: a.raridade, limite: a.limite, emitidas: a.emitidas,
        textura: pngDaTextura(a.textura) ? `data:image/png;base64,${a.textura.replace(/^data:image\/png;base64,/, "")}` : null,
        primeiro: a.primeiroNome, conta: dono?.conta ?? "", vaga: dono?.vaga ?? 0, quando: a.primeiroEm ? a.primeiroEm.getTime() : null,
        nivel: a.nivelUso ? a.nivelUsoMax ?? 0 : -1,
      });
      marcos.push({ chave: `arma:${a.codigo}`, tipo: "primeiro", icone: "⚔️", texto: `conquistou a primeira ${a.nome}`, nome: a.primeiroNome, conta: dono?.conta ?? "", vaga: dono?.vaga ?? 0, destaque: a.raridade >= 3 });
    }
    armas.sort((x, y) => y.raridade - x.raridade || (y.quando ?? 0) - (x.quando ?? 0));
  } catch { /* tabela ainda sem as colunas novas */ }
  await registrarMarcos(marcos);
  const evo = await evolucao(porNivel.map((j) => ({ conta: j.conta, vaga: j.vaga, nivel: j.nivel })));

  const dados: DadosPublicos = {
    agora: Date.now(),
    online: !!st,
    status: st ? {
      tps: st.tps, maxJogadores: st.maxJogadores, dia: st.dia, hora: st.hora, clima: st.clima, versaoMod: st.versaoMod,
      chefe: st.chefe ? { fase: st.chefe.fase, nome: st.chefe.nome } : null,
      memUsadaMb: Math.round(st.memUsadaMb ?? 0), memMaxMb: Math.round(st.memMaxMb ?? 0), cpu: usoCpu(),
    } : null,
    onlineAgora: (st?.jogadores ?? []).map((j) => ({ nick: j.nick, nivel: j.nivel ?? 0, classe: nomeClasse(j.classe ?? ""), reino: j.reino ?? "" })),
    totais: {
      jogadores: todos.length,
      mortes: fichas.reduce((s, j) => s + j.mortes, 0),
      conquistas: fichas.reduce((s, j) => s + j.conquistas, 0),
      nivelMedio: fichas.length ? Math.round(fichas.reduce((s, j) => s + j.nivel, 0) / fichas.length) : 0,
      chunks, mundoMb, mods,
    },
    rankings: {
      fortes: porNivel.slice(0, 10).map((j) => ({ ...linha(j, j.nivel, perfil(j)), ...(evo.get(`${j.conta}#${j.vaga}`) ?? {}) })),
      fracos: [...porNivel].reverse().slice(0, 5).map((j) => linha(j, j.nivel, perfil(j))),
      ricos: [...fichas].sort((a, b) => b.moedas + b.banco - (a.moedas + a.banco)).slice(0, 10).map((j) => linha(j, j.moedas + j.banco, perfil(j))),
      mortes: fichas.filter((j) => j.mortes > 0).sort((a, b) => b.mortes - a.mortes).slice(0, 10).map((j) => linha(j, j.mortes, perfil(j))),
      conquistas: fichas.filter((j) => j.conquistas > 0).sort((a, b) => b.conquistas - a.conquistas).slice(0, 10).map((j) => linha(j, j.conquistas, perfil(j))),
    },
    guilda, classes, torre, temporada, pesca, corridas, reinos, feed: eventos,
    aventureiros: porNivel.map((j) => ({ nick: j.nick, conta: j.conta, vaga: j.vaga, nivel: j.nivel, rank: j.rank, classe: nomeClasse(j.classe), titulo: j.titulo, online: j.emUso && noJogo.has(j.conta.toLowerCase()) })),
    clas, mural, cronicas: await ultimasCronicas(), armas,
  };
  g.__frxPublico = { em: Date.now(), dados };
  return dados;
}

/** Bestiário do site: criaturas ativas do Códex. As não derrotadas não revelam nome, história, golpes nem aparência. Cache de 30 s. */
export async function bestiarioPublico(): Promise<CriaturaPublica[]> {
  if (g.__frxBestiario && Date.now() - g.__frxBestiario.em < 30_000) return g.__frxBestiario.lista;
  const todas = await prisma.criaturaCodex.findMany({ where: { ativa: true }, orderBy: [{ rankMin: "asc" }, { rankMax: "asc" }, { nome: "asc" }] });
  const lista = todas.map((c): CriaturaPublica => {
    const descoberto = c.derrotas > 0;
    const locais = c.locais.split(",").map((l) => LOCAIS.find((x) => x.id === l)?.nome).filter((x): x is string => !!x);
    const tipo = c.tipo === "monstro" ? "monstro" : "chefe";
    if (!descoberto) return { descoberto, tipo, rankMin: c.rankMin, rankMax: c.rankMax, locais, codigo: "", nome: "", descricao: "", derrotas: 0, skin: false, slim: false, golpes: [], primeiro: "", primeiroEm: null };
    return {
      descoberto, tipo, rankMin: c.rankMin, rankMax: c.rankMax, locais, codigo: c.codigo, nome: c.nome, descricao: c.descricao, derrotas: c.derrotas,
      skin: !!pngDaSkin(c.skin), slim: c.slim,
      primeiro: (c as unknown as { primeiroNome?: string }).primeiroNome ?? "",
      primeiroEm: (c as unknown as { primeiroEm?: Date | null }).primeiroEm?.getTime() ?? null,
      golpes: c.golpes.split(",").map((id) => GOLPES.find((x) => x.id === id)).filter((x): x is (typeof GOLPES)[number] => !!x).map((x) => ({ nome: x.nome, icone: x.icone, desc: x.desc })),
    };
  });
  g.__frxBestiario = { em: Date.now(), lista };
  return lista;
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

/** Mapa público: as imagens da superfície, o spawn, a Torre e as capitais dos reinos (decisão de 18/11; PUBLICO_MAPA_REINOS=0 no .env esconde os reinos). */
export function mapaPublico(): MapaPublico {
  let tiles: [number, number][] = [], atualizadoEm = 0;
  try { const jm = mapaExplorado().jm; tiles = jm?.tiles.day ?? []; atualizadoEm = jm?.atualizadoEm ?? 0; } catch { /* */ }
  const spawn = spawnDoMundo();
  let reinos: MapaPublico["reinos"] = [];
  try {
    const r = reinosDoServidor();
    if (process.env.PUBLICO_MAPA_REINOS !== "0") reinos = r.reinos.map((x) => ({ nome: x.nome, x: x.x, z: x.z }));
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
export async function perfilPublico(nick: string, vagaPedida = 0): Promise<PerfilPublico | null> {
  // aceita o nick da conta ou o nome de um personagem (letras, números e espaços)
  if (!NICK.test(nick) && !/^[\p{L}\p{N}][\p{L}\p{N} ]{1,14}[\p{L}\p{N}]$/u.test(nick)) return null;
  const perfis = (g.__frxPerfis ??= new Map());
  const chave = `${nick.toLowerCase()}#${vagaPedida}`;
  const c = perfis.get(chave);
  if (c && Date.now() - c.em < 20_000) return c.perfil;
  type Guardado = { vaga: number; idJogo: string; nome: string; classe: string; raca: string; nivel: number; rank: string; emUso: boolean; base: unknown; detalhe: unknown };
  let conta = NICK.test(nick) ? await prisma.jogador.findFirst({ where: { nick: { equals: nick, mode: "insensitive" }, ativo: true } }) : null;
  let vaga = vagaPedida;
  if (!conta) {   // não é nick de conta: procura um personagem com esse nome
    const achado = await prisma.personagem.findFirst({ where: { nome: { equals: nick, mode: "insensitive" } } }).catch(() => null);
    if (achado) { conta = await prisma.jogador.findFirst({ where: { uuid: achado.uuid, ativo: true } }); if (!vaga) vaga = achado.vaga; }
  }
  const personagens = conta ? ((await prisma.personagem.findMany({ where: { uuid: conta.uuid }, orderBy: { vaga: "asc" } }).catch(() => [])) as unknown as Guardado[]) : [];
  const emUso = personagens.find((x) => x.emUso) ?? null;
  // personagem fora de uso com retrato guardado: a ficha inteira passa a ser a dele (o banco continua sendo o da conta)
  const outro = personagens.find((x) => x.vaga === vaga && !x.emUso && x.base) ?? null;
  const j = conta && outro ? ({ ...conta, ...(outro.base as object), detalhe: outro.detalhe } as unknown as NonNullable<typeof conta>) : conta;
  let perfil: PerfilPublico | null = null;
  if (j) {
    const dele = outro ?? emUso;
    // placar, temporada e clãs são de cada personagem (na vaga 1 a identidade é o próprio UUID da conta)
    const idJogo = dele?.idJogo || j.uuid;
    const contaUuid = j.uuid;
    type Hab = { nome?: string; descricao?: string; nivel?: number; usos?: number; ultimate?: boolean; aprendida?: boolean };
    const d = (j.detalhe ?? {}) as {
      habilidades?: Hab[]; unica?: { nome?: string; descricao?: string; nivel?: number };
      raca?: { principal?: string; secundaria?: string; nivel?: string };
      tempoJogoMin?: number; mobsMortos?: number; blocosAndados?: number;
      colecao?: { criaturas?: number; totalCriaturas?: number; proximoMarco?: number; chefes?: string[] };
    };
    const cls = (CLASSES as Record<string, { nome: string; estilo: string; recurso: string }>)[j.classe];
    const indice = Math.max(0, RANKS.indexOf(j.rank));
    const mundo = pastaMundo();
    const placar = lerJson<Record<string, { recorde?: number; abates?: number }>>(path.join(mundo, "frx_torre_placar.json")) ?? {};
    const temp = lerJson<{ pontos?: Record<string, { pts?: number }> }>(path.join(mundo, "frx_temporada.json"));
    const pontos = Object.entries(temp?.pontos ?? {}).sort((a, b) => (b[1].pts ?? 0) - (a[1].pts ?? 0));
    const pos = pontos.findIndex(([u]) => u === idJogo);
    const st = statusAtual().dados;
    const meusClas = lerClas(mundo).flatMap((c) => {
      const eu = c.membros.find((m) => m.uuid === idJogo);
      return eu ? [{ nome: c.nome, cor: c.cor, cargo: eu.cargo === "dono" ? "líder" : eu.cargo }] : [];
    });
    perfil = {
      personagens: personagens.map((p) => ({ vaga: p.vaga, nome: p.nome || j.nick, classe: p.classe, raca: p.raca, nivel: p.nivel, rank: p.rank, emUso: p.emUso, temFicha: p.emUso || !!p.base })),
      personagem: dele?.nome || j.nick, personagemEmUso: !outro,
      historico: await historicoDe(j.nick, dele?.vaga ?? 0),
      nick: j.nick, titulo: j.titulo, nivel: j.nivel, xp: j.xp, rank: j.rank, classe: nomeClasse(j.classe), estilo: cls?.estilo ?? "", recurso: cls?.recurso ?? "",
      online: !outro && !!st && st.jogadores.some((x) => x.uuid === contaUuid),
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
      torre: placar[idJogo] ? { recorde: placar[idJogo].recorde ?? 0, abates: placar[idJogo].abates ?? 0 } : null,
      temporada: pos >= 0 ? { pts: pontos[pos][1].pts ?? 0, posicao: pos + 1 } : null,
      clas: meusClas,
      colecao: d.colecao && typeof d.colecao.criaturas === "number"
        ? { criaturas: d.colecao.criaturas, total: d.colecao.totalCriaturas ?? 0, proximoMarco: d.colecao.proximoMarco ?? 0, chefes: (d.colecao.chefes ?? []).filter((x) => typeof x === "string").slice(0, 40) }
        : null,
    };
  }
  perfis.set(chave, { em: Date.now(), perfil });
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
