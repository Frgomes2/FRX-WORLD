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

/** Mecânicas especiais do mod: vão no campo "efeito" no lugar de um efeito de poção. */
export const EFEITOS_ESPECIAIS = {
  "frx:puxar": "Fisga o alvo da mira e arrasta até você (alcance, dano, duração = lentidão)",
  "frx:provocar": "Monstros no raio passam a te atacar; você ganha Resistência (potência) pela duração",
  "frx:salto": "Salta na direção da mira (alcance) e causa dano em área (raio) ao cair",
  "frx:fenda": "Linha no chão (alcance) que atravessa e levanta todos os inimigos",
  "frx:executar": "Dano cresce até ×3 conforme a vida que falta no alvo; matar zera a recarga",
  "frx:vampirismo": "Pela duração, potência × 10% do dano causado volta como vida",
  "frx:refletir": "Pela duração, potência × 10% do dano recebido volta no agressor",
  "frx:lancar": "Área em volta (raio) que arremessa para o alto; potência = nível de Absorção em você",
  "frx:explosiva": "Tiro em linha (alcance) que explode no impacto (raio)",
  "frx:ricochete": "Acerta o alvo e salta em até potência inimigos dentro do raio (−25% por salto)",
  "frx:marca": "Marca o alvo pela duração: brilha e leva potência × 10% a mais de dano seu",
  "frx:gancho": "Puxa você até o bloco mirado (alcance), sem dano de queda",
  "frx:leque": "Cone à frente (alcance) que acerta e empurra todos",
  "frx:voleio": "Salta para trás e acerta o alvo da mira (alcance)",
  "frx:perfurar": "Linha (alcance) que atravessa todos; dano até ×2 conforme a distância",
  "frx:armadilha": "Armadilha no chão mirado (alcance, raio): prende pela duração quem pisar; dura 45 s, máx. 2",
  "frx:congelar": "Em volta de você (raio): dano e inimigos congelados no lugar pela duração",
  "frx:misseis": "Potência mísseis teleguiados em sequência no alvo (alcance); procuram outro alvo se ele morrer",
  "frx:escudo_mana": "Pela duração, potência × 10% do dano recebido sai do recurso (2 por ponto de dano)",
  "frx:trocar": "Troca de lugar com o alvo da mira (inimigo ou aliado)",
  "frx:sifao": "Dano no alvo e devolve potência × 10 de recurso",
  "frx:zona": "Zona no ponto mirado (alcance, raio) que causa o dano por segundo pela duração; usa o elemento",
  "frx:singularidade": "Buraco negro (alcance, raio): puxa para o centro, dano por segundo e explosão (×2) no fim",
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
  h({ codigo: "arremesso_de_machado", nome: "Arremesso de Machado", classe: "GUERREIRO", tipo: "SKILLSHOT", icone: "stone_axe", custo: 15, cooldown: 7, dano: 8, alcance: 16, efeito: "minecraft:slowness", potencia: 2, duracao: 3, nivelReq: 2, descricao: "Lança o machado em linha reta e deixa o alvo lento." }),
  h({ codigo: "gancho_de_corrente", nome: "Gancho de Corrente", classe: "GUERREIRO", tipo: "ALVO", icone: "chain", custo: 20, cooldown: 12, dano: 4, alcance: 14, efeito: "frx:puxar", duracao: 2, nivelReq: 4, descricao: "Fisga o inimigo da mira e arrasta ele até você." }),
  h({ codigo: "provocar", nome: "Provocar", classe: "GUERREIRO", tipo: "AREA", icone: "bell", custo: 10, cooldown: 18, raio: 9, efeito: "frx:provocar", potencia: 1, duracao: 6, nivelReq: 6, descricao: "Todos os monstros em volta largam os aliados e vêm em você. Você fica mais resistente." }),
  h({ codigo: "salto_esmagador", nome: "Salto Esmagador", classe: "GUERREIRO", tipo: "MOBILIDADE", icone: "anvil", custo: 30, cooldown: 14, dano: 11, alcance: 10, raio: 4, efeito: "frx:salto", nivelReq: 8, descricao: "Salta alto na direção da mira e esmaga o chão ao cair, jogando os inimigos para o ar." }),
  h({ codigo: "postura_de_revide", nome: "Postura de Revide", classe: "GUERREIRO", tipo: "BUFF", icone: "shield", custo: 25, cooldown: 22, efeito: "frx:refletir", potencia: 5, duracao: 6, nivelReq: 9, descricao: "Por alguns segundos, metade do dano que você leva volta em quem bateu." }),
  h({ codigo: "fenda_sismica", nome: "Fenda Sísmica", classe: "GUERREIRO", tipo: "SKILLSHOT", icone: "pointed_dripstone", custo: 35, cooldown: 13, dano: 10, alcance: 12, efeito: "frx:fenda", nivelReq: 11, descricao: "Racha o chão em linha reta: atravessa e levanta todos os inimigos no caminho." }),
  h({ codigo: "executar", nome: "Executar", classe: "GUERREIRO", tipo: "ALVO", icone: "netherite_axe", custo: 40, cooldown: 16, dano: 9, alcance: 4.5, efeito: "frx:executar", nivelReq: 14, descricao: "Quanto menos vida o alvo tem, mais dói (até o triplo). Se matar, a recarga zera e metade da fúria volta." }),
  h({ codigo: "sede_de_sangue", nome: "Sede de Sangue", classe: "GUERREIRO", tipo: "BUFF", icone: "nether_wart", custo: 35, cooldown: 40, efeito: "frx:vampirismo", potencia: 3, duracao: 8, nivelReq: 16, aprendizado: "ITEM", descricao: "Por alguns segundos, 30% de todo dano que você causa volta como vida." }),
  h({ codigo: "ira_do_tita", nome: "Ira do Titã", classe: "GUERREIRO", tipo: "AREA", icone: "nether_star", custo: 90, cooldown: 110, dano: 18, raio: 7, efeito: "frx:lancar", potencia: 4, duracao: 8, nivelReq: 25, ultimate: true, descricao: "Explosão de fúria: arremessa todos em volta para o alto e te cobre de vida extra." }),
  // Arqueiro (energia)
  h({ codigo: "tiro_preciso", nome: "Tiro Preciso", classe: "ARQUEIRO", tipo: "SKILLSHOT", icone: "arrow", custo: 25, cooldown: 4, dano: 9, alcance: 30, descricao: "Uma flecha certeira em linha reta." }),
  h({ codigo: "recuo", nome: "Recuo", classe: "ARQUEIRO", tipo: "MOBILIDADE", icone: "feather", custo: 20, cooldown: 8, alcance: -7, nivelReq: 3, descricao: "Salta para trás, abrindo distância." }),
  h({ codigo: "chuva_de_flechas", nome: "Chuva de Flechas", classe: "ARQUEIRO", tipo: "AREA", icone: "spectral_arrow", custo: 40, cooldown: 12, dano: 6, alcance: 25, raio: 4, nivelReq: 6, descricao: "Flechas caem no ponto mirado." }),
  h({ codigo: "olho_de_aguia", nome: "Olho de Águia", classe: "ARQUEIRO", tipo: "BUFF", icone: "spyglass", custo: 30, cooldown: 25, efeito: "minecraft:speed", potencia: 1, duracao: 12, nivelReq: 10, descricao: "Fica mais rápido por um tempo." }),
  h({ codigo: "flecha_perfurante", nome: "Flecha Perfurante", classe: "ARQUEIRO", tipo: "SKILLSHOT", icone: "tipped_arrow", custo: 35, cooldown: 8, dano: 14, alcance: 40, nivelReq: 12, aprendizado: "ITEM", descricao: "Um disparo muito forte e de longe." }),
  h({ codigo: "tempestade_de_flechas", nome: "Tempestade de Flechas", classe: "ARQUEIRO", tipo: "AREA", icone: "crossbow", custo: 80, cooldown: 100, dano: 12, alcance: 30, raio: 7, nivelReq: 20, ultimate: true, descricao: "Cobre uma área enorme de flechas." }),
  h({ codigo: "flecha_explosiva", nome: "Flecha Explosiva", classe: "ARQUEIRO", tipo: "SKILLSHOT", elemento: "FOGO", icone: "tnt", custo: 30, cooldown: 9, dano: 9, alcance: 28, raio: 3, efeito: "frx:explosiva", nivelReq: 4, descricao: "Explode onde acertar, queimando todos em volta do impacto." }),
  h({ codigo: "voleio_de_recuo", nome: "Voleio de Recuo", classe: "ARQUEIRO", tipo: "MOBILIDADE", icone: "rabbit_foot", custo: 25, cooldown: 10, dano: 8, alcance: 18, efeito: "frx:voleio", nivelReq: 5, descricao: "Salta para trás e, no ar, acerta e atrasa o inimigo da mira." }),
  h({ codigo: "marca_do_cacador", nome: "Marca do Caçador", classe: "ARQUEIRO", tipo: "ALVO", icone: "target", custo: 20, cooldown: 18, dano: 2, alcance: 30, efeito: "frx:marca", potencia: 3, duracao: 10, nivelReq: 7, descricao: "Marca a presa: ela brilha através das paredes e leva 30% a mais de dano seu." }),
  h({ codigo: "leque_de_flechas", nome: "Leque de Flechas", classe: "ARQUEIRO", tipo: "AREA", icone: "bow", custo: 35, cooldown: 9, dano: 8, alcance: 10, efeito: "frx:leque", nivelReq: 8, descricao: "Cinco flechas em leque: acerta e empurra todos num cone à sua frente." }),
  h({ codigo: "armadilha_de_cacador", nome: "Armadilha de Caçador", classe: "ARQUEIRO", tipo: "AREA", icone: "tripwire_hook", custo: 30, cooldown: 16, dano: 7, alcance: 12, raio: 2.5, efeito: "frx:armadilha", duracao: 4, nivelReq: 9, descricao: "Arma uma armadilha no chão mirado. Quem pisar fica preso e à mostra." }),
  h({ codigo: "flecha_gancho", nome: "Flecha-Gancho", classe: "ARQUEIRO", tipo: "MOBILIDADE", icone: "lead", custo: 25, cooldown: 12, alcance: 26, efeito: "frx:gancho", nivelReq: 11, descricao: "Crava uma flecha com corda no bloco mirado e te puxa até lá. Sem dano de queda." }),
  h({ codigo: "tiro_ricochete", nome: "Tiro Ricochete", classe: "ARQUEIRO", tipo: "ALVO", icone: "prismarine_shard", custo: 35, cooldown: 10, dano: 11, alcance: 28, raio: 7, efeito: "frx:ricochete", potencia: 3, nivelReq: 14, aprendizado: "ITEM", descricao: "A flecha acerta o alvo e ricocheteia em até 3 inimigos próximos, perdendo força a cada salto." }),
  h({ codigo: "disparo_do_falcao", nome: "Disparo do Falcão", classe: "ARQUEIRO", tipo: "SKILLSHOT", icone: "end_rod", custo: 85, cooldown: 110, dano: 26, alcance: 60, efeito: "frx:perfurar", nivelReq: 25, ultimate: true, descricao: "Um tiro de precisão que atravessa todos na linha. Quanto mais longe o alvo, mais dano (até o dobro)." }),
  // Mago (mana)
  h({ codigo: "bola_de_fogo", nome: "Bola de Fogo", classe: "MAGO", tipo: "SKILLSHOT", elemento: "FOGO", icone: "fire_charge", custo: 20, cooldown: 3, dano: 8, alcance: 24, descricao: "Clássica. Queima o alvo." }),
  h({ codigo: "lanca_de_gelo", nome: "Lança de Gelo", classe: "MAGO", tipo: "SKILLSHOT", elemento: "GELO", icone: "ice", custo: 25, cooldown: 6, dano: 7, alcance: 24, nivelReq: 3, descricao: "Fere e deixa o alvo lento." }),
  h({ codigo: "teleporte", nome: "Teleporte", classe: "MAGO", tipo: "MOBILIDADE", elemento: "ARCANO", icone: "ender_pearl", custo: 30, cooldown: 12, alcance: 8, nivelReq: 5, descricao: "Some e reaparece alguns blocos à frente." }),
  h({ codigo: "meteoro", nome: "Meteoro", classe: "MAGO", tipo: "AREA", elemento: "FOGO", icone: "magma_block", custo: 50, cooldown: 14, dano: 14, alcance: 30, raio: 4, nivelReq: 10, descricao: "Uma rocha em chamas cai no ponto mirado." }),
  h({ codigo: "raio_arcano", nome: "Raio", classe: "MAGO", tipo: "ALVO", elemento: "RAIO", icone: "lightning_rod", custo: 40, cooldown: 10, dano: 12, alcance: 20, nivelReq: 12, aprendizado: "ITEM", descricao: "Um relâmpago no alvo da mira." }),
  h({ codigo: "chuva_de_meteoros", nome: "Chuva de Meteoros", classe: "MAGO", tipo: "AREA", elemento: "FOGO", icone: "fire_coral_block", custo: 100, cooldown: 120, dano: 24, alcance: 30, raio: 7, nivelReq: 20, ultimate: true, descricao: "O céu desaba em fogo." }),
  h({ codigo: "nova_de_gelo", nome: "Nova de Gelo", classe: "MAGO", tipo: "AREA", elemento: "GELO", icone: "blue_ice", custo: 35, cooldown: 14, dano: 6, raio: 5, efeito: "frx:congelar", duracao: 3, nivelReq: 4, descricao: "Uma explosão de gelo em volta de você congela no lugar todos os inimigos próximos." }),
  h({ codigo: "misseis_arcanos", nome: "Mísseis Arcanos", classe: "MAGO", tipo: "ALVO", elemento: "ARCANO", icone: "amethyst_shard", custo: 30, cooldown: 8, dano: 5, alcance: 22, efeito: "frx:misseis", potencia: 4, nivelReq: 6, descricao: "Quatro mísseis teleguiados em sequência. Se o alvo morrer, os que sobram procuram outro." }),
  h({ codigo: "telecinese", nome: "Telecinese", classe: "MAGO", tipo: "ALVO", elemento: "ARCANO", icone: "shulker_shell", custo: 30, cooldown: 12, dano: 4, alcance: 16, efeito: "minecraft:levitation", potencia: 4, duracao: 2, nivelReq: 7, descricao: "Ergue o inimigo no ar e deixa ele despencar." }),
  h({ codigo: "barreira_de_mana", nome: "Barreira de Mana", classe: "MAGO", tipo: "BUFF", elemento: "ARCANO", icone: "heart_of_the_sea", custo: 20, cooldown: 30, efeito: "frx:escudo_mana", potencia: 7, duracao: 12, nivelReq: 8, descricao: "Por 12 s, 70% do dano que você levaria sai da mana em vez da vida. Quebra se a mana acabar." }),
  h({ codigo: "transposicao", nome: "Transposição", classe: "MAGO", tipo: "ALVO", elemento: "ARCANO", icone: "chorus_fruit", custo: 30, cooldown: 15, dano: 5, alcance: 20, efeito: "frx:trocar", nivelReq: 9, descricao: "Troca de lugar com o alvo da mira, inimigo ou aliado. O inimigo chega tonto e lento." }),
  h({ codigo: "sifao_arcano", nome: "Sifão Arcano", classe: "MAGO", tipo: "ALVO", elemento: "ARCANO", icone: "experience_bottle", custo: 5, cooldown: 22, dano: 6, alcance: 16, efeito: "frx:sifao", potencia: 4, nivelReq: 11, descricao: "Suga a energia do alvo: causa dano e devolve 40 de mana." }),
  h({ codigo: "corrente_de_raios", nome: "Corrente de Raios", classe: "MAGO", tipo: "ALVO", elemento: "RAIO", icone: "trident", custo: 45, cooldown: 12, dano: 11, alcance: 20, raio: 7, efeito: "frx:ricochete", potencia: 4, nivelReq: 15, descricao: "O relâmpago acerta o alvo e salta por até 4 inimigos próximos, enfraquecendo a cada salto." }),
  h({ codigo: "nevasca", nome: "Nevasca", classe: "MAGO", tipo: "AREA", elemento: "GELO", icone: "powder_snow_bucket", custo: 55, cooldown: 20, dano: 5, alcance: 24, raio: 5, efeito: "frx:zona", duracao: 6, nivelReq: 16, aprendizado: "ITEM", descricao: "Uma tempestade de neve fica 6 s no ponto mirado, ferindo e atrasando quem estiver dentro." }),
  h({ codigo: "singularidade", nome: "Singularidade", classe: "MAGO", tipo: "AREA", elemento: "ARCANO", icone: "dragon_breath", custo: 100, cooldown: 130, dano: 7, alcance: 24, raio: 7, efeito: "frx:singularidade", duracao: 5, nivelReq: 25, ultimate: true, descricao: "Abre um buraco negro que suga os inimigos para o centro por 5 s e depois explode." }),
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
