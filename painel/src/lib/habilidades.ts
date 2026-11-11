/** Classes, tipos e habilidades sugeridas (aba Habilidades). */

export const CLASSES = {
  GUERREIRO: { nome: "Guerreiro", recurso: "Fúria", estilo: "Corpo a corpo, vida e armadura altas. A fúria enche ao bater e apanhar." },
  ARQUEIRO: { nome: "Arqueiro", recurso: "Energia", estilo: "Dano à distância e mobilidade. Energia baixa que volta rápido." },
  MAGO: { nome: "Mago", recurso: "Mana", estilo: "Magia de dano em área. A mana cresce com o nível." },
  CLERIGO: { nome: "Clérigo", recurso: "Mana", estilo: "Cura e magia sagrada, forte contra mortos-vivos." },
  LADINO: { nome: "Ladino", recurso: "Energia", estilo: "Furtividade e golpes pelas costas (dano dobrado)." },
  ASSASSINO: { nome: "Assassino", recurso: "Energia", estilo: "Dano explosivo: habilidades de alvo +50% pelas costas e +50% em alvos com menos de 35% de vida." },
  INVOCADOR: { nome: "Invocador", recurso: "Mana", estilo: "Invoca criaturas aliadas temporárias." },
} as const;
export type Classe = keyof typeof CLASSES;

export const TIPOS = {
  SKILLSHOT: "Linha reta, pode errar (alcance)",
  ALVO: "Trava no que está na mira (alcance). Dano negativo = cura num aliado ou em você",
  AREA: "Área (raio) no ponto mirado (alcance) ou em volta de você (alcance 0). Dano negativo cura aliados",
  MOBILIDADE: "Avanço (alcance; negativo = para trás). Arcano/Sombra = teleporte. Dano fere quem estiver no caminho",
  BUFF: "Efeito em você (efeito, potência, duração); raio > 0 inclui aliados perto",
  INVOCAR: "Invoca o mob do campo efeito (quantidade = potência) por duração segundos",
} as const;
export type Tipo = keyof typeof TIPOS;

export const ELEMENTOS = {
  FISICO: "Físico", FOGO: "Fogo (queima)", GELO: "Gelo (lentidão)", RAIO: "Raio (relâmpago)",
  SAGRADO: "Sagrado (×2 em mortos-vivos)", SOMBRA: "Sombra (definhar)", NATUREZA: "Natureza (veneno)", ARCANO: "Arcano",
} as const;

type H = {
  codigo: string; nome: string; descricao: string; classe: Classe; tipo: Tipo; elemento: keyof typeof ELEMENTOS; icone: string;
  custo: number; cooldown: number; dano: number; alcance: number; raio: number; duracao: number; efeito: string; potencia: number;
  nivelReq: number; ultimate: boolean; aprendizado: "NIVEL" | "ITEM";
};

const h = (p: Partial<H> & Pick<H, "codigo" | "nome" | "classe" | "tipo">): H => {
  const icone = p.icone ?? "book";
  return {
    descricao: "", elemento: "FISICO", custo: 20, cooldown: 5, dano: 0, alcance: 0, raio: 0, duracao: 0,
    efeito: "", potencia: 0, nivelReq: 1, ultimate: false, aprendizado: "NIVEL", ...p,
    icone: icone.includes(":") ? icone : `minecraft:${icone}`,
  };
};

export const SUGESTOES_HABILIDADES: H[] = [
  // Guerreiro (fúria)
  h({ codigo: "golpe_brutal", nome: "Golpe Brutal", classe: "GUERREIRO", tipo: "ALVO", icone: "iron_axe", custo: 20, cooldown: 6, dano: 10, alcance: 4, descricao: "Um golpe pesado no inimigo à frente." }),
  h({ codigo: "investida", nome: "Investida", classe: "GUERREIRO", tipo: "MOBILIDADE", icone: "leather_boots", custo: 15, cooldown: 10, dano: 6, alcance: 8, nivelReq: 3, descricao: "Avança e derruba quem estiver no caminho." }),
  h({ codigo: "grito_de_guerra", nome: "Grito de Guerra", classe: "GUERREIRO", tipo: "BUFF", icone: "goat_horn", custo: 30, cooldown: 30, efeito: "minecraft:strength", potencia: 1, duracao: 10, raio: 8, nivelReq: 5, descricao: "Você e aliados próximos ficam mais fortes." }),
  h({ codigo: "terremoto", nome: "Terremoto", classe: "GUERREIRO", tipo: "AREA", icone: "cobblestone", custo: 40, cooldown: 15, dano: 8, raio: 5, efeito: "minecraft:slowness", potencia: 1, duracao: 3, nivelReq: 10, descricao: "Golpeia o chão e atrasa todos em volta." }),
  h({ codigo: "redemoinho", nome: "Redemoinho de Aço", classe: "GUERREIRO", tipo: "AREA", icone: "iron_sword", custo: 35, cooldown: 12, dano: 12, raio: 4, nivelReq: 12, aprendizado: "ITEM", descricao: "Gira a arma acertando tudo em volta." }),
  h({ codigo: "furia_imortal", nome: "Fúria Imortal", classe: "GUERREIRO", tipo: "BUFF", icone: "totem_of_undying", custo: 80, cooldown: 120, efeito: "minecraft:resistance", potencia: 2, duracao: 10, nivelReq: 20, ultimate: true, descricao: "Por alguns segundos quase nada te machuca." }),
  // Arqueiro (energia)
  h({ codigo: "tiro_preciso", nome: "Tiro Preciso", classe: "ARQUEIRO", tipo: "SKILLSHOT", icone: "arrow", custo: 25, cooldown: 4, dano: 9, alcance: 30, descricao: "Uma flecha certeira em linha reta." }),
  h({ codigo: "recuo", nome: "Recuo", classe: "ARQUEIRO", tipo: "MOBILIDADE", icone: "feather", custo: 20, cooldown: 8, alcance: -7, nivelReq: 3, descricao: "Salta para trás, abrindo distância." }),
  h({ codigo: "chuva_de_flechas", nome: "Chuva de Flechas", classe: "ARQUEIRO", tipo: "AREA", icone: "spectral_arrow", custo: 40, cooldown: 12, dano: 6, alcance: 25, raio: 4, nivelReq: 6, descricao: "Flechas caem no ponto mirado." }),
  h({ codigo: "olho_de_aguia", nome: "Olho de Águia", classe: "ARQUEIRO", tipo: "BUFF", icone: "spyglass", custo: 30, cooldown: 25, efeito: "minecraft:speed", potencia: 1, duracao: 12, nivelReq: 10, descricao: "Fica mais rápido por um tempo." }),
  h({ codigo: "flecha_perfurante", nome: "Flecha Perfurante", classe: "ARQUEIRO", tipo: "SKILLSHOT", icone: "tipped_arrow", custo: 35, cooldown: 8, dano: 14, alcance: 40, nivelReq: 12, aprendizado: "ITEM", descricao: "Um disparo muito forte e de longe." }),
  h({ codigo: "tempestade_de_flechas", nome: "Tempestade de Flechas", classe: "ARQUEIRO", tipo: "AREA", icone: "crossbow", custo: 80, cooldown: 100, dano: 12, alcance: 30, raio: 7, nivelReq: 20, ultimate: true, descricao: "Cobre uma área enorme de flechas." }),
  // Mago (mana)
  h({ codigo: "bola_de_fogo", nome: "Bola de Fogo", classe: "MAGO", tipo: "SKILLSHOT", elemento: "FOGO", icone: "fire_charge", custo: 20, cooldown: 3, dano: 8, alcance: 24, descricao: "Clássica. Queima o alvo." }),
  h({ codigo: "lanca_de_gelo", nome: "Lança de Gelo", classe: "MAGO", tipo: "SKILLSHOT", elemento: "GELO", icone: "ice", custo: 25, cooldown: 6, dano: 7, alcance: 24, nivelReq: 3, descricao: "Fere e deixa o alvo lento." }),
  h({ codigo: "teleporte", nome: "Teleporte", classe: "MAGO", tipo: "MOBILIDADE", elemento: "ARCANO", icone: "ender_pearl", custo: 30, cooldown: 12, alcance: 8, nivelReq: 5, descricao: "Some e reaparece alguns blocos à frente." }),
  h({ codigo: "meteoro", nome: "Meteoro", classe: "MAGO", tipo: "AREA", elemento: "FOGO", icone: "magma_block", custo: 50, cooldown: 14, dano: 14, alcance: 30, raio: 4, nivelReq: 10, descricao: "Uma rocha em chamas cai no ponto mirado." }),
  h({ codigo: "raio_arcano", nome: "Raio", classe: "MAGO", tipo: "ALVO", elemento: "RAIO", icone: "lightning_rod", custo: 40, cooldown: 10, dano: 12, alcance: 20, nivelReq: 12, aprendizado: "ITEM", descricao: "Um relâmpago no alvo da mira." }),
  h({ codigo: "chuva_de_meteoros", nome: "Chuva de Meteoros", classe: "MAGO", tipo: "AREA", elemento: "FOGO", icone: "fire_coral_block", custo: 100, cooldown: 120, dano: 24, alcance: 30, raio: 7, nivelReq: 20, ultimate: true, descricao: "O céu desaba em fogo." }),
  // Clérigo (mana)
  h({ codigo: "luz_sagrada", nome: "Luz Sagrada", classe: "CLERIGO", tipo: "SKILLSHOT", elemento: "SAGRADO", icone: "glowstone_dust", custo: 20, cooldown: 3, dano: 7, alcance: 24, descricao: "Raio de luz; dobra contra mortos-vivos." }),
  h({ codigo: "cura", nome: "Cura", classe: "CLERIGO", tipo: "ALVO", elemento: "SAGRADO", icone: "golden_apple", custo: 25, cooldown: 6, dano: -8, alcance: 16, descricao: "Cura o aliado na mira, ou você." }),
  h({ codigo: "escudo_divino", nome: "Escudo Divino", classe: "CLERIGO", tipo: "BUFF", elemento: "SAGRADO", icone: "shield", custo: 30, cooldown: 20, efeito: "minecraft:absorption", potencia: 1, duracao: 15, raio: 8, nivelReq: 5, descricao: "Corações extras para você e aliados perto." }),
  h({ codigo: "consagrar", nome: "Consagrar", classe: "CLERIGO", tipo: "AREA", elemento: "SAGRADO", icone: "sunflower", custo: 40, cooldown: 14, dano: 8, raio: 5, nivelReq: 10, descricao: "O chão em volta queima os inimigos." }),
  h({ codigo: "prece_coletiva", nome: "Prece Coletiva", classe: "CLERIGO", tipo: "AREA", elemento: "SAGRADO", icone: "beacon", custo: 45, cooldown: 25, dano: -10, raio: 8, nivelReq: 12, aprendizado: "ITEM", descricao: "Cura todos os aliados em volta." }),
  h({ codigo: "julgamento", nome: "Julgamento", classe: "CLERIGO", tipo: "AREA", elemento: "SAGRADO", icone: "nether_star", custo: 100, cooldown: 110, dano: 20, raio: 8, nivelReq: 20, ultimate: true, descricao: "A luz julga todos os inimigos em volta." }),
  // Ladino (energia)
  h({ codigo: "punhalada", nome: "Punhalada", classe: "LADINO", tipo: "ALVO", icone: "iron_sword", custo: 25, cooldown: 4, dano: 9, alcance: 4, descricao: "Dano dobrado se acertar pelas costas." }),
  h({ codigo: "sumir", nome: "Sumir", classe: "LADINO", tipo: "BUFF", elemento: "SOMBRA", icone: "black_dye", custo: 40, cooldown: 25, efeito: "minecraft:invisibility", potencia: 0, duracao: 8, nivelReq: 3, descricao: "Fica invisível por alguns segundos." }),
  h({ codigo: "passo_sombrio", nome: "Passo Sombrio", classe: "LADINO", tipo: "MOBILIDADE", elemento: "SOMBRA", icone: "ink_sac", custo: 30, cooldown: 10, alcance: 10, nivelReq: 5, descricao: "Teleporta pelas sombras." }),
  h({ codigo: "lamina_envenenada", nome: "Lâmina Envenenada", classe: "LADINO", tipo: "ALVO", elemento: "NATUREZA", icone: "spider_eye", custo: 30, cooldown: 10, dano: 4, alcance: 4, efeito: "minecraft:poison", potencia: 1, duracao: 6, nivelReq: 8, descricao: "Envenena o alvo." }),
  h({ codigo: "leque_de_laminas", nome: "Leque de Lâminas", classe: "LADINO", tipo: "AREA", icone: "shears", custo: 35, cooldown: 10, dano: 8, raio: 4, nivelReq: 12, aprendizado: "ITEM", descricao: "Lâminas para todos os lados." }),
  h({ codigo: "execucao", nome: "Execução", classe: "LADINO", tipo: "ALVO", elemento: "SOMBRA", icone: "wither_skeleton_skull", custo: 80, cooldown: 90, dano: 30, alcance: 6, nivelReq: 20, ultimate: true, descricao: "Um golpe mortal no alvo da mira." }),
  // Assassino (energia) — habilidades de ALVO: +50% pelas costas e +50% em alvo com < 35% de vida
  h({ codigo: "golpe_mortal", nome: "Golpe Mortal", classe: "ASSASSINO", tipo: "ALVO", icone: "iron_sword", custo: 20, cooldown: 3, dano: 9, alcance: 4, descricao: "Corte rápido e preciso. Brilha contra alvos feridos." }),
  h({ codigo: "investida_sombria", nome: "Investida Sombria", classe: "ASSASSINO", tipo: "MOBILIDADE", elemento: "SOMBRA", icone: "ender_eye", custo: 25, cooldown: 8, dano: 5, alcance: 10, nivelReq: 3, descricao: "Some nas sombras e reaparece à frente." }),
  h({ codigo: "corte_hemorragico", nome: "Corte Hemorrágico", classe: "ASSASSINO", tipo: "ALVO", icone: "redstone", custo: 25, cooldown: 8, dano: 6, alcance: 4, efeito: "minecraft:wither", potencia: 1, duracao: 6, nivelReq: 5, descricao: "Abre uma ferida que sangra por alguns segundos." }),
  h({ codigo: "marca_da_morte", nome: "Marca da Morte", classe: "ASSASSINO", tipo: "ALVO", elemento: "SOMBRA", icone: "wither_rose", custo: 30, cooldown: 14, dano: 3, alcance: 12, efeito: "minecraft:weakness", potencia: 1, duracao: 10, nivelReq: 8, descricao: "Marca o alvo: ele fica fraco e mais fácil de executar." }),
  h({ codigo: "frenesi_de_laminas", nome: "Frenesi de Lâminas", classe: "ASSASSINO", tipo: "BUFF", icone: "blaze_powder", custo: 40, cooldown: 30, efeito: "minecraft:strength", potencia: 1, duracao: 10, nivelReq: 10, descricao: "Fica mais forte por alguns segundos." }),
  h({ codigo: "danca_das_adagas", nome: "Dança das Adagas", classe: "ASSASSINO", tipo: "AREA", icone: "shears", custo: 35, cooldown: 10, dano: 9, raio: 3.5, nivelReq: 12, aprendizado: "ITEM", descricao: "Gira cortando todos em volta." }),
  h({ codigo: "mil_cortes", nome: "Mil Cortes", classe: "ASSASSINO", tipo: "ALVO", elemento: "SOMBRA", icone: "netherite_sword", custo: 90, cooldown: 100, dano: 34, alcance: 8, nivelReq: 20, ultimate: true, descricao: "Uma rajada de golpes no alvo da mira. Quase sempre fatal num alvo ferido." }),
  // Invocador (mana)
  h({ codigo: "dardo_arcano", nome: "Dardo Arcano", classe: "INVOCADOR", tipo: "SKILLSHOT", elemento: "ARCANO", icone: "amethyst_shard", custo: 15, cooldown: 2, dano: 6, alcance: 24, descricao: "Um projétil mágico rápido." }),
  h({ codigo: "matilha", nome: "Matilha", classe: "INVOCADOR", tipo: "INVOCAR", icone: "bone", custo: 35, cooldown: 25, efeito: "minecraft:wolf", potencia: 2, duracao: 30, descricao: "Invoca 2 lobos que lutam por você." }),
  h({ codigo: "elo_vital", nome: "Elo Vital", classe: "INVOCADOR", tipo: "BUFF", elemento: "NATUREZA", icone: "glistering_melon_slice", custo: 30, cooldown: 20, efeito: "minecraft:regeneration", potencia: 1, duracao: 10, raio: 8, nivelReq: 5, descricao: "Regeneração para você e aliados perto." }),
  h({ codigo: "golem", nome: "Golem de Ferro", classe: "INVOCADOR", tipo: "INVOCAR", icone: "iron_block", custo: 60, cooldown: 60, efeito: "minecraft:iron_golem", potencia: 1, duracao: 40, nivelReq: 8, descricao: "Um golem protege a área." }),
  h({ codigo: "legiao_de_neve", nome: "Legião de Neve", classe: "INVOCADOR", tipo: "INVOCAR", icone: "snowball", custo: 40, cooldown: 30, efeito: "minecraft:snow_golem", potencia: 3, duracao: 30, nivelReq: 12, aprendizado: "ITEM", descricao: "Três bonecos de neve atiram nos inimigos." }),
  h({ codigo: "guardiao_ancestral", nome: "Guardiões Ancestrais", classe: "INVOCADOR", tipo: "INVOCAR", icone: "carved_pumpkin", custo: 100, cooldown: 150, efeito: "minecraft:iron_golem", potencia: 3, duracao: 45, nivelReq: 20, ultimate: true, descricao: "Três golens ao seu lado." }),
];
