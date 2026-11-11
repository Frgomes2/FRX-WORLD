/** Capitais com rei, reputação e campanha (ids iguais aos do mod). */
export const REINOS = [
  { id: "tempest", nome: "Tempest", campanha: "A Federação em Perigo" },
  { id: "dwargon", nome: "Dwargon", campanha: "O Juramento da Montanha" },
  { id: "sarion", nome: "Sarion", campanha: "A Profecia Élfica" },
  { id: "leste", nome: "Império do Leste", campanha: "A Vontade do Império" },
  { id: "leste-sakura", nome: "Sakura", campanha: "O Florescer de Sakura" },
  { id: "oeste", nome: "Oeste", campanha: "A Coroa do Oeste" },
  { id: "lubelius", nome: "Lubelius", campanha: "A Luz de Luminus" },
  { id: "demonios", nome: "Lordes Demônios", campanha: "O Banquete dos Lordes" },
] as const;

/** Mesmos limites de reinos/Reputacao.java. */
export const NIVEIS_REPUTACAO = [
  { nome: "Neutro", minimo: 0, desconto: 0, cor: "text-pedra-500" },
  { nome: "Amigável", minimo: 100, desconto: 3, cor: "text-green-400" },
  { nome: "Honrado", minimo: 500, desconto: 6, cor: "text-cyan-300" },
  { nome: "Reverenciado", minimo: 1500, desconto: 10, cor: "text-blue-400" },
  { nome: "Exaltado", minimo: 4000, desconto: 15, cor: "text-purple-300" },
  { nome: "Herói", minimo: 10000, desconto: 20, cor: "text-ouro" },
] as const;

export function nivelReputacao(pontos: number) {
  let n: (typeof NIVEIS_REPUTACAO)[number] = NIVEIS_REPUTACAO[0];
  for (const x of NIVEIS_REPUTACAO) if (pontos >= x.minimo) n = x;
  return n;
}

export type Campanha = { nome: string; etapa: number; total: number; progresso?: number; qtd?: number; etapaTitulo?: string };

/** Aparência de cada reino no painel. */
export const VISUAL_REINO: Record<string, { cor: string; emblema: string; raca: string; terreno: string }> = {
  tempest: { cor: "#4fb3ff", emblema: "🌀", raca: "Hobgoblins e monstros", terreno: "Floresta de Jura" },
  dwargon: { cor: "#d98b3a", emblema: "⚒️", raca: "Anões", terreno: "Montanha (subterrâneo)" },
  sarion: { cor: "#7ee0c3", emblema: "🔮", raca: "Elfos", terreno: "Ilhas e torres arcanas" },
  leste: { cor: "#e0504a", emblema: "🏯", raca: "Povo oriental", terreno: "Planícies imperiais" },
  "leste-sakura": { cor: "#ff9ccf", emblema: "🌸", raca: "Povo oriental", terreno: "Bosque de cerejeiras" },
  oeste: { cor: "#c9a64a", emblema: "🛡️", raca: "Humanos", terreno: "Campos de Farmus" },
  lubelius: { cor: "#f4f1d0", emblema: "✨", raca: "Fiéis sagrados", terreno: "Catedral sagrada" },
  demonios: { cor: "#a24bd6", emblema: "😈", raca: "Demônios", terreno: "Terras de cinzas" },
};

/** Próxima faixa de reputação e quanto falta (null = já é Herói). */
export function proximoNivel(pontos: number) {
  const prox = NIVEIS_REPUTACAO.find((n) => n.minimo > pontos);
  if (!prox) return null;
  const atual = nivelReputacao(pontos);
  return { prox, frac: (pontos - atual.minimo) / (prox.minimo - atual.minimo) };
}
