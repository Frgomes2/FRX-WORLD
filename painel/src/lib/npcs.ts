/** Tipos de NPC que podem ter skin própria (mesmos papéis e nomes de reinos/NpcsReino.java). */

export const PAPEIS = [
  { id: "rei", nome: "Rei / Soberano", icone: "👑" },
  { id: "ferreiro", nome: "Ferreiro", icone: "⚒️" },
  { id: "armeiro", nome: "Armeiro", icone: "🛡️" },
  { id: "mercador", nome: "Mercador", icone: "💰" },
  { id: "comprador", nome: "Comprador", icone: "⚖️" },
  { id: "cambista", nome: "Cambista", icone: "💱" },
  { id: "banqueiro", nome: "Banqueiro", icone: "🏦" },
  { id: "sacerdote", nome: "Sacerdote", icone: "✨" },
  { id: "sabio", nome: "Sábio", icone: "📜" },
  { id: "cartografo", nome: "Cartógrafo", icone: "🗺️" },
  { id: "taverneiro", nome: "Taverneiro", icone: "🍺" },
  { id: "cozinheiro", nome: "Cozinheiro", icone: "🍖" },
  { id: "recepcionista", nome: "Recepcionista da Guilda", icone: "📋" },
  { id: "guarda", nome: "Guarda", icone: "⚔️" },
  { id: "morador", nome: "Moradores", icone: "🏘️" },
] as const;

export type Papel = (typeof PAPEIS)[number]["id"];

/** Reino → raça dos moradores (reinos/Racas.java). */
export const RACA_DO_REINO: Record<string, string> = {
  tempest: "hobgoblin", dwargon: "anao", sarion: "elfo", leste: "oriental", "leste-sakura": "oriental",
  oeste: "humano", lubelius: "sagrado", demonios: "demonio",
};

/** Nomes dos NPCs em cada reino. */
export const NOMES_NPC: Record<string, Partial<Record<Papel, string>>> = {
  tempest: { rei: "Lorde Rimuru Tempest", ferreiro: "Kaijin, Mestre Ferreiro", armeiro: "Kurobe, Forjador", mercador: "Myourmile, Mercador", sacerdote: "Shuna, Sacerdotisa", sabio: "Rigurd, Conselheiro", cartografo: "Gobta, Explorador", taverneiro: "Taverneiro de Tempest", comprador: "Gobzo, Comprador de Saques", guarda: "Guarda de Tempest" },
  dwargon: { rei: "Rei Gazel Dwargo", ferreiro: "Mestre Ferreiro Anão", armeiro: "Armeiro Real", mercador: "Mercador da Montanha", sacerdote: "Guardião das Runas", sabio: "Ancião de Dwargon", cartografo: "Mapeador dos Túneis", taverneiro: "Taverneiro Barba-de-Ferro", comprador: "Cambista Anão", guarda: "Cavaleiro Pégaso" },
  sarion: { rei: "Imperatriz Elmesia El-Ru Sarion", ferreiro: "Ferreiro Élfico", armeiro: "Tecelã de Mithril", mercador: "Mercador Arcano", sacerdote: "Sacerdotisa da Lua", sabio: "Arquimago de Sarion", cartografo: "Navegante das Ilhas", taverneiro: "Taverneira Élfica", comprador: "Avaliador Élfico", guarda: "Guarda da Dinastia" },
  leste: { rei: "Imperador Ludora", ferreiro: "Mestre Forjador Imperial", armeiro: "Armeiro Imperial", mercador: "Mercador da Seda", sacerdote: "Monge do Templo", sabio: "Estrategista Imperial", cartografo: "Cartógrafo do Império", taverneiro: "Dono da Casa de Chá", comprador: "Comprador Imperial", guarda: "Soldado Imperial" },
  "leste-sakura": { rei: "Senhora das Cerejeiras", ferreiro: "Ferreiro de Katanas", armeiro: "Armeiro Samurai", mercador: "Mercadora de Chá", sacerdote: "Sacerdotisa do Santuário", sabio: "Mestre do Jardim", cartografo: "Viajante do Leste", taverneiro: "Dona da Casa de Saquê", comprador: "Mercador de Relíquias", guarda: "Samurai do Distrito" },
  oeste: { rei: "Rei Edmaris de Farmus", ferreiro: "Garm, Ferreiro de Blumund", armeiro: "Dold, Artesão de Armaduras", mercador: "Mercador da Rota Ocidental", sacerdote: "Padre da Igreja Ocidental", sabio: "Fuze, Mestre da Guilda", cartografo: "Kabal, Aventureiro Veterano", taverneiro: "Taverna do Javali Dourado", comprador: "Elen, Compradora de Relíquias", banqueiro: "Banco de Blumund", guarda: "Cavaleiro de Farmus" },
  lubelius: { rei: "Luminus Valentine, Deusa de Lubelius", ferreiro: "Ferreiro dos Cavaleiros Sagrados", armeiro: "Armeiro do Templo", mercador: "Mercador da Catedral", sacerdote: "Sumo Sacerdote Louis", sabio: "Hinata Sakaguchi, Capitã Sagrada", cartografo: "Peregrino Sagrado", taverneiro: "Hospedaria do Peregrino", comprador: "Tesoureiro da Igreja", banqueiro: "Cofre Sagrado", guarda: "Cavaleiro Sagrado" },
  demonios: { rei: "Guy Crimson, Lorde Demônio Primordial", ferreiro: "Forjador do Abismo", armeiro: "Armeiro Infernal", mercador: "Mercador de Almas", sacerdote: "Sacerdote das Sombras", sabio: "Misery, Serva Escarlate", cartografo: "Batedor das Cinzas", taverneiro: "Taverna do Caldeirão", comprador: "Agiota Demoníaco", banqueiro: "Cofre dos Lordes", guarda: "Arquidemônio de Guarda" },
};

export const chavePadrao = (papel: string) => `papel:${papel}`;
export const chaveReino = (reino: string, papel: string) => `reino:${reino}:${papel}`;
export const CHAVE_VALIDA = /^(papel:[a-z]+|reino:[a-z-]+:[a-z]+)$/;
